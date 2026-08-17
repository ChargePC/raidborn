package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.randomcara.raidborn.content.entity.VillageSide;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;

import java.util.UUID;
import javax.annotation.Nullable;

public final class AttackRaidbornHooks {
    private AttackRaidbornHooks() {
    }

    public enum AttackTier {
        LOYALTY,
        HONOR,
        HERO
    }

    private static final String TAG_ATTACK_ID = "RaidbornAttackId";
    private static final String TAG_ATTACK_EXPIRES = "RaidbornAttackExpiresAt";
    private static final String TAG_SPAWNED_ATTACK_DEFENDER = "RaidbornSpawnedAttackDefender";
    private static final String TAG_EXISTING_ATTACK_DEFENDER = "RaidbornExistingAttackDefender";
    private static final String TAG_ATTACK_ALLY = "RaidbornAttackAlly";
    private static final String TAG_ATTACK_OWNER = "RaidbornAttackOwner";

    private static final int MARK_EXTRA_LIFETIME_TICKS = 1200;

    public static AttackTier getAttackTier(ServerPlayer player) {
        if (player.hasEffect(ModEffects.HERO_OF_THE_RAID.get())) {
            return AttackTier.HERO;
        }

        if (player.hasEffect(ModEffects.ILLAGER_HONOR.get())) {
            return AttackTier.HONOR;
        }

        return AttackTier.LOYALTY;
    }

    /**
     * Marks live in persistent data because they must survive a chunk unload, and each carries an
     * expiry: if the server goes down mid-Attack, or the entity is unloaded when the cleanup runs,
     * the mark stops counting on its own instead of leaving the mob an event defender forever.
     */
    private static void mark(Entity entity, String flag, UUID attackId) {
        CompoundTag tag = entity.getPersistentData();
        tag.putBoolean(flag, true);
        tag.putUUID(TAG_ATTACK_ID, attackId);
        tag.putLong(TAG_ATTACK_EXPIRES, entity.level().getGameTime() + markLifetimeTicks());
    }

    private static int markLifetimeTicks() {
        return RaidbornServerConfig.ATTACK_LARGE_TIME_LIMIT_TICKS.get()
                + RaidbornServerConfig.ATTACK_VICTORY_CELEBRATION_TICKS.get()
                + MARK_EXTRA_LIFETIME_TICKS;
    }

    private static boolean hasLiveMark(Entity entity, String flag) {
        CompoundTag tag = entity.getPersistentData();

        if (!tag.getBoolean(flag)) {
            return false;
        }

        if (entity.level().getGameTime() > tag.getLong(TAG_ATTACK_EXPIRES)) {
            clearAttackMarks(entity);
            return false;
        }

        return true;
    }

    public static void markSpawnedAttackDefender(Entity entity, UUID attackId) {
        mark(entity, TAG_SPAWNED_ATTACK_DEFENDER, attackId);
    }

    public static void markExistingAttackDefender(Entity entity, UUID attackId) {
        mark(entity, TAG_EXISTING_ATTACK_DEFENDER, attackId);
    }

    public static void markAttackAlly(Entity entity, UUID attackId, UUID ownerUuid) {
        mark(entity, TAG_ATTACK_ALLY, attackId);
        entity.getPersistentData().putUUID(TAG_ATTACK_OWNER, ownerUuid);
    }

    public static boolean isSpawnedAttackDefender(Entity entity) {
        return hasLiveMark(entity, TAG_SPAWNED_ATTACK_DEFENDER);
    }

    public static boolean isAttackDefender(Entity entity) {
        return hasLiveMark(entity, TAG_SPAWNED_ATTACK_DEFENDER)
                || hasLiveMark(entity, TAG_EXISTING_ATTACK_DEFENDER);
    }

    public static boolean isAttackDefender(Entity entity, UUID attackId) {
        return isAttackDefender(entity) && attackId.equals(readAttackId(entity));
    }

    public static boolean isAttackAlly(Entity entity) {
        return hasLiveMark(entity, TAG_ATTACK_ALLY);
    }

    public static boolean isAttackAlly(Entity entity, UUID attackId) {
        return isAttackAlly(entity) && attackId.equals(readAttackId(entity));
    }

    @Nullable
    private static UUID readAttackId(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        return tag.hasUUID(TAG_ATTACK_ID) ? tag.getUUID(TAG_ATTACK_ID) : null;
    }

    public static void clearAttackMarks(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        tag.remove(TAG_SPAWNED_ATTACK_DEFENDER);
        tag.remove(TAG_EXISTING_ATTACK_DEFENDER);
        tag.remove(TAG_ATTACK_ALLY);
        tag.remove(TAG_ATTACK_ID);
        tag.remove(TAG_ATTACK_EXPIRES);
        tag.remove(TAG_ATTACK_OWNER);
    }

    public static boolean isRecruitOwnedBy(Entity entity, UUID ownerUuid) {
        return entity instanceof Mob mob
                && RecruitOwnership.isRecruited(mob)
                && ownerUuid.equals(RecruitOwnership.getOwnerUUID(mob));
    }

    public static boolean isValidAttackOwnerTarget(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /** Extended by datapack through the {@code raidborn:illager_threats} tag. */
    public static boolean isPotentialIllagerThreat(Entity entity) {
        return VillageSide.isIllagerThreat(entity);
    }

    public static boolean isVillageSideEntity(@Nullable Entity entity, AttackInstance attack) {
        if (entity == null) {
            return false;
        }

        return VillageSide.isDefender(entity) || attack.isRegisteredDefender(entity.getUUID());
    }

    public static boolean isInsideAttackArea(Entity entity, AttackInstance attack) {
        if (!entity.level().dimension().equals(attack.getDimension())) {
            return false;
        }

        double radius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get());
        return entity.distanceToSqr(
                attack.getCenter().getX() + 0.5D,
                attack.getCenter().getY() + 0.5D,
                attack.getCenter().getZ() + 0.5D
        ) <= radius * radius;
    }

    public static boolean canMobBeAttackAlly(Mob mob, AttackInstance attack, UUID ownerUuid) {
        if (!mob.isAlive() || isAttackDefender(mob)) {
            return false;
        }

        return isRecruitOwnedBy(mob, ownerUuid) || isAttackAlly(mob, attack.getAttackId());
    }

    public static boolean isAttackThreat(LivingEntity entity, AttackInstance attack, UUID ownerUuid) {
        if (!entity.isAlive()) {
            return false;
        }

        if (entity instanceof Player player) {
            return player.getUUID().equals(ownerUuid) && isValidAttackOwnerTarget(player);
        }

        if (entity instanceof Villager || isAttackDefender(entity)) {
            return false;
        }

        if (isRecruitOwnedBy(entity, ownerUuid) || isAttackAlly(entity, attack.getAttackId())) {
            return true;
        }

        if (!isPotentialIllagerThreat(entity)) {
            return false;
        }

        if (entity instanceof Mob mob && mob.getTarget() != null) {
            LivingEntity target = mob.getTarget();

            if (target.getUUID().equals(ownerUuid) || isVillageSideEntity(target, attack)) {
                return true;
            }
        }

        return isInsideAttackArea(entity, attack);
    }
}
