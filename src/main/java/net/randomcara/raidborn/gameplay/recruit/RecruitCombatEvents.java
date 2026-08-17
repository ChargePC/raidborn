package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageBedData;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class RecruitCombatEvents {
    @SubscribeEvent
    public static void onRecruitChangesTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        RecruitmentEvents.sanitizeRecruitState(mob);
        SettlementBridge.sanitizeVillageState(mob);

        if (WarbellVillageData.isVillageMode(mob)) {
            LivingEntity newTarget = event.getNewTarget();

            if (newTarget != null && SettlementBridge.isProtectedVillageTarget(mob, newTarget)) {
                // On the revenge path the target is left to the deferred cleanup; see
                // RecruitTargeting#isRevengeTargetChange. Outside it the change can be cancelled outright.
                if (!RecruitTargeting.isRevengeTargetChange(mob, newTarget)) {
                    event.setCanceled(true);
                }

                mob.getNavigation().stop();

                if (mob instanceof Monster monster) {
                    monster.setAggressive(false);
                }
            }

            return;
        }

        if (!RecruitOwnership.isRecruited(mob)) return;

        UUID ownerId = RecruitOwnership.getOwnerUUID(mob);
        if (ownerId == null) return;

        Entity ownerEntity = mob.level().getPlayerByUUID(ownerId);
        if (!(ownerEntity instanceof ServerPlayer owner) || !RecruitmentEvents.ownerHasAllianceEffect(owner)) {
            RecruitTargeting.clearFollowTarget(mob);
            return;
        }

        LivingEntity newTarget = event.getNewTarget();

        if (newTarget != null && RecruitmentEvents.isValidAttackTarget(owner, mob, newTarget) && !RecruitTargeting.isProtectedRecruitTarget(mob, newTarget)) {
            if (SquadOrders.getOrder(mob) == SquadOrder.FOLLOW
                    && owner.distanceToSqr(newTarget) <= RecruitTargeting.FOLLOW_TARGET_LEASH_RADIUS_SQR) {
                RecruitTargeting.rememberFollowTarget(mob, newTarget);
            }
            return;
        }

        if (newTarget != null && RecruitTargeting.isProtectedRecruitTarget(mob, newTarget)) {
            LivingEntity rememberedTarget = RecruitTargeting.getFollowTarget(owner, mob);

            if (rememberedTarget != null) {
                // setNewTarget instead of mob.setTarget(): calling setTarget here re-enters this event.
                event.setNewTarget(rememberedTarget);
                RecruitCombatMovement.applyCombatMovement(mob, rememberedTarget, 1.25D, 0.95D, 10.0D * 10.0D);
            } else if (!RecruitTargeting.isRevengeTargetChange(mob, newTarget)) {
                // Normal acquisition (NearestAttackableTargetGoal<Player>): cancelling keeps the previous
                // target, so nothing downstream sees null and no attack goal starts a wind-up.
                event.setCanceled(true);
                mob.getNavigation().stop();

                if (mob instanceof Monster monster) {
                    monster.setAggressive(false);
                }
            } else {
                // Revenge path: RecruitTickEvents clears it on the next tick.
                mob.getNavigation().stop();

                if (mob instanceof Monster monster) {
                    monster.setAggressive(false);
                }
            }
            return;
        }

        if (SquadOrders.getOrder(mob) == SquadOrder.FOLLOW) {
            LivingEntity rememberedTarget = RecruitTargeting.getFollowTarget(owner, mob);

            if (rememberedTarget != null) {
                event.setNewTarget(rememberedTarget);
                mob.setTarget(rememberedTarget);
                RecruitCombatMovement.applyCombatMovement(mob, rememberedTarget, 1.25D, 0.95D, 10.0D * 10.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Mob mob
                && WarbellVillageBedData.getWakeProtection(mob) > 0
                && event.getSource().getEntity() == null) {
            event.setCanceled(true);
            return;
        }

        Entity attackerEntity = event.getSource().getEntity();

        if (attackerEntity instanceof Mob attackerMob && RecruitOwnership.isRecruited(attackerMob)) {
            LivingEntity livingTarget = event.getEntity();

            if (RecruitTargeting.isProtectedRecruitTarget(attackerMob, livingTarget)) {
                RecruitTargeting.clearOwnerAsTarget(attackerMob);
                event.setCanceled(true);
                return;
            }
        }

        if (attackerEntity instanceof Mob attackerMob && WarbellVillageData.isVillageMode(attackerMob)) {
            LivingEntity livingTarget = event.getEntity();

            if (SettlementBridge.isProtectedVillageTarget(attackerMob, livingTarget)) {
                SettlementBridge.clearProtectedPlayerTarget(attackerMob);
                event.setCanceled(true);
                return;
            }
        }

        if (event.getEntity() instanceof Mob hurtMob
                && attackerEntity instanceof ServerPlayer playerAttacker
                && RecruitOwnership.isYours(playerAttacker, hurtMob)) {
            RecruitTargeting.clearOwnerAsTarget(hurtMob);
            RecruitTargeting.clearFollowTarget(hurtMob);
        }

        if (event.getEntity() instanceof Mob hurtVillageMob
                && attackerEntity instanceof ServerPlayer playerAttacker
                && WarbellVillageData.isVillageMode(hurtVillageMob)
                && SettlementBridge.isProtectedVillageTarget(hurtVillageMob, playerAttacker)) {
            SettlementBridge.clearProtectedPlayerTarget(hurtVillageMob);
            SquadOrders.clearCombatState(hurtVillageMob);
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide || !RecruitmentEvents.canCommandRecruits(player)) {
            return;
        }

        if (attackerEntity instanceof LivingEntity livingAttacker) {
            commandRecruitsAttack(player, livingAttacker);
        }
    }

    @SubscribeEvent
    public static void onPlayerHurtsSomeone(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player.level().isClientSide || !RecruitmentEvents.canCommandRecruits(player)) {
            return;
        }

        LivingEntity victim = event.getEntity();

        if (victim instanceof Mob mob && RecruitOwnership.isYours(player, mob)) {
            SquadOrders.clearCombatState(mob);
            RecruitTargeting.clearFollowTarget(mob);
            return;
        }

        if (victim instanceof Mob mob
                && WarbellVillageData.isVillageMode(mob)
                && SettlementBridge.isProtectedVillageTarget(mob, player)) {
            SettlementBridge.clearProtectedPlayerTarget(mob);
            SquadOrders.clearCombatState(mob);
            return;
        }

        commandRecruitsAttack(player, victim);
    }

    static void commandRecruitsAttack(ServerPlayer player, LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return;

        List<Mob> mobs = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RecruitmentEvents.COMMAND_RADIUS),
                mob -> RecruitOwnership.isYours(player, mob) && mob.isAlive() && RecruitmentEvents.isRecruitable(mob)
        );

        for (Mob mob : mobs) {
            RecruitmentEvents.sanitizeRecruitState(mob);

            if (!RecruitOwnership.isYours(player, mob)) continue;

            RecruitTargeting.clearOwnerAsTarget(mob);

            SquadOrder order = SquadOrders.getOrder(mob);
            if (order == SquadOrder.HOLD || order == SquadOrder.ATTACK) continue;

            if (!RecruitmentEvents.isValidAttackTarget(player, mob, target)) {
                SquadOrders.clearCombatState(mob);
                continue;
            }

            if (mob.isSleeping()) {
                WarbellVillageBedData.wakeUpAndStand(mob);
            }

            if (order == SquadOrder.FOLLOW) {
                RecruitTargeting.rememberFollowTarget(mob, target);
            }

            mob.setTarget(target);
            RecruitCombatMovement.applyCombatMovement(mob, target, 1.25D, 0.95D, 10.0D * 10.0D);
        }
    }

    private RecruitCombatEvents() {
    }
}
