package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.gameplay.recruit.FollowOwnerGoal;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

public class OminousRelicItem extends Item implements ICurioItem {

    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b7f0151f-6d2d-4d59-8f8a-6b4cc7cb9d11");

    private static final double RECRUIT_RADIUS = 64.0D;

    public OminousRelicItem(Properties properties) {
        super(properties);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity living = slotContext.entity();
        if (living.level().isClientSide) return;
        if (!(living instanceof ServerPlayer player)) return;

        AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        int recruitedIllagers = countOwnedRecruitedIllagers(player);

        double bonusHealth = recruitedIllagers * 1.0D;

        AttributeModifier oldModifier = maxHealth.getModifier(HEALTH_MODIFIER_UUID);
        if (oldModifier != null) {
            maxHealth.removeModifier(oldModifier);
        }

        if (bonusHealth > 0.0D) {
            AttributeModifier newModifier = new AttributeModifier(
                    HEALTH_MODIFIER_UUID,
                    "raidborn_ominous_relic_recruit_bonus",
                    bonusHealth,
                    AttributeModifier.Operation.ADDITION
            );
            maxHealth.addTransientModifier(newModifier);
        }

        if (living.getHealth() > living.getMaxHealth()) {
            living.setHealth(living.getMaxHealth());
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        LivingEntity living = slotContext.entity();
        if (living.level().isClientSide) return;

        AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        AttributeModifier oldModifier = maxHealth.getModifier(HEALTH_MODIFIER_UUID);
        if (oldModifier != null) {
            maxHealth.removeModifier(oldModifier);
        }

        if (living.getHealth() > living.getMaxHealth()) {
            living.setHealth(living.getMaxHealth());
        }
    }

    private int countOwnedRecruitedIllagers(ServerPlayer player) {
        List<Mob> mobs = player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RECRUIT_RADIUS),
                mob -> isOwnedRecruitedIllager(player, mob)
        );

        return mobs.size();
    }

    private boolean isOwnedRecruitedIllager(ServerPlayer player, Mob mob) {
        if (!mob.isAlive()) return false;
        if (!mob.getPersistentData().getBoolean(FollowOwnerGoal.TAG_RECRUITED)) return false;
        if (!mob.getPersistentData().hasUUID(FollowOwnerGoal.TAG_OWNER)) return false;

        UUID ownerUUID = mob.getPersistentData().getUUID(FollowOwnerGoal.TAG_OWNER);
        return player.getUUID().equals(ownerUUID);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("Gain half a heart for each nearby recruit", 0xFF44AEB9)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
