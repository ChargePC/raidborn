package net.randomcara.raidborn.integration.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModBlocks;
import net.randomcara.raidborn.transmutation.recipe.TransmutationRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import java.util.List;

@JeiPlugin
public class RaidbornJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new TransmutationRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<TransmutationRecipe> recipes = minecraft.level.getRecipeManager()
                .getAllRecipesFor(TransmutationRecipe.Type.INSTANCE);

        registration.addRecipes(TransmutationRecipeCategory.TRANSMUTATION_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.TRANSMUTATION_TABLE.get()),
                TransmutationRecipeCategory.TRANSMUTATION_TYPE
        );
    }
}
