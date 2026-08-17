package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LightfedPillItem extends Item implements ICurioItem {

    private static final int REQUIRED_STILL_TICKS = 100;
    private static final int FEED_INTERVAL_TICKS = 40;
    private static final int MIN_LIGHT_LEVEL = 6;

    public LightfedPillItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "charm".equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Feeds from light while you remain still", 0xFF7A9A22),
                TooltipHelper.line("Restores 1 hunger every 2 seconds after charging", 0xFFE5AD25)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static boolean hasEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve().map(handler -> {
            ICurioStacksHandler charmHandler = handler.getCurios().get("charm");

            if (charmHandler == null) {
                return false;
            }

            for (int i = 0; i < charmHandler.getStacks().getSlots(); i++) {
                ItemStack stack = charmHandler.getStacks().getStackInSlot(i);

                if (stack.getItem() instanceof LightfedPillItem) {
                    return true;
                }
            }

            return false;
        }).orElse(false);
    }

    @Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
    public static class Events {

        private static final Map<UUID, LightfedData> DATA = new HashMap<>();

        public static void clearServerState() {
            DATA.clear();
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            if (!(event.player instanceof ServerPlayer player)) {
                return;
            }

            UUID uuid = player.getUUID();

            if (!hasEquipped(player)) {
                DATA.remove(uuid);
                return;
            }

            LightfedData data = DATA.computeIfAbsent(uuid, id -> new LightfedData(player));

            boolean moved = data.hasMoved(player);
            boolean enoughLight = player.level().getMaxLocalRawBrightness(player.blockPosition()) >= MIN_LIGHT_LEVEL;

            if (moved || !enoughLight || player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
                data.reset(player);
                return;
            }

            data.stillTicks++;

            if (data.stillTicks < REQUIRED_STILL_TICKS) {
                return;
            }

            if (data.feedCooldown > 0) {
                data.feedCooldown--;
                return;
            }

            FoodData foodData = player.getFoodData();

            if (foodData.getFoodLevel() < 20) {
                foodData.eat(1, 0.0F);
            }

            data.feedCooldown = FEED_INTERVAL_TICKS;
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            DATA.remove(event.getEntity().getUUID());
        }
    }

    private static class LightfedData {

        private double lastX;
        private double lastY;
        private double lastZ;
        private int stillTicks;
        private int feedCooldown;

        private LightfedData(Player player) {
            this.lastX = player.getX();
            this.lastY = player.getY();
            this.lastZ = player.getZ();
        }

        private boolean hasMoved(Player player) {
            double dx = player.getX() - lastX;
            double dy = player.getY() - lastY;
            double dz = player.getZ() - lastZ;

            return dx * dx + dy * dy + dz * dz > 0.003D;
        }

        private void reset(Player player) {
            this.lastX = player.getX();
            this.lastY = player.getY();
            this.lastZ = player.getZ();
            this.stillTicks = 0;
            this.feedCooldown = 0;
        }
    }
}