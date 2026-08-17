package net.randomcara.raidborn.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.GrumblagerModel;
import net.randomcara.raidborn.client.renderer.layer.GrumblagerArmorLayer;
import net.randomcara.raidborn.content.entity.grumblager.Grumblager;

public class GrumblagerRenderer extends MobRenderer<Grumblager, GrumblagerModel<Grumblager>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/entity/grumblager.png");

    public GrumblagerRenderer(EntityRendererProvider.Context context) {
        super(context, new GrumblagerModel<>(context.bakeLayer(GrumblagerModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new GrumblagerArmorLayer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Grumblager entity) {
        return TEXTURE;
    }
}