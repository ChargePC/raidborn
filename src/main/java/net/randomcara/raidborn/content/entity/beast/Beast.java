package net.randomcara.raidborn.content.entity.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class Beast extends AbstractIllager {
    private static final EntityDataAccessor<Integer> ATTACK_TICK =
            SynchedEntityData.defineId(Beast.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> INVENTORY_OPEN =
            SynchedEntityData.defineId(Beast.class, EntityDataSerializers.BOOLEAN);

    private static final int ATTACK_ANIMATION_TIME = 16;

    private static final double ATTACK_LAUNCH_STRENGTH = 0.4D;
    private static final String TAG_CREATOR = "Creator";
    private static final String TAG_INVENTORY = "BeastInventory";
    private static final String TAG_RAID_SUPPLIES_GENERATED = "RaidSuppliesGenerated";
    private static final int INVENTORY_SIZE = 15;
    private static final int COMBAT_MEMORY_TICKS = 100;
    private static final int RAID_LEASH_SEARCH_INTERVAL = 10;
    private static final int RAID_RELEASH_DELAY_TICKS = 60;
    private static final int COMBAT_NAVIGATION_REFRESH_INTERVAL = 10;
    private static final double RAID_LEASH_SEARCH_RADIUS = 48.0D;

    private final ItemStackHandler beastInventory = new ItemStackHandler(INVENTORY_SIZE);

    @Nullable
    private UUID creatorUUID;
    private int raidLeashSearchCooldown;
    private int navigationRefreshCooldown;
    private int inventoryOpeners;
    private boolean raidSuppliesGenerated;
    private float chestLidProgress;
    private float previousChestLidProgress;

    public Beast(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractIllager.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 8.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ATTACK_TICK, 0);
        this.entityData.define(INVENTORY_OPEN, false);
    }

    @Override
    protected void registerGoals() {
        // Raider.registerGoals() already installs FloatGoal at priority 0.
        super.registerGoals();

        this.goalSelector.addGoal(3, new BeastMeleeAttackGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Villager.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class));

        // Always hostile to golems and villagers, in or out of a raid. IronGollet is covered because
        // it extends IronGolem.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
    }

    private boolean isProtectedTarget(LivingEntity entity) {
        if (isRaidAlly(entity) || isCreator(entity) || isCurrentLeashHolder(entity)) {
            return true;
        }

        return entity instanceof Player player && ModEffects.hasAllianceEffect(player);
    }

    private boolean isRaidAlly(LivingEntity entity) {
        return entity != this
                && (entity instanceof Raider || entity.getMobType() == MobType.ILLAGER);
    }

    private boolean isCurrentLeashHolder(Entity entity) {
        return this.isLeashed() && this.getLeashHolder() == entity;
    }

    private boolean isPlayerControlled() {
        return this.creatorUUID != null || RecruitOwnership.isRecruited(this);
    }

    private boolean hasValidCombatTarget() {
        LivingEntity target = this.getTarget();
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && !isProtectedTarget(target);
    }

    public void setCreatorUUID(@Nullable UUID creatorUUID) {
        this.creatorUUID = creatorUUID;
    }

    @Nullable
    public UUID getCreatorUUID() {
        return this.creatorUUID;
    }

    public boolean isCreator(Entity entity) {
        if (this.creatorUUID != null && this.creatorUUID.equals(entity.getUUID())) {
            return true;
        }

        return RecruitOwnership.isRecruited(this)
                && entity.getUUID().equals(RecruitOwnership.getOwnerUUID(this));
    }

    public ItemStackHandler getBeastInventory() {
        return this.beastInventory;
    }

    public boolean isInCombat() {
        if (!this.isAlive()) {
            return true;
        }

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            return true;
        }

        LivingEntity lastAttacker = this.getLastHurtByMob();
        return lastAttacker != null
                && lastAttacker.isAlive()
                && this.tickCount - this.getLastHurtByMobTimestamp() < COMBAT_MEMORY_TICKS;
    }

    public boolean isInventoryOpen() {
        return this.entityData.get(INVENTORY_OPEN);
    }

    public float getChestLidProgress(float partialTick) {
        return Mth.lerp(partialTick, this.previousChestLidProgress, this.chestLidProgress);
    }

    public void startInventoryOpen(Player player) {
        if (this.level().isClientSide || !isCreator(player) || isInCombat()) {
            return;
        }

        this.inventoryOpeners++;
        if (this.inventoryOpeners == 1) {
            this.entityData.set(INVENTORY_OPEN, true);
            this.playSound(SoundEvents.CHEST_OPEN, 0.8F, 0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    public void stopInventoryOpen(Player player) {
        if (this.level().isClientSide || this.inventoryOpeners <= 0) {
            return;
        }

        this.inventoryOpeners--;
        if (this.inventoryOpeners == 0) {
            this.entityData.set(INVENTORY_OPEN, false);
            this.playSound(SoundEvents.CHEST_CLOSE, 0.8F, 0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.creatorUUID != null) {
            tag.putUUID(TAG_CREATOR, this.creatorUUID);
        }
        tag.put(TAG_INVENTORY, this.beastInventory.serializeNBT());
        tag.putBoolean(TAG_RAID_SUPPLIES_GENERATED, this.raidSuppliesGenerated);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.creatorUUID = tag.hasUUID(TAG_CREATOR) ? tag.getUUID(TAG_CREATOR) : null;

        if (tag.contains(TAG_INVENTORY, Tag.TAG_COMPOUND)) {
            this.beastInventory.deserializeNBT(tag.getCompound(TAG_INVENTORY));
        }

        this.raidSuppliesGenerated = tag.getBoolean(TAG_RAID_SUPPLIES_GENERATED);
        this.inventoryOpeners = 0;
        this.entityData.set(INVENTORY_OPEN, false);
        this.chestLidProgress = 0.0F;
        this.previousChestLidProgress = 0.0F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        tickAttackAnimation();
        tickChestLid();
        forgetProtectedTargets();

        if (!this.level().isClientSide) {
            releaseRaidLeashForCombat();
            refreshCombatNavigation();
            tryAttachToRaidIllager();
        }
    }

    private void tickChestLid() {
        this.previousChestLidProgress = this.chestLidProgress;
        float target = isInventoryOpen() ? 1.0F : 0.0F;
        this.chestLidProgress = Mth.clamp(
                this.chestLidProgress + Mth.clamp(target - this.chestLidProgress, -0.2F, 0.2F),
                0.0F,
                1.0F
        );
    }

    /**
     * The raid leash helps while the party travels but fights melee navigation once the Beast has a
     * target. In combat it is removed without dropping a lead; afterwards the Beast waits briefly and
     * can be attached to another pillager or vindicator from the same wave.
     */
    private void releaseRaidLeashForCombat() {
        if (!hasValidCombatTarget() || !this.isLeashed()) {
            return;
        }

        Entity leashHolder = this.getLeashHolder();
        if (!(leashHolder instanceof Raider)) {
            return;
        }

        this.dropLeash(true, false);
        this.raidLeashSearchCooldown = RAID_RELEASH_DELAY_TICKS;
        this.getNavigation().stop();
    }

    /** Repaths periodically: without it the Beast stalls when leaving march movement. */
    private void refreshCombatNavigation() {
        LivingEntity target = this.getTarget();

        if (!hasValidCombatTarget() || target == null) {
            this.navigationRefreshCooldown = 0;
            return;
        }

        if (this.navigationRefreshCooldown > 0) {
            this.navigationRefreshCooldown--;
            return;
        }

        this.navigationRefreshCooldown = COMBAT_NAVIGATION_REFRESH_INTERVAL;
        this.getNavigation().moveTo(target, 1.05D);
    }

    private void tryAttachToRaidIllager() {
        if (this.isLeashed()
                || isPlayerControlled()
                || hasValidCombatTarget()
                || !this.hasActiveRaid()
                || this.getCurrentRaid() == null
                || this.getCurrentRaid().isOver()) {
            return;
        }

        if (this.raidLeashSearchCooldown > 0) {
            this.raidLeashSearchCooldown--;
            return;
        }

        this.raidLeashSearchCooldown = RAID_LEASH_SEARCH_INTERVAL;

        Raider nearestHandler = null;
        double nearestDistanceSqr = Double.MAX_VALUE;

        for (Raider raider : this.level().getEntitiesOfClass(
                Raider.class,
                this.getBoundingBox().inflate(RAID_LEASH_SEARCH_RADIUS),
                this::isValidRaidLeashHandler
        )) {
            double distanceSqr = this.distanceToSqr(raider);
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearestHandler = raider;
            }
        }

        if (nearestHandler != null) {
            this.setLeashedTo(nearestHandler, true);
        }
    }

    private boolean isValidRaidLeashHandler(Raider raider) {
        if (!raider.isAlive()
                || (!(raider instanceof Pillager) && !(raider instanceof Vindicator))
                || raider.getCurrentRaid() != this.getCurrentRaid()
                || raider.getWave() != this.getWave()) {
            return false;
        }

        return !isLeadingAnotherBeast(raider);
    }

    private boolean isLeadingAnotherBeast(Raider raider) {
        return !this.level().getEntitiesOfClass(
                Beast.class,
                raider.getBoundingBox().inflate(RAID_LEASH_SEARCH_RADIUS),
                beast -> beast != this
                        && beast.isAlive()
                        && beast.isLeashed()
                        && beast.getLeashHolder() == raider
        ).isEmpty();
    }

    private void tickAttackAnimation() {
        int attackTick = this.entityData.get(ATTACK_TICK);
        if (attackTick > 0) {
            this.entityData.set(ATTACK_TICK, attackTick - 1);
        }
    }

    private void forgetProtectedTargets() {
        LivingEntity target = this.getTarget();
        if (target != null && isProtectedTarget(target)) {
            this.setTarget(null);
            this.getNavigation().stop();
            this.setAggressive(false);
        }

        LivingEntity lastAttacker = this.getLastHurtByMob();
        if (lastAttacker != null && isProtectedTarget(lastAttacker)) {
            this.setLastHurtByMob(null);
        }
    }

    public int getAttackAnimationTick() {
        return this.entityData.get(ATTACK_TICK);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && isProtectedTarget(target)) {
            super.setTarget(null);
            this.setAggressive(false);
            return;
        }

        super.setTarget(target);
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof LivingEntity livingEntity && isProtectedTarget(livingEntity)) {
            return true;
        }

        return super.isAlliedTo(entity);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isProtectedTarget(target) && super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity livingTarget && isProtectedTarget(livingTarget)) {
            this.setTarget(null);
            this.setAggressive(false);
            return false;
        }

        startAttackAnimation();

        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = attackDamage > 0.0F
                ? attackDamage / 2.0F + this.random.nextInt(Mth.floor(attackDamage))
                : attackDamage;
        boolean hurt = target.hurt(this.damageSources().mobAttack(this), damage);

        if (hurt) {
            // Scaled by target resistance like the vanilla golem. Without it a knockback-immune target was
            // still launched upwards.
            double resistance = target instanceof LivingEntity hitTarget
                    ? hitTarget.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    : 0.0D;

            double launch = ATTACK_LAUNCH_STRENGTH * Math.max(0.0D, 1.0D - resistance);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, launch, 0.0D));

            this.doEnchantDamageEffects(this, target);
        }

        this.playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 1.0F);
        return hurt;
    }

    private void startAttackAnimation() {
        this.entityData.set(ATTACK_TICK, ATTACK_ANIMATION_TIME);
        this.level().broadcastEntityEvent(this, (byte) 4);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            // Animation only. The sound comes from doHurtTarget: the server playSound already reaches the
            // clients, and playing it again here doubled every hit.
            this.entityData.set(ATTACK_TICK, ATTACK_ANIMATION_TIME);
            return;
        }

        super.handleEntityEvent(id);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);

        for (int slot = 0; slot < this.beastInventory.getSlots(); slot++) {
            ItemStack stack = this.beastInventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
            }
        }
    }

    /**
     * The automatic leash used by raid illagers consumes no real lead, so breaking it by distance or
     * in combat must not drop one. Leads placed by the player keep vanilla behaviour.
     */
    @Override
    public void dropLeash(boolean broadcastPacket, boolean dropLead) {
        Entity leashHolder = this.getLeashHolder();

        boolean automaticRaidLeash = leashHolder instanceof Raider raider
                && this.hasActiveRaid()
                && this.getCurrentRaid() != null
                && raider.getCurrentRaid() == this.getCurrentRaid();

        super.dropLeash(
                broadcastPacket,
                automaticRaidLeash ? false : dropLead
        );
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return this.isAlive() && !this.isLeashed();
    }

    @Override
    protected boolean shouldStayCloseToLeashHolder() {
        return true;
    }

    @Override
    protected double followLeashSpeed() {
        return 1.15D;
    }

    @Override
    public MobType getMobType() {
        return MobType.ILLAGER;
    }

    @Override
    public IllagerArmPose getArmPose() {
        return this.isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
    }

    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {
        populateRaidSupplies(wave);
    }

    /** A player-made Beast has a creatorUUID and never receives automatic raid cargo. */
    private void populateRaidSupplies(int wave) {
        if (this.level().isClientSide || this.raidSuppliesGenerated || this.creatorUUID != null) {
            return;
        }

        this.raidSuppliesGenerated = true;

        int waveBonus = Mth.clamp(wave / 3, 0, 2);

        addRaidSupply(new ItemStack(Items.ARROW, randomCount(8 + waveBonus * 2, 16 + waveBonus * 3)));
        addRaidSupply(createRaidFood(waveBonus));
        addRaidSupply(new ItemStack(Items.STICK, randomCount(3, 8 + waveBonus)));

        int extraSupplyStacks = 2 + this.random.nextInt(3);
        for (int i = 0; i < extraSupplyStacks; i++) {
            addRaidSupply(createRandomBasicSupply(waveBonus));
        }

        addRaidSupply(createWornRaidWeapon());

        float bonusRoll = this.random.nextFloat();
        if (bonusRoll < 0.10F) {
            ItemStack healingPotion = new ItemStack(Items.POTION);
            PotionUtils.setPotion(healingPotion, Potions.HEALING);
            addRaidSupply(healingPotion);
        } else if (bonusRoll < 0.28F) {
            addRaidSupply(new ItemStack(Items.EMERALD, randomCount(1, 2)));
        } else if (bonusRoll < 0.46F) {
            addRaidSupply(new ItemStack(Items.IRON_INGOT, 1));
        }

        if (this.random.nextFloat() < 0.12F) {
            addRaidSupply(createDamagedItem(Items.SHIELD, 0.55F, 0.82F));
        }
    }

    private ItemStack createRaidFood(int waveBonus) {
        float roll = this.random.nextFloat();

        if (roll < 0.55F) {
            return new ItemStack(Items.BREAD, randomCount(2, 4 + waveBonus));
        }

        if (roll < 0.85F) {
            return new ItemStack(Items.BAKED_POTATO, randomCount(3, 6 + waveBonus));
        }

        return new ItemStack(Items.COOKED_PORKCHOP, randomCount(1, 2 + waveBonus));
    }

    private ItemStack createRandomBasicSupply(int waveBonus) {
        return switch (this.random.nextInt(8)) {
            case 0 -> new ItemStack(Items.TORCH, randomCount(4, 10 + waveBonus * 2));
            case 1 -> new ItemStack(Items.COAL, randomCount(3, 7 + waveBonus));
            case 2 -> new ItemStack(Items.STRING, randomCount(2, 5 + waveBonus));
            case 3 -> new ItemStack(Items.LEATHER, randomCount(1, 3 + waveBonus));
            case 4 -> new ItemStack(Items.IRON_NUGGET, randomCount(3, 8 + waveBonus * 2));
            case 5 -> new ItemStack(Items.OAK_PLANKS, randomCount(4, 10 + waveBonus * 2));
            case 6 -> new ItemStack(Items.FLINT, randomCount(2, 5 + waveBonus));
            default -> new ItemStack(Items.STICK, randomCount(3, 8 + waveBonus));
        };
    }

    private ItemStack createWornRaidWeapon() {
        float roll = this.random.nextFloat();

        if (roll < 0.55F) {
            return createDamagedItem(Items.CROSSBOW, 0.55F, 0.82F);
        }

        if (roll < 0.82F) {
            return createDamagedItem(Items.IRON_AXE, 0.60F, 0.84F);
        }

        return createDamagedItem(Items.STONE_AXE, 0.40F, 0.72F);
    }

    private ItemStack createDamagedItem(Item item, float minimumDamage, float maximumDamage) {
        ItemStack stack = new ItemStack(item);
        int maximumDurability = stack.getMaxDamage();

        if (maximumDurability <= 1) {
            return stack;
        }

        int minimum = Mth.clamp(Mth.floor(maximumDurability * minimumDamage), 1, maximumDurability - 1);
        int maximum = Mth.clamp(Mth.floor(maximumDurability * maximumDamage), minimum, maximumDurability - 1);
        stack.setDamageValue(randomCount(minimum, maximum));
        return stack;
    }

    private int randomCount(int minimum, int maximum) {
        if (maximum <= minimum) {
            return minimum;
        }

        return minimum + this.random.nextInt(maximum - minimum + 1);
    }

    private void addRaidSupply(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        int startSlot = this.random.nextInt(INVENTORY_SIZE);
        for (int offset = 0; offset < INVENTORY_SIZE; offset++) {
            int slot = (startSlot + offset) % INVENTORY_SIZE;
            if (this.beastInventory.getStackInSlot(slot).isEmpty()) {
                this.beastInventory.setStackInSlot(slot, stack);
                return;
            }
        }
    }

    @Override
    public boolean canJoinRaid() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.RAVAGER_CELEBRATE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.RAVAGER_STEP, 1.0F, 1.0F);
    }

    private final class BeastMeleeAttackGoal extends MeleeAttackGoal {
        private BeastMeleeAttackGoal() {
            // longMemory false, otherwise it fixates on targets it can't reach
            super(Beast.this, 1.05D, false);
        }

        @Override
        public void start() {
            super.start();
            Beast.this.setAggressive(true);
        }

        @Override
        public void stop() {
            super.stop();
            Beast.this.setAggressive(false);
        }
    }

}
