package vectorwing.farmersdelight.integration.jei.category;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.tag.ModTags;
import vectorwing.farmersdelight.common.utility.ClientRenderUtils;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.integration.jei.FDRecipeTypes;
import vectorwing.farmersdelight.integration.jei.resource.DecompositionDummy;

public class DecompositionRecipeCategory implements IRecipeCategory<DecompositionDummy> {
    public static final Identifier UID = Identifier.fromNamespaceAndPath("farmersdelight", "decomposition");
    private static final int slotSize = 22;
    private final Component title = TextUtils.JEI("decomposition");
    private final IDrawable background;
    private final IDrawable slotIcon;
    private final IDrawable icon;
    private final ItemStack organicCompost;
    private final ItemStack richSoil;

    public DecompositionRecipeCategory(IGuiHelper helper) {
        Identifier backgroundImage = Identifier.fromNamespaceAndPath("farmersdelight", "textures/gui/jei/decomposition.png");
        this.background = helper.createDrawable(backgroundImage, 0, 0, 118, 80);
        this.organicCompost = new ItemStack(ModBlocks.ORGANIC_COMPOST.get());
        this.richSoil = new ItemStack(ModItems.RICH_SOIL.get());
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, this.richSoil);
        this.slotIcon = helper.createDrawable(backgroundImage, 119, 0, 22, 22);
    }

    public IRecipeType<DecompositionDummy> getRecipeType() {
        return FDRecipeTypes.DECOMPOSITION;
    }

    public Component getTitle() {
        return this.title;
    }

    public int getWidth() {
        return 118;
    }

    public int getHeight() {
        return 80;
    }

    public IDrawable getIcon() {
        return this.icon;
    }

    public void setRecipe(IRecipeLayoutBuilder builder, DecompositionDummy recipe, IFocusGroup focusGroup) {
        List<ItemStack> accelerators = new ArrayList<>();
        BuiltInRegistries.BLOCK.get(ModTags.Blocks.COMPOST_ACTIVATORS).ifPresent(s -> s.forEach(f -> accelerators.add(new ItemStack(f.value()))));
        builder.addSlot(RecipeIngredientRole.INPUT, 9, 26).add(this.organicCompost);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 93, 26).add(this.richSoil);
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 64, 54).addItemStacks(accelerators);
    }

    public void draw(DecompositionDummy recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.slotIcon.draw(guiGraphics, 63, 53);
    }

    public void getTooltip(ITooltipBuilder tooltip, DecompositionDummy recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (ClientRenderUtils.isCursorInsideBounds(40, 38, 11, 11, mouseX, mouseY)) {
            tooltip.add(translateKey(".light"));
        }

        if (ClientRenderUtils.isCursorInsideBounds(53, 38, 11, 11, mouseX, mouseY)) {
            tooltip.add(translateKey(".fluid"));
        }

        if (ClientRenderUtils.isCursorInsideBounds(67, 38, 11, 11, mouseX, mouseY)) {
            tooltip.add(translateKey(".accelerators"));
        }
    }

    private static MutableComponent translateKey(String suffix) {
        return TextUtils.JEI("decomposition" + suffix);
    }
}
