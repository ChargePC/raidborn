package net.randomcara.raidborn.gameplay.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.gameplay.loot.EntityDropReplacer;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.compat.RaidbornCompat;

import java.util.List;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MimicArtifactLootEvents {
    private static final String ARTIFACTS_NAMESPACE = RaidbornCompat.ARTIFACTS;

    private static final ResourceLocation ARTIFACTS_MIMIC =
            ResourceLocation.fromNamespaceAndPath(ARTIFACTS_NAMESPACE, "mimic");
    private static final float RAIDBORN_REPLACEMENT_CHANCE = 0.35F;

    private static final List<ResourceLocation> RAIDBORN_ARTIFACTS = List.of(
            id("raidborn_necklace"),
            id("giga_emerald"),
            id("bloody_chalice"),
            id("evoker_idol"),
            id("ominous_relic"),
            id("poison_arrowhead"),
            id("temporal_relic"),
            id("spider_pendant"),
            id("experience_ring"),
            id("oath_ring"),
            id("soggy_ring"),
            id("sacred_sun"),
            id("lightfed_pill"),
            id("arcane_dice"),
            id("big_red_button"),
            id("voodoo_villager_doll"),
            id("anywhere_pillow"),
            id("totem_of_healing"),
            id("totem_of_protection"),
            id("totem_of_resistance")
    );

    private static final EntityDropReplacer REPLACER = new EntityDropReplacer(
            ARTIFACTS_MIMIC,
            ARTIFACTS_NAMESPACE,
            RAIDBORN_REPLACEMENT_CHANCE,
            RAIDBORN_ARTIFACTS,
            Raidborn.LOGGER
    );

    private MimicArtifactLootEvents() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        REPLACER.onLivingDrops(event);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, path);
    }
}
