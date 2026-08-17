package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;
import net.randomcara.raidborn.gameplay.recruit.RecruitTeleport;
import net.randomcara.raidborn.gameplay.recruit.SquadOrder;
import net.randomcara.raidborn.gameplay.recruit.SquadOrders;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.List;

/**
 * Pulls the owner's patrol back together when an Attack kicks off.
 *
 * <p>{@link FollowOwnerGoal} won't teleport a recruit until it's 40 blocks out, and
 * {@code AttackIllagerAIHandler} only registers allies that are already inside the village area.
 * Result was stragglers turning up halfway through the fight, or not at all.
 */
public final class AttackRecruitRally {
    private static final double ALREADY_CLOSE_DIST_SQR = 10.0D * 10.0D;

    private AttackRecruitRally() {
    }

    public static int rallyRecruitsToOwner(ServerLevel level, ServerPlayer owner) {
        if (!RaidbornServerConfig.ATTACK_RALLY_RECRUITS_ON_START.get()) {
            return 0;
        }

        double scanRadius = RaidbornServerConfig.ATTACK_RALLY_RADIUS.get();
        AABB scanBox = owner.getBoundingBox().inflate(scanRadius);

        List<Mob> recruits = level.getEntitiesOfClass(
                Mob.class,
                scanBox,
                mob -> mob.isAlive() && RecruitOwnership.isYours(owner, mob)
        );

        int rallied = 0;

        for (Mob recruit : recruits) {
            // Recruits in Settlement Mode belong to their village, not to the combat patrol.
            if (WarbellVillageData.isVillageMode(recruit)) {
                continue;
            }

            if (recruit.distanceToSqr(owner) <= ALREADY_CLOSE_DIST_SQR) {
                continue;
            }

            if (!RecruitTeleport.tryTeleportNearOwner(recruit, owner)) {
                continue;
            }

            // Without resetting the order a recruit on HOLD walks straight back to its old position.
            if (SquadOrders.getOrder(recruit) != SquadOrder.FOLLOW) {
                SquadOrders.resetToFollow(recruit);
            }

            rallied++;
        }

        return rallied;
    }
}
