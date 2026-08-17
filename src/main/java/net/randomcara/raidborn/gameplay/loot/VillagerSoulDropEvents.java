package net.randomcara.raidborn.gameplay.loot;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillagerSoulDropEvents {
    private static final float VILLAGER_SOUL_DROP_CHANCE = 0.40F;

    private VillagerSoulDropEvents() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getRandom().nextFloat() >= VILLAGER_SOUL_DROP_CHANCE) return;

        event.getDrops().add(new ItemEntity(
                villager.level(),
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                new ItemStack(ModItems.VILLAGER_SOUL.get())
        ));
    }
}
