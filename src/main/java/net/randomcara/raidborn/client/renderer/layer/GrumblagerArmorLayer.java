package net.randomcara.raidborn.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.client.model.GrumblagerModel;
import net.randomcara.raidborn.content.entity.grumblager.Grumblager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GrumblagerArmorLayer extends RenderLayer<Grumblager, GrumblagerModel<Grumblager>> {

    /** Offsets for the vanilla/default armor model only. Positive Y moves down. */
    private static final float HELMET_Y_OFFSET = 1.25F;
    private static final float BOOTS_Y_OFFSET = -6.25F;

    private static final Field MODEL_PART_CHILDREN_FIELD = findModelPartChildrenField();

    private final HumanoidModel<Grumblager> innerModel;
    private final HumanoidModel<Grumblager> outerModel;

    public GrumblagerArmorLayer(RenderLayerParent<Grumblager, GrumblagerModel<Grumblager>> parent,
                                EntityModelSet modelSet) {
        super(parent);
        this.innerModel = new HumanoidModel<>(modelSet.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        this.outerModel = new HumanoidModel<>(modelSet.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       Grumblager entity,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        this.renderArmorPiece(poseStack, buffer, entity, EquipmentSlot.HEAD, packedLight);
        this.renderArmorPiece(poseStack, buffer, entity, EquipmentSlot.CHEST, packedLight);
        this.renderArmorPiece(poseStack, buffer, entity, EquipmentSlot.FEET, packedLight);

        // LEGS is not rendered: the Grumblager can still equip leggings, they just do not show.
    }

    private void renderArmorPiece(PoseStack poseStack,
                                  MultiBufferSource buffer,
                                  Grumblager entity,
                                  EquipmentSlot slot,
                                  int packedLight) {
        ItemStack stack = entity.getItemBySlot(slot);

        if (!(stack.getItem() instanceof ArmorItem armorItem)) {
            return;
        }

        if (armorItem.getEquipmentSlot() != slot) {
            return;
        }

        HumanoidModel<Grumblager> defaultModel = this.usesInnerModel(slot) ? this.innerModel : this.outerModel;

        this.copyPartPoses(defaultModel);
        this.applyDefaultModelOffsets(defaultModel, slot);
        this.setDefaultModelVisibility(defaultModel, slot);

        // Keeps custom models and textures from other mods: pivots and offsets are left untouched.
        Model armorModel = ForgeHooksClient.getArmorModel(
                entity,
                stack,
                slot,
                defaultModel
        );

        // Chestplate only: hides the central torso part without moving anything.
        if (slot == EquipmentSlot.CHEST) {
            hideChestBodyParts(armorModel);
        }

        poseStack.pushPose();

        if (stack.getItem() instanceof DyeableLeatherItem dyeableLeatherItem) {
            int color = dyeableLeatherItem.getColor(stack);
            float red = (float) (color >> 16 & 255) / 255.0F;
            float green = (float) (color >> 8 & 255) / 255.0F;
            float blue = (float) (color & 255) / 255.0F;

            this.renderModel(
                    poseStack,
                    buffer,
                    packedLight,
                    stack,
                    armorModel,
                    this.getArmorTexture(entity, stack, armorItem, slot, null),
                    red,
                    green,
                    blue
            );

            this.renderModel(
                    poseStack,
                    buffer,
                    packedLight,
                    stack,
                    armorModel,
                    this.getArmorTexture(entity, stack, armorItem, slot, "overlay"),
                    1.0F,
                    1.0F,
                    1.0F
            );
        } else {
            this.renderModel(
                    poseStack,
                    buffer,
                    packedLight,
                    stack,
                    armorModel,
                    this.getArmorTexture(entity, stack, armorItem, slot, null),
                    1.0F,
                    1.0F,
                    1.0F
            );
        }

        poseStack.popPose();
    }

    private void renderModel(PoseStack poseStack,
                             MultiBufferSource buffer,
                             int packedLight,
                             ItemStack stack,
                             Model armorModel,
                             ResourceLocation texture,
                             float red,
                             float green,
                             float blue) {
        VertexConsumer vertexConsumer = ItemRenderer.getArmorFoilBuffer(
                buffer,
                RenderType.armorCutoutNoCull(texture),
                false,
                stack.hasFoil()
        );

        armorModel.renderToBuffer(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                red,
                green,
                blue,
                1.0F
        );
    }

    private void copyPartPoses(HumanoidModel<Grumblager> armorModel) {
        GrumblagerModel<Grumblager> parentModel = this.getParentModel();

        armorModel.head.copyFrom(parentModel.getHeadPart());
        armorModel.hat.copyFrom(parentModel.getHeadPart());

        armorModel.body.copyFrom(parentModel.getBodyPart());

        armorModel.rightArm.copyFrom(parentModel.getRightArmPart());
        armorModel.leftArm.copyFrom(parentModel.getLeftArmPart());

        armorModel.rightLeg.copyFrom(parentModel.getRightLegPart());
        armorModel.leftLeg.copyFrom(parentModel.getLeftLegPart());

        armorModel.crouching = false;
        armorModel.riding = false;
        armorModel.young = false;
    }

    private void applyDefaultModelOffsets(HumanoidModel<Grumblager> armorModel, EquipmentSlot slot) {
        switch (slot) {
            case HEAD -> {
                armorModel.head.y += HELMET_Y_OFFSET;
                armorModel.hat.y += HELMET_Y_OFFSET;
            }

            case FEET -> {
                applyRotatedLocalOffset(armorModel.rightLeg, 0.0F, BOOTS_Y_OFFSET, 0.0F);
                applyRotatedLocalOffset(armorModel.leftLeg, 0.0F, BOOTS_Y_OFFSET, 0.0F);
            }

            default -> {
            }
        }
    }

    private static void applyRotatedLocalOffset(ModelPart part, float xOffset, float yOffset, float zOffset) {
        Vector3f offset = new Vector3f(xOffset, yOffset, zOffset);

        offset.rotate(new Quaternionf().rotationZYX(
                part.zRot,
                part.yRot,
                part.xRot
        ));

        part.x += offset.x();
        part.y += offset.y();
        part.z += offset.z();
    }

    private void setDefaultModelVisibility(HumanoidModel<Grumblager> armorModel, EquipmentSlot slot) {
        armorModel.setAllVisible(false);

        switch (slot) {
            case HEAD -> {
                armorModel.head.visible = true;
                armorModel.hat.visible = true;
            }

            case CHEST -> {
                // Vanilla model shows the chestplate arms only; the body would show below the belt.
                armorModel.body.visible = false;
                armorModel.rightArm.visible = true;
                armorModel.leftArm.visible = true;
            }

            case FEET -> {
                armorModel.rightLeg.visible = true;
                armorModel.leftLeg.visible = true;
            }

            default -> {
            }
        }
    }

    private static void hideChestBodyParts(Model model) {
        if (model instanceof HumanoidModel<?> humanoidModel) {
            humanoidModel.body.visible = false;

            // Not setAllVisible(false), which would break pauldrons, extra parts, helmets and boots.
            humanoidModel.rightArm.visible = true;
            humanoidModel.leftArm.visible = true;
        }

        Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        hideChestPartsFromModelFields(model, visited);
    }

    private static void hideChestPartsFromModelFields(Model model, Set<ModelPart> visited) {
        Class<?> clazz = model.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(model);

                    if (fieldValue instanceof ModelPart part) {
                        hideLikelyChestPartTree(field.getName(), part, visited);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // A model we cannot read just keeps its chest plate visible.
                    Raidborn.LOGGER.debug("Could not read model part {}.{}", clazz.getName(), field.getName(), e);
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static void hideLikelyChestPartTree(String partName, ModelPart part, Set<ModelPart> visited) {
        if (part == null || !visited.add(part)) {
            return;
        }

        if (isLikelyCentralChestPart(partName)) {
            part.visible = false;
        }

        Map<String, ModelPart> children = getModelPartChildren(part);
        for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
            hideLikelyChestPartTree(entry.getKey(), entry.getValue(), visited);
        }
    }

    private static boolean isLikelyCentralChestPart(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.contains("arm")
                || lower.contains("shoulder")
                || lower.contains("pauldron")
                || lower.contains("sleeve")
                || lower.contains("left")
                || lower.contains("right")
                || lower.contains("leg")
                || lower.contains("boot")
                || lower.contains("foot")
                || lower.contains("head")
                || lower.contains("helmet")
                || lower.contains("horn")) {
            return false;
        }

        return lower.equals("body")
                || lower.equals("torso")
                || lower.equals("chest")
                || lower.equals("jacket")
                || lower.contains("body")
                || lower.contains("torso")
                || lower.contains("breastplate")
                || lower.contains("abdomen")
                || lower.contains("waist");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> getModelPartChildren(ModelPart part) {
        if (MODEL_PART_CHILDREN_FIELD == null) {
            return Map.of();
        }

        try {
            Object children = MODEL_PART_CHILDREN_FIELD.get(part);
            if (children instanceof Map<?, ?> map) {
                return (Map<String, ModelPart>) map;
            }
        } catch (IllegalAccessException | RuntimeException e) {
            Raidborn.LOGGER.debug("Could not read the children of a ModelPart", e);
        }

        return Map.of();
    }

    private static Field findModelPartChildrenField() {
        for (Field field : ModelPart.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (RuntimeException e) {
                    Raidborn.LOGGER.debug("ModelPart children field is not reachable", e);
                    return null;
                }
            }
        }

        return null;
    }

    private boolean usesInnerModel(EquipmentSlot slot) {
        return false;
    }

    private ResourceLocation getArmorTexture(Grumblager entity,
                                             ItemStack stack,
                                             ArmorItem armorItem,
                                             EquipmentSlot slot,
                                             String type) {
        String materialName = armorItem.getMaterial().getName();
        String namespace = "minecraft";
        String path = materialName;

        int separator = materialName.indexOf(':');
        if (separator >= 0) {
            namespace = materialName.substring(0, separator);
            path = materialName.substring(separator + 1);
        }

        int layer = this.usesInnerModel(slot) ? 2 : 1;

        String defaultTexture = namespace
                + ":textures/models/armor/"
                + path
                + "_layer_"
                + layer
                + (type == null ? "" : "_" + type)
                + ".png";

        String texture = ForgeHooksClient.getArmorTexture(
                entity,
                stack,
                defaultTexture,
                slot,
                type
        );

        ResourceLocation parsed = ResourceLocation.tryParse(texture);
        if (parsed != null) {
            return parsed;
        }

        ResourceLocation fallback = ResourceLocation.tryParse(defaultTexture);
        if (fallback != null) {
            return fallback;
        }

        return ResourceLocation.fromNamespaceAndPath(
                "minecraft",
                "textures/models/armor/leather_layer_1.png"
        );
    }
}