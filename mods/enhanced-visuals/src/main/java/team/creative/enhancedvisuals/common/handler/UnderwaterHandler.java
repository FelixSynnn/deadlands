package team.creative.enhancedvisuals.common.handler;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeBlur;
import team.creative.enhancedvisuals.common.event.EVEvents;

public class UnderwaterHandler extends VisualHandler {
    @CreativeConfig
    public double intensity = 5.0;
    @CreativeConfig
    public double fadeFactor = 0.5;
    @CreativeConfig
    public VisualType blur = new VisualTypeBlur("blur");
    public Visual blurVisual;

    public UnderwaterHandler() {
        this.enabled = false;
    }

    @Override
    public void tick(@Nullable Player player) {
        if (this.blurVisual == null) {
            this.blurVisual = new Visual(this.blur, this, 0);
            this.blurVisual.setOpacityInternal(0.0F);
            this.add(this.blurVisual);
        }

        if (player != null) {
            double aimed = EVEvents.areEyesInWater(player) ? this.intensity : 0.0;
            if (this.blurVisual.getOpacityInternal() < aimed) {
                this.blurVisual.setOpacityInternal((float)Math.min(this.blurVisual.getOpacityInternal() + this.fadeFactor, aimed));
            } else if (this.blurVisual.getOpacityInternal() > aimed) {
                this.blurVisual.setOpacityInternal((float)Math.max(this.blurVisual.getOpacityInternal() - this.fadeFactor, aimed));
            }
        } else {
            this.blurVisual.setOpacityInternal(0.0F);
        }
    }
}
