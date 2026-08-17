package net.randomcara.raidborn.transmutation.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.randomcara.raidborn.Raidborn;
import net.randomcara.raidborn.core.registry.ModRecipeSerializers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TransmutationRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient soul;
    private final Ingredient input;
    private final ItemStack output;
    private final ResourceLocation outputTag;
    private final int outputTagCount;

    public TransmutationRecipe(ResourceLocation id, Ingredient soul, Ingredient input, ItemStack output) {
        this(id, soul, input, output, null, 1);
    }

    public TransmutationRecipe(ResourceLocation id, Ingredient soul, Ingredient input, ItemStack output, @Nullable ResourceLocation outputTag, int outputTagCount) {
        this.id = id;
        this.soul = soul;
        this.input = input;
        this.output = output;
        this.outputTag = outputTag;
        this.outputTagCount = Math.max(1, outputTagCount);
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (level.isClientSide || container.getContainerSize() < 2) {
            return false;
        }

        return this.soul.test(container.getItem(0)) && this.input.test(container.getItem(1));
    }

    public boolean matches(ItemStack soulStack, ItemStack inputStack) {
        return this.soul.test(soulStack) && this.input.test(inputStack);
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return getPreviewOutput();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return getPreviewOutput();
    }

    public Ingredient getSoul() {
        return this.soul;
    }

    public Ingredient getInput() {
        return this.input;
    }

    public ItemStack getOutput() {
        return getPreviewOutput();
    }

    public ItemStack getFixedOutput() {
        return this.output.copy();
    }

    public boolean hasRandomTagOutput() {
        return this.outputTag != null;
    }

    @Nullable
    public ResourceLocation getOutputTag() {
        return this.outputTag;
    }

    public int getOutputTagCount() {
        return this.outputTagCount;
    }

    public boolean canCraftResult() {
        return hasRandomTagOutput() ? !getItemsFromOutputTag().isEmpty() : !this.output.isEmpty();
    }

    public ItemStack getTransmutationResult(Level level) {
        if (!hasRandomTagOutput()) {
            return this.output.copy();
        }

        List<Item> items = getItemsFromOutputTag();
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(items.get(level.random.nextInt(items.size())), this.outputTagCount);
    }

    public List<ItemStack> getJeiOutputs() {
        List<ItemStack> stacks = new ArrayList<>();

        if (!hasRandomTagOutput()) {
            if (!this.output.isEmpty()) {
                stacks.add(this.output.copy());
            }

            return stacks;
        }

        for (Item item : getItemsFromOutputTag()) {
            ItemStack stack = new ItemStack(item, this.outputTagCount);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        return stacks;
    }

    private ItemStack getPreviewOutput() {
        if (!hasRandomTagOutput()) {
            return this.output.copy();
        }

        List<Item> items = getItemsFromOutputTag();
        return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(0), this.outputTagCount);
    }

    private List<Item> getItemsFromOutputTag() {
        List<Item> items = new ArrayList<>();

        if (this.outputTag == null) {
            return items;
        }

        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, this.outputTag);

        try {
            for (ItemStack stack : Ingredient.of(tagKey).getItems()) {
                addValidItem(items, stack.getItem());
            }
        } catch (Exception ignored) {
        }

        try {
            if (ForgeRegistries.ITEMS.tags() != null) {
                ForgeRegistries.ITEMS.tags().getTag(tagKey).stream().forEach(item -> addValidItem(items, item));
            }
        } catch (Exception ignored) {
        }

        try {
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item.builtInRegistryHolder().is(tagKey)) {
                    addValidItem(items, item);
                }
            }
        } catch (Exception ignored) {
        }

        return items;
    }

    private void addValidItem(List<Item> items, Item item) {
        if (item != null && item != Items.AIR && !items.contains(item)) {
            items.add(item);
        }
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TRANSMUTATION.get();
    }

    @Override
    public RecipeType<?> getType() {
        return Type.INSTANCE;
    }

    public static class Type implements RecipeType<TransmutationRecipe> {
        public static final Type INSTANCE = new Type();
        public static final String ID = "transmutation";
    }

    public static class Serializer implements RecipeSerializer<TransmutationRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Raidborn.MOD_ID, "transmutation");

        @Override
        public TransmutationRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            Ingredient soul = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "soul"));
            Ingredient input = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "input"));
            ItemStack output = ItemStack.EMPTY;
            ResourceLocation outputTag = null;
            int outputTagCount = 1;

            if (json.has("output")) {
                output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "output"));
            } else if (json.has("output_tag")) {
                outputTag = ResourceLocation.parse(GsonHelper.getAsString(json, "output_tag"));
                outputTagCount = GsonHelper.getAsInt(json, "count", 1);
            } else {
                throw new IllegalArgumentException("Transmutation recipe needs either 'output' or 'output_tag': " + recipeId);
            }

            return new TransmutationRecipe(recipeId, soul, input, output, outputTag, outputTagCount);
        }

        @Nullable
        @Override
        public TransmutationRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            Ingredient soul = Ingredient.fromNetwork(buffer);
            Ingredient input = Ingredient.fromNetwork(buffer);

            boolean hasTagOutput = buffer.readBoolean();
            ItemStack output = ItemStack.EMPTY;
            ResourceLocation outputTag = null;
            int outputTagCount = 1;

            if (hasTagOutput) {
                outputTag = buffer.readResourceLocation();
                outputTagCount = buffer.readVarInt();
            } else {
                output = buffer.readItem();
            }

            return new TransmutationRecipe(recipeId, soul, input, output, outputTag, outputTagCount);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TransmutationRecipe recipe) {
            recipe.soul.toNetwork(buffer);
            recipe.input.toNetwork(buffer);
            buffer.writeBoolean(recipe.hasRandomTagOutput());

            if (recipe.hasRandomTagOutput()) {
                buffer.writeResourceLocation(recipe.outputTag);
                buffer.writeVarInt(recipe.outputTagCount);
            } else {
                buffer.writeItem(recipe.output);
            }
        }
    }
}
