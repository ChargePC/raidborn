package net.randomcara.raidborn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.randomcara.raidborn.core.registry.ModItems;

public class ClientPacketHandler {
    public static void handleItemActivation(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !stack.is(ModItems.ANYWHERE_PILLOW.get())) {
            minecraft.gameRenderer.displayItemActivation(stack);
        }
    }
}
