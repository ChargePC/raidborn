package net.randomcara.raidborn.content.entity.beast.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.randomcara.raidborn.content.entity.beast.Beast;
import net.randomcara.raidborn.core.registry.ModMenuTypes;
import org.jetbrains.annotations.Nullable;

public class BeastInventoryMenu extends AbstractContainerMenu {
    private static final int BEAST_SLOT_COUNT = 15;

    private static final int PLAYER_INVENTORY_START = BEAST_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private static final int SLOT_SPACING = 18;

    private static final int BEAST_SLOTS_X = 80;
    private static final int BEAST_SLOTS_Y = 18;

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;

    private static final int HOTBAR_X = 8;
    private static final int HOTBAR_Y = 142;

    private final Beast beast;

    public BeastInventoryMenu(
            int containerId,
            Inventory playerInventory,
            FriendlyByteBuf extraData
    ) {
        this(
                containerId,
                playerInventory,
                findBeast(playerInventory.player, extraData.readVarInt())
        );
    }

    public BeastInventoryMenu(
            int containerId,
            Inventory playerInventory,
            @Nullable Beast beast
    ) {
        super(ModMenuTypes.BEAST_INVENTORY_MENU.get(), containerId);
        this.beast = beast;

        ItemStackHandler handler = beast != null
                ? beast.getBeastInventory()
                : new ItemStackHandler(BEAST_SLOT_COUNT);

        addBeastSlots(handler);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        if (beast != null && !playerInventory.player.level().isClientSide) {
            beast.startInventoryOpen(playerInventory.player);
        }
    }

    @Nullable
    private static Beast findBeast(Player player, int entityId) {
        Entity entity = player.level().getEntity(entityId);
        return entity instanceof Beast foundBeast ? foundBeast : null;
    }

    private void addBeastSlots(ItemStackHandler handler) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                int index = column + row * 5;
                int x = BEAST_SLOTS_X + column * SLOT_SPACING;
                int y = BEAST_SLOTS_Y + row * SLOT_SPACING;

                this.addSlot(new SlotItemHandler(handler, index, x, y));
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                int x = PLAYER_INVENTORY_X + column * SLOT_SPACING;
                int y = PLAYER_INVENTORY_Y + row * SLOT_SPACING;

                this.addSlot(new Slot(inventory, index, x, y));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int column = 0; column < 9; column++) {
            int x = HOTBAR_X + column * SLOT_SPACING;
            this.addSlot(new Slot(inventory, column, x, HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copiedStack = sourceStack.copy();

        if (index < BEAST_SLOT_COUNT) {
            if (!this.moveItemStackTo(
                    sourceStack,
                    PLAYER_INVENTORY_START,
                    HOTBAR_END,
                    true
            )) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(
                sourceStack,
                0,
                BEAST_SLOT_COUNT,
                false
        )) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return copiedStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.beast != null
                && this.beast.isAlive()
                && this.beast.isCreator(player)
                && !this.beast.isInCombat()
                && player.distanceToSqr(this.beast) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (this.beast != null && !player.level().isClientSide) {
            this.beast.stopInventoryOpen(player);
        }
    }

    @Nullable
    public Beast getBeast() {
        return this.beast;
    }
}
