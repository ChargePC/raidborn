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

/**
 * Hands every Attack defender someone to fight.
 *
 * <p>Vanilla target goals only see what is in front of them, so a squad left to itself piles onto
 * the first raider it finds and ignores the player. This picks targets for the squad as a whole.
 *
 * <p>The mirror of this runs in {@link AttackIllagerAIHandler} for the player's side: same shape,
 * opposite point of view.
 */
final class DefenderTargeting {

    private static final int RETARGET_MIN_TICKS = 34;
    private static final int RETARGET_JITTER_TICKS = 18;

    private DefenderTargeting() {
    }

    /**
     * Who a defender goes for, in order. The first rule that matches decides; ties inside a rule
     * are broken in {@link Candidate#ORDER}.
     */
    private enum Priority {
        /** Whoever is hitting this defender right now. Being shot in the back overrides orders. */
        RETALIATION,

        /** The event owner. The player is the objective, and the squad converges on them. */
        OWNER,

        /** Anything with a villager or another defender in its sights. */
        ATTACKING_OUR_SIDE,

        /** Everyone else the Attack considers hostile. */
        ANY
    }

    /**
     * A threat a given defender could take, ranked. Within the same priority the defender goes for
     * whatever fewest of its squadmates are already on, and then for whatever is closest, which is
     * what keeps a squad from dogpiling one raider.
     */
    private record Candidate(LivingEntity threat, Priority priority, int defendersOnIt, double distanceSqr) {
        private static final Comparator<Candidate> ORDER = Comparator
                .comparing(Candidate::priority)
                .thenComparingInt(Candidate::defendersOnIt)
                .thenComparingDouble(Candidate::distanceSqr);
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

    /**
     * Everything the defenders are allowed to fight, plus the mobs the area scan turned up that the
     * Attack was not tracking yet.
     */
    private record ThreatScan(List<LivingEntity> threats, List<Mob> newcomers) {
    }

    private static ThreatScan scanThreats(AttackInstance attack,
                                          ServerLevel level,
                                          @Nullable LivingEntity owner,
                                          UUID ownerUuid) {
        Map<UUID, LivingEntity> threats = new LinkedHashMap<>();
        List<Mob> newcomers = new ArrayList<>();

        if (owner instanceof Player player
                && player.getUUID().equals(ownerUuid)
                && AttackRaidbornHooks.isValidAttackOwnerTarget(player)) {
            threats.put(player.getUUID(), player);
        }

        for (UUID allyUuid : attack.getParticipatingRecruitUuids()) {
            if (level.getEntity(allyUuid) instanceof LivingEntity ally
                    && AttackRaidbornHooks.isAttackThreat(ally, attack, ownerUuid)) {
                threats.put(ally.getUUID(), ally);
            }
        }

        double scanRadius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get());
        AABB scanBox = new AABB(attack.getCenter()).inflate(scanRadius);

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, scanBox, LivingEntity::isAlive)) {
            if (living instanceof Villager || AttackRaidbornHooks.isAttackDefender(living, attack.getAttackId())) {
                continue;
            }

            if (!AttackRaidbornHooks.isAttackThreat(living, attack, ownerUuid)) {
                continue;
            }

            threats.put(living.getUUID(), living);

            if (living instanceof Mob mob) {
                newcomers.add(mob);
            }
        }

        return new ThreatScan(new ArrayList<>(threats.values()), newcomers);
    }

    /** A mob that shows up fighting on the player's side counts as a participant from then on. */
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
        if (!target.isAlive()) {
            return false;
        }

        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        for (LivingEntity threat : threats) {
            if (threat.getUUID().equals(target.getUUID())) {
                return true;
            }
        }

        return false;
    }

    /**
     * The target has to pass the defender's own rules.
     *
     * <p>Without this a Juggernaut was handed a target 90 blocks away, its {@code aiStep} cleared it
     * on the next tick for exceeding the chase range, and the two systems swapped the field 20 times
     * a second, invalidating the path on every swap.
     */
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

            Candidate candidate = new Candidate(
                    threat,
                    priorityOf(attack, defender, threat, ownerUuid),
                    defendersAlreadyOn(defendersPerThreat, threat, currentTarget),
                    defender.distanceToSqr(threat)
            );

            if (best == null || Candidate.ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }

        if (best == null) {
            return null;
        }

        boolean keepsCurrent = currentTarget != null
                && isStillListed(currentTarget, threats)
                && canTarget(defender, currentTarget);

        if (!keepsCurrent) {
            return best.threat();
        }

        /*
         * Staying on the current target is the default, and the only thing that overrides it is a
         * genuinely more urgent one. Everything else waits for the window: a defender that swaps
         * because a raider drifted a block closer spends the fight walking instead of swinging.
         */
        Priority current = priorityOf(attack, defender, currentTarget, ownerUuid);

        return best.priority().compareTo(current) < 0 || RetargetWindow.isOpen(defender, RETARGET_MIN_TICKS, RETARGET_JITTER_TICKS)
                ? best.threat()
                : currentTarget;
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

    /** A defender is in the count for its own target, and must not read itself as a crowd. */
    private static int defendersAlreadyOn(Map<UUID, Integer> defendersPerThreat,
                                          LivingEntity threat,
                                          @Nullable LivingEntity currentTarget) {
        int count = defendersPerThreat.getOrDefault(threat.getUUID(), 0);

        return currentTarget != null && currentTarget.getUUID().equals(threat.getUUID())
                ? Math.max(0, count - 1)
                : count;
    }
}
