package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import java.util.UUID;

/**
 * Target protection and combat memory for recruits.
 *
 * <p>Careful in here, the vanilla revenge path breaks if you look at it funny. See
 * {@link #isRevengeTargetChange} and {@link #clearTargetKeepRevenge}.
 */
public final class RecruitTargeting {
    static final double FOLLOW_TARGET_LEASH_RADIUS = 48.0D;

    static final double FOLLOW_TARGET_LEASH_RADIUS_SQR = FOLLOW_TARGET_LEASH_RADIUS * FOLLOW_TARGET_LEASH_RADIUS;

    static final int FOLLOW_TARGET_MEMORY_TICKS = 20 * 15;

    static final String TAG_FOLLOW_TARGET = "raidborn_follow_target";

    static final String TAG_FOLLOW_TARGET_EXPIRE = "raidborn_follow_target_expire";

    /**
     * Clears the active target and movement but keeps {@code getLastHurtByMob()}. That's on purpose.
     *
     * <p>Vanilla {@code HurtByTargetGoal} can still be using the revenge target while it works
     * through {@code start()}/{@code alertOthers()}. Clear both at once and the goal is left with a
     * null target, then {@code Pillager#isAlliedTo(null)} takes the server down while the pillager
     * is alerting its friends.
     */
    static void clearTargetKeepRevenge(Mob mob) {
        mob.setTarget(null);
        mob.getNavigation().stop();

        if (mob instanceof Monster monster) {
            monster.setAggressive(false);
        }
    }

    public static boolean isProtectedRecruitTarget(Mob recruit, LivingEntity target) {
        if (target == null) return false;

        UUID ownerId = RecruitOwnership.getOwnerUUID(recruit);
        if (ownerId == null) return false;

        if (target instanceof ServerPlayer player && ownerId.equals(player.getUUID())) {
            return true;
        }

        return target instanceof Mob targetMob && RecruitOwnership.isSameSquad(recruit, targetMob);
    }

    /**
     * {@code HurtByTargetGoal.start()} calls {@code setTarget(getLastHurtByMob())} and then
     * {@code alertOthers()}, which hands {@code getTarget()} to {@code Pillager#isAlliedTo}. Blocking
     * the target change on that path would leave {@code getTarget()} null inside {@code alertOthers()}
     * and crash the server, so it is left to the deferred player-tick cleanup.
     *
     * <p>TODO: a mixin on alertOthers would be cleaner than deferring, look at this again later.
     */
    static boolean isRevengeTargetChange(Mob mob, LivingEntity newTarget) {
        return newTarget != null && newTarget == mob.getLastHurtByMob();
    }

    static void clearFollowTarget(Mob mob) {
        mob.getPersistentData().remove(TAG_FOLLOW_TARGET);
        mob.getPersistentData().remove(TAG_FOLLOW_TARGET_EXPIRE);
    }

    static void rememberFollowTarget(Mob mob, LivingEntity target) {
        if (!RecruitOwnership.isRecruited(mob) || SquadOrders.getOrder(mob) != SquadOrder.FOLLOW) return;

        if (target == null || !target.isAlive() || target.isRemoved() || isProtectedRecruitTarget(mob, target)) {
            clearFollowTarget(mob);
            return;
        }

        mob.getPersistentData().putUUID(TAG_FOLLOW_TARGET, target.getUUID());
        mob.getPersistentData().putLong(TAG_FOLLOW_TARGET_EXPIRE, mob.level().getGameTime() + FOLLOW_TARGET_MEMORY_TICKS);
    }

    static LivingEntity getFollowTarget(ServerPlayer owner, Mob mob) {
        if (owner == null || !RecruitOwnership.isRecruited(mob) || SquadOrders.getOrder(mob) != SquadOrder.FOLLOW) return null;
        if (!mob.getPersistentData().hasUUID(TAG_FOLLOW_TARGET)) return null;

        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            clearFollowTarget(mob);
            return null;
        }

        long expireAt = mob.getPersistentData().getLong(TAG_FOLLOW_TARGET_EXPIRE);
        if (mob.level().getGameTime() > expireAt) {
            clearFollowTarget(mob);
            return null;
        }

        Entity entity = serverLevel.getEntity(mob.getPersistentData().getUUID(TAG_FOLLOW_TARGET));
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
            clearFollowTarget(mob);
            return null;
        }

        if (isProtectedRecruitTarget(mob, target) || !RecruitmentEvents.isValidAttackTarget(owner, mob, target)) {
            clearFollowTarget(mob);
            return null;
        }

        if (owner.distanceToSqr(target) > FOLLOW_TARGET_LEASH_RADIUS_SQR
                || owner.distanceToSqr(mob) > FOLLOW_TARGET_LEASH_RADIUS_SQR) {
            clearFollowTarget(mob);
            return null;
        }

        return target;
    }

    public static boolean restoreFollowTarget(ServerPlayer owner, Mob mob) {
        LivingEntity rememberedTarget = getFollowTarget(owner, mob);
        if (rememberedTarget == null) return false;

        mob.setTarget(rememberedTarget);
        RecruitCombatMovement.applyCombatMovement(mob, rememberedTarget, 1.25D, 0.95D, 10.0D * 10.0D);
        return true;
    }

    static void clearOwnerAsTarget(Mob mob) {
        if (!RecruitOwnership.isRecruited(mob)) {
            clearFollowTarget(mob);
            return;
        }

        UUID ownerId = RecruitOwnership.getOwnerUUID(mob);
        if (ownerId == null) {
            clearFollowTarget(mob);
            return;
        }

        Entity ownerEntity = mob.level().getPlayerByUUID(ownerId);
        if (!(ownerEntity instanceof ServerPlayer owner) || !RecruitmentEvents.ownerHasAllianceEffect(owner)) {
            clearFollowTarget(mob);
            return;
        }

        // Preserves the revenge target: see clearTargetKeepRevenge.
        if (isProtectedRecruitTarget(mob, mob.getTarget())) {
            if (!restoreFollowTarget(owner, mob)) {
                clearTargetKeepRevenge(mob);
            }
        }
    }

    private RecruitTargeting() {
    }
}
