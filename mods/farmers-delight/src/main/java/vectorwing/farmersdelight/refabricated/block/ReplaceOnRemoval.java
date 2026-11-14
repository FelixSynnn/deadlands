package vectorwing.farmersdelight.refabricated.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public interface ReplaceOnRemoval {
    boolean onRemove(BlockState var1, BlockState var2, LevelAccessor var3, BlockPos var4, int var5);
}
