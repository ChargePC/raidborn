package net.randomcara.raidborn.gameplay.banner;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

/**
 * The banner lives in the Curios "back" slot. It used to sit in the chest armour slot, which meant
 * leading a squad cost you your chestplate, and nothing rendered there anyway.
 *
 * <p>Which items go in the slot is data, not code: {@code data/curios/tags/items/back.json}.
 */
public final class BannerSlot {
    public static final String IDENTIFIER = "back";

    /** One back slot, so the banner is always index 0. */
    private static final int INDEX = 0;

    private BannerSlot() {
    }

    public static ItemStack get(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .flatMap(handler -> handler.findCurio(IDENTIFIER, INDEX))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static boolean isWearingBanner(LivingEntity entity) {
        return get(entity).getItem() instanceof BannerItem;
    }

    /**
     * False when the entity has no back slot at all, so callers can leave the stack they were about
     * to move where it is instead of dropping it into nothing.
     */
    public static boolean set(LivingEntity entity, ItemStack stack) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity).resolve();

        if (inventory.isEmpty() || inventory.get().getStacksHandler(IDENTIFIER).isEmpty()) {
            return false;
        }

        inventory.get().setEquippedCurio(IDENTIFIER, INDEX, stack);
        return true;
    }
}
