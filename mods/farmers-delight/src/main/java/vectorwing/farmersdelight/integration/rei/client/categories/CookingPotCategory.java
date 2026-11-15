package vectorwing.farmersdelight.integration.rei.client.categories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.DisplayRenderer;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.integration.rei.REICategoryIdentifiers;
import vectorwing.farmersdelight.integration.rei.display.CookingPotDisplay;

public class CookingPotCategory implements DisplayCategory<CookingPotDisplay> {
    private static final Component TITLE = Component.translatable("jei.farmersdelight.cooking");
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("farmersdelight", "textures/gui/cooking_pot.png");

    public CategoryIdentifier<CookingPotDisplay> getCategoryIdentifier() {
        return REICategoryIdentifiers.COOKING;
    }

    public Component getTitle() {
        return TITLE;
    }

    public Renderer getIcon() {
        return EntryStacks.of(ModBlocks.COOKING_POT.get());
    }

    public int getDisplayHeight() {
        return 88;
    }

    public int getDisplayWidth(CookingPotDisplay display) {
        return 154;
    }

    public DisplayRenderer getDisplayRenderer(CookingPotDisplay display) {
        return new DisplayRenderer() {
            public void render(GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
                graphics.drawString(Minecraft.getInstance().font, CookingPotCategory.TITLE.getVisualOrderText(), bounds.x + 5, bounds.y + 6, -1, false);
            }

            public int getHeight() {
                return 10 + 9;
            }
        };
    }

    public List<Widget> setupDisplay(CookingPotDisplay display, Rectangle bounds) {
        List<EntryIngredient> recipeIngredients = display.getInputEntries();
        List<EntryIngredient> resultStack = display.getOutputEntries();
        EntryIngredient containerStack = display.getOutputContainer();
        Point startPoint = new Point(bounds.getCenterX() - 58, bounds.getCenterY() - 28);
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createTexturedWidget(BACKGROUND, startPoint.x, startPoint.y, 29.0F, 16.0F, 116, 56));
        int borderSlotSize = 18;

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                int inputIndex = row * 3 + column;
                if (inputIndex < recipeIngredients.size()) {
                    widgets.add(
                        Widgets.createSlot(new Point(startPoint.x + column * borderSlotSize + 1, startPoint.y + row * borderSlotSize + 1))
                            .entries((Collection)display.getInputEntries().get(inputIndex))
                            .disableBackground()
                    );
                }
            }
        }

        widgets.add(Widgets.createSlot(new Point(startPoint.x + 95, startPoint.y + 10)).entries((Collection)resultStack.get(0)).disableBackground());
        if (!containerStack.isEmpty()) {
            Slot containerSlot = Widgets.createSlot(new Point(startPoint.x + 63, startPoint.y + 39));
            containerSlot.entries(containerStack);
            widgets.add(containerSlot);
        }

        widgets.add(Widgets.createSlot(new Point(startPoint.x + 95, startPoint.y + 39)).entries((Collection)resultStack.get(0)));
        int startTime = Minecraft.getInstance().player.tickCount;
        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
            int ticksPassed = (Minecraft.getInstance().player.tickCount - startTime) % 200;
            int arrowAnimationWidth = Math.floorDiv(ticksPassed * 25, 200);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, startPoint.x + 60, startPoint.y + 9, 176.0F, 15.0F, arrowAnimationWidth, 17, 256, 256, 0);
        }));
        widgets.add(Widgets.createTexturedWidget(BACKGROUND, startPoint.x + 18, startPoint.y + 39, 176.0F, 0.0F, 17, 15));
        widgets.add(Widgets.createTexturedWidget(BACKGROUND, startPoint.x + 64, startPoint.y + 2, 176.0F, 32.0F, 8, 11));
        widgets.add(Widgets.createTexturedWidget(BACKGROUND, startPoint.x + 63, startPoint.y + 21, 176.0F, 43.0F, 9, 9));
        widgets.add(Widgets.createTooltip(() -> new Rectangle(startPoint.x + 61, startPoint.y + 2, 22, 28), this.getTooltipStrings(display)));
        return widgets;
    }

    public List<Component> getTooltipStrings(CookingPotDisplay display) {
        List<Component> tooltipStrings = new ArrayList<>();
        int cookTime = display.getCookTime();
        if (cookTime > 0) {
            int cookTimeSeconds = cookTime / 20;
            tooltipStrings.add(Component.translatable("category.rei.campfire.time", cookTimeSeconds));
        }

        float experience = display.getExperience();
        if (experience > 0.0F) {
            tooltipStrings.add(Component.translatable("category.rei.cooking.xp", experience));
        }

        return tooltipStrings;
    }
}
