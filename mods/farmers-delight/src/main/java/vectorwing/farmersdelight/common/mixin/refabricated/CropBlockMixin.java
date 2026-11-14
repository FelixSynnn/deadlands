package vectorwing.farmersdelight.common.mixin.refabricated;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.block.RichSoilFarmlandBlock;
import vectorwing.farmersdelight.common.utility.SoilUtils;

@Mixin(CropBlock.class)
public class CropBlockMixin {
    @ModifyVariable(method = "getGrowthSpeed", at = @At(value = "LOAD", ordinal = 1), ordinal = 1)
    private static float farmersdelightrefabricated$modifyGrowthSpeedForNonFarmland(float original, Block block, BlockGetter level, BlockPos pos) {
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.getBlock() instanceof RichSoilBlock && SoilUtils.isAbleToPlaceRichSoil(block) && original < 1.0E-5F) {
            return 1.0F;
        } else if (!(belowState.getBlock() instanceof RichSoilFarmlandBlock) || !SoilUtils.isAbleToPlaceRichSoilFarmland(block) || !(original < 1.0E-5F)) {
            return original;
        } else {
            return belowState.hasProperty(RichSoilFarmlandBlock.MOISTURE) && belowState.getValue(RichSoilFarmlandBlock.MOISTURE) > 0 ? 3.0F : 1.0F;
        }
    }
}
