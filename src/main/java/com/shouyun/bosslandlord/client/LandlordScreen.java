package com.shouyun.bosslandlord.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.CantBeatItLandlordClient;
import com.shouyun.bosslandlord.game.*;
import com.shouyun.bosslandlord.network.Packets;
import com.shouyun.bosslandlord.world.DuelStake;
import com.shouyun.bosslandlord.world.MatchSounds;
import com.shouyun.bosslandlord.world.SeatEntity;
import com.shouyun.bosslandlord.world.TableEntity;
import com.shouyun.bosslandlord.world.TableProp;
import com.shouyun.bosslandlord.world.TableSeat;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class LandlordScreen extends Screen {
    private static final int WIDTH = 640, HEIGHT = 360, CENTER = WIDTH / 2;
    private static final int HEADER_Y = 9, HEADER_HEIGHT = 26, SEAT_WIDTH = 120, EDGE = 14;
    private static final int BOTTOM_CARD_Y = HEADER_Y + 4, BOTTOM_CARD_HEIGHT = 18;
    private static final int HAND_Y = 267, CARD_WIDTH = 38, CARD_HEIGHT = 57;
    private static final int CONTROL_Y = 333, CONTROL_HEIGHT = 18;
    private static final int VIEW_FIRST_X = 385, VIEW_THIRD_X = 440, VIEW_WIDTH = 51;
    private static final int GOLD = 0xFFF4CC86, TEXT = 0xFFF8EDD5, MUTED = 0xFFBEAF94;
    private static final int INK = 0xFF2A211A, PAPER = 0xF7F1DFC0, GARNET = 0xFF912F39, GREEN = 0xFF265046;
    private static final int PANEL = 0xDF17251F, TRIM = 0xFFA48657;
    private static final int[] STAKE_X = {197, 275, 353}, STAKE_WIDTH = {72, 72, 90};
    private static final ResourceLocation DEALER_RAIL = ResourceLocation.fromNamespaceAndPath(
            CantBeatItLandlord.MODID, "textures/gui/dealer_rail.png");
    private static boolean thirdPerson = true;

    private CompoundTag state;
    private final int viewer, watchedTable;
    private MatchPhase phase;
    private LandlordAuction.Step auction;
    private PlayEffect effect;
    private int[] hand;
    private final Set<Integer> selected = new HashSet<>();
    private final Set<Integer> dragged = new HashSet<>();
    private int hintIndex, sentRevision = -1;
    private long sentAt, concedeUntil;
    private boolean painting, paintValue;
    private boolean propsOpen;
    private int propTarget;
    private String localMessage = "";
    private Entity previousCamera;
    private CameraType previousType;
    private ArmorStand tableCamera;

    public LandlordScreen(CompoundTag state) {
        super(Component.literal("打不过就斗地主"));
        viewer = state.getBoolean("watch") ? -1 : state.getInt("viewer");
        propTarget = (viewer + 1) % 3;
        watchedTable = state.getInt("table");
        readState(state);
    }

    private boolean watching() { return viewer < 0; }
    private boolean duel() { return state.getBoolean("duel"); }
    private DuelStake duelStake() {
        DuelStake stake = DuelStake.byIndex(state.getInt("duelStake"));
        return stake == null ? DuelStake.NONE : stake;
    }

    boolean matches(CompoundTag next) {
        return !watching() && next.getUUID("match").equals(state.getUUID("match"));
    }

    @Override public void tick() {
        if (!watching() || minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getEntity(watchedTable) instanceof TableEntity table)
                || table.isRemoved() || minecraft.player.distanceToSqr(table) > 100) {
            onClose();
            return;
        }
        CompoundTag publicState = table.publicState();
        if (publicState.hasUUID("match") && publicState.getUUID("match").equals(state.getUUID("match"))) update(publicState);
    }

    public void update(CompoundTag next) {
        if (state.getInt("revision") != next.getInt("revision")) {
            selected.clear();
            hintIndex = 0;
            localMessage = "";
            sentRevision = -1;
            painting = false;
        } else if (!state.getString("message").equals(next.getString("message"))) {
            sentRevision = -1;
        }
        readState(next);
    }

    private void readState(CompoundTag next) {
        state = next;
        phase = MatchPhase.valueOf(next.getString("phase"));
        auction = LandlordAuction.Step.valueOf(next.getString("auction"));
        effect = PlayEffect.valueOf(next.getString("effect"));
        hand = next.getIntArray("hand");
    }

    @Override protected void init() {
        if (minecraft.level == null || minecraft.player == null || previousType != null) return;
        previousCamera = minecraft.getCameraEntity();
        previousType = minecraft.options.getCameraType();
        Vec3 center = new Vec3(state.getDouble("x"), state.getDouble("y"), state.getDouble("z"));
        float angle = viewer == 1 ? -Mth.PI / 2F : 0F;
        Vec3 focus = center.add(new Vec3(0, 1.04, .10).yRot(angle));
        Vec3 eye = center.add(new Vec3(0, 3.7, 2.21).yRot(angle));
        tableCamera = new ArmorStand(minecraft.level, eye.x, eye.y, eye.z);
        for (Vec3 offset : List.of(new Vec3(0, 4.05, 4.95), new Vec3(0, 3.85, 3.85),
                new Vec3(.95, 3.65, 3.15), new Vec3(-.95, 3.65, 3.15))) {
            Vec3 candidate = center.add(offset.yRot(angle));
            ClipContext clip = new ClipContext(candidate, focus, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tableCamera);
            if (minecraft.level.clip(clip).getType() == HitResult.Type.MISS) {
                eye = candidate;
                break;
            }
        }
        Vec3 forward = focus.subtract(eye);
        float yaw = (float)Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(forward.y, forward.horizontalDistance()));
        tableCamera.moveTo(eye.x, eye.y - tableCamera.getEyeHeight(), eye.z, yaw, pitch);
        tableCamera.yRotO = yaw;
        tableCamera.xRotO = pitch;
        tableCamera.yHeadRot = tableCamera.yHeadRotO = yaw;
        tableCamera.xo = tableCamera.getX();
        tableCamera.yo = tableCamera.getY();
        tableCamera.zo = tableCamera.getZ();
        changeView(watching() || thirdPerson);
    }

    private void changeView(boolean outside) {
        if (tableCamera == null || minecraft.player == null) return;
        if (!watching()) thirdPerson = outside;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        if (outside) {
            minecraft.setCameraEntity(tableCamera);
        } else {
            var player = minecraft.player;
            if (player.getVehicle() instanceof SeatEntity) {
                float yaw = TableSeat.at(viewer).yaw;
                player.setYRot(yaw);
                player.yRotO = yaw;
                player.setXRot(13F);
                player.xRotO = 13F;
            }
            minecraft.setCameraEntity(player);
        }
    }

    @Override public void removed() {
        if (previousType == null) return;
        Entity current = minecraft.getCameraEntity();
        if (current == tableCamera || current == minecraft.player) {
            Entity restore = previousCamera == null || previousCamera.isRemoved() ? minecraft.player : previousCamera;
            minecraft.setCameraEntity(restore);
        }
        minecraft.options.setCameraType(previousType);
        previousType = null;
        tableCamera = null;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) {}

    private float scale() { return Math.min(width / (float)WIDTH, height / (float)HEIGHT); }
    private float left() { return (width - WIDTH * scale()) / 2; }
    private float bottomOffset() { return height / scale() - HEIGHT; }
    private float localX(double x) { return (float)(x - left()) / scale(); }
    private float localY(double y) { return (float)y / scale(); }
    private float age(String timestamp, float tick) {
        return minecraft.level == null ? 0 : (float)(minecraft.level.getGameTime() - state.getLong(timestamp)) + tick;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        float x = localX(mouseX), y = localY(mouseY);
        float bottomOffset = bottomOffset();
        g.pose().pushPose();
        g.pose().translate(left(), 0, 0);
        g.pose().scale(scale(), scale(), 1);

        participant(g, viewer == 1 ? TableSeat.GUEST : TableSeat.BOSS, EDGE, false);
        participant(g, viewer > 0 ? TableSeat.PLAYER : TableSeat.GUEST, WIDTH - EDGE - SEAT_WIDTH, true);
        drawWager(g);
        drawBottomCards(g);
        if (watching()) {
            panel(g, VIEW_FIRST_X, HEADER_Y, VIEW_WIDTH * 2 + 4, HEADER_HEIGHT, TRIM);
            g.drawCenteredString(font, "观战中", VIEW_FIRST_X + VIEW_WIDTH + 2, HEADER_Y + 9, GOLD);
        } else {
            button(g, "第一人称", VIEW_FIRST_X, HEADER_Y, VIEW_WIDTH, HEADER_HEIGHT, x, y, true, !thirdPerson);
            button(g, "第三人称", VIEW_THIRD_X, HEADER_Y, VIEW_WIDTH, HEADER_HEIGHT, x, y, true, thirdPerson);
        }

        g.pose().pushPose();
        g.pose().translate(0, bottomOffset / 2, 0);
        drawLastPlay(g, tick);
        float moveAge = age("lastMoveAt", tick);
        boolean animatingCombo = effect != PlayEffect.NONE && moveAge < MatchPhase.COMBO_TICKS;
        ComboAnimation.draw(g, font, effect, state.getBoolean("blastedPlane"), moveAge, CENTER);
        if (phase == MatchPhase.RESULT && !animatingCombo) drawResult(g);
        g.pose().popPose();

        g.pose().translate(0, bottomOffset, 0);
        y -= bottomOffset;
        if (phase != MatchPhase.RESULT && !animatingCombo) drawStatus(g, x, y, tick);
        if (!watching()) {
            drawRail(g);
            drawHand(g, x, y, tick, bottomOffset);
            if (phase != MatchPhase.SHUFFLE && phase != MatchPhase.RESULT) {
                String playerRole = role(viewer);
                g.drawString(font, playerRole, 119, CONTROL_Y + 5, TEXT, false);
                g.drawString(font, hand.length + " 张", 125 + font.width(playerRole), CONTROL_Y + 5, GOLD, false);
            }
        }

        for (Control control : controls()) {
            button(g, control.text(), control.x(), CONTROL_Y, control.width(), CONTROL_HEIGHT, x, y,
                    control.enabled(), control.primary());
        }
        g.pose().popPose();
    }

    private void drawWager(GuiGraphics g) {
        int x = 167;
        panel(g, x, HEADER_Y, 64, HEADER_HEIGHT, TRIM);
        if (duel()) {
            DuelStake stake = duelStake();
            g.drawCenteredString(font, stake == DuelStake.NONE ? "好友对决" : "赌" + stake.title, x + 32, HEADER_Y + 9, GOLD);
            return;
        }
        g.drawString(font, "赌注", x + 9, HEADER_Y + 9, TEXT, false);
        scaledCenter(g, "×" + state.getInt("multiplier"), x + 45, HEADER_Y + 7, 1.25F, GOLD);
    }

    private String role(int seat) {
        int landlord = state.getInt("landlord");
        if (landlord < 0) return state.getInt("candidate") == seat ? "地主候选" : seat == 2 ? "农民" : "待定";
        return seat == landlord ? "地主" : "农民";
    }

    private void participant(GuiGraphics g, TableSeat placement, int x, boolean right) {
        int seat = placement.ordinal();
        boolean current = phase == MatchPhase.PLAY && state.getInt("turn") == seat;
        boolean landlord = state.getInt("landlord") == seat;
        int accent = current ? GOLD : landlord ? 0xFFD69A85 : TRIM;
        panel(g, x, HEADER_Y, SEAT_WIDTH, HEADER_HEIGHT, accent);
        int markX = right ? x + SEAT_WIDTH - 4 : x + 3;
        g.fill(markX, HEADER_Y + 5, markX + 1, HEADER_Y + HEADER_HEIGHT - 5, accent);

        boolean showCount = phase != MatchPhase.SHUFFLE;
        int textEdge = right ? x + SEAT_WIDTH - 9 : x + 9;
        int textWidth = SEAT_WIDTH - (showCount ? 43 : 18);
        String name = font.plainSubstrByWidth(state.getString("name" + seat), textWidth);
        String role = role(seat);
        if (current) role += " · 出牌";
        else if (state.getBoolean("pass" + seat)) role += " · 不出";
        else if (placement == TableSeat.BOSS && auction == LandlordAuction.Step.BOSS_RESPONSE) role += " · 叫牌";
        g.drawString(font, name, right ? textEdge - font.width(name) : textEdge, HEADER_Y + 4, TEXT, false);
        g.drawString(font, role, right ? textEdge - font.width(role) : textEdge, HEADER_Y + 14,
                current || landlord ? accent : MUTED, false);
        if (showCount) {
            int badgeX = right ? x + 5 : x + SEAT_WIDTH - 32;
            clipped(g, badgeX, HEADER_Y + 5, 27, 16, current ? 0x604E3E28 : 0x800F1713);
            String count = state.getInt("count" + seat) + "张";
            g.drawString(font, count, badgeX + (27 - font.width(count)) / 2, HEADER_Y + 9, GOLD, false);
        }
    }

    private void drawBottomCards(GuiGraphics g) {
        int[] bottom = state.getIntArray("bottom");
        if (phase == MatchPhase.SHUFFLE) return;
        panel(g, CENTER - 41, HEADER_Y, 82, HEADER_HEIGHT, TRIM);
        g.drawString(font, "底牌", CENTER - 34, HEADER_Y + 9, TEXT, false);
        for (int i = 0; i < 3; i++) {
            CardArt.draw(g, bottom.length == 3 ? bottom[i] : CardArt.BACK, CENTER - 8 + i * 16,
                    BOTTOM_CARD_Y, 12, BOTTOM_CARD_HEIGHT);
        }
    }

    private void drawLastPlay(GuiGraphics g, float tick) {
        int[] cards = state.getIntArray("target").clone();
        Combination combo = Combination.parse(cards);
        if (combo == null) return;
        int leader = state.getInt("leader");
        String name = leader == viewer ? "你" : font.plainSubstrByWidth(state.getString("name" + leader), 96);
        String caption = name + "  ·  " + combo.kind().title;
        g.drawCenteredString(font, caption, CENTER, 68, GOLD);
        Arrays.sort(cards);
        int step = Math.min(16, 215 / Math.max(1, cards.length));
        int start = CENTER - ((cards.length - 1) * step + 22) / 2;
        float t = Mth.clamp(age("lastMoveAt", tick) / MatchPhase.CARD_FLIGHT_TICKS, 0, 1);
        int shift = state.getInt("lastSeat") == leader ? Math.round(12 * (1 - t) * (1 - t)) : 0;
        for (int i = 0; i < cards.length; i++) CardArt.draw(g, cards[i], start + i * step, 81 + shift, 22, 33);
    }

    private void drawResult(GuiGraphics g) {
        if (state.contains("flipAt")) {
            g.drawCenteredString(font, state.getString("result"), CENTER, 223, GOLD);
            return;
        }
        boolean won = state.getBoolean("won");
        String result = state.getString("result");
        if (viewer == 0 && !duel() && !won) result += "\n掀桌重来可取消本局扣血";
        if (!watching() && duel() && duelStake() != DuelStake.NONE && !won) {
            result += "\n重赛需先结算本局赌注";
        }
        var lines = font.split(Component.literal(result), 246);
        clipped(g, 185, 151, 270, 42 + lines.size() * 12, PAPER);
        int color = won || watching() ? GREEN : GARNET;
        g.hLine(197, 442, 156, color);
        scaledCenter(g, watching() ? "牌局结束" : won ? "你赢了" : "你输了", CENTER, 165, 1.35F, color);
        int lineY = 183;
        for (var line : lines) {
            g.drawString(font, line, CENTER - font.width(line) / 2, lineY, INK, false);
            lineY += 12;
        }
    }

    private void drawStatus(GuiGraphics g, float mouseX, float mouseY, float tick) {
        String msg = phase == MatchPhase.SHUFFLE
                ? age("phaseAt", tick) < MatchPhase.RIFFLE_TICKS ? "洗牌中" : "发牌中"
                : trayMessage(mouseX, mouseY);
        msg = font.plainSubstrByWidth(msg, 350);
        String timer = timerLabel();
        int timerWidth = timer.isEmpty() ? 0 : font.width(timer) + 15;
        int content = font.width(msg) + timerWidth;
        int x = CENTER - content / 2;
        panel(g, x - 9, 232, content + 18, 19, TRIM);
        g.drawString(font, msg, x, 238, TEXT, false);
        if (!timer.isEmpty()) {
            g.vLine(x + font.width(msg) + 6, 236, 246, TRIM);
            g.drawString(font, timer, x + font.width(msg) + 14, 238, GOLD, false);
        }
    }

    private static void drawRail(GuiGraphics g) {
        // Draw the nonempty band of the generated transparent sprite.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(DEALER_RAIL, 75, 301, 490, 57, 4F, 234F, 2164, 256, 2172, 724);
        RenderSystem.disableBlend();
    }

    private void drawHand(GuiGraphics g, float mouseX, float mouseY, float tick, float bottomOffset) {
        if (phase == MatchPhase.SHUFFLE) return;
        int hover = hitCard(mouseX, mouseY);
        float phaseAge = age("phaseAt", tick);
        int[] bottom = state.getIntArray("bottom");
        for (int i = 0; i < hand.length; i++) {
            int card = hand[i];
            int x = cardX(i), y = cardY(i);
            boolean picked = selected.contains(card);
            float delay = i * .44F;
            if (phase == MatchPhase.AUCTION && phaseAge < 17) {
                float t = Mth.clamp((phaseAge - delay) / 8, 0, 1);
                if (t == 0) continue;
                y += Math.round((1 - t) * 24);
                if (t < .5F) card = CardArt.BACK;
            } else if (phase == MatchPhase.STAKE && phaseAge < 14 && includes(bottom, card)) {
                float t = Mth.clamp(phaseAge / 14, 0, 1);
                x = Math.round(Mth.lerp(t, CENTER, x));
                y = Math.round(Mth.lerp(t, BOTTOM_CARD_Y - bottomOffset, y));
            }
            clipped(g, x + 2, y + 3, CARD_WIDTH, CARD_HEIGHT, 0x77000000);
            if (picked || hand[i] == hover) {
                clipped(g, x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, picked ? GOLD : TEXT);
            }
            CardArt.draw(g, card, x, y, CARD_WIDTH, CARD_HEIGHT);
            if (picked) g.hLine(x + 3, x + CARD_WIDTH - 4, y + CARD_HEIGHT - 3, 0xFFAD7134);
        }
    }

    private String trayMessage(float mouseX, float mouseY) {
        if (propsOpen && canUseProps()) {
            int count = propBurst();
            if (count >= PropBurst.LIMIT) {
                return "道具冷却 · " + Mth.ceil(-age("propResetAt", 0) / 20F) + " 秒";
            }
            return "点击名字切换对象 · 连用 " + count + " / " + PropBurst.LIMIT;
        }
        if (!localMessage.isEmpty()) return localMessage;
        if (phase != MatchPhase.STAKE || viewer != 0) return state.getString("message");
        int option = 0;
        for (int i = 0; i < STAKE_X.length; i++) {
            if (inside(mouseX, mouseY, STAKE_X[i], CONTROL_Y, STAKE_WIDTH[i], CONTROL_HEIGHT)) option = i + 1;
        }
        if (option == 0) return "×1 扣 1/3   ·   ×2 扣 2/3   ·   ×3 扣全部最大生命";
        return String.format(Locale.ROOT, "赢扣对手 %.1f   ·   输扣自己 %.1f",
                WagerMath.stake(state.getFloat("bossMax"), option),
                WagerMath.stake(state.getFloat("playerMax"), option));
    }

    private String timerLabel() {
        boolean waitingForPlayer = viewer == 0 && (phase == MatchPhase.STAKE
                || phase == MatchPhase.AUCTION && auction != LandlordAuction.Step.BOSS_RESPONSE)
                || viewer == 1 && phase == MatchPhase.AUCTION && auction == LandlordAuction.Step.BOSS_RESPONSE
                || viewer >= 0 && phase == MatchPhase.PLAY && state.getInt("turn") == viewer;
        if (!waitingForPlayer) return "";
        return state.getInt("remaining") + " 秒";
    }

    private record Control(String text, int x, int width, boolean enabled, boolean primary, Runnable click) {}

    private List<Control> controls() {
        if (watching()) return List.of(new Control("退出观战", 268, 104, true, false, this::onClose));
        var controls = new ArrayList<Control>();
        boolean ready = ready();
        if (propsOpen && canUseProps()) return propControls();
        switch (phase) {
            case AUCTION -> {
                boolean rivalDecision = auction == LandlordAuction.Step.BOSS_RESPONSE;
                if (viewer == 2 || viewer == 1 && !rivalDecision) break;
                boolean rob = auction == LandlordAuction.Step.PLAYER_ROB || viewer == 1 && state.getInt("candidate") == 0;
                boolean enabled = ready && (viewer == 0 ? !rivalDecision : rivalDecision);
                controls.add(new Control(rob ? "抢地主" : "叫地主", 226, 90, enabled, true, () -> send(Packets.CLAIM)));
                controls.add(new Control(rob ? "不抢" : "不叫", 324, 90, enabled, false, () -> send(Packets.DECLINE)));
            }
            case STAKE -> {
                if (viewer != 0) break;
                controls.add(new Control("不加倍 ×1", STAKE_X[0], STAKE_WIDTH[0], ready, false, () -> send(Packets.NORMAL)));
                controls.add(new Control("加倍 ×2", STAKE_X[1], STAKE_WIDTH[1], ready, true, () -> send(Packets.DOUBLE)));
                controls.add(new Control("超级加倍 ×3", STAKE_X[2], STAKE_WIDTH[2], ready, true, () -> send(Packets.SUPER)));
            }
            case PLAY -> {
                boolean mine = myTurn();
                controls.add(new Control("出牌  Enter", 192, 75, mine && !selected.isEmpty(), true, () -> play(false)));
                controls.add(new Control("不出  空格", 272, 69, mine && state.contains("target"), false, () -> play(true)));
                controls.add(new Control("提示  H", 346, 55, mine, false, this::hint));
                controls.add(new Control("清空", 406, 42, !selected.isEmpty(), false, this::clear));
            }
            case RESULT -> {
                if (state.contains("flipAt")) break;
                boolean canRematch = viewer != 2;
                controls.add(new Control("结算离桌", canRematch ? 213 : 268, 104, ready, !canRematch,
                        () -> send(Packets.LEAVE)));
                if (canRematch) controls.add(new Control(state.getBoolean("won") ? "再来一局" : "掀桌重来",
                        324, 104, ready, true, () -> send(Packets.REMATCH)));
            }
            case SHUFFLE -> {}
        }
        if (phase != MatchPhase.RESULT) {
            String title = viewer == 2 ? "离席" : Util.getMillis() < concedeUntil ? "确认认输" : "认输";
            controls.add(new Control(title, 474, 48, true, false, this::concede));
        }
        if (canUseProps()) controls.add(new Control("道具", 531, 49, true, false, () -> propsOpen = true));
        return controls;
    }

    private boolean canUseProps() { return phase != MatchPhase.SHUFFLE && phase != MatchPhase.RESULT; }

    private int propBurst() {
        return state.contains("propResetAt") && age("propResetAt", 0) < 0F ? state.getInt("propBurst") : 0;
    }

    private List<Control> propControls() {
        List<Control> controls = new ArrayList<>();
        String target = font.plainSubstrByWidth(state.getString("name" + propTarget), 57);
        controls.add(new Control(target + " ↻", 190, 81, true, false, this::cyclePropTarget));
        boolean free = !state.contains("prop") && propBurst() < PropBurst.LIMIT;
        for (TableProp prop : TableProp.values()) {
            controls.add(new Control(prop.title, 276 + prop.ordinal() * 60, 55, free,
                    false, () -> Packets.request(Packets.TABLE_PROP, state.getUUID("match"),
                            state.getInt("revision"), new int[]{propTarget, prop.ordinal()})));
        }
        controls.add(new Control("收起", 531, 49, true, false, () -> propsOpen = false));
        return controls;
    }

    private void cyclePropTarget() {
        propTarget = (propTarget + 1) % 3;
        if (propTarget == viewer) propTarget = (propTarget + 1) % 3;
    }

    private static void clipped(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 3, y, x + w - 3, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        g.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int trim) {
        clipped(g, x, y + 1, w, h, 0x5010140F);
        clipped(g, x, y, w, h, PANEL);
        g.hLine(x + 4, x + w - 5, y + h - 1, trim);
    }

    private void scaledCenter(GuiGraphics g, String text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    private void button(GuiGraphics g, String text, int x, int y, int w, int h,
                        float mouseX, float mouseY, boolean enabled, boolean primary) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = !enabled ? 0xBF222C25
                : primary ? (hover ? 0xFFF3D49B : 0xFFDAC08B)
                : (hover ? 0xF43A5345 : PANEL);
        clipped(g, x, y + 1, w, h, 0x5010140F);
        clipped(g, x, y, w, h, bg);
        if (enabled) {
            g.hLine(x + 4, x + w - 5, y + h - 1, primary ? 0xFF9D6D39 : TRIM);
        }
        int fg = !enabled ? MUTED : primary ? INK : TEXT;
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, fg, false);
    }

    private static boolean inside(float x, float y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static boolean includes(int[] cards, int card) {
        for (int c : cards) if (c == card) return true;
        return false;
    }

    private int step() { return Math.min(31, 412 / Math.max(1, hand.length - 1)); }
    private int cardX(int i) { return CENTER - ((hand.length - 1) * step() + CARD_WIDTH) / 2 + i * step(); }
    private int cardY(int index) {
        float offset = hand.length > 1 ? index * 2F / (hand.length - 1) - 1 : 0;
        return HAND_Y + Math.round(3 * offset * offset) - (selected.contains(hand[index]) ? 11 : 0);
    }
    private int hitCard(float x, float y) {
        for (int i = hand.length - 1; i >= 0; i--) {
            if (inside(x, y, cardX(i), cardY(i), CARD_WIDTH, CARD_HEIGHT)) return hand[i];
        }
        return -1;
    }

    private boolean ready() {
        return sentRevision != state.getInt("revision") || Util.getMillis() - sentAt > 1400;
    }

    private boolean myTurn() {
        if (watching() || phase != MatchPhase.PLAY || state.getInt("turn") != viewer || !ready()) return false;
        int lock = effect == PlayEffect.NONE ? MatchPhase.CARD_FLIGHT_TICKS : MatchPhase.COMBO_TICKS;
        return state.getIntArray("lastCards").length == 0 || age("lastMoveAt", 0) >= lock;
    }

    private void send(int action) { send(action, new int[0]); }
    private void send(int action, int[] cards) {
        if (!ready() && action != Packets.LEAVE) return;
        sentRevision = state.getInt("revision");
        sentAt = Util.getMillis();
        Packets.request(action, state.getUUID("match"), sentRevision, cards);
    }

    private void play(boolean pass) {
        if (!myTurn()) return;
        if (pass) {
            if (state.contains("target")) send(Packets.PASS);
            else localMessage = "你先出牌，不能不出";
            return;
        }
        int[] cards = selected.stream().mapToInt(Integer::intValue).toArray();
        Combination combo = Combination.parse(cards);
        if (combo == null) {
            localMessage = "请先选出一种合法牌型";
        } else if (!combo.beats(Combination.parse(state.getIntArray("target")))) {
            localMessage = "这手牌压不过上一手";
        } else {
            send(Packets.PLAY, cards);
        }
    }

    private void hint() {
        if (!myTurn()) return;
        List<Moves.Move> moves = Moves.legal(hand, Combination.parse(state.getIntArray("target")));
        selected.clear();
        if (moves.isEmpty()) {
            localMessage = "没有能压过的牌";
            return;
        }
        moves.sort(Comparator.comparingDouble(m -> (m.combination().explosive() ? 100 : 0)
                - m.cards().length * 3 + m.combination().high() * .4));
        Moves.Move move = moves.get(hintIndex++ % moves.size());
        for (int card : move.cards()) selected.add(card);
        localMessage = "提示  " + move.combination().kind().title + "  ·  再按 H 换一种";
    }

    private void clear() {
        selected.clear();
        localMessage = "";
    }

    private void concede() {
        if (viewer == 2) {
            send(Packets.LEAVE);
            return;
        }
        long now = Util.getMillis();
        if (now < concedeUntil) {
            send(Packets.LEAVE);
        } else {
            concedeUntil = now + 4000;
            int stake = state.getInt("multiplier");
            if (duel()) {
                DuelStake agreed = duelStake();
                localMessage = agreed.isItem() ? "再次点击认输将交出 1 个" + agreed.title
                        : agreed == DuelStake.HEALTH ? "再次点击认输将扣除 1/3 最大生命" : "再次点击确认认输";
            } else localMessage = "再次点击认输将按 ×" + stake + " 结算";
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float x = localX(mouseX), y = localY(mouseY);
        if (button == 1) { clear(); return true; }
        if (button != 0) return false;
        if (!watching()) {
            if (inside(x, y, VIEW_FIRST_X, HEADER_Y, VIEW_WIDTH, HEADER_HEIGHT)) { changeView(false); return true; }
            if (inside(x, y, VIEW_THIRD_X, HEADER_Y, VIEW_WIDTH, HEADER_HEIGHT)) { changeView(true); return true; }
        }
        y -= bottomOffset();
        for (Control control : controls()) {
            if (inside(x, y, control.x(), CONTROL_Y, control.width(), CONTROL_HEIGHT)) {
                if (control.enabled()) control.click().run();
                return true;
            }
        }
        if (watching() || phase != MatchPhase.PLAY) return false;
        int card = hitCard(x, y);
        if (card < 0) return false;
        paintValue = !selected.contains(card);
        painting = true;
        dragged.clear();
        paint(card);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(MatchSounds.PLAY.get(), 1.44F, .22F));
        return true;
    }

    private void paint(int card) {
        if (!dragged.add(card)) return;
        if (paintValue) selected.add(card);
        else selected.remove(card);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!painting || button != 0) return false;
        int card = hitCard(localX(x), localY(y) - bottomOffset());
        if (card >= 0) paint(card);
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        painting = false;
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (CantBeatItLandlordClient.OPEN.matches(key, scan)) { onClose(); return true; }
        if (!watching() && (key == GLFW.GLFW_KEY_V || key == GLFW.GLFW_KEY_F5)) { changeView(!thirdPerson); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { play(false); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { play(true); return true; }
        if (key == GLFW.GLFW_KEY_H) { hint(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
