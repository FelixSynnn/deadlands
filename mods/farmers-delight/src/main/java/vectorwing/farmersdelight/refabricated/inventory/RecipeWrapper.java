package vectorwing.farmersdelight.refabricated.inventory;

import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public class RecipeWrapper implements RecipeInput {
    private final ItemHandler handler;
    private final StackedItemContents stackedContents;
    private final int ingredientAmount;

    public RecipeWrapper(ItemHandler handler) {
        this.handler = handler;
        this.stackedContents = new StackedItemContents();
        int ingredientAmount = 0;

        for (int value : handler.getInputSlotIndexes()) {
            ItemStack itemstack = handler.getStackInSlot(value);
            if (!itemstack.isEmpty()) {
                ingredientAmount++;
                this.stackedContents.accountStack(itemstack, 1);
            }
        }

        this.ingredientAmount = ingredientAmount;
    }

    public StackedItemContents stackedContents() {
        return this.stackedContents;
    }

    public int ingredientAmount() {
        return this.ingredientAmount;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.handler.getStackInSlot(slot);
    }

    @Override
    public int size() {
        return this.handler.getSlotCount();
    }
}
