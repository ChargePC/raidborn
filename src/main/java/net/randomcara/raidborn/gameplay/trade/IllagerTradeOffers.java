package net.randomcara.raidborn.gameplay.trade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.compat.RaidbornCompat;

import java.util.List;

public final class IllagerTradeOffers {
    private static final float DEFAULT_PRICE_MULTIPLIER = 0.05F;
    private static final int MAX_EMERALDS_PER_STACK = 64;

    private static final String RAIDBORN_MODID = "raidborn";

    private static final String SANDR_MODID = RaidbornCompat.SAVAGE_AND_RAVAGE;
    private static final String IINV_MODID = RaidbornCompat.ILLAGER_INVASION;
    private static final String GI_MODID = RaidbornCompat.GUARD_ILLAGERS;
    private static final String HR_MODID = RaidbornCompat.HUNTERS_RETURN;
    private static final String CONJ_MODID = RaidbornCompat.CONJURER_ILLAGER;
    private static final String RAVAGE_AND_CABBAGE_MODID = RaidbornCompat.RAVAGE_AND_CABBAGE;
    private static final String ENCHANT_WITH_MOB_MODID = RaidbornCompat.ENCHANT_WITH_MOB;

    // Trade-only integrations: no compat switch of their own, so they stay local to this file.
    private static final String FNF_MODID = "friendsandfoes";
    private static final String WANDERING_BAGS_MODID = "wandering_bags";

    private static final ResourceLocation EWM_TRADE_BOOK_LOW = id(RAIDBORN_MODID, "trades/ewm_mob_enchant_book_low");
    private static final ResourceLocation EWM_TRADE_BOOK_MID = id(RAIDBORN_MODID, "trades/ewm_mob_enchant_book_mid");
    private static final ResourceLocation EWM_TRADE_BOOK_HIGH = id(RAIDBORN_MODID, "trades/ewm_mob_enchant_book_high");

    private static final List<Potion> POTION_TIER1 = List.of(Potions.SWIFTNESS, Potions.FIRE_RESISTANCE, Potions.NIGHT_VISION, Potions.WATER_BREATHING);
    private static final List<Potion> POTION_TIER2 = List.of(Potions.HEALING, Potions.REGENERATION, Potions.STRENGTH, Potions.INVISIBILITY);
    private static final List<Potion> POTION_TIER3 = List.of(Potions.STRONG_HEALING, Potions.STRONG_REGENERATION, Potions.STRONG_STRENGTH, Potions.LONG_INVISIBILITY);

    private IllagerTradeOffers() {
    }

    private static ResourceLocation id(String modid, String path) {
        return ResourceLocation.fromNamespaceAndPath(modid, path);
    }

    private static MerchantOffer trade(ItemStack costA, ItemStack costB, ItemStack result, int maxUses) {
        return new MerchantOffer(costA, costB, result, maxUses, 0, DEFAULT_PRICE_MULTIPLIER);
    }

    private static MerchantOffer buyFromPlayer(ItemStack youGive, int emeraldsYouGet, int maxUses) {
        return trade(youGive, ItemStack.EMPTY, new ItemStack(Items.EMERALD, emeraldsYouGet), maxUses);
    }

    private static MerchantOffer sellToPlayer(int emeraldCost, ItemStack youGet, int maxUses) {
        return trade(new ItemStack(Items.EMERALD, emeraldCost), ItemStack.EMPTY, youGet, maxUses);
    }

    private static MerchantOffer sellToPlayerBigCost(int emeraldCost, ItemStack youGet, int maxUses) {
        if (emeraldCost <= MAX_EMERALDS_PER_STACK) {
            return sellToPlayer(emeraldCost, youGet, maxUses);
        }

        int firstCost = Math.min(MAX_EMERALDS_PER_STACK, emeraldCost);
        int secondCost = Math.min(MAX_EMERALDS_PER_STACK, Math.max(0, emeraldCost - firstCost));
        ItemStack costB = secondCost > 0 ? new ItemStack(Items.EMERALD, secondCost) : ItemStack.EMPTY;
        return trade(new ItemStack(Items.EMERALD, firstCost), costB, youGet, maxUses);
    }

    private static MerchantOffer copyOffer(MerchantOffer offer) {
        return new MerchantOffer(offer.createTag());
    }

    private static ItemStack potionStack(int count, Potion potion) {
        ItemStack stack = new ItemStack(Items.POTION, count);
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private static ItemStack splashPotionStack(int count, Potion potion) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION, count);
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private static ItemStack tippedArrowStack(int count, Potion potion) {
        ItemStack stack = new ItemStack(Items.TIPPED_ARROW, count);
        PotionUtils.setPotion(stack, potion);
        return stack;
    }

    private static Potion randomPotion(RandomSource random, int tier) {
        List<Potion> pool = switch (tier) {
            case 3 -> POTION_TIER3;
            case 2 -> POTION_TIER2;
            default -> POTION_TIER1;
        };
        return pool.get(random.nextInt(pool.size()));
    }

    private static MerchantOffer randomPotionOffer(RandomSource random, int emeraldCost, int tier, int maxUses) {
        return sellToPlayer(emeraldCost, potionStack(1, randomPotion(random, tier)), maxUses);
    }

    private static MerchantOffer randomSplashPotionOffer(RandomSource random, int emeraldCost, int tier, int maxUses) {
        List<Potion> pool = switch (tier) {
            case 3 -> List.of(Potions.STRONG_HARMING, Potions.STRONG_POISON, Potions.LONG_WEAKNESS);
            case 2 -> List.of(Potions.HARMING, Potions.POISON, Potions.WEAKNESS, Potions.SLOWNESS);
            default -> List.of(Potions.WEAKNESS, Potions.SLOWNESS);
        };
        return sellToPlayer(emeraldCost, splashPotionStack(1, pool.get(random.nextInt(pool.size()))), maxUses);
    }

    private static MerchantOffer randomTippedArrowsOffer(RandomSource random, int emeraldCost, int tier, int arrowCount, int maxUses) {
        List<Potion> pool = switch (tier) {
            case 3 -> List.of(Potions.POISON, Potions.STRONG_POISON, Potions.WEAKNESS);
            case 2 -> List.of(Potions.POISON, Potions.WEAKNESS, Potions.SLOWNESS);
            default -> List.of(Potions.SLOWNESS, Potions.WEAKNESS);
        };
        return sellToPlayer(emeraldCost, tippedArrowStack(arrowCount, pool.get(random.nextInt(pool.size()))), maxUses);
    }

    private static ItemStack randomEnchantedBook(RandomSource random, int tier) {
        List<Enchantment> pool = switch (tier) {
            case 3 -> List.of(Enchantments.MENDING, Enchantments.SHARPNESS, Enchantments.ALL_DAMAGE_PROTECTION, Enchantments.POWER_ARROWS, Enchantments.UNBREAKING);
            case 2 -> List.of(Enchantments.UNBREAKING, Enchantments.BLOCK_EFFICIENCY, Enchantments.ALL_DAMAGE_PROTECTION, Enchantments.SHARPNESS, Enchantments.POWER_ARROWS);
            default -> List.of(Enchantments.UNBREAKING, Enchantments.ALL_DAMAGE_PROTECTION, Enchantments.POWER_ARROWS, Enchantments.SHARPNESS, Enchantments.BLOCK_EFFICIENCY);
        };

        Enchantment enchantment = pool.get(random.nextInt(pool.size()));
        int maxLevel = enchantment.getMaxLevel();
        int highestRoll = tier <= 1 ? Math.min(2, maxLevel) : maxLevel;
        int level = Math.min(maxLevel, 1 + random.nextInt(Math.max(1, highestRoll)));
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
    }

    private static MerchantOffer randomBookOffer(RandomSource random, int emeraldCost, int tier, int maxUses) {
        return sellToPlayer(emeraldCost, randomEnchantedBook(random, tier), maxUses);
    }

    private static ItemStack modItem(String modid, String path, int count) {
        if (!RaidbornCompat.isEnabled(modid)) return ItemStack.EMPTY;

        Item item = ForgeRegistries.ITEMS.getValue(id(modid, path));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static ItemStack ewmMobEnchantBookFromLoot(ServerPlayer player, ResourceLocation lootTableId) {
        if (player == null || player.server == null) return ItemStack.EMPTY;

        try {
            LootParams params = new LootParams.Builder(player.serverLevel()).create(LootContextParamSets.EMPTY);
            for (ItemStack stack : player.server.getLootData().getLootTable(lootTableId).getRandomItems(params)) {
                if (!stack.isEmpty()) return stack.copy();
            }
        } catch (RuntimeException e) {
            // The table ships with the Enchant With Mob compat pack; a datapack can still break or
            // drop it. Fall through to the plain book rather than losing the whole offer list.
            Raidborn.LOGGER.warn("Loot table {} failed to roll a trade book", lootTableId, e);
        }

        return modItem(ENCHANT_WITH_MOB_MODID, "mob_enchant_book", 1);
    }

    private static void addSellLootBookOffer(MerchantOffers offers, ServerPlayer player, int emeraldCost, ResourceLocation lootTableId, int maxUses) {
        ItemStack book = ewmMobEnchantBookFromLoot(player, lootTableId);
        if (!book.isEmpty()) offers.add(sellToPlayer(emeraldCost, book, maxUses));
    }

    private static void addSellModItemOffer(MerchantOffers offers, int emeraldCost, String modid, String path, int count, int maxUses) {
        ItemStack stack = modItem(modid, path, count);
        if (!stack.isEmpty()) offers.add(sellToPlayer(emeraldCost, stack, maxUses));
    }

    private static void addSellModItemBigCostOffer(MerchantOffers offers, int emeraldCost, String modid, String path, int count, int maxUses) {
        ItemStack stack = modItem(modid, path, count);
        if (!stack.isEmpty()) offers.add(sellToPlayerBigCost(emeraldCost, stack, maxUses));
    }

    private static MerchantOffers accumulateLevels(LevelOfferFactory factory, RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        for (int currentLevel = 1; currentLevel <= level; currentLevel++) {
            for (MerchantOffer offer : factory.create(random, currentLevel)) {
                offers.add(copyOffer(offer));
            }
        }
        return offers;
    }

    @FunctionalInterface
    private interface LevelOfferFactory {
        MerchantOffers create(RandomSource random, int level);
    }

    public static MerchantOffers pillagerOffers(RandomSource random) { return pillagerOffers(random, 1); }
    public static MerchantOffers vindicatorOffers(RandomSource random) { return vindicatorOffers(random, 1); }
    public static MerchantOffers evokerOffers(RandomSource random) { return evokerOffers(random, 1); }
    public static MerchantOffers witchOffers(RandomSource random) { return witchOffers(random, 1); }

    public static MerchantOffers pillagerOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::pillagerOffersForLevel, random, level);
    }

    public static MerchantOffers pillagerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.STRING, 14), 1, 16)); offers.add(sellToPlayer(2, new ItemStack(Items.ARROW, 32), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.FLINT, 26), 1, 12)); offers.add(sellToPlayer(9, new ItemStack(Items.BOW), 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.STICK, 32), 1, 16)); offers.add(sellToPlayer(2, new ItemStack(Items.SPECTRAL_ARROW, 16), 6)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.GUNPOWDER, 16), 2, 8)); offers.add(randomTippedArrowsOffer(random, 4, 2, 8, 4)); }
            case 5 -> {
                offers.add(buyFromPlayer(new ItemStack(Items.FIREWORK_ROCKET, 3), 1, 12));
                offers.add(sellToPlayer(16, new ItemStack(Items.CROSSBOW), 4));
                ItemStack pillagerBag = modItem(WANDERING_BAGS_MODID, "pillager_bag", 1);
                if (!pillagerBag.isEmpty()) offers.add(sellToPlayerBigCost(96, pillagerBag, 1));
            }
        }
        return offers;
    }

    public static MerchantOffers vindicatorOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::vindicatorOffersForLevel, random, level);
    }

    public static MerchantOffers vindicatorOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.COAL, 15), 2, 12)); offers.add(sellToPlayer(9, new ItemStack(Items.IRON_SWORD), 3)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 4), 1, 10)); offers.add(sellToPlayer(24, new ItemStack(Items.IRON_AXE), 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 6), 1, 12)); offers.add(sellToPlayer(8, new ItemStack(Items.SHIELD), 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.BONE, 16), 1, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.GOLDEN_APPLE), 3)); }
            case 5 -> {
                offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3));
                offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_AXE), 2));
                ItemStack voodooDoll = modItem(RAIDBORN_MODID, "voodoo_villager_doll", 1);
                if (!voodooDoll.isEmpty()) offers.add(sellToPlayer(80, voodooDoll, 1));
            }
        }
        return offers;
    }

    public static MerchantOffers evokerOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::evokerOffersForLevel, random, level);
    }

    public static MerchantOffers evokerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 3), 1, 10)); offers.add(randomPotionOffer(random, 8, 2, 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); offers.add(randomBookOffer(random, 18, 2, 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.REDSTONE, 16), 2, 12)); offers.add(randomPotionOffer(random, 14, 3, 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 6)); offers.add(randomBookOffer(random, 28, 3, 2)); }
            case 5 -> {
                offers.add(buyFromPlayer(new ItemStack(Items.BLAZE_POWDER, 8), 4, 8));
                ItemStack conch = modItem(SANDR_MODID, "conch_of_conjuring", 1);
                if (!conch.isEmpty()) offers.add(sellToPlayer(64, conch, 1));
                ItemStack dagger = modItem(RAIDBORN_MODID, "ominous_dagger", 1);
                if (!dagger.isEmpty()) offers.add(sellToPlayer(64, dagger, 1));
                offers.add(trade(new ItemStack(Items.EMERALD, 64), ItemStack.EMPTY, new ItemStack(Items.TOTEM_OF_UNDYING), 1));
            }
        }
        return offers;
    }

    public static MerchantOffers witchOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::witchOffersForLevel, random, level);
    }

    public static MerchantOffers witchOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLASS_BOTTLE, 6), 1, 16)); offers.add(randomPotionOffer(random, 7, 1, 5)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.SUGAR, 24), 1, 16)); offers.add(randomPotionOffer(random, 11, 2, 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.SPIDER_EYE, 16), 2, 16)); offers.add(randomSplashPotionOffer(random, 14, 2, 4)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.NETHER_WART, 16), 2, 10)); offers.add(randomBookOffer(random, 20, 2, 3)); }
            case 5 -> {
                offers.add(buyFromPlayer(new ItemStack(Items.GHAST_TEAR), 8, 3));
                offers.add(randomSplashPotionOffer(random, 22, 3, 2));
                ItemStack witchBag = modItem(WANDERING_BAGS_MODID, "witch_bag", 1);
                if (!witchBag.isEmpty()) offers.add(sellToPlayerBigCost(96, witchBag, 1));
            }
        }
        return offers;
    }

    public static MerchantOffers illusionerOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::illusionerOffersForLevel, random, level);
    }

    public static MerchantOffers illusionerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLASS, 16), 2, 12)); offers.add(sellToPlayer(10, new ItemStack(Items.SPECTRAL_ARROW, 16), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.AMETHYST_SHARD, 10), 2, 12)); offers.add(randomPotionOffer(random, 12, 2, 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLOWSTONE_DUST, 16), 2, 12)); offers.add(randomPotionOffer(random, 16, 3, 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 8)); offers.add(randomSplashPotionOffer(random, 18, 3, 2)); }
            case 5 -> {
                offers.add(randomBookOffer(random, 28, 3, 2));
                ItemStack totem = modItem(FNF_MODID, "totem_of_illusion", 1);
                if (!totem.isEmpty()) offers.add(sellToPlayerBigCost(96, totem, 1));
                ItemStack dust = modItem(IINV_MODID, "illusionary_dust", 6);
                if (!dust.isEmpty()) offers.add(sellToPlayer(18, dust, 2));
            }
        }
        return offers;
    }

    private static boolean sandrLoaded() { return RaidbornCompat.isEnabled(SANDR_MODID); }
    private static ItemStack sandrItem(String path, int count) { return sandrLoaded() ? modItem(SANDR_MODID, path, count) : ItemStack.EMPTY; }

    public static MerchantOffers sandrExecutionerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::sandrExecutionerOffersForLevel, random, level); }
    public static MerchantOffers sandrExecutionerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(sellToPlayer(24, new ItemStack(Items.IRON_AXE), 4)); offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 8), 2, 12)); }
            case 2 -> { offers.add(sellToPlayer(12, new ItemStack(Items.GOLDEN_AXE), 4)); offers.add(buyFromPlayer(new ItemStack(Items.GOLD_INGOT, 8), 2, 12)); }
            case 3 -> { offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_AXE), 4)); offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 12)); }
            case 4 -> { ItemStack cleaver = sandrItem("cleaver_of_beheading", 1); if (!cleaver.isEmpty()) offers.add(sellToPlayerBigCost(96, cleaver, 1)); }
        }
        return offers;
    }

    public static MerchantOffers sandrGrieferOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::sandrGrieferOffersForLevel, random, level); }
    public static MerchantOffers sandrGrieferOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(sellToPlayer(18, new ItemStack(Items.TNT, 2), 3)); offers.add(buyFromPlayer(new ItemStack(Items.GUNPOWDER, 12), 3, 12)); }
            case 2 -> { offers.add(sellToPlayer(8, new ItemStack(Items.FLINT_AND_STEEL, 6), 3)); offers.add(buyFromPlayer(new ItemStack(Items.FIRE_CHARGE, 6), 3, 12)); }
            case 3 -> { offers.add(sellToPlayer(32, new ItemStack(Items.OBSIDIAN, 8), 2)); offers.add(buyFromPlayer(new ItemStack(Items.BLAZE_POWDER, 6), 4, 10)); }
            case 4 -> { ItemStack spores = sandrItem("creeper_spores", 12); if (!spores.isEmpty()) offers.add(sellToPlayer(10, spores, 6)); }
            case 5 -> { ItemStack plating = sandrItem("blast_proof_plating", 1); if (!plating.isEmpty()) offers.add(sellToPlayerBigCost(88, plating, 1)); }
        }
        return offers;
    }

    public static MerchantOffers sandrIceologerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::sandrIceologerOffersForLevel, random, level); }
    public static MerchantOffers sandrIceologerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(sellToPlayer(10, new ItemStack(Items.POWDER_SNOW_BUCKET), 4)); offers.add(buyFromPlayer(new ItemStack(Items.SNOWBALL, 16), 1, 16)); }
            case 2 -> { offers.add(sellToPlayer(2, new ItemStack(Items.PACKED_ICE, 8), 3)); offers.add(buyFromPlayer(new ItemStack(Items.SPRUCE_SAPLING, 8), 2, 12)); }
            case 3 -> { offers.add(sellToPlayer(18, new ItemStack(Items.BLUE_ICE, 16), 4)); offers.add(randomBookOffer(random, 28, 3, 2)); }
            case 4 -> { offers.add(randomPotionOffer(random, 14, 2, 4)); offers.add(randomSplashPotionOffer(random, 18, 3, 2)); }
            case 5 -> { ItemStack wand = sandrItem("wand_of_freezing", 1); if (!wand.isEmpty()) offers.add(sellToPlayerBigCost(96, wand, 1)); }
        }
        return offers;
    }

    public static MerchantOffers sandrTricksterOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::sandrTricksterOffersForLevel, random, level); }
    public static MerchantOffers sandrTricksterOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(randomPotionOffer(random, 8, 1, 6)); offers.add(randomSplashPotionOffer(random, 12, 2, 4)); }
            case 2 -> offers.add(randomBookOffer(random, 18, 2, 4));
            case 3 -> { offers.add(randomPotionOffer(random, 14, 2, 4)); offers.add(randomSplashPotionOffer(random, 18, 3, 2)); }
            case 4 -> { offers.add(randomBookOffer(random, 28, 3, 2)); offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 6)); }
            case 5 -> { ItemStack mask = sandrItem("mask_of_dishonesty", 1); if (!mask.isEmpty()) offers.add(sellToPlayerBigCost(80, mask, 1)); }
        }
        return offers;
    }

    public static MerchantOffers iinvProvokerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::iinvProvokerOffersForLevel, random, level); }
    public static MerchantOffers iinvProvokerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.ARROW, 32), 2, 16)); offers.add(sellToPlayer(10, new ItemStack(Items.BOW), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.STRING, 18), 2, 12)); offers.add(randomPotionOffer(random, 11, 2, 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.FEATHER, 24), 2, 16)); offers.add(sellToPlayer(2, new ItemStack(Items.SPECTRAL_ARROW, 16), 6)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLOWSTONE_DUST, 16), 2, 12)); offers.add(randomTippedArrowsOffer(random, 4, 2, 8, 4)); }
            case 5 -> { offers.add(buyFromPlayer(new ItemStack(Items.FIREWORK_ROCKET, 3), 1, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.CROSSBOW), 4)); }
        }
        return offers;
    }

    public static MerchantOffers iinvBasherOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvBasherOffersForLevel, random, level);
    }

    public static MerchantOffers iinvBasherOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.COAL, 16), 2, 12)); offers.add(sellToPlayer(9, new ItemStack(Items.IRON_SWORD), 3)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 4), 1, 10)); offers.add(sellToPlayer(24, new ItemStack(Items.IRON_AXE), 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 8), 1, 12)); offers.add(sellToPlayer(8, new ItemStack(Items.SHIELD), 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.BONE, 16), 1, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.GOLDEN_APPLE), 3)); }
            case 5 -> { offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_AXE), 2)); }
        }
        return offers;
    }

    public static MerchantOffers iinvSorcererOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvSorcererOffersForLevel, random, level);
    }

    public static MerchantOffers iinvSorcererOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 3), 1, 10)); offers.add(randomPotionOffer(random, 8, 2, 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); offers.add(randomBookOffer(random, 18, 2, 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.REDSTONE, 16), 2, 12)); offers.add(randomPotionOffer(random, 14, 3, 3)); }
            case 4 -> { addSellModItemOffer(offers, 18, IINV_MODID, "unusual_dust", 6, 2); offers.add(randomBookOffer(random, 28, 3, 2)); }
            case 5 -> { addSellModItemOffer(offers, 22, IINV_MODID, "magical_fire_charge", 4, 1); offers.add(randomPotionOffer(random, 18, 3, 2)); }
        }
        return offers;
    }

    public static MerchantOffers iinvArchivistOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvArchivistOffersForLevel, random, level);
    }

    public static MerchantOffers iinvArchivistOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 4), 1, 12)); offers.add(sellToPlayer(8, new ItemStack(Items.BOOKSHELF), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.PAPER, 24), 1, 12)); offers.add(randomBookOffer(random, 18, 2, 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.INK_SAC, 16), 2, 12)); offers.add(sellToPlayer(12, new ItemStack(Items.WRITABLE_BOOK), 4)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); offers.add(randomBookOffer(random, 28, 3, 2)); }
            case 5 -> { addSellModItemBigCostOffer(offers, 80, IINV_MODID, "lost_candle", 1, 1); offers.add(randomPotionOffer(random, 14, 2, 4)); }
        }
        return offers;
    }

    public static MerchantOffers iinvInquisitorOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvInquisitorOffersForLevel, random, level);
    }

    public static MerchantOffers iinvInquisitorOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 8), 2, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.IRON_CHESTPLATE), 2)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 10), 1, 12)); offers.add(sellToPlayer(24, new ItemStack(Items.IRON_SWORD), 3)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.GOLD_INGOT, 8), 2, 10)); offers.add(sellToPlayer(18, new ItemStack(Items.SHIELD), 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); offers.add(sellToPlayer(28, new ItemStack(Items.DIAMOND_CHESTPLATE), 1)); }
            case 5 -> { addSellModItemBigCostOffer(offers, 96, IINV_MODID, "platinum_sheet", 1, 1); offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_SWORD), 1)); }
        }
        return offers;
    }

    public static MerchantOffers iinvMarauderOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvMarauderOffersForLevel, random, level);
    }

    public static MerchantOffers iinvMarauderOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.COAL, 16), 2, 12)); offers.add(sellToPlayer(12, new ItemStack(Items.IRON_AXE), 4)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 4), 1, 10)); offers.add(sellToPlayer(16, new ItemStack(Items.SHIELD), 3)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 8), 1, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.GOLDEN_APPLE), 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.BONE, 16), 1, 12)); offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_AXE), 2)); }
            case 5 -> { addSellModItemBigCostOffer(offers, 96, IINV_MODID, "platinum_infused_hatchet", 1, 1); offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); }
        }
        return offers;
    }

    public static MerchantOffers iinvInvokerOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvInvokerOffersForLevel, random, level);
    }

    public static MerchantOffers iinvInvokerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 3), 1, 10)); offers.add(randomPotionOffer(random, 8, 2, 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); offers.add(randomBookOffer(random, 18, 2, 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.REDSTONE, 16), 2, 12)); offers.add(randomPotionOffer(random, 14, 3, 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 6)); offers.add(randomBookOffer(random, 28, 3, 2)); }
            case 5 -> { addSellModItemBigCostOffer(offers, 96, IINV_MODID, "primal_essence", 1, 1); addSellModItemBigCostOffer(offers, 128, RAIDBORN_MODID, "villager_soul", 1, 1); }
        }
        return offers;
    }

    public static MerchantOffers iinvAlchemistOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvAlchemistOffersForLevel, random, level);
    }

    public static MerchantOffers iinvAlchemistOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLASS_BOTTLE, 8), 1, 16)); offers.add(randomPotionOffer(random, 7, 1, 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.SUGAR, 24), 1, 16)); offers.add(randomPotionOffer(random, 11, 2, 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.SPIDER_EYE, 16), 2, 16)); offers.add(randomSplashPotionOffer(random, 14, 2, 4)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.NETHER_WART, 16), 2, 10)); offers.add(randomBookOffer(random, 20, 2, 3)); }
            case 5 -> { offers.add(buyFromPlayer(new ItemStack(Items.GHAST_TEAR), 8, 3)); offers.add(randomSplashPotionOffer(random, 22, 3, 2)); }
        }
        return offers;
    }

    public static MerchantOffers iinvFirecallerOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvFirecallerOffersForLevel, random, level);
    }

    public static MerchantOffers iinvFirecallerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BLAZE_POWDER, 8), 4, 10)); offers.add(sellToPlayer(12, new ItemStack(Items.FIRE_CHARGE, 8), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.COAL, 24), 2, 12)); offers.add(sellToPlayer(14, new ItemStack(Items.BLAZE_ROD, 2), 4)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.MAGMA_CREAM, 8), 3, 10)); offers.add(randomPotionOffer(random, 14, 2, 4)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.GUNPOWDER, 16), 2, 10)); offers.add(sellToPlayer(18, new ItemStack(Items.FLINT_AND_STEEL), 3)); }
            case 5 -> { offers.add(randomBookOffer(random, 28, 3, 2)); offers.add(sellToPlayer(22, potionStack(1, Potions.FIRE_RESISTANCE), 1)); }
        }
        return offers;
    }

    public static MerchantOffers iinvNecromancerOffers(RandomSource random, int level) {
        return accumulateLevels(IllagerTradeOffers::iinvNecromancerOffersForLevel, random, level);
    }

    public static MerchantOffers iinvNecromancerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BONE, 24), 1, 12)); offers.add(sellToPlayer(10, new ItemStack(Items.ROTTEN_FLESH, 32), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.SPIDER_EYE, 16), 2, 16)); offers.add(randomPotionOffer(random, 11, 2, 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.GUNPOWDER, 16), 2, 10)); offers.add(randomSplashPotionOffer(random, 14, 2, 4)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 8)); offers.add(randomBookOffer(random, 20, 2, 3)); }
            case 5 -> { offers.add(randomBookOffer(random, 28, 3, 2)); offers.add(randomPotionOffer(random, 18, 3, 2)); }
        }
        return offers;
    }

    public static MerchantOffers tapArcherOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::tapArcherOffersForLevel, random, level); }
    public static MerchantOffers tapArcherOffersForLevel(RandomSource random, int level) { return pillagerOffersForLevel(random, level); }

    public static MerchantOffers tapLegionerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::tapLegionerOffersForLevel, random, level); }
    public static MerchantOffers tapLegionerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 8), 2, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.IRON_CHESTPLATE), 2)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 10), 1, 12)); offers.add(sellToPlayer(24, new ItemStack(Items.IRON_SWORD), 3)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.GOLD_INGOT, 8), 2, 10)); offers.add(sellToPlayer(18, new ItemStack(Items.SHIELD), 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); offers.add(sellToPlayer(28, new ItemStack(Items.DIAMOND_CHESTPLATE), 1)); }
            case 5 -> { offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_SWORD), 1)); offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_AXE), 1)); }
        }
        return offers;
    }

    public static MerchantOffers tapSkirmisherOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::tapSkirmisherOffersForLevel, random, level); }
    public static MerchantOffers tapSkirmisherOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 8), 1, 12)); offers.add(sellToPlayer(10, new ItemStack(Items.IRON_AXE), 4)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.BEEF, 16), 2, 12)); offers.add(sellToPlayer(12, new ItemStack(Items.IRON_SWORD), 3)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.PORKCHOP, 16), 2, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.SHIELD), 3)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.MUTTON, 16), 2, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.GOLDEN_APPLE), 3)); }
            case 5 -> { offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_AXE), 2)); }
        }
        return offers;
    }

    public static MerchantOffers guardIllagerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::guardIllagerOffersForLevel, random, level); }
    public static MerchantOffers guardIllagerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.IRON_INGOT, 4), 1, 12)); offers.add(sellToPlayer(12, new ItemStack(Items.IRON_SWORD), 3)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 8), 1, 12)); offers.add(sellToPlayer(16, new ItemStack(Items.SHIELD), 3)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.GOLD_INGOT, 8), 2, 10)); offers.add(sellToPlayer(18, new ItemStack(Items.IRON_CHESTPLATE), 2)); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); offers.add(sellToPlayer(24, new ItemStack(Items.DIAMOND_SWORD), 1)); }
            case 5 -> { addSellModItemBigCostOffer(offers, 96, GI_MODID, "guard_helm", 1, 1); offers.add(sellToPlayer(32, new ItemStack(Items.DIAMOND_CHESTPLATE), 1)); }
        }
        return offers;
    }

    public static MerchantOffers hunterOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::hunterOffersForLevel, random, level); }
    public static MerchantOffers hunterOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.LEATHER, 10), 1, 12)); offers.add(sellToPlayer(8, new ItemStack(Items.IRON_SWORD), 4)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.BEEF, 16), 2, 12)); offers.add(sellToPlayer(10, new ItemStack(Items.BOW), 6)); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.PORKCHOP, 16), 2, 12)); addSellModItemOffer(offers, 18, HR_MODID, "boomerang", 1, 2); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.MUTTON, 16), 2, 12)); addSellModItemOffer(offers, 18, HR_MODID, "mini_crossbow", 1, 2); }
            case 5 -> { offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3)); offers.add(sellToPlayer(16, new ItemStack(Items.CROSSBOW), 2)); }
        }
        return offers;
    }

    public static MerchantOffers conjurerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::conjurerOffersForLevel, random, level); }
    public static MerchantOffers conjurerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 3), 1, 10)); offers.add(randomPotionOffer(random, 8, 2, 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); addSellModItemOffer(offers, 12, CONJ_MODID, "throwable_ball", 4, 4); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.REDSTONE, 16), 2, 12)); addSellModItemOffer(offers, 14, CONJ_MODID, "throwing_card", 8, 3); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 6)); offers.add(randomBookOffer(random, 28, 3, 2)); }
            case 5 -> { addSellModItemBigCostOffer(offers, 96, CONJ_MODID, "conjurer_hat", 1, 1); offers.add(randomPotionOffer(random, 18, 3, 2)); }
        }
        return offers;
    }

    public static MerchantOffers ewmEnchanterOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::ewmEnchanterOffersForLevel, random, level); }
    public static MerchantOffers ewmEnchanterOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.PAPER, 24), 1, 12)); offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 3), 1, 10)); offers.add(sellToPlayer(8, new ItemStack(Items.BOOKSHELF), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); offers.add(sellToPlayer(6, new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 8)); addSellModItemOffer(offers, 24, ENCHANT_WITH_MOB_MODID, "mob_enchant_book", 1, 4); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.AMETHYST_SHARD, 8), 2, 12)); addSellModItemOffer(offers, 32, ENCHANT_WITH_MOB_MODID, "mob_enchant_book", 1, 3); addSellModItemOffer(offers, 24, ENCHANT_WITH_MOB_MODID, "mob_unenchant_book", 1, 2); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLOWSTONE_DUST, 16), 2, 12)); offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 8)); addSellModItemOffer(offers, 40, ENCHANT_WITH_MOB_MODID, "mob_enchant_book", 1, 2); }
            case 5 -> {
                offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3));
                addSellModItemOffer(offers, 56, ENCHANT_WITH_MOB_MODID, "mob_enchant_book", 1, 2);
                addSellModItemBigCostOffer(offers, 72, ENCHANT_WITH_MOB_MODID, "mob_unenchant_book", 1, 1);
                addSellModItemBigCostOffer(offers, 80, ENCHANT_WITH_MOB_MODID, "enchanter_hat", 1, 1);
                addSellModItemBigCostOffer(offers, 96, ENCHANT_WITH_MOB_MODID, "enchanter_clothes", 1, 1);
                addSellModItemBigCostOffer(offers, 80, ENCHANT_WITH_MOB_MODID, "enchanter_boots", 1, 1);
            }
        }
        return offers;
    }

    public static MerchantOffers ewmEnchanterOffers(ServerPlayer player, int level) {
        MerchantOffers offers = new MerchantOffers();
        int clampedLevel = Math.max(1, Math.min(level, 5));
        for (int currentLevel = 1; currentLevel <= clampedLevel; currentLevel++) {
            for (MerchantOffer offer : ewmEnchanterOffersForLevel(player, currentLevel)) {
                offers.add(copyOffer(offer));
            }
        }
        return offers;
    }

    public static MerchantOffers ewmEnchanterOffersForLevel(ServerPlayer player, int level) {
        MerchantOffers offers = new MerchantOffers();
        switch (level) {
            case 1 -> { offers.add(buyFromPlayer(new ItemStack(Items.PAPER, 24), 1, 12)); offers.add(buyFromPlayer(new ItemStack(Items.BOOK, 3), 1, 10)); offers.add(sellToPlayer(8, new ItemStack(Items.BOOKSHELF), 6)); }
            case 2 -> { offers.add(buyFromPlayer(new ItemStack(Items.LAPIS_LAZULI, 16), 2, 12)); offers.add(sellToPlayer(6, new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 8)); addSellLootBookOffer(offers, player, 24, EWM_TRADE_BOOK_LOW, 4); }
            case 3 -> { offers.add(buyFromPlayer(new ItemStack(Items.AMETHYST_SHARD, 8), 2, 12)); addSellLootBookOffer(offers, player, 32, EWM_TRADE_BOOK_MID, 3); addSellModItemOffer(offers, 24, ENCHANT_WITH_MOB_MODID, "mob_unenchant_book", 1, 2); }
            case 4 -> { offers.add(buyFromPlayer(new ItemStack(Items.GLOWSTONE_DUST, 16), 2, 12)); offers.add(buyFromPlayer(new ItemStack(Items.ENDER_PEARL, 4), 2, 8)); addSellLootBookOffer(offers, player, 40, EWM_TRADE_BOOK_HIGH, 2); }
            case 5 -> {
                offers.add(buyFromPlayer(new ItemStack(Items.DIAMOND), 12, 3));
                addSellLootBookOffer(offers, player, 56, EWM_TRADE_BOOK_HIGH, 2);
                addSellModItemBigCostOffer(offers, 16, ENCHANT_WITH_MOB_MODID, "enchanter_hat", 1, 1);
                addSellModItemBigCostOffer(offers, 32, ENCHANT_WITH_MOB_MODID, "enchanter_clothes", 1, 1);
                addSellModItemBigCostOffer(offers, 16, ENCHANT_WITH_MOB_MODID, "enchanter_boots", 1, 1);
            }
        }
        return offers;
    }

    public static MerchantOffers cabbagerOffers(RandomSource random, int level) { return accumulateLevels(IllagerTradeOffers::cabbagerOffersForLevel, random, level); }
    public static MerchantOffers cabbagerOffersForLevel(RandomSource random, int level) {
        MerchantOffers offers = new MerchantOffers();
        ItemStack cabbage = modItem(RAVAGE_AND_CABBAGE_MODID, "cabbage", 1);
        ItemStack cabbageSeeds = modItem(RAVAGE_AND_CABBAGE_MODID, "cabbage_seeds", 1);
        ItemStack corruptedCabbage = modItem(RAVAGE_AND_CABBAGE_MODID, "corrupted_cabbage", 1);
        ItemStack ravagerMilk = modItem(RAVAGE_AND_CABBAGE_MODID, "ravager_milk", 1);

        switch (level) {
            case 1 -> { if (!cabbageSeeds.isEmpty()) offers.add(sellToPlayer(2, new ItemStack(cabbageSeeds.getItem(), 6), 16)); offers.add(buyFromPlayer(new ItemStack(Items.WHEAT, 20), 1, 16)); }
            case 2 -> { if (!cabbage.isEmpty()) offers.add(buyFromPlayer(new ItemStack(cabbage.getItem(), 24), 2, 12)); offers.add(sellToPlayer(4, new ItemStack(Items.BONE_MEAL, 12), 12)); }
            case 3 -> { if (!cabbageSeeds.isEmpty()) offers.add(sellToPlayer(6, new ItemStack(cabbageSeeds.getItem(), 12), 10)); offers.add(buyFromPlayer(new ItemStack(Items.PUMPKIN, 6), 1, 12)); }
            case 4 -> { if (!corruptedCabbage.isEmpty()) offers.add(sellToPlayer(18, new ItemStack(corruptedCabbage.getItem(), 2), 6)); if (!cabbage.isEmpty()) offers.add(buyFromPlayer(new ItemStack(cabbage.getItem(), 32), 3, 10)); }
            case 5 -> { if (!ravagerMilk.isEmpty()) offers.add(sellToPlayerBigCost(64, new ItemStack(ravagerMilk.getItem()), 2)); if (!corruptedCabbage.isEmpty()) offers.add(sellToPlayer(32, new ItemStack(corruptedCabbage.getItem(), 4), 4)); }
        }
        return offers;
    }
}
