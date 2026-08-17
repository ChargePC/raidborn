package net.randomcara.raidborn.gameplay.banner;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.gameplay.recruit.RecruitmentEvents;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class BannerBackpackEvents {
    private static final String TAG_HAD_BANNER = "raidborn_had_banner_chest";

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!player.isShiftKeyDown()) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!(held.getItem() instanceof BannerItem)) return;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack banner = held.copy();
        banner.setCount(1);
        held.shrink(1);

        if (!chest.isEmpty()) {
            giveBackOrDrop(player, chest);
        }

        player.setItemSlot(EquipmentSlot.CHEST, banner);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        boolean hasBanner = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof BannerItem;
        boolean hadBanner = player.getPersistentData().getBoolean(TAG_HAD_BANNER);

        if (hadBanner && !hasBanner) {
            RecruitmentEvents.disbandSquad(player);
            player.displayClientMessage(Component.literal("§eBanner removed. Your squad has disbanded."), true);
        }

        player.getPersistentData().putBoolean(TAG_HAD_BANNER, hasBanner);
    }

    private static void giveBackOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
    }
}
