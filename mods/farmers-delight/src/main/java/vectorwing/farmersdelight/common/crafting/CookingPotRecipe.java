package vectorwing.farmersdelight.common.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.client.recipe.CookingPotRecipeDisplay;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeBookCategories;
import vectorwing.farmersdelight.common.registry.ModRecipeSerializers;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.refabricated.inventory.RecipeWrapper;

public class CookingPotRecipe implements Recipe<RecipeWrapper> {
    public static final int INPUT_SLOTS = 6;
    private final String group;
    private final CookingPotBookCategory category;
    private final List<Ingredient> inputItems;
    private final ItemStack result;
    private final @Nullable ItemStack container;
    private final float experience;
    private final int cookTime;
    private PlacementInfo placementInfo;

    public CookingPotRecipe(
        String group,
        @Nullable CookingPotBookCategory tab,
        List<Ingredient> inputItems,
        ItemStack output,
        Optional<ItemStack> container,
        float experience,
        int cookTime
    ) {
        this.group = group;
        this.category = tab;
        this.inputItems = inputItems;
        this.result = output;
        this.container = container.orElse(null);
        this.experience = experience;
        this.cookTime = cookTime;
    }

    public List<Ingredient> input() {
        return this.inputItems;
    }

    public ItemStack result() {
        return this.result;
    }

    public @Nullable CookingPotBookCategory category() {
        return this.category;
    }

    public ItemStack container() {
        if (this.container == null) {
            ItemStack template = this.result.getRecipeRemainder();
            if (template != null) {
                return template;
            }

            Item item = CookingPotBlockEntity.INGREDIENT_REMAINDER_OVERRIDES.get(this.result.getItem());
            if (item != null) {
                return new ItemStack(item);
            }
        }

        return this.container;
    }

    public Optional<ItemStack> containerOverride() {
        return Optional.ofNullable(this.container);
    }

    public ItemStack assemble(RecipeWrapper inv, Provider provider) {
        return this.result.copy();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeWrapper>> getSerializer() {
        return ModRecipeSerializers.COOKING.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeWrapper>> getType() {
        return ModRecipeTypes.COOKING.get();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
            new CookingPotRecipeDisplay(
                this.input().stream().map(Ingredient::display).toList(),
                this.container == null ? Optional.empty() : Optional.of(new ItemStackSlotDisplay(this.container)),
                new ItemStackSlotDisplay(this.result()),
                new ItemSlotDisplay(ModItems.COOKING_POT.get()),
                this.cookTime,
                this.experience
            )
        );
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(this.inputItems);
        }

        return this.placementInfo;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return switch (this.category()) {
            case MEALS -> (RecipeBookCategory)ModRecipeBookCategories.COOKING_MEALS.get();
            case DRINKS -> (RecipeBookCategory)ModRecipeBookCategories.COOKING_DRINKS.get();
            case MISC -> (RecipeBookCategory)ModRecipeBookCategories.COOKING_MISC.get();
        };
    }

    public float getExperience() {
        return this.experience;
    }

    public int getCookTime() {
        return this.cookTime;
    }

    public boolean matches(RecipeWrapper inv, Level level) {
        return inv.ingredientAmount() == this.inputItems.size() && inv.stackedContents().canCraft(this, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o != null && this.getClass() == o.getClass()) {
            CookingPotRecipe that = (CookingPotRecipe)o;
            if (Float.compare(that.getExperience(), this.getExperience()) != 0) {
                return false;
            } else if (this.getCookTime() != that.getCookTime()) {
                return false;
            } else if (!this.group().equals(that.group())) {
                return false;
            } else if (this.category != that.category) {
                return false;
            } else if (!this.inputItems.equals(that.inputItems)) {
                return false;
            } else {
                return !this.result.equals(that.result) ? false : this.container.equals(that.container);
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = this.group().hashCode();
        result = 31 * result + (this.category() != null ? this.category().hashCode() : 0);
        result = 31 * result + this.inputItems.hashCode();
        result = 31 * result + this.result.hashCode();
        result = 31 * result + Objects.requireNonNullElse(this.container, ItemStack.EMPTY).hashCode();
        result = 31 * result + (this.getExperience() != 0.0F ? Float.floatToIntBits(this.getExperience()) : 0);
        return 31 * result + this.getCookTime();
    }

    public static class Serializer {
        private static final MapCodec<CookingPotRecipe> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(CookingPotRecipe::group),
                    CookingPotBookCategory.CODEC.optionalFieldOf("recipe_book_tab", CookingPotBookCategory.MISC).forGetter(CookingPotRecipe::category),
                    Ingredient.CODEC.listOf(1, 6).fieldOf("ingredients").forGetter(CookingPotRecipe::input),
                    ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
                    ItemStack.CODEC.optionalFieldOf("container").forGetter(CookingPotRecipe::containerOverride),
                    Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(CookingPotRecipe::getExperience),
                    Codec.INT.optionalFieldOf("cookingtime", 200).forGetter(CookingPotRecipe::getCookTime)
                )
                .apply(inst, CookingPotRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, CookingPotRecipe> STREAM_CODEC = StreamCodec.of(
            CookingPotRecipe.Serializer::toNetwork, CookingPotRecipe.Serializer::fromNetwork
        );

        public static MapCodec<CookingPotRecipe> codec() {
            return CODEC;
        }

        public static StreamCodec<RegistryFriendlyByteBuf, CookingPotRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static CookingPotRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CookingPotBookCategory tab = CookingPotBookCategory.STREAM_CODEC.decode(buffer);
            List<Ingredient> inputItems = Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
            ItemStack output = ItemStack.STREAM_CODEC.decode(buffer);
            Optional<ItemStack> container = ByteBufCodecs.optional(ItemStack.STREAM_CODEC).decode(buffer);
            float experience = buffer.readFloat();
            int cookTime = buffer.readVarInt();
            return new CookingPotRecipe(group, tab, inputItems, output, container, experience, cookTime);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, CookingPotRecipe recipe) {
            buffer.writeUtf(recipe.group);
            CookingPotBookCategory.STREAM_CODEC.encode(buffer, recipe.category);
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, recipe.inputItems);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            ByteBufCodecs.optional(ItemStack.STREAM_CODEC).encode(buffer, Optional.ofNullable(recipe.container));
            buffer.writeFloat(recipe.experience);
            buffer.writeVarInt(recipe.cookTime);
        }
    }
}
