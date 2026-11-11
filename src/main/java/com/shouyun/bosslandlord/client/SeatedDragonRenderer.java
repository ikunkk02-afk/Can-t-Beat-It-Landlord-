package com.shouyun.bosslandlord.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shouyun.bosslandlord.world.SeatEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SeatedDragonRenderer extends EnderDragonRenderer {
    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png"));
    private final SeatedDragonPose seatedPose;

    public SeatedDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        seatedPose = new SeatedDragonPose(context.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    @Override public Vec3 getRenderOffset(EnderDragon dragon, float partialTick) {
        if (!(dragon.getVehicle() instanceof SeatEntity seat)) return super.getRenderOffset(dragon, partialTick);
        // LevelRenderer has already interpolated the dragon's world position. Draw at the chair
        // instead, even if the first mounted frame still carries a pre-seat position.
        return seat.seatedPosition(dragon).subtract(
                Mth.lerp((double)partialTick, dragon.xOld, dragon.getX()),
                Mth.lerp((double)partialTick, dragon.yOld, dragon.getY()),
                Mth.lerp((double)partialTick, dragon.zOld, dragon.getZ()));
    }

    @Override public void render(EnderDragon dragon, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        if (!(dragon.getVehicle() instanceof SeatEntity seat)) {
            super.render(dragon, yaw, partialTick, pose, buffer, light);
            return;
        }

        float scale = SeatEntity.visualScale(dragon);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - seat.getYRot()));
        pose.scale(-scale, -scale, scale);
        seatedPose.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(dragon))), light);
        seatedPose.render(pose, buffer.getBuffer(EYES), light);
        pose.popPose();

        pose.pushPose();
        pose.scale(scale, scale, scale);
        var name = new RenderNameTagEvent(dragon, dragon.getDisplayName(), this, pose, buffer, light, partialTick);
        NeoForge.EVENT_BUS.post(name);
        if (name.canRender().isTrue() || name.canRender().isDefault() && shouldShowName(dragon)) {
            renderNameTag(dragon, name.getContent(), pose, buffer, light, partialTick);
        }
        pose.popPose();
    }
}
