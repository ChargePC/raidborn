package net.randomcara.raidborn.gameplay.recruit;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.player.Player;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.util.AvoidGoals;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillageDoorOpenGoal;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillageSleepGoal;
import net.randomcara.raidborn.gameplay.settlement.ai.WarbellVillageWanderGoal;

public class RecruitGoalInstaller {
    private static final int VILLAGE_DOOR_GOAL_PRIORITY = 1;
    private static final int VILLAGE_SLEEP_GOAL_PRIORITY = 2;
    private static final int SUPPORT_HEAL_GOAL_PRIORITY = 2;
    private static final int AVOID_PLAYER_GOAL_PRIORITY = 2;
    private static final int FOLLOW_GOAL_PRIORITY = 3;
    private static final int VILLAGE_GOAL_PRIORITY = 4;

    public static void install(Mob mob) {
        addGoalOnce(mob, FOLLOW_GOAL_PRIORITY, FollowOwnerGoal.class, () -> new FollowOwnerGoal(mob));
        addSupportHealerGoal(mob);

        if (RecruitmentEvents.supportsVillageMode(mob)) {
            addVillageGoals(mob);
        }

        replacePlayerAvoidance(mob);
    }

    public static void installForVillage(Mob mob) {
        addGoalOnce(mob, FOLLOW_GOAL_PRIORITY, FollowOwnerGoal.class, () -> new FollowOwnerGoal(mob));
        addVillageGoals(mob);
        replacePlayerAvoidance(mob);
    }

    private static void addGoalOnce(Mob mob, int priority, Class<? extends Goal> type, Supplier<Goal> factory) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal())) return;
        }

        mob.goalSelector.addGoal(priority, factory.get());
    }

    private static void addSupportHealerGoal(Mob mob) {
        if (!SupportHealerGoal.isSupportHealer(mob)) return;

        addGoalOnce(mob, SUPPORT_HEAL_GOAL_PRIORITY, SupportHealerGoal.RaidbornSupportHealerGoal.class,
                () -> new SupportHealerGoal.RaidbornSupportHealerGoal(mob));
    }

    private static void addVillageGoals(Mob mob) {
        addGoalOnce(mob, VILLAGE_GOAL_PRIORITY, WarbellVillageWanderGoal.class, () -> new WarbellVillageWanderGoal(mob));
        addGoalOnce(mob, VILLAGE_SLEEP_GOAL_PRIORITY, WarbellVillageSleepGoal.class, () -> new WarbellVillageSleepGoal(mob));
        addGoalOnce(mob, VILLAGE_DOOR_GOAL_PRIORITY, WarbellVillageDoorOpenGoal.class, () -> new WarbellVillageDoorOpenGoal(mob));
    }

    private static void replacePlayerAvoidance(Mob mob) {
        if (!(mob instanceof PathfinderMob pathfinderMob)) return;
        if (!fleesFromPlayers(pathfinderMob)) return;

        AvoidGoals.removeAvoidPlayerGoals(pathfinderMob);

        for (WrappedGoal wrapped : pathfinderMob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof RaidbornRecruitAvoid) return;
        }

        pathfinderMob.goalSelector.addGoal(AVOID_PLAYER_GOAL_PRIORITY, new RaidbornRecruitAvoid(pathfinderMob));
    }

    private static boolean fleesFromPlayers(Mob mob) {
        if (mob instanceof Evoker) return true;

        ResourceLocation id = RecruitmentEvents.getEntityId(mob);
        if (id == null) return false;

        return id.equals(RaidbornCompatEntities.SANDR_ICEOLOGER) || id.equals(RaidbornCompatEntities.SANDR_TRICKSTER) || id.equals(RaidbornCompatEntities.IINV_ARCHIVIST) || id.equals(RaidbornCompatEntities.IINV_FIRECALLER) || id.equals(RaidbornCompatEntities.EWM_ENCHANTER);
    }

    static class RaidbornRecruitAvoid extends AvoidEntityGoal<Player> {
        private final PathfinderMob mob;

        public RaidbornRecruitAvoid(PathfinderMob mob) {
            super(
                    mob,
                    Player.class,
                    8.0F,
                    0.6D,
                    1.0D,
                    living -> {
                        if (!(living instanceof Player player)) return false;
                        if (player instanceof ServerPlayer serverPlayer && RecruitmentEvents.ownerHasAllianceEffect(serverPlayer)) return false;

                        return !RecruitOwnership.isRecruited(mob);
                    }
            );
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (RecruitOwnership.isRecruited(this.mob)) return false;

            Player nearest = this.mob.level().getNearestPlayer(this.mob, 8.0D);
            if (nearest instanceof ServerPlayer player && RecruitmentEvents.ownerHasAllianceEffect(player)) return false;

            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (RecruitOwnership.isRecruited(this.mob)) return false;

            Player nearest = this.mob.level().getNearestPlayer(this.mob, 8.0D);
            if (nearest instanceof ServerPlayer player && RecruitmentEvents.ownerHasAllianceEffect(player)) return false;

            return super.canContinueToUse();
        }
    }
}
