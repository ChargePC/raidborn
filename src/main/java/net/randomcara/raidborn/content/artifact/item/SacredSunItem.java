package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SacredSunItem extends Item implements ICurioItem {

    private static final int LIGHT_LEVEL = 14;
    private static final double BURN_RADIUS = 8.0D;
    private static final int BURN_SECONDS = 4;
    private static final int BURN_INTERVAL_TICKS = 10;

    private static final String TAG_LIGHT_X = "raidborn_sacred_sun_light_x";
    private static final String TAG_LIGHT_Y = "raidborn_sacred_sun_light_y";
    private static final String TAG_LIGHT_Z = "raidborn_sacred_sun_light_z";
    private static final String TAG_LIGHT_DIM = "raidborn_sacred_sun_light_dim";

    private static final Map<UUID, LightPoint> ACTIVE_LIGHTS = new HashMap<>();

    public static void clearServerState() {
        ACTIVE_LIGHTS.clear();
    }

    public SacredSunItem(Properties props) {
        super(props);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "charm".equals(slotContext.identifier());
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
            removeStoredLight(player);
            return;
        }

        tickSacredSun(level, player);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (newStack.getItem() instanceof SacredSunItem) {
            return;
        }

        if (slotContext.entity() instanceof Player player) {
            removeStoredLight(player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Emits sacred sunlight while equipped", 0xFFE27A),
                TooltipHelper.line("Nearby undead burn under its holy sun", 0xFFB347)
        );
    }

    private static void tickSacredSun(ServerLevel level, ServerPlayer player) {
        BlockPos lightPos = findLightPosition(level, player);

        if (lightPos == null) {
            removeStoredLight(player);
            return;
        }

        LightPoint previous = getKnownLight(player);

        if (previous != null && (!previous.dimension().equals(level.dimension()) || !previous.pos().equals(lightPos))) {
            removeLight(previous, player.getUUID(), player.getServer());
        }

        placeLight(level, lightPos);
        storeLight(player, new LightPoint(level.dimension(), lightPos));

        if (player.tickCount % BURN_INTERVAL_TICKS == 0) {
            burnNearbyUndead(level, player);
        }
    }

    private static BlockPos findLightPosition(ServerLevel level, Player player) {
        BlockPos feet = player.blockPosition();
        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        BlockPos head = feet.above();

        if (canPlaceLight(level, feet)) {
            return feet;
        }

        if (canPlaceLight(level, eyes)) {
            return eyes;
        }

        if (canPlaceLight(level, head)) {
            return head;
        }

        return null;
    }

    private static boolean canPlaceLight(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.LIGHT);
    }

    private static void placeLight(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);

        if (current.is(Blocks.LIGHT) && current.getValue(LightBlock.LEVEL) == LIGHT_LEVEL) {
            return;
        }

        level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3);
    }

    private static void burnNearbyUndead(ServerLevel level, Player player) {
        AABB area = player.getBoundingBox().inflate(BURN_RADIUS);

        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                target -> target != player
                        && target.isAlive()
                        && target.getMobType() == MobType.UNDEAD
                        && !target.fireImmune()
        );

        double maxDistance = BURN_RADIUS * BURN_RADIUS;

        for (LivingEntity target : targets) {
            if (target.distanceToSqr(player) > maxDistance) {
                continue;
            }

            if (!player.hasLineOfSight(target)) {
                continue;
            }

            if (target.isInWaterRainOrBubble()) {
                continue;
            }

            if (target instanceof Zombie zombie && zombie.isBaby()) {
                continue;
            }

            if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                continue;
            }

            target.setSecondsOnFire(BURN_SECONDS);
        }
    }

    private static LightPoint getKnownLight(Player player) {
        LightPoint cached = ACTIVE_LIGHTS.get(player.getUUID());

        if (cached != null) {
            return cached;
        }

        CompoundTag data = player.getPersistentData();

        if (!data.contains(TAG_LIGHT_X) || !data.contains(TAG_LIGHT_Y) || !data.contains(TAG_LIGHT_Z)) {
            return null;
        }

        ResourceKey<Level> dimension = player.level().dimension();

        if (data.contains(TAG_LIGHT_DIM)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(TAG_LIGHT_DIM));

            if (id != null) {
                dimension = ResourceKey.create(Registries.DIMENSION, id);
            }
        }

        BlockPos pos = new BlockPos(
                data.getInt(TAG_LIGHT_X),
                data.getInt(TAG_LIGHT_Y),
                data.getInt(TAG_LIGHT_Z)
        );

        LightPoint point = new LightPoint(dimension, pos);
        ACTIVE_LIGHTS.put(player.getUUID(), point);
        return point;
    }

    private static void storeLight(Player player, LightPoint point) {
        ACTIVE_LIGHTS.put(player.getUUID(), point);

        CompoundTag data = player.getPersistentData();
        data.putInt(TAG_LIGHT_X, point.pos().getX());
        data.putInt(TAG_LIGHT_Y, point.pos().getY());
        data.putInt(TAG_LIGHT_Z, point.pos().getZ());
        data.putString(TAG_LIGHT_DIM, point.dimension().location().toString());
    }

    private static void clearStoredLight(Player player) {
        ACTIVE_LIGHTS.remove(player.getUUID());

        CompoundTag data = player.getPersistentData();
        data.remove(TAG_LIGHT_X);
        data.remove(TAG_LIGHT_Y);
        data.remove(TAG_LIGHT_Z);
        data.remove(TAG_LIGHT_DIM);
    }

    private static void removeStoredLight(Player player) {
        LightPoint point = getKnownLight(player);

        if (point != null) {
            removeLight(point, player.getUUID(), player.getServer());
        }

        clearStoredLight(player);
    }

    private static void removeLight(LightPoint point, UUID owner, MinecraftServer server) {
        if (server == null) {
            return;
        }

        if (isLightUsedByOtherPlayer(point, owner)) {
            return;
        }

        ServerLevel level = server.getLevel(point.dimension());

        if (level == null) {
            return;
        }

        BlockState state = level.getBlockState(point.pos());

        if (state.is(Blocks.LIGHT)) {
            level.setBlock(point.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean isLightUsedByOtherPlayer(LightPoint point, UUID owner) {
        for (Map.Entry<UUID, LightPoint> entry : ACTIVE_LIGHTS.entrySet()) {
            if (entry.getKey().equals(owner)) {
                continue;
            }

            LightPoint other = entry.getValue();

            if (other.dimension().equals(point.dimension()) && other.pos().equals(point.pos())) {
                return true;
            }
        }

        return false;
    }

    private record LightPoint(ResourceKey<Level> dimension, BlockPos pos) {
    }

    @Mod.EventBusSubscriber(modid = Raidborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class Events {

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            removeStoredLight(event.getEntity());
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            removeStoredLight(event.getEntity());
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            removeStoredLight(event.getOriginal());
            clearStoredLight(event.getEntity());
        }
    }
}