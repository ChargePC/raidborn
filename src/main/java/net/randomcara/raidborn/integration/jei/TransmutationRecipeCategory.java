package net.randomcara.raidborn.integration.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.randomcara.bentoslib.integration.jei.FastCyclingItemStackRenderer;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModBlocks;
import net.randomcara.raidborn.transmutation.recipe.TransmutationRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import java.util.List;

public class TransmutationRecipeCategory implements IRecipeCategory<TransmutationRecipe> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "transmutation");

    public static final RecipeType<TransmutationRecipe> TRANSMUTATION_TYPE =
            RecipeType.create(Raidborn.MOD_ID, "transmutation", TransmutationRecipe.class);

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/jei/transmutation_table.png");
    private static final ResourceLocation BUBBLES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "textures/gui/transmutation_table/bubbles.png");

    private static final int BACKGROUND_WIDTH = 100;
    private static final int BACKGROUND_HEIGHT = 64;

    private static final int SOUL_SLOT_X = 29;
    private static final int SOUL_SLOT_Y = 4;
    private static final int INPUT_SLOT_X = 29;
    private static final int INPUT_SLOT_Y = 45;
    private static final int OUTPUT_SLOT_X = 70;
    private static final int OUTPUT_SLOT_Y = 25;

    private static final int BUBBLES_X = 13;
    private static final int BUBBLES_Y = 1;
    private static final int BUBBLES_WIDTH = 12;
    private static final int BUBBLES_HEIGHT = 29;

    private static final int[] BUBBLE_STEPS = {29, 24, 20, 16, 11, 6, 0};

    private final IDrawable background;
    private final IDrawable icon;

    public TransmutationRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(BACKGROUND_TEXTURE, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.TRANSMUTATION_TABLE.get()));
    }

    @Override
    public RecipeType<TransmutationRecipe> getRecipeType() {
        return TRANSMUTATION_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.raidborn.transmutation_table");
    }

    @Override
    public int getWidth() {
        return BACKGROUND_WIDTH;
    }

    @Override
    public int getHeight() {
        return BACKGROUND_HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TransmutationRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, SOUL_SLOT_X, SOUL_SLOT_Y)
                .addIngredients(recipe.getSoul());

        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_SLOT_X, INPUT_SLOT_Y)
                .addIngredients(recipe.getInput());

        addOutputSlot(builder, recipe.getJeiOutputs());
    }

    private void addOutputSlot(IRecipeLayoutBuilder builder, List<ItemStack> outputs) {
        IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y);
        if (outputs.isEmpty()) {
            return;
        }

        slot.addItemStacks(outputs);

        if (outputs.size() > 1) {
            slot.setCustomRenderer(VanillaTypes.ITEM_STACK, new FastCyclingItemStackRenderer(outputs, 120L));
        }
    }

    @Override
    public void draw(TransmutationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        this.background.draw(guiGraphics, 0, 0);
        drawBubbles(guiGraphics);
    }

    private void drawBubbles(GuiGraphics guiGraphics) {
        int height = BUBBLE_STEPS[(int) ((System.currentTimeMillis() / 100L) % BUBBLE_STEPS.length)];
        if (height <= 0) {
            return;
        }

        guiGraphics.blit(
                BUBBLES_TEXTURE,
                BUBBLES_X,
                BUBBLES_Y + BUBBLES_HEIGHT - height,
                0,
                BUBBLES_HEIGHT - height,
                BUBBLES_WIDTH,
                height,
                BUBBLES_WIDTH,
                BUBBLES_HEIGHT
        );
    }
}
