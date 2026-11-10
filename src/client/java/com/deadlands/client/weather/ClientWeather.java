package com.deadlands.client.weather;

import com.deadlands.network.WeatherPayload;
import com.deadlands.registry.DLParticles;
import com.deadlands.world.weather.WeatherDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The client's side of the DEADLANDS weather: eases the fog, dust and wind in and out (about a minute)
 * and blows dust, wind-borne grit and ash around the camera. Dust shows only where the land is dry;
 * elsewhere a dust storm is just wind. The fog itself is applied by {@code AtmosphericFogMixin}.
 */
public final class ClientWeather {
	private static WeatherDirector.Kind kind = WeatherDirector.Kind.CLEAR;
	private static float target;
	private static float current;
	private static WeatherDirector.Kind shown = WeatherDirector.Kind.CLEAR;

	private ClientWeather() {
	}

	public static void set(WeatherPayload payload) {
		kind = WeatherDirector.Kind.byIndex(payload.kind());
		target = Mth.clamp(payload.strength(), 0.0F, 1.0F);
	}

	public static void reset() {
		kind = WeatherDirector.Kind.CLEAR;
		shown = WeatherDirector.Kind.CLEAR;
		target = 0.0F;
		current = 0.0F;
	}

	/** 0 to 1: how thick the weather fog is. */
	public static float fog() {
		return shown == WeatherDirector.Kind.FOG ? current : 0.0F;
	}

	/** 0 to 1: how thick the dust is where the survivor stands. */
	public static float dust() {
		return shown == WeatherDirector.Kind.DUST && dryHere() ? current : 0.0F;
	}

	/** 0 to 1: how hard the wind blows (dust storms are windy everywhere; rain adds to it). */
	public static float wind() {
		Minecraft minecraft = Minecraft.getInstance();
		float rain = minecraft.level == null ? 0.0F : minecraft.level.getRainLevel(1.0F) * 0.35F + minecraft.level.getThunderLevel(1.0F) * 0.3F;
		float own = shown == WeatherDirector.Kind.WIND || shown == WeatherDirector.Kind.DUST ? current : 0.0F;
		return Math.min(1.0F, own + rain);
	}

	private static boolean dryHere() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return false;
		}
		String biome = minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		return biome.contains("dry") || biome.contains("desert") || biome.contains("badlands") || biome.contains("wasteland") || biome.contains("savanna");
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		// Fade out the old kind before the new one comes in.
		if (shown != kind) {
			current = Math.max(0.0F, current - 0.02F);
			if (current <= 0.0F) {
				shown = kind;
			}
		} else {
			current += Mth.clamp(target - current, -0.02F, 0.02F);
		}
		RandomSource random = minecraft.level.getRandom();
		BlockPos at = minecraft.player.blockPosition();
		double x = minecraft.player.getX();
		double y = minecraft.player.getEyeY();
		double z = minecraft.player.getZ();
		boolean outside = minecraft.level.canSeeSky(at);
		float dust = dust();
		if (dust > 0.0F && outside) {
			for (int i = 0; i < (int) (dust * 10); i++) {
				minecraft.level.addParticle(DLParticles.DUST, x + random.nextGaussian() * 10.0, y + random.nextGaussian() * 3.0, z + random.nextGaussian() * 10.0,
					0.25 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.02, 0.1 + random.nextDouble() * 0.08);
			}
		}
		float wind = wind();
		if (wind > 0.3F && outside && random.nextFloat() < wind * 0.5F) {
			minecraft.level.addParticle(DLParticles.DUST, x + random.nextGaussian() * 8.0, y + random.nextGaussian() * 2.0, z + random.nextGaussian() * 8.0,
				0.3 * wind, 0.0, 0.12 * wind);
		}
		String biome = minecraft.level.getBiome(at).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		if (biome.contains("burned") && outside && random.nextInt(3) == 0) {
			minecraft.level.addParticle(DLParticles.ASH, x + random.nextGaussian() * 9.0, y + 4.0 + random.nextDouble() * 4.0, z + random.nextGaussian() * 9.0,
				0.0, -0.02, 0.0);
		}
	}
}
