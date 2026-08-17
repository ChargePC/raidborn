package net.randomcara.raidborn.core.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Entity ids from the optional illager mods.
 *
 * <p>Separate from {@link RaidbornCompat}, which answers "is this integration active". This one
 * just names entities. A {@link ResourceLocation} is inert if the mod isn't there, so the constants
 * are always safe to touch and only the lookups need an {@code *Loaded} guard.
 */
public final class RaidbornCompatEntities {
    public static final String SANDR_MODID = RaidbornCompat.SAVAGE_AND_RAVAGE;
    public static final String IINV_MODID = RaidbornCompat.ILLAGER_INVASION;
    public static final String TAP_MODID = RaidbornCompat.TAKES_A_PILLAGE;
    public static final String GI_MODID = RaidbornCompat.GUARD_ILLAGERS;
    public static final String GV_MODID = RaidbornCompat.GUARD_VILLAGERS;
    public static final String HR_MODID = RaidbornCompat.HUNTERS_RETURN;
    public static final String CONJ_MODID = RaidbornCompat.CONJURER_ILLAGER;
    public static final String EWM_MODID = RaidbornCompat.ENCHANT_WITH_MOB;
    public static final String RNC_MODID = RaidbornCompat.RAVAGE_AND_CABBAGE;

    public static final ResourceLocation RNC_CABBAGER = id(RNC_MODID, "cabbager");

    public static final ResourceLocation MC_ILLUSIONER = id("minecraft", "illusioner");

    public static final ResourceLocation SANDR_EXECUTIONER = id(SANDR_MODID, "executioner");
    public static final ResourceLocation SANDR_GRIEFER = id(SANDR_MODID, "griefer");
    public static final ResourceLocation SANDR_ICEOLOGER = id(SANDR_MODID, "iceologer");
    public static final ResourceLocation SANDR_TRICKSTER = id(SANDR_MODID, "trickster");

    public static final ResourceLocation IINV_PROVOKER = id(IINV_MODID, "provoker");
    public static final ResourceLocation IINV_BASHER = id(IINV_MODID, "basher");
    public static final ResourceLocation IINV_SORCERER = id(IINV_MODID, "sorcerer");
    public static final ResourceLocation IINV_ARCHIVIST = id(IINV_MODID, "archivist");
    public static final ResourceLocation IINV_INQUISITOR = id(IINV_MODID, "inquisitor");
    public static final ResourceLocation IINV_MARAUDER = id(IINV_MODID, "marauder");
    public static final ResourceLocation IINV_INVOKER = id(IINV_MODID, "invoker");
    public static final ResourceLocation IINV_ALCHEMIST = id(IINV_MODID, "alchemist");
    public static final ResourceLocation IINV_FIRECALLER = id(IINV_MODID, "firecaller");
    public static final ResourceLocation IINV_NECROMANCER = id(IINV_MODID, "necromancer");

    public static final ResourceLocation TAP_ARCHER = id(TAP_MODID, "archer");
    public static final ResourceLocation TAP_LEGIONER = id(TAP_MODID, "legioner");
    public static final ResourceLocation TAP_SKIRMISHER = id(TAP_MODID, "skirmisher");

    public static final ResourceLocation GI_GUARD = id(GI_MODID, "guard_illager");
    public static final ResourceLocation GV_GUARD = id(GV_MODID, "guard");
    public static final ResourceLocation HR_HUNTER = id(HR_MODID, "hunter");
    public static final ResourceLocation CONJ_CONJURER = id(CONJ_MODID, "conjurer");
    public static final ResourceLocation EWM_ENCHANTER = id(EWM_MODID, "enchanter");

    /** Illager Invasion spellcasters, which flee the player instead of closing in. */
    private static final Set<ResourceLocation> IINV_CASTERS = Set.of(
            IINV_SORCERER,
            IINV_ARCHIVIST,
            IINV_NECROMANCER,
            IINV_INVOKER,
            IINV_FIRECALLER
    );

    /** Savage and Ravage casters that keep their distance the same way. */
    private static final Set<ResourceLocation> SANDR_CASTERS = Set.of(
            SANDR_ICEOLOGER,
            SANDR_TRICKSTER
    );

    private RaidbornCompatEntities() {
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation entityId(Entity entity) {
        return ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
    }

    // Routed through RaidbornCompat rather than ModList so the [compat] config switch can turn an
    // integration off even with the mod installed.

    public static boolean sandrLoaded() {
        return RaidbornCompat.isEnabled(SANDR_MODID);
    }

    public static boolean iinvLoaded() {
        return RaidbornCompat.isEnabled(IINV_MODID);
    }

    public static boolean tapLoaded() {
        return RaidbornCompat.isEnabled(TAP_MODID);
    }

    public static boolean giLoaded() {
        return RaidbornCompat.isEnabled(GI_MODID);
    }

    public static boolean guardVillagersLoaded() {
        return RaidbornCompat.isEnabled(GV_MODID);
    }

    public static boolean hrLoaded() {
        return RaidbornCompat.isEnabled(HR_MODID);
    }

    public static boolean conjLoaded() {
        return RaidbornCompat.isEnabled(CONJ_MODID);
    }

    public static boolean ewmLoaded() {
        return RaidbornCompat.isEnabled(EWM_MODID);
    }

    /**
     * Whether this entity is a spellcaster that runs from players, and therefore carries an
     * {@code AvoidEntityGoal} the alliance effects have to strip.
     */
    public static boolean fleesFromPlayers(Entity entity) {
        if (entity.getType() == EntityType.EVOKER) return true;

        ResourceLocation id = entityId(entity);
        if (id == null) return false;

        return (iinvLoaded() && IINV_CASTERS.contains(id))
                || (sandrLoaded() && SANDR_CASTERS.contains(id));
    }
}
