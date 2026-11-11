package com.shouyun.bosslandlord.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class GuestEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("guest.idle");
    private static final RawAnimation SEATED = RawAnimation.begin().thenLoop("guest.seated");
    private static final RawAnimation PLAY = RawAnimation.begin().thenPlay("guest.play");
    private static final RawAnimation SING = RawAnimation.begin().thenPlay("guest.sing");
    // 10 ticks of pose blending plus a 95-tick singing animation.
    private static final int SONG_TICKS = 105;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private static final EntityDataAccessor<Integer> APPEARANCE =
            SynchedEntityData.defineId(GuestEntity.class, EntityDataSerializers.INT);
    private boolean hasSung;
    private long songEndsAt;

    public GuestEntity(EntityType<? extends GuestEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE, GuestChoice.NAOCHI.ordinal());
    }

    public void setAppearance(GuestChoice choice) {
        entityData.set(APPEARANCE, choice.ordinal());
    }

    public GuestChoice appearance() {
        GuestChoice choice = GuestChoice.byIndex(entityData.get(APPEARANCE));
        return choice == GuestChoice.NEARBY ? GuestChoice.NAOCHI : choice;
    }

    @Override protected Component getTypeName() {
        return appearance().title();
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pose", 10,
                state -> state.setAndContinue(isPassenger() ? SEATED : IDLE))
                .triggerableAnim("sing", SING));
        controllers.add(new AnimationController<>(this, "gesture", 3, state -> PlayState.STOP)
                .triggerableAnim("play", PLAY));
    }

    public void animateCardTurn(boolean playedCards) {
        if (!hasSung) {
            hasSung = true;
            songEndsAt = level().getGameTime() + SONG_TICKS;
            SoundEvent voice = (appearance() == GuestChoice.GU_ZHENXING
                    ? MatchSounds.GU_ZHENXING_SONG : MatchSounds.NAOCHI_SONG).get();
            triggerAnim("pose", "sing");
            level().playSound(null, this, voice, SoundSource.NEUTRAL, 1F, 1F);
        } else if (playedCards && songEndsAt == 0) {
            triggerAnim("gesture", "play");
        }
    }

    // Match ticks drive this because seated mobs skip their own entity ticks.
    public void tickSong() {
        if (songEndsAt != 0 && level().getGameTime() >= songEndsAt) {
            songEndsAt = 0;
            stopTriggeredAnim("pose", "sing");
        }
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    // Seated mobs deliberately skip entity ticks. Use world time so their animations still run.
    @Override public double getTick(Object entity) { return level().getGameTime(); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Appearance", appearance().ordinal());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAppearance(GuestChoice.byIndex(tag.getInt("Appearance")));
    }
}
