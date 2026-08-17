package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OathRingItem extends Item implements ICurioItem {

    private static final double SHARE_RADIUS = 64.0D;

    private static final String TAG_RECRUITED = "raidborn_recruited";
    private static final String TAG_OWNER = "raidborn_owner";

    private static final Map<UUID, Map<MobEffect, EffectSnapshot>> PLAYER_EFFECT_CACHE = new HashMap<>();

    public static void clearServerState() {
        PLAYER_EFFECT_CACHE.clear();
    }

    public OathRingItem(Properties props) {
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
            PLAYER_EFFECT_CACHE.remove(player.getUUID());
            return;
        }

        tickOathRing(level, player);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            PLAYER_EFFECT_CACHE.put(player.getUUID(), snapshotEffects(player.getActiveEffects()));
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (newStack.getItem() instanceof OathRingItem) {
            return;
        }

        if (slotContext.entity() instanceof Player player) {
            PLAYER_EFFECT_CACHE.remove(player.getUUID());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("When you gain an effect, your recruited illagers gain it too", 0xC8A2FF),
                TooltipHelper.line("Only works while equipped", 0xAAAAAA)
        );
    }

    private static void tickOathRing(ServerLevel level, ServerPlayer player) {
        UUID playerId = player.getUUID();

        Map<MobEffect, EffectSnapshot> previous = PLAYER_EFFECT_CACHE.get(playerId);
        Map<MobEffect, EffectSnapshot> current = snapshotEffects(player.getActiveEffects());

        if (previous == null) {
            PLAYER_EFFECT_CACHE.put(playerId, current);
            return;
        }

        for (MobEffectInstance effect : player.getActiveEffects()) {
            MobEffect mobEffect = effect.getEffect();
            EffectSnapshot oldSnapshot = previous.get(mobEffect);

            if (shouldShareEffect(effect, oldSnapshot)) {
                shareEffectToRecruits(level, player, effect);
            }
        }

        PLAYER_EFFECT_CACHE.put(playerId, current);
    }

    private static boolean shouldShareEffect(MobEffectInstance current, @Nullable EffectSnapshot previous) {
        if (previous == null) {
            return true;
        }

        if (current.getAmplifier() > previous.amplifier()) {
            return true;
        }

        return current.getAmplifier() == previous.amplifier()
                && current.getDuration() > previous.duration() + 20;
    }

    private static void shareEffectToRecruits(ServerLevel level, ServerPlayer player, MobEffectInstance original) {
        AABB area = player.getBoundingBox().inflate(SHARE_RADIUS);

        List<LivingEntity> recruits = level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity != player && entity.isAlive() && isOwnedRecruit(player, entity)
        );

        for (LivingEntity recruit : recruits) {
            MobEffectInstance copy = new MobEffectInstance(
                    original.getEffect(),
                    original.getDuration(),
                    original.getAmplifier(),
                    original.isAmbient(),
                    original.isVisible(),
                    original.showIcon()
            );

            recruit.addEffect(copy, player);
        }
    }

    private static boolean isOwnedRecruit(Player player, LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();

        boolean recruited = data.getBoolean(TAG_RECRUITED) || entity.getTags().contains(TAG_RECRUITED);

        if (!recruited) {
            return false;
        }

        UUID ownerId = null;

        if (data.hasUUID(TAG_OWNER)) {
            ownerId = data.getUUID(TAG_OWNER);
        } else if (data.contains(TAG_OWNER)) {
            try {
                ownerId = UUID.fromString(data.getString(TAG_OWNER));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (ownerId == null) {
            String tagPrefix = TAG_OWNER + ":";
            String playerId = player.getUUID().toString();

            for (String tag : entity.getTags()) {
                if (tag.equals(tagPrefix + playerId)) {
                    return true;
                }
            }

            return false;
        }

        return ownerId.equals(player.getUUID());
    }

    private static Map<MobEffect, EffectSnapshot> snapshotEffects(Collection<MobEffectInstance> effects) {
        Map<MobEffect, EffectSnapshot> snapshot = new HashMap<>();

        for (MobEffectInstance effect : effects) {
            snapshot.put(
                    effect.getEffect(),
                    new EffectSnapshot(effect.getAmplifier(), effect.getDuration())
            );
        }

        return snapshot;
    }

    private record EffectSnapshot(int amplifier, int duration) {
    }
}