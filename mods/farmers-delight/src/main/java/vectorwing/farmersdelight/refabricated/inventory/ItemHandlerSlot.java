package vectorwing.farmersdelight.refabricated.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemHandlerSlot extends Slot {
    private static final Container EMPTY_INVENTORY = new SimpleContainer(0);
    private final ItemHandler itemHandler;

    public ItemHandlerSlot(ItemHandler inventoryIn, int index, int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, index, xPosition, yPosition);
        this.itemHandler = inventoryIn;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && this.itemHandler.isItemValid(this.getContainerSlot(), stack);
    }

    @Override
    public ItemStack getItem() {
        return this.itemHandler.getStackInSlot(this.getContainerSlot());
    }

    @Override
    public void set(ItemStack stack) {
        this.itemHandler.setStackInSlot(this.getContainerSlot(), stack);
        this.setChanged();
    }

    @Override
    public ItemStack remove(int amount) {
        ItemStack stack = this.itemHandler.extractItem(this.getContainerSlot(), amount, false);
        this.setChanged();
        return stack;
    }

    @Override
    public int getMaxStackSize() {
        return this.itemHandler.getSlotLimit(this.getContainerSlot());
    }

    public ItemHandler getItemHandler() {
        return this.itemHandler;
    }
}
