package net.randomcara.raidborn.core.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.transmutation.blockentity.TransmutationTableBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Raidborn.MOD_ID);

    public static final RegistryObject<BlockEntityType<TransmutationTableBlockEntity>> TRANSMUTATION_TABLE =
            BLOCK_ENTITIES.register("transmutation_table", () -> BlockEntityType.Builder.of(
                    TransmutationTableBlockEntity::new,
                    ModBlocks.TRANSMUTATION_TABLE.get()
            ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
