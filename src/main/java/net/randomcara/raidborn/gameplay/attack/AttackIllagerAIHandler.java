package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AttackIllagerAIHandler {
    private static final double ALLY_DEFENDER_ENGAGE_RANGE_SQR = 70.0D * 70.0D;
    private static final double ALLY_OWNER_RESCUE_RANGE_SQR = 36.0D * 36.0D;
    private static final double ALLY_CLOSE_DEFENDER_RANGE_SQR = 10.0D * 10.0D;

    private static final int ALLY_RETARGET_MIN_TICKS = 40;
    private static final int ALLY_RETARGET_JITTER_TICKS = 20;

    /** Below the vanilla random stroll (8) and above the melee attack goals. */
    private static final int MARCH_GOAL_PRIORITY = 6;

    private AttackIllagerAIHandler() {
    }

    public static void registerNearbyAllies(AttackInstance attack, ServerLevel level, ServerPlayer owner) {
        double scanRadius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get());
        AABB scanBox = new AABB(attack.getCenter()).inflate(scanRadius);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, scanBox, Mob::isAlive)) {
            if (AttackRaidbornHooks.isAttackDefender(mob)) {
                continue;
            }

            if (!AttackRaidbornHooks.canMobBeAttackAlly(mob, attack, owner.getUUID())) {
                if (!AttackRaidbornHooks.isPotentialIllagerThreat(mob)) {
                    continue;
                }

                if (!AttackRaidbornHooks.isInsideAttackArea(mob, attack)) {
                    continue;
                }
            }

            attack.addParticipatingRecruit(mob.getUUID());
            AttackRaidbornHooks.markAttackAlly(mob, attack.getAttackId(), owner.getUUID());
            ensureMarchGoal(mob);
        }
    }

    /** The march goal stays installed on the mob but only engages while it is marked as an ally. */
    private static void ensureMarchGoal(Mob mob) {
        if (!(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }

        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof AttackMarchGoal) {
                return;
            }
        }

        mob.goalSelector.addGoal(MARCH_GOAL_PRIORITY, new AttackMarchGoal(pathfinderMob));
    }

    public static void tickAllyTargets(AttackInstance attack, ServerLevel level, ServerPlayer owner) {
        List<Villager> aliveVillagers = getAliveVillagerTargets(attack, level);
        List<Mob> defenders = getAliveDefenders(attack, level);

        for (UUID allyUuid : attack.getParticipatingRecruitUuids()) {
            Entity entity = level.getEntity(allyUuid);

            if (!(entity instanceof Mob ally) || !ally.isAlive()) {
                continue;
            }

            if (AttackRaidbornHooks.isAttackDefender(ally)) {
                continue;
            }

            LivingEntity currentTarget = ally.getTarget();
            LivingEntity chosenTarget = chooseTarget(attack, ally, owner, aliveVillagers, defenders, currentTarget);

            if (chosenTarget != null && chosenTarget != currentTarget) {
                ally.setTarget(chosenTarget);
            }
        }
    }

    private static List<Villager> getAliveVillagerTargets(AttackInstance attack, ServerLevel level) {
        return attack.getAliveVillagerUuids()
                .stream()
                .map(level::getEntity)
                .filter(entity -> entity instanceof Villager)
                .map(entity -> (Villager) entity)
                .filter(Villager::isAlive)
                .toList();
    }

    private static List<Mob> getAliveDefenders(AttackInstance attack, ServerLevel level) {
        List<Mob> defenders = new ArrayList<>();

        for (UUID defenderUuid : attack.getAllDefenderUuids()) {
            Entity entity = level.getEntity(defenderUuid);

            if (entity instanceof Mob mob && mob.isAlive()) {
                defenders.add(mob);
            }
        }

        return defenders;
    }

    /**
     * Who an ally goes for, in order: whoever is hitting it, whoever is hitting the squad, whoever
     * is hitting the owner, then the objective itself.
     *
     * <p>Same shape as {@link DefenderTargeting} on the other side but written out separately, since
     * the two pull from different pools (threats vs villagers and defenders). The retarget window is
     * the only bit actually shared, and that's in {@link RetargetWindow}.
     */
    private static LivingEntity chooseTarget(AttackInstance attack,
                                             Mob ally,
                                             ServerPlayer owner,
                                             List<Villager> aliveVillagers,
                                             List<Mob> defenders,
                                             @Nullable LivingEntity currentTarget) {

        LivingEntity lastDamager = ally.getLastHurtByMob();
        if (lastDamager instanceof Mob damagerMob
                && damagerMob.isAlive()
                && AttackRaidbornHooks.isAttackDefender(damagerMob, attack.getAttackId())
                && damagerMob.distanceToSqr(ally) <= ALLY_DEFENDER_ENGAGE_RANGE_SQR) {
            return damagerMob;
        }

        Mob defenderTargetingThisAlly = defenders.stream()
                .filter(defender -> defender.distanceToSqr(ally) <= ALLY_DEFENDER_ENGAGE_RANGE_SQR)
                .filter(defender -> defender.getTarget() != null && defender.getTarget().getUUID().equals(ally.getUUID()))
                .min(Comparator.comparingDouble(defender -> defender.distanceToSqr(ally)))
                .orElse(null);

        if (defenderTargetingThisAlly != null) {
            return defenderTargetingThisAlly;
        }

        Mob defenderTargetingOwner = defenders.stream()
                .filter(defender -> defender.distanceToSqr(ally) <= ALLY_OWNER_RESCUE_RANGE_SQR)
                .filter(defender -> defender.getTarget() instanceof Player player && player.getUUID().equals(owner.getUUID()))
                .min(Comparator.comparingDouble(defender -> defender.distanceToSqr(ally)))
                .orElse(null);

        if (defenderTargetingOwner != null) {
            return defenderTargetingOwner;
        }

        // Past the urgent cases the target is sticky, and only the retarget window releases it.
        if (isStillValid(attack, currentTarget, aliveVillagers, defenders)
                && !RetargetWindow.isOpen(ally, ALLY_RETARGET_MIN_TICKS, ALLY_RETARGET_JITTER_TICKS)) {
            return currentTarget;
        }

        Mob closeDefender = defenders.stream()
                .filter(defender -> defender.distanceToSqr(ally) <= ALLY_CLOSE_DEFENDER_RANGE_SQR)
                .min(Comparator.comparingDouble(defender -> defender.distanceToSqr(ally)))
                .orElse(null);

        if (closeDefender != null) {
            return closeDefender;
        }

        Villager closestVillager = aliveVillagers.stream()
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(ally)))
                .orElse(null);

        if (closestVillager != null) {
            return closestVillager;
        }

        return defenders.stream()
                .min(Comparator.comparingDouble(defender -> defender.distanceToSqr(ally)))
                .orElse(null);
    }

    private static boolean isStillValid(AttackInstance attack,
                                        @Nullable LivingEntity currentTarget,
                                        List<Villager> aliveVillagers,
                                        List<Mob> defenders) {
        if (currentTarget == null || !currentTarget.isAlive()) {
            return false;
        }

        if (currentTarget instanceof Villager villager) {
            return attack.getAliveVillagerUuids().contains(villager.getUUID());
        }

        if (currentTarget instanceof Mob mob) {
            for (Mob defender : defenders) {
                if (defender.getUUID().equals(mob.getUUID())) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void tickVictoryCelebration(AttackInstance attack, ServerLevel level, @Nullable ServerPlayer owner) {
        for (UUID allyUuid : attack.getParticipatingRecruitUuids()) {
            Entity entity = level.getEntity(allyUuid);

            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }

            mob.setTarget(null);
            mob.getNavigation().stop();

            if (mob instanceof Raider raider) {
                raider.setCelebrating(true);
            }

            if (!mob.isPassenger() && mob.onGround() && mob.tickCount % 50 == 0) {
                mob.getJumpControl().jump();
            }

            if (!mob.isSilent() && level.getGameTime() % 100L == 0L) {
                if (mob instanceof Raider raider) {
                    mob.playSound(raider.getCelebrateSound(), 1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
                } else {
                    mob.playAmbientSound();
                }
            }

            if (level.getGameTime() % 40L == 0L && level.getRandom().nextFloat() < 0.45F) {
                AttackCelebration.launchFirework(level, mob, DyeColor.BLUE, DyeColor.GRAY, DyeColor.LIGHT_GRAY);
            }
        }
    }

    public static void clearAllies(AttackInstance attack, ServerLevel level) {
        for (UUID allyUuid : attack.getParticipatingRecruitUuids()) {
            Entity entity = level.getEntity(allyUuid);

            if (entity instanceof Mob mob) {
                mob.setTarget(null);

                if (mob instanceof Raider raider) {
                    raider.setCelebrating(false);
                }

                AttackRaidbornHooks.clearAttackMarks(mob);
            }
        }
    }
}
