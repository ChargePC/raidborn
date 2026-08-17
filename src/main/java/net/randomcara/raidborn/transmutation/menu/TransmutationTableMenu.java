package net.randomcara.raidborn.transmutation.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.randomcara.raidborn.core.registry.ModMenuTypes;
import net.randomcara.raidborn.transmutation.blockentity.TransmutationTableBlockEntity;
import org.jetbrains.annotations.NotNull;

public class TransmutationTableMenu extends AbstractContainerMenu {
    private static final int FUEL_SLOT = 0;
    private static final int SOUL_SLOT = 1;
    private static final int INPUT_SLOT = 2;

    private static final int MACHINE_SLOT_COUNT = 3;
    private static final int PLAYER_INV_START = 3;
    private static final int PLAYER_INV_END = 30;
    private static final int HOTBAR_START = 30;
    private static final int HOTBAR_END = 39;

    private final TransmutationTableBlockEntity blockEntity;
    private final ContainerData data;

    public TransmutationTableMenu(int containerId, Inventory inventory, FriendlyByteBuf extraData) {
        this(
                containerId,
                inventory,
                (TransmutationTableBlockEntity) inventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(3)
        );
    }

    public TransmutationTableMenu(int containerId, Inventory inventory, TransmutationTableBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.TRANSMUTATION_TABLE_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        this.addDataSlots(data);

        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(this::addMachineSlots);
        addPlayerInventory(inventory);
        addPlayerHotbar(inventory);
    }

    private void addMachineSlots(IItemHandler handler) {
        this.addSlot(new SlotItemHandler(handler, FUEL_SLOT, 17, 17) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return TransmutationTableBlockEntity.isFuel(stack);
            }
        });

        this.addSlot(new SlotItemHandler(handler, SOUL_SLOT, 79, 17) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return TransmutationTableBlockEntity.isSoul(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public int getMaxStackSize(@NotNull ItemStack stack) {
                return 1;
            }
        });

        this.addSlot(new SlotItemHandler(handler, INPUT_SLOT, 79, 58) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public int getMaxStackSize(@NotNull ItemStack stack) {
                return 1;
            }
        });
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);

        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (!this.moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveStackToMachine(sourceStack)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return copy;
    }

    private boolean moveStackToMachine(ItemStack sourceStack) {
        if (TransmutationTableBlockEntity.isFuel(sourceStack)) {
            return this.moveItemStackTo(sourceStack, FUEL_SLOT, FUEL_SLOT + 1, false);
        }

        if (TransmutationTableBlockEntity.isSoul(sourceStack)) {
            return moveOneItemToMachineSlot(sourceStack, SOUL_SLOT);
        }

        return moveOneItemToMachineSlot(sourceStack, INPUT_SLOT);
    }

    private boolean moveOneItemToMachineSlot(ItemStack sourceStack, int slotIndex) {
        Slot targetSlot = this.slots.get(slotIndex);

        if (targetSlot == null || targetSlot.hasItem() || !targetSlot.mayPlace(sourceStack)) {
            return false;
        }

        ItemStack singleItem = sourceStack.copy();
        singleItem.setCount(1);

        targetSlot.set(singleItem);
        targetSlot.setChanged();
        sourceStack.shrink(1);

        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null) {
            return false;
        }

        return player.distanceToSqr(
                this.blockEntity.getBlockPos().getX() + 0.5D,
                this.blockEntity.getBlockPos().getY() + 0.5D,
                this.blockEntity.getBlockPos().getZ() + 0.5D
        ) <= 64.0D;
    }

    public boolean isCrafting() {
        return this.data.get(0) > 0;
    }

    public boolean hasFuel() {
        return this.data.get(1) > 0;
    }

    public int getScaledProgress() {
        int progress = this.data.get(0);
        int maxProgress = this.data.get(2);
        int progressArrowSize = 28;

        return maxProgress > 0 && progress > 0 ? progress * progressArrowSize / maxProgress : 0;
    }

    public int getFuelWidth() {
        int fuel = this.data.get(1);
        return fuel > 0 ? Mth.clamp(fuel * 18, 0, 18) : 0;
    }

    public int getBubbleHeight() {
        if (!isCrafting()) {
            return 0;
        }

        int[] bubbleSizes = new int[]{29, 24, 20, 16, 11, 6, 0};
        return bubbleSizes[(this.data.get(0) / 2) % bubbleSizes.length];
    }
}
