package vectorwing.farmersdelight.common.block.entity;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface Basket extends Container {
    VoxelShape[] COLLECTION_AREA_SHAPES = new VoxelShape[]{
        Block.box(0.0, -16.0, 0.0, 16.0, 16.0, 16.0),
        Block.box(0.0, 0.0, 0.0, 16.0, 32.0, 16.0),
        Block.box(0.0, 0.0, -16.0, 16.0, 16.0, 16.0),
        Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 32.0),
        Block.box(-16.0, 0.0, 0.0, 16.0, 16.0, 16.0),
        Block.box(0.0, 0.0, 0.0, 32.0, 16.0, 16.0)
    };

    default VoxelShape getFacingCollectionArea(int facingIndex) {
        return COLLECTION_AREA_SHAPES[facingIndex];
    }

    double getLevelX();

    double getLevelY();

    double getLevelZ();

    void setCooldown(int var1);

    boolean isOnCooldown();

    boolean isOnCustomCooldown();

    void tryTransfer(BooleanSupplier var1);

    default boolean collectItems(Level level, int facingIndex) {
        for (ItemEntity itementity : this.getItemsToCollect(level, facingIndex)) {
            if (this.collectItem(itementity)) {
                return true;
            }
        }

        return false;
    }

    default List<ItemEntity> getItemsToCollect(Level level, int facingIndex) {
        return this.getFacingCollectionArea(facingIndex)
            .toAabbs()
            .stream()
            .flatMap(
                aabb -> level.getEntitiesOfClass(
                        ItemEntity.class, aabb.move(this.getLevelX() - 0.5, this.getLevelY() - 0.5, this.getLevelZ() - 0.5), EntitySelector.ENTITY_STILL_ALIVE
                    )
                    .stream()
            )
            .collect(Collectors.toList());
    }

    default boolean collectItem(ItemEntity itemEntity) {
        boolean flag = false;
        ItemStack entityItemStack = itemEntity.getItem().copy();
        ItemStack remainderStack = this.insert(entityItemStack);
        if (remainderStack.isEmpty()) {
            flag = true;
            itemEntity.discard();
        } else {
            itemEntity.setItem(remainderStack);
        }

        return flag;
    }

    default ItemStack insert(ItemStack stack) {
        int size = this.getContainerSize();

        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            stack = this.insert(slot, stack);
        }

        return stack;
    }

    default ItemStack insert(int slot, ItemStack stack) {
        ItemStack slotStack = this.getItem(slot);
        if (this.canPlaceItem(slot, stack)) {
            boolean inserted = false;
            if (slotStack.isEmpty()) {
                this.setItem(slot, stack);
                stack = ItemStack.EMPTY;
                inserted = true;
            } else if (canMergeItems(slotStack, stack)) {
                int insertCount = stack.getMaxStackSize() - slotStack.getCount();
                insertCount = Math.min(stack.getCount(), insertCount);
                stack.shrink(insertCount);
                slotStack.grow(insertCount);
                inserted = insertCount > 0;
            }

            if (inserted) {
                if (this.isEmpty() && !this.isOnCustomCooldown()) {
                    this.setCooldown(8);
                }

                this.setChanged();
            }
        }

        return stack;
    }

    static boolean canMergeItems(ItemStack stack1, ItemStack stack2) {
        return stack1.getCount() <= stack1.getMaxStackSize() && ItemStack.isSameItemSameComponents(stack1, stack2);
    }
}
