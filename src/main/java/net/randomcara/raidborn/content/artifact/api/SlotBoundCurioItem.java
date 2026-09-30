package net.randomcara.raidborn.content.artifact.api;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public interface SlotBoundCurioItem extends ICurioItem {
    String CHARM = "charm";

    String RING = "ring";

    String NECKLACE = "necklace";

    String curioSlot();

    @Override
    default boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return curioSlot().equals(slotContext.identifier());
    }
}
