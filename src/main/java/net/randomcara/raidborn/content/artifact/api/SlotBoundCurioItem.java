package net.randomcara.raidborn.content.artifact.api;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * A curio that belongs in exactly one slot.
 *
 * <p>Curios asks every item about every slot, so each artifact used to carry its own copy of the
 * same identifier check. The check lives here now and the item only names its slot.
 */
public interface SlotBoundCurioItem extends ICurioItem {

    String CHARM = "charm";

    String RING = "ring";

    String NECKLACE = "necklace";

    /**
     * The Curios slot identifier this item accepts.
     */
    String curioSlot();

    @Override
    default boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return curioSlot().equals(slotContext.identifier());
    }
}
