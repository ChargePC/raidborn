package net.randomcara.raidborn.core.compat;

import net.minecraftforge.fml.ModList;
import net.randomcara.raidborn.core.config.RaidbornServerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Single place to ask whether an optional integration is active. */
public final class RaidbornCompat {
    public static final String ILLAGER_INVASION = "illagerinvasion";
    public static final String SAVAGE_AND_RAVAGE = "savage_and_ravage";
    public static final String TAKES_A_PILLAGE = "takesapillage";
    public static final String GUARD_ILLAGERS = "guardillagers";
    public static final String GUARD_VILLAGERS = "guardvillagers";
    public static final String HUNTERS_RETURN = "hunters_return";
    public static final String CONJURER_ILLAGER = "conjurer_illager";
    public static final String ENCHANT_WITH_MOB = "enchantwithmob";
    public static final String RAVAGE_AND_CABBAGE = "ravageandcabbage";
    public static final String ARTIFACTS = "artifacts";

    private static final Map<String, BooleanSupplier> SWITCHES = new HashMap<>();

    static {
        SWITCHES.put(ILLAGER_INVASION, RaidbornServerConfig::isCompatIllagerInvasionEnabled);
        SWITCHES.put(SAVAGE_AND_RAVAGE, RaidbornServerConfig::isCompatSavageAndRavageEnabled);
        SWITCHES.put(TAKES_A_PILLAGE, RaidbornServerConfig::isCompatTakesAPillageEnabled);
        SWITCHES.put(GUARD_ILLAGERS, RaidbornServerConfig::isCompatGuardIllagersEnabled);
        SWITCHES.put(GUARD_VILLAGERS, RaidbornServerConfig::isCompatGuardVillagersEnabled);
        SWITCHES.put(HUNTERS_RETURN, RaidbornServerConfig::isCompatHuntersReturnEnabled);
        SWITCHES.put(CONJURER_ILLAGER, RaidbornServerConfig::isCompatConjurerIllagerEnabled);
        SWITCHES.put(ENCHANT_WITH_MOB, RaidbornServerConfig::isCompatEnchantWithMobEnabled);
        SWITCHES.put(RAVAGE_AND_CABBAGE, RaidbornServerConfig::isCompatRavageAndCabbageEnabled);
        SWITCHES.put(ARTIFACTS, RaidbornServerConfig::isCompatArtifactsEnabled);
    }

    private RaidbornCompat() {
    }

    public static boolean isEnabled(String modid) {
        return ModList.get().isLoaded(modid) && isEnabledInConfig(modid);
    }

    /** Presence only, ignoring the config switch. */
    public static boolean isLoaded(String modid) {
        return ModList.get().isLoaded(modid);
    }

    private static boolean isEnabledInConfig(String modid) {
        BooleanSupplier configSwitch = SWITCHES.get(modid);

        if (configSwitch == null) {
            return true;
        }

        // Fails open on purpose. This also runs on the client, including before entering a world, where
        // the server config may not be loaded yet.
        if (!RaidbornServerConfig.SPEC.isLoaded()) {
            return true;
        }

        return configSwitch.getAsBoolean();
    }
}
