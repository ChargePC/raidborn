package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Heavy swing with wind-up, frontal arc and its own interval.
 *
 * <p>Not {@code MeleeAttackGoal}: its 20 tick interval is fixed, and the Juggernaut needs a slower
 * one that the wind-up can fit inside.
 */
class HeavyMeleeAttackGoal extends Goal {

    private static final int ATTACK_INTERVAL_TICKS = 30;
    private static final int PATH_RECALCULATE_TICKS = 10;

    private final Juggernaut juggernaut;

    private int pathRecalculateCooldown;
    private int attackCooldown;

    HeavyMeleeAttackGoal(Juggernaut juggernaut) {
        this.juggernaut = juggernaut;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.juggernaut.isValidTarget(this.juggernaut.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.juggernaut.getTarget();

        return this.juggernaut.isValidTarget(target)
                && this.juggernaut.distanceToSqr(target) <= Juggernaut.MAX_CHASE_RANGE * Juggernaut.MAX_CHASE_RANGE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.pathRecalculateCooldown = 0;
        this.attackCooldown = 0;
        this.juggernaut.setAggressive(true);
    }

    @Override
    public void stop() {
        this.juggernaut.setAggressive(false);
        this.juggernaut.getNavigation().stop();

        if (!this.juggernaut.isValidTarget(this.juggernaut.getTarget())) {
            this.juggernaut.setTarget(null);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = this.juggernaut.getTarget();

        if (target == null) {
            return;
        }

        this.juggernaut.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (this.pathRecalculateCooldown > 0) {
            this.pathRecalculateCooldown--;
        } else {
            this.pathRecalculateCooldown = PATH_RECALCULATE_TICKS;
            this.juggernaut.getNavigation().moveTo(target, 1.0D);
        }

        if (this.attackCooldown > 0 || this.juggernaut.isWindingUpSwing()) {
            return;
        }

        if (this.juggernaut.canReachForAttack(target)) {
            this.attackCooldown = ATTACK_INTERVAL_TICKS;
            this.juggernaut.beginHeavySwing(target);
        }
    }
}
