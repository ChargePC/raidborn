package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.core.util.MobSleep;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillagePathing;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageBedData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageWorkstationData;

import java.util.List;

public final class RecruitmentEvents {
    static final double DISBAND_RADIUS = 192.0D;

    static final double COMMAND_RADIUS = 32.0D;

    static final double CLEANUP_RADIUS = 64.0D;

    static final int RECRUIT_SYNC_INTERVAL = 2;

    static final int VILLAGE_SYNC_INTERVAL = 10;

    static final int BED_CLICK_WAKE_COOLDOWN = 20 * 5;

    static final String TAG_HAD_HONOR = "raidborn_had_honor";

    static final ResourceLocation ADV_RECRUIT_ILLAGER =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "you_work_for_me_now");

    static final String CRIT_RECRUIT_ILLAGER = "recruit_first_illager";

    static final ResourceLocation BEAST_ENTITY_ID =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "beast");

    static final float BEAST_SOUL_HEAL_AMOUNT = 25.0F;

    static ResourceLocation getEntityId(Entity entity) {
        return ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
    }

    static boolean recruitmentEnabledFor(Entity entity) {
        return RaidbornServerConfig.isRecruitmentEnabledFor(getEntityId(entity));
    }

    /**
     * Whether the hover tooltip should offer this mob as recruitable.
     *
     * <p>Reads the same rules the recruitment itself uses, so the tooltip cannot promise a recruit
     * the config or a missing integration would refuse.
     */
    public static boolean isRecruitableTooltipTarget(Entity entity) {
        return recruitmentEnabledFor(entity) && isRecruitable(entity);
    }

    static boolean isSpecialRecruitmentEnabled(Entity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return true;

        if (id.equals(RaidbornCompatEntities.SANDR_ICEOLOGER)) return RaidbornServerConfig.isIceologerRecruitable();
        if (id.equals(RaidbornCompatEntities.SANDR_TRICKSTER)) return RaidbornServerConfig.isTricksterRecruitable();
        if (id.equals(RaidbornCompatEntities.IINV_ARCHIVIST)) return RaidbornServerConfig.isArchivistRecruitable();
        if (id.equals(RaidbornCompatEntities.IINV_FIRECALLER)) return RaidbornServerConfig.isFirecallerRecruitable();

        return true;
    }

    static boolean isBeast(Entity entity) {
        ResourceLocation id = getEntityId(entity);
        return id != null && id.equals(BEAST_ENTITY_ID);
    }

    static boolean supportsVillageMode(Mob mob) {
        return !isBeast(mob);
    }

    static boolean isRecruitable(Entity entity) {
        if (isBeast(entity)) return true;
        if (entity instanceof net.minecraft.world.entity.monster.Ravager) return false;
        if (entity instanceof Witch) return RaidbornServerConfig.isWitchRecruitable();
        if (!isSpecialRecruitmentEnabled(entity)) return false;

        ResourceLocation id = getEntityId(entity);

        if (RaidbornCompatEntities.conjLoaded() && id != null && id.equals(RaidbornCompatEntities.CONJ_CONJURER)) return false;

        if (RaidbornCompatEntities.sandrLoaded() && id != null) {
            if (id.equals(RaidbornCompatEntities.SANDR_ICEOLOGER)) return true;
            if (id.equals(RaidbornCompatEntities.SANDR_TRICKSTER)) return true;
            if (id.equals(RaidbornCompatEntities.SANDR_EXECUTIONER)) return true;
            if (id.equals(RaidbornCompatEntities.SANDR_GRIEFER)) return true;
        }

        if (RaidbornCompatEntities.iinvLoaded() && id != null && RaidbornCompatEntities.IINV_MODID.equals(id.getNamespace())) {
            if (id.equals(RaidbornCompatEntities.IINV_PROVOKER)) return true;
            if (id.equals(RaidbornCompatEntities.IINV_BASHER)) return true;
            if (id.equals(RaidbornCompatEntities.IINV_INQUISITOR)) return true;
            if (id.equals(RaidbornCompatEntities.IINV_MARAUDER)) return true;
            if (id.equals(RaidbornCompatEntities.IINV_ALCHEMIST)) return true;
            if (id.equals(RaidbornCompatEntities.IINV_ARCHIVIST)) return true;
            if (id.equals(RaidbornCompatEntities.IINV_FIRECALLER)) return true;
            return false;
        }

        if (RaidbornCompatEntities.tapLoaded() && id != null && RaidbornCompatEntities.TAP_MODID.equals(id.getNamespace())) {
            if (id.equals(RaidbornCompatEntities.TAP_ARCHER)) return true;
            if (id.equals(RaidbornCompatEntities.TAP_LEGIONER)) return true;
            if (id.equals(RaidbornCompatEntities.TAP_SKIRMISHER)) return true;
            return false;
        }

        if (RaidbornCompatEntities.ewmLoaded() && id != null && id.equals(RaidbornCompatEntities.EWM_ENCHANTER)) return true;
        if (RaidbornCompatEntities.giLoaded() && id != null && id.equals(RaidbornCompatEntities.GI_GUARD)) return true;
        if (RaidbornCompatEntities.hrLoaded() && id != null && id.equals(RaidbornCompatEntities.HR_HUNTER)) return true;

        return entity instanceof AbstractIllager;
    }

    static boolean ownerHasAllianceEffect(Player player) {
        return player != null && ModEffects.hasAllianceEffect(player);
    }

    static boolean ownerHasAllianceEffect(ServerPlayer player) {
        return ownerHasAllianceEffect((Player) player);
    }

    static void releaseRecruit(Mob mob) {
        MobSleep.wake(mob);

        WarbellVillageData.clearVillageData(mob);
        WarbellVillageBedData.clearBed(mob);
        WarbellVillageWorkstationData.clearWorkstation(mob);
        mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, false);
        mob.getPersistentData().remove(FollowOwnerGoal.TAG_OWNER);
        SquadOrders.clearOrderData(mob);
        RecruitTargeting.clearFollowTarget(mob);
        SquadOrders.clearCombatState(mob);
        WarbellVillagePathing.applyVillageNavigation(mob);
    }

    public static void disbandSquad(ServerPlayer player) {
        List<Mob> mobs = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(DISBAND_RADIUS),
                mob -> RecruitOwnership.isYours(player, mob)
        );

        for (Mob mob : mobs) {
            releaseRecruit(mob);
        }
    }

    static boolean canAttemptRecruit(ServerPlayer player) {
        return ownerHasAllianceEffect(player)
                && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof BannerItem;
    }

    public static boolean canCommandRecruits(ServerPlayer player) {
        return ownerHasAllianceEffect(player);
    }

    /**
     * Enforces the one invariant of recruitment: {@code TAG_RECRUITED} and {@code TAG_OWNER} are
     * either both present or both absent, and a recruit is never also a settlement member.
     *
     * <p>Every path in the mod writes the pair together, so it cannot drift on its own. What can
     * break it is persistent data outliving the code that wrote it: an entity conversion carries the
     * tag compound over to a different mob, {@code /data} can set one half of it, and other mods
     * copying entity NBT do the same. Half a pair leaves either an ownerless recruit that nothing
     * commands or an owned mob that no goal follows, and neither is recoverable in play.
     *
     * <p>Called at the points where such a mob first surfaces — entity join, the recruitment
     * interaction, and the target-change event — and deliberately not on a timer.
     */
    static void sanitizeRecruitState(Mob mob) {
        boolean recruited = mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED);
        boolean hasOwner = mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);

        if (recruited && !hasOwner) {
            MobSleep.wake(mob);

            mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, false);
            SquadOrders.clearOrderData(mob);
            RecruitTargeting.clearFollowTarget(mob);
            return;
        }

        if (!recruited && hasOwner) {
            MobSleep.wake(mob);

            mob.getPersistentData().remove(FollowOwnerGoal.TAG_OWNER);
            SquadOrders.clearOrderData(mob);
            RecruitTargeting.clearFollowTarget(mob);
            return;
        }

        if (recruited) {
            if (WarbellVillageData.isVillageMember(mob)) {
                WarbellVillageData.clearVillageData(mob);
                WarbellVillageBedData.clearBed(mob);
                WarbellVillageWorkstationData.clearWorkstation(mob);
            }

            SquadOrders.ensureDefaultOrder(mob);
        }
    }

    static boolean isValidAttackTarget(ServerPlayer owner, Mob recruit, LivingEntity target) {
        return SquadOrders.isValidTarget(owner, recruit, target);
    }

    static void setPatrolFlags(Mob mob) {
        mob.getPersistentData().putBoolean("Patrolling", false);
        mob.getPersistentData().putBoolean("PatrolLeader", false);
        mob.setPersistenceRequired();
    }

    private RecruitmentEvents() {
    }
}
