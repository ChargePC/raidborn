package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.core.util.MobSleep;

public class RecruitTeleport {
    private static final int RING_MIN_RADIUS = 2;
    private static final int RING_MAX_RADIUS = 4;
    private static final int RING_VERTICAL_REACH = 1;
    private static final int SCATTER_ATTEMPTS = 16;
    private static final double SCATTER_MIN_RADIUS = 3.0D;
    private static final double SCATTER_SPREAD = 2.5D;

    public static boolean tryTeleportNearOwner(Mob mob, ServerPlayer owner) {
        if (mob == null || owner == null || mob.level().isClientSide || !mob.isAlive() || !owner.isAlive()) {
            return false;
        }

        if (mob.isPassenger() || mob.isVehicle()) return false;

        MobSleep.wake(mob);

        return teleportIntoRing(mob, owner) || scatterNearOwner(mob, owner);
    }

    private static boolean teleportIntoRing(Mob mob, ServerPlayer owner) {
        BlockPos ownerPos = owner.blockPosition();

        for (int radius = RING_MIN_RADIUS; radius <= RING_MAX_RADIUS; radius++) {
            for (int yOffset = -RING_VERTICAL_REACH; yOffset <= RING_VERTICAL_REACH; yOffset++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) continue;

                        BlockPos candidate = ownerPos.offset(xOffset, yOffset, zOffset);
                        if (!canTeleportRecruitTo(mob, candidate)) continue;

                        arrive(mob);
                        mob.teleportToWithTicket(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean scatterNearOwner(Mob mob, ServerPlayer owner) {
        for (int attempt = 0; attempt < SCATTER_ATTEMPTS; attempt++) {
            double angle = owner.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = SCATTER_MIN_RADIUS + owner.getRandom().nextDouble() * SCATTER_SPREAD;
            double x = owner.getX() + Math.cos(angle) * radius;
            double z = owner.getZ() + Math.sin(angle) * radius;
            if (mob.randomTeleport(x, owner.getY(), z, true)) {
                arrive(mob);
                return true;
            }
        }

        return false;
    }

    private static void arrive(Mob mob) {
        SquadOrders.clearCombatState(mob);
    }

    static boolean canTeleportRecruitTo(Mob mob, BlockPos pos) {
        Level level = mob.level();
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;

        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP)) return false;

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!feet.canBeReplaced() || !head.canBeReplaced()) return false;

        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        AABB movedBox = mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ());
        return level.noCollision(mob, movedBox);
    }
}
