package com.deadlands.config;

/**
 * Every DEADLANDS setting, grouped into sections.
 *
 * <p>Each public section field becomes a block in {@code config/deadlands.jsonc} and a category in
 * the settings screen; each {@link ConfigEntry} field inside a section becomes one setting.
 * Declaration order is the order shown to players.
 */
public final class DeadlandsConfig {
	public int configVersion = ConfigManager.CURRENT_VERSION;

	public General general = new General();
	public World world = new World();
	public Zombies zombies = new Zombies();
	public Hordes hordes = new Hordes();
	public Bosses bosses = new Bosses();
	public Loot loot = new Loot();
	public Survival survival = new Survival();
	public Weather weather = new Weather();
	public WorldEvents events = new WorldEvents();
	public Performance performance = new Performance();
	public Horror horror = new Horror();
	public Audio audio = new Audio();
	public Interface ui = new Interface();

	public enum ScarePreset {
		LOW, NORMAL, NIGHTMARE
	}

	public enum SurvivalDifficulty {
		STORY, SURVIVOR, VETERAN, NIGHTMARE
	}

	public static final class General {
		@ConfigEntry(comment = "Give each survivor a Survivor's Journal the first time they join a world.")
		public boolean giveJournalOnFirstJoin = true;

		@ConfigEntry(comment = "Announce the start of each new day to every player with the DAY card.")
		public boolean announceNewDays = true;

		@ConfigEntry(comment = "How many recent events the world chronicle keeps.", min = 8, max = 256)
		public int chronicleLength = 64;

		@ConfigEntry(comment = "Write extra diagnostic lines to the log.")
		public boolean debugLogging = false;
	}

	public static final class World {
		@ConfigEntry(comment = "Replace the overworld's land biomes with the ten DEADLANDS biomes. Read when a world loads; affects chunks generated afterwards.")
		public boolean deadlandsBiomes = true;

		@ConfigEntry(comment = "Generate roads between towns in newly generated chunks.")
		public boolean roadNetwork = true;

		@ConfigEntry(comment = "How decayed roads are: cracks, potholes, missing pavement, weeds (0 = intact).", min = 0.0, max = 1.0)
		public double roadDecay = 0.45;

		@ConfigEntry(comment = "Put power and telephone poles along roads, and electrical pylons along highways.")
		public boolean roadsideUtilities = true;

		@ConfigEntry(comment = "Generate railway lines between towns in newly generated chunks (needs the road network).")
		public boolean railways = true;

		@ConfigEntry(comment = "Abandoned vehicles on and beside the roads.")
		public boolean abandonedVehicles = true;
	}

	public static final class Zombies {
		@ConfigEntry(comment = "Multiplier on how many infected spawn.", min = 0.0, max = 4.0)
		public double populationMultiplier = 1.0;

		@ConfigEntry(comment = "Most infected allowed around a single player.", min = 0, max = 200)
		public int maxPerPlayer = 40;

		@ConfigEntry(comment = "Hard cap on infected loaded in the whole world.", min = 0, max = 2000)
		public int globalCap = 250;

		@ConfigEntry(comment = "Closest distance in blocks an infected may spawn from a player.", min = 8, max = 128)
		public int spawnDistanceMin = 24;

		@ConfigEntry(comment = "Farthest distance in blocks an infected may spawn from a player.", min = 16, max = 256)
		public int spawnDistanceMax = 96;

		@ConfigEntry(comment = "Share of infected that are Runners.", min = 0.0, max = 1.0)
		public double runnerShare = 0.15;

		@ConfigEntry(comment = "How far in blocks the infected hear a gunshot.", min = 16, max = 256)
		public int gunshotHearingRange = 96;

		@ConfigEntry(comment = "Let the infected break player barricades.")
		public boolean breakBarricades = true;

		@ConfigEntry(comment = "Spawn infected around survivors. Hordes, nests and migrating groups are separate.")
		public boolean naturalSpawning = true;

		@ConfigEntry(comment = "Share of the night-time population that roams during the day (the rest hides indoors).", min = 0.0, max = 1.0)
		public double daytimePopulation = 0.5;

		@ConfigEntry(comment = "Nests release infected when they hear survivors nearby.")
		public boolean nestReleases = true;
	}

	public static final class Hordes {
		@ConfigEntry(comment = "Enable horde nights.")
		public boolean enabled = true;

		@ConfigEntry(comment = "A horde comes every this many days.", min = 1, max = 60)
		public int intervalDays = 5;

		@ConfigEntry(comment = "Size of the first horde.", min = 5, max = 300)
		public int baseSize = 30;

		@ConfigEntry(comment = "Growth per horde as a share of the first horde (0.15 = +15% each time).", min = 0.0, max = 2.0)
		public double growthPerHorde = 0.15;

		@ConfigEntry(comment = "Hordes never grow beyond this size.", min = 5, max = 500)
		public int maxSize = 120;

		@ConfigEntry(comment = "Closest distance in blocks horde members spawn from a player.", min = 16, max = 128)
		public int spawnRadiusMin = 32;

		@ConfigEntry(comment = "Farthest distance in blocks horde members spawn from a player.", min = 24, max = 192)
		public int spawnRadiusMax = 64;

		@ConfigEntry(comment = "Survivors cannot sleep through a horde night.")
		public boolean preventSleep = true;
	}

	public static final class Bosses {
		@ConfigEntry(comment = "Enable boss encounters. A boss replaces the horde on its day.")
		public boolean enabled = true;

		@ConfigEntry(comment = "A boss encounter comes every this many days.", min = 2, max = 120)
		public int intervalDays = 10;

		@ConfigEntry(comment = "Boss health multiplier.", min = 0.25, max = 5.0)
		public double healthMultiplier = 1.0;

		@ConfigEntry(comment = "Boss damage multiplier.", min = 0.25, max = 5.0)
		public double damageMultiplier = 1.0;
	}

	public static final class Loot {
		@ConfigEntry(comment = "Multiplier on the chance that a container also holds a rare find.", min = 0.1, max = 5.0)
		public double rarityMultiplier = 1.0;

		@ConfigEntry(comment = "How full containers are when first opened (1.0 = normal).", min = 0.0, max = 3.0)
		public double containerFill = 1.0;

		@ConfigEntry(comment = "Share of town containers that start locked with a keycard or the maintenance key (locks a building was designed with are always kept). Affects newly generated towns.", min = 0.0, max = 1.0)
		public double lockedShare = 0.12;
	}

	public static final class Survival {
		@ConfigEntry(comment = "Survival difficulty: STORY (gentle, never lethal), SURVIVOR, VETERAN, NIGHTMARE.")
		public SurvivalDifficulty difficulty = SurvivalDifficulty.SURVIVOR;

		@ConfigEntry(comment = "Track thirst.")
		public boolean thirst = true;

		@ConfigEntry(comment = "Track body temperature.")
		public boolean temperature = true;

		@ConfigEntry(comment = "Track stamina for sprinting and melee.")
		public boolean stamina = true;

		@ConfigEntry(comment = "Track sleep and fatigue.")
		public boolean sleep = true;

		@ConfigEntry(comment = "Carrying more than you can slows you down.")
		public boolean weight = true;

		@ConfigEntry(comment = "Infected bites can infect you.")
		public boolean infection = true;

		@ConfigEntry(comment = "Multiplier on how fast hunger, thirst and fatigue drain.", min = 0.25, max = 3.0)
		public double drainMultiplier = 1.0;
	}

	public static final class Weather {
		@ConfigEntry(comment = "How often fog rolls in (0 = never).", min = 0.0, max = 3.0)
		public double fogFrequency = 1.0;

		@ConfigEntry(comment = "How often storms happen (0 = never).", min = 0.0, max = 3.0)
		public double stormFrequency = 1.0;

		@ConfigEntry(comment = "Allow dust storms in dry biomes.")
		public boolean dustStorms = true;
	}

	public static final class WorldEvents {
		@ConfigEntry(comment = "Enable announced supply drops.")
		public boolean supplyDrops = true;

		@ConfigEntry(comment = "Average days between supply drops.", min = 1, max = 30)
		public int supplyDropIntervalDays = 3;

		@ConfigEntry(comment = "Enable helicopter and aircraft crash sites.")
		public boolean crashSites = true;

		@ConfigEntry(comment = "Enable distress signals on the radio.")
		public boolean distressSignals = true;

		@ConfigEntry(comment = "Enable hordes that migrate between towns.")
		public boolean migratingHordes = true;
	}

	public static final class Performance {
		@ConfigEntry(comment = "Most infected whose AI runs at full rate at once.", min = 16, max = 1000)
		public int maxActiveAi = 150;

		@ConfigEntry(comment = "Ticks between AI updates for distant infected.", min = 1, max = 40)
		public int distantAiInterval = 4;

		@ConfigEntry(comment = "Ticks between settlement checks: newcomers walking in, walls going up.", min = 20, max = 6000)
		public int farSimulationInterval = 200;

		@ConfigEntry(comment = "Within this many chunks of a survivor, a settlement is left to its real residents instead of the daily simulation.", min = 2, max = 32)
		public int fullSimulationChunks = 6;
	}

	public static final class Horror {
		@ConfigEntry(comment = "How often the Horror Director scares survivors. Normal: at night a major scare every 4 to 8 minutes of active play and something unsettling every 1 to 3; by day a major scare every 10 to 15 minutes.")
		public ScarePreset scarePreset = ScarePreset.NORMAL;

		@ConfigEntry(comment = "Photosensitivity: flashes and flickers become slow fades. Scares still happen.", client = true)
		public boolean photosensitivity = false;

		@ConfigEntry(comment = "How strongly the camera reacts to scares (0 = not at all).", min = 0.0, max = 1.0, client = true)
		public double cameraShake = 1.0;

		@ConfigEntry(comment = "How much the hands move in first person: raising a newly drawn item, breathing, trembling when hurt or chased (0 = still).", min = 0.0, max = 1.0, client = true)
		public double firstPersonMotion = 1.0;

		@ConfigEntry(comment = "Shader compatibility: with a shader pack (Iris) or Sodium, draw the night's darkness and the flashlight beam on the screen, so they work with any pack.", client = true)
		public boolean shaderCompatibility = true;
	}

	public static final class Audio {
		@ConfigEntry(comment = "Play DEADLANDS interface sounds.", client = true)
		public boolean interfaceSounds = true;

		@ConfigEntry(comment = "Volume of DEADLANDS ambience.", min = 0.0, max = 1.0, client = true)
		public double ambienceVolume = 1.0;

		@ConfigEntry(comment = "Muffle infected heard through walls and use distant versions of their voices far away.", client = true)
		public boolean infectedVoiceEffects = true;

		@ConfigEntry(comment = "Play the DEADLANDS soundtrack in the world: exploration, night, danger and safe-zone music that follows what is happening "
			+ "(replaces Minecraft's in-game music).", client = true)
		public boolean dynamicMusic = true;

		@ConfigEntry(comment = "Volume of the DEADLANDS soundtrack in the world, on top of Minecraft's Music slider.", min = 0.0, max = 1.0, client = true)
		public double musicVolume = 0.45;

		@ConfigEntry(comment = "How often the world makes ambient sounds (crows, wind, creaking metal, distant noises). 1 is the intended amount.",
			min = 0.25, max = 2.0, client = true)
		public double ambienceDensity = 1.0;

		@ConfigEntry(comment = "Quiet wind and distant crows under the DEADLANDS main menu.", client = true)
		public boolean menuAmbience = true;

		@ConfigEntry(comment = "How many infected must be chasing a survivor (within 48 blocks, for 2 seconds) before chase music starts.", min = 1, max = 50)
		public int chaseMusicThreshold = 8;

		@ConfigEntry(comment = "Chase music fades once fewer than this many have been chasing for 10 seconds.", min = 1, max = 50)
		public int chaseMusicStopBelow = 5;
	}

	public static final class Interface {
		@ConfigEntry(comment = "Replace the Minecraft title screen with the DEADLANDS menu.", client = true)
		public boolean replaceTitleScreen = true;

		@ConfigEntry(comment = "Show the DEADLANDS backdrop behind other menus.", client = true)
		public boolean menuBackdrop = true;

		@ConfigEntry(comment = "Show the DAY card when a new day begins.", client = true)
		public boolean dayCard = true;

		@ConfigEntry(comment = "Film grain on DEADLANDS menus.", client = true)
		public boolean filmGrain = true;

		@ConfigEntry(comment = "Show a card with the town's name and condition when you enter a town.", client = true)
		public boolean townCards = true;

		@ConfigEntry(comment = "Show the horde card and the horde counter during horde nights.", client = true)
		public boolean hordeHud = true;

		@ConfigEntry(comment = "Show thirst, stamina, temperature, fatigue and injuries on screen.", client = true)
		public boolean survivalHud = true;

		@ConfigEntry(comment = "DEADLANDS health, armour and hunger bars instead of Minecraft's hearts and drumsticks.", client = true)
		public boolean vitalsHud = true;

		@ConfigEntry(comment = "DEADLANDS inventory, crafting and chest screens (without Minecraft's recipe book).", client = true)
		public boolean inventoryScreens = true;

		@ConfigEntry(comment = "Play the story's cinematic moments (any key skips one).", client = true)
		public boolean cutscenes = true;
	}
}
