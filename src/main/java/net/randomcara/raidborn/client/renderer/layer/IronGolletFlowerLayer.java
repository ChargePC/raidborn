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

/**
 * Poppy offered to villagers, equivalent to {@code IronGolemFlowerLayer}.
 *
 * <p>The vanilla layer is typed on {@code IronGolem} and reads the golem model, so it cannot be
 * reused here.
 */
@OnlyIn(Dist.CLIENT)
public class IronGolletFlowerLayer extends RenderLayer<IronGollet, IronGolletModel<IronGollet>> {
    /**
     * Flower position in the right arm's frame, in blocks. In the model the arm runs from y=-2 to y=12
     * with its centre at x=-1, so the "hand" sits at the lower end.
     */
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

        // Scale, lay the flower down, and only then centre the block model on the origin. Vanilla
        // centres before scaling, which makes the offset vary with the scale.
        poseStack.scale(FLOWER_SCALE, FLOWER_SCALE, FLOWER_SCALE);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        this.blockRenderer.renderSingleBlock(
                Blocks.POPPY.defaultBlockState(),
                poseStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }
}
