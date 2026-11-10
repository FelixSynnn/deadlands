package com.deadlands.mixin;

import com.deadlands.noise.NoiseSystem;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards every game event (the vibrations sculk sensors hear) to the DEADLANDS noise system.
 * All of {@code Level.gameEvent}'s convenience overloads end in this method.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"))
	private void deadlands$hearGameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		NoiseSystem.onGameEvent((ServerLevel) (Object) this, event, pos, context);
	}
}
