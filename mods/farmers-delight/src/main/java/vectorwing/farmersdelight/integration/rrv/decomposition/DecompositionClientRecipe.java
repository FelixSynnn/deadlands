package vectorwing.farmersdelight.integration.rrv.decomposition;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe.RecipePosition;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu.SlotFillContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.ClientRenderUtils;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class DecompositionClientRecipe implements ReliableClientRecipe {
    private final SlotContent input;
    private final SlotContent output;
    private static final SlotContent activators = SlotContent.ofItemList(
        List.of(
            Items.RED_MUSHROOM,
            Items.BROWN_MUSHROOM,
            Items.MYCELIUM,
            Items.PODZOL,
            ModItems.RED_MUSHROOM_COLONY.get(),
            ModItems.BROWN_MUSHROOM_COLONY.get(),
            ModItems.RICH_SOIL.get(),
            ModItems.RICH_SOIL_FARMLAND.get()
        )
    );
    private static final MutableComponent LIGHT_TOOLTIP = createTooltip(".light");
    private static final MutableComponent FLUID_TOOLTIP = createTooltip(".fluid");
    private static final MutableComponent ACCELERATORS_TOOLTIP = createTooltip(".accelerators");

    public DecompositionClientRecipe(ItemStack input, ItemStack output) {
        this.input = SlotContent.of(input);
        this.output = SlotContent.of(output);
    }

    public Identifier getId() {
        return FarmersDelight.id("decomposition");
    }

    public ReliableClientRecipeType getViewType() {
        return DecompositionClientRecipeType.INSTANCE;
    }

    public void bindSlots(SlotFillContext slotFillContext) {
        slotFillContext.bindSlot(0, this.input);
        slotFillContext.bindSlot(1, this.output);
        slotFillContext.bindSlot(2, activators);
    }

    public void renderRecipe(RecipeViewScreen screen, RecipePosition recipePosition, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int iconY = 38;
        int iconX = 38;
        int x = recipePosition.left() + mouseX;
        int y = recipePosition.top() + mouseY;
        if (ClientRenderUtils.isCursorInsideBounds(iconX, iconY, 11, 11, mouseX, mouseY)) {
            guiGraphics.setTooltipForNextFrame(Minecraft.getInstance().font, LIGHT_TOOLTIP, x, y);
        } else if (ClientRenderUtils.isCursorInsideBounds(iconX + 12, iconY, 11, 11, mouseX, mouseY)) {
            guiGraphics.setTooltipForNextFrame(Minecraft.getInstance().font, FLUID_TOOLTIP, x, y);
        } else if (ClientRenderUtils.isCursorInsideBounds(iconX + 27, iconY, 11, 11, mouseX, mouseY)) {
            guiGraphics.setTooltipForNextFrame(Minecraft.getInstance().font, ACCELERATORS_TOOLTIP, x, y);
        }
    }

    private static MutableComponent createTooltip(@NonNull String suffix) {
        return TextUtils.JEI("decomposition" + suffix);
    }

    public List<SlotContent> getIngredients() {
        return List.of(this.input);
    }

    public List<SlotContent> getResults() {
        return List.of(this.output);
    }
}
