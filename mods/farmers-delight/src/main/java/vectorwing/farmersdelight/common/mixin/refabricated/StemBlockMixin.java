package vectorwing.farmersdelight.common.mixin.refabricated;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.block.RichSoilFarmlandBlock;
import vectorwing.farmersdelight.common.utility.SoilUtils;

@Mixin(StemBlock.class)
public class StemBlockMixin {
    @ModifyExpressionValue(
        method = "randomTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z")
    )
    private boolean fdrf$modifyRandomTickCallForStems(boolean original, @Local(ordinal = 1) BlockState below) {
        if (below.getBlock() instanceof RichSoilBlock) {
            return SoilUtils.isAbleToPlaceRichSoil((Block)(Object)this);
        } else {
            return below.getBlock() instanceof RichSoilFarmlandBlock ? SoilUtils.isAbleToPlaceRichSoilFarmland((Block)(Object)this) : original;
        }
    }
}
