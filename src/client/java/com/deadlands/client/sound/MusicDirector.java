package com.deadlands.client.sound;

import com.deadlands.client.cutscene.CutscenePlayer;
import com.deadlands.config.ConfigManager;
import com.deadlands.registry.DLSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * All in-game music (Audio Rules). Minecraft's own game music never plays in a world
 * ({@code MinecraftMixin}); the menus keep the DEADLANDS theme. Silence is the default:
 *
 * <ul>
 *   <li><b>Chase music</b> only when the server says a crowd is really chasing this survivor
 *       ({@code ChaseTracker}: 8 or more for 2 seconds); it fades in over 2 seconds and out over
 *       {@value #CHASE_FADE_OUT} ticks once the crowd thins.</li>
 *   <li><b>Exploration music</b>: at most one piece every 10 to 15 minutes of active play (paused
 *       time does not count), never during a chase or a cutscene.</li>
 * </ul>
 * Both play on the Music source, so Minecraft's Music slider applies, scaled again by
 * {@code audio.musicVolume} (45% by default).
 */
public final class MusicDirector {
	private static final int FADE_IN = 40;
	private static final int CHASE_FADE_OUT = 100;
	private static final int EXPLORE_MIN = 12000;
	private static final int EXPLORE_MAX = 18000;
	private static final RandomSource RANDOM = RandomSource.create();

	private static boolean chased;
	private static FadingMusic chase;
	private static FadingMusic explore;
	private static int activeTicks;
	private static int nextExplore = -1;

	private MusicDirector() {
	}

	public static void onChase(boolean value) {
		chased = value;
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		if (chased && (chase == null || chase.isStopped())) {
			if (explore != null) {
				explore.fadeOut(FADE_IN);
			}
			chase = new FadingMusic(DLSounds.MUSIC_CHASE.value(), true);
			minecraft.getSoundManager().play(chase);
		} else if (!chased && chase != null && !chase.isStopped()) {
			chase.fadeOut(CHASE_FADE_OUT);
		}

		activeTicks++;
		if (nextExplore < 0) {
			nextExplore = EXPLORE_MIN + RANDOM.nextInt(EXPLORE_MAX - EXPLORE_MIN);
		}
		boolean quiet = (chase == null || chase.isStopped()) && (explore == null || explore.isStopped());
		if (activeTicks >= nextExplore && quiet && !CutscenePlayer.active()) {
			explore = new FadingMusic(DLSounds.MUSIC_EXPLORE.value(), false);
			minecraft.getSoundManager().play(explore);
			activeTicks = 0;
			nextExplore = EXPLORE_MIN + RANDOM.nextInt(EXPLORE_MAX - EXPLORE_MIN);
		}
	}

	public static void reset() {
		chased = false;
		if (chase != null) {
			chase.fadeOut(1);
		}
		if (explore != null) {
			explore.fadeOut(1);
		}
		chase = null;
		explore = null;
		activeTicks = 0;
		nextExplore = -1;
	}

	/** A music track that fades in, follows the music volume setting, and can be faded out. */
	static final class FadingMusic extends AbstractTickableSoundInstance {
		private int age;
		private int fadeOutTicks = -1;
		private int fadeOutLeft;

		FadingMusic(SoundEvent sound, boolean loop) {
			super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = loop;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.001F;
		}

		void fadeOut(int ticks) {
			if (fadeOutTicks < 0) {
				fadeOutTicks = Math.max(1, ticks);
				fadeOutLeft = fadeOutTicks;
			}
		}

		@Override
		public void tick() {
			age++;
			float target = (float) ConfigManager.get().audio.musicVolume;
			float level = Math.min(1.0F, age / (float) FADE_IN);
			if (fadeOutTicks > 0) {
				fadeOutLeft--;
				level *= Math.max(0.0F, fadeOutLeft / (float) fadeOutTicks);
				if (fadeOutLeft <= 0) {
					stop();
					return;
				}
			}
			this.volume = Math.max(0.001F, target * level * com.deadlands.client.horror.ScarePlayer.musicDuck());
		}
	}
}
