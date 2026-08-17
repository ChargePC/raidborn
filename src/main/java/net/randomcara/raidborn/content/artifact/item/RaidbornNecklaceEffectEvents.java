package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.artifact.item.RaidbornNecklaceItem;
import net.randomcara.raidborn.core.registry.ModEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaidbornNecklaceEffectEvents {

    private static final String PLAYER_PERSISTED_TAG = "PlayerPersisted";

    private static final String TAG_LAST_ALLIANCE_EFFECT = "RaidbornNecklaceLastAllianceEffect";
    private static final String TAG_PENDING_ALLIANCE_EFFECT = "RaidbornNecklacePendingAllianceEffect";

    private static final String TAG_EFFECT_ID = "EffectId";
    private static final String TAG_DURATION = "Duration";
    private static final String TAG_AMPLIFIER = "Amplifier";
    private static final String TAG_AMBIENT = "Ambient";
    private static final String TAG_VISIBLE = "Visible";
    private static final String TAG_SHOW_ICON = "ShowIcon";
    private static final String TAG_NECKLACE_GAME_TIME = "NecklaceGameTime";

    private static final long NECKLACE_RECENT_TICKS = 80L;

    private static final Map<UUID, SavedAllianceEffect> LAST_EFFECT_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Long> LAST_NECKLACE_TICK_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, SavedAllianceEffect> PENDING_RESTORE_BY_PLAYER = new HashMap<>();

    public static void clearServerState() {
        LAST_EFFECT_BY_PLAYER.clear();
        LAST_NECKLACE_TICK_BY_PLAYER.clear();
        PENDING_RESTORE_BY_PLAYER.clear();
    }

    private RaidbornNecklaceEffectEvents() {
    }

    public static void rememberNecklaceTick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long gameTime = player.level().getGameTime();

        LAST_NECKLACE_TICK_BY_PLAYER.put(playerId, gameTime);

        CompoundTag persistedRoot = getPersistedRoot(player);
        persistedRoot.putLong(TAG_NECKLACE_GAME_TIME, gameTime);

        SavedAllianceEffect currentEffect = SavedAllianceEffect.captureFrom(player);

        if (currentEffect != null) {
            LAST_EFFECT_BY_PLAYER.put(playerId, currentEffect);
            persistedRoot.put(TAG_LAST_ALLIANCE_EFFECT, currentEffect.save());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();

        PENDING_RESTORE_BY_PLAYER.remove(playerId);

        if (!wasNecklaceEquippedAtDeath(player)) {
            LAST_EFFECT_BY_PLAYER.remove(playerId);
            return;
        }

        SavedAllianceEffect effectToRestore = SavedAllianceEffect.captureFrom(player);

        if (effectToRestore == null) {
            effectToRestore = LAST_EFFECT_BY_PLAYER.get(playerId);
        }

        if (effectToRestore == null) {
            CompoundTag persistedRoot = getExistingPersistedRoot(player);

            if (persistedRoot.contains(TAG_LAST_ALLIANCE_EFFECT, Tag.TAG_COMPOUND)) {
                effectToRestore = SavedAllianceEffect.load(persistedRoot.getCompound(TAG_LAST_ALLIANCE_EFFECT));
            }
        }

        if (effectToRestore == null) {
            return;
        }

        PENDING_RESTORE_BY_PLAYER.put(playerId, effectToRestore);

        CompoundTag persistedRoot = getPersistedRoot(player);
        persistedRoot.put(TAG_PENDING_ALLIANCE_EFFECT, effectToRestore.save());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        UUID playerId = newPlayer.getUUID();

        CompoundTag originalRoot = getExistingPersistedRoot(event.getOriginal());
        CompoundTag newRoot = getPersistedRoot(newPlayer);

        if (originalRoot.contains(TAG_PENDING_ALLIANCE_EFFECT, Tag.TAG_COMPOUND)) {
            CompoundTag copiedEffect = originalRoot.getCompound(TAG_PENDING_ALLIANCE_EFFECT).copy();
            newRoot.put(TAG_PENDING_ALLIANCE_EFFECT, copiedEffect);

            SavedAllianceEffect loaded = SavedAllianceEffect.load(copiedEffect);

            if (loaded != null) {
                PENDING_RESTORE_BY_PLAYER.put(playerId, loaded);
            }

            originalRoot.remove(TAG_PENDING_ALLIANCE_EFFECT);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (event.player.level().isClientSide) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (player.isDeadOrDying()) {
            return;
        }

        UUID playerId = player.getUUID();

        SavedAllianceEffect pendingEffect = PENDING_RESTORE_BY_PLAYER.remove(playerId);

        if (pendingEffect == null) {
            CompoundTag persistedRoot = getExistingPersistedRoot(player);

            if (persistedRoot.contains(TAG_PENDING_ALLIANCE_EFFECT, Tag.TAG_COMPOUND)) {
                pendingEffect = SavedAllianceEffect.load(persistedRoot.getCompound(TAG_PENDING_ALLIANCE_EFFECT));
                persistedRoot.remove(TAG_PENDING_ALLIANCE_EFFECT);
            }
        } else {
            CompoundTag persistedRoot = getExistingPersistedRoot(player);
            persistedRoot.remove(TAG_PENDING_ALLIANCE_EFFECT);
        }

        if (pendingEffect != null) {
            pendingEffect.applyTo(player);
        }
    }

    private static boolean wasNecklaceEquippedAtDeath(ServerPlayer player) {
        if (RaidbornNecklaceItem.hasEquippedNecklace(player)) {
            return true;
        }

        UUID playerId = player.getUUID();
        long now = player.level().getGameTime();

        Long lastTick = LAST_NECKLACE_TICK_BY_PLAYER.get(playerId);

        if (lastTick != null && now - lastTick <= NECKLACE_RECENT_TICKS) {
            return true;
        }

        CompoundTag persistedRoot = getExistingPersistedRoot(player);

        if (!persistedRoot.contains(TAG_NECKLACE_GAME_TIME, Tag.TAG_LONG)) {
            return false;
        }

        long persistedTick = persistedRoot.getLong(TAG_NECKLACE_GAME_TIME);
        return now - persistedTick <= NECKLACE_RECENT_TICKS;
    }

    private static CompoundTag getPersistedRoot(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();

        if (!persistentData.contains(PLAYER_PERSISTED_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(PLAYER_PERSISTED_TAG, new CompoundTag());
        }

        return persistentData.getCompound(PLAYER_PERSISTED_TAG);
    }

    private static CompoundTag getExistingPersistedRoot(net.minecraft.world.entity.player.Player player) {
        CompoundTag persistentData = player.getPersistentData();

        if (!persistentData.contains(PLAYER_PERSISTED_TAG, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return persistentData.getCompound(PLAYER_PERSISTED_TAG);
    }

    private static final class SavedAllianceEffect {
        private final String effectId;
        private final int duration;
        private final int amplifier;
        private final boolean ambient;
        private final boolean visible;
        private final boolean showIcon;

        private SavedAllianceEffect(
                String effectId,
                int duration,
                int amplifier,
                boolean ambient,
                boolean visible,
                boolean showIcon
        ) {
            this.effectId = effectId;
            this.duration = duration;
            this.amplifier = amplifier;
            this.ambient = ambient;
            this.visible = visible;
            this.showIcon = showIcon;
        }

        private static SavedAllianceEffect captureFrom(ServerPlayer player) {
            SavedAllianceEffect hero = captureEffect(player, ModEffects.HERO_OF_THE_RAID.get());

            if (hero != null) {
                return hero;
            }

            SavedAllianceEffect honor = captureEffect(player, ModEffects.ILLAGER_HONOR.get());

            if (honor != null) {
                return honor;
            }

            return captureEffect(player, ModEffects.ILLAGER_LOYALTY.get());
        }

        private static SavedAllianceEffect captureEffect(ServerPlayer player, MobEffect effect) {
            MobEffectInstance instance = player.getEffect(effect);

            if (instance == null) {
                return null;
            }

            ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);

            if (effectId == null) {
                return null;
            }

            int duration = instance.getDuration();

            if (duration == 0) {
                return null;
            }

            return new SavedAllianceEffect(
                    effectId.toString(),
                    duration,
                    instance.getAmplifier(),
                    instance.isAmbient(),
                    instance.isVisible(),
                    instance.showIcon()
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();

            tag.putString(TAG_EFFECT_ID, effectId);
            tag.putInt(TAG_DURATION, duration);
            tag.putInt(TAG_AMPLIFIER, amplifier);
            tag.putBoolean(TAG_AMBIENT, ambient);
            tag.putBoolean(TAG_VISIBLE, visible);
            tag.putBoolean(TAG_SHOW_ICON, showIcon);

            return tag;
        }

        private static SavedAllianceEffect load(CompoundTag tag) {
            if (!tag.contains(TAG_EFFECT_ID, Tag.TAG_STRING)) {
                return null;
            }

            String effectId = tag.getString(TAG_EFFECT_ID);
            int duration = tag.getInt(TAG_DURATION);

            if (duration == 0) {
                return null;
            }

            int amplifier = tag.getInt(TAG_AMPLIFIER);
            boolean ambient = tag.getBoolean(TAG_AMBIENT);
            boolean visible = !tag.contains(TAG_VISIBLE) || tag.getBoolean(TAG_VISIBLE);
            boolean showIcon = !tag.contains(TAG_SHOW_ICON) || tag.getBoolean(TAG_SHOW_ICON);

            return new SavedAllianceEffect(
                    effectId,
                    duration,
                    amplifier,
                    ambient,
                    visible,
                    showIcon
            );
        }

        private void applyTo(ServerPlayer player) {
            ResourceLocation effectKey = ResourceLocation.tryParse(effectId);

            if (effectKey == null) {
                return;
            }

            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectKey);

            if (effect == null) {
                return;
            }

            player.addEffect(new MobEffectInstance(
                    effect,
                    duration,
                    amplifier,
                    ambient,
                    visible,
                    showIcon
            ));
        }
    }
}