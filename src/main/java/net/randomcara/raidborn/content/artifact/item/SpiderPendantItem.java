package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpiderPendantItem extends Item implements ICurioItem {

    private static final double WALL_CLIMB_SPEED = 0.18D;
    private static final double WALL_HORIZONTAL_DAMPING = 0.72D;

    private static final double WALL_STICK_HORIZONTAL_DAMPING = 0.12D;
    private static final double WALL_STICK_VERTICAL_SPEED = 0.0D;

    private static final double CEILING_SEARCH_DISTANCE = 0.18D;
    private static final double CEILING_STICK_UPWARD_PUSH = 0.04D;
    private static final double CEILING_HORIZONTAL_DAMPING = 0.85D;

    private static final float FALL_DISTANCE_REDUCTION_BLOCKS = 12.0F;
    private static final float FALL_DAMAGE_MULTIPLIER = 0.65F;

    public SpiderPendantItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "necklace".equals(slotContext.identifier());
    }

    public static boolean isEquipped(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(SpiderPendantItem::hasSpiderPendant)
                .orElse(false);
    }

    private static boolean hasSpiderPendant(ICuriosItemHandler curiosInventory) {
        for (ICurioStacksHandler stacksHandler : curiosInventory.getCurios().values()) {
            if (stacksHandler == null) {
                continue;
            }

            var stacks = stacksHandler.getStacks();

            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack equippedStack = stacks.getStackInSlot(slot);

                if (!equippedStack.isEmpty() && equippedStack.getItem() instanceof SpiderPendantItem) {
                    return true;
                }
            }
        }

        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        if (!SpiderPendantItem.isEquipped(player)) {
            return;
        }

        if (shouldIgnorePlayer(player)) {
            return;
        }

        if (player.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> ClientSpiderMovement.handleClientTick(player)
            );
            return;
        }

        if (handleServerWallStick(player)) {
            player.fallDistance = 0.0F;
            player.hasImpulse = true;
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!SpiderPendantItem.isEquipped(player)) {
            return;
        }

        float reducedDistance = Math.max(0.0F, event.getDistance() - FALL_DISTANCE_REDUCTION_BLOCKS);

        event.setDistance(reducedDistance);
        event.setDamageMultiplier(event.getDamageMultiplier() * FALL_DAMAGE_MULTIPLIER);
    }

    private static boolean shouldIgnorePlayer(Player player) {
        return player.isSpectator()
                || player.getAbilities().flying
                || player.isFallFlying()
                || player.isPassenger()
                || player.isInWaterOrBubble()
                || player.isInLava();
    }

    private static boolean handleSpiderMovement(Player player, boolean jumpDown, boolean shiftDown) {
        if (player.onGround()) {
            return false;
        }

        if (shiftDown && player.horizontalCollision) {
            return stickToWall(player);
        }

        if (!jumpDown) {
            return false;
        }

        boolean usedAbility = false;

        if (player.horizontalCollision) {
            usedAbility = climbWall(player);
        }

        if (hasCeilingClose(player)) {
            usedAbility = clingToCeiling(player) || usedAbility;
        }

        return usedAbility;
    }

    private static boolean climbWall(Player player) {
        Vec3 motion = player.getDeltaMovement();

        player.setDeltaMovement(
                motion.x * WALL_HORIZONTAL_DAMPING,
                Math.max(motion.y, WALL_CLIMB_SPEED),
                motion.z * WALL_HORIZONTAL_DAMPING
        );

        return true;
    }

    private static boolean stickToWall(Player player) {
        Vec3 motion = player.getDeltaMovement();

        player.setDeltaMovement(
                motion.x * WALL_STICK_HORIZONTAL_DAMPING,
                WALL_STICK_VERTICAL_SPEED,
                motion.z * WALL_STICK_HORIZONTAL_DAMPING
        );

        return true;
    }

    private static boolean clingToCeiling(Player player) {
        Vec3 motion = player.getDeltaMovement();

        player.setDeltaMovement(
                motion.x * CEILING_HORIZONTAL_DAMPING,
                Math.max(motion.y, CEILING_STICK_UPWARD_PUSH),
                motion.z * CEILING_HORIZONTAL_DAMPING
        );

        return true;
    }

    private static boolean handleServerWallStick(Player player) {
        if (player.onGround()) {
            return false;
        }

        if (!player.isShiftKeyDown()) {
            return false;
        }

        if (!player.horizontalCollision) {
            return false;
        }

        return stickToWall(player);
    }

    private static boolean hasCeilingClose(Player player) {
        AABB checkBox = player.getBoundingBox()
                .deflate(0.02D)
                .move(0.0D, CEILING_SEARCH_DISTANCE, 0.0D);

        return !player.level().noCollision(player, checkBox);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSpiderMovement {

        private static void handleClientTick(Player player) {
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.player == null || minecraft.player != player) {
                return;
            }

            boolean jumpDown = minecraft.options.keyJump.isDown();
            boolean shiftDown = minecraft.options.keyShift.isDown();

            if (handleSpiderMovement(player, jumpDown, shiftDown)) {
                player.fallDistance = 0.0F;
                player.hasImpulse = true;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Hold Jump to climb walls and ceilings", 0xFFC8C2A7),
                TooltipHelper.line("Hold Sneak while climbing to stick to walls", 0xFFC8C2A7),
                TooltipHelper.line("Greatly reduces fall damage", 0xFFC8C2A7)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}