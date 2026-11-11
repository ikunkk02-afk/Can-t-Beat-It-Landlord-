package com.shouyun.bosslandlord.client;

import com.mojang.math.Axis;
import com.shouyun.bosslandlord.game.MatchPhase;
import com.shouyun.bosslandlord.game.PlayEffect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class ComboAnimation {
    private ComboAnimation() {}

    static void draw(GuiGraphics g, Font font, PlayEffect effect, boolean blastedPlane, float age, int cx) {
        if (effect == PlayEffect.NONE || age < 0 || age >= MatchPhase.COMBO_TICKS) return;
        float t = age / MatchPhase.COMBO_TICKS;
        if (blastedPlane) {
            float crash = Math.max(0F, (t - .30F) / .70F);
            drawPlane(g, cx - 112 + t * 216, 145 + crash * crash * 53, -8 + crash * 112, t, true);
        }
        switch (effect) {
            case AIRPLANE -> drawPlane(g, cx - 135 + t * 270, 177 - Mth.sin(t * Mth.PI) * 35, -12, t, false);
            case BOMB -> {
                float lift = Mth.sin(Math.min(1, t * 3) * Mth.HALF_PI);
                if (t < .42F) {
                    for (int i = 0; i < 4; i++) {
                        rotatedCard(g, CardArt.BACK, cx - 25 + i * 16, 177 - 12 * lift, 22, (i - 1.5F) * 13);
                    }
                }
                burst(g, cx, 163, t, .25F, 0xFF9C3D, 0xFFE1A0, 122, 34);
            }
            case ROCKET -> {
                float flight = Math.min(1F, t / .62F);
                for (int side : new int[]{-1, 1}) {
                    int x = cx + side * (int)(12 + flight * 50);
                    int y = (int)(188 - flight * 61);
                    int spark = side == -1 ? 0xBAA8FF : 0xFFD76F;
                    if (t < .70F) {
                        for (int i = 1; i <= 10; i++) {
                            int tail = (int)(flight * i * 4);
                            dot(g, x - side * tail / 2, y + tail, 4 - i / 4, argb(spark, 1F - i / 11F));
                        }
                        rotatedCard(g, side == -1 ? 52 : 53, x, y, 24, side * flight * 24);
                    }
                    burst(g, cx + side * 62, 127, t, .43F, spark, 0xFFF4CA, 74, 23);
                }
            }
            case NONE -> {}
        }
        if (t > .23F) {
            String label = switch (effect) {
                case AIRPLANE -> "飞 机";
                case BOMB -> "炸 弹";
                case ROCKET -> "王 炸";
                case NONE -> "";
            };
            if (blastedPlane) label += "  ·  击落飞机";
            int half = font.width(label) / 2 + 17;
            float fade = Math.min(1, (1 - t) * 6);
            g.fill(cx - half, 209, cx + half, 230, argb(0x241812, .9F * fade));
            g.fill(cx - half, 209, cx + half, 210, argb(0xE0A95B, fade));
            g.drawCenteredString(font, label, cx, 216, argb(0xFFE2A2, fade));
        }
    }

    private static void drawPlane(GuiGraphics g, float x, float y, float angle, float progress, boolean hit) {
        for (int i = 1; i < 10; i++) {
            int color = hit && progress > .35F ? 0xFF964B : 0xC8E8F8;
            dot(g, (int)x - i * 7, (int)y + i, 4 - i / 3, argb(color, (1 - i / 10F) * (1 - progress * .65F)));
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        // Three overlapping cards form the fuselage and wings.
        rotatedCard(g, 16, -9, 3, 22, 66);
        rotatedCard(g, 17, -9, -10, 22, 112);
        rotatedCard(g, 18, 4, -2, 24, 90);
        g.pose().popPose();
    }

    private static void rotatedCard(GuiGraphics g, int card, float x, float y, int width, float angle) {
        int height = width * 3 / 2;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        CardArt.draw(g, card, -width / 2, -height / 2, width, height);
        g.pose().popPose();
    }

    private static void burst(GuiGraphics g, int x, int y, float t, float start, int color, int core, int radius, int points) {
        if (t < start) return;
        float progress = (t - start) / (1 - start);
        float spread = (1 - (1 - progress) * (1 - progress)) * radius;
        for (int i = 0; i < points; i++) {
            float angle = i * Mth.TWO_PI / points;
            float distance = spread * (.62F + (i % 5) * .09F);
            int dx = (int)(Mth.cos(angle) * distance);
            int dy = (int)(Mth.sin(angle) * distance * .47F + progress * progress * 12);
            int size = 2 + (i % 3) + (int)((1 - progress) * 3);
            dot(g, x + dx, y + dy, size, argb(i % 3 == 0 ? core : color, 1 - progress));
        }
    }

    private static void dot(GuiGraphics g, int x, int y, int size, int color) {
        int half = size / 2;
        g.fill(x - half, y - half, x - half + size, y - half + size, color);
    }

    private static int argb(int rgb, float opacity) {
        return (Mth.clamp(Math.round(opacity * 255), 0, 255) << 24) | rgb;
    }
}
