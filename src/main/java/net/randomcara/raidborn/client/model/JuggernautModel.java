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
import net.randomcara.raidborn.content.entity.juggernaut.Juggernaut;

/** Iron Juggernaut model exported from Blockbench (400x400). */
public class JuggernautModel<T extends Juggernaut> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = ModModelLayers.JUGGERNAUT;

    /**
     * Stride frequency. {@code limbSwing} already grows with distance walked, so the cadence speeds up
     * on its own when he runs; this only sets the step length. The renderer uses the same constant to
     * keep the body sway in phase with the legs.
     */
    public static final float WALK_FREQUENCY = 0.45F;

    private static final float RUN_THRESHOLD = 0.45F;
    private static final float RUN_RANGE = 0.45F;

    private static final float LEG_SWING_WALK = 1.35F;
    private static final float LEG_SWING_RUN_BONUS = 0.55F;
    private static final float ARM_SWING_WALK = 1.00F;
    private static final float ARM_SWING_RUN_BONUS = 0.50F;

    private static final float WINDUP_FRACTION = 0.5F;
    private static final float SLAM_END_FRACTION = 0.7F;

    private static final float ATTACK_BLEND_IN = 0.15F;
    private static final float ATTACK_BLEND_OUT = 0.25F;

    private static final float WINDUP_ARM_ROTATION = -2.2F;
    private static final float SLAM_ARM_ROTATION = 0.7F;

    private static final float SINGLE_ARM_BODY_TWIST = 0.05F;

    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    /** Stored in {@code prepareMobModel} so the swing does not step tick by tick. */
    private float partialTick;

    public JuggernautModel(ModelPart root) {
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.head = root.getChild("head");
        this.leftArm = root.getChild("left_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(16, 77)
                        .addBox(-32.0F, -21.0F, 9.0F, 54.0F, 39.0F, 30.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 152)
                        .addBox(-21.0F, 18.0F, 13.0F, 32.0F, 8.0F, 22.0F, new CubeDeformation(0.0F))
                        .texOffs(186, 97)
                        .addBox(-21.0F, 18.0F, 13.0F, 32.0F, 12.0F, 22.0F, new CubeDeformation(0.025F))
                        .texOffs(132, 0)
                        .addBox(-32.0F, -21.0F, 9.0F, 54.0F, 44.0F, 30.0F, new CubeDeformation(0.025F)),
                PartPose.offset(4.0F, -17.0F, -20.0F)
        );

        partdefinition.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(224, 172)
                        .addBox(-17.8333F, -6.6667F, -9.5F, 19.0F, 61.0F, 19.0F, new CubeDeformation(0.0F))
                        .texOffs(238, 270)
                        .addBox(-17.8333F, -6.6667F, -9.5F, 19.0F, 61.0F, 19.0F, new CubeDeformation(0.025F))
                        .texOffs(0, 193)
                        .addBox(-16.8333F, -0.6667F, -8.5F, 17.0F, 54.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-29.1667F, -32.3333F, 3.5F)
        );

        partdefinition.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 38)
                        .addBox(-8.0F, -9.5F, -9.95F, 16.0F, 13.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 22)
                        .addBox(-2.0F, -3.5F, -12.95F, 4.0F, 9.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(56, 49)
                        .addBox(-8.0F, -9.5F, -9.95F, 16.0F, 17.0F, 10.0F, new CubeDeformation(0.025F)),
                PartPose.offset(-1.0F, -20.5F, -11.05F)
        );

        PartDefinition leftArm = partdefinition.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(224, 172)
                        .mirror()
                        .addBox(-1.1667F, -7.6667F, -9.5F, 19.0F, 61.0F, 19.0F, new CubeDeformation(0.0F))
                        .mirror(false)
                        .texOffs(0, 320)
                        .addBox(-1.1667F, -7.6667F, -9.5F, 19.0F, 61.0F, 19.0F, new CubeDeformation(0.025F))
                        .texOffs(0, 193)
                        .mirror()
                        .addBox(-0.1667F, -1.6667F, -8.5F, 17.0F, 54.0F, 17.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(27.1667F, -31.3333F, 3.5F)
        );

        leftArm.addOrReplaceChild("crusher2", CubeListBuilder.create(), PartPose.offset(8.3333F, 25.3333F, 0.0F));

        partdefinition.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(63, 17)
                        .mirror()
                        .addBox(-5.5F, -1.5F, -5.5F, 11.0F, 19.0F, 11.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(13.5F, 6.5F, 3.5F)
        );

        partdefinition.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(63, 17)
                        .addBox(-5.5F, -1.5F, -5.5F, 11.0F, 19.0F, 11.0F, new CubeDeformation(0.0F))
                        .texOffs(116, 164)
                        .addBox(-5.5F, -1.5F, -5.5F, 11.0F, 19.0F, 11.0F, new CubeDeformation(0.025F)),
                PartPose.offset(-15.5F, 6.5F, 3.5F)
        );

        return LayerDefinition.create(meshdefinition, 400, 400);
    }

    @Override
    public void prepareMobModel(T entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.partialTick = partialTick;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.body.resetPose();
        this.head.resetPose();
        this.leftArm.resetPose();
        this.rightArm.resetPose();
        this.leftLeg.resetPose();
        this.rightLeg.resetPose();

        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        float gait = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float run = smoothStep(Mth.clamp((gait - RUN_THRESHOLD) / RUN_RANGE, 0.0F, 1.0F));

        this.applyIdle(ageInTicks, 1.0F - gait);
        this.applyWalk(limbSwing, gait, run);
        this.applyAttack(entity);
    }

    private void applyIdle(float ageInTicks, float weight) {
        if (weight <= 0.0F) {
            return;
        }

        float breath = Mth.cos(ageInTicks * 0.055F);

        this.body.xRot += breath * 0.020F * weight;
        this.head.xRot += breath * 0.015F * weight;

        this.leftArm.xRot += breath * 0.050F * weight;
        this.rightArm.xRot += breath * 0.050F * weight;

        this.leftArm.zRot -= (0.05F + breath * 0.02F) * weight;
        this.rightArm.zRot += (0.05F + breath * 0.02F) * weight;
    }

    private void applyWalk(float limbSwing, float gait, float run) {
        if (gait <= 0.0F) {
            return;
        }

        float swing = Mth.cos(limbSwing * WALK_FREQUENCY);
        float counterSwing = -swing;

        float legAmount = (LEG_SWING_WALK + LEG_SWING_RUN_BONUS * run) * gait;
        float armAmount = (ARM_SWING_WALK + ARM_SWING_RUN_BONUS * run) * gait;

        this.rightLeg.xRot += swing * legAmount;
        this.leftLeg.xRot += counterSwing * legAmount;

        float legSpread = (0.05F + 0.07F * run) * gait;
        this.rightLeg.zRot -= legSpread + swing * 0.05F * gait;
        this.leftLeg.zRot += legSpread + counterSwing * 0.05F * gait;

        this.rightArm.xRot += counterSwing * armAmount;
        this.leftArm.xRot += swing * armAmount;

        float armSpread = 0.06F + 0.20F * run;
        this.rightArm.zRot += armSpread + counterSwing * 0.10F * gait;
        this.leftArm.zRot -= armSpread + swing * 0.10F * gait;

        float twist = (0.06F + 0.07F * run) * gait;
        this.body.yRot += swing * twist;
        this.head.yRot -= swing * twist * 0.6F;

        this.body.xRot -= 0.10F * run * gait;
    }

    private void applyAttack(T entity) {
        // Without partial ticks the swing would step in 20 visible increments per second.
        float attackTicks = entity.getAttackAnimationTicks() - this.partialTick;

        if (attackTicks <= 0.0F) {
            return;
        }

        float length = entity.getAttackAnimationLength();
        float progress = Mth.clamp(1.0F - (attackTicks / length), 0.0F, 1.0F);
        float armRotation;

        if (progress < WINDUP_FRACTION) {
            float t = progress / WINDUP_FRACTION;
            armRotation = WINDUP_ARM_ROTATION * Mth.sin(t * Mth.HALF_PI);
        } else if (progress < SLAM_END_FRACTION) {
            float t = (progress - WINDUP_FRACTION) / (SLAM_END_FRACTION - WINDUP_FRACTION);
            armRotation = Mth.lerp(smoothStep(t), WINDUP_ARM_ROTATION, SLAM_ARM_ROTATION);
        } else {
            float t = (progress - SLAM_END_FRACTION) / (1.0F - SLAM_END_FRACTION);
            armRotation = Mth.lerp(smoothStep(t), SLAM_ARM_ROTATION, 0.0F);
        }

        // The swing blends over the walk instead of replacing it, otherwise the arms jump on the first
        // and last frame of the attack.
        float blend = smoothStep(attackBlendWeight(progress));

        // The arm outside the swing stays on the walk animation and is not overwritten.
        Juggernaut.SwingArms arms = entity.getSwingArms();

        if (arms != Juggernaut.SwingArms.LEFT) {
            this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, armRotation);
            this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, 0.10F);
            this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, 0.06F);
        }

        if (arms != Juggernaut.SwingArms.RIGHT) {
            this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, armRotation);
            this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, -0.10F);
            this.leftArm.yRot = Mth.lerp(blend, this.leftArm.yRot, -0.06F);
        }

        this.body.xRot = Mth.lerp(blend, this.body.xRot, armRotation * -0.08F);

        // In the rig the arms are siblings of the body: twisting above this factor visibly detaches them.
        if (arms != Juggernaut.SwingArms.BOTH) {
            float side = arms == Juggernaut.SwingArms.LEFT ? 1.0F : -1.0F;
            this.body.yRot = Mth.lerp(blend, this.body.yRot, armRotation * SINGLE_ARM_BODY_TWIST * side);
        }
    }

    private static float attackBlendWeight(float progress) {
        if (progress < ATTACK_BLEND_IN) {
            return progress / ATTACK_BLEND_IN;
        }

        if (progress > 1.0F - ATTACK_BLEND_OUT) {
            return (1.0F - progress) / ATTACK_BLEND_OUT;
        }

        return 1.0F;
    }

    /** S-curve: starts and ends at zero speed, so nothing snaps at the seams. */
    private static float smoothStep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.rightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.leftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.leftLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.rightLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
