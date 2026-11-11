package com.deadlands.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nothing travels through a portal, so even one left from an older world leads nowhere. */
@Mixin(Entity.class)
public abstract class EntityPortalMixin {
	@Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
	private void deadlands$noPortalTravel(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
