package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class AttackEventHandler {
    private AttackEventHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        AttackManager.tick(server);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        int interval = Math.max(1, RaidbornServerConfig.ATTACK_CHECK_INTERVAL_TICKS.get());
        if (player.tickCount % interval != 0) {
            return;
        }

        AttackManager.tryStartAttack(player);
    }

    /**
     * Villager losses are only registered here. Counting "entity not found" as a death made the
     * Attack win itself whenever villagers on the far side of the village left simulation distance.
     */
    @SubscribeEvent
    public static void onVillagerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            AttackManager.onVillagerLost(villager.getUUID());
        }
    }

    /** A villager turning into a zombie fires no LivingDeathEvent: the entity is replaced. */
    @SubscribeEvent
    public static void onVillagerConverted(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            AttackManager.onVillagerLost(villager.getUUID());
        }
    }
}
