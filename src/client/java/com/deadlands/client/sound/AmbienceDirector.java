package com.deadlands.client.sound;

import com.deadlands.Deadlands;
import com.deadlands.audio.LocationTracker;
import com.deadlands.client.weather.ClientWeather;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.LocationPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Location-based ambience (Milestone 9). On top of the biome's own sound bed, each kind of place
 * (from {@code LocationTracker}) has its own scattered sounds: birds and a woodpecker in the forest,
 * a far dog and wind chimes in the suburbs, glass and car alarms in the city, a buzzing light and
 * drips in a hospital, lockers and paper in a school, steam and groaning metal in industry, tarps in
 * camp, ventilation in a bunker. Night swaps birds for crickets and an owl and brings the distant
 * infected closer. Sounds come from somewhere around you at a random distance, pitch and volume, never
 * the same one twice running.
 *
 * <p>Indoors (no sky above), outdoor sounds are quieter and lower, as if through walls; a powered
 * town hums inside its buildings. A wind loop follows the weather, rain and height. Everything is
 * scaled by {@code audio.ambienceVolume}.
 */
public final class AmbienceDirector {
	private static LocationTracker.Place place = LocationTracker.Place.OPEN;
	private static boolean powered;
	private static int cooldown = 200;
	private static String last = "";
	private static Loop wind;
	private static Loop hum;

	private AmbienceDirector() {
	}

	public static void set(LocationPayload payload) {
		place = LocationTracker.Place.byIndex(payload.place());
		powered = payload.powered();
		cooldown = Math.min(cooldown, 60);
	}

	public static void reset() {
		place = LocationTracker.Place.OPEN;
		powered = false;
		if (wind != null) {
			wind.done = true;
			wind = null;
		}
		if (hum != null) {
			hum.done = true;
			hum = null;
		}
	}

	/** The sounds a place can make, outdoor ones first. Names are DEADLANDS sound events. */
	private static List<String> pool(boolean night) {
		List<String> pool = new ArrayList<>();
		switch (place) {
			case FOREST -> pool.addAll(List.of("ambient.rustle", "ambient.creak", "ambient.gust"));
			case SUBURB -> pool.addAll(List.of("ambient.dog", "ambient.chime", "ambient.creak", "ambient.door_bang"));
			case CITY -> pool.addAll(List.of("ambient.glass", "ambient.metal", "ambient.car_alarm", "ambient.door_bang", "ambient.radio"));
			case HOSPITAL -> pool.addAll(List.of("ambient.flicker", "ambient.drip", "ambient.gurney", "ambient.door_bang"));
			case SCHOOL -> pool.addAll(List.of("ambient.paper", "ambient.locker", "ambient.door_bang", "ambient.creak"));
			case INDUSTRIAL -> pool.addAll(List.of("ambient.steam", "ambient.groan", "ambient.metal", "ambient.radio"));
			case FARM -> pool.addAll(List.of("ambient.crow", "ambient.creak", "ambient.gust", "ambient.rustle"));
			case CAMPSITE -> pool.addAll(List.of("ambient.tarp", "ambient.ember", "ambient.crow"));
			case BUNKER -> pool.addAll(List.of("ambient.vent", "ambient.drip", "ambient.groan"));
			case UNDERGROUND -> pool.addAll(List.of("ambient.drip", "ambient.groan"));
			default -> pool.addAll(List.of("ambient.gust", "ambient.crow"));
		}
		boolean outdoorPlace = place != LocationTracker.Place.BUNKER && place != LocationTracker.Place.UNDERGROUND;
		if (outdoorPlace) {
			if (night) {
				pool.addAll(List.of("ambient.crickets", "ambient.owl", "infected.distant"));
			} else if (place != LocationTracker.Place.CITY && place != LocationTracker.Place.INDUSTRIAL) {
				pool.addAll(List.of("ambient.bird", "ambient.bird", "ambient.woodpecker"));
			}
		}
		return pool;
	}

	/** Sounds that belong outside, muffled when heard from indoors. */
	private static boolean outdoor(String name) {
		return switch (name) {
			case "ambient.flicker", "ambient.drip", "ambient.gurney", "ambient.paper", "ambient.locker", "ambient.vent" -> false;
			default -> true;
		};
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		float volume = (float) ConfigManager.get().audio.ambienceVolume;
		boolean indoors = !minecraft.level.canSeeSky(minecraft.player.blockPosition().above());
		loops(minecraft, volume, indoors);
		if (volume <= 0.0F || --cooldown > 0) {
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		cooldown = 140 + random.nextInt(320);
		long time = Math.floorMod(minecraft.level.getDayTime(), 24000L);
		boolean night = time > 13000L && time < 23000L;
		List<String> pool = pool(night);
		pool.remove(last);
		if (pool.isEmpty()) {
			return;
		}
		String name = pool.get(random.nextInt(pool.size()));
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Deadlands.id(name));
		if (sound == null) {
			return;
		}
		last = name;
		boolean muffled = indoors && outdoor(name);
		double distance = outdoor(name) ? 10.0 + random.nextDouble() * 18.0 : 3.0 + random.nextDouble() * 5.0;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double x = minecraft.player.getX() + Math.cos(angle) * distance;
		double y = minecraft.player.getEyeY() + (random.nextDouble() - 0.3) * 4.0;
		double z = minecraft.player.getZ() + Math.sin(angle) * distance;
		float loud = volume * (0.5F + random.nextFloat() * 0.5F) * (muffled ? 0.35F : 1.0F);
		float pitch = (0.88F + random.nextFloat() * 0.24F) * (muffled ? 0.85F : 1.0F);
		minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, loud, pitch, random, x, y, z));
	}

	private static void loops(Minecraft minecraft, float volume, boolean indoors) {
		float height = Mth.clamp((float) (minecraft.player.getY() - 80.0) / 60.0F, 0.0F, 0.5F);
		float windTarget = volume * Math.min(1.0F, ClientWeather.wind() + height) * (indoors ? 0.3F : 1.0F);
		wind = keep(minecraft, wind, "ambient.wind_loop", windTarget);
		float humTarget = powered && indoors ? volume * 0.35F : 0.0F;
		hum = keep(minecraft, hum, "ambient.hum_loop", humTarget);
	}

	private static Loop keep(Minecraft minecraft, Loop loop, String name, float target) {
		if (loop != null && (loop.done || !minecraft.getSoundManager().isActive(loop))) {
			loop = null;
		}
		if (loop == null && target > 0.02F) {
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Deadlands.id(name));
			if (sound != null) {
				loop = new Loop(sound);
				minecraft.getSoundManager().play(loop);
			}
		}
		if (loop != null) {
			loop.target = target;
		}
		return loop;
	}

	/** A looping bed whose volume follows a target and which stops itself when it falls silent. */
	private static final class Loop extends AbstractTickableSoundInstance {
		float target;
		boolean done;

		Loop(SoundEvent sound) {
			super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.relative = true;
			this.volume = 0.01F;
		}

		@Override
		public void tick() {
			volume += (target - volume) * 0.05F;
			if (done || (target <= 0.0F && volume < 0.01F)) {
				done = true;
				stop();
			}
		}
	}
}
