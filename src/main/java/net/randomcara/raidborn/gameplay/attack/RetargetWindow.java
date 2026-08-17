package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.world.entity.Mob;

/**
 * When a mob is allowed to reconsider who it is fighting.
 *
 * <p>Both sides of an Attack pick targets for a whole group at once, and both need the same thing
 * from it: the group must not recalculate on the same tick. Every swap cancels a path, and vanilla
 * {@code MeleeAttackGoal} refuses to repath for 20 ticks afterwards, so a synchronised squad stands
 * still. The interval is derived from the mob's UUID, which spreads the group out and stays stable
 * across reloads.
 */
final class RetargetWindow {

    private RetargetWindow() {
    }

    static boolean isOpen(Mob mob, int minTicks, int jitterTicks) {
        int interval = minTicks + (mob.getUUID().hashCode() & 0x7FFFFFFF) % jitterTicks;
        return mob.tickCount % interval == 0;
    }
}
