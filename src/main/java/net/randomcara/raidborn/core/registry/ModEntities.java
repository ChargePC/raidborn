package net.randomcara.raidborn.core.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.Beast;
import net.randomcara.raidborn.content.entity.grumblager.Grumblager;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;
import net.randomcara.raidborn.content.entity.juggernaut.Juggernaut;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Raidborn.MOD_ID);

    public static final RegistryObject<EntityType<Grumblager>> GRUMBLAGER =
            ENTITY_TYPES.register("grumblager",
                    () -> EntityType.Builder.of(Grumblager::new, MobCategory.MONSTER)
                            .sized(0.6F, 0.975F)
                            .clientTrackingRange(8)
                            .build("grumblager"));

    public static final RegistryObject<EntityType<Beast>> BEAST =
            ENTITY_TYPES.register("beast",
                    () -> EntityType.Builder.of(Beast::new, MobCategory.MONSTER)
                            .sized(1.4F, 2.4F)
                            .clientTrackingRange(10)
                            .build("beast"));

    public static final RegistryObject<EntityType<IronGollet>> IRON_GOLLET =
            ENTITY_TYPES.register("iron_gollet",
                    () -> EntityType.Builder.of(IronGollet::new, MobCategory.MISC)
                            .sized(0.6F, 1.0F)
                            .clientTrackingRange(10)
                            .build("iron_gollet"));

    public static final RegistryObject<EntityType<Juggernaut>> JUGGERNAUT =
            ENTITY_TYPES.register("juggernaut",
                    () -> EntityType.Builder.of(Juggernaut::new, MobCategory.MISC)
                            .sized(2.9F, 3.0F)
                            .clientTrackingRange(12)
                            .build("juggernaut"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}