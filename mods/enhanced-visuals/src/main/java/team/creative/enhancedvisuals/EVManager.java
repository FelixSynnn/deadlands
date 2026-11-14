package team.creative.enhancedvisuals;

import java.util.Collection;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.premade.IntMinMax;
import team.creative.creativecore.common.config.premade.curve.Curve;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;
import team.creative.creativecore.common.util.type.Color;
import team.creative.enhancedvisuals.api.Particle;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualCategory;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;

public interface EVManager {
    Random random();

    void playSound(Identifier var1);

    void playSound(Identifier var1, BlockPos var2);

    void playSound(Identifier var1, float var2);

    void playSound(Identifier var1, BlockPos var2, float var3);

    void playSoundFadeOut(Identifier var1, BlockPos var2, DecimalCurve var3);

    Collection<Visual> visuals(VisualCategory var1);

    void clearEverything();

    void add(Visual var1);

    boolean remove(Visual var1);

    void playTicking(Identifier var1, BlockPos var2, DecimalCurve var3);

    Visual addVisualFadeOut(VisualType var1, VisualHandler var2, IntMinMax var3);

    Visual addVisualFadeOut(VisualType var1, VisualHandler var2, int var3);

    Visual addVisualFadeOut(VisualType var1, VisualHandler var2, Curve var3);

    void addParticlesFadeOut(VisualType var1, VisualHandler var2, int var3, IntMinMax var4, boolean var5);

    void addParticlesFadeOut(VisualType var1, VisualHandler var2, int var3, IntMinMax var4, boolean var5, @Nullable Color var6);

    void addParticlesFadeOut(VisualType var1, VisualHandler var2, int var3, int var4, boolean var5);

    void addParticlesFadeOut(VisualType var1, VisualHandler var2, int var3, Curve var4, boolean var5, @Nullable Color var6);

    Particle addParticle(VisualType var1, VisualHandler var2, boolean var3, @Nullable Color var4);

    int generateOffset(Random var1, int var2, int var3);
}
