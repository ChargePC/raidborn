package net.randomcara.raidborn.client;

import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.BeastModel;
import net.randomcara.raidborn.client.model.GrumblagerModel;
import net.randomcara.raidborn.client.model.IronGolletModel;
import net.randomcara.raidborn.client.model.JuggernautModel;
import net.randomcara.raidborn.client.renderer.BeastRenderer;
import net.randomcara.raidborn.client.renderer.GrumblagerRenderer;
import net.randomcara.raidborn.client.renderer.IronGolletRenderer;
import net.randomcara.raidborn.client.renderer.JuggernautRenderer;
import net.randomcara.raidborn.client.renderer.curio.BannerCurioRenderer;
import net.randomcara.raidborn.content.entity.beast.client.BeastInventoryScreen;
import net.randomcara.raidborn.core.registry.ModEntities;
import net.randomcara.raidborn.core.registry.ModMenuTypes;
import net.randomcara.raidborn.core.registry.ModRecipeTypes;
import net.randomcara.raidborn.transmutation.client.TransmutationTableScreen;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.TRANSMUTATION_TABLE_MENU.get(), TransmutationTableScreen::new);
            MenuScreens.register(ModMenuTypes.BEAST_INVENTORY_MENU.get(), BeastInventoryScreen::new);

            registerBannerCurioRenderer();
        });
    }

    private static void registerBannerCurioRenderer() {
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof BannerItem) {
                CuriosRendererRegistry.register(item, BannerCurioRenderer::new);
            }
        }
    }

    @SubscribeEvent
    public static void registerRecipeBookCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ModRecipeTypes.TRANSMUTATION.get(), recipe -> RecipeBookCategories.UNKNOWN);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GrumblagerModel.LAYER_LOCATION, GrumblagerModel::createBodyLayer);
        event.registerLayerDefinition(BeastModel.LAYER_LOCATION, BeastModel::createBodyLayer);
        event.registerLayerDefinition(IronGolletModel.LAYER_LOCATION, IronGolletModel::createBodyLayer);
        event.registerLayerDefinition(JuggernautModel.LAYER_LOCATION, JuggernautModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GRUMBLAGER.get(), GrumblagerRenderer::new);
        event.registerEntityRenderer(ModEntities.BEAST.get(), BeastRenderer::new);
        event.registerEntityRenderer(ModEntities.IRON_GOLLET.get(), IronGolletRenderer::new);
        event.registerEntityRenderer(ModEntities.JUGGERNAUT.get(), JuggernautRenderer::new);
    }
}
