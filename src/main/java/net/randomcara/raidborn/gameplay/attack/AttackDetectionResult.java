package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;

public record AttackDetectionResult(
        BlockPos center,
        int radius,
        List<Villager> villagers,
        List<BlockPos> poiPositions,
        List<IronGolem> existingDefenders
) {
    public AttackDetectionResult {
        villagers = List.copyOf(villagers);
        poiPositions = List.copyOf(poiPositions);
        existingDefenders = List.copyOf(existingDefenders);
    }
}
