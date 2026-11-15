package vectorwing.farmersdelight.common.mixin.refabricated;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.block.RichSoilFarmlandBlock;
import vectorwing.farmersdelight.common.utility.SoilUtils;

@Mixin(VegetationBlock.class)
public class VegetationBlockMixin {
    @ModifyReturnValue(method = "canSurvive", at = @At("RETURN"))
    private boolean farmersdelightrefabricated$allowPlantsOnBushes(boolean original, BlockState state, LevelReader level, BlockPos pos) {
        if (state.getBlock() != (Object)this) {
            return original;
        } else if (level.getBlockState(pos.below()).getBlock() instanceof RichSoilBlock) {
            return SoilUtils.isAbleToPlaceRichSoil((Block)(Object)this);
        } else {
            return level.getBlockState(pos.below()).getBlock() instanceof RichSoilFarmlandBlock
                ? SoilUtils.isAbleToPlaceRichSoilFarmland((Block)(Object)this)
                : original;
        }
    }
}
