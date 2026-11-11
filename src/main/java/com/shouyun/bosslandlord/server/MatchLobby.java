package com.shouyun.bosslandlord.server;

import com.shouyun.bosslandlord.Config;
import com.shouyun.bosslandlord.network.Packets;
import com.shouyun.bosslandlord.world.DuelStake;
import com.shouyun.bosslandlord.world.GuestChoice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

final class MatchLobby {
    private static final int FRIEND_RANGE = 32, INVITE_TICKS = 20 * 25, LOBBY_TICKS = 20 * 120;
    private static final Map<UUID, MatchLobby> BY_HOST = new HashMap<>();
    private static final Map<UUID, MatchLobby> BY_INVITEE = new HashMap<>();

    private final UUID id = UUID.randomUUID();
    private final ServerPlayer host;
    private final Mob boss;
    private final int preferredOpponent;
    private final int preferredThird;
    private final DuelStake preferredStake;
    private final long expires;
    private enum InvitationMode { TEAM, DUEL, THREE_PLAYERS }
    private record Invitation(ServerPlayer player, UUID token) {}
    private List<Invitation> invitations = List.of();
    private final Set<UUID> accepted = new HashSet<>();
    private InvitationMode invitationMode;
    private DuelStake invitationStake = DuelStake.NONE;
    private long inviteUntil, lastInviteAt = -100;
    private String status = "";

    private MatchLobby(ServerPlayer host, Mob boss, int preferredOpponent, int preferredThird, DuelStake preferredStake) {
        this.host = host;
        this.boss = boss;
        this.preferredOpponent = preferredOpponent;
        this.preferredThird = preferredThird;
        this.preferredStake = preferredStake;
        this.expires = MatchManager.time(host) + LOBBY_TICKS;
    }

    static void open(ServerPlayer player, Mob boss) {
        open(player, boss, null);
    }

    static void open(ServerPlayer player, Mob boss, ServerPlayer preferredOpponent) {
        open(player, boss, preferredOpponent, null, DuelStake.NONE);
    }

    private static MatchLobby open(ServerPlayer player, Mob boss, ServerPlayer preferredOpponent,
                                   ServerPlayer preferredThird, DuelStake preferredStake) {
        MatchLobby lobby = new MatchLobby(player, boss, preferredOpponent == null ? -1 : preferredOpponent.getId(),
                preferredThird == null ? -1 : preferredThird.getId(), preferredStake);
        BY_HOST.put(player.getUUID(), lobby);
        lobby.sync(true);
        return lobby;
    }

    static void inviteRematch(ServerPlayer player, Mob boss, ServerPlayer other, DuelStake stake) {
        open(player, boss, other, null, stake).invite(boss == null ? InvitationMode.DUEL : InvitationMode.TEAM, stake, other.getId());
    }

    static void inviteThreeRematch(ServerPlayer player, ServerPlayer first, ServerPlayer second) {
        open(player, null, first, second, DuelStake.NONE)
                .invite(InvitationMode.THREE_PLAYERS, DuelStake.NONE, first.getId(), second.getId());
    }

    static boolean reopen(ServerPlayer player) {
        MatchLobby lobby = BY_HOST.get(player.getUUID());
        if (lobby == null) return false;
        lobby.sync(true);
        return true;
    }

    static boolean handle(ServerPlayer host, Packets.Action packet) {
        if ((packet.action() < Packets.PICK_GUEST || packet.action() > Packets.DUEL_PLAYER)
                && packet.action() != Packets.THREE_PLAYERS) return false;
        MatchLobby lobby = BY_HOST.get(host.getUUID());
        if (lobby == null || !lobby.id.equals(packet.match())) return true;
        int[] values = packet.cards();
        if (packet.action() == Packets.CANCEL_LOBBY) lobby.close();
        else if (packet.action() == Packets.DUEL_PLAYER && values.length == 2) {
            DuelStake stake = DuelStake.byIndex(values[1]);
            if (stake != null) lobby.invite(InvitationMode.DUEL, stake, values[0]);
        } else if (packet.action() == Packets.THREE_PLAYERS && values.length == 2 && values[0] != values[1]) {
            lobby.invite(InvitationMode.THREE_PLAYERS, DuelStake.NONE, values);
        } else if (values.length == 1) {
            int value = values[0];
            if (packet.action() == Packets.PICK_GUEST && lobby.boss != null && value >= 0 && value < GuestChoice.values().length) {
                lobby.choose(GuestChoice.byIndex(value));
            } else if (packet.action() == Packets.INVITE_PLAYER && lobby.boss != null) {
                lobby.invite(InvitationMode.TEAM, DuelStake.NONE, value);
            }
        }
        return true;
    }

    static boolean nearbyPlayer(ServerPlayer host, ServerPlayer other) {
        return other != host && other.isAlive() && !other.hasDisconnected() && !other.isSpectator()
                && !other.isPassenger() && !other.isSleeping() && other.serverLevel() == host.serverLevel()
                && other.distanceToSqr(host) <= FRIEND_RANGE * FRIEND_RANGE
                && !MatchManager.isPlaying(other) && !BY_HOST.containsKey(other.getUUID());
    }

    static ServerPlayer aimedOpponent(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(FRIEND_RANGE));
        return player.serverLevel().players().stream()
                .filter(other -> canInvite(player, other) && player.hasLineOfSight(other)
                        && other.getBoundingBox().inflate(.25).clip(eye, end).isPresent())
                .min(java.util.Comparator.comparingDouble(other -> other.distanceToSqr(player))).orElse(null);
    }

    static boolean hasOpponent(ServerPlayer player) {
        return player.serverLevel().players().stream().anyMatch(other -> canInvite(player, other));
    }

    private static boolean canInvite(ServerPlayer host, ServerPlayer other) {
        return nearbyPlayer(host, other) && !BY_INVITEE.containsKey(other.getUUID());
    }

    private void choose(GuestChoice choice) {
        if (MatchManager.startMatch(host, boss, choice, null)) close();
        else update("暂时无法开局，请查看聊天提示后重试。");
    }

    private void invite(InvitationMode mode, DuelStake stake, int... playerIds) {
        long now = MatchManager.time(host);
        if (!invitations.isEmpty() || now - lastInviteAt < 20) return;
        lastInviteAt = now;
        var invites = new ArrayList<Invitation>();
        for (int playerId : playerIds) {
            if (!(host.serverLevel().getEntity(playerId) instanceof ServerPlayer other) || !canInvite(host, other)) {
                update("有玩家暂时无法加入，请重新选择牌友。");
                return;
            }
            invites.add(new Invitation(other, UUID.randomUUID()));
        }
        if (mode == InvitationMode.DUEL && !DuelEscrow.canFund(host, stake)) {
            update("你的背包里需要 1 个" + stake.title + "。");
            return;
        }
        invitations = List.copyOf(invites);
        invitationMode = mode;
        invitationStake = stake;
        inviteUntil = now + INVITE_TICKS;
        for (Invitation invite : invitations) {
            BY_INVITEE.put(invite.player().getUUID(), this);
            sendInvitation(invite);
        }
        update(mode == InvitationMode.THREE_PLAYERS ? "已发出邀请，等待两位好友同意。"
                : "已邀请 " + invitations.getFirst().player().getScoreboardName()
                + (mode == InvitationMode.DUEL ? "（" + stake.title + "）" : "") + "，等待回应。");
    }

    private void sendInvitation(Invitation invite) {
        String command = "/bosslandlord ";
        String message = switch (invitationMode) {
            case TEAM -> " 邀请你和 " + boss.getName().getString() + " 打斗地主。 ";
            case DUEL -> " 邀请你进行好友对决。 " + stakeTerms(invitationStake);
            case THREE_PLAYERS -> " 邀请你参加三人友谊局。两位受邀玩家都接受后开局。 ";
        };
        invite.player().sendSystemMessage(Component.literal("§6[牌桌邀请] §r" + host.getScoreboardName() + message)
                .append(Component.literal("[接受]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command + "accept " + invite.token()))))
                .append("  ")
                .append(Component.literal("[拒绝]").withStyle(s -> s.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command + "decline " + invite.token())))));
    }

    private static String stakeTerms(DuelStake stake) {
        if (stake == DuelStake.HEALTH) return "赌注为生命，败方扣除 1/3 最大生命，当前血量不足时会死亡。 ";
        if (stake.isItem()) return "双方各押 1 个" + stake.title + "，胜者拿走两份赌注。 ";
        return "友谊局，仅计胜负。 ";
    }

    static void respond(ServerPlayer player, UUID id, boolean accept) {
        MatchLobby lobby = BY_INVITEE.get(player.getUUID());
        if (lobby == null || MatchManager.time(player) >= lobby.inviteUntil || lobby.invitations.stream()
                .noneMatch(invite -> invite.player() == player && invite.token().equals(id))) {
            MatchManager.tell(player, "这条牌桌邀请已失效。");
            return;
        }
        if (!accept) {
            lobby.cancelInvitation(player.getScoreboardName() + " 拒绝了邀请，请选择牌友。");
        } else if (lobby.hasUnavailableInvitee()) {
            lobby.cancelInvitation("好友已离开附近或正忙，请选择牌友。");
            MatchManager.tell(player, "目前无法入座，请靠近房主并离开载具。");
        } else if (lobby.accepted.add(player.getUUID())) {
            if (lobby.accepted.size() == lobby.invitations.size()) lobby.startInvitedMatch();
            else {
                lobby.update(player.getScoreboardName() + " 已接受，等待另一位好友。");
                MatchManager.tell(player, "已接受邀请，等待另一位好友。");
            }
        }
    }

    private boolean hasUnavailableInvitee() {
        return invitations.stream().anyMatch(invite -> !nearbyPlayer(host, invite.player()));
    }

    private void startInvitedMatch() {
        ServerPlayer first = invitations.getFirst().player();
        boolean started = switch (invitationMode) {
            case TEAM -> MatchManager.startMatch(host, boss, GuestChoice.NEARBY, first);
            case DUEL -> MatchManager.startDuel(host, first, invitationStake);
            case THREE_PLAYERS -> MatchManager.startThreePlayers(host, first, invitations.get(1).player());
        };
        if (started) close();
        else {
            for (Invitation invite : invitations) MatchManager.tell(invite.player(), "房主暂时无法开局。");
            clearInvitation();
            update("暂时无法开局，请查看聊天提示后重试。");
        }
    }

    static void tick(long now) {
        for (MatchLobby lobby : List.copyOf(BY_HOST.values())) {
            if (lobby.host.hasDisconnected() || !lobby.host.isAlive() || lobby.host.isSpectator()
                    || (lobby.boss != null && (lobby.host.serverLevel() != lobby.boss.level()
                    || !lobby.boss.isAlive() || lobby.boss.isRemoved())) || now >= lobby.expires) {
                lobby.close();
                if (now >= lobby.expires) MatchManager.tell(lobby.host, "选角超时，请重新开局。");
            } else if (!lobby.invitations.isEmpty() && (now >= lobby.inviteUntil || lobby.hasUnavailableInvitee())) {
                lobby.cancelInvitation("好友邀请已失效，请选择牌友或重新邀请。");
            } else if (now % 20 == 0) {
                lobby.sync();
            }
        }
    }

    static void disconnect(ServerPlayer player) {
        MatchLobby owned = BY_HOST.get(player.getUUID());
        if (owned != null) owned.close();
        MatchLobby received = BY_INVITEE.get(player.getUUID());
        if (received != null) {
            received.cancelInvitation("好友已离开，请选择牌友。");
        }
    }

    static void stop() {
        BY_HOST.clear();
        BY_INVITEE.clear();
    }

    private void update(String text) {
        status = text;
        sync();
    }

    private void clearInvitation() {
        for (Invitation invite : invitations) BY_INVITEE.remove(invite.player().getUUID(), this);
        invitations = List.of();
        accepted.clear();
        invitationStake = DuelStake.NONE;
    }

    private void notifyCancelled() {
        for (Invitation invite : invitations) {
            if (accepted.contains(invite.player().getUUID()) && !invite.player().hasDisconnected())
                MatchManager.tell(invite.player(), "三人桌邀请已取消。");
        }
    }

    private void cancelInvitation(String text) {
        notifyCancelled();
        clearInvitation();
        update(text);
    }

    private void close() {
        BY_HOST.remove(host.getUUID(), this);
        if (accepted.size() < invitations.size()) notifyCancelled();
        clearInvitation();
        if (!host.hasDisconnected()) Packets.send(host, state(false));
    }

    private CompoundTag state(boolean active) {
        CompoundTag state = new CompoundTag();
        state.putBoolean("lobby", true);
        state.putBoolean("active", active);
        state.putUUID("match", id);
        return state;
    }

    private void sync() {
        sync(false);
    }

    private void sync(boolean open) {
        if (host.hasDisconnected()) return;
        CompoundTag state = state(true);
        state.putBoolean("open", open);
        state.putBoolean("duel", boss == null);
        state.putInt("preferredOpponent", preferredOpponent);
        state.putInt("preferredThird", preferredThird);
        state.putInt("preferredStake", preferredStake.ordinal());
        if (boss != null) {
            Mob nearby = MatchManager.findNearbyGuest(host, boss);
            state.putBoolean("nearbyAvailable", nearby != null || Config.FILL_VILLAGER.get());
            state.putInt("nearbyEntity", nearby == null ? -1 : nearby.getId());
            state.putString("nearbyName", nearby == null ? "临时村民" : nearby.getName().getString());
            state.putString("boss", boss.getName().getString());
        }
        state.putString("status", status);
        state.putBoolean("waiting", !invitations.isEmpty());
        if (!invitations.isEmpty()) {
            state.putInt("remaining", (int)Math.max(0, (inviteUntil - MatchManager.time(host) + 19) / 20));
            if (invitationMode == InvitationMode.DUEL) state.putInt("pendingDuelStake", invitationStake.ordinal());
        }
        ListTag friends = new ListTag();
        for (ServerPlayer other : host.serverLevel().players()) {
            MatchLobby reservedBy = BY_INVITEE.get(other.getUUID());
            if (!nearbyPlayer(host, other) || (reservedBy != null && reservedBy != this)) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("entity", other.getId());
            entry.putString("name", other.getScoreboardName());
            friends.add(entry);
        }
        state.put("friends", friends);
        Packets.send(host, state);
    }
}
