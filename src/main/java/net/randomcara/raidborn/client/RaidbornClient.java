package net.randomcara.raidborn.client;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.renderer.layer.BannerBackLayer;
import net.randomcara.raidborn.Raidborn;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RaidbornClient {

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        PlayerRenderer defaultRenderer = event.getSkin("default");
        if (defaultRenderer != null) {
            defaultRenderer.addLayer(new BannerBackLayer(defaultRenderer));
        }

        PlayerRenderer slimRenderer = event.getSkin("slim");
        if (slimRenderer != null) {
            slimRenderer.addLayer(new BannerBackLayer(slimRenderer));
        }
    }
}