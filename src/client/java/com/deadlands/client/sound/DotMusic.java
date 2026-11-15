package com.deadlands.client.sound;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.DotEntity;
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
 * D.O.T.'s layered music (the owner's LL layers, and his chase theme as layer 4), by his own terror radius.
 * Whichever killer is nearest relative to its own radius plays (TerrorRadius).
 * All four layers start together as loops (so they stay in step)
 * the moment one is in range (or stalking this survivor, from any distance: layer 1); the highest layer that applies is heard and the others are silent, each
 * cross-fading over about a second (the four files are full mixes of rising intensity, so playing them
 * on top of each other only made them overlap): layer 1 within its terror radius ({@code audio.*TerrorRadius}),
 * layer 2 within half of it, layer 3 within a quarter, layer 4 while it
 * is chasing this survivor. With none in range (or the chase over and him gone) everything fades out over about
 * three seconds and stops. Meanwhile the DEADLANDS soundtrack is turned down and Minecraft's own music
 * steps aside ({@code MusicManagerMixin}).
 */
public final class DotMusic {
	private static final float FADE_IN = 1.0F / 20.0F;
	private static final float FADE_OUT_ALL = 1.0F / 60.0F;
	private static final float DUCKED = 0.0F; // the soundtrack goes silent under the chase music (no overlap)

	private static final Layer[] LAYERS = new Layer[4];
	private static boolean running;
	private static float master;
	private static float duck = 1.0F;
	/** Whether an Asesino is within {@link #QUIET_RANGE}: the world goes quiet (the ambience holds its breath). */
	private static boolean near;
	private static final double QUIET_RANGE = 96.0;

	private DotMusic() {
	}

	/** True while the layers are playing (Minecraft's music stays off). */
	public static boolean playing() {
		return running;
	}

	/** Whether an Asesino is close enough that the world goes quiet. */
	public static boolean near() {
		return near;
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
			// Its own terror radius decides the layer; whichever killer is nearer in terror plays (TerrorRadius).
			if (TerrorRadius.winner(minecraft, player) == TerrorRadius.DOT) {
				int layer = TerrorRadius.layer(TerrorRadius.dot(minecraft, player));
				if (layer >= 0) {
					want[layer] = true;
				}
			}
		}
		int top = -1;
		for (int i = 0; i < want.length; i++) {
			if (want[i]) {
				top = i;
			}
		}
		boolean any = top >= 0;
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
				layer.level = i == top ? Math.min(1.0F, layer.level + FADE_IN) : Math.max(0.0F, layer.level - FADE_IN);
			}
		}
		if (!any && master <= 0.0F || player == null) {
			stop(minecraft);
		}
	}

	private static @Nullable DotEntity nearest(Minecraft minecraft, LocalPlayer player, double range) {
		DotEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (DotEntity asesino : minecraft.level.getEntitiesOfClass(DotEntity.class, player.getBoundingBox().inflate(Math.max(range, 16.0)),
			DotEntity::isAlive)) {
			double distance = asesino.distanceToSqr(player);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = asesino;
			}
		}
		// Chasing from beyond layer 1's range still counts (layer 4).
		if (best == null) {
			for (DotEntity asesino : minecraft.level.getEntitiesOfClass(DotEntity.class, player.getBoundingBox().inflate(160.0),
				a -> a.isAlive() && (a.preyId() == player.getId()))) {
				return asesino;
			}
		}
		return best;
	}

	private static void start(Minecraft minecraft) {
		running = true;
		master = 0.0F;
		for (int i = 0; i < LAYERS.length; i++) {
			LAYERS[i] = new Layer(DLSounds.DOT_MUSIC.get(i).value());
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
		near = false;
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
