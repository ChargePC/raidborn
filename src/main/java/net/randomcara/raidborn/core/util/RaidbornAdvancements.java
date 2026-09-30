package net.randomcara.raidborn.core.util;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.randomcara.raidborn.Raidborn;
import org.jetbrains.annotations.Nullable;

public class RaidbornAdvancements {
    public static boolean award(@Nullable ServerPlayer player, ResourceLocation advancementId, String criterion) {
        if (player == null || player.server == null) return false;

        Advancement advancement = player.server.getAdvancements().getAdvancement(advancementId);
        if (advancement == null) {
            Raidborn.LOGGER.debug("Advancement {} is not loaded; skipping criterion {}", advancementId, criterion);
            return false;
        }

        try {
            return player.getAdvancements().award(advancement, criterion);
        } catch (RuntimeException e) {
            Raidborn.LOGGER.warn("Failed to award {} on {}", criterion, advancementId, e);
            return false;
        }
    }
}
