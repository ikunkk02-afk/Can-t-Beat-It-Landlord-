package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.CantBeatItLandlord;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class CardArt {
    static final int BACK = 54;
    static final int FACE_WIDTH = 160, FACE_HEIGHT = 240;
    private static final int PADDING = 8, COLUMNS = 8;
    private static final int TILE_WIDTH = FACE_WIDTH + PADDING * 2, TILE_HEIGHT = FACE_HEIGHT + PADDING * 2;
    static final int ATLAS_WIDTH = COLUMNS * TILE_WIDTH, ATLAS_HEIGHT = 7 * TILE_HEIGHT;
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CantBeatItLandlord.MODID, "textures/entity/cards_hd.png");

    private CardArt() {}

    static int textureX(int card) { return card % COLUMNS * TILE_WIDTH + PADDING; }
    static int textureY(int card) { return card / COLUMNS * TILE_HEIGHT + PADDING; }

    static void draw(GuiGraphics g, int card, int x, int y, int width, int height) {
        g.blit(TEXTURE, x, y, width, height, (float)textureX(card), (float)textureY(card),
                FACE_WIDTH, FACE_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
    }
}
