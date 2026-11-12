package com.deadlands.client.sound;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.AsesinoEntity;
import com.deadlands.registry.DLSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Asesino's layered chase music. All four layers start together as loops (so they stay in step)
 * the moment one is in range, and each layer's volume fades in or out over about a second as the
 * nearest Asesino comes closer: layer 1 within {@code audio.asesinoLayer1} blocks (48), layer 2 within
 * {@code asesinoLayer2} (24), layer 3 within {@code asesinoLayer3} (12), layer 4 while he is chasing
 * this survivor. With none in range (or the chase over and him gone) everything fades out over about
 * three seconds and stops. Meanwhile the DEADLANDS soundtrack is turned down and Minecraft's own music
 * steps aside ({@code MusicManagerMixin}).
 */
public final class AsesinoMusic {
	private static final float FADE_IN = 1.0F / 20.0F;
	private static final float FADE_OUT_ALL = 1.0F / 60.0F;
	private static final float DUCKED = 0.2F;

	private static final Layer[] LAYERS = new Layer[4];
	private static boolean running;
	private static float master;
	private static float duck = 1.0F;

	private AsesinoMusic() {
	}

	/** True while the layers are playing (Minecraft's music stays off). */
	public static boolean playing() {
		return running;
	}

	/** How loud the DEADLANDS soundtrack may be, 0 to 1. */
	public static float duck() {
		return duck;
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		DeadlandsConfig.Audio config = ConfigManager.get().audio;
		boolean[] want = new boolean[4];
		if (player != null && minecraft.level != null && config.asesinoMusic && !player.isSpectator()) {
			AsesinoEntity nearest = nearest(minecraft, player, config.asesinoLayer1);
			if (nearest != null) {
				double distance = nearest.distanceTo(player);
				want[0] = distance <= config.asesinoLayer1;
				want[1] = distance <= config.asesinoLayer2;
				want[2] = distance <= config.asesinoLayer3;
				want[3] = nearest.chasingId() == player.getId();
			}
		}
		boolean any = want[0] || want[3];
		if (any && !running && player != null) {
			start(minecraft);
		}
		if (!running) {
			duck = Math.min(1.0F, duck + FADE_OUT_ALL);
			return;
		}
		master = any ? Math.min(1.0F, master + FADE_IN) : Math.max(0.0F, master - FADE_OUT_ALL);
		duck = 1.0F - (1.0F - DUCKED) * master;
		for (int i = 0; i < LAYERS.length; i++) {
			Layer layer = LAYERS[i];
			if (layer != null) {
				layer.level = want[i] ? Math.min(1.0F, layer.level + FADE_IN) : Math.max(0.0F, layer.level - FADE_IN);
			}
		}
		if (!any && master <= 0.0F || player == null) {
			stop(minecraft);
		}
	}

	private static @Nullable AsesinoEntity nearest(Minecraft minecraft, LocalPlayer player, double range) {
		AsesinoEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (AsesinoEntity asesino : minecraft.level.getEntitiesOfClass(AsesinoEntity.class, player.getBoundingBox().inflate(Math.max(range, 16.0)),
			AsesinoEntity::isAlive)) {
			double distance = asesino.distanceToSqr(player);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = asesino;
			}
		}
		// Chasing from beyond layer 1's range still counts (layer 4).
		if (best == null) {
			for (AsesinoEntity asesino : minecraft.level.getEntitiesOfClass(AsesinoEntity.class, player.getBoundingBox().inflate(96.0),
				a -> a.isAlive() && a.chasingId() == player.getId())) {
				return asesino;
			}
		}
		return best;
	}

	private static void start(Minecraft minecraft) {
		running = true;
		master = 0.0F;
		for (int i = 0; i < LAYERS.length; i++) {
			LAYERS[i] = new Layer(DLSounds.ASESINO_MUSIC.get(i).value());
			minecraft.getSoundManager().play(LAYERS[i]);
		}
	}

	private static void stop(Minecraft minecraft) {
		for (int i = 0; i < LAYERS.length; i++) {
			if (LAYERS[i] != null) {
				minecraft.getSoundManager().stop(LAYERS[i]);
				LAYERS[i] = null;
			}
		}
		running = false;
		master = 0.0F;
	}

	/** Forget everything (leaving a world). */
	public static void reset() {
		stop(Minecraft.getInstance());
		duck = 1.0F;
	}

	/** One looping layer; its volume follows {@link #level} times the master fade. */
	private static final class Layer extends AbstractTickableSoundInstance {
		float level;

		Layer(SoundEvent event) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
			this.volume = 0.0001F;
		}

		@Override
		public boolean canStartSilent() {
			return true; // all four start at once, silent, so they stay in step
		}

		@Override
		public void tick() {
			if (!running) {
				stop();
				return;
			}
			float live = (float) ConfigManager.get().audio.musicVolume;
			volume = Math.max(0.0001F, Mth.clamp(level * master * live, 0.0F, 1.0F));
		}
	}
}
