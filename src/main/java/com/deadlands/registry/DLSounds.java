package com.deadlands.registry;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events. Every event here is backed by an original, procedurally synthesised OGG file
 * (see tools/assetgen/gen_audio.py) mapped in assets/deadlands/sounds.json.
 */
public final class DLSounds {
	private static final List<Holder.Reference<SoundEvent>> ALL = new ArrayList<>();

	public static final Holder.Reference<SoundEvent> UI_CLICK = register("ui.click");
	public static final Holder.Reference<SoundEvent> UI_JOURNAL_OPEN = register("ui.journal_open");
	public static final Holder.Reference<SoundEvent> UI_JOURNAL_CLOSE = register("ui.journal_close");
	public static final Holder.Reference<SoundEvent> UI_PAGE_TURN = register("ui.page_turn");
	public static final Holder.Reference<SoundEvent> EVENT_DAY_BEGIN = register("event.day_begin");
	public static final Holder.Reference<SoundEvent> EVENT_TOWN_ENTER = register("event.town_enter");
	public static final Holder.Reference<SoundEvent> MUSIC_TITLE = register("music.title");

	// Containers: each kind opens and closes with its own sound.
	public static final Holder.Reference<SoundEvent> CRATE_OPEN = register("container.crate_open");
	public static final Holder.Reference<SoundEvent> CRATE_CLOSE = register("container.crate_close");
	public static final Holder.Reference<SoundEvent> DRAWER_OPEN = register("container.drawer_open");
	public static final Holder.Reference<SoundEvent> DRAWER_CLOSE = register("container.drawer_close");
	public static final Holder.Reference<SoundEvent> CABINET_OPEN = register("container.cabinet_open");
	public static final Holder.Reference<SoundEvent> CABINET_CLOSE = register("container.cabinet_close");
	public static final Holder.Reference<SoundEvent> LOCKER_OPEN = register("container.locker_open");
	public static final Holder.Reference<SoundEvent> LOCKER_CLOSE = register("container.locker_close");
	public static final Holder.Reference<SoundEvent> TOOLBOX_OPEN = register("container.toolbox_open");
	public static final Holder.Reference<SoundEvent> TOOLBOX_CLOSE = register("container.toolbox_close");
	public static final Holder.Reference<SoundEvent> CONTAINER_LOCKED = register("container.locked");
	public static final Holder.Reference<SoundEvent> CONTAINER_UNLOCK = register("container.unlock");

	// Biome ambience (Milestone 3): the biome JSONs refer to these by id.
	public static final Holder.Reference<SoundEvent> AMBIENT_DEAD_FOREST = register("ambient.dead_forest.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_PINE = register("ambient.pine.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_SUBURBS = register("ambient.suburbs.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_FIELDS = register("ambient.fields.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_DRY_WIND = register("ambient.dry_wind.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_MARSH = register("ambient.marsh.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_FROZEN = register("ambient.frozen.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_INDUSTRIAL = register("ambient.industrial.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_BURNED = register("ambient.burned.loop");
	public static final Holder.Reference<SoundEvent> AMBIENT_MOOD = register("ambient.mood");
	public static final Holder.Reference<SoundEvent> AMBIENT_CREAK = register("ambient.creak");
	public static final Holder.Reference<SoundEvent> AMBIENT_CROW = register("ambient.crow");
	public static final Holder.Reference<SoundEvent> AMBIENT_METAL = register("ambient.metal");
	public static final Holder.Reference<SoundEvent> AMBIENT_GUST = register("ambient.gust");
	public static final Holder.Reference<SoundEvent> AMBIENT_FROG = register("ambient.frog");
	public static final Holder.Reference<SoundEvent> AMBIENT_EMBER = register("ambient.ember");

	// The infected (Milestone 4). Idle voices are chosen and played by each client (distance and
	// walls change which version is heard); hurt, death, attack and event sounds come from the server.
	public static final Holder.Reference<SoundEvent> INFECTED_WALKER_AMBIENT = register("infected.walker.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_WALKER_HURT = register("infected.walker.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_WALKER_DEATH = register("infected.walker.death");
	public static final Holder.Reference<SoundEvent> INFECTED_RUNNER_AMBIENT = register("infected.runner.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_RUNNER_ALERT = register("infected.runner.alert");
	public static final Holder.Reference<SoundEvent> INFECTED_RUNNER_HURT = register("infected.runner.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_RUNNER_DEATH = register("infected.runner.death");
	public static final Holder.Reference<SoundEvent> INFECTED_BRUTE_AMBIENT = register("infected.brute.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_BRUTE_ROAR = register("infected.brute.roar");
	public static final Holder.Reference<SoundEvent> INFECTED_BRUTE_HURT = register("infected.brute.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_BRUTE_DEATH = register("infected.brute.death");
	public static final Holder.Reference<SoundEvent> INFECTED_BRUTE_STEP = register("infected.brute.step");
	public static final Holder.Reference<SoundEvent> INFECTED_BRUTE_SLAM = register("infected.brute.slam");
	public static final Holder.Reference<SoundEvent> INFECTED_SCREAMER_AMBIENT = register("infected.screamer.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_SCREAMER_SCREAM = register("infected.screamer.scream");
	public static final Holder.Reference<SoundEvent> INFECTED_SCREAMER_HURT = register("infected.screamer.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_SCREAMER_DEATH = register("infected.screamer.death");
	public static final Holder.Reference<SoundEvent> INFECTED_MILITARY_AMBIENT = register("infected.military.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_CRAWLER_AMBIENT = register("infected.crawler.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_CRAWLER_HURT = register("infected.crawler.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_CRAWLER_DEATH = register("infected.crawler.death");
	public static final Holder.Reference<SoundEvent> INFECTED_BLOATER_AMBIENT = register("infected.bloater.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_BLOATER_BURST = register("infected.bloater.burst");
	public static final Holder.Reference<SoundEvent> INFECTED_BLOATER_HURT = register("infected.bloater.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_STALKER_AMBIENT = register("infected.stalker.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_STALKER_POUNCE = register("infected.stalker.pounce");
	public static final Holder.Reference<SoundEvent> INFECTED_STALKER_HURT = register("infected.stalker.hurt");
	public static final Holder.Reference<SoundEvent> INFECTED_STALKER_DEATH = register("infected.stalker.death");
	public static final Holder.Reference<SoundEvent> INFECTED_CHARRED_AMBIENT = register("infected.charred.ambient");
	public static final Holder.Reference<SoundEvent> INFECTED_BREATH = register("infected.breath");
	public static final Holder.Reference<SoundEvent> INFECTED_SCRATCH = register("infected.scratch");
	public static final Holder.Reference<SoundEvent> INFECTED_BASH_WOOD = register("infected.bash_wood");
	public static final Holder.Reference<SoundEvent> INFECTED_BASH_METAL = register("infected.bash_metal");
	public static final Holder.Reference<SoundEvent> INFECTED_STEP = register("infected.step");
	public static final Holder.Reference<SoundEvent> INFECTED_ATTACK = register("infected.attack");
	public static final Holder.Reference<SoundEvent> INFECTED_DISTANT = register("infected.distant");
	public static final Holder.Reference<SoundEvent> INFECTED_MUFFLED = register("infected.muffled");
	public static final Holder.Reference<SoundEvent> INFECTED_EMERGE = register("infected.emerge");

	// Nests and hordes (Milestone 4).
	public static final Holder.Reference<SoundEvent> NEST_PULSE = register("nest.pulse");
	public static final Holder.Reference<SoundEvent> NEST_RELEASE = register("nest.release");
	public static final Holder.Reference<SoundEvent> NEST_DESTROYED = register("nest.destroyed");
	public static final Holder.Reference<SoundEvent> NEST_ABSORB = register("nest.absorb");
	public static final Holder.Reference<SoundEvent> EVENT_HORDE_START = register("event.horde_start");
	public static final Holder.Reference<SoundEvent> EVENT_HORDE_BROKEN = register("event.horde_broken");
	public static final Holder.Reference<SoundEvent> EVENT_HORDE_DAWN = register("event.horde_dawn");
	public static final Holder.Reference<SoundEvent> EVENT_HORDE_DISTANT = register("event.horde_distant");
	public static final Holder.Reference<SoundEvent> WEAPON_PISTOL_SHOT = register("weapon.pistol_shot");
	public static final Holder.Reference<SoundEvent> WEAPON_DRY_FIRE = register("weapon.dry_fire");
	public static final Holder.Reference<SoundEvent> WEAPON_SHOTGUN_SHOT = register("weapon.shotgun_shot");
	public static final Holder.Reference<SoundEvent> WEAPON_RIFLE_SHOT = register("weapon.rifle_shot");
	public static final Holder.Reference<SoundEvent> WEAPON_RELOAD = register("weapon.reload");
	public static final Holder.Reference<SoundEvent> VEHICLE_ENGINE = register("vehicle.engine");
	public static final Holder.Reference<SoundEvent> VEHICLE_IMPACT = register("vehicle.impact");
	public static final Holder.Reference<SoundEvent> VEHICLE_REPAIR = register("vehicle.repair");
	public static final Holder.Reference<SoundEvent> VEHICLE_HORN = register("vehicle.horn");
	public static final Holder.Reference<SoundEvent> RADIO_STATIC = register("radio.static");
	public static final Holder.Reference<SoundEvent> RADIO_TUNE = register("radio.tune");
	public static final Holder.Reference<SoundEvent> EVENT_BOSS = register("event.boss");
	public static final Holder.Reference<SoundEvent> HELICOPTER_ROTOR = register("helicopter.rotor");
	/** Location ambience (Milestone 9), played by name from {@code client.sound.AmbienceDirector}. */
	private static final List<Holder.Reference<SoundEvent>> PLACE_AMBIENCE = List.of(
		register("ambient.bird"),
		register("ambient.woodpecker"),
		register("ambient.crickets"),
		register("ambient.owl"),
		register("ambient.rustle"),
		register("ambient.dog"),
		register("ambient.chime"),
		register("ambient.door_bang"),
		register("ambient.glass"),
		register("ambient.car_alarm"),
		register("ambient.flicker"),
		register("ambient.drip"),
		register("ambient.gurney"),
		register("ambient.paper"),
		register("ambient.locker"),
		register("ambient.steam"),
		register("ambient.groan"),
		register("ambient.tarp"),
		register("ambient.vent"),
		register("ambient.radio"),
		register("ambient.wind_loop"),
		register("ambient.hum_loop"));
	private static final java.util.Map<String, Holder.Reference<SoundEvent>> BOSS_SOUNDS = registerBossSounds();
	public static final Holder.Reference<SoundEvent> NPC_GREET = register("npc.greet");
	public static final Holder.Reference<SoundEvent> NPC_IDLE = register("npc.idle");
	public static final Holder.Reference<SoundEvent> NPC_ALERT = register("npc.alert");
	public static final Holder.Reference<SoundEvent> NPC_HURT = register("npc.hurt");
	public static final Holder.Reference<SoundEvent> NPC_DEATH = register("npc.death");

	private DLSounds() {
	}

	private static Holder.Reference<SoundEvent> register(String name) {
		Identifier id = Deadlands.id(name);
		Holder.Reference<SoundEvent> holder = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		ALL.add(holder);
		return holder;
	}

	private static java.util.Map<String, Holder.Reference<SoundEvent>> registerBossSounds() {
		java.util.Map<String, Holder.Reference<SoundEvent>> sounds = new java.util.HashMap<>();
		for (com.deadlands.progression.BossKind kind : com.deadlands.progression.BossKind.values()) {
			for (String what : new String[] {"roar", "attack", "hurt", "death"}) {
				sounds.put(kind.id() + "." + what, register("boss." + kind.id() + "." + what));
			}
		}
		return sounds;
	}

	/** A boss's voice: roar, attack, hurt or death. */
	public static SoundEvent boss(com.deadlands.progression.BossKind kind, String what) {
		return BOSS_SOUNDS.get(kind.id() + "." + what).value();
	}

	public static List<Holder.Reference<SoundEvent>> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
