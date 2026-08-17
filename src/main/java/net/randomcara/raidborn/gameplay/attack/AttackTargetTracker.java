package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

public final class AttackTargetTracker {
    private static final String NEXT_BELL_RING_TICK_TAG = "RaidbornAttackNextBellRingTick";
    private static final String NEXT_EXTRA_PANIC_TARGET_TICK_TAG = "RaidbornAttackNextExtraPanicTargetTick";

    private static final int PANIC_MEMORY_TICKS = 100;
    private static final int PANIC_TARGET_MIN_COOLDOWN = 80;
    private static final int PANIC_TARGET_RANDOM_COOLDOWN = 70;
    private static final double PANIC_MAX_THREAT_DISTANCE = 40.0D;
    private static final double PANIC_CLOSE_TARGET_DISTANCE_SQR = 2.25D;

    private static final int BELL_SEARCH_RADIUS = 48;
    private static final int SHELTER_SEARCH_RADIUS = 22;
    private static final int SHELTER_MAX_CANDIDATES = 32;

    private AttackTargetTracker() {
    }


    public static void reinforceVillageTargets(AttackInstance attack, ServerLevel level) {
        Map<UUID, Villager> foundVillagers = new LinkedHashMap<>();

        int centerScanRadius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_DETECTION_RADIUS.get());
        collectVillagers(
                level,
                AABB.ofSize(
                        Vec3.atCenterOf(attack.getCenter()),
                        centerScanRadius * 2.0D,
                        Math.max(centerScanRadius * 2.0D, 48.0D),
                        centerScanRadius * 2.0D
                ),
                foundVillagers
        );

        for (BlockPos poiPosition : attack.getVillagePoiPositions()) {
            collectVillagers(
                    level,
                    AABB.ofSize(
                            Vec3.atCenterOf(poiPosition),
                            96.0D,
                            48.0D,
                            96.0D
                    ),
                    foundVillagers
            );
        }

        for (UUID knownVillagerUuid : attack.getAliveVillagerUuids()) {
            Entity entity = level.getEntity(knownVillagerUuid);

            if (entity instanceof Villager villager && villager.isAlive()) {
                collectVillagers(
                        level,
                        AABB.ofSize(
                                villager.position(),
                                64.0D,
                                32.0D,
                                64.0D
                        ),
                        foundVillagers
                );
            }
        }

        for (Villager villager : foundVillagers.values()) {
            if (!isUsableAttackVillager(villager)) {
                continue;
            }

            if (!belongsToAttackVillage(attack, villager)) {
                continue;
            }

            attack.addDiscoveredVillager(villager.getUUID());
        }
    }

    private static void collectVillagers(ServerLevel level,
                                                         AABB box,
                                                         Map<UUID, Villager> result) {
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box, AttackTargetTracker::isUsableAttackVillager)) {
            result.putIfAbsent(villager.getUUID(), villager);
        }
    }

    private static boolean isUsableAttackVillager(Villager villager) {
        return villager.isAlive()
                && (!RaidbornServerConfig.ATTACK_IGNORE_VILLAGERS_IN_VEHICLES.get() || !villager.isPassenger());
    }

    private static boolean belongsToAttackVillage(AttackInstance attack, Villager villager) {
        int centerRadius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_DETECTION_RADIUS.get()) + 12;

        if (villager.blockPosition().distSqr(attack.getCenter()) <= centerRadius * centerRadius) {
            return true;
        }

        for (BlockPos poiPosition : attack.getVillagePoiPositions()) {
            if (villager.blockPosition().distSqr(poiPosition) <= 56.0D * 56.0D) {
                return true;
            }
        }

        return false;
    }

    /**
     * Safety net for villagers removed without dying (command, another mod).
     *
     * <p>Entity not found means <em>unloaded</em>, not dead: only loaded and provably invalid ones
     * are dropped. The normal loss comes from {@code LivingDeathEvent} in {@link AttackEventHandler}.
     */
    public static void updateAliveVillagers(AttackInstance attack, ServerLevel level) {
        Iterator<UUID> iterator = attack.getAliveVillagerUuids().iterator();

        while (iterator.hasNext()) {
            Entity entity = level.getEntity(iterator.next());

            if (entity != null && (!(entity instanceof Villager villager) || !villager.isAlive())) {
                iterator.remove();
            }
        }
    }

    public static void tickVillagerPanic(AttackInstance attack, ServerLevel level, ServerPlayer owner) {
        List<LivingEntity> threats = collectPanicThreats(attack, level, owner);

        if (threats.isEmpty()) {
            return;
        }

        for (UUID villagerUuid : attack.getAliveVillagerUuids()) {
            Entity entity = level.getEntity(villagerUuid);

            if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                continue;
            }

            LivingEntity closestThreat = threats.stream()
                    .filter(LivingEntity::isAlive)
                    .min(Comparator.comparingDouble(threat -> threat.distanceToSqr(villager)))
                    .orElse(null);

            if (closestThreat == null) {
                continue;
            }

            tickVanillaPanicTriggerStyle(attack, level, villager, closestThreat);
        }
    }

    private static List<LivingEntity> collectPanicThreats(AttackInstance attack, ServerLevel level, ServerPlayer owner) {
        List<LivingEntity> threats = new ArrayList<>();

        if (owner != null && owner.isAlive() && !owner.isCreative() && !owner.isSpectator()) {
            threats.add(owner);
        }

        for (UUID allyUuid : attack.getParticipatingRecruitUuids()) {
            Entity entity = level.getEntity(allyUuid);

            if (entity instanceof LivingEntity living && living.isAlive()) {
                threats.add(living);
            }
        }

        double scanRadius = Math.max(attack.getRadius(), RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get());
        AABB scanBox = new AABB(attack.getCenter()).inflate(scanRadius);

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, scanBox, LivingEntity::isAlive)) {
            if (AttackRaidbornHooks.isPotentialIllagerThreat(living)
                    && !AttackRaidbornHooks.isAttackDefender(living)
                    && AttackRaidbornHooks.isInsideAttackArea(living, attack)) {
                threats.add(living);
            }
        }

        return threats;
    }

    private static void tickVanillaPanicTriggerStyle(AttackInstance attack,
                                                     ServerLevel level,
                                                     Villager villager,
                                                     LivingEntity closestThreat) {
        double distanceSqr = villager.distanceToSqr(closestThreat);

        if (distanceSqr > PANIC_MAX_THREAT_DISTANCE * PANIC_MAX_THREAT_DISTANCE) {
            villager.setSprinting(false);
            return;
        }

        Brain<Villager> brain = villager.getBrain();

        boolean wasAlreadyPanicking = brain.isActive(Activity.PANIC);

        if (!wasAlreadyPanicking) {
            brain.eraseMemory(MemoryModuleType.PATH);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.BREED_TARGET);
            brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        }

        brain.setMemoryWithExpiry(
                MemoryModuleType.NEAREST_HOSTILE,
                closestThreat,
                PANIC_MEMORY_TICKS
        );

        brain.setMemoryWithExpiry(
                MemoryModuleType.HURT_BY_ENTITY,
                closestThreat,
                PANIC_MEMORY_TICKS
        );

        DamageSource fakeDamageSource = createFakePanicDamageSource(level, closestThreat);
        brain.setMemoryWithExpiry(
                MemoryModuleType.HURT_BY,
                fakeDamageSource,
                PANIC_MEMORY_TICKS
        );

        if (!wasAlreadyPanicking) {
            brain.setActiveActivityIfPossible(Activity.PANIC);
        }

        if (level.getGameTime() % 100L == 0L) {
            villager.spawnGolemIfNeeded(level, level.getGameTime(), 3);
        }

        if (villager.tickCount % 80 == 0 && level.random.nextFloat() < 0.35F) {
            level.broadcastEntityEvent(villager, (byte) 42);
        }

        BlockPos nearestBell = attack.getBellPos();

        if (nearestBell != null) {
            setMeetingPoint(villager, level, nearestBell);
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime(), 200L);

            if (villager.distanceToSqr(Vec3.atCenterOf(nearestBell)) <= 5.0D * 5.0D) {
                tryRingBell(level, villager, nearestBell);
            }
        }

        pickPanicWalkTarget(level, villager, closestThreat, nearestBell);
    }

    private static DamageSource createFakePanicDamageSource(ServerLevel level, LivingEntity closestThreat) {
        if (closestThreat instanceof Player player) {
            return level.damageSources().playerAttack(player);
        }

        return level.damageSources().mobAttack(closestThreat);
    }

    private static void setMeetingPoint(Villager villager, ServerLevel level, BlockPos bellPos) {
        villager.getBrain().setMemory(
                MemoryModuleType.MEETING_POINT,
                GlobalPos.of(level.dimension(), bellPos)
        );
    }

    /** The shelter is only looked up after the cooldown passes; before, the result was mostly discarded. */
    private static void pickPanicWalkTarget(ServerLevel level,
                                                              Villager villager,
                                                              LivingEntity closestThreat,
                                                              @Nullable BlockPos nearestBell) {
        long gameTime = level.getGameTime();
        long nextTargetTick = villager.getPersistentData().getLong(NEXT_EXTRA_PANIC_TARGET_TICK_TAG);

        if (gameTime < nextTargetTick && villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return;
        }

        villager.getPersistentData().putLong(
                NEXT_EXTRA_PANIC_TARGET_TICK_TAG,
                gameTime + PANIC_TARGET_MIN_COOLDOWN + level.random.nextInt(PANIC_TARGET_RANDOM_COOLDOWN)
        );

        BlockPos shelter = findShelter(level, villager, closestThreat, nearestBell);

        if (shelter != null) {
            villager.getBrain().setMemoryWithExpiry(
                    MemoryModuleType.HIDING_PLACE,
                    GlobalPos.of(level.dimension(), shelter),
                    240L
            );
        }

        Vec3 target = null;
        float roll = level.random.nextFloat();

        if (nearestBell != null && roll < 0.25F) {
            BlockPos standNearBell = findStandableNear(level, nearestBell, 5);
            if (standNearBell != null) {
                target = Vec3.atBottomCenterOf(standNearBell);
            }
        }

        if (target == null && shelter != null && roll < 0.82F) {
            target = Vec3.atBottomCenterOf(shelter);
        }

        if (target == null) {
            target = DefaultRandomPos.getPosAway(
                    villager,
                    10,
                    5,
                    closestThreat.position()
            );
        }

        if (target == null) {
            return;
        }

        double configuredSpeed = RaidbornServerConfig.ATTACK_VILLAGER_PANIC_SPEED.get();

        float speed = (float) Mth.clamp(configuredSpeed, 0.42D, 0.58D);

        BlockPos targetPos = BlockPos.containing(target);

        if (targetPos.distSqr(villager.blockPosition()) <= PANIC_CLOSE_TARGET_DISTANCE_SQR) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            villager.setSprinting(false);
            return;
        }

        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(targetPos, speed, 1)
        );

        villager.setSprinting(false);
    }

    /**
     * Shelter is any village POI near the villager and away from the threat.
     *
     * <p>{@code PoiManager} already maintains this index: beds, workstations and the bell enter it
     * when the village generates.
     */
    @Nullable
    private static BlockPos findShelter(ServerLevel level,
                                        Villager villager,
                                        LivingEntity closestThreat,
                                        @Nullable BlockPos nearestBell) {
        List<BlockPos> candidates = level.getPoiManager()
                .getInRange(holder -> true, villager.blockPosition(), SHELTER_SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                .map(record -> record.getPos().immutable())
                .limit(SHELTER_MAX_CANDIDATES)
                .sorted(Comparator.comparingDouble(pos -> shelterScore(pos, villager, closestThreat, nearestBell)))
                .toList();

        for (BlockPos candidate : candidates.subList(0, Math.min(4, candidates.size()))) {
            BlockPos standable = findStandableNear(level, candidate, 2);

            if (standable != null) {
                return standable;
            }
        }

        return null;
    }

    private static double shelterScore(BlockPos pos,
                                       Villager villager,
                                       LivingEntity closestThreat,
                                       @Nullable BlockPos nearestBell) {
        double distanceToVillager = pos.distSqr(villager.blockPosition());
        double distanceToThreat = Vec3.atCenterOf(pos).distanceToSqr(closestThreat.position());
        double bellPenalty = nearestBell != null ? pos.distSqr(nearestBell) * 0.015D : 0.0D;

        return distanceToVillager - distanceToThreat * 0.22D + bellPenalty;
    }

    private static BlockPos findStandableNear(ServerLevel level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos mutablePos : BlockPos.betweenClosed(
                origin.offset(-radius, -2, -radius),
                origin.offset(radius, 2, radius)
        )) {
            BlockPos pos = mutablePos.immutable();

            if (!isGoodStandPosition(level, pos)) {
                continue;
            }

            double distance = pos.distSqr(origin);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }

        return best;
    }

    private static boolean isGoodStandPosition(ServerLevel level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        return below.isFaceSturdy(level, belowPos, Direction.UP)
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty();
    }

    /** Resolved once per Attack: the centre does not move and the bell is the meeting POI. */
    @Nullable
    public static BlockPos findVillageBell(ServerLevel level, BlockPos attackCenter) {
        return level.getPoiManager()
                .findClosest(holder -> holder.is(PoiTypes.MEETING), attackCenter, BELL_SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                .orElse(null);
    }

    private static void tryRingBell(ServerLevel level, Villager villager, BlockPos bellPos) {
        long gameTime = level.getGameTime();
        long nextRingTick = villager.getPersistentData().getLong(NEXT_BELL_RING_TICK_TAG);

        if (gameTime < nextRingTick) {
            return;
        }

        villager.getPersistentData().putLong(
                NEXT_BELL_RING_TICK_TAG,
                gameTime + 70L + level.random.nextInt(90)
        );

        BlockState bellState = level.getBlockState(bellPos);

        if (!bellState.is(Blocks.BELL)) {
            return;
        }

        level.blockEvent(bellPos, bellState.getBlock(), 1, Direction.NORTH.get3DDataValue());

        level.playSound(
                null,
                bellPos,
                SoundEvents.BELL_BLOCK,
                SoundSource.BLOCKS,
                2.0F,
                0.95F + level.random.nextFloat() * 0.1F
        );

        for (Villager nearbyVillager : level.getEntitiesOfClass(
                Villager.class,
                new AABB(bellPos).inflate(32.0D),
                Villager::isAlive
        )) {
            nearbyVillager.getBrain().setMemoryWithExpiry(
                    MemoryModuleType.HEARD_BELL_TIME,
                    gameTime,
                    200L
            );

            setMeetingPoint(nearbyVillager, level, bellPos);
        }
    }

    public static void clearVillagerPanic(AttackInstance attack, ServerLevel level) {
        for (UUID villagerUuid : attack.getInitialVillagerUuids()) {
            Entity entity = level.getEntity(villagerUuid);

            if (!(entity instanceof Villager villager)) {
                continue;
            }

            Brain<Villager> brain = villager.getBrain();

            brain.eraseMemory(MemoryModuleType.HURT_BY);
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
            brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);

            villager.setLastHurtByMob(null);
            villager.setSprinting(false);
            villager.getNavigation().stop();

            villager.getPersistentData().remove(NEXT_BELL_RING_TICK_TAG);
            villager.getPersistentData().remove(NEXT_EXTRA_PANIC_TARGET_TICK_TAG);

            brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
        }
    }

    public static void tickVillagerVictoryCelebration(AttackInstance attack, ServerLevel level) {
        for (UUID villagerUuid : attack.getAliveVillagerUuids()) {
            Entity entity = level.getEntity(villagerUuid);

            if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                continue;
            }

            villager.getBrain().setMemoryWithExpiry(
                    MemoryModuleType.CELEBRATE_LOCATION,
                    villager.blockPosition(),
                    600L
            );

            if (villager.onGround() && villager.tickCount % 12 == 0) {
                villager.getJumpControl().jump();
            }

            if (level.getGameTime() % 20L == 0L) {
                level.broadcastEntityEvent(villager, (byte) 14);

                level.playSound(
                        null,
                        villager.getX(),
                        villager.getY(),
                        villager.getZ(),
                        SoundEvents.VILLAGER_CELEBRATE,
                        SoundSource.NEUTRAL,
                        1.0F,
                        0.9F + level.getRandom().nextFloat() * 0.2F
                );

                level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        villager.getX(),
                        villager.getY() + 1.1D,
                        villager.getZ(),
                        8,
                        0.35D,
                        0.35D,
                        0.35D,
                        0.02D
                );
            }

            if (level.getGameTime() % 40L == 0L && level.getRandom().nextFloat() < 0.45F) {
                AttackCelebration.launchFirework(level, villager, DyeColor.YELLOW, DyeColor.WHITE, DyeColor.WHITE);
            }
        }
    }
}
