package com.shouyun.bosslandlord.world;

import net.minecraft.network.chat.Component;

public enum GuestChoice {
    NEARBY("nearby", "附近生物"),
    NAOCHI("naochi", "闹吃"),
    GU_ZHENXING("gu_zhenxing", "古振兴");

    private static final GuestChoice[] OPTIONS = values();
    public final String id;
    private final String name;

    GuestChoice(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Component title() {
        return Component.literal(name);
    }

    public static GuestChoice byIndex(int index) {
        return index >= 0 && index < OPTIONS.length ? OPTIONS[index] : NEARBY;
    }
}
