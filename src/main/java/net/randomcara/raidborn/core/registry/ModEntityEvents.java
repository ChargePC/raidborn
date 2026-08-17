package net.randomcara.raidborn.core.registry;

import net.minecraft.world.entity.raid.Raid;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.Beast;
import net.randomcara.raidborn.content.entity.grumblager.Grumblager;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;
import net.randomcara.raidborn.content.entity.juggernaut.Juggernaut;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityEvents {
    public static Raid.RaiderType GRUMBLAGER_RAIDER_TYPE;
    public static Raid.RaiderType BEAST_RAIDER_TYPE;

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GRUMBLAGER.get(), Grumblager.createAttributes().build());
        event.put(ModEntities.BEAST.get(), Beast.createAttributes().build());
        event.put(ModEntities.IRON_GOLLET.get(), IronGollet.createAttributes().build());
        event.put(ModEntities.JUGGERNAUT.get(), Juggernaut.createAttributes().build());
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GRUMBLAGER_RAIDER_TYPE = Raid.RaiderType.create(
                    "grumblager",
                    ModEntities.GRUMBLAGER.get(),
                    new int[]{1, 1, 2, 2, 2, 3, 3, 3}
            );

            BEAST_RAIDER_TYPE = Raid.RaiderType.create(
                    "beast",
                    ModEntities.BEAST.get(),
                    new int[]{0, 0, 1, 0, 1, 0, 1, 2}
            );
        });
    }
}