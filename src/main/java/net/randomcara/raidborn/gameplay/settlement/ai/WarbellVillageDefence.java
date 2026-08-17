package net.randomcara.raidborn.gameplay.settlement.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.gameplay.recruit.SettlementBridge;
import net.randomcara.raidborn.gameplay.recruit.SquadOrders;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.Comparator;
import javax.annotation.Nullable;

/**
 * Village defence for a settled illager: who counts as an intruder, and how to get to them.
 *
 * <p>Kept apart from {@link WarbellVillageWanderGoal} because the two answer different questions.
 * The goal owns the daily routine — sleep, work, wander — and asks this once per tick whether there
 * is anything to fight instead.
 *
 * <p>Combat is leashed to the settlement on purpose: a settled illager that chases a golem over the
 * hill stops being a villager and never finds its way back.
 */
class WarbellVillageDefence {

    private static final int SCAN_INTERVAL_TICKS = 10;

    /** Intruders are worth answering slightly outside the walls, but not chasing beyond them. */
    private static final double SCAN_PADDING = 8.0D;

    private static final double APPROACH_SPEED = 1.1D;

    /** Evokers cast from range; walking into melee wastes the fangs and gets them killed. */
    private static final double EVOKER_APPROACH_SPEED = 0.95D;
    private static final double EVOKER_STOP_DISTANCE_SQR = 10.0D * 10.0D;

    private int scanCooldown;

    void reset() {
        this.scanCooldown = 0;
    }

    /**
     * The intruder this mob should be fighting, or null to carry on with the routine.
     *
     * <p>A target already set is kept as long as it is alive and both sides are still inside the
     * settlement; otherwise the area is rescanned, at most once every {@link #SCAN_INTERVAL_TICKS}.
     */
    @Nullable
    LivingEntity selectTarget(Mob mob, BlockPos bellPos) {
        int radius = WarbellVillageData.getVillageRadius(mob);
        LivingEntity target = mob.getTarget();

        if (target != null && shouldDrop(mob, bellPos, radius, target)) {
            SquadOrders.clearCombatState(mob);
            mob.setTarget(null);

            if (mob instanceof Monster monster) {
                monster.setAggressive(false);
            }

            target = null;
        }

        if (target != null) {
            return target;
        }

        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return null;
        }

        this.scanCooldown = SCAN_INTERVAL_TICKS;
        LivingEntity found = findIntruder(mob, bellPos, radius);

        if (found != null) {
            mob.setTarget(found);
        }

        return found;
    }

    private static boolean shouldDrop(Mob mob, BlockPos bellPos, int radius, LivingEntity target) {
        return !target.isAlive()
                || target.isRemoved()
                || SettlementBridge.isProtectedVillageTarget(mob, target)
                || WarbellVillageData.isPositionOutsideVillage(bellPos, radius, target.blockPosition(), WarbellVillageData.COMBAT_LEASH_BUFFER)
                || WarbellVillageData.isMobOutsideVillage(mob, WarbellVillageData.COMBAT_LEASH_BUFFER * 2.0D);
    }

    @Nullable
    private static LivingEntity findIntruder(Mob mob, BlockPos bellPos, int radius) {
        AABB scanBox = new AABB(bellPos).inflate(radius + SCAN_PADDING);

        return mob.level().getEntitiesOfClass(
                        LivingEntity.class,
                        scanBox,
                        entity -> entity != mob
                                && entity.isAlive()
                                && isIntruder(entity)
                                && !WarbellVillageData.isPositionOutsideVillage(bellPos, radius, entity.blockPosition(), 4.0D)
                )
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    /** The settlement is an illager one, so the villagers and their golems are the hostile side. */
    private static boolean isIntruder(LivingEntity entity) {
        return entity instanceof AbstractVillager || entity instanceof IronGolem;
    }

    static void approach(Mob mob, LivingEntity target) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (mob instanceof Monster monster) {
            monster.setAggressive(true);
        }

        if (!(mob instanceof Evoker)) {
            mob.getNavigation().moveTo(target, APPROACH_SPEED);
            return;
        }

        if (mob.distanceToSqr(target) > EVOKER_STOP_DISTANCE_SQR) {
            mob.getNavigation().moveTo(target, EVOKER_APPROACH_SPEED);
        } else {
            mob.getNavigation().stop();
        }
    }
}
