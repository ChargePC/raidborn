package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.bentoslib.curio.CurioActivationHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class BloodyChaliceItem extends Item implements ICurioItem {

    private static final String TAG_RECRUITED = "raidborn_recruited";
    private static final String TAG_OWNER = "raidborn_owner";

    private static final float HEAL_AMOUNT = 6.0F;
    private static final double PATROL_RADIUS = 96.0D;

    public BloodyChaliceItem(Properties props) {
        super(props);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "charm".equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("When your patrol gets a kill, you heal 3 hearts", 0xAA0000),
                TooltipHelper.line("When you get a kill, your patrol heals 3 hearts", 0xFF5555)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (!(killer instanceof LivingEntity livingKiller)) return;

        if (livingKiller instanceof ServerPlayer player) {
            if (!hasBloodyChaliceEquipped(player)) return;

            List<Mob> patrol = getOwnedPatrolIllagers(player);
            for (Mob mob : patrol) {
                if (mob.isAlive()) {
                    mob.heal(HEAL_AMOUNT);
                }
            }
            return;
        }

        if (livingKiller instanceof Mob mobKiller && isRecruitedIllager(mobKiller)) {
            UUID ownerId = getOwnerUUID(mobKiller);
            if (ownerId == null) return;

            ServerPlayer owner = mobKiller.getServer() != null
                    ? mobKiller.getServer().getPlayerList().getPlayer(ownerId)
                    : null;

            if (owner == null) return;
            if (owner.level() != mobKiller.level()) return;
            if (!hasBloodyChaliceEquipped(owner)) return;

            owner.heal(HEAL_AMOUNT);
        }
    }

    private static boolean hasBloodyChaliceEquipped(ServerPlayer player) {
        return CurioActivationHelper.isEquipped(player, ModItems.BLOODY_CHALICE.get());
    }

    private static boolean isRecruitedIllager(Mob mob) {
        return mob.getPersistentData().getBoolean(TAG_RECRUITED)
                && mob.getPersistentData().hasUUID(TAG_OWNER);
    }

    private static UUID getOwnerUUID(Mob mob) {
        if (!mob.getPersistentData().hasUUID(TAG_OWNER)) {
            return null;
        }
        return mob.getPersistentData().getUUID(TAG_OWNER);
    }

    private static List<Mob> getOwnedPatrolIllagers(ServerPlayer player) {
        UUID ownerId = player.getUUID();

        return player.serverLevel().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(PATROL_RADIUS),
                mob -> mob.isAlive()
                        && mob.getPersistentData().getBoolean(TAG_RECRUITED)
                        && mob.getPersistentData().hasUUID(TAG_OWNER)
                        && ownerId.equals(mob.getPersistentData().getUUID(TAG_OWNER))
        );
    }
}
