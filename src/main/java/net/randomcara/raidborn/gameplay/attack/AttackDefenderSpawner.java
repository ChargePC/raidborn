package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;
import net.randomcara.raidborn.content.entity.juggernaut.Juggernaut;
import net.randomcara.raidborn.content.entity.juggernaut.JuggernautOrigin;
import net.randomcara.raidborn.content.entity.juggernaut.JuggernautVillageEvents;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEntities;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puts the village's defence on the field when an Attack starts, and takes it off when it ends.
 *
 * <p>Three questions, answered in order and all only ever asked from here: how many defenders the
 * tier owes after discounting the ones already standing around, where each one fits, and the
 * spawning and bookkeeping itself. Who they fight afterwards is {@link DefenderTargeting}.
 */
public final class AttackDefenderSpawner {

    /** A tight village may have no free 3x3 around the first villager drawn, so several are tried. */
    private static final int HERO_JUGGERNAUT_ORIGIN_ATTEMPTS = 6;

    /** A gentle slope passes, a village roof does not. Same limits as the Juggernaut spawner. */
    private static final int SPAWN_MAX_RISE_ABOVE_ORIGIN = 2;
    private static final int SPAWN_MAX_HEIGHT_DIFFERENCE = 8;
    private static final int SPAWN_HEADROOM_BLOCKS = 3;

    private AttackDefenderSpawner() {
    }

    public static void prepareDefenders(AttackInstance attack, ServerLevel level, AttackDetectionResult result) {
        for (IronGolem defender : result.existingDefenders()) {
            attack.addExistingDefender(defender.getUUID());
            AttackRaidbornHooks.markExistingAttackDefender(defender, attack.getAttackId());
        }

        if (result.villagers().isEmpty()) {
            return;
        }

        spawnHeroJuggernaut(attack, level, result);
        spawnHeroSuperDefender(attack, level, result);

        if (!RaidbornServerConfig.ATTACK_SPAWN_DEFENDERS_PER_VILLAGER.get()) {
            return;
        }

        int villagerCount = result.villagers().size();

        Map<EntityType<?>, Integer> wanted = plannedForTier(attack.getAttackTier(), villagerCount);
        discount(wanted, enrollDefendersAlreadyInVillage(attack, level, result));

        spawnWave(attack, level, result, wanted, capForVillageSize(villagerCount));
    }

    public static void removeSpawnedDefenders(AttackInstance attack, ServerLevel level) {
        for (UUID defenderUuid : attack.getSpawnedDefenderUuids()) {
            Entity entity = level.getEntity(defenderUuid);

            if (entity != null && AttackRaidbornHooks.isSpawnedAttackDefender(entity)) {
                entity.discard();
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // How many
    // ------------------------------------------------------------------------------------------

    /** The tier picks the ratio per villager, the config caps each type. */
    private static Map<EntityType<?>, Integer> plannedForTier(AttackRaidbornHooks.AttackTier tier, int villagers) {
        Map<EntityType<?>, Integer> wanted = new LinkedHashMap<>();

        switch (tier) {
            case HERO -> {
                addWanted(wanted, EntityType.IRON_GOLEM, villagers / 2, RaidbornServerConfig.ATTACK_HERO_MAX_IRON_GOLEMS.get());
                addWanted(wanted, ModEntities.IRON_GOLLET.get(), villagers, RaidbornServerConfig.ATTACK_HERO_MAX_IRON_GOLLETS.get());
            }
            case HONOR -> {
                addWanted(wanted, EntityType.IRON_GOLEM, villagers / 3, RaidbornServerConfig.ATTACK_HONOR_MAX_IRON_GOLEMS.get());
                addWanted(wanted, ModEntities.IRON_GOLLET.get(), villagers, RaidbornServerConfig.ATTACK_HONOR_MAX_IRON_GOLLETS.get());
            }
            default -> {
                addWanted(wanted, EntityType.IRON_GOLEM, villagers / 4, RaidbornServerConfig.ATTACK_LOYALTY_MAX_IRON_GOLEMS.get());
                addWanted(wanted, ModEntities.IRON_GOLLET.get(), villagers / 2, RaidbornServerConfig.ATTACK_LOYALTY_MAX_IRON_GOLLETS.get());
            }
        }

        for (RaidbornServerConfig.ExtraDefenderSlot slot : RaidbornServerConfig.ATTACK_EXTRA_DEFENDERS) {
            addExtraSlot(wanted, tier, villagers, slot);
        }

        return wanted;
    }

    private static void addExtraSlot(Map<EntityType<?>, Integer> wanted,
                                     AttackRaidbornHooks.AttackTier tier,
                                     int villagers,
                                     RaidbornServerConfig.ExtraDefenderSlot slot) {
        if (!slot.enabled().get()) {
            return;
        }

        int perVillager = tier == AttackRaidbornHooks.AttackTier.LOYALTY ? villagers / 2 : villagers;

        int slotCap = switch (tier) {
            case HERO -> slot.heroMax().get();
            case HONOR -> slot.honorMax().get();
            default -> slot.loyaltyMax().get();
        };

        entityTypeOf(slot.entityId().get()).ifPresent(type -> addWanted(wanted, type, perVillager, slotCap));
    }

    private static void addWanted(Map<EntityType<?>, Integer> wanted, EntityType<?> type, int count, int typeCap) {
        int capped = Math.min(count, typeCap);

        if (type == null || capped <= 0) {
            return;
        }

        wanted.merge(type, capped, Integer::sum);
    }

    private static void discount(Map<EntityType<?>, Integer> wanted, Map<EntityType<?>, Integer> alreadyPresent) {
        alreadyPresent.forEach((type, present) ->
                wanted.computeIfPresent(type, (ignored, count) -> count > present ? count - present : null));
    }

    /**
     * The ceiling on a whole wave, on top of the per-type limits each tier already applies.
     *
     * <p>Stock values leave room to spare — the most any tier asks for is seven — so this only comes
     * into play once someone raises the per-tier maximums or turns on the extra defender slots. A
     * value of zero switches off the per-villager wave entirely.
     */
    private static int capForVillageSize(int villagers) {
        if (villagers <= 5) {
            return RaidbornServerConfig.ATTACK_MAX_EXTRA_DEFENDERS_SMALL.get();
        }

        if (villagers <= 12) {
            return RaidbornServerConfig.ATTACK_MAX_EXTRA_DEFENDERS_MEDIUM.get();
        }

        return RaidbornServerConfig.ATTACK_MAX_EXTRA_DEFENDERS_LARGE.get();
    }

    /** Entity types come from the config as strings, so an unknown or misspelled id is expected. */
    private static Optional<EntityType<?>> entityTypeOf(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        ResourceLocation location = ResourceLocation.tryParse(id.trim());

        return location == null
                ? Optional.empty()
                : Optional.ofNullable(ForgeRegistries.ENTITY_TYPES.getValue(location));
    }

    /**
     * Registers the golems the village already had as defenders of this Attack and reports how many
     * of each type they are, so the wave does not land on top of them.
     */
    private static Map<EntityType<?>, Integer> enrollDefendersAlreadyInVillage(AttackInstance attack,
                                                                              ServerLevel level,
                                                                              AttackDetectionResult result) {
        Map<UUID, EntityType<?>> byUuid = new LinkedHashMap<>();

        for (IronGolem defender : result.existingDefenders()) {
            enroll(byUuid, defender, attack);
        }

        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, villageScanBox(attack), IronGolem::isAlive)) {
            enroll(byUuid, golem, attack);
        }

        Map<EntityType<?>, Integer> counts = new LinkedHashMap<>();

        for (EntityType<?> entityType : byUuid.values()) {
            counts.merge(entityType, 1, Integer::sum);
        }

        return counts;
    }

    private static void enroll(Map<UUID, EntityType<?>> byUuid, Mob defender, AttackInstance attack) {
        if (defender == null || !defender.isAlive()) {
            return;
        }

        byUuid.putIfAbsent(defender.getUUID(), defender.getType());

        if (defender instanceof IronGollet gollet) {
            gollet.setVillageLinked(true);
        }

        attack.addExistingDefender(defender.getUUID());
        AttackRaidbornHooks.markExistingAttackDefender(defender, attack.getAttackId());
    }

    // ------------------------------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------------------------------

    /**
     * Types are filled in the order they were planned, so the golems and gollets a tier promises are
     * served first and the configured extra slots share out whatever budget is left.
     */
    private static void spawnWave(AttackInstance attack,
                                  ServerLevel level,
                                  AttackDetectionResult result,
                                  Map<EntityType<?>, Integer> wanted,
                                  int cap) {
        if (wanted.isEmpty() || cap <= 0) {
            return;
        }

        int spawned = 0;

        for (Map.Entry<EntityType<?>, Integer> entry : wanted.entrySet()) {
            if (spawned >= cap) {
                break;
            }

            spawned += spawnBatch(attack, level, result, entry.getKey(), Math.min(entry.getValue(), cap - spawned));
        }
    }

    private static int spawnBatch(AttackInstance attack,
                                  ServerLevel level,
                                  AttackDetectionResult result,
                                  EntityType<?> defenderType,
                                  int wanted) {
        if (wanted <= 0) {
            return 0;
        }

        List<Villager> villagers = result.villagers();
        int maxAttempts = Math.max(villagers.size() * 8, wanted * 10 + 24);
        int spawned = 0;

        for (int attempt = 0; attempt < maxAttempts && spawned < wanted; attempt++) {
            BlockPos anchor = villagers.get(attempt % villagers.size()).blockPosition();

            Optional<BlockPos> pos = findSpawnPos(
                    level,
                    defenderType,
                    anchor,
                    result.poiPositions(),
                    attack.getCenter()
            );

            if (pos.isPresent() && spawnDefender(level, defenderType, pos.get(), attack)) {
                spawned++;
            }
        }

        return spawned;
    }

    private static void spawnHeroJuggernaut(AttackInstance attack, ServerLevel level, AttackDetectionResult result) {
        if (attack.getAttackTier() != AttackRaidbornHooks.AttackTier.HERO) {
            return;
        }

        if (hasJuggernautInVillage(attack, level)) {
            Raidborn.LOGGER.debug(
                    "Juggernaut: Hero Attack em {} já tem um Juggernaut na área, mantendo o existente",
                    attack.getCenter().toShortString()
            );
            return;
        }

        List<Villager> villagers = result.villagers();
        int origins = Math.min(villagers.size(), HERO_JUGGERNAUT_ORIGIN_ATTEMPTS);
        int firstIndex = level.random.nextInt(villagers.size());

        Juggernaut juggernaut = null;

        for (int i = 0; i < origins && juggernaut == null; i++) {
            BlockPos origin = villagers.get((firstIndex + i) % villagers.size()).blockPosition();
            juggernaut = JuggernautVillageEvents.spawnJuggernaut(level, origin, JuggernautOrigin.EVENT, MobSpawnType.EVENT);
        }

        if (juggernaut == null) {
            juggernaut = JuggernautVillageEvents.spawnJuggernaut(
                    level,
                    attack.getCenter(),
                    JuggernautOrigin.EVENT,
                    MobSpawnType.EVENT
            );
        }

        if (juggernaut == null) {
            Raidborn.LOGGER.debug(
                    "Juggernaut: Hero Attack em {} sem espaço livre para o defensor",
                    attack.getCenter().toShortString()
            );
            return;
        }

        AttackRaidbornHooks.markSpawnedAttackDefender(juggernaut, attack.getAttackId());
        attack.addSpawnedDefender(juggernaut.getUUID());
    }

    private static void spawnHeroSuperDefender(AttackInstance attack, ServerLevel level, AttackDetectionResult result) {
        if (attack.getAttackTier() != AttackRaidbornHooks.AttackTier.HERO
                || !RaidbornServerConfig.ATTACK_HERO_SUPER_DEFENDER_ENABLED.get()) {
            return;
        }

        Optional<EntityType<?>> superDefender =
                entityTypeOf(RaidbornServerConfig.ATTACK_HERO_SUPER_DEFENDER_ENTITY_ID.get());

        if (superDefender.isEmpty()) {
            return;
        }

        EntityType<?> entityType = superDefender.get();

        if (hasSuperDefenderInVillage(attack, level, entityType)) {
            return;
        }

        List<Villager> villagers = result.villagers();
        BlockPos origin = villagers.get(level.random.nextInt(villagers.size())).blockPosition();

        findSpawnPos(level, entityType, origin, result.poiPositions(), attack.getCenter())
                .ifPresent(pos -> spawnDefender(level, entityType, pos, attack));
    }

    private static boolean spawnDefender(ServerLevel level, EntityType<?> entityType, BlockPos pos, AttackInstance attack) {
        if (!(entityType.create(level) instanceof Mob mob)) {
            return false;
        }

        mob.moveTo(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F,
                0.0F
        );

        if (mob instanceof IronGolem golem) {
            golem.setPlayerCreated(false);
        }

        if (mob instanceof IronGollet gollet) {
            gollet.setVillageLinked(true);
        }

        ForgeEventFactory.onFinalizeSpawn(
                mob,
                level,
                level.getCurrentDifficultyAt(pos),
                MobSpawnType.EVENT,
                null,
                null
        );

        if (RaidbornServerConfig.ATTACK_EXTRA_DEFENDERS_PERSISTENT.get()) {
            mob.setPersistenceRequired();
        }

        AttackRaidbornHooks.markSpawnedAttackDefender(mob, attack.getAttackId());

        if (!level.addFreshEntity(mob)) {
            mob.discard();
            return false;
        }

        attack.addSpawnedDefender(mob.getUUID());
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Duplicate guards
    // ------------------------------------------------------------------------------------------

    /**
     * A village fields one Juggernaut at a time.
     *
     * <p>The natural and raid spawns each keep their own slot in the village record, but an Attack
     * has no such slot and cannot use theirs: it can start next to a Juggernaut either of them
     * already placed, or next to one a previous Attack left behind because its chunk was unloaded
     * when the cleanup ran. So the check is against the ground rather than against bookkeeping.
     *
     * <p>Whoever is already standing there has been registered as a defender of this Attack by the
     * detector scan, which collects {@link IronGolem} and therefore covers the Juggernaut too.
     */
    private static boolean hasJuggernautInVillage(AttackInstance attack, ServerLevel level) {
        return !level.getEntitiesOfClass(Juggernaut.class, villageScanBox(attack), Juggernaut::isAlive).isEmpty();
    }

    /** Same rule as the Juggernaut, for whatever entity the config names as the Hero super defender. */
    private static boolean hasSuperDefenderInVillage(AttackInstance attack, ServerLevel level, EntityType<?> type) {
        return !level.getEntities(
                (Entity) null,
                villageScanBox(attack),
                entity -> entity.isAlive() && entity.getType() == type
        ).isEmpty();
    }

    /** The village floor, never smaller than 16 blocks so a tiny hamlet still gets a sane area. */
    private static AABB villageScanBox(AttackInstance attack) {
        return new AABB(attack.getCenter()).inflate(Math.max(16.0D, attack.getRadius()));
    }

    // ------------------------------------------------------------------------------------------
    // Where they fit
    // ------------------------------------------------------------------------------------------

    /**
     * Ring around the villager first, then around the nearest POI, then a wider ring around the
     * event centre. Each fallback is looser than the last, so a cramped village still gets its
     * defenders somewhere sensible instead of none at all.
     */
    private static Optional<BlockPos> findSpawnPos(ServerLevel level,
                                                   EntityType<?> entityType,
                                                   BlockPos villagerPos,
                                                   List<BlockPos> pois,
                                                   BlockPos center) {
        Optional<BlockPos> nearVillager = searchRing(level, entityType, villagerPos, 3, 8, 40);
        if (nearVillager.isPresent()) {
            return nearVillager;
        }

        Optional<BlockPos> closestPoi = pois.stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(villagerPos)));

        if (closestPoi.isPresent()) {
            Optional<BlockPos> nearPoi = searchRing(level, entityType, closestPoi.get(), 3, 10, 40);
            if (nearPoi.isPresent()) {
                return nearPoi;
            }
        }

        return searchRing(level, entityType, center, 4, 14, 64);
    }

    private static Optional<BlockPos> searchRing(ServerLevel level,
                                                 EntityType<?> entityType,
                                                 BlockPos origin,
                                                 int minDistance,
                                                 int maxDistance,
                                                 int attempts) {
        RandomSource random = level.random;

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.nextInt(random, minDistance, maxDistance);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);

            BlockPos ground = groundNear(level, entityType, x, z, origin.getY());

            if (ground != null) {
                return Optional.of(ground);
            }
        }

        return Optional.empty();
    }

    /**
     * Searches for ground starting from the origin height, not from the top of the terrain.
     *
     * <p>{@code getHeightmapPos} returns the house roof when x/z falls on a building, and a flat roof
     * passes any "solid floor, open sky" test, which is how defenders ended up spawning on rooftops.
     * The villager used as anchor stands on the ground, so its height is the right reference.
     */
    @Nullable
    private static BlockPos groundNear(ServerLevel level, EntityType<?> entityType, int x, int z, int originY) {
        int highestY = Math.min(
                level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, originY, z)).getY(),
                originY + SPAWN_MAX_RISE_ABOVE_ORIGIN
        );

        for (int offset = 0; offset <= SPAWN_MAX_HEIGHT_DIFFERENCE; offset++) {
            BlockPos below = new BlockPos(x, originY - offset, z);

            if (isFreeToStandOn(level, entityType, below)) {
                return below;
            }

            BlockPos above = new BlockPos(x, originY + offset, z);

            if (offset > 0 && above.getY() <= highestY && isFreeToStandOn(level, entityType, above)) {
                return above;
            }
        }

        return null;
    }

    private static boolean isFreeToStandOn(ServerLevel level, EntityType<?> entityType, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        if (!standsOnSolidGround(level, pos) || !hasHeadroom(level, pos)) {
            return false;
        }

        AABB collisionBox = spawnCollisionBox(pos, entityType);

        return level.noCollision(collisionBox)
                && level.getEntities((Entity) null, collisionBox, Entity::isAlive).isEmpty();
    }

    private static boolean standsOnSolidGround(ServerLevel level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);

        if (below.isAir() || below.is(BlockTags.LEAVES) || below.is(Blocks.LAVA) || below.is(Blocks.WATER)) {
            return false;
        }

        return below.getFluidState().isEmpty() && below.isFaceSturdy(level, belowPos, Direction.UP);
    }

    private static boolean hasHeadroom(ServerLevel level, BlockPos pos) {
        for (int y = 0; y <= SPAWN_HEADROOM_BLOCKS; y++) {
            BlockState state = level.getBlockState(pos.above(y));

            if (!state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /** Widened to at least a golem's footprint so two defenders never land on top of each other. */
    private static AABB spawnCollisionBox(BlockPos pos, EntityType<?> entityType) {
        double halfWidth = Math.max(0.9D, entityType.getWidth()) / 2.0D;
        double height = Math.max(2.0D, entityType.getHeight());
        Vec3 center = Vec3.atBottomCenterOf(pos);

        return new AABB(
                center.x - halfWidth,
                center.y,
                center.z - halfWidth,
                center.x + halfWidth,
                center.y + height,
                center.z + halfWidth
        );
    }
}
