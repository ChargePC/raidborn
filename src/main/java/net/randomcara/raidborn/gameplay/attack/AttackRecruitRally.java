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

public class AttackRecruitRally {
    private static final double ALREADY_CLOSE_DIST_SQR = 10.0D * 10.0D;

    public static int rallyRecruitsToOwner(ServerLevel level, ServerPlayer owner) {
        if (!RaidbornServerConfig.ATTACK_RALLY_RECRUITS_ON_START.get()) {
            return 0;
        }

        double scanRadius = RaidbornServerConfig.ATTACK_RALLY_RADIUS.get();
        AABB scanBox = owner.getBoundingBox().inflate(scanRadius);
        List<Mob> recruits = level.getEntitiesOfClass(Mob.class, scanBox, mob -> mob.isAlive() && RecruitOwnership.isYours(owner, mob));

        int rallied = 0;

        for (Mob recruit : recruits) {
            if (WarbellVillageData.isVillageMode(recruit) || recruit.distanceToSqr(owner) <= ALREADY_CLOSE_DIST_SQR || !RecruitTeleport.tryTeleportNearOwner(recruit, owner)) {
                continue;
            }

            if (SquadOrders.getOrder(recruit) != SquadOrder.FOLLOW) {
                SquadOrders.resetToFollow(recruit);
            }

            rallied++;
        }

        return rallied;
    }
}
