package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.CantBeatItLandlordClient;
import com.shouyun.bosslandlord.game.MatchPhase;
import com.shouyun.bosslandlord.network.Packets;
import com.shouyun.bosslandlord.world.SeatEntity;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class ClientState {
    private static final Set<ResourceLocation> TABLE_HIDDEN_LAYERS = Set.of(
            VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR, VanillaGuiLayers.BOSS_OVERLAY,
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH,
            VanillaGuiLayers.SELECTED_ITEM_NAME);
    public static CompoundTag state;
    private ClientState() {}
    public static boolean active() { return state != null && state.getBoolean("active"); }
    public static void receive(CompoundTag n) {
        Minecraft mc = Minecraft.getInstance();
        if (n.getBoolean("lobby")) {
            if (n.getBoolean("active")) {
                if (active() || mc.level == null || mc.player == null) return;
                if (mc.screen instanceof GuestSelectionScreen selection && selection.matches(n)) selection.update(n);
                else if (n.getBoolean("open")) mc.setScreen(new GuestSelectionScreen(n));
            } else if (mc.screen instanceof GuestSelectionScreen selection && selection.matches(n)) {
                mc.setScreen(null);
            }
            return;
        }
        if (n.getBoolean("watch")) {
            if (!active() && mc.level != null && mc.player != null) mc.setScreen(new LandlordScreen(n));
            return;
        }
        if (!n.getBoolean("active")) {
            if (state != null && n.hasUUID("match") && !n.getUUID("match").equals(state.getUUID("match"))) return;
            TableMusic.accept(n);
            state = null;
            if (mc.screen instanceof LandlordScreen screen && screen.matches(n)) mc.setScreen(null);
            return;
        }
        state = n;
        TableMusic.accept(n);
        if (mc.screen instanceof LandlordScreen screen && screen.matches(n)) screen.update(n);
        else if (n.getBoolean("open") && mc.level != null && mc.player != null) mc.setScreen(new LandlordScreen(n));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        TableMusic.tick();
        if (mc.level == null || mc.player == null) { state = null; return; }
        while (CantBeatItLandlordClient.OPEN.consumeClick()) {
            if (mc.screen != null) continue;
            if (active()) Packets.request(Packets.REOPEN, state.getUUID("match"), state.getInt("revision"), new int[0]);
            else Packets.request(Packets.START, Packets.NONE, 0, new int[0]);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        TableMusic.reset();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void selectMusic(SelectMusicEvent event) {
        if (TableMusic.replacesWorldMusic()) event.overrideMusic(null);
    }
    @SubscribeEvent public static void hideGameHud(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof LandlordScreen && TABLE_HIDDEN_LAYERS.contains(event.getName())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void hideHeldItem(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof LandlordScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void holdSeat(MovementInputUpdateEvent event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) {
            event.getInput().shiftKeyDown = false;
            event.getInput().jumping = false;
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (active() && mc.screen == null) {
            String key = CantBeatItLandlordClient.OPEN.getTranslatedKeyMessage().getString();
            MatchPhase phase = MatchPhase.valueOf(state.getString("phase"));
            String status = switch (phase) {
                case SHUFFLE -> "洗牌发牌中";
                case AUCTION -> "叫地主阶段";
                case STAKE -> "选择赌注";
                case PLAY -> state.getInt("turn") == state.getInt("viewer") ? "轮到你了" : "牌友出牌中";
                case RESULT -> "牌局结算";
            };
            String text = key + " 返回牌桌 · " + status;
            int x = (event.getGuiGraphics().guiWidth() - mc.font.width(text)) / 2;
            int y = event.getGuiGraphics().guiHeight() - 60;
            event.getGuiGraphics().fill(x - 6, y - 4, x + mc.font.width(text) + 6, y + 13, 0xD019302B);
            event.getGuiGraphics().drawString(mc.font, text, x, y, 0xFFFFDE94);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void scaleBefore(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity && !event.isCanceled()) {
            event.getPoseStack().pushPose();
            float scale = SeatEntity.visualScale(event.getEntity());
            event.getPoseStack().scale(scale, scale, scale);
        }
    }
    @SubscribeEvent public static void scaleAfter(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity().getVehicle() instanceof SeatEntity) event.getPoseStack().popPose();
    }
}
