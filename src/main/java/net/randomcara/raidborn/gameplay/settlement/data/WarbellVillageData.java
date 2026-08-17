package net.randomcara.raidborn.gameplay.settlement.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModBlocks;
import net.randomcara.raidborn.core.util.MobSleep;
import net.randomcara.raidborn.gameplay.recruit.SquadOrders;

public final class WarbellVillageData {
    public static final String TAG_VILLAGE_MEMBER = "raidborn_village_member";
    public static final String TAG_BELL_X = "raidborn_village_bell_x";
    public static final String TAG_BELL_Y = "raidborn_village_bell_y";
    public static final String TAG_BELL_Z = "raidborn_village_bell_z";
    public static final String TAG_VILLAGE_RADIUS = "raidborn_village_radius";
    public static final String TAG_VILLAGE_WANDER_COOLDOWN = "raidborn_village_wander_cooldown";

    public static final int MIN_VILLAGE_RADIUS = 16;
    public static final int DEFAULT_VILLAGE_RADIUS = 48;
    public static final int MAX_VILLAGE_RADIUS = 96;
    public static final int SEARCH_VERTICAL_RANGE = 6;

    public static final double ACTIVATION_SCAN_RADIUS = 96.0D;
    public static final double BREAK_CLEAR_RADIUS = 128.0D;
    public static final double OUTSIDE_VILLAGE_BUFFER = 6.0D;
    public static final double COMBAT_LEASH_BUFFER = 14.0D;

    private WarbellVillageData() {
    }

    public static int getDefaultVillageRadius() {
        return RaidbornServerConfig.getSettlementDefaultRadius();
    }

    public static int getMaxVillageRadius() {
        return RaidbornServerConfig.getSettlementMaxRadius();
    }

    public static boolean isVillageMember(Mob mob) {
        return mob.getPersistentData().getBoolean(TAG_VILLAGE_MEMBER);
    }

    public static void setVillageBell(Mob mob, BlockPos pos, int radius) {
        int finalRadius = Math.max(MIN_VILLAGE_RADIUS, Math.min(getMaxVillageRadius(), radius));

        mob.getPersistentData().putBoolean(TAG_VILLAGE_MEMBER, true);
        mob.getPersistentData().putInt(TAG_BELL_X, pos.getX());
        mob.getPersistentData().putInt(TAG_BELL_Y, pos.getY());
        mob.getPersistentData().putInt(TAG_BELL_Z, pos.getZ());
        mob.getPersistentData().putInt(TAG_VILLAGE_RADIUS, finalRadius);
        mob.getPersistentData().putInt(TAG_VILLAGE_WANDER_COOLDOWN, 0);

        SquadOrders.clearCombatState(mob);
        applyVillageNavigation(mob);
    }

    public static boolean hasVillageBell(Mob mob) {
        return mob.getPersistentData().contains(TAG_BELL_X)
                && mob.getPersistentData().contains(TAG_BELL_Y)
                && mob.getPersistentData().contains(TAG_BELL_Z);
    }

    public static BlockPos getVillageBellPos(Mob mob) {
        if (!hasVillageBell(mob)) return null;

        return new BlockPos(
                mob.getPersistentData().getInt(TAG_BELL_X),
                mob.getPersistentData().getInt(TAG_BELL_Y),
                mob.getPersistentData().getInt(TAG_BELL_Z)
        );
    }

    public static int getVillageRadius(Mob mob) {
        int radius = mob.getPersistentData().getInt(TAG_VILLAGE_RADIUS);
        if (radius <= 0) return getDefaultVillageRadius();

        return Math.max(MIN_VILLAGE_RADIUS, Math.min(getMaxVillageRadius(), radius));
    }

    public static int getWanderCooldown(Mob mob) {
        return mob.getPersistentData().getInt(TAG_VILLAGE_WANDER_COOLDOWN);
    }

    public static void setWanderCooldown(Mob mob, int ticks) {
        mob.getPersistentData().putInt(TAG_VILLAGE_WANDER_COOLDOWN, Math.max(0, ticks));
    }

    public static void tickWanderCooldown(Mob mob, int amount) {
        if (amount <= 0) return;

        int current = getWanderCooldown(mob);
        if (current > 0) setWanderCooldown(mob, current - amount);
    }

    public static void clearVillageData(Mob mob) {
        mob.getPersistentData().remove(TAG_VILLAGE_MEMBER);
        mob.getPersistentData().remove(TAG_BELL_X);
        mob.getPersistentData().remove(TAG_BELL_Y);
        mob.getPersistentData().remove(TAG_BELL_Z);
        mob.getPersistentData().remove(TAG_VILLAGE_RADIUS);
        mob.getPersistentData().remove(TAG_VILLAGE_WANDER_COOLDOWN);

        applyVillageNavigation(mob);
    }

    public static void resetMobFromVillage(Mob mob) {
        MobSleep.wake(mob);

        WarbellVillageBedData.clearBed(mob);
        WarbellVillageWorkstationData.clearWorkstation(mob);
        clearVillageData(mob);
        SquadOrders.clearCombatState(mob);
    }

    public static boolean isVillageMode(Mob mob) {
        return isVillageMember(mob) && hasVillageBell(mob);
    }

    public static boolean isLinkedToBell(Mob mob, BlockPos bellPos) {
        BlockPos stored = getVillageBellPos(mob);
        return stored != null && stored.equals(bellPos);
    }

    public static boolean isBellValid(Mob mob) {
        BlockPos bellPos = getVillageBellPos(mob);
        if (bellPos == null) return false;

        BlockState state = mob.level().getBlockState(bellPos);
        return state.is(ModBlocks.GRAND_WARBELL.get());
    }

    public static boolean isInsideVillageRadius(BlockPos center, BlockPos testPos, int radius) {
        return center != null && testPos != null && center.distSqr(testPos) <= (double) radius * radius;
    }

    public static boolean isMobOutsideVillage(Mob mob, double extraBuffer) {
        BlockPos bellPos = getVillageBellPos(mob);
        if (bellPos == null) return false;

        double centerX = bellPos.getX() + 0.5D;
        double centerY = bellPos.getY();
        double centerZ = bellPos.getZ() + 0.5D;
        double allowed = getVillageRadius(mob) + Math.max(0.0D, extraBuffer);

        return mob.distanceToSqr(centerX, centerY, centerZ) > allowed * allowed;
    }

    public static boolean isPositionOutsideVillage(BlockPos bellPos, int radius, BlockPos pos, double extraBuffer) {
        if (bellPos == null || pos == null) return true;

        double allowed = radius + Math.max(0.0D, extraBuffer);
        return bellPos.distSqr(pos) > allowed * allowed;
    }

    public static void applyVillageNavigation(Mob mob) {
        if (!(mob.getNavigation() instanceof GroundPathNavigation navigation)) return;

        boolean villageMode = isVillageMode(mob);
        navigation.setCanOpenDoors(villageMode);
        navigation.setCanPassDoors(villageMode);
    }
}
