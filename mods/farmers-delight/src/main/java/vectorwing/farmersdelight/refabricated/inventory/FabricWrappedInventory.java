package vectorwing.farmersdelight.refabricated.inventory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import org.jspecify.annotations.NonNull;

public abstract class FabricWrappedInventory implements ItemHandler {
    private final List<ItemHandlerStackWrapper> fabricWrappers;

    public FabricWrappedInventory(int size) {
        this.fabricWrappers = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            this.fabricWrappers.add(new ItemHandlerStackWrapper(this, i));
        }
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return this.fabricWrappers.get(slot);
    }

    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        long amount = 0L;

        try {
            for (ItemHandlerStackWrapper wrapper : this.fabricWrappers) {
                amount += wrapper.insert(resource, maxAmount - amount, transaction);
                if (amount == maxAmount) {
                    return maxAmount;
                }
            }

            return amount;
        } catch (Exception ex) {
            CrashReport report = CrashReport.forThrowable(ex, "Inserting resources into slots");
            report.addCategory("Slotted insertion details")
                .setDetail("Slots", () -> Objects.toString(this.fabricWrappers, null))
                .setDetail("Resource", () -> Objects.toString(resource, null))
                .setDetail("Max amount", maxAmount)
                .setDetail("Transaction", transaction);
            throw new ReportedException(report);
        }
    }

    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        long amount = 0L;

        try {
            for (ItemHandlerStackWrapper wrapper : this.fabricWrappers) {
                amount += wrapper.extract(resource, maxAmount - amount, transaction);
                if (amount == maxAmount) {
                    return maxAmount;
                }
            }

            return amount;
        } catch (Exception ex) {
            CrashReport report = CrashReport.forThrowable(ex, "Inserting resources into slots");
            report.addCategory("Slotted insertion details")
                .setDetail("Slots", () -> Objects.toString(this.fabricWrappers, null))
                .setDetail("Resource", () -> Objects.toString(resource, null))
                .setDetail("Max amount", maxAmount)
                .setDetail("Transaction", transaction);
            throw new ReportedException(report);
        }
    }

    @Override
    public @NonNull Iterator<StorageView<ItemVariant>> iterator() {
        return this.getSlots().stream().map(storageViews -> (StorageView<ItemVariant>)storageViews).iterator();
    }
}
