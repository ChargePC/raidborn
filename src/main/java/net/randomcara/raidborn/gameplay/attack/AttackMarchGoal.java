package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AttackMarchGoal extends Goal {
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int WAYPOINT_HORIZONTAL_RANGE = 15;
    private static final int WAYPOINT_VERTICAL_RANGE = 7;
    private static final double MARCH_SPEED = 1.0D;
    private static final double DIRECT_PATH_MARGIN = 0.8D;

    private final PathfinderMob mob;
    private int repathCooldown;

    public AttackMarchGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!AttackRaidbornHooks.isAttackAlly(this.mob)) {
            return false;
        }

        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.isTargetBeyondPathfinding(target);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.repathCooldown = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.repathCooldown > 0) {
            this.repathCooldown--;
            return;
        }

        if (!this.mob.getNavigation().isDone()) {
            return;
        }

        this.repathCooldown = REPATH_INTERVAL_TICKS;

        Vec3 waypoint = DefaultRandomPos.getPosTowards(this.mob, WAYPOINT_HORIZONTAL_RANGE, WAYPOINT_VERTICAL_RANGE, target.position(), Math.PI / 2.0D);
        if (waypoint != null) {
            this.mob.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, MARCH_SPEED);
            return;
        }

        this.mob.getNavigation().moveTo(target, MARCH_SPEED);
    }

    private boolean isTargetBeyondPathfinding(LivingEntity target) {
        double range = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE) * DIRECT_PATH_MARGIN;
        return this.mob.distanceToSqr(target) > range * range;
    }
}
