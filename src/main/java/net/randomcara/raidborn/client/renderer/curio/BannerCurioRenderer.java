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

public class BannerCurioRenderer implements ICurioRenderer {
    private static final float SCALE = 0.60F;
    private static final float OFFSET_X = 0.0F;
    private static final float OFFSET_Y = -12.0F;
    private static final float OFFSET_Z = 3.9F;
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
        if (!BannerSlot.IDENTIFIER.equals(slotContext.identifier())) return;
        if (!(stack.getItem() instanceof BannerItem banner)) return;
        if (!(renderLayerParent.getModel() instanceof HumanoidModel<?> model)) return;

        poseStack.pushPose();

        model.head.translateAndRotate(poseStack);

        poseStack.translate(OFFSET_X / 16.0F, OFFSET_Y / 16.0F, OFFSET_Z / 16.0F);
        poseStack.scale(SCALE, SCALE, SCALE);

        VertexConsumer consumer = ModelBakery.BANNER_BASE.buffer(renderTypeBuffer, RenderType::entitySolid);
        this.pole.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        this.bar.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);

        this.flag.y = FLAG_PIVOT_Y;

        BannerRenderer.renderPatterns(poseStack, renderTypeBuffer, light, OverlayTexture.NO_OVERLAY, this.flag, ModelBakery.BANNER_BASE, true, BannerBlockEntity.createPatterns(banner.getColor(), BannerBlockEntity.getItemPatterns(stack)));

        poseStack.popPose();
    }
}
