package com.shouyun.bosslandlord;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue BOSS_RANGE = B.comment("Boss invitation range, in blocks.")
            .defineInRange("bossRange", 56, 8, 128);
    public static final ModConfigSpec.IntValue TURN_SECONDS = B.comment("Player turn time. On timeout a legal move is played automatically.")
            .defineInRange("turnSeconds", 45, 10, 180);
    public static final ModConfigSpec.BooleanValue CHALLENGE_ANY_MOB = B.comment("Allow Survival players to challenge any mob they are looking at.")
            .define("challengeAnyMobInSurvival", false);
    public static final ModConfigSpec.BooleanValue FILL_VILLAGER = B.comment("Invite a temporary villager when no suitable mob is nearby.")
            .define("fillMissingGuest", true);
    public static final ModConfigSpec SPEC = B.build();
    private Config() {}
}
