package net.randomcara.raidborn.content.item.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.gameplay.attack.AttackRaidbornHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class VillageLootItem extends Item {
    private static final String TAG_TIER = "RaidbornVillageLootTier";

    private static final ResourceLocation LOYALTY_LOOT_TABLE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "gameplay/village_loot/loyalty");

    private static final ResourceLocation HONOR_LOOT_TABLE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "gameplay/village_loot/honor");

    private static final ResourceLocation HERO_LOOT_TABLE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "gameplay/village_loot/hero");

    private static final ResourceLocation BUNDLE_DROP_CONTENTS_SOUND =
            ResourceLocation.fromNamespaceAndPath("minecraft", "item.bundle.drop_contents");

    public VillageLootItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createForTier(AttackRaidbornHooks.AttackTier tier) {
        ItemStack stack = new ItemStack(ModItems.VILLAGE_LOOT.get());
        setTier(stack, tier);
        return stack;
    }

    public static void setTier(ItemStack stack, AttackRaidbornHooks.AttackTier tier) {
        stack.getOrCreateTag().putString(TAG_TIER, tier.name().toLowerCase(Locale.ROOT));
    }

    public static AttackRaidbornHooks.AttackTier getTier(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_TIER)) {
            return AttackRaidbornHooks.AttackTier.LOYALTY;
        }

        String tierId = tag.getString(TAG_TIER);

        if ("hero".equalsIgnoreCase(tierId) || "hero_of_the_raid".equalsIgnoreCase(tierId)) {
            return AttackRaidbornHooks.AttackTier.HERO;
        }

        if ("honor".equalsIgnoreCase(tierId) || "illager_honor".equalsIgnoreCase(tierId)) {
            return AttackRaidbornHooks.AttackTier.HONOR;
        }

        return AttackRaidbornHooks.AttackTier.LOYALTY;
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.UNCOMMON;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        AttackRaidbornHooks.AttackTier tier = getTier(stack);

        boolean gaveLoot = generateAndGiveLoot(serverLevel, serverPlayer, tier);
        if (!gaveLoot) {
            giveFallbackLoot(serverPlayer, tier);
        }

        playBundleDropContentsSound(serverLevel, serverPlayer);

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        serverPlayer.containerMenu.broadcastChanges();

        return InteractionResultHolder.consume(stack);
    }

    private static boolean generateAndGiveLoot(ServerLevel level, ServerPlayer player, AttackRaidbornHooks.AttackTier tier) {
        LootTable lootTable = level.getServer().getLootData().getLootTable(getLootTableId(tier));

        LootParams lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);

        List<ItemStack> generatedLoot = lootTable.getRandomItems(lootParams);

        boolean gaveAnyLoot = false;

        for (ItemStack lootStack : generatedLoot) {
            if (!lootStack.isEmpty()) {
                giveOrDrop(player, lootStack);
                gaveAnyLoot = true;
            }
        }

        return gaveAnyLoot;
    }

    private static void giveFallbackLoot(ServerPlayer player, AttackRaidbornHooks.AttackTier tier) {
        switch (tier) {
            case HERO -> {
                giveOrDrop(player, new ItemStack(Items.EMERALD, 16));
                giveOrDrop(player, new ItemStack(Items.DIAMOND, 3));
                giveOrDrop(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 12));
            }
            case HONOR -> {
                giveOrDrop(player, new ItemStack(Items.EMERALD, 10));
                giveOrDrop(player, new ItemStack(Items.DIAMOND, 2));
                giveOrDrop(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 6));
            }
            case LOYALTY -> {
                giveOrDrop(player, new ItemStack(Items.EMERALD, 6));
                giveOrDrop(player, new ItemStack(Items.IRON_INGOT, 8));
                giveOrDrop(player, new ItemStack(Items.BREAD, 4));
            }
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemStack remaining = stack.copy();
        boolean inserted = player.getInventory().add(remaining);

        if (!inserted || !remaining.isEmpty()) {
            ItemEntity dropped = player.drop(remaining, false);

            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setTarget(player.getUUID());
            }
        }
    }

    private static ResourceLocation getLootTableId(AttackRaidbornHooks.AttackTier tier) {
        return switch (tier) {
            case HERO -> HERO_LOOT_TABLE;
            case HONOR -> HONOR_LOOT_TABLE;
            case LOYALTY -> LOYALTY_LOOT_TABLE;
        };
    }

    private static void playBundleDropContentsSound(ServerLevel level, ServerPlayer player) {
        Optional<SoundEvent> optionalSound = BuiltInRegistries.SOUND_EVENT.getOptional(BUNDLE_DROP_CONTENTS_SOUND);

        if (optionalSound.isEmpty()) {
            return;
        }

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                optionalSound.get(),
                SoundSource.PLAYERS,
                1.0F,
                0.9F + level.random.nextFloat() * 0.2F
        );
    }

    @Override
    public Component getName(ItemStack stack) {
        AttackRaidbornHooks.AttackTier tier = getTier(stack);

        return switch (tier) {
            case HERO -> Component.translatable("item.raidborn.village_loot")
                    .append(Component.literal(" - Hero"));
            case HONOR -> Component.translatable("item.raidborn.village_loot")
                    .append(Component.literal(" - Honor"));
            case LOYALTY -> Component.translatable("item.raidborn.village_loot")
                    .append(Component.literal(" - Loyalty"));
        };
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level level,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        AttackRaidbornHooks.AttackTier tier = getTier(stack);

        tooltip.add(Component.translatable("tooltip.raidborn.village_loot.open")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("tooltip.raidborn.village_loot.tier." + tier.name().toLowerCase(Locale.ROOT))
                .withStyle(getTierColor(tier)));
    }

    private static ChatFormatting getTierColor(AttackRaidbornHooks.AttackTier tier) {
        return switch (tier) {
            case HERO -> ChatFormatting.LIGHT_PURPLE;
            case HONOR -> ChatFormatting.GOLD;
            case LOYALTY -> ChatFormatting.GREEN;
        };
    }
}
