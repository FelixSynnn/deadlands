package com.deadlands.client.sound;

import com.deadlands.client.hud.HordeHud;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.BossEntity;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.npc.Faction;
import com.deadlands.npc.NpcEntity;
import com.deadlands.registry.DLSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The DEADLANDS soundtrack in the world. While {@code audio.dynamicMusic} is on it replaces
 * Minecraft's in-game music (the menus keep the title theme through Minecraft's music manager).
 *
 * <p>Moods: EXPLORE by day, NIGHT after dark or deep underground, SAFE in a settlement that
 * tolerates the survivor (the server says so) or in friendly company, DANGER when infected hunt
 * nearby, a boss is close or the survivor is being hurt, and HORDE on a horde night. Danger is a
 * score (hunting infected by kind and distance) that must hold for a moment before the music turns,
 * and must stay low for twelve seconds before it lets go, so one shambler does not start the drums
 * and a short lull does not stop them.
 *
 * <p>Tense moods cut in with a short fade and loop until they end with a long one; quiet moods play
 * one track, then leave a silence (one to three minutes; shorter with Minecraft's Music Frequency
 * at Frequent, almost none at Constant). Tracks are chosen at random but never one of the last
 * played for that mood. Apart from a crossfade only one track plays. Volume follows Minecraft's
 * Music slider and {@code audio.musicVolume}.
 */
public final class MusicDirector {
	public enum Mood {
		NONE, EXPLORE, NIGHT, SAFE, DANGER, HORDE;

		boolean tense() {
			return this == DANGER || this == HORDE;
		}
	}

	private static final RandomSource RANDOM = RandomSource.create();
	private static final int HANDOVER_TICKS = 80;
	private static final float DANGER_SCORE = 2.5F;
	private static final float CALM_SCORE = 1.0F;
	private static final int DANGER_ENTER_TICKS = 30;
	private static final int DANGER_LEAVE_TICKS = 240;
	/** A tense track hands over to another of its pool after this long, so a long fight is not one loop. */
	private static final int TENSE_ROTATE_TICKS = 20 * 150;

	private static boolean owning;
	private static int handover;
	private static Track current;
	private static final List<Track> fading = new ArrayList<>();
	private static Mood playing = Mood.NONE;
	private static int silence;
	private static int threatTicks;
	private static int calmTicks;
	private static boolean inDanger;
	private static float threat;
	private static boolean safeZone;
	private static Mood lastWanted = Mood.NONE;
	private static final Map<Mood, Deque<Holder.Reference<SoundEvent>>> RECENT = new EnumMap<>(Mood.class);

	private MusicDirector() {
	}

	/** Minecraft's own music manager stays quiet while this is true. */
	public static boolean ownsMusic() {
		return owning;
	}

	/** The mood of the track now playing (NONE in a silence). */
	public static Mood playing() {
		return current != null ? playing : Mood.NONE;
	}

	public static String currentTrack() {
		return current == null ? "" : String.valueOf(current.getIdentifier());
	}

	public static float threat() {
		return threat;
	}

	/** The mood the situation calls for right now (the music follows it, after its fades and silences). */
	public static Mood wanted() {
		return lastWanted;
	}

	public static void setSafeZone(boolean safe) {
		safeZone = safe;
	}

	public static void tick(Minecraft minecraft) {
		boolean enabled = ConfigManager.get().audio.dynamicMusic && minecraft.level != null && minecraft.player != null;
		if (!enabled) {
			if (owning) {
				release(minecraft);
			}
			return;
		}
		if (!owning) {
			take(minecraft);
		}
		fading.removeIf(track -> track.isStopped() || !minecraft.getSoundManager().isActive(track) && track.age > 40);
		if (handover > 0) {
			// Whatever Minecraft was playing (the title theme, carried into the world) fades out first.
			handover--;
			minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, handover / (float) HANDOVER_TICKS);
			if (handover == 0) {
				minecraft.getMusicManager().stopPlaying();
				minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 1.0F);
			}
			return;
		}
		if (minecraft.level.getGameTime() % 10L == 0L) {
			threat = threat(minecraft);
		}
		if (threat >= DANGER_SCORE) {
			threatTicks++;
			calmTicks = 0;
		} else {
			threatTicks = 0;
			calmTicks = threat < CALM_SCORE ? calmTicks + 1 : 0;
		}
		if (!inDanger && threatTicks >= DANGER_ENTER_TICKS) {
			inDanger = true;
		} else if (inDanger && calmTicks >= DANGER_LEAVE_TICKS) {
			inDanger = false;
		}

		if (current != null && current.age > 40 && !minecraft.getSoundManager().isActive(current)) {
			current = null; // a quiet track came to its end
			silence = gap(minecraft);
		}
		Mood wanted = wanted(minecraft);
		lastWanted = wanted;
		if (current != null && playing != wanted) {
			if (wanted.tense()) {
				fadeOut(current, 40);
			} else if (playing.tense()) {
				fadeOut(current, 160);
				silence = 200 + RANDOM.nextInt(400);
			} else {
				fadeOut(current, 200);
				silence = 100 + RANDOM.nextInt(200);
			}
			current = null;
		} else if (current != null && playing.tense() && current.age > TENSE_ROTATE_TICKS && pool(playing).size() > 1) {
			fadeOut(current, 100);
			current = start(minecraft, wanted, 100);
		}
		if (current == null) {
			if (wanted.tense()) {
				current = start(minecraft, wanted, 50);
			} else if (--silence <= 0) {
				current = start(minecraft, wanted, 120);
			}
		}
	}

	public static void reset() {
		Minecraft minecraft = Minecraft.getInstance();
		if (current != null) {
			minecraft.getSoundManager().stop(current);
		}
		for (Track track : fading) {
			minecraft.getSoundManager().stop(track);
		}
		if (handover > 0) {
			minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 1.0F);
		}
		current = null;
		fading.clear();
		playing = Mood.NONE;
		owning = false;
		handover = 0;
		inDanger = false;
		threat = 0.0F;
		threatTicks = 0;
		calmTicks = 0;
		safeZone = false;
	}

	private static void take(Minecraft minecraft) {
		owning = true;
		handover = HANDOVER_TICKS;
		silence = 300 + RANDOM.nextInt(300);
	}

	private static void release(Minecraft minecraft) {
		if (current != null) {
			fadeOut(current, 40);
			current = null;
		}
		if (handover > 0) {
			minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 1.0F);
			handover = 0;
		}
		playing = Mood.NONE;
		owning = false;
	}

	private static Mood wanted(Minecraft minecraft) {
		if (HordeHud.isActive() && minecraft.level.dimension() == Level.OVERWORLD) {
			return Mood.HORDE;
		}
		if (inDanger) {
			return Mood.DANGER;
		}
		if (safeZone || friendlyCompany(minecraft)) {
			return Mood.SAFE;
		}
		long time = Math.floorMod(minecraft.level.getDayTime(), 24000L);
		boolean night = time >= 13000L && time < 23000L;
		LocalPlayer player = minecraft.player;
		boolean deep = player.getY() < 40.0 && !minecraft.level.canSeeSky(player.blockPosition());
		return night || deep ? Mood.NIGHT : Mood.EXPLORE;
	}

	/** How dangerous the next few seconds look: infected hunting nearby (by kind and distance), bosses, being hurt. */
	private static float threat(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player.isCreative() || player.isSpectator()) {
			return 0.0F;
		}
		float score = 0.0F;
		for (InfectedEntity infected : minecraft.level.getEntitiesOfClass(InfectedEntity.class, player.getBoundingBox().inflate(28.0), Entity::isAlive)) {
			double distance = Math.sqrt(infected.distanceToSqr(player));
			float weight = switch (infected.kind()) {
				case BRUTE -> 2.5F;
				case RUNNER, STALKER -> 1.8F;
				case MILITARY, CHARRED -> 1.3F;
				default -> 1.0F;
			};
			if (infected.hasFlag(InfectedEntity.FLAG_HUNTING) && distance < 28.0) {
				score += weight * (distance < 10.0 ? 1.6F : 1.0F);
			} else if (distance < 8.0) {
				score += 0.35F * weight;
			}
		}
		if (!minecraft.level.getEntitiesOfClass(BossEntity.class, player.getBoundingBox().inflate(64.0), Entity::isAlive).isEmpty()) {
			score += 6.0F;
		}
		if (player.hurtTime > 0 && score > 0.0F) {
			score += 1.5F;
		}
		return score;
	}

	/** Two or more survivors close by who are not bandits: a camp, a caravan, a settlement. */
	private static boolean friendlyCompany(Minecraft minecraft) {
		int friends = 0;
		for (NpcEntity npc : minecraft.level.getEntitiesOfClass(NpcEntity.class, minecraft.player.getBoundingBox().inflate(20.0), Entity::isAlive)) {
			if (npc.faction() != Faction.BANDITS && ++friends >= 2) {
				return true;
			}
		}
		return false;
	}

	private static List<Holder.Reference<SoundEvent>> pool(Mood mood) {
		return switch (mood) {
			case NIGHT -> DLSounds.MUSIC_NIGHT;
			case SAFE -> DLSounds.MUSIC_SAFE;
			case DANGER -> DLSounds.MUSIC_DANGER;
			case HORDE -> {
				List<Holder.Reference<SoundEvent>> horde = new ArrayList<>(DLSounds.MUSIC_HORDE);
				horde.addAll(DLSounds.MUSIC_DANGER);
				yield horde;
			}
			default -> DLSounds.MUSIC_EXPLORE;
		};
	}

	private static Track start(Minecraft minecraft, Mood mood, int fadeIn) {
		List<Holder.Reference<SoundEvent>> pool = pool(mood);
		Deque<Holder.Reference<SoundEvent>> recent = RECENT.computeIfAbsent(mood, m -> new ArrayDeque<>());
		List<Holder.Reference<SoundEvent>> choices = new ArrayList<>(pool);
		choices.removeAll(recent);
		if (choices.isEmpty()) {
			choices = new ArrayList<>(pool);
		}
		// HORDE prefers the horde score itself whenever it was not the last one played.
		Holder.Reference<SoundEvent> pick = mood == Mood.HORDE && choices.contains(DLSounds.MUSIC_HORDE.getFirst())
			? DLSounds.MUSIC_HORDE.getFirst() : choices.get(RANDOM.nextInt(choices.size()));
		recent.addLast(pick);
		while (recent.size() > Math.min(2, pool.size() - 1)) {
			recent.removeFirst();
		}
		Track track = new Track(pick.value(), mood.tense(), mood.tense() ? 0.9F : 0.75F, fadeIn);
		minecraft.getSoundManager().play(track);
		playing = mood;
		return track;
	}

	private static void fadeOut(Track track, int ticks) {
		track.fadeOut(ticks);
		fading.add(track);
	}

	/** The silence after a quiet track, by Minecraft's Music Frequency option. */
	private static int gap(Minecraft minecraft) {
		MusicManager.MusicFrequency frequency = minecraft.options.musicFrequency().get();
		return switch (frequency) {
			case CONSTANT -> 40 + RANDOM.nextInt(60);
			case FREQUENT -> 400 + RANDOM.nextInt(1000);
			default -> 1200 + RANDOM.nextInt(2400);
		};
	}

	/** A streamed track whose volume follows a fade and the DEADLANDS music volume. */
	static final class Track extends AbstractTickableSoundInstance {
		private final float target;
		private final int fadeIn;
		private float level;
		private int fadeOut;
		int age;

		Track(SoundEvent event, boolean loop, float target, int fadeIn) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = loop;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
			this.target = target;
			this.fadeIn = Math.max(1, fadeIn);
			this.volume = 0.0001F;
		}

		@Override
		public boolean canStartSilent() {
			return true; // with the Music slider at zero it still "plays", so the director does not keep restarting it
		}

		void fadeOut(int ticks) {
			if (fadeOut == 0) {
				fadeOut = Math.max(1, ticks);
			}
		}

		@Override
		public void tick() {
			age++;
			if (fadeOut > 0) {
				level -= 1.0F / fadeOut;
				if (level <= 0.0F) {
					level = 0.0F;
					stop();
					return;
				}
			} else if (level < 1.0F) {
				level = Math.min(1.0F, level + 1.0F / fadeIn);
			}
			float live = target * (float) ConfigManager.get().audio.musicVolume;
			volume = Math.max(0.0001F, live * level * level);
		}
	}
}
