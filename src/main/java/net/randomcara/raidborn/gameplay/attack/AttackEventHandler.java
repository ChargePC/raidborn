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
public class AttackEventHandler {
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
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        int interval = Math.max(1, RaidbornServerConfig.ATTACK_CHECK_INTERVAL_TICKS.get());
        if (player.tickCount % interval != 0) {
            return;
        }

        AttackManager.tryStartAttack(player);
    }

    @SubscribeEvent
    public static void onVillagerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            AttackManager.onVillagerLost(villager.getUUID());
        }
    }

    @SubscribeEvent
    public static void onVillagerConverted(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            AttackManager.onVillagerLost(villager.getUUID());
        }
    }
}
