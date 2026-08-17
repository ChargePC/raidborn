package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.world.entity.Mob;

/**
 * When a mob is allowed to reconsider who it's fighting.
 *
 * <p>Both sides of an Attack retarget a whole group at once and both need the group NOT to
 * recalculate on the same tick. Every swap cancels the current path and vanilla
 * {@code MeleeAttackGoal} then refuses to repath for 20 ticks, so a squad in lockstep just stands
 * there. Interval comes off the mob's UUID: spreads them out, survives a reload.
 */
final class RetargetWindow {

    private RetargetWindow() {
    }

    static boolean isOpen(Mob mob, int minTicks, int jitterTicks) {
        int interval = minTicks + (mob.getUUID().hashCode() & 0x7FFFFFFF) % jitterTicks;
        return mob.tickCount % interval == 0;
    }
}
