package team.creative.enhancedvisuals.api;

import java.util.Collection;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.Side;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.api.ICreativeConfig;
import team.creative.creativecore.common.config.api.CreativeConfig.DecimalRange;
import team.creative.creativecore.common.config.premade.IntMinMax;
import team.creative.creativecore.common.config.premade.curve.Curve;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;
import team.creative.creativecore.common.util.type.Color;
import team.creative.enhancedvisuals.EVManager;
import team.creative.enhancedvisuals.EnhancedVisuals;
import team.creative.enhancedvisuals.api.type.VisualType;

public class VisualHandler implements ICreativeConfig, EVManager {
    @CreativeConfig
    public boolean enabled = true;
    @CreativeConfig
    @DecimalRange(max = 1.0, min = 0.0)
    public float opacity = 1.0F;

    @Override
    public void configured(Side side) {
    }

    public void tick(@Nullable Player player) {
    }

    public boolean isEnabled(@Nullable Player player) {
        return this.enabled && this.opacity > 0.0F;
    }

    public EVManager manager() {
        return EnhancedVisuals.MANAGER;
    }

    @Override
    public Random random() {
        return this.manager().random();
    }

    @Override
    public void playSound(Identifier identifier) {
        this.manager().playSound(identifier);
    }

    @Override
    public void playSound(Identifier identifier, BlockPos pos) {
        this.manager().playSound(identifier, pos);
    }

    @Override
    public void playSound(Identifier identifier, float volume) {
        this.manager().playSound(identifier, volume);
    }

    @Override
    public void playSound(Identifier identifier, BlockPos pos, float volume) {
        this.manager().playSound(identifier, pos, volume);
    }

    @Override
    public void playSoundFadeOut(Identifier identifier, BlockPos pos, DecimalCurve volume) {
        this.manager().playSoundFadeOut(identifier, pos, volume);
    }

    @Override
    public Collection<Visual> visuals(VisualCategory category) {
        return this.manager().visuals(category);
    }

    @Override
    public void clearEverything() {
        this.manager().clearEverything();
    }

    @Override
    public void add(Visual visual) {
        this.manager().add(visual);
    }

    @Override
    public boolean remove(Visual visual) {
        return this.manager().remove(visual);
    }

    @Override
    public void playTicking(Identifier identifier, BlockPos pos, DecimalCurve volume) {
        this.manager().playTicking(identifier, pos, volume);
    }

    @Override
    public Visual addVisualFadeOut(VisualType vt, VisualHandler handler, IntMinMax time) {
        return this.manager().addVisualFadeOut(vt, handler, time);
    }

    @Override
    public Visual addVisualFadeOut(VisualType vt, VisualHandler handler, int time) {
        return this.manager().addVisualFadeOut(vt, handler, time);
    }

    @Override
    public Visual addVisualFadeOut(VisualType vt, VisualHandler handler, Curve curve) {
        return this.manager().addVisualFadeOut(vt, handler, curve);
    }

    @Override
    public void addParticlesFadeOut(VisualType vt, VisualHandler handler, int count, IntMinMax time, boolean rotate) {
        this.manager().addParticlesFadeOut(vt, handler, count, time, rotate);
    }

    @Override
    public void addParticlesFadeOut(VisualType vt, VisualHandler handler, int count, IntMinMax time, boolean rotate, @Nullable Color color) {
        this.manager().addParticlesFadeOut(vt, handler, count, time, rotate, color);
    }

    @Override
    public void addParticlesFadeOut(VisualType vt, VisualHandler handler, int count, int time, boolean rotate) {
        this.manager().addParticlesFadeOut(vt, handler, count, time, rotate);
    }

    @Override
    public void addParticlesFadeOut(VisualType vt, VisualHandler handler, int count, Curve curve, boolean rotate, @Nullable Color color) {
        this.manager().addParticlesFadeOut(vt, handler, count, curve, rotate, color);
    }

    @Override
    public Particle addParticle(VisualType vt, VisualHandler handler, boolean rotate, @Nullable Color color) {
        return this.manager().addParticle(vt, handler, rotate, color);
    }

    @Override
    public int generateOffset(Random rand, int dimensionLength, int spacingBuffer) {
        return this.manager().generateOffset(rand, dimensionLength, spacingBuffer);
    }
}
