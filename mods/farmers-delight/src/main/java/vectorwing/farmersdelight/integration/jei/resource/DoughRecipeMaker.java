package vectorwing.farmersdelight.integration.jei.resource;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.registry.ModItems;

public class DoughRecipeMaker {
    public static List<RecipeHolder<CraftingRecipe>> createRecipe() {
        NonNullList<Ingredient> inputs = NonNullList.of(Ingredient.of(Items.WHEAT), Ingredient.of(Items.WHEAT), Ingredient.of(Items.WATER_BUCKET));
        ItemStack output = new ItemStack(ModItems.WHEAT_DOUGH.get());
        String path = "farmersdelight.dough";
        Identifier id = FarmersDelight.id("wheat_dough_from_water");
        return List.of(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), new ShapelessRecipe(path, CraftingBookCategory.MISC, output, inputs)));
    }
}
