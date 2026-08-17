package net.randomcara.raidborn.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.randomcara.raidborn.content.entity.iron_gollet.IronGollet;

public class IronGolletModel<T extends IronGollet> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = ModModelLayers.IRON_GOLLET;

    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public IronGolletModel(ModelPart root) {
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /** Poppy-holding arm, as in vanilla {@code IronGolemModel}. */
    public ModelPart getFlowerHoldingArm() {
        return this.rightArm;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(50, 19)
                        .addBox(-1.0F, -2.0F, -2.5F, 4.0F, 14.0F, 5.0F, new CubeDeformation(0.025F))
                        .texOffs(32, 36)
                        .mirror()
                        .addBox(-1.0F, -2.0F, -2.5F, 4.0F, 14.0F, 5.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(8.6F, 11.0F, -1.5F)
        );

        partdefinition.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(46, 0)
                        .addBox(-3.0F, -2.0F, -2.5F, 4.0F, 14.0F, 5.0F, new CubeDeformation(0.025F))
                        .texOffs(32, 36)
                        .addBox(-3.0F, -2.0F, -2.5F, 4.0F, 14.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-8.4F, 11.0F, -1.5F)
        );

        PartDefinition head = partdefinition.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 51)
                        .addBox(-1.0F, -1.975F, -6.05F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 36)
                        .addBox(-4.0F, -5.975F, -4.05F, 8.0F, 7.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 47)
                        .addBox(-4.0F, -17.975F, -0.05F, 8.0F, 12.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 11.975F, -2.95F)
        );

        head.addOrReplaceChild(
                "cube_r1",
                CubeListBuilder.create()
                        .texOffs(50, 47)
                        .addBox(-4.0F, -6.0F, 0.0F, 8.0F, 12.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -11.975F, -0.05F, 0.0F, -1.5708F, 0.0F)
        );

        partdefinition.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-7.4F, -8.0F, -4.0F, 15.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 18)
                        .addBox(-7.4F, -8.0F, -4.0F, 15.0F, 10.0F, 8.0F, new CubeDeformation(0.025F)),
                PartPose.offset(0.0F, 18.0F, -1.0F)
        );

        partdefinition.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(50, 38)
                        .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, 20.0F, -0.5F)
        );

        partdefinition.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(50, 38)
                        .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, 20.0F, -0.5F)
        );

        return LayerDefinition.create(meshdefinition, 70, 70);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.leftArm.resetPose();
        this.rightArm.resetPose();
        this.head.resetPose();
        this.body.resetPose();
        this.rightLeg.resetPose();
        this.leftLeg.resetPose();

        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        float walkAmount = Math.min(limbSwingAmount, 1.0F);
        float walkSpeed = 0.6662F;

        this.rightLeg.xRot = Mth.cos(limbSwing * walkSpeed) * 1.45F * walkAmount;
        this.leftLeg.xRot = Mth.cos(limbSwing * walkSpeed + Mth.PI) * 1.45F * walkAmount;

        this.rightLeg.zRot = Mth.cos(limbSwing * walkSpeed) * 0.06F * walkAmount;
        this.leftLeg.zRot = Mth.cos(limbSwing * walkSpeed + Mth.PI) * 0.06F * walkAmount;

        this.rightArm.xRot = Mth.cos(limbSwing * walkSpeed + Mth.PI) * 0.95F * walkAmount;
        this.leftArm.xRot = Mth.cos(limbSwing * walkSpeed) * 0.95F * walkAmount;

        this.rightArm.zRot = Mth.cos(limbSwing * walkSpeed + Mth.PI) * 0.04F * walkAmount;
        this.leftArm.zRot = Mth.cos(limbSwing * walkSpeed) * 0.04F * walkAmount;

        this.body.xRot = Mth.cos(limbSwing * walkSpeed * 0.5F) * 0.025F * walkAmount;
        this.body.zRot = Mth.cos(limbSwing * walkSpeed) * 0.025F * walkAmount;

        if (entity.isCarryingVillager()) {
            this.rightArm.xRot = -2.75F;
            this.leftArm.xRot = -2.75F;
            this.rightArm.zRot = 0.15F;
            this.leftArm.zRot = -0.15F;
            return;
        }

        int attackTicks = entity.getAttackAnimationTicks();

        if (attackTicks > 0) {
            float attackProgress = 1.0F - ((float) attackTicks / 10.0F);
            float attackCurve = Mth.sin(attackProgress * Mth.PI);

            float attackArmRotation = -2.356F * attackCurve;

            this.rightArm.xRot = attackArmRotation;
            this.leftArm.xRot = attackArmRotation;

            this.rightArm.yRot = 0.08F * attackCurve;
            this.leftArm.yRot = -0.08F * attackCurve;

            this.rightArm.zRot = 0.12F * attackCurve;
            this.leftArm.zRot = -0.12F * attackCurve;

            this.body.xRot = -0.05F * attackCurve;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.leftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.rightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.rightLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.leftLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}