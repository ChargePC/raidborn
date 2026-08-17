package net.randomcara.raidborn.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.grumblager.Grumblager;

public class GrumblagerModel<T extends Grumblager> extends EntityModel<T> implements ArmedModel, HeadedModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "grumblager"), "main");

    private final ModelPart head;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart body;

    public GrumblagerModel(ModelPart root) {
        this.head = root.getChild("head");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        partDefinition.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 28).addBox(-4.0F, -5.0F, -3.5F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 42).addBox(-1.0F, -2.0F, -5.5F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 9.0F, -2.5F)
        );

        partDefinition.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 42).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.2F))
                        .texOffs(40, 15).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.0F, 18.0F, 0.0F)
        );

        partDefinition.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 42).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.2F))
                        .texOffs(40, 15).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(3.0F, 18.0F, 0.0F)
        );

        partDefinition.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.2F))
                        .texOffs(32, 28).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-7.0F, 10.0F, 0.0F)
        );

        partDefinition.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(40, 0).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.2F)).mirror(false)
                        .texOffs(32, 28).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(7.0F, 10.0F, 0.0F)
        );

        partDefinition.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6.0F, -8.0F, -4.0F, 12.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 18).addBox(-6.0F, 2.0F, -4.0F, 12.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 16.0F, 0.0F)
        );

        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;

        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.55F * limbSwingAmount;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 0.55F * limbSwingAmount;

        this.rightArm.yRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        this.leftLeg.zRot = 0.0F;

        ItemStack mainHand = entity.getMainHandItem();
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        float attackTime = entity.getAttackAnim(ageInTicks);

        if (mainHand.getItem() instanceof CrossbowItem) {
            if (entity.isChargingCrossbow()) {
                this.animateCrossbowCharge(entity, rightHanded);
            } else {
                this.animateCrossbowHold(rightHanded);
            }
        } else if (mainHand.getItem() instanceof BowItem && entity.isUsingItem()) {
            this.animateBowUse(rightHanded);
        } else if (attackTime > 0.0F && isMeleeWeapon(mainHand)) {
            this.animateSimpleSwing(entity, attackTime);
        }
    }

    private boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    private void animateCrossbowCharge(T entity, boolean rightHanded) {
        ModelPart mainArm = rightHanded ? this.rightArm : this.leftArm;
        ModelPart offArm = rightHanded ? this.leftArm : this.rightArm;

        mainArm.xRot = -1.55F + this.head.xRot;
        mainArm.yRot = (rightHanded ? -0.1F : 0.1F) + this.head.yRot;
        mainArm.zRot = 0.0F;

        offArm.xRot = -1.45F + this.head.xRot;
        offArm.yRot = (rightHanded ? 0.55F : -0.55F) + this.head.yRot;
        offArm.zRot = 0.0F;

        float charge = Mth.clamp(entity.getTicksUsingItem() / 20.0F, 0.0F, 1.0F);
        offArm.yRot = Mth.lerp(charge, offArm.yRot, (rightHanded ? 0.15F : -0.15F) + this.head.yRot);
        offArm.xRot = Mth.lerp(charge, offArm.xRot, -1.55F + this.head.xRot);
    }

    private void animateCrossbowHold(boolean rightHanded) {
        ModelPart mainArm = rightHanded ? this.rightArm : this.leftArm;
        ModelPart offArm = rightHanded ? this.leftArm : this.rightArm;

        mainArm.xRot = -1.57F + this.head.xRot;
        mainArm.yRot = (rightHanded ? -0.05F : 0.05F) + this.head.yRot;
        mainArm.zRot = 0.0F;

        offArm.xRot = -1.52F + this.head.xRot;
        offArm.yRot = (rightHanded ? 0.22F : -0.22F) + this.head.yRot;
        offArm.zRot = 0.0F;
    }

    private void animateBowUse(boolean rightHanded) {
        if (rightHanded) {
            this.rightArm.yRot = -0.1F + this.head.yRot;
            this.leftArm.yRot = 0.4F + this.head.yRot;
            this.rightArm.xRot = -1.5707964F + this.head.xRot;
            this.leftArm.xRot = -0.9424779F + this.head.xRot;
        } else {
            this.leftArm.yRot = 0.1F + this.head.yRot;
            this.rightArm.yRot = -0.4F + this.head.yRot;
            this.leftArm.xRot = -1.5707964F + this.head.xRot;
            this.rightArm.xRot = -0.9424779F + this.head.xRot;
        }
    }

    private void animateSimpleSwing(T entity, float attackTime) {
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart attackArm = rightHanded ? this.rightArm : this.leftArm;
        ModelPart otherArm = rightHanded ? this.leftArm : this.rightArm;

        float swing = Mth.sin(attackTime * (float) Math.PI);

        otherArm.xRot *= 0.6F;
        otherArm.yRot = 0.0F;
        otherArm.zRot = 0.0F;

        attackArm.yRot = rightHanded ? -0.08F : 0.08F;
        attackArm.zRot = 0.0F;
        attackArm.xRot = -0.25F - swing * 0.55F;
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        ModelPart armPart = arm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
        armPart.translateAndRotate(poseStack);
        poseStack.translate(0.0D, -0.1D, 0.0D);
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }

    public ModelPart getHeadPart() {
        return this.head;
    }

    public ModelPart getBodyPart() {
        return this.body;
    }

    public ModelPart getRightArmPart() {
        return this.rightArm;
    }

    public ModelPart getLeftArmPart() {
        return this.leftArm;
    }

    public ModelPart getRightLegPart() {
        return this.rightLeg;
    }

    public ModelPart getLeftLegPart() {
        return this.leftLeg;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        rightLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        leftLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        rightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        leftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}