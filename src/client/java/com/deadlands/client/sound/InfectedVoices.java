package com.deadlands.client.sound;

import com.deadlands.bridge.ClientBridge;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.registry.DLSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * What each survivor hears of the infected around them, decided on their own client:
 *
 * <ul>
 *   <li>within 20 blocks, with a clear line, the kind's own voice;</li>
 *   <li>behind walls, a muffled, low version of it;</li>
 *   <li>farther away (to about 40 blocks), a thin distant moan;</li>
 *   <li>within a few blocks, breathing (stalkers breathe too, though they never moan).</li>
 * </ul>
 * Voices are occasional, never a loop: each infected speaks on its own timer, and no more than
 * six voices play at once. With {@code audio.infectedVoiceEffects} off, every infected uses its own
 * voice at any distance. Also adds the charred's smoke.
 */
public final class InfectedVoices implements ClientBridge.InfectedHandler {
	private static final int MAX_VOICES = 6;
	private static final double CLEAR_RANGE = 20.0;
	private static final double DISTANT_RANGE = 40.0;
	private static final double BREATH_RANGE = 4.0;
	private static final int BREATH_CHECK_TICKS = 40;

	private final List<SoundInstance> playing = new ArrayList<>();

	@Override
	public void ambient(InfectedEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !entity.isAlive() || !minecraft.gameRenderer.getMainCamera().isInitialized()) {
			return;
		}
		if (!room(minecraft.getSoundManager())) {
			return;
		}
		Vec3 ear = minecraft.gameRenderer.getMainCamera().position();
		double distance = ear.distanceTo(entity.getEyePosition());
		InfectedKind kind = entity.kind();
		SoundEvent sound = kind.ambientSound().value();
		// A volume above 1 does not play louder, it carries farther (16 blocks per unit): the plain
		// voice must still be heard at CLEAR_RANGE, where the distant one takes over.
		float volume = kind == InfectedKind.BRUTE ? 2.0F : 1.5F;
		float pitch = voicePitch(entity);
		if (ConfigManager.get().audio.infectedVoiceEffects) {
			if (distance > DISTANT_RANGE) {
				return;
			}
			if (distance > CLEAR_RANGE) {
				sound = DLSounds.INFECTED_DISTANT.value();
				volume = 2.6F; // carries to about 42 blocks
				pitch *= kind == InfectedKind.BRUTE ? 0.8F : 1.0F;
			} else if (occluded(minecraft.level, ear, entity)) {
				sound = DLSounds.INFECTED_MUFFLED.value();
				volume = 1.25F;
			}
		}
		play(minecraft.getSoundManager(), new InfectedVoiceSound(sound, entity, volume, pitch));
	}

	@Override
	public void tick(InfectedEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !entity.isAlive()) {
			return;
		}
		RandomSource random = entity.getRandom();
		if (entity.kind() == InfectedKind.CHARRED && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SMOKE, entity.getRandomX(0.4), entity.getRandomY(), entity.getRandomZ(0.4), 0.0, 0.02, 0.0);
		}
		if ((entity.tickCount + entity.getId()) % BREATH_CHECK_TICKS != 0 || minecraft.player == null) {
			return;
		}
		if (entity.distanceTo(minecraft.player) < BREATH_RANGE && random.nextBoolean() && room(minecraft.getSoundManager())) {
			play(minecraft.getSoundManager(), new InfectedVoiceSound(DLSounds.INFECTED_BREATH.value(), entity, 0.5F, voicePitch(entity)));
		}
	}

	public void reset() {
		playing.clear();
	}

	private boolean room(SoundManager sounds) {
		playing.removeIf(sound -> !sounds.isActive(sound));
		return playing.size() < MAX_VOICES;
	}

	private void play(SoundManager sounds, SoundInstance sound) {
		sounds.play(sound);
		playing.add(sound);
	}

	/** Each infected keeps its own pitch: lower for the big ones, higher for the thin ones. */
	private static float voicePitch(InfectedEntity entity) {
		float own = 0.92F + (entity.getUUID().hashCode() & 15) / 100.0F;
		float byKind = switch (entity.kind()) {
			case BRUTE -> 0.8F;
			case SCREAMER, RUNNER -> 1.08F;
			case BLOATER -> 0.9F;
			default -> 1.0F;
		};
		return own * byKind / Math.max(0.85F, entity.heightScale());
	}

	/** Is there a solid block between the listener and the infected's head? */
	private static boolean occluded(ClientLevel level, Vec3 ear, InfectedEntity entity) {
		HitResult hit = level.clip(new ClipContext(ear, entity.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return hit.getType() != HitResult.Type.MISS;
	}
}
