package net.randomcara.raidborn.gameplay.recruit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

import java.util.List;
import java.util.UUID;

public final class SupportHealerGoal {
    static final float SUPPORT_HEAL_MIN_MISSING_HEALTH = 1.0F;

    static double supportHealRadius() {
        return RaidbornServerConfig.getSquadSupportHealRadius();
    }

    static double supportHealRadiusSqr() {
        double radius = supportHealRadius();
        return radius * radius;
    }

    static int supportHealCooldownTicks() {
        return RaidbornServerConfig.getSquadSupportHealCooldownTicks();
    }

    static final String TAG_SUPPORT_HEAL_NEXT = "raidborn_support_heal_next";

    static boolean isSupportHealer(Mob mob) {
        if (mob instanceof Witch) return true;

        ResourceLocation id = RecruitmentEvents.getEntityId(mob);
        return id != null && id.equals(RaidbornCompatEntities.IINV_ALCHEMIST);
    }

    static boolean isValidSupportHealTarget(Mob healer, LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        if (target instanceof Player player && player.getAbilities().instabuild) return false;
        if (target.isInvertedHealAndHarm()) return false;
        if (target.getHealth() >= target.getMaxHealth() - SUPPORT_HEAL_MIN_MISSING_HEALTH) return false;
        if (healer.distanceToSqr(target) > supportHealRadiusSqr()) return false;

        return healer.hasLineOfSight(target);
    }

    static void throwSupportHealingPotion(Mob healer, LivingEntity target) {
        if (healer.level().isClientSide || target == null) return;

        ItemStack potionStack = PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.HEALING);
        ThrownPotion potion = new ThrownPotion(healer.level(), healer);
        potion.setItem(potionStack);

        double dx = target.getX() - healer.getX();
        double dy = target.getY() + target.getEyeHeight() - 1.1D - potion.getY();
        double dz = target.getZ() - healer.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        potion.shoot(dx, dy + horizontalDistance * 0.2D, dz, 0.75F, 8.0F);

        healer.level().addFreshEntity(potion);
        healer.swing(InteractionHand.MAIN_HAND);

        healer.level().playSound(
                null,
                healer.getX(),
                healer.getY(),
                healer.getZ(),
                SoundEvents.WITCH_THROW,
                SoundSource.HOSTILE,
                1.0F,
                0.8F + healer.getRandom().nextFloat() * 0.4F
        );
    }

    static class RaidbornSupportHealerGoal extends Goal {
        private final Mob mob;
        private LivingEntity healTarget;

        public RaidbornSupportHealerGoal(Mob mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (!RaidbornServerConfig.isSupportHealerAiEnabled()) return false;
            if (this.mob.level().isClientSide) return false;
            if (!isSupportHealer(this.mob)) return false;
            if (!RecruitOwnership.isRecruited(this.mob)) return false;
            if (WarbellVillageData.isVillageMode(this.mob)) return false;
            if (this.mob.isSleeping() || this.mob.isPassenger() || this.mob.isVehicle()) return false;
            if (SquadOrders.getOrder(this.mob) == SquadOrder.ATTACK) return false;
            if (this.mob.getTarget() != null) return false;

            long gameTime = this.mob.level().getGameTime();
            long nextHealTime = this.mob.getPersistentData().getLong(TAG_SUPPORT_HEAL_NEXT);
            if (gameTime < nextHealTime) return false;

            ServerPlayer owner = getOwner();
            if (owner == null || !owner.isAlive() || !RecruitmentEvents.ownerHasAllianceEffect(owner)) return false;

            this.healTarget = findBestHealTarget(owner);
            return this.healTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (this.healTarget != null && isValidSupportHealTarget(this.mob, this.healTarget)) {
                this.mob.getLookControl().setLookAt(this.healTarget, 30.0F, 30.0F);
                throwSupportHealingPotion(this.mob, this.healTarget);
            }

            this.mob.getPersistentData().putLong(
                    TAG_SUPPORT_HEAL_NEXT,
                    this.mob.level().getGameTime() + supportHealCooldownTicks() + this.mob.getRandom().nextInt(20)
            );

            this.healTarget = null;
        }

        @Override
        public void stop() {
            this.healTarget = null;
        }

        private ServerPlayer getOwner() {
            UUID ownerId = RecruitOwnership.getOwnerUUID(this.mob);
            if (ownerId == null) return null;

            Entity entity = this.mob.level().getPlayerByUUID(ownerId);
            return entity instanceof ServerPlayer player ? player : null;
        }

        private LivingEntity findBestHealTarget(ServerPlayer owner) {
            LivingEntity bestTarget = null;
            double bestScore = Double.MAX_VALUE;

            if (isValidSupportHealTarget(this.mob, owner)) {
                bestTarget = owner;
                bestScore = getHealScore(owner) - 0.25D;
            }

            List<Mob> allies = this.mob.level().getEntitiesOfClass(
                    Mob.class,
                    this.mob.getBoundingBox().inflate(supportHealRadius()),
                    ally -> ally != this.mob
                            && ally.isAlive()
                            && RecruitOwnership.isSameSquad(this.mob, ally)
                            && isValidSupportHealTarget(this.mob, ally)
            );

            for (Mob ally : allies) {
                double score = getHealScore(ally);

                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = ally;
                }
            }

            return bestTarget;
        }

        private double getHealScore(LivingEntity entity) {
            double maxHealth = Math.max(1.0D, entity.getMaxHealth());
            double healthRatio = entity.getHealth() / maxHealth;
            double distancePenalty = this.mob.distanceToSqr(entity) / supportHealRadiusSqr() * 0.10D;

            return healthRatio + distancePenalty;
        }
    }

    private SupportHealerGoal() {
    }
}
