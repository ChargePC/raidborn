package net.randomcara.raidborn.client.renderer.curio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.randomcara.raidborn.gameplay.banner.BannerSlot;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws the banner mounted on the wearer's head, the way the raid captains carry theirs, using the
 * vanilla banner model.
 *
 * <p>The item still sits in the Curios back slot. That is where it is stored, this is where it shows.
 */
public class BannerCurioRenderer implements ICurioRenderer {

    /** The flag is 40 units tall against an 8 unit head, so it needs to come down a long way. */
    private static final float SCALE = 0.60F;

    /**
     * Where the banner sits on the head, in model units, a sixteenth of a block each. Applied before
     * {@link #SCALE}, so resizing the banner does not shift any of the three.
     *
     * <p>X is negative towards the wearer's right, Y negative upwards, Z negative forwards.
     */
    private static final float OFFSET_X = 0.0F;
    private static final float OFFSET_Y = -12.0F;
    private static final float OFFSET_Z = 3.9F;

    /** Same trick as the block renderer: lift the flag so it hangs from the bar instead of below it. */
    private static final float FLAG_PIVOT_Y = -32.0F;

    private final ModelPart flag;
    private final ModelPart pole;
    private final ModelPart bar;

    public BannerCurioRenderer() {
        ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BANNER);

        this.flag = root.getChild("flag");
        this.pole = root.getChild("pole");
        this.bar = root.getChild("bar");
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource renderTypeBuffer,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {

        // Curios offers the renderer for whatever slot the stack lands in, and only one of them is ours.
        if (!BannerSlot.IDENTIFIER.equals(slotContext.identifier())) return;
        if (!(stack.getItem() instanceof BannerItem banner)) return;
        if (!(renderLayerParent.getModel() instanceof HumanoidModel<?> model)) return;

        poseStack.pushPose();

        // Riding the head part means the banner turns with the head and ducks when they crouch,
        // without any of that being worked out again here.
        model.head.translateAndRotate(poseStack);

        poseStack.translate(OFFSET_X / 16.0F, OFFSET_Y / 16.0F, OFFSET_Z / 16.0F);
        poseStack.scale(SCALE, SCALE, SCALE);

        VertexConsumer consumer = ModelBakery.BANNER_BASE.buffer(renderTypeBuffer, RenderType::entitySolid);
        this.pole.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        this.bar.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);

        // No wind here. The block renderer sways its flag, a banner strapped to someone's head does
        // not, and xRot stays at the zero it was baked with.
        this.flag.y = FLAG_PIVOT_Y;

        BannerRenderer.renderPatterns(
                poseStack,
                renderTypeBuffer,
                light,
                OverlayTexture.NO_OVERLAY,
                this.flag,
                ModelBakery.BANNER_BASE,
                true,
                BannerBlockEntity.createPatterns(banner.getColor(), BannerBlockEntity.getItemPatterns(stack))
        );

        poseStack.popPose();
    }
}
