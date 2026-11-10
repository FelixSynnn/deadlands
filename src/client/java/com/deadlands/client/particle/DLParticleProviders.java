package com.deadlands.client.particle;

import com.deadlands.registry.DLParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * How the DEADLANDS particles move and look: ash drifts down in grey flakes, dust blows sideways in
 * a sandy haze, blood falls in dark drops, spores rise slowly in a sick green, embers lift and fade
 * orange. All animate through their sprite frames as they age.
 */
public final class DLParticleProviders {
	private DLParticleProviders() {
	}

	public static void register() {
		ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
		registry.register(DLParticles.ASH, sprites -> new Provider(sprites, Kind.ASH));
		registry.register(DLParticles.DUST, sprites -> new Provider(sprites, Kind.DUST));
		registry.register(DLParticles.BLOOD, sprites -> new Provider(sprites, Kind.BLOOD));
		registry.register(DLParticles.SPORE, sprites -> new Provider(sprites, Kind.SPORE));
		registry.register(DLParticles.EMBER, sprites -> new Provider(sprites, Kind.EMBER));
	}

	enum Kind {
		//    gravity lifetime scale  red    green  blue
		ASH(0.02F, 50, 0.9F, 0.42F, 0.41F, 0.40F),
		DUST(0.0F, 34, 0.7F, 0.62F, 0.54F, 0.40F),
		BLOOD(0.9F, 22, 0.6F, 0.42F, 0.05F, 0.04F),
		SPORE(-0.015F, 70, 0.5F, 0.45F, 0.52F, 0.24F),
		EMBER(-0.03F, 18, 0.45F, 1.0F, 0.55F, 0.18F);

		final float gravity;
		final int lifetime;
		final float scale;
		final float red;
		final float green;
		final float blue;

		Kind(float gravity, int lifetime, float scale, float red, float green, float blue) {
			this.gravity = gravity;
			this.lifetime = lifetime;
			this.scale = scale;
			this.red = red;
			this.green = green;
			this.blue = blue;
		}
	}

	static final class Mote extends BaseAshSmokeParticle {
		Mote(ClientLevel level, double x, double y, double z, double xs, double ys, double zs, SpriteSet sprites, Kind kind) {
			super(level, x, y, z, 0.1F, kind == Kind.ASH ? -0.1F : 0.1F, 0.1F, xs, ys, zs, kind.scale, sprites, 1.0F, kind.lifetime, kind.gravity,
				kind == Kind.BLOOD);
			float shade = 0.85F + random.nextFloat() * 0.3F;
			this.rCol = Math.min(1.0F, kind.red * shade);
			this.gCol = Math.min(1.0F, kind.green * shade);
			this.bCol = Math.min(1.0F, kind.blue * shade);
		}
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		private final Kind kind;

		Provider(SpriteSet sprites, Kind kind) {
			this.sprites = sprites;
			this.kind = kind;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xs, double ys, double zs,
			RandomSource random) {
			return new Mote(level, x, y, z, xs, ys, zs, sprites, kind);
		}
	}
}
