package team.creative.enhancedvisuals.api;

import team.creative.creativecore.common.config.premade.curve.Curve;
import team.creative.creativecore.common.util.type.Color;
import team.creative.enhancedvisuals.api.type.VisualType;

public class Visual {
    public final VisualType type;
    public final VisualHandler handler;
    private float opacity;
    public final boolean endless;
    public final Curve animation;
    private boolean displayed = false;
    private int tick = 0;
    public Color color;
    public int variant;

    public Visual(VisualType type, VisualHandler handler, Curve animation, int variant) {
        this.type = type;
        this.handler = handler;
        this.animation = animation;
        this.variant = variant;
        this.endless = false;
        this.color = type.getColor();
    }

    public Visual(VisualType type, VisualHandler handler, int variant) {
        this.type = type;
        this.handler = handler;
        this.animation = null;
        this.variant = variant;
        this.endless = true;
        this.color = type.getColor();
    }

    public void setOpacityInternal(float opacity) {
        this.opacity = opacity;
    }

    public float getOpacityInternal() {
        return this.opacity;
    }

    public float getOpacity() {
        return this.handler.opacity * this.opacity * this.type.opacity;
    }

    public boolean displayed() {
        return this.displayed;
    }

    public void addToDisplay() {
        this.displayed = true;
    }

    public void removeFromDisplay() {
        this.displayed = false;
    }

    public VisualCategory getCategory() {
        return this.type.cat;
    }

    public boolean isVisible() {
        return this.type.isVisible(this.handler, this);
    }

    public boolean tick() {
        if (this.endless) {
            return true;
        }

        this.opacity = (float)this.animation.valueAt(this.tick++);
        return this.opacity > 0.0F;
    }

    public int getWidth(int screenWidth) {
        return screenWidth;
    }

    public int getHeight(int screenHeight) {
        return screenHeight;
    }

    public boolean isAffectedByWater() {
        return this.type.isAffectedByWater();
    }
}
