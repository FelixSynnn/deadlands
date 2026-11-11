package com.deadlands.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The night sky shows only faint stars: a tenth of vanilla's brightness (Hard Rule 3). */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void deadlands$faintStars(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		state.starBrightness *= 0.1F;
	}
}
