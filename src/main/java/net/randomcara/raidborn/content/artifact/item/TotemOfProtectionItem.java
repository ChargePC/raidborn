package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.api.curio.IActivatableCurioItem;
import net.randomcara.bentoslib.client.tooltip.ActivatableArtifactTooltipHelper;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.bentoslib.curio.CurioActivationHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;
import net.randomcara.raidborn.core.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class TotemOfProtectionItem extends Item implements ICurioItem, IActivatableCurioItem {

    private static final String TAG_RECRUITED = "raidborn_recruited";
    private static final String TAG_OWNER = "raidborn_owner";

    private static final String TAG_ACTIVE_UNTIL = "raidborn_totem_protection_until";
    private static final String TAG_LAST_APPLY_TICK = "raidborn_totem_protection_last_apply";

    private static final int COOLDOWN_TICKS = 20 * 45;
    private static final int DURATION_TICKS = 20 * 15;
    private static final int APPLY_INTERVAL_TICKS = 10;

    private static double getRadius() {
        return RaidbornServerConfig.getTotemProtectionRadius();
    }

    private static final int AREA_COLOR = 0xFF66CEE6;

    public TotemOfProtectionItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        applySharedTotemCooldown(player);

        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        data.putLong(TAG_ACTIVE_UNTIL, now + DURATION_TICKS);
        data.putLong(TAG_LAST_APPLY_TICK, now - APPLY_INTERVAL_TICKS);

        Raidborn.showItemActivation(player, stack.copy());

        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS,
                1.0F,
                1.1F
        );

        Raidborn.showTotemAreaVisual(
                player,
                AREA_COLOR,
                (float) getRadius(),
                DURATION_TICKS
        );

        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static void applySharedTotemCooldown(ServerPlayer player) {
        player.getCooldowns().addCooldown(ModItems.TOTEM_OF_HEALING.get(), COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(ModItems.TOTEM_OF_RESISTANCE.get(), COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(ModItems.TOTEM_OF_PROTECTION.get(), COOLDOWN_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!CurioActivationHelper.isEquipped(player, ModItems.TOTEM_OF_PROTECTION.get())) return;

        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        long activeUntil = data.getLong(TAG_ACTIVE_UNTIL);
        if (activeUntil <= now) return;

        long lastApply = data.getLong(TAG_LAST_APPLY_TICK);
        if (now - lastApply >= APPLY_INTERVAL_TICKS) {
            applyProtection(player);
            data.putLong(TAG_LAST_APPLY_TICK, now);
        }
    }

    private static void applyProtection(ServerPlayer player) {
        for (ServerPlayer nearbyPlayer : getPlayersInArea(player)) {
            applyCleanseAndResistance(nearbyPlayer);
        }

        UUID ownerId = player.getUUID();
        AABB box = player.getBoundingBox().inflate(getRadius(), 3.0D, getRadius());

        List<Mob> allies = player.serverLevel().getEntitiesOfClass(
                Mob.class,
                box,
                mob -> mob.isAlive()
                        && isOwnedAlly(mob, ownerId)
                        && isInsideSquareArea(player, mob)
        );

        for (Mob mob : allies) {
            applyCleanseAndResistance(mob);
        }
    }

    private static List<ServerPlayer> getPlayersInArea(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(getRadius(), 3.0D, getRadius());

        return player.serverLevel().getPlayers(
                otherPlayer -> otherPlayer.isAlive()
                        && box.intersects(otherPlayer.getBoundingBox())
                        && isInsideSquareArea(player, otherPlayer)
        );
    }

    private static boolean isInsideSquareArea(ServerPlayer player, Mob mob) {
        return Math.abs(mob.getX() - player.getX()) <= getRadius()
                && Math.abs(mob.getZ() - player.getZ()) <= getRadius();
    }

    private static boolean isInsideSquareArea(ServerPlayer player, Player otherPlayer) {
        return Math.abs(otherPlayer.getX() - player.getX()) <= getRadius()
                && Math.abs(otherPlayer.getZ() - player.getZ()) <= getRadius();
    }

    private static boolean isOwnedAlly(Mob mob, UUID ownerId) {
        return isRecruitedIllager(mob, ownerId) || isTamedMobOf(mob, ownerId);
    }

    private static boolean isRecruitedIllager(Mob mob, UUID ownerId) {
        return mob.getPersistentData().getBoolean(TAG_RECRUITED)
                && mob.getPersistentData().hasUUID(TAG_OWNER)
                && ownerId.equals(mob.getPersistentData().getUUID(TAG_OWNER));
    }

    private static boolean isTamedMobOf(Mob mob, UUID ownerId) {
        if (mob instanceof TamableAnimal tamable) {
            return tamable.isTame() && ownerId.equals(tamable.getOwnerUUID());
        }

        if (mob instanceof AbstractHorse horse) {
            return horse.isTamed() && ownerId.equals(horse.getOwnerUUID());
        }

        if (mob instanceof OwnableEntity ownable) {
            return ownerId.equals(ownable.getOwnerUUID());
        }

        return false;
    }

    private static void applyCleanseAndResistance(LivingEntity entity) {
        entity.clearFire();

        List<MobEffect> harmful = new ArrayList<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(effect.getEffect());
            }
        }

        for (MobEffect effect : harmful) {
            entity.removeEffect(effect);
        }

        entity.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                30,
                2,
                false,
                true,
                true
        ));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ActivatableArtifactTooltipHelper.addActivationLine(tooltip);

        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Gives Resistance III to players and allies", 0x9FE6FF),
                TooltipHelper.line("Removes fire and harmful effects", 0xCFEFFF),
                TooltipHelper.line("Cooldown: 45s", 0xFFAA00)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
