package net.randomcara.raidborn.gameplay.trade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModSounds;
import net.randomcara.raidborn.core.util.RaidbornAdvancements;

import javax.annotation.Nullable;

public class IllagerMerchant implements Merchant {
    private static final ResourceLocation ADV_FIRST_TRADE = ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "totally_legal_business");
    private static final String CRIT_FIRST_TRADE = "first_trade";

    private static final float ACCEPT_VOLUME = 1.0F;
    private static final float ACCEPT_PITCH = 1.0F;
    private static final float XP_SOUND_VOLUME = 0.5F;
    private static final float XP_SOUND_PITCH = 1.0F;

    private final MerchantOffers offers;
    private final Mob boundMob;
    private final String type;

    @Nullable
    private Player tradingPlayer;

    public IllagerMerchant(MerchantOffers offers, Mob boundMob, String type) {
        this.offers = offers;
        this.boundMob = boundMob;
        this.type = type;
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        return this.offers;
    }

    public void replaceOffers(MerchantOffers newOffers) {
        if (newOffers == this.offers) return;
        this.offers.clear();
        this.offers.addAll(newOffers);
    }

    @Override
    public void overrideOffers(@Nullable MerchantOffers newOffers) {
        if (newOffers != null) {
            replaceOffers(newOffers);
        }
    }

    @Nullable
    private ServerPlayer getServerTradingPlayer() {
        return this.tradingPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private boolean hasActiveMerchantState() {
        return this.boundMob != null && this.boundMob.isAlive() && IllagerTradeEvents.canTradeWithIllager(this.boundMob);
    }

    private SoundEvent randomAcceptSound(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> ModSounds.ILLAGER_ACCEPT1.get();
            case 1 -> ModSounds.ILLAGER_ACCEPT2.get();
            default -> ModSounds.ILLAGER_ACCEPT3.get();
        };
    }

    private void playMerchantSound(SoundEvent sound, float volume, float pitch) {
        ServerPlayer player = getServerTradingPlayer();
        if (player == null || !hasActiveMerchantState()) return;

        player.level().playSound(null, this.boundMob.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public void playSelectSound() {
        ServerPlayer player = getServerTradingPlayer();
        if (player != null && hasActiveMerchantState()) {
            playMerchantSound(randomAcceptSound(player), ACCEPT_VOLUME, ACCEPT_PITCH);
        }
    }

    public void playTradeSound() {
        ServerPlayer player = getServerTradingPlayer();
        if (player == null || !hasActiveMerchantState()) return;

        playMerchantSound(randomAcceptSound(player), ACCEPT_VOLUME, ACCEPT_PITCH);
        playMerchantSound(SoundEvents.EXPERIENCE_ORB_PICKUP, XP_SOUND_VOLUME, XP_SOUND_PITCH);
    }

    private static int getEmeraldCost(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.EMERALD) ? stack.getCount() : 0;
    }

    private static int computeTradeXp(@Nullable MerchantOffer offer, int levelBefore) {
        if (offer == null) return 3;

        ItemStack costA = offer.getBaseCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();
        int emeraldCost = getEmeraldCost(costA) + getEmeraldCost(costB);
        boolean hasSecondEmeraldCost = getEmeraldCost(costB) > 0;
        boolean uniqueTrade = IllagerTradeEvents.isUniqueOffer(offer);
        int xp = 3;

        if (emeraldCost > 0) {
            xp += Math.max(1, emeraldCost / 4);
            if (emeraldCost >= 16) xp += 1;
            if (emeraldCost >= 24) xp += 2;
            if (emeraldCost >= 32) xp += 3;
            if (emeraldCost >= 48) xp += 5;
            if (emeraldCost >= 64) xp += 8;
            if (emeraldCost >= 96) xp += 12;
        } else {
            xp += 2;
        }

        if (hasSecondEmeraldCost) xp += 5;

        if (!result.isEmpty()) {
            int resultCount = result.getCount();
            if (resultCount >= 8) xp += 1;
            if (resultCount >= 16) xp += 1;
            if (resultCount >= 32) xp += 2;
        }

        int maxUses = offer.getMaxUses();
        if (maxUses <= 1) xp += 12;
        else if (maxUses == 2) xp += 8;
        else if (maxUses == 3) xp += 6;
        else if (maxUses == 4) xp += 4;
        else if (maxUses <= 6) xp += 2;
        if (uniqueTrade) xp += 14;

        double multiplier = switch (levelBefore) {
            case 1 -> 1.00D;
            case 2 -> 1.20D;
            case 3 -> 1.50D;
            case 4 -> 1.90D;
            default -> 2.35D;
        };

        xp = (int) Math.round(xp * multiplier);
        switch (levelBefore) {
            case 3 -> xp += 2;
            case 4 -> xp += 6;
            case 5 -> xp += 10;
        }

        return Math.max(3, Math.min(95, xp));
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        if (offer == null) return;

        ServerPlayer player = getServerTradingPlayer();
        if (player == null || !hasActiveMerchantState()) {
            if (player != null) player.closeContainer();
            return;
        }

        offer.increaseUses();
        playTradeSound();
        player.giveExperiencePoints(1);
        RaidbornAdvancements.award(player, ADV_FIRST_TRADE, CRIT_FIRST_TRADE);

        int levelBefore = IllagerTradeEvents.getLevel(this.boundMob);
        int xpGained = computeTradeXp(offer, levelBefore);
        boolean leveledUp = IllagerTradeEvents.addXpAndMaybeLevelUp(this.boundMob, xpGained);
        int levelNow = IllagerTradeEvents.getLevel(this.boundMob);
        int xpNow = IllagerTradeEvents.getXp(this.boundMob);

        if (leveledUp) {
            MerchantOffers upgradedOffers = IllagerTradeEvents.appendNewLevelOffers(this.boundMob, this.type, this.offers, player, levelBefore, levelNow);
            replaceOffers(upgradedOffers);
            if (IllagerTradeEvents.hasGigaEmeraldEquipped(player)) {
                IllagerTradeEvents.applyGigaEmeraldReputationDiscounts(this.offers);
            }
        }

        IllagerTradeEvents.saveOffersToBucket(this.boundMob, this.type, this.offers, player.level().getGameTime());

        if (player.containerMenu instanceof MerchantMenu menu) {
            player.sendMerchantOffers(menu.containerId, this.offers, levelNow, xpNow, true, false);
            player.containerMenu.broadcastChanges();
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return this.boundMob == null ? 0 : IllagerTradeEvents.getXp(this.boundMob);
    }

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return this.tradingPlayer != null && this.tradingPlayer.level().isClientSide;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return ModSounds.ILLAGER_ACCEPT1.get();
    }

    @Override
    public void overrideXp(int xp) {
        if (this.boundMob != null) {
            this.boundMob.getPersistentData().putInt(IllagerTradeEvents.TAG_XP, xp);
        }
    }
}
