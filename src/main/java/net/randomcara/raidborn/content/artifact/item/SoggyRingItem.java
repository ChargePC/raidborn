package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SoggyRingItem extends Item implements ICurioItem {

    private static final int LAVA_WALK_RADIUS = 1;
    private static final int LAVA_FIZZ_EVENT = 1501;
    private static final int RESTORE_DELAY_TICKS = 160;

    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> TEMPORARY_COBBLESTONE = new HashMap<>();

    public static void clearServerState() {
        TEMPORARY_COBBLESTONE.clear();
    }

    public SoggyRingItem(Properties props) {
        super(props);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "ring".equals(slotContext.identifier());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!player.isAlive() || player.isSpectator()) {
            return;
        }

        player.clearFire();
        player.setRemainingFireTicks(0);

        transformLavaUnderPlayer(level, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Lets you walk over lava", 0x66CCFF),
                TooltipHelper.line("Lava beneath you becomes temporary cobblestone", 0xAAAAAA),
                TooltipHelper.line("Grants immunity to fire damage", 0xFF8844)
        );
    }

    private static void transformLavaUnderPlayer(ServerLevel level, Player player) {
        BlockPos center = player.blockPosition().below();

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-LAVA_WALK_RADIUS, 0, -LAVA_WALK_RADIUS),
                center.offset(LAVA_WALK_RADIUS, 0, LAVA_WALK_RADIUS)
        )) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D) > 6.25D) {
                continue;
            }

            transformLavaAt(level, pos.immutable());
        }
    }

    private static void transformLavaAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (!state.getFluidState().is(FluidTags.LAVA)) {
            return;
        }

        BlockState above = level.getBlockState(pos.above());

        if (!above.isAir()) {
            return;
        }

        level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
        playLavaFizz(level, pos);
        scheduleCobblestoneRestore(level, pos);
    }

    private static void scheduleCobblestoneRestore(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> levelBlocks = TEMPORARY_COBBLESTONE.computeIfAbsent(
                level.dimension(),
                dimension -> new HashMap<>()
        );

        levelBlocks.putIfAbsent(pos.immutable(), level.getGameTime() + RESTORE_DELAY_TICKS);
    }

    private static void tickCobblestoneRestore(ServerLevel level) {
        Map<BlockPos, Long> levelBlocks = TEMPORARY_COBBLESTONE.get(level.dimension());

        if (levelBlocks == null || levelBlocks.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();

        Iterator<Map.Entry<BlockPos, Long>> iterator = levelBlocks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();

            BlockPos pos = entry.getKey();
            long restoreTime = entry.getValue();

            if (gameTime < restoreTime) {
                continue;
            }

            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.COBBLESTONE)) {
                level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
                playLavaFizz(level, pos);
            }

            iterator.remove();
        }

        if (levelBlocks.isEmpty()) {
            TEMPORARY_COBBLESTONE.remove(level.dimension());
        }
    }

    private static void playLavaFizz(ServerLevel level, BlockPos pos) {
        level.levelEvent(LAVA_FIZZ_EVENT, pos, 0);
    }

    private static boolean hasSoggyRingEquipped(Player player) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();

        if (optional.isEmpty()) {
            return false;
        }

        ICuriosItemHandler handler = optional.get();

        for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
            for (int i = 0; i < stacksHandler.getStacks().getSlots(); i++) {
                ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);

                if (!stack.isEmpty() && stack.getItem() instanceof SoggyRingItem) {
                    return true;
                }
            }
        }

        return false;
    }

    @Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class Events {

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            if (!(event.level instanceof ServerLevel level)) {
                return;
            }

            tickCobblestoneRestore(level);
        }

        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            if (!event.getSource().is(DamageTypeTags.IS_FIRE)) {
                return;
            }

            if (!hasSoggyRingEquipped(player)) {
                return;
            }

            event.setCanceled(true);
            player.clearFire();
            player.setRemainingFireTicks(0);
        }
    }
}