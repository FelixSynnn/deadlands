package com.deadlands.client.sound;

import com.deadlands.entity.InfectedEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One infected voice that moves with its infected, and stops if the infected is gone. */
public class InfectedVoiceSound extends AbstractTickableSoundInstance {
	private final InfectedEntity entity;

	public InfectedVoiceSound(SoundEvent sound, InfectedEntity entity, float volume, float pitch) {
		super(sound, SoundSource.HOSTILE, RandomSource.create(entity.getId() * 31L + entity.tickCount));
		this.entity = entity;
		this.volume = volume;
		this.pitch = pitch;
		follow();
	}

	@Override
	public void tick() {
		if (entity.isRemoved()) {
			stop();
			return;
		}
		follow();
	}

	private void follow() {
		this.x = entity.getX();
		this.y = entity.getEyeY();
		this.z = entity.getZ();
	}
}
