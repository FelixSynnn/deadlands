package vectorwing.farmersdelight.common.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.state.CanvasSign;
import vectorwing.farmersdelight.common.registry.ModBlockEntityTypes;

public class HangingCanvasSignBlockEntity extends HangingSignBlockEntity {
    public HangingCanvasSignBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        if (state.getBlock() instanceof CanvasSign canvasSign && canvasSign.isDarkBackground()) {
            this.frontText = this.createDefaultSignText().setColor(DyeColor.WHITE);
            this.backText = this.createDefaultSignText().setColor(DyeColor.WHITE);
        }
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntityTypes.HANGING_CANVAS_SIGN.get();
    }

    @Override
    public boolean isValidBlockState(BlockState blockState) {
        return this.getType().isValid(blockState);
    }

    @Override
    public int getTextLineHeight() {
        return 9;
    }

    @Override
    public int getMaxTextLineWidth() {
        return 60;
    }
}
