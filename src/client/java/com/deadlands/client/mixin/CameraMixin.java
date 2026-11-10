package com.deadlands.client.mixin;

import com.deadlands.client.cutscene.CutscenePlayer;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a cutscene the camera goes where the script says, detached so the survivor's own body shows and the hand does not. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "setup", at = @At("TAIL"))
	private void deadlands$cutsceneCamera(Level level, Entity entity, boolean thirdPerson, boolean mirror, float partialTick, CallbackInfo ci) {
		if (!CutscenePlayer.active()) {
			return;
		}
		float[] rotation = CutscenePlayer.rotation(partialTick);
		setRotation(rotation[0], rotation[1]);
		setPosition(CutscenePlayer.position(partialTick));
		detached = true;
	}
}
