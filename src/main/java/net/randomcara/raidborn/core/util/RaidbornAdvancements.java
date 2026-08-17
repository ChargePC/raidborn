package net.randomcara.raidborn.core.util;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.randomcara.raidborn.Raidborn;

import javax.annotation.Nullable;

/**
 * Grants a criterion on a datapack advancement.
 *
 * <p>Same three guards every time (no server, advancement not loaded, criterion rejected), so
 * they're here instead of copy-pasted at every award site. Datapacks can drop any of Raidborn's
 * advancements, so a missing one is a no-op and not an error.
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
