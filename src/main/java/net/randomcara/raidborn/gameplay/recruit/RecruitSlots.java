package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Evoker;
import net.randomcara.raidborn.content.artifact.item.RaidbornNecklaceItem;
import net.randomcara.raidborn.content.item.utility.RaidBagItem;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;

import java.util.List;

public final class RecruitSlots {
    /** Slot counting radius from the [squad] config. A recruit farther than this frees its slot. */
    static double slotScanRadius() {
        return RaidbornServerConfig.getSquadSlotScanRadius();
    }

    public static int getRecruitCost(Entity entity) {
        if (RecruitmentEvents.isBeast(entity)) return 5;

        ResourceLocation id = RecruitmentEvents.getEntityId(entity);
        if (id == null) return 1;
        if (entity instanceof Evoker) return 5;

        if (id.equals(RaidbornCompatEntities.SANDR_ICEOLOGER)) return 3;
        if (id.equals(RaidbornCompatEntities.SANDR_TRICKSTER)) return 3;
        if (id.equals(RaidbornCompatEntities.IINV_ARCHIVIST)) return 3;
        if (id.equals(RaidbornCompatEntities.IINV_FIRECALLER)) return 3;
        if (id.equals(RaidbornCompatEntities.EWM_ENCHANTER)) return 3;
        if (RaidbornCompatEntities.sandrLoaded() && id.equals(RaidbornCompatEntities.SANDR_EXECUTIONER)) return 2;
        if (RaidbornCompatEntities.iinvLoaded() && id.equals(RaidbornCompatEntities.IINV_INQUISITOR)) return 3;
        if (RaidbornCompatEntities.tapLoaded() && id.equals(RaidbornCompatEntities.TAP_LEGIONER)) return 2;

        return 1;
    }

    static int countRecruitSlots(ServerPlayer player, double radius) {
        List<Mob> mobs = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(radius),
                mob -> RecruitOwnership.isYours(player, mob)
        );

        int slots = 0;
        for (Mob mob : mobs) {
            slots += getRecruitCost(mob);
        }

        slots += RaidBagItem.getStoredRecruitSlots(player);
        return slots;
    }

    static int getMaxRecruitSlots(ServerPlayer player) {
        int baseSlots;

        if (player.hasEffect(ModEffects.HERO_OF_THE_RAID.get())) {
            baseSlots = RaidbornServerConfig.getHeroRecruitSlots();
        } else if (player.hasEffect(ModEffects.ILLAGER_HONOR.get())) {
            baseSlots = RaidbornServerConfig.getHonorRecruitSlots();
        } else if (player.hasEffect(ModEffects.ILLAGER_LOYALTY.get())) {
            baseSlots = RaidbornServerConfig.getLoyaltyRecruitSlots();
        } else {
            baseSlots = 0;
        }

        return baseSlots + RaidbornNecklaceItem.getEquippedBonusRecruitSlots(player);
    }

    static void enforceRecruitSlotLimit(ServerPlayer player) {
        if (!RecruitmentEvents.ownerHasAllianceEffect(player)) {
            return;
        }

        int maxSlots = getMaxRecruitSlots(player);
        int usedSlots = countRecruitSlots(player, slotScanRadius());

        if (usedSlots <= maxSlots) {
            return;
        }

        List<Mob> recruits = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(slotScanRadius()),
                mob -> RecruitOwnership.isYours(player, mob) && mob.isAlive()
        );

        if (recruits.isEmpty()) {
            return;
        }

        recruits.sort((a, b) -> Double.compare(
                b.distanceToSqr(player),
                a.distanceToSqr(player)
        ));

        int releasedCount = 0;

        for (Mob mob : recruits) {
            if (usedSlots <= maxSlots) {
                break;
            }

            int cost = getRecruitCost(mob);
            RecruitmentEvents.releaseRecruit(mob);
            usedSlots -= cost;
            releasedCount++;
        }

        if (releasedCount > 0) {
            int finalUsed = countRecruitSlots(player, slotScanRadius());

            player.displayClientMessage(
                    Component.literal("§cRecruit limit exceeded. " + releasedCount + " companion(s) dismissed. (" + finalUsed + "/" + maxSlots + " slots)"),
                    true
            );
        }
    }

    private RecruitSlots() {
    }
}
