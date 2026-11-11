package com.deadlands.client.sound;

import com.deadlands.Deadlands;
import com.deadlands.audio.LocationTracker;
import com.deadlands.client.weather.ClientWeather;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.LocationPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Each kind of place has a <b>profile</b>: layers of occasional sounds, each with its own spacing,
 * distances, height and conditions (day or night, dry or wet, a storm). A city has wind between
 * buildings, crows on the rooftops, creaking signs, loose metal and distant noises; a forest has wind
 * in the branches, leaves, creaking boughs, crows and far birds; a hospital has buzzing lights,
 * drips, wind whistling through broken windows and noises nobody can place; a highway has wind,
 * loose signs and rattling metal. Every layer keeps its own clock, no two one-shots start within two
 * seconds of each other, at most three play at once, and the same sound never plays twice running,
 * so nothing loops and silence stays part of it.
 *
 * <p>One-shots are placed around the listener with no distance roll-off of their own: their loudness
 * is worked out here from their distance (Minecraft's linear roll-off silences anything placed past
 * sixteen blocks, which is why far crows and dogs need this). Outdoor sounds heard from indoors are
 * quieter and duller. Loops follow the weather: wind that rises with storms and height (in the trees,
 * between buildings, or in the open), rain drumming on the roof (on metal it rings), the hush of a
 * room, and the hum of a powered town. Everything is scaled by {@code audio.ambienceVolume}, and
 * {@code audio.ambienceDensity} sets how often one-shots come.
 */
public final class AmbienceDirector {
	private static final int DAY = 1;
	private static final int NIGHT = 2;
	private static final int DRY = 4;
	private static final int WET = 8;
	private static final int STORM = 16;
	private static final int ALWAYS = DAY | NIGHT | DRY | WET;
	private static final int DAY_DRY = DAY | DRY;
	private static final int NIGHT_ANY = NIGHT | DRY | WET;
	private static final int DAY_ANY = DAY | DRY | WET;
	private static final int ANY_DRY = DAY | NIGHT | DRY;

	private static final int MIN_GAP = 40;
	private static final int MAX_PLAYING = 3;

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

	private static final Map<Profile, List<Layer>> PROFILES = new EnumMap<>(Profile.class);

	static {
		Layer crow = new Layer("ambient.crow", 25, 70, 30, 70, 10, 1.0F, ANY_DRY, true);
		Layer flock = new Layer("ambient.crow_flock", 120, 300, 40, 80, 14, 1.0F, DAY_ANY, true);
		Layer birds = new Layer("ambient.birds_far", 20, 55, 25, 60, 5, 0.7F, DAY_DRY, true);
		Layer thunder = new Layer("ambient.thunder_far", 25, 70, 150, 300, 30, 1.0F, ALWAYS | STORM, true);
		Layer infected = new Layer("infected.distant", 60, 150, 40, 90, 0, 0.7F, NIGHT_ANY, true);
		Layer dog = new Layer("ambient.dog", 90, 240, 60, 120, 0, 0.8F, ALWAYS, true);
		Layer crickets = new Layer("ambient.crickets", 25, 60, 6, 14, 0, 0.45F, NIGHT | DRY, true);
		Layer owl = new Layer("ambient.owl", 70, 160, 20, 50, 6, 0.6F, NIGHT | DRY, true);
		Layer gust = new Layer("ambient.gust", 30, 80, 10, 30, 2, 0.6F, ALWAYS, true);
		Layer leaves = new Layer("ambient.leaves", 12, 35, 6, 20, 3, 0.55F, ALWAYS, true);
		Layer branch = new Layer("ambient.branch", 25, 70, 8, 25, 5, 0.6F, ALWAYS, true);
		Layer rustle = new Layer("ambient.rustle", 20, 50, 4, 12, 0, 0.5F, ALWAYS, true);
		Layer woodpecker = new Layer("ambient.woodpecker", 60, 150, 20, 40, 5, 0.5F, DAY_DRY, true);
		Layer sign = new Layer("ambient.sign_creak", 30, 90, 12, 30, 4, 0.7F, ALWAYS, true);
		Layer rattle = new Layer("ambient.rattle", 35, 100, 10, 28, 2, 0.6F, ALWAYS, true);
		Layer metal = new Layer("ambient.metal", 40, 110, 25, 60, 3, 0.8F, ALWAYS, true);
		Layer buzz = new Layer("ambient.buzz", 50, 130, 8, 20, 4, 0.45F, ALWAYS, true);
		Layer glass = new Layer("ambient.glass", 60, 170, 15, 35, 4, 0.55F, ALWAYS, true);
		Layer alarm = new Layer("ambient.car_alarm", 300, 600, 60, 120, 0, 0.5F, ALWAYS, true);
		Layer radio = new Layer("ambient.radio", 200, 420, 15, 35, 3, 0.4F, ALWAYS, true);
		Layer door = new Layer("ambient.door_bang", 70, 180, 15, 40, 0, 0.6F, ALWAYS, true);
		Layer creak = new Layer("ambient.creak", 40, 100, 8, 25, 2, 0.55F, ALWAYS, true);
		Layer chime = new Layer("ambient.chime", 45, 110, 10, 25, 3, 0.45F, ALWAYS, true);
		Layer unexplained = new Layer("ambient.unexplained", 110, 260, 40, 90, 0, 0.7F, NIGHT_ANY, true);
		// Indoor sounds (never muffled).
		Layer settle = new Layer("ambient.settle", 35, 90, 4, 12, 2, 0.5F, ALWAYS, false);
		Layer window = new Layer("ambient.window_wind", 40, 100, 5, 14, 1, 0.5F, ALWAYS, false);
		Layer drip = new Layer("ambient.drip", 20, 50, 3, 8, 1, 0.45F, ALWAYS, false);
		Layer roomRattle = new Layer("ambient.rattle", 60, 150, 4, 12, 1, 0.4F, ALWAYS, false);
		Layer faraway = new Layer("ambient.unexplained", 90, 220, 15, 40, 0, 0.45F, ALWAYS, false);
		Layer flicker = new Layer("ambient.flicker", 25, 60, 4, 10, 2, 0.45F, ALWAYS, false);
		Layer gurney = new Layer("ambient.gurney", 90, 200, 8, 20, 0, 0.4F, ALWAYS, false);
		Layer indoorDoor = new Layer("ambient.door_bang", 80, 180, 10, 30, 0, 0.45F, ALWAYS, false);
		Layer paper = new Layer("ambient.paper", 30, 80, 3, 9, 0, 0.4F, ALWAYS, false);
		Layer locker = new Layer("ambient.locker", 60, 160, 6, 18, 0, 0.45F, ALWAYS, false);
		Layer steam = new Layer("ambient.steam", 30, 80, 8, 20, 2, 0.5F, ALWAYS, false);
		Layer groan = new Layer("ambient.groan", 45, 120, 10, 30, 0, 0.55F, ALWAYS, false);
		Layer vent = new Layer("ambient.vent", 30, 80, 4, 12, 2, 0.45F, ALWAYS, false);
		Layer indoorBuzz = new Layer("ambient.buzz", 40, 100, 3, 8, 2, 0.4F, ALWAYS, false);
		Layer tarp = new Layer("ambient.tarp", 20, 50, 4, 10, 1, 0.5F, ALWAYS, true);
		Layer ember = new Layer("ambient.ember", 15, 40, 2, 6, 0, 0.4F, ALWAYS, true);

		PROFILES.put(Profile.OPEN, List.of(gust, crow, flock, birds, thunder, infected, dog, crickets, owl));
		PROFILES.put(Profile.FOREST, List.of(leaves, branch, rustle, crow, flock, birds, woodpecker, owl, crickets, infected, thunder,
			new Layer("ambient.dog", 200, 420, 70, 130, 0, 0.6F, ALWAYS, true)));
		PROFILES.put(Profile.SUBURB, List.of(crow, flock, dog, chime, sign, door, creak, birds, infected, unexplained, thunder,
			new Layer("ambient.car_alarm", 400, 800, 70, 130, 0, 0.45F, ALWAYS, true)));
		PROFILES.put(Profile.CITY, List.of(new Layer("ambient.crow", 30, 80, 25, 60, 16, 1.0F, ANY_DRY, true),
			new Layer("ambient.crow_flock", 120, 260, 35, 70, 20, 1.0F, DAY_ANY, true), sign, rattle, metal, buzz, glass, alarm, radio,
			new Layer("infected.distant", 40, 110, 35, 80, 0, 0.7F, ALWAYS, true), new Layer("ambient.dog", 100, 240, 60, 120, 0, 0.7F, ALWAYS, true),
			door, unexplained, thunder));
		PROFILES.put(Profile.HIGHWAY, List.of(new Layer("ambient.sign_creak", 30, 70, 10, 30, 5, 0.75F, ALWAYS, true),
			new Layer("ambient.rattle", 40, 90, 10, 30, 2, 0.6F, ALWAYS, true), new Layer("ambient.crow", 40, 90, 30, 70, 12, 1.0F, ANY_DRY, true),
			new Layer("ambient.crow_flock", 200, 400, 50, 90, 14, 1.0F, DAY_ANY, true), metal,
			new Layer("ambient.buzz", 90, 200, 10, 25, 8, 0.4F, ALWAYS, true), new Layer("ambient.birds_far", 40, 90, 30, 70, 5, 0.6F, DAY_DRY, true),
			new Layer("ambient.dog", 150, 300, 70, 130, 0, 0.6F, ALWAYS, true), infected, gust, thunder));
		PROFILES.put(Profile.INTERIOR, List.of(settle, window, creak, roomRattle, drip, faraway, new Layer("ambient.crow", 60, 140, 25, 60, 8, 0.8F, ANY_DRY, true),
			new Layer("ambient.dog", 100, 240, 60, 120, 0, 0.6F, ALWAYS, true), infected, thunder));
		PROFILES.put(Profile.HOSPITAL, List.of(flicker, drip, gurney, indoorDoor, window, roomRattle, settle, faraway, indoorBuzz, thunder));
		PROFILES.put(Profile.SCHOOL, List.of(paper, locker, indoorDoor, creak, window, settle, faraway, thunder));
		PROFILES.put(Profile.INDUSTRIAL, List.of(steam, groan, metal, radio, rattle, buzz, sign, crow, unexplained, thunder));
		PROFILES.put(Profile.FARM, List.of(new Layer("ambient.crow", 20, 60, 25, 60, 6, 1.0F, ANY_DRY, true),
			new Layer("ambient.crow_flock", 90, 240, 30, 70, 8, 1.0F, DAY_ANY, true), creak, gust, rustle, birds, dog, crickets, owl, infected, thunder));
		PROFILES.put(Profile.CAMPSITE, List.of(tarp, ember, crow, rustle, branch, owl, crickets, birds, thunder));
		PROFILES.put(Profile.BUNKER, List.of(vent, drip, groan, indoorBuzz, settle));
		PROFILES.put(Profile.UNDERGROUND, List.of(drip, new Layer("ambient.groan", 60, 160, 10, 30, 0, 0.4F, ALWAYS, false), settle,
			new Layer("ambient.unexplained", 180, 400, 20, 50, 0, 0.35F, ALWAYS, false)));
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
		loops(minecraft, level, player, volume);
		if (volume <= 0.0F) {
			return;
		}
		RandomSource random = level.getRandom();
		int now = conditions(level, player);
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
				globalGap = MIN_GAP + random.nextInt(40);
			}
			int span = Math.max(1, layer.maxGap() - layer.minGap());
			CLOCKS.put(key, (int) ((layer.minGap() + random.nextInt(span)) * 20 / density));
		}
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
		float loud = volume * layer.loud() * (0.75F + random.nextFloat() * 0.25F) / (1.0F + distance / 14.0F) * (muffled ? 0.3F : 1.0F);
		float pitch = (0.92F + random.nextFloat() * 0.16F) * (muffled ? 0.9F : 1.0F);
		SoundInstance instance = new SimpleSoundInstance(sound.location(), SoundSource.AMBIENT, Math.min(1.0F, loud), pitch, random, false, 0,
			SoundInstance.Attenuation.NONE, x, y, z, false);
		minecraft.getSoundManager().play(instance);
		PLAYING.add(instance);
		return true;
	}

	// ============================================================================ loops

	private static void loops(Minecraft minecraft, ClientLevel level, LocalPlayer player, float volume) {
		float height = Mth.clamp((float) (player.getY() - 80.0) / 60.0F, 0.0F, 0.5F);
		float wind = Math.min(1.0F, ClientWeather.wind() + height);
		float outdoorWind = volume * (0.12F + wind * 0.88F) * (indoors ? 0.25F : 1.0F);
		String bed = switch (profile) {
			case FOREST, FARM, CAMPSITE -> "ambient.wind_trees_loop";
			case CITY, SUBURB, HIGHWAY, INDUSTRIAL, INTERIOR, HOSPITAL, SCHOOL -> "ambient.wind_street_loop";
			default -> "ambient.wind_loop";
		};
		boolean underground = profile == Profile.UNDERGROUND || profile == Profile.BUNKER;
		for (String name : List.of("ambient.wind_trees_loop", "ambient.wind_street_loop", "ambient.wind_loop")) {
			keep(minecraft, name, name.equals(bed) && !underground ? outdoorWind * (wind < 0.05F ? 0.5F : 1.0F) : 0.0F);
		}
		float rain = level.getRainLevel(1.0F);
		boolean rainsHere = rain > 0.05F && level.getBiome(player.blockPosition()).value().getPrecipitationAt(player.blockPosition(),
			level.getSeaLevel()) == Biome.Precipitation.RAIN;
		boolean sheltered = roofed && indoors && !underground;
		float drum = rainsHere && sheltered ? volume * rain * (level.isThundering() ? 0.9F : 0.7F) : 0.0F;
		keep(minecraft, "ambient.rain_metal_loop", metalRoof ? drum : 0.0F);
		keep(minecraft, "ambient.rain_roof_loop", metalRoof ? 0.0F : drum);
		boolean building = indoors && switch (profile) {
			case INTERIOR, HOSPITAL, SCHOOL, INDUSTRIAL, BUNKER -> true;
			default -> false;
		};
		keep(minecraft, "ambient.room_tone_loop", building ? volume * 0.35F : 0.0F);
		keep(minecraft, "ambient.hum_loop", powered && building ? volume * 0.3F : 0.0F);
		keep(minecraft, "ambient.transformer_loop", powered && !indoors && (profile == Profile.CITY || profile == Profile.SUBURB) ? volume * 0.12F : 0.0F);
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
