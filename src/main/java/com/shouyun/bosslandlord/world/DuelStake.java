package com.shouyun.bosslandlord.world;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum DuelStake {
    NONE("友谊局", null),
    HEALTH("生命", null),
    DIAMOND("钻石", Items.DIAMOND),
    EMERALD("绿宝石", Items.EMERALD),
    NETHERITE("下界合金锭", Items.NETHERITE_INGOT),
    ENCHANTED_APPLE("附魔金苹果", Items.ENCHANTED_GOLDEN_APPLE),
    NETHER_STAR("下界之星", Items.NETHER_STAR);

    private static final DuelStake[] VALUES = values();
    public final String title;
    private final Item item;

    DuelStake(String title, Item item) {
        this.title = title;
        this.item = item;
    }

    public boolean isItem() { return item != null; }
    public Item item() { return item; }

    public static DuelStake byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
