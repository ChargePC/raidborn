package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.List;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class RecruitTickEvents {
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide) return;

        boolean hasRecruitEffectNow = RecruitmentEvents.ownerHasAllianceEffect(player);
        boolean hadEffectBefore = player.getPersistentData().getBoolean(RecruitmentEvents.TAG_HAD_HONOR);

        if (hadEffectBefore && !hasRecruitEffectNow) {
            RecruitmentEvents.disbandSquad(player);
        }

        player.getPersistentData().putBoolean(RecruitmentEvents.TAG_HAD_HONOR, hasRecruitEffectNow);

        boolean runRecruitSync = player.tickCount % RecruitmentEvents.RECRUIT_SYNC_INTERVAL == 0;
        boolean runVillageSync = player.tickCount % RecruitmentEvents.VILLAGE_SYNC_INTERVAL == 0;

        if (!runRecruitSync && !runVillageSync) return;

        if (runRecruitSync) {
            RecruitSlots.enforceRecruitSlotLimit(player);
            tickRecruitMobs(player);
        }

        if (runVillageSync) {
            tickVillageMobs(player);
        }
    }

    static void tickRecruitMobs(ServerPlayer player) {
        List<Mob> recruitMobs = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RecruitmentEvents.CLEANUP_RADIUS),
                mob -> RecruitOwnership.isYours(player, mob) && mob.isAlive()
        );

        for (Mob mob : recruitMobs) {
            if (!RecruitmentEvents.supportsVillageMode(mob) && WarbellVillageData.isVillageMember(mob)) {
                WarbellVillageData.resetMobFromVillage(mob);
            }

            RecruitTargeting.clearOwnerAsTarget(mob);

            switch (SquadOrders.getOrder(mob)) {
                case FOLLOW -> tickFollowOrder(player, mob);
                case ATTACK -> tickAttackOrder(player, mob);
                case HOLD -> tickHoldOrder(player, mob);
            }

            LivingEntity currentTarget = mob.getTarget();
            if (currentTarget != null && (!RecruitmentEvents.isValidAttackTarget(player, mob, currentTarget) || RecruitTargeting.isProtectedRecruitTarget(mob, currentTarget))) {
                if (SquadOrders.getOrder(mob) == SquadOrder.FOLLOW && RecruitTargeting.restoreFollowTarget(player, mob)) {
                    currentTarget = mob.getTarget();
                } else {
                    if (SquadOrders.getOrder(mob) == SquadOrder.FOLLOW) {
                        RecruitTargeting.clearFollowTarget(mob);
                    }

                    SquadOrders.clearCombatState(mob);

                    if (SquadOrders.getOrder(mob) == SquadOrder.ATTACK) {
                        SquadOrders.setOrder(mob, SquadOrder.FOLLOW);
                    }
                }
            }

            RecruitmentEvents.setPatrolFlags(mob);
        }
    }

    static void tickFollowOrder(ServerPlayer player, Mob mob) {
        LivingEntity target = mob.getTarget();

        if (target != null && RecruitmentEvents.isValidAttackTarget(player, mob, target) && !RecruitTargeting.isProtectedRecruitTarget(mob, target)) {
            if (player.distanceToSqr(target) <= RecruitTargeting.FOLLOW_TARGET_LEASH_RADIUS_SQR
                    && player.distanceToSqr(mob) <= RecruitTargeting.FOLLOW_TARGET_LEASH_RADIUS_SQR) {
                RecruitTargeting.rememberFollowTarget(mob, target);
                RecruitCombatMovement.applyCombatMovement(mob, target, 1.25D, 0.95D, 10.0D * 10.0D);
            } else {
                RecruitTargeting.clearFollowTarget(mob);
                SquadOrders.clearCombatState(mob);
            }
        } else if (!RecruitTargeting.restoreFollowTarget(player, mob)) {
            SquadOrders.clearCombatState(mob);
        }
    }

    static void tickAttackOrder(ServerPlayer player, Mob mob) {
        LivingEntity forcedTarget = SquadOrders.resolveAttackTarget(mob);
        long expire = SquadOrders.getAttackExpire(mob);
        double maxChaseRadius = SquadOrders.attackOrderChaseRadius();
        double maxChaseDistSqr = maxChaseRadius * maxChaseRadius;

        if (forcedTarget == null
                || !SquadOrders.isValidTarget(player, mob, forcedTarget)
                || player.level().getGameTime() > expire
                || mob.distanceToSqr(forcedTarget) > maxChaseDistSqr) {
            SquadOrders.setOrder(mob, SquadOrder.FOLLOW);
            SquadOrders.clearCombatState(mob);
            return;
        }

        mob.setTarget(forcedTarget);
        RecruitCombatMovement.applyCombatMovement(mob, forcedTarget, 1.25D, 0.95D, 10.0D * 10.0D);
    }

    static void tickHoldOrder(ServerPlayer player, Mob mob) {
        BlockPos holdPos = SquadOrders.getHoldPos(mob);

        if (holdPos == null) {
            SquadOrders.setOrder(mob, SquadOrder.FOLLOW);
            SquadOrders.clearCombatState(mob);
            return;
        }

        LivingEntity current = mob.getTarget();
        double holdScanRadius = SquadOrders.holdScanRadius();
        double holdLeashRadius = SquadOrders.holdLeashRadius();
        double holdScanDistSqr = holdScanRadius * holdScanRadius;
        double holdLeashDistSqr = holdLeashRadius * holdLeashRadius;

        boolean validCurrent = current != null
                && SquadOrders.isValidTarget(player, mob, current)
                && current.blockPosition().distSqr(holdPos) <= holdScanDistSqr;

        if (validCurrent) {
            if (mob.blockPosition().distSqr(holdPos) > holdLeashDistSqr) {
                SquadOrders.clearCombatState(mob);
                moveToHoldPos(mob, holdPos);
            }
            return;
        }

        LivingEntity bestTarget = findBestHoldTarget(player, mob, holdPos);
        if (bestTarget != null) {
            mob.setTarget(bestTarget);
            RecruitCombatMovement.applyCombatMovement(mob, bestTarget, 1.1D, 0.95D, 10.0D * 10.0D);
            return;
        }

        SquadOrders.clearCombatState(mob);

        if (mob.blockPosition().distSqr(holdPos) > holdLeashDistSqr) {
            moveToHoldPos(mob, holdPos);
        } else {
            tickHoldWander(mob, holdPos);
        }
    }

    static LivingEntity findBestHoldTarget(ServerPlayer player, Mob mob, BlockPos holdPos) {
        List<LivingEntity> nearbyTargets = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                new AABB(holdPos).inflate(SquadOrders.holdScanRadius()),
                entity -> entity != player && entity != mob && SquadOrders.isValidTarget(player, mob, entity)
        );

        LivingEntity bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (LivingEntity entity : nearbyTargets) {
            double dist = entity.blockPosition().distSqr(holdPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = entity;
            }
        }

        return bestTarget;
    }

    static void moveToHoldPos(Mob mob, BlockPos holdPos) {
        mob.getNavigation().moveTo(
                holdPos.getX() + 0.5D,
                holdPos.getY(),
                holdPos.getZ() + 0.5D,
                1.1D
        );
    }

    static void tickHoldWander(Mob mob, BlockPos holdPos) {
        int cooldown = mob.getPersistentData().getInt(SquadOrders.TAG_HOLD_WANDER_COOLDOWN);

        if (cooldown > 0) {
            mob.getPersistentData().putInt(SquadOrders.TAG_HOLD_WANDER_COOLDOWN, Math.max(0, cooldown - 10));
            return;
        }

        if (mob.getNavigation().isInProgress()) return;

        int maxOffset = (int) SquadOrders.holdWanderRadius();
        int offsetX = mob.getRandom().nextInt(maxOffset * 2 + 1) - maxOffset;
        int offsetZ = mob.getRandom().nextInt(maxOffset * 2 + 1) - maxOffset;

        mob.getNavigation().moveTo(
                holdPos.getX() + 0.5D + offsetX,
                holdPos.getY(),
                holdPos.getZ() + 0.5D + offsetZ,
                0.9D
        );

        mob.getPersistentData().putInt(
                SquadOrders.TAG_HOLD_WANDER_COOLDOWN,
                40 + mob.getRandom().nextInt(40)
        );
    }

    /**
     * Catches settlement members that {@link WarbellVillageWanderGoal} can no longer help.
     *
     * <p>That goal maintains its own bed, workstation and navigation, but only runs while the bell
     * is still valid. A member whose bell got mined while it sat in an unloaded chunk ends up with
     * no goal willing to run and no event that ever reached it, so the release has to come from
     * outside, which is all this does.
     */
    static void tickVillageMobs(ServerPlayer player) {
        List<Mob> villageMobs = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RecruitmentEvents.CLEANUP_RADIUS * 2.0D),
                mob -> WarbellVillageData.isVillageMember(mob) && mob.isAlive()
        );

        for (Mob mob : villageMobs) {
            SettlementBridge.sanitizeVillageState(mob);
        }
    }

    private RecruitTickEvents() {
    }
}
