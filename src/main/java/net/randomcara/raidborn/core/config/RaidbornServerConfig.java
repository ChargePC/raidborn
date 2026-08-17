package net.randomcara.raidborn.core.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RaidbornServerConfig {
    /** How many configurable extra defender slots [attack.extra_defenders] exposes. */
    private static final int EXTRA_DEFENDER_SLOTS = 3;

    /** Per-slot ceiling, high enough that the practical limit is the village size. */
    private static final int EXTRA_DEFENDER_MAX_PER_TIER = 128;

    /**
     * One [attack.extra_defenders.extraDefenderN] block.
     *
     * <p>Every slot carries the same five options, so they are declared once and repeated per slot
     * rather than spelled out three times. The option paths are unchanged, and an existing
     * raidborn-server.toml keeps working.
     */
    public record ExtraDefenderSlot(
            ForgeConfigSpec.BooleanValue enabled,
            ForgeConfigSpec.ConfigValue<String> entityId,
            ForgeConfigSpec.IntValue loyaltyMax,
            ForgeConfigSpec.IntValue honorMax,
            ForgeConfigSpec.IntValue heroMax) {
    }

    private static ExtraDefenderSlot defineExtraDefender(ForgeConfigSpec.Builder b, int slot) {
        b.push("extraDefender" + slot);

        // Left-to-right argument evaluation keeps the options in the order they were written in
        // before, so the generated file does not get reshuffled.
        ExtraDefenderSlot defined = new ExtraDefenderSlot(
                b.comment("If true, this configurable extra defender can spawn during Attacks.")
                        .define("enabled", false),
                b.comment(
                                "Entity ID for configurable extra defender " + slot + ".",
                                "Leave empty to disable.",
                                "Format: \"namespace:id\". Example: \"minecraft:iron_golem\".",
                                "The entity must be a Mob to be spawned by this system."
                        )
                        .define("entityId", ""),
                b.comment("Maximum extra defender " + slot + " spawned by an Attack started with Illager Loyalty.")
                        .defineInRange("loyaltyMax", 0, 0, EXTRA_DEFENDER_MAX_PER_TIER),
                b.comment("Maximum extra defender " + slot + " spawned by an Attack started with Illager Honor.")
                        .defineInRange("honorMax", 0, 0, EXTRA_DEFENDER_MAX_PER_TIER),
                b.comment("Maximum extra defender " + slot + " spawned by an Attack started with Hero of the Illage.")
                        .defineInRange("heroMax", 0, 0, EXTRA_DEFENDER_MAX_PER_TIER)
        );

        b.pop();
        return defined;
    }

    public static final ForgeConfigSpec SPEC;
    public static final Values VALUES;

    public static final ForgeConfigSpec.BooleanValue ARTIFACT_CHEST_LOOT_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ARTIFACT_LOOT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue VILLAGER_SOUL_DROP_CHANCE;

    public static final ForgeConfigSpec.IntValue LOYALTY_RECRUIT_SLOTS;
    public static final ForgeConfigSpec.IntValue HONOR_RECRUIT_SLOTS;
    public static final ForgeConfigSpec.IntValue HERO_RECRUIT_SLOTS;
    public static final ForgeConfigSpec.BooleanValue SUPPORT_HEALER_AI_ENABLED;

    public static final ForgeConfigSpec.IntValue SETTLEMENT_DEFAULT_RADIUS;
    public static final ForgeConfigSpec.IntValue SETTLEMENT_MAX_RADIUS;

    public static final ForgeConfigSpec.IntValue TRANSMUTATION_CRAFT_TIME_TICKS;

    public static final ForgeConfigSpec.DoubleValue TOTEM_HEALING_RADIUS;
    public static final ForgeConfigSpec.DoubleValue TOTEM_PROTECTION_RADIUS;
    public static final ForgeConfigSpec.DoubleValue TOTEM_RESISTANCE_RADIUS;

    public static final ForgeConfigSpec.BooleanValue ATTACK_ENABLED;
    public static final ForgeConfigSpec.IntValue ATTACK_CHECK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_DETECTION_RADIUS;
    public static final ForgeConfigSpec.IntValue ATTACK_REQUIRED_VILLAGERS;
    public static final ForgeConfigSpec.IntValue ATTACK_REQUIRED_POIS;
    public static final ForgeConfigSpec.BooleanValue ATTACK_REQUIRE_NATURAL_GOLEM;
    public static final ForgeConfigSpec.BooleanValue ATTACK_IGNORE_VILLAGERS_IN_VEHICLES;
    public static final ForgeConfigSpec.BooleanValue ATTACK_RALLY_RECRUITS_ON_START;
    public static final ForgeConfigSpec.IntValue ATTACK_RALLY_RADIUS;
    public static final ForgeConfigSpec.IntValue ATTACK_ABANDON_RADIUS;
    public static final ForgeConfigSpec.IntValue ATTACK_ABANDON_TIME_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_SMALL_TIME_LIMIT_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_MEDIUM_TIME_LIMIT_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_LARGE_TIME_LIMIT_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_MAX_EXTRA_DEFENDERS_SMALL;
    public static final ForgeConfigSpec.IntValue ATTACK_MAX_EXTRA_DEFENDERS_MEDIUM;
    public static final ForgeConfigSpec.IntValue ATTACK_MAX_EXTRA_DEFENDERS_LARGE;
    public static final ForgeConfigSpec.BooleanValue ATTACK_SPAWN_DEFENDERS_PER_VILLAGER;
    public static final ForgeConfigSpec.IntValue ATTACK_LOYALTY_MAX_IRON_GOLEMS;
    public static final ForgeConfigSpec.IntValue ATTACK_LOYALTY_MAX_IRON_GOLLETS;
    public static final ForgeConfigSpec.IntValue ATTACK_HONOR_MAX_IRON_GOLEMS;
    public static final ForgeConfigSpec.IntValue ATTACK_HONOR_MAX_IRON_GOLLETS;
    public static final ForgeConfigSpec.IntValue ATTACK_HERO_MAX_IRON_GOLEMS;
    public static final ForgeConfigSpec.IntValue ATTACK_HERO_MAX_IRON_GOLLETS;

    /** The [attack.extra_defenders] slots, in config order. */
    public static final List<ExtraDefenderSlot> ATTACK_EXTRA_DEFENDERS;

    public static final ForgeConfigSpec.BooleanValue ATTACK_HERO_SUPER_DEFENDER_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> ATTACK_HERO_SUPER_DEFENDER_ENTITY_ID;
    public static final ForgeConfigSpec.BooleanValue ATTACK_EXTRA_DEFENDERS_PERSISTENT;
    public static final ForgeConfigSpec.BooleanValue ATTACK_DESPAWN_SPAWNED_DEFENDERS_AFTER_END;
    public static final ForgeConfigSpec.IntValue ATTACK_VICTORY_CELEBRATION_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_END_BOSSBAR_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue ATTACK_COOLDOWN_MATCH_EXTRA_RADIUS;
    public static final ForgeConfigSpec.DoubleValue ATTACK_VILLAGER_PANIC_SPEED;

    private static final Set<String> TRADES_DISABLED_CACHE = new HashSet<>();
    private static final Set<String> RECRUIT_DISABLED_CACHE = new HashSet<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        VALUES = new Values(builder);

        ARTIFACT_CHEST_LOOT_ENABLED = VALUES.artifactChestLootEnabled;
        ARTIFACT_LOOT_CHANCE = VALUES.artifactLootChance;
        VILLAGER_SOUL_DROP_CHANCE = VALUES.villagerSoulDropChance;

        LOYALTY_RECRUIT_SLOTS = VALUES.loyaltyRecruitSlots;
        HONOR_RECRUIT_SLOTS = VALUES.honorRecruitSlots;
        HERO_RECRUIT_SLOTS = VALUES.heroRecruitSlots;
        SUPPORT_HEALER_AI_ENABLED = VALUES.supportHealerAiEnabled;

        SETTLEMENT_DEFAULT_RADIUS = VALUES.settlementDefaultRadius;
        SETTLEMENT_MAX_RADIUS = VALUES.settlementMaxRadius;

        TRANSMUTATION_CRAFT_TIME_TICKS = VALUES.transmutationCraftTimeTicks;

        TOTEM_HEALING_RADIUS = VALUES.totemHealingRadius;
        TOTEM_PROTECTION_RADIUS = VALUES.totemProtectionRadius;
        TOTEM_RESISTANCE_RADIUS = VALUES.totemResistanceRadius;

        ATTACK_ENABLED = VALUES.attackEnabled;
        ATTACK_CHECK_INTERVAL_TICKS = VALUES.attackCheckIntervalTicks;
        ATTACK_DETECTION_RADIUS = VALUES.attackDetectionRadius;
        ATTACK_REQUIRED_VILLAGERS = VALUES.attackRequiredVillagers;
        ATTACK_REQUIRED_POIS = VALUES.attackRequiredPois;
        ATTACK_REQUIRE_NATURAL_GOLEM = VALUES.attackRequireNaturalGolem;
        ATTACK_IGNORE_VILLAGERS_IN_VEHICLES = VALUES.attackIgnoreVillagersInVehicles;
        ATTACK_RALLY_RECRUITS_ON_START = VALUES.attackRallyRecruitsOnStart;
        ATTACK_RALLY_RADIUS = VALUES.attackRallyRadius;
        ATTACK_ABANDON_RADIUS = VALUES.attackAbandonRadius;
        ATTACK_ABANDON_TIME_TICKS = VALUES.attackAbandonTimeTicks;
        ATTACK_SMALL_TIME_LIMIT_TICKS = VALUES.attackSmallTimeLimitTicks;
        ATTACK_MEDIUM_TIME_LIMIT_TICKS = VALUES.attackMediumTimeLimitTicks;
        ATTACK_LARGE_TIME_LIMIT_TICKS = VALUES.attackLargeTimeLimitTicks;
        ATTACK_MAX_EXTRA_DEFENDERS_SMALL = VALUES.attackMaxExtraDefendersSmall;
        ATTACK_MAX_EXTRA_DEFENDERS_MEDIUM = VALUES.attackMaxExtraDefendersMedium;
        ATTACK_MAX_EXTRA_DEFENDERS_LARGE = VALUES.attackMaxExtraDefendersLarge;
        ATTACK_SPAWN_DEFENDERS_PER_VILLAGER = VALUES.attackSpawnDefendersPerVillager;
        ATTACK_LOYALTY_MAX_IRON_GOLEMS = VALUES.attackLoyaltyMaxIronGolems;
        ATTACK_LOYALTY_MAX_IRON_GOLLETS = VALUES.attackLoyaltyMaxIronGollets;
        ATTACK_HONOR_MAX_IRON_GOLEMS = VALUES.attackHonorMaxIronGolems;
        ATTACK_HONOR_MAX_IRON_GOLLETS = VALUES.attackHonorMaxIronGollets;
        ATTACK_HERO_MAX_IRON_GOLEMS = VALUES.attackHeroMaxIronGolems;
        ATTACK_HERO_MAX_IRON_GOLLETS = VALUES.attackHeroMaxIronGollets;

        ATTACK_EXTRA_DEFENDERS = VALUES.attackExtraDefenders;

        ATTACK_HERO_SUPER_DEFENDER_ENABLED = VALUES.attackHeroSuperDefenderEnabled;
        ATTACK_HERO_SUPER_DEFENDER_ENTITY_ID = VALUES.attackHeroSuperDefenderEntityId;
        ATTACK_EXTRA_DEFENDERS_PERSISTENT = VALUES.attackExtraDefendersPersistent;
        ATTACK_DESPAWN_SPAWNED_DEFENDERS_AFTER_END = VALUES.attackDespawnSpawnedDefendersAfterEnd;
        ATTACK_VICTORY_CELEBRATION_TICKS = VALUES.attackVictoryCelebrationTicks;
        ATTACK_END_BOSSBAR_DELAY_TICKS = VALUES.attackEndBossbarDelayTicks;
        ATTACK_COOLDOWN_TICKS = VALUES.attackCooldownTicks;
        ATTACK_COOLDOWN_MATCH_EXTRA_RADIUS = VALUES.attackCooldownMatchExtraRadius;
        ATTACK_VILLAGER_PANIC_SPEED = VALUES.attackVillagerPanicSpeed;

        SPEC = builder.build();
    }

    public static class Values {
        public final ForgeConfigSpec.BooleanValue artifactChestLootEnabled;
        public final ForgeConfigSpec.DoubleValue artifactLootChance;
        public final ForgeConfigSpec.DoubleValue villagerSoulDropChance;

        public final ForgeConfigSpec.BooleanValue tradesEnabledGlobal;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> tradesDisabledFor;

        public final ForgeConfigSpec.BooleanValue recruitmentEnabledGlobal;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> recruitmentDisabledFor;
        public final ForgeConfigSpec.IntValue loyaltyRecruitSlots;
        public final ForgeConfigSpec.IntValue honorRecruitSlots;
        public final ForgeConfigSpec.IntValue heroRecruitSlots;
        public final ForgeConfigSpec.BooleanValue supportHealerAiEnabled;
        public final ForgeConfigSpec.BooleanValue witchRecruitable;
        public final ForgeConfigSpec.BooleanValue iceologerRecruitable;
        public final ForgeConfigSpec.BooleanValue tricksterRecruitable;
        public final ForgeConfigSpec.BooleanValue archivistRecruitable;
        public final ForgeConfigSpec.BooleanValue firecallerRecruitable;
        public final ForgeConfigSpec.BooleanValue grumblagerArmorEquippingEnabled;

        public final ForgeConfigSpec.IntValue settlementDefaultRadius;
        public final ForgeConfigSpec.IntValue settlementMaxRadius;

        public final ForgeConfigSpec.IntValue transmutationCraftTimeTicks;

        public final ForgeConfigSpec.DoubleValue totemHealingRadius;
        public final ForgeConfigSpec.DoubleValue totemProtectionRadius;
        public final ForgeConfigSpec.DoubleValue totemResistanceRadius;

        public final ForgeConfigSpec.BooleanValue attackEnabled;
        public final ForgeConfigSpec.IntValue attackCheckIntervalTicks;
        public final ForgeConfigSpec.IntValue attackDetectionRadius;
        public final ForgeConfigSpec.IntValue attackRequiredVillagers;
        public final ForgeConfigSpec.IntValue attackRequiredPois;
        public final ForgeConfigSpec.BooleanValue attackRequireNaturalGolem;
        public final ForgeConfigSpec.BooleanValue attackIgnoreVillagersInVehicles;
        public final ForgeConfigSpec.BooleanValue attackRallyRecruitsOnStart;
        public final ForgeConfigSpec.IntValue attackRallyRadius;
        public final ForgeConfigSpec.IntValue attackAbandonRadius;
        public final ForgeConfigSpec.IntValue attackAbandonTimeTicks;
        public final ForgeConfigSpec.IntValue attackSmallTimeLimitTicks;
        public final ForgeConfigSpec.IntValue attackMediumTimeLimitTicks;
        public final ForgeConfigSpec.IntValue attackLargeTimeLimitTicks;
        public final ForgeConfigSpec.IntValue attackMaxExtraDefendersSmall;
        public final ForgeConfigSpec.IntValue attackMaxExtraDefendersMedium;
        public final ForgeConfigSpec.IntValue attackMaxExtraDefendersLarge;
        public final ForgeConfigSpec.BooleanValue attackSpawnDefendersPerVillager;
        public final ForgeConfigSpec.IntValue attackLoyaltyMaxIronGolems;
        public final ForgeConfigSpec.IntValue attackLoyaltyMaxIronGollets;
        public final ForgeConfigSpec.IntValue attackHonorMaxIronGolems;
        public final ForgeConfigSpec.IntValue attackHonorMaxIronGollets;
        public final ForgeConfigSpec.IntValue attackHeroMaxIronGolems;
        public final ForgeConfigSpec.IntValue attackHeroMaxIronGollets;

        public final List<ExtraDefenderSlot> attackExtraDefenders;

        public final ForgeConfigSpec.BooleanValue attackHeroSuperDefenderEnabled;
        public final ForgeConfigSpec.ConfigValue<String> attackHeroSuperDefenderEntityId;
        public final ForgeConfigSpec.BooleanValue attackExtraDefendersPersistent;
        public final ForgeConfigSpec.BooleanValue attackDespawnSpawnedDefendersAfterEnd;
        public final ForgeConfigSpec.IntValue attackVictoryCelebrationTicks;
        public final ForgeConfigSpec.IntValue attackEndBossbarDelayTicks;
        public final ForgeConfigSpec.IntValue attackCooldownTicks;
        public final ForgeConfigSpec.IntValue attackCooldownMatchExtraRadius;
        public final ForgeConfigSpec.DoubleValue attackVillagerPanicSpeed;

        public final ForgeConfigSpec.BooleanValue juggernautNaturalSpawnEnabled;
        public final ForgeConfigSpec.DoubleValue juggernautNaturalSpawnChance;
        public final ForgeConfigSpec.IntValue juggernautMinVillagers;
        public final ForgeConfigSpec.IntValue juggernautVillageScanRadius;
        public final ForgeConfigSpec.IntValue juggernautReplacementDelayDays;
        public final ForgeConfigSpec.DoubleValue juggernautReplacementDailyChance;
        public final ForgeConfigSpec.BooleanValue juggernautRaidRewardEnabled;
        public final ForgeConfigSpec.IntValue juggernautRaidRewardMinBadOmen;
        public final ForgeConfigSpec.IntValue juggernautRaidRewardMinDelayTicks;
        public final ForgeConfigSpec.IntValue juggernautRaidRewardMaxDelayTicks;

        public final ForgeConfigSpec.DoubleValue squadCommandRadius;
        public final ForgeConfigSpec.DoubleValue squadHoldScanRadius;
        public final ForgeConfigSpec.DoubleValue squadHoldLeashRadius;
        public final ForgeConfigSpec.DoubleValue squadHoldWanderRadius;
        public final ForgeConfigSpec.DoubleValue squadAttackOrderChaseRadius;
        public final ForgeConfigSpec.IntValue squadAttackOrderDurationTicks;
        public final ForgeConfigSpec.DoubleValue squadFollowTeleportDistance;
        public final ForgeConfigSpec.DoubleValue squadSlotScanRadius;
        public final ForgeConfigSpec.DoubleValue squadSupportHealRadius;
        public final ForgeConfigSpec.IntValue squadSupportHealCooldownTicks;

        public final ForgeConfigSpec.BooleanValue compatIllagerInvasion;
        public final ForgeConfigSpec.BooleanValue compatSavageAndRavage;
        public final ForgeConfigSpec.BooleanValue compatTakesAPillage;
        public final ForgeConfigSpec.BooleanValue compatGuardIllagers;
        public final ForgeConfigSpec.BooleanValue compatGuardVillagers;
        public final ForgeConfigSpec.BooleanValue compatHuntersReturn;
        public final ForgeConfigSpec.BooleanValue compatConjurerIllager;
        public final ForgeConfigSpec.BooleanValue compatEnchantWithMob;
        public final ForgeConfigSpec.BooleanValue compatRavageAndCabbage;
        public final ForgeConfigSpec.BooleanValue compatArtifacts;

        Values(ForgeConfigSpec.Builder b) {
            b.push("loot");

            artifactChestLootEnabled = b
                    .comment("Allows Raidborn artifacts to be injected into vanilla chest loot.")
                    .define("artifactChestLootEnabled", true);

            artifactLootChance = b
                    .comment("Chance for the injected artifact loot pool to roll. 0.33 is roughly one in three chests.")
                    .defineInRange("artifactLootChance", 0.33D, 0.0D, 1.0D);

            villagerSoulDropChance = b
                    .comment("Chance for Villagers to drop a Villager Soul when the Raidborn soul drop event is used.")
                    .defineInRange("villagerSoulDropChance", 0.10D, 0.0D, 1.0D);

            b.pop();

            b.push("trades");

            tradesEnabledGlobal = b
                    .comment("If false, disables all Raidborn trades.")
                    .define("enabledGlobal", true);

            tradesDisabledFor = b
                    .comment(
                            "Entity IDs that should not get Raidborn trades.",
                            "Format: \"namespace:id\". Example: \"minecraft:pillager\"."
                    )
                    .defineListAllowEmpty("disabledFor", List.of(), o -> o instanceof String s && isValidRL(s));

            b.pop();

            b.push("recruitment");

            recruitmentEnabledGlobal = b
                    .comment("If false, disables all Raidborn recruitment.")
                    .define("enabledGlobal", true);

            recruitmentDisabledFor = b
                    .comment(
                            "Entity IDs that should not be recruited by Raidborn.",
                            "Format: \"namespace:id\". Example: \"minecraft:pillager\"."
                    )
                    .defineListAllowEmpty("disabledFor", List.of(), o -> o instanceof String s && isValidRL(s));

            loyaltyRecruitSlots = b
                    .comment("Maximum recruit slots granted by Illager Loyalty.")
                    .defineInRange("loyaltyRecruitSlots", 5, 0, 128);

            honorRecruitSlots = b
                    .comment("Maximum recruit slots granted by Illager Honor.")
                    .defineInRange("honorRecruitSlots", 10, 0, 128);

            heroRecruitSlots = b
                    .comment("Maximum recruit slots granted by Hero of the Illage.")
                    .defineInRange("heroRecruitSlots", 15, 0, 128);

            supportHealerAiEnabled = b
                    .comment("Allows recruited Witches and Alchemists to heal the player and allied recruits.")
                    .define("supportHealerAiEnabled", true);

            witchRecruitable = b
                    .comment("If true, witches can be recruited by Raidborn.")
                    .define("witchRecruitable", true);

            b.push("special_units");

            iceologerRecruitable = b
                    .comment("If true, Iceologers can be recruited by Raidborn.")
                    .define("iceologerRecruitable", true);

            tricksterRecruitable = b
                    .comment("If true, Tricksters can be recruited by Raidborn.")
                    .define("tricksterRecruitable", true);

            archivistRecruitable = b
                    .comment("If true, Archivists can be recruited by Raidborn.")
                    .define("archivistRecruitable", true);

            firecallerRecruitable = b
                    .comment("If true, Firecallers can be recruited by Raidborn.")
                    .define("firecallerRecruitable", true);

            b.pop();

            b.push("grumblager");

            grumblagerArmorEquippingEnabled = b
                    .comment("If true, recruited Grumblagers can be equipped with armor by right-clicking them with armor pieces.")
                    .define("armorEquippingEnabled", false);

            b.pop();
            b.pop();

            b.push("settlement");

            settlementDefaultRadius = b
                    .comment("Default radius used when recruits are linked to a Grand Warbell settlement.")
                    .defineInRange("defaultRadius", 48, 16, 256);

            settlementMaxRadius = b
                    .comment("Maximum allowed Grand Warbell settlement radius.")
                    .defineInRange("maxRadius", 96, 16, 512);

            b.pop();

            b.push("transmutation");

            transmutationCraftTimeTicks = b
                    .comment("Ticks needed for the Transmutation Table to finish one recipe. 200 ticks = 10 seconds.")
                    .defineInRange("craftTimeTicks", 200, 20, 20 * 60 * 10);

            b.pop();

            b.push("totems");

            totemHealingRadius = b
                    .comment("Area radius used by the Totem of Healing.")
                    .defineInRange("healingRadius", 8.0D, 1.0D, 64.0D);

            totemProtectionRadius = b
                    .comment("Area radius used by the Totem of Protection.")
                    .defineInRange("protectionRadius", 8.0D, 1.0D, 64.0D);

            totemResistanceRadius = b
                    .comment("Area radius used by the Totem of Resistance.")
                    .defineInRange("resistanceRadius", 8.0D, 1.0D, 64.0D);

            b.pop();

            b.push("attack");

            attackEnabled = b
                    .comment("Enables the Raidborn Attack event system.")
                    .define("enabled", true);

            attackCheckIntervalTicks = b
                    .comment("How often each player is checked for Attack activation.")
                    .defineInRange("checkIntervalTicks", 40, 5, 200);

            attackDetectionRadius = b
                    .comment("Radius used to detect a valid village around the player.")
                    .defineInRange("detectionRadius", 64, 16, 256);

            attackRequiredVillagers = b
                    .comment("Minimum living villagers required to start an Attack.")
                    .defineInRange("requiredVillagers", 3, 1, 128);

            attackRequiredPois = b
                    .comment("Minimum valid village POIs required to start an Attack.")
                    .defineInRange("requiredPois", 3, 1, 256);

            attackRequireNaturalGolem = b
                    .comment("If true, at least one naturally spawned iron golem is required to start an Attack.")
                    .define("requireNaturalGolem", true);

            attackIgnoreVillagersInVehicles = b
                    .comment("If true, villagers inside boats or minecarts are ignored for Attack activation and objectives.")
                    .define("ignoreVillagersInVehicles", true);

            attackRallyRecruitsOnStart = b
                    .comment(
                            "If true, the owner's recruits are teleported next to them when an Attack starts.",
                            "Recruits in Settlement Mode are never rallied. Rallied recruits are set back to the Follow order."
                    )
                    .define("rallyRecruitsOnStart", true);

            attackRallyRadius = b
                    .comment(
                            "Radius around the owner searched for recruits to rally when an Attack starts.",
                            "Recruits in unloaded chunks cannot be found regardless of this value."
                    )
                    .defineInRange("rallyRadius", 128, 16, 512);

            attackAbandonRadius = b
                    .comment("Distance from the Attack center before the owner starts abandoning the Attack.")
                    .defineInRange("abandonRadius", 96, 32, 512);

            attackAbandonTimeTicks = b
                    .comment("How long the owner may stay outside the abandon radius before the Attack is abandoned. 600 ticks = 30 seconds.")
                    .defineInRange("abandonTimeTicks", 600, 20, 20 * 60 * 10);

            attackSmallTimeLimitTicks = b
                    .comment("Time limit for small villages. 12000 ticks = 10 minutes.")
                    .defineInRange("smallTimeLimitTicks", 12000, 20 * 60, 20 * 60 * 60);

            attackMediumTimeLimitTicks = b
                    .comment("Time limit for medium villages. 18000 ticks = 15 minutes.")
                    .defineInRange("mediumTimeLimitTicks", 18000, 20 * 60, 20 * 60 * 60);

            attackLargeTimeLimitTicks = b
                    .comment("Time limit for large villages. 24000 ticks = 20 minutes.")
                    .defineInRange("largeTimeLimitTicks", 24000, 20 * 60, 20 * 60 * 60);

            attackMaxExtraDefendersSmall = b
                    .comment("Global maximum extra defenders spawned for villages with 3 to 5 registered villagers.")
                    .defineInRange("maxExtraDefendersSmall", 8, 0, 128);

            attackMaxExtraDefendersMedium = b
                    .comment("Global maximum extra defenders spawned for villages with 6 to 12 registered villagers.")
                    .defineInRange("maxExtraDefendersMedium", 18, 0, 256);

            attackMaxExtraDefendersLarge = b
                    .comment("Global maximum extra defenders spawned for villages with 13 or more registered villagers.")
                    .defineInRange("maxExtraDefendersLarge", 28, 0, 512);

            attackSpawnDefendersPerVillager = b
                    .comment("If true, the Attack tries to spawn extra village defenders near registered villagers.")
                    .define("spawnDefendersPerVillager", true);

            b.push("loyalty");

            attackLoyaltyMaxIronGolems = b
                    .comment("Maximum extra Iron Golems spawned by an Attack started with Illager Loyalty.")
                    .defineInRange("maxIronGolems", 1, 0, 128);

            attackLoyaltyMaxIronGollets = b
                    .comment("Maximum extra Iron Gollets spawned by an Attack started with Illager Loyalty.")
                    .defineInRange("maxIronGollets", 3, 0, 128);

            b.pop();

            b.push("honor");

            attackHonorMaxIronGolems = b
                    .comment("Maximum extra Iron Golems spawned by an Attack started with Illager Honor.")
                    .defineInRange("maxIronGolems", 2, 0, 128);

            attackHonorMaxIronGollets = b
                    .comment("Maximum extra Iron Gollets spawned by an Attack started with Illager Honor.")
                    .defineInRange("maxIronGollets", 5, 0, 128);

            b.pop();

            b.push("hero");

            attackHeroMaxIronGolems = b
                    .comment(
                            "Maximum extra Iron Golems spawned by an Attack started with Hero of the Illage.",
                            "Default: 0. At this tier the village is defended by the Iron Juggernaut and Iron Gollets instead."
                    )
                    .defineInRange("maxIronGolems", 0, 0, 128);

            attackHeroMaxIronGollets = b
                    .comment("Maximum extra Iron Gollets spawned by an Attack started with Hero of the Illage.")
                    .defineInRange("maxIronGollets", 7, 0, 128);

            attackHeroSuperDefenderEnabled = b
                    .comment(
                            "If true, Hero of the Illage Attacks spawn one special configurable defender.",
                            "Default: false."
                    )
                    .define("superDefenderEnabled", false);

            attackHeroSuperDefenderEntityId = b
                    .comment(
                            "Entity ID for the special Hero Attack defender.",
                            "This entity is spawned only once when a Hero of the Illage Attack starts.",
                            "Format: \"namespace:id\". Example: \"minecraft:iron_golem\", \"minecraft:warden\", or a modded entity ID.",
                            "The entity must be a Mob to be spawned by this system."
                    )
                    .define("superDefenderEntityId", "minecraft:iron_golem");

            b.pop();

            b.push("extra_defenders");

            List<ExtraDefenderSlot> slots = new ArrayList<>(EXTRA_DEFENDER_SLOTS);
            for (int slot = 1; slot <= EXTRA_DEFENDER_SLOTS; slot++) {
                slots.add(defineExtraDefender(b, slot));
            }
            attackExtraDefenders = List.copyOf(slots);

            b.pop();

            attackExtraDefendersPersistent = b
                    .comment("If true, spawned Attack defenders are marked persistent while the event is active.")
                    .define("extraDefendersPersistent", true);

            attackDespawnSpawnedDefendersAfterEnd = b
                    .comment("If true, defenders spawned by the Attack are removed when the Attack ends.")
                    .define("despawnSpawnedDefendersAfterEnd", true);

            attackVictoryCelebrationTicks = b
                    .comment("How long allied illagers celebrate after winning an Attack. 300 ticks = 15 seconds.")
                    .defineInRange("victoryCelebrationTicks", 300, 20, 20 * 60 * 5);

            attackEndBossbarDelayTicks = b
                    .comment("How long the victory/failure bossbar stays visible after the Attack ends.")
                    .defineInRange("endBossbarDelayTicks", 100, 20, 20 * 60);

            attackCooldownTicks = b
                    .comment("Village cooldown after an Attack ends. 12000 ticks = 10 minutes.")
                    .defineInRange("cooldownTicks", 12000, 0, 20 * 60 * 60);

            attackCooldownMatchExtraRadius = b
                    .comment("Extra radius used when checking whether a village center is still on cooldown.")
                    .defineInRange("cooldownMatchExtraRadius", 32, 0, 256);

            attackVillagerPanicSpeed = b
                    .comment("Navigation speed used by registered villagers when fleeing during an Attack.")
                    .defineInRange("villagerPanicSpeed", 0.85D, 0.1D, 2.0D);

            b.pop();

            b.push("juggernaut");

            juggernautNaturalSpawnEnabled = b
                    .comment(
                            "If true, eligible villages can gain an Iron Juggernaut on their own.",
                            "Turn this off to keep Juggernauts as Attack defenders and raid rewards only."
                    )
                    .define("naturalSpawnEnabled", true);

            juggernautNaturalSpawnChance = b
                    .comment("Chance for an eligible village to receive a natural Juggernaut when scanned.")
                    .defineInRange("naturalSpawnChance", 0.25D, 0.0D, 1.0D);

            juggernautMinVillagers = b
                    .comment("Minimum living villagers for a village to be eligible for a natural Juggernaut.")
                    .defineInRange("minVillagers", 2, 1, 64);

            juggernautVillageScanRadius = b
                    .comment("Radius around a village bell scanned for Juggernaut bookkeeping.")
                    .defineInRange("villageScanRadius", 96, 16, 256);

            juggernautReplacementDelayDays = b
                    .comment("In-game days a village waits before it may replace a dead natural Juggernaut.")
                    .defineInRange("replacementDelayDays", 5, 0, 1000);

            juggernautReplacementDailyChance = b
                    .comment("Daily chance to replace a dead natural Juggernaut once the delay has passed.")
                    .defineInRange("replacementDailyChance", 0.10D, 0.0D, 1.0D);

            juggernautRaidRewardEnabled = b
                    .comment("If true, a village that survives a strong raid is granted a Juggernaut.")
                    .define("raidRewardEnabled", true);

            juggernautRaidRewardMinBadOmen = b
                    .comment("Minimum Bad Omen level of the survived raid for the Juggernaut reward.")
                    .defineInRange("raidRewardMinBadOmenLevel", 5, 1, 10);

            juggernautRaidRewardMinDelayTicks = b
                    .comment("Shortest delay between the raid victory and the reward Juggernaut appearing.")
                    .defineInRange("raidRewardMinDelayTicks", 1200, 0, 20 * 60 * 60 * 2);

            juggernautRaidRewardMaxDelayTicks = b
                    .comment(
                            "Longest delay between the raid victory and the reward Juggernaut appearing.",
                            "Values below the minimum are clamped up to it at runtime."
                    )
                    .defineInRange("raidRewardMaxDelayTicks", 24000, 0, 20 * 60 * 60 * 2);

            b.pop();

            b.push("squad");

            squadCommandRadius = b
                    .comment("How far the Illager Warhorn reaches when issuing an order.")
                    .defineInRange("commandRadius", 48.0D, 8.0D, 256.0D);

            squadHoldScanRadius = b
                    .comment("How far a recruit on Hold looks for threats around its hold position.")
                    .defineInRange("holdScanRadius", 14.0D, 2.0D, 128.0D);

            squadHoldLeashRadius = b
                    .comment("How far a recruit on Hold may stray before returning to its hold position.")
                    .defineInRange("holdLeashRadius", 20.0D, 2.0D, 128.0D);

            squadHoldWanderRadius = b
                    .comment("How far a recruit on Hold wanders while idle.")
                    .defineInRange("holdWanderRadius", 6.0D, 0.0D, 64.0D);

            squadAttackOrderChaseRadius = b
                    .comment("How far a recruit chases the target of an explicit Attack order.")
                    .defineInRange("attackOrderChaseRadius", 48.0D, 8.0D, 256.0D);

            squadAttackOrderDurationTicks = b
                    .comment("How long an explicit Attack order lasts. 400 ticks = 20 seconds.")
                    .defineInRange("attackOrderDurationTicks", 400, 20, 20 * 60 * 10);

            squadFollowTeleportDistance = b
                    .comment(
                            "Distance at which a following recruit teleports to its owner instead of walking.",
                            "Lower values make the squad stick closer but teleport more visibly."
                    )
                    .defineInRange("followTeleportDistance", 40.0D, 8.0D, 128.0D);

            squadSlotScanRadius = b
                    .comment(
                            "Radius around the owner searched when counting recruits against the slot limit.",
                            "Recruits farther away than this stop consuming slots."
                    )
                    .defineInRange("slotScanRadius", 160.0D, 16.0D, 512.0D);

            squadSupportHealRadius = b
                    .comment("Range within which recruited healers throw healing potions at allies.")
                    .defineInRange("supportHealRadius", 10.0D, 2.0D, 64.0D);

            squadSupportHealCooldownTicks = b
                    .comment("Cooldown between healing potions thrown by a recruited healer.")
                    .defineInRange("supportHealCooldownTicks", 120, 20, 20 * 60 * 5);

            b.pop();

            b.push("compat");
            b.comment(
                    "Per-mod switches for Raidborn's optional integrations.",
                    "Turning one off makes Raidborn behave as if that mod were not installed:",
                    "its creatures stop being recruitable, tradeable and settlement-eligible.",
                    "A switch has no effect when the mod is absent."
            );

            compatIllagerInvasion = b.define("illagerInvasion", true);
            compatSavageAndRavage = b.define("savageAndRavage", true);
            compatTakesAPillage = b.define("takesAPillage", true);
            compatGuardIllagers = b.define("guardIllagers", true);
            compatGuardVillagers = b.define("guardVillagers", true);
            compatHuntersReturn = b.define("huntersReturn", true);
            compatConjurerIllager = b.define("conjurerIllager", true);
            compatEnchantWithMob = b.define("enchantWithMob", true);
            compatRavageAndCabbage = b.define("ravageAndCabbage", true);
            compatArtifacts = b.define("artifacts", true);

            b.pop();
        }

        private static boolean isValidRL(String s) {
            try {
                ResourceLocation.parse(s);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    public static void bake() {
        TRADES_DISABLED_CACHE.clear();
        RECRUIT_DISABLED_CACHE.clear();

        for (String id : VALUES.tradesDisabledFor.get()) {
            TRADES_DISABLED_CACHE.add(id);
        }

        for (String id : VALUES.recruitmentDisabledFor.get()) {
            RECRUIT_DISABLED_CACHE.add(id);
        }
    }

    public static boolean isJuggernautNaturalSpawnEnabled() {
        return VALUES.juggernautNaturalSpawnEnabled.get();
    }

    public static double getJuggernautNaturalSpawnChance() {
        return VALUES.juggernautNaturalSpawnChance.get();
    }

    public static int getJuggernautMinVillagers() {
        return VALUES.juggernautMinVillagers.get();
    }

    public static int getJuggernautVillageScanRadius() {
        return VALUES.juggernautVillageScanRadius.get();
    }

    public static long getJuggernautReplacementDelayDays() {
        return VALUES.juggernautReplacementDelayDays.get();
    }

    public static double getJuggernautReplacementDailyChance() {
        return VALUES.juggernautReplacementDailyChance.get();
    }

    public static boolean isJuggernautRaidRewardEnabled() {
        return VALUES.juggernautRaidRewardEnabled.get();
    }

    public static int getJuggernautRaidRewardMinBadOmen() {
        return VALUES.juggernautRaidRewardMinBadOmen.get();
    }

    public static int getJuggernautRaidRewardMinDelayTicks() {
        return VALUES.juggernautRaidRewardMinDelayTicks.get();
    }

    /** Never below the minimum: a max under it would break the delay roll. */
    public static int getJuggernautRaidRewardMaxDelayTicks() {
        return Math.max(
                VALUES.juggernautRaidRewardMaxDelayTicks.get(),
                VALUES.juggernautRaidRewardMinDelayTicks.get()
        );
    }

    public static double getSquadCommandRadius() {
        return VALUES.squadCommandRadius.get();
    }

    public static double getSquadHoldScanRadius() {
        return VALUES.squadHoldScanRadius.get();
    }

    public static double getSquadHoldLeashRadius() {
        return VALUES.squadHoldLeashRadius.get();
    }

    public static double getSquadHoldWanderRadius() {
        return VALUES.squadHoldWanderRadius.get();
    }

    public static double getSquadAttackOrderChaseRadius() {
        return VALUES.squadAttackOrderChaseRadius.get();
    }

    public static long getSquadAttackOrderDurationTicks() {
        return VALUES.squadAttackOrderDurationTicks.get();
    }

    public static double getSquadFollowTeleportDistance() {
        return VALUES.squadFollowTeleportDistance.get();
    }

    public static double getSquadSlotScanRadius() {
        return VALUES.squadSlotScanRadius.get();
    }

    public static double getSquadSupportHealRadius() {
        return VALUES.squadSupportHealRadius.get();
    }

    public static int getSquadSupportHealCooldownTicks() {
        return VALUES.squadSupportHealCooldownTicks.get();
    }

    public static boolean isCompatIllagerInvasionEnabled() {
        return VALUES.compatIllagerInvasion.get();
    }

    public static boolean isCompatSavageAndRavageEnabled() {
        return VALUES.compatSavageAndRavage.get();
    }

    public static boolean isCompatTakesAPillageEnabled() {
        return VALUES.compatTakesAPillage.get();
    }

    public static boolean isCompatGuardIllagersEnabled() {
        return VALUES.compatGuardIllagers.get();
    }

    public static boolean isCompatGuardVillagersEnabled() {
        return VALUES.compatGuardVillagers.get();
    }

    public static boolean isCompatHuntersReturnEnabled() {
        return VALUES.compatHuntersReturn.get();
    }

    public static boolean isCompatConjurerIllagerEnabled() {
        return VALUES.compatConjurerIllager.get();
    }

    public static boolean isCompatEnchantWithMobEnabled() {
        return VALUES.compatEnchantWithMob.get();
    }

    public static boolean isCompatRavageAndCabbageEnabled() {
        return VALUES.compatRavageAndCabbage.get();
    }

    public static boolean isCompatArtifactsEnabled() {
        return VALUES.compatArtifacts.get();
    }

    public static boolean isArtifactChestLootEnabled() {
        return VALUES.artifactChestLootEnabled.get();
    }

    public static double getArtifactLootChance() {
        return VALUES.artifactLootChance.get();
    }

    public static double getVillagerSoulDropChance() {
        return VALUES.villagerSoulDropChance.get();
    }

    public static boolean isTradesEnabledFor(ResourceLocation entityId) {
        if (!VALUES.tradesEnabledGlobal.get()) return false;
        if (entityId == null) return false;
        return !TRADES_DISABLED_CACHE.contains(entityId.toString());
    }

    public static boolean isRecruitmentEnabledFor(ResourceLocation entityId) {
        if (!VALUES.recruitmentEnabledGlobal.get()) return false;
        if (entityId == null) return false;
        return !RECRUIT_DISABLED_CACHE.contains(entityId.toString());
    }

    public static int getLoyaltyRecruitSlots() {
        return VALUES.loyaltyRecruitSlots.get();
    }

    public static int getHonorRecruitSlots() {
        return VALUES.honorRecruitSlots.get();
    }

    public static int getHeroRecruitSlots() {
        return VALUES.heroRecruitSlots.get();
    }

    public static boolean isSupportHealerAiEnabled() {
        return VALUES.supportHealerAiEnabled.get();
    }

    public static boolean isWitchRecruitable() {
        return VALUES.witchRecruitable.get();
    }

    public static boolean isIceologerRecruitable() {
        return VALUES.iceologerRecruitable.get();
    }

    public static boolean isTricksterRecruitable() {
        return VALUES.tricksterRecruitable.get();
    }

    public static boolean isArchivistRecruitable() {
        return VALUES.archivistRecruitable.get();
    }

    public static boolean isFirecallerRecruitable() {
        return VALUES.firecallerRecruitable.get();
    }

    public static boolean isGrumblagerArmorEquippingEnabled() {
        return VALUES.grumblagerArmorEquippingEnabled.get();
    }

    public static int getSettlementDefaultRadius() {
        int max = getSettlementMaxRadius();
        return Math.min(max, VALUES.settlementDefaultRadius.get());
    }

    public static int getSettlementMaxRadius() {
        return VALUES.settlementMaxRadius.get();
    }

    public static int getTransmutationCraftTimeTicks() {
        return VALUES.transmutationCraftTimeTicks.get();
    }

    public static double getTotemHealingRadius() {
        return VALUES.totemHealingRadius.get();
    }

    public static double getTotemProtectionRadius() {
        return VALUES.totemProtectionRadius.get();
    }

    public static double getTotemResistanceRadius() {
        return VALUES.totemResistanceRadius.get();
    }
}