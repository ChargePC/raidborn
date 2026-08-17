package net.randomcara.raidborn.gameplay.settlement.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageBedData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageWorkstationData;

import java.util.EnumSet;

public class WarbellVillageSleepGoal extends Goal {
    private static final double BED_REACH_SQR = 0.48D * 0.48D;
    private static final double FINAL_APPROACH_START_SQR = 1.75D * 1.75D;
    private static final double MOVE_SPEED = 0.82D;
    private static final int REPATH_INTERVAL = 6;
    private static final int SLEEP_RETRY_COOLDOWN_TICKS = 20;
    private static final int STUCK_THRESHOLD = 24;

    private final Mob mob;
    private int repathCooldown;
    private int stuckTicks;
    private double lastDistanceSqr = Double.MAX_VALUE;

    public WarbellVillageSleepGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return !this.mob.level().isClientSide && shouldSleepNow();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.level().isClientSide) return false;
        if (!WarbellVillageData.isVillageMode(this.mob)) return false;
        if (WarbellVillageBedData.isSleepBlocked(this.mob)) return false;
        if (!WarbellVillageRoutine.shouldSleepNow(this.mob)) return false;
        if (this.mob.getTarget() != null) return false;

        return WarbellVillageBedData.hasBed(this.mob) && WarbellVillageBedData.isBedValid(this.mob);
    }

    @Override
    public void start() {
        resetMovement();
        WarbellVillagePathing.applyVillageNavigation(this.mob);
    }

    @Override
    public void tick() {
        tickTimers();
        WarbellVillagePathing.applyVillageNavigation(this.mob);

        if (WarbellVillageBedData.isSleepBlocked(this.mob)) {
            this.mob.getNavigation().stop();
            return;
        }

        if (!WarbellVillageRoutine.shouldSleepNow(this.mob)) return;

        BlockPos bedPos = getValidBedPos();
        if (bedPos == null) return;

        WarbellVillageBedData.refreshBedSlot(this.mob, bedPos);

        BlockPos sleepPos = WarbellVillageBedData.getAssignedSleepPos(this.mob, bedPos);
        if (sleepPos == null) {
            WarbellVillageBedData.setSleepRetryCooldown(this.mob, 40);
            return;
        }

        if (this.mob.isSleeping()) {
            WarbellVillageBedData.lockSleepingMobToBed(this.mob);
            return;
        }

        BlockPos entryPos = WarbellVillageBedData.findBestBedInteractionPos(this.mob, bedPos);
        if (entryPos == null) {
            WarbellVillageBedData.setSleepRetryCooldown(this.mob, 30);
            return;
        }

        moveToBedOrSleep(entryPos, sleepPos);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        resetMovement();

        if (!this.mob.isSleeping()) return;

        boolean shouldWake = this.mob.getTarget() != null
                || !WarbellVillageData.isVillageMode(this.mob)
                || !WarbellVillageBedData.hasBed(this.mob)
                || !WarbellVillageBedData.isBedValid(this.mob)
                || !WarbellVillageRoutine.shouldSleepNow(this.mob);

        if (shouldWake) {
            WarbellVillageBedData.wakeUpAndStand(this.mob, 100);
        }
    }

    private boolean shouldSleepNow() {
        if (!WarbellVillageData.isVillageMode(this.mob)) return false;
        if (WarbellVillageBedData.isSleepBlocked(this.mob)) return false;
        if (!WarbellVillageRoutine.shouldSleepNow(this.mob)) return false;
        if (this.mob.getTarget() != null) return false;
        if (WarbellVillageBedData.getWakeCooldown(this.mob) > 0) return false;
        if (WarbellVillageBedData.getSleepRetryCooldown(this.mob) > 0) return false;

        return WarbellVillageBedData.hasBed(this.mob) && WarbellVillageBedData.isBedValid(this.mob);
    }

    private void tickTimers() {
        WarbellVillageBedData.tickAllTimers(this.mob, 1);
        WarbellVillageWorkstationData.tickWorkSearchCooldown(this.mob, 1);
    }

    private BlockPos getValidBedPos() {
        BlockPos bedPos = WarbellVillageBedData.getBedPos(this.mob);
        if (bedPos == null) {
            WarbellVillageBedData.setSleepRetryCooldown(this.mob, 40);
            return null;
        }

        BlockPos normalized = WarbellVillageBedData.normalizeBedPos(this.mob, bedPos);
        if (normalized == null) {
            WarbellVillageBedData.clearBed(this.mob);
            WarbellVillageBedData.setSleepRetryCooldown(this.mob, 40);
        }

        return normalized;
    }

    private void moveToBedOrSleep(BlockPos entryPos, BlockPos sleepPos) {
        double entryX = entryPos.getX() + 0.5D;
        double entryY = entryPos.getY();
        double entryZ = entryPos.getZ() + 0.5D;

        double sleepX = sleepPos.getX() + 0.5D;
        double sleepY = sleepPos.getY() + 0.45D;
        double sleepZ = sleepPos.getZ() + 0.5D;

        this.mob.getLookControl().setLookAt(sleepX, sleepY, sleepZ, 25.0F, 25.0F);

        double distanceSqr = this.mob.distanceToSqr(entryX, entryY, entryZ);
        if (distanceSqr > BED_REACH_SQR || !isOnSleepEntry(entryPos)) {
            moveCloserToBedEntry(entryPos, entryX, entryY, entryZ, distanceSqr);
            return;
        }

        if (this.mob.getNavigation().isInProgress()) {
            this.mob.getNavigation().stop();
        }

        resetMovement();

        try {
            this.mob.startSleeping(sleepPos);
            WarbellVillageBedData.markSleepStart(this.mob);
            WarbellVillageBedData.lockSleepingMobToBed(this.mob);
        } catch (RuntimeException e) {
            // startSleeping writes BedBlock.OCCUPIED; a modded bed without that property throws.
            // Back off and let the villager try a different bed instead of retrying every tick.
            Raidborn.LOGGER.debug("{} could not sleep at {}", this.mob.getType(), sleepPos, e);
            WarbellVillageBedData.setSleepRetryCooldown(this.mob, SLEEP_RETRY_COOLDOWN_TICKS);
        }
    }

    private void moveCloserToBedEntry(BlockPos entryPos, double x, double y, double z, double distanceSqr) {
        boolean shouldRepath = this.repathCooldown-- <= 0 || !this.mob.getNavigation().isInProgress();

        if (shouldRepath) {
            boolean moving = this.mob.getNavigation().moveTo(x, y, z, MOVE_SPEED);
            this.repathCooldown = REPATH_INTERVAL;

            if (!moving && distanceSqr > FINAL_APPROACH_START_SQR) {
                WarbellVillageBedData.setSleepRetryCooldown(this.mob, 20);
                return;
            }
        }

        if (distanceSqr <= FINAL_APPROACH_START_SQR) {
            this.mob.getMoveControl().setWantedPosition(x, y, z, MOVE_SPEED);
        }

        if (distanceSqr >= this.lastDistanceSqr - 0.025D) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }

        this.lastDistanceSqr = distanceSqr;

        if (this.stuckTicks >= STUCK_THRESHOLD) {
            this.mob.getNavigation().stop();
            this.mob.getNavigation().moveTo(x, y, z, MOVE_SPEED);
            this.mob.getMoveControl().setWantedPosition(x, y, z, MOVE_SPEED);
            this.stuckTicks = 0;
            this.repathCooldown = 3;
        }
    }

    private boolean isOnSleepEntry(BlockPos entryPos) {
        BlockPos mobPos = this.mob.blockPosition();
        return mobPos.getX() == entryPos.getX()
                && mobPos.getZ() == entryPos.getZ()
                && Math.abs(mobPos.getY() - entryPos.getY()) <= 1;
    }

    private void resetMovement() {
        this.repathCooldown = 0;
        this.stuckTicks = 0;
        this.lastDistanceSqr = Double.MAX_VALUE;
    }
}
