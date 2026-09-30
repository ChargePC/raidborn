package net.randomcara.raidborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.randomcara.raidborn.client.hud.RecruitTooltipOverlay;

import java.util.function.Supplier;

public class RecruitTooltipDataPacket {
    private final int entityId;
    private final boolean valid;
    private final int hp;
    private final int slots;
    private final boolean recruited;

    public RecruitTooltipDataPacket(int entityId, boolean valid, int hp, int slots, boolean recruited) {
        this.entityId = entityId;
        this.valid = valid;
        this.hp = hp;
        this.slots = slots;
        this.recruited = recruited;
    }

    public static void encode(RecruitTooltipDataPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.valid);
        buf.writeVarInt(msg.hp);
        buf.writeVarInt(msg.slots);
        buf.writeBoolean(msg.recruited);
    }

    public static RecruitTooltipDataPacket decode(FriendlyByteBuf buf) {
        return new RecruitTooltipDataPacket(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(RecruitTooltipDataPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecruitTooltipOverlay.handleTooltipData(msg.entityId, msg.valid, msg.hp, msg.slots, msg.recruited)));
        ctx.setPacketHandled(true);
    }
}
