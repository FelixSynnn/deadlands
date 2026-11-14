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
	/** Only when a crowd is really chasing (see {@code ChaseTracker}); looped and faded by the client. */
	public static final Holder.Reference<SoundEvent> MUSIC_CHASE = register("music.chase");
	/** At most one exploration piece every 10 to 15 minutes of play. */
	public static final Holder.Reference<SoundEvent> MUSIC_EXPLORE_CHOSEN = register("music.explore");

	// Horror Director (Phase 2): the night creature, dread beds, unsettling moments and scare stingers.
	public static final Holder.Reference<SoundEvent> HORROR_NIGHT_CREATURE = register("horror.night_creature");
	public static final Holder.Reference<SoundEvent> HORROR_DREAD = register("horror.dread");
	public static final Holder.Reference<SoundEvent> HORROR_WATER_CREAK = register("horror.water_creak");
	public static final Holder.Reference<SoundEvent> HORROR_WHISPER = register("horror.whisper");
	public static final Holder.Reference<SoundEvent> HORROR_DOOR_CREAK = register("horror.door_creak");
	public static final Holder.Reference<SoundEvent> HORROR_DISTANT_HORN = register("horror.distant_horn");
	public static final Holder.Reference<SoundEvent> HORROR_STING_FOREST = register("horror.sting_forest");
	public static final Holder.Reference<SoundEvent> HORROR_RISER = register("horror.riser");
	public static final Holder.Reference<SoundEvent> HORROR_STINGER = register("horror.stinger");
	public static final Holder.Reference<SoundEvent> HORROR_SWELL = register("horror.swell");
	public static final Holder.Reference<SoundEvent> HORROR_WHOOSH = register("horror.whoosh");
	public static final Holder.Reference<SoundEvent> HORROR_SUSPENSE = register("horror.suspense");
	public static final Holder.Reference<SoundEvent> HORROR_SCREAM = register("horror.scream");
	public static final Holder.Reference<SoundEvent> HORROR_JUMPSCARE = register("horror.jumpscare");

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
	// Phase 4 jumpscares and feeding (files from tools/assetgen/import_zombie_sfx.py).
	public static final Holder.Reference<SoundEvent> HORROR_BREATH = register("horror.breath");
	public static final Holder.Reference<SoundEvent> HORROR_STALKER_CHANT = register("horror.stalker_chant");
	public static final Holder.Reference<SoundEvent> HORROR_STALKER_LAUGH = register("horror.stalker_laugh");
	public static final Holder.Reference<SoundEvent> INFECTED_FEEDING = register("infected.feeding");
	public static final Holder.Reference<SoundEvent> INFECTED_FEEDING_SCREECH = register("infected.feeding_screech");
	// Phase 5 cinematics (tools/assetgen/gen_cinematics.py).
	public static final Holder.Reference<SoundEvent> MUSIC_INTRO = register("music.intro");
	public static final Holder.Reference<SoundEvent> EVENT_REUNION = register("event.reunion");
	public static final Holder.Reference<SoundEvent> AMBIENT_INTRO = register("ambient.intro");
	// The Asesino (files from the owner's Serial folder; tools/assetgen/import_asesino.py).
	public static final Holder.Reference<SoundEvent> ASESINO_AMBIENT = register("asesino.ambient");
	public static final Holder.Reference<SoundEvent> ASESINO_HURT = register("asesino.hurt");
	public static final Holder.Reference<SoundEvent> ASESINO_DEATH = register("asesino.death");
	public static final Holder.Reference<SoundEvent> ASESINO_STEP = register("asesino.step");
	public static final Holder.Reference<SoundEvent> ASESINO_SWING = register("asesino.swing");
	public static final Holder.Reference<SoundEvent> ASESINO_HIT = register("asesino.hit");
	public static final Holder.Reference<SoundEvent> ASESINO_EXECUTION = register("asesino.execution");
	public static final Holder.Reference<SoundEvent> ASESINO_CHASE_START = register("asesino.chase_start");
	public static final Holder.Reference<SoundEvent> ASESINO_POUNCE = register("asesino.pounce");
	public static final Holder.Reference<SoundEvent> ASESINO_POUNCE_HIT = register("asesino.pounce_hit");
	public static final Holder.Reference<SoundEvent> ASESINO_POUNCE_MISS = register("asesino.pounce_miss");
	public static final Holder.Reference<SoundEvent> ASESINO_STUN = register("asesino.stun");
	public static final Holder.Reference<SoundEvent> ASESINO_BREACH = register("asesino.breach");
	public static final Holder.Reference<SoundEvent> ASESINO_SCENT = register("asesino.scent");
	public static final Holder.Reference<SoundEvent> ASESINO_FRENZY = register("asesino.frenzy");
	public static final Holder.Reference<SoundEvent> ASESINO_SHRIEK = register("asesino.shriek");
	// The Guest (the owner's GuestSoundsPack; tools/assetgen/import_guest_sfx.py).
	public static final Holder.Reference<SoundEvent> GUEST_AMBIENT = register("guest.ambient");
	public static final Holder.Reference<SoundEvent> GUEST_STEP = register("guest.step");
	public static final Holder.Reference<SoundEvent> GUEST_SWING = register("guest.swing");
	public static final Holder.Reference<SoundEvent> GUEST_FEED = register("guest.feed");
	public static final Holder.Reference<SoundEvent> GUEST_KILL = register("guest.kill");
	public static final Holder.Reference<SoundEvent> GUEST_ROAR = register("guest.roar");
	public static final Holder.Reference<SoundEvent> GUEST_ROAR_BLAST = register("guest.roar_blast");
	public static final Holder.Reference<SoundEvent> GUEST_SENSE = register("guest.sense");
	public static final Holder.Reference<SoundEvent> GUEST_GRAB_WARN = register("guest.grab_warn");
	public static final Holder.Reference<SoundEvent> GUEST_GRAB_PREPARE = register("guest.grab_prepare");
	public static final Holder.Reference<SoundEvent> GUEST_GRAB_LUNGE = register("guest.grab_lunge");
	public static final Holder.Reference<SoundEvent> GUEST_GRAB_HIT = register("guest.grab_hit");
	public static final Holder.Reference<SoundEvent> GUEST_GRAB_MISS = register("guest.grab_miss");
	public static final Holder.Reference<SoundEvent> GUEST_SLAM = register("guest.slam");
	public static final Holder.Reference<SoundEvent> GUEST_HEAL = register("guest.heal");
	public static final Holder.Reference<SoundEvent> GUEST_ARRIVE = register("guest.arrive");
	public static final Holder.Reference<SoundEvent> GUEST_LEAVE = register("guest.leave");
	public static final Holder.Reference<SoundEvent> GUEST_FEVER = register("guest.fever");
	public static final Holder.Reference<SoundEvent> GUEST_KNOCK = register("guest.knock");
	public static final Holder.Reference<SoundEvent> GUEST_TEAR = register("guest.tear");
	public static final Holder.Reference<SoundEvent> GUEST_HURT = register("guest.hurt");
	public static final Holder.Reference<SoundEvent> GUEST_DEATH = register("guest.death");
	// D.O.T. (the owner's sounds; tools/assetgen/import_dot_sfx.py)
	public static final Holder.Reference<SoundEvent> DOT_IDLE = register("dot.idle");
	public static final Holder.Reference<SoundEvent> DOT_STEP = register("dot.step");
	public static final Holder.Reference<SoundEvent> DOT_SWING = register("dot.swing");
	public static final Holder.Reference<SoundEvent> DOT_KILL = register("dot.kill");
	public static final Holder.Reference<SoundEvent> DOT_PROVOKED = register("dot.provoked");
	public static final Holder.Reference<SoundEvent> DOT_STATIC_STEP = register("dot.static_step");
	public static final Holder.Reference<SoundEvent> DOT_RESTORE = register("dot.restore");
	public static final Holder.Reference<SoundEvent> DOT_GRASP_WINDUP = register("dot.grasp_windup");
	public static final Holder.Reference<SoundEvent> DOT_GRASP = register("dot.grasp");
	public static final Holder.Reference<SoundEvent> DOT_FIELD_START = register("dot.field_start");
	public static final Holder.Reference<SoundEvent> DOT_FIELD_LOOP = register("dot.field_loop");
	public static final Holder.Reference<SoundEvent> DOT_FIELD_END = register("dot.field_end");
	public static final Holder.Reference<SoundEvent> DOT_HURT = register("dot.hurt");
	public static final Holder.Reference<SoundEvent> DOT_DEATH = register("dot.death");
	// Mythos and his sub-agents (tools/assetgen/import_mythos_sfx.py).
	public static final Holder.Reference<SoundEvent> MYTHOS_IDLE = register("mythos.idle");
	public static final Holder.Reference<SoundEvent> MYTHOS_STEP = register("mythos.step");
	public static final Holder.Reference<SoundEvent> MYTHOS_SLASH = register("mythos.slash");
	public static final Holder.Reference<SoundEvent> MYTHOS_SLASH_HIT = register("mythos.slash_hit");
	public static final Holder.Reference<SoundEvent> MYTHOS_ENTANGLE = register("mythos.entangle");
	public static final Holder.Reference<SoundEvent> MYTHOS_ENTANGLE_HIT = register("mythos.entangle_hit");
	public static final Holder.Reference<SoundEvent> MYTHOS_MASS_INFECTION = register("mythos.mass_infection");
	public static final Holder.Reference<SoundEvent> MYTHOS_MASS_INFECTION_FAR = register("mythos.mass_infection_far");
	public static final Holder.Reference<SoundEvent> MYTHOS_PACKET = register("mythos.packet");
	public static final Holder.Reference<SoundEvent> MYTHOS_PUDDLE = register("mythos.puddle");
	public static final Holder.Reference<SoundEvent> MYTHOS_REJUVENATE = register("mythos.rejuvenate");
	public static final Holder.Reference<SoundEvent> MYTHOS_KILL = register("mythos.kill");
	public static final Holder.Reference<SoundEvent> MYTHOS_SUMMON = register("mythos.summon");
	public static final Holder.Reference<SoundEvent> MYTHOS_HURT = register("mythos.hurt");
	public static final Holder.Reference<SoundEvent> MYTHOS_DEATH = register("mythos.death");
	public static final Holder.Reference<SoundEvent> MYTHOS_BLINK = register("mythos.blink");
	public static final Holder.Reference<SoundEvent> MYTHOS_PHASE = register("mythos.phase");
	public static final Holder.Reference<SoundEvent> MYTHOS_TORNADO_WARNING = register("mythos.tornado_warning");
	public static final Holder.Reference<SoundEvent> SUB_AGENT_IDLE = register("sub_agent.idle");
	public static final Holder.Reference<SoundEvent> SUB_AGENT_STEP = register("sub_agent.step");
	public static final Holder.Reference<SoundEvent> SUB_AGENT_HIT = register("sub_agent.hit");
	public static final Holder.Reference<SoundEvent> SUB_AGENT_DEATH = register("sub_agent.death");
	public static final List<Holder.Reference<SoundEvent>> MYTHOS_MUSIC = List.of(register("music.mythos.layer1"), register("music.mythos.layer2"),
		register("music.mythos.layer3"), register("music.mythos.layer4"));
	public static final List<Holder.Reference<SoundEvent>> DOT_MUSIC = List.of(register("music.dot.layer1"), register("music.dot.layer2"),
		register("music.dot.layer3"), register("music.dot.layer4"));
	public static final List<Holder.Reference<SoundEvent>> GUEST_MUSIC = List.of(register("music.guest.layer1"), register("music.guest.layer2"),
		register("music.guest.layer3"), register("music.guest.layer4"));
	public static final List<Holder.Reference<SoundEvent>> ASESINO_MUSIC = List.of(register("music.asesino.layer1"), register("music.asesino.layer2"),
		register("music.asesino.layer3"), register("music.asesino.layer4"));
	public static final Holder.Reference<SoundEvent> DISASTER_CAUSTIC_BURN = register("disaster.caustic_rain.burn");
	/** Disasters, in {@code world.disaster.Disaster} order: the warning, and the sound while it lasts. */
	public static final List<Holder.Reference<SoundEvent>> DISASTER_WARNINGS = java.util.Arrays.stream(com.deadlands.world.disaster.Disaster.values())
		.map(d -> register("disaster." + d.id() + ".warning")).toList();
	public static final List<Holder.Reference<SoundEvent>> DISASTER_SOUNDS = java.util.Arrays.stream(com.deadlands.world.disaster.Disaster.values())
		.map(d -> register("disaster." + d.id())).toList();
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
	// Phase 4 melee (tools/assetgen/gen_weapon_sfx.py).
	public static final Holder.Reference<SoundEvent> WEAPON_SWING = register("weapon.swing");
	public static final Holder.Reference<SoundEvent> WEAPON_HIT_BLUNT = register("weapon.hit_blunt");
	public static final Holder.Reference<SoundEvent> WEAPON_HIT_BLADE = register("weapon.hit_blade");
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

	// The soundtrack (tools/assetgen/gen_music.py). One event per track, so client.sound.MusicDirector
	// chooses the track itself and never repeats the last ones.
	public static final List<Holder.Reference<SoundEvent>> MUSIC_EXPLORE = List.of(register("music.empty_roads"), register("music.what_remains"),
		register("music.overgrown"), register("music.static_lullaby"));
	public static final List<Holder.Reference<SoundEvent>> MUSIC_NIGHT = List.of(register("music.long_night"), register("music.no_lights"),
		register("music.hollow_hours"));
	public static final List<Holder.Reference<SoundEvent>> MUSIC_DANGER = List.of(register("music.pursuit"), register("music.they_hear_you"));
	public static final List<Holder.Reference<SoundEvent>> MUSIC_HORDE = List.of(register("music.horde"));
	public static final List<Holder.Reference<SoundEvent>> MUSIC_SAFE = List.of(register("music.campfire"), register("music.shelter"));

	// The soundscape (tools/assetgen/gen_soundscape.py), played by name from client.sound.AmbienceDirector.
	private static final List<Holder.Reference<SoundEvent>> SOUNDSCAPE = List.of(
		register("ambient.crow_flock"),
		register("ambient.birds_far"),
		register("ambient.leaves"),
		register("ambient.branch"),
		register("ambient.sign_creak"),
		register("ambient.rattle"),
		register("ambient.thunder_far"),
		register("ambient.buzz"),
		register("ambient.window_wind"),
		register("ambient.settle"),
		register("ambient.unexplained"),
		register("ambient.wind_trees_loop"),
		register("ambient.wind_street_loop"),
		register("ambient.rain_roof_loop"),
		register("ambient.rain_metal_loop"),
		register("ambient.room_tone_loop"),
		register("ambient.transformer_loop"),
		register("ambient.menu_loop"));

	// What the infected say when they spot a survivor (alert) and while they chase one (chase),
	// per kind (tools/assetgen/gen_infected_voices.py). The runner's alert existed before.
	private static final List<String> VOICE_KINDS = List.of("walker", "runner", "brute", "screamer", "military", "crawler", "bloater", "stalker",
		"charred");
	private static final java.util.Map<String, Holder.Reference<SoundEvent>> ALERT_VOICES = registerVoices("alert");
	private static final java.util.Map<String, Holder.Reference<SoundEvent>> CHASE_VOICES = registerVoices("chase");

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

	private static java.util.Map<String, Holder.Reference<SoundEvent>> registerVoices(String what) {
		java.util.Map<String, Holder.Reference<SoundEvent>> voices = new java.util.HashMap<>();
		for (String kind : VOICE_KINDS) {
			voices.put(kind, kind.equals("runner") && what.equals("alert") ? INFECTED_RUNNER_ALERT : register("infected." + kind + "." + what));
		}
		return voices;
	}

	/** The cry an infected of this kind gives when it spots a survivor. */
	public static SoundEvent infectedAlert(String kind) {
		return ALERT_VOICES.getOrDefault(kind, ALERT_VOICES.get("walker")).value();
	}

	/** What an infected of this kind makes while it chases a survivor. */
	public static SoundEvent infectedChase(String kind) {
		return CHASE_VOICES.getOrDefault(kind, CHASE_VOICES.get("walker")).value();
	}

	public static List<Holder.Reference<SoundEvent>> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
