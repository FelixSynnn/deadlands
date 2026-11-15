package team.creative.enhancedvisuals.common.addon.survive;

import com.stereowalker.survive.api.needs.PlayerNeeds;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeOverlay;

public class TemperatureHandler extends VisualHandler {
    @CreativeConfig
    public double defaultIntensity = 0.0;
    @CreativeConfig
    public double mediumIntensity = 0.2;
    @CreativeConfig
    public double maxIntensity = 0.3;
    @CreativeConfig
    public double fadeFactor = 0.005;
    @CreativeConfig
    public VisualType freeze = new VisualTypeOverlay("freeze");
    public Visual freezeVisual;
    @CreativeConfig
    public VisualType heat = new VisualTypeOverlay("heat");
    public Visual heatVisual;
    @CreativeConfig
    public double coldestTemperature = -1.4;
    @CreativeConfig
    public double hypotheremiaTemperature = -1.0;
    @CreativeConfig
    public double coldTemperature = -0.5;
    @CreativeConfig
    public double defaultTemperature = 0.0;
    @CreativeConfig
    public double warmTemperature = 0.5;
    @CreativeConfig
    public double hyperthermiaTemperature = 1.0;
    @CreativeConfig
    public double hottestTemperature = 1.4;

    public double getTemperature(Player player) {
        return PlayerNeeds.api().getTemperature(player).getDisplayTemperature();
    }

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

        double aimedHeat = this.defaultIntensity;
        double aimedFreeze = this.defaultIntensity;
        double temp = this.defaultTemperature;
        if (player != null && player.isAlive()) {
            temp = this.getTemperature(player);
        }

        if (temp <= this.coldestTemperature) {
            aimedHeat = 0.0;
            aimedFreeze = this.maxIntensity;
        } else if (temp < this.hypotheremiaTemperature) {
            aimedHeat = 0.0;
            aimedFreeze = (1.0 - (temp - this.coldestTemperature) / (this.hypotheremiaTemperature - this.coldestTemperature))
                    * (this.maxIntensity - this.mediumIntensity)
                + this.mediumIntensity;
        } else if (temp < this.coldTemperature) {
            aimedHeat = 0.0;
            aimedFreeze = (1.0 - (temp - this.hypotheremiaTemperature) / (this.coldTemperature - this.hypotheremiaTemperature)) * this.mediumIntensity;
        } else if (temp >= this.hottestTemperature) {
            aimedHeat = this.maxIntensity;
            aimedFreeze = 0.0;
        } else if (temp > this.hyperthermiaTemperature) {
            aimedHeat = (this.hottestTemperature - temp)
                    / (this.hottestTemperature - this.hyperthermiaTemperature)
                    * (this.maxIntensity - this.mediumIntensity)
                + this.mediumIntensity;
            aimedFreeze = 0.0;
        } else if (temp > this.warmTemperature) {
            aimedHeat = (this.hyperthermiaTemperature - temp) / (this.hyperthermiaTemperature - this.warmTemperature) * this.mediumIntensity;
            aimedFreeze = 0.0;
        } else {
            aimedHeat = 0.0;
            aimedFreeze = 0.0;
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
