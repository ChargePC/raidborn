package net.randomcara.raidborn.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.randomcara.bentoslib.client.render.area.AreaVisualClient;
import net.randomcara.raidborn.core.registry.ModItems;

import java.util.function.Supplier;

public class ItemActivationPacket {
    private final ItemStack stack;

    public ItemActivationPacket(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(ItemActivationPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static ItemActivationPacket decode(FriendlyByteBuf buf) {
        return new ItemActivationPacket(buf.readItem());
    }

    public static void handle(ItemActivationPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && !msg.stack.is(ModItems.ANYWHERE_PILLOW.get())) {
                minecraft.gameRenderer.displayItemActivation(msg.stack);
                AreaVisualClient.startFromActivatedStack(minecraft.player, msg.stack);
            }
        });
        ctx.setPacketHandled(true);
    }
}
