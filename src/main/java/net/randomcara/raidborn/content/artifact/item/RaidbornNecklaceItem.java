package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.content.artifact.item.RaidbornNecklaceEffectEvents;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;

public class RaidbornNecklaceItem extends Item implements ICurioItem {

    public static final int BONUS_RECRUIT_SLOTS = 5;

    private static final String CURIOS_NECKLACE_SLOT = "necklace";

    public RaidbornNecklaceItem(Properties props) {
        super(props);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CURIOS_NECKLACE_SLOT.equals(slotContext.identifier());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!CURIOS_NECKLACE_SLOT.equals(slotContext.identifier())) {
            return;
        }

        if (slotContext.entity() instanceof ServerPlayer player) {
            RaidbornNecklaceEffectEvents.rememberNecklaceTick(player);
        }
    }

    public static int getEquippedBonusRecruitSlots(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(RaidbornNecklaceItem::countBonusRecruitSlots)
                .orElse(0);
    }

    public static boolean hasEquippedNecklace(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(RaidbornNecklaceItem::hasNecklace)
                .orElse(false);
    }

    private static boolean hasNecklace(ICuriosItemHandler curiosInventory) {
        for (ICurioStacksHandler stacksHandler : curiosInventory.getCurios().values()) {
            if (stacksHandler == null) {
                continue;
            }

            var stacks = stacksHandler.getStacks();

            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack equippedStack = stacks.getStackInSlot(slot);

                if (!equippedStack.isEmpty() && equippedStack.getItem() instanceof RaidbornNecklaceItem) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int countBonusRecruitSlots(ICuriosItemHandler curiosInventory) {
        int bonus = 0;

        for (ICurioStacksHandler stacksHandler : curiosInventory.getCurios().values()) {
            if (stacksHandler == null) {
                continue;
            }

            var stacks = stacksHandler.getStacks();

            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack equippedStack = stacks.getStackInSlot(slot);

                if (!equippedStack.isEmpty() && equippedStack.getItem() instanceof RaidbornNecklaceItem) {
                    bonus += BONUS_RECRUIT_SLOTS;
                }
            }
        }

        return bonus;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(
                tooltip,
                TooltipHelper.line("+" + BONUS_RECRUIT_SLOTS + " Recruitment Slots", 0x55FF55),
                TooltipHelper.line("Preserves your alliance effect after death", 0xAAAAAA)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}