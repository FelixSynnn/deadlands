package vectorwing.farmersdelight.common.registry;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.world.WildCropGeneration;
import vectorwing.farmersdelight.refabricated.FDRefabricatedTags;

public class ModBiomeModifiers {
    private static @NonNull ResourceKey<PlacedFeature> modFeature(String red_colony) {
        return ResourceKey.create(Registries.PLACED_FEATURE, FarmersDelight.id(red_colony));
    }

    public static void init() {
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(FDRefabricatedTags.Biomes.HAS_BROWN_MUSHROOM_COLONY),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_BROWN_MUSHROOM_COLONIES
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(FDRefabricatedTags.Biomes.HAS_RED_MUSHROOM_COLONY),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_RED_MUSHROOM_COLONIES
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(FDRefabricatedTags.Biomes.HAS_WILD_CABBAGE),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_CABBAGES
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(FDRefabricatedTags.Biomes.HAS_WILD_BEETROOTS),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_BEETROOTS
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(
                0.4F, 0.9F, FDRefabricatedTags.Biomes.WILD_CARROTS_WHITELIST, FDRefabricatedTags.Biomes.WILD_CARROTS_BLACKLIST
            ),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_CARROTS
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(0.4F, 0.9F, FDRefabricatedTags.Biomes.WILD_ONIONS_WHITELIST, FDRefabricatedTags.Biomes.WILD_ONIONS_BLACKLIST),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_ONIONS
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(
                0.1F, 0.3F, FDRefabricatedTags.Biomes.WILD_POTATOES_WHITELIST, FDRefabricatedTags.Biomes.WILD_POTATOES_BLACKLIST
            ),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_POTATOES
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(-4.0F, 4.0F, FDRefabricatedTags.Biomes.WILD_RICE_WHITELIST, FDRefabricatedTags.Biomes.WILD_RICE_BLACKLIST),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_RICE
        );
        BiomeModifications.addFeature(
            new ModBiomeModifiers.FDBiomeSelector(
                -4.0F, 4.0F, FDRefabricatedTags.Biomes.WILD_TOMATOES_WHITELIST, FDRefabricatedTags.Biomes.WILD_TOMATOES_BLACKLIST
            ),
            Decoration.VEGETAL_DECORATION,
            WildCropGeneration.PATCH_WILD_TOMATOES
        );
    }

    public record FDBiomeSelector(float minTemperature, float maxTemperature, TagKey<Biome> allowed, @Nullable TagKey<Biome> denied)
        implements Predicate<BiomeSelectionContext> {
        public FDBiomeSelector(TagKey<Biome> tagKey) {
            this(-4.0F, 4.0F, tagKey, null);
        }

        public boolean test(BiomeSelectionContext context) {
            Holder<Biome> biome = context.getBiomeRegistryEntry();
            float temp = biome.value().getBaseTemperature();
            return this.denied != null && biome.is(this.denied) ? false : biome.is(this.allowed) && temp >= this.minTemperature && temp <= this.maxTemperature;
        }
    }
}
