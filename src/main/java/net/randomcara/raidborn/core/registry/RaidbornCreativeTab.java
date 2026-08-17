package net.randomcara.raidborn.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;

public class RaidbornCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Raidborn.MOD_ID);

    public static final RegistryObject<CreativeModeTab> RAIDBORN_TAB =
            CREATIVE_TABS.register("raidborn_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.raidborn"))
                    .icon(() -> ModItems.GIGA_EMERALD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        add(output,
                                ModItems.VILLAGER_SOUL,
                                ModItems.SOUL_OF_MANY_VILLAGERS,
                                ModItems.VILLAGE_SOUL,
                                ModItems.RAIDBORN_ESSENCE,
                                ModItems.SAVAGE_ESSENCE,
                                ModItems.OVERLORD_ESSENCE
                        );

                        add(output,
                                ModItems.RAIDBORN_NECKLACE,
                                ModItems.POISON_ARROWHEAD,
                                ModItems.SPIDER_PENDANT,
                                ModItems.EXPERIENCE_RING,
                                ModItems.SOGGY_RING,
                                ModItems.OATH_RING,
                                ModItems.GIGA_EMERALD,
                                ModItems.BLOODY_CHALICE,
                                ModItems.EVOKER_IDOL,
                                ModItems.OMINOUS_RELIC,
                                ModItems.TEMPORAL_RELIC,
                                ModItems.LIGHTFED_PILL,
                                ModItems.SACRED_SUN,
                                ModItems.ANYWHERE_PILLOW,
                                ModItems.ARCANE_DICE,
                                ModItems.BIG_RED_BUTTON,
                                ModItems.VOODOO_VILLAGER_DOLL,
                                ModItems.TOTEM_OF_HEALING,
                                ModItems.TOTEM_OF_PROTECTION,
                                ModItems.TOTEM_OF_RESISTANCE
                        );

                        add(output,
                                ModItems.ARTIFACT_FRAGMENT,
                                ModItems.ARTIFACT_TEMPLATE,
                                ModItems.RAID_BAG,
                                ModItems.ILLAGER_WARHORN,
                                ModItems.OMINOUS_DAGGER,
                                ModItems.GRAND_WARBELL,
                                ModItems.TRANSMUTATION_TABLE,
                                ModItems.BEAST_HEART,
                                ModItems.GRUMBLAGER_SPAWN_EGG,
                                ModItems.IRON_GOLLET_SPAWN_EGG,
                                ModItems.JUGGERNAUT_SPAWN_EGG,
                                ModItems.BEAST_SPAWN_EGG
                        );
                    }).build());

    @SafeVarargs
    private static void add(CreativeModeTab.Output output, RegistryObject<? extends Item>... items) {
        for (RegistryObject<? extends Item> item : items) {
            output.accept(item.get());
        }
    }
}
