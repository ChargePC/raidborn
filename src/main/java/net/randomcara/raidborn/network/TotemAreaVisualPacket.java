package net.randomcara.raidborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.randomcara.bentoslib.client.render.area.AreaVisualClient;
import net.randomcara.raidborn.core.config.RaidbornClientConfig;

import java.util.UUID;
import java.util.function.Supplier;

public class TotemAreaVisualPacket {
    private final UUID ownerId;
    private final int argbColor;
    private final float halfSize;
    private final int durationTicks;

    public TotemAreaVisualPacket(UUID ownerId, int argbColor, float halfSize, int durationTicks) {
        this.ownerId = ownerId;
        this.argbColor = argbColor;
        this.halfSize = halfSize;
        this.durationTicks = durationTicks;
    }

    public static void encode(TotemAreaVisualPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.ownerId);
        buf.writeInt(msg.argbColor);
        buf.writeFloat(msg.halfSize);
        buf.writeVarInt(msg.durationTicks);
    }

    public static TotemAreaVisualPacket decode(FriendlyByteBuf buf) {
        return new TotemAreaVisualPacket(buf.readUUID(), buf.readInt(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(TotemAreaVisualPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (RaidbornClientConfig.showTotemAreaVisual()) {
                AreaVisualClient.start(msg.ownerId, msg.argbColor, msg.halfSize, msg.durationTicks);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
