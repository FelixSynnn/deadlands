package com.deadlands.client.mixin;

import com.deadlands.client.light.Darkness;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hard Rule 3 at the lightmap's inputs (see {@link Darkness}). The values are written to the
 * lightmap uniform block in this order: ambient light, sky factor (1), block factor, night vision
 * (3), darkness effect, world darkening, brightness slider (6); then the sky light colour.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";
	private static final String PUT_VEC3 = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;";

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 1))
	private float deadlands$darkNights(float skyFactor) {
		return Darkness.skyFactor(skyFactor);
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 3))
	private float deadlands$noNightVision(float nightVision) {
		return 0.0F;
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 6))
	private float deadlands$noBrightnessBoost(float brightness) {
		return 0.0F;
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_VEC3, ordinal = 0))
	private Vector3fc deadlands$overcast(Vector3fc skyLightColor) {
		return Darkness.overcast(skyLightColor);
	}
}
