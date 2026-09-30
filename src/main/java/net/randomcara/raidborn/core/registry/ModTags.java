package net.randomcara.raidborn.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.randomcara.raidborn.Raidborn;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> BEAST_FRAME_BLOCKS = BlockTags.create(Raidborn.id("beast_frame_blocks"));
    }

    public static class EntityTypes {
        public static final TagKey<EntityType<?>> JUGGERNAUT_TARGETS = TagKey.create(Registries.ENTITY_TYPE, Raidborn.id("juggernaut_targets"));

        public static final TagKey<EntityType<?>> ILLAGER_THREATS = TagKey.create(Registries.ENTITY_TYPE, Raidborn.id("illager_threats"));
    }
}
