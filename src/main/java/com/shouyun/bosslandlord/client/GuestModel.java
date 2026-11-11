package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.world.GuestEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Geometry and animation exported directly from the editable Blockbench projects. */
public final class GuestModel extends GeoModel<GuestEntity> {
    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, path);
    }

    @Override public ResourceLocation getModelResource(GuestEntity guest) {
        return asset("geo/" + guest.appearance().id + ".geo.json");
    }

    @Override public ResourceLocation getTextureResource(GuestEntity guest) {
        return asset("textures/entity/guest/" + guest.appearance().id + ".png");
    }

    @Override public ResourceLocation getAnimationResource(GuestEntity guest) {
        return asset("animations/guest.animation.json");
    }
}
