package com.shouyun.bosslandlord.world;

import com.shouyun.bosslandlord.server.MatchManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class TableEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> PUBLIC = SynchedEntityData.defineId(TableEntity.class, EntityDataSerializers.COMPOUND_TAG);
    public TableEntity(EntityType<? extends TableEntity> type, Level level) { super(type, level); noPhysics = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(PUBLIC, new CompoundTag()); }
    public CompoundTag publicState() { return entityData.get(PUBLIC); }
    public void publicState(CompoundTag tag) { entityData.set(PUBLIC, tag); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) MatchManager.interactWithTable(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}
