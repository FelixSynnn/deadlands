package vectorwing.farmersdelight.integration.rrv.cutting;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu.SlotDefinition;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class CuttingClientRecipeType implements ReliableClientRecipeType {
    public static final CuttingClientRecipeType INSTANCE = new CuttingClientRecipeType();

    public Component getDisplayName() {
        return TextUtils.JEI("cutting");
    }

    public Identifier getId() {
        return FarmersDelight.id("cutting");
    }

    public ItemStack getIcon() {
        return new ItemStack(ModItems.CUTTING_BOARD.get());
    }

    public int getDisplayWidth() {
        return 118;
    }

    public int getDisplayHeight() {
        return 58;
    }

    public Identifier getGuiTexture() {
        return FarmersDelight.id("textures/gui/eiv/cutting_board.png");
    }

    public int getSlotCount() {
        return 6;
    }

    public void placeSlots(SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 16, 28);
        slotDefinition.addItemSlot(1, 16, 8);
        slotDefinition.addItemSlot(2, 75, 20);
        slotDefinition.addItemSlot(3, 93, 20);
        slotDefinition.addItemSlot(4, 75, 38);
        slotDefinition.addItemSlot(5, 93, 38);
    }

    public List<ItemStack> getCraftReferences() {
        return List.of(new ItemStack(ModItems.CUTTING_BOARD.get()));
    }
}
