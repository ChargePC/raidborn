package net.randomcara.raidborn.content.entity.iron_gollet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.randomcara.raidborn.content.entity.VillageSide;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

public class IronGollet extends IronGolem {
    private static final EntityDataAccessor<Boolean> CARRYING_VILLAGER =
            SynchedEntityData.defineId(IronGollet.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> VILLAGE_LINKED =
            SynchedEntityData.defineId(IronGollet.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> ATTACK_ANIMATION_TICKS =
            SynchedEntityData.defineId(IronGollet.class, EntityDataSerializers.INT);

    private static final String TAG_CARRYING = "raidborn_iron_gollet_carrying";
    private static final String TAG_VILLAGE_LINKED = "raidborn_iron_gollet_village_linked";
    private static final String TAG_OWNER = "raidborn_iron_gollet_owner";

    private static final int OWNER_REGENERATION_DURATION_TICKS = 30;
    private static final int OWNER_REGENERATION_REFRESH_TICKS = 10;

    private static final double HEAL_SEARCH_XZ = 32.0D;
    private static final double HEAL_SEARCH_Y = 16.0D;

    private static final int HEALING_PRIORITY_REFRESH_TICKS = 10;

    private static final float FULL_HEALTH_EPSILON = 0.05F;

    private static final double ATTACK_LAUNCH_STRENGTH = 0.25D;

    private static final float VOICE_PITCH_MULTIPLIER = 1.30F;
    private static final float SOUND_VOLUME = 0.80F;
    private static final float STEP_SOUND_VOLUME = 0.50F;
    private static final float STEP_SOUND_PITCH = 1.30F;

    private static final float CARRY_SOUND_VOLUME = 0.70F;
    private static final float CARRY_CLAMP_PITCH = 1.40F;
    private static final float CARRY_RELEASE_PITCH = 1.20F;

    private static final float HEAL_DONE_VOLUME = 0.60F;
    private static final float HEAL_DONE_PITCH = 1.35F;

    /**
     * Added on top of the {@code Mob} melee reach, which derives from entity width. At 0.6 wide the
     * Gollet reached ~1.43 blocks against an Iron Golem's ~2.9, short enough to whiff at point blank.
     */
    private static final double ATTACK_RANGE_BONUS_SQR = 1.6D;

    private static final int MAX_CARRYING_TICKS = 140;
    private static final int FORCED_RELEASE_IGNORE_TICKS = 100;

    private static final UUID CARRYING_SPEED_MODIFIER_UUID =
            UUID.fromString("59a282a8-6ec0-42f0-a631-8b9b4f986fb2");

    private static final AttributeModifier CARRYING_SPEED_MODIFIER =
            new AttributeModifier(
                    CARRYING_SPEED_MODIFIER_UUID,
                    "Iron Gollet carrying villager speed penalty",
                    -0.12D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            );

    private UUID ignoredVillagerUuid;
    private int ignoredVillagerTicks;
    private int carryingVillagerTicks;

    /** Villager this Gollet is heading for. Read by neighbours so two do not chase the same one. */
    @Nullable
    private UUID healingTargetUuid;

    private UUID ownerUuid;
    private int ownerRegenerationRefreshTicks;

    private boolean healingPriority;
    private int healingPriorityRefreshTicks;

    public IronGollet(EntityType<? extends IronGollet> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnData,
            @Nullable CompoundTag dataTag
    ) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData, dataTag);

        if (spawnType == MobSpawnType.SPAWN_EGG && !this.isVillageLinked()) {
            this.setPlayerCreated(true);
        }

        return result;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 7.5D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CARRYING_VILLAGER, false);
        this.entityData.define(VILLAGE_LINKED, false);
        this.entityData.define(ATTACK_ANIMATION_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new HealHurtVillagerGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new MoveTowardsTargetGoal(this, 0.9D, 32.0F));
        this.goalSelector.addGoal(6, new MoveBackToVillageGoal(this, 0.6D, false));
        this.goalSelector.addGoal(7, new GolemRandomStrollInVillageGoal(this, 0.6D));
        this.goalSelector.addGoal(8, new OfferFlowerGoal(this));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new VillageAwareHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new SupportAwareDefendVillageGoal(this));

        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this,
                Mob.class,
                5,
                false,
                false,
                entity -> !this.hasHealingPriority()
                        && entity instanceof Enemy
                        && !(entity instanceof Creeper)
        ));

        // Closes the persistent anger the IronGolem base already carries: without these goals the
        // anger was ticked and saved to NBT but never became a target and never expired.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this,
                Player.class,
                10,
                true,
                false,
                entity -> !this.hasHealingPriority()
                        && this.isAngryAt(entity)
                        && this.canAttackThreat(entity)
        ));

        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    /** Vanilla village defence, minus the case where the Gollet is busy carrying a hurt villager. */
    private static class SupportAwareDefendVillageGoal extends DefendVillageTargetGoal {
        private final IronGollet gollet;

        SupportAwareDefendVillageGoal(IronGollet gollet) {
            super(gollet);
            this.gollet = gollet;
        }

        @Override
        public boolean canUse() {
            // cheap check first, super scans a 10 block radius
            return !this.gollet.hasHealingPriority() && super.canUse();
        }
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (--this.healingPriorityRefreshTicks <= 0) {
                this.healingPriorityRefreshTicks = HEALING_PRIORITY_REFRESH_TICKS;
                this.healingPriority = this.computeHealingPriority();
            }

            this.tickIgnoredVillager();
            this.tickCarriedPassengerLogic();
        }

        super.aiStep();

        int attackTicks = this.getAttackAnimationTicks();
        if (attackTicks > 0) {
            this.entityData.set(ATTACK_ANIMATION_TICKS, attackTicks - 1);
        }

        if (!this.level().isClientSide) {
            LivingEntity target = this.getTarget();
            if (target != null && !this.canAttackThreat(target)) {
                this.setTarget(null);
            }
        }
    }

    private void tickCarriedPassengerLogic() {
        Player carriedOwner = this.getCarriedOwner();

        if (carriedOwner != null) {
            this.carryingVillagerTicks = 0;

            if (!carriedOwner.isAlive()
                    || !this.isOwnedBy(carriedOwner)
                    || !this.isPlayerCreated()
                    || this.isVillageLinked()
                    || carriedOwner.isShiftKeyDown()) {
                carriedOwner.stopRiding();
                this.setCarryingVillager(false);
                this.ownerRegenerationRefreshTicks = 0;
                return;
            }

            this.setCarryingVillager(true);
            this.applyRegenerationToOwner(carriedOwner);
            return;
        }

        this.ownerRegenerationRefreshTicks = 0;
        this.tickCarriedVillager();
    }

    private void applyRegenerationToOwner(Player owner) {
        if (this.ownerRegenerationRefreshTicks > 0) {
            this.ownerRegenerationRefreshTicks--;
            return;
        }

        MobEffectInstance current = owner.getEffect(MobEffects.REGENERATION);
        if (current == null || current.getAmplifier() < 1 || current.getDuration() <= 15) {
            owner.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    OWNER_REGENERATION_DURATION_TICKS,
                    1,
                    false,
                    true,
                    true
            ));
        }

        this.ownerRegenerationRefreshTicks = OWNER_REGENERATION_REFRESH_TICKS;
    }

    private void tickCarriedVillager() {
        Villager carried = this.getCarriedVillager();

        if (carried == null) {
            this.carryingVillagerTicks = 0;

            if (this.isCarryingVillager()) {
                this.setCarryingVillager(false);
            }

            return;
        }

        this.setCarryingVillager(true);
        this.carryingVillagerTicks++;

        if (!carried.isAlive()) {
            this.releaseCarriedVillager(carried, ReleaseReason.INTERRUPTED);
            return;
        }

        if (this.isVillagerFullyHealed(carried)) {
            this.releaseCarriedVillager(carried, ReleaseReason.HEALED);
            return;
        }

        if (this.carryingVillagerTicks >= MAX_CARRYING_TICKS) {
            this.releaseCarriedVillager(carried, ReleaseReason.INTERRUPTED);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity livingTarget && !this.canAttackThreat(livingTarget)) {
            this.setTarget(null);
            return false;
        }

        this.entityData.set(ATTACK_ANIMATION_TICKS, 10);
        this.level().broadcastEntityEvent(this, (byte) 4);

        // Same curve as the Iron Golem but read from the attribute instead of repeating its numbers.
        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (int) attackDamage > 0
                ? attackDamage / 2.0F + this.getRandom().nextInt((int) attackDamage)
                : attackDamage;

        boolean hurt = target.hurt(this.damageSources().mobAttack(this), damage);

        if (hurt) {
            // Upward hit like the golem's, shorter. The horizontal push already comes from the default
            // hurt() knockback; adding another one doubled it.
            double resistance = target instanceof LivingEntity livingTarget
                    ? livingTarget.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    : 0.0D;

            double launch = ATTACK_LAUNCH_STRENGTH * Math.max(0.0D, 1.0D - resistance);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, launch, 0.0D));

            this.doEnchantDamageEffects(this, target);
        }

        // The golem plays the sound whether it connects or not: a blocked hit still rings metal.
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, this.getVoicePitch());

        return hurt;
    }

    @Override
    public double getMeleeAttackRangeSqr(LivingEntity entity) {
        return super.getMeleeAttackRangeSqr(entity) + ATTACK_RANGE_BONUS_SQR;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            this.entityData.set(ATTACK_ANIMATION_TICKS, 10);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Iron ingot repair comes before mounting. Without this the player-created Gollet was the only
        // golem in the game that could not be repaired: this override captured every right click.
        if (player.getItemInHand(hand).is(Items.IRON_INGOT) && this.getHealth() < this.getMaxHealth()) {
            return super.mobInteract(player, hand);
        }

        if (!this.isPlayerCreated() || this.isVillageLinked()) {
            return super.mobInteract(player, hand);
        }

        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // An older Gollet may have no owner saved; the first player to interact claims it.
        if (this.ownerUuid == null) {
            this.setOwner(player);
        }

        if (!this.isOwnedBy(player) || !this.getPassengers().isEmpty()) {
            return super.mobInteract(player, hand);
        }

        this.getNavigation().stop();
        this.setTarget(null);

        if (player.startRiding(this, true)) {
            this.setCarryingVillager(true);
            this.ownerRegenerationRefreshTicks = 0;
            this.applyRegenerationToOwner(player);
            return InteractionResult.CONSUME;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.8D;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (!this.getPassengers().isEmpty()) {
            return false;
        }

        if (passenger instanceof Villager) {
            return true;
        }

        return passenger instanceof Player player
                && this.isPlayerCreated()
                && !this.isVillageLinked()
                && this.isOwnedBy(player);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }

        double y = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
        moveFunction.accept(passenger, this.getX(), y, this.getZ());
    }

    /**
     * Only drops the passenger when the Gollet is actually gone. {@code remove} also fires for
     * {@code UNLOADED_TO_CHUNK} and {@code CHANGED_DIMENSION}, and dismounting on those was
     * dropping the carried villager every chunk unload.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!reason.shouldSave()) {
            Villager carried = this.getCarriedVillager();
            if (carried != null) {
                carried.removeEffect(MobEffects.REGENERATION);
            }

            this.healingTargetUuid = null;
            this.ejectPassengers();
        }

        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_CARRYING, this.isCarryingVillager());
        tag.putBoolean(TAG_VILLAGE_LINKED, this.isVillageLinked());

        if (this.ownerUuid != null) {
            tag.putUUID(TAG_OWNER, this.ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setCarryingVillager(tag.getBoolean(TAG_CARRYING));
        this.setVillageLinked(tag.getBoolean(TAG_VILLAGE_LINKED));
        this.ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.carryingVillagerTicks = 0;
        this.ownerRegenerationRefreshTicks = 0;
        this.healingTargetUuid = null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    /** Raises hurt and death pitch at once; multiplying preserves the vanilla random variation. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * VOICE_PITCH_MULTIPLIER;
    }

    @Override
    protected float getSoundVolume() {
        return SOUND_VOLUME;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, STEP_SOUND_VOLUME, STEP_SOUND_PITCH);
    }

    void playVillagerClampSound() {
        this.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, CARRY_SOUND_VOLUME, CARRY_CLAMP_PITCH);
    }

    private void playVillagerReleaseSound() {
        this.playSound(SoundEvents.IRON_TRAPDOOR_OPEN, CARRY_SOUND_VOLUME, CARRY_RELEASE_PITCH);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    public boolean isCarryingVillager() {
        return this.entityData.get(CARRYING_VILLAGER);
    }

    public void setCarryingVillager(boolean carrying) {
        this.entityData.set(CARRYING_VILLAGER, carrying);
        this.updateCarryingSpeedModifier(carrying);
        this.setSprinting(false);
    }

    public boolean isVillageLinked() {
        return this.entityData.get(VILLAGE_LINKED);
    }

    public void setVillageLinked(boolean villageLinked) {
        this.entityData.set(VILLAGE_LINKED, villageLinked);
    }

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
        this.setPlayerCreated(true);
        this.setVillageLinked(false);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    public int getAttackAnimationTicks() {
        return this.entityData.get(ATTACK_ANIMATION_TICKS);
    }

    /**
     * Cached: the answer costs two entity scans and is read by the target goal predicates, which run
     * once per evaluated candidate.
     */
    public boolean hasHealingPriority() {
        return this.healingPriority;
    }

    private boolean computeHealingPriority() {
        if (this.getCarriedOwner() != null) {
            return true;
        }

        Villager carried = this.getCarriedVillager();
        if (carried != null && carried.isAlive() && !this.isVillagerFullyHealed(carried)) {
            return true;
        }

        return this.findHurtVillager() != null;
    }

    public boolean canAttackThreat(LivingEntity threat) {
        if (threat == null || threat == this || !threat.isAlive()) {
            return false;
        }

        // creepers would level the village along with whoever they were sent after
        if (threat instanceof Creeper || VillageSide.isDefender(threat)) {
            return false;
        }

        if (threat instanceof Player player) {
            return this.isVillageLinked() && !player.isCreative() && !player.isSpectator();
        }

        if (threat instanceof Enemy) {
            return true;
        }

        // A passive animal only becomes a target while it is on someone from the village. Without this
        // a village-linked Gollet hunted cows, sheep and the player's pets.
        return VillageSide.isAttackingVillage(threat);
    }

    @Nullable
    public Player getCarriedOwner() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player && this.isOwnedBy(player)) {
                return player;
            }
        }

        return null;
    }

    @Nullable
    public Villager getCarriedVillager() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Villager villager) {
                return villager;
            }
        }

        return null;
    }

    /**
     * Nearest hurt villager no other Gollet is already going for.
     *
     * <p>No global registry, each Gollet just publishes its target in {@link #healingTargetUuid} and
     * the others read it off them. Two can still pick the same villager on the same tick; the
     * farther one gives up next tick.
     */
    @Nullable
    public Villager findHurtVillager() {
        if (this.getCarriedOwner() != null) {
            return null;
        }

        AABB box = this.getBoundingBox().inflate(HEAL_SEARCH_XZ, HEAL_SEARCH_Y, HEAL_SEARCH_XZ);
        Set<UUID> takenByOtherGollets = new HashSet<>();

        for (IronGollet other : this.level().getEntitiesOfClass(IronGollet.class, box, gollet -> gollet != this && gollet.isAlive())) {
            if (other.healingTargetUuid != null) {
                takenByOtherGollets.add(other.healingTargetUuid);
            }
        }

        return this.level().getEntitiesOfClass(
                        Villager.class,
                        box,
                        villager -> villager.isAlive()
                                && !villager.isPassenger()
                                && !this.isVillagerFullyHealed(villager)
                                && !this.isIgnoredVillager(villager)
                                && !takenByOtherGollets.contains(villager.getUUID())
                )
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    boolean isVillagerFullyHealed(Villager villager) {
        return !villager.isAlive() || villager.getHealth() >= villager.getMaxHealth() - FULL_HEALTH_EPSILON;
    }

    /** Why a carried villager is being put down. */
    enum ReleaseReason {
        /** Back to full health: top them up and ring the chime. */
        HEALED,

        /** Died, or took too long. The Gollet skips them for a while so it does not loop on one. */
        INTERRUPTED
    }

    void releaseCarriedVillager(Villager villager, ReleaseReason reason) {
        if (reason == ReleaseReason.HEALED) {
            if (villager.isAlive()) {
                villager.setHealth(villager.getMaxHealth());
            }
        } else {
            this.ignoreVillager(villager, FORCED_RELEASE_IGNORE_TICKS);
        }

        this.healingTargetUuid = null;
        villager.removeEffect(MobEffects.REGENERATION);
        villager.stopRiding();
        this.ejectPassengers();

        this.playVillagerReleaseSound();

        if (reason == ReleaseReason.HEALED) {
            this.playSound(SoundEvents.IRON_GOLEM_REPAIR, HEAL_DONE_VOLUME, HEAL_DONE_PITCH);
        }

        this.carryingVillagerTicks = 0;
        this.setCarryingVillager(false);
        this.getNavigation().stop();
    }

    /** Published so neighbouring Gollets skip this villager. Cleared when the goal stops. */
    void setHealingTarget(@Nullable Villager villager) {
        this.healingTargetUuid = villager == null ? null : villager.getUUID();
    }

    private void ignoreVillager(Villager villager, int ticks) {
        this.ignoredVillagerUuid = villager.getUUID();
        this.ignoredVillagerTicks = ticks;
    }

    boolean isIgnoredVillager(Villager villager) {
        return this.ignoredVillagerUuid != null
                && this.ignoredVillagerTicks > 0
                && this.ignoredVillagerUuid.equals(villager.getUUID());
    }

    private void tickIgnoredVillager() {
        if (this.ignoredVillagerTicks > 0) {
            this.ignoredVillagerTicks--;
        }

        if (this.ignoredVillagerTicks <= 0) {
            this.ignoredVillagerUuid = null;
            this.ignoredVillagerTicks = 0;
        }
    }

    private void updateCarryingSpeedModifier(boolean carrying) {
        AttributeInstance movementSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        AttributeModifier existing = movementSpeed.getModifier(CARRYING_SPEED_MODIFIER_UUID);

        if (carrying) {
            if (existing == null) {
                movementSpeed.addTransientModifier(CARRYING_SPEED_MODIFIER);
            }
        } else if (existing != null) {
            movementSpeed.removeModifier(CARRYING_SPEED_MODIFIER_UUID);
        }
    }

    private static class VillageAwareHurtByTargetGoal extends TargetGoal {
        private final IronGollet gollet;

        private VillageAwareHurtByTargetGoal(IronGollet gollet) {
            super(gollet, false);
            this.gollet = gollet;
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = this.gollet.getLastHurtByMob();
            return attacker != null && this.gollet.canAttackThreat(attacker);
        }

        @Override
        public void start() {
            LivingEntity attacker = this.gollet.getLastHurtByMob();
            if (attacker != null && this.gollet.canAttackThreat(attacker)) {
                this.gollet.setTarget(attacker);
            }

            super.start();
        }
    }

}