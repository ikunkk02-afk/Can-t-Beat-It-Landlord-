package com.shouyun.bosslandlord.game;

/** Each player decides once, starting at a randomly selected seat. The last claim wins. */
public final class ThreePlayerAuction {
    private int turn;
    private int candidate = -1;
    private int decisions;

    public ThreePlayerAuction(int firstSeat) {
        if (firstSeat < 0 || firstSeat >= 3) throw new IllegalArgumentException("Invalid first seat");
        turn = firstSeat;
    }

    public int turn() { return turn; }
    public int candidate() { return candidate; }
    public boolean complete() { return decisions == 3; }

    public boolean decide(int seat, boolean claim) {
        if (complete() || seat != turn) return false;
        if (claim) candidate = seat;
        decisions++;
        turn = complete() ? -1 : (turn + 1) % 3;
        return true;
    }
}
