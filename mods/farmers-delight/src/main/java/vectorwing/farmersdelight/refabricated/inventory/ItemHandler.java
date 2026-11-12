package vectorwing.farmersdelight.refabricated.inventory;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

public interface ItemHandler extends SlottedStorage<ItemVariant> {
    @Override
    int getSlotCount();

    int getSlotLimit(int var1);

    ItemStack getStackInSlot(int var1);

    ItemStack insertItem(int var1, ItemStack var2, boolean var3);

    ItemStack extractItem(int var1, int var2, boolean var3);

    void setStackInSlot(int var1, ItemStack var2);

    boolean isItemValid(int var1, ItemStack var2);

    default IntList getInputSlotIndexes() {
        return IntList.of();
    }

    @Deprecated(forRemoval = true, since = "3.1.0")
    default void commitModifiedStacks() {
    }

    @Deprecated(forRemoval = true, since = "3.1.0")
    default ItemStack removeItem(int slot, int amount) {
        return this.extractItem(slot, amount, false);
    }

    @Deprecated(forRemoval = true, since = "3.1.0")
    default long insertSlot(int slot, ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return this.getSlot(slot).insert(resource, maxAmount, transaction);
    }

    @Deprecated(forRemoval = true, since = "3.1.0")
    default long extractSlot(int slot, ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return this.getSlot(slot).extract(resource, maxAmount, transaction);
    }
}
