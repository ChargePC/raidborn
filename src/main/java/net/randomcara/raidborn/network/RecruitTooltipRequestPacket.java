package net.randomcara.raidborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;
import net.randomcara.raidborn.gameplay.recruit.RecruitSlots;
import net.randomcara.raidborn.gameplay.recruit.RecruitmentEvents;

import java.util.function.Supplier;

public class RecruitTooltipRequestPacket {
    private static final double REACH_SQR = 6.0D * 6.0D;

    private final int entityId;

    public RecruitTooltipRequestPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(RecruitTooltipRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static RecruitTooltipRequestPacket decode(FriendlyByteBuf buf) {
        return new RecruitTooltipRequestPacket(buf.readVarInt());
    }

    public static void handle(RecruitTooltipRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            Entity entity = player.isShiftKeyDown() ? player.level().getEntity(msg.entityId) : null;
            if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved() && player.distanceToSqr(mob) <= REACH_SQR && RecruitmentEvents.isRecruitableTooltipTarget(mob)) {
                int hp = Math.max(1, Mth.ceil(mob.getMaxHealth()));
                int slots = Math.max(1, RecruitSlots.getRecruitCost(mob));
                sendTooltip(player, new RecruitTooltipDataPacket(msg.entityId, true, hp, slots, RecruitOwnership.isYours(player, mob)));
            } else {
                sendTooltip(player, new RecruitTooltipDataPacket(msg.entityId, false, 0, 0, false));
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void sendTooltip(ServerPlayer player, RecruitTooltipDataPacket packet) {
        Raidborn.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
