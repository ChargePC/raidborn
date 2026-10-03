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
import net.randomcara.raidborn.core.util.RaidbornTooltips;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;
import net.randomcara.raidborn.gameplay.recruit.RecruitRoster;
import net.randomcara.raidborn.gameplay.recruit.RecruitSlots;
import net.randomcara.raidborn.gameplay.recruit.SquadOrders;
import net.randomcara.raidborn.world.settlement.SettlementSpawnMarkerEvents;
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
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.need_alliance") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        List<Mob> recruits = getOwnedRecruits(player, SEARCH_RADIUS);
        if (recruits.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.raidborn.no_recruits_nearby") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        ListTag storedList = new ListTag();
        int totalStoredSlots = 0;

        for (Mob mob : recruits) {
            if (!mob.isAlive() || mob.isRemoved()) {
                player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.invalid_recruit") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
                return InteractionResultHolder.fail(stack);
            }

            CompoundTag entityTag;
            try {
                entityTag = mob.serializeNBT();
            } catch (RuntimeException e) {
                Raidborn.LOGGER.warn("Could not serialise {} for the raid bag", mob.getType(), e);
                player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.store_failed") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
                return InteractionResultHolder.fail(stack);
            }

            int slotCost = RecruitSlots.getRecruitCost(mob);
            entityTag.putInt(TAG_STORED_SLOT_COST, slotCost);
            totalStoredSlots += slotCost;

            storedList.add(entityTag);
        }

        if (storedList.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.nothing_to_store") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
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

        player.displayClientMessage(Component.translatable(recruits.size() == 1 ? "message.raidborn.raid_bag.stored_one" : "message.raidborn.raid_bag.stored", recruits.size(), totalStoredSlots) .withStyle(Style.EMPTY.withColor(0x76DB4C)), true);

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
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.sealed", String.format("%.1f", seconds)) .withStyle(Style.EMPTY.withColor(0xD9A441)), true);
            return InteractionResultHolder.fail(stack);
        }

        String requiredEffect = tag.getString(TAG_CAPTURED_EFFECT);
        String currentEffect = getCurrentAllianceEffect(player);

        if (requiredEffect.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.missing_effect") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        if (currentEffect == null || getEffectTier(currentEffect) < getEffectTier(requiredEffect)) {
            player.displayClientMessage(Component.translatable(EFFECT_HERO.equals(requiredEffect) ? "message.raidborn.raid_bag.needs_effect" : "message.raidborn.raid_bag.needs_effect_or_stronger", getPrettyEffectName(requiredEffect)) .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!tag.contains(TAG_STORED_MOBS, 9)) {
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.no_patrol") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        ListTag storedList = tag.getList(TAG_STORED_MOBS, 10);
        if (storedList.isEmpty()) {
            clearStoredPatrolData(tag);
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.empty") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        int maxSlots = getMaxRecruitSlotsForEffect(player, currentEffect);
        int activeSlots = RecruitRoster.get(player.server).countSlots(player);
        int storedSlots = getStoredSlotCostFromList(storedList);
        int finalSlots = activeSlots + storedSlots;

        if (maxSlots <= 0) {
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.no_limit") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        if (finalSlots > maxSlots) {
            player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.limit_exceeded", activeSlots, maxSlots, storedSlots) .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
            return InteractionResultHolder.fail(stack);
        }

        List<Mob> preparedMobs = new ArrayList<>();

        for (int i = 0; i < storedList.size(); i++) {
            CompoundTag entityTag = storedList.getCompound(i).copy();

            Entity loaded;
            try {
                loaded = EntityTypeLoader.load(serverLevel, entityTag);
            } catch (RuntimeException e) {
                Raidborn.LOGGER.warn("Could not rebuild a stored recruit from the raid bag", e);
                player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.rebuild_failed") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
                return InteractionResultHolder.fail(stack);
            }

            if (!(loaded instanceof Mob mob)) {
                player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.invalid_stored") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
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

                player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.no_room") .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
                return InteractionResultHolder.fail(stack);
            }

            addedMobs.add(mob);
        }

        clearStoredPatrolData(tag);

        player.displayClientMessage(Component.translatable(addedMobs.size() == 1 ? "message.raidborn.raid_bag.released_one" : "message.raidborn.raid_bag.released", addedMobs.size()) .withStyle(Style.EMPTY.withColor(0x76DB4C)), true);

        return InteractionResultHolder.success(stack);
    }

    private static void prepareReleasedMob(ServerPlayer player, Mob mob, int index, int total) {
        Vec3 pos = findReleasePosition(player, mob, index, total);
        mob.moveTo(pos.x, pos.y, pos.z, player.getYRot(), mob.getXRot());

        mob.getPersistentData().putBoolean(FollowOwnerGoal.TAG_RECRUITED, true);
        mob.getPersistentData().putUUID(FollowOwnerGoal.TAG_OWNER, player.getUUID());

        SettlementSpawnMarkerEvents.clearSettlementHome(mob);

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
        return player.level().getEntitiesOfClass(Mob.class, box, mob -> isOwnedRecruit(player, mob));
    }

    private static boolean isOwnedRecruit(ServerPlayer player, Mob mob) {
        return mob.isAlive() && !mob.isRemoved() && RecruitOwnership.isYours(player, mob);
    }

    public static boolean hasStoredPatrol(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_STORED_MOBS, 9) && !tag.getList(TAG_STORED_MOBS, 10).isEmpty();
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
        Component ownerText = ownerName.isEmpty() ? Component.translatable("message.raidborn.raid_bag.another_player") : Component.literal(ownerName);

        player.displayClientMessage(Component.translatable("message.raidborn.raid_bag.belongs_to", ownerText) .withStyle(Style.EMPTY.withColor(0xD9534F)), true);
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

    private static int getEffectTier(String effect) {
        return switch (effect) {
            case EFFECT_LOYALTY -> 1;
            case EFFECT_HONOR -> 2;
            case EFFECT_HERO -> 3;
            default -> 0;
        };
    }

    private static Component getPrettyEffectName(String effect) {
        return switch (effect) {
            case EFFECT_LOYALTY -> ModEffects.ILLAGER_LOYALTY.get().getDisplayName();
            case EFFECT_HONOR -> ModEffects.ILLAGER_HONOR.get().getDisplayName();
            case EFFECT_HERO -> ModEffects.HERO_OF_THE_RAID.get().getDisplayName();
            default -> Component.translatable("message.raidborn.raid_bag.unknown_effect");
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

        TooltipHelper.addShiftDescription(tooltip, RaidbornTooltips.line("raid_bag.usage", 0xDDDDDD), RaidbornTooltips.line("raid_bag.sealed", 0xD9A441), RaidbornTooltips.line("raid_bag.owner_only", 0xC77DFF));

        if (tag != null) {
            if (tag.hasUUID(TAG_OWNER_UUID)) {
                String ownerName = tag.getString(TAG_OWNER_NAME);
                if (!ownerName.isEmpty()) {
                    tooltip.add(RaidbornTooltips.line("raid_bag.owner", 0x76DB4C, ownerName));
                }
            }

            if (tag.contains(TAG_STORED_MOBS, 9)) {
                int count = tag.getList(TAG_STORED_MOBS, 10).size();
                if (count > 0) {
                    tooltip.add(RaidbornTooltips.line("raid_bag.stored_recruits", 0xFF5555, count));

                    int slots = getStoredSlotCostFromList(tag.getList(TAG_STORED_MOBS, 10));
                    tooltip.add(RaidbornTooltips.line("raid_bag.stored_slots", 0xFFAA55, slots));
                }
            }

            if (tag.contains(TAG_CAPTURED_EFFECT)) {
                String effect = tag.getString(TAG_CAPTURED_EFFECT);
                if (!effect.isEmpty()) {
                    tooltip.add(RaidbornTooltips.line(EFFECT_HERO.equals(effect) ? "raid_bag.required_effect" : "raid_bag.required_effect_or_stronger", 0x55C1FF, getPrettyEffectName(effect)));
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
