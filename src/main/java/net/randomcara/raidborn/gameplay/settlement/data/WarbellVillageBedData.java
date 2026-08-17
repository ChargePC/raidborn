package net.randomcara.raidborn.gameplay.settlement.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.content.entity.grumblager.Grumblager;
import net.randomcara.raidborn.core.util.MobSleep;

import java.util.ArrayList;
import java.util.List;

public final class WarbellVillageBedData {
    public static final String TAG_BED_X = "raidborn_village_bed_x";
    public static final String TAG_BED_Y = "raidborn_village_bed_y";
    public static final String TAG_BED_Z = "raidborn_village_bed_z";
    public static final String TAG_BED_WAKE_COOLDOWN = "raidborn_bed_wake_cooldown";
    public static final String TAG_BED_WAKE_PROTECTION = "raidborn_bed_wake_protection";
    public static final String TAG_SLEEP_START_TIME = "raidborn_sleep_start_time";
    public static final String TAG_SLEEP_RETRY_COOLDOWN = "raidborn_sleep_retry_cooldown";
    public static final String TAG_BED_SEARCH_COOLDOWN = "raidborn_bed_search_cooldown";
    public static final String TAG_BED_SLOT = "raidborn_bed_slot";
    public static final String TAG_SLEEP_BLOCKED_UNTIL_TIME = "raidborn_sleep_blocked_until_time";

    public static final int MANUAL_WAKE_AWAKE_TICKS = 100;

    private static final int DEFAULT_WAKE_COOLDOWN = MANUAL_WAKE_AWAKE_TICKS;
    private static final int DEFAULT_WAKE_PROTECTION = 15;
    private static final int LOCAL_SEARCH_RADIUS = 24;

    private static final int BED_SLOT_FOOT = 0;
    private static final int BED_SLOT_HEAD = 1;

    private static final double SLEEPING_Y_OFFSET = 0.6875D;
    private static final double SLEEP_LOCK_XZ_TOLERANCE = 0.035D;
    private static final double SLEEP_LOCK_Y_TOLERANCE = 0.35D;

    private WarbellVillageBedData() {
    }

    public static void setBed(Mob mob, BlockPos pos) {
        BlockPos normalized = normalizeBedPos(mob, pos);
        if (normalized == null) normalized = pos;

        mob.getPersistentData().putInt(TAG_BED_X, normalized.getX());
        mob.getPersistentData().putInt(TAG_BED_Y, normalized.getY());
        mob.getPersistentData().putInt(TAG_BED_Z, normalized.getZ());

        refreshBedSlot(mob, normalized);
        setBedSearchCooldown(mob, 40);
    }

    public static boolean hasBed(Mob mob) {
        return mob.getPersistentData().contains(TAG_BED_X)
                && mob.getPersistentData().contains(TAG_BED_Y)
                && mob.getPersistentData().contains(TAG_BED_Z);
    }

    public static BlockPos getBedPos(Mob mob) {
        if (!hasBed(mob)) return null;

        return new BlockPos(
                mob.getPersistentData().getInt(TAG_BED_X),
                mob.getPersistentData().getInt(TAG_BED_Y),
                mob.getPersistentData().getInt(TAG_BED_Z)
        );
    }

    public static void clearBed(Mob mob) {
        mob.getPersistentData().remove(TAG_BED_X);
        mob.getPersistentData().remove(TAG_BED_Y);
        mob.getPersistentData().remove(TAG_BED_Z);
        mob.getPersistentData().remove(TAG_BED_SLOT);
        mob.getPersistentData().remove(TAG_BED_WAKE_COOLDOWN);
        mob.getPersistentData().remove(TAG_BED_WAKE_PROTECTION);
        mob.getPersistentData().remove(TAG_SLEEP_START_TIME);
        mob.getPersistentData().remove(TAG_SLEEP_RETRY_COOLDOWN);
        mob.getPersistentData().remove(TAG_BED_SEARCH_COOLDOWN);
        mob.getPersistentData().remove(TAG_SLEEP_BLOCKED_UNTIL_TIME);
    }

    public static boolean isBedValid(Mob mob) {
        BlockPos pos = getBedPos(mob);
        return pos != null && isCompleteBedAt(mob, pos);
    }

    public static boolean isCompleteBedAt(Mob mob, BlockPos pos) {
        BlockPos footPos = normalizeBedPos(mob, pos);
        if (footPos == null) return false;

        BlockState footState = mob.level().getBlockState(footPos);
        if (!(footState.getBlock() instanceof BedBlock)
                || !footState.hasProperty(BedBlock.PART)
                || !footState.hasProperty(BedBlock.FACING)
                || footState.getValue(BedBlock.PART) != BedPart.FOOT) {
            return false;
        }

        Direction facing = footState.getValue(BedBlock.FACING);
        BlockPos headPos = footPos.relative(facing);
        BlockState headState = mob.level().getBlockState(headPos);

        return headState.getBlock() instanceof BedBlock
                && headState.hasProperty(BedBlock.PART)
                && headState.hasProperty(BedBlock.FACING)
                && headState.getValue(BedBlock.PART) == BedPart.HEAD
                && headState.getValue(BedBlock.FACING) == facing;
    }

    public static BlockPos normalizeBedPos(Mob mob, BlockPos pos) {
        if (pos == null) return null;

        BlockState state = mob.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;

        if (state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
            BedPart part = state.getValue(BedBlock.PART);
            Direction facing = state.getValue(BedBlock.FACING);

            if (part == BedPart.HEAD) {
                return pos.relative(facing.getOpposite()).immutable();
            }
        }

        return pos.immutable();
    }

    public static BlockPos getBedHeadPos(Mob mob) {
        BlockPos pos = getBedPos(mob);
        return pos == null ? null : getBedHeadPos(mob, pos);
    }

    public static BlockPos getBedHeadPos(Mob mob, BlockPos pos) {
        BlockPos footPos = normalizeBedPos(mob, pos);
        if (footPos == null) return null;

        BlockState footState = mob.level().getBlockState(footPos);
        if (!(footState.getBlock() instanceof BedBlock)
                || !footState.hasProperty(BedBlock.PART)
                || !footState.hasProperty(BedBlock.FACING)
                || footState.getValue(BedBlock.PART) != BedPart.FOOT) {
            return null;
        }

        Direction facing = footState.getValue(BedBlock.FACING);
        BlockPos headPos = footPos.relative(facing);
        BlockState headState = mob.level().getBlockState(headPos);

        if (!(headState.getBlock() instanceof BedBlock)
                || !headState.hasProperty(BedBlock.PART)
                || !headState.hasProperty(BedBlock.FACING)
                || headState.getValue(BedBlock.PART) != BedPart.HEAD
                || headState.getValue(BedBlock.FACING) != facing) {
            return null;
        }

        return headPos.immutable();
    }

    public static boolean isSameBed(Mob mob, BlockPos pos) {
        BlockPos stored = getBedPos(mob);
        if (stored == null || pos == null) return false;

        BlockPos normalizedStored = normalizeBedPos(mob, stored);
        BlockPos normalizedTest = normalizeBedPos(mob, pos);
        return normalizedStored != null && normalizedStored.equals(normalizedTest);
    }

    public static void spawnLinkParticles(Mob mob, BlockPos bedPos) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;

        BlockPos normalized = normalizeBedPos(mob, bedPos);
        if (normalized == null) normalized = bedPos;

        serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                mob.getX(), mob.getY() + 1.0D, mob.getZ(),
                6,
                0.35D, 0.4D, 0.35D,
                0.0D
        );

        serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                normalized.getX() + 0.5D, normalized.getY() + 0.7D, normalized.getZ() + 0.5D,
                6,
                0.25D, 0.15D, 0.25D,
                0.0D
        );
    }

    public static int getWakeCooldown(Mob mob) {
        return isSleepBlocked(mob) ? getSleepBlockRemainingTicks(mob) : mob.getPersistentData().getInt(TAG_BED_WAKE_COOLDOWN);
    }

    public static void setWakeCooldown(Mob mob, int ticks) {
        mob.getPersistentData().putInt(TAG_BED_WAKE_COOLDOWN, Math.max(0, ticks));
    }

    public static void tickWakeCooldown(Mob mob, int amount) {
        if (amount <= 0 || isSleepBlocked(mob)) return;

        int current = mob.getPersistentData().getInt(TAG_BED_WAKE_COOLDOWN);
        if (current > 0) setWakeCooldown(mob, current - amount);
    }

    public static int getWakeProtection(Mob mob) {
        return mob.getPersistentData().getInt(TAG_BED_WAKE_PROTECTION);
    }

    public static void setWakeProtection(Mob mob, int ticks) {
        mob.getPersistentData().putInt(TAG_BED_WAKE_PROTECTION, Math.max(0, ticks));
    }

    public static void tickWakeProtection(Mob mob, int amount) {
        if (amount <= 0) return;

        int current = getWakeProtection(mob);
        if (current > 0) setWakeProtection(mob, current - amount);
    }

    public static void markSleepStart(Mob mob) {
        mob.getPersistentData().putLong(TAG_SLEEP_START_TIME, mob.level().getGameTime());
    }

    public static long getSleepTicks(Mob mob) {
        long start = mob.getPersistentData().getLong(TAG_SLEEP_START_TIME);
        return start <= 0L ? 0L : Math.max(0L, mob.level().getGameTime() - start);
    }

    public static void clearSleepStart(Mob mob) {
        mob.getPersistentData().remove(TAG_SLEEP_START_TIME);
    }

    public static int getSleepRetryCooldown(Mob mob) {
        return isSleepBlocked(mob) ? getSleepBlockRemainingTicks(mob) : mob.getPersistentData().getInt(TAG_SLEEP_RETRY_COOLDOWN);
    }

    public static void setSleepRetryCooldown(Mob mob, int ticks) {
        mob.getPersistentData().putInt(TAG_SLEEP_RETRY_COOLDOWN, Math.max(0, ticks));
    }

    public static void tickSleepRetryCooldown(Mob mob, int amount) {
        if (amount <= 0 || isSleepBlocked(mob)) return;

        int current = mob.getPersistentData().getInt(TAG_SLEEP_RETRY_COOLDOWN);
        if (current > 0) setSleepRetryCooldown(mob, current - amount);
    }

    public static int getBedSearchCooldown(Mob mob) {
        return mob.getPersistentData().getInt(TAG_BED_SEARCH_COOLDOWN);
    }

    public static void setBedSearchCooldown(Mob mob, int ticks) {
        mob.getPersistentData().putInt(TAG_BED_SEARCH_COOLDOWN, Math.max(0, ticks));
    }

    public static void tickBedSearchCooldown(Mob mob, int amount) {
        if (amount <= 0) return;

        int current = getBedSearchCooldown(mob);
        if (current > 0) setBedSearchCooldown(mob, current - amount);
    }

    public static void tickAllTimers(Mob mob, int amount) {
        tickWakeCooldown(mob, amount);
        tickWakeProtection(mob, amount);
        tickSleepRetryCooldown(mob, amount);
        tickBedSearchCooldown(mob, amount);
    }

    public static void blockSleepFor(Mob mob, int ticks) {
        if (mob == null || mob.level().isClientSide) return;

        int safeTicks = Math.max(0, ticks);
        long until = mob.level().getGameTime() + safeTicks;

        mob.getPersistentData().putLong(TAG_SLEEP_BLOCKED_UNTIL_TIME, until);
        setWakeCooldown(mob, safeTicks);
        setSleepRetryCooldown(mob, safeTicks);
    }

    public static boolean isSleepBlocked(Mob mob) {
        if (mob == null || mob.level().isClientSide) return false;

        long until = mob.getPersistentData().getLong(TAG_SLEEP_BLOCKED_UNTIL_TIME);
        if (until <= 0L) return false;

        long now = mob.level().getGameTime();
        if (now >= until) {
            mob.getPersistentData().remove(TAG_SLEEP_BLOCKED_UNTIL_TIME);
            setWakeCooldown(mob, 0);
            setSleepRetryCooldown(mob, 0);
            return false;
        }

        int remaining = (int) Math.max(1L, until - now);
        setWakeCooldown(mob, remaining);
        setSleepRetryCooldown(mob, remaining);
        return true;
    }

    public static int getSleepBlockRemainingTicks(Mob mob) {
        if (mob == null || mob.level().isClientSide) return 0;

        long until = mob.getPersistentData().getLong(TAG_SLEEP_BLOCKED_UNTIL_TIME);
        if (until <= 0L) return 0;

        long now = mob.level().getGameTime();
        return (int) Math.max(0L, until - now);
    }

    public static void wakeUpAndStand(Mob mob) {
        wakeUpAndStand(mob, MANUAL_WAKE_AWAKE_TICKS, DEFAULT_WAKE_PROTECTION);
    }

    public static void wakeUpAndStand(Mob mob, int wakeCooldownTicks) {
        wakeUpAndStand(mob, Math.max(MANUAL_WAKE_AWAKE_TICKS, wakeCooldownTicks), DEFAULT_WAKE_PROTECTION);
    }

    public static void wakeUpAndStand(Mob mob, int wakeCooldownTicks, int wakeProtectionTicks) {
        if (mob == null || mob.level().isClientSide) return;

        int finalWakeCooldown = Math.max(MANUAL_WAKE_AWAKE_TICKS, wakeCooldownTicks);
        BlockPos bedPos = getBedPos(mob);

        if (bedPos == null) {
            MobSleep.wake(mob);
            finishWakeUp(mob, wakeProtectionTicks, finalWakeCooldown);
            return;
        }

        BlockPos normalized = normalizeBedPos(mob, bedPos);
        if (normalized == null) normalized = bedPos;

        Direction facing = Direction.NORTH;
        BlockState state = mob.level().getBlockState(normalized);
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.FACING)) {
            facing = state.getValue(BedBlock.FACING);
        }

        MobSleep.wake(mob);

        BlockPos standPos = findSafeStandPos(mob, normalized, facing);
        mob.getNavigation().stop();

        if (standPos != null) {
            mob.teleportTo(standPos.getX() + 0.5D, standPos.getY(), standPos.getZ() + 0.5D);
        }

        finishWakeUp(mob, wakeProtectionTicks, finalWakeCooldown);
    }

    private static void finishWakeUp(Mob mob, int wakeProtectionTicks, int wakeCooldownTicks) {
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mob.fallDistance = 0.0F;

        clearSleepStart(mob);
        setWakeProtection(mob, wakeProtectionTicks);
        blockSleepFor(mob, wakeCooldownTicks);
    }

    public static void lockSleepingMobToBed(Mob mob) {
        if (mob == null || mob.level().isClientSide || !mob.isAlive() || !mob.isSleeping()) return;
        if (!hasBed(mob) || !isBedValid(mob)) return;

        BlockPos bedPos = getBedPos(mob);
        if (bedPos == null) return;

        BlockPos normalized = normalizeBedPos(mob, bedPos);
        if (normalized == null) return;

        BlockPos sleepPos = getAssignedSleepPos(mob, normalized);
        if (sleepPos == null) return;

        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mob.fallDistance = 0.0F;

        double targetX = sleepPos.getX() + 0.5D;
        double targetY = sleepPos.getY() + SLEEPING_Y_OFFSET;
        double targetZ = sleepPos.getZ() + 0.5D;

        boolean pushedOut = Math.abs(mob.getX() - targetX) > SLEEP_LOCK_XZ_TOLERANCE
                || Math.abs(mob.getZ() - targetZ) > SLEEP_LOCK_XZ_TOLERANCE
                || Math.abs(mob.getY() - targetY) > SLEEP_LOCK_Y_TOLERANCE;

        if (pushedOut) {
            mob.teleportTo(targetX, targetY, targetZ);
            mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
            mob.fallDistance = 0.0F;
        }
    }

    private static boolean isDoubleBedSleeper(Mob mob) {
        return mob instanceof Grumblager;
    }

    public static int getBedSlot(Mob mob) {
        int slot = mob.getPersistentData().getInt(TAG_BED_SLOT);
        return slot == BED_SLOT_FOOT ? BED_SLOT_FOOT : BED_SLOT_HEAD;
    }

    private static void setBedSlot(Mob mob, int slot) {
        mob.getPersistentData().putInt(TAG_BED_SLOT, slot == BED_SLOT_FOOT ? BED_SLOT_FOOT : BED_SLOT_HEAD);
    }

    private static List<Mob> getBedClaimants(Mob mob, BlockPos bedPos) {
        BlockPos normalized = normalizeBedPos(mob, bedPos);
        if (normalized == null || !(mob.level() instanceof ServerLevel serverLevel)) return List.of();

        return serverLevel.getEntitiesOfClass(
                Mob.class,
                new AABB(normalized).inflate(64.0D),
                other -> other != mob
                        && other.isAlive()
                        && WarbellVillageData.isVillageMode(other)
                        && hasBed(other)
                        && isBedValid(other)
                        && normalized.equals(getBedPos(other))
        );
    }

    public static void refreshBedSlot(Mob mob, BlockPos bedPos) {
        BlockPos normalized = normalizeBedPos(mob, bedPos);
        if (normalized == null) return;

        if (!isDoubleBedSleeper(mob)) {
            setBedSlot(mob, BED_SLOT_HEAD);
            return;
        }

        boolean footTaken = false;
        boolean headTaken = false;
        boolean blockedByNormalSleeper = false;

        for (Mob other : getBedClaimants(mob, normalized)) {
            if (!isDoubleBedSleeper(other)) {
                blockedByNormalSleeper = true;
                break;
            }

            if (getBedSlot(other) == BED_SLOT_FOOT) {
                footTaken = true;
            } else {
                headTaken = true;
            }
        }

        if (blockedByNormalSleeper) {
            setBedSlot(mob, BED_SLOT_HEAD);
            return;
        }

        int current = getBedSlot(mob);
        if (current == BED_SLOT_FOOT && !footTaken) return;
        if (current == BED_SLOT_HEAD && !headTaken) return;

        setBedSlot(mob, !footTaken ? BED_SLOT_FOOT : BED_SLOT_HEAD);
    }

    public static BlockPos getAssignedSleepPos(Mob mob, BlockPos pos) {
        BlockPos footPos = normalizeBedPos(mob, pos);
        if (footPos == null) return null;

        BlockPos headPos = getBedHeadPos(mob, footPos);
        if (headPos == null) return null;

        if (isDoubleBedSleeper(mob) && getBedSlot(mob) == BED_SLOT_FOOT) {
            return footPos;
        }

        return headPos;
    }

    private static List<BlockPos> buildBedInteractionCandidates(Mob mob, BlockPos footPos, BlockPos headPos, Direction facing) {
        List<BlockPos> candidates = new ArrayList<>();
        boolean useFootSlot = isDoubleBedSleeper(mob) && getBedSlot(mob) == BED_SLOT_FOOT;

        if (useFootSlot) {
            addCandidate(candidates, footPos.relative(facing.getClockWise()));
            addCandidate(candidates, footPos.relative(facing.getCounterClockWise()));
            addCandidate(candidates, footPos.relative(facing.getOpposite()));
            addCandidate(candidates, headPos.relative(facing.getClockWise()));
            addCandidate(candidates, headPos.relative(facing.getCounterClockWise()));
            addCandidate(candidates, headPos.relative(facing));
        } else {
            addCandidate(candidates, headPos.relative(facing.getClockWise()));
            addCandidate(candidates, headPos.relative(facing.getCounterClockWise()));
            addCandidate(candidates, headPos.relative(facing));
            addCandidate(candidates, footPos.relative(facing.getClockWise()));
            addCandidate(candidates, footPos.relative(facing.getCounterClockWise()));
            addCandidate(candidates, footPos.relative(facing.getOpposite()));
        }

        return candidates;
    }

    private static void addCandidate(List<BlockPos> candidates, BlockPos pos) {
        if (!candidates.contains(pos)) {
            candidates.add(pos.immutable());
        }
    }

    public static boolean isBedClaimedByOther(Mob mob, BlockPos bedPos) {
        List<Mob> claimants = getBedClaimants(mob, bedPos);
        if (claimants.isEmpty()) return false;
        if (!isDoubleBedSleeper(mob)) return true;

        for (Mob other : claimants) {
            if (!isDoubleBedSleeper(other)) return true;
        }

        return claimants.size() >= 2;
    }

    public static BlockPos findNearestFreeBed(Mob mob) {
        if (mob == null || !mob.isAlive()) return null;
        if (!WarbellVillageData.isVillageMode(mob) || !WarbellVillageData.isBellValid(mob)) return null;

        BlockPos bellPos = WarbellVillageData.getVillageBellPos(mob);
        if (bellPos == null) return null;

        int radius = WarbellVillageData.getVillageRadius(mob);
        int localRange = Math.min(radius, LOCAL_SEARCH_RADIUS);
        BlockPos mobPos = mob.blockPosition();

        BlockPos localMin = mobPos.offset(-localRange, -4, -localRange);
        BlockPos localMax = mobPos.offset(localRange, 4, localRange);
        BlockPos localResult = findBestFreeBedInBox(mob, bellPos, radius, localMin, localMax);
        if (localResult != null) return localResult;

        BlockPos fullMin = bellPos.offset(-radius, -WarbellVillageData.SEARCH_VERTICAL_RANGE, -radius);
        BlockPos fullMax = bellPos.offset(radius, WarbellVillageData.SEARCH_VERTICAL_RANGE, radius);
        return findBestFreeBedInBox(mob, bellPos, radius, fullMin, fullMax);
    }

    private static BlockPos findBestFreeBedInBox(Mob mob, BlockPos bellPos, int radius, BlockPos min, BlockPos max) {
        BlockPos bestBed = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = mob.level().getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) != BedPart.FOOT) continue;
            if (!WarbellVillageData.isInsideVillageRadius(bellPos, pos, radius)) continue;

            BlockPos normalized = normalizeBedPos(mob, pos);
            if (normalized == null || isBedClaimedByOther(mob, normalized)) continue;

            BlockPos interactionPos = findBestBedInteractionPos(mob, normalized);
            if (interactionPos == null) continue;

            double score = mob.blockPosition().distSqr(interactionPos)
                    + mob.blockPosition().distSqr(normalized) * 0.10D
                    + bellPos.distSqr(normalized) * 0.01D;

            if (score < bestScore) {
                bestScore = score;
                bestBed = normalized.immutable();
            }
        }

        return bestBed;
    }

    public static boolean findAndAssignNearestBed(Mob mob) {
        if (mob == null || !mob.isAlive()) return false;

        if (hasBed(mob) && isBedValid(mob)) {
            refreshBedSlot(mob, getBedPos(mob));
            return true;
        }

        if (getBedSearchCooldown(mob) > 0) return false;

        BlockPos foundBed = findNearestFreeBed(mob);
        if (foundBed == null) {
            setBedSearchCooldown(mob, 80 + mob.getRandom().nextInt(60));
            return false;
        }

        setBed(mob, foundBed);
        spawnLinkParticles(mob, foundBed);
        return true;
    }

    public static BlockPos findBestBedInteractionPos(Mob mob, BlockPos bedPos) {
        BlockPos footPos = normalizeBedPos(mob, bedPos);
        if (footPos == null) return null;

        BlockPos headPos = getBedHeadPos(mob, footPos);
        if (headPos == null) return null;

        refreshBedSlot(mob, footPos);

        BlockState footState = mob.level().getBlockState(footPos);
        Direction facing = Direction.NORTH;
        if (footState.getBlock() instanceof BedBlock && footState.hasProperty(BedBlock.FACING)) {
            facing = footState.getValue(BedBlock.FACING);
        }

        List<BlockPos> candidates = buildBedInteractionCandidates(mob, footPos, headPos, facing);
        BlockPos assignedSleepPos = getAssignedSleepPos(mob, footPos);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos candidate : candidates) {
            if (!canStandAt(mob, candidate)) continue;

            boolean closeEnough = mob.distanceToSqr(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D) <= 2.25D;
            if (!closeEnough) {
                var path = mob.getNavigation().createPath(candidate, 0);
                if (path == null || !path.canReach()) continue;
            }

            double score = mob.blockPosition().distSqr(candidate);
            if (assignedSleepPos != null) {
                score += candidate.distSqr(assignedSleepPos) * 0.45D;
            }

            if (score < bestScore) {
                bestScore = score;
                best = candidate.immutable();
            }
        }

        return best;
    }

    private static BlockPos findSafeStandPos(Mob mob, BlockPos bedPos, Direction facing) {
        BlockPos[] candidates = {
                bedPos.relative(facing.getOpposite()),
                bedPos.relative(facing.getClockWise()),
                bedPos.relative(facing.getCounterClockWise()),
                bedPos.relative(facing),
                bedPos.above(),
                bedPos
        };

        for (BlockPos candidate : candidates) {
            if (canStandAt(mob, candidate)) {
                return candidate.immutable();
            }
        }

        return bedPos.relative(facing.getOpposite()).immutable();
    }

    private static boolean canStandAt(Mob mob, BlockPos pos) {
        BlockPos below = pos.below();
        AABB box = mob.getDimensions(Pose.STANDING).makeBoundingBox(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D
        );

        return mob.level().getBlockState(below).entityCanStandOn(mob.level(), below, mob)
                && mob.level().noCollision(mob, box);
    }
}
