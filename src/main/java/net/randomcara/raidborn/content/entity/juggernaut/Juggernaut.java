package net.randomcara.raidborn.content.entity.juggernaut;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.randomcara.raidborn.content.entity.VillageSide;
import net.randomcara.raidborn.core.registry.ModTags;

import javax.annotation.Nullable;

/**
 * Heavy defensive Iron Golem variant.
 *
 * <p>Extends {@link IronGolem} on purpose: villagers and the Hero Attack scan, which looks for
 * {@code IronGolem.class}, then recognise it as a village defender with no extra code.
 */
public class Juggernaut extends IronGolem {
    public static final double DETECTION_RANGE = 16.0D;
    public static final double MAX_CHASE_RANGE = 32.0D;

    private static final double ATTACK_RANGE = 3.0D;
    private static final double ATTACK_ARC_DEGREES = 100.0D;
    private static final double ATTACK_MAX_VERTICAL_DIFFERENCE = 2.5D;

    private static final int ATTACK_WINDUP_TICKS = 10;
    private static final int ATTACK_ANIMATION_TICKS = 20;

    /** Damage scales off {@link Attributes#ATTACK_DAMAGE} so attribute modifiers still apply. */
    private static final float ATTACK_DAMAGE_MIN_FACTOR = 0.6F;
    private static final float ATTACK_DAMAGE_MAX_FACTOR = 1.4F;

    private static final int SLAM_COOLDOWN_TICKS = 60;

    private static final double MAIN_TARGET_LAUNCH = 0.45D;
    private static final double MAIN_TARGET_HORIZONTAL_KNOCKBACK = 0.15D;

    private static final int SHIELD_DISABLE_TICKS = 100;
    private static final int SHIELD_EXTRA_DURABILITY_DAMAGE = 6;

    private static final float REPAIR_INGOT_AMOUNT = 25.0F;
    private static final float REPAIR_BLOCK_AMOUNT = 100.0F;

    private static final float FALL_IMPACT_MIN_DISTANCE = 4.0F;

    /**
     * One entity event per swing, with the arm encoded in the id.
     *
     * <p>A {@code SynchedEntityData} counter would be resent every tick of the animation to every
     * tracking client. This mirrors vanilla {@code IronGolem.attackAnimationTick}: one packet per
     * swing, each side counting down on its own.
     *
     * <p>Trade-off: a client that starts tracking mid-swing misses the rest of the animation.
     */
    private static final byte EVENT_SWING_RIGHT = 4;
    private static final byte EVENT_SWING_LEFT = 5;
    private static final byte EVENT_SWING_BOTH = 6;

    public static final int HOME_RESTRICTION_RADIUS = 48;

    private static final String TAG_ORIGIN = "RaidbornJuggernautOrigin";
    private static final String TAG_HOME_BELL = "RaidbornJuggernautHomeBell";
    private static final String TAG_SLAM_COOLDOWN = "RaidbornJuggernautSlamCooldown";
    private static final String TAG_AGGRESSION = "RaidbornJuggernautAggression";

    private final JuggernautAggression aggression = new JuggernautAggression();

    private JuggernautOrigin origin = JuggernautOrigin.SPAWNED;

    @Nullable
    private BlockPos homeBellPos;

    @Nullable
    private LivingEntity pendingSwingTarget;

    private int swingWindupTicks;
    private int slamCooldownTicks;

    @Nullable
    private Vec3 explosionMovementToRestore;

    /** Counted down on both sides, started by the swing entity event. */
    private int attackAnimationTicks;
    private SwingArms swingArms = SwingArms.RIGHT;

    private boolean nextSwingUsesLeftArm;

    private boolean currentSwingIsSlam;

    public Juggernaut(EntityType<? extends Juggernaut> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
        // He walks along the bottom at normal speed, so water is not worth avoiding.
        // See isAffectedByFluids().
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_IRON_CLOSED, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, 20.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, MAX_CHASE_RANGE);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(false);
        navigation.setCanFloat(false);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new HeavyMeleeAttackGoal(this));
        this.goalSelector.addGoal(4, new MoveBackToVillageGoal(this, 0.6D, false));
        this.goalSelector.addGoal(5, new GolemRandomStrollInVillageGoal(this, 0.5D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new JuggernautHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                LivingEntity.class,
                10,
                false,
                false,
                this::isValidTarget
        ) {
            @Override
            protected double getFollowDistance() {
                return DETECTION_RANGE;
            }
        });
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnData,
            @Nullable CompoundTag dataTag
    ) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData, dataTag);

        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.MOB_SUMMONED) {
            this.setPlayerCreated(true);
        }

        return result;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            this.aggression.tick(this.level().getGameTime(), JuggernautVillageEvents.isDefensiveEventActive(this));
            this.tickSwing();

            if (this.slamCooldownTicks > 0) {
                this.slamCooldownTicks--;
            }

            LivingEntity target = this.getTarget();
            if (target != null && (!this.isValidTarget(target) || this.distanceToSqr(target) > MAX_CHASE_RANGE * MAX_CHASE_RANGE)) {
                this.setTarget(null);
            }
        }

        super.aiStep();

        if (this.attackAnimationTicks > 0) {
            this.attackAnimationTicks--;
        }
    }

    boolean isWindingUpSwing() {
        return this.swingWindupTicks > 0;
    }

    private void tickSwing() {
        if (this.swingWindupTicks <= 0) {
            return;
        }

        this.swingWindupTicks--;

        if (this.swingWindupTicks > 0) {
            return;
        }

        LivingEntity target = this.pendingSwingTarget;
        this.pendingSwingTarget = null;

        if (target != null) {
            this.performHeavyHit(target);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public int getExperienceReward() {
        return 15 + this.getRandom().nextInt(11);
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return this.origin.dropsLoot() ? super.getDefaultLootTable() : BuiltInLootTables.EMPTY;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living) || !this.isValidTarget(living)) {
            return false;
        }

        if (this.swingWindupTicks > 0) {
            return false;
        }

        this.beginHeavySwing(living);
        return true;
    }

    public void beginHeavySwing(LivingEntity target) {
        this.pendingSwingTarget = target;
        this.swingWindupTicks = ATTACK_WINDUP_TICKS;

        /*
         * The area slam is decided when the swing opens, not on impact: the animation must know from
         * the first frame whether one arm or both go up, which makes the two-arm wind-up a readable
         * tell for the player. The cooldown is reserved here as well.
         */
        this.currentSwingIsSlam = this.slamCooldownTicks <= 0;

        if (this.currentSwingIsSlam) {
            this.slamCooldownTicks = SLAM_COOLDOWN_TICKS;
        } else {
            this.nextSwingUsesLeftArm = !this.nextSwingUsesLeftArm;
        }

        this.swingArms = this.currentSwingIsSlam
                ? SwingArms.BOTH
                : (this.nextSwingUsesLeftArm ? SwingArms.LEFT : SwingArms.RIGHT);

        this.attackAnimationTicks = ATTACK_ANIMATION_TICKS;
        this.level().broadcastEntityEvent(this, swingEventId(this.swingArms));
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.6F);
    }

    private static byte swingEventId(SwingArms arms) {
        return switch (arms) {
            case LEFT -> EVENT_SWING_LEFT;
            case BOTH -> EVENT_SWING_BOTH;
            case RIGHT -> EVENT_SWING_RIGHT;
        };
    }

    private void performHeavyHit(LivingEntity target) {
        if (!target.isAlive() || !this.isAlive() || !this.canReachForAttack(target)) {
            return;
        }

        DamageSource source = this.damageSources().mobAttack(this);

        float base = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = base * (ATTACK_DAMAGE_MIN_FACTOR
                + this.getRandom().nextFloat() * (ATTACK_DAMAGE_MAX_FACTOR - ATTACK_DAMAGE_MIN_FACTOR));

        boolean blockedWithShield = target instanceof Player player
                && player.isBlocking()
                && player.isDamageSourceBlocked(source);

        boolean hurt = target.hurt(source, damage);

        if (blockedWithShield && target instanceof Player player) {
            this.breakShield(player);
        }

        if (!hurt && !blockedWithShield) {
            return;
        }

        if (hurt) {
            Vec3 away = target.position().subtract(this.position());
            double horizontalLength = away.horizontalDistance();
            double pushX = horizontalLength < 1.0E-4D ? 0.0D : (away.x / horizontalLength) * MAIN_TARGET_HORIZONTAL_KNOCKBACK;
            double pushZ = horizontalLength < 1.0E-4D ? 0.0D : (away.z / horizontalLength) * MAIN_TARGET_HORIZONTAL_KNOCKBACK;

            target.push(pushX, MAIN_TARGET_LAUNCH, pushZ);
            target.hurtMarked = true;

            this.doEnchantDamageEffects(this, target);
            this.setLastHurtMob(target);
        }

        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.8F);

        if (this.currentSwingIsSlam) {
            JuggernautImpact.slam(this, target);
        }
    }

    private void breakShield(Player player) {
        ItemStack useItem = player.getUseItem();

        if (!useItem.is(Items.SHIELD)) {
            return;
        }

        useItem.hurtAndBreak(
                SHIELD_EXTRA_DURABILITY_DAMAGE,
                player,
                broken -> broken.broadcastBreakEvent(player.getUsedItemHand())
        );

        player.getCooldowns().addCooldown(Items.SHIELD, SHIELD_DISABLE_TICKS);
        player.stopUsingItem();
        this.level().broadcastEntityEvent(player, (byte) 30);
    }

    public boolean canReachForAttack(LivingEntity target) {
        if (Math.abs(target.getY() - this.getY()) > ATTACK_MAX_VERTICAL_DIFFERENCE) {
            return false;
        }

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double reach = ATTACK_RANGE + (this.getBbWidth() + target.getBbWidth()) * 0.5D;

        if (horizontal > reach) {
            return false;
        }

        if (!this.hasLineOfSight(target)) {
            return false;
        }

        return this.isWithinAttackArc(target);
    }

    private boolean isWithinAttackArc(LivingEntity target) {
        Vec3 toTarget = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());

        if (toTarget.lengthSqr() < 1.0E-4D) {
            return true;
        }

        Vec3 look = this.getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);

        if (flatLook.lengthSqr() < 1.0E-4D) {
            return true;
        }

        double dot = flatLook.normalize().dot(toTarget.normalize());
        return dot >= Math.cos(Math.toRadians(ATTACK_ARC_DEGREES * 0.5D));
    }

    public enum SwingArms {
        RIGHT,
        LEFT,
        BOTH
    }

    public SwingArms getSwingArms() {
        return this.swingArms;
    }

    public int getAttackAnimationTicks() {
        return this.attackAnimationTicks;
    }

    public int getAttackAnimationLength() {
        return ATTACK_ANIMATION_TICKS;
    }

    @Override
    public void handleEntityEvent(byte id) {
        SwingArms arms = switch (id) {
            case EVENT_SWING_RIGHT -> SwingArms.RIGHT;
            case EVENT_SWING_LEFT -> SwingArms.LEFT;
            case EVENT_SWING_BOTH -> SwingArms.BOTH;
            default -> null;
        };

        if (arms == null) {
            super.handleEntityEvent(id);
            return;
        }

        this.swingArms = arms;
        this.attackAnimationTicks = ATTACK_ANIMATION_TICKS;
    }

    public boolean isValidTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || candidate == this || !candidate.isAlive()) {
            return false;
        }

        // IronGollet extends IronGolem, so it is covered here.
        if (VillageSide.isDefender(candidate)) {
            return false;
        }

        // Creepers are excluded so they do not blow up the village along with the attacker.
        if (candidate instanceof Creeper) {
            return false;
        }

        if (candidate instanceof Player player) {
            return this.isPlayerConsideredEnemy(player);
        }

        if (candidate.getType().is(ModTags.EntityTypes.JUGGERNAUT_TARGETS)) {
            return true;
        }

        if (candidate instanceof Animal || candidate.isAlliedTo(this)) {
            return VillageSide.isAttackingVillage(candidate);
        }

        if (candidate instanceof Enemy) {
            return true;
        }

        return VillageSide.isAttackingVillage(candidate);
    }

    private boolean isPlayerConsideredEnemy(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }

        // During a Hero Attack the event owner is hostile to the defended objective regardless of
        // village reputation.
        if (JuggernautVillageEvents.isAttackEventEnemy(this, player)) {
            return true;
        }

        if (!this.aggression.holdsGrudgeAgainst(player.getUUID())) {
            return false;
        }

        // Hero of the Village only shields players who never hit the Juggernaut itself.
        return this.aggression.wasHitBy(player.getUUID()) || !player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    public void rememberAggression(LivingEntity aggressor, int durationTicks, boolean hitTheJuggernaut) {
        if (aggressor == this || !aggressor.isAlive()) {
            return;
        }

        if (aggressor instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }

        this.aggression.remember(
                aggressor.getUUID(),
                this.level().getGameTime() + durationTicks,
                hitTheJuggernaut,
                JuggernautVillageEvents.isDefensiveEventActive(this)
        );
    }

    public void rememberVillageAggression(LivingEntity aggressor, boolean villagerWasKilled) {
        this.rememberAggression(
                aggressor,
                villagerWasKilled ? JuggernautAggression.VILLAGER_KILLED_TICKS : JuggernautAggression.DEFAULT_TICKS,
                false
        );
    }

    /**
     * Undoes explosion knockback.
     *
     * <p>{@code Explosion.explode} adds velocity straight to deltaMovement right after calling
     * {@code hurt}, bypassing {@code KNOCKBACK_RESISTANCE}, and Forge exposes no hook there. Saving
     * the velocity before the damage and restoring it at the end of the tick is what keeps the 100%
     * knockback resistance valid against creepers and TNT as well.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isImmuneToDamageType(source)) {
            return false;
        }

        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        Vec3 movementBeforeExplosion = explosion ? this.getDeltaMovement() : null;

        boolean hurt = super.hurt(source, amount);

        if (movementBeforeExplosion != null) {
            this.explosionMovementToRestore = movementBeforeExplosion;
        }

        if (hurt && !this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            this.rememberAggression(attacker, JuggernautAggression.DEFAULT_TICKS, true);
        }

        return hurt;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.explosionMovementToRestore != null) {
            this.setDeltaMovement(this.explosionMovementToRestore);
            this.explosionMovementToRestore = null;
        }
    }

    /** Fall is absent on purpose: {@link #causeFallDamage} already returns false. */
    private boolean isImmuneToDamageType(DamageSource source) {
        return source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.SWEET_BERRY_BUSH);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect() == MobEffects.POISON
                || effect.getEffect() == MobEffects.HUNGER
                || effect.getEffect() == MobEffects.REGENERATION
                || effect.getEffect() == MobEffects.HEAL) {
            return false;
        }

        return super.canBeAffected(effect);
    }

    /** Potions and regeneration do not heal it; only iron repair goes through {@link #repair(float)}. */
    @Override
    public void heal(float amount) {
    }

    private void repair(float amount) {
        super.heal(amount);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /**
     * Opts out of vanilla fluid physics.
     *
     * <p>The water slowdown comes from no attribute: the aquatic branch of {@code LivingEntity.travel}
     * replaces walk acceleration with a fixed 0.02 and applies {@code getWaterSlowDown()} drag. This
     * keeps him on the land branch, walking across the bottom at normal speed.
     *
     * <p>Accepted side effect: the same applies to lava, where he sinks instead of floating. The lava
     * pathfinding malus stays negative, so he never walks in willingly.
     */
    @Override
    protected boolean isAffectedByFluids() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Solid hitbox: players and mobs cannot walk through the Juggernaut. */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (vehicle instanceof Boat || vehicle instanceof AbstractMinecart) {
            return false;
        }

        return super.startRiding(vehicle, force);
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        if (distance >= FALL_IMPACT_MIN_DISTANCE && !this.level().isClientSide) {
            JuggernautImpact.landing(this);
        }

        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        float healAmount;

        if (stack.is(Items.IRON_INGOT)) {
            healAmount = REPAIR_INGOT_AMOUNT;
        } else if (stack.is(Items.IRON_BLOCK)) {
            healAmount = REPAIR_BLOCK_AMOUNT;
        } else {
            return InteractionResult.PASS;
        }

        if (this.isPlayerConsideredEnemy(player)) {
            return InteractionResult.PASS;
        }

        if (this.getHealth() >= this.getMaxHealth()) {
            return InteractionResult.PASS;
        }

        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        this.repair(healAmount);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        boolean usedBlock = healAmount == REPAIR_BLOCK_AMOUNT;

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                usedBlock ? SoundEvents.IRON_GOLEM_REPAIR : SoundEvents.IRON_GOLEM_STEP,
                SoundSource.NEUTRAL,
                1.0F,
                usedBlock ? 0.8F : 1.1F
        );

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    usedBlock ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CRIT,
                    this.getX(),
                    this.getY(this.getBbHeight() * 0.6D),
                    this.getZ(),
                    usedBlock ? 40 : 14,
                    1.0D,
                    0.8D,
                    1.0D,
                    usedBlock ? 0.05D : 0.2D
            );
        }

        return InteractionResult.CONSUME;
    }

    public JuggernautOrigin getOrigin() {
        return this.origin;
    }

    public void setOrigin(JuggernautOrigin origin) {
        this.origin = origin;

        if (origin != JuggernautOrigin.SPAWNED) {
            this.setPlayerCreated(false);
            this.setPersistenceRequired();
        }
    }

    @Nullable
    public BlockPos getHomeBellPos() {
        return this.homeBellPos;
    }

    public void setHomeBellPos(@Nullable BlockPos pos) {
        this.homeBellPos = pos == null ? null : pos.immutable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_ORIGIN, this.origin.ordinal());

        if (this.homeBellPos != null) {
            tag.putLong(TAG_HOME_BELL, this.homeBellPos.asLong());
        }

        this.aggression.save(tag, TAG_AGGRESSION);
        tag.putInt(TAG_SLAM_COOLDOWN, this.slamCooldownTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.origin = JuggernautOrigin.byId(tag.getInt(TAG_ORIGIN));
        this.homeBellPos = tag.contains(TAG_HOME_BELL) ? BlockPos.of(tag.getLong(TAG_HOME_BELL)) : null;

        this.aggression.load(tag, TAG_AGGRESSION);

        this.pendingSwingTarget = null;
        this.swingWindupTicks = 0;
        this.currentSwingIsSlam = false;
        this.slamCooldownTicks = tag.getInt(TAG_SLAM_COOLDOWN);

        // Vanilla does not save the Mob territory restriction, so the village link was lost on every
        // reload. The bell is persisted above, so it is enough to reapply it.
        if (this.homeBellPos != null) {
            this.restrictTo(this.homeBellPos, HOME_RESTRICTION_RADIUS);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.4F, 0.7F);
    }

    @Override
    protected float getSoundVolume() {
        return 1.4F;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F;
    }

}
