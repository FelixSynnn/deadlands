package vectorwing.farmersdelight.integration.rei.client.categories;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.integration.rei.REICategoryIdentifiers;
import vectorwing.farmersdelight.integration.rei.display.CuttingDisplay;

public class CuttingCategory implements DisplayCategory<CuttingDisplay> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("farmersdelight", "textures/gui/jei/cutting_board.png");
    public static final int OUTPUT_GRID_X = 76;
    public static final int OUTPUT_GRID_Y = 10;

    public CategoryIdentifier<? extends CuttingDisplay> getCategoryIdentifier() {
        return REICategoryIdentifiers.CUTTING;
    }

    public Component getTitle() {
        return Component.translatable("jei.farmersdelight.cutting");
    }

    public Renderer getIcon() {
        return EntryStacks.of(ModBlocks.CUTTING_BOARD.get());
    }

    public int getDisplayHeight() {
        return 89;
    }

    public int getDisplayWidth(CuttingDisplay display) {
        return 155;
    }

    public List<Widget> setupDisplay(CuttingDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        Point startPoint = new Point(bounds.getCenterX() - 58, bounds.getCenterY() - 28);
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createTexturedWidget(BACKGROUND, startPoint.x, startPoint.y, 0.0F, 0.0F, 117, 57));
        widgets.add(Widgets.createSlot(new Point(startPoint.x + 16, startPoint.y + 8)).entries(display.getTool()).disableBackground());
        widgets.add(
            Widgets.createSlot(new Point(startPoint.x + 16, startPoint.y + 27)).entries((Collection)display.getInputEntries().get(0)).disableBackground()
        );
        int size = display.getRollableOutputs().size();
        int centerX = size > 1 ? 1 : 10;
        int centerY = size > 2 ? 1 : 10;

        for (int i = 0; i < size; i++) {
            int xOffset = centerX + (i % 2 == 0 ? 0 : 19);
            int yOffset = centerY + i / 2 * 19;
            Pair<EntryIngredient, Float> output = display.getRollableOutputs().get(i);
            EntryIngredient ingredient = output.getFirst();
            if (!ingredient.isEmpty()) {
                float chance = output.getSecond();
                if (chance < 1.0F) {
                    ingredient = ingredient.map(
                        stack -> stack.copy()
                            .tooltip(new Component[]{TextUtils.JEI("chance", chance < 0.01 ? "<1" : (int)(chance * 100.0F)).withStyle(ChatFormatting.GOLD)})
                    );
                }
            }

            widgets.add(
                Widgets.createTexturedWidget(
                    BACKGROUND, startPoint.x + 76 + xOffset - 1, startPoint.y + 10 + yOffset - 1, output.getSecond() < 1.0F ? 18.0F : 0.0F, 58.0F, 18, 18
                )
            );
            widgets.add(Widgets.createSlot(new Point(startPoint.x + 76 + xOffset, startPoint.y + 10 + yOffset)).entries(ingredient).disableBackground());
        }

        return widgets;
    }
}
