package vectorwing.farmersdelight.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class CookingPotTooltip implements ClientTooltipComponent {
    private static final int ITEM_SIZE = 16;
    private static final int MARGIN = 4;
    private final int textSpacing = 9 + 1;
    private final ItemStack mealStack;

    public CookingPotTooltip(CookingPotTooltip.CookingPotTooltipComponent tooltip) {
        this.mealStack = tooltip.mealStack;
    }

    @Override
    public int getHeight(@NonNull Font font) {
        return this.mealStack.isEmpty() ? this.textSpacing : this.textSpacing + 16;
    }

    @Override
    public int getWidth(@NonNull Font font) {
        if (!this.mealStack.isEmpty()) {
            MutableComponent textServingsOf = this.mealStack.getCount() == 1
                ? TextUtils.tooltip("cooking_pot.single_serving")
                : TextUtils.tooltip("cooking_pot.many_servings", this.mealStack.getCount());
            return Math.max(font.width(textServingsOf), font.width(this.mealStack.getHoverName()) + 20);
        } else {
            return font.width(TextUtils.tooltip("cooking_pot.empty"));
        }
    }

    @Override
    public void renderImage(@NonNull Font font, int mouseX, int mouseY, int width, int height, @NonNull GuiGraphics gui) {
        if (!this.mealStack.isEmpty()) {
            gui.renderItem(this.mealStack, mouseX, mouseY + this.textSpacing, 0);
        }
    }

    @Override
    public void renderText(@NonNull GuiGraphics guiGraphics, @NonNull Font font, int x, int y) {
        Integer color = ChatFormatting.GRAY.getColor();
        int gray = color == null ? -1 : ARGB.opaque(color);
        if (!this.mealStack.isEmpty()) {
            MutableComponent textServingsOf = this.mealStack.getCount() == 1
                ? TextUtils.tooltip("cooking_pot.single_serving")
                : TextUtils.tooltip("cooking_pot.many_servings", this.mealStack.getCount());
            guiGraphics.drawWordWrap(font, textServingsOf, x, y, 96, gray);
            guiGraphics.drawWordWrap(font, this.mealStack.getHoverName(), x + 16 + 4, y + this.textSpacing + 4, 96, -1);
        } else {
            MutableComponent textEmpty = TextUtils.tooltip("cooking_pot.empty");
            guiGraphics.drawWordWrap(font, textEmpty, x, y, 96, gray);
        }
    }

    public record CookingPotTooltipComponent(ItemStack mealStack) implements TooltipComponent {
    }
}
