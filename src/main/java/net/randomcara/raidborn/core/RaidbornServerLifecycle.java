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

/**
 * Clears runtime state held in static fields.
 *
 * <p>A client process is not restarted between worlds: without this, leaving one world and opening
 * another carries positions, UUIDs and events over. The worst case was {@link SoggyRingItem},
 * whose pending-block map is keyed by dimension, and {@code minecraft:overworld} is the same key
 * in every world.
 */
@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class RaidbornServerLifecycle {
    private RaidbornServerLifecycle() {
    }

    /** Before the final save: ends the events that write marks onto entities. */
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
