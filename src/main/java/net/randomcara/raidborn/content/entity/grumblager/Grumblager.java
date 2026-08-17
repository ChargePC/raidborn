package net.randomcara.raidborn.content.entity.grumblager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;

import javax.annotation.Nullable;

public class Grumblager extends AbstractIllager implements CrossbowAttackMob, RangedAttackMob {
    private static final EntityDataAccessor<Boolean> IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Grumblager.class, EntityDataSerializers.BOOLEAN);

    private static final double BOW_SPEED = 0.9D;
    private static final double CROSSBOW_SPEED = 1.0D;
    private static final double MELEE_SPEED = 1.0D;

    private static final int BOW_ATTACK_INTERVAL = 24;

    private static final float BOW_RANGE = 15.0F;
    private static final float CROSSBOW_RANGE = 12.0F;

    private static final float VOICE_PITCH_MULTIPLIER = 1.25F;

    private static final float SPAWN_ARMOR_CHANCE = 0.65F;
    private static final float NATURAL_SPAWN_ARMOR_CHANCE = 0.65F;
    private static final float SPAWNED_ARMOR_DROP_CHANCE = 0.085F;

    @Nullable
    private RangedBowAttackGoal<Grumblager> bowGoal;

    @Nullable
    private RangedCrossbowAttackGoal<Grumblager> crossbowGoal;

    @Nullable
    private MeleeAttackGoal meleeGoal;

    public Grumblager(EntityType<? extends Grumblager> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_CHARGING_CROSSBOW, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.bowGoal = new RangedBowAttackGoal<>(this, BOW_SPEED, BOW_ATTACK_INTERVAL, BOW_RANGE);
        this.crossbowGoal = new RangedCrossbowAttackGoal<>(this, CROSSBOW_SPEED, CROSSBOW_RANGE);
        this.meleeGoal = createMeleeGoal();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, AbstractVillager.class, 8.0F));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));

        this.reassessWeaponGoal();
    }

    private MeleeAttackGoal createMeleeGoal() {
        return new MeleeAttackGoal(this, MELEE_SPEED, false) {
            @Override
            public void start() {
                super.start();
                Grumblager.this.setAggressive(true);
            }

            @Override
            public void stop() {
                super.stop();
                Grumblager.this.setAggressive(false);
            }
        };
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (!this.level().isClientSide) {
            this.reassessWeaponGoal();
        }
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);

        if (!this.level().isClientSide && slot == EquipmentSlot.MAINHAND) {
            this.reassessWeaponGoal();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        EquipmentSlot armorSlot = getArmorSlotFromStack(heldStack);

        if (armorSlot == null || !RaidbornServerConfig.isGrumblagerArmorEquippingEnabled() || !isRecruited()) {
            return super.mobInteract(player, hand);
        }

        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !isOwnedBy(serverPlayer)) {
            return super.mobInteract(player, hand);
        }

        equipArmorFromPlayer(serverPlayer, hand, heldStack, armorSlot);
        return InteractionResult.CONSUME;
    }

    @Nullable
    private static EquipmentSlot getArmorSlotFromStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armorItem)) {
            return null;
        }

        EquipmentSlot slot = armorItem.getEquipmentSlot();
        return slot.getType() == EquipmentSlot.Type.ARMOR ? slot : null;
    }

    private boolean isRecruited() {
        return this.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)
                && this.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);
    }

    private boolean isOwnedBy(ServerPlayer player) {
        return this.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)
                && this.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER).equals(player.getUUID());
    }

    private void equipArmorFromPlayer(ServerPlayer player, InteractionHand hand, ItemStack heldStack, EquipmentSlot armorSlot) {
        ItemStack oldArmor = this.getItemBySlot(armorSlot);

        if (!oldArmor.isEmpty()) {
            this.spawnAtLocation(oldArmor.copy());
        }

        ItemStack newArmor = heldStack.copy();
        newArmor.setCount(1);

        this.setItemSlot(armorSlot, newArmor);
        this.setDropChance(armorSlot, 2.0F);

        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }

        this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, 1.0F);
        player.swing(hand, true);
    }

    public void reassessWeaponGoal() {
        if (this.level().isClientSide || this.meleeGoal == null || this.bowGoal == null || this.crossbowGoal == null) {
            return;
        }

        this.goalSelector.removeGoal(this.meleeGoal);
        this.goalSelector.removeGoal(this.bowGoal);
        this.goalSelector.removeGoal(this.crossbowGoal);

        Item item = this.getMainHandItem().getItem();

        if (item instanceof BowItem) {
            this.goalSelector.addGoal(2, this.bowGoal);
        } else if (item instanceof CrossbowItem) {
            this.goalSelector.addGoal(2, this.crossbowGoal);
        } else {
            this.goalSelector.addGoal(2, this.meleeGoal);
        }
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem || weapon instanceof CrossbowItem;
    }

    public boolean isChargingCrossbow() {
        return this.entityData.get(IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        this.entityData.set(IS_CHARGING_CROSSBOW, charging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack crossbow, Projectile projectile, float projectileAngle) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY(0.3333333333333333D) - projectile.getY() + horizontal * 0.2D;

        projectile.shoot(dx, dy, dz, 1.6F, getArrowInaccuracy());
        this.level().addFreshEntity(projectile);
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, getShootPitch());
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, Items.ARROW.getDefaultInstance(), velocity);

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, getArrowInaccuracy());
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, getShootPitch());
        this.level().addFreshEntity(arrow);
    }

    private float getArrowInaccuracy() {
        return 14 - this.level().getDifficulty().getId() * 4;
    }

    private float getShootPitch() {
        return 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(rollWeapon(random)));
    }

    private static Item rollWeapon(RandomSource random) {
        float roll = random.nextFloat();

        if (roll < 0.34F) {
            return Items.IRON_SWORD;
        }

        return roll < 0.67F ? Items.CROSSBOW : Items.BOW;
    }

    private void equipSpawnArmor(RandomSource random) {
        maybeEquipArmorPiece(random, EquipmentSlot.HEAD);
        maybeEquipArmorPiece(random, EquipmentSlot.CHEST);
        maybeEquipArmorPiece(random, EquipmentSlot.LEGS);
        maybeEquipArmorPiece(random, EquipmentSlot.FEET);
    }

    private void maybeEquipArmorPiece(RandomSource random, EquipmentSlot slot) {
        if (random.nextFloat() > SPAWN_ARMOR_CHANCE) {
            return;
        }

        Item armor = getArmorForSlot(slot, random.nextBoolean());

        if (armor != null) {
            this.setItemSlot(slot, new ItemStack(armor));
            this.setDropChance(slot, SPAWNED_ARMOR_DROP_CHANCE);
        }
    }

    @Nullable
    private static Item getArmorForSlot(EquipmentSlot slot, boolean chain) {
        return switch (slot) {
            case HEAD -> chain ? Items.CHAINMAIL_HELMET : Items.LEATHER_HELMET;
            case CHEST -> chain ? Items.CHAINMAIL_CHESTPLATE : Items.LEATHER_CHESTPLATE;
            case LEGS -> chain ? Items.CHAINMAIL_LEGGINGS : Items.LEATHER_LEGGINGS;
            case FEET -> chain ? Items.CHAINMAIL_BOOTS : Items.LEATHER_BOOTS;
            default -> null;
        };
    }

    private boolean shouldSpawnWithArmor(MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.EVENT) {
            return true;
        }

        if (spawnType == MobSpawnType.SPAWN_EGG) {
            return true;
        }

        if (spawnType == MobSpawnType.NATURAL) {
            return this.random.nextFloat() < NATURAL_SPAWN_ARMOR_CHANCE;
        }

        return false;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                        DifficultyInstance difficulty,
                                        MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, dataTag);

        this.populateDefaultEquipmentSlots(this.random, difficulty);
        this.populateDefaultEquipmentEnchantments(this.random, difficulty);

        if (shouldSpawnWithArmor(spawnType)) {
            this.equipSpawnArmor(this.random);
        }

        this.reassessWeaponGoal();
        return data;
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (this.isChargingCrossbow()) {
            return IllagerArmPose.CROSSBOW_CHARGE;
        }

        if (this.getMainHandItem().getItem() instanceof CrossbowItem && this.isAggressive()) {
            return IllagerArmPose.CROSSBOW_HOLD;
        }

        return this.isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.CROSSED;
    }

    /** Raises hurt and death pitch: it borrows the Pillager voice but has 14 health against 24. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * VOICE_PITCH_MULTIPLIER;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.PILLAGER_CELEBRATE;
    }
}