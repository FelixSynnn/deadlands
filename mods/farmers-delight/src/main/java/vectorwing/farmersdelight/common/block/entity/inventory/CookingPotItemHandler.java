package vectorwing.farmersdelight.common.block.entity.inventory;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.refabricated.inventory.ItemHandler;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;

public class CookingPotItemHandler implements ItemHandler {
    private static final int SLOTS_INPUT = 6;
    private static final int SLOT_CONTAINER_INPUT = 7;
    private static final int SLOT_MEAL_OUTPUT = 8;
    private final ItemStackHandler itemHandler;
    private final Direction side;

    public CookingPotItemHandler(ItemStackHandler itemHandler, @Nullable Direction side) {
        this.itemHandler = itemHandler;
        this.side = side;
    }

    @Override
    public boolean isItemValid(int slot, @NonNull ItemStack stack) {
        return this.itemHandler.isItemValid(slot, stack);
    }

    @Override
    public int getSlotCount() {
        return this.itemHandler.getSlotCount();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return this.itemHandler.getSlot(slot);
    }

    @Override
    public @NonNull ItemStack getStackInSlot(int slot) {
        return this.itemHandler.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        this.itemHandler.setStackInSlot(slot, stack);
    }

    @Override
    public @NonNull ItemStack insertItem(int slot, @NonNull ItemStack stack, boolean simulate) {
        if (this.side != null && !this.side.equals(Direction.UP)) {
            return slot == 7 ? this.itemHandler.insertItem(slot, stack, simulate) : stack;
        } else {
            return slot < 6 ? this.itemHandler.insertItem(slot, stack, simulate) : stack;
        }
    }

    @Override
    public @NonNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (this.side != null && !this.side.equals(Direction.UP)) {
            return slot == 8 ? this.itemHandler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        } else {
            return slot < 6 ? this.itemHandler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.itemHandler.getSlotLimit(slot);
    }

    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long inserted = 0L;
        Iterator<SingleStackStorage> it = this.getInsertableSlotsFor(resource);

        while (it.hasNext()) {
            SingleStackStorage slot = it.next();
            inserted += slot.insert(resource, maxAmount - inserted, transaction);
            if (inserted >= maxAmount) {
                break;
            }
        }

        return inserted;
    }

    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long extracted = 0L;
        Iterator<SingleStackStorage> it = this.getSlotsContaining(resource);

        while (it.hasNext()) {
            SingleStackStorage slot = it.next();
            extracted += slot.extract(resource, maxAmount - extracted, transaction);
            if (extracted >= maxAmount) {
                break;
            }
        }

        return extracted;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return this.side != null && !this.side.equals(Direction.UP)
            ? Stream.of(this.itemHandler.getSlots().get(8)).map(storageView -> (StorageView<ItemVariant>)storageView).iterator()
            : this.itemHandler.getSlots().subList(0, 6).stream().map(storageView -> (StorageView<ItemVariant>)storageView).iterator();
    }

    private Iterator<SingleStackStorage> getInsertableSlotsFor(ItemVariant resource) {
        List<SingleSlotStorage<ItemVariant>> slots = this.side != null && !this.side.equals(Direction.UP)
            ? List.of(this.itemHandler.getSlots().get(7))
            : this.itemHandler.getSlots().subList(0, 6);
        return slots.stream()
            .filter(views -> views.isResourceBlank() || views.getResource().equals(resource))
            .map(storageView -> (SingleStackStorage)storageView)
            .iterator();
    }

    private Iterator<SingleStackStorage> getSlotsContaining(ItemVariant resource) {
        List<SingleSlotStorage<ItemVariant>> slots = this.side != null && !this.side.equals(Direction.UP)
            ? List.of(this.itemHandler.getSlots().get(8))
            : this.itemHandler.getSlots().subList(0, 6);
        return slots.stream().filter(views -> views.getResource().equals(resource)).map(storageView -> (SingleStackStorage)storageView).iterator();
    }
}
