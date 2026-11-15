package team.creative.enhancedvisuals.common.addon.survive;

import com.stereowalker.survive.api.needs.PlayerNeeds;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.premade.IntMinMax;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeFocus;

public class ThirstHandler extends VisualHandler {
    @CreativeConfig
    public double defaultIntensity = 0.0;
    @CreativeConfig
    public double maxIntensity = 5.0;
    @CreativeConfig
    public double fadeFactor = 0.05F;
    @CreativeConfig
    public IntMinMax thirstLevel = new IntMinMax(2, 8);
    @CreativeConfig
    public VisualType focus = new VisualTypeFocus("focus");
    public Visual focusVisual;

    public double getThirst(Player player) {
        return PlayerNeeds.api().getWater(player).getWaterLevel();
    }

    @Override
    public void tick(@Nullable Player player) {
        if (this.focusVisual == null) {
            this.focusVisual = new Visual(this.focus, this, 0);
            this.add(this.focusVisual);
        }

        double aimedSaturation = this.defaultIntensity;
        if (player != null && player.isAlive()) {
            double thirst = this.getThirst(player);
            if (thirst <= this.thirstLevel.max) {
                double leftFoodInSpan = thirst - this.thirstLevel.min;
                double spanLength = this.thirstLevel.spanLength();
                aimedSaturation = (1.0 - leftFoodInSpan / spanLength) * this.maxIntensity;
            }

            if (this.focusVisual.getOpacityInternal() < aimedSaturation) {
                this.focusVisual.setOpacityInternal((float)Math.min(this.focusVisual.getOpacityInternal() + this.fadeFactor, aimedSaturation));
            } else if (this.focusVisual.getOpacityInternal() > aimedSaturation) {
                this.focusVisual.setOpacityInternal((float)Math.max(this.focusVisual.getOpacityInternal() - this.fadeFactor, aimedSaturation));
            }
        } else {
            this.focusVisual.setOpacityInternal((float)this.defaultIntensity);
        }
    }
}
