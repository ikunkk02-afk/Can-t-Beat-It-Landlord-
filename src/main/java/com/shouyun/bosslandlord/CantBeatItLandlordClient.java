package com.shouyun.bosslandlord;

import com.mojang.blaze3d.platform.InputConstants;
import com.shouyun.bosslandlord.client.*;
import com.shouyun.bosslandlord.network.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = CantBeatItLandlord.MODID, dist = Dist.CLIENT)
public final class CantBeatItLandlordClient {
    public static final KeyMapping OPEN = new KeyMapping("key.bosslandlord.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.bosslandlord");
    public CantBeatItLandlordClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener(CantBeatItLandlordClient::keys);
        bus.addListener(CantBeatItLandlordClient::renderers);
        Packets.clientReceiver = ClientState::receive;
        NeoForge.EVENT_BUS.register(ClientState.class);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CantBeatItLandlord.TABLE.get(), TableRenderer::new);
        event.registerEntityRenderer(CantBeatItLandlord.SEAT.get(), SeatRenderer::new);
        event.registerEntityRenderer(CantBeatItLandlord.GUEST.get(), GuestRenderer::new);
        event.registerEntityRenderer(EntityType.ENDER_DRAGON, SeatedDragonRenderer::new);
        event.registerEntityRenderer(EntityType.WARDEN, SeatedWardenRenderer::new);
    }
}
