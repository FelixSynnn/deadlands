package com.deadlands.client.mixin;

import com.deadlands.client.sound.MusicDirector;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the DEADLANDS music director plays the world's soundtrack, Minecraft's own music manager
 * stays quiet, so the two never overlap. In the menus (and with dynamic music off) it runs as usual.
 */
@Mixin(MusicManager.class)
public class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void deadlands$quietWhileDirected(CallbackInfo ci) {
		if (com.deadlands.client.sound.MenuMusic.active()) {
			((net.minecraft.client.sounds.MusicManager) (Object) this).stopPlaying(); // the menu plays its own
			ci.cancel();
		} else if (MusicDirector.ownsMusic()) {
			ci.cancel();
		} else if (com.deadlands.client.sound.AsesinoMusic.playing() || com.deadlands.client.sound.GuestMusic.playing()
			|| com.deadlands.client.sound.DotMusic.playing() || com.deadlands.client.sound.MythosMusic.playing()) {
			// The Asesino's chase music: Minecraft's own music steps aside (stopped, then held off).
			((net.minecraft.client.sounds.MusicManager) (Object) this).stopPlaying();
			ci.cancel();
		}
	}
}
