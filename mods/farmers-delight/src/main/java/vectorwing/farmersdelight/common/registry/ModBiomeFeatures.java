package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import vectorwing.farmersdelight.common.world.configuration.WildCropConfiguration;
import vectorwing.farmersdelight.common.world.feature.WildCropFeature;
import vectorwing.farmersdelight.common.world.feature.WildRiceFeature;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModBiomeFeatures {
    public static final Supplier<Feature<NoneFeatureConfiguration>> WILD_RICE = RegUtils.regFeature(
        "wild_rice", () -> new WildRiceFeature(NoneFeatureConfiguration.CODEC)
    );
    public static final Supplier<Feature<WildCropConfiguration>> WILD_CROP = RegUtils.regFeature(
        "wild_crop", () -> new WildCropFeature(WildCropConfiguration.CODEC)
    );

    public static void touch() {
    }
}
