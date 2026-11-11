package com.shouyun.bosslandlord.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.world.SeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SeatRenderer extends EntityRenderer<SeatEntity> {
    public SeatRenderer(EntityRendererProvider.Context c) { super(c); }
    @Override public void render(SeatEntity seat, float yaw, float tick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-seat.getYRot())); pose.translate(-.5, 0, -.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CantBeatItLandlord.CHAIR_MODEL.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(SeatEntity e) { return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS; }
}
