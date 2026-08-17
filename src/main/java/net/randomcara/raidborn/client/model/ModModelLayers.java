package net.randomcara.raidborn.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.randomcara.raidborn.Raidborn;

public class ModModelLayers {
    public static final ModelLayerLocation IRON_GOLLET =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "iron_gollet"),
                    "main"
            );

    public static final ModelLayerLocation JUGGERNAUT =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "juggernaut"),
                    "main"
            );
}