package com.shouyun.bosslandlord.server;

import com.mojang.brigadier.Command;
import com.shouyun.bosslandlord.*;
import com.shouyun.bosslandlord.game.*;
import com.shouyun.bosslandlord.network.Packets;
import com.shouyun.bosslandlord.world.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.*;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.*;

public final class MatchManager {
    private static final Map<UUID, Match> MATCHES = new HashMap<>();
    private static final Map<UUID, Match> PARTICIPANTS = new HashMap<>();
    private static final Map<UUID, PropBurst> PROP_BURSTS = new HashMap<>();
    private static final String RECOVERY = "bosslandlord:recovery";
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, id("bosses"));
    private static final ResourceKey<DamageType> WAGER = ResourceKey.create(Registries.DAMAGE_TYPE, id("card_wager"));
    private MatchManager() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, path); }
    static long time(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§6[打不过就斗地主] §r" + text), false); }
    static boolean isPlaying(Entity entity) { return PARTICIPANTS.containsKey(entity.getUUID()); }

    public static void action(ServerPlayer player, Packets.Action packet) {
        if (packet.action() == Packets.START) { start(player); return; }
        if (MatchLobby.handle(player, packet)) return;
        Match m = PARTICIPANTS.get(player.getUUID());
        if (m == null || !m.id.equals(packet.match())) return;
        int seat = m.seatOf(player);
        if (seat < 0) return;
        switch (packet.action()) {
            case Packets.PLAY, Packets.PASS -> {
                if (m.phase != MatchPhase.PLAY || packet.revision() != m.revision || m.round.turn() != seat) return;
                if (!m.canPlay()) { m.sync(false); return; }
                if (packet.action() == Packets.PLAY && packet.cards().length == 0) return;
                m.play(seat, packet.action() == Packets.PASS ? new int[0] : packet.cards());
            }
            case Packets.CLAIM, Packets.DECLINE -> {
                if (m.phase != MatchPhase.AUCTION || packet.revision() != m.revision) return;
                boolean claim = packet.action() == Packets.CLAIM;
                if (seat == 0) m.bid(claim);
                if (seat == 1 && m.duel() && m.auction.step() == LandlordAuction.Step.BOSS_RESPONSE)
                    m.rivalBid(claim);
            }
            case Packets.NORMAL, Packets.DOUBLE, Packets.SUPER -> {
                if (seat == 0 && m.phase == MatchPhase.STAKE && packet.revision() == m.revision)
                    m.chooseStake(packet.action() - Packets.NORMAL + 1);
            }
            case Packets.LEAVE -> leave(player, m, "你认输了");
            case Packets.REOPEN -> m.syncPlayer(player, seat, true, m.state());
            case Packets.REMATCH -> {
                if (packet.revision() == m.revision) m.requestRematch(player, seat);
            }
            case Packets.TABLE_PROP -> {
                if (packet.cards().length == 2)
                    m.useProp(seat, packet.cards()[0], TableProp.byIndex(packet.cards()[1]));
            }
            default -> {}
        }
    }
    public static void interactWithTable(ServerPlayer player, TableEntity table) {
        Match match = PARTICIPANTS.get(player.getUUID());
        if (match != null) {
            if (match.table == table) match.syncPlayer(player, match.seatOf(player), true, match.state());
            return;
        }
        if (player.distanceToSqr(table) > 64 || MatchLobby.reopen(player)) return;
        CompoundTag publicState = table.publicState();
        if (publicState.getBoolean("active")) {
            CompoundTag watch = publicState.copy();
            watch.putBoolean("watch", true);
            watch.putInt("table", table.getId());
            Packets.send(player, watch);
        }
    }
    public static void startBossChallenge(ServerPlayer player) {
        start(player, false);
    }

    private static void start(ServerPlayer player) {
        start(player, true);
    }

    private static void start(ServerPlayer player, boolean allowDuel) {
        Match match = PARTICIPANTS.get(player.getUUID());
        if (match != null) {
            match.syncPlayer(player, match.seatOf(player), true, match.state());
            return;
        }
        if (MatchLobby.reopen(player)) return;
        if (!readyToStart(player)) return;
        ServerPlayer opponent = allowDuel ? MatchLobby.aimedOpponent(player) : null;
        if (opponent != null) {
            MatchLobby.open(player, null, opponent);
            return;
        }
        ServerLevel level = player.serverLevel();
        int range = Config.BOSS_RANGE.get();
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1);
        if (canChallengeAnyMob(player)) {
            Vec3 end = eye.add(look.scale(range));
            Mob aimed = level.getEntitiesOfClass(Mob.class, new AABB(eye, end).inflate(.35),
                    mob -> mob.isAlive() && !(mob instanceof GuestEntity)
                            && mob.getBoundingBox().inflate(.25).clip(eye, end).isPresent()
                            && player.hasLineOfSight(mob)).stream()
                    .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(eye))).orElse(null);
            if (aimed != null) {
                if (mobAvailable(player, aimed) && tableSpace(player) != null) MatchLobby.open(player, aimed);
                return;
            }
        }
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
                e -> e.isAlive() && e.getType().is(BOSSES) && e.distanceToSqr(player) <= range * range);
        nearby.sort(Comparator.comparingDouble(e -> {
            double dot = e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look);
            boolean fighting = player.getLastHurtMob() == e || player.getLastHurtByMob() == e;
            return e.distanceToSqr(player) - (dot > .92 ? range * range : 0) - (fighting ? range * range * 2 : 0);
        }));
        if (nearby.isEmpty()) {
            if (allowDuel && MatchLobby.hasOpponent(player)) MatchLobby.open(player, null);
            else tell(player, canChallengeAnyMob(player) ? "请对准附近的生物再发起挑战。"
                    : "附近没有可挑战的 Boss。靠近监守者、凋灵或末影龙后再试。");
            return;
        }
        Mob boss = nearby.getFirst();
        if (!mobAvailable(player, boss) || tableSpace(player) == null) return;
        MatchLobby.open(player, boss);
    }

    private static boolean readyToStart(ServerPlayer player) {
        if (!player.isAlive() || player.hasDisconnected() || player.isSpectator() || isPlaying(player)) return false;
        if (player.isPassenger() || player.isSleeping()) {
            tell(player, "请先离开载具或床。");
            return false;
        }
        return true;
    }

    private static boolean canChallengeAnyMob(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && Config.CHALLENGE_ANY_MOB.get();
    }

    private static boolean mobAvailable(ServerPlayer player, Mob mob) {
        if (!mob.getType().is(BOSSES) && !canChallengeAnyMob(player)) {
            tell(player, "生存模式的任意生物挑战当前未启用。");
            return false;
        }
        int range = Config.BOSS_RANGE.get();
        if (mob.isRemoved() || !mob.isAlive() || mob.level() != player.serverLevel() || mob.distanceToSqr(player) > range * range) {
            tell(player, "对手已离开挑战范围，请靠近后重试。");
            return false;
        }
        if (isPlaying(mob)) { tell(player, "这位对手正在别的牌局里。"); return false; }
        if (mob.isPassenger() || mob.isVehicle() || mob instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0
                || mob.hasPose(Pose.DIGGING) || mob.hasPose(Pose.EMERGING)) {
            tell(player, "等对手离开载具或完成当前动作后再邀请。");
            return false;
        }
        return true;
    }

    private static Vec3 tableSpace(ServerPlayer player) {
        Vec3 center = findTableSpace(player);
        if (center == null) tell(player, "附近放不下牌桌。请找一片约 5×5 格、上方无遮挡的平地。");
        return center;
    }

    static Mob findNearbyGuest(ServerPlayer player, Mob boss) {
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                e -> e != boss && !(e instanceof GuestEntity) && e.isAlive() && !e.getType().is(BOSSES)
                        && !e.isPassenger() && !e.isVehicle() && e.getBbWidth() <= 2 && e.getBbHeight() <= 3.5 && !isPlaying(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player) + (e instanceof Enemy ? 100 : 0)))
                .orElse(null);
    }

    private static Mob spawnGuest(ServerLevel level, Vec3 center, GuestChoice choice) {
        Mob guest;
        if (choice == GuestChoice.NEARBY) {
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) return null;
            villager.setCustomName(Component.translatable("entity.bosslandlord.card_friend"));
            villager.setCustomNameVisible(true);
            guest = villager;
        } else {
            GuestEntity character = CantBeatItLandlord.GUEST.get().create(level);
            if (character == null) return null;
            character.setAppearance(choice);
            guest = character;
        }
        TableSeat seat = TableSeat.GUEST;
        guest.moveTo(center.x + seat.x, center.y, center.z + seat.z, seat.yaw, 0);
        return level.addFreshEntity(guest) ? guest : null;
    }

    static boolean startMatch(ServerPlayer player, Mob rival, GuestChoice choice, ServerPlayer friend) {
        return startMatch(player, rival, choice, friend, DuelStake.NONE);
    }

    static boolean startDuel(ServerPlayer player, ServerPlayer opponent, DuelStake stake) {
        return startMatch(player, opponent, GuestChoice.NAOCHI, null, stake);
    }

    private static boolean startMatch(ServerPlayer player, LivingEntity rival, GuestChoice choice, ServerPlayer friend, DuelStake stake) {
        if (!readyToStart(player)) return false;
        if (rival instanceof Mob mob && !mobAvailable(player, mob)) return false;
        if (rival instanceof ServerPlayer opponent) {
            if (!MatchLobby.nearbyPlayer(player, opponent)) return false;
            if (!DuelEscrow.canFund(player, stake) || !DuelEscrow.canFund(opponent, stake)) {
                tell(player, "双方背包都要有 1 个" + stake.title + "才能开局。");
                tell(opponent, "双方背包都要有 1 个" + stake.title + "才能开局。");
                return false;
            }
        }
        Vec3 center = tableSpace(player);
        if (center == null) return false;
        LivingEntity guest = friend != null ? friend
                : choice == GuestChoice.NEARBY && rival instanceof Mob boss ? findNearbyGuest(player, boss) : null;
        boolean temporary = guest == null;
        if (temporary) {
            if (choice == GuestChoice.NEARBY && !Config.FILL_VILLAGER.get()) {
                tell(player, "附近没有空闲生物，请选一位临时牌友。");
                return false;
            }
            guest = spawnGuest(player.serverLevel(), center, choice);
            if (guest == null) { tell(player, "临时牌友未能入座，请重试。"); return false; }
        }
        Match match = new Match(player, rival, guest, choice, temporary, center, stake);
        MATCHES.put(player.getUUID(), match);
        for (LivingEntity entity : match.people) PARTICIPANTS.put(entity.getUUID(), match);
        try {
            match.seat();
            if (stake.isItem() && rival instanceof ServerPlayer opponent) {
                match.escrowReserved = DuelEscrow.reserve(player, opponent, stake);
                if (!match.escrowReserved) {
                    match.close(false);
                    tell(player, "入座时赌注已变动，请重新邀请。");
                    tell(opponent, "入座时赌注已变动，请重新邀请。");
                    return false;
                }
            }
            match.sync(true);
            return true;
        } catch (Exception e) {
            CantBeatItLandlord.LOGGER.error("Could not set up card table", e);
            match.close(false); tell(player, "没能摆好牌桌，已恢复原位。请换一片空地重试。");
            return false;
        }
    }
    private static Vec3 findTableSpace(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        for (int radius = 0; radius <= 6; radius += 2) for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2) {
            if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
            for (int dy : new int[]{0, -1, 1, -2, 2}) {
                BlockPos c = base.offset(dx, dy, dz); boolean clear = true;
                for (int x = -2; x <= 2 && clear; x++) for (int z = -2; z <= 2 && clear; z++) {
                    BlockPos floor = c.offset(x, -1, z);
                    if (!level.getWorldBorder().isWithinBounds(floor) || !level.hasChunkAt(floor)
                            || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) { clear = false; break; }
                    for (int h = 0; h <= 3; h++) {
                        BlockPos p = c.offset(x, h, z);
                        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) { clear = false; break; }
                    }
                }
                if (clear) {
                    Vec3 v = Vec3.atBottomCenterOf(c);
                    boolean overlapping = MATCHES.values().stream().anyMatch(m -> m.level == level && m.center.distanceToSqr(v) < 49);
                    if (!overlapping) return v;
                }
            }
        }
        return null;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % 20 == 0) {
            PROP_BURSTS.values().removeIf(burst -> burst.used(now) == 0);
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) DuelEscrow.deliver(player);
        }
        MatchLobby.tick(now);
        for (Match m : List.copyOf(MATCHES.values())) {
            try { m.tick(); }
            catch (Exception e) { CantBeatItLandlord.LOGGER.error("Card match interrupted; restoring participants", e); m.close(false); }
        }
    }
    @SubscribeEvent public static void freeze(EntityTickEvent.Pre event) {
        // Applies on both sides and to rideTick, stopping dragon terrain damage and warden brains as well.
        if (event.getEntity() instanceof Mob && event.getEntity().getVehicle() instanceof SeatEntity) event.setCanceled(true);
    }
    @SubscribeEvent public static void protect(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide && isPlaying(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void preventTargeting(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && !target.level().isClientSide && isPlaying(target)) event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent public static void preventEffects(MobEffectEvent.Applicable event) {
        if (!event.getEntity().level().isClientSide && isPlaying(event.getEntity())
                && !event.getEffectInstance().getEffect().value().isBeneficial())
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    @SubscribeEvent public static void preventAttack(AttackEntityEvent event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity || protectedTarget(event.getTarget()))
            event.setCanceled(true);
    }
    @SubscribeEvent public static void preventProjectileHit(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && protectedTarget(hit.getEntity()))
            event.setCanceled(true);
    }
    private static boolean protectedTarget(Entity target) {
        Entity body = target instanceof PartEntity<?> part ? part.getParent() : target;
        return !body.level().isClientSide && isPlaying(body);
    }
    @SubscribeEvent public static void preventItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) event.setCanceled(true);
    }
    @SubscribeEvent public static void preventBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) event.setCanceled(true);
    }
    @SubscribeEvent public static void preventMining(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) event.setCanceled(true);
    }
    @SubscribeEvent public static void preventMobUse(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity && !(event.getTarget() instanceof TableEntity)) event.setCanceled(true);
    }
    @SubscribeEvent public static void preventSpecificMobUse(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity && !(event.getTarget() instanceof TableEntity)) event.setCanceled(true);
    }
    @SubscribeEvent public static void mount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide && event.isDismounting() && PARTICIPANTS.containsKey(event.getEntityMounting().getUUID()) && event.getEntityBeingMounted() instanceof SeatEntity)
            event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MatchLobby.disconnect(player);
        Match m = PARTICIPANTS.get(player.getUUID());
        if (m != null) leave(player, m, "离开游戏，牌局认输");
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && !PARTICIPANTS.containsKey(event.getEntity().getUUID())) restore(event.getEntity());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        restore(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) DuelEscrow.recover(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        MatchLobby.stop();
        for (Match m : List.copyOf(MATCHES.values())) m.close(false);
        MATCHES.clear(); PARTICIPANTS.clear();
        PROP_BURSTS.clear();
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bosslandlord")
                .executes(c -> { start(c.getSource().getPlayerOrException()); return Command.SINGLE_SUCCESS; })
                .then(Commands.literal("start").executes(c -> { start(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("accept").then(Commands.argument("invite", UuidArgument.uuid()).executes(c -> {
                    MatchLobby.respond(c.getSource().getPlayerOrException(), UuidArgument.getUuid(c, "invite"), true);
                    return 1;
                })))
                .then(Commands.literal("decline").then(Commands.argument("invite", UuidArgument.uuid()).executes(c -> {
                    MatchLobby.respond(c.getSource().getPlayerOrException(), UuidArgument.getUuid(c, "invite"), false);
                    return 1;
                })))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException(); Match m = PARTICIPANTS.get(p.getUUID());
                    if (m != null) leave(p, m, "你认输了");
                    return 1;
                })));
    }

    private static void leave(ServerPlayer player, Match match, String reason) {
        if (match.rematchBy != null) {
            if (player.hasDisconnected()) match.close(false);
            return;
        }
        if (player == match.player) match.leave(false, reason);
        else if (player == match.rival) match.leave(true, "对手认输");
        else if (match.phase == MatchPhase.RESULT) match.close(true);
        else match.replaceFriend(player);
    }
    private static void snapshot(LivingEntity e, boolean temporary) {
        CompoundTag n = new CompoundTag();
        n.putDouble("x", e.getX()); n.putDouble("y", e.getY()); n.putDouble("z", e.getZ());
        n.putFloat("yaw", e.getYRot()); n.putFloat("pitch", e.getXRot());
        n.putBoolean("gravity", e.isNoGravity()); n.putBoolean("invulnerable", e.isInvulnerable());
        n.putBoolean("silent", e.isSilent()); n.putBoolean("physics", e.noPhysics);
        n.putBoolean("temporary", temporary);
        n.putString("dimension", e.level().dimension().location().toString());
        if (e instanceof Mob mob) {
            n.putBoolean("noAI", mob.isNoAi());
            if (mob.getTarget() != null) n.putUUID("target", mob.getTarget().getUUID());
        }
        e.getPersistentData().put(RECOVERY, n);
    }
    private static void restore(Entity e) {
        if (!e.getPersistentData().contains(RECOVERY)) return;
        CompoundTag n = e.getPersistentData().getCompound(RECOVERY).copy();
        e.getPersistentData().remove(RECOVERY);
        e.stopRiding();
        e.setNoGravity(n.getBoolean("gravity")); e.setInvulnerable(n.getBoolean("invulnerable"));
        e.setSilent(n.getBoolean("silent")); e.noPhysics = n.getBoolean("physics");
        e.fallDistance = 0; e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof Mob mob) {
            mob.setNoAi(n.getBoolean("noAI"));
            if (e.level() instanceof ServerLevel sl && n.hasUUID("target") && sl.getEntity(n.getUUID("target")) instanceof LivingEntity target && target.isAlive())
                mob.setTarget(target);
        }
        if (n.getBoolean("temporary")) { e.discard(); return; }
        if (n.contains("dimension") && !n.getString("dimension").equals(e.level().dimension().location().toString())) return;
        if (e instanceof ServerPlayer sp && sp.connection != null && !sp.hasDisconnected()) sp.connection.teleport(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"), n.getFloat("yaw"), n.getFloat("pitch"));
        else if (e instanceof ServerPlayer) e.moveTo(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"), n.getFloat("yaw"), n.getFloat("pitch"));
        else {
            e.teleportTo(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"));
            e.setYRot(n.getFloat("yaw")); e.setXRot(n.getFloat("pitch"));
        }
    }

    private static final class Match {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final LivingEntity rival;
        final LivingEntity[] people;
        final GuestChoice guestChoice;
        final DuelStake duelStake;
        boolean escrowReserved;
        final ServerLevel level; final Vec3 center;
        boolean temporary;
        LandlordRound round = new LandlordRound(new Random());
        LandlordAuction auction = new LandlordAuction();
        final List<SeatEntity> seats = new ArrayList<>();
        final long started;
        TableEntity table;
        MatchPhase phase = MatchPhase.SHUFFLE;
        int revision, multiplier = 1;
        private record PropFlight(TableProp prop, int from, int to, long start) {}
        private PropFlight propFlight;
        private ServerPlayer rematchBy;
        private long flipAt = -1;
        boolean won, closed;
        long deadline, phaseAt, finishAt, dealAt, comboAt = -1;
        int lastSeat = -1;
        long lastMoveAt;
        int[] lastCards = new int[0];
        PlayEffect effect = PlayEffect.NONE;
        boolean blastedPlane;
        String message = "洗牌中", result = "";
        Match(ServerPlayer p, LivingEntity opponent, LivingEntity g, GuestChoice choice, boolean t, Vec3 c, DuelStake stake) {
            player = p; rival = opponent; temporary = t; center = c; level = p.serverLevel();
            people = new LivingEntity[]{p, opponent, g};
            guestChoice = choice;
            duelStake = stake;
            started = time(p);
            phaseAt = level.getGameTime();
            deadline = started + MatchPhase.SHUFFLE_TICKS;
            dealAt = started + MatchPhase.RIFFLE_TICKS;
        }

        boolean duel() { return rival instanceof ServerPlayer; }

        int seatOf(ServerPlayer participant) {
            for (int i = 0; i < people.length; i++) if (people[i] == participant) return i;
            return -1;
        }

        private String rivalName() { return rival.getName().getString(); }
        void seat() {
            table = CantBeatItLandlord.TABLE.get().create(level);
            if (table == null) throw new IllegalStateException("No table entity");
            table.moveTo(center.x, center.y, center.z, 0, 0);
            if (!level.addFreshEntity(table)) throw new IllegalStateException("Table spawn denied");
            for (int i = 0; i < 3; i++) {
                TableSeat placement = TableSeat.at(i);
                SeatEntity seat = CantBeatItLandlord.SEAT.get().create(level);
                if (seat == null) throw new IllegalStateException("No seat entity");
                seat.moveTo(center.x + placement.x, center.y, center.z + placement.z, placement.yaw, 0);
                seats.add(seat);
                if (!level.addFreshEntity(seat)) throw new IllegalStateException("Chair spawn denied");
                seatParticipant(people[i], seat, temporary && i == 2);
            }
            for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.ofSize(center, 48, 24, 48),
                    e -> e.getTarget() != null && isPlaying(e.getTarget()))) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            sound(MatchSounds.SHUFFLE.get(), 1F);
        }

        private void seatParticipant(LivingEntity entity, SeatEntity seat, boolean disposable) {
            snapshot(entity, disposable);
            entity.setInvulnerable(true);
            entity.setNoGravity(true);
            // Vanilla entity-bound sounds respect the entity's silent flag.
            entity.setSilent(!(entity instanceof GuestEntity));
            entity.noPhysics = true;
            entity.setDeltaMovement(Vec3.ZERO);
            entity.fallDistance = 0;
            entity.stopUsingItem();
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            if (entity instanceof EnderDragon dragon) dragon.nearestCrystal = null;
            if (entity instanceof ServerPlayer sp)
                sp.connection.teleport(seat.getX(), seat.getY() - .16, seat.getZ(), seat.getYRot(), 13);
            else entity.teleportTo(seat.getX(), seat.getY(), seat.getZ());
            if (!entity.startRiding(seat, true)) throw new IllegalStateException("Participant mounting denied");
            seat.positionRider(entity);
        }

        void replaceFriend(ServerPlayer friend) {
            if (closed || people[2] != friend) return;
            Mob replacement = spawnGuest(level, center, GuestChoice.NAOCHI);
            if (replacement == null) { close(false); return; }
            PARTICIPANTS.remove(friend.getUUID());
            restore(friend);
            endClient(friend);
            people[2] = replacement;
            temporary = true;
            PARTICIPANTS.put(replacement.getUUID(), this);
            try {
                seatParticipant(replacement, seats.get(2), true);
            } catch (RuntimeException e) {
                CantBeatItLandlord.LOGGER.error("Replacement card guest could not take its seat", e);
                close(false);
                return;
            }
            message = "好友离席，闹吃接手出牌";
            revision++;
            if (phase == MatchPhase.PLAY && round.turn() == 2) deadline = time(player) + 36;
            sync(false);
        }

        void tick() {
            if (closed) return;
            if (player.isRemoved() || !player.isAlive() || player.serverLevel() != level || !rival.isAlive()
                    || rival.isRemoved() || rival.level() != level || table == null || table.isRemoved()
                    || seats.stream().anyMatch(Entity::isRemoved)) { close(false); return; }
            if (people[2] instanceof ServerPlayer friend && (!friend.isAlive() || friend.isRemoved() || friend.hasDisconnected()
                    || friend.serverLevel() != level || friend.getVehicle() != seats.get(2))) {
                if (phase == MatchPhase.RESULT) { close(rematchBy == null); return; }
                replaceFriend(friend);
                if (closed) return;
            }
            if (!people[2].isAlive() || people[2].isRemoved()) { close(false); return; }
            for (int i = 0; i < seats.size(); i++) {
                if (people[i].getVehicle() != seats.get(i)) { close(false); return; }
                people[i].fallDistance = 0;
            }
            if (people[2] instanceof GuestEntity guest) guest.tickSong();
            finishProp();
            long now = time(player);
            if (comboAt >= 0 && now >= comboAt) {
                comboAt = -1;
                emitCombo();
            }
            if (phase == MatchPhase.RESULT) {
                if (now >= finishAt) {
                    if (rematchBy != null) rematch(rematchBy);
                    else close(true);
                }
                return;
            }
            if (now - started > 20 * 60 * 20) {
                if (rival instanceof ServerPlayer opponent) {
                    tell(player, "对决超时，牌局结束。");
                    tell(opponent, "对决超时，牌局结束。");
                    close(false);
                } else finish(false, "牌局超时");
                return;
            }
            if (phase == MatchPhase.SHUFFLE && dealAt >= 0 && now >= dealAt) {
                dealAt = -1;
                sound(MatchSounds.DEAL.get(), 1F);
            }
            if (now >= deadline) {
                switch (phase) {
                    case SHUFFLE -> enter(MatchPhase.AUCTION, 320, "看手牌，决定是否叫地主");
                    case AUCTION -> {
                        if (auction.step() == LandlordAuction.Step.BOSS_RESPONSE)
                            rivalBid(!duel() && LandlordAuction.strongHand(round.hand(1)));
                        else bid(false);
                    }
                    case STAKE -> chooseStake(1);
                    case PLAY -> play(round.turn(), round.suggest(round.turn()));
                    case RESULT -> {}
                }
            } else if (phase != MatchPhase.SHUFFLE && (now - started) % 20 == 0) sync(false);
        }

        void enter(MatchPhase next, long ticks, String text) {
            phase = next;
            phaseAt = level.getGameTime();
            deadline = time(player) + ticks;
            message = text;
            revision++;
            sync(false);
        }

        void bid(boolean claim) {
            if (!auction.playerDecision(claim)) return;
            sound(claim ? MatchSounds.BID.get() : MatchSounds.PASS.get(), 1F);
            if (auction.step() == LandlordAuction.Step.COMPLETE) {
                assignLandlord();
                return;
            }
            deadline = time(player) + (duel() ? Config.TURN_SECONDS.get() * 20L : 26);
            message = claim ? "你叫了地主，等待 " + rivalName() + " 决定" : "你不叫地主，轮到 " + rivalName();
            revision++;
            sync(false);
        }

        void rivalBid(boolean rob) {
            if (duel() && !rob && auction.candidate() == -1) {
                round = new LandlordRound(new Random());
                auction = new LandlordAuction();
                dealAt = time(player) + MatchPhase.RIFFLE_TICKS;
                sound(MatchSounds.SHUFFLE.get(), 1F);
                enter(MatchPhase.SHUFFLE, MatchPhase.SHUFFLE_TICKS, "双方不叫地主，重新发牌");
                return;
            }
            if (!auction.bossDecision(rob)) return;
            if (auction.step() == LandlordAuction.Step.COMPLETE) {
                sound(auction.candidate() == 1 ? MatchSounds.BID.get() : MatchSounds.PASS.get(), .87F);
                assignLandlord();
                return;
            }
            sound(MatchSounds.BID.get(), .87F);
            deadline = time(player) + 320;
            message = rivalName() + " 抢了地主，要反抢吗？";
            revision++;
            sync(false);
        }

        void assignLandlord() {
            round.assignLandlord(auction.candidate());
            if (duel()) beginPlay(people[round.landlord()].getName().getString() + " 成为地主，先出牌");
            else if (round.landlord() == 0) enter(MatchPhase.STAKE, 320, "你拿到了底牌，选择本局赌注");
            else beginPlay(rivalName() + " 成为地主，先出牌");
        }

        void chooseStake(int stake) {
            multiplier = stake;
            sound(stake == 1 ? MatchSounds.BID.get() : MatchSounds.STAKE.get(), stake == 3 ? 1.17F : 1F);
            beginPlay("你是地主，先出牌");
        }

        void beginPlay(String text) {
            enter(MatchPhase.PLAY, humanTurn() ? Config.TURN_SECONDS.get() * 20L : 30, text);
        }

        private boolean humanTurn() { return people[round.turn()] instanceof ServerPlayer; }

        boolean canPlay() {
            int duration = effect == PlayEffect.NONE ? MatchPhase.CARD_FLIGHT_TICKS : MatchPhase.COMBO_TICKS;
            return lastCards.length == 0 || level.getGameTime() - lastMoveAt >= duration;
        }

        void play(int seat, int[] cards) {
            Combination previous = round.target();
            String error = round.play(seat, cards);
            if (!error.isEmpty()) {
                if (!(people[seat] instanceof ServerPlayer)) throw new IllegalStateException("AI generated illegal play: " + error);
                message = error;
                sync(false);
                return;
            }
            revision++;
            lastSeat = seat;
            lastMoveAt = level.getGameTime();
            lastCards = Arrays.stream(cards).sorted().toArray();
            Combination combo = Combination.parse(cards);
            if (people[seat] instanceof GuestEntity guest) guest.animateCardTurn(combo != null);
            effect = PlayEffect.of(combo);
            blastedPlane = effect.blasts(previous);
            message = people[seat].getName().getString() + (combo == null ? " 不出" : " 打出" + combo.kind().title);
            sound(combo == null ? MatchSounds.PASS.get() : MatchSounds.PLAY.get(), 1F);
            if (effect != PlayEffect.NONE) comboAt = time(player) + 7;
            if (round.winner() >= 0) {
                finish(round.playerWins(), round.winner() == round.landlord() ? "地主获胜" : "农民获胜");
                return;
            }
            long wait = humanTurn() ? Config.TURN_SECONDS.get() * 20L : effect == PlayEffect.NONE ? 28 : 36;
            deadline = time(player) + wait;
            sync(false);
        }

        void emitCombo() {
            double x = center.x, y = center.y + 1.17, z = center.z;
            switch (effect) {
                case AIRPLANE -> {
                    sound(MatchSounds.AIRPLANE.get(), 1F);
                    for (int i = -3; i <= 3; i++)
                        level.sendParticles(ParticleTypes.CLOUD, x + i * .31, y + .06 * i, z, 2, .06, .02, .10, .01);
                }
                case BOMB -> {
                    sound(MatchSounds.BOMB.get(), 1F);
                    level.sendParticles(ParticleTypes.FLAME, x, y, z, 22, .48, .13, .40, .065);
                    level.sendParticles(ParticleTypes.SMOKE, x, y, z, 16, .33, .12, .32, .05);
                }
                case ROCKET -> {
                    sound(MatchSounds.ROCKET.get(), 1F);
                    level.sendParticles(ParticleTypes.FIREWORK, x - .36, y + .35, z, 22, .18, .33, .18, .14);
                    level.sendParticles(ParticleTypes.END_ROD, x + .36, y + .35, z, 20, .18, .33, .18, .12);
                }
                case NONE -> {}
            }
            if (blastedPlane) level.sendParticles(ParticleTypes.SMOKE, x + .5, y + .45, z, 15, .6, .12, .28, .07);
        }

        void leave(boolean hostWins, String reason) {
            if (phase != MatchPhase.RESULT) finish(hostWins, reason);
            close(true);
        }

        void requestRematch(ServerPlayer requester, int seat) {
            if (phase != MatchPhase.RESULT || rematchBy != null || (seat != 0 && !(duel() && seat == 1))) return;
            if (seat == 0 ? won : !won) {
                rematch(requester);
                return;
            }
            rematchBy = requester;
            flipAt = level.getGameTime();
            finishAt = time(player) + MatchPhase.TABLE_FLIP_TICKS;
            effect = PlayEffect.NONE;
            comboAt = -1;
            result = requester.getScoreboardName() + " 掀翻牌桌，准备重来";
            revision++;
            level.playSound(null, BlockPos.containing(center), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, .95F, .73F);
            sync(false);
        }

        private void rematch(ServerPlayer requester) {
            ServerPlayer teammate = people[2] instanceof ServerPlayer friend ? friend : null;
            close(flipAt < 0);
            if (!readyToStart(requester)) return;
            if (rival instanceof ServerPlayer opponent) {
                MatchLobby.inviteRematch(requester, null, requester == player ? opponent : player, duelStake);
            } else if (rival instanceof Mob boss && boss.isAlive()) {
                if (teammate != null) MatchLobby.inviteRematch(requester, boss, teammate, DuelStake.NONE);
                else if (!startMatch(requester, boss, guestChoice, null)) startBossChallenge(requester);
            } else {
                tell(requester, "这位对手已被击败，可以选择下一位对手。");
                startBossChallenge(requester);
            }
        }

        void useProp(int from, int to, TableProp prop) {
            if (prop == null || to < 0 || to >= people.length || from == to || propFlight != null
                    || phase == MatchPhase.SHUFFLE || phase == MatchPhase.RESULT) return;
            PropBurst burst = PROP_BURSTS.computeIfAbsent(people[from].getUUID(), id -> new PropBurst());
            if (!burst.tryUse(time(player))) return;
            propFlight = new PropFlight(prop, from, to, level.getGameTime());
            sync(false);
        }

        private void finishProp() {
            if (propFlight == null || level.getGameTime() - propFlight.start() < TableProp.FLIGHT_TICKS) return;
            PropFlight flight = propFlight;
            propFlight = null;
            LivingEntity target = people[flight.to()];
            LivingEntity from = people[flight.from()];
            double height = target.getY() + .96;
            boolean water = flight.prop() == TableProp.WATER;
            if (water) {
                level.sendParticles(ParticleTypes.SPLASH, target.getX(), height, target.getZ(), 14, .23, .12, .23, .06);
            } else {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, flight.prop().stack()),
                        target.getX(), height, target.getZ(), 15, .22, .20, .22, .075);
            }
            level.playSound(null, target.getX(), height, target.getZ(),
                    water ? SoundEvents.BOTTLE_FILL : SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, .75F, 1.12F);
            if (target instanceof ServerPlayer receiver)
                tell(receiver, from.getName().getString() + (water ? " 给你倒了杯水。" : " 朝你扔了" + flight.prop().title + "。"));
            sync(false);
        }

        void finish(boolean playerWon, String reason) {
            if (phase == MatchPhase.RESULT || closed) return;
            won = playerWon;
            long now = time(player);
            finishAt = now + 130;
            LivingEntity loser = won ? rival : player;
            if (duel()) {
                result = reason + " · " + (won ? player : rival).getName().getString() + " 赢得对决";
                if (duelStake.isItem()) result += "，获得 1 个" + duelStake.title;
                if (duelStake == DuelStake.HEALTH) {
                    result += "，" + loser.getName().getString() + " 将扣除 " + wagerDamage(loser, 1) + " 点生命";
                }
            } else {
                result = reason + " · " + (won ? rivalName() : "你") + " 将扣除 "
                        + wagerDamage(loser, multiplier) + " 点生命（×" + multiplier + "）";
            }
            enter(MatchPhase.RESULT, 130, result);
            tell(player, result);
            if (rival instanceof ServerPlayer opponent) tell(opponent, result);
            if (people[2] instanceof ServerPlayer friend) tell(friend, friendResult());
        }

        private boolean friendWon() {
            return round.sameSide(0, 2) == won;
        }

        private String friendResult() {
            return (friendWon() ? "你所在的农民一方获胜" : "你所在的农民一方落败")
                    + " · 生命赌注由房主与 " + rivalName() + " 结算";
        }

        CompoundTag state() {
            CompoundTag n = new CompoundTag();
            n.putBoolean("active", true); n.putUUID("match", id);
            n.putBoolean("duel", duel());
            if (duel()) n.putInt("duelStake", duelStake.ordinal());
            if (flipAt >= 0) n.putLong("flipAt", flipAt);
            if (propFlight != null) {
                n.putInt("prop", propFlight.prop().ordinal());
                n.putInt("propFrom", propFlight.from());
                n.putInt("propTo", propFlight.to());
                n.putLong("propAt", propFlight.start());
            }
            n.putInt("revision", revision);
            n.putString("phase", phase.name());
            n.putLong("phaseAt", phaseAt);
            n.putInt("turn", round.turn());
            n.putString("auction", auction.step().name());
            n.putInt("candidate", auction.candidate());
            n.putInt("landlord", round.landlord());
            n.putInt("multiplier", multiplier);
            n.putFloat("playerMax", player.getMaxHealth());
            n.putFloat("bossMax", rival.getMaxHealth());
            n.putInt("lastSeat", lastSeat);
            n.putLong("lastMoveAt", lastMoveAt);
            n.putIntArray("lastCards", lastCards);
            n.putString("effect", effect.name());
            n.putBoolean("blastedPlane", blastedPlane);
            n.putInt("leader", round.leader());
            n.putString("message", switch (phase) {
                case AUCTION -> "房主与 " + rivalName() + " 竞选地主";
                case STAKE -> "房主正在选择赌注";
                default -> message.startsWith("你是地主") ? "房主成为地主，先出牌" : message;
            });
            n.putString("result", result.replace(" · 你 将扣除 ", " · 房主将扣除 "));
            n.putBoolean("won", won); n.putInt("remaining", (int)Math.max(0, (deadline - time(player) + 19) / 20));
            n.putDouble("x", center.x); n.putDouble("y", center.y); n.putDouble("z", center.z);
            n.putIntArray("bottom", round.bottom());
            if (round.target() != null) n.putIntArray("target", round.shown(round.leader()));
            for (int i = 0; i < 3; i++) {
                n.putString("name" + i, people[i].getName().getString()); n.putInt("entity" + i, people[i].getId());
                n.putInt("count" + i, round.count(i)); n.putBoolean("pass" + i, round.passed(i));
                n.putIntArray("shown" + i, round.shown(i));
            }
            return n;
        }
        void sync(boolean open) {
            CompoundTag publicData = state();
            if (table != null && !table.isRemoved()) table.publicState(publicData);
            syncPlayer(player, 0, open, publicData);
            if (rival instanceof ServerPlayer opponent) syncPlayer(opponent, 1, open, publicData);
            if (people[2] instanceof ServerPlayer friend) syncPlayer(friend, 2, open, publicData);
        }

        void syncPlayer(ServerPlayer viewer, int seat, boolean open, CompoundTag publicData) {
            if (viewer.hasDisconnected()) return;
            CompoundTag data = publicData.copy();
            data.putBoolean("open", open);
            data.putInt("viewer", seat);
            PropBurst burst = PROP_BURSTS.get(viewer.getUUID());
            long now = time(viewer);
            if (burst != null && burst.used(now) > 0) {
                data.putInt("propBurst", burst.used(now));
                data.putLong("propResetAt", level.getGameTime() + (burst.resetAt() - now));
            }
            if (phase != MatchPhase.SHUFFLE) data.putIntArray("hand", round.hand(seat));
            if (seat == 0) {
                data.putString("message", message);
                data.putString("result", result);
            } else if (seat == 1) {
                if (phase == MatchPhase.AUCTION && auction.step() == LandlordAuction.Step.BOSS_RESPONSE) {
                    data.putString("message", auction.candidate() == 0 ? "房主叫了地主，你要抢吗？" : "房主不叫，轮到你叫地主");
                }
                if (phase == MatchPhase.RESULT) data.putBoolean("won", !won);
            } else {
                if (phase == MatchPhase.AUCTION || phase == MatchPhase.STAKE) data.putString("message", "等待房主与 " + rivalName() + " 决定地主和赌注");
                else if (phase == MatchPhase.PLAY && message.startsWith("你是地主")) data.putString("message", "房主成为地主，先出牌");
                if (phase == MatchPhase.RESULT) {
                    data.putBoolean("won", friendWon());
                    data.putString("result", flipAt >= 0 ? result : friendResult());
                }
            }
            Packets.send(viewer, data);
        }

        private void endClient(ServerPlayer viewer) {
            if (viewer.hasDisconnected()) return;
            CompoundTag end = new CompoundTag();
            end.putUUID("match", id);
            Packets.send(viewer, end);
        }

        void close(boolean settle) {
            if (closed) return;
            closed = true; MATCHES.remove(player.getUUID());
            for (LivingEntity e : people) PARTICIPANTS.remove(e.getUUID());
            if (escrowReserved && rival instanceof ServerPlayer opponent) {
                DuelEscrow.resolve(player, opponent, phase == MatchPhase.RESULT ? won ? player : opponent : null);
            }
            for (LivingEntity e : people) restore(e);
            if (temporary && !people[2].isRemoved()) people[2].discard();
            for (SeatEntity seat : seats) seat.discard();
            if (table != null) table.discard();
            if (phase == MatchPhase.RESULT) {
                if (duel() && duelStake == DuelStake.HEALTH) {
                    applyWager(won ? rival : player, won ? player : rival, 1);
                } else if (!duel() && (settle || flipAt >= 0)) {
                    clearNearbyHostiles();
                    if (settle) applyWager(won ? rival : player, won ? player : rival, multiplier);
                }
            }
            endClient(player);
            if (rival instanceof ServerPlayer opponent) endClient(opponent);
            if (people[2] instanceof ServerPlayer friend) endClient(friend);
        }

        private void clearNearbyHostiles() {
            final int radius = 10;
            for (LivingEntity person : people) {
                if (!(person instanceof ServerPlayer p) || p.serverLevel() != level) continue;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius),
                        e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(p) <= radius * radius
                                && e != rival && e != people[2] && !e.getType().is(BOSSES) && !e.hasCustomName() && !isPlaying(e))) {
                    mob.discard();
                }
            }
        }
        void sound(SoundEvent event, float pitch) {
            level.playSound(null, BlockPos.containing(center), event, SoundSource.PLAYERS, .7F, pitch);
        }
    }

    private static String wagerDamage(LivingEntity loser, int multiplier) {
        return String.format(Locale.ROOT, "%.2f", Math.min(loser.getHealth(), WagerMath.stake(loser.getMaxHealth(), multiplier)));
    }

    /** Exact max-health wager: armor, resistance and absorption do not discount the agreed stake. */
    private static void applyWager(LivingEntity target, LivingEntity attacker, int multiplier) {
        if (!target.isAlive()) return;
        float amount = WagerMath.stake(target.getMaxHealth(), multiplier);
        float health = WagerMath.remaining(target.getHealth(), target.getMaxHealth(), multiplier);
        if (attacker instanceof ServerPlayer p) target.setLastHurtByPlayer(p);
        if (health > 0) {
            target.setHealth(health); target.hurtTime = 10; target.hurtMarked = true;
            target.level().broadcastEntityEvent(target, (byte)2);
        } else {
            var holder = target.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(WAGER);
            DamageSource source = new DamageSource(holder, attacker);
            if (target instanceof EnderDragon dragon) dragon.hurt(dragon.head, source, Math.max(amount, target.getMaxHealth() * 10));
            else {
                target.hurt(source, Math.max(amount, target.getMaxHealth() * 10));
                if (target.isAlive()) { target.setHealth(0); target.die(source); }
            }
        }
    }
}
