package net.randomcara.raidborn.gameplay.settlement.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageBedData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageWorkstationData;

import java.util.EnumSet;

/**
 * The daily routine of a settled illager: sleep at night, stand at its workstation during the day,
 * wander inside the settlement the rest of the time.
 *
 * <p>Fighting is not part of the routine. {@link WarbellVillageDefence} answers that question first
 * every tick, and the routine only runs when there is no intruder.
 */
public class WarbellVillageWanderGoal extends Goal {
    private static final int MIN_WANDER_COOLDOWN = 40;
    private static final int MAX_WANDER_COOLDOWN = 90;
    private static final int REPATH_INTERVAL = 15;
    private static final int STUCK_THRESHOLD = 45;

    private static final double WORK_REACH_SQR = 1.85D * 1.85D;

    /*
     * Wanted travel speed in blocks per tick, converted into a goal speed modifier by
     * getVillageMoveSpeed. Stated this way because recruits are every illager in the game and their
     * base MOVEMENT_SPEED differs; a shared modifier would make a vindicator jog and a witch crawl.
     * The clamp keeps the conversion from turning into a sprint or a shuffle on the extremes.
     */
    private static final double WANDER_SPEED = 0.20D;
    private static final double RETURN_SPEED = 0.24D;
    private static final double WORK_SPEED = 0.19D;
    private static final double MIN_SPEED_MODIFIER = 0.68D;
    private static final double MAX_SPEED_MODIFIER = 1.02D;

    private final Mob mob;
    private final WarbellVillageDefence defence = new WarbellVillageDefence();

    private int repathCooldown;
    private int stuckTicks;
    private double lastDistanceToTarget = Double.MAX_VALUE;
    private BlockPos activeTargetPos;

    public WarbellVillageWanderGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !this.mob.level().isClientSide
                && WarbellVillageData.isVillageMode(this.mob)
                && WarbellVillageData.isBellValid(this.mob);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.level().isClientSide
                && WarbellVillageData.isVillageMode(this.mob)
                && WarbellVillageData.isBellValid(this.mob);
    }

    @Override
    public void start() {
        resetPathingState();
        this.defence.reset();
        WarbellVillagePathing.applyVillageNavigation(this.mob);
    }

    @Override
    public void tick() {
        tickTimers();
        WarbellVillagePathing.applyVillageNavigation(this.mob);

        if (!WarbellVillageData.isVillageMode(this.mob)) return;

        if (!WarbellVillageData.isBellValid(this.mob)) {
            WarbellVillageData.resetMobFromVillage(this.mob);
            return;
        }

        refreshBedAndWorkstation();

        BlockPos bellPos = WarbellVillageData.getVillageBellPos(this.mob);
        if (bellPos == null) {
            WarbellVillageData.resetMobFromVillage(this.mob);
            return;
        }

        LivingEntity intruder = this.defence.selectTarget(this.mob, bellPos);
        if (intruder != null) {
            if (this.mob.isSleeping()) {
                WarbellVillageBedData.wakeUpAndStand(this.mob, 100);
            }

            WarbellVillageDefence.approach(this.mob, intruder);
            return;
        }

        if (this.mob.isSleeping() && !WarbellVillageRoutine.shouldSleepNow(this.mob)) {
            WarbellVillageBedData.wakeUpAndStand(this.mob, 30);
        }

        tickRoutine(bellPos);
    }

    @Override
    public void stop() {
        resetPathingState();
        this.defence.reset();

        if (WarbellVillageData.isVillageMode(this.mob) && this.mob.getTarget() == null) {
            this.mob.getNavigation().stop();
        }
    }

    /** HOME and GATHER have no behaviour of their own yet: both come out as wandering. */
    private void tickRoutine(BlockPos bellPos) {
        WarbellVillageRoutine.Activity activity = WarbellVillageRoutine.getCurrentActivity(this.mob);

        if (activity == WarbellVillageRoutine.Activity.SLEEP) {
            this.mob.getNavigation().stop();
            resetPathingState();
            return;
        }

        if (activity == WarbellVillageRoutine.Activity.WORK && handleWorkActivity()) {
            return;
        }

        handleVillageWander(bellPos);
    }

    private void tickTimers() {
        WarbellVillageBedData.tickAllTimers(this.mob, 1);
        WarbellVillageWorkstationData.tickWorkSearchCooldown(this.mob, 1);
    }

    private double getVillageMoveSpeed(double targetSpeed) {
        double baseSpeed = Math.max(0.001D, this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double modifier = targetSpeed / baseSpeed;
        return Mth.clamp(modifier, MIN_SPEED_MODIFIER, MAX_SPEED_MODIFIER);
    }

    /** Beds and workstations are blocks: the player mines them, so the claim has to be re-checked. */
    private void refreshBedAndWorkstation() {
        if (WarbellVillageBedData.hasBed(this.mob) && !WarbellVillageBedData.isBedValid(this.mob)) {
            WarbellVillageBedData.clearBed(this.mob);
        }

        if (!WarbellVillageBedData.hasBed(this.mob)) {
            WarbellVillageBedData.findAndAssignNearestBed(this.mob);
        }

        if (!WarbellVillageWorkstationData.canUseWorkstation(this.mob)) {
            return;
        }

        if (WarbellVillageWorkstationData.hasWorkstation(this.mob)
                && !WarbellVillageWorkstationData.isWorkstationValid(this.mob)) {
            WarbellVillageWorkstationData.clearWorkstation(this.mob);
        }

        if (!WarbellVillageWorkstationData.hasWorkstation(this.mob)) {
            WarbellVillageWorkstationData.assignNearestWorkstation(this.mob);
        }
    }

    private boolean handleWorkActivity() {
        if (!WarbellVillageRoutine.isEmployed(this.mob)) return false;

        BlockPos workPos = WarbellVillageWorkstationData.getWorkstationPos(this.mob);
        if (workPos == null || !WarbellVillageWorkstationData.isWorkstationValid(this.mob)) return false;

        BlockPos interactionPos = WarbellVillageWorkstationData.findWorkstationStandPos(this.mob, workPos);
        if (interactionPos == null) return false;

        lookAtBlock(workPos);

        double targetX = interactionPos.getX() + 0.5D;
        double targetY = interactionPos.getY();
        double targetZ = interactionPos.getZ() + 0.5D;

        if (this.mob.distanceToSqr(targetX, targetY, targetZ) > WORK_REACH_SQR) {
            smartMoveTo(interactionPos, getVillageMoveSpeed(WORK_SPEED));
            return true;
        }

        this.mob.getNavigation().stop();
        resetPathingState();

        int cooldown = WarbellVillageData.getWanderCooldown(this.mob);
        if (cooldown > 0) {
            WarbellVillageData.setWanderCooldown(this.mob, cooldown - 1);
            return true;
        }

        if (!this.mob.getNavigation().isInProgress()) {
            this.mob.getNavigation().moveTo(targetX, targetY, targetZ, getVillageMoveSpeed(WORK_SPEED));
            WarbellVillageData.setWanderCooldown(this.mob, 25 + this.mob.getRandom().nextInt(20));
        }

        return true;
    }

    private void handleVillageWander(BlockPos bellPos) {
        if (bellPos == null) return;

        double centerX = bellPos.getX() + 0.5D;
        double centerY = bellPos.getY();
        double centerZ = bellPos.getZ() + 0.5D;

        int radius = WarbellVillageData.getVillageRadius(this.mob);
        double allowed = radius + WarbellVillageData.OUTSIDE_VILLAGE_BUFFER;

        if (this.mob.distanceToSqr(centerX, centerY, centerZ) > allowed * allowed) {
            smartMoveTo(bellPos, getVillageMoveSpeed(RETURN_SPEED));
            return;
        }

        int cooldown = WarbellVillageData.getWanderCooldown(this.mob);
        if (cooldown > 0) {
            WarbellVillageData.setWanderCooldown(this.mob, cooldown - 1);
            return;
        }

        if (this.mob.getNavigation().isInProgress()) return;

        if (!tryPickWanderTarget(bellPos, centerX, centerY, centerZ, radius)) {
            WarbellVillageData.setWanderCooldown(this.mob, 20);
        }
    }

    private boolean tryPickWanderTarget(BlockPos bellPos, double centerX, double centerY, double centerZ, int radius) {
        for (int i = 0; i < 12; i++) {
            int offsetX = this.mob.getRandom().nextInt(radius * 2 + 1) - radius;
            int offsetZ = this.mob.getRandom().nextInt(radius * 2 + 1) - radius;

            double targetX = centerX + offsetX;
            double targetZ = centerZ + offsetZ;

            if (distanceToCenterSqr(targetX, targetZ, centerX, centerZ) > (double) radius * radius) {
                continue;
            }

            if (this.mob.getNavigation().moveTo(targetX, centerY, targetZ, getVillageMoveSpeed(WANDER_SPEED))) {
                int cooldown = MIN_WANDER_COOLDOWN + this.mob.getRandom().nextInt(MAX_WANDER_COOLDOWN - MIN_WANDER_COOLDOWN + 1);
                WarbellVillageData.setWanderCooldown(this.mob, cooldown);
                resetPathingState();
                return true;
            }
        }

        return false;
    }

    /** Repaths on an interval, and again early when the distance to the goal stops shrinking. */
    private boolean smartMoveTo(BlockPos targetPos, double speed) {
        if (targetPos == null) return false;

        double distance = this.mob.blockPosition().distSqr(targetPos);

        if (targetPos.equals(this.activeTargetPos)) {
            this.stuckTicks = distance >= this.lastDistanceToTarget - 0.04D ? this.stuckTicks + 1 : 0;
        } else {
            this.activeTargetPos = targetPos.immutable();
            this.stuckTicks = 0;
        }

        this.lastDistanceToTarget = distance;

        if (this.repathCooldown-- > 0 && this.mob.getNavigation().isInProgress() && this.stuckTicks < STUCK_THRESHOLD) {
            return true;
        }

        this.mob.getNavigation().stop();

        boolean moved = this.mob.getNavigation().moveTo(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D,
                speed
        );

        this.repathCooldown = REPATH_INTERVAL;

        if (this.stuckTicks >= STUCK_THRESHOLD) {
            this.stuckTicks = 0;
        }

        return moved;
    }

    private void lookAtBlock(BlockPos pos) {
        this.mob.getLookControl().setLookAt(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                20.0F,
                20.0F
        );
    }

    private void resetPathingState() {
        this.repathCooldown = 0;
        this.stuckTicks = 0;
        this.lastDistanceToTarget = Double.MAX_VALUE;
        this.activeTargetPos = null;
    }

    private double distanceToCenterSqr(double x, double z, double centerX, double centerZ) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz;
    }
}
