package vectorwing.farmersdelight.integration.rrv;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.builtin.shapeless.ShapelessServerRecipe;
import cc.cassian.rrv.common.recipe.ServerRecipeManager;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.integration.rrv.cooking_pot.CookingPotClientRecipe;
import vectorwing.farmersdelight.integration.rrv.cooking_pot.CookingPotServerRecipe;
import vectorwing.farmersdelight.integration.rrv.cutting.CuttingClientRecipe;
import vectorwing.farmersdelight.integration.rrv.cutting.CuttingServerRecipe;
import vectorwing.farmersdelight.integration.rrv.decomposition.DecompositionClientRecipe;
import vectorwing.farmersdelight.integration.rrv.decomposition.DecompositionServerRecipe;

public class RRVPlugin implements ReliableRecipeViewerPlugin {
    public void onIntegrationInitialize() {
        ItemView.addServerRecipeProvider(
            recipeList -> {
                ServerRecipeManager.INSTANCE
                    .getRecipesForType(ModRecipeTypes.COOKING.get())
                    .forEach(
                        recipe -> recipeList.add(
                            new CookingPotServerRecipe(recipe.input(), recipe.result(), recipe.container(), recipe.getExperience(), recipe.getCookTime())
                        )
                    );
                ServerRecipeManager.INSTANCE
                    .getRecipesForType(ModRecipeTypes.CUTTING.get())
                    .forEach(
                        recipe -> recipeList.add(new CuttingServerRecipe(recipe.getInput(), recipe.getResults(), recipe.getTool(), recipe.getRollableResults()))
                    );
                recipeList.add(new DecompositionServerRecipe());
                recipeList.add(
                    new ShapelessServerRecipe(List.of(Ingredient.of(Items.WHEAT), Ingredient.of(Items.WATER_BUCKET)), new ItemStack(ModItems.WHEAT_DOUGH.get()))
                );
            }
        );
        ItemView.addClientRecipeWrapper(
            CookingPotServerRecipe.TYPE,
            modRecipe -> Collections.singletonList(
                new CookingPotClientRecipe(
                    null, modRecipe.getIngredients(), modRecipe.getResult(), modRecipe.getContainer(), modRecipe.getExperience(), modRecipe.getCookTime()
                )
            )
        );
        ItemView.addClientRecipeWrapper(
            CuttingServerRecipe.TYPE,
            modRecipe -> Collections.singletonList(
                new CuttingClientRecipe(null, modRecipe.getIngredient(), modRecipe.getResults(), modRecipe.getTool(), modRecipe.getRollableResults())
            )
        );
        ItemView.addClientRecipeWrapper(
            DecompositionServerRecipe.TYPE,
            modRecipe -> List.of(
                new DecompositionClientRecipe(ModItems.ORGANIC_COMPOST.get().getDefaultInstance(), ModItems.RICH_SOIL.get().getDefaultInstance())
            )
        );
    }
}
