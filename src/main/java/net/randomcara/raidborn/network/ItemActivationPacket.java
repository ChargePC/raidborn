package net.randomcara.raidborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.randomcara.raidborn.client.ClientPacketHandler;

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
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleItemActivation(msg.stack)));
        ctx.setPacketHandled(true);
    }
}
