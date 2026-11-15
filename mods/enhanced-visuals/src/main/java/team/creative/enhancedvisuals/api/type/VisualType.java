package team.creative.enhancedvisuals.api.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import team.creative.creativecore.Side;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.api.ICreativeConfig;
import team.creative.creativecore.common.config.api.CreativeConfig.DecimalRange;
import team.creative.creativecore.common.util.type.Color;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualCategory;
import team.creative.enhancedvisuals.api.VisualHandler;

public abstract class VisualType implements ICreativeConfig {
    private static final List<VisualType> TYPES = new ArrayList<>();
    public Object clientSideType;
    @CreativeConfig
    public boolean disabled = false;
    private boolean isEffectedByWater = true;
    @CreativeConfig
    @DecimalRange(max = 1.0, min = 0.0)
    public float opacity = 1.0F;
    public final String name;
    public final VisualCategory cat;

    public static Iterable<VisualType> types() {
        return TYPES;
    }

    public VisualType(String name, VisualCategory cat) {
        this.name = name;
        this.cat = cat;
        TYPES.add(this);
    }

    public VisualType setIgnoreWater() {
        this.isEffectedByWater = false;
        return this;
    }

    public boolean isAffectedByWater() {
        return this.cat.isAffectedByWater() && this.isEffectedByWater;
    }

    @Override
    public void configured(Side side) {
    }

    public Color getColor() {
        return null;
    }

    public boolean canRotate() {
        return true;
    }

    public boolean isVisible(VisualHandler handler, Visual visual) {
        return visual.getOpacity() > 0.0F && !this.disabled;
    }

    public boolean scaleVariants() {
        return false;
    }

    public double randomScale(Random rand) {
        return 1.0;
    }
}
