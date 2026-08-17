package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.gameplay.bossbar.EventBossBarController;
import net.randomcara.raidborn.content.item.utility.VillageLootItem;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

public class AttackInstance {
    private static final int START_BOSSBAR_FILL_TICKS = 300;

    private static final int AREA_SCAN_INTERVAL_TICKS = 10;

    private final UUID attackId;
    private final UUID ownerPlayerUuid;
    private final ResourceKey<Level> dimension;
    private final BlockPos center;
    private final int radius;
    private final AttackRaidbornHooks.AttackTier attackTier;

    private final Set<UUID> initialVillagerUuids = new LinkedHashSet<>();
    private final Set<UUID> aliveVillagerUuids = new LinkedHashSet<>();
    private final Set<UUID> spawnedDefenderUuids = new LinkedHashSet<>();
    /** Spawned plus pre-existing, kept together so the per-tick lookup does not allocate. */
    private final Set<UUID> allDefenderUuids = new LinkedHashSet<>();
    private final Set<UUID> participatingRecruitUuids = new LinkedHashSet<>();
    private final Set<BlockPos> villagePoiPositions = new LinkedHashSet<>();

    /** Resolved once on start: the event centre does not move. */
    @Nullable
    private BlockPos bellPos;

    private int activeTickCount;
    private int abandonTickCount;
    private int endedTickCount;

    private AttackState state = AttackState.ACTIVE;

    /** Cleanup runs from several paths (victory, defeat, server shutdown) and must only run once. */
    private boolean cleanupDone;

    private boolean cooldownRegistered;

    public AttackInstance(UUID attackId,
                          UUID ownerPlayerUuid,
                          ResourceKey<Level> dimension,
                          BlockPos center,
                          int radius,
                          Set<UUID> initialVillagers,
                          Set<BlockPos> villagePoiPositions,
                          AttackRaidbornHooks.AttackTier attackTier) {
        this.attackId = attackId;
        this.ownerPlayerUuid = ownerPlayerUuid;
        this.dimension = dimension;
        this.center = center.immutable();
        this.radius = radius;
        this.attackTier = attackTier;
        this.initialVillagerUuids.addAll(initialVillagers);
        this.aliveVillagerUuids.addAll(initialVillagers);
        for (BlockPos poiPosition : villagePoiPositions) {
            this.villagePoiPositions.add(poiPosition.immutable());
        }
    }

    public void start(ServerLevel level, ServerPlayer owner, AttackDetectionResult result) {
        this.bellPos = AttackTargetTracker.findVillageBell(level, center);

        // Before registering allies, so anyone teleported now already counts as a participant.
        AttackRecruitRally.rallyRecruitsToOwner(level, owner);

        AttackDefenderSpawner.prepareDefenders(this, level, result);
        AttackIllagerAIHandler.registerNearbyAllies(this, level, owner);
        AttackTargetTracker.reinforceVillageTargets(this, level);
        AttackTargetTracker.tickVillagerPanic(this, level, owner);
        createBossBar(owner);
        playAttackStartSound(level, owner);
    }

    private void playAttackStartSound(ServerLevel level, ServerPlayer owner) {
        level.playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                SoundEvents.RAID_HORN.value(),
                SoundSource.HOSTILE,
                64.0F,
                1.0F
        );
    }

    public void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerPlayerUuid);

        if (level == null) {
            if (state.isOver()) {
                endedTickCount++;
            } else {
                fail(null);
            }

            return;
        }

        if (state.isOver()) {
            tickEnded(level, owner);
            return;
        }

        activeTickCount++;

        if (owner == null || !owner.isAlive()) {
            fail(level);
            return;
        }

        if (!owner.level().dimension().equals(dimension)) {
            tickAbandon(level, owner);
        } else {
            double distanceSqr = owner.distanceToSqr(
                    center.getX() + 0.5D,
                    center.getY() + 0.5D,
                    center.getZ() + 0.5D
            );

            double abandonRadius = RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get();

            if (distanceSqr > abandonRadius * abandonRadius) {
                tickAbandon(level, owner);
            } else {
                abandonTickCount = 0;
            }
        }

        if (state.isOver()) {
            return;
        }

        if (!level.hasChunkAt(center)) {
            fail(level);
            return;
        }

        if (activeTickCount >= getTimeLimitTicks()) {
            fail(level);
            return;
        }

        if (activeTickCount % 40 == 0) {
            AttackTargetTracker.reinforceVillageTargets(this, level);
            AttackTargetTracker.updateAliveVillagers(this, level);
        }

        /*
         * The three routines below scan the whole event area for entities. None of them needs a
         * one-tick response: panic memories last 100 ticks and the defender retarget window is ~40.
         * Running them on different phases keeps it to at most one scan per tick instead of three.
         */
        if (activeTickCount % AREA_SCAN_INTERVAL_TICKS == 0) {
            AttackTargetTracker.tickVillagerPanic(this, level, owner);
        }

        if (activeTickCount % AREA_SCAN_INTERVAL_TICKS == 3) {
            AttackIllagerAIHandler.registerNearbyAllies(this, level, owner);
        }

        if (activeTickCount % AREA_SCAN_INTERVAL_TICKS == 6) {
            DefenderTargeting.tick(this, level, owner);
        }

        AttackIllagerAIHandler.tickAllyTargets(this, level, owner);

        if (aliveVillagerUuids.isEmpty()) {
            completeVictory(level, owner);
            return;
        }

        updateBossBar(owner);
    }

    private void tickAbandon(ServerLevel level, ServerPlayer owner) {
        abandonTickCount++;

        if (abandonTickCount >= RaidbornServerConfig.ATTACK_ABANDON_TIME_TICKS.get()) {
            abandon(level);
        } else {
            updateBossBar(owner);
        }
    }

    private void tickEnded(ServerLevel level, ServerPlayer owner) {
        endedTickCount++;

        if (state == AttackState.VICTORY) {
            AttackIllagerAIHandler.tickVictoryCelebration(this, level, owner);
        } else {
            AttackTargetTracker.tickVillagerVictoryCelebration(this, level);
        }

        updateBossBar(owner);
    }

    /** Only reachable from an ACTIVE tick, so the rewards are granted exactly once. */
    private void completeVictory(ServerLevel level, ServerPlayer owner) {
        state = AttackState.VICTORY;
        abandonTickCount = 0;

        grantRewards(owner);
        AttackIllagerAIHandler.tickVictoryCelebration(this, level, owner);
        updateBossBar(owner);
        cleanupAfterEnd(level);
    }

    private void abandon(ServerLevel level) {
        state = AttackState.ABANDONED;
        cleanupAfterEnd(level);
    }

    private void fail(@Nullable ServerLevel level) {
        state = AttackState.FAILED;

        if (level != null) {
            cleanupAfterEnd(level);
        }
    }

    private void cleanupAfterEnd(ServerLevel level) {
        if (cleanupDone) {
            return;
        }

        AttackTargetTracker.clearVillagerPanic(this, level);

        if (RaidbornServerConfig.ATTACK_DESPAWN_SPAWNED_DEFENDERS_AFTER_END.get()) {
            AttackDefenderSpawner.removeSpawnedDefenders(this, level);
        }

        // Every mark is cleared here, including on defenders that already lived in the village.
        // Leaving it written made the Juggernaut treat that golem as an event defender forever.
        clearMarks(level, allDefenderUuids);

        if (state != AttackState.VICTORY) {
            AttackIllagerAIHandler.clearAllies(this, level);
        }

        cleanupDone = true;
    }

    private static void clearMarks(ServerLevel level, Set<UUID> uuids) {
        for (UUID uuid : uuids) {
            Entity entity = level.getEntity(uuid);

            if (entity != null) {
                AttackRaidbornHooks.clearAttackMarks(entity);
            }
        }
    }

    /**
     * Forced shutdown. Attack state is not persisted, so everything it spread through the world has
     * to be undone before the final save.
     */
    void shutdown(ServerLevel level) {
        if (state == AttackState.ACTIVE) {
            state = AttackState.FAILED;
        }

        cleanupAfterEnd(level);
        AttackIllagerAIHandler.clearAllies(this, level);
        removeBossBar();
    }

    private int getTimeLimitTicks() {
        int villagers = getInitialVillagerCount();

        if (villagers <= 5) {
            return RaidbornServerConfig.ATTACK_SMALL_TIME_LIMIT_TICKS.get();
        }

        if (villagers <= 12) {
            return RaidbornServerConfig.ATTACK_MEDIUM_TIME_LIMIT_TICKS.get();
        }

        return RaidbornServerConfig.ATTACK_LARGE_TIME_LIMIT_TICKS.get();
    }

    public boolean shouldBeRemoved() {
        if (!state.isOver()) {
            return false;
        }

        return endedTickCount >= (state == AttackState.VICTORY
                ? RaidbornServerConfig.ATTACK_VICTORY_CELEBRATION_TICKS.get()
                : RaidbornServerConfig.ATTACK_END_BOSSBAR_DELAY_TICKS.get());
    }

    private void createBossBar(ServerPlayer owner) {
        EventBossBarController.create(
                attackId,
                Component.translatable("event.raidborn.attack"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10,
                owner
        );

        updateBossBar(owner);
    }

    private void updateBossBar(@Nullable ServerPlayer owner) {
        EventBossBarController.update(attackId, bossBarName(), bossBarProgress(), owner);
    }

    void removeBossBar() {
        EventBossBarController.remove(attackId);
    }

    private Component bossBarName() {
        return switch (state) {
            case VICTORY -> Component.translatable("event.raidborn.attack.victory");
            case ABANDONED -> Component.translatable("event.raidborn.attack.abandoned");
            case FAILED -> Component.translatable("event.raidborn.attack.failed");
            case ACTIVE -> {
                int alive = aliveVillagerUuids.size();

                yield !isStartBossBarFilling() && alive > 0 && alive <= 2
                        ? Component.translatable("event.raidborn.attack.remaining", alive)
                        : Component.translatable("event.raidborn.attack");
            }
        };
    }

    /**
     * Fills up over the first {@link #START_BOSSBAR_FILL_TICKS} so the bar reads as the village
     * mustering, then tracks the villagers still standing and empties on victory.
     */
    private float bossBarProgress() {
        if (state == AttackState.VICTORY) {
            return 0.0F;
        }

        if (state.isDefeat()) {
            return 1.0F;
        }

        if (isStartBossBarFilling()) {
            return Mth.clamp((float) activeTickCount / (float) START_BOSSBAR_FILL_TICKS, 0.0F, 1.0F);
        }

        int initial = Math.max(1, initialVillagerUuids.size());
        int alive = Math.max(0, aliveVillagerUuids.size());

        return Mth.clamp((float) alive / (float) initial, 0.0F, 1.0F);
    }

    private boolean isStartBossBarFilling() {
        return !state.isOver() && activeTickCount < START_BOSSBAR_FILL_TICKS;
    }

    private void grantRewards(ServerPlayer owner) {
        ItemStack loot = VillageLootItem.createForTier(attackTier);

        if (loot.isEmpty()) {
            return;
        }

        owner.getInventory().add(loot);

        if (!loot.isEmpty()) {
            owner.drop(loot, false);
        }

        owner.containerMenu.broadcastChanges();
    }

    public void onVillagerLost(UUID uuid) {
        aliveVillagerUuids.remove(uuid);
    }

    public Set<UUID> getAllDefenderUuids() {
        return allDefenderUuids;
    }

    public boolean isRegisteredDefender(UUID uuid) {
        return allDefenderUuids.contains(uuid);
    }

    public void addExistingDefender(UUID uuid) {
        allDefenderUuids.add(uuid);
    }

    public void addSpawnedDefender(UUID uuid) {
        spawnedDefenderUuids.add(uuid);
        allDefenderUuids.add(uuid);
    }

    public void addParticipatingRecruit(UUID uuid) {
        participatingRecruitUuids.add(uuid);
    }

    public boolean addDiscoveredVillager(UUID uuid) {
        boolean added = initialVillagerUuids.add(uuid);
        aliveVillagerUuids.add(uuid);
        return added;
    }

    public UUID getAttackId() {
        return attackId;
    }

    public UUID getOwnerPlayerUuid() {
        return ownerPlayerUuid;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getCenter() {
        return center;
    }

    @Nullable
    public BlockPos getBellPos() {
        return bellPos;
    }

    public int getRadius() {
        return radius;
    }

    public AttackRaidbornHooks.AttackTier getAttackTier() {
        return attackTier;
    }

    public Set<UUID> getInitialVillagerUuids() {
        return initialVillagerUuids;
    }

    public Set<UUID> getAliveVillagerUuids() {
        return aliveVillagerUuids;
    }

    public Set<UUID> getSpawnedDefenderUuids() {
        return spawnedDefenderUuids;
    }

    public Set<UUID> getParticipatingRecruitUuids() {
        return participatingRecruitUuids;
    }

    public Set<BlockPos> getVillagePoiPositions() {
        return villagePoiPositions;
    }

    private int getInitialVillagerCount() {
        return initialVillagerUuids.size();
    }


    public AttackState getState() {
        return state;
    }

    public boolean isVictory() {
        return state == AttackState.VICTORY;
    }

    public boolean isFailed() {
        return state.isDefeat();
    }

    public boolean isAbandoned() {
        return state == AttackState.ABANDONED;
    }

    public boolean isEnded() {
        return state.isOver();
    }

    public boolean isCooldownRegistered() {
        return cooldownRegistered;
    }

    public void setCooldownRegistered(boolean cooldownRegistered) {
        this.cooldownRegistered = cooldownRegistered;
    }
}
