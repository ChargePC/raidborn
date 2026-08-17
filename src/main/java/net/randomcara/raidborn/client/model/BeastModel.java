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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.content.entity.beast.Beast;

public class BeastModel<T extends Beast>
        extends EntityModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(
                            Raidborn.MOD_ID,
                            "beast"
                    ),
                    "main"
            );

    private static final float ATTACK_DURATION = 16.0F;

    /** Positive value opens the lid upwards. */
    private static final float CHEST_OPEN_ROTATION =
            40.0F * ((float) Math.PI / 180.0F);

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart chestBase;
    private final ModelPart chestLid;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart bbMain;

    public BeastModel(ModelPart root) {
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.chestBase = root.getChild("chest");
        this.chestLid = this.chestBase.getChild("chest_lid");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
        this.bbMain = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(88, 93)
                        .addBox(-4.0F, -7.75F, -8.0F, 8.0F, 11.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(56, 93)
                        .addBox(-4.0F, -7.75F, -8.0F, 8.0F, 19.0F, 8.0F, new CubeDeformation(0.2F))
                        .texOffs(90, 23)
                        .addBox(-2.0F, -0.75F, -10.0F, 4.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.25F, -7.0F)
        );

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 36)
                        .addBox(-11.0F, -37.0F, -5.0F, 20.0F, 26.0F, 14.0F, new CubeDeformation(0.0F))
                        .texOffs(68, 36)
                        .addBox(-11.0F, -37.0F, -5.0F, 20.0F, 25.0F, 14.0F, new CubeDeformation(0.2F)),
                PartPose.offset(1.0F, 25.0F, -2.0F)
        );

        PartDefinition chest = root.addOrReplaceChild(
                "chest",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-11.0F, -5.25F, -3.75F, 22.0F, 21.0F, 15.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.75F, 8.75F)
        );

        chest.addOrReplaceChild(
                "chest_lid",
                CubeListBuilder.create()
                        .texOffs(74, 0)
                        .addBox(-11.0F, -8.0F, 0.0F, 22.0F, 8.0F, 15.0F, new CubeDeformation(0.0F))
                        .texOffs(74, 31)
                        .addBox(-1.0F, -2.0F, 15.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(74, 23)
                        .addBox(-4.0F, 1.0F, 15.5F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -5.25F, -3.75F)
        );

        root.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(0, 93).mirror()
                        .addBox(0.0F, -3.0F, -4.0F, 6.0F, 32.0F, 8.0F, new CubeDeformation(0.0F))
                        .mirror(false)
                        .texOffs(28, 93).mirror()
                        .addBox(0.0F, -3.0F, -4.0F, 6.0F, 32.0F, 8.0F, new CubeDeformation(0.1F))
                        .mirror(false),
                PartPose.offset(10.0F, -9.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(0, 93)
                        .addBox(-6.0F, -3.0F, -4.0F, 6.0F, 32.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(28, 93)
                        .addBox(-6.0F, -3.0F, -4.0F, 6.0F, 32.0F, 8.0F, new CubeDeformation(0.1F)),
                PartPose.offset(-10.0F, -9.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(88, 112).mirror()
                        .addBox(-3.0F, 0.0F, -3.5F, 6.0F, 10.0F, 7.0F, new CubeDeformation(0.0F))
                        .mirror(false),
                PartPose.offset(6.0F, 14.0F, 0.5F)
        );

        root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(88, 112)
                        .addBox(-3.0F, 0.0F, -3.5F, 6.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-6.0F, 14.0F, 0.5F)
        );

        root.addOrReplaceChild(
                "bb_main",
                CubeListBuilder.create()
                        .texOffs(68, 75)
                        .addBox(-10.0F, -10.0F, -7.0F, 20.0F, 4.0F, 14.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 76)
                        .addBox(-10.0F, -6.0F, -7.0F, 20.0F, 3.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F)
        );

        return LayerDefinition.create(
                meshDefinition,
                256,
                256
        );
    }

    @Override
    public void setupAnim(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        this.head.yRot =
                netHeadYaw
                        * ((float) Math.PI / 180.0F);

        this.head.xRot =
                headPitch
                        * ((float) Math.PI / 180.0F);

        this.leftArm.yRot = 0.0F;
        this.rightArm.yRot = 0.0F;

        this.leftArm.zRot = 0.0F;
        this.rightArm.zRot = 0.0F;

        this.leftLeg.yRot = 0.0F;
        this.rightLeg.yRot = 0.0F;

        this.leftLeg.zRot = 0.0F;
        this.rightLeg.zRot = 0.0F;

        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;

        float rightArmWalk =
                Mth.cos(
                        limbSwing * 0.6662F
                                + (float) Math.PI
                )
                        * 0.9F
                        * limbSwingAmount;

        float leftArmWalk =
                Mth.cos(
                        limbSwing * 0.6662F
                )
                        * 0.9F
                        * limbSwingAmount;

        this.rightLeg.xRot =
                Mth.cos(
                        limbSwing * 0.6662F
                )
                        * 1.4F
                        * limbSwingAmount;

        this.leftLeg.xRot =
                Mth.cos(
                        limbSwing * 0.6662F
                                + (float) Math.PI
                )
                        * 1.4F
                        * limbSwingAmount;

        float partialTick =
                Mth.clamp(
                        ageInTicks - entity.tickCount,
                        0.0F,
                        1.0F
                );

        float attackTick =
                Math.max(
                        0.0F,
                        entity.getAttackAnimationTick()
                                - partialTick
                );

        if (attackTick > 0.0F) {
            float progress =
                    Mth.clamp(
                            1.0F
                                    - attackTick
                                    / ATTACK_DURATION,
                            0.0F,
                            1.0F
                    );

            float armRotation;

            if (progress < 0.35F) {
                armRotation =
                        lerp(
                                0.0F,
                                -Mth.PI,
                                smooth(
                                        progress / 0.35F
                                )
                        );
            } else if (progress < 0.75F) {
                armRotation =
                        lerp(
                                -Mth.PI,
                                0.0F,
                                smooth(
                                        (progress - 0.35F)
                                                / 0.40F
                                )
                        );
            } else {
                float returnProgress =
                        smooth(
                                (progress - 0.75F)
                                        / 0.25F
                        );

                this.rightArm.xRot =
                        lerp(
                                0.0F,
                                rightArmWalk,
                                returnProgress
                        );

                this.leftArm.xRot =
                        lerp(
                                0.0F,
                                leftArmWalk,
                                returnProgress
                        );

                armRotation = Float.NaN;
            }

            if (!Float.isNaN(armRotation)) {
                this.rightArm.xRot = armRotation;
                this.leftArm.xRot = armRotation;
            }
        } else {
            this.rightArm.xRot = rightArmWalk;
            this.leftArm.xRot = leftArmWalk;
        }

        this.chestLid.xRot =
                CHEST_OPEN_ROTATION
                        * entity.getChestLidProgress(partialTick);
    }

    private static float lerp(
            float start,
            float end,
            float progress
    ) {
        return start
                + (end - start)
                * progress;
    }

    private static float smooth(float value) {
        value =
                Mth.clamp(
                        value,
                        0.0F,
                        1.0F
                );

        return value
                * value
                * (3.0F - 2.0F * value);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.chestBase.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.leftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.rightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.leftLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.rightLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.bbMain.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}