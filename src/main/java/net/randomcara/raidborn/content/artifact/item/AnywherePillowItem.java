package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.api.curio.IActivatableCurioItem;
import net.randomcara.bentoslib.client.tooltip.ActivatableArtifactTooltipHelper;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class AnywherePillowItem extends Item implements ICurioItem, IActivatableCurioItem {

    private static final int COOLDOWN_TICKS = 20 * 20;
    private static final double MONSTER_HORIZONTAL_RADIUS = 8.0D;
    private static final double MONSTER_VERTICAL_RADIUS = 5.0D;

    private static final Set<UUID> PILLOW_SLEEPERS = ConcurrentHashMap.newKeySet();

    public static void clearServerState() {
        PILLOW_SLEEPERS.clear();
    }

    public AnywherePillowItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();

        if (player.getCooldowns().isOnCooldown(ModItems.ANYWHERE_PILLOW.get())) {
            return false;
        }

        if (!level.dimension().equals(Level.OVERWORLD)) {
            Raidborn.showItemActivation(player, stack.copy());
            explodeWrongDimension(player, level);
            player.getCooldowns().addCooldown(ModItems.ANYWHERE_PILLOW.get(), COOLDOWN_TICKS);
            return true;
        }

        if (player.isSleeping()) {
            player.displayClientMessage(Component.literal("You are already sleeping."), true);
            return false;
        }

        if (player.isPassenger()) {
            player.displayClientMessage(Component.literal("You can't sleep while riding."), true);
            return false;
        }

        if (!level.isNight() && !level.isThundering()) {
            player.displayClientMessage(Component.literal("You can only use this at night or during a storm."), true);
            return false;
        }

        if (!player.onGround()) {
            player.displayClientMessage(Component.literal("You need solid ground under you."), true);
            return false;
        }

        if (player.isInWater() || player.isInLava()) {
            player.displayClientMessage(Component.literal("You can't sleep here."), true);
            return false;
        }

        BlockPos feetPos = player.blockPosition();
        BlockPos groundPos = feetPos.below();

        if (!hasValidGround(level, groundPos)) {
            player.displayClientMessage(Component.literal("The pillow needs solid ground."), true);
            return false;
        }

        if (!hasEnoughRoom(level, feetPos)) {
            player.displayClientMessage(Component.literal("There isn't enough room here."), true);
            return false;
        }

        if (!player.isCreative() && hasNearbyMonster(level, player)) {
            player.displayClientMessage(Component.literal("You may not rest now; monsters are nearby."), true);
            return false;
        }

        PILLOW_SLEEPERS.add(player.getUUID());

        Raidborn.showItemActivation(player, stack.copy());

        player.stopUsingItem();
        player.startSleeping(feetPos);

        level.updateSleepingPlayerList();

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS,
                0.8F,
                0.9F
        );

        player.getCooldowns().addCooldown(ModItems.ANYWHERE_PILLOW.get(), COOLDOWN_TICKS);

        return true;
    }

    private static void explodeWrongDimension(ServerPlayer player, ServerLevel level) {

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS,
                1.0F,
                0.8F
        );

        level.explode(
                player,
                player.getX(),
                player.getY(),
                player.getZ(),
                4.0F,
                true,
                Level.ExplosionInteraction.TNT
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static boolean hasValidGround(ServerLevel level, BlockPos groundPos) {
        BlockState groundState = level.getBlockState(groundPos);
        return !groundState.isAir() && groundState.isFaceSturdy(level, groundPos, Direction.UP);
    }

    private static boolean hasEnoughRoom(ServerLevel level, BlockPos feetPos) {
        return level.getBlockState(feetPos).getCollisionShape(level, feetPos).isEmpty()
                && level.getBlockState(feetPos.above()).getCollisionShape(level, feetPos.above()).isEmpty();
    }

    private static boolean hasNearbyMonster(ServerLevel level, ServerPlayer player) {
        List<Monster> monsters = level.getEntitiesOfClass(
                Monster.class,
                player.getBoundingBox().inflate(
                        MONSTER_HORIZONTAL_RADIUS,
                        MONSTER_VERTICAL_RADIUS,
                        MONSTER_HORIZONTAL_RADIUS
                ),
                monster -> monster.isAlive() && monster.isPreventingPlayerRest(player)
        );

        return !monsters.isEmpty();
    }

    @SubscribeEvent
    public static void onSleepingLocationCheck(SleepingLocationCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!PILLOW_SLEEPERS.contains(player.getUUID())) {
            return;
        }

        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PILLOW_SLEEPERS.remove(player.getUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ActivatableArtifactTooltipHelper.addActivationLine(tooltip);

        TooltipHelper.addShiftDescription(
                tooltip,
                Component.literal("Sleep almost anywhere"),
                TooltipHelper.line("Cooldown: 20s", 0xFFAA00)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
