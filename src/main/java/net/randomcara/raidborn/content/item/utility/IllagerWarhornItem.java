package net.randomcara.raidborn.content.item.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.core.registry.ModSounds;
import net.randomcara.raidborn.gameplay.recruit.RecruitTeleport;
import net.randomcara.raidborn.gameplay.recruit.RecruitmentEvents;
import net.randomcara.raidborn.gameplay.recruit.SquadOrder;
import net.randomcara.raidborn.gameplay.recruit.SquadOrders;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class IllagerWarhornItem extends Item {
    private static final String TAG_SELECTED_MODE = "raidborn_warhorn_mode";
    private static final int COMMAND_COOLDOWN_TICKS = 40;
    private static final double LOOK_RANGE = 48.0D;

    public IllagerWarhornItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    private SquadOrder getSelectedMode(ItemStack stack) {
        return SquadOrder.fromString(stack.getOrCreateTag().getString(TAG_SELECTED_MODE));
    }

    private void setSelectedMode(ItemStack stack, SquadOrder mode) {
        stack.getOrCreateTag().putString(TAG_SELECTED_MODE, mode.getId());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SquadOrder mode = getSelectedMode(stack);

        tooltip.add(Component.literal("Current Mode: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatMode(mode)).withStyle(getModeColor(mode))));

        TooltipHelper.addShiftDescription(
                tooltip,
                Component.literal("Shift + Right Click: Change mode").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Right Click: Give order").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Hold: squad stays and defends the commanded position").withStyle(ChatFormatting.DARK_GRAY)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!RecruitmentEvents.canCommandRecruits(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("§cYou cannot command your squad right now."), true);
            return InteractionResultHolder.fail(stack);
        }

        SquadOrder selected = getSelectedMode(stack);

        if (player.isShiftKeyDown()) {
            SquadOrder next = selected.nextSelectable();
            setSelectedMode(stack, next);

            player.getCooldowns().addCooldown(this, COMMAND_COOLDOWN_TICKS);
            serverPlayer.displayClientMessage(Component.literal("§eWarhorn mode: §f" + formatMode(next)), true);
            return InteractionResultHolder.success(stack);
        }

        List<Mob> squad = SquadOrders.getNearbySquad(serverPlayer);
        if (squad.isEmpty()) {
            serverPlayer.displayClientMessage(Component.literal("§7You have no recruited illagers nearby."), true);
            return InteractionResultHolder.success(stack);
        }

        switch (selected) {
            case FOLLOW -> {
                for (Mob mob : squad) {
                    SquadOrders.setOrder(mob, SquadOrder.FOLLOW);

                    if (mob.distanceToSqr(serverPlayer) > 40.0D * 40.0D) {
                        RecruitTeleport.tryTeleportNearOwner(mob, serverPlayer);
                    }
                }

                playOrderSound(level, serverPlayer, SquadOrder.FOLLOW);
                player.getCooldowns().addCooldown(this, COMMAND_COOLDOWN_TICKS);
                serverPlayer.displayClientMessage(Component.literal("§aSquad order: Follow"), true);
            }
            case ATTACK -> {
                LivingEntity lookedTarget = getLookTarget(serverPlayer);

                if (lookedTarget == null) {
                    serverPlayer.displayClientMessage(Component.literal("§7No valid target in sight."), true);
                    return InteractionResultHolder.success(stack);
                }

                int affected = 0;

                for (Mob mob : squad) {
                    if (!SquadOrders.isValidTarget(serverPlayer, mob, lookedTarget)) {
                        continue;
                    }

                    SquadOrders.setAttackTarget(mob, lookedTarget, level.getGameTime());
                    mob.setTarget(lookedTarget);
                    mob.getNavigation().moveTo(lookedTarget, 1.25D);
                    affected++;
                }

                if (affected <= 0) {
                    serverPlayer.displayClientMessage(Component.literal("§7That target is not valid for your squad."), true);
                } else {
                    playOrderSound(level, serverPlayer, SquadOrder.ATTACK);
                    player.getCooldowns().addCooldown(this, COMMAND_COOLDOWN_TICKS);
                    serverPlayer.displayClientMessage(Component.literal("§4Squad order: Attack target"), true);
                }
            }
            case HOLD -> {
                BlockPos holdPos = serverPlayer.blockPosition();

                for (Mob mob : squad) {
                    SquadOrders.setHoldPos(mob, holdPos);
                }

                playOrderSound(level, serverPlayer, SquadOrder.HOLD);
                player.getCooldowns().addCooldown(this, COMMAND_COOLDOWN_TICKS);
                serverPlayer.displayClientMessage(Component.literal("§6Squad order: Hold position"), true);
            }
        }

        return InteractionResultHolder.success(stack);
    }

    private void playOrderSound(Level level, ServerPlayer player, SquadOrder order) {
        switch (order) {
            case ATTACK -> level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    ModSounds.ILLAGER_WARHORN_ATTACK.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F
            );
            case FOLLOW -> level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    ModSounds.ILLAGER_WARHORN_FOLLOW.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F
            );
            case HOLD -> level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    ModSounds.ILLAGER_WARHORN_FOLLOW.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F
            );
        }
    }

    private LivingEntity getLookTarget(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 fullEnd = eyePos.add(look.scale(LOOK_RANGE));

        HitResult blockHit = player.level().clip(new ClipContext(
                eyePos,
                fullEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? fullEnd : blockHit.getLocation();
        AABB searchBox = player.getBoundingBox().expandTowards(end.subtract(eyePos)).inflate(1.5D);

        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> isValidLookTarget(player, entity)
        );

        double bestDistance = Double.MAX_VALUE;
        LivingEntity best = null;

        for (LivingEntity candidate : candidates.stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .toList()) {
            AABB box = candidate.getBoundingBox().inflate(0.35D);
            EntityHitResult intercept = box.clip(eyePos, end).map(vec -> new EntityHitResult(candidate, vec)).orElse(null);

            if (intercept == null) continue;

            double dist = eyePos.distanceToSqr(intercept.getLocation());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = candidate;
            }
        }

        return best;
    }

    private boolean isValidLookTarget(ServerPlayer player, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;
        if (entity.isSpectator()) return false;
        if (!entity.isPickable()) return false;
        if (entity == player) return false;
        return true;
    }

    private String formatMode(SquadOrder mode) {
        return switch (mode) {
            case FOLLOW -> "Follow";
            case ATTACK -> "Attack";
            case HOLD -> "Hold";
        };
    }

    private ChatFormatting getModeColor(SquadOrder mode) {
        return switch (mode) {
            case FOLLOW -> ChatFormatting.GREEN;
            case ATTACK -> ChatFormatting.RED;
            case HOLD -> ChatFormatting.GOLD;
        };
    }
}
