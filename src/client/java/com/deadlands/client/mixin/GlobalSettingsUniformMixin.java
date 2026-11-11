package com.deadlands.client.mixin;

import com.deadlands.client.light.FlashlightClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.Std140Builder;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Appends {@code vec4 DeadlandsFlashlight} to the shared {@code Globals} uniform block (see
 * {@link FlashlightClient}). The buffer grows by one std140 vec4 at the end, so shaders that declare
 * the vanilla block still read exactly what they did before; the DEADLANDS copy of
 * {@code globals.glsl} declares the extra member.
 */
@Mixin(GlobalSettingsUniform.class)
public abstract class GlobalSettingsUniformMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140SizeCalculator;get()I"))
	private static int deadlands$roomForFlashlight(int vanillaSize) {
		return ((vanillaSize + 15) & ~15) + 16;
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;get()Ljava/nio/ByteBuffer;"))
	private ByteBuffer deadlands$writeFlashlight(Std140Builder builder, Operation<ByteBuffer> original) {
		float[] beam = FlashlightClient.uniform();
		builder.putVec4(beam[0], beam[1], beam[2], beam[3]);
		return original.call(builder);
	}
}
