package vectorwing.farmersdelight.common.mixin.refabricated;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import vectorwing.farmersdelight.refabricated.duck.SkilletEntityRenderState;

@Mixin(HumanoidRenderState.class)
public class HumanoidRenderStateMixin implements SkilletEntityRenderState {
    @Unique
    private long fdrf$skilletFlipTimestamp = -1L;

    @Override
    public long fdrf$getSkilletFlipTimestamp() {
        return this.fdrf$skilletFlipTimestamp;
    }

    @Override
    public void fdrf$setSkilletFlipTimestamp(long value) {
        this.fdrf$skilletFlipTimestamp = value;
    }
}
