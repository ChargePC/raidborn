package net.randomcara.raidborn.core.util;

import net.minecraft.world.entity.Mob;
import net.randomcara.raidborn.Raidborn;

/**
 * Wakes a mob without trusting the bed it is lying on.
 *
 * <p>{@code stopSleeping} reads {@code BedBlock.FACING} off whatever block reports itself as a bed.
 * A modded bed that answers {@code isBed} without carrying that property makes the vanilla call
 * throw, and recruits sleep in whatever the settlement was built with. Losing the wake-up is
 * survivable — the mob stands up on its next path — so it is caught here instead of taking down the
 * squad update around it.
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
