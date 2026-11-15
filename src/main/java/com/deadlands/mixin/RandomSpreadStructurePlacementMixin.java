package com.deadlands.mixin;

import com.deadlands.config.ConfigManager;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Town frequency ({@code world.townFrequency}): when the towns' structure set (data/deadlands/worldgen/structure_set/
 * towns.json, salt 461873205, spacing 34 and separation 14 chunks) is read as a world loads, both are divided by the
 * square root of the setting, so towns become that many times as common per area (1.5: spacing 28, separation 11).
 * The roads, which read the same placement (world.town.TownSites), follow automatically.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {
	private static final int TOWN_SALT = 461873205;

	@Shadow
	@Final
	@Mutable
	private int spacing;

	@Shadow
	@Final
	@Mutable
	private int separation;

	@Inject(method = "<init>(Lnet/minecraft/core/Vec3i;Lnet/minecraft/world/level/levelgen/structure/placement/StructurePlacement$FrequencyReductionMethod;FILjava/util/Optional;IILnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadType;)V",
		at = @At("TAIL"))
	private void deadlands$townFrequency(Vec3i offset, StructurePlacement.FrequencyReductionMethod reduction, float frequency, int salt,
		Optional<StructurePlacement.ExclusionZone> exclusion, int spacing, int separation, RandomSpreadType type, CallbackInfo ci) {
		if (salt != TOWN_SALT) {
			return;
		}
		double scale = Math.sqrt(Math.max(0.5, ConfigManager.get().world.townFrequency));
		int newSpacing = Math.max(8, (int) Math.round(spacing / scale));
		int newSeparation = Math.max(2, Math.min(newSpacing - 2, (int) Math.round(separation / scale)));
		this.spacing = newSpacing;
		this.separation = newSeparation;
	}
}
