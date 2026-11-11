package com.shouyun.bosslandlord.client;

import com.shouyun.bosslandlord.CantBeatItLandlord;
import com.shouyun.bosslandlord.game.MatchPhase;
import com.shouyun.bosslandlord.game.PlayEffect;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Local music follows the participant's match, even when the card screen is closed. */
final class TableMusic {
    private static final int INTENSE_CARD_COUNT = 5;
    private static final float BACKGROUND_VOLUME = .50F;
    private static final float RESULT_VOLUME = .72F;

    private enum Cue {
        GAME("game"), INTENSE("intense"), VICTORY("victory"), DEFEAT("defeat");

        final ResourceLocation id;
        Cue(String name) {
            id = ResourceLocation.fromNamespaceAndPath(CantBeatItLandlord.MODID, "music." + name);
        }
    }

    private static ClientLevel world;
    private static UUID match;
    private static Cue desiredLoop;
    private static Cue pendingResult;
    private static LoopTrack currentLoop;
    private static LoopTrack fadingLoop;
    private static SoundInstance result;
    private static boolean resultHandled;
    private static int resultDelay;
    private static int retryDelay;

    private TableMusic() {}

    static void accept(CompoundTag state) {
        if (!state.getBoolean("active")) {
            desiredLoop = null;
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        UUID id = state.getUUID("match");
        if (level != world || !id.equals(match)) {
            reset();
            world = level;
            match = id;
        }

        MatchPhase phase = MatchPhase.valueOf(state.getString("phase"));
        if (phase == MatchPhase.RESULT) {
            if (resultHandled) return;
            resultHandled = true;
            desiredLoop = null;
            pendingResult = state.getBoolean("won") ? Cue.VICTORY : Cue.DEFEAT;
            long moveAge = Math.max(0, level.getGameTime() - state.getLong("lastMoveAt"));
            resultDelay = PlayEffect.NONE.name().equals(state.getString("effect"))
                    ? 0 : (int)Math.max(0, MatchPhase.COMBO_TICKS - moveAge);
        } else if (!resultHandled) {
            boolean intense = phase == MatchPhase.PLAY && (
                    state.getInt("count0") <= INTENSE_CARD_COUNT
                    || state.getInt("count1") <= INTENSE_CARD_COUNT
                    || state.getInt("count2") <= INTENSE_CARD_COUNT);
            desiredLoop = intense ? Cue.INTENSE : Cue.GAME;
        }
    }

    static void tick() {
        if (world == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != world || minecraft.player == null) {
            reset();
            return;
        }
        SoundManager sounds = minecraft.getSoundManager();
        boolean audible = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0;

        if (fadingLoop != null && (fadingLoop.isStopped() || !sounds.isActive(fadingLoop))) fadingLoop = null;
        if (currentLoop != null) {
            if (!sounds.isActive(currentLoop)) {
                currentLoop = null;
            } else if (currentLoop.cue != desiredLoop) {
                if (fadingLoop != null) sounds.stop(fadingLoop);
                currentLoop.fading = true;
                fadingLoop = currentLoop;
                currentLoop = null;
                retryDelay = 0;
            }
        }
        if (retryDelay > 0) retryDelay--;
        if (desiredLoop != null && currentLoop == null && audible && retryDelay == 0) {
            currentLoop = new LoopTrack(desiredLoop);
            sounds.play(currentLoop);
            retryDelay = 20;
        }

        if (result != null && !sounds.isActive(result)) result = null;
        if (pendingResult != null && resultDelay-- <= 0) {
            if (audible) {
                result = new SimpleSoundInstance(
                        pendingResult.id, SoundSource.MUSIC,
                        RESULT_VOLUME, 1F, SoundInstance.createUnseededRandom(), false, 0,
                        SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                sounds.play(result);
            }
            pendingResult = null;
        }
    }

    static boolean replacesWorldMusic() {
        return world != null && Minecraft.getInstance().level == world
                && (desiredLoop != null || currentLoop != null || fadingLoop != null || pendingResult != null || result != null);
    }

    static void reset() {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (currentLoop != null) sounds.stop(currentLoop);
        if (fadingLoop != null) sounds.stop(fadingLoop);
        if (result != null) sounds.stop(result);
        world = null;
        match = null;
        desiredLoop = null;
        pendingResult = null;
        currentLoop = null;
        fadingLoop = null;
        result = null;
        resultHandled = false;
        resultDelay = 0;
        retryDelay = 0;
    }

    private static final class LoopTrack extends AbstractTickableSoundInstance {
        private final Cue cue;
        private boolean fading;

        LoopTrack(Cue cue) {
            super(SoundEvent.createVariableRangeEvent(cue.id), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.cue = cue;
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = .02F;
        }

        @Override public void tick() {
            if (fading) {
                volume = Math.max(0F, volume - .05F);
                if (volume == 0F) stop();
            } else {
                volume = Math.min(BACKGROUND_VOLUME, volume + .05F);
            }
        }
    }
}
