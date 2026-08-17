package net.randomcara.raidborn.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.randomcara.raidborn.Raidborn;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> BEAST_FRAME_BLOCKS =
                BlockTags.create(ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "beast_frame_blocks"));
    }

    public static class EntityTypes {
        /** Extra Juggernaut targets, configurable by datapack. */
        public static final TagKey<net.minecraft.world.entity.EntityType<?>> JUGGERNAUT_TARGETS =
                TagKey.create(
                        Registries.ENTITY_TYPE,
                        ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "juggernaut_targets")
                );

        /** What a village treats as an illager invader during a Hero Attack. */
        public static final TagKey<net.minecraft.world.entity.EntityType<?>> ILLAGER_THREATS =
                TagKey.create(
                        Registries.ENTITY_TYPE,
                        ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "illager_threats")
                );
    }
}
