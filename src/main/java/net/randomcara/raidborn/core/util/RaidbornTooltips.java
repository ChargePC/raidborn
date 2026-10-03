package net.randomcara.raidborn.core.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class RaidbornTooltips {
    public static Component line(String key, int color, Object... args) {
        return Component.translatable("tooltip.raidborn." + key, args).withStyle(Style.EMPTY.withColor(color));
    }
}
