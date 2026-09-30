package net.randomcara.raidborn.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.JuggernautModel;
import net.randomcara.raidborn.client.model.ModModelLayers;
import net.randomcara.raidborn.content.entity.juggernaut.Juggernaut;

public class JuggernautRenderer extends MobRenderer<Juggernaut, JuggernautModel<Juggernaut>> {
    private static final ResourceLocation TEXTURE = Raidborn.id("textures/entity/juggernaut.png");
    private static final float MODEL_SCALE = 0.76F;
    private static final float SHADOW_RADIUS = 1.3F;
    private static final float LEAN_WALK_DEGREES = 6.0F;
    private static final float LEAN_RUN_BONUS_DEGREES = 4.5F;
    private static final float STEP_BOB_WALK = 0.045F;
    private static final float STEP_BOB_RUN_BONUS = 0.055F;
    private static final float MIN_WALK_SPEED = 0.01F;

    public JuggernautRenderer(EntityRendererProvider.Context context) {
        super(context, new JuggernautModel<>(context.bakeLayer(ModModelLayers.JUGGERNAUT)), SHADOW_RADIUS);
    }

    @Override
    protected void setupRotations(Juggernaut entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick);

        float speed = Mth.clamp(entity.walkAnimation.speed(partialTick), 0.0F, 1.0F);
        if (speed < MIN_WALK_SPEED) {
            return;
        }

        float walkPosition = entity.walkAnimation.position(partialTick);
        float swing = Mth.cos(walkPosition * JuggernautModel.WALK_FREQUENCY);
        float lean = (LEAN_WALK_DEGREES + LEAN_RUN_BONUS_DEGREES * speed) * speed * swing;
        float stepBob = (STEP_BOB_WALK + STEP_BOB_RUN_BONUS * speed) * speed;
        poseStack.translate(0.0F, stepBob * Mth.abs(swing), 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(lean));
    }

    @Override
    protected void scale(Juggernaut entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(Juggernaut entity) {
        return TEXTURE;
    }
}
