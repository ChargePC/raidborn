package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class EvokerIdolItem extends Item implements ICurioItem {

    private static final String TAG_LAST_PROC_TICK = "raidborn_evoker_idol_last_proc_tick";
    private static final String TAG_SUMMONED_BY_IDOL = "raidborn_evoker_idol_summoned";
    private static final String TAG_TARGET_UUID = "raidborn_evoker_idol_target";

    private static final double PROC_CHANCE = 0.15D;
    private static final int VEX_COUNT = 3;
    private static final int VEX_LIFETIME_TICKS = 15 * 20;
    private static final int PROC_COOLDOWN_TICKS = 20;

    public EvokerIdolItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "charm".equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("15% chance to summon 3 Vex when hit", 0xDDDDDD),
                TooltipHelper.line("The Vex go after whoever hit you", 0xBBBBFF)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (event.getAmount() <= 0.0F) return;

        DamageSource source = event.getSource();
        Entity sourceEntity = source.getEntity();

        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        if (!attacker.isAlive()) return;
        if (attacker == player) return;

        if (!hasEvokerIdolEquipped(player)) return;

        long now = player.level().getGameTime();
        long lastProc = player.getPersistentData().getLong(TAG_LAST_PROC_TICK);
        if (now - lastProc < PROC_COOLDOWN_TICKS) return;

        if (player.getRandom().nextDouble() >= PROC_CHANCE) return;

        player.getPersistentData().putLong(TAG_LAST_PROC_TICK, now);

        summonVexes(player.serverLevel(), player, attacker);

        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Vex vex)) return;
        if (vex.level().isClientSide) return;
        if (!(vex.level() instanceof ServerLevel serverLevel)) return;

        CompoundTag data = vex.getPersistentData();
        if (!data.getBoolean(TAG_SUMMONED_BY_IDOL)) return;
        if (!data.hasUUID(TAG_TARGET_UUID)) return;

        UUID targetUuid = data.getUUID(TAG_TARGET_UUID);
        Entity entity = serverLevel.getEntity(targetUuid);

        if (!(entity instanceof LivingEntity target)) return;
        if (!target.isAlive()) return;

        if (vex.getTarget() != target) {
            vex.setTarget(target);
        }

        vex.setLastHurtByMob(target);
    }

    private static boolean hasEvokerIdolEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(handler -> handler.findFirstCurio(stack -> stack.is(ModItems.EVOKER_IDOL.get())).isPresent())
                .orElse(false);
    }

    private static void summonVexes(ServerLevel level, ServerPlayer owner, LivingEntity target) {
        RandomSource random = level.getRandom();

        for (int i = 0; i < VEX_COUNT; i++) {
            Vex vex = EntityType.VEX.create(level);
            if (vex == null) continue;

            double angle = ((Math.PI * 2.0D) / VEX_COUNT) * i + (random.nextDouble() * 0.35D);
            double distance = 1.5D + random.nextDouble() * 1.2D;

            double spawnX = owner.getX() + Math.cos(angle) * distance;
            double spawnY = owner.getY() + 1.0D + random.nextDouble() * 0.75D;
            double spawnZ = owner.getZ() + Math.sin(angle) * distance;

            vex.moveTo(spawnX, spawnY, spawnZ, random.nextFloat() * 360.0F, 0.0F);
            vex.setBoundOrigin(owner.blockPosition());
            vex.setLimitedLife(VEX_LIFETIME_TICKS);

            vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            vex.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

            vex.setTarget(target);
            vex.setLastHurtByMob(target);

            CompoundTag data = vex.getPersistentData();
            data.putBoolean(TAG_SUMMONED_BY_IDOL, true);
            data.putUUID(TAG_TARGET_UUID, target.getUUID());

            vex.setDeltaMovement(new Vec3(
                    (random.nextDouble() - 0.5D) * 0.2D,
                    0.05D,
                    (random.nextDouble() - 0.5D) * 0.2D
            ));

            level.addFreshEntity(vex);
        }
    }
}
