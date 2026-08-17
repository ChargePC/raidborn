package net.randomcara.raidborn.core.registry;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.transmutation.recipe.TransmutationRecipe;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Raidborn.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TransmutationRecipe>> TRANSMUTATION =
            SERIALIZERS.register("transmutation", () -> TransmutationRecipe.Serializer.INSTANCE);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
