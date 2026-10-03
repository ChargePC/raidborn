package net.randomcara.raidborn.gameplay.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ArtifactChestLootModifier extends LootModifier {
    public static final Codec<ArtifactChestLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, ArtifactChestLootModifier::new));

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

    public ArtifactChestLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!TARGET_TABLES.contains(context.getQueriedLootTableId()) || !RaidbornServerConfig.isArtifactChestLootEnabled()) {
            return generatedLoot;
        }

        if (context.getRandom().nextFloat() < RaidbornServerConfig.getArtifactLootChance()) {
            generatedLoot.add(new ItemStack(ARTIFACTS.get(context.getRandom().nextInt(ARTIFACTS.size())).get()));
        }

        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
