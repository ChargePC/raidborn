package net.randomcara.raidborn.world.settlement;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SettlementSpawnMarkerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean REQUIRE_MARKER_TAG = false;
    private static final String SPAWN_MARKER_TAG = "raidborn_spawn_marker";
    private static final int SPAWN_DELAY_TICKS = 2;
    private static final int MAX_ATTEMPTS = 5;

    private static final String SETTLEMENT_HOME_MARKER_TAG = "RaidbornSettlementHome";
    private static final String SETTLEMENT_HOME_X_TAG = "RaidbornSettlementHomeX";
    private static final String SETTLEMENT_HOME_Y_TAG = "RaidbornSettlementHomeY";
    private static final String SETTLEMENT_HOME_Z_TAG = "RaidbornSettlementHomeZ";

    private static final int SETTLEMENT_RESTRICT_RADIUS = 18;
    private static final int SETTLEMENT_SOFT_RETURN_DISTANCE = 24;
    private static final int SETTLEMENT_HARD_RETURN_DISTANCE = 44;
    private static final int SETTLEMENT_RETURN_SCAN_INTERVAL_TICKS = 20;
    private static final double SETTLEMENT_RETURN_SPEED = 1.0D;

    private static final Queue<PendingSettlementSpawn> PENDING_SPAWNS = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED_MARKERS = ConcurrentHashMap.newKeySet();

    public static void clearServerState() {
        QUEUED_MARKERS.clear();
    }

    private static int returnScanCooldown;

    private SettlementSpawnMarkerEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!(event.getEntity() instanceof ArmorStand armorStand)) {
            return;
        }

        IllagerStrengthCategory category = getMarkerCategory(armorStand);
        if (category == null) {
            return;
        }

        PendingSettlementSpawn pending = PendingSettlementSpawn.from(level, armorStand, category);
        event.setCanceled(true);

        if (!QUEUED_MARKERS.add(pending.uniqueKey())) {
            LOGGER.info("Duplicate settlement marker ignored at {} as {}.", pending.blockPos(), category);
            return;
        }

        PENDING_SPAWNS.add(pending);
        LOGGER.info("Queued settlement marker at {} as {}.", pending.blockPos(), category);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();

        if (!PENDING_SPAWNS.isEmpty()) {
            processPendingSpawns(server);
        }

        tickSettlementIllagerHomes(server);
    }

    private static void processPendingSpawns(MinecraftServer server) {
        int amountToProcess = PENDING_SPAWNS.size();

        for (int i = 0; i < amountToProcess; i++) {
            PendingSettlementSpawn pending = PENDING_SPAWNS.poll();

            if (pending == null) {
                return;
            }

            pending.tick();

            if (pending.ageTicks() < SPAWN_DELAY_TICKS) {
                PENDING_SPAWNS.add(pending);
                continue;
            }

            processPendingSpawn(server, pending);
        }
    }

    private static void processPendingSpawn(MinecraftServer server, PendingSettlementSpawn pending) {
        ServerLevel level = server.getLevel(pending.dimension());

        if (level == null) {
            LOGGER.warn("Could not process settlement marker at {}. Dimension {} is not loaded.",
                    pending.blockPos(), pending.dimension().location());
            QUEUED_MARKERS.remove(pending.uniqueKey());
            return;
        }

        Set<UUID> nearbyMobUuidsBeforeSpawn = collectNearbyMobUuids(level, pending.blockPos());

        boolean spawned = CompatibleIllagerTypes.spawnRandomIllager(
                level,
                pending.category(),
                pending.x(),
                pending.y(),
                pending.z(),
                pending.yRot(),
                pending.xRot(),
                pending.yHeadRot()
        );

        if (spawned) {
            Mob spawnedMob = findNewSpawnedMob(level, pending.blockPos(), nearbyMobUuidsBeforeSpawn);
            if (spawnedMob != null) {
                markSettlementIllager(spawnedMob, pending.blockPos());
            }

            LOGGER.info("Replaced settlement marker at {} with {}.", pending.blockPos(), pending.category());
            return;
        }

        pending.incrementAttempts();

        if (pending.attempts() < MAX_ATTEMPTS) {
            LOGGER.warn("Failed to replace settlement marker at {}. Retrying.", pending.blockPos());
            PENDING_SPAWNS.add(pending);
            return;
        }

        LOGGER.error("Failed to replace settlement marker at {} after {} attempts.", pending.blockPos(), MAX_ATTEMPTS);
        QUEUED_MARKERS.remove(pending.uniqueKey());
    }

    private static Set<UUID> collectNearbyMobUuids(ServerLevel level, BlockPos origin) {
        Set<UUID> result = new HashSet<>();
        AABB box = new AABB(origin).inflate(6.0D, 4.0D, 6.0D);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
            result.add(mob.getUUID());
        }

        return result;
    }

    @Nullable
    private static Mob findNewSpawnedMob(ServerLevel level, BlockPos origin, Set<UUID> oldMobUuids) {
        AABB box = new AABB(origin).inflate(6.0D, 4.0D, 6.0D);

        Mob best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
            if (oldMobUuids.contains(mob.getUUID())) {
                continue;
            }

            double distance = mob.distanceToSqr(Vec3.atCenterOf(origin));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }

        return best;
    }

    private static void markSettlementIllager(Mob mob, BlockPos homePos) {
        BlockPos immutableHome = homePos.immutable();

        CompoundTag data = mob.getPersistentData();
        data.putBoolean(SETTLEMENT_HOME_MARKER_TAG, true);
        data.putInt(SETTLEMENT_HOME_X_TAG, immutableHome.getX());
        data.putInt(SETTLEMENT_HOME_Y_TAG, immutableHome.getY());
        data.putInt(SETTLEMENT_HOME_Z_TAG, immutableHome.getZ());

        mob.restrictTo(immutableHome, SETTLEMENT_RESTRICT_RADIUS);
        mob.setPersistenceRequired();
    }

    private static void tickSettlementIllagerHomes(MinecraftServer server) {
        if (--returnScanCooldown > 0) {
            return;
        }

        returnScanCooldown = SETTLEMENT_RETURN_SCAN_INTERVAL_TICKS;

        for (ServerLevel level : server.getAllLevels()) {
            tickSettlementIllagerHomes(level);
        }
    }

    private static void tickSettlementIllagerHomes(ServerLevel level) {
        Set<UUID> processed = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            int centerChunkX = player.chunkPosition().x;
            int centerChunkZ = player.chunkPosition().z;

            for (int chunkX = centerChunkX - 6; chunkX <= centerChunkX + 6; chunkX++) {
                for (int chunkZ = centerChunkZ - 6; chunkZ <= centerChunkZ + 6; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);

                    if (chunk == null) {
                        continue;
                    }

                    AABB chunkBox = new AABB(
                            chunkX << 4,
                            level.getMinBuildHeight(),
                            chunkZ << 4,
                            (chunkX << 4) + 16,
                            level.getMaxBuildHeight(),
                            (chunkZ << 4) + 16
                    );

                    for (Mob mob : level.getEntitiesOfClass(Mob.class, chunkBox, SettlementSpawnMarkerEvents::isSettlementIllager)) {
                        if (processed.add(mob.getUUID())) {
                            tickSettlementIllagerHome(level, mob);
                        }
                    }
                }
            }
        }
    }

    private static boolean isSettlementIllager(Mob mob) {
        return mob.isAlive() && mob.getPersistentData().getBoolean(SETTLEMENT_HOME_MARKER_TAG);
    }

    private static void tickSettlementIllagerHome(ServerLevel level, Mob mob) {
        BlockPos home = getSettlementHome(mob);

        if (home == null) {
            return;
        }

        mob.restrictTo(home, SETTLEMENT_RESTRICT_RADIUS);

        double distanceToHomeSqr = mob.distanceToSqr(Vec3.atCenterOf(home));
        double softDistanceSqr = SETTLEMENT_SOFT_RETURN_DISTANCE * SETTLEMENT_SOFT_RETURN_DISTANCE;
        double hardDistanceSqr = SETTLEMENT_HARD_RETURN_DISTANCE * SETTLEMENT_HARD_RETURN_DISTANCE;

        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            double targetDistanceToHomeSqr = mob.getTarget().distanceToSqr(Vec3.atCenterOf(home));

            if (targetDistanceToHomeSqr > softDistanceSqr || distanceToHomeSqr > softDistanceSqr) {
                mob.setTarget(null);
                mob.setAggressive(false);
            }
        }

        if (distanceToHomeSqr <= softDistanceSqr) {
            return;
        }

        BlockPos returnPos = findReturnPosition(level, home);

        if (returnPos == null) {
            returnPos = home;
        }

        PathNavigation navigation = mob.getNavigation();

        if (distanceToHomeSqr > hardDistanceSqr || navigation.isDone()) {
            navigation.stop();
            navigation.moveTo(
                    returnPos.getX() + 0.5D,
                    returnPos.getY(),
                    returnPos.getZ() + 0.5D,
                    SETTLEMENT_RETURN_SPEED
            );

            mob.getMoveControl().setWantedPosition(
                    returnPos.getX() + 0.5D,
                    returnPos.getY(),
                    returnPos.getZ() + 0.5D,
                    SETTLEMENT_RETURN_SPEED
            );
        }
    }

    @Nullable
    private static BlockPos getSettlementHome(Mob mob) {
        CompoundTag data = mob.getPersistentData();

        if (!data.getBoolean(SETTLEMENT_HOME_MARKER_TAG)) {
            return null;
        }

        if (!data.contains(SETTLEMENT_HOME_X_TAG)
                || !data.contains(SETTLEMENT_HOME_Y_TAG)
                || !data.contains(SETTLEMENT_HOME_Z_TAG)) {
            return null;
        }

        return new BlockPos(
                data.getInt(SETTLEMENT_HOME_X_TAG),
                data.getInt(SETTLEMENT_HOME_Y_TAG),
                data.getInt(SETTLEMENT_HOME_Z_TAG)
        );
    }

    @Nullable
    private static BlockPos findReturnPosition(ServerLevel level, BlockPos home) {
        if (isGoodStandPosition(level, home)) {
            return home;
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos mutablePos : BlockPos.betweenClosed(home.offset(-5, -3, -5), home.offset(5, 3, 5))) {
            BlockPos pos = mutablePos.immutable();

            if (!isGoodStandPosition(level, pos)) {
                continue;
            }

            double distance = pos.distSqr(home);
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

        return level.getWorldBorder().isWithinBounds(pos)
                && below.isFaceSturdy(level, belowPos, Direction.UP)
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty();
    }

    @Nullable
    private static IllagerStrengthCategory getMarkerCategory(ArmorStand armorStand) {
        if (REQUIRE_MARKER_TAG && !armorStand.getTags().contains(SPAWN_MARKER_TAG)) {
            return null;
        }

        ItemStack helmet = armorStand.getItemBySlot(EquipmentSlot.HEAD);

        if (helmet.is(Items.LEATHER_HELMET)) {
            return IllagerStrengthCategory.COMMON;
        }

        if (helmet.is(Items.IRON_HELMET)) {
            return IllagerStrengthCategory.STRONG;
        }

        if (helmet.is(Items.DIAMOND_HELMET)) {
            return IllagerStrengthCategory.VERY_STRONG;
        }

        return null;
    }

    private static final class PendingSettlementSpawn {
        private final ResourceKey<Level> dimension;
        private final IllagerStrengthCategory category;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float xRot;
        private final float yHeadRot;
        private final BlockPos blockPos;
        private final String uniqueKey;

        private int ageTicks;
        private int attempts;

        private PendingSettlementSpawn(ResourceKey<Level> dimension, IllagerStrengthCategory category,
                                       double x, double y, double z, float yRot, float xRot,
                                       float yHeadRot, BlockPos blockPos) {
            this.dimension = dimension;
            this.category = category;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.yHeadRot = yHeadRot;
            this.blockPos = blockPos;
            this.uniqueKey = dimension.location()
                    + "|" + category
                    + "|" + Double.doubleToLongBits(x)
                    + "|" + Double.doubleToLongBits(y)
                    + "|" + Double.doubleToLongBits(z);
        }

        private static PendingSettlementSpawn from(ServerLevel level, ArmorStand armorStand, IllagerStrengthCategory category) {
            return new PendingSettlementSpawn(
                    level.dimension(),
                    category,
                    armorStand.getX(),
                    armorStand.getY(),
                    armorStand.getZ(),
                    armorStand.getYRot(),
                    armorStand.getXRot(),
                    armorStand.getYHeadRot(),
                    armorStand.blockPosition()
            );
        }

        private void tick() {
            ageTicks++;
        }

        private void incrementAttempts() {
            attempts++;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private IllagerStrengthCategory category() {
            return category;
        }

        private double x() {
            return x;
        }

        private double y() {
            return y;
        }

        private double z() {
            return z;
        }

        private float yRot() {
            return yRot;
        }

        private float xRot() {
            return xRot;
        }

        private float yHeadRot() {
            return yHeadRot;
        }

        private BlockPos blockPos() {
            return blockPos;
        }

        private String uniqueKey() {
            return uniqueKey;
        }

        private int ageTicks() {
            return ageTicks;
        }

        private int attempts() {
            return attempts;
        }
    }
}
