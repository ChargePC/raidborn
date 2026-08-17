package net.randomcara.raidborn.core.compat;

import net.minecraftforge.event.AddPackFindersEvent;
import net.randomcara.bentoslib.compat.ModGatedPackLoader;
import net.randomcara.raidborn.Raidborn;

public final class RaidbornCompatPacks {
    private static final String ENCHANT_WITH_MOB_MODID = "enchantwithmob";

    private RaidbornCompatPacks() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        ModGatedPackLoader.registerModGatedPack(
                event,
                Raidborn.MOD_ID,
                Raidborn.LOGGER,
                ENCHANT_WITH_MOB_MODID,
                "compat/enchantwithmob",
                "Raidborn: Enchant With Mob compatibility"
        );
    }
}
