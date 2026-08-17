package net.randomcara.raidborn.content.entity.juggernaut;

/** How a Juggernaut entered the world. Only NATURAL and RAID drop loot. */
public enum JuggernautOrigin {
    SPAWNED,
    NATURAL,
    RAID,
    EVENT;

    private static final JuggernautOrigin[] VALUES = values();

    public boolean dropsLoot() {
        return this == NATURAL || this == RAID;
    }

    public static JuggernautOrigin byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SPAWNED;
    }
}
