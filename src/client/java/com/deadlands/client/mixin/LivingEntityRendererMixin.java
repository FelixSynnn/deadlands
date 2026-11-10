package com.deadlands.client.mixin;

import com.deadlands.client.render.VehicleSeat;
import com.deadlands.vehicle.VehicleEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes, for anyone humanoid, whether they drive or ride a DEADLANDS vehicle. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void deadlands$seat(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		if (state instanceof VehicleSeat seat) {
			int value = 0;
			if (entity.getVehicle() instanceof VehicleEntity vehicle) {
				value = vehicle.getControllingPassenger() == entity ? 1 : 2;
			}
			seat.deadlands$setSeat(value);
		}
	}
}
