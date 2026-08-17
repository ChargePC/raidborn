package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

import java.util.List;
import java.util.UUID;

public final class SquadOrders {
    public static final String TAG_ORDER = "raidborn_order";
    public static final String TAG_HOLD_X = "raidborn_hold_x";
    public static final String TAG_HOLD_Y = "raidborn_hold_y";
    public static final String TAG_HOLD_Z = "raidborn_hold_z";
    public static final String TAG_ATTACK_TARGET = "raidborn_attack_target";
    public static final String TAG_ATTACK_EXPIRE = "raidborn_attack_expire";
    public static final String TAG_HOLD_WANDER_COOLDOWN = "raidborn_hold_wander_cooldown";

    private SquadOrders() {
    }

    public static double commandRadius() {
        return RaidbornServerConfig.getSquadCommandRadius();
    }

    public static double holdScanRadius() {
        return RaidbornServerConfig.getSquadHoldScanRadius();
    }

    public static double holdLeashRadius() {
        return RaidbornServerConfig.getSquadHoldLeashRadius();
    }

    public static double holdWanderRadius() {
        return RaidbornServerConfig.getSquadHoldWanderRadius();
    }

    public static double attackOrderChaseRadius() {
        return RaidbornServerConfig.getSquadAttackOrderChaseRadius();
    }

    public static long attackOrderDurationTicks() {
        return RaidbornServerConfig.getSquadAttackOrderDurationTicks();
    }

    public static SquadOrder getOrder(Mob mob) {
        return SquadOrder.fromString(mob.getPersistentData().getString(TAG_ORDER));
    }

    public static void ensureDefaultOrder(Mob mob) {
        if (!mob.getPersistentData().contains(TAG_ORDER)) {
            mob.getPersistentData().putString(TAG_ORDER, SquadOrder.FOLLOW.getId());
        }
    }

    public static void setOrder(Mob mob, SquadOrder order) {
        mob.getPersistentData().putString(TAG_ORDER, order.getId());

        if (order != SquadOrder.HOLD) clearHoldPos(mob);
        if (order != SquadOrder.ATTACK) clearAttackTarget(mob);
        if (order == SquadOrder.FOLLOW) clearCombatState(mob);
    }

    public static void setHoldPos(Mob mob, BlockPos pos) {
        mob.getPersistentData().putInt(TAG_HOLD_X, pos.getX());
        mob.getPersistentData().putInt(TAG_HOLD_Y, pos.getY());
        mob.getPersistentData().putInt(TAG_HOLD_Z, pos.getZ());
        mob.getPersistentData().putString(TAG_ORDER, SquadOrder.HOLD.getId());
        mob.getPersistentData().putInt(TAG_HOLD_WANDER_COOLDOWN, 0);

        clearAttackTarget(mob);
        clearCombatState(mob);
    }

    public static boolean hasHoldPos(Mob mob) {
        return mob.getPersistentData().contains(TAG_HOLD_X)
                && mob.getPersistentData().contains(TAG_HOLD_Y)
                && mob.getPersistentData().contains(TAG_HOLD_Z);
    }

    public static BlockPos getHoldPos(Mob mob) {
        if (!hasHoldPos(mob)) return null;

        return new BlockPos(
                mob.getPersistentData().getInt(TAG_HOLD_X),
                mob.getPersistentData().getInt(TAG_HOLD_Y),
                mob.getPersistentData().getInt(TAG_HOLD_Z)
        );
    }

    public static void clearHoldPos(Mob mob) {
        mob.getPersistentData().remove(TAG_HOLD_X);
        mob.getPersistentData().remove(TAG_HOLD_Y);
        mob.getPersistentData().remove(TAG_HOLD_Z);
        mob.getPersistentData().remove(TAG_HOLD_WANDER_COOLDOWN);
    }

    public static void setAttackTarget(Mob mob, LivingEntity target, long gameTime) {
        mob.getPersistentData().putUUID(TAG_ATTACK_TARGET, target.getUUID());
        mob.getPersistentData().putLong(TAG_ATTACK_EXPIRE, gameTime + attackOrderDurationTicks());
        mob.getPersistentData().putString(TAG_ORDER, SquadOrder.ATTACK.getId());
        clearHoldPos(mob);
    }

    public static UUID getAttackTargetUUID(Mob mob) {
        return mob.getPersistentData().hasUUID(TAG_ATTACK_TARGET)
                ? mob.getPersistentData().getUUID(TAG_ATTACK_TARGET)
                : null;
    }

    public static long getAttackExpire(Mob mob) {
        return mob.getPersistentData().getLong(TAG_ATTACK_EXPIRE);
    }

    public static void clearAttackTarget(Mob mob) {
        mob.getPersistentData().remove(TAG_ATTACK_TARGET);
        mob.getPersistentData().remove(TAG_ATTACK_EXPIRE);
    }

    public static LivingEntity resolveAttackTarget(Mob mob) {
        UUID uuid = getAttackTargetUUID(mob);
        if (uuid == null || !(mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        Entity entity = serverLevel.getEntity(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    public static void clearOrderData(Mob mob) {
        mob.getPersistentData().remove(TAG_ORDER);
        clearAttackTarget(mob);
        clearHoldPos(mob);
    }

    public static void resetToFollow(Mob mob) {
        clearOrderData(mob);
        mob.getPersistentData().putString(TAG_ORDER, SquadOrder.FOLLOW.getId());
        clearCombatState(mob);
    }

    /**
     * Drops everything that would make the mob keep fighting: the active target, both revenge
     * slots, and whatever path it was running. Only place this happens: capture, release, teleport
     * and order changes all go through here so a recruit can't come back still swinging.
     */
    public static void clearCombatState(Mob mob) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.setLastHurtMob(null);
        mob.setLastHurtByPlayer(null);

        mob.getNavigation().stop();

        if (mob instanceof Monster monster) {
            monster.setAggressive(false);
        }
    }

    public static List<Mob> getNearbySquad(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(commandRadius()),
                mob -> RecruitOwnership.isYours(player, mob) && mob.isAlive()
        );
    }

    public static boolean isValidTarget(ServerPlayer owner, Mob recruit, LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        if (target == owner) return false;

        if (target instanceof Mob targetMob) {
            if (RecruitOwnership.isYours(owner, targetMob)) return false;
            if (RecruitOwnership.isSameSquad(recruit, targetMob)) return false;
        }

        return true;
    }
}
