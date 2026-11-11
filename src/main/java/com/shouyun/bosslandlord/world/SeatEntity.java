package com.shouyun.bosslandlord.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) { super(type, level); noPhysics = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return false; }
    public Vec3 seatedPosition(Entity passenger) {
        double height = passenger instanceof EnderDragon ? 9.0 / 16 : passenger instanceof WitherBoss ? .27 : passenger instanceof Warden ? .06 : -.16;
        return position().add(0, height, 0);
    }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 anchor = seatedPosition(passenger);
        move.accept(passenger, anchor.x, anchor.y, anchor.z);
        if (!(passenger instanceof Player)) {
            passenger.setYRot(getYRot()); passenger.yRotO = getYRot(); passenger.setXRot(0);
            if (passenger instanceof LivingEntity living) {
                living.yBodyRot = getYRot(); living.yBodyRotO = getYRot();
                living.yHeadRot = getYRot(); living.yHeadRotO = getYRot();
            }
        }
    }
    public static float visualScale(Entity entity) {
        if (entity instanceof EnderDragon) return .18F;
        if (entity instanceof Player || entity instanceof GuestEntity) return 1;
        return Math.min(1, 1.75F / Math.max(1, entity.getBbHeight()));
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return position().add(0, .1, 1); }
}
