# DEADLANDS: Development Status

- **Version:** 1.0.0 (first release; was 0.9.0-m9-dev)
- **Last updated:** 2026-11-10 (the atmosphere, audio, texture and stability overhaul; the automated
  tests run for the first time since Milestone 2, and pass)
- **Current work:** the **atmosphere, audio, texture and stability overhaul** (branch
  `overhaul/atmosphere-audio-textures`): **done; builds; the 56 server game tests and the client
  game test pass** (see [Build and test results](#build-and-test-results)).
- **Milestone 9 (Polish):** written; passes the automated tests; not played.
- **Milestone 8 (Story):** written in full; passes the automated tests; not played through.
- **Milestone 7 (NPCs):** written in full; passes the automated tests; not played.
- **Milestone 6 (Vehicles):** written in full; passes the server game tests; never driven.
- **Milestone 5 (Survival):** passes the automated tests; not played (stats, injuries, medicine,
  food and drink, skills, weight, weapons, restyled screens; no new equipment slots or station recipes).
- **Milestone 4 (Zombies):** passes the automated tests; the infected have been seen in screenshots
  and hunting in the client test, but not played against.
- **Milestone 3 (World):** passes the automated tests; DEADLANDS worlds generate (towns, roads,
  natural structures found by the client test) but have not been explored by hand.
- **What the tests show, and what they do not.** Milestones 3 to 9 were written without any test
  run, at the project owner's request. The overhaul's brief asked for testing, so the server game
  tests and the client game test were run for the first time and now pass: the game starts, every
  registry, data file and mixin loads, worlds generate, infected and survivors spawn, hunt and
  render, saves reload, and a dedicated server can be joined. That is not playing the game:
  balance, the story from start to end, driving, bosses, hordes and settlements over days have still
  not been tried, and the manual checklists in [docs/TESTING.md](docs/TESTING.md) have not been done
  for Milestones 3 to 9.
- **Last milestone tested by hand:** 2 (Visual foundation): complete and tested

## Toolchain (verified 2026-11-06)

| Component | Version | Source of truth |
|-----------|---------|-----------------|
| Minecraft | 1.21.11 | meta.fabricmc.net game versions |
| Fabric Loader | 0.20.1 | meta.fabricmc.net loader list for 1.21.11 |
| Fabric API | 0.141.6+1.21.11 | maven.fabricmc.net (latest for 1.21.11) |
| Fabric Loom | 1.19.3 (`net.fabricmc.fabric-loom-remap`) | latest stable 1.19 release; official 1.21.11 example mod uses 1.19 |
| Gradle | 9.8.2 (wrapper) | official 1.21.11 example mod |
| Mappings | Mojang official | forward-compatible with the unobfuscated 26.x line |
| Java | 21 toolchain for compiling and running the game; Gradle itself on JDK 25+ | Loom 1.19 requirement |

APIs were checked against Minecraft 1.21.11 sources decompiled by Loom and against the Fabric API
0.141.6 sources, not guessed. 1.21.11 details that matter: `ResourceLocation` is
`net.minecraft.resources.Identifier`; `Util` moved to `net.minecraft.util`; permissions use
`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`; buttons render through
`AbstractButton.renderContents`; key bindings need a `KeyMapping.Category`; block entities save
through `ValueInput` / `ValueOutput`; block entity renderers use the render-state API
(`createRenderState`, `extractRenderState`, `submit` to a `SubmitNodeCollector`); vanilla's
`BlockEntityType` constructor is private (use `FabricBlockEntityTypeBuilder`); the item `chain`
is now `iron_chain`. Milestone 4 adds: `Mob.serverAiStep` and `Mob.getLootTable` are final; game
rules are read with `getGameRules().get(GameRules.SPAWN_MONSTERS)`; entity models extend
`EntityModel<RenderState>` and animate keyframes with `AnimationDefinition.bake(root)` and
`KeyframeAnimation.apply(millis, scale)`.

### How the code was checked before it was compiled

Before the first compile, these checks were run over the sources (scripts, not the game):

- every import resolves to a class in the decompiled Minecraft or Fabric API sources, or in the mod;
- every `Type.member` used statically exists in that type;
- every method name called anywhere is declared somewhere (Minecraft, Fabric, the JDK or the mod);
- every translation key used in Java exists in `en_us.json`, including the dynamically built ones
  (containers, town sizes, landmarks, settings, creative tabs);
- every item, block, sound event, setting, creative tab, entity look and entity loot table has the
  assets the server game tests look for;
- the mixin target, entity builder, sleep event, renderer and emissive layer signatures were read
  in the sources.

These catch missing classes, typos and missing assets. The compile then confirmed argument types,
generics and overloads. Logic errors, mixins, data files and everything visual still need a run.

## Atmosphere, audio, texture and stability overhaul (builds; automated tests pass)

Every change is listed in [CHANGELOG.md](CHANGELOG.md). The state before the overhaul is tagged
`backup/pre-overhaul-0.9.0-m9-dev`. Everything added is original (synthesised or painted by the
generators in `tools/assetgen`): no samples, licensed music or third-party art.

| System | What exists | Where |
|--------|-------------|-------|
| Stalker crash fix | The client crashed (`ClassCastException`: `StalkerEntity` cannot be cast to `Sniffer`) when a Stalker pounced: the pounce was entity event 63, which vanilla's packet handler takes as the Sniffer's. Every DEADLANDS entity event now lies at 100 and above, with a server test that compares them with vanilla's ids and a client scene that sends every event and provokes a real pounce | `entity/DLEntityEvents`, `InfectedEntity`, `StalkerScenes` |
| Soundtrack | 13 original pieces; a director picks exploration, night, safe-zone, danger or horde music from what is happening, fades between moods, avoids repeats and leaves silence between quiet pieces; Minecraft's own music manager is held back while it plays | `client/sound/MusicDirector`, `client/mixin/MusicManagerMixin`, `gen_music.py` |
| Soundscape | Profiles for 13 kinds of place, each a set of layered one-shots with their own spacing, distance, height and weather, plus loops for wind, rain on the roof, room tone and power hum; at most three one-shots at once, never two within 2 s; a quiet bed under the main menu | `client/sound/AmbienceDirector`, `client/sound/MenuAmbience`, `gen_soundscape.py` |
| Infected voices | New voices for every kind; alert and chase voices driven by a synced hunting flag; idle groans thin out in a crowd; at most six voices at once | `client/sound/InfectedVoices`, `InfectedEntity.FLAG_HUNTING`, `gen_infected_voices.py` |
| Main menu | A new backdrop (a dead city street at sunset) with drifting fog, light shafts, swaying weeds and crows; layout and functions unchanged | `client/ui/TitleBackdrop`, `gen_menu.py` |
| Infected looks | New skin and faces, four new outfits (66 looks), six wear overlays per rig and a per-infected tint | `client/render/infected/InfectedRenderer`, `InfectedWearLayer`, `gen_infected.py` |
| Weapons | Every weapon icon repainted; six new weapons (survival knife, hunting knife, rusty machete, nail bat, lead pipe, pipe wrench) with recipes and loot; handheld models | `registry/DLWeapons`, `gen_weapons.py` |
| Blocks | Per-position variants for 29 blocks (19 DEADLANDS, 10 vanilla); ten new weathered blocks | `registry/DLBlocks`, `gen_blocks.py`, `gen_variants.py` |
| Vanilla biomes | The vanilla biomes DEADLANDS keeps get murky water and the DEADLANDS sky and haze | `world/DLWorldgen` |
| Embers | Drawn full-bright and cooling as they rise (they turned almost black at night) | `client/particle/DLParticleProviders` |
| Settings | *Audio*: DEADLANDS soundtrack in the world, Soundtrack volume, How often ambient sounds play, Wind and crows on the main menu | `config/DeadlandsConfig` |

### Build and test results

Run on 2026-11-10 on Windows 11 (Gradle on JDK 26, the game on Java 21):

| Check | Result |
|-------|--------|
| `dev.ps1 build` | compiles; **`All 57 required tests passed :)`** (the 56 DEADLANDS server game tests and one that is not part of DEADLANDS); `BUILD SUCCESSFUL` in 24 s |
| `dev.ps1 runClientGameTest` | **64 checks pass**, `DEADLANDS client game test passed`, `BUILD SUCCESSFUL` in 3m 53s |
| Startup | `DEADLANDS 0.9.0-m9-dev ready in 446 ms: 100 blocks, 90 items, 9 entity types, 188 sounds, 15 creative tabs` |
| Stalker regression | every infected entity event sent to client-side Stalkers starts its animation; a Stalker with a target pounces and the client plays the pounce and its sound; ten Stalkers spawned, signalled and removed five times over; no crash |
| Audio in the client | the menu bed plays; the director owns the music and wants exploration by day, danger with hunting infected near (threat 4.8; a danger track reaches the sound engine), safe-zone music with two settlers close and night music after dark, and fades the danger out once the infected are gone; one-shots play in the open; rain rings on a metal roof and drums on a wooden one; alert and chase voices play |
| Crowd: 40 infected hunting the survivor for 10 s at night | **116 fps on average, lowest 102**; 25 voices started, **never more than 6 at once**; 458 infected sounds in all (354 step, 76 attack, 14 breath, 11 chase, 3 hurt); live heap **339 MB before, 339 MB after** |
| Resource reload (F3+T) in a world | reloads without an error, the world carries on, and the same daylight view looks the same (mean brightness 101.5 before, 101.5 after) |
| Screenshots reviewed | the main menu, the infected (lineup, close-up, dusk), weapons in the hotbar and in hand, block variants, the new blocks, embers at night |
| Jar | 48.8 MB (about 21 MB before the overhaul; the soundtrack is 17 MB, streamed rather than held in memory) |

Frame rates come from a test window on this machine while another Minecraft instance was running,
so they show headroom rather than a benchmark.

### Decisions and limitations

1. **The main-menu reference image was not available.** The brief asked for the menu to match an
   image provided earlier; it was not in this session or on disk. The backdrop follows the written
   description (a ruined skyline at sunset in fog, abandoned buildings and vehicles, overgrowth, a
   damaged road; the title upper left, the menu down the left, the build stamp upper right). Share
   the image to have it matched.
2. **No connected textures.** Minecraft 1.21.11 has no connected-texture support; it needs a
   renderer mod (Continuity, Fusion) or custom baked models for every block. Per-position variants
   with matching edges (vanilla's own mechanism, as grass uses) break up repetition instead.
3. **The audio has been measured, not listened to.** Levels, peaks, loop seams, lengths and spectra
   were checked, and the client test confirms that the right sounds start at the right moments;
   nobody has listened to the mix in game yet.
4. **The music director only runs in a world.** Menus keep Minecraft's music manager and the title
   theme.
5. **The DEADLANDS biomes' own ambient additions were made five times rarer** to leave room for the
   new soundscape; their sounds are unchanged.

## Milestone 9 (passes the automated tests; not played)

| System | What exists | Where |
|--------|-------------|-------|
| Weather | A weather director decides every half day, weighted by the fog, storm and dust-storm settings: clear, fog, wind or dust, with a strength. Storm frequency also scales vanilla rain (0 keeps the sky clear). Clients draw it: denser, tinted fog; dust only in dry biomes; grit on the wind; ash in burned biomes; a wind loop | `world/weather/WeatherDirector`, `client/weather/ClientWeather`, `client/mixin/AtmosphericFogMixin` |
| World events | Rolled once a day: a supply drop every `events.supplyDropIntervalDays` (a crate falls under smoke, lands as a military crate with its own loot, and makes a loud noise the infected hear); otherwise a one-in-two chance of a crash site, a convoy, a bandit patrol, a distress call or a power outage. Each is announced (chat, a title card, the chronicle) and the emergency radio repeats the last day's | `event/WorldEvents`, `event/SupplyDropEntity`, `client/render/SupplyDropRenderer` |
| Location ambience | The server tells each survivor what kind of place they are in (open country, forest, suburb, city, hospital, school, industrial, farm, campsite, bunker, underground). The client plays that place's one-shots (22 sounds: birds, crickets, owls, dogs, chimes, banging doors, glass, car alarms, flickering lights, drips, gurneys, lockers, steam, groans, vents, radios), muffles outdoor ones indoors, and runs wind and electrical hum loops | `audio/LocationTracker`, `client/sound/AmbienceDirector` |
| Cutscenes | Scripted camera shots with letterbox, subtitles and fades, skippable, each seen once per survivor: the opening (after the introduction), the first boss, the missing research, the first horde, the cure, the extraction. *Interface > Cutscenes* turns them off | `cutscene/Cutscenes`, `client/cutscene/CutscenePlayer`, `client/cutscene/CutsceneScreen`, `client/mixin/CameraMixin` |
| Particles | Ash, dust, blood, spores and embers: blood when infected and bosses are hurt, spores at nest cores, embers for muzzle flashes, supply-drop smoke and crash sites, dust and ash in the weather | `registry/DLParticles`, `client/particle/DLParticleProviders` |
| Firearms | Recoil kicks the camera; a muzzle flash | `item/FirearmItem`, `bridge/ClientBridge.recoil`, `client/DeadlandsClient` |
| Boss bar | A themed boss bar replaces vanilla's | `client/hud/BossBarHud` |
| Textures | A finishing pass over 544 DEADLANDS block and item textures and the vanilla blocks DEADLANDS restyles: light from the upper left, hue-shifted shading, a five-step ramp, calmer saturation, coloured item outlines. Originals in `texture-backup/` | `tools/assetgen/dl_realism.py` |
| The four characters | Their supplied skins (slim arms, drawn with the slim player model), allies who never target or hurt each other and join each other's fights within 24 blocks, met on the road while exploring (see below), and only one of each per world | `npc/NpcEntity`, `npc/NpcSpawns`, `npc/NpcWorldData`, `client/render/NpcRenderer` |
| Commands | `/deadlands event <kind>` starts a world event; `/deadlands weather <kind>` sets the weather | `command/DeadlandsCommand` |

**Meeting the characters.** Every half minute, a survivor who has covered at least 48 blocks since
the last check may meet the next character not yet placed, 24 to 40 blocks away on open ground. The
chance is 1.2% (`NpcSpawns.CHARACTER_ENCOUNTER_CHANCE`) for the first 40 minutes of exploring since
anyone was last met, then rises to certain at an hour (the count is saved with the world). That is
about 27 minutes per character on average (one in ten within 5, half within 29, nine in ten within
45, never more than 60) and about 82 minutes for all three after Harley, who is at the opening as
before. Settler towns no longer hide a character among their
residents. The story's fallback (characters given a place two days after Harley is met) still
applies, so the story never stalls.

**One of each per world.** `deadlands_npcs.dat` records who has been placed (as before) and now also
which entity each character is and whether one was lost (`/kill` or the void, the only things that
can hurt them). Any other copy is removed when it first ticks; `/deadlands npc <name>` refuses a
character who has been placed unless they were lost. Both new fields are optional, so older worlds
load unchanged (in a world where an older version placed a character twice, the first copy to tick
is kept).

**Names.** Characters are survivors, not players: their name is on their nametag and they never
appear in the player list. A player with the same name keeps their own name, skin and list entry,
whoever arrived first.

### Review of Milestones 1 to 9 (fixes)

- Settlements: the arrival check now runs at *Performance > Far simulation interval* (was a fixed
  ten seconds), and a settlement with a survivor within *Full simulation distance* is left to its
  real residents instead of the daily abstract losses and arrivals. Both settings were declared but
  unused.
- Theft memory (settlements) and half-tied tow ropes are forgotten when a survivor leaves the server.
- Harley no longer takes the field case from a survivor if it was already delivered.
- *General > Debug logging* now logs world events and boss nights.
- The roadside shack and supply cache keep 9 chunks away from towns (`exclusion_zone`), as the
  other wilderness structures do.
- The `[Milestone N]` tags are gone from every setting: all 69 do something.
- Version 0.9.0-m9-dev.

## Milestone 8 (see the status at the top)

| System | What exists | Where |
|--------|-------------|-------|
| The opening | A survivor's first arrival: Harley stands a few blocks away and, the first time, her camp is built around her (tent, lit fire, workbench, a crate of supplies). The survivor is given a radio, bandages, water and food. A skippable black-screen introduction types out the outbreak from day -90 to day 0; when it ends, Harley speaks and the first quest begins | `story/StoryIntro`, `client/ui/IntroScreen` |
| Quests | **The Missing Research**: investigate a hospital, the Kessler research facility and a Meridian evacuation site, recover Harley's field case from her expedition camp, find four of her research notes; Harley then reveals the army knew. **The Cure**: bring her hospital samples and records, the Kessler documents, Patient Zero's records, the army's research, the experimental formula and trophies from two bosses; she needs a day. **A Way Out**: learn where Site Calder is, reach it, fit the helicopter's four parts. Progress is the world's (co-operative) and saved | `story/Quests`, `story/StoryState` |
| Recurring characters | Each has a quest: Itzbreadloaf wants engine parts and tires for his van; Imlilylol wants a radio tower working so she can reach everyone (she then reveals Site Calder and where FelixSyn is); FelixSyn wants the evacuation orders and the Warden put to rest (he gives a blue keycard). Those helped join the extraction. They recognise a survivor who has read their journals. Their story progress is saved. Once their histories are told they point the way to the next of the four. If nobody has met one two days after Harley, the story gives them a place (FelixSyn near a hospital, the others in held towns) and they appear when a survivor comes near; trail notes and radio calls point there. They cannot die | `story/Quests`, `story/StoryDirector`, `npc/NpcDialogue` |
| Lore | 134 original entries across nine shelves (Harley's research, character journals, military reports, medical records, government documents, newspapers, radio transcripts, missing persons, personal journals and photographs), with multi-part series and one timeline. They turn up as documents, journals, newspapers, tapes and photographs in containers that fit them (a school locker, a clinic cabinet, a police locker...), more often at story sites; some only at Kessler, Site Calder, the evacuation and expedition camps, or on a boss. Reading one adds it to the archive; some reveal things to the story (Site Calder, what the army knew) | `story/Lore`, `story/LoreEntries` (generated), `story/StoryLoot`, `item/DocumentItem` |
| Discovery archive | Saved with the survivor and kept through death. The journal's new ARCHIVE tab shows the shelves with how much of each has been found and opens any entry to read again | `story/Archive`, `client/ui/JournalScreen`, `client/ui/DocumentScreen` |
| Journal | The TASKS tab is now QUESTS: every active quest with its objectives (done, to do, and which way and how far), the vehicle being restored, and the factions' regard, flowing over both pages | `progression/Tasks`, `client/ui/JournalScreen` |
| Radio | A hand-held radio and its tuner (a dial, a signal meter, typed-out transmissions, static that rises as the signal falls). 87.5 emergency broadcasts with tonight's threats; 91.2 survivors calling the roll, distress calls with coordinates, the recurring characters calling from where they are; 96.4 military (needs a repaired tower) with Site Calder's grid reference and contact reports days before each boss; 103.8 something speaking (needs two towers) that knows where Wing D is; 108.0 static, a song, a numbers station. Radio towers have a dead transmitter that radio parts and a wrench repair; each repaired tower strengthens the signal for everyone | `story/Radio`, `client/ui/RadioScreen`, `block/RadioTransmitterBlock` |
| Keycard doors | Wall-mounted keycard readers (green, blue, red) and security doors that only they open (for three seconds, like a button). At the Kessler facility (green lab, red Wing D) and Site Calder (blue command post, red armoury) | `block/KeycardReaderBlock`, `registry/DLStory` |
| Story sites | The Kessler research facility (lobby, LANTERN lab, Wing D's cells); Site Calder (a fenced compound, a painted pad, a hangar with the helicopter, the command post and armoury); Harley's expedition camp; the Meridian evacuation camps as their own structure. Each is a structure the story can find | `gen_sites.py`, `story/StoryPlaces` |
| Bosses | The Abomination (Ward C; slam, grab and throw, sheds crawlers), the Butcher (Leon Vask; chain hook, bleeding cleaver, enrages), the Warden (Colonel Hale; riot shield turns frontal blows, bash, stamp, calls runners), the Experiment (Subject E-12; leaps, caustic spit, regenerates unless burning), Patient Zero (Elias; blinks, summons, drains life, brings darkness). Each has its own rig, texture, animations for every attack, four synthesised sounds, a boss bar and a title card with a sting. On boss nights one comes at dusk 40 to 56 blocks from a survivor with an escort; nobody can sleep; it retreats at dawn. Later passes are half again as strong per pass. Each drops its trophy (a cure ingredient; the Warden's is a red keycard), its lore, rounds and medicine. Saved across restarts | `entity/BossEntity`, `registry/DLBosses`, `horde/BossDirector`, `client/render/boss/`, `client/hud/BossCardHud` |
| The helicopter | At Site Calder: missing its rotor blade, turbine parts, avionics and fuel (found in Calder's containers); fitted with a wrench. Whole, it asks what to do: leave together, stay behind with the cure, or not yet. It spins up, everyone near climbs in, Harley and those who joined are brought aboard, and it lifts away | `story/HelicopterEntity`, `client/render/HelicopterRenderer` |
| Endings | **Extraction** (cure done, everyone leaves), **Rescue** (leave before the cure is done), **Staying Behind** (the helicopter takes Harley; the survivor carries the cure to the settlements, which are restocked), and a secret **Patient Zero** ending (defeat Patient Zero, bring Harley his blood, read all six of his records, tell her about Elias). Each has an ending screen naming who came; the world goes on | `story/Endings`, `client/ui/EndingScreen` |
| Commands | `/deadlands boss <kind>` brings a boss at once (it counts for the story) | `command/DeadlandsCommand` |
| Tests (not run) | `DeadlandsStoryGameTest` (8): lore and its text, documents and where they turn up, story state saving, quest conditions, the secret ending's conditions, radio and story text, keycard levels, bosses and their scaling | `src/gametest/` |

**Limits of what was written:** the opening and the endings are text screens (Milestone 9 adds a camera cutscene after the opening);
the helicopter does not fly anywhere you can follow; story progress is shared by everyone in the
world; story places are found from Harley's camp up to about 1,600 blocks away; boss models are
box rigs; only DEADLANDS containers carry lore.

## Milestone 7 (see the status at the top)

| System | What exists | Where |
|--------|-------------|-------|
| Survivors | One entity with ten roles (settler, guard, trader, doctor, mechanic, farmer, leader, bandit, travelling merchant, researcher), each person with a generated name, one of three looks for the role, one of four temperaments, a faction and a home. Drawn with Minecraft's player model and 64x64 skins | `npc/NpcEntity`, `npc/NpcRole`, `client/render/NpcRenderer` |
| Routine | By day they keep within sixteen blocks of home; at night everyone but the fighters goes back and stays within three. Guards, leaders and bandits keep walking the perimeter | `NpcEntity.applyRoutine` |
| Fighting | Fighters attack the infected, whoever hurts them or their neighbours, fighters of a faction they are at war with, and survivors their faction holds hostile. Everyone else runs from the infected. The infected now hunt survivors as well as players | `NpcEntity.registerGoals`, `entity/InfectedEntity` |
| Factions and reputation | Settlers, Military, Bandits (start hostile), Drifters, Research Team (starts friendly). Reputation per faction from -100 to 100, saved with the player and kept through death: up for trading, killing infected near their people and restoring power; down for hurting or killing them. Five standings (hostile, wary, neutral, friendly, trusted) set prices, free services and whether fighters attack on sight | `npc/Faction`, `npc/Reputation` |
| Conversation | Using a survivor opens a DEADLANDS conversation screen: who they are, how their people regard you, a greeting that depends on temperament, standing and whether they are grieving, and replies as buttons. Rumours name a real nearby town, its direction, distance and what became of it (including a settlement that has fallen) | `npc/NpcDialogue`, `client/ui/DialogueScreen` |
| Work | By day non-fighters go to their job near home and do it where it can be seen: farmers tend crops (which grow under their hands) and sow bare farmland, mechanics slowly mend the nearest vehicle, doctors heal hurt neighbours, the rest work at a workbench, crafting table or fire | `npc/NpcWorkGoal` |
| Belongings | Each carries a few things of their own (food, water, perhaps bandages or rounds) and the tool of their trade in hand (guards a crowbar or bat, leaders a machete, mechanics a wrench...). They bandage and feed themselves from it when badly hurt, and drop it when they die. Saved | `NpcEntity.stock` |
| People | Half of all survivors have a partner, sibling (sharing a surname) or friend among the neighbours. They talk about them ("Do you have anyone?"); when one dies the other grieves (their greeting changes), and whoever killed them loses more reputation and is attacked by the other if they are a fighter. Saved | `NpcEntity.link`, `die` |
| Voices | Wordless synthesised voices: murmurs, greetings, shouts when a fighter picks a target or sees a theft, cries when hurt, a death cry. Each survivor's voice has its own pitch | `NpcEntity`, `gen_npcs.py` |
| Trading | Traders, doctors, mechanics, farmers and travelling merchants each sell and buy from their own list through Minecraft's trading screen, priced in 9 mm rounds, with limited stock and prices that follow standing | `npc/NpcTrades` |
| Services | Doctors treat wounds, bleeding, fractures and sickness (six rounds; free to friends), but not infection. Mechanics improve the nearest vehicle's engine, tires and body (ten rounds; free to the trusted). Leaders tell the town's history, whether it has power and how the settlement is doing, and take gifts: food, water and medicine fill the stores, planks, logs, stone and iron raise the defences, and both raise reputation | `NpcDialogue` |
| Settlements | Survivor holdouts, military checkpoints and bandit towns are inhabited: most buildings hold a resident suited to the building (a doctor in the clinic, a mechanic in the garage, a trader in the shops, the leader in the town hall) | `world/town/TownBuildingPiece.populate`, `block/SpawnMarkerBlock` |
| Settlement life | Each inhabited town has a saved record: population, stores, defences, morale and what happened last. Once a day, loaded or not, it eats and forages, may be attacked (harder as the days pass; held off by defences and numbers), may go hungry, lose people or fall, and gains newcomers when it is doing well. Residents lost while nobody was near are gone when their chunk loads; newcomers walk in when a survivor is close; a fallen town's people are gone. Short stores make traders dearer | `npc/Settlements` |
| Access and theft | The wary are told to leave a settlement and, after 25 seconds, treated as trespassers its fighters attack until they go. Military towns keep a curfew after dark for anyone they do not count as a friend. Going through a settlement's containers where a resident can see is theft: reputation falls and the witness shouts | `npc/Access`, `Settlements.containerOpened` |
| Horde defence | Killing infected near residents while a horde is on earns more reputation than usual and raises the settlement's defences and morale | `spawning/InfectedSystems` |
| Camps | Two survivor camps (lit fire, a few drifters and a travelling merchant) join the abandoned campsites; a research camp of Harley's people (two researchers selling medicine and buying tissue, a guard) joins the abandoned research outpost | `gen_sites.py` |
| On the road | By day a travelling merchant sometimes appears near a survivor in the open, stays ten minutes and moves on | `npc/NpcSpawns` |
| Recurring characters | Harley, Itzbreadloaf, Imlilylol and FelixSyn, each with a role, a greeting and a history told a part at a time (how far each survivor has heard is saved and kept through death); once a history is told, they point the way to the next of the four if the world has placed them, with direction and distance. Harley stands near where the first survivor arrives; the other three each turn up once (in Milestone 9: met while exploring), so the order differs by world, and the world remembers who has been placed and where. Skins: shipped with the mod (since Milestone 9 the supplied ones from `character-skins/`), replaced by a file at `config/deadlands/skins/<id>.png`; never looked up by username | `npc/NpcCharacter`, `npc/NpcWorldData` |
| Commands | `/deadlands npc <role or character>` (game masters); it is also the fallback if a character is lost | `command/DeadlandsCommand` |
| Tests (not run) | `DeadlandsNpcGameTest` (9): standing and reputation, trades, identity and characters, spawn markers, text and skins, settlement records and simulation, access rules, relationships and belongings (with saving), where characters are | `src/gametest/` |

**Since then:** towns build walls as their defences grow (barricades either side of each gate, in
three stages, built while a survivor is near enough for the gates to be loaded); survivor and
research camps are small settlements of their own, simulated like towns; the characters' quests
and storyline are Milestone 8's, above.

**Limits of what was written:** voices are wordless; theft is only noticed at DEADLANDS
containers; walls are built in stages, not repaired or torn down; a settlement-lost survivor is
only seen gone when the area is loaded again.

## Milestone 6 (see the status at the top)

| System | What exists | Where |
|--------|-------------|-------|
| Vehicles | Twelve kinds, each its own entity type with its own speed, acceleration, turning, tank, fuel use, seats, load space and strength: sedan, pickup, SUV, military jeep, ambulance, police car, delivery van, bus, fire truck, cargo truck, motorcycle, tractor | `vehicle/VehicleKind`, `vehicle/VehicleEntity`, `registry/DLVehicles` |
| Condition | Engine, tires and body (0 to 100 each), a battery, fuel in litres, and a finish (faded, rusted, clean, or burned out). Saved with the vehicle and synced. Worn tires and a rough engine lower the top speed | `VehicleEntity` |
| Restoration | A found vehicle is usually broken two or three ways (about one in six is burned out for good). Using it lists what it needs; using it with engine parts, a tire, a battery or a body panel fits the part, provided you carry a wrench. It drives once the engine and tires reach 50, it has a battery and the body is not destroyed. Fitting parts trains Engineer | `vehicle/VehicleParts` |
| Driving | The driver's movement keys steer it; the driver's client moves it and the server follows, as with Minecraft's boats. Faster on paved ground than off it. It climbs one block. Passengers ride in the other seats | `VehicleEntity.drive` |
| Fuel | Driving burns fuel by distance. A fuel can holds 20 litres: fill it at a pump, pour it into a tank. Each fuel pump holds what was left in it (a little over half are dry, the rest 25 to 150 litres), decided by its position and then only ever going down, saved | `item/FuelCanItem`, `block/FuelPumpBlock`, `vehicle/FuelPumpBlockEntity` |
| Storage | Sneak and use a vehicle to open its load space (1 to 6 rows by kind), saved with it | `VehicleEntity.openStorage` |
| Lights and horn | With a battery the driver can switch the headlights (G): an invisible light block kept a few blocks ahead lights the road, and the lamps glow on the model. The horn (H) is loud to everything, the infected included. Saved | `VehicleEntity.control`, `network/VehicleControlPayload` |
| Towing | Use a tow rope (crafted from string and iron) on the vehicle that will pull, then on the one to be towed: it follows on the rope, facing the puller, and the puller is a third slower. Use the towed one again to untie it and get the rope back. Too far apart, a passenger climbing in, or either destroyed, and the rope comes off | `VehicleEntity.rope`, `tickTow` |
| Restoration task | Looking over a vehicle that does not run makes it your restoration task: the journal's new TASKS page names it, where it was left, and what it still needs, kept up to date. Getting it running with fuel in it finishes the task (Engineer experience). Saved with the player. The same page shows your standing with every faction | `progression/Tasks`, `client/ui/JournalScreen` |
| Damage | Attacks wear the body; at zero everyone is thrown out and it is a wreck (fire leaves it burned out). Driving into the living hurts them badly and costs bodywork and sometimes engine | `VehicleEntity.hurtServer`, `runOver` |
| Noise | A moving engine is a moderate or loud noise every second and a half: the infected hear you coming | `VehicleEntity.serverTick` |
| In the world | Abandoned vehicles on and beside roads between towns (about one every 270 blocks, in a lane or run into the ditch), parked at the kerb in towns (more in towns people fled than in those still lived in; military vehicles in checkpoint towns), beside ranger stations and hunting cabins in the forests, and at gas stations, checkpoints, evacuation camps and farmsteads. About a quarter of found vehicles are overgrown, and the growth has to be pulled off first. About six in ten found vehicles carry cargo, rolled when the load space is first opened. Setting: *World > Abandoned vehicles*. Parts, wrenches and fuel cans in garage, workshop, utility and gas station loot. Gas stations already existed; their pumps now work | `vehicle/VehicleSpawns`, `world/road/RoadFeature` |
| Models and textures | Twelve box-built rigs (body, cabin, load box or bed, light bars, wheels on their axles, and inside: seats, dashboard, steering wheel) generated as Java with their textures in four finishes; the glass is see-through, open cabs (jeep, tractor) have no sides, and closed cabs have front doors that swing when someone gets in or out. Wheels turn with distance, the front pair steers, and wheels are missing where tires are gone. Four overlays per kind: dents and scratches below 70 body, a battered body with rust holes and cracked glass below 35, overgrowth, and the glowing lights. 96 textures. Review sheets: `tools/assetgen/_cache/vehicles_preview.png`, `vehicle_overlays.png` | `client/render/vehicle/`, `gen_vehicles.py` |
| Dashboard | While riding: the vehicle's name, LIGHTS or TOWING when on, fuel in litres, and engine, tires and body gauges | `client/hud/VehicleHud` |
| Sounds | A seamless engine loop that follows each driven vehicle and rises with its speed; impact, repair, a two-tone horn and door sounds. Synthesised (doors are Minecraft's) | `gen_vehicles.py` |
| Commands | `/deadlands vehicle <kind>` places a restored, fuelled vehicle; add `abandoned` for a random wreck (game masters) | `command/DeadlandsCommand` |
| Tests (not run) | `DeadlandsVehicleGameTest` (6): kinds and textures, restoration, saving condition and cargo, fuel pumps, wear levels, overgrowth and lights with saving and the overlay textures, rugged-vehicle markers | `src/gametest/` |

**Since then:** drivers hold the wheel and passengers rest their hands in their laps; a towed
vehicle can tow another, up to three behind one, and the rope is drawn between them.

**Limits of what was written:** doors are only the front pair; dents and cracks are painted
overlays, not changes to the shape; there are no trailers.

## Milestone 5 so far (see the status at the top)

Survival stats, injuries, treatment, food and drink, skills, carrying weight and first weapons.
**Not done:** a new inventory layout with extra equipment slots (a worn backpack, holster, radio),
crafting stations with recipes of their own, item categories and sorting, and a recipe list to
replace Minecraft's recipe book. The new screens restyle Minecraft's menus; they do not add slots.

| System | What exists | Where |
|--------|-------------|-------|
| Body state | Each survivor carries thirst, stamina, body temperature, fatigue, infection and injuries (bleeding, fracture, sickness), saved with the player and reset on respawn. Hunger stays Minecraft's food bar. Creative and spectator players are exempt | `survival/SurvivalData`, `SurvivalAttachments` |
| Thirst | Drains over about a day and a half (faster sprinting, in heat, when sick). Low thirst halves stamina recovery; at zero it hurts | `survival/SurvivalSystem` |
| Stamina | Sprinting, jumping and melee swings spend it; it returns when you stop. At zero you are winded: no sprinting and weaker hits until it is back above a quarter. Fatigue lowers the maximum | `SurvivalSystem`, `mixin/PlayerMixin` |
| Temperature | Body temperature drifts toward the surroundings: biome, time of day, height, shelter, being wet, fires within 5 blocks, armour worn, sprinting. Cold burns food and freezing hurts and slows; heat burns water and overheating hurts | `survival/Temperature` |
| Fatigue | Builds over two and a half days awake; a full night's sleep clears it. Exhaustion slows and weakens | `SurvivalSystem` |
| Injuries | Infected hits can cause bleeding and infection; hard hits bleeding; long falls a fracture. Bleeding hurts until bandaged (light bleeding can clot). A fracture slows you until splinted. Bad food and dirty water make you sick | `SurvivalSystem.afterDamage` |
| Infection | Three stages over about three days, ending in death unless treated; a survivor who dies of it gets up as a walker with their name. Antiseptic cures it early, antibiotics push it back, antiviral serum beats a late one | `SurvivalSystem`, `SurvivalDamage` |
| Difficulty | `survival.difficulty`: STORY (slow, and never lethal), SURVIVOR, VETERAN, NIGHTMARE. Each system has its own on/off setting, plus `drainMultiplier` | `survival/SurvivalRules` |
| Food and drink | 9 foods (canned beans, soup, fruit, jerky, crackers, energy bar, field ration, stale bread, blown can) and 6 drink items (bottle of water, dirty water, soda, energy drink, empty bottle, purification tablets). Quality is the kind of food: sealed is safe, stale is a gamble, a blown can usually sickens. An empty bottle fills with dirty water at any water; tablets, a campfire or a furnace make it clean | `registry/DLSurvivalItems`, `item/SurvivalItem`, `item/EmptyBottleItem` |
| Medicine | Bandage, dirty rag, splint, antiseptic, antibiotics, painkillers, antiviral serum, first aid kit. Medicine cannot be used (and wasted) when you do not have what it treats. Tooltips say what each does. Six recipes (purifying water three ways, rags from wool, bandages, splint) | `DLSurvivalItems`, `data/deadlands/recipe` |
| HUD | Four small meters bottom left (thirst, stamina, temperature, fatigue), dim while fine, plus an icon and name for everything that is wrong; a new condition says what to do about it for a few seconds. Setting: *Interface > Body meters and injuries* | `client/hud/SurvivalHud`, `network/SurvivalPayload` |
| Loot | Every container, building, site and infected loot table now gives DEADLANDS food, drink and medicine instead of vanilla stand-ins (bread, potions, golden apples...) | `tools/assetgen/dl_data.survival_loot` |
| Skills | Five trees, ten levels each, grown by doing: Survivalist (staying alive, kills: slower thirst and fatigue, more carrying capacity), Medic (treating injuries: stronger healing), Gunslinger (weapon kills: firearm damage and rate), Engineer (restoring power: a chance to keep the fuse), Scavenger (searching containers: a chance of extra loot). Kept through death. A chat line on each level; the journal's LOG page shows every level with a progress bar, and `/deadlands skills` lists them | `skill/`, `network/SkillsPayload` |
| Carrying weight | Everything carried is weighed (light stackables 0.1 kg, blocks 0.25, small stacks 0.5, tools and weapons 2, armour 4). Capacity 45 kg plus the Survivalist bonus; over it you slow in proportion (to 40% at double) and sprinting costs more. A backpack (+15 kg, 18 slots) or military backpack (+25 kg, 27 slots) raises capacity while carried and opens its own storage when used in the hand; what is inside is kept on the item and still weighs. The storage uses Minecraft's chest screen. A fifth HUD meter. Setting: *Survival > Carrying weight* | `survival/CarryWeight` |
| Workbench and recipes | A workbench block (in the factory, craftable) opens a 3x3 crafting grid. 14 recipes: purifying water three ways, rags, bandages, splint, the four melee weapons, 9 mm rounds, a backpack, the workbench. **It uses Minecraft's crafting screen and recipes also work at a crafting table** | `block/WorkbenchBlock`, `data/deadlands/recipe` |
| Weapons | Crowbar, baseball bat, machete and fire axe (melee, different damage and swing speed), and three firearms that take rounds from the inventory and fire along the line of sight: pistol (9 damage, 48 blocks), shotgun (7 spreading pellets of 4, 24 blocks), hunting rifle (18 damage, 96 blocks, slow). Guns hold 12, 5 and 4 rounds; using an empty gun (or sneak-using) reloads from the inventory and takes one and a half to two and a half seconds. Every shot wears the gun and makes a gunshot noise every infected and nest in range hears. Found in loot; rounds can be crafted. **No recoil, aiming or ammo counter on screen yet** (the tooltip shows what is loaded) | `registry/DLWeapons`, `item/FirearmItem` |
| Item condition | Weapons show Good, Worn or Failing in their tooltip from the durability left; a failing gun sometimes jams | `item/ItemCondition` |
| Health, armour and hunger bars | DEADLANDS bars with pictograms replace Minecraft's hearts, armour icons and drumsticks (absorption and saturation shown). Setting: *Interface > DEADLANDS health and hunger bars* | `client/hud/VitalsHud` |
| Inventory, crafting and chest screens | DEADLANDS versions of Minecraft's inventory, crafting grid and chest screens: the same menus and slots (armour and off hand are the equipment slots) on a DEADLANDS panel, and the inventory gains a status page (water, stamina, rest, load in kg, what is wrong, skill levels). No recipe book. Setting: *Interface > DEADLANDS inventory and crafting screens*. **Laid out by arithmetic, never seen** | `client/ui/DLInventoryScreen`, `DLCraftingScreen`, `DLChestScreen`, `client/mixin/MinecraftMixin` |
| Ammo counter | Rounds loaded and magazine size beside the hotbar while a gun is held | `client/hud/VitalsHud` |
| Tests (not run) | `DeadlandsSurvivalGameTest` (7): body state save and load, conditions, treatment and sync rules, damage types and text, skills, load and weapons, walls muffling noise | `src/gametest/` |

### Fixed since Milestone 4

| Shortfall | What changed |
|-----------|--------------|
| 115 structures | **123 templates**: two gas stations (one burned out) with a new fuel pump block, a rest stop, a roadside stand, two farmsteads, a town library and a factory |
| Walls did not muffle noise | Each solid block between a noise and an infected or a nest takes 20% of the noise's reach (never below 30%) |
| Infected never seen moving | Still not seen in the game, but the poses are now drawn offline from the rig and the model's maths (`tools/assetgen/preview_poses.py`, sheet in `_cache/infected_poses.png`): every kind stands on the ground inside its hitbox, the crawler lies face down, the stalker crouches. Keyframed actions and textures on the model are still unchecked |
| Balance was a guess | A paper review found runners far too fast (ground speed goes with the square of the attribute: the burst was about 9 blocks a second). Speeds retuned: walkers 1.9 blocks a second, runners 4.4 and 6.0 in a burst, brutes 1.7. The rest is still untested in play |

## Milestone 4 (see the status at the top)

Every row below is code and data that exists in the repository but has **not been verified in
the game**. Nobody has seen an infected move, heard one in the world, or played a horde night.

| System | What exists | Where |
|--------|-------------|-------|
| Infected kinds | 9 entity types. **Walker** (22 HP, slow, the common shambler), **Runner** (fast, bursts then tires, shrieks to alert others), **Brute** (70 HP, armoured, knockback-resistant, ground slam, breaks walls faster), **Screamer** (weak; screams to summon nearby infected and keeps its distance), **Military** (32 HP, armoured, helmet and vest), **Crawler** (low, leaps, slows what it bites), **Bloater** (swells and bursts into a poison and nausea cloud), **Stalker** (climbs walls, stalks silently, pounces), **Charred** (fireproof, sets its target alight, smoulders). Stats, hearing, breaching power and size per kind; spawn eggs for all 9 | `entity/InfectedKind`, `entity/*Entity`, `registry/DLEntities` |
| Looks | 58 visual variants: walker 24 (T-shirts, flannel, office, hoodies, scrubs, overalls, police, hi-vis, sundress, athletes, winter parkas, pajamas), runner 8, brute 5, screamer 4, military 8 (woodland, desert, urban, hazmat, riot, medic), crawler 6, bloater 1, stalker 1, charred 1. Each infected also gets one of 5 builds and 5 heights. Variants are weighted by biome (farmers in farmland, parkas in the frozen outskirts, desert camo in the wasteland). 256x128 painted textures with tears, wounds, blood and decay; glow layers for the charred's embers and the stalker's eyes | `entity/InfectedVariants` (generated), `gen_infected.py` |
| Models and animation | Two rigs: humanoid (26 parts, including optional helmet, cap, gas mask, vest, belt, backpack, claws, bloated belly, growths, torn cloth, and missing forearms or shins) and brute (14 parts). Procedural poses per kind: shamble with a limp or a reaching arm, sprint, heavy lumber, face-down crawl, crouched stalk, wall climb, waddle; breathing, a working jaw, sudden head twitches; no two infected move in step. 7 keyframed actions blended on top: attack swipe and bite, brute slam, scream, pounce, pounding on doors (loops while breaching), emerging from a nest, bloater swelling | `client/render/infected/` |
| AI | Hunts survivors it sees or hears; remembers where it last saw them and searches there; investigates noises; is drawn to survivor light at night; groups into packs that wander together and alert each other (screamers and runners loudest); breaks doors, barricades and weak blocks (shared progress, so a crowd breaks through faster; brutes break more); in daylight, idle infected drift into buildings and stay there (stalkers strongly, runners rarely); released nest infected go back to their nest; wanders in packs when idle | `entity/ai/` |
| Noise | Game events (footsteps, block breaking and placing, doors, explosions, projectiles...) become noises of six loudness levels (quiet 6 blocks to explosion 96+); sprinting is louder than walking and crouching makes no footstep noise. Infected investigate by priority and remember for longer the louder it was; nests hear them too. Gunshots have their level ready for the weapons in Milestone 5. Walls do not muffle noise yet | `noise/`, `mixin/ServerLevelMixin` |
| Nests | 5 kinds (residential, industrial, military, underground, advanced) with their own textures and mix of residents. A nest has a population that regrows (faster at night), grows alert as it hears survivor noise, and past a threshold releases a few infected toward the noise, then waits out a cooldown. Released infected that find nothing return to it. Breaking a nest lets everything inside out at once, drops biomass and sometimes an infected tissue sample, and goes in the chronicle. All of it is saved, so an emptied nest stays empty. Placed in town buildings by town condition, and in the overrun school's basement. Biomass, floor growth and wall veins around them | `nest/`, `registry/DLNests`, `gen_nests.py` |
| Spawning | DEADLANDS biomes no longer spawn vanilla monsters. Every 2 seconds, around each survivor, groups spawn 24 to 96 blocks away (configurable) in the dark: by day mostly indoors and at half the population (`zombies.daytimePopulation`), at night anywhere dark. Kind mix by biome, town and depth (more runners at night, brutes in industrial areas, crawlers underground). Never in survivor-lit rooms. Local and global caps; natural infected despawn far from everyone | `spawning/` |
| Performance | One manager tracks every loaded infected. Once a second they are ranked by distance to the nearest survivor; the nearest `performance.maxActiveAi` think every tick, the rest every `performance.distantAiInterval` ticks while still walking on their last orders. Horde members get their own allowance above the global cap | `spawning/InfectedManager` |
| Hordes | On every horde day of the encounter schedule (every 5 days, never on a boss day): a warning at dusk, then waves every 10 seconds from one direction until the night's total has arrived, at most 60 alive at once. Size grows with each horde (30, +15% per horde, capped) and with the number of survivors online. Hordes hunt survivors directly. The horde ends at dawn or when every member is dead ("the horde is broken"); both go in the chronicle. Sleeping is blocked on horde nights (`hordes.preventSleep`). Saved, so a horde survives a restart and resumes | `horde/HordeDirector`, `persistence/HordeRecord` |
| Migrations | Groups of 8 to 15 infected (more as the days pass, up to 27) travel between towns as data, about a block a second, even with nobody near. Within 88 blocks of a survivor they appear as real infected; once everyone is 150 blocks away for 30 seconds they become data again with their survivors counted. Setting: *World events > Migrating hordes* | `horde/MigrationSystem`, `persistence/MigrationRecord` |
| Sound | 46 new sound events, 94 synthesised sounds: voices per kind (moans, shrieks, roars, gurgles, the screamer's scream), hurt and death, footsteps, door bashing on wood and metal, scratching, breathing, nest pulse, release, burst and absorb, horde stings and a distant crowd. Idle voices are chosen on each client: the kind's voice within 20 blocks, a muffled voice through walls, a thin distant moan to about 40 blocks, breathing within 4 blocks; never a loop, at most 6 at once. Setting: *Audio > Distant and muffled infected* | `client/sound/`, `gen_infected_audio.py` |
| Horde HUD | A card at the start (how many are coming), when the horde is broken, and at dawn (how many were killed), each with its sting; a counter with a bar at the top right while it lasts; the horde heard far off in a random direction every 20 to 50 seconds. Setting: *Interface > Horde card and counter* | `client/hud/HordeHud`, `network/HordeEventPayload` |
| Loot | Each kind has a drop table, plus the outfit's own (keys, keycards and papers from police, medical and office infected, a fuse from some workers, tissue samples from hazmat and lab infected, military kit), with vanilla items as stand-ins until Milestone 5 | `data/deadlands/loot_table/entities` |
| Journal and status | Infected killed per survivor, shown in the journal; horde state in `/deadlands status` | `persistence/PlayerRecord`, `client/ui/JournalScreen` |
| Commands | `/deadlands horde start|stop|status`, `/deadlands infected count|clear`, `/deadlands noise <loudness>` (game masters) | `command/DeadlandsCommand` |
| Settings | New: *Infected appear naturally*, *Daytime population*, *Nest releases*, *No sleeping on horde nights*, *Distant and muffled infected*, *Horde card and counter*. The zombie, horde, migration and AI-performance settings now work. 63 settings, 42 active | `config/DeadlandsConfig` |
| Tests (not run) | `DeadlandsInfectedGameTest`: kinds registered with attributes and spawn eggs, 50 to 60 looks, every look has a texture and loot, infected keep look and orders through save and load, nests keep their state and take infected back, the horde mix, horde, migration and power state survive a save, louder noises carry farther. Written but never executed | `src/gametest/` |

## Milestone 3, second half (see the status at the top)

| System | What exists | Where |
|--------|-------------|-------|
| Cities | A fourth town size (radius 104, 60 to 80 buildings, about 1 town in 15) with a grid of cross streets, crossings with traffic lights and zebra stripes, and city buildings: apartment blocks (3), office blocks (3), department store, motels (2), bank, courthouse, fire stations (2), emergency rooms (2). Cities and large towns are linked by highways | `world/town/TownPlanner`, `TownCrossingPiece`, `gen_city.py` |
| Western towns | Towns in the dry wasteland are built from their own set: saloons (2), general stores (2), wooden houses (3), sheriff's office | `gen_city.py`, `TownPlanner.pickCategory` |
| More buildings | 25 more seeded variants of the existing town buildings (other floor plans, materials and wear). 76 town building templates in all | `gen_towns.py` |
| Household containers | Kitchen cupboard (double doors), fridge, dresser (drawer), safe (heavy door, hard to break) and school desk (lid), each in four conditions with animated parts and its own loot tables; houses now use them instead of stand-ins | `container/ContainerKind`, `gen_containers.py` |
| Road types | Four-lane highways (double yellow centre line, dashed lane lines, wide shoulders), two-lane roads and dirt roads. Climate shows on the road: snow in the frozen outskirts, mud in the marsh and farmland, flooding in low ground. Fallen logs and roadblocks, power pylons along highways, utility poles and road signs (warning, speed, route) | `world/road/` |
| Railways | Rail lines along about four in ten of the road links between towns larger than settlements: ballast, sleepers and rails, trestles over water and valleys, platforms near towns and buffer stops at the ends. Setting: *World > Railways* | `world/road/RailNetwork`, `RailFeature` |
| Sites outside towns | 10 structures from 37 templates: the school (evacuated, shelter, overrun with a basement nest; 40x30 with its own layout), forest outposts (ranger stations and hunting cabins), camps (campsites and evacuation camps), military posts (checkpoints and watchtowers), bunkers, utility sites (substations, water towers, radio towers), the power station, a water treatment plant, farm sites (silos and windmills) and a research outpost. Kept away from towns; 8 new loot tables (school lockers, the school office and its key, a hidden stash, hunting, campsites, utilities, the research lab) | `gen_sites.py`, `data/deadlands/worldgen/structure` |
| Power | Street lamps, traffic lights, transformers, a power generator and a new item, the fuse. Generators stand at the power station, substations, bunkers and the school. Using a fuse on one restores power to every town within 700 blocks: their street lamps light at night, fewer infected appear there at night, the town card says the power is back, and it goes in the chronicle. Saved per town | `world/power/PowerGrid`, `block/PowerGeneratorBlock`, `block/LampHeadBlock` |
| Furniture | 8 more decor blocks: traffic light, three road signs, transformer, power generator, bookshelf, whiteboard (24 in all) | `block/DecorKind`, `gen_decor.py` |
| Decals | Evacuation notice and scattered papers (and biomass veins for nests): 11 in all | `gen_decals.py` |
| Tests (not run) | `DeadlandsWorldGameTest` gains: city and western buildings exist and cities are the largest towns, every site and site template loads, railways and roads are placed, the power grid's parts exist; the loot table test covers the new tables. Written but never executed | `src/gametest/` |

## Milestone 3, first half (see the status at the top)

Every row below is code and data that exists in the repository but has **not been verified in
the game**. APIs were checked against the decompiled Minecraft 1.21.11 and Fabric API sources;
building layouts and textures were checked from rendered previews of the generated files.

| System | What exists | Where |
|--------|-------------|-------|
| Biomes | 10 biomes in the 1.21.11 format: Dead Forest, Overgrown Suburbs, Abandoned Farmland, Dry Wasteland, Flooded Marsh, Dead Pine Valley, Frozen Outskirts, Industrial Wasteland, Burned Forest, Rural Countryside. Each has its own sky, fog and water colours, fog distance, ambient particles (ash, spores, snow...), an ambient loop with a mood sound and one-shot additions, no vanilla music, grass and foliage colours, vegetation and a spawn list (vanilla monsters removed in Milestone 4) | `data/deadlands/worldgen/biome`, `world/biome/DLBiomes`, `gen_world.py` |
| Biome placement | A mixin into the overworld biome builder swaps each vanilla land biome for a DEADLANDS biome in the same climate slot (plains become farmland or countryside by temperature, forests dead or burned forest, deserts the dry wasteland...). Terrain shape, oceans, rivers, beaches, mountain peaks and caves stay vanilla. Setting: *World > DEADLANDS biomes* | `mixin/OverworldBiomeBuilderMixin`, `world/biome/BiomeReplacement` |
| Ground and vegetation | Per-biome ground cover (dead grass, podzol, cracked earth, ash, mud, gravel and rubble patches, puddles in the marsh) placed by one feature; dead, charred, pine, marsh and overgrown trees; fallen logs, boulders, scrap, rubble and hay piles, wild wheat, moss. 4 new blocks: cracked earth, dead log, charred log, dead leaves. 24 configured and 33 placed features in one global order, so biomes never disagree about feature order | `world/feature/`, `gen_world.py`, `gen_terrain.py` |
| Biome ambience | 9 seamless 32-second stereo loops (dead forest, pine, suburbs, fields, dry wind, marsh, frozen, industrial, burned) and 19 one-shots (creaks, crows, distant metal, gusts, frogs, embers, mood), synthesised | `sounds/ambient`, `gen_ambience.py` |
| Road network | Towns are linked by roads (a relative neighbourhood graph: 2 to 4 roads per town, no long road that duplicates a shorter detour). Each road is a smooth curve with a gentle meander and a smoothed, slope-limited height profile, and ends exactly at a town gate. Drawn chunk by chunk without seams: cracked asphalt with a dashed yellow centre line and white edge lines, shoulders, decay (gravel, dirt and weeds breaking through), embankments and cuts, bridges with guardrails and pillars over water and valleys, tunnels through hills, utility poles with crossarms. Settings: *Road network*, *Road decay*, *Roadside poles* | `world/road/` |
| Towns | At most one town per 34x34-chunk region; a site must be dry, in a town biome, and neither too steep nor flooded. Three sizes (settlement 5-12 buildings, small town 15-30, large town 30-60) and six conditions (evacuated, overrun, military checkpoint, survivor holdout, bandit territory, long abandoned) that set damage, blood, how much loot is left and what blocks the gates. Main streets run from the centre to the gates where roads arrive; large towns get side streets. Streets follow the terrain with flat crossings and levelled verges, dead street lamps, and cracks, blood and rubble set by the condition. Gates: concrete checkpoints with a supply crate, evacuation cordons, or scrap walls | `world/town/` |
| Town buildings | 28 building templates from 15 archetypes in 6 categories: cottages (3), suburban houses (3), two-storey houses (2), trailers (2), corner store, grocery, pharmacy, hardware store, diner, police station with cells, town hall, church, clinics (2), auto garages (2), warehouse, barn, farmhouses with vegetable plots (2), sheds (2). Furnished rooms, loot containers with 12 new location loot tables, locked containers (pharmacy key, records-room key, maintenance key, keycard), blood, rubble and cobwebs. Buildings face their street, sit level with it on a foundation, and are worn down by the town's condition (roof holes, broken walls, blood). In scavenged towns some containers are already empty and hanging open; `loot.lockedShare` locks a share of the rest | `gen_towns.py`, `world/town/TownPlanner`, `TownBuildingPiece` |
| Furniture and fixtures | 16 blocks with custom models and textures: table, chair, kitchen counter, store shelf, old bed, couch, desk, stove, sink, toilet, utility pole, crossarm, street lamp post and lamp head, guardrail, concrete barrier. Collision shapes match the models; furniture faces the player who places it | `block/DecorKind`, `registry/DLDecor`, `gen_decor.py` |
| Town identity | Every town has a name, former population, history, who held it last, how much is left to scavenge, a story line and a landmark (the most prominent building that actually stands there), all derived from the seed. The first survivor to reach a town discovers it (saved, and written to the chronicle); everyone sees the town card, with its own sound, when they arrive. Setting: *Interface > Town cards* | `world/town/TownIdentity`, `TownTracker`, `client/hud/TownCardHud`, `persistence/TownRecord` |
| Restyled vanilla blocks | 27 more: dry grass, dead bush, bush, leaf litter, vines, lily pad, sugar cane, moss, mud, snow, ice, packed ice, podzol, rooted dirt, snowy grass side, dirt path, farmland, hay, red sand, mossy cobblestone, iron bars, cobweb | `gen_terrain.py`, `gen_towns.py` |
| Settings | New *World* section (DEADLANDS biomes, road network, road decay, roadside poles) and *Interface > Town cards*; `loot.lockedShare` now works | `config/DeadlandsConfig` |
| Tests (not run) | `DeadlandsWorldGameTest`: biome replacement, town templates load and fit their plots, town loot tables parse, town identities are stable. Written but never executed | `src/gametest/` |

## Completed systems (Milestone 2)

| System | What exists | Where |
|--------|-------------|-------|
| Building and ground blocks | 25 blocks and 4 slabs: asphalt, cracked asphalt, yellow and white road lines (they turn to follow the player's facing), sidewalk, four concretes (weathered, cracked, stained, mossy), weathered and soot-stained bricks, plaster and peeling plaster, hospital tiles (clean and cracked), boarded planks, weathered siding, rusted sheet metal, corrugated metal, diamond plate, rubble, ash, dry dirt, dead grass block, broken glass (translucent). Each has the right tool, hardness, sound, map colour and drop. Peeling plaster has three texture variants picked per block position so walls do not repeat in a grid | `registry/DLBlocks`, `block/RoadLineBlock`, `tools/assetgen/gen_blocks.py` |
| Decals | 8 thin, non-colliding, replaceable decals: dried blood spots, dried blood pool, blood smear, bloody handprint, drag marks, faded stain, scorch mark, survivor mark. They attach to floors, walls and ceilings with a random quarter turn, break almost instantly and drop nothing | `block/DecalBlock`, `gen_decals.py` |
| Vanilla environment retextures | 24 DEADLANDS-style replacements: grass block (top, side, overlay), dirt, coarse dirt, stone, cobblestone, gravel, sand, oak/spruce/birch logs and log tops, oak and spruce planks, oak/spruce/birch leaves, short grass, fern, tall grass; dry, desaturated grass and foliage colormaps | `assets/minecraft/textures`, `gen_vanilla.py` |
| LabPBR maps | `_n` (normal, ambient occlusion, height) and `_s` (smoothness, F0, porosity) next to every DEADLANDS block texture (28), for shader packs that read LabPBR 1.3 | `dl_materials.save` |
| Containers | Military crate, filing cabinet, medical cabinet, supply locker and toolbox. Each comes in four conditions (clean, dusty, rusty, damaged) with its own textures, and can be placed ajar. Block entity with a persistent inventory. Animated moving parts drawn by a block entity renderer with eased motion: crate and toolbox lids swing open onto hollow interiors, the filing drawer slides out over hanging files, cabinet and locker doors swing on their hinges. Open/close sounds per kind; custom screens per style (wood, metal, medical) | `container/`, `client/render/`, `client/ui/DLContainerScreen` |
| Loot | Loot rolls the first time a container is opened (never at generation), from a table per kind plus a rare table (rare chance per kind, scaled by the loot config). Damaged containers hold less. Contents persist and never re-roll. 11 container loot tables and 34 block drop tables | `LootContainerBlockEntity.unpackLootTable`, `data/deadlands/loot_table` |
| Locks and keys | A container can be locked with a named key or a keycard access level; the lock is saved with the block entity and with structure templates. Using it with the right key or card in either hand unlocks it for good; otherwise the action bar says what is needed and a locked sound plays. A locked container cannot be broken by hand in survival (explosives still work); clients are sent the lock so mining prediction matches the server, but never the contents. Keys carry their id in a data component and are named after it ("Ranger Station Key"); keycards come in levels 1 to 3 (green, blue, red) | `container/ContainerLock`, `item/KeyItem`, `item/KeycardItem`, `registry/DLDataComponents` |
| First structures | Roadside ranger shack (9x5x9: siding walls, rusted roof with a hole, broken windows, blood trail; its supply locker always holds the Ranger Station key, which opens its locked medical cabinet) and overrun supply cache (11x4x11: barricade walls, three crates, one keycard-locked, one left ajar). NBT templates placed by the world generator as single-piece jigsaw structures (random spread, any rotation, land biomes only), or with `/place template` | `data/deadlands/structure`, `data/deadlands/worldgen`, `gen_structures.py` |
| Creative tabs | Building Materials, Containers, Custom Blocks (decals), Quest Items (keys and keycards) and Lore (the journal); still no empty placeholder tabs | `registry/DLTab` |
| Audio | 12 new synthesized sounds: open and close for each container kind, a locked rattle and an unlock click | `sounds/container`, `gen_audio.py` |
| Automated tests | 11 server game tests on every `build` (5 new: block assets, every loot table parses, locks need the right key, locked containers resist survival players, structure templates hold locked loot). The client test (23 checks) right-clicks every container with real input, tries the lock without its key and by mining it, opens it with the key, places both structures, checks that container contents and locks survive save and reload, and finds both structures generated naturally in a normal world | `src/gametest/` |

## Completed systems (Milestone 1)

| System | What exists | Where |
|--------|-------------|-------|
| Project setup | Gradle 9.8.2 wrapper, Loom 1.19.3, Mojang mappings, split client/common source sets, game-test source set, `dev.ps1` JDK picker | `build.gradle`, `gradle.properties`, `dev.ps1` |
| Boot sequence | Ordered init: config, sounds, data components, items, blocks, containers, block entities, menus, creative tabs, networking, world clock, survivor tracking, commands; startup summary in the log | `Deadlands`, `DeadlandsClient` |
| Registry system | Items, blocks, sound events, creative categories for all 15 categories from the design; a tab is registered only once it has content | `registry/` |
| Configuration | 11 sections, 51 settings declared once with `@ConfigEntry`; commented `config/deadlands.jsonc`; lenient parsing, clamping, unknown-key warnings, atomic saves, backups; `/deadlands config reload`; clients resync on change | `config/` |
| Persistence | `DeadlandsWorldData` (SavedData + Codec, schema-versioned): world-clock progress, a record per survivor, the world chronicle | `persistence/` |
| World clock | DEADLANDS days from overworld time; `DayChangeCallback` exactly once per day, persisted; skipped days reported; rewinds resync without firing | `progression/WorldClock` |
| Encounter schedule | Horde every 5 days (30 infected, +15% per horde, capped at 120), boss every 10 days in roster order, escalation from day 60; all configurable | `progression/` |
| Survivor tracking | Arrival record and chronicle entry, one Survivor's Journal per survivor, deaths recorded | `player/SurvivorTracker` |
| Networking | Server-authoritative `world_status`, `day_begin`, `request_status` | `network/` |
| Commands | `/deadlands status`, `chronicle`; `day next`, `day set <n>`, `config reload`, `journal` for game masters | `command/DeadlandsCommand` |
| Custom UI | DEADLANDS title screen and backdrop, restyled vanilla widgets and menu backgrounds, Survivor's Journal (LOG, THREATS, SETTINGS), DEADLANDS Settings, DAY card HUD, **J** key | `client/` |

## Moved out of Milestone 2

| Item | Now in | Status |
|------|--------|--------|
| Furniture (beds, desks, shelves, chairs...) | M3 | Written in Milestone 3 (24 blocks), not yet tested |
| DEADLANDS player inventory and creative inventory backgrounds | M5 | The player inventory is being replaced anyway (equipment slots, weight, crafting) |

## Partially completed

| Area | Done | Missing |
|------|------|---------|
| Milestones 3 to 9 as a whole | Written; the automated tests pass (see the status at the top) | Playing all of it by hand: the manual checklists, balance, the story, driving, hordes |
| Milestone 5 | Survival stats, injuries, medicine, food and drink, HUD, skills, carrying weight, melee weapons and a pistol | Extra equipment slots, stations with their own recipes, sorting and a recipe list; the hotbar, experience bar, air bubbles, furnaces and other vanilla screens are still vanilla |
| Structures | 123 templates in 15 structures: 78 town buildings, 43 site templates, 2 ruins from Milestone 2 | Past the 120 target. Gas station pumps are decoration until vehicles need fuel (M6) |
| Bosses | Schedule computed and shown | Milestone 8 (a boss day never also has a horde; that rule is in the schedule) |
| Configuration coverage | 69 settings declared and editable; all active | |
| Container and infected loot | Tables for every container, building, site, kind and outfit, with DEADLANDS food, drink and medicine | Vanilla items still stand in for weapons, tools, armour and materials |
| Noise | Game events, six loudness levels, walls muffle, nests and infected listen; the pistol makes gunshots | |
| Infected audio and ambience | Voices picked per listener (clear, muffled, distant, breathing, alert, chase) within a shared budget; a soundscape per kind of place with weather and indoor muffling; a soundtrack that follows the situation (overhaul) | Real occlusion filtering of every sound; a listening pass over the mix |
| Roads | Highways, roads and dirt roads with climate effects, obstacles, pylons, poles and signs | Wrecked cars (vehicles are M6), overpasses |
| Visual identity | Menus, journal, HUD cards, containers, DEADLANDS blocks, infected, 51 retextured vanilla blocks, a finishing pass over block and item textures (M9) | Other vanilla blocks, items, mobs (the vanilla mobs that still spawn outside DEADLANDS biomes), HUD bars, inventories and the font (M5, M9) |
| Localization | English for every string | No other languages |

## Not implemented yet (by milestone)

- **M5 Survival, the rest:** extra equipment slots, crafting stations with their own recipes,
  sorting, a recipe list.
- **Polish not covered by Milestone 9 or the overhaul:** real occlusion filtering of every sound
  (ambience and infected voices are muffled behind walls, other sounds are not), cutscenes with
  acting figures (they are camera moves with subtitles), a new font, connected textures (they need
  a renderer mod), the vanilla blocks, items and mobs DEADLANDS has not restyled.

## Known issues and limitations

1. **Milestones 3 to 9 have only been run by the automated tests.** The game starts, worlds
   generate and the client test's scenes work, but nobody has played: expect problems in what the
   tests do not reach (long sessions, the story's later quests, bosses, hordes, driving, settlements
   over days, multiplayer with several survivors).
2. **The infected have been seen standing, not moving.** Screenshots show every kind and look
   standing still; the client test checks that their animations start and that forty of them hunt
   without trouble, but nobody has watched them walk, attack or climb. Expect poses to need tuning,
   the crawler's face-down pose and the stalker's crouch above all.
3. **Balance is untested.** Spawn rates, caps, horde sizes, nest release thresholds, breaching
   speed and kind stats are first guesses.
4. **The story has never been played through.** Quest conditions, the places it finds, the bosses'
   attacks and balance, the helicopter's lift-off and the endings are reasoned, not watched.
   Bosses are spawned at the surface near a survivor; underground survivors may find one waiting.
4. **Survivors and settlements have never been seen.** Work, grief, trespass, theft and the daily
   settlement simulation are reasoned, not watched; the simulation's numbers (forage, attack
   strength, arrivals) are first guesses. Headlight light blocks follow a moving vehicle every two
   ticks, which may flicker or lag at speed.
4. **Vehicles have never been driven.** The driving model follows Minecraft's boat (the driver's
   client moves the vehicle), but steering feel, speeds, climbing, the seats' positions and how the
   long vehicles fit their square hitboxes are all untried. Vehicles placed by the world generator
   are entities added during generation, which is the part most likely to misbehave.
5. **Survival has never been played.** Drain rates, injury chances and temperature numbers are
   reasoned, not felt. The stamina sprint lock and the screen swap rely on mixins that compile
   but have never been applied to a running game.
5. **Vanilla monsters still spawn outside DEADLANDS biomes.** Oceans, rivers, beaches, mountain
   peaks and the vanilla cave biomes keep their own spawn lists.
6. **Migrating groups are data while nobody is near.** They walk straight lines over any terrain
   and only meet blocks once they appear near a survivor.
7. **Gradle needs JDK 25+.** Loom 1.19 refuses to run on Java 21. Use `dev.ps1`, which finds JDK 26
   on this machine; the game itself still runs on Java 21.
8. **Title-screen add-ons from other mods are hidden.** The DEADLANDS menu replaces vanilla's
   title-screen content, so buttons other mods inject there (for example Mod Menu's "Mods") do not
   appear. Turn off *Interface > DEADLANDS title screen* to get them back.
9. **Meeting a character is partly luck.** One survivor may meet someone in five minutes and
   another only near the hour, when the chance becomes certain. The story's fallback places those
   still missing two days after Harley is met, which can be sooner. Several survivors exploring at
   once meet them sooner.
10. **LabPBR maps are untested with a shader pack.** No shader pack (Iris) is installed in the test
    setup.
11. **The texture pass is automatic.** It was reviewed on before/after sheets (544 kept, 87 left as
    painted, entity skins untouched), never in the game's lighting. Rerunning a generator repaints
    its textures; `build_all.py realism` (the last group) finishes them again.
12. **Trees can grow in towns and on roads.** Vegetation is decorated after structures and roads,
    so trees can grow in yards, on verges and through decayed road surface. Nothing grows inside
    buildings or on intact asphalt.
13. **The Milestone 2 ruins can land on a road.** They keep 9 chunks from towns (since Milestone 9),
    but roads are drawn after structures and do not know where the ruins are.
14. **World changes only affect new chunks.** Existing worlds keep their old terrain; towns, roads,
    railways and sites appear only in newly generated areas and can be cut off at the border with
    old chunks.
15. **Backdrop resolution:** the title art is 1920x1080 and is upscaled on 4K screens.
16. **Dev-run log noise:** Realms authorization, "Failed to retrieve profile key pair" and
    chat-verification toasts are expected with offline development accounts.

17. **The overhaul's audio has not been listened to** (see the overhaul's decisions above): its
    levels and spectra were measured and the client test hears the right sounds at the right
    moments, but the mix (music against ambience against voices) needs ears.
18. **The main menu follows a description, not the reference image**, which was not available.
19. **A long jump into new land stalls the integrated server for 3 to 4 seconds** while DEADLANDS
    chunks (towns, roads) generate: every client test logs `Can't keep up! ... 69 ticks behind`
    when it teleports 758 blocks to a natural supply cache. Walking or driving is unaffected so
    far; flying fast in creative or `/tp` far away shows it.
20. **One client test run failed once** (of about a dozen): the integrated server stopped answering
    (Fabric's network synchronizer timed out, "interfacing with packets at a lower level") on a
    day when a world event, a distress call, spawned survivors and infected 43 blocks from the test
    player. It has not happened again; if it does, the
    distress call's spawning is the first suspect.
21. **The particle generator no longer reproduces the committed ash, blood, dust and spore
    sprites** (they were changed after it last ran). Only the ember frames were regenerated in the
    overhaul; restore the other four from git after running `gen_polish.particles()`.

Milestones 1 and 2 had no known functional bugs when Milestone 3 began.

## Next tasks

1. **Listen to the overhaul** with headphones: a day and a night in the open, rain under a metal
   and a wooden roof, a town, a hospital, a horde night, a settlement. Tune the mix (music,
   ambience, voices), the ambience density and the danger thresholds from what is heard.
2. **Play Milestones 3 to 9** (the manual checklists in docs/TESTING.md): fly through new worlds
   (biomes, cities, western towns, highways, railways, sites, the school, power restoration), watch
   every infected kind up close (poses, animations, textures, voices), trigger nests, and play a
   horde night with one and with several survivors. Tune poses and balance from what is seen.
3. The rest of Milestone 5: extra equipment slots, stations with their own recipes, sorting, a recipe list.
