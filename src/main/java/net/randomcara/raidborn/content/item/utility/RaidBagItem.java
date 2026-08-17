package net.randomcara.raidborn.content.item.utility;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.artifact.item.RaidbornNecklaceItem;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;
import net.randomcara.raidborn.gameplay.recruit.RecruitSlots;
import net.randomcara.raidborn.gameplay.recruit.SquadOrders;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RaidBagItem extends Item {
    private static final double SEARCH_RADIUS = 192.0D;
    private static final int RELEASE_COOLDOWN_TICKS = 20 * 20;

    private static final String TAG_OWNER_UUID = "raidborn_bag_owner_uuid";
    private static final String TAG_OWNER_NAME = "raidborn_bag_owner_name";
    private static final String TAG_CAPTURED_EFFECT = "raidborn_bag_captured_effect";
    private static final String TAG_STORED_MOBS = "raidborn_bag_stored_mobs";
    private static final String TAG_RELEASE_UNLOCK_TIME = "raidborn_bag_release_unlock_time";
    private static final String TAG_BAG_ID = "raidborn_bag_id";

    private static final String TAG_STORED_SLOT_COST = "raidborn_stored_slot_cost";

    private static final String EFFECT_LOYALTY = "loyalty";
    private static final String EFFECT_HONOR = "honor";
    private static final String EFFECT_HERO = "hero";

    public RaidBagItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        if (!checkBagOwner(serverPlayer, stack)) {
            return InteractionResultHolder.fail(stack);
        }

        return hasStoredPatrol(stack) ? releasePatrol(serverPlayer, stack) : capturePatrol(serverPlayer, stack);
    }

    private static InteractionResultHolder<ItemStack> capturePatrol(ServerPlayer player, ItemStack stack) {
        String currentEffect = getCurrentAllianceEffect(player);
        if (currentEffect == null) {
            player.displayClientMessage(
                    Component.literal("You need Illager Loyalty, Illager Honor, or Hero of the Illage.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        List<Mob> recruits = getOwnedRecruits(player, SEARCH_RADIUS);
        if (recruits.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("You have no recruited illagers nearby.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        ListTag storedList = new ListTag();
        int totalStoredSlots = 0;

        for (Mob mob : recruits) {
            if (!mob.isAlive() || mob.isRemoved()) {
                player.displayClientMessage(
                        Component.literal("A recruit was invalid and could not be stored.")
                                .withStyle(Style.EMPTY.withColor(0xD9534F)),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }

            CompoundTag entityTag;
            try {
                entityTag = mob.serializeNBT();
            } catch (RuntimeException e) {
                // A modded recruit can refuse to serialise. Abort the whole capture rather than
                // store a half-written patrol.
                Raidborn.LOGGER.warn("Could not serialise {} for the raid bag", mob.getType(), e);
                player.displayClientMessage(
                        Component.literal("Failed to store a recruit safely.")
                                .withStyle(Style.EMPTY.withColor(0xD9534F)),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }

            int slotCost = RecruitSlots.getRecruitCost(mob);
            entityTag.putInt(TAG_STORED_SLOT_COST, slotCost);
            totalStoredSlots += slotCost;

            storedList.add(entityTag);
        }

        if (storedList.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("No valid recruits were found to store.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        CompoundTag tag = stack.getOrCreateTag();
        bindBagToPlayerIfNeeded(tag, player);

        if (!tag.hasUUID(TAG_BAG_ID)) {
            tag.putUUID(TAG_BAG_ID, UUID.randomUUID());
        }

        tag.putString(TAG_CAPTURED_EFFECT, currentEffect);
        tag.put(TAG_STORED_MOBS, storedList);
        tag.putLong(TAG_RELEASE_UNLOCK_TIME, player.level().getGameTime() + RELEASE_COOLDOWN_TICKS);

        for (Mob mob : recruits) {
            SquadOrders.clearCombatState(mob);
            mob.remove(Entity.RemovalReason.DISCARDED);
        }

        player.displayClientMessage(
                Component.literal("Stored " + recruits.size() + " recruited illagers (" + totalStoredSlots + " slots).")
                        .withStyle(Style.EMPTY.withColor(0x76DB4C)),
                true
        );

        return InteractionResultHolder.success(stack);
    }

    private static InteractionResultHolder<ItemStack> releasePatrol(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (!checkBagOwner(player, stack)) {
            return InteractionResultHolder.fail(stack);
        }

        long now = player.level().getGameTime();
        long unlockTime = tag.getLong(TAG_RELEASE_UNLOCK_TIME);
        if (now < unlockTime) {
            long remainingTicks = unlockTime - now;
            double seconds = remainingTicks / 20.0D;

            player.displayClientMessage(
                    Component.literal(String.format("The bag is still sealed for %.1f seconds.", seconds))
                            .withStyle(Style.EMPTY.withColor(0xD9A441)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        String requiredEffect = tag.getString(TAG_CAPTURED_EFFECT);
        String currentEffect = getCurrentAllianceEffect(player);

        if (requiredEffect.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("This bag is missing capture effect data.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        if (!requiredEffect.equals(currentEffect)) {
            player.displayClientMessage(
                    Component.literal("You need the same alliance effect used when the patrol was captured: " + getPrettyEffectName(requiredEffect))
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!tag.contains(TAG_STORED_MOBS, 9)) {
            player.displayClientMessage(
                    Component.literal("This bag contains no valid stored patrol.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        ListTag storedList = tag.getList(TAG_STORED_MOBS, 10);
        if (storedList.isEmpty()) {
            clearStoredPatrolData(tag);
            player.displayClientMessage(
                    Component.literal("The bag was empty.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        int maxSlots = getMaxRecruitSlotsForEffect(player, currentEffect);
        int activeSlots = getActiveRecruitSlots(player);
        int storedSlots = getStoredSlotCostFromList(storedList);
        int finalSlots = activeSlots + storedSlots;

        if (maxSlots <= 0) {
            player.displayClientMessage(
                    Component.literal("You do not have a valid recruit limit.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        if (finalSlots > maxSlots) {
            player.displayClientMessage(
                    Component.literal("Cannot release this patrol. Recruit limit would be exceeded: "
                                    + activeSlots + "/" + maxSlots
                                    + " active slots, bag requires " + storedSlots + " slots.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        List<Mob> preparedMobs = new ArrayList<>();

        for (int i = 0; i < storedList.size(); i++) {
            CompoundTag entityTag = storedList.getCompound(i).copy();

            Entity loaded;
            try {
                loaded = EntityTypeLoader.load(serverLevel, entityTag);
            } catch (RuntimeException e) {
                // Stored NBT can outlive the mod that wrote it; rebuilding then fails.
                Raidborn.LOGGER.warn("Could not rebuild a stored recruit from the raid bag", e);
                player.displayClientMessage(
                        Component.literal("Failed to rebuild stored patrol safely.")
                                .withStyle(Style.EMPTY.withColor(0xD9534F)),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }

            if (!(loaded instanceof Mob mob)) {
                player.displayClientMessage(
                        Component.literal("A stored recruit was invalid.")
                                .withStyle(Style.EMPTY.withColor(0xD9534F)),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }

            prepareReleasedMob(player, mob, i, storedList.size());
            preparedMobs.add(mob);
        }

        List<Mob> addedMobs = new ArrayList<>();

        for (Mob mob : preparedMobs) {
            if (!serverLevel.addFreshEntity(mob)) {
                for (Mob added : addedMobs) {
                    added.remove(Entity.RemovalReason.DISCARDED);
                }

                player.displayClientMessage(
                        Component.literal("Not enough room to release the patrol safely.")
                                .withStyle(Style.EMPTY.withColor(0xD9534F)),
                        true
                );
                return InteractionResultHolder.fail(stack);
            }

            addedMobs.add(mob);
        }

        clearStoredPatrolData(tag);

        player.displayClientMessage(
                Component.literal("Released " + addedMobs.size() + " recruited illagers.")
                        .withStyle(Style.EMPTY.withColor(0x76DB4C)),
                true
        );

        return InteractionResultHolder.success(stack);
    }

    private static void prepareReleasedMob(ServerPlayer player, Mob mob, int index, int total) {
        Vec3 pos = findReleasePosition(player, mob, index, total);

        mob.moveTo(pos.x, pos.y, pos.z, player.getYRot(), mob.getXRot());

        mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, true);
        mob.getPersistentData().putUUID(FollowOwnerGoal.TAG_OWNER, player.getUUID());

        mob.setPersistenceRequired();
        SquadOrders.clearCombatState(mob);
    }

    private static Vec3 findReleasePosition(ServerPlayer player, Mob mob, int index, int total) {
        ServerLevel level = player.serverLevel();

        double baseRadius = 2.5D + (index / 6) * 1.2D;
        double angle = (Math.PI * 2.0D / Math.max(total, 1)) * index;

        for (int ring = 0; ring < 4; ring++) {
            double radius = baseRadius + ring * 1.2D;

            for (int yOff = 0; yOff <= 3; yOff++) {
                double x = player.getX() + Math.cos(angle) * radius;
                double y = player.getY() + yOff;
                double z = player.getZ() + Math.sin(angle) * radius;

                mob.moveTo(x, y, z, mob.getYRot(), mob.getXRot());

                if (level.noCollision(mob, mob.getBoundingBox())) {
                    return new Vec3(x, y, z);
                }
            }
        }

        return new Vec3(player.getX(), player.getY() + 1.0D, player.getZ());
    }

    private static List<Mob> getOwnedRecruits(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);

        return player.level().getEntitiesOfClass(
                Mob.class,
                box,
                mob -> isOwnedRecruit(player, mob)
        );
    }

    private static boolean isOwnedRecruit(ServerPlayer player, Mob mob) {
        if (!mob.isAlive() || mob.isRemoved()) return false;
        if (!isRecruited(mob)) return false;

        UUID owner = getOwnerUUID(mob);
        return owner != null && owner.equals(player.getUUID());
    }

    private static boolean isRecruited(Mob mob) {
        return mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)
                && mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);
    }

    private static UUID getOwnerUUID(Mob mob) {
        if (!mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)) return null;
        return mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER);
    }

    public static boolean hasStoredPatrol(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.contains(TAG_STORED_MOBS, 9)
                && !tag.getList(TAG_STORED_MOBS, 10).isEmpty();
    }

    private static void bindBagToPlayerIfNeeded(CompoundTag tag, ServerPlayer player) {
        if (!tag.hasUUID(TAG_OWNER_UUID)) {
            tag.putUUID(TAG_OWNER_UUID, player.getUUID());
            tag.putString(TAG_OWNER_NAME, player.getGameProfile().getName());
        }
    }

    private static boolean checkBagOwner(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.hasUUID(TAG_OWNER_UUID)) {
            return true;
        }

        UUID owner = tag.getUUID(TAG_OWNER_UUID);
        if (owner.equals(player.getUUID())) {
            return true;
        }

        String ownerName = tag.getString(TAG_OWNER_NAME);
        if (ownerName == null || ownerName.isEmpty()) {
            ownerName = "another player";
        }

        player.displayClientMessage(
                Component.literal("This Raid Bag belongs to " + ownerName + ".")
                        .withStyle(Style.EMPTY.withColor(0xD9534F)),
                true
        );
        return false;
    }

    @Nullable
    private static String getCurrentAllianceEffect(ServerPlayer player) {
        if (player.hasEffect(ModEffects.HERO_OF_THE_RAID.get())) {
            return EFFECT_HERO;
        }
        if (player.hasEffect(ModEffects.ILLAGER_HONOR.get())) {
            return EFFECT_HONOR;
        }
        if (player.hasEffect(ModEffects.ILLAGER_LOYALTY.get())) {
            return EFFECT_LOYALTY;
        }
        return null;
    }

    private static int getMaxRecruitSlotsForEffect(ServerPlayer player, @Nullable String effect) {
        if (effect == null) return 0;

        int baseSlots = switch (effect) {
            case EFFECT_LOYALTY -> RaidbornServerConfig.getLoyaltyRecruitSlots();
            case EFFECT_HONOR -> RaidbornServerConfig.getHonorRecruitSlots();
            case EFFECT_HERO -> RaidbornServerConfig.getHeroRecruitSlots();
            default -> 0;
        };

        if (baseSlots <= 0) {
            return baseSlots;
        }

        return baseSlots + RaidbornNecklaceItem.getEquippedBonusRecruitSlots(player);
    }

    private static int getActiveRecruitSlots(ServerPlayer player) {
        int total = 0;

        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) {
                    continue;
                }

                if (!isOwnedRecruit(player, mob)) {
                    continue;
                }

                total += Math.max(1, RecruitSlots.getRecruitCost(mob));
            }
        }

        return total;
    }

    private static int getStoredSlotCostFromList(ListTag list) {
        int total = 0;

        for (int i = 0; i < list.size(); i++) {
            CompoundTag mobTag = list.getCompound(i);

            int slotCost = 1;
            if (mobTag.contains(TAG_STORED_SLOT_COST, 3)) {
                slotCost = mobTag.getInt(TAG_STORED_SLOT_COST);
            }

            total += Math.max(1, slotCost);
        }

        return total;
    }

    private static String getPrettyEffectName(String effect) {
        return switch (effect) {
            case EFFECT_LOYALTY -> "Illager Loyalty";
            case EFFECT_HONOR -> "Illager Honor";
            case EFFECT_HERO -> "Hero of the Illage";
            default -> "Unknown";
        };
    }

    private static void clearStoredPatrolData(CompoundTag tag) {
        tag.remove(TAG_STORED_MOBS);
        tag.remove(TAG_CAPTURED_EFFECT);
        tag.remove(TAG_RELEASE_UNLOCK_TIME);
    }

    public static boolean playerHasStoredSquad(ServerPlayer player) {
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof RaidBagItem)) continue;
            if (!hasStoredPatrol(stack)) continue;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.hasUUID(TAG_OWNER_UUID)) continue;

            if (player.getUUID().equals(tag.getUUID(TAG_OWNER_UUID))) {
                return true;
            }
        }

        return false;
    }

    public static int getStoredRecruitCount(ServerPlayer player) {
        int total = 0;
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof RaidBagItem)) continue;
            if (!hasStoredPatrol(stack)) continue;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.hasUUID(TAG_OWNER_UUID)) continue;
            if (!player.getUUID().equals(tag.getUUID(TAG_OWNER_UUID))) continue;

            ListTag list = tag.getList(TAG_STORED_MOBS, 10);
            total += list.size();
        }

        return total;
    }

    public static int getStoredRecruitSlots(ServerPlayer player) {
        int total = 0;
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof RaidBagItem)) continue;
            if (!hasStoredPatrol(stack)) continue;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.hasUUID(TAG_OWNER_UUID)) continue;
            if (!player.getUUID().equals(tag.getUUID(TAG_OWNER_UUID))) continue;

            ListTag list = tag.getList(TAG_STORED_MOBS, 10);
            total += getStoredSlotCostFromList(list);
        }

        return total;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();

        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Right click to store or release your recruited illagers", 0xDDDDDD),
                TooltipHelper.line("Release is locked for 20s after storing", 0xD9A441),
                TooltipHelper.line("Only the owner can use this bag", 0xC77DFF)
        );

        if (tag != null) {
            if (tag.hasUUID(TAG_OWNER_UUID)) {
                String ownerName = tag.getString(TAG_OWNER_NAME);
                if (!ownerName.isEmpty()) {
                    tooltip.add(Component.literal("Owner: " + ownerName)
                            .withStyle(Style.EMPTY.withColor(0x76DB4C)));
                }
            }

            if (tag.contains(TAG_STORED_MOBS, 9)) {
                int count = tag.getList(TAG_STORED_MOBS, 10).size();
                if (count > 0) {
                    tooltip.add(Component.literal("Stored recruits: " + count)
                            .withStyle(Style.EMPTY.withColor(0xFF5555)));

                    int slots = getStoredSlotCostFromList(tag.getList(TAG_STORED_MOBS, 10));

                    tooltip.add(Component.literal("Stored slots: " + slots)
                            .withStyle(Style.EMPTY.withColor(0xFFAA55)));
                }
            }

            if (tag.contains(TAG_CAPTURED_EFFECT)) {
                String effect = tag.getString(TAG_CAPTURED_EFFECT);
                if (!effect.isEmpty()) {
                    tooltip.add(Component.literal("Required effect: " + getPrettyEffectName(effect))
                            .withStyle(Style.EMPTY.withColor(0x55C1FF)));
                }
            }
        }

        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static class EntityTypeLoader {
        @Nullable
        public static Entity load(ServerLevel level, CompoundTag entityTag) {
            return EntityType.loadEntityRecursive(entityTag, level, entity -> entity);
        }
    }

}