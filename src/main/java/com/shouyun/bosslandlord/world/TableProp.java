package com.shouyun.bosslandlord.world;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public enum TableProp {
    EGG("鸡蛋", Items.EGG), SNOWBALL("雪球", Items.SNOWBALL),
    PAPER("纸团", Items.PAPER), WATER("倒杯水", Items.POTION);

    private static final TableProp[] OPTIONS = values();
    public static final int FLIGHT_TICKS = 12;
    public final String title;
    private final Item item;

    TableProp(String title, Item item) {
        this.title = title;
        this.item = item;
    }

    public ItemStack stack() {
        return this == WATER ? PotionContents.createItemStack(item, Potions.WATER) : new ItemStack(item);
    }

    public static TableProp byIndex(int index) {
        return index >= 0 && index < OPTIONS.length ? OPTIONS[index] : null;
    }
}
