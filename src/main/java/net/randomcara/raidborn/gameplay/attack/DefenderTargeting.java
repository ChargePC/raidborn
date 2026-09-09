package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;
import net.randomcara.raidborn.content.entity.juggernaut.Juggernaut;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class DefenderTargeting {
    private static final int RETARGET_MIN_TICKS = 34;
    private static final int RETARGET_JITTER_TICKS = 18;

    private enum Priority {
        RETALIATION,

        OWNER,

        ATTACKING_OUR_SIDE,

        ANY
    }

    private record Candidate(LivingEntity threat, Priority priority, int defendersOnIt, double distanceSqr) {
        private static final Comparator<Candidate> ORDER = Comparator.comparing(Candidate::priority).thenComparingInt(Candidate::defendersOnIt).thenComparingDouble(Candidate::distanceSqr);
    }

    static void tick(AttackInstance attack, ServerLevel level, @Nullable LivingEntity owner) {
        UUID ownerUuid = attack.getOwnerPlayerUuid();
        ThreatScan scan = scanThreats(attack, level, owner, ownerUuid);
        enroll(attack, scan.newcomers(), ownerUuid);

        List<LivingEntity> threats = scan.threats();
        Map<UUID, Integer> defendersPerThreat = countDefendersPerThreat(attack, level, threats);

        for (UUID defenderUuid : attack.getAllDefenderUuids()) {
            Entity entity = level.getEntity(defenderUuid);
            if (!(entity instanceof Mob defender) || !defender.isAlive()) {
                continue;
            }

            LivingEntity currentTarget = defender.getTarget();
            if (currentTarget != null && !isStillListed(currentTarget, threats)) {
                releaseTarget(defendersPerThreat, currentTarget.getUUID());
                defender.setTarget(null);
                currentTarget = null;
            }

            LivingEntity chosen = chooseTarget(attack, defender, threats, ownerUuid, currentTarget, defendersPerThreat);
            if (chosen == null || chosen == currentTarget) {
                continue;
            }

            if (currentTarget != null) {
                releaseTarget(defendersPerThreat, currentTarget.getUUID());
            }

            defender.setTarget(chosen);
            defendersPerThreat.merge(chosen.getUUID(), 1, Integer::sum);
        }
    }

    private record ThreatScan(List<LivingEntity> threats, List<Mob> newcomers) {
    }

    private static ThreatScan scanThreats(AttackInstance attack,
                                          ServerLevel level,
                                          @Nullable LivingEntity owner,
                                          UUID ownerUuid) {
        Map<UUID, LivingEntity> threats = new LinkedHashMap<>();
        List<Mob> newcomers = new ArrayList<>();

        if (owner instanceof Player player && player.getUUID().equals(ownerUuid) && AttackRaidbornHooks.isValidAttackOwnerTarget(player)) {
            threats.put(player.getUUID(), player);
        }

        for (UUID allyUuid : attack.getParticipatingRecruitUuids()) {
            if (level.getEntity(allyUuid) instanceof LivingEntity ally && AttackRaidbornHooks.isAttackThreat(ally, attack, ownerUuid)) {
                threats.put(ally.getUUID(), ally);
            }
        }

        double scanRadius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get());
        AABB scanBox = new AABB(attack.getCenter()).inflate(scanRadius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, scanBox, LivingEntity::isAlive)) {
            if (living instanceof Villager || AttackRaidbornHooks.isAttackDefender(living, attack.getAttackId()) || !AttackRaidbornHooks.isAttackThreat(living, attack, ownerUuid)) {
                continue;
            }

            threats.put(living.getUUID(), living);

            if (living instanceof Mob mob) {
                newcomers.add(mob);
            }
        }

        return new ThreatScan(new ArrayList<>(threats.values()), newcomers);
    }

    private static void enroll(AttackInstance attack, List<Mob> newcomers, UUID ownerUuid) {
        for (Mob mob : newcomers) {
            attack.addParticipatingRecruit(mob.getUUID());
            AttackRaidbornHooks.markAttackAlly(mob, attack.getAttackId(), ownerUuid);
        }
    }

    private static Map<UUID, Integer> countDefendersPerThreat(AttackInstance attack,
                                                              ServerLevel level,
                                                              List<LivingEntity> threats) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();

        for (UUID defenderUuid : attack.getAllDefenderUuids()) {
            Entity entity = level.getEntity(defenderUuid);
            if (!(entity instanceof Mob defender) || !defender.isAlive()) {
                continue;
            }

            LivingEntity target = defender.getTarget();
            if (target != null && isStillListed(target, threats)) {
                counts.merge(target.getUUID(), 1, Integer::sum);
            }
        }

        return counts;
    }

    private static void releaseTarget(Map<UUID, Integer> defendersPerThreat, UUID targetUuid) {
        defendersPerThreat.computeIfPresent(targetUuid, (ignored, count) -> count > 1 ? count - 1 : null);
    }

    private static boolean isStillListed(LivingEntity target, List<LivingEntity> threats) {
        if (!target.isAlive() || (target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            return false;
        }

        for (LivingEntity threat : threats) {
            if (threat.getUUID().equals(target.getUUID())) {
                return true;
            }
        }

        return false;
    }

    private static boolean canTarget(Mob defender, LivingEntity threat) {
        if (threat == defender || !threat.isAlive()) {
            return false;
        }

        double followRange = defender.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (defender.distanceToSqr(threat) > followRange * followRange) {
            return false;
        }

        if (defender instanceof Juggernaut juggernaut) {
            return juggernaut.isValidTarget(threat);
        }

        if (defender instanceof IronGollet gollet) {
            return gollet.canAttackThreat(threat);
        }

        return true;
    }

    @Nullable
    private static LivingEntity chooseTarget(AttackInstance attack,
                                             Mob defender,
                                             List<LivingEntity> threats,
                                             UUID ownerUuid,
                                             @Nullable LivingEntity currentTarget,
                                             Map<UUID, Integer> defendersPerThreat) {
        Candidate best = null;

        for (LivingEntity threat : threats) {
            if (!canTarget(defender, threat)) {
                continue;
            }

            Candidate candidate = new Candidate(threat, priorityOf(attack, defender, threat, ownerUuid), defendersAlreadyOn(defendersPerThreat, threat, currentTarget), defender.distanceToSqr(threat));
            if (best == null || Candidate.ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }

        if (best == null) {
            return null;
        }

        boolean keepsCurrent = currentTarget != null && isStillListed(currentTarget, threats) && canTarget(defender, currentTarget);

        if (!keepsCurrent) {
            return best.threat();
        }

        Priority current = priorityOf(attack, defender, currentTarget, ownerUuid);
        return best.priority().compareTo(current) < 0 || RetargetWindow.isOpen(defender, RETARGET_MIN_TICKS, RETARGET_JITTER_TICKS) ? best.threat() : currentTarget;
    }

    private static Priority priorityOf(AttackInstance attack, Mob defender, LivingEntity threat, UUID ownerUuid) {
        LivingEntity lastDamager = defender.getLastHurtByMob();
        if (lastDamager != null && lastDamager.getUUID().equals(threat.getUUID())) {
            return Priority.RETALIATION;
        }

        if (threat instanceof Player player && player.getUUID().equals(ownerUuid)) {
            return Priority.OWNER;
        }

        if (threat instanceof Mob threatMob && AttackRaidbornHooks.isVillageSideEntity(threatMob.getTarget(), attack)) {
            return Priority.ATTACKING_OUR_SIDE;
        }

        return Priority.ANY;
    }

    private static int defendersAlreadyOn(Map<UUID, Integer> defendersPerThreat,
                                          LivingEntity threat,
                                          @Nullable LivingEntity currentTarget) {
        int count = defendersPerThreat.getOrDefault(threat.getUUID(), 0);

        return currentTarget != null && currentTarget.getUUID().equals(threat.getUUID()) ? Math.max(0, count - 1) : count;
    }
}
