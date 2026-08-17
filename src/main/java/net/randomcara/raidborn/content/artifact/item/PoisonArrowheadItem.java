package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModItems;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Raidborn.MOD_ID)
public class PoisonArrowheadItem extends Item implements ICurioItem {

    private static final int POISON_DURATION = 100;
    private static final int POISON_AMPLIFIER = 2;

    public PoisonArrowheadItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "necklace".equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Your weapon hits and projectiles poison enemies", 0xFF76DB4C),
                TooltipHelper.line("Your recruited illagers can poison too", 0xFF4F8C29)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (event.getAmount() <= 0.0F) return;

        DamageSource source = event.getSource();
        Entity directEntity = source.getDirectEntity();
        Entity poisonSourceEntity = getPoisonSourceEntity(source, directEntity);
        if (poisonSourceEntity == null) return;

        if (!isValidPoisonAttack(source, directEntity, poisonSourceEntity)) return;

        ServerPlayer ownerPlayer = getPlayerResponsibleForPoison(poisonSourceEntity);
        if (ownerPlayer == null) return;
        if (!hasPoisonArrowheadEquipped(ownerPlayer)) return;
        if (!canPoisonTarget(ownerPlayer, poisonSourceEntity, target)) return;

        target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_DURATION, POISON_AMPLIFIER));
    }

    private static Entity getPoisonSourceEntity(DamageSource source, @Nullable Entity directEntity) {
        if (directEntity instanceof Projectile projectile) {
            return projectile.getOwner();
        }

        Entity causingEntity = source.getEntity();
        if (causingEntity instanceof LivingEntity) {
            return causingEntity;
        }

        if (directEntity instanceof LivingEntity) {
            return directEntity;
        }

        return null;
    }

    private static boolean isValidPoisonAttack(DamageSource source, @Nullable Entity directEntity, Entity poisonSourceEntity) {
        if (directEntity instanceof Projectile) {
            return true;
        }

        Entity causingEntity = source.getEntity();
        if (causingEntity != null && causingEntity != poisonSourceEntity) {
            return false;
        }

        if (!(poisonSourceEntity instanceof LivingEntity attacker)) {
            return false;
        }

        return isHoldingMeleeWeaponOrTool(attacker);
    }

    private static boolean isHoldingMeleeWeaponOrTool(LivingEntity attacker) {
        if (isMeleeWeaponOrTool(attacker.getMainHandItem())) {
            return true;
        }

        return isMeleeWeaponOrTool(attacker.getOffhandItem());
    }

    private static boolean isMeleeWeaponOrTool(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();

        if (item instanceof SwordItem) {
            return true;
        }

        if (item instanceof TieredItem) {
            return true;
        }

        if (item instanceof TridentItem) {
            return true;
        }

        return stack.canPerformAction(ToolActions.SWORD_DIG)
                || stack.canPerformAction(ToolActions.SWORD_SWEEP)
                || stack.canPerformAction(ToolActions.PICKAXE_DIG)
                || stack.canPerformAction(ToolActions.AXE_DIG)
                || stack.canPerformAction(ToolActions.SHOVEL_DIG)
                || stack.canPerformAction(ToolActions.HOE_DIG);
    }

    private static boolean canPoisonTarget(ServerPlayer ownerPlayer, Entity poisonSourceEntity, LivingEntity target) {
        if (target == ownerPlayer) return false;
        if (target == poisonSourceEntity) return false;

        if (target instanceof Mob targetMob && isRecruited(targetMob)) {
            UUID targetOwner = getOwnerUUID(targetMob);
            if (targetOwner != null && targetOwner.equals(ownerPlayer.getUUID())) {
                return false;
            }
        }

        if (poisonSourceEntity instanceof Mob sourceMob && target instanceof Mob targetMob) {
            if (isRecruited(sourceMob) && isRecruited(targetMob)) {
                UUID sourceOwner = getOwnerUUID(sourceMob);
                UUID targetOwner = getOwnerUUID(targetMob);
                if (sourceOwner != null && sourceOwner.equals(targetOwner)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static ServerPlayer getPlayerResponsibleForPoison(Entity poisonSourceEntity) {
        if (poisonSourceEntity instanceof ServerPlayer player) {
            return player;
        }

        if (poisonSourceEntity instanceof Mob mob && isRecruited(mob)) {
            UUID ownerUuid = getOwnerUUID(mob);
            if (ownerUuid == null) return null;

            Entity ownerEntity = mob.level().getPlayerByUUID(ownerUuid);
            if (ownerEntity instanceof ServerPlayer player) {
                return player;
            }
        }

        return null;
    }

    private static boolean isRecruited(Mob mob) {
        return mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)
                && mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER);
    }

    private static UUID getOwnerUUID(Mob mob) {
        if (!mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)) return null;
        return mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER);
    }

    private static boolean hasPoisonArrowheadEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(handler -> handler.findFirstCurio(stack -> stack.is(ModItems.POISON_ARROWHEAD.get())).isPresent())
                .orElse(false);
    }
}