package com.shouyun.bosslandlord.server;

import com.shouyun.bosslandlord.world.DuelStake;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class DuelEscrow {
    private static final String DEPOSIT = "bosslandlord:duel_deposit";
    private static final String PAYOUT = "bosslandlord:duel_payout";

    private DuelEscrow() {}

    static boolean canFund(ServerPlayer player, DuelStake stake) {
        return !stake.isItem() || (!data(player).contains(DEPOSIT) && findStake(player, stake) >= 0);
    }

    static boolean reserve(ServerPlayer first, ServerPlayer second, DuelStake stake) {
        CompoundTag firstData = data(first), secondData = data(second);
        if (firstData.contains(DEPOSIT) || secondData.contains(DEPOSIT)) return false;
        int firstSlot = findStake(first, stake), secondSlot = findStake(second, stake);
        if (firstSlot < 0 || secondSlot < 0) return false;
        Tag deposit = stake.item().getDefaultInstance().save(first.registryAccess());
        Tag secondDeposit = deposit.copy();
        deposit(first, firstData, firstSlot, deposit);
        deposit(second, secondData, secondSlot, secondDeposit);
        return true;
    }

    static void resolve(ServerPlayer first, ServerPlayer second, ServerPlayer winner) {
        queue(winner == null ? first : winner, takeDeposit(first));
        queue(winner == null ? second : winner, takeDeposit(second));
        deliverOrNotify(first);
        deliverOrNotify(second);
    }

    static void recover(ServerPlayer player) {
        queue(player, takeDeposit(player));
        deliverOrNotify(player);
    }

    static void deliver(ServerPlayer player) {
        CompoundTag data = data(player);
        if (!data.contains(PAYOUT) || player.hasDisconnected()) return;
        ListTag pending = data.getList(PAYOUT, Tag.TAG_COMPOUND);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < pending.size();) {
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), pending.getCompound(i));
            if (stack.isEmpty()) {
                pending.remove(i);
                continue;
            }
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) slot = inventory.getFreeSlot();
            if (slot < 0) {
                i++;
                continue;
            }
            inventory.add(slot, stack);
            if (stack.isEmpty()) pending.remove(i);
            else pending.set(i++, stack.save(player.registryAccess()));
        }
        if (pending.isEmpty()) data.remove(PAYOUT);
    }

    private static void deliverOrNotify(ServerPlayer player) {
        deliver(player);
        if (!player.hasDisconnected() && data(player).contains(PAYOUT)) {
            MatchManager.tell(player, "赌注已结算。请腾出背包空间，待领物品会自动发放。");
        }
    }

    private static int findStake(ServerPlayer player, DuelStake stake) {
        ItemStack expected = stake.item().getDefaultInstance();
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(items.get(i), expected)) return i;
        }
        return -1;
    }

    private static CompoundTag data(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void deposit(ServerPlayer player, CompoundTag data, int slot, Tag deposit) {
        player.getInventory().removeItem(slot, 1);
        player.getInventory().setChanged();
        data.put(DEPOSIT, deposit);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
    }

    private static CompoundTag takeDeposit(ServerPlayer player) {
        CompoundTag data = data(player);
        CompoundTag deposit = data.getCompound(DEPOSIT);
        data.remove(DEPOSIT);
        return deposit;
    }

    private static void queue(ServerPlayer player, CompoundTag item) {
        if (item.isEmpty()) return;
        CompoundTag data = data(player);
        ListTag pending = data.getList(PAYOUT, Tag.TAG_COMPOUND);
        pending.add(item);
        data.put(PAYOUT, pending);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
    }
}
