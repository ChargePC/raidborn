package net.randomcara.raidborn.core;

import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.artifact.item.AnywherePillowItem;
import net.randomcara.raidborn.content.artifact.item.ExperienceRingItem;
import net.randomcara.raidborn.content.artifact.item.LightfedPillItem;
import net.randomcara.raidborn.content.artifact.item.OathRingItem;
import net.randomcara.raidborn.content.artifact.item.RaidbornNecklaceEffectEvents;
import net.randomcara.raidborn.content.artifact.item.SacredSunItem;
import net.randomcara.raidborn.content.artifact.item.SoggyRingItem;
import net.randomcara.raidborn.gameplay.attack.AttackManager;
import net.randomcara.raidborn.world.settlement.SettlementSpawnMarkerEvents;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class RaidbornServerLifecycle {
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AttackManager.shutdown(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AnywherePillowItem.clearServerState();
        ExperienceRingItem.clearServerState();
        LightfedPillItem.Events.clearServerState();
        OathRingItem.clearServerState();
        RaidbornNecklaceEffectEvents.clearServerState();
        SacredSunItem.clearServerState();
        SoggyRingItem.clearServerState();
        SettlementSpawnMarkerEvents.clearServerState();
    }
}
