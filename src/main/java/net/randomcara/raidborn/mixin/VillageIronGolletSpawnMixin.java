package net.randomcara.raidborn.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;
import net.randomcara.raidborn.core.registry.ModEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawns Iron Gollets when the vanilla Iron Golem village piece is placed during worldgen. */
@Mixin(SinglePoolElement.class)
public abstract class VillageIronGolletSpawnMixin {
    @Unique
    private static final ResourceLocation RAIDBORN$IRON_GOLEM_VILLAGE_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "village/common/iron_golem"
            );

    @Unique
    private static final int RAIDBORN$IRON_GOLLETS_PER_VILLAGE = 2;

    /**
     * Under Parchment the field is named "template". The Mixin annotation processor writes the
     * obfuscated name into raidborn.refmap.json for the shipped jar. The shadow must stay private
     * because the original field is private.
     */
    @Shadow
    @Final
    private Either<ResourceLocation, StructureTemplate> template;

    @Inject(
            method = "place",
            at = @At("RETURN")
    )
    private void raidborn$spawnVillageIronGollets(
            StructureTemplateManager structureTemplateManager,
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            BlockPos placementPos,
            BlockPos pivotPos,
            Rotation rotation,
            BoundingBox boundingBox,
            RandomSource random,
            boolean keepJigsaws,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!Boolean.TRUE.equals(callback.getReturnValue())) {
            return;
        }

        ResourceLocation templateId = this.template.left().orElse(null);
        if (!RAIDBORN$IRON_GOLEM_VILLAGE_TEMPLATE.equals(templateId)) {
            return;
        }

        for (int index = 0; index < RAIDBORN$IRON_GOLLETS_PER_VILLAGE; index++) {
            raidborn$spawnIronGollet(level, placementPos, random, index);
        }
    }

    @Unique
    private static void raidborn$spawnIronGollet(
            WorldGenLevel level,
            BlockPos origin,
            RandomSource random,
            int index
    ) {
        BlockPos spawnPos = raidborn$findSpawnPosition(level, origin, index);

        if (spawnPos == null) {
            return;
        }

        IronGollet gollet = ModEntities.IRON_GOLLET.get().create(level.getLevel());
        if (gollet == null) {
            return;
        }

        gollet.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                random.nextFloat() * 360.0F,
                0.0F
        );

        if (!level.noCollision(gollet)) {
            gollet.discard();
            return;
        }

        gollet.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.STRUCTURE,
                null,
                null
        );

        gollet.setVillageLinked(true);
        gollet.setPersistenceRequired();

        level.addFreshEntity(gollet);
    }

    /**
     * Keeps the two Gollets from overlapping, and wants solid ground under them.
     *
     * <p>Returns {@code null} if nothing fits. Used to fall back to the origin itself, which put
     * the Gollet inside a block or hovering.
     */
    @Unique
    @org.jetbrains.annotations.Nullable
    private static BlockPos raidborn$findSpawnPosition(
            WorldGenLevel level,
            BlockPos origin,
            int index
    ) {
        BlockPos[] candidates = index == 0
                ? new BlockPos[]{origin, origin.north(), origin.east(), origin.south(), origin.west()}
                : new BlockPos[]{origin.east(), origin.west(), origin.south(), origin.north(), origin};

        for (BlockPos candidate : candidates) {
            BlockPos below = candidate.below();

            boolean solidFloor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            boolean freeSpace = level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty();

            if (solidFloor && freeSpace) {
                return candidate;
            }
        }

        return null;
    }
}
