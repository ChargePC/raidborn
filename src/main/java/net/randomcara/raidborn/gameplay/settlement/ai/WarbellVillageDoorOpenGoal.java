package net.randomcara.raidborn.gameplay.settlement.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

public class WarbellVillageDoorOpenGoal extends OpenDoorGoal {
    private final Mob mob;

    public WarbellVillageDoorOpenGoal(Mob mob) {
        super(mob, true);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!canOpenDoorsNow()) return false;
        applyDoorPathing(true);
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return canOpenDoorsNow() && super.canContinueToUse();
    }

    @Override
    public void start() {
        applyDoorPathing(true);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();

        if (!WarbellVillageData.isVillageMode(this.mob)) {
            applyDoorPathing(false);
        }
    }

    private boolean canOpenDoorsNow() {
        return WarbellVillageData.isVillageMode(this.mob)
                && !this.mob.isSleeping()
                && this.mob.getNavigation() instanceof GroundPathNavigation;
    }

    private void applyDoorPathing(boolean value) {
        if (this.mob.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(value);
            navigation.setCanPassDoors(value);
        }
    }
}
