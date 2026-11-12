package team.creative.enhancedvisuals.common.handler;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeSaturation;

public class SaturationHandler extends VisualHandler {
    @CreativeConfig
    public double fadeFactor = 5.0E-4;
    @CreativeConfig
    public DecimalCurve saturation = new DecimalCurve(2.0, 0.0, 8.0, 1.0);
    @CreativeConfig
    public VisualType desaturate = new VisualTypeSaturation("desaturate");
    public Visual saturationVisual;

    @Override
    public void tick(@Nullable Player player) {
        if (this.saturationVisual == null) {
            this.saturationVisual = new Visual(this.desaturate, this, 0);
            this.saturationVisual.setOpacityInternal(1.0F);
            this.add(this.saturationVisual);
        }

        double aimedSaturation = this.saturation.maxValue;
        if (player != null && player.isAlive()) {
            aimedSaturation = this.saturation.valueAt(player.getFoodData().getFoodLevel());
            if (this.saturationVisual.getOpacityInternal() < aimedSaturation) {
                this.saturationVisual.setOpacityInternal((float)Math.min(this.saturationVisual.getOpacityInternal() + this.fadeFactor, aimedSaturation));
            } else if (this.saturationVisual.getOpacityInternal() > aimedSaturation) {
                this.saturationVisual.setOpacityInternal((float)Math.max(this.saturationVisual.getOpacityInternal() - this.fadeFactor, aimedSaturation));
            }
        } else {
            this.saturationVisual.setOpacityInternal((float)this.saturation.maxValue);
        }
    }
}
