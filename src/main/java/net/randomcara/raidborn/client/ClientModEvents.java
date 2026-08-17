package net.randomcara.raidborn.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.randomcara.bentoslib.client.render.area.AreaVisualClient;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.BeastModel;
import net.randomcara.raidborn.client.model.GrumblagerModel;
import net.randomcara.raidborn.client.model.IronGolletModel;
import net.randomcara.raidborn.client.model.JuggernautModel;
import net.randomcara.raidborn.client.renderer.BeastRenderer;
import net.randomcara.raidborn.client.renderer.GrumblagerRenderer;
import net.randomcara.raidborn.client.renderer.IronGolletRenderer;
import net.randomcara.raidborn.client.renderer.JuggernautRenderer;
import net.randomcara.raidborn.content.entity.beast.client.BeastInventoryScreen;
import net.randomcara.raidborn.core.registry.ModEntities;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.core.registry.ModMenuTypes;
import net.randomcara.raidborn.transmutation.client.TransmutationTableScreen;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    private static final float TOTEM_AREA_HALF_SIZE = 8.0F;
    private static final int TOTEM_AREA_DURATION_TICKS = 20 * 15;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.TRANSMUTATION_TABLE_MENU.get(), TransmutationTableScreen::new);
            MenuScreens.register(ModMenuTypes.BEAST_INVENTORY_MENU.get(), BeastInventoryScreen::new);

            registerTotemAreaVisuals();
        });
    }

    // per-item area color lives here because BentosLib knows nothing about items, it only holds the
    // registry we fill on init.
    private static void registerTotemAreaVisuals() {
        AreaVisualClient.register(
                ModItems.TOTEM_OF_HEALING.get(), 0xFFE31700, TOTEM_AREA_HALF_SIZE, TOTEM_AREA_DURATION_TICKS);
        AreaVisualClient.register(
                ModItems.TOTEM_OF_PROTECTION.get(), 0xFF66CEE6, TOTEM_AREA_HALF_SIZE, TOTEM_AREA_DURATION_TICKS);
        AreaVisualClient.register(
                ModItems.TOTEM_OF_RESISTANCE.get(), 0xFFF47800, TOTEM_AREA_HALF_SIZE, TOTEM_AREA_DURATION_TICKS);
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