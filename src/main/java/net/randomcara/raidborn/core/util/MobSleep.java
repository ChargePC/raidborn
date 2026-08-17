package net.randomcara.raidborn.core.util;

import net.minecraft.world.entity.Mob;
import net.randomcara.raidborn.Raidborn;

/**
 * Wakes a mob without trusting the bed it's lying on.
 *
 * <p>{@code stopSleeping} reads {@code BedBlock.FACING} off whatever block claims to be a bed, so a
 * modded bed that answers {@code isBed} without that property throws. Recruits sleep in whatever
 * the settlement was built out of, so this happens. Missing one wake-up is harmless (the mob stands
 * up on its next path) and better than dropping the whole squad update.
 */
public final class MobSleep {
    private MobSleep() {
    }

    public static void wake(Mob mob) {
        // Called unconditionally, as the vanilla method also resets the pose on a mob that was not
        // asleep, and callers rely on that.
        try {
            mob.stopSleeping();
        } catch (RuntimeException e) {
            Raidborn.LOGGER.debug("Could not wake {} at {}", mob.getType(), mob.blockPosition(), e);
        }
    }
}
