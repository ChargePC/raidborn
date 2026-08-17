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
 * Wipes runtime state kept in static fields.
 *
 * <p>The client process doesn't restart between worlds, so without this you leave one world and
 * open another with the old positions, UUIDs and events still in memory. Worst offender was
 * {@link SoggyRingItem}: its pending-block map is keyed by dimension and
 * {@code minecraft:overworld} is the same key in every single world.
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
