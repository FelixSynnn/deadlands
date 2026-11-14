package vectorwing.farmersdelight.integration.rrv.cutting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu.OptionalSlotRenderer;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu.SlotFillContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;

public class CuttingClientRecipe implements ReliableClientRecipe {
    private final LinkedHashMap<SlotContent, Float> rollableResults;
    private final SlotContent ingredient;
    private final List<SlotContent> results;
    private final SlotContent tool;
    private final Identifier id;

    public CuttingClientRecipe(Identifier identifier, Ingredient input, List<ItemStack> results, Ingredient tool, List<ChanceResult> rollableResults) {
        this.id = identifier;
        this.results = new ArrayList<>();
        this.rollableResults = new LinkedHashMap<>();
        results.forEach(ingredient -> this.results.add(SlotContent.of(ingredient)));
        rollableResults.forEach(ingredient -> this.rollableResults.put(SlotContent.of(ingredient.stack()), ingredient.chance()));
        this.ingredient = SlotContent.of(input);
        this.tool = SlotContent.of(tool);
    }

    public ReliableClientRecipeType getViewType() {
        return CuttingClientRecipeType.INSTANCE;
    }

    public Identifier getId() {
        return this.id;
    }

    public void bindSlots(SlotFillContext slotFillContext) {
        int i = 0;
        slotFillContext.bindSlot(i, this.ingredient);
        slotFillContext.bindSlot(++i, this.tool);
        i++;

        for (Entry<SlotContent, Float> result : this.rollableResults.entrySet()) {
            Float chance = result.getValue();
            if (chance.floatValue() != 1.0) {
                slotFillContext.bindOptionalSlot(i, result.getKey(), CuttingClientRecipe.CuttingSlotRenderer.CHANCE_OUTPUT);
                slotFillContext.addAdditionalStackModifier(
                    i,
                    (stack, tooltip) -> tooltip.add(
                        Component.literal("Production chance: %s%%".formatted(chance * 100.0F))
                            .setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.GOLD)))
                    )
                );
            } else {
                slotFillContext.bindOptionalSlot(i, result.getKey(), CuttingClientRecipe.CuttingSlotRenderer.GUARANTEED_OUTPUT);
            }

            i++;
        }

        if (i <= 2) {
            slotFillContext.bindOptionalSlot(i, SlotContent.of(ItemStack.EMPTY), OptionalSlotRenderer.DEFAULT);
            i++;
        }

        if (i <= 3) {
            slotFillContext.bindOptionalSlot(i, SlotContent.of(ItemStack.EMPTY), OptionalSlotRenderer.DEFAULT);
            i++;
        }

        if (i <= 4) {
            slotFillContext.bindOptionalSlot(i, SlotContent.of(ItemStack.EMPTY), OptionalSlotRenderer.DEFAULT);
            i++;
        }

        if (i <= 5) {
            slotFillContext.bindOptionalSlot(i, SlotContent.of(ItemStack.EMPTY), OptionalSlotRenderer.DEFAULT);
        }
    }

    public List<SlotContent> getIngredients() {
        return Collections.singletonList(this.ingredient);
    }

    public List<SlotContent> getResults() {
        return this.results;
    }

    public interface CuttingSlotRenderer {
        Identifier SLOT_TEXTURE = FarmersDelight.id("textures/gui/jei/cutting_board.png");
        OptionalSlotRenderer GUARANTEED_OUTPUT = (guiGraphics, mouseX, mouseY, partialTicks) -> guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, 0, 0, 0.0F, 58.0F, 18, 18, 256, 256
        );
        OptionalSlotRenderer CHANCE_OUTPUT = (guiGraphics, mouseX, mouseY, partialTicks) -> guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, 0, 0, 18.0F, 58.0F, 18, 18, 256, 256
        );

        void render(GuiGraphics var1, int var2, int var3, float var4);
    }
}
