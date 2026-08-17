package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.phys.Vec3;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;

/** Casters keep their distance instead of closing in, so a recruited evoker is not a melee unit. */
public final class RecruitCombatMovement {
    static final double CASTER_MIN_COMBAT_DISTANCE = 6.0D;

    static final double CASTER_MIN_COMBAT_DISTANCE_SQR = CASTER_MIN_COMBAT_DISTANCE * CASTER_MIN_COMBAT_DISTANCE;

    static final double CASTER_IDEAL_COMBAT_DISTANCE = 10.0D;

    static final double CASTER_IDEAL_COMBAT_DISTANCE_SQR = CASTER_IDEAL_COMBAT_DISTANCE * CASTER_IDEAL_COMBAT_DISTANCE;

    static final int CASTER_RETREAT_HORIZONTAL_RANGE = 8;

    static final int CASTER_RETREAT_VERTICAL_RANGE = 4;

    static boolean isDistanceFightingMob(Mob mob) {
        if (mob instanceof Witch || mob instanceof Evoker) return true;

        ResourceLocation id = RecruitmentEvents.getEntityId(mob);
        if (id == null) return false;

        return id.equals(RaidbornCompatEntities.MC_ILLUSIONER)
                || id.equals(RaidbornCompatEntities.SANDR_ICEOLOGER)
                || id.equals(RaidbornCompatEntities.IINV_ALCHEMIST)
                || id.equals(RaidbornCompatEntities.IINV_ARCHIVIST)
                || id.equals(RaidbornCompatEntities.IINV_SORCERER)
                || id.equals(RaidbornCompatEntities.IINV_INVOKER)
                || id.equals(RaidbornCompatEntities.IINV_FIRECALLER)
                || id.equals(RaidbornCompatEntities.IINV_NECROMANCER)
                || id.equals(RaidbornCompatEntities.EWM_ENCHANTER);
    }

    static void applyDistanceCombatMovement(Mob mob, LivingEntity target, double approachSpeed, double preferredStopDistSqr) {
        if (target == null || !target.isAlive() || target.isRemoved()) {
            mob.getNavigation().stop();
            return;
        }

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSqr = mob.distanceToSqr(target);
        double stopDistSqr = Math.max(CASTER_IDEAL_COMBAT_DISTANCE_SQR, preferredStopDistSqr);

        if (distSqr > stopDistSqr) {
            mob.getNavigation().moveTo(target, approachSpeed);
            return;
        }

        if (distSqr < CASTER_MIN_COMBAT_DISTANCE_SQR) {
            if (mob instanceof PathfinderMob pathfinderMob) {
                Vec3 retreatPos = DefaultRandomPos.getPosAway(
                        pathfinderMob,
                        CASTER_RETREAT_HORIZONTAL_RANGE,
                        CASTER_RETREAT_VERTICAL_RANGE,
                        target.position()
                );

                if (retreatPos != null) {
                    mob.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, approachSpeed);
                    return;
                }
            }

            Vec3 away = new Vec3(
                    mob.getX() - target.getX(),
                    0.0D,
                    mob.getZ() - target.getZ()
            );

            if (away.lengthSqr() > 1.0E-4D) {
                away = away.normalize();
                mob.getNavigation().moveTo(
                        mob.getX() + away.x * 4.0D,
                        mob.getY(),
                        mob.getZ() + away.z * 4.0D,
                        approachSpeed
                );
                return;
            }
        }

        mob.getNavigation().stop();
    }

    static void applyCombatMovement(Mob mob, LivingEntity target, double speed, double evokerSpeed, double evokerStopDistSqr) {
        if (mob instanceof Monster monster) {
            monster.setAggressive(true);
        }

        if (isDistanceFightingMob(mob)) {
            applyDistanceCombatMovement(mob, target, evokerSpeed, evokerStopDistSqr);
            return;
        }

        mob.getNavigation().moveTo(target, speed);
    }

    private RecruitCombatMovement() {
    }
}
