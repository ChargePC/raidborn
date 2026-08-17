package net.randomcara.raidborn.gameplay.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.gameplay.loot.ChestLootInjector;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModItems;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChestLootInjectorEvents {

    private static final Set<ResourceLocation> TARGET_TABLES = Set.of(
            BuiltInLootTables.PILLAGER_OUTPOST,
            BuiltInLootTables.JUNGLE_TEMPLE,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.STRONGHOLD_LIBRARY,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.END_CITY_TREASURE,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.BURIED_TREASURE,
            BuiltInLootTables.SHIPWRECK_TREASURE
    );

    private static final List<Supplier<? extends Item>> ARTIFACTS = List.of(
            ModItems.RAIDBORN_NECKLACE,
            ModItems.GIGA_EMERALD,
            ModItems.BLOODY_CHALICE,
            ModItems.EVOKER_IDOL,
            ModItems.OMINOUS_RELIC,
            ModItems.POISON_ARROWHEAD,
            ModItems.TEMPORAL_RELIC,
            ModItems.ARCANE_DICE,
            ModItems.BIG_RED_BUTTON,
            ModItems.VOODOO_VILLAGER_DOLL,
            ModItems.ANYWHERE_PILLOW,
            ModItems.TOTEM_OF_HEALING,
            ModItems.TOTEM_OF_PROTECTION,
            ModItems.TOTEM_OF_RESISTANCE,
            ModItems.SPIDER_PENDANT,
            ModItems.EXPERIENCE_RING,
            ModItems.SOGGY_RING,
            ModItems.OATH_RING,
            ModItems.LIGHTFED_PILL,
            ModItems.SACRED_SUN
    );

    private static final ChestLootInjector INJECTOR = new ChestLootInjector(
            TARGET_TABLES,
            ARTIFACTS,
            // The pool name shows up in the table built at runtime; keeping it stable avoids colliding
            // with packs that already reference it.
            "raidborn_artifact_injection",
            "Raidborn artifact",
            Raidborn.LOGGER,
            RaidbornServerConfig::isArtifactChestLootEnabled,
            true,
            RaidbornServerConfig::getArtifactLootChance,
            0.33D
    );

    private ChestLootInjectorEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLootTableLoad(LootTableLoadEvent event) {
        INJECTOR.onLootTableLoad(event);
    }
}
