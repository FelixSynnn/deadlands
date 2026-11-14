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
	public Disasters disasters = new Disasters();
	public Asesino asesino = new Asesino();
	public Guest guest = new Guest();
	public Stealth stealth = new Stealth();
	public Dot dot = new Dot();
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

		@ConfigEntry(comment = "How common towns are (1 = the old spacing of 34 chunks; 1.5 = about one and a half times as many). Read when a world loads; affects newly generated chunks.", min = 0.5, max = 3.0)
		public double townFrequency = 1.5;
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

		@ConfigEntry(comment = "Stalkers' climbing speed as a share of their walking speed.", min = 0.0, max = 1.0)
		public double stalkerClimbFactor = 0.3;

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

		@ConfigEntry(comment = "Natural spawning: share of the local cap that fills up (0.6 = 40% fewer infected than before).", min = 0.0, max = 2.0)
		public double spawnRateFactor = 0.6;

		@ConfigEntry(comment = "Natural spawning: multiplier on how many appear together in a group (at least one).", min = 0.1, max = 2.0)
		public double groupSizeFactor = 0.6;

		@ConfigEntry(comment = "By day, out in the open: most infected around you, as a share of the night's number. Caves and indoors keep the daytime population above.", min = 0.0, max = 1.0)
		public double daytimeOutdoorCap = 0.25;
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

		@ConfigEntry(comment = "Chance that a hit from an infected starts an infection (before the difficulty's multiplier). Was 0.10.", min = 0.0, max = 1.0)
		public double infectionChancePerHit = 0.05;

		@ConfigEntry(comment = "Multiplier on how fast hunger, thirst and fatigue drain.", min = 0.25, max = 3.0)
		public double drainMultiplier = 1.0;

		@ConfigEntry(comment = "Safe respawn: when you come back after dying, hostile creatures and zombies near your respawn point vanish (no loot or experience).")
		public boolean safeRespawn = true;

		@ConfigEntry(comment = "Safe respawn: how far around the respawn point, in blocks.", min = 8, max = 128)
		public int safeRespawnRadius = 50;
	}

	public static final class Weather {
		@ConfigEntry(comment = "How often fog rolls in (0 = never).", min = 0.0, max = 3.0)
		public double fogFrequency = 1.0;

		@ConfigEntry(comment = "How often storms happen (0 = never).", min = 0.0, max = 3.0)
		public double stormFrequency = 1.0;

		@ConfigEntry(comment = "Allow dust storms in dry biomes.")
		public boolean dustStorms = true;
	}

	/** Phase 5: the Guest ({@code entity.GuestEntity}, {@code entity.GuestSpawner}). */
	/** D.O.T. (Deceased On Transfer), Patient-4 ({@code entity.DotEntity}). */
	public static final class Dot {
		@ConfigEntry(comment = "D.O.T., Patient-4: a rare night walker in big structures and on town roads. Passive until provoked.")
		public boolean enabled = true;

		@ConfigEntry(comment = "Chance each minute at night, per survivor, that he comes (when none is near).", min = 0.0, max = 1.0)
		public double spawnChance = 0.04;

		@ConfigEntry(comment = "Spawn him inside big structures (subway stations, the research facility, Site Calder, the school, the power station, the treatment plant).")
		public boolean inStructures = true;

		@ConfigEntry(comment = "Spawn him standing in the road in towns, in your way.")
		public boolean onTownRoads = true;

		@ConfigEntry(comment = "At most one within this many blocks.", min = 32, max = 512)
		public int areaRadius = 160;

		@ConfigEntry(comment = "Damage per hit on Normal (Minecraft scales it for Easy and Hard).", min = 1.0, max = 100.0)
		public double damage = 10.0;

		@ConfigEntry(comment = "Can't be hurt at all. Off (the default): very high health and heavy damage resistance instead; either way, running and hiding is how you survive him.")
		public boolean invulnerable = false;

		@ConfigEntry(comment = "Health (when not invulnerable).", min = 20.0, max = 5000.0)
		public double health = 800.0;

		@ConfigEntry(comment = "Share of incoming damage he ignores (when not invulnerable).", min = 0.0, max = 0.99)
		public double damageResistance = 0.75;

		@ConfigEntry(comment = "Walking speed (he walks; he never runs).", min = 0.05, max = 0.6)
		public double speed = 0.2;

		@ConfigEntry(comment = "Speed while provoked.", min = 0.05, max = 0.8)
		public double aggroSpeed = 0.28;

		@ConfigEntry(comment = "Seconds he keeps after you, once provoked, if you stay away and out of sight.", min = 5, max = 600)
		public int giveUpSeconds = 30;

		@ConfigEntry(comment = "Player names that always provoke him on sight, and that he hunts twice as long (comma-separated).")
		public String alwaysProvokes = "itzbreadloaf";

		@ConfigEntry(comment = "Seconds of looking straight at him that provoke him.", min = 0.5, max = 20.0)
		public double stareSeconds = 2.0;

		@ConfigEntry(comment = "He walks through doors, fences, glass panes and leaves, breaking them (needs mobGriefing).")
		public boolean pushesThrough = true;

		@ConfigEntry(comment = "Climbing speed as a share of his walking speed.", min = 0.0, max = 1.0)
		public double climbFactor = 0.3;

		@ConfigEntry(comment = "His jumpscares (Wrong Frame, Behind the Light, Road Ghost, 404 Death). They respect the Photosensitivity setting.")
		public boolean jumpscares = true;
	}

	public static final class Stealth {
		@ConfigEntry(comment = "Hiding in the dark: hostile DEADLANDS creatures see less far where it is dark, and lose survivors who stay out of sight in the dark.")
		public boolean enabled = true;

		@ConfigEntry(comment = "Share of the normal sight range at light 0 to 3 (it rises to the full range at light 12).", min = 0.05, max = 1.0)
		public double darkRangeFactor = 0.35;

		@ConfigEntry(comment = "Crouching multiplies the sight range by this again.", min = 0.1, max = 1.0)
		public double crouchFactor = 0.5;

		@ConfigEntry(comment = "Highest light level (at the survivor) that counts as darkness for losing a chaser.", min = 0, max = 15)
		public int darkLight = 7;

		@ConfigEntry(comment = "Seconds out of sight, in the dark, before a chaser loses its target.", min = 1, max = 60)
		public int loseSeconds = 5;

		@ConfigEntry(comment = "Seconds a creature searches the last place it saw its target before giving up.", min = 0, max = 120)
		public int searchSeconds = 10;

		@ConfigEntry(comment = "Seconds after a survivor attacks during which they are seen at full range.", min = 0, max = 30)
		public int attackRevealSeconds = 3;
	}

	public static final class Guest {
		@ConfigEntry(comment = "Climbing speed as a share of its walking speed.", min = 0.0, max = 1.0)
		public double climbFactor = 0.3;

		@ConfigEntry(comment = "Minutes after nightfall before the Guest can come (it never appears the moment night falls).", min = 0.0, max = 8.0)
		public double nightDelayMinutes = 3.0;

		@ConfigEntry(comment = "Let the Guest come.")
		public boolean enabled = true;

		@ConfigEntry(comment = "Only at night (it leaves at dawn).")
		public boolean nightOnly = true;

		@ConfigEntry(comment = "Chance per survivor every 30 seconds at night that it comes for them.", min = 0.0, max = 1.0)
		public double spawnChance = 0.12;

		@ConfigEntry(comment = "Most Guests in the world at once.", min = 1, max = 10)
		public int maxAlive = 2;

		@ConfigEntry(comment = "Minutes sheltering in the same spot at night before it is certain to come.", min = 1.0, max = 60.0)
		public double campMinutes = 5.0;

		@ConfigEntry(comment = "Let it tear through doors, glass and wooden walls (also needs the mobGriefing rule).")
		public boolean breaksBlocks = true;

		@ConfigEntry(comment = "Health.", min = 20.0, max = 3000.0)
		public double health = 300.0;

		@ConfigEntry(comment = "Claw damage before Minecraft's difficulty scaling: 3.5 kills an unarmoured survivor in 6 hits on Normal.", min = 0.5, max = 100.0)
		public double damage = 3.5;

		@ConfigEntry(comment = "Damage of the grab-and-slam.", min = 0.5, max = 100.0)
		public double grabDamage = 7.0;

		@ConfigEntry(comment = "Walking speed.", min = 0.1, max = 0.8)
		public double speed = 0.31;

		@ConfigEntry(comment = "Speed while charging.", min = 0.1, max = 1.0)
		public double chargeSpeed = 0.37;

		@ConfigEntry(comment = "How far it notices survivors in sight, in blocks.", min = 16.0, max = 128.0)
		public double followRange = 48.0;

		@ConfigEntry(comment = "Armor.", min = 0.0, max = 30.0)
		public double armor = 10.0;
	}

	/** Phase 5: the Asesino ({@code entity.AsesinoEntity}, {@code entity.AsesinoSpawner}). */
	public static final class Asesino {
		@ConfigEntry(comment = "Let the Asesino hunt.")
		public boolean enabled = true;

		@ConfigEntry(comment = "Only at night.")
		public boolean nightOnly = true;

		@ConfigEntry(comment = "Chance per survivor every 30 seconds in or near a town.", min = 0.0, max = 1.0)
		public double townSpawnChance = 0.25;

		@ConfigEntry(comment = "Chance per survivor every 30 seconds away from towns.", min = 0.0, max = 1.0)
		public double wildSpawnChance = 0.02;

		@ConfigEntry(comment = "No second Asesino within this many blocks of a survivor (about one per town).", min = 32, max = 512)
		public int areaRadius = 160;

		@ConfigEntry(comment = "Most Asesinos in the world at once.", min = 1, max = 20)
		public int maxAlive = 3;

		@ConfigEntry(comment = "Health.", min = 20.0, max = 2000.0)
		public double health = 160.0;

		@ConfigEntry(comment = "Damage per hit.", min = 1.0, max = 100.0)
		public double damage = 9.0;

		@ConfigEntry(comment = "Climbing speed as a share of his walking speed.", min = 0.0, max = 1.0)
		public double climbFactor = 0.3;

		@ConfigEntry(comment = "The Aberrant's lunge: seconds between lunges.", min = 2, max = 60)
		public int lungeCooldownSeconds = 8;

		@ConfigEntry(comment = "The Aberrant's lunge: damage as a share of his slash damage.", min = 0.0, max = 3.0)
		public double lungeDamageFactor = 1.0;

		@ConfigEntry(comment = "Speed while roaming and stalking.", min = 0.1, max = 0.6)
		public double stalkSpeed = 0.26;

		@ConfigEntry(comment = "Speed after 30 seconds of chasing (it rises from the stalking speed).", min = 0.1, max = 0.8)
		public double chaseSpeedMax = 0.34;

		@ConfigEntry(comment = "About how long he stalks someone before he decides to attack, in minutes (he may strike sooner if they give him a chance).", min = 1.0, max = 30.0)
		public double stalkMinutes = 10.0;

		@ConfigEntry(comment = "How far he follows and notices survivors, in blocks.", min = 16.0, max = 128.0)
		public double followRange = 64.0;

		@ConfigEntry(comment = "Knockback resistance, 0 to 1.", min = 0.0, max = 1.0)
		public double knockbackResistance = 0.9;

		@ConfigEntry(comment = "Armor.", min = 0.0, max = 30.0)
		public double armor = 6.0;
	}

	/** Phase 5: disaster weather ({@code world.disaster.DisasterDirector}). */
	public static final class Disasters {
		@ConfigEntry(comment = "Allow disasters: twisters, floods, tremors and more, each announced before it starts.")
		public boolean enabled = true;

		@ConfigEntry(comment = "Average days between disasters.", min = 1, max = 60)
		public int averageDaysBetween = 4;

		@ConfigEntry(comment = "The first day a disaster can happen.", min = 1, max = 100)
		public int firstDay = 3;

		@ConfigEntry(comment = "Let disasters change the world: break blocks, place water and snow, start fires. Off keeps the world as it is.")
		public boolean destructive = false;

		@ConfigEntry(comment = "Twisters that drag and lift anything near them.")
		public boolean twister = true;

		@ConfigEntry(comment = "Flash floods: torrential rain, low ground runs with water.")
		public boolean flashFlood = true;

		@ConfigEntry(comment = "Tremors: the ground shakes in waves.")
		public boolean tremor = true;

		@ConfigEntry(comment = "Skyfall: burning rocks falling around survivors.")
		public boolean skyfall = true;

		@ConfigEntry(comment = "Whiteouts: freezing snowstorms in cold country.")
		public boolean whiteout = true;

		@ConfigEntry(comment = "Dust walls: choking dust storms in hot, dry country.")
		public boolean dustWall = true;

		@ConfigEntry(comment = "Caustic rain that burns anyone outside without a helmet.")
		public boolean causticRain = true;

		@ConfigEntry(comment = "Static storms: lightning that strikes close and often.")
		public boolean staticStorm = true;

		@ConfigEntry(comment = "The Green Tornado (uncommon): a green, spore-thick twister that poisons and sickens instead of tearing things up.")
		public boolean greenTornado = true;

		@ConfigEntry(comment = "How likely the Green Tornado is against the others (twister 9, static storm 12; 3 is uncommon).", min = 0, max = 50)
		public int greenTornadoWeight = 3;

		@ConfigEntry(comment = "The Green Tornado brings its character with it (not in yet: a placeholder).")
		public boolean greenTornadoCharacter = true;
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

		@ConfigEntry(comment = "The flashlight really lights the blocks it points at (invisible light blocks that follow the beam), so it works with Sodium and shader packs.")
		public boolean flashlightLightsBlocks = true;

		@ConfigEntry(comment = "No sleeping while the Aberrant or the Guest is within this many blocks (0 turns it off); a sleeper is woken if one comes this close.", min = 0.0, max = 160.0)
		public double noSleepRadius = 48.0;

		@ConfigEntry(comment = "No sleeping at night: the night can't be skipped, it has to be survived (a bed still sets your spawn point).")
		public boolean noSleepAtNight = true;
	}

	public static final class Audio {
		@ConfigEntry(comment = "Play DEADLANDS interface sounds.", client = true)
		public boolean interfaceSounds = true;

		@ConfigEntry(comment = "Layered chase music when the Asesino is near.", client = true)
		public boolean asesinoMusic = true;

		@ConfigEntry(comment = "The Aberrant's terror radius in blocks: his music's layer 1 within it, layer 2 within half, layer 3 within a quarter, layer 4 while he chases you.", min = 8.0, max = 128.0, client = true)
		public double aberrantTerrorRadius = 40.0;

		@ConfigEntry(comment = "The Guest's terror radius in blocks: its music's layer 1 within it, layer 2 within half, layer 3 within a quarter, layer 4 while it hunts you.", min = 8.0, max = 128.0, client = true)
		public double guestTerrorRadius = 32.0;

		@ConfigEntry(comment = "D.O.T.'s terror radius in blocks: his music's layer 1 within it, layer 2 within half, layer 3 within a quarter, layer 4 while he pursues you.", min = 8.0, max = 128.0, client = true)
		public double dotTerrorRadius = 36.0;

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
		@ConfigEntry(comment = "Music in the main menu: a shuffled DEADLANDS track every visit, never the same one twice in a row.", client = true)
		public boolean menuMusic = true;

		@ConfigEntry(comment = "Main menu music volume (0 mutes it).", min = 0.0, max = 1.0, client = true)
		public double menuMusicVolume = 0.8;

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
