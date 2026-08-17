package net.randomcara.raidborn.gameplay.settlement.ai;

import net.minecraft.world.entity.Mob;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageWorkstationData;

public final class WarbellVillageRoutine {
    private static final long WAKE_UP_END = 2000L;
    private static final long WORK_MORNING_END = 6000L;
    private static final long GATHER_END = 9000L;
    private static final long HOME_START = 11500L;
    private static final long NIGHT_START = 13000L;

    private WarbellVillageRoutine() {
    }

    public enum Activity {
        WORK,
        WANDER,
        GATHER,
        HOME,
        SLEEP
    }

    public static boolean isEmployed(Mob mob) {
        return WarbellVillageWorkstationData.canUseWorkstation(mob)
                && WarbellVillageWorkstationData.hasWorkstation(mob)
                && WarbellVillageWorkstationData.isWorkstationValid(mob);
    }

    public static long getDayTime(Mob mob) {
        return mob.level().getDayTime() % 24000L;
    }

    public static boolean isNightTime(Mob mob) {
        long time = getDayTime(mob);
        return mob.level().isNight() || time >= NIGHT_START;
    }

    public static Activity getCurrentActivity(Mob mob) {
        long time = getDayTime(mob);

        if (isNightTime(mob)) return Activity.SLEEP;
        if (time < WAKE_UP_END) return Activity.WANDER;
        if (time < WORK_MORNING_END) return isEmployed(mob) ? Activity.WORK : Activity.WANDER;
        if (time < GATHER_END) return Activity.GATHER;
        if (time < HOME_START) return Activity.WANDER;

        return Activity.HOME;
    }

    public static boolean shouldSleepNow(Mob mob) {
        return getCurrentActivity(mob) == Activity.SLEEP;
    }

    public static boolean shouldGoHomeNow(Mob mob) {
        Activity activity = getCurrentActivity(mob);
        return activity == Activity.HOME || activity == Activity.SLEEP;
    }

    public static boolean shouldGatherNow(Mob mob) {
        return getCurrentActivity(mob) == Activity.GATHER;
    }

    public static boolean shouldWorkNow(Mob mob) {
        return getCurrentActivity(mob) == Activity.WORK;
    }

    public static boolean shouldWanderNow(Mob mob) {
        return getCurrentActivity(mob) == Activity.WANDER;
    }
}
