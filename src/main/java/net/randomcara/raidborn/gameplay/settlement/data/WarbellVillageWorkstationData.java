package net.randomcara.raidborn.gameplay.settlement.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.core.compat.RaidbornCompat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WarbellVillageWorkstationData {
    public static final String TAG_WORK_X = "raidborn_village_work_x";
    public static final String TAG_WORK_Y = "raidborn_village_work_y";
    public static final String TAG_WORK_Z = "raidborn_village_work_z";
    public static final String TAG_WORK_SEARCH_COOLDOWN = "raidborn_village_work_search_cooldown";

    private static final int LOCAL_SEARCH_RADIUS = 24;

    private static final String SANDR_MODID = RaidbornCompat.SAVAGE_AND_RAVAGE;
    private static final String IINV_MODID = RaidbornCompat.ILLAGER_INVASION;
    private static final String TAP_MODID = RaidbornCompat.TAKES_A_PILLAGE;
    private static final String GI_MODID = RaidbornCompat.GUARD_ILLAGERS;
    private static final String HR_MODID = RaidbornCompat.HUNTERS_RETURN;
    private static final String RNC_MODID = RaidbornCompat.RAVAGE_AND_CABBAGE;
    private static final String EWM_MODID = RaidbornCompat.ENCHANT_WITH_MOB;

    private static final ResourceLocation MC_PILLAGER = ResourceLocation.fromNamespaceAndPath("minecraft", "pillager");
    private static final ResourceLocation MC_ILLUSIONER = ResourceLocation.fromNamespaceAndPath("minecraft", "illusioner");

    private static final ResourceLocation SANDR_EXECUTIONER = ResourceLocation.fromNamespaceAndPath(SANDR_MODID, "executioner");
    private static final ResourceLocation SANDR_GRIEFER = ResourceLocation.fromNamespaceAndPath(SANDR_MODID, "griefer");
    private static final ResourceLocation SANDR_ICEOLOGER = ResourceLocation.fromNamespaceAndPath(SANDR_MODID, "iceologer");
    private static final ResourceLocation SANDR_TRICKSTER = ResourceLocation.fromNamespaceAndPath(SANDR_MODID, "trickster");

    private static final ResourceLocation IINV_PROVOKER = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "provoker");
    private static final ResourceLocation IINV_BASHER = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "basher");
    private static final ResourceLocation IINV_INQUISITOR = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "inquisitor");
    private static final ResourceLocation IINV_MARAUDER = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "marauder");
    private static final ResourceLocation IINV_ALCHEMIST = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "alchemist");
    private static final ResourceLocation IINV_ARCHIVIST = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "archivist");
    private static final ResourceLocation IINV_FIRECALLER = ResourceLocation.fromNamespaceAndPath(IINV_MODID, "firecaller");

    private static final ResourceLocation TAP_ARCHER = ResourceLocation.fromNamespaceAndPath(TAP_MODID, "archer");
    private static final ResourceLocation TAP_LEGIONER = ResourceLocation.fromNamespaceAndPath(TAP_MODID, "legioner");
    private static final ResourceLocation TAP_SKIRMISHER = ResourceLocation.fromNamespaceAndPath(TAP_MODID, "skirmisher");

    private static final ResourceLocation GI_GUARD = ResourceLocation.fromNamespaceAndPath(GI_MODID, "guard_illager");
    private static final ResourceLocation HR_HUNTER = ResourceLocation.fromNamespaceAndPath(HR_MODID, "hunter");
    private static final ResourceLocation RNC_CABBAGER = ResourceLocation.fromNamespaceAndPath(RNC_MODID, "cabbager");
    private static final ResourceLocation EWM_ENCHANTER = ResourceLocation.fromNamespaceAndPath(EWM_MODID, "enchanter");

    private WarbellVillageWorkstationData() {
    }

    public static void setWorkstation(Mob mob, BlockPos pos) {
        mob.getPersistentData().putInt(TAG_WORK_X, pos.getX());
        mob.getPersistentData().putInt(TAG_WORK_Y, pos.getY());
        mob.getPersistentData().putInt(TAG_WORK_Z, pos.getZ());
        setWorkSearchCooldown(mob, 40);
    }

    public static boolean hasWorkstation(Mob mob) {
        return mob.getPersistentData().contains(TAG_WORK_X)
                && mob.getPersistentData().contains(TAG_WORK_Y)
                && mob.getPersistentData().contains(TAG_WORK_Z);
    }

    public static BlockPos getWorkstationPos(Mob mob) {
        if (!hasWorkstation(mob)) return null;

        return new BlockPos(
                mob.getPersistentData().getInt(TAG_WORK_X),
                mob.getPersistentData().getInt(TAG_WORK_Y),
                mob.getPersistentData().getInt(TAG_WORK_Z)
        );
    }

    public static void clearWorkstation(Mob mob) {
        mob.getPersistentData().remove(TAG_WORK_X);
        mob.getPersistentData().remove(TAG_WORK_Y);
        mob.getPersistentData().remove(TAG_WORK_Z);
        mob.getPersistentData().remove(TAG_WORK_SEARCH_COOLDOWN);
    }

    public static boolean isWorkstationValid(Mob mob) {
        BlockPos pos = getWorkstationPos(mob);
        return pos != null && isValidWorkstationFor(mob, mob.level().getBlockState(pos));
    }

    public static boolean isSameWorkstation(Mob mob, BlockPos pos) {
        BlockPos stored = getWorkstationPos(mob);
        return stored != null && stored.equals(pos);
    }

    public static boolean isWorkBenchState(BlockState state) {
        if (state == null) return false;

        Block block = state.getBlock();
        return block == Blocks.FLETCHING_TABLE
                || block == Blocks.GRINDSTONE
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.BREWING_STAND
                || block == Blocks.LECTERN
                || block == Blocks.BARREL
                || block == Blocks.COMPOSTER
                || block instanceof CauldronBlock;
    }

    public static boolean isValidWorkstationFor(Mob mob, BlockState state) {
        if (mob == null || state == null) return false;

        Block expected = getRequiredWorkstationBlock(mob);
        if (expected != null) {
            return state.is(expected);
        }

        return shouldUseCauldron(mob) && state.getBlock() instanceof CauldronBlock;
    }

    public static boolean canUseWorkstation(Mob mob) {
        return getRequiredWorkstationBlock(mob) != null || shouldUseCauldron(mob);
    }

    public static void spawnLinkParticles(Mob mob, BlockPos workstationPos) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;

        serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                mob.getX(), mob.getY() + 1.0D, mob.getZ(),
                6,
                0.35D, 0.4D, 0.35D,
                0.0D
        );

        serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                workstationPos.getX() + 0.5D, workstationPos.getY() + 0.9D, workstationPos.getZ() + 0.5D,
                6,
                0.25D, 0.2D, 0.25D,
                0.0D
        );
    }

    public static boolean isWorkstationClaimedByOther(Mob mob, BlockPos workstationPos) {
        if (!(mob.level() instanceof ServerLevel serverLevel) || workstationPos == null) return false;

        List<Mob> nearby = serverLevel.getEntitiesOfClass(
                Mob.class,
                new AABB(workstationPos).inflate(64.0D),
                other -> other != mob
                        && other.isAlive()
                        && WarbellVillageData.isVillageMode(other)
                        && hasWorkstation(other)
                        && isWorkstationValid(other)
                        && workstationPos.equals(getWorkstationPos(other))
        );

        return !nearby.isEmpty();
    }

    public static int getWorkSearchCooldown(Mob mob) {
        return mob.getPersistentData().getInt(TAG_WORK_SEARCH_COOLDOWN);
    }

    public static void setWorkSearchCooldown(Mob mob, int ticks) {
        mob.getPersistentData().putInt(TAG_WORK_SEARCH_COOLDOWN, Math.max(0, ticks));
    }

    public static void tickWorkSearchCooldown(Mob mob, int amount) {
        if (amount <= 0) return;

        int current = getWorkSearchCooldown(mob);
        if (current > 0) setWorkSearchCooldown(mob, current - amount);
    }

    public static BlockPos findNearestFreeWorkstation(Mob mob) {
        if (mob == null || !mob.isAlive() || !canUseWorkstation(mob)) return null;
        if (!WarbellVillageData.isVillageMode(mob) || !WarbellVillageData.isBellValid(mob)) return null;

        BlockPos bellPos = WarbellVillageData.getVillageBellPos(mob);
        if (bellPos == null) return null;

        int radius = WarbellVillageData.getVillageRadius(mob);
        int localRange = Math.min(radius, LOCAL_SEARCH_RADIUS);
        BlockPos mobPos = mob.blockPosition();

        BlockPos localMin = mobPos.offset(-localRange, -4, -localRange);
        BlockPos localMax = mobPos.offset(localRange, 4, localRange);
        BlockPos localResult = findBestFreeWorkstationInBox(mob, bellPos, radius, localMin, localMax);
        if (localResult != null) return localResult;

        BlockPos fullMin = bellPos.offset(-radius, -WarbellVillageData.SEARCH_VERTICAL_RANGE, -radius);
        BlockPos fullMax = bellPos.offset(radius, WarbellVillageData.SEARCH_VERTICAL_RANGE, radius);
        return findBestFreeWorkstationInBox(mob, bellPos, radius, fullMin, fullMax);
    }

    private static BlockPos findBestFreeWorkstationInBox(Mob mob, BlockPos bellPos, int radius, BlockPos min, BlockPos max) {
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = mob.level().getBlockState(pos);

            if (!WarbellVillageData.isInsideVillageRadius(bellPos, pos, radius)) continue;
            if (!isValidWorkstationFor(mob, state) || isWorkstationClaimedByOther(mob, pos)) continue;

            BlockPos interactionPos = findWorkstationStandPos(mob, pos);
            if (interactionPos == null) continue;

            double score = mob.blockPosition().distSqr(interactionPos)
                    + mob.blockPosition().distSqr(pos) * 0.10D
                    + bellPos.distSqr(pos) * 0.01D;

            if (score < bestScore) {
                bestScore = score;
                bestPos = pos.immutable();
            }
        }

        return bestPos;
    }

    public static boolean assignNearestWorkstation(Mob mob) {
        if (mob == null || !mob.isAlive() || !canUseWorkstation(mob)) return false;

        if (hasWorkstation(mob) && isWorkstationValid(mob)) {
            return true;
        }

        if (getWorkSearchCooldown(mob) > 0) return false;

        BlockPos found = findNearestFreeWorkstation(mob);
        if (found == null) {
            setWorkSearchCooldown(mob, 80 + mob.getRandom().nextInt(60));
            return false;
        }

        setWorkstation(mob, found);
        spawnLinkParticles(mob, found);
        return true;
    }

    public static BlockPos findWorkstationStandPos(Mob mob, BlockPos workstationPos) {
        if (workstationPos == null) return null;

        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(workstationPos.relative(direction));
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos candidate : candidates) {
            if (!canStandAt(mob, candidate)) continue;

            var path = mob.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) continue;

            double score = mob.blockPosition().distSqr(candidate);
            if (score < bestScore) {
                bestScore = score;
                best = candidate.immutable();
            }
        }

        return best;
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

    @Nullable
    private static Block getRequiredWorkstationBlock(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id == null) return null;

        if (id.equals(MC_PILLAGER)
                || id.equals(IINV_PROVOKER)
                || id.equals(TAP_ARCHER)
                || id.equals(HR_HUNTER)) {
            return Blocks.FLETCHING_TABLE;
        }

        if (mob instanceof Vindicator
                || id.equals(SANDR_EXECUTIONER)
                || id.equals(IINV_BASHER)
                || id.equals(IINV_INQUISITOR)
                || id.equals(IINV_MARAUDER)
                || id.equals(TAP_LEGIONER)
                || id.equals(TAP_SKIRMISHER)
                || id.equals(GI_GUARD)) {
            return Blocks.GRINDSTONE;
        }

        if (mob instanceof Evoker
                || id.equals(SANDR_ICEOLOGER)
                || id.equals(IINV_FIRECALLER)
                || id.equals(EWM_ENCHANTER)) {
            return Blocks.ENCHANTING_TABLE;
        }

        if (id.equals(MC_ILLUSIONER)
                || id.equals(SANDR_TRICKSTER)
                || id.equals(IINV_ALCHEMIST)) {
            return Blocks.BREWING_STAND;
        }

        if (id.equals(SANDR_GRIEFER)) return Blocks.BARREL;
        if (id.equals(IINV_ARCHIVIST)) return Blocks.LECTERN;
        if (id.equals(RNC_CABBAGER)) return Blocks.COMPOSTER;

        return null;
    }

    private static boolean shouldUseCauldron(Mob mob) {
        return mob instanceof Witch;
    }
}
