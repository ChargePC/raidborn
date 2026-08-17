package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AttackSavedData extends SavedData {
    private static final String DATA_NAME = Raidborn.MOD_ID + "_attack_data";

    private final List<CooldownEntry> cooldowns = new ArrayList<>();

    public static AttackSavedData get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(AttackSavedData::load, AttackSavedData::new, DATA_NAME);
    }

    public static AttackSavedData load(CompoundTag tag) {
        AttackSavedData data = new AttackSavedData();
        ListTag list = tag.getList("Cooldowns", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entryTag.getString("Dimension"));
            if (dimensionId == null) {
                continue;
            }

            BlockPos center = BlockPos.of(entryTag.getLong("Center"));
            int radius = entryTag.getInt("Radius");
            long expiresAt = entryTag.getLong("ExpiresAt");
            data.cooldowns.add(new CooldownEntry(dimensionId.toString(), center, radius, expiresAt));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (CooldownEntry entry : cooldowns) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Dimension", entry.dimensionId());
            entryTag.putLong("Center", entry.center().asLong());
            entryTag.putInt("Radius", entry.radius());
            entryTag.putLong("ExpiresAt", entry.expiresAt());
            list.add(entryTag);
        }

        tag.put("Cooldowns", list);
        return tag;
    }

    public boolean isOnCooldown(ResourceKey<Level> dimension, BlockPos center, int radius, long gameTime) {
        cleanupExpired(gameTime);

        String dimensionId = dimension.location().toString();
        double extra = RaidbornServerConfig.ATTACK_COOLDOWN_MATCH_EXTRA_RADIUS.get();

        for (CooldownEntry entry : cooldowns) {
            if (!entry.dimensionId().equals(dimensionId)) {
                continue;
            }

            double matchRadius = Math.max(radius, entry.radius()) + extra;
            if (entry.center().distSqr(center) <= matchRadius * matchRadius) {
                return true;
            }
        }

        return false;
    }

    public void addCooldown(ResourceKey<Level> dimension, BlockPos center, int radius, long expiresAt) {
        if (RaidbornServerConfig.ATTACK_COOLDOWN_TICKS.get() <= 0) {
            return;
        }

        cooldowns.add(new CooldownEntry(dimension.location().toString(), center.immutable(), radius, expiresAt));
        setDirty();
    }

    private void cleanupExpired(long gameTime) {
        boolean changed = false;
        Iterator<CooldownEntry> iterator = cooldowns.iterator();

        while (iterator.hasNext()) {
            CooldownEntry entry = iterator.next();
            if (entry.expiresAt() <= gameTime) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
        }
    }

    private record CooldownEntry(String dimensionId, BlockPos center, int radius, long expiresAt) {
    }
}
