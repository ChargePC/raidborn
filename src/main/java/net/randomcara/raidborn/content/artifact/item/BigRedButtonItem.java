package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.randomcara.bentoslib.api.curio.IActivatableCurioItem;
import net.randomcara.bentoslib.client.tooltip.ActivatableArtifactTooltipHelper;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

public class BigRedButtonItem extends Item implements ICurioItem, IActivatableCurioItem {

    private static final double SEARCH_RADIUS = 192.0D;
    private static final float EXPLOSION_RADIUS = 2.5F;
    private static final int COOLDOWN_TICKS = 2000;

    public BigRedButtonItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        Level level = player.level();

        List<Mob> recruits = getPlayerRecruits(player, SEARCH_RADIUS);

        if (recruits.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("You have no recruited illagers nearby.")
                            .withStyle(Style.EMPTY.withColor(0xD9534F)),
                    true
            );
            return false;
        }

        Mob chosen = recruits.get(player.getRandom().nextInt(recruits.size()));

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.STONE_BUTTON_CLICK_ON,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        level.playSound(
                null,
                chosen.getX(),
                chosen.getY(),
                chosen.getZ(),
                SoundEvents.TNT_PRIMED,
                SoundSource.PLAYERS,
                1.0F,
                0.8F
        );

        level.explode(
                null,
                chosen.getX(),
                chosen.getY() + 0.5D,
                chosen.getZ(),
                EXPLOSION_RADIUS,
                false,
                Level.ExplosionInteraction.NONE
        );

        chosen.hurt(level.damageSources().magic(), Float.MAX_VALUE);

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ActivatableArtifactTooltipHelper.addActivationLine(tooltip);

        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Press it. Something will happen.", 0xFFFF7777),
                TooltipHelper.line("Cooldown: 1m 40s", 0xFFAA00)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static List<Mob> getPlayerRecruits(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);

        return player.level().getEntitiesOfClass(
                Mob.class,
                box,
                mob -> isValidOwnedRecruit(player, mob)
        );
    }

    private static boolean isValidOwnedRecruit(ServerPlayer player, Mob mob) {
        if (!(mob instanceof AbstractIllager)) return false;
        if (!isRecruited(mob)) return false;

        UUID owner = getOwnerUUID(mob);
        return owner != null && owner.equals(player.getUUID()) && mob.isAlive();
    }

    private static boolean isRecruited(Mob mob) {
        return mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)
                && mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);
    }

    private static UUID getOwnerUUID(Mob mob) {
        if (!mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)) {
            return null;
        }

        return mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER);
    }
}
