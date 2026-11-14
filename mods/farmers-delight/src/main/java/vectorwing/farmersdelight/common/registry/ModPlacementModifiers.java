package vectorwing.farmersdelight.common.registry;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import vectorwing.farmersdelight.common.world.filter.BiomeTagFilter;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModPlacementModifiers {
    public static final Supplier<PlacementModifierType<BiomeTagFilter>> BIOME_TAG = RegUtils.regPlacementMod(
        "biome_tag", () -> typeConvert(BiomeTagFilter.CODEC)
    );

    private static <P extends PlacementModifier> PlacementModifierType<P> typeConvert(MapCodec<P> codec) {
        return () -> codec;
    }

    public static void touch() {
    }
}
