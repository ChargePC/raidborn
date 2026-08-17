package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.api.curio.IActivatableCurioItem;
import net.randomcara.bentoslib.client.tooltip.ActivatableArtifactTooltipHelper;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VoodooVillagerDollItem extends Item implements ICurioItem, IActivatableCurioItem {

    public static final int COOLDOWN_TICKS = 45 * 20;
    public static final int BUFF_TICKS = 15 * 20;
    public static final double RADIUS = 48.0D;

    private static final Set<String> VALID_ILLAGER_IDS = Set.of(
            "minecraft:vindicator",
            "takeapillager:skirmisher",
            "illagerinvasion:marauder",
            "illagerinvasion:basher",
            "illagerinvasion:inquisitor",
            "savage_and_ravage:executioner",
            "guardillagers:guard_illager",
            "hunters_return:hunter"
    );

    public VoodooVillagerDollItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        UUID ownerId = player.getUUID();

        List<Mob> targets = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RADIUS),
                mob -> isValidTarget(mob, ownerId)
        );

        if (targets.isEmpty()) {
            player.displayClientMessage(Component.literal("No recruited illagers nearby."), true);
            return false;
        }

        MobEffectInstance speed = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_TICKS, 0, false, true);
        MobEffectInstance strength = new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_TICKS, 0, false, true);

        for (Mob mob : targets) {
            mob.addEffect(new MobEffectInstance(speed));
            mob.addEffect(new MobEffectInstance(strength));
        }

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                0.9F,
                0.85F
        );

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        player.displayClientMessage(
                Component.literal("Voodoo empowered " + targets.size() + " recruited illager(s)!"),
                true
        );

        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static boolean isValidTarget(Mob mob, UUID ownerId) {
        if (!mob.isAlive()) return false;

        if (!mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)) return false;
        if (!mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)) return false;
        if (!ownerId.equals(mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER))) return false;

        ResourceLocation id = mob.getType().builtInRegistryHolder().key().location();
        String idString = id.toString();

        if (!VALID_ILLAGER_IDS.contains(idString)) return false;

        if (idString.equals("hunters_return:hunter")) {
            return mob.getMainHandItem().getItem() instanceof SwordItem;
        }

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ActivatableArtifactTooltipHelper.addActivationLine(tooltip);

        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Buffs your recruited illagers", 0xAAAAAA),
                TooltipHelper.line("Works on most melee illagers", 0xAAAAAA),
                TooltipHelper.line("15s of Speed and Strength", 0x55FF55),
                TooltipHelper.line("Cooldown: 45s", 0xFFAA00)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
