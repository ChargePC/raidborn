package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModBlocks;
import net.randomcara.raidborn.core.util.MobSleep;
import net.randomcara.raidborn.core.util.RaidbornAdvancements;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillagePathing;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageBedData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageWorkstationData;

import java.util.List;

/** Converts a recruit into a settlement resident bound to a bell, and clears the link when it goes. */
@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class SettlementBridge {
    static final ResourceLocation ADV_BOUND_TO_THE_BELL =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "bound_to_the_bell");

    static final String CRIT_BOUND_TO_THE_BELL = "link_settlement_illager";

    static void convertRecruitToVillage(Mob mob, BlockPos bellPos, int radius) {
        MobSleep.wake(mob);

        mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, false);
        mob.getPersistentData().remove(FollowOwnerGoal.TAG_OWNER);

        WarbellVillageBedData.clearBed(mob);
        WarbellVillageWorkstationData.clearWorkstation(mob);
        SquadOrders.clearOrderData(mob);
        RecruitTargeting.clearFollowTarget(mob);
        WarbellVillageData.setVillageBell(mob, bellPos, radius);
        SquadOrders.clearCombatState(mob);
        WarbellVillagePathing.applyVillageNavigation(mob);
    }

    public static boolean isProtectedVillageTarget(Mob mob, LivingEntity target) {
        return target != null
                && WarbellVillageData.isVillageMode(mob)
                && target instanceof Player player
                && RecruitmentEvents.ownerHasAllianceEffect(player);
    }

    static void clearProtectedPlayerTarget(Mob mob) {
        if (!WarbellVillageData.isVillageMode(mob)) return;
        if (!isProtectedVillageTarget(mob, mob.getTarget())) return;

        // Revenge memory left intact for the same reason as recruits: see RecruitTargeting.
        RecruitTargeting.clearTargetKeepRevenge(mob);
    }

    /**
     * Re-checks everything a settlement member claims but does not own.
     *
     * <p>The bell, bed and workstation are all blocks and the player can mine any of them while the
     * mob is nowhere near. Breaking a bell fires {@link #clearVillageOnBrokenBell} for members
     * loaded around it, but one sitting in an unloaded chunk never hears, and there's no event at
     * all for a bed or workstation vanishing. So this runs whenever the member ticks near a player,
     * which is the earliest it could react anyway.
     *
     * <p>That's why this one is periodic and {@link RecruitmentEvents#sanitizeRecruitState} isn't.
     */
    static void sanitizeVillageState(Mob mob) {
        if (!WarbellVillageData.isVillageMember(mob)) return;

        if (mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)
                || mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)) {
            mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, false);
            mob.getPersistentData().remove(FollowOwnerGoal.TAG_OWNER);
            SquadOrders.clearOrderData(mob);
            RecruitTargeting.clearFollowTarget(mob);
        }

        if (!WarbellVillageData.hasVillageBell(mob) || !WarbellVillageData.isBellValid(mob)) {
            WarbellVillageData.resetMobFromVillage(mob);
            return;
        }

        if (WarbellVillageBedData.hasBed(mob) && !WarbellVillageBedData.isBedValid(mob)) {
            if (mob.isSleeping()) {
                WarbellVillageBedData.wakeUpAndStand(mob, RecruitmentEvents.BED_CLICK_WAKE_COOLDOWN);
            }

            WarbellVillageBedData.clearBed(mob);
        }

        if (WarbellVillageWorkstationData.hasWorkstation(mob)
                && !WarbellVillageWorkstationData.isWorkstationValid(mob)) {
            WarbellVillageWorkstationData.clearWorkstation(mob);
        }

        clearProtectedPlayerTarget(mob);
        WarbellVillagePathing.applyVillageNavigation(mob);
    }

    public static void tryActivateVillageMode(ServerPlayer player, BlockPos bellPos) {
        if (player == null || player.level().isClientSide) return;

        if (!RecruitmentEvents.canCommandRecruits(player)) {
            player.displayClientMessage(Component.literal("§7You cannot command recruits right now."), true);
            return;
        }

        AABB area = new AABB(bellPos).inflate(WarbellVillageData.ACTIVATION_SCAN_RADIUS);

        List<Mob> mobs = player.level().getEntitiesOfClass(
                Mob.class,
                area,
                mob -> RecruitOwnership.isYours(player, mob) && mob.isAlive() && RecruitmentEvents.isRecruitable(mob) && RecruitmentEvents.supportsVillageMode(mob)
        );

        int affected = 0;
        int radius = WarbellVillageData.getDefaultVillageRadius();

        for (Mob mob : mobs) {
            RecruitmentEvents.sanitizeRecruitState(mob);

            if (!RecruitOwnership.isYours(player, mob)) continue;

            RecruitTargeting.clearOwnerAsTarget(mob);
            convertRecruitToVillage(mob, bellPos, radius);
            RecruitGoalInstaller.installForVillage(mob);

            WarbellVillageBedData.findAndAssignNearestBed(mob);
            WarbellVillageWorkstationData.assignNearestWorkstation(mob);

            RecruitmentEvents.setPatrolFlags(mob);
            affected++;
        }

        if (affected > 0) {
            RaidbornAdvancements.award(player, ADV_BOUND_TO_THE_BELL, CRIT_BOUND_TO_THE_BELL);
            player.displayClientMessage(Component.literal("§aSettlement mode enabled for " + affected + " illager(s)."), true);
        } else {
            player.displayClientMessage(Component.literal("§7No valid recruited illagers found."), true);
        }
    }

    public static void clearVillageOnBrokenBell(Level level, BlockPos bellPos) {
        if (level == null || level.isClientSide) return;

        AABB area = new AABB(bellPos).inflate(WarbellVillageData.BREAK_CLEAR_RADIUS);

        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                area,
                mob -> WarbellVillageData.isVillageMember(mob) && WarbellVillageData.isLinkedToBell(mob, bellPos)
        );

        for (Mob mob : mobs) {
            WarbellVillageData.resetMobFromVillage(mob);
        }
    }

    static boolean isLinkedBrokenBed(BlockPos brokenPos, BlockState brokenState, Mob mob) {
        if (!(brokenState.getBlock() instanceof BedBlock) || !WarbellVillageBedData.hasBed(mob)) {
            return false;
        }

        BlockPos normalizedBroken = brokenPos;
        if (brokenState.hasProperty(BedBlock.PART)
                && brokenState.hasProperty(BedBlock.FACING)
                && brokenState.getValue(BedBlock.PART) == BedPart.HEAD) {
            normalizedBroken = brokenPos.relative(brokenState.getValue(BedBlock.FACING).getOpposite());
        }

        BlockPos linkedBed = WarbellVillageBedData.getBedPos(mob);
        return linkedBed != null && linkedBed.equals(normalizedBroken);
    }

    @SubscribeEvent
    public static void onVillageLinkedBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;

        BlockPos brokenPos = event.getPos();
        BlockState brokenState = event.getState();

        if (brokenState.is(ModBlocks.GRAND_WARBELL.get())) {
            clearVillageOnBrokenBell(level, brokenPos);
            return;
        }

        boolean bedBroken = brokenState.getBlock() instanceof BedBlock;
        boolean workstationBroken = WarbellVillageWorkstationData.isWorkBenchState(brokenState);
        if (!bedBroken && !workstationBroken) return;

        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                new AABB(brokenPos).inflate(WarbellVillageData.BREAK_CLEAR_RADIUS),
                mob -> WarbellVillageData.isVillageMode(mob) && mob.isAlive()
        );

        for (Mob mob : mobs) {
            boolean changed = false;

            if (bedBroken && isLinkedBrokenBed(brokenPos, brokenState, mob)) {
                if (mob.isSleeping()) {
                    WarbellVillageBedData.wakeUpAndStand(mob, RecruitmentEvents.BED_CLICK_WAKE_COOLDOWN);
                }

                WarbellVillageBedData.clearBed(mob);
                WarbellVillageBedData.setBedSearchCooldown(mob, 0);
                changed = true;
            }

            if (workstationBroken
                    && WarbellVillageWorkstationData.hasWorkstation(mob)
                    && brokenPos.equals(WarbellVillageWorkstationData.getWorkstationPos(mob))) {
                WarbellVillageWorkstationData.clearWorkstation(mob);
                WarbellVillageWorkstationData.setWorkSearchCooldown(mob, 0);
                changed = true;
            }

            if (changed) {
                mob.getNavigation().stop();
            }
        }
    }

    @SubscribeEvent
    public static void onVillageLinkedBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;

        BlockState placedState = event.getPlacedBlock();
        boolean bedPlaced = placedState.getBlock() instanceof BedBlock;
        boolean workstationPlaced = WarbellVillageWorkstationData.isWorkBenchState(placedState);
        if (!bedPlaced && !workstationPlaced) return;

        BlockPos placedPos = event.getPos();
        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                new AABB(placedPos).inflate(WarbellVillageData.BREAK_CLEAR_RADIUS),
                mob -> WarbellVillageData.isVillageMode(mob) && mob.isAlive()
        );

        for (Mob mob : mobs) {
            if (bedPlaced && (!WarbellVillageBedData.hasBed(mob) || !WarbellVillageBedData.isBedValid(mob))) {
                WarbellVillageBedData.setBedSearchCooldown(mob, 0);
            }

            if (workstationPlaced && (!WarbellVillageWorkstationData.hasWorkstation(mob) || !WarbellVillageWorkstationData.isWorkstationValid(mob))) {
                WarbellVillageWorkstationData.setWorkSearchCooldown(mob, 0);
            }
        }
    }

    private SettlementBridge() {
    }
}
