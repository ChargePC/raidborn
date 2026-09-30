package net.randomcara.raidborn;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
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
import net.randomcara.raidborn.network.ItemActivationPacket;
import net.randomcara.raidborn.network.RecruitTooltipDataPacket;
import net.randomcara.raidborn.network.RecruitTooltipRequestPacket;
import net.randomcara.raidborn.network.TotemAreaVisualPacket;
import org.slf4j.Logger;

@Mod(Raidborn.MOD_ID)
public class Raidborn {
    public static final String MOD_ID = "raidborn";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(id("main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

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

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(AreaVisualRenderEvents.class));

        loadingContext.registerConfig(ModConfig.Type.SERVER, RaidbornServerConfig.SPEC);
        loadingContext.registerConfig(ModConfig.Type.CLIENT, RaidbornClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.messageBuilder(ItemActivationPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT).encoder(ItemActivationPacket::encode).decoder(ItemActivationPacket::decode).consumerMainThread(ItemActivationPacket::handle).add();

            ActivateCurioItemPacket.register(CHANNEL, nextPacketId());

            CHANNEL.messageBuilder(TotemAreaVisualPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT).encoder(TotemAreaVisualPacket::encode).decoder(TotemAreaVisualPacket::decode).consumerMainThread(TotemAreaVisualPacket::handle).add();
            CHANNEL.messageBuilder(RecruitTooltipRequestPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_SERVER).encoder(RecruitTooltipRequestPacket::encode).decoder(RecruitTooltipRequestPacket::decode).consumerMainThread(RecruitTooltipRequestPacket::handle).add();
            CHANNEL.messageBuilder(RecruitTooltipDataPacket.class, nextPacketId(), NetworkDirection.PLAY_TO_CLIENT).encoder(RecruitTooltipDataPacket::encode).decoder(RecruitTooltipDataPacket::decode).consumerMainThread(RecruitTooltipDataPacket::handle).add();
        });
    }

    public static void showItemActivation(ServerPlayer player, ItemStack stack) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ItemActivationPacket(stack));
    }

    public static void showTotemAreaVisual(ServerPlayer player, int argbColor, float halfSize, int durationTicks) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new TotemAreaVisualPacket(player.getUUID(), argbColor, halfSize, durationTicks));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static int nextPacketId() {
        return packetId++;
    }
}
