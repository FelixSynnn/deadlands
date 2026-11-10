package com.deadlands.mixin;

import com.deadlands.survival.LocalSurvival;
import com.deadlands.survival.SurvivalAttachments;
import com.deadlands.survival.SurvivalSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A winded survivor cannot sprint. Minecraft asks this question before letting a player start or
 * keep sprinting (the same check that stops sprinting on an empty stomach); the client answers
 * from the state the server last sent, the server from the survivor's own data.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"), cancellable = true)
	private void deadlands$windedCannotSprint(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) {
			return;
		}
		Player self = (Player) (Object) this;
		boolean winded = self instanceof ServerPlayer survivor
			? SurvivalSystem.applies(survivor) && SurvivalAttachments.get(survivor).winded()
			: self.level().isClientSide() && self.isLocalPlayer() && LocalSurvival.winded();
		if (winded) {
			cir.setReturnValue(false);
		}
	}
}
