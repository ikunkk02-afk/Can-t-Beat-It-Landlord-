package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.world.GuestEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GuestRenderer extends GeoEntityRenderer<GuestEntity> {
    public GuestRenderer(EntityRendererProvider.Context context) {
        super(context, new GuestModel());
        shadowRadius = .38F;
    }
}
