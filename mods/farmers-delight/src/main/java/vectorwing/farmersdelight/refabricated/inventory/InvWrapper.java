package vectorwing.farmersdelight.refabricated.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public class InvWrapper extends FabricWrappedInventory {
    private final Container inv;

    public InvWrapper(Container inv) {
        super(inv.getContainerSize());
        this.inv = inv;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            return o instanceof InvWrapper wrapper ? this.getInv().equals(wrapper.getInv()) : false;
        }
    }

    public @NonNull Container getInv() {
        return this.inv;
    }

    @Override
    public int getSlotCount() {
        return this.inv.getContainerSize();
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.inv.getMaxStackSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.inv.getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = this.inv.getItem(slot);
        if (!stackInSlot.isEmpty()) {
            if (stackInSlot.getCount() < Math.min(stackInSlot.getMaxStackSize(), this.getSlotLimit(slot))
                && !ItemStack.isSameItemSameComponents(stack, stackInSlot)
                && this.inv.canPlaceItem(slot, stack)) {
                int min = Math.min(stack.getMaxStackSize(), this.getSlotLimit(slot)) - stackInSlot.getCount();
                if (stack.getCount() <= min) {
                    if (!simulate) {
                        ItemStack copy = stack.copy();
                        copy.grow(stackInSlot.getCount());
                        this.inv.setItem(slot, copy);
                        this.inv.setChanged();
                    }

                    return ItemStack.EMPTY;
                } else {
                    ItemStack copy = stack.copy();
                    if (!simulate) {
                        ItemStack split = copy.split(min);
                        split.grow(stackInSlot.getCount());
                        this.inv.setItem(slot, split);
                        this.inv.setChanged();
                        return stack;
                    } else {
                        copy.shrink(min);
                        return copy;
                    }
                }
            } else {
                return stack;
            }
        } else {
            if (!this.inv.canPlaceItem(slot, stack)) {
                return stack;
            }

            int min = Math.min(stack.getMaxStackSize(), this.getSlotLimit(slot));
            if (min < stack.getCount()) {
                ItemStack copy = stack.copy();
                if (!simulate) {
                    this.inv.setItem(slot, copy.split(min));
                    this.inv.setChanged();
                    return stack;
                } else {
                    copy.shrink(min);
                    return copy;
                }
            } else {
                if (!simulate) {
                    this.inv.setItem(slot, stack);
                    this.inv.setChanged();
                }

                return ItemStack.EMPTY;
            }
        }
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = this.inv.getItem(slot);
        if (stackInSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (simulate) {
            if (stackInSlot.getCount() < amount) {
                return stackInSlot.copy();
            }

            ItemStack copy = stackInSlot.copy();
            copy.setCount(amount);
            return copy;
        } else {
            int min = Math.min(stackInSlot.getCount(), amount);
            ItemStack decreased = this.inv.removeItem(slot, min);
            this.inv.setChanged();
            return decreased;
        }
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        this.inv.setItem(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.inv.canPlaceItem(slot, stack);
    }
}
