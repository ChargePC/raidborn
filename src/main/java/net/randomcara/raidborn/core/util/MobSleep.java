package net.randomcara.raidborn.core.util;

import net.minecraft.world.entity.Mob;
import net.randomcara.raidborn.Raidborn;

public class MobSleep {
    public static void wake(Mob mob) {
        try {
            mob.stopSleeping();
        } catch (RuntimeException e) {
            Raidborn.LOGGER.debug("Could not wake {} at {}", mob.getType(), mob.blockPosition(), e);
        }
    }
}
