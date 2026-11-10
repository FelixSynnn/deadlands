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
 * and greys it; a dust storm browns it and pulls it in further. Underwater, lava and blindness fogs are
 * untouched (they are other fog environments).
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
	private static final int FOG_GREY = 0xFF9A9C98;
	private static final int DUST_BROWN = 0xFFA08A64;

	@Inject(method = "setupFog", at = @At("TAIL"))
	private void deadlands$weatherFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
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
		int colour = cir.getReturnValueI();
		if (fog > 0.0F) {
			colour = ARGB.srgbLerp(fog * 0.6F, colour, FOG_GREY);
		}
		if (dust > 0.0F) {
			colour = ARGB.srgbLerp(dust * 0.75F, colour, DUST_BROWN);
		}
		cir.setReturnValue(colour);
	}
}
