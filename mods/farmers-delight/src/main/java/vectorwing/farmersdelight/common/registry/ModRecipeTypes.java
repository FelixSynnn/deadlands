package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModRecipeTypes {
    public static final Supplier<RecipeType<CookingPotRecipe>> COOKING = RegUtils.regRecipe("cooking", () -> registerRecipeType("cooking"));
    public static final Supplier<RecipeType<CuttingBoardRecipe>> CUTTING = RegUtils.regRecipe("cutting", () -> registerRecipeType("cutting"));

    public static <T extends Recipe<?>> RecipeType<T> registerRecipeType(String identifier) {
        return new RecipeType<T>() {
            @Override
            public String toString() {
                return "farmersdelight:" + identifier;
            }
        };
    }

    public static void touch() {
    }
}
