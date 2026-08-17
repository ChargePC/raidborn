package net.randomcara.raidborn.transmutation.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModBlockEntities;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.transmutation.menu.TransmutationTableMenu;
import net.randomcara.raidborn.transmutation.recipe.TransmutationRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class TransmutationTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_SLOT = 0;
    public static final int SOUL_SLOT = 1;
    public static final int INPUT_SLOT = 2;

    private static final int MAX_FUEL = 1;

    private static int getMaxProgress() {
        return RaidbornServerConfig.getTransmutationCraftTimeTicks();
    }

    private final ItemStackHandler itemHandler = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SOUL_SLOT || slot == INPUT_SLOT ? 1 : super.getSlotLimit(slot);
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    private int progress;
    private int fuel;

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> fuel;
                case 2 -> getMaxProgress();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> fuel = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public TransmutationTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSMUTATION_TABLE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.raidborn.transmutation_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TransmutationTableMenu(containerId, inventory, this, this.data);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.lazyItemHandler = LazyOptional.of(() -> this.itemHandler);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.lazyItemHandler.invalidate();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return this.lazyItemHandler.cast();
        }

        return super.getCapability(cap, side);
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(this.itemHandler.getSlots());

        for (int i = 0; i < this.itemHandler.getSlots(); i++) {
            inventory.setItem(i, this.itemHandler.getStackInSlot(i));
        }

        if (this.level != null) {
            Containers.dropContents(this.level, this.worldPosition, inventory);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.put("inventory", this.itemHandler.serializeNBT());
        tag.putInt("transmutation_table.progress", this.progress);
        tag.putInt("transmutation_table.fuel", this.fuel);
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.itemHandler.deserializeNBT(tag.getCompound("inventory"));
        this.progress = tag.getInt("transmutation_table.progress");
        this.fuel = tag.getInt("transmutation_table.fuel");
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TransmutationTableBlockEntity table) {
        if (level.isClientSide) {
            return;
        }

        boolean changed = false;

        if (table.fuel <= 0 && isFuel(table.itemHandler.getStackInSlot(FUEL_SLOT))) {
            table.itemHandler.extractItem(FUEL_SLOT, 1, false);
            table.fuel = MAX_FUEL;
            changed = true;
        }

        if (table.hasRecipe() && table.fuel > 0) {
            table.progress++;
            changed = true;

            if (table.progress >= getMaxProgress()) {
                boolean crafted = table.craftItem();
                table.progress = 0;

                if (crafted) {
                    table.fuel--;
                    level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                } else {
                    level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 0.8F);
                }
            }
        } else if (table.progress != 0) {
            table.progress = 0;
            changed = true;
        }

        if (changed) {
            table.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean hasRecipe() {
        if (this.level == null) {
            return false;
        }

        return getRecipe().isPresent();
    }

    private boolean craftItem() {
        if (this.level == null) {
            return false;
        }

        Optional<TransmutationRecipe> match = getRecipe();
        if (match.isEmpty()) {
            return false;
        }

        ItemStack result = match.get().getTransmutationResult(this.level);
        if (result.isEmpty()) {
            return false;
        }

        this.itemHandler.extractItem(SOUL_SLOT, 1, false);
        this.itemHandler.setStackInSlot(INPUT_SLOT, result.copy());
        return true;
    }

    private Optional<TransmutationRecipe> getRecipe() {
        if (this.level == null) {
            return Optional.empty();
        }

        return this.level.getRecipeManager().getRecipeFor(TransmutationRecipe.Type.INSTANCE, createRecipeContainer(), this.level);
    }

    private SimpleContainer createRecipeContainer() {
        SimpleContainer inventory = new SimpleContainer(2);
        inventory.setItem(0, this.itemHandler.getStackInSlot(SOUL_SLOT));
        inventory.setItem(1, this.itemHandler.getStackInSlot(INPUT_SLOT));
        return inventory;
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(Items.AMETHYST_SHARD);
    }

    public static boolean isSoul(ItemStack stack) {
        return stack.is(ModItems.VILLAGER_SOUL.get())
                || stack.is(ModItems.SOUL_OF_MANY_VILLAGERS.get())
                || stack.is(ModItems.VILLAGE_SOUL.get());
    }
}
