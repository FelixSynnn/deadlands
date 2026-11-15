package vectorwing.farmersdelight.integration.jei;

import com.google.common.collect.ImmutableList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import vectorwing.farmersdelight.client.gui.CookingPotScreen;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModMenuTypes;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.integration.jei.category.CookingRecipeCategory;
import vectorwing.farmersdelight.integration.jei.category.CuttingRecipeCategory;
import vectorwing.farmersdelight.integration.jei.category.DecompositionRecipeCategory;
import vectorwing.farmersdelight.integration.jei.resource.DecompositionDummy;
import vectorwing.farmersdelight.integration.jei.resource.DoughRecipeMaker;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("farmersdelight", "jei_plugin");

    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(new IRecipeCategory[]{new CookingRecipeCategory(registry.getJeiHelpers().getGuiHelper())});
        registry.addRecipeCategories(new IRecipeCategory[]{new CuttingRecipeCategory(registry.getJeiHelpers().getGuiHelper())});
        registry.addRecipeCategories(new IRecipeCategory[]{new DecompositionRecipeCategory(registry.getJeiHelpers().getGuiHelper())});
    }

    public void registerRecipes(IRecipeRegistration registration) {
        FDRecipes modRecipes = new FDRecipes();
        registration.addRecipes(FDRecipeTypes.COOKING, modRecipes.getCookingPotRecipes());
        registration.addRecipes(FDRecipeTypes.CUTTING, modRecipes.getCuttingBoardRecipes());
        registration.addRecipes(FDRecipeTypes.DECOMPOSITION, ImmutableList.of(new DecompositionDummy()));
        registration.addRecipes(RecipeTypes.CRAFTING, DoughRecipeMaker.createRecipe());
        registration.addIngredientInfo(new ItemStack(ModItems.WHEAT_DOUGH.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.dough")});
        registration.addIngredientInfo(new ItemStack(ModItems.STRAW.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.straw")});
        registration.addIngredientInfo(new ItemStack(ModItems.HAM.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.ham")});
        registration.addIngredientInfo(new ItemStack(ModItems.SMOKED_HAM.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.ham")});
        registration.addIngredientInfo(new ItemStack(ModItems.FLINT_KNIFE.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.knife")});
        registration.addIngredientInfo(new ItemStack(ModItems.COPPER_KNIFE.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.knife")});
        registration.addIngredientInfo(new ItemStack(ModItems.IRON_KNIFE.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.knife")});
        registration.addIngredientInfo(new ItemStack(ModItems.DIAMOND_KNIFE.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.knife")});
        registration.addIngredientInfo(new ItemStack(ModItems.NETHERITE_KNIFE.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.knife")});
        registration.addIngredientInfo(new ItemStack(ModItems.GOLDEN_KNIFE.get()), VanillaTypes.ITEM_STACK, new Component[]{TextUtils.JEI("info.knife")});
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_CABBAGES.get()), new ItemStack(ModItems.CABBAGE.get()), new ItemStack(ModItems.CABBAGE_LEAF.get())),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_cabbages")}
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_BEETROOTS.get()), new ItemStack(Items.BEETROOT)),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_beetroots")}
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_CARROTS.get()), new ItemStack(Items.CARROT)),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_carrots")}
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_ONIONS.get()), new ItemStack(ModItems.ONION.get())),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_onions")}
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_POTATOES.get()), new ItemStack(Items.POTATO)),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_potatoes")}
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_TOMATOES.get()), new ItemStack(ModItems.TOMATO.get())),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_tomatoes")}
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(ModItems.WILD_RICE.get()), new ItemStack(ModItems.RICE.get()), new ItemStack(ModItems.RICE_PANICLE.get())),
            VanillaTypes.ITEM_STACK,
            new Component[]{TextUtils.JEI("info.wild_rice")}
        );
    }

    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(FDRecipeTypes.COOKING, new ItemStack[]{new ItemStack(ModItems.COOKING_POT.get())});
        registration.addCraftingStation(FDRecipeTypes.CUTTING, new ItemStack[]{new ItemStack(ModItems.CUTTING_BOARD.get())});
        registration.addCraftingStation(RecipeTypes.CAMPFIRE_COOKING, new ItemStack[]{new ItemStack(ModItems.STOVE.get())});
        registration.addCraftingStation(RecipeTypes.CAMPFIRE_COOKING, new ItemStack[]{new ItemStack(ModItems.SKILLET.get())});
        registration.addCraftingStation(FDRecipeTypes.DECOMPOSITION, new ItemStack[]{new ItemStack(ModBlocks.ORGANIC_COMPOST.get())});
    }

    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(CookingPotScreen.class, 89, 25, 24, 17, new IRecipeType[]{FDRecipeTypes.COOKING});
    }

    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(CookingPotMenu.class, ModMenuTypes.COOKING_POT.get(), FDRecipeTypes.COOKING, 0, 6, 9, 36);
    }

    public Identifier getPluginUid() {
        return ID;
    }
}
