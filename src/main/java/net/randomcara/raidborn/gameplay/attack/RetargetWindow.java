package net.randomcara.raidborn.gameplay.attack;

import net.minecraft.world.entity.Mob;

class RetargetWindow {
    static boolean isOpen(Mob mob, int minTicks, int jitterTicks) {
        int interval = minTicks + (mob.getUUID().hashCode() & 0x7FFFFFFF) % jitterTicks;
        return mob.tickCount % interval == 0;
    }
}
