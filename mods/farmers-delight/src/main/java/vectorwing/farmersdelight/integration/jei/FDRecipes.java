package vectorwing.farmersdelight.integration.jei;

import java.util.List;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

public class FDRecipes {
    private final SynchronizedRecipes synchronizedRecipes;

    public FDRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != null) {
            this.synchronizedRecipes = level.recipeAccess().getSynchronizedRecipes();
        } else {
            throw new NullPointerException("Minecraft level must not be null.");
        }
    }

    public List<RecipeHolder<CookingPotRecipe>> getCookingPotRecipes() {
        return List.copyOf(this.synchronizedRecipes.getAllOfType(ModRecipeTypes.COOKING.get()));
    }

    public List<RecipeHolder<CuttingBoardRecipe>> getCuttingBoardRecipes() {
        return List.copyOf(this.synchronizedRecipes.getAllOfType(ModRecipeTypes.CUTTING.get()));
    }
}
