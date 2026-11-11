package com.shouyun.bosslandlord.world;

import com.shouyun.bosslandlord.CantBeatItLandlord;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MatchSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CantBeatItLandlord.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHUFFLE = register("shuffle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEAL = register("deal");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAY = register("play");
    public static final DeferredHolder<SoundEvent, SoundEvent> PASS = register("pass");
    public static final DeferredHolder<SoundEvent, SoundEvent> BID = register("bid");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAKE = register("stake");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRPLANE = register("airplane");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB = register("bomb");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET = register("rocket");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIN = register("win");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOSS = register("loss");
    public static final DeferredHolder<SoundEvent, SoundEvent> NAOCHI_SONG = register("guest.naochi");
    public static final DeferredHolder<SoundEvent, SoundEvent> GU_ZHENXING_SONG = register("guest.gu_zhenxing");

    private MatchSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
