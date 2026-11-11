package com.shouyun.bosslandlord.game;

public enum MatchPhase {
    SHUFFLE, AUCTION, STAKE, PLAY, RESULT;

    public static final int RIFFLE_TICKS = 30;
    public static final int SHUFFLE_TICKS = 62;
    public static final int CARD_FLIGHT_TICKS = 10;
    public static final int COMBO_TICKS = 28;
    public static final int TABLE_FLIP_TICKS = 16;
}
