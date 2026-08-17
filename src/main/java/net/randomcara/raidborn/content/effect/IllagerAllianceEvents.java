package net.randomcara.raidborn.content.effect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.effect.IllagerAlliance.Betrayal;
import net.randomcara.raidborn.content.entity.VillageSide;
import net.randomcara.raidborn.core.compat.RaidbornCompatEntities;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.core.util.AvoidGoals;
import net.randomcara.raidborn.core.util.RaidbornAdvancements;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;

import javax.annotation.Nullable;

/**
 * The rules every {@link IllagerAlliance} shares.
 *
 * <p>All three pacts protect the player the same way, so the handlers run once over whichever pact
 * the player holds instead of once per pact. Anything specific to a single one is read off the
 * enum rather than branched on here.
 */
@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public final class IllagerAllianceEvents {
    private static final ResourceLocation ADV_FRIENDLY_FIRE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "friendly_fire");
    private static final String CRIT_FRIENDLY_FIRE = "lose_oath";

    /** How far around the player illagers get their target cleared. */
    private static final double PROTECTION_RADIUS = 32.0D;

    private static final int ALLIANCE_TARGET_PRIORITY = 2;
    private static final int ALLIANCE_TARGET_CHANCE = 10;

    private IllagerAllianceEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        applyBadOmenRules(player);

        if (!ModEffects.hasAllianceEffect(player)) return;

        for (Mob mob : player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(PROTECTION_RADIUS),
                mob -> mob.isAlive() && isUnrecruitedIllager(mob)
        )) {
            releaseProtectedTarget(mob);
        }
    }

    /** Suspends or blocks Bad Omen, and hands a suspended pact back once it wears off. */
    private static void applyBadOmenRules(Player player) {
        for (IllagerAlliance alliance : IllagerAlliance.all()) {
            boolean hasBadOmen = player.hasEffect(MobEffects.BAD_OMEN);

            switch (alliance.badOmenRule()) {
                case BLOCKS -> {
                    if (hasBadOmen && alliance.isOn(player)) {
                        player.removeEffect(MobEffects.BAD_OMEN);
                    }
                }
                case SUSPENDS -> {
                    CompoundTag data = player.getPersistentData();
                    String tag = alliance.suspendTag();

                    if (hasBadOmen) {
                        if (alliance.isOn(player)) {
                            data.putBoolean(tag, true);
                            alliance.revoke(player);
                        }
                    } else if (data.getBoolean(tag)) {
                        data.putBoolean(tag, false);
                        alliance.grant(player);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getEffectInstance() == null) return;

        MobEffect added = event.getEffectInstance().getEffect();
        IllagerAlliance gained = IllagerAlliance.forEffect(added);

        if (gained != null) {
            // The pacts are exclusive, and none of them coexists with the village's own reward.
            for (IllagerAlliance other : IllagerAlliance.all()) {
                if (other != gained) other.revoke(player);
            }

            player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
            return;
        }

        if (added == MobEffects.BAD_OMEN && blocksBadOmen(player)) {
            event.setCanceled(true);
            player.removeEffect(MobEffects.BAD_OMEN);
            return;
        }

        if (added == MobEffects.HERO_OF_THE_VILLAGE && ModEffects.hasAllianceEffect(player)) {
            event.setCanceled(true);
            player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        }
    }

    @SubscribeEvent
    public static void onPlayerHurtsIllager(LivingHurtEvent event) {
        breakPactIfBetrayed(event.getSource().getEntity(), event.getEntity(), Betrayal.ON_HURT);
    }

    @SubscribeEvent
    public static void onPlayerKillsIllager(LivingDeathEvent event) {
        breakPactIfBetrayed(event.getSource().getEntity(), event.getEntity(), Betrayal.ON_KILL);
    }

    private static void breakPactIfBetrayed(@Nullable Entity attacker, LivingEntity victim, Betrayal trigger) {
        if (!(attacker instanceof Player player)) return;

        IllagerAlliance alliance = IllagerAlliance.of(player);
        if (alliance == null || alliance.betrayal() != trigger) return;

        // Recruits count here on purpose: turning on your own squad is still turning on the pact.
        if (!VillageSide.isIllagerSide(victim)) return;

        alliance.revoke(player);

        if (player instanceof ServerPlayer serverPlayer) {
            RaidbornAdvancements.award(serverPlayer, ADV_FRIENDLY_FIRE, CRIT_FRIENDLY_FIRE);
        }
    }

    @SubscribeEvent
    public static void onProtectedPlayerHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob mob)) return;
        if (!isUnrecruitedIllager(mob)) return;
        if (!isProtected(event.getEntity())) return;

        releaseProtectedTarget(mob);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHostileTargetsPlayer(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!isUnrecruitedIllager(mob)) return;
        if (!isProtected(event.getNewTarget())) return;

        // Deliberately not clearing the target here: vanilla HurtByTargetGoal may still be using it,
        // and nulling it mid-event strands the goal. See RecruitTargeting#clearTargetKeepRevenge.
        // The player tick above does the safe cleanup a moment later.
        stopChasing(mob);
    }

    @SubscribeEvent
    public static void onInteractVillager(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager)) return;
        if (!ModEffects.hasAllianceEffect(event.getEntity())) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof IronGolem golem) {
            huntAlliedPlayers(golem);
        }

        if (!(entity instanceof Mob mob)) return;
        if (RecruitOwnership.isRecruited(mob)) return;

        // Casters normally keep their distance from players; an ally should not be fled from.
        if (RaidbornCompatEntities.fleesFromPlayers(mob)) {
            AvoidGoals.removeAvoidPlayerGoals(mob);
        }

        if (mob instanceof Vex vex && isProtected(vex.getTarget())) {
            vex.setTarget(null);
            vex.setLastHurtByMob(null);
        }

        if (RaidbornCompatEntities.guardVillagersLoaded()
                && RaidbornCompatEntities.GV_GUARD.equals(RaidbornCompatEntities.entityId(mob))) {
            huntAlliedPlayers(mob);
        }
    }

    /** Makes village defenders treat an allied player as a raider. */
    private static void huntAlliedPlayers(Mob defender) {
        defender.targetSelector.addGoal(ALLIANCE_TARGET_PRIORITY, new NearestAttackableTargetGoal<>(
                defender,
                Player.class,
                ALLIANCE_TARGET_CHANCE,
                true,
                false,
                target -> target instanceof Player player && ModEffects.hasAllianceEffect(player)
        ));
    }

    private static void releaseProtectedTarget(Mob mob) {
        if (!isProtected(mob.getTarget())) return;

        mob.setTarget(null);
        stopChasing(mob);
    }

    private static void stopChasing(Mob mob) {
        mob.getNavigation().stop();

        if (mob instanceof Monster monster) {
            monster.setAggressive(false);
        }
    }

    private static boolean blocksBadOmen(Player player) {
        for (IllagerAlliance alliance : IllagerAlliance.all()) {
            if (alliance.badOmenRule() == IllagerAlliance.BadOmenRule.BLOCKS && alliance.isOn(player)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isProtected(@Nullable LivingEntity target) {
        return target instanceof Player player && ModEffects.hasAllianceEffect(player);
    }

    private static boolean isUnrecruitedIllager(Entity entity) {
        return entity instanceof Mob mob
                && VillageSide.isIllagerSide(mob)
                && !RecruitOwnership.isRecruited(mob);
    }
}
