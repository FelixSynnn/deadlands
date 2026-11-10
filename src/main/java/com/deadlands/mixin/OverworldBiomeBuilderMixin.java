package com.deadlands.mixin;

import com.deadlands.world.biome.BiomeReplacement;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Names a DEADLANDS biome in vanilla's overworld climate slots for land biomes (see
 * {@link BiomeReplacement}). The table is built when a world's worldgen registries load, through
 * the overworld multi-noise preset, so every world type that uses it (default, large biomes,
 * amplified) gets DEADLANDS biomes in newly generated chunks. Biome lookups then resolve the new
 * keys through the registry like any other biome, so structures, features and spawning need no
 * further patching.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {
	@Inject(method = "addSurfaceBiome", at = @At("HEAD"), cancellable = true)
	private void deadlands$replaceSurfaceBiome(
		Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
		Climate.Parameter temperature,
		Climate.Parameter humidity,
		Climate.Parameter continentalness,
		Climate.Parameter erosion,
		Climate.Parameter weirdness,
		float offset,
		ResourceKey<Biome> biome,
		CallbackInfo ci
	) {
		if (!BiomeReplacement.enabled()) {
			return;
		}
		ResourceKey<Biome> replacement = BiomeReplacement.replace(biome, temperature);
		if (replacement == biome) {
			return;
		}
		// Same two entries vanilla adds (surface and depth 1), with the DEADLANDS biome.
		consumer.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(0.0F), weirdness, offset), replacement));
		consumer.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.0F), weirdness, offset), replacement));
		ci.cancel();
	}
}
