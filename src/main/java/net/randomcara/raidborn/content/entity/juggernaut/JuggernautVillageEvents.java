package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.VillageSide;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.core.registry.ModEntities;
import net.randomcara.raidborn.gameplay.attack.AttackRaidbornHooks;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** World-side Juggernaut logic: village spawning, post-raid reward and aggression bookkeeping. */
@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class JuggernautVillageEvents {
    private static final long DANGER_RECENT_DAYS = 2L;

    private static final int RAID_SEARCH_RADIUS = 96;

    private static final int VILLAGE_SCAN_INTERVAL_TICKS = 100;

    private static final int SPAWN_MIN_RADIUS = 3;
    private static final int SPAWN_MAX_RADIUS = 16;
    private static final int SPAWN_SAMPLES_PER_RING = 12;
    private static final int SPAWN_MAX_HEIGHT_DIFFERENCE = 8;
    /** A gentle slope passes; a village roof (4+ blocks above the villager) does not. */
    private static final int SPAWN_MAX_RISE_ABOVE_ORIGIN = 2;
    private static final int SPAWN_CLEARANCE_HEIGHT = 3;
    private static final double SPAWN_HALF_WIDTH = 1.45D;

    private static final int DEFENSIVE_EVENT_RAID_RADIUS_SQR = 96 * 96;
    private static final double AGGRESSION_NOTIFY_RANGE = 32.0D;

    private static final Predicate<Holder<PoiType>> MEETING_POI = holder -> holder.is(PoiTypes.MEETING);

    private JuggernautVillageEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (level.getGameTime() % VILLAGE_SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        if (level.players().isEmpty()) {
            return;
        }

        JuggernautVillageData data = JuggernautVillageData.get(level);
        String dimensionId = level.dimension().location().toString();

        discoverVillages(level, data, dimensionId);
        processVillages(level, data, dimensionId);
    }

    private static void discoverVillages(ServerLevel level, JuggernautVillageData data, String dimensionId) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }

            List<BlockPos> bells = level.getPoiManager()
                    .getInRange(MEETING_POI, player.blockPosition(), RaidbornServerConfig.getJuggernautVillageScanRadius(), PoiManager.Occupancy.ANY)
                    .map(record -> record.getPos().immutable())
                    .toList();

            for (BlockPos bellPos : bells) {
                data.getOrCreateRecord(dimensionId, bellPos);
            }
        }
    }

    private static void processVillages(ServerLevel level, JuggernautVillageData data, String dimensionId) {
        long gameTime = level.getGameTime();
        long currentDay = level.getDayTime() / 24000L;

        for (JuggernautVillageData.VillageRecord record : List.copyOf(data.getRecords(dimensionId))) {
            BlockPos bellPos = record.getBellPos();

            if (!level.isLoaded(bellPos) || !hasNearbyPlayer(level, bellPos)) {
                continue;
            }

            // Bell gone: the record has no owner left. Without this the list only grows.
            if (level.getPoiManager().getType(bellPos).isEmpty()) {
                data.removeRecord(dimensionId, record);
                continue;
            }

            boolean changed = tickNaturalSpawn(level, record, bellPos, currentDay);
            changed |= tickRaidReward(level, record, bellPos, gameTime);

            if (changed) {
                data.setDirty();
            }
        }
    }

    private static boolean hasNearbyPlayer(ServerLevel level, BlockPos bellPos) {
        Player player = level.getNearestPlayer(
                bellPos.getX() + 0.5D,
                bellPos.getY() + 0.5D,
                bellPos.getZ() + 0.5D,
                RaidbornServerConfig.getJuggernautVillageScanRadius(),
                false
        );

        return player != null;
    }

    /**
     * The link is only dropped when the Juggernaut actually dies, in {@link #onJuggernautDeath}.
     *
     * <p>"Not found" never counts as a death, only as confirmation. {@code level.getEntity} returns
     * null for an unloaded chunk and the Juggernaut never despawns anyway
     * ({@code removeWhenFarAway} is false). Treating it as a death stamped the death day and five
     * days later the village got a second natural Juggernaut while the first was still walking
     * around.
     */
    private static boolean isJuggernautAlive(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof Juggernaut juggernaut && juggernaut.isAlive();
    }

    private static boolean tickNaturalSpawn(
            ServerLevel level,
            JuggernautVillageData.VillageRecord record,
            BlockPos bellPos,
            long currentDay
    ) {
        if (!RaidbornServerConfig.isJuggernautNaturalSpawnEnabled()) {
            return false;
        }

        if (!record.isNaturalRolled()) {
            if (countVillagers(level, bellPos) < RaidbornServerConfig.getJuggernautMinVillagers()) {
                return false;
            }

            boolean allowed = level.random.nextFloat() < RaidbornServerConfig.getJuggernautNaturalSpawnChance();
            record.setNaturalRoll(allowed);

            Raidborn.LOGGER.debug(
                    "Juggernaut: rolled village at {} -> {} (chance {}%)",
                    bellPos.toShortString(),
                    allowed ? "gets a natural defender" : "no natural defender",
                    (int) (RaidbornServerConfig.getJuggernautNaturalSpawnChance() * 100.0F)
            );

            trySpawnNatural(level, record, bellPos, currentDay);
            return true;
        }

        if (!record.isNaturalAllowed() || record.getNaturalJuggernautId() != null) {
            return false;
        }

        if (record.getNaturalDeathDay() < 0L) {
            return trySpawnNatural(level, record, bellPos, currentDay);
        }

        if (currentDay - record.getNaturalDeathDay() < RaidbornServerConfig.getJuggernautReplacementDelayDays()) {
            return false;
        }

        if (record.getLastVillagerDangerDay() < 0L
                || currentDay - record.getLastVillagerDangerDay() > DANGER_RECENT_DAYS) {
            return false;
        }

        if (record.getLastReplacementAttemptDay() == currentDay) {
            return false;
        }

        record.setLastReplacementAttemptDay(currentDay);

        if (level.random.nextFloat() < RaidbornServerConfig.getJuggernautReplacementDailyChance()) {
            trySpawnNatural(level, record, bellPos, currentDay);
        }

        return true;
    }

    private static boolean trySpawnNatural(
            ServerLevel level,
            JuggernautVillageData.VillageRecord record,
            BlockPos bellPos,
            long currentDay
    ) {
        if (!record.isNaturalAllowed() || record.getNaturalJuggernautId() != null) {
            return false;
        }

        Juggernaut juggernaut = spawnJuggernaut(level, bellPos, JuggernautOrigin.NATURAL, MobSpawnType.STRUCTURE);

        if (juggernaut == null) {
            Raidborn.LOGGER.debug(
                    "Juggernaut: no free space near the bell at {}, retrying on the next scan",
                    bellPos.toShortString()
            );
            return false;
        }

        Raidborn.LOGGER.debug(
                "Natural Juggernaut spawned at {} for the village at {}",
                juggernaut.blockPosition().toShortString(),
                bellPos.toShortString()
        );

        record.setNaturalJuggernautId(juggernaut.getUUID());
        record.setNaturalDeathDay(-1L);
        record.setLastReplacementAttemptDay(currentDay);
        return true;
    }

    private static boolean tickRaidReward(
            ServerLevel level,
            JuggernautVillageData.VillageRecord record,
            BlockPos bellPos,
            long gameTime
    ) {
        boolean changed = observeRaid(level, record, bellPos, gameTime);

        long pending = record.getPendingRaidSpawnGameTime();

        if (pending < 0L || gameTime < pending) {
            return changed;
        }

        if (record.getRaidJuggernautId() != null && isJuggernautAlive(level, record.getRaidJuggernautId())) {
            record.setPendingRaidSpawnGameTime(-1L);
            return true;
        }

        Juggernaut juggernaut = spawnJuggernaut(level, bellPos, JuggernautOrigin.RAID, MobSpawnType.EVENT);

        if (juggernaut == null) {
            // No room right now: retry on the next scan instead of losing the reward.
            record.setPendingRaidSpawnGameTime(gameTime + VILLAGE_SCAN_INTERVAL_TICKS);
            return true;
        }

        record.setRaidJuggernautId(juggernaut.getUUID());
        record.setPendingRaidSpawnGameTime(-1L);
        return true;
    }

    private static boolean observeRaid(
            ServerLevel level,
            JuggernautVillageData.VillageRecord record,
            BlockPos bellPos,
            long gameTime
    ) {
        if (!RaidbornServerConfig.isJuggernautRaidRewardEnabled()) {
            return false;
        }

        Raid raid = level.getRaids().getNearbyRaid(bellPos, RAID_SEARCH_RADIUS * RAID_SEARCH_RADIUS);

        if (raid == null) {
            return false;
        }

        // Bad Omen level has to be read while the raid runs: it is gone after the victory.
        record.observeRaid(raid.getId(), raid.getBadOmenLevel());

        if (!raid.isVictory() || raid.getId() == record.getLastHandledRaidId()) {
            return true;
        }

        record.setLastHandledRaidId(raid.getId());

        if (record.getObservedRaidBadOmenLevel() < RaidbornServerConfig.getJuggernautRaidRewardMinBadOmen()) {
            return true;
        }

        if (record.getRaidJuggernautId() != null && isJuggernautAlive(level, record.getRaidJuggernautId())) {
            return true;
        }

        if (countVillagers(level, bellPos) < 1) {
            return true;
        }

        int delay = Mth.nextInt(level.random, RaidbornServerConfig.getJuggernautRaidRewardMinDelayTicks(), RaidbornServerConfig.getJuggernautRaidRewardMaxDelayTicks());
        record.setPendingRaidSpawnGameTime(gameTime + delay);
        return true;
    }

    private static int countVillagers(ServerLevel level, BlockPos bellPos) {
        AABB box = new AABB(bellPos).inflate(48.0D, 24.0D, 48.0D);
        return level.getEntitiesOfClass(Villager.class, box, Villager::isAlive).size();
    }

    @Nullable
    public static Juggernaut spawnJuggernaut(
            ServerLevel level,
            BlockPos origin,
            JuggernautOrigin juggernautOrigin,
            MobSpawnType spawnType
    ) {
        BlockPos spawnPos = findSpawnPosition(level, origin);

        if (spawnPos == null) {
            return null;
        }

        Juggernaut juggernaut = ModEntities.JUGGERNAUT.get().create(level);

        if (juggernaut == null) {
            return null;
        }

        juggernaut.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F,
                0.0F
        );

        ForgeEventFactory.onFinalizeSpawn(
                juggernaut,
                level,
                level.getCurrentDifficultyAt(spawnPos),
                spawnType,
                null,
                null
        );

        juggernaut.setOrigin(juggernautOrigin);
        juggernaut.setHomeBellPos(origin);
        juggernaut.restrictTo(origin, Juggernaut.HOME_RESTRICTION_RADIUS);

        if (!level.addFreshEntity(juggernaut)) {
            juggernaut.discard();
            return null;
        }

        return juggernaut;
    }

    /**
     * Looks for a 3x3x3 clearing without water, lava or collisions; the Juggernaut is almost three
     * blocks wide.
     *
     * <p>Rings outward from the bell instead of sampling random points. A village centre is all
     * houses and paths, so random sampling missed nearly every time.
     */
    @Nullable
    public static BlockPos findSpawnPosition(ServerLevel level, BlockPos origin) {
        float startAngle = level.random.nextFloat() * Mth.TWO_PI;

        for (int radius = SPAWN_MIN_RADIUS; radius <= SPAWN_MAX_RADIUS; radius++) {
            for (int sample = 0; sample < SPAWN_SAMPLES_PER_RING; sample++) {
                float angle = startAngle + sample * (Mth.TWO_PI / SPAWN_SAMPLES_PER_RING);

                int x = origin.getX() + Mth.floor(Mth.cos(angle) * radius);
                int z = origin.getZ() + Mth.floor(Mth.sin(angle) * radius);

                BlockPos ground = findGroundNearOrigin(level, x, z, origin.getY());

                if (ground != null) {
                    return ground;
                }
            }
        }

        return null;
    }

    /**
     * Tests the heights at that x/z starting from the origin level, nearest first, never far above it.
     *
     * <p>Can't just use {@code getHeightmapPos}, it gives the top of the terrain at that x/z, which
     * inside a village means a house roof. Flat roofs sail through {@link #isValidSpawnArea}, so the
     * Juggernaut ended up on top of buildings. At 2.9 blocks wide its box overhangs the slab and
     * clips the walls, and {@code isPushable()} is false so nothing ever shoves it back out.
     */
    @Nullable
    private static BlockPos findGroundNearOrigin(ServerLevel level, int x, int z, int originY) {
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, originY, z)
        );

        int highestY = Math.min(surface.getY(), originY + SPAWN_MAX_RISE_ABOVE_ORIGIN);
        int lowestY = originY - SPAWN_MAX_HEIGHT_DIFFERENCE;

        for (int offset = 0; offset <= SPAWN_MAX_HEIGHT_DIFFERENCE; offset++) {
            int below = originY - offset;

            if (below >= lowestY) {
                BlockPos candidate = new BlockPos(x, below, z);

                if (isValidSpawnArea(level, candidate)) {
                    return candidate;
                }
            }

            int above = originY + offset;

            if (offset > 0 && above <= highestY) {
                BlockPos candidate = new BlockPos(x, above, z);

                if (isValidSpawnArea(level, candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static boolean isValidSpawnArea(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floorPos = pos.offset(dx, -1, dz);
                BlockState floor = level.getBlockState(floorPos);

                // Dirt paths and farmland fail isFaceSturdy and a village is made of them. It only has to be
                // standable and not liquid.
                if (floor.getCollisionShape(level, floorPos).isEmpty() || !floor.getFluidState().isEmpty()) {
                    return false;
                }

                for (int dy = 0; dy < SPAWN_CLEARANCE_HEIGHT; dy++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);

                    // Tall grass, flowers and torches block nothing: collision is what matters.
                    if (!state.getCollisionShape(level, checkPos).isEmpty() || !state.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }

        AABB box = new AABB(
                pos.getX() + 0.5D - SPAWN_HALF_WIDTH,
                pos.getY(),
                pos.getZ() + 0.5D - SPAWN_HALF_WIDTH,
                pos.getX() + 0.5D + SPAWN_HALF_WIDTH,
                pos.getY() + SPAWN_CLEARANCE_HEIGHT,
                pos.getZ() + 0.5D + SPAWN_HALF_WIDTH
        );

        return level.noCollision(box) && level.getEntitiesOfClass(LivingEntity.class, box).isEmpty();
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity victim = event.getEntity();

        if (!VillageSide.isDefender(victim)) {
            return;
        }

        if (victim instanceof Villager) {
            markVillagerDanger(level, victim.blockPosition());
        }

        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !isValidAggressor(attacker)) {
            return;
        }

        notifyNearbyJuggernauts(level, victim, attacker, false);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity victim = event.getEntity();

        if (victim instanceof Juggernaut juggernaut) {
            onJuggernautDeath(level, juggernaut);
            return;
        }

        if (!(victim instanceof Villager)) {
            return;
        }

        markVillagerDanger(level, victim.blockPosition());

        if (event.getSource().getEntity() instanceof LivingEntity attacker && isValidAggressor(attacker)) {
            notifyNearbyJuggernauts(level, victim, attacker, true);
        }
    }

    private static void onJuggernautDeath(ServerLevel level, Juggernaut juggernaut) {
        BlockPos bellPos = juggernaut.getHomeBellPos();

        if (bellPos == null) {
            return;
        }

        JuggernautVillageData data = JuggernautVillageData.get(level);
        JuggernautVillageData.VillageRecord record =
                data.findRecord(level.dimension().location().toString(), bellPos);

        if (record == null) {
            return;
        }

        UUID id = juggernaut.getUUID();

        if (id.equals(record.getNaturalJuggernautId())) {
            record.setNaturalJuggernautId(null);
            record.setNaturalDeathDay(level.getDayTime() / 24000L);
            data.setDirty();
            return;
        }

        if (id.equals(record.getRaidJuggernautId())) {
            record.setRaidJuggernautId(null);
            data.setDirty();
        }
    }

    private static void markVillagerDanger(ServerLevel level, BlockPos pos) {
        JuggernautVillageData data = JuggernautVillageData.get(level);
        JuggernautVillageData.VillageRecord record =
                data.findRecord(level.dimension().location().toString(), pos);

        if (record == null) {
            return;
        }

        long currentDay = level.getDayTime() / 24000L;

        if (record.getLastVillagerDangerDay() != currentDay) {
            record.setLastVillagerDangerDay(currentDay);
            data.setDirty();
        }
    }

    private static void notifyNearbyJuggernauts(
            ServerLevel level,
            LivingEntity victim,
            LivingEntity attacker,
            boolean villagerWasKilled
    ) {
        AABB box = victim.getBoundingBox().inflate(AGGRESSION_NOTIFY_RANGE, 16.0D, AGGRESSION_NOTIFY_RANGE);

        // Look up by type instead of by class: this handler runs on every hit taken by any villager or
        // golem in the world, and the type index avoids walking the rest.
        for (Juggernaut juggernaut : level.getEntities(ModEntities.JUGGERNAUT.get(), box, Juggernaut::isAlive)) {
            if (juggernaut == attacker) {
                continue;
            }

            juggernaut.rememberVillageAggression(attacker, villagerWasKilled);

            if (juggernaut.getTarget() == null && juggernaut.isValidTarget(attacker)) {
                juggernaut.setTarget(attacker);
            }
        }
    }

    private static boolean isValidAggressor(LivingEntity attacker) {
        if (!attacker.isAlive() || VillageSide.isDefender(attacker)) {
            return false;
        }

        if (attacker instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        return true;
    }

    /** A running raid or Hero Attack keeps the aggression memory alive. */
    public static boolean isDefensiveEventActive(Juggernaut juggernaut) {
        Level level = juggernaut.level();

        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (AttackRaidbornHooks.isAttackDefender(juggernaut)) {
            return true;
        }

        Raid raid = serverLevel.getRaids().getNearbyRaid(juggernaut.blockPosition(), DEFENSIVE_EVENT_RAID_RADIUS_SQR);
        return raid != null && !raid.isStopped();
    }

    /** During a Hero Attack the event owner counts as an enemy of the defended objective. */
    public static boolean isAttackEventEnemy(Juggernaut juggernaut, Player player) {
        if (!AttackRaidbornHooks.isAttackDefender(juggernaut)) {
            return false;
        }

        return player instanceof ServerPlayer serverPlayer
                && ModEffects.hasAllianceEffect(serverPlayer);
    }
}
