package net.randomcara.raidborn;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.randomcara.bentoslib.client.render.area.AreaVisualClient;
import net.randomcara.bentoslib.client.render.area.AreaVisualRenderEvents;
import net.randomcara.bentoslib.network.ActivateCurioItemPacket;
import net.randomcara.raidborn.core.compat.RaidbornCompatPacks;
import net.randomcara.raidborn.core.config.RaidbornClientConfig;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModBlockEntities;
import net.randomcara.raidborn.core.registry.ModBlocks;
import net.randomcara.raidborn.core.registry.ModEffects;
import net.randomcara.raidborn.core.registry.ModEntities;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.core.registry.ModLootModifiers;
import net.randomcara.raidborn.core.registry.ModMenuTypes;
import net.randomcara.raidborn.core.registry.ModRecipeSerializers;
import net.randomcara.raidborn.core.registry.ModSounds;
import net.randomcara.raidborn.core.registry.RaidbornCreativeTab;
import net.randomcara.raidborn.core.registry.RaidbornStructureTypes;
import net.randomcara.raidborn.gameplay.recruit.RecruitOwnership;
import net.randomcara.raidborn.gameplay.recruit.RecruitSlots;
import net.randomcara.raidborn.gameplay.recruit.RecruitmentEvents;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

@Mod(Raidborn.MOD_ID)
public class Raidborn {
    public static final String MOD_ID = "raidborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;
    private static final double RECRUIT_TOOLTIP_REACH_SQR = 6.0D * 6.0D;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public Raidborn(FMLJavaModLoadingContext loadingContext) {
        IEventBus modBus = loadingContext.getModEventBus();

        ModEffects.EFFECTS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModRecipeSerializers.SERIALIZERS.register(modBus);
        ModEntities.register(modBus);
        RaidbornCreativeTab.CREATIVE_TABS.register(modBus);
        RaidbornStructureTypes.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(RaidbornCompatPacks::onAddPackFinders);

        MinecraftForge.EVENT_BUS.register(this);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                MinecraftForge.EVENT_BUS.register(AreaVisualRenderEvents.class)
        );

        loadingContext.registerConfig(ModConfig.Type.SERVER, RaidbornServerConfig.SPEC);
        loadingContext.registerConfig(ModConfig.Type.CLIENT, RaidbornClientConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.messageBuilder(ItemActivationPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(ItemActivationPacket::encode)
                    .decoder(ItemActivationPacket::decode)
                    .consumerMainThread(ItemActivationPacket::handle)
                    .add();

            // don't move this, the packet id inside the channel has to stay put
            ActivateCurioItemPacket.register(CHANNEL, nextPacketId());

            CHANNEL.messageBuilder(TotemAreaVisualPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(TotemAreaVisualPacket::encode)
                    .decoder(TotemAreaVisualPacket::decode)
                    .consumerMainThread(TotemAreaVisualPacket::handle)
                    .add();

            CHANNEL.messageBuilder(RecruitTooltipRequestPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER)
                    .encoder(RecruitTooltipRequestPacket::encode)
                    .decoder(RecruitTooltipRequestPacket::decode)
                    .consumerMainThread(RecruitTooltipRequestPacket::handle)
                    .add();

            CHANNEL.messageBuilder(RecruitTooltipDataPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(RecruitTooltipDataPacket::encode)
                    .decoder(RecruitTooltipDataPacket::decode)
                    .consumerMainThread(RecruitTooltipDataPacket::handle)
                    .add();
        });
    }

    private static int nextPacketId() {
        return packetId++;
    }

    public static void showItemActivation(ServerPlayer player, ItemStack stack) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ItemActivationPacket(stack));
    }

    public static void showTotemAreaVisual(ServerPlayer player, int argbColor, float halfSize, int durationTicks) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new TotemAreaVisualPacket(player.getUUID(), argbColor, halfSize, durationTicks)
        );
    }

    public static class ItemActivationPacket {
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

        public static void handle(ItemActivationPacket msg,
                                  Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
            var ctx = ctxSupplier.get();

            ctx.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();

                if (minecraft.player == null || minecraft.gameRenderer == null) {
                    return;
                }

                if (msg.stack.is(ModItems.ANYWHERE_PILLOW.get())) {
                    return;
                }

                minecraft.gameRenderer.displayItemActivation(msg.stack);
                AreaVisualClient.startFromActivatedStack(minecraft.player, msg.stack);
            });

            ctx.setPacketHandled(true);
        }
    }

    public static class TotemAreaVisualPacket {
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
            return new TotemAreaVisualPacket(
                    buf.readUUID(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readVarInt()
            );
        }

        public static void handle(TotemAreaVisualPacket msg,
                                  Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
            var ctx = ctxSupplier.get();

            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> {
                        if (!RaidbornClientConfig.showTotemAreaVisual()) return;

                        AreaVisualClient.start(
                                msg.ownerId,
                                msg.argbColor,
                                msg.halfSize,
                                msg.durationTicks
                        );
                    }
            ));

            ctx.setPacketHandled(true);
        }
    }

    public static class RecruitTooltipRequestPacket {
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

        public static void handle(RecruitTooltipRequestPacket msg,
                                  Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
            var ctx = ctxSupplier.get();

            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }

                if (!player.isShiftKeyDown()) {
                    sendInvalidRecruitTooltip(player, msg.entityId);
                    return;
                }

                Entity entity = player.level().getEntity(msg.entityId);
                if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                    sendInvalidRecruitTooltip(player, msg.entityId);
                    return;
                }

                if (player.distanceToSqr(mob) > RECRUIT_TOOLTIP_REACH_SQR) {
                    sendInvalidRecruitTooltip(player, msg.entityId);
                    return;
                }

                if (!RecruitmentEvents.isRecruitableTooltipTarget(mob)) {
                    sendInvalidRecruitTooltip(player, msg.entityId);
                    return;
                }

                int hp = Math.max(1, Mth.ceil(mob.getMaxHealth()));
                int slots = Math.max(1, RecruitSlots.getRecruitCost(mob));
                boolean recruited = RecruitOwnership.isYours(player, mob);

                CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new RecruitTooltipDataPacket(msg.entityId, true, hp, slots, recruited)
                );
            });

            ctx.setPacketHandled(true);
        }

        private static void sendInvalidRecruitTooltip(ServerPlayer player, int entityId) {
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RecruitTooltipDataPacket(entityId, false, 0, 0, false)
            );
        }
    }

    public static class RecruitTooltipDataPacket {
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
            return new RecruitTooltipDataPacket(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()
            );
        }

        public static void handle(RecruitTooltipDataPacket msg,
                                  Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier) {
            var ctx = ctxSupplier.get();

            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> net.randomcara.raidborn.client.hud.RecruitTooltipOverlay.handleTooltipData(
                            msg.entityId,
                            msg.valid,
                            msg.hp,
                            msg.slots,
                            msg.recruited
                    )
            ));

            ctx.setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("raidborn server starting");
    }
}
