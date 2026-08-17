package net.randomcara.raidborn.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.keybind.BentosLibKeyMappings;
import net.randomcara.bentoslib.network.ActivateCurioItemPacket;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.hud.RecruitTooltipOverlay;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaidbornClientForgeEvents {

    private RaidbornClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            RecruitTooltipOverlay.clear();
            return;
        }

        RecruitTooltipOverlay.clientTick(minecraft);

        if (minecraft.screen != null) {
            return;
        }

        while (BentosLibKeyMappings.ACTIVATE_CURIO_ITEM.consumeClick()) {
            Raidborn.CHANNEL.sendToServer(new ActivateCurioItemPacket());
        }
    }
}
