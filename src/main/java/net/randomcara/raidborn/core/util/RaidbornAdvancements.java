package net.randomcara.raidborn.core.util;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.randomcara.raidborn.Raidborn;

import javax.annotation.Nullable;

/**
 * Grants a criterion on a datapack advancement.
 *
 * <p>Every caller needs the same three guards — no server, advancement not loaded, criterion
 * rejected — so they live here instead of being restated at each award site. A datapack is free to
 * drop any of Raidborn's advancements, so a missing one is a no-op rather than an error.
 */
public final class RaidbornAdvancements {
    private RaidbornAdvancements() {
    }

    /** @return whether the criterion was newly granted. */
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
