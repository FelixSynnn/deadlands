package com.deadlands.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Nether is closed: an obsidian frame never lights into a portal. */
@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
	@Inject(method = "findEmptyPortalShape", at = @At("HEAD"), cancellable = true)
	private static void deadlands$noNetherPortals(LevelAccessor level, BlockPos pos, Direction.Axis axis, CallbackInfoReturnable<Optional<PortalShape>> cir) {
		cir.setReturnValue(Optional.empty());
	}
}
