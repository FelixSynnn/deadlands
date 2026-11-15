package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.crafting.display.RecipeDisplay.Type;
import vectorwing.farmersdelight.client.recipe.CookingPotRecipeDisplay;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModRecipeDisplays {
    public static final Supplier<Type<CookingPotRecipeDisplay>> COOKING = RegUtils.regRecipeDisplay("cooking", () -> CookingPotRecipeDisplay.TYPE);

    public static void touch() {
    }
}
