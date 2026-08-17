package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public class GigaEmeraldItem extends Item implements ICurioItem {

    public GigaEmeraldItem(Properties props) {
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
                TooltipHelper.line("Better reputation means better trade prices", 0x55FF55),
                TooltipHelper.line("Works with villagers and illagers", 0x00AA00)
        );

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
