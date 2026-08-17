package net.randomcara.raidborn.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.BeastModel;
import net.randomcara.raidborn.content.entity.beast.Beast;

public class BeastRenderer extends MobRenderer<Beast, BeastModel<Beast>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/entity/beast.png");

    public BeastRenderer(EntityRendererProvider.Context context) {
        super(context, new BeastModel<>(context.bakeLayer(BeastModel.LAYER_LOCATION)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(Beast entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Beast entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(1.0F, 1.0F, 1.0F);
    }
}