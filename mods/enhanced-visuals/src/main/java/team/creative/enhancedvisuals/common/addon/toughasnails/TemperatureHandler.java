package team.creative.enhancedvisuals.common.addon.toughasnails;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeOverlay;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;

public class TemperatureHandler extends VisualHandler {
    @CreativeConfig
    public double fadeFactor = 0.005;
    @CreativeConfig
    public VisualType freeze = new VisualTypeOverlay("freeze");
    public Visual freezeVisual;
    @CreativeConfig
    public VisualType heat = new VisualTypeOverlay("heat");
    public Visual heatVisual;
    @CreativeConfig
    public double icyIntensity = 0.3;
    @CreativeConfig
    public double coldIntensity = 0.2;
    @CreativeConfig
    public double neutralIntensity = 0.0;
    @CreativeConfig
    public double warmIntensity = 0.2;
    @CreativeConfig
    public double hotIntensity = 0.3;

    @Override
    public void tick(@Nullable Player player) {
        if (this.freezeVisual == null) {
            this.freezeVisual = new Visual(this.freeze, this, 0);
            this.freezeVisual.setOpacityInternal(0.0F);
            this.add(this.freezeVisual);
            this.heatVisual = new Visual(this.heat, this, 0);
            this.heatVisual.setOpacityInternal(0.0F);
            this.add(this.heatVisual);
        }

        double aimedHeat = this.neutralIntensity;
        double aimedFreeze = this.neutralIntensity;
        TemperatureLevel temp = TemperatureLevel.NEUTRAL;
        if (player != null && player.isAlive() && TemperatureHelper.isTemperatureEnabled()) {
            temp = TemperatureHelper.getTemperatureForPlayer(player);
        }

        switch (temp) {
            case ICY:
                aimedHeat = this.neutralIntensity;
                aimedFreeze = this.icyIntensity;
                break;
            case COLD:
                aimedHeat = this.neutralIntensity;
                aimedFreeze = this.coldIntensity;
                break;
            case NEUTRAL:
                aimedHeat = this.neutralIntensity;
                aimedFreeze = this.neutralIntensity;
                break;
            case WARM:
                aimedHeat = this.warmIntensity;
                aimedFreeze = this.neutralIntensity;
                break;
            case HOT:
                aimedHeat = this.hotIntensity;
                aimedFreeze = this.neutralIntensity;
        }

        if (this.freezeVisual.getOpacityInternal() < aimedFreeze) {
            this.freezeVisual.setOpacityInternal((float)Math.min(this.freezeVisual.getOpacityInternal() + this.fadeFactor, aimedFreeze));
        } else if (this.freezeVisual.getOpacityInternal() > aimedFreeze) {
            this.freezeVisual.setOpacityInternal((float)Math.max(this.freezeVisual.getOpacityInternal() - this.fadeFactor, aimedFreeze));
        }

        if (this.heatVisual.getOpacityInternal() < aimedHeat) {
            this.heatVisual.setOpacityInternal((float)Math.min(this.heatVisual.getOpacityInternal() + this.fadeFactor, aimedHeat));
        } else if (this.heatVisual.getOpacityInternal() > aimedHeat) {
            this.heatVisual.setOpacityInternal((float)Math.max(this.heatVisual.getOpacityInternal() - this.fadeFactor, aimedHeat));
        }
    }
}
