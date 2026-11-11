package com.shouyun.bosslandlord.game;

public final class WagerMath {
    private WagerMath() {}
    public static float stake(float maximum, int multiplier) {
        if (!Float.isFinite(maximum) || maximum <= 0 || multiplier < 1 || multiplier > 3)
            throw new IllegalArgumentException("Invalid health");
        return maximum * (multiplier / 3F);
    }
    public static float remaining(float current, float maximum, int multiplier) {
        if (!Float.isFinite(current)) throw new IllegalArgumentException("Invalid health");
        float after = current - stake(maximum, multiplier);
        return after <= .001F ? 0 : after;
    }
}
