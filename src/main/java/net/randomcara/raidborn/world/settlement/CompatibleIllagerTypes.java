package net.randomcara.raidborn.world.settlement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.randomcara.bentoslib.world.spawn.CategorizedMobSpawnTable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CompatibleIllagerTypes {

    /** Persisted NBT in saved worlds: do not rename. */
    private static final String SETTLEMENT_ILLAGER_TAG = "RaidbornSettlementIllager";

    private static final Map<IllagerStrengthCategory, List<ResourceLocation>> ILLAGERS_BY_CATEGORY =
            new EnumMap<>(IllagerStrengthCategory.class);

    static {
        ILLAGERS_BY_CATEGORY.put(IllagerStrengthCategory.COMMON, List.of(
                id("minecraft:pillager"),
                id("minecraft:vindicator"),
                id("illagerinvasion:basher"),
                id("illagerinvasion:marauder"),
                id("takesapillage:archer"),
                id("takesapillage:skirmisher"),
                id("guardillagers:guard_illager"),
                id("hunters_return:hunter"),
                id("ravageandcabbage:cabbager"),
                id("raidborn:grumblager")
        ));

        ILLAGERS_BY_CATEGORY.put(IllagerStrengthCategory.STRONG, List.of(
                id("minecraft:witch"),
                id("savage_and_ravage:griefer"),
                id("savage_and_ravage:trickster"),
                id("illagerinvasion:provoker"),
                id("illagerinvasion:alchemist"),
                id("illagerinvasion:archivist"),
                id("takesapillage:legioner"),
                id("enchantwithmob:enchanter")
        ));

        ILLAGERS_BY_CATEGORY.put(IllagerStrengthCategory.VERY_STRONG, List.of(
                id("minecraft:evoker"),
                id("minecraft:illusioner"),
                id("savage_and_ravage:executioner"),
                id("savage_and_ravage:iceologer"),
                id("illagerinvasion:inquisitor"),
                id("illagerinvasion:firecaller"),
                id("illagerinvasion:sorcerer"),
                id("illagerinvasion:necromancer")
        ));
    }

    private static final CategorizedMobSpawnTable<IllagerStrengthCategory> SPAWN_TABLE =
            new CategorizedMobSpawnTable<>(ILLAGERS_BY_CATEGORY, SETTLEMENT_ILLAGER_TAG);

    private CompatibleIllagerTypes() {
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid entity id: " + value);
        }
        return id;
    }

    public static boolean spawnRandomIllager(ServerLevel level, IllagerStrengthCategory category, double x, double y,
                                             double z, float yRot, float xRot, float yHeadRot) {
        return SPAWN_TABLE.spawnRandom(level, category, x, y, z, yRot, xRot, yHeadRot);
    }
}
