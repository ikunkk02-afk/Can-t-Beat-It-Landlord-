package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.network.Packets;
import com.shouyun.bosslandlord.world.DuelStake;
import com.shouyun.bosslandlord.world.GuestChoice;
import com.shouyun.bosslandlord.world.GuestEntity;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

final class GuestSelectionScreen extends Screen {
    private static final int WIDTH = 640, HEIGHT = 360;
    private static final int SLOT_X = 30, SLOT_Y = 54, SLOT_W = 139, SLOT_H = 70, SLOT_STEP = 147;
    private static final int DETAILS_X = 40, PREVIEW_X = 467, PREVIEW_TOP = 128, PREVIEW_BOTTOM = 292, PREVIEW_FLOOR = 278;
    private static final int FRIENDS = 3, FRIEND_Y = 244, FRIEND_BUTTON = 23, FRIEND_NEXT_X = 137;
    private static final int BUTTON_Y = 321, BUTTON_H = 28, BACK_W = 72, CONFIRM_X = 454, CONFIRM_W = 149;
    private static final int DUEL_X = 356, DUEL_W = 92;
    private static final int STAKE_Y = 269, STAKE_PREV_X = 111, STAKE_NEXT_X = 243;
    private static final int THREE_X = 246, THREE_Y = 17, THREE_W = 108, THREE_H = 26;
    private static final int TEXT = 0xFFFFECD4, MUTED = 0xFFABBCC3, LINE = 0xFF35464C;
    private static final int[] ACCENTS = {0xFFE6C678, 0xFFFFAE69, 0xFF79E1DC, 0xFFA8C9FC};
    private static final GuestChoice[] CHOICES = GuestChoice.values();
    private static final ResourceLocation STAGE = texture("selection_stage");
    private static final ResourceLocation PORTRAITS = texture("selection_portraits");
    private static final ResourceLocation CONTROLS = texture("selection_controls");
    private CompoundTag state;
    private ListTag friends;
    private DuelStake duelStake;
    private int selection, selectedFriendId = -1;
    private int secondFriendId = -1;
    private boolean threePlayers;
    private GuestEntity characterPreview;
    private LivingEntity villagerPreview;
    private long sentAt = -1000, selectedAt = Util.getMillis();

    GuestSelectionScreen(CompoundTag state) {
        super(Component.literal("选择牌友"));
        update(state);
        DuelStake preferred = DuelStake.byIndex(state.getInt("preferredStake"));
        duelStake = preferred == null ? DuelStake.NONE : preferred;
        threePlayers = state.contains("preferredThird") && state.getInt("preferredThird") >= 0;
        if (duelOnly() || threePlayers || state.getInt("preferredOpponent") >= 0) {
            selection = FRIENDS;
            int fallback = selectedFriendId;
            selectedFriendId = state.getInt("preferredOpponent");
            if (friendIndex() < 0) selectedFriendId = fallback;
            if (threePlayers) secondFriendId = state.getInt("preferredThird");
            updateSecondFriend();
        } else if (!state.getBoolean("nearbyAvailable")) selection = 1;
    }

    boolean matches(CompoundTag next) { return state.getUUID("match").equals(next.getUUID("match")); }

    void update(CompoundTag next) {
        state = next;
        friends = next.getList("friends", Tag.TAG_COMPOUND);
        if (friendIndex() < 0) selectedFriendId = friends.isEmpty() ? -1 : friends.getCompound(0).getInt("entity");
        updateSecondFriend();
    }

    @Override protected void init() {
        if (minecraft.level == null) return;
        characterPreview = CantBeatItLandlord.GUEST.get().create(minecraft.level);
        villagerPreview = EntityType.VILLAGER.create(minecraft.level);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        Packets.request(Packets.CANCEL_LOBBY, state.getUUID("match"), 0, new int[0]);
        super.onClose();
    }

    private float scale() { return Math.min(width / (float)WIDTH, height / (float)HEIGHT); }
    private float left() { return (width - WIDTH * scale()) / 2; }
    private float top() { return (height - HEIGHT * scale()) / 2; }
    private int screenX(int x) { return Math.round(left() + x * scale()); }
    private int screenY(int y) { return Math.round(top() + y * scale()); }
    private int friendIndex() {
        return friendIndex(selectedFriendId);
    }
    private int friendIndex(int entityId) {
        for (int i = 0; i < friends.size(); i++) if (friends.getCompound(i).getInt("entity") == entityId) return i;
        return -1;
    }
    private boolean choosingFriend() { return selection == FRIENDS; }
    private boolean duelOnly() { return state.getBoolean("duel"); }
    private boolean friendOnly() { return duelOnly() || threePlayers; }
    private int slotX(int index) { return SLOT_X + (friendOnly() ? 0 : index * SLOT_STEP); }
    private int accent() { return ACCENTS[selection]; }
    private void select(int index) {
        if (selection != index) { selection = index; selectedAt = Util.getMillis(); }
    }
    private void cycleFriend(int direction) {
        if (friends.isEmpty()) return;
        selectedFriendId = friends.getCompound(Math.floorMod(friendIndex() + direction, friends.size())).getInt("entity");
        selectedAt = Util.getMillis();
    }

    private void updateSecondFriend() {
        if (secondFriendId != selectedFriendId && friendIndex(secondFriendId) >= 0) return;
        secondFriendId = -1;
        for (int i = 0; i < friends.size(); i++) {
            int id = friends.getCompound(i).getInt("entity");
            if (id != selectedFriendId) {
                secondFriendId = id;
                return;
            }
        }
    }

    private void cycleThreeFriend(int direction, boolean second) {
        if (friends.size() <= 2 || state.getBoolean("waiting")) return;
        int index = friendIndex(second ? secondFriendId : selectedFriendId);
        int excluded = second ? selectedFriendId : secondFriendId;
        int chosen;
        do {
            index = Math.floorMod(index + direction, friends.size());
            chosen = friends.getCompound(index).getInt("entity");
        } while (chosen == excluded);
        if (second) secondFriendId = chosen;
        else {
            selectedFriendId = chosen;
            selectedAt = Util.getMillis();
        }
    }

    private boolean showThreeButton() { return choosingFriend() && (friends.size() >= 2 || threePlayers); }

    private void toggleThreePlayers() {
        if (state.getBoolean("waiting")) return;
        threePlayers = !threePlayers;
        updateSecondFriend();
    }

    private boolean canConfirm() {
        if (threePlayers) return friendIndex() >= 0 && friendIndex(secondFriendId) >= 0 && !state.getBoolean("waiting");
        return choosingFriend() ? friendIndex() >= 0 && !state.getBoolean("waiting")
                : selection != 0 || state.getBoolean("nearbyAvailable");
    }
    private void confirm(boolean duel) {
        if (!canConfirm() || Util.getMillis() - sentAt < 350) return;
        sentAt = Util.getMillis();
        if (threePlayers) {
            Packets.request(Packets.THREE_PLAYERS, state.getUUID("match"), 0, new int[]{selectedFriendId, secondFriendId});
            return;
        }
        Packets.request(choosingFriend() ? duel ? Packets.DUEL_PLAYER : Packets.INVITE_PLAYER : Packets.PICK_GUEST,
                state.getUUID("match"), 0, duel ? new int[]{selectedFriendId, duelStake.ordinal()}
                        : new int[]{choosingFriend() ? selectedFriendId : selection});
    }
    private LivingEntity preview() {
        if (minecraft.level == null) return null;
        if (choosingFriend()) return minecraft.level.getEntity(selectedFriendId) instanceof LivingEntity player ? player : null;
        if (selection == 0) return minecraft.level.getEntity(state.getInt("nearbyEntity")) instanceof LivingEntity mob ? mob
                : state.getBoolean("nearbyAvailable") ? villagerPreview : null;
        if (characterPreview != null) characterPreview.setAppearance(CHOICES[selection]);
        return characterPreview;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        int x = layoutX(mouseX), y = layoutY(mouseY);
        LivingEntity model = preview();
        g.fill(0, 0, width, height, 0xFF070C10);
        g.pose().pushPose();
        g.pose().translate(left(), top(), 0);
        g.pose().scale(scale(), scale(), 1);
        g.blit(STAGE, 0, 0, WIDTH, HEIGHT, 0, 0, 1672, 941, 1672, 941);
        label(g, threePlayers ? "三人桌" : duelOnly() ? "好友对决" : "选择牌友", DETAILS_X, 22, 2.1F, TEXT);
        String boss = threePlayers ? "三名玩家 · 友谊局" : duelOnly() ? "闹吃补第三席"
                : font.plainSubstrByWidth("挑战 · " + state.getString("boss"), 218);
        g.drawString(font, boss, 601 - font.width(boss), 28, MUTED, true);
        if (showThreeButton()) button(g, threePlayers ? "返回选人" : "三人桌", THREE_X, THREE_Y, THREE_W, THREE_H,
                !state.getBoolean("waiting"), !threePlayers, x, y);
        for (int i = friendOnly() ? FRIENDS : 0; i < 4; i++) drawSlot(g, i, x, y);
        drawDetails(g, x, y);
        if (model == null) {
            String unavailable = choosingFriend() ? "等待好友来到附近" : "暂无可预览的生物";
            g.drawCenteredString(font, unavailable, PREVIEW_X, 214, MUTED);
        }
        drawFooter(g, x, y);
        g.pose().popPose();

        if (model != null) drawPreview(g, model, x);
    }

    private void drawDetails(GuiGraphics g, int mouseX, int mouseY) {
        String name, details, category;
        if (choosingFriend()) {
            int index = friendIndex();
            name = index < 0 ? "好友席位" : friends.getCompound(index).getString("name");
            category = threePlayers ? "第一位好友" : duelOnly() ? "邀请对手" : "好友同桌";
            details = threePlayers ? "选择两位好友，两人都接受后开局"
                    : index < 0 ? "让好友来到附近，即可发送邀请" : "发送邀请，等对方接受后一起开局";
        } else {
            name = selection == 0 && state.getBoolean("nearbyAvailable") ? state.getString("nearbyName") : CHOICES[selection].title().getString();
            category = selection == 0 ? "就地邀请" : "临时牌友";
            details = switch (selection) {
                case 1 -> "黑色礼帽 · 浓须 · 工装长裤";
                case 2 -> "短发 · 灰色束脚裤 · 赤足";
                default -> state.getBoolean("nearbyAvailable") ? "邀请附近的生物，一起坐下打牌" : "附近暂时没有可以入座的生物";
            };
        }
        g.fill(DETAILS_X, 158, DETAILS_X + 17, 160, accent());
        g.drawString(font, category, DETAILS_X + 24, 155, accent(), false);
        name = font.plainSubstrByWidth(name, 155);
        float titleScale = Math.min(3.4F, 240F / Math.max(1, font.width(name)));
        label(g, name, DETAILS_X, 177, titleScale, TEXT);
        g.drawString(font, details, DETAILS_X, 220, MUTED, false);
        if (threePlayers) {
            drawThreeFriendRow(g, "第一位", selectedFriendId, FRIEND_Y, mouseX, mouseY);
            drawThreeFriendRow(g, "第二位", secondFriendId, STAKE_Y, mouseX, mouseY);
        } else if (selection == 1 || selection == 2) {
            g.drawString(font, "自动出牌 · 随时入座", DETAILS_X, 245, TEXT, false);
        } else if (choosingFriend() && friends.size() > 1) {
            button(g, "‹", DETAILS_X, FRIEND_Y, FRIEND_BUTTON, FRIEND_BUTTON, true, false, mouseX, mouseY);
            button(g, "›", FRIEND_NEXT_X, FRIEND_Y, FRIEND_BUTTON, FRIEND_BUTTON, true, false, mouseX, mouseY);
            g.drawCenteredString(font, (friendIndex() + 1) + " / " + friends.size(), 100, FRIEND_Y + 8, TEXT);
        } else if (choosingFriend() && !duelOnly()) {
            g.drawString(font, "等好友回应时，也可以选择临时牌友", DETAILS_X, 245, MUTED, false);
        }
        if (choosingFriend() && !threePlayers) drawDuelStake(g, mouseX, mouseY);
        String shortcut = threePlayers ? "← → 第一位   ↑ ↓ 第二位   Enter 邀请"
                : duelOnly() ? "← → 切换好友   ·   Enter 邀请" : "← → 切换牌友   ·   Enter 确认";
        g.drawString(font, shortcut, DETAILS_X, 298, MUTED, false);
    }

    private void drawThreeFriendRow(GuiGraphics g, String seat, int playerId, int y, int mouseX, int mouseY) {
        boolean enabled = friends.size() > 2 && !state.getBoolean("waiting");
        g.drawString(font, seat, DETAILS_X, y + 8, MUTED, false);
        button(g, "‹", STAKE_PREV_X, y, FRIEND_BUTTON, FRIEND_BUTTON, enabled, false, mouseX, mouseY);
        button(g, "›", STAKE_NEXT_X, y, FRIEND_BUTTON, FRIEND_BUTTON, enabled, false, mouseX, mouseY);
        int index = friendIndex(playerId);
        String name = index < 0 ? "暂无人选" : friends.getCompound(index).getString("name");
        g.drawCenteredString(font, font.plainSubstrByWidth(name, 100), 189, y + 8, TEXT);
    }

    private void drawDuelStake(GuiGraphics g, int mouseX, int mouseY) {
        boolean enabled = !state.getBoolean("waiting");
        g.drawString(font, "对决赌注", DETAILS_X, STAKE_Y + 8, MUTED, false);
        button(g, "‹", STAKE_PREV_X, STAKE_Y, FRIEND_BUTTON, FRIEND_BUTTON, enabled, false, mouseX, mouseY);
        button(g, "›", STAKE_NEXT_X, STAKE_Y, FRIEND_BUTTON, FRIEND_BUTTON, enabled, false, mouseX, mouseY);
        DuelStake pending = state.contains("pendingDuelStake") ? DuelStake.byIndex(state.getInt("pendingDuelStake")) : null;
        DuelStake shown = pending == null ? duelStake : pending;
        g.drawCenteredString(font, shown.isItem() ? "各 1 个" + shown.title : shown.title, 189, STAKE_Y + 8, TEXT);
    }

    private void cycleStake(int direction) {
        if (state.getBoolean("waiting")) return;
        DuelStake[] options = DuelStake.values();
        duelStake = options[Math.floorMod(duelStake.ordinal() + direction, options.length)];
    }

    private void drawPreview(GuiGraphics g, LivingEntity model, int mouseX) {
        float reveal = 1 - Mth.clamp((Util.getMillis() - selectedAt) / 220F, 0, 1);
        int center = PREVIEW_X + Math.round(11 * reveal * reveal);
        int top = screenY(PREVIEW_TOP), bottom = screenY(PREVIEW_BOTTOM);
        float size = Math.max(model.getBbHeight(), model.getBbWidth());
        int modelScale = Math.max(1, Math.round(143 * scale() / Math.max(.65F, size)));
        // The inventory renderer centers the entity on its bounding box.
        float offset = (screenY(PREVIEW_FLOOR) - (top + bottom) / 2F) / modelScale
                - model.getBbHeight() / (2F * model.getScale());
        InventoryScreen.renderEntityInInventoryFollowsAngle(g, screenX(center - 120), top,
                screenX(center + 120), bottom, modelScale, offset,
                .32F + Mth.clamp((PREVIEW_X - mouseX) / 940F, -.21F, .21F), -.035F, model);
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        button(g, "返回", DETAILS_X, BUTTON_Y, BACK_W, BUTTON_H, true, false, mouseX, mouseY);
        String status = state.getString("status");
        if (state.getBoolean("waiting")) status += "  " + state.getInt("remaining") + " 秒";
        if (!status.isEmpty()) {
            int x = DETAILS_X + BACK_W + 12;
            int limit = choosingFriend() && !friendOnly() ? DUEL_X : CONFIRM_X;
            g.drawString(font, font.plainSubstrByWidth(status, limit - x - 12), x, BUTTON_Y + 10, accent(), false);
        }
        if (choosingFriend() && !friendOnly())
            button(g, "好友对决", DUEL_X, BUTTON_Y, DUEL_W, BUTTON_H, canConfirm(), false, mouseX, mouseY);
        String confirm = choosingFriend()
                ? state.getBoolean("waiting") ? "等待回应…" : threePlayers ? "邀请两位好友  →"
                : duelOnly() ? "邀请对决  →" : "邀请同桌  →"
                : "确认入座  →";
        button(g, confirm, CONFIRM_X, BUTTON_Y, CONFIRM_W, BUTTON_H, canConfirm(), true, mouseX, mouseY);
    }

    private void drawSlot(GuiGraphics g, int index, int mouseX, int mouseY) {
        int x = slotX(index);
        boolean picked = index == selection;
        boolean hover = inside(mouseX, mouseY, x, SLOT_Y, SLOT_W, SLOT_H);
        boolean available = index == 0 ? state.getBoolean("nearbyAvailable") : index != FRIENDS || !friends.isEmpty();
        g.fill(x, SLOT_Y, x + SLOT_W, SLOT_Y + SLOT_H, picked ? ACCENTS[index] : hover ? MUTED : LINE);
        drawTile(g, PORTRAITS, index, x + 1, SLOT_Y + 1, SLOT_W - 2, SLOT_H - 2, 384, 192);
        if (!available) g.fill(x + 1, SLOT_Y + 1, x + SLOT_W - 1, SLOT_Y + SLOT_H - 1, 0x75060B0F);
        if (picked) {
            g.fill(x + 1, SLOT_Y + SLOT_H - 3, x + SLOT_W - 1, SLOT_Y + SLOT_H - 1, ACCENTS[index]);
        }
        String title = index == FRIENDS ? "邀请好友" : CHOICES[index].title().getString();
        String status = !available ? "暂无人选" : picked ? "已选择" : index == FRIENDS ? friends.size() + " 位在线" : "可入座";
        label(g, title, x + 83, SLOT_Y + 22, 1.14F, available ? TEXT : MUTED);
        g.drawString(font, status, x + 83, SLOT_Y + 42, picked && available ? ACCENTS[index] : MUTED, false);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, "textures/gui/guest/" + name + ".png");
    }
    private static void drawTile(GuiGraphics g, ResourceLocation texture, int index, int x, int y, int w, int h,
                                 int tileWidth, int tileHeight) {
        g.blit(texture, x, y, w, h, index % 2 * tileWidth, index / 2 * tileHeight, tileWidth, tileHeight,
                tileWidth * 2, tileHeight * 2);
    }
    private void label(GuiGraphics g, String text, int x, int y, float size, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
    private void button(GuiGraphics g, String text, int x, int y, int w, int h,
                        boolean enabled, boolean primary, int mouseX, int mouseY) {
        boolean hover = enabled && inside(mouseX, mouseY, x, y, w, h);
        int tile = !enabled ? 2 : hover ? 1 : primary ? 3 : 0;
        drawTile(g, CONTROLS, tile, x, y, w, h, 256, 128);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : MUTED);
    }
    private int layoutX(double x) { return Mth.floor((x - left()) / scale()); }
    private int layoutY(double y) { return Mth.floor((y - top()) / scale()); }
    private static boolean inside(int x, int y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = layoutX(mouseX), y = layoutY(mouseY);
        for (int i = friendOnly() ? FRIENDS : 0; i < 4; i++)
            if (inside(x, y, slotX(i), SLOT_Y, SLOT_W, SLOT_H)) { select(i); return true; }
        if (inside(x, y, DETAILS_X, BUTTON_Y, BACK_W, BUTTON_H)) onClose();
        else if (inside(x, y, CONFIRM_X, BUTTON_Y, CONFIRM_W, BUTTON_H)) confirm(duelOnly());
        else if (showThreeButton() && inside(x, y, THREE_X, THREE_Y, THREE_W, THREE_H)) toggleThreePlayers();
        else if (threePlayers) {
            for (int row : new int[]{FRIEND_Y, STAKE_Y}) {
                if (inside(x, y, STAKE_PREV_X, row, FRIEND_BUTTON, FRIEND_BUTTON)) {
                    cycleThreeFriend(-1, row == STAKE_Y);
                    return true;
                }
                if (inside(x, y, STAKE_NEXT_X, row, FRIEND_BUTTON, FRIEND_BUTTON)) {
                    cycleThreeFriend(1, row == STAKE_Y);
                    return true;
                }
            }
            return false;
        } else if (choosingFriend() && !duelOnly() && inside(x, y, DUEL_X, BUTTON_Y, DUEL_W, BUTTON_H)) confirm(true);
        else if (choosingFriend() && inside(x, y, STAKE_PREV_X, STAKE_Y, FRIEND_BUTTON, FRIEND_BUTTON)) cycleStake(-1);
        else if (choosingFriend() && inside(x, y, STAKE_NEXT_X, STAKE_Y, FRIEND_BUTTON, FRIEND_BUTTON)) cycleStake(1);
        else if (choosingFriend() && friends.size() > 1 && inside(x, y, DETAILS_X, FRIEND_Y, FRIEND_BUTTON, FRIEND_BUTTON)) cycleFriend(-1);
        else if (choosingFriend() && friends.size() > 1 && inside(x, y, FRIEND_NEXT_X, FRIEND_Y, FRIEND_BUTTON, FRIEND_BUTTON)) cycleFriend(1);
        else return false;
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int direction = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            if (threePlayers) cycleThreeFriend(direction, false);
            else if (duelOnly()) cycleFriend(direction);
            else select(Math.floorMod(selection + direction, 4));
        } else if (choosingFriend() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
            if (threePlayers) cycleThreeFriend(direction, true);
            else cycleFriend(direction);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirm(duelOnly());
        else return super.keyPressed(key, scan, modifiers);
        return true;
    }
}
