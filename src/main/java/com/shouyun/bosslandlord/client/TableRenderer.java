package com.shouyun.bosslandlord.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.game.MatchPhase;
import com.shouyun.bosslandlord.world.TableEntity;
import com.shouyun.bosslandlord.world.TableProp;
import com.shouyun.bosslandlord.world.TableSeat;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

public final class TableRenderer extends EntityRenderer<TableEntity> {
    private static final ResourceLocation CARDS = CardArt.TEXTURE;
    public TableRenderer(EntityRendererProvider.Context c) { super(c); }
    @Override public void render(TableEntity table, float yaw, float tick, PoseStack pose, MultiBufferSource buffer, int light) {
        var n = table.publicState();
        pose.pushPose();
        if (n.contains("flipAt")) {
            float age = table.level().getGameTime() - n.getLong("flipAt") + tick;
            float t = Mth.clamp(age / MatchPhase.TABLE_FLIP_TICKS, 0F, 1F);
            pose.translate(0, .14 * Mth.sin(t * Mth.PI), -.85);
            pose.mulPose(Axis.XP.rotationDegrees(-116 * (1 - (1 - t) * (1 - t))));
            pose.translate(0, 0, .85);
        }
        pose.pushPose(); pose.translate(-.5, 0, -.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CantBeatItLandlord.TABLE_MODEL.get().defaultBlockState(), pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (MatchPhase.SHUFFLE.name().equals(n.getString("phase"))) {
            float age = (float)(table.level().getGameTime() - n.getLong("phaseAt")) + tick;
            shuffle(pose, buffer, Mth.clamp(age, 0, MatchPhase.SHUFFLE_TICKS), light);
            pose.popPose();
            return;
        }
        float moveAge = (float)(table.level().getGameTime() - n.getLong("lastMoveAt")) + tick;
        for (int seat = 0; seat < 3; seat++) {
            TableSeat placement = TableSeat.at(seat);
            pose.pushPose();
            pose.translate(placement.x * .40, .963 + seat * .002, placement.z * .24);
            pose.mulPose(Axis.YP.rotationDegrees(placement.cardYaw()));
            int[] cards = n.getIntArray("shown" + seat).clone();
            Arrays.sort(cards);
            float step = Math.min(.17F, 1.2F / Math.max(1, cards.length));
            boolean moving = n.contains("lastSeat") && n.getInt("lastSeat") == seat && moveAge < MatchPhase.CARD_FLIGHT_TICKS;
            for (int i = 0; i < cards.length; i++) {
                float x = (i - (cards.length - 1) / 2F) * step;
                if (!moving) {
                    card(pose, buffer, cards[i], x, -.14F, .19F, .28F, light);
                    continue;
                }
                float delay = 2F * i / Math.max(1, cards.length - 1);
                float t = Mth.clamp((moveAge - delay) / 8F, 0F, 1F);
                float ease = 1 - (1 - t) * (1 - t);
                pose.pushPose();
                pose.translate(x * ease, Mth.sin(t * Mth.PI) * .28, (1 - ease) * .81);
                pose.mulPose(Axis.YP.rotationDegrees((1 - ease) * (i % 2 == 0 ? 15 : -15)));
                card(pose, buffer, cards[i], 0, -.14F, .19F, .28F, light);
                pose.popPose();
            }
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0, .964, -.73);
        int[] bottom = n.getIntArray("bottom");
        for (int i = 0; i < 3; i++) {
            int face = i < bottom.length ? bottom[i] : 54;
            card(pose, buffer, face, (i - 1) * .21F, -.09F, .16F, .24F, light);
        }
        pose.popPose();
        pose.popPose();
        if (n.contains("prop")) drawProp(table, tick, pose, buffer, light);
        super.render(table, yaw, tick, pose, buffer, light);
    }

    private static void drawProp(TableEntity table, float tick, PoseStack pose, MultiBufferSource buffer, int light) {
        var state = table.publicState();
        TableProp prop = TableProp.byIndex(state.getInt("prop"));
        float age = table.level().getGameTime() - state.getLong("propAt") + tick;
        if (prop == null || age < 0 || age >= TableProp.FLIGHT_TICKS) return;
        TableSeat from = TableSeat.at(state.getInt("propFrom"));
        TableSeat to = TableSeat.at(state.getInt("propTo"));
        float t = age / TableProp.FLIGHT_TICKS;
        pose.pushPose();
        double lift = prop == TableProp.WATER ? 1.03 + Mth.sin(t * Mth.PI) * .11 : .86 + Mth.sin(t * Mth.PI) * .75;
        pose.translate(Mth.lerp(t, from.x * .76, to.x), lift,
                Mth.lerp(t, from.z * .76, to.z));
        pose.mulPose(Axis.YP.rotationDegrees(t * (prop == TableProp.WATER ? 35 : 400)));
        pose.mulPose(Axis.ZP.rotationDegrees(prop == TableProp.WATER ? -78 * Mth.clamp((t - .62F) / .38F, 0F, 1F) : t * 325));
        pose.scale(.78F, .78F, .78F);
        Minecraft.getInstance().getItemRenderer().renderStatic(prop.stack(), ItemDisplayContext.GROUND,
                light, OverlayTexture.NO_OVERLAY, pose, buffer, table.level(), table.getId());
        pose.popPose();
    }

    private static void shuffle(PoseStack pose, MultiBufferSource buffer, float age, int light) {
        if (age < MatchPhase.RIFFLE_TICKS) {
            float progress = age / MatchPhase.RIFFLE_TICKS;
            float spread = Mth.sin(progress * Mth.PI);
            for (int i = 0; i < 14; i++) {
                int side = i % 2 == 0 ? -1 : 1;
                float riffle = Mth.sin(progress * Mth.PI * 4 - i * .38F) * spread;
                pose.pushPose();
                pose.translate(side * spread * .40, .966 + i * .003 + (riffle + spread) * .055, side * spread * .11);
                pose.mulPose(Axis.YP.rotationDegrees(side * spread * 27));
                card(pose, buffer, 54, 0, -.16F, .23F, .33F, light);
                pose.popPose();
            }
            return;
        }
        for (int i = 0; i < 5; i++) {
            pose.pushPose();
            pose.translate(0, .968 + i * .004, 0);
            card(pose, buffer, 54, 0, -.15F, .20F, .30F, light);
            pose.popPose();
        }
        float deal = age - MatchPhase.RIFFLE_TICKS;
        for (int i = 0; i < 15; i++) {
            float progress = (deal - i * 1.55F) / 9F;
            if (progress < 0) continue;
            float t = Math.min(progress, 1);
            float ease = 1 - (1 - t) * (1 - t);
            TableSeat seat = TableSeat.at(i % 3);
            float spread = (i / 3 - 2) * .085F;
            double x = seat.x * .47 + (seat == TableSeat.PLAYER ? spread : 0);
            double z = seat.z * .38 + (seat == TableSeat.PLAYER ? 0 : spread);
            pose.pushPose();
            pose.translate(x * ease, .968 + i * .001 + Mth.sin(t * Mth.PI) * .25F, z * ease);
            pose.mulPose(Axis.YP.rotationDegrees(ease * Mth.wrapDegrees(seat.cardYaw())));
            card(pose, buffer, 54, 0, -.12F, .17F, .25F, light);
            pose.popPose();
        }
    }
    private static void card(PoseStack pose, MultiBufferSource buffers, int card, float x, float z, float w, float h, int light) {
        if (card < 0 || card > 54) return;
        float u0 = (CardArt.textureX(card) + .5F) / CardArt.ATLAS_WIDTH;
        float v0 = (CardArt.textureY(card) + .5F) / CardArt.ATLAS_HEIGHT;
        float u1 = (CardArt.textureX(card) + CardArt.FACE_WIDTH - .5F) / CardArt.ATLAS_WIDTH;
        float v1 = (CardArt.textureY(card) + CardArt.FACE_HEIGHT - .5F) / CardArt.ATLAS_HEIGHT;
        var p = pose.last(); var v = buffers.getBuffer(RenderType.entityCutoutNoCull(CARDS));
        vertex(v, p, x - w / 2, 0, z, u0, v0, light);
        vertex(v, p, x + w / 2, 0, z, u1, v0, light);
        vertex(v, p, x + w / 2, 0, z + h, u1, v1, light);
        vertex(v, p, x - w / 2, 0, z + h, u0, v1, light);
    }
    private static void vertex(VertexConsumer v, PoseStack.Pose p, float x, float y, float z, float u, float t, int light) {
        v.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, t).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
    }
    @Override public ResourceLocation getTextureLocation(TableEntity e) { return CARDS; }
}
