package team.creative.enhancedvisuals.common.handler;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.premade.IntMinMax;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;
import team.creative.creativecore.common.util.type.Color;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeOverlay;

public class PotionHandler extends VisualHandler {
    @CreativeConfig
    public IntMinMax duration = new IntMinMax(30, 60);
    @CreativeConfig
    public VisualType potion = new VisualTypeOverlay("potion");

    public void impact(double distance, ItemStack stack) {
        double modifier = 1.0 - Math.min(5.0, distance) / 5.0;
        int var11 = ((PotionContents)stack.getComponents().get(DataComponents.POTION_CONTENTS)).getColor();
        float r = (var11 >> 16 & 0xFF) / 255.0F;
        float g = (var11 >> 8 & 0xFF) / 255.0F;
        float b = (var11 & 0xFF) / 255.0F;
        if (!(modifier <= 0.0)) {
            Visual v = this.addVisualFadeOut(this.potion, this, new DecimalCurve(0.0, Math.min(1.0, modifier * 2.0), this.duration.next(this.random()), 0.0));
            v.color = new Color(r, g, b);
        }
    }
}
