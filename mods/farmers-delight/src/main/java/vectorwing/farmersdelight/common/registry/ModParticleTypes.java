package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModParticleTypes {
    public static final Supplier<SimpleParticleType> STAR = RegUtils.regParticle("star", () -> FabricParticleTypes.simple(true));
    public static final Supplier<SimpleParticleType> STEAM = RegUtils.regParticle("steam", () -> FabricParticleTypes.simple(true));
    public static final Supplier<SimpleParticleType> SPARKLE = RegUtils.regParticle("sparkle", () -> FabricParticleTypes.simple(true));

    public static void touch() {
    }
}
