package net.randomcara.raidborn.gameplay.trade;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;

public class IllagerMerchantMenu extends MerchantMenu {
    private static final int NO_SELECTION = -1;

    @Nullable
    private final IllagerMerchant illagerMerchant;
    private int lastSelection = NO_SELECTION;

    public IllagerMerchantMenu(int id, Inventory inventory, Merchant merchant) {
        super(id, inventory, merchant);
        this.illagerMerchant = merchant instanceof IllagerMerchant illager ? illager : null;
    }

    @Override
    public void setSelectionHint(int index) {
        super.setSelectionHint(index);

        MerchantOffers offers = this.getOffers();
        if (!isValidSelection(index, offers)) {
            this.lastSelection = NO_SELECTION;
            return;
        }

        if (index == this.lastSelection) return;
        this.lastSelection = index;

        MerchantOffer selectedOffer = offers.get(index);
        if (!selectedOffer.isOutOfStock() && this.illagerMerchant != null) {
            this.illagerMerchant.playSelectSound();
        }
    }

    private static boolean isValidSelection(int index, @Nullable MerchantOffers offers) {
        return offers != null && index >= 0 && index < offers.size();
    }
}
