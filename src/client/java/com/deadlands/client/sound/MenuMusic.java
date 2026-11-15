package com.deadlands.client.sound;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Music in the main menu, always (unless muted: {@code ui.menuMusic}, volume {@code ui.menuMusicVolume}).
 *
 * <p>The quiet tracks of the DEADLANDS soundtrack are shuffled like a deck: every track plays once before any
 * repeats, and a new deck never starts with the track that just ended. Each visit to the menu starts the next
 * track; when a track ends the next one fades in. A track fading out finishes before the next begins, so two
 * never overlap. Joining a world fades the menu music out (the world's own soundtrack takes over), and
 * Minecraft's own menu music stays silent ({@code MusicManagerMixin}).
 */
public final class MenuMusic {
	private static final List<String> TRACKS = List.of("music.title", "music.what_remains", "music.empty_roads", "music.static_lullaby",
		"music.hollow_hours", "music.overgrown", "music.long_night", "music.no_lights");
	private static final float FADE_IN = 1.0F / 60.0F;
	private static final float FADE_OUT = 1.0F / 40.0F;

	private static final Deque<String> DECK = new ArrayDeque<>();
	private static @Nullable String last;
	private static @Nullable Track playing;
	private static boolean inMenu;

	private MenuMusic() {
	}

	/** True while the menu music owns the music (Minecraft's own stays quiet). */
	public static boolean active() {
		return playing != null || inMenu && ConfigManager.get().ui.menuMusic;
	}

	public static void tick(Minecraft minecraft) {
		boolean menu = minecraft.level == null;
		boolean wanted = menu && ConfigManager.get().ui.menuMusic && ConfigManager.get().ui.menuMusicVolume > 0.0;
		if (menu && !inMenu && playing != null) {
			playing.fading = true; // a new visit to the menu: the next track
		}
		inMenu = menu;
		if (playing != null) {
			if (!wanted) {
				playing.fading = true;
			}
			playing.age++;
			playing.silent = minecraft.getSoundManager().isActive(playing) ? 0 : playing.silent + 1;
			// It ended or faded out (gone from the sound engine for a second, once it had time to start): the next may begin.
			if (playing.isStopped() || playing.age > 60 && playing.silent > 20) {
				minecraft.getSoundManager().stop(playing);
				playing = null;
			}
		}
		if (wanted && playing == null) {
			String next = draw();
			playing = new Track(SoundEvent.createVariableRangeEvent(Deadlands.id(next)));
			minecraft.getSoundManager().play(playing);
		}
	}

	/** The next track from the shuffled deck (a fresh deck never begins with the track just played). */
	private static String draw() {
		if (DECK.isEmpty()) {
			List<String> deck = new ArrayList<>(TRACKS);
			Collections.shuffle(deck);
			if (deck.getFirst().equals(last) && deck.size() > 1) {
				Collections.swap(deck, 0, deck.size() - 1);
			}
			DECK.addAll(deck);
		}
		last = DECK.poll();
		return last;
	}

	/** Leaving the game or reloading: stop at once. */
	public static void reset() {
		if (playing != null) {
			Minecraft.getInstance().getSoundManager().stop(playing);
			playing = null;
		}
	}

	private static final class Track extends AbstractTickableSoundInstance {
		float level;
		boolean fading;
		int age;
		int silent;

		Track(SoundEvent event) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = false;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
			this.volume = 0.0001F;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			level = fading ? Math.max(0.0F, level - FADE_OUT) : Math.min(1.0F, level + FADE_IN);
			if (fading && level <= 0.0F) {
				stop();
				return;
			}
			volume = Math.max(0.0001F, Mth.clamp(level * (float) ConfigManager.get().ui.menuMusicVolume, 0.0F, 1.0F));
		}
	}
}
