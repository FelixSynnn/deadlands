package vectorwing.farmersdelight.refabricated.inventory;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.minecraft.world.item.ItemStack;

public class ItemHandlerStackWrapper extends SingleStackStorage {
    private final ItemHandler handler;
    private final int slot;

    public ItemHandlerStackWrapper(ItemHandler handler, int slot) {
        this.handler = handler;
        this.slot = slot;
    }

    @Override
    protected ItemStack getStack() {
        return this.handler.getStackInSlot(this.slot);
    }

    @Override
    protected void setStack(ItemStack stack) {
        this.handler.setStackInSlot(this.slot, stack);
    }

    @Override
    protected int getCapacity(ItemVariant itemVariant) {
        return itemVariant.isBlank()
            ? this.handler.getSlotLimit(this.slot)
            : Math.min(this.handler.getSlotLimit(this.slot), itemVariant.toStack().getMaxStackSize());
    }
}
