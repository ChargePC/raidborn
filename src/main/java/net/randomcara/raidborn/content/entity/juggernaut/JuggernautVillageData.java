package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.randomcara.raidborn.Raidborn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Per-village Juggernaut state, keyed by the meeting POI (bell) position.
 *
 * <p>Living in a {@link SavedData} is what lets the natural-spawn roll happen exactly once and the
 * post-raid timer keep running with the chunk unloaded.
 */
public class JuggernautVillageData extends SavedData {
    private static final String DATA_NAME = Raidborn.MOD_ID + "_juggernaut_villages";

    /** Bells closer than this to each other are treated as the same village. */
    public static final int VILLAGE_MERGE_DISTANCE = 64;

    /**
     * Grouped by dimension: the bell lookup runs on every hit taken by a villager, and scanning the
     * whole world list each time gets expensive after a lot of exploration.
     */
    private final Map<String, List<VillageRecord>> recordsByDimension = new HashMap<>();

    public static JuggernautVillageData get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(JuggernautVillageData::load, JuggernautVillageData::new, DATA_NAME);
    }

    public static JuggernautVillageData load(CompoundTag tag) {
        JuggernautVillageData data = new JuggernautVillageData();
        ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            VillageRecord record = VillageRecord.load(list.getCompound(i));
            data.recordsByDimension.computeIfAbsent(record.dimensionId, id -> new ArrayList<>()).add(record);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (List<VillageRecord> records : this.recordsByDimension.values()) {
            for (VillageRecord record : records) {
                list.add(record.save());
            }
        }

        tag.put("Villages", list);
        return tag;
    }

    public List<VillageRecord> getRecords(String dimensionId) {
        return this.recordsByDimension.getOrDefault(dimensionId, List.of());
    }

    @Nullable
    public VillageRecord findRecord(String dimensionId, BlockPos bellPos) {
        VillageRecord best = null;
        double bestDistance = VILLAGE_MERGE_DISTANCE * (double) VILLAGE_MERGE_DISTANCE;

        for (VillageRecord record : this.getRecords(dimensionId)) {
            double distance = record.getBellPos().distSqr(bellPos);

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = record;
            }
        }

        return best;
    }

    public VillageRecord getOrCreateRecord(String dimensionId, BlockPos bellPos) {
        VillageRecord existing = this.findRecord(dimensionId, bellPos);

        if (existing != null) {
            return existing;
        }

        VillageRecord record = new VillageRecord(dimensionId, bellPos.asLong());
        this.recordsByDimension.computeIfAbsent(dimensionId, id -> new ArrayList<>()).add(record);
        this.setDirty();
        return record;
    }

    public void removeRecord(String dimensionId, VillageRecord record) {
        List<VillageRecord> records = this.recordsByDimension.get(dimensionId);

        if (records != null && records.remove(record)) {
            this.setDirty();
        }
    }

    public static class VillageRecord {
        private final String dimensionId;
        private long bellPos;

        private boolean naturalRolled;
        private boolean naturalAllowed;

        @Nullable
        private UUID naturalJuggernautId;

        /** Minecraft day the natural Juggernaut died, or -1 if it never existed or never died. */
        private long naturalDeathDay = -1L;
        private long lastReplacementAttemptDay = -1L;
        private long lastVillagerDangerDay = -1L;

        @Nullable
        private UUID raidJuggernautId;

        /** Game time at which the raid Juggernaut is due, or -1. */
        private long pendingRaidSpawnGameTime = -1L;
        private int lastHandledRaidId = -1;
        private int observedRaidId = -1;
        private int observedRaidBadOmenLevel;

        private VillageRecord(String dimensionId, long bellPos) {
            this.dimensionId = dimensionId;
            this.bellPos = bellPos;
        }

        private static VillageRecord load(CompoundTag tag) {
            VillageRecord record = new VillageRecord(tag.getString("Dimension"), tag.getLong("Bell"));
            record.naturalRolled = tag.getBoolean("NaturalRolled");
            record.naturalAllowed = tag.getBoolean("NaturalAllowed");
            record.naturalJuggernautId = tag.hasUUID("NaturalId") ? tag.getUUID("NaturalId") : null;
            record.naturalDeathDay = tag.contains("NaturalDeathDay") ? tag.getLong("NaturalDeathDay") : -1L;
            record.lastReplacementAttemptDay = tag.contains("LastReplacementDay") ? tag.getLong("LastReplacementDay") : -1L;
            record.lastVillagerDangerDay = tag.contains("LastDangerDay") ? tag.getLong("LastDangerDay") : -1L;
            record.raidJuggernautId = tag.hasUUID("RaidId") ? tag.getUUID("RaidId") : null;
            record.pendingRaidSpawnGameTime = tag.contains("PendingRaidSpawn") ? tag.getLong("PendingRaidSpawn") : -1L;
            record.lastHandledRaidId = tag.contains("LastHandledRaid") ? tag.getInt("LastHandledRaid") : -1;
            record.observedRaidId = tag.contains("ObservedRaid") ? tag.getInt("ObservedRaid") : -1;
            record.observedRaidBadOmenLevel = tag.getInt("ObservedRaidBadOmen");
            return record;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", this.dimensionId);
            tag.putLong("Bell", this.bellPos);
            tag.putBoolean("NaturalRolled", this.naturalRolled);
            tag.putBoolean("NaturalAllowed", this.naturalAllowed);

            if (this.naturalJuggernautId != null) {
                tag.putUUID("NaturalId", this.naturalJuggernautId);
            }

            tag.putLong("NaturalDeathDay", this.naturalDeathDay);
            tag.putLong("LastReplacementDay", this.lastReplacementAttemptDay);
            tag.putLong("LastDangerDay", this.lastVillagerDangerDay);

            if (this.raidJuggernautId != null) {
                tag.putUUID("RaidId", this.raidJuggernautId);
            }

            tag.putLong("PendingRaidSpawn", this.pendingRaidSpawnGameTime);
            tag.putInt("LastHandledRaid", this.lastHandledRaidId);
            tag.putInt("ObservedRaid", this.observedRaidId);
            tag.putInt("ObservedRaidBadOmen", this.observedRaidBadOmenLevel);
            return tag;
        }

        public String getDimensionId() {
            return this.dimensionId;
        }

        public BlockPos getBellPos() {
            return BlockPos.of(this.bellPos);
        }

        public boolean isNaturalRolled() {
            return this.naturalRolled;
        }

        public boolean isNaturalAllowed() {
            return this.naturalAllowed;
        }

        public void setNaturalRoll(boolean allowed) {
            this.naturalRolled = true;
            this.naturalAllowed = allowed;
        }

        @Nullable
        public UUID getNaturalJuggernautId() {
            return this.naturalJuggernautId;
        }

        public void setNaturalJuggernautId(@Nullable UUID id) {
            this.naturalJuggernautId = id;
        }

        public long getNaturalDeathDay() {
            return this.naturalDeathDay;
        }

        public void setNaturalDeathDay(long day) {
            this.naturalDeathDay = day;
        }

        public long getLastReplacementAttemptDay() {
            return this.lastReplacementAttemptDay;
        }

        public void setLastReplacementAttemptDay(long day) {
            this.lastReplacementAttemptDay = day;
        }

        public long getLastVillagerDangerDay() {
            return this.lastVillagerDangerDay;
        }

        public void setLastVillagerDangerDay(long day) {
            this.lastVillagerDangerDay = day;
        }

        @Nullable
        public UUID getRaidJuggernautId() {
            return this.raidJuggernautId;
        }

        public void setRaidJuggernautId(@Nullable UUID id) {
            this.raidJuggernautId = id;
        }

        public long getPendingRaidSpawnGameTime() {
            return this.pendingRaidSpawnGameTime;
        }

        public void setPendingRaidSpawnGameTime(long gameTime) {
            this.pendingRaidSpawnGameTime = gameTime;
        }

        public int getLastHandledRaidId() {
            return this.lastHandledRaidId;
        }

        public void setLastHandledRaidId(int raidId) {
            this.lastHandledRaidId = raidId;
        }

        public int getObservedRaidBadOmenLevel() {
            return this.observedRaidBadOmenLevel;
        }

        public void observeRaid(int raidId, int badOmenLevel) {
            if (this.observedRaidId != raidId) {
                this.observedRaidId = raidId;
                this.observedRaidBadOmenLevel = badOmenLevel;
                return;
            }

            this.observedRaidBadOmenLevel = Math.max(this.observedRaidBadOmenLevel, badOmenLevel);
        }
    }
}
