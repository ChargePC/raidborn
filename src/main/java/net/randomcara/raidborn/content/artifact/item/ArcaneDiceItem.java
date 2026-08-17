package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.api.curio.IActivatableCurioItem;
import net.randomcara.bentoslib.client.tooltip.ActivatableArtifactTooltipHelper;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

public class ArcaneDiceItem extends Item implements ICurioItem, IActivatableCurioItem {

    private static final String TAG_RECRUITED = "raidborn_recruited";
    private static final String TAG_OWNER = "raidborn_owner";

    private static final int COOLDOWN_TICKS = 20 * 90;
    private static final double RADIUS = 96.0D;

    public ArcaneDiceItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        List<Mob> patrol = getOwnedPatrolIllagers(player);
        RandomSource random = player.getRandom();

        player.addEffect(createRandomBuff(random));

        for (Mob mob : patrol) {
            mob.addEffect(createRandomBuff(random));
        }

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                1.0F,
                0.9F + random.nextFloat() * 0.3F
        );

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static List<Mob> getOwnedPatrolIllagers(ServerPlayer player) {
        UUID ownerId = player.getUUID();

        return player.serverLevel().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RADIUS),
                mob -> mob.isAlive()
                        && mob.getPersistentData().getBoolean(TAG_RECRUITED)
                        && mob.getPersistentData().hasUUID(TAG_OWNER)
                        && ownerId.equals(mob.getPersistentData().getUUID(TAG_OWNER))
        );
    }

    private static MobEffectInstance createRandomBuff(RandomSource random) {
        int roll = random.nextInt(8);

        return switch (roll) {
            case 0 -> effect(MobEffects.MOVEMENT_SPEED, 20 * 45, 1);
            case 1 -> effect(MobEffects.DAMAGE_BOOST, 20 * 45, 0);
            case 2 -> effect(MobEffects.DAMAGE_RESISTANCE, 20 * 45, 0);
            case 3 -> effect(MobEffects.REGENERATION, 20 * 20, 1);
            case 4 -> effect(MobEffects.FIRE_RESISTANCE, 20 * 60, 0);
            case 5 -> effect(MobEffects.ABSORPTION, 20 * 45, 1);
            case 6 -> effect(MobEffects.JUMP, 20 * 45, 1);
            default -> effect(MobEffects.LUCK, 20 * 60, 0);
        };
    }

    private static MobEffectInstance effect(MobEffect effect, int duration, int amplifier) {
        return new MobEffectInstance(effect, duration, amplifier, false, true, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ActivatableArtifactTooltipHelper.addActivationLine(tooltip);

        TooltipHelper.addShiftDescription(
                tooltip,
                Component.literal("Rolls a random buff for you and your recruits")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Every target gets its own effect")
                        .withStyle(ChatFormatting.DARK_PURPLE),
                TooltipHelper.line("Cooldown: 90s", 0xFFAA00)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
