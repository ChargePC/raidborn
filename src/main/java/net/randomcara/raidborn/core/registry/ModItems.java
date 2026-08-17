package net.randomcara.raidborn.core.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.artifact.item.*;
import net.randomcara.raidborn.content.item.essence.OverlordEssenceItem;
import net.randomcara.raidborn.content.item.essence.RaidbornEssenceItem;
import net.randomcara.raidborn.content.item.essence.SavageEssenceItem;
import net.randomcara.raidborn.content.item.utility.IllagerWarhornItem;
import net.randomcara.raidborn.content.item.utility.RaidBagItem;
import net.randomcara.raidborn.content.item.utility.VillageLootItem;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Raidborn.MOD_ID);

    public static final RegistryObject<Item> RAIDBORN_NECKLACE =
            ITEMS.register("raidborn_necklace", () -> new RaidbornNecklaceItem(singleStack()));

    public static final RegistryObject<Item> VILLAGER_SOUL =
            ITEMS.register("villager_soul", () -> new Item(stacksTo(16)));

    public static final RegistryObject<Item> VOODOO_VILLAGER_DOLL =
            ITEMS.register("voodoo_villager_doll", () -> new VoodooVillagerDollItem(singleStack()));

    public static final RegistryObject<Item> RAIDBORN_ESSENCE =
            ITEMS.register("raidborn_essence", () -> new RaidbornEssenceItem(singleStack()));

    public static final RegistryObject<Item> GIGA_EMERALD =
            ITEMS.register("giga_emerald", () -> new GigaEmeraldItem(singleStack()));

    public static final RegistryObject<Item> BLOODY_CHALICE =
            ITEMS.register("bloody_chalice", () -> new BloodyChaliceItem(singleStack()));

    public static final RegistryObject<Item> ARCANE_DICE =
            ITEMS.register("arcane_dice", () -> new ArcaneDiceItem(singleStack()));

    public static final RegistryObject<Item> TOTEM_OF_HEALING =
            ITEMS.register("totem_of_healing", () -> new TotemOfHealingItem(singleStack()));

    public static final RegistryObject<Item> TOTEM_OF_RESISTANCE =
            ITEMS.register("totem_of_resistance", () -> new TotemOfResistanceItem(singleStack()));

    public static final RegistryObject<Item> TOTEM_OF_PROTECTION =
            ITEMS.register("totem_of_protection", () -> new TotemOfProtectionItem(singleStack()));

    public static final RegistryObject<Item> EVOKER_IDOL =
            ITEMS.register("evoker_idol", () -> new EvokerIdolItem(singleStack()));

    public static final RegistryObject<Item> OMINOUS_RELIC =
            ITEMS.register("ominous_relic", () -> new OminousRelicItem(singleStack()));

    public static final RegistryObject<Item> POISON_ARROWHEAD =
            ITEMS.register("poison_arrowhead", () -> new PoisonArrowheadItem(singleStack()));

    public static final RegistryObject<Item> BIG_RED_BUTTON =
            ITEMS.register("big_red_button", () -> new BigRedButtonItem(singleStack()));

    public static final RegistryObject<Item> TEMPORAL_RELIC =
            ITEMS.register("temporal_relic", () -> new TemporalRelicItem(singleStack()));

    public static final RegistryObject<Item> SPIDER_PENDANT =
            ITEMS.register("spider_pendant", () -> new SpiderPendantItem(singleStack()));

    public static final RegistryObject<Item> LIGHTFED_PILL =
            ITEMS.register("lightfed_pill", () -> new LightfedPillItem(singleStack()));

    public static final RegistryObject<Item> SACRED_SUN =
            ITEMS.register("sacred_sun", () -> new SacredSunItem(singleStack()));

    public static final RegistryObject<Item> EXPERIENCE_RING =
            ITEMS.register("experience_ring", () -> new ExperienceRingItem(singleStack()));

    public static final RegistryObject<Item> SOGGY_RING =
            ITEMS.register("soggy_ring", () -> new SoggyRingItem(singleStack()));

    public static final RegistryObject<Item> OATH_RING =
            ITEMS.register("oath_ring", () -> new OathRingItem(singleStack()));

    public static final RegistryObject<Item> ANYWHERE_PILLOW =
            ITEMS.register("anywhere_pillow", () -> new AnywherePillowItem(singleStack()));

    public static final RegistryObject<Item> RAID_BAG =
            ITEMS.register("raid_bag", () -> new RaidBagItem(singleStack()));

    public static final RegistryObject<Item> OMINOUS_DAGGER =
            ITEMS.register("ominous_dagger", () -> new OminousDaggerItem(Tiers.IRON, new Item.Properties().durability(450)));

    public static final RegistryObject<Item> SAVAGE_ESSENCE =
            ITEMS.register("savage_essence", () -> new SavageEssenceItem(singleStack()));

    public static final RegistryObject<Item> OVERLORD_ESSENCE =
            ITEMS.register("overlord_essence", () -> new OverlordEssenceItem(singleStack()));

    public static final RegistryObject<Item> VILLAGE_SOUL =
            ITEMS.register("village_soul", () -> new Item(stacksTo(16)));

    public static final RegistryObject<Item> SOUL_OF_MANY_VILLAGERS =
            ITEMS.register("soul_of_many_villagers", () -> new Item(stacksTo(16)));

    public static final RegistryObject<Item> ILLAGER_WARHORN =
            ITEMS.register("illager_warhorn", () -> new IllagerWarhornItem(singleStack()));

    public static final RegistryObject<Item> ARTIFACT_FRAGMENT =
            ITEMS.register("artifact_fragment", () -> new Item(stacksTo(64)));

    public static final RegistryObject<Item> ARTIFACT_TEMPLATE =
            ITEMS.register("artifact_template", () -> new Item(stacksTo(16)));

    public static final RegistryObject<Item> VILLAGE_LOOT =
            ITEMS.register("village_loot", () -> new VillageLootItem(stacksTo(16)));

    public static final RegistryObject<Item> GRAND_WARBELL =
            ITEMS.register("grand_warbell", () -> new BlockItem(ModBlocks.GRAND_WARBELL.get(), new Item.Properties()));

    public static final RegistryObject<Item> BEAST_HEART =
            ITEMS.register("beast_heart", () -> new BlockItem(ModBlocks.BEAST_HEART.get(), new Item.Properties()));

    public static final RegistryObject<Item> TRANSMUTATION_TABLE =
            ITEMS.register("transmutation_table", () -> new BlockItem(ModBlocks.TRANSMUTATION_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> GRUMBLAGER_SPAWN_EGG =
            ITEMS.register("grumblager_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntities.GRUMBLAGER,
                    0x8e9393,
                    0x432228,
                    new Item.Properties()
            ));

    public static final RegistryObject<Item> BEAST_SPAWN_EGG =
            ITEMS.register("beast_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntities.BEAST,
                    0x605f5a,
                    0xbdd3d3,
                    new Item.Properties()
            ));

    public static final RegistryObject<Item> IRON_GOLLET_SPAWN_EGG =
            ITEMS.register("iron_gollet_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            ModEntities.IRON_GOLLET,
                            0xf1e3d4,
                            0xD44C3F,
                            new Item.Properties()
                    ));

    public static final RegistryObject<Item> JUGGERNAUT_SPAWN_EGG =
            ITEMS.register("juggernaut_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            ModEntities.JUGGERNAUT,
                            0xf1e3d4,
                            0x5a5048,
                            new Item.Properties()
                    ));

    private static Item.Properties singleStack() {
        return stacksTo(1);
    }

    private static Item.Properties stacksTo(int amount) {
        return new Item.Properties().stacksTo(amount);
    }
}
