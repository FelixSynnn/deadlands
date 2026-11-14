package vectorwing.farmersdelight.common.world.modifier;

import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

@Deprecated
public record AddFeaturesByFilterBiomeModifier(
    HolderSet<Biome> allowedBiomes,
    Optional<HolderSet<Biome>> deniedBiomes,
    Optional<Float> minimumTemperature,
    Optional<Float> maximumTemperature,
    HolderSet<PlacedFeature> features,
    Decoration step
) {
}
