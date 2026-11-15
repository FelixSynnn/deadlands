package vectorwing.farmersdelight.integration.rei;

import java.util.List;
import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import me.shedaniel.rei.api.common.registry.display.ServerDisplayRegistry;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultShapelessDisplay;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.integration.rei.display.CookingPotDisplay;
import vectorwing.farmersdelight.integration.rei.display.CuttingDisplay;
import vectorwing.farmersdelight.integration.rei.display.DecompositionDisplay;

public class CommonREIPlugin implements REICommonPlugin {
    public void registerDisplaySerializer(DisplaySerializerRegistry registry) {
        registry.register(FarmersDelight.id("default/cooking"), CookingPotDisplay.SERIALIZER);
        registry.register(FarmersDelight.id("default/cutting"), CuttingDisplay.SERIALIZER);
        registry.register(FarmersDelight.id("default/decomposition"), DecompositionDisplay.SERIALIZER);
    }

    public void registerDisplays(ServerDisplayRegistry registry) {
        registry.beginRecipeFiller(CookingPotRecipe.class).filterType(ModRecipeTypes.COOKING.get()).fill(CookingPotDisplay::new);
        registry.beginRecipeFiller(CuttingBoardRecipe.class).filterType(ModRecipeTypes.CUTTING.get()).fill(CuttingDisplay::new);
        registry.add(new DecompositionDisplay());
        registry.add(
            new DefaultShapelessDisplay(
                new RecipeHolder<>(
                    ResourceKey.create(Registries.RECIPE, FarmersDelight.id("wheat_dough_from_water")),
                    new ShapelessRecipe(
                        "farmersdelight.dough",
                        CraftingBookCategory.MISC,
                        new ItemStack(ModItems.WHEAT_DOUGH.get()),
                        List.of(Ingredient.of(Items.WHEAT), Ingredient.of(Items.WATER_BUCKET))
                    )
                )
            )
        );
    }
}
