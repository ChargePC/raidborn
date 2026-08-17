package net.randomcara.raidborn.content.entity.iron_gollet;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import javax.annotation.Nullable;

/**
 * Grab a hurt villager, carry them away from whatever hit them, let regen do the rest.
 *
 * <p>No ranged healing, the Gollet has to actually reach the villager and pick them up. Getting
 * interrupted halfway is normal, so this is written to pick up where it left off instead of
 * assuming it runs to the end.
 */
class HealHurtVillagerGoal extends Goal {

    /** The search costs two entity scans, and GoalSelector calls canUse every tick. */
    private static final int SEARCH_INTERVAL_TICKS = 10;

    private static final double PICKUP_DISTANCE_SQR = 2.25D;

    private static final double MOVE_SPEED = 1.05D;
    private static final double MOVE_FALLBACK_SPEED = 0.95D;
    private static final int PATH_RECALCULATE_TICKS = 8;

    private static final int STUCK_TICKS_LIMIT = 30;
    private static final double STUCK_MOVEMENT_SQR = 0.0025D;

    private static final int REGEN_DURATION_TICKS = 80;
    private static final int REGEN_REFRESH_TICKS = 10;
    private static final int REGEN_REAPPLY_BELOW_TICKS = 40;

    private static final int FLEE_COOLDOWN_TICKS = 18;
    private static final int FLEE_HORIZONTAL_DISTANCE = 8;
    private static final int FLEE_VERTICAL_DISTANCE = 3;
    private static final double FLEE_SPEED = 0.75D;
    private static final double FLEE_FALLBACK_SPEED = 0.65D;
    private static final double FLEE_FALLBACK_DISTANCE = 5.0D;
    private static final double FLEE_THREAT_SEARCH_XZ = 12.0D;
    private static final double FLEE_THREAT_SEARCH_Y = 6.0D;

    private final IronGollet gollet;

    @Nullable
    private Villager targetVillager;

    private int regenRefreshCooldown;
    private int fleeCooldown;
    private int pathRecalculateCooldown;
    private int stuckTicks;

    /** Set when the direct path stopped working, cleared after one detour attempt. */
    private boolean preferDetour;

    @Nullable
    private Vec3 lastPosition;

    HealHurtVillagerGoal(IronGollet gollet) {
        this.gollet = gollet;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.gollet.getCarriedOwner() != null) {
            return false;
        }

        Villager carried = this.gollet.getCarriedVillager();
        if (carried != null && carried.isAlive() && !this.gollet.isVillagerFullyHealed(carried)) {
            this.setTargetVillager(carried);
            return true;
        }

        if (this.gollet.tickCount % SEARCH_INTERVAL_TICKS != 0) {
            return false;
        }

        this.setTargetVillager(this.gollet.findHurtVillager());
        return this.targetVillager != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.gollet.getCarriedOwner() != null) {
            return false;
        }

        Villager carried = this.gollet.getCarriedVillager();

        if (carried != null) {
            return carried.isAlive() && !this.gollet.isIgnoredVillager(carried);
        }

        return this.targetVillager != null
                && this.targetVillager.isAlive()
                && !this.gollet.isVillagerFullyHealed(this.targetVillager)
                && !this.gollet.isIgnoredVillager(this.targetVillager);
    }

    @Override
    public void start() {
        this.resetProgress();
        this.lastPosition = this.gollet.position();
        this.gollet.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.setTargetVillager(null);
        this.resetProgress();
        this.lastPosition = null;
    }

    private void resetProgress() {
        this.regenRefreshCooldown = 0;
        this.fleeCooldown = 0;
        this.pathRecalculateCooldown = 0;
        this.stuckTicks = 0;
        this.preferDetour = false;
    }

    /** Published on the Gollet so neighbouring ones do not converge on the same villager. */
    private void setTargetVillager(@Nullable Villager villager) {
        this.targetVillager = villager;
        this.gollet.setHealingTarget(villager);
    }

    @Override
    public void tick() {
        Villager carried = this.gollet.getCarriedVillager();

        if (carried != null) {
            this.setTargetVillager(carried);
            this.gollet.setCarryingVillager(true);
            this.gollet.getLookControl().setLookAt(carried, 30.0F, 30.0F);
            this.healCarriedVillager(carried);
            this.fleeFromNearbyThreats(carried);
            return;
        }

        if (this.needsNewTarget()) {
            this.setTargetVillager(this.gollet.findHurtVillager());
            this.pathRecalculateCooldown = 0;
            this.preferDetour = false;
        }

        if (this.targetVillager == null || !this.targetVillager.isAlive()) {
            return;
        }

        this.gollet.getLookControl().setLookAt(this.targetVillager, 30.0F, 30.0F);

        if (this.gollet.distanceToSqr(this.targetVillager) <= PICKUP_DISTANCE_SQR) {
            this.pickUp(this.targetVillager);
            return;
        }

        this.updateStuckDetection();

        if (this.pathRecalculateCooldown > 0 && !this.gollet.getNavigation().isDone() && this.stuckTicks < STUCK_TICKS_LIMIT) {
            this.pathRecalculateCooldown--;
            return;
        }

        this.pathRecalculateCooldown = PATH_RECALCULATE_TICKS;
        this.moveToVillager(this.targetVillager);
    }

    private boolean needsNewTarget() {
        return this.targetVillager == null
                || !this.targetVillager.isAlive()
                || this.targetVillager.isPassenger()
                || this.gollet.isVillagerFullyHealed(this.targetVillager)
                || this.gollet.isIgnoredVillager(this.targetVillager);
    }

    private void pickUp(Villager villager) {
        if (villager.isPassenger()) {
            return;
        }

        villager.startRiding(this.gollet, true);
        this.gollet.setCarryingVillager(true);
        this.gollet.getNavigation().stop();
        this.gollet.playVillagerClampSound();
        this.healCarriedVillager(villager);
    }

    /**
     * Straight at the villager, or around the houses when that isn't working.
     *
     * <p>The detour is just a random spot roughly toward the villager. Needed because a villager
     * cowering indoors is somewhere the direct path keeps failing on while the door sits two blocks
     * to the left. Walk to the general area and the next direct path usually works.
     */
    private void moveToVillager(Villager villager) {
        if (!this.preferDetour && this.gollet.getNavigation().moveTo(villager, MOVE_SPEED)) {
            return;
        }

        this.preferDetour = false;

        Vec3 toward = DefaultRandomPos.getPosTowards(this.gollet, 8, 4, villager.position(), Mth.HALF_PI);

        if (toward != null) {
            this.gollet.getNavigation().moveTo(toward.x, toward.y, toward.z, MOVE_FALLBACK_SPEED);
        }
    }

    /** Standing still for {@link #STUCK_TICKS_LIMIT} ticks sends the next move through the detour. */
    private void updateStuckDetection() {
        if (this.lastPosition == null) {
            this.lastPosition = this.gollet.position();
            this.stuckTicks = 0;
            return;
        }

        if (this.gollet.position().distanceToSqr(this.lastPosition) >= STUCK_MOVEMENT_SQR) {
            this.stuckTicks = 0;
            this.lastPosition = this.gollet.position();
            return;
        }

        this.stuckTicks++;

        if (this.stuckTicks >= STUCK_TICKS_LIMIT) {
            this.pathRecalculateCooldown = 0;
            this.preferDetour = true;
            this.stuckTicks = 0;
            this.lastPosition = this.gollet.position();
            this.gollet.getNavigation().stop();
        }
    }

    private void healCarriedVillager(Villager villager) {
        if (this.gollet.isVillagerFullyHealed(villager)) {
            this.gollet.releaseCarriedVillager(villager, IronGollet.ReleaseReason.HEALED);
            return;
        }

        if (this.regenRefreshCooldown > 0) {
            this.regenRefreshCooldown--;
            return;
        }

        MobEffectInstance current = villager.getEffect(MobEffects.REGENERATION);

        if (current == null || current.getAmplifier() < 1 || current.getDuration() <= REGEN_REAPPLY_BELOW_TICKS) {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION_TICKS, 1, false, true, true));
        }

        this.regenRefreshCooldown = REGEN_REFRESH_TICKS;
    }

    private void fleeFromNearbyThreats(Villager carried) {
        LivingEntity nearestThreat = this.findNearestThreat(carried);

        if (nearestThreat == null) {
            if (!this.gollet.getNavigation().isDone()) {
                this.gollet.getNavigation().stop();
            }

            return;
        }

        if (this.fleeCooldown > 0 && !this.gollet.getNavigation().isDone()) {
            this.fleeCooldown--;
            return;
        }

        this.fleeCooldown = FLEE_COOLDOWN_TICKS;

        Vec3 fleePos = DefaultRandomPos.getPosAway(
                this.gollet,
                FLEE_HORIZONTAL_DISTANCE,
                FLEE_VERTICAL_DISTANCE,
                nearestThreat.position()
        );

        if (fleePos != null) {
            this.gollet.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, FLEE_SPEED);
            return;
        }

        // Cornered: no valid position away from the threat, so just shove off in a straight line.
        Vec3 away = this.gollet.position().subtract(nearestThreat.position());

        if (away.lengthSqr() < 0.01D) {
            away = new Vec3(
                    this.gollet.getRandom().nextDouble() - 0.5D,
                    0.0D,
                    this.gollet.getRandom().nextDouble() - 0.5D
            );
        }

        Vec3 fallback = this.gollet.position().add(away.normalize().scale(FLEE_FALLBACK_DISTANCE));
        this.gollet.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, FLEE_FALLBACK_SPEED);
        this.gollet.getMoveControl().setWantedPosition(fallback.x, fallback.y, fallback.z, FLEE_FALLBACK_SPEED);
    }

    @Nullable
    private LivingEntity findNearestThreat(Villager carried) {
        AABB box = this.gollet.getBoundingBox().inflate(
                FLEE_THREAT_SEARCH_XZ,
                FLEE_THREAT_SEARCH_Y,
                FLEE_THREAT_SEARCH_XZ
        );

        return this.gollet.level().getEntitiesOfClass(LivingEntity.class, box, living -> this.isThreat(living, carried))
                .stream()
                .min(Comparator.comparingDouble(this.gollet::distanceToSqr))
                .orElse(null);
    }

    /**
     * Wider than {@link IronGollet#canAttackThreat}: while carrying, anything already aimed at the
     * village counts as a reason to walk away, even something the Gollet would not attack.
     */
    private boolean isThreat(LivingEntity living, Villager carried) {
        if (!living.isAlive() || living == this.gollet || living == carried) {
            return false;
        }

        if (living instanceof Villager || living instanceof IronGolem || living instanceof Creeper) {
            return false;
        }

        if (living instanceof Player player) {
            return this.gollet.isVillageLinked() && !player.isCreative() && !player.isSpectator();
        }

        if (hasRecentlyHurt(carried, living) || hasRecentlyHurt(this.gollet, living)) {
            return this.gollet.canAttackThreat(living);
        }

        if (living instanceof Enemy) {
            return true;
        }

        if (!(living instanceof Mob mob)) {
            return false;
        }

        LivingEntity target = mob.getTarget();

        return target != null
                && (target == this.gollet
                || target == carried
                || target instanceof Villager
                || target instanceof IronGolem);
    }

    private static boolean hasRecentlyHurt(LivingEntity victim, LivingEntity suspect) {
        LivingEntity lastHurtBy = victim.getLastHurtByMob();
        return lastHurtBy != null && lastHurtBy.getUUID().equals(suspect.getUUID());
    }
}
