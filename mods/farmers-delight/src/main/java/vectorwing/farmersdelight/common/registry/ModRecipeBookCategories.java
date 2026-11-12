package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModRecipeBookCategories {
    public static final Supplier<RecipeBookCategory> COOKING_MEALS = RegUtils.regRecipeBookCategory("cooking_meals", RecipeBookCategory::new);
    public static final Supplier<RecipeBookCategory> COOKING_DRINKS = RegUtils.regRecipeBookCategory("cooking_drinks", RecipeBookCategory::new);
    public static final Supplier<RecipeBookCategory> COOKING_MISC = RegUtils.regRecipeBookCategory("cooking_misc", RecipeBookCategory::new);
    public static final Supplier<RecipeBookCategory> CUTTING = RegUtils.regRecipeBookCategory("cutting", RecipeBookCategory::new);

    public static void touch() {
    }
}
