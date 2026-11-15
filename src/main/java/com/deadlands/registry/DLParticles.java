package com.deadlands.registry;

import com.deadlands.Deadlands;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * DEADLANDS particles (Milestone 9): ash falling in burned country, dust in dust storms, blood from
 * the wounded, spores drifting off nests, embers from crash sites and muzzle flashes. Their sprites and
 * motion are client-side ({@code client.particle.DLParticleProviders}).
 */
public final class DLParticles {
	public static final SimpleParticleType ASH = register("ash");
	public static final SimpleParticleType DUST = register("dust");
	public static final SimpleParticleType BLOOD = register("blood");
	public static final SimpleParticleType SPORE = register("spore");
	public static final SimpleParticleType EMBER = register("ember");
	/** Mythos' corruption: pixel blocks breaking up, magenta and violet. */
	public static final SimpleParticleType GLITCH = register("glitch");

	private DLParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Deadlands.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
