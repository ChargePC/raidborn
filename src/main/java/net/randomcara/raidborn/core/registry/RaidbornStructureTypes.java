package net.randomcara.raidborn.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.bentoslib.world.structure.TerrainCheckedJigsawStructure;
import net.randomcara.raidborn.Raidborn;

public final class RaidbornStructureTypes {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Raidborn.MOD_ID);

    // The class lives in BentosLib but the registry id stays under the `raidborn` namespace:
    // changing it would break structures already generated in saved worlds.
    public static final RegistryObject<StructureType<TerrainCheckedJigsawStructure>> TERRAIN_CHECKED_JIGSAW =
            STRUCTURE_TYPES.register("terrain_checked_jigsaw", () -> () -> TerrainCheckedJigsawStructure.CODEC);

    static {
        TerrainCheckedJigsawStructure.setStructureType(TERRAIN_CHECKED_JIGSAW::get);
    }

    private RaidbornStructureTypes() {
    }

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
    }
}
