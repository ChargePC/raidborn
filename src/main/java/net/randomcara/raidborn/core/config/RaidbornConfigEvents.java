package net.randomcara.raidborn.core.config;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.randomcara.raidborn.Raidborn;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RaidbornConfigEvents {
    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        bakeServerConfig(event);
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        bakeServerConfig(event);
    }

    private static void bakeServerConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == RaidbornServerConfig.SPEC) {
            RaidbornServerConfig.bake();
        }
    }
}
