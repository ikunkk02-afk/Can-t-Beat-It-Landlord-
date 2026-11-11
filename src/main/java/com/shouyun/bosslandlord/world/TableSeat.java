package com.shouyun.bosslandlord.world;

/** World offsets from the table center. The player faces north, with the boss to the west. */
public enum TableSeat {
    PLAYER(0, 1.8, 180),
    BOSS(-1.95, 0, -90),
    GUEST(1.95, 0, 90);

    private static final TableSeat[] BY_INDEX = values();

    public final double x;
    public final double z;
    public final float yaw;

    TableSeat(double x, double z, float yaw) {
        this.x = x;
        this.z = z;
        this.yaw = yaw;
    }

    public static TableSeat at(int index) { return BY_INDEX[index]; }

    /** Rotation from the south side, where the card's top edge faces the center. */
    public float cardYaw() { return 180 - yaw; }
}
