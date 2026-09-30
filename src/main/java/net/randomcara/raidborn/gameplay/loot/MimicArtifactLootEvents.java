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
public class MimicArtifactLootEvents {
    private static final String ARTIFACTS_NAMESPACE = RaidbornCompat.ARTIFACTS;
    private static final ResourceLocation ARTIFACTS_MIMIC = ResourceLocation.fromNamespaceAndPath(ARTIFACTS_NAMESPACE, "mimic");
    private static final float RAIDBORN_REPLACEMENT_CHANCE = 0.35F;

    private static final List<ResourceLocation> RAIDBORN_ARTIFACTS = List.of(
            Raidborn.id("raidborn_necklace"),
            Raidborn.id("giga_emerald"),
            Raidborn.id("bloody_chalice"),
            Raidborn.id("evoker_idol"),
            Raidborn.id("ominous_relic"),
            Raidborn.id("poison_arrowhead"),
            Raidborn.id("temporal_relic"),
            Raidborn.id("spider_pendant"),
            Raidborn.id("experience_ring"),
            Raidborn.id("oath_ring"),
            Raidborn.id("soggy_ring"),
            Raidborn.id("sacred_sun"),
            Raidborn.id("lightfed_pill"),
            Raidborn.id("arcane_dice"),
            Raidborn.id("big_red_button"),
            Raidborn.id("voodoo_villager_doll"),
            Raidborn.id("anywhere_pillow"),
            Raidborn.id("totem_of_healing"),
            Raidborn.id("totem_of_protection"),
            Raidborn.id("totem_of_resistance")
    );

    private static final EntityDropReplacer REPLACER = new EntityDropReplacer(ARTIFACTS_MIMIC, ARTIFACTS_NAMESPACE, RAIDBORN_REPLACEMENT_CHANCE, RAIDBORN_ARTIFACTS, Raidborn.LOGGER);

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        REPLACER.onLivingDrops(event);
    }
}
