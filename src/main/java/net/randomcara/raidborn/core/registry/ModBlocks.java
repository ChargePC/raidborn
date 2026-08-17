package net.randomcara.raidborn.core.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.BeastHeartBlock;
import net.randomcara.raidborn.gameplay.settlement.block.GrandWarbellBlock;
import net.randomcara.raidborn.transmutation.block.TransmutationTableBlock;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Raidborn.MOD_ID);

    public static final RegistryObject<Block> GRAND_WARBELL = BLOCKS.register("grand_warbell",
            () -> new GrandWarbellBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.ANVIL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> BEAST_HEART = BLOCKS.register("beast_heart",
            () -> new BeastHeartBlock(BlockBehaviour.Properties.copy(Blocks.MUD)));

    public static final RegistryObject<Block> TRANSMUTATION_TABLE = BLOCKS.register("transmutation_table",
            () -> new TransmutationTableBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
