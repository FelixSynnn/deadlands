package vectorwing.farmersdelight.common.block.entity.dispenser;

import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.CuttingBoardBlock;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

@MethodsReturnNonnullByDefault
public class CuttingBoardDispenseBehavior extends OptionalDispenseItemBehavior {
    private static final HashMap<Item, DispenseItemBehavior> DISPENSE_ITEM_BEHAVIOR_HASH_MAP = new HashMap<>();
    public static final CuttingBoardDispenseBehavior INSTANCE = new CuttingBoardDispenseBehavior();

    public static void registerBehaviour(Item item, CuttingBoardDispenseBehavior behavior) {
        DISPENSE_ITEM_BEHAVIOR_HASH_MAP.put(item, DispenserBlock.DISPENSER_REGISTRY.get(item));
        DispenserBlock.registerBehavior(item, behavior);
    }

    @Override
    public final ItemStack dispense(BlockSource source, ItemStack stack) {
        if (this.tryDispenseStackOnCuttingBoard(source, stack)) {
            this.playSound(source);
            this.playAnimation(source, source.state().getValue(DispenserBlock.FACING));
            return stack;
        } else {
            return DISPENSE_ITEM_BEHAVIOR_HASH_MAP.get(stack.getItem()).dispense(source, stack);
        }
    }

    public boolean tryDispenseStackOnCuttingBoard(BlockSource source, ItemStack stack) {
        this.setSuccess(false);
        Level level = source.level();
        BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CuttingBoardBlock && level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity cuttingBoard) {
            if (!cuttingBoard.isEmpty() && cuttingBoard.processStoredItemUsingTool(stack, null)) {
                this.setSuccess(true);
            }

            return true;
        } else {
            return false;
        }
    }
}
