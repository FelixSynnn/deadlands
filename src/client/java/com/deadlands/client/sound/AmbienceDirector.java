package com.deadlands.client.sound;

import com.deadlands.Deadlands;
import com.deadlands.audio.LocationTracker;
import com.deadlands.client.cutscene.CutscenePlayer;
import com.deadlands.client.horror.ScarePlayer;
import com.deadlands.client.weather.ClientWeather;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.LocationPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world's soundscape, decided on each client from where the survivor is (the server's
 * {@link LocationTracker}), the time of day, the weather and whether there is a roof overhead.
 *
 * <p><b>Mostly quiet</b> (Hard Rule 5; 2026-11-11: "the ambiance is too loud, make it not always
 * play"). The soundscape alternates between <i>spells of sound</i> (one and a half to four minutes)
 * and <i>hushes</i> (30 to 90 seconds since 2026-11-12) in which the calm beds fade out
 * and no everyday sound plays. Weather is never hushed: rain on the roof and a real storm wind go on.
 * Everything is about half as loud as before, everyday one-shots are at least six seconds apart, and
 * at most two play at once.
 *
 * <p><b>Scary sounds, sometimes.</b> Every one and a half to three and a half minutes after dark (or
 * underground), and every four to eight by day, the world first goes quiet for a few seconds and then lets one eerie
 * sound through: the far call of something at night, distant infected, an unexplained noise, a low
 * dread swell, or water creaking in the pipes indoors. It waits while a scare from the Horror
 * Director is recent (two minutes), during a chase and in cutscenes, so the two never pile up.
 *
 * <p>Each kind of place has a <b>profile</b>: layers of occasional sounds, each with its own spacing,
 * distances, height and conditions (day or night, dry or wet, a storm). A city has wind between
 * buildings, crows on the rooftops, creaking signs and loose metal; a forest has leaves, creaking
 * boughs, crows and far birds; a hospital has buzzing lights, drips and wind through broken windows.
 * Every layer keeps its own clock and the same sound never plays twice running.
 *
 * <p>One-shots are placed around the listener with no distance roll-off of their own: their loudness
 * is worked out here from their distance (Minecraft's linear roll-off silences anything placed past
 * sixteen blocks). Outdoor sounds heard from indoors are quieter and duller. Beds: the biome's own
 * loop (which Minecraft no longer plays; see {@code gen_world.py}), wind that follows the weather and
 * height, rain on the roof, the hush of a room and the hum of a powered town. Everything is scaled by
 * {@code audio.ambienceVolume}, and {@code audio.ambienceDensity} sets how often one-shots come.
 */
public final class AmbienceDirector {
	private static final int DAY = 1;
	private static final int NIGHT = 2;
	private static final int DRY = 4;
	private static final int WET = 8;
	private static final int STORM = 16;
	private static final int ALWAYS = DAY | NIGHT | DRY | WET;
	private static final int DAY_DRY = DAY | DRY;
	private static final int DAY_ANY = DAY | DRY | WET;
	private static final int ANY_DRY = DAY | NIGHT | DRY;

	/** Overall loudness: about half of what the soundscape had (2026-11-11). */
	private static final float ONE_SHOT_GAIN = 0.5F;
	private static final float BED_GAIN = 0.45F;
	/** At least ten seconds between two everyday one-shots, and at most two at once. */
	private static final int MIN_GAP = 120;
	private static final int MAX_PLAYING = 2;

	/** Where the listener is, as far as the soundscape cares. */
	public enum Profile {
		OPEN, FOREST, SUBURB, CITY, HIGHWAY, INTERIOR, HOSPITAL, SCHOOL, INDUSTRIAL, FARM, CAMPSITE, BUNKER, UNDERGROUND
	}

	/**
	 * One kind of occasional sound: gaps in seconds, distances in blocks, lift above the ear, loudness
	 * at 0 blocks, when it may play, and whether it is an outdoor sound (muffled indoors).
	 */
	private record Layer(String sound, int minGap, int maxGap, float near, float far, float lift, float loud, int when, boolean outdoor) {
		boolean allowed(int now) {
			return (when & now & (DAY | NIGHT)) != 0 && (when & now & (DRY | WET)) != 0 && ((when & STORM) == 0 || (now & STORM) != 0);
		}
	}

	/** A scary sound let through now and then: distance in blocks (0 = in the head, unplaced), loudness, weight. */
	private record Eerie(String sound, float near, float far, float loud, int weight) {
	}

	private static final Map<Profile, List<Layer>> PROFILES = new EnumMap<>(Profile.class);

	/** The bed each DEADLANDS biome used to loop through Minecraft ({@code gen_world.py} BIOMES "loop"). */
	private static final Map<String, String> BIOME_BEDS = Map.of(
		"dead_forest", "ambient.dead_forest.loop",
		"overgrown_suburbs", "ambient.suburbs.loop",
		"abandoned_farmland", "ambient.fields.loop",
		"rural_countryside", "ambient.fields.loop",
		"burned_forest", "ambient.burned.loop",
		"dead_pine_valley", "ambient.pine.loop",
		"dry_wasteland", "ambient.dry_wind.loop",
		"flooded_marsh", "ambient.marsh.loop",
		"frozen_outskirts", "ambient.frozen.loop",
		"industrial_wasteland", "ambient.industrial.loop");
	private static final Set<String> BED_NAMES = new LinkedHashSet<>(BIOME_BEDS.values());

	// The "alien" call at night is the one the project owner chose for the eerie effect, so it comes most often.
	private static final List<Eerie> EERIE_NIGHT = List.of(
		new Eerie("horror.night_creature", 60, 120, 0.42F, 3),
		new Eerie("infected.distant", 50, 100, 0.38F, 2),
		new Eerie("ambient.unexplained", 45, 90, 0.38F, 2),
		new Eerie("horror.dread", 0, 0, 0.16F, 1));
	private static final List<Eerie> EERIE_DAY = List.of(
		new Eerie("ambient.unexplained", 60, 110, 0.32F, 2),
		new Eerie("infected.distant", 70, 120, 0.28F, 2),
		new Eerie("horror.dread", 0, 0, 0.12F, 1));
	private static final List<Eerie> EERIE_INSIDE = List.of(
		new Eerie("horror.water_creak", 8, 20, 0.3F, 2),
		new Eerie("ambient.unexplained", 12, 35, 0.34F, 2),
		new Eerie("horror.dread", 0, 0, 0.16F, 1));

	static {
		Layer crow = new Layer("ambient.crow", 40, 110, 30, 70, 10, 1.0F, ANY_DRY, true);
		Layer flock = new Layer("ambient.crow_flock", 180, 420, 40, 80, 14, 1.0F, DAY_ANY, true);
		Layer birds = new Layer("ambient.birds_far", 35, 90, 25, 60, 5, 0.7F, DAY_DRY, true);
		Layer thunder = new Layer("ambient.thunder_far", 25, 70, 150, 300, 30, 1.0F, ALWAYS | STORM, true);
		Layer dog = new Layer("ambient.dog", 150, 360, 60, 120, 0, 0.8F, ALWAYS, true);
		Layer crickets = new Layer("ambient.crickets", 40, 100, 6, 14, 0, 0.45F, NIGHT | DRY, true);
		Layer owl = new Layer("ambient.owl", 110, 240, 20, 50, 6, 0.6F, NIGHT | DRY, true);
		Layer gust = new Layer("ambient.gust", 45, 120, 10, 30, 2, 0.6F, ALWAYS, true);
		Layer leaves = new Layer("ambient.leaves", 20, 55, 6, 20, 3, 0.55F, ALWAYS, true);
		Layer branch = new Layer("ambient.branch", 40, 110, 8, 25, 5, 0.6F, ALWAYS, true);
		Layer rustle = new Layer("ambient.rustle", 30, 80, 4, 12, 0, 0.5F, ALWAYS, true);
		Layer woodpecker = new Layer("ambient.woodpecker", 90, 220, 20, 40, 5, 0.5F, DAY_DRY, true);
		Layer sign = new Layer("ambient.sign_creak", 45, 130, 12, 30, 4, 0.7F, ALWAYS, true);
		Layer rattle = new Layer("ambient.rattle", 55, 150, 10, 28, 2, 0.6F, ALWAYS, true);
		Layer metal = new Layer("ambient.metal", 60, 160, 25, 60, 3, 0.8F, ALWAYS, true);
		Layer buzz = new Layer("ambient.buzz", 75, 190, 8, 20, 4, 0.45F, ALWAYS, true);
		Layer glass = new Layer("ambient.glass", 90, 240, 15, 35, 4, 0.55F, ALWAYS, true);
		Layer alarm = new Layer("ambient.car_alarm", 450, 900, 60, 120, 0, 0.5F, ALWAYS, true);
		Layer radio = new Layer("ambient.radio", 300, 600, 15, 35, 3, 0.4F, ALWAYS, true);
		Layer door = new Layer("ambient.door_bang", 110, 270, 15, 40, 0, 0.6F, ALWAYS, true);
		Layer creak = new Layer("ambient.creak", 60, 150, 8, 25, 2, 0.55F, ALWAYS, true);
		Layer chime = new Layer("ambient.chime", 70, 170, 10, 25, 3, 0.45F, ALWAYS, true);
		// Indoor sounds (never muffled).
		Layer settle = new Layer("ambient.settle", 50, 130, 4, 12, 2, 0.5F, ALWAYS, false);
		Layer window = new Layer("ambient.window_wind", 60, 150, 5, 14, 1, 0.5F, ALWAYS, false);
		Layer drip = new Layer("ambient.drip", 30, 75, 3, 8, 1, 0.45F, ALWAYS, false);
		Layer roomRattle = new Layer("ambient.rattle", 90, 220, 4, 12, 1, 0.4F, ALWAYS, false);
		Layer flicker = new Layer("ambient.flicker", 40, 90, 4, 10, 2, 0.45F, ALWAYS, false);
		Layer gurney = new Layer("ambient.gurney", 130, 300, 8, 20, 0, 0.4F, ALWAYS, false);
		Layer indoorDoor = new Layer("ambient.door_bang", 120, 270, 10, 30, 0, 0.45F, ALWAYS, false);
		Layer paper = new Layer("ambient.paper", 45, 120, 3, 9, 0, 0.4F, ALWAYS, false);
		Layer locker = new Layer("ambient.locker", 90, 240, 6, 18, 0, 0.45F, ALWAYS, false);
		Layer steam = new Layer("ambient.steam", 45, 120, 8, 20, 2, 0.5F, ALWAYS, false);
		Layer groan = new Layer("ambient.groan", 70, 180, 10, 30, 0, 0.55F, ALWAYS, false);
		Layer vent = new Layer("ambient.vent", 45, 120, 4, 12, 2, 0.45F, ALWAYS, false);
		Layer indoorBuzz = new Layer("ambient.buzz", 60, 150, 3, 8, 2, 0.4F, ALWAYS, false);
		Layer tarp = new Layer("ambient.tarp", 30, 75, 4, 10, 1, 0.5F, ALWAYS, true);
		Layer ember = new Layer("ambient.ember", 25, 60, 2, 6, 0, 0.4F, ALWAYS, true);

		// The distant infected and the unexplained noises are no longer everyday layers: they are the
		// eerie sounds below, let through only now and then.
		PROFILES.put(Profile.OPEN, List.of(gust, crow, flock, birds, thunder, dog, crickets, owl));
		PROFILES.put(Profile.FOREST, List.of(leaves, branch, rustle, crow, flock, birds, woodpecker, owl, crickets, thunder,
			new Layer("ambient.dog", 300, 600, 70, 130, 0, 0.6F, ALWAYS, true)));
		PROFILES.put(Profile.SUBURB, List.of(crow, flock, dog, chime, sign, door, creak, birds, thunder,
			new Layer("ambient.car_alarm", 600, 1200, 70, 130, 0, 0.45F, ALWAYS, true)));
		PROFILES.put(Profile.CITY, List.of(new Layer("ambient.crow", 45, 120, 25, 60, 16, 1.0F, ANY_DRY, true),
			new Layer("ambient.crow_flock", 180, 390, 35, 70, 20, 1.0F, DAY_ANY, true), sign, rattle, metal, buzz, glass, alarm, radio,
			new Layer("ambient.dog", 150, 360, 60, 120, 0, 0.7F, ALWAYS, true), door, thunder));
		PROFILES.put(Profile.HIGHWAY, List.of(new Layer("ambient.sign_creak", 45, 110, 10, 30, 5, 0.75F, ALWAYS, true),
			new Layer("ambient.rattle", 60, 140, 10, 30, 2, 0.6F, ALWAYS, true), new Layer("ambient.crow", 60, 140, 30, 70, 12, 1.0F, ANY_DRY, true),
			new Layer("ambient.crow_flock", 300, 600, 50, 90, 14, 1.0F, DAY_ANY, true), metal,
			new Layer("ambient.buzz", 130, 300, 10, 25, 8, 0.4F, ALWAYS, true), new Layer("ambient.birds_far", 60, 140, 30, 70, 5, 0.6F, DAY_DRY, true),
			new Layer("ambient.dog", 220, 450, 70, 130, 0, 0.6F, ALWAYS, true), gust, thunder));
		PROFILES.put(Profile.INTERIOR, List.of(settle, window, creak, roomRattle, drip, new Layer("ambient.crow", 90, 210, 25, 60, 8, 0.8F, ANY_DRY, true),
			new Layer("ambient.dog", 150, 360, 60, 120, 0, 0.6F, ALWAYS, true), thunder));
		PROFILES.put(Profile.HOSPITAL, List.of(flicker, drip, gurney, indoorDoor, window, roomRattle, settle, indoorBuzz, thunder));
		PROFILES.put(Profile.SCHOOL, List.of(paper, locker, indoorDoor, creak, window, settle, thunder));
		PROFILES.put(Profile.INDUSTRIAL, List.of(steam, groan, metal, radio, rattle, buzz, sign, crow, thunder));
		PROFILES.put(Profile.FARM, List.of(new Layer("ambient.crow", 30, 90, 25, 60, 6, 1.0F, ANY_DRY, true),
			new Layer("ambient.crow_flock", 140, 360, 30, 70, 8, 1.0F, DAY_ANY, true), creak, gust, rustle, birds, dog, crickets, owl, thunder));
		PROFILES.put(Profile.CAMPSITE, List.of(tarp, ember, crow, rustle, branch, owl, crickets, birds, thunder));
		PROFILES.put(Profile.BUNKER, List.of(vent, drip, groan, indoorBuzz, settle));
		PROFILES.put(Profile.UNDERGROUND, List.of(drip, new Layer("ambient.groan", 90, 240, 10, 30, 0, 0.4F, ALWAYS, false), settle));
	}

	private static LocationTracker.Place place = LocationTracker.Place.OPEN;
	private static boolean powered;
	private static Profile profile = Profile.OPEN;
	private static final Map<String, Integer> CLOCKS = new HashMap<>();
	private static final List<SoundInstance> PLAYING = new ArrayList<>();
	private static final Map<String, Loop> LOOPS = new HashMap<>();
	private static int globalGap = 100;
	private static int surveyIn;
	private static boolean indoors;
	private static boolean onRoad;
	private static boolean metalRoof;
	private static boolean roofed;
	private static String last = "";
	private static float crowBearing = 0.0F;
	private static int crowBearingFor;
	private static double lastDensity = -1.0;

	// Spells of sound and hushes.
	private static boolean hush = true;
	private static int spellLeft = 600;
	/** 0 to 1: how far the calm beds are faded in (ten-second fades). */
	private static float bedLevel;

	// Eerie moments.
	private static int eerieIn = 1800;
	private static int eerieAt = -1;
	private static Eerie eerie;
	private static String lastEerie = "";

	private AmbienceDirector() {
	}

	public static void set(LocationPayload payload) {
		place = LocationTracker.Place.byIndex(payload.place());
		powered = payload.powered();
		MusicDirector.setSafeZone(payload.safe());
	}

	public static Profile profile() {
		return profile;
	}

	/** Whether the soundscape is in a hush (for the debug overlay and tests). */
	public static boolean hushed() {
		return hush;
	}

	public static void reset() {
		place = LocationTracker.Place.OPEN;
		powered = false;
		profile = Profile.OPEN;
		CLOCKS.clear();
		PLAYING.clear();
		for (Loop loop : LOOPS.values()) {
			loop.done = true;
		}
		LOOPS.clear();
		globalGap = 100;
		last = "";
		hush = true;
		spellLeft = 600;
		bedLevel = 0.0F;
		eerieIn = 1800;
		eerieAt = -1;
		eerie = null;
		lastEerie = "";
	}

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || minecraft.isPaused()) {
			return;
		}
		float volume = (float) ConfigManager.get().audio.ambienceVolume;
		if (--surveyIn <= 0) {
			surveyIn = 10;
			survey(level, player);
		}
		RandomSource random = level.getRandom();
		int now = conditions(level, player);
		spells(random, now);
		loops(minecraft, level, player, volume);
		// A scare is holding its breath: no new ambience until it lets go.
		if (ScarePlayer.silenced()) {
			return;
		}
		// The Asesino is near: the world goes quiet (the calm beds fade, no new sounds).
		if (AsesinoMusic.near()) {
			hush = true;
			spellLeft = Math.max(spellLeft, 400);
			return;
		}
		if (volume <= 0.0F) {
			return;
		}
		eerie(minecraft, player, random, now, volume);
		double density = ConfigManager.get().audio.ambienceDensity;
		if (density != lastDensity) {
			CLOCKS.clear(); // a new density takes effect now, not after the old gaps run out
			lastDensity = density;
		}
		PLAYING.removeIf(sound -> !minecraft.getSoundManager().isActive(sound));
		if (globalGap > 0) {
			globalGap--;
		}
		if (--crowBearingFor <= 0) {
			crowBearing = random.nextFloat() * Mth.TWO_PI;
			crowBearingFor = 2400 + random.nextInt(3600);
		}
		for (Layer layer : PROFILES.getOrDefault(profile, List.of())) {
			String key = profile.name() + "/" + layer.sound();
			Integer clock = CLOCKS.get(key);
			if (clock == null) {
				// A layer that has just become possible waits part of its gap first.
				CLOCKS.put(key, (int) ((layer.minGap() * 20 / 3 + random.nextInt(Math.max(1, layer.maxGap() * 20))) / density));
				continue;
			}
			if (clock > 0) {
				CLOCKS.put(key, clock - 1);
				continue;
			}
			if (hush) {
				CLOCKS.put(key, 40 + random.nextInt(60));
				continue;
			}
			if (!layer.allowed(now)) {
				CLOCKS.put(key, 100 + random.nextInt(200));
				continue;
			}
			if (globalGap > 0 || PLAYING.size() >= MAX_PLAYING || layer.sound().equals(last)) {
				CLOCKS.put(key, 20 + random.nextInt(40));
				continue;
			}
			if (play(minecraft, player, layer, random, volume)) {
				last = layer.sound();
				globalGap = MIN_GAP + random.nextInt(200);
			}
			int span = Math.max(1, layer.maxGap() - layer.minGap());
			CLOCKS.put(key, (int) ((layer.minGap() + random.nextInt(span)) * 20 * 0.7 / density)); // a third sooner (2026-11-12)
		}
	}

	/** Alternates spells of sound with hushes, and fades the calm beds to match. */
	private static void spells(RandomSource random, int now) {
		if (--spellLeft <= 0) {
			hush = !hush;
			boolean night = (now & NIGHT) != 0;
			if (hush) {
				spellLeft = 600 + random.nextInt(1200); // 30 to 90 seconds (2026-11-12: "play more often")
			} else {
				spellLeft = (night ? 1800 : 2400) + random.nextInt(2400); // 1.5 to 3.5 min at night, 2 to 4 min by day
				globalGap = 100 + random.nextInt(200);
			}
		}
		bedLevel = Mth.approach(bedLevel, hush ? 0.0F : 1.0F, 1.0F / 200.0F);
	}

	/** Now and then, after a moment of quiet, one scary sound. */
	private static void eerie(Minecraft minecraft, LocalPlayer player, RandomSource random, int now, float volume) {
		if (eerieAt >= 0) {
			if (--eerieAt < 0 && eerie != null) {
				playEerie(minecraft, player, eerie, random, volume);
				lastEerie = eerie.sound();
				eerie = null;
			}
			return;
		}
		if (--eerieIn > 0) {
			return;
		}
		if (ScarePlayer.sinceLastScare() < 1200 || MusicDirector.chased() || CutscenePlayer.active()) {
			eerieIn = 600; // try again in half a minute
			return;
		}
		boolean below = profile == Profile.UNDERGROUND || profile == Profile.BUNKER;
		boolean night = (now & NIGHT) != 0;
		List<Eerie> pool = indoors || below ? EERIE_INSIDE : night ? EERIE_NIGHT : EERIE_DAY;
		eerie = pick(pool, random);
		eerieIn = night || below ? 1800 + random.nextInt(2400) : 4800 + random.nextInt(4800); // 1.5-3.5 min after dark, 4-8 by day
		// The world goes quiet first: the beds fade for four seconds, then it comes.
		hush = true;
		spellLeft = Math.max(spellLeft, 900);
		eerieAt = 80;
	}

	private static Eerie pick(List<Eerie> pool, RandomSource random) {
		int total = 0;
		for (Eerie e : pool) {
			total += e.sound().equals(lastEerie) ? 0 : e.weight();
		}
		if (total <= 0) {
			return pool.get(random.nextInt(pool.size()));
		}
		int roll = random.nextInt(total);
		for (Eerie e : pool) {
			if (e.sound().equals(lastEerie)) {
				continue;
			}
			roll -= e.weight();
			if (roll < 0) {
				return e;
			}
		}
		return pool.get(0);
	}

	private static void playEerie(Minecraft minecraft, LocalPlayer player, Eerie e, RandomSource random, float volume) {
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Deadlands.id(e.sound()));
		if (sound == null) {
			return;
		}
		float pitch = 0.94F + random.nextFloat() * 0.1F;
		float loud = Math.min(1.0F, volume * e.loud());
		if (e.far() <= 0.0F) {
			minecraft.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.AMBIENT, loud, pitch, random, false, 0,
				SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
			return;
		}
		float distance = e.near() + random.nextFloat() * (e.far() - e.near());
		float bearing = random.nextFloat() * Mth.TWO_PI;
		double x = player.getX() + Mth.cos(bearing) * distance;
		double y = player.getEyeY() + random.nextFloat() * 4.0F;
		double z = player.getZ() + Mth.sin(bearing) * distance;
		minecraft.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.AMBIENT, loud, pitch, random, false, 0,
			SoundInstance.Attenuation.NONE, x, y, z, false));
	}

	/** Once every half second: what is overhead and underfoot, and so which profile applies. */
	private static void survey(ClientLevel level, LocalPlayer player) {
		BlockPos head = BlockPos.containing(player.getEyePosition());
		indoors = !level.canSeeSky(head);
		roofed = false;
		metalRoof = false;
		for (int dy = 1; dy <= 12; dy++) {
			BlockState above = level.getBlockState(head.above(dy));
			if (!above.isAir() && !above.canBeReplaced()) {
				roofed = true;
				SoundType type = above.getSoundType();
				metalRoof = type == SoundType.METAL || type == SoundType.COPPER || type == SoundType.NETHERITE_BLOCK || type == SoundType.CHAIN
					|| type == SoundType.ANVIL || type == SoundType.IRON || metallic(above);
				break;
			}
		}
		BlockState under = level.getBlockState(player.blockPosition().below());
		Identifier ground = BuiltInRegistries.BLOCK.getKey(under.getBlock());
		onRoad = ground.getNamespace().equals(Deadlands.MOD_ID) && (ground.getPath().startsWith("asphalt") || ground.getPath().startsWith("cracked_asphalt"));
		profile = switch (place) {
			case FOREST -> onRoad ? Profile.HIGHWAY : Profile.FOREST;
			case FARM -> onRoad ? Profile.HIGHWAY : Profile.FARM;
			case OPEN -> onRoad ? Profile.HIGHWAY : Profile.OPEN;
			case SUBURB -> indoors ? Profile.INTERIOR : Profile.SUBURB;
			case CITY -> indoors ? Profile.INTERIOR : Profile.CITY;
			case HOSPITAL -> indoors ? Profile.HOSPITAL : Profile.SUBURB;
			case SCHOOL -> indoors ? Profile.SCHOOL : Profile.SUBURB;
			case INDUSTRIAL -> Profile.INDUSTRIAL;
			case CAMPSITE -> Profile.CAMPSITE;
			case BUNKER -> Profile.BUNKER;
			case UNDERGROUND -> Profile.UNDERGROUND;
		};
	}

	private static boolean metallic(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.contains("metal") || path.contains("iron") || path.contains("corrugated") || path.contains("diamond_plate") || path.contains("copper");
	}

	private static int conditions(ClientLevel level, LocalPlayer player) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		boolean night = time >= 12800L && time < 23200L;
		boolean wet = level.getRainLevel(1.0F) > 0.2F;
		return (night ? NIGHT : DAY) | (wet ? WET : DRY) | (level.isThundering() ? STORM : 0);
	}

	private static boolean play(Minecraft minecraft, LocalPlayer player, Layer layer, RandomSource random, float volume) {
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Deadlands.id(layer.sound()));
		if (sound == null) {
			return false;
		}
		float distance = layer.near() + random.nextFloat() * (layer.far() - layer.near());
		float bearing = layer.sound().startsWith("ambient.crow") ? crowBearing + (random.nextFloat() - 0.5F) * 1.4F : random.nextFloat() * Mth.TWO_PI;
		double x = player.getX() + Mth.cos(bearing) * distance;
		double y = player.getEyeY() + layer.lift() * (0.7F + random.nextFloat() * 0.6F);
		double z = player.getZ() + Mth.sin(bearing) * distance;
		boolean muffled = indoors && layer.outdoor();
		// Loudness from distance: close sounds full, far ones thin, as an ear hears them.
		float loud = volume * ONE_SHOT_GAIN * layer.loud() * (0.75F + random.nextFloat() * 0.25F) / (1.0F + distance / 14.0F) * (muffled ? 0.3F : 1.0F);
		float pitch = (0.92F + random.nextFloat() * 0.16F) * (muffled ? 0.9F : 1.0F);
		SoundInstance instance = new SimpleSoundInstance(sound.location(), SoundSource.AMBIENT, Math.min(1.0F, loud), pitch, random, false, 0,
			SoundInstance.Attenuation.NONE, x, y, z, false);
		minecraft.getSoundManager().play(instance);
		PLAYING.add(instance);
		return true;
	}

	// ============================================================================ loops

	private static void loops(Minecraft minecraft, ClientLevel level, LocalPlayer player, float volume) {
		float beds = volume * BED_GAIN;
		float height = Mth.clamp((float) (player.getY() - 80.0) / 60.0F, 0.0F, 0.5F);
		float wind = Math.min(1.0F, ClientWeather.wind() + height);
		// Calm air is heard only in a spell of sound; a real gale (a storm, a dust storm, a peak) is weather and always heard.
		float gale = Mth.clamp((wind - 0.3F) / 0.7F, 0.0F, 1.0F);
		float outdoorWind = beds * (0.14F * bedLevel + 0.8F * gale) * (indoors ? 0.25F : 1.0F);
		String windBed = switch (profile) {
			case FOREST, FARM, CAMPSITE -> "ambient.wind_trees_loop";
			case CITY, SUBURB, HIGHWAY, INDUSTRIAL, INTERIOR, HOSPITAL, SCHOOL -> "ambient.wind_street_loop";
			default -> "ambient.wind_loop";
		};
		boolean underground = profile == Profile.UNDERGROUND || profile == Profile.BUNKER;
		for (String name : List.of("ambient.wind_trees_loop", "ambient.wind_street_loop", "ambient.wind_loop")) {
			keep(minecraft, name, name.equals(windBed) && !underground ? outdoorWind : 0.0F);
		}
		// The biome's own bed: under the wind, only in a spell of sound, faint indoors.
		String biomeBed = underground ? null : level.getBiome(player.blockPosition()).unwrapKey()
			.filter(key -> key.identifier().getNamespace().equals(Deadlands.MOD_ID))
			.map(key -> BIOME_BEDS.get(key.identifier().getPath())).orElse(null);
		for (String name : BED_NAMES) {
			keep(minecraft, name, name.equals(biomeBed) ? beds * 0.5F * bedLevel * (indoors ? 0.2F : 1.0F) : 0.0F);
		}
		float rain = level.getRainLevel(1.0F);
		boolean rainsHere = rain > 0.05F && level.getBiome(player.blockPosition()).value().getPrecipitationAt(player.blockPosition(),
			level.getSeaLevel()) == Biome.Precipitation.RAIN;
		boolean sheltered = roofed && indoors && !underground;
		float drum = rainsHere && sheltered ? beds * rain * (level.isThundering() ? 0.9F : 0.7F) * (1.0F + 0.4F * ClientWeather.heavy()) : 0.0F;
		keep(minecraft, "ambient.rain_metal_loop", metalRoof ? drum : 0.0F);
		keep(minecraft, "ambient.rain_roof_loop", metalRoof ? 0.0F : drum);
		boolean building = indoors && switch (profile) {
			case INTERIOR, HOSPITAL, SCHOOL, INDUSTRIAL, BUNKER -> true;
			default -> false;
		};
		keep(minecraft, "ambient.room_tone_loop", building ? beds * 0.4F * bedLevel : 0.0F);
		keep(minecraft, "ambient.hum_loop", powered && building ? beds * 0.35F * (0.4F + 0.6F * bedLevel) : 0.0F);
		keep(minecraft, "ambient.transformer_loop", powered && !indoors && (profile == Profile.CITY || profile == Profile.SUBURB) ? beds * 0.15F * bedLevel : 0.0F);
	}

	private static void keep(Minecraft minecraft, String name, float target) {
		Loop loop = LOOPS.get(name);
		if (loop != null && (loop.done || !minecraft.getSoundManager().isActive(loop) && loop.age > 40)) {
			LOOPS.remove(name);
			loop = null;
		}
		if (loop == null && target > 0.02F) {
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Deadlands.id(name));
			if (sound != null) {
				loop = new Loop(sound);
				minecraft.getSoundManager().play(loop);
				LOOPS.put(name, loop);
			}
		}
		if (loop != null) {
			loop.target = target;
		}
	}

	/** A looping bed whose volume eases toward a target and which stops itself once silent. */
	private static final class Loop extends AbstractTickableSoundInstance {
		float target;
		boolean done;
		int age;

		Loop(SoundEvent sound) {
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
			volume += (target - volume) * 0.04F;
			if (done || (target <= 0.0F && volume < 0.01F)) {
				done = true;
				stop();
			}
		}
	}
}
