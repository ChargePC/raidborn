package net.randomcara.raidborn.content.entity.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModBlocks;
import net.randomcara.raidborn.core.registry.ModEntities;
import net.randomcara.raidborn.core.registry.ModTags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class BeastSummonEvents {
    private BeastSummonEvents() {
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!canTriggerCheck(event.getPlacedBlock())) {
            return;
        }

        UUID creatorUUID = getCreatorUUID(event.getEntity());
        BlockPos origin = event.getPos();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-1, -2, -1),
                origin.offset(1, 1, 1)
        )) {
            if (trySpawnBeast(level, pos.immutable(), creatorUUID)) {
                return;
            }
        }
    }

    @Nullable
    private static UUID getCreatorUUID(@Nullable Entity placingEntity) {
        return placingEntity instanceof Player player
                ? player.getUUID()
                : null;
    }

    private static boolean canTriggerCheck(BlockState state) {
        return state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.CHEST)
                || state.is(ModBlocks.BEAST_HEART.get())
                || state.is(ModTags.Blocks.BEAST_FRAME_BLOCKS);
    }

    private static boolean trySpawnBeast(
            ServerLevel level,
            BlockPos heartPos,
            @Nullable UUID creatorUUID
    ) {
        BlockState heartState = level.getBlockState(heartPos);

        if (!isValidHeart(level, heartPos, heartState)) {
            return false;
        }

        ChestPattern pattern = findValidChestPattern(level, heartPos);

        if (pattern == null) {
            return false;
        }

        Beast beast = ModEntities.BEAST.get().create(level);

        if (beast == null) {
            return false;
        }

        clearStructure(
                level,
                heartPos,
                pattern.chestPos(),
                pattern.xShape()
        );

        // The chest is the Beast's back, so it spawns facing away from it.
        Direction beastFacing = pattern.chestDirection().getOpposite();

        beast.moveTo(
                heartPos.getX() + 0.5D,
                heartPos.getY() - 0.95D,
                heartPos.getZ() + 0.5D,
                beastFacing.toYRot(),
                0.0F
        );

        beast.setCreatorUUID(creatorUUID);
        beast.setPersistenceRequired();

        level.addFreshEntity(beast);
        spawnSummonParticles(level, heartPos);

        return true;
    }

    private static boolean isValidHeart(
            ServerLevel level,
            BlockPos heartPos,
            BlockState heartState
    ) {
        return heartState.is(ModBlocks.BEAST_HEART.get())
                && level.getBlockState(heartPos.above())
                .is(Blocks.CARVED_PUMPKIN);
    }

    // The chest may sit on any of the four sides of the Beast Heart; the arms run perpendicular to it.
    @Nullable
    private static ChestPattern findValidChestPattern(
            ServerLevel level,
            BlockPos heartPos
    ) {
        for (Direction chestDirection : Direction.Plane.HORIZONTAL) {
            BlockPos chestPos = heartPos.relative(chestDirection);

            if (!level.getBlockState(chestPos).is(Blocks.CHEST)) {
                continue;
            }

            if (chestDirection.getAxis() == Direction.Axis.Z
                    && hasXFrame(level, heartPos)) {
                return new ChestPattern(
                        chestPos,
                        chestDirection,
                        true
                );
            }

            if (chestDirection.getAxis() == Direction.Axis.X
                    && hasZFrame(level, heartPos)) {
                return new ChestPattern(
                        chestPos,
                        chestDirection,
                        false
                );
            }
        }

        return null;
    }

    private static boolean hasXFrame(
            ServerLevel level,
            BlockPos heartPos
    ) {
        return isFrame(level, heartPos.west())
                && isFrame(level, heartPos.east())
                && isFrame(level, heartPos.below());
    }

    private static boolean hasZFrame(
            ServerLevel level,
            BlockPos heartPos
    ) {
        return isFrame(level, heartPos.north())
                && isFrame(level, heartPos.south())
                && isFrame(level, heartPos.below());
    }

    private static boolean isFrame(
            ServerLevel level,
            BlockPos pos
    ) {
        return level.getBlockState(pos)
                .is(ModTags.Blocks.BEAST_FRAME_BLOCKS);
    }

    private static void clearStructure(
            ServerLevel level,
            BlockPos heartPos,
            BlockPos chestPos,
            boolean xShape
    ) {
        clearBlock(level, heartPos.above());
        clearBlock(level, heartPos);
        clearBlock(level, heartPos.below());

        clearChest(level, chestPos);

        if (xShape) {
            clearBlock(level, heartPos.west());
            clearBlock(level, heartPos.east());
        } else {
            clearBlock(level, heartPos.north());
            clearBlock(level, heartPos.south());
        }
    }

    private static void clearChest(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof Container container) {
            Containers.dropContents(level, pos, container);
        }

        clearBlock(level, pos);
    }

    private static void clearBlock(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);

        level.levelEvent(
                2001,
                pos,
                Block.getId(state)
        );

        level.setBlock(
                pos,
                Blocks.AIR.defaultBlockState(),
                3
        );
    }

    private static void spawnSummonParticles(
            ServerLevel level,
            BlockPos heartPos
    ) {
        level.sendParticles(
                ParticleTypes.POOF,
                heartPos.getX() + 0.5D,
                heartPos.getY() + 0.5D,
                heartPos.getZ() + 0.5D,
                30,
                0.4D,
                0.8D,
                0.4D,
                0.02D
        );
    }

    private record ChestPattern(
            BlockPos chestPos,
            Direction chestDirection,
            boolean xShape
    ) {
    }
}