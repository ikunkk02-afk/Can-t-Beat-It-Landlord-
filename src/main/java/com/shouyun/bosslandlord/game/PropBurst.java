package com.shouyun.bosslandlord.game;

/** Five uses share one streak across all props. Ten quiet seconds reset it. */
public final class PropBurst {
    public static final int LIMIT = 5;
    public static final int RESET_TICKS = 200;

    private int used;
    private long resetAt;

    public boolean tryUse(long now) {
        int count = used(now);
        if (count >= LIMIT) return false;
        used = count + 1;
        resetAt = now + RESET_TICKS;
        return true;
    }

    public int used(long now) { return now < resetAt ? used : 0; }
    public long resetAt() { return resetAt; }
}
