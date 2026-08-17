package net.randomcara.raidborn.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.IronGolletModel;
import net.randomcara.raidborn.client.model.ModModelLayers;
import net.randomcara.raidborn.client.renderer.layer.IronGolletFlowerLayer;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;

public class IronGolletRenderer extends MobRenderer<IronGollet, IronGolletModel<IronGollet>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/entity/iron_gollet.png");

    public IronGolletRenderer(EntityRendererProvider.Context context) {
        super(context, new IronGolletModel<>(context.bakeLayer(ModModelLayers.IRON_GOLLET)), 0.45F);
        this.addLayer(new IronGolletFlowerLayer(this, context.getBlockRenderDispatcher()));
    }

    @Override
    public ResourceLocation getTextureLocation(IronGollet entity) {
        return TEXTURE;
    }
}