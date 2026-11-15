package com.deadlands.client.sound;

import com.deadlands.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Quiet wind and far-off crows under the DEADLANDS menus ({@code audio.menuAmbience}), beneath the
 * title theme. It fades in with the menu and out as a world loads, and follows the Ambient slider.
 */
public final class MenuAmbience {
	private static Bed bed;

	private MenuAmbience() {
	}

	public static void tick(Minecraft minecraft) {
		boolean wanted = minecraft.level == null && minecraft.screen != null && ConfigManager.get().audio.menuAmbience
			&& ConfigManager.get().ui.menuBackdrop;
		float target = wanted ? 0.3F * (float) ConfigManager.get().audio.ambienceVolume : 0.0F; // quieter (2026-11-11)
		if (bed != null && (bed.isStopped() || !minecraft.getSoundManager().isActive(bed) && bed.age > 40)) {
			bed = null;
		}
		if (bed == null && target > 0.02F) {
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(com.deadlands.Deadlands.id("ambient.menu_loop"));
			if (sound != null) {
				bed = new Bed(sound);
				minecraft.getSoundManager().play(bed);
			}
		}
		if (bed != null) {
			bed.target = target;
		}
	}

	/** Whether the menu bed is playing (for the client test). */
	public static boolean playing() {
		return bed != null && !bed.isStopped();
	}

	private static final class Bed extends AbstractTickableSoundInstance {
		float target;
		int age;

		Bed(SoundEvent sound) {
			super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
			this.volume = 0.01F;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			age++;
			volume += (target - volume) * 0.02F;
			if (target <= 0.0F && volume < 0.01F) {
				stop();
			}
		}
	}
}
