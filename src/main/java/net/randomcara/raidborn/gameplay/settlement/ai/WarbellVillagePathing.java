package net.randomcara.raidborn.gameplay.settlement.ai;

import net.minecraft.world.entity.Mob;
import net.randomcara.raidborn.gameplay.settlement.data.WarbellVillageData;

public final class WarbellVillagePathing {
    private WarbellVillagePathing() {
    }

    public static void applyVillageNavigation(Mob mob) {
        WarbellVillageData.applyVillageNavigation(mob);
    }
}
