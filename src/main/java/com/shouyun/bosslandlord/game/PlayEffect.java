package com.shouyun.bosslandlord.game;

public enum PlayEffect {
    NONE, AIRPLANE, BOMB, ROCKET;

    public static PlayEffect of(Combination combo) {
        if (combo == null) return NONE;
        return switch (combo.kind()) {
            case AIRPLANE, AIRPLANE_SINGLE, AIRPLANE_PAIR -> AIRPLANE;
            case BOMB -> BOMB;
            case ROCKET -> ROCKET;
            default -> NONE;
        };
    }

    public boolean blasts(Combination previous) {
        return (this == BOMB || this == ROCKET) && of(previous) == AIRPLANE;
    }
}
