package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who a Juggernaut holds a grudge against, and for how long.
 *
 * <p>A grudge normally expires on its own clock. One taken during a defensive event instead lasts
 * until that event ends, so a raider cannot simply back off for half a minute mid-fight and be
 * treated as a neutral again.
 *
 * <p>The map is bounded: past {@link #MEMORY_LIMIT} entries the one expiring soonest is dropped.
 */
final class JuggernautAggression {

    static final int DEFAULT_TICKS = 600;
    static final int VILLAGER_KILLED_TICKS = 1200;

    private static final int MEMORY_LIMIT = 16;

    private static final String TAG_TARGET = "Target";
    private static final String TAG_EXPIRES = "ExpiresAt";
    private static final String TAG_DIRECT = "Direct";
    private static final String TAG_UNTIL_EVENT_END = "UntilEventEnd";

    private final Map<UUID, Grudge> grudges = new HashMap<>();

    void tick(long gameTime, boolean eventActive) {
        if (grudges.isEmpty()) {
            return;
        }

        grudges.values().removeIf(grudge -> grudge.untilEventEnd ? !eventActive : gameTime >= grudge.expiresAt);
    }

    void remember(UUID aggressor, long expiresAt, boolean hitTheJuggernaut, boolean untilEventEnd) {
        Grudge existing = grudges.get(aggressor);

        if (existing != null) {
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
            existing.hitTheJuggernaut |= hitTheJuggernaut;
            existing.untilEventEnd |= untilEventEnd;
            return;
        }

        if (grudges.size() >= MEMORY_LIMIT) {
            grudges.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().expiresAt, b.getValue().expiresAt))
                    .map(Map.Entry::getKey)
                    .ifPresent(grudges::remove);
        }

        grudges.put(aggressor, new Grudge(expiresAt, hitTheJuggernaut, untilEventEnd));
    }

    boolean holdsGrudgeAgainst(UUID uuid) {
        return grudges.containsKey(uuid);
    }

    /** True when the grudge came from a hit on the Juggernaut itself, not on the village. */
    boolean wasHitBy(UUID uuid) {
        Grudge grudge = grudges.get(uuid);
        return grudge != null && grudge.hitTheJuggernaut;
    }

    void save(CompoundTag entityTag, String key) {
        ListTag list = new ListTag();

        for (Map.Entry<UUID, Grudge> entry : grudges.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_TARGET, entry.getKey());
            tag.putLong(TAG_EXPIRES, entry.getValue().expiresAt);
            tag.putBoolean(TAG_DIRECT, entry.getValue().hitTheJuggernaut);
            tag.putBoolean(TAG_UNTIL_EVENT_END, entry.getValue().untilEventEnd);
            list.add(tag);
        }

        entityTag.put(key, list);
    }

    void load(CompoundTag entityTag, String key) {
        grudges.clear();

        ListTag list = entityTag.getList(key, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);

            if (!tag.hasUUID(TAG_TARGET)) {
                continue;
            }

            grudges.put(
                    tag.getUUID(TAG_TARGET),
                    new Grudge(
                            tag.getLong(TAG_EXPIRES),
                            tag.getBoolean(TAG_DIRECT),
                            tag.getBoolean(TAG_UNTIL_EVENT_END)
                    )
            );
        }
    }

    private static final class Grudge {
        private long expiresAt;
        private boolean hitTheJuggernaut;
        private boolean untilEventEnd;

        private Grudge(long expiresAt, boolean hitTheJuggernaut, boolean untilEventEnd) {
            this.expiresAt = expiresAt;
            this.hitTheJuggernaut = hitTheJuggernaut;
            this.untilEventEnd = untilEventEnd;
        }
    }
}
