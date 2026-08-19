package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.Beast;
import net.randomcara.raidborn.content.item.utility.RaidBagItem;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.core.util.RaidbornAdvancements;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillagePathing;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageBedData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;
import net.randomcara.raidborn.world.settlement.SettlementSpawnMarkerEvents;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class RecruitInteractionEvents {
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!RecruitmentEvents.recruitmentEnabledFor(mob) || !RecruitmentEvents.isRecruitable(mob)) return;

        RecruitGoalInstaller.install(mob);

        if (!RecruitmentEvents.supportsVillageMode(mob) && WarbellVillageData.isVillageMember(mob)) {
            WarbellVillageData.resetMobFromVillage(mob);
        }

        RecruitmentEvents.sanitizeRecruitState(mob);

        if (RecruitmentEvents.supportsVillageMode(mob)) {
            SettlementBridge.sanitizeVillageState(mob);
        }

        RecruitTargeting.clearOwnerAsTarget(mob);

        if (RecruitmentEvents.supportsVillageMode(mob)) {
            SettlementBridge.clearProtectedPlayerTarget(mob);
        }
    }

    @SubscribeEvent
    public static void onBedRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockState clickedState = player.level().getBlockState(clickedPos);

        if (!(clickedState.getBlock() instanceof BedBlock)) return;

        final BlockPos normalizedClicked;
        if (clickedState.hasProperty(BedBlock.PART)
                && clickedState.hasProperty(BedBlock.FACING)
                && clickedState.getValue(BedBlock.PART) == BedPart.HEAD) {
            normalizedClicked = clickedPos.relative(clickedState.getValue(BedBlock.FACING).getOpposite());
        } else {
            normalizedClicked = clickedPos;
        }

        List<Mob> nearby = player.level().getEntitiesOfClass(
                Mob.class,
                new AABB(normalizedClicked).inflate(3.0D),
                mob -> mob.isAlive()
                        && WarbellVillageBedData.hasBed(mob)
                        && WarbellVillageBedData.isSameBed(mob, normalizedClicked)
        );

        boolean wokeSleepingIllager = false;

        for (Mob mob : nearby) {
            if (mob.isSleeping()) {
                WarbellVillageBedData.wakeUpAndStand(mob, RecruitmentEvents.BED_CLICK_WAKE_COOLDOWN);
                wokeSleepingIllager = true;
            }
        }

        if (wokeSleepingIllager) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onInteractBeast(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        if (!(event.getTarget() instanceof Mob beast) || !RecruitmentEvents.isBeast(beast)) return;

        ItemStack heldItem = player.getItemInHand(event.getHand());
        if (!heldItem.is(ModItems.VILLAGER_SOUL.get())) return;
        if (beast.getHealth() >= beast.getMaxHealth()) return;

        beast.heal(RecruitmentEvents.BEAST_SOUL_HEAL_AMOUNT);

        if (!player.getAbilities().instabuild) {
            heldItem.shrink(1);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onInteractRecruit(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide || !player.isShiftKeyDown()) {
            return;
        }

        Entity target = event.getTarget();
        boolean attemptRecruit = RecruitmentEvents.canAttemptRecruit(player);

        if (!(target instanceof Mob mob)) return;

        if (RecruitmentEvents.isBeast(mob) && player.getItemInHand(event.getHand()).is(ModItems.VILLAGER_SOUL.get())) {
            return;
        }

        if (!RecruitmentEvents.recruitmentEnabledFor(mob) || !RecruitmentEvents.isSpecialRecruitmentEnabled(mob) || !RecruitmentEvents.isRecruitable(mob)) {
            return;
        }

        if (!attemptRecruit) return;

        RecruitmentEvents.sanitizeRecruitState(mob);
        SettlementBridge.sanitizeVillageState(mob);

        UUID playerId = player.getUUID();
        boolean recruited = mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED);
        boolean hasOwner = mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);
        boolean villageMember = WarbellVillageData.isVillageMember(mob);

        int maxSlots = RecruitSlots.getMaxRecruitSlots(player);

        if (recruited && hasOwner && playerId.equals(mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER))) {
            RecruitmentEvents.releaseRecruit(mob);

            int used = RecruitSlots.countRecruitSlots(player, RecruitSlots.slotScanRadius());
            player.displayClientMessage(Component.literal("§cCompanion dismissed. (" + used + "/" + maxSlots + " slots)"), true);
        } else if (recruited && hasOwner) {
            player.displayClientMessage(Component.literal("§7This mob already serves another leader."), true);
        } else {
            if (RaidBagItem.playerHasStoredSquad(player)) {
                int storedCount = RaidBagItem.getStoredRecruitCount(player);
                int used = RecruitSlots.countRecruitSlots(player, RecruitSlots.slotScanRadius());
                player.displayClientMessage(
                        Component.literal("§7Your squad is stored in a Raid Bag (" + storedCount + " recruits, " + used + "/" + maxSlots + " slots used)."),
                        true
                );
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }

            int currentSlots = RecruitSlots.countRecruitSlots(player, RecruitSlots.slotScanRadius());
            int cost = RecruitSlots.getRecruitCost(mob);

            if (currentSlots + cost > maxSlots) {
                player.displayClientMessage(Component.literal("§cRecruit limit reached (" + currentSlots + "/" + maxSlots + " slots used)."), true);
            } else {
                if (villageMember) {
                    WarbellVillageData.resetMobFromVillage(mob);
                }

                mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, true);
                mob.getPersistentData().putUUID(FollowOwnerGoal.TAG_OWNER, playerId);

                if (mob instanceof Beast beast) {
                    beast.setCreatorUUID(playerId);
                }

                // A worldgen settlement illager stops belonging to that structure the moment it is
                // recruited, otherwise the return scan keeps hauling it back home mid-patrol.
                SettlementSpawnMarkerEvents.clearSettlementHome(mob);

                SquadOrders.setOrder(mob, SquadOrder.FOLLOW);

                RecruitmentEvents.setPatrolFlags(mob);
                RecruitTargeting.clearFollowTarget(mob);
                SquadOrders.clearCombatState(mob);
                RecruitGoalInstaller.install(mob);
                WarbellVillagePathing.applyVillageNavigation(mob);

                RaidbornAdvancements.award(player, RecruitmentEvents.ADV_RECRUIT_ILLAGER, RecruitmentEvents.CRIT_RECRUIT_ILLAGER);

                int after = currentSlots + cost;
                player.displayClientMessage(Component.literal("§aCompanion recruited! (" + after + "/" + maxSlots + " slots)"), true);
            }
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private RecruitInteractionEvents() {
    }
}
