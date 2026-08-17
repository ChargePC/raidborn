package net.randomcara.raidborn.client.hud;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RaidbornGuiOverlays {

    private RaidbornGuiOverlays() {
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "artifact_slot", ArtifactHudOverlay.OVERLAY);
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "recruit_tooltip", RecruitTooltipOverlay.OVERLAY);
    }
}
