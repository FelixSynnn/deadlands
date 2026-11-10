package com.deadlands.client.mixin;

import com.deadlands.client.render.VehicleSeat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Seated poses in vehicles: the driver reaches forward to the wheel (or the handlebars), everyone
 * else rests their hands in their lap. The legs keep Minecraft's riding pose.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Shadow
	@Final
	public ModelPart rightArm;

	@Shadow
	@Final
	public ModelPart leftArm;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void deadlands$seatedArms(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof VehicleSeat seat) || seat.deadlands$seat() == 0) {
			return;
		}
		boolean driving = seat.deadlands$seat() == 1;
		rightArm.xRot = driving ? -1.25F : -0.55F;
		leftArm.xRot = driving ? -1.25F : -0.55F;
		rightArm.yRot = driving ? 0.12F : -0.2F;
		leftArm.yRot = driving ? -0.12F : 0.2F;
		rightArm.zRot = 0.0F;
		leftArm.zRot = 0.0F;
	}
}
