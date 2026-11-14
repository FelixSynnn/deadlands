package vectorwing.farmersdelight.integration.jei.category;

import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.ClientRenderUtils;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.integration.jei.FDRecipeTypes;

public class CookingRecipeCategory implements IRecipeCategory<RecipeHolder<CookingPotRecipe>> {
    protected final IDrawable heatIndicator;
    protected final IDrawable timeIcon;
    protected final IDrawable expIcon;
    protected final IDrawableAnimated arrow;
    private final Component title = TextUtils.JEI("cooking");
    private final IDrawable background;
    private final IDrawable icon;

    public CookingRecipeCategory(IGuiHelper helper) {
        Identifier widgetBackgroundImage = Identifier.fromNamespaceAndPath("farmersdelight", "textures/gui/jei/cooking_pot.png");
        Identifier interfaceImage = Identifier.fromNamespaceAndPath("farmersdelight", "textures/gui/cooking_pot.png");
        this.background = helper.createDrawable(widgetBackgroundImage, 0, 0, 116, 56);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.COOKING_POT.get()));
        this.heatIndicator = helper.createDrawable(interfaceImage, 176, 0, 17, 15);
        this.timeIcon = helper.createDrawable(interfaceImage, 176, 32, 8, 11);
        this.expIcon = helper.createDrawable(interfaceImage, 176, 43, 9, 9);
        this.arrow = helper.drawableBuilder(interfaceImage, 176, 15, 24, 17).buildAnimated(200, StartDirection.LEFT, false);
    }

    public IRecipeType<RecipeHolder<CookingPotRecipe>> getRecipeType() {
        return FDRecipeTypes.COOKING;
    }

    public Component getTitle() {
        return this.title;
    }

    public int getWidth() {
        return 116;
    }

    public int getHeight() {
        return 56;
    }

    public IDrawable getIcon() {
        return this.icon;
    }

    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CookingPotRecipe> holder, IFocusGroup focusGroup) {
        CookingPotRecipe recipe = holder.value();
        List<Ingredient> recipeIngredients = recipe.input();
        ItemStack resultStack = recipe.result().copy();
        ItemStack containerStack = recipe.container();
        int borderSlotSize = 18;

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                int inputIndex = row * 3 + column;
                if (inputIndex < recipeIngredients.size()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, column * borderSlotSize + 1, row * borderSlotSize + 1).add(recipeIngredients.get(inputIndex));
                }
            }
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 10).add(resultStack);
        if (containerStack != null) {
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 63, 39).add(containerStack.copy());
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 39).add(resultStack);
    }

    public void draw(RecipeHolder<CookingPotRecipe> holder, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.arrow.draw(guiGraphics, 60, 9);
        this.heatIndicator.draw(guiGraphics, 18, 39);
        this.timeIcon.draw(guiGraphics, 64, 2);
        if (holder.value().getExperience() > 0.0F) {
            this.expIcon.draw(guiGraphics, 63, 21);
        }
    }

    public void getTooltip(
        ITooltipBuilder tooltipBuilder, RecipeHolder<CookingPotRecipe> holder, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY
    ) {
        CookingPotRecipe recipe = holder.value();
        if (ClientRenderUtils.isCursorInsideBounds(61, 2, 22, 28, mouseX, mouseY)) {
            int cookTime = recipe.getCookTime();
            if (cookTime > 0) {
                int cookTimeSeconds = cookTime / 20;
                tooltipBuilder.add(Component.translatable("gui.jei.category.smelting.time.seconds", cookTimeSeconds));
            }

            float experience = recipe.getExperience();
            if (experience > 0.0F) {
                tooltipBuilder.add(Component.translatable("gui.jei.category.smelting.experience", experience));
            }
        }
    }
}
