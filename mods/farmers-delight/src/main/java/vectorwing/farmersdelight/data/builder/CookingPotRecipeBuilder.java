package vectorwing.farmersdelight.data.builder;

import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.AdvancementRequirements.Strategy;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.advancements.criterion.InventoryChangeTrigger.TriggerInstance;
import net.minecraft.advancements.criterion.ItemPredicate.Builder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CookingPotBookCategory;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

@MethodsReturnNonnullByDefault
public class CookingPotRecipeBuilder implements RecipeBuilder {
    private final HolderGetter<Item> items;
    private CookingPotBookCategory category;
    private final NonNullList<Ingredient> ingredients = NonNullList.create();
    private final Item result;
    private final ItemStack resultStack;
    private final int cookingTime;
    private final float experience;
    private final ItemStack container;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    private @Nullable String namespace;

    public CookingPotRecipeBuilder(HolderGetter<Item> items, ItemLike result, int count, int cookingTime, float experience, @Nullable ItemLike container) {
        this(items, new ItemStack(result.asItem(), count), cookingTime, experience, container);
    }

    public CookingPotRecipeBuilder(HolderGetter<Item> items, ItemStack result, int cookingTime, float experience, @Nullable ItemLike container) {
        this.items = items;
        this.result = result.getItem();
        this.resultStack = result;
        this.cookingTime = cookingTime;
        this.experience = experience;
        this.container = container != null ? new ItemStack(container.asItem()) : null;
        this.category = null;
    }

    public static CookingPotRecipeBuilder cookingPotRecipe(HolderGetter<Item> items, ItemLike mainResult, int count, int cookingTime, float experience) {
        return new CookingPotRecipeBuilder(items, mainResult, count, cookingTime, experience, null);
    }

    public static CookingPotRecipeBuilder cookingPotRecipe(
        HolderGetter<Item> items, ItemLike mainResult, int count, int cookingTime, float experience, ItemLike container
    ) {
        return new CookingPotRecipeBuilder(items, mainResult, count, cookingTime, experience, container);
    }

    public CookingPotRecipeBuilder addIngredient(TagKey<Item> tag) {
        return this.addIngredient(Ingredient.of(this.items.getOrThrow(tag)));
    }

    public CookingPotRecipeBuilder addIngredient(ItemLike itemIn) {
        return this.addIngredient(itemIn, 1);
    }

    public CookingPotRecipeBuilder addIngredient(ItemLike itemIn, int quantity) {
        for (int i = 0; i < quantity; i++) {
            this.addIngredient(Ingredient.of(itemIn));
        }

        return this;
    }

    public CookingPotRecipeBuilder addIngredient(Ingredient ingredientIn) {
        return this.addIngredient(ingredientIn, 1);
    }

    public CookingPotRecipeBuilder addIngredient(Ingredient ingredientIn, int quantity) {
        for (int i = 0; i < quantity; i++) {
            this.ingredients.add(ingredientIn);
        }

        return this;
    }

    @Override
    public RecipeBuilder group(@Nullable String p_176495_) {
        return this;
    }

    public CookingPotRecipeBuilder setRecipeBookCategory(CookingPotBookCategory category) {
        this.category = category;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result;
    }

    public CookingPotRecipeBuilder unlockedBy(String criterionName, Criterion<?> criterionTrigger) {
        this.criteria.put(criterionName, criterionTrigger);
        return this;
    }

    public CookingPotRecipeBuilder unlockedByItems(String criterionName, ItemLike... items) {
        return this.unlockedBy(criterionName, TriggerInstance.hasItems(items));
    }

    public CookingPotRecipeBuilder unlockedByAnyIngredient(ItemLike... itemCollection) {
        this.criteria.put("has_any_ingredient", TriggerInstance.hasItems(Builder.item().of(this.items, itemCollection).build()));
        return this;
    }

    public CookingPotRecipeBuilder setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public static Identifier getDefaultRecipeId(ItemLike itemLike) {
        return Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(itemLike.asItem()));
    }

    public void saveToFD(RecipeOutput output) {
        this.setNamespace("farmersdelight").save(output);
    }

    @Override
    public void save(RecipeOutput output) {
        Identifier defaultLocation = getDefaultRecipeId(this.result);
        this.save(
            output,
            Identifier.fromNamespaceAndPath(this.namespace != null ? this.namespace : defaultLocation.getNamespace(), defaultLocation.getPath())
                .withPrefix("cooking/")
        );
    }

    public void build(RecipeOutput outputIn, String save) {
        Identifier resourcelocation = BuiltInRegistries.ITEM.getKey(this.result);
        if (Identifier.parse(save).equals(resourcelocation)) {
            throw new IllegalStateException("Cooking Recipe " + save + " should remove its 'save' argument");
        }

        this.save(outputIn, Identifier.parse(save));
    }

    public void build(RecipeOutput output, Identifier id) {
        this.save(output, ResourceKey.create(Registries.RECIPE, id));
    }

    public void save(RecipeOutput output, Identifier id) {
        this.build(output, id);
    }

    @Override
    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        net.minecraft.advancements.Advancement.Builder advancementBuilder = output.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(resourceKey))
            .rewards(net.minecraft.advancements.AdvancementRewards.Builder.recipe(resourceKey))
            .requirements(Strategy.OR);
        this.criteria.forEach(advancementBuilder::addCriterion);
        CookingPotRecipe recipe = new CookingPotRecipe(
            "", this.category, this.ingredients, this.resultStack, Optional.ofNullable(this.container), this.experience, this.cookingTime
        );
        output.accept(resourceKey, recipe, advancementBuilder.build(resourceKey.identifier().withPrefix("recipes/")));
    }
}
