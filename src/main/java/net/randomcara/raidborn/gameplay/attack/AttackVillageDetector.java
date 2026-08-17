package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AttackVillageDetector {
    private static final int EXTRA_SCAN_AROUND_POI_RADIUS = 48;
    private static final int EXTRA_SCAN_AROUND_CENTER_RADIUS = 64;
    private static final int EXTRA_VERTICAL_SCAN_RADIUS = 24;

    private static final int POI_CLUSTER_DISTANCE = 32;

    private static final Set<String> VALID_MINECRAFT_POI_PATHS = Set.of(
            "home",
            "meeting",
            "armorer",
            "butcher",
            "cartographer",
            "cleric",
            "farmer",
            "fisherman",
            "fletcher",
            "leatherworker",
            "librarian",
            "mason",
            "shepherd",
            "toolsmith",
            "weaponsmith"
    );

    private AttackVillageDetector() {
    }

    public static Optional<AttackDetectionResult> detect(ServerLevel level, BlockPos origin) {
        int radius = RaidbornServerConfig.ATTACK_DETECTION_RADIUS.get();

        List<AttackPoiData> validPois = collectValidPois(level, origin, radius);
        if (validPois.size() < RaidbornServerConfig.ATTACK_REQUIRED_POIS.get()) {
            return Optional.empty();
        }

        List<Villager> villagers = collectVillageVillagers(level, origin, radius, validPois);
        if (villagers.size() < RaidbornServerConfig.ATTACK_REQUIRED_VILLAGERS.get()) {
            return Optional.empty();
        }

        Optional<BlockPos> center = calculateAttackCenter(validPois, villagers);
        if (center.isEmpty()) {
            return Optional.empty();
        }

        BlockPos attackCenter = center.get();

        List<IronGolem> naturalIronGolems = collectNaturalIronGolems(
                level,
                origin,
                attackCenter,
                radius,
                validPois
        );

        if (RaidbornServerConfig.ATTACK_REQUIRE_NATURAL_GOLEM.get() && naturalIronGolems.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new AttackDetectionResult(
                attackCenter,
                radius,
                villagers,
                clusterPoiPositions(validPois),
                List.copyOf(naturalIronGolems)
        ));
    }

    /**
     * Cuts the POI list down to one per 32 block cell.
     *
     * <p>The {@code home} tag hits every bed in the village, so an average one came back with dozens
     * of positions and each turned into a 96 block entity scan, here and again in the periodic
     * reinforcement pass. 32 is a guess that worked, could probably go wider.
     */
    private static List<BlockPos> clusterPoiPositions(List<AttackPoiData> pois) {
        List<BlockPos> clustered = new ArrayList<>();

        for (AttackPoiData poi : pois) {
            boolean covered = false;

            for (BlockPos kept : clustered) {
                if (kept.distSqr(poi.pos()) <= POI_CLUSTER_DISTANCE * POI_CLUSTER_DISTANCE) {
                    covered = true;
                    break;
                }
            }

            if (!covered) {
                clustered.add(poi.pos().immutable());
            }
        }

        return clustered;
    }

    private static List<Villager> collectVillageVillagers(ServerLevel level,
                                                          BlockPos origin,
                                                          int radius,
                                                          List<AttackPoiData> validPois) {
        Map<UUID, Villager> villagers = new LinkedHashMap<>();

        collectVillagersInBox(
                level,
                AABB.ofSize(
                        Vec3.atCenterOf(origin),
                        radius * 2.0D,
                        Math.max(radius * 2.0D, EXTRA_VERTICAL_SCAN_RADIUS * 2.0D),
                        radius * 2.0D
                ),
                villagers
        );

        BlockPos averagePoiCenter = averageBlockPos(validPois.stream().map(AttackPoiData::pos).toList());
        int centerRadius = Math.max(radius, EXTRA_SCAN_AROUND_CENTER_RADIUS);

        collectVillagersInBox(
                level,
                AABB.ofSize(
                        Vec3.atCenterOf(averagePoiCenter),
                        centerRadius * 2.0D,
                        Math.max(radius * 2.0D, EXTRA_VERTICAL_SCAN_RADIUS * 2.0D),
                        centerRadius * 2.0D
                ),
                villagers
        );

        int poiScanRadius = Math.max(32, Math.min(Math.max(radius, EXTRA_SCAN_AROUND_POI_RADIUS), 80));

        for (AttackPoiData poi : validPois) {
            collectVillagersInBox(
                    level,
                    AABB.ofSize(
                            Vec3.atCenterOf(poi.pos()),
                            poiScanRadius * 2.0D,
                            EXTRA_VERTICAL_SCAN_RADIUS * 2.0D,
                            poiScanRadius * 2.0D
                    ),
                    villagers
            );
        }

        List<Villager> result = new ArrayList<>(villagers.values());
        result.removeIf(villager -> !isUsableVillager(villager));
        result.sort(Comparator.comparingDouble(villager -> averageDistanceToPois(villager.blockPosition(), validPois)));

        return result;
    }

    private static void collectVillagersInBox(ServerLevel level, AABB box, Map<UUID, Villager> result) {
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box, AttackVillageDetector::isUsableVillager)) {
            result.putIfAbsent(villager.getUUID(), villager);
        }
    }

    private static boolean isUsableVillager(Villager villager) {
        return villager.isAlive()
                && (!RaidbornServerConfig.ATTACK_IGNORE_VILLAGERS_IN_VEHICLES.get() || !villager.isPassenger());
    }

    private static List<IronGolem> collectNaturalIronGolems(ServerLevel level,
                                                            BlockPos origin,
                                                            BlockPos attackCenter,
                                                            int radius,
                                                            List<AttackPoiData> validPois) {
        Map<UUID, IronGolem> golems = new LinkedHashMap<>();

        collectNaturalIronGolemsInBox(
                level,
                AABB.ofSize(
                        Vec3.atCenterOf(origin),
                        radius * 2.0D,
                        Math.max(radius * 2.0D, EXTRA_VERTICAL_SCAN_RADIUS * 2.0D),
                        radius * 2.0D
                ),
                golems
        );

        int centerRadius = Math.max(radius, EXTRA_SCAN_AROUND_CENTER_RADIUS);
        collectNaturalIronGolemsInBox(
                level,
                AABB.ofSize(
                        Vec3.atCenterOf(attackCenter),
                        centerRadius * 2.0D,
                        Math.max(radius * 2.0D, EXTRA_VERTICAL_SCAN_RADIUS * 2.0D),
                        centerRadius * 2.0D
                ),
                golems
        );

        int poiScanRadius = Math.max(32, Math.min(Math.max(radius, EXTRA_SCAN_AROUND_POI_RADIUS), 80));

        for (AttackPoiData poi : validPois) {
            collectNaturalIronGolemsInBox(
                    level,
                    AABB.ofSize(
                            Vec3.atCenterOf(poi.pos()),
                            poiScanRadius * 2.0D,
                            EXTRA_VERTICAL_SCAN_RADIUS * 2.0D,
                            poiScanRadius * 2.0D
                    ),
                    golems
            );
        }

        List<IronGolem> result = new ArrayList<>(golems.values());
        result.sort(Comparator.comparingDouble(golem -> golem.blockPosition().distSqr(attackCenter)));

        return result;
    }

    private static void collectNaturalIronGolemsInBox(ServerLevel level,
                                                      AABB box,
                                                      Map<UUID, IronGolem> result) {
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, box, golem ->
                golem.isAlive()
                        && !golem.isPlayerCreated()
                        && !AttackRaidbornHooks.isSpawnedAttackDefender(golem))) {
            result.putIfAbsent(golem.getUUID(), golem);
        }
    }

    private static List<AttackPoiData> collectValidPois(ServerLevel level, BlockPos origin, int radius) {
        return level.getPoiManager()
                .getInRange(AttackVillageDetector::isValidVillagePoi, origin, radius, PoiManager.Occupancy.ANY)
                .map(record -> new AttackPoiData(record.getPos(), getPoiPath(record.getPoiType()).orElse("")))
                .distinct()
                .toList();
    }

    private static boolean isValidVillagePoi(Holder<PoiType> holder) {
        Optional<String> path = getPoiPath(holder);
        return path.filter(VALID_MINECRAFT_POI_PATHS::contains).isPresent();
    }

    private static Optional<String> getPoiPath(Holder<PoiType> holder) {
        return holder.unwrapKey()
                .map(ResourceKey::location)
                .filter(location -> "minecraft".equals(location.getNamespace()))
                .map(ResourceLocation::getPath);
    }

    private static Optional<BlockPos> calculateAttackCenter(List<AttackPoiData> pois, List<Villager> villagers) {
        Optional<BlockPos> bell = pois.stream()
                .filter(poi -> "meeting".equals(poi.path()))
                .map(AttackPoiData::pos)
                .min(Comparator.comparingDouble(pos -> averageDistanceToVillagers(pos, villagers)));

        if (bell.isPresent()) {
            return bell;
        }

        if (!pois.isEmpty()) {
            return Optional.of(averageBlockPos(pois.stream().map(AttackPoiData::pos).toList()));
        }

        if (!villagers.isEmpty()) {
            return Optional.of(averageBlockPos(villagers.stream().map(Villager::blockPosition).toList()));
        }

        return Optional.empty();
    }

    private static double averageDistanceToVillagers(BlockPos pos, List<Villager> villagers) {
        if (villagers.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;
        for (Villager villager : villagers) {
            total += villager.blockPosition().distSqr(pos);
        }
        return total / villagers.size();
    }

    private static double averageDistanceToPois(BlockPos pos, List<AttackPoiData> pois) {
        if (pois.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;
        for (AttackPoiData poi : pois) {
            total += pos.distSqr(poi.pos());
        }
        return total / pois.size();
    }

    private static BlockPos averageBlockPos(List<BlockPos> positions) {
        long x = 0L;
        long y = 0L;
        long z = 0L;

        for (BlockPos pos : positions) {
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }

        int size = Math.max(1, positions.size());
        return new BlockPos((int) (x / size), (int) (y / size), (int) (z / size));
    }

    private record AttackPoiData(BlockPos pos, String path) {
    }
}
