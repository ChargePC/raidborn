package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AttackManager {
    private static final Map<UUID, AttackInstance> ACTIVE_ATTACKS = new HashMap<>();

    private AttackManager() {
    }

    public static void tryStartAttack(ServerPlayer player) {
        if (!RaidbornServerConfig.ATTACK_ENABLED.get()) {
            return;
        }

        if (!ModEffects.hasAllianceEffect(player)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        if (isPlayerAlreadyOwnerOfAttack(player.getUUID())) {
            return;
        }

        Optional<AttackDetectionResult> detection = AttackVillageDetector.detect(level, player.blockPosition());
        if (detection.isEmpty()) {
            return;
        }

        AttackDetectionResult result = detection.get();

        if (hasActiveAttackNear(level.dimension(), result.center(), RaidbornServerConfig.ATTACK_ABANDON_RADIUS.get())) {
            return;
        }

        AttackSavedData savedData = AttackSavedData.get(level);
        if (savedData.isOnCooldown(level.dimension(), result.center(), result.radius(), level.getGameTime())) {
            return;
        }

        Set<UUID> villagerUuids = new HashSet<>();
        for (Villager villager : result.villagers()) {
            villagerUuids.add(villager.getUUID());
        }

        AttackRaidbornHooks.AttackTier attackTier = AttackRaidbornHooks.getAttackTier(player);

        Set<BlockPos> poiPositions = new HashSet<>(result.poiPositions());

        AttackInstance attack = new AttackInstance(
                UUID.randomUUID(),
                player.getUUID(),
                level.dimension(),
                result.center(),
                result.radius(),
                villagerUuids,
                poiPositions,
                attackTier
        );

        ACTIVE_ATTACKS.put(attack.getAttackId(), attack);
        attack.start(level, player, result);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE_ATTACKS.isEmpty()) {
            return;
        }

        for (AttackInstance attack : new ArrayList<>(ACTIVE_ATTACKS.values())) {
            attack.tick(server);
            registerCooldownIfNeeded(server, attack);

            if (attack.shouldBeRemoved()) {
                attack.removeBossBar();
                ServerLevel level = server.getLevel(attack.getDimension());
                if (level != null && attack.isVictory()) {
                    AttackIllagerAIHandler.clearAllies(attack, level);
                }
                ACTIVE_ATTACKS.remove(attack.getAttackId());
            }
        }
    }

    /**
     * Attack state lives in memory only. On shutdown each one is ended with the full cleanup,
     * otherwise the marks written on entities outlive the restart with no event owning them.
     */
    public static void shutdown(MinecraftServer server) {
        for (AttackInstance attack : new ArrayList<>(ACTIVE_ATTACKS.values())) {
            ServerLevel level = server.getLevel(attack.getDimension());

            if (level != null) {
                attack.shutdown(level);
            }
        }

        ACTIVE_ATTACKS.clear();
    }

    public static void onVillagerLost(UUID villagerUuid) {
        for (AttackInstance attack : ACTIVE_ATTACKS.values()) {
            attack.onVillagerLost(villagerUuid);
        }
    }

    private static void registerCooldownIfNeeded(MinecraftServer server, AttackInstance attack) {
        if (!attack.isEnded() || attack.isCooldownRegistered()) {
            return;
        }

        ServerLevel level = server.getLevel(attack.getDimension());
        if (level == null) {
            attack.setCooldownRegistered(true);
            return;
        }

        long expiresAt = level.getGameTime() + RaidbornServerConfig.ATTACK_COOLDOWN_TICKS.get();
        AttackSavedData.get(level).addCooldown(attack.getDimension(), attack.getCenter(), attack.getRadius(), expiresAt);
        attack.setCooldownRegistered(true);
    }

    private static boolean isPlayerAlreadyOwnerOfAttack(UUID playerUuid) {
        for (AttackInstance attack : ACTIVE_ATTACKS.values()) {
            if (!attack.isEnded() && attack.getOwnerPlayerUuid().equals(playerUuid)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasActiveAttackNear(ResourceKey<Level> dimension, BlockPos center, double radius) {
        double radiusSqr = radius * radius;

        for (AttackInstance attack : ACTIVE_ATTACKS.values()) {
            if (attack.isEnded()) {
                continue;
            }

            if (!attack.getDimension().equals(dimension)) {
                continue;
            }

            if (attack.getCenter().distSqr(center) <= radiusSqr) {
                return true;
            }
        }

        return false;
    }

    public static Optional<AttackInstance> getAttack(UUID attackId) {
        return Optional.ofNullable(ACTIVE_ATTACKS.get(attackId));
    }
}
