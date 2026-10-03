package net.randomcara.raidborn.content.artifact.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.randomcara.bentoslib.client.tooltip.TooltipHelper;
import net.randomcara.raidborn.content.artifact.api.SlotBoundCurioItem;
import net.randomcara.raidborn.core.util.RaidbornTooltips;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GigaEmeraldItem extends Item implements SlotBoundCurioItem {
    public GigaEmeraldItem(Properties props) {
        super(props);
    }

    @Override
    public String curioSlot() {
        return CHARM;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TooltipHelper.addShiftDescription(tooltip, RaidbornTooltips.line("giga_emerald.reputation", 0x55FF55), RaidbornTooltips.line("giga_emerald.works_with", 0x00AA00));

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
