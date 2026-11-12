package com.deadlands.client.mixin;

import com.deadlands.client.weather.ClientWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DEADLANDS weather in Minecraft's open-air fog: weather fog pulls the fog in to a few dozen blocks
 * and greys it; a dust storm browns it and pulls it in further. Rain thins the view (Phase 3: weather
 * reduces visibility): to about 110 blocks in plain rain, 70 in a thunderstorm and 44 in heavy rain,
 * with an overcast grey. Every tint is scaled by how bright the sky's own fog colour already is, so
 * at night the fog stays as dark as the night (Hard Rule 3: no grey glow on the horizon). Underwater,
 * lava and blindness fogs are untouched (they are other fog environments).
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
	private static final int FOG_GREY = 0xFF9A9C98;
	private static final int DUST_BROWN = 0xFFA08A64;
	private static final int RAIN_GREY = 0xFF6E7378;

	/** How much rain is falling on the camera (0 where the biome has none). */
	private static float rainAt(ClientLevel level, Camera camera, float partialTick) {
		float rain = level.getRainLevel(partialTick);
		if (rain <= 0.0F) {
			return 0.0F;
		}
		net.minecraft.core.BlockPos pos = camera.blockPosition();
		return level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == net.minecraft.world.level.biome.Biome.Precipitation.NONE ? 0.0F : rain;
	}

	/** Lerps toward {@code target} dimmed to the base colour's own brightness (dark nights stay dark). */
	private static int tint(int colour, int target, float amount) {
		float luma = (0.299F * ARGB.red(colour) + 0.587F * ARGB.green(colour) + 0.114F * ARGB.blue(colour)) / 255.0F;
		float k = Mth.clamp(luma / 0.6F, 0.0F, 1.0F);
		int dim = ARGB.color(255, (int) (ARGB.red(target) * k), (int) (ARGB.green(target) * k), (int) (ARGB.blue(target) * k));
		return ARGB.srgbLerp(amount, colour, dim);
	}

	@Inject(method = "setupFog", at = @At("TAIL"))
	private void deadlands$weatherFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float rain = rainAt(level, camera, partial);
		if (rain > 0.0F) {
			float heavy = ClientWeather.heavy();
			float storm = Math.min(1.0F, level.getThunderLevel(partial));
			float rainEnd = heavy > 0.0F ? Mth.lerp(heavy, 70.0F, 44.0F) : Mth.lerp(storm, 110.0F, 70.0F);
			fog.environmentalStart = Math.min(fog.environmentalStart, Mth.lerp(rain, fog.environmentalStart, 0.0F));
			fog.environmentalEnd = Math.min(fog.environmentalEnd, Mth.lerp(rain, fog.environmentalEnd, rainEnd));
		}
		float thick = Math.max(ClientWeather.fog(), ClientWeather.dust() * 1.1F);
		if (thick <= 0.0F) {
			return;
		}
		float end = ClientWeather.dust() > ClientWeather.fog() ? 36.0F : 52.0F;
		fog.environmentalStart = Mth.lerp(Math.min(1.0F, thick), fog.environmentalStart, 2.0F);
		fog.environmentalEnd = Mth.lerp(Math.min(1.0F, thick), fog.environmentalEnd, end);
	}

	@Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
	private void deadlands$weatherColour(ClientLevel level, Camera camera, int renderDistance, float partialTick, CallbackInfoReturnable<Integer> cir) {
		float fog = ClientWeather.fog();
		float dust = ClientWeather.dust();
		float rain = rainAt(level, camera, partialTick);
		int colour = cir.getReturnValueI();
		if (rain > 0.0F) {
			colour = tint(colour, RAIN_GREY, rain * (0.3F + 0.3F * ClientWeather.heavy()));
		}
		if (fog > 0.0F) {
			colour = tint(colour, FOG_GREY, fog * 0.6F);
		}
		if (dust > 0.0F) {
			colour = tint(colour, DUST_BROWN, dust * 0.75F);
		}
		cir.setReturnValue(colour);
	}
}
