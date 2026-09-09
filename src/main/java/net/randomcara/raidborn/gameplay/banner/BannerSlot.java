package net.randomcara.raidborn.gameplay.banner;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

public class BannerSlot {
    public static final String IDENTIFIER = "back";
    private static final int INDEX = 0;

    public static ItemStack get(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve().flatMap(handler -> handler.findCurio(IDENTIFIER, INDEX)).map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }

    public static boolean isWearingBanner(LivingEntity entity) {
        return get(entity).getItem() instanceof BannerItem;
    }

    public static boolean set(LivingEntity entity, ItemStack stack) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity).resolve();
        if (inventory.isEmpty() || inventory.get().getStacksHandler(IDENTIFIER).isEmpty()) {
            return false;
        }

        inventory.get().setEquippedCurio(IDENTIFIER, INDEX, stack);
        return true;
    }
}
