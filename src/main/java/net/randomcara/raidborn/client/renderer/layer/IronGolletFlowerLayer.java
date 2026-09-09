package net.randomcara.raidborn.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.randomcara.raidborn.client.model.IronGolletModel;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;

@OnlyIn(Dist.CLIENT)
public class IronGolletFlowerLayer extends RenderLayer<IronGollet, IronGolletModel<IronGollet>> {
    private static final float FLOWER_X = -0.16F;
    private static final float FLOWER_Y = 0.72F;
    private static final float FLOWER_Z = -0.16F;
    private static final float FLOWER_SCALE = 0.28F;

    private final BlockRenderDispatcher blockRenderer;

    public IronGolletFlowerLayer(RenderLayerParent<IronGollet, IronGolletModel<IronGollet>> renderer,
                                 BlockRenderDispatcher blockRenderer) {
        super(renderer);
        this.blockRenderer = blockRenderer;
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       IronGollet entity,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        if (entity.getOfferFlowerTick() == 0) {
            return;
        }

        poseStack.pushPose();

        ModelPart arm = this.getParentModel().getFlowerHoldingArm();
        arm.translateAndRotate(poseStack);

        poseStack.translate(FLOWER_X, FLOWER_Y, FLOWER_Z);

        poseStack.scale(FLOWER_SCALE, FLOWER_SCALE, FLOWER_SCALE);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        this.blockRenderer.renderSingleBlock(Blocks.POPPY.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}
