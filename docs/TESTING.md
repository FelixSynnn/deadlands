# DEADLANDS: Testing Guide

Every command below runs from the project folder in PowerShell. `dev.ps1` runs Gradle on a JDK
25+ (required by Loom) while the game itself runs on Java 21.

> The automated tests start Minecraft test servers. `build.gradle` sets `eula = true` in
> `fabricApi.configureTests`, which accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA)
> for those test servers. Set it to `false` if you do not agree (the game tests will then not run).

## Status (2026-11-10)

Both automated suites run and pass on the atmosphere overhaul branch: `dev.ps1 build` ends with
`All 57 required tests passed :)` (the 56 DEADLANDS server game tests in section 1 and one test
that is not part of DEADLANDS), and `dev.ps1 runClientGameTest` ends with
`DEADLANDS client game test passed` (section 2). The results of each run, with the frame rate and
memory figures, are in [DEVELOPMENT_STATUS.md](../DEVELOPMENT_STATUS.md#build-and-test-results).

Milestones 3 to 9 were written without any test run, at the project owner's request; the
automated tests were first run during the atmosphere overhaul. They start the game, load every
registry, data file and mixin, generate DEADLANDS worlds, spawn infected and survivors and join a
dedicated server, but they do not play the game: the manual checklists (sections 3 to 14) have not
been performed for Milestones 3 to 9, so balance, the story played through, driving, bosses,
hordes and settlements over days are still untried.

## 1. Automated: build + server game tests (every build)

```bash
powershell -ExecutionPolicy Bypass -File .\dev.ps1 build
```

Expected: `All 57 required tests passed :)` (the 56 DEADLANDS tests below; the batch also holds
one test that is not part of DEADLANDS) and `BUILD SUCCESSFUL`, in about a minute. The log also
shows `DEADLANDS 0.9.0-m9-dev ready in N ms: 100 blocks, 90 items, 9 entity types, 188 sounds, 15
creative tabs` and the world-clock sequence `Day 2 begins`, `Day 5 begins (2 days skipped)`,
`World clock moved back from day 5 to day 1`.

| Test | Proves |
|------|--------|
| `contentIsRegistered` | items, sounds and non-empty creative tabs are registered |
| `everyItemSoundAndSettingHasItsAssets` | every item has texture/model/definition and a name, every sound event has its OGG, every setting/section/tab/boss has English text |
| `configRoundTripsAndClamps` | defaults survive write + read; out-of-range, invalid and unknown values are clamped/rejected and reported |
| `encounterScheduleFollowsTheDesign` | hordes every 5 days from 30, bosses on 10/20/30/40/50 in roster order replacing hordes, escalation from day 60, size cap, clock text |
| `worldDataSurvivesSaveAndLoad` | `DeadlandsWorldData` codec round trip; chronicle cap; days-survived maths |
| `worldClockFiresOncePerDayAndHandlesRewinds` | one event per new day, never twice, one event for multi-day jumps, none on rewind |
| `everyBlockHasStateModelNameAndLoot` | every registered block has a blockstate, an item model definition, an English name and (unless it drops nothing) a loot table |
| `allLootTablesParse` | the 11 container loot tables and every block drop table load through the game's own loot codec |
| `locksNeedTheRightKey` | a key opens only its own lock (not another key, not a blank key); a level 2 keycard lock rejects level 1 and accepts levels 2 and 3 |
| `lockedContainersResistSurvivalPlayers` | a locked cabinet cannot be mined by a survival player but can by a creative one; unlocking makes it breakable again; clients are sent the lock but not the contents |
| `structureTemplatesHoldLockedLoot` | both templates load; the shack's medical cabinet is locked and its locker holds the key table; the cache has three crates; the worldgen structure is registered |

Milestone 3 tests (`DeadlandsWorldGameTest`, pass):

| Test | Proves |
|------|--------------|
| `landBiomesBecomeDeadlandsBiomes` | plains, forest and desert map to DEADLANDS biomes; oceans and rivers stay vanilla |
| `townTemplatesFitTheirPlots` | the town structure is registered, every listed building template loads and fits a 14x14 plot, and there are at least 25 |
| `townLootTablesParse` | the 21 town and site loot tables load through the game's loot codec |
| `townIdentityIsStable` | the same seed and site always give the same town; population ranges and loot labels match the town's size and condition |
| `citiesAndWesternTownsHaveTheirBuildings` | the town structure lists city and western buildings; cities are the largest size, major, with a street grid, and had tens of thousands of people |
| `sitesAreRegisteredAndTheirTemplatesLoad` | the 10 site structures are registered, all 37 site templates load, and the rail and road features are placed |
| `thePowerGridHasItsParts` | the fuse and the generator are registered and street lamps have a lit state |

Milestone 4 tests (`DeadlandsInfectedGameTest`, pass):

| Test | Proves |
|------|--------------|
| `infectedKindsAreRegistered` | all 9 entity types exist, map back to their kind, have attributes with the kind's health and a spawn egg, and there are 60 to 80 looks in total (66 since the overhaul) |
| `infectedLooksHaveTexturesAndLoot` | every look has its texture; every kind and every outfit has a loot table that parses |
| `infectedKeepTheirLookAndOrdersThroughSaveAndLoad` | a walker gets a look on spawn; look, build, height, horde night, migration and home nest survive saving and loading the entity |
| `nestsKeepTheirStateAndTakeInfectedBack` | a nest block entity loads and saves its population, cooldown and release count, and absorbs a returning infected |
| `hordesAreMostlyWalkersAndRunners` | over 4,000 rolls, walkers and runners are at least 70% of a horde, screamers, stalkers and charred never appear, and later hordes bring more brutes |
| `hordeMigrationAndPowerSurviveSaveAndLoad` | the horde in progress, a migrating group, powered towns and the migration id counter survive the world-data codec |
| `louderNoisesCarryFarther` | each loudness level has a larger radius and a higher priority than the one below; explosions reach at least 96 blocks |
| `entityEventsStayClearOfVanilla` | every DEADLANDS entity event id lies above the highest id vanilla declares (read by reflection) and none repeats; sending each to a stalker, screamer and brute starts its animation (the Stalker crash regression) |

The other server game tests, by class (all pass):

| Class | Tests |
|-------|-------|
| `DeadlandsSurvivalGameTest` (Milestone 5) | `bodyStateSurvivesSaveAndLoad`, `conditionsFollowTheBody`, `treatmentAndPayload`, `survivalItemsDamageTypesAndText`, `skillsGrowAndSave`, `loadAndWeapons`, `wallsMuffleNoise` |
| `DeadlandsVehicleGameTest` (Milestone 6) | `vehicleKindsAreRegisteredWithTextures`, `restorationMakesAVehicleDrivable`, `vehiclesKeepConditionAndCargo`, `pumpsHoldWhatTheyHoldAndRunDry`, `vehiclesShowWearAndKeepTheirSwitches`, `ruggedMarkersLeaveAVehicle` |
| `DeadlandsNpcGameTest` (Milestone 7) | `standingFollowsReputation`, `tradersDealInRounds`, `survivorsKeepWhoTheyAre`, `markersBecomeWhatTheyStandFor`, `survivorsHaveTheirTextAndSkins`, `settlementsLiveAndDieByTheNumbers`, `accessFollowsStandingAndCurfew`, `survivorsHaveTheirPeopleAndBelongings`, `theWorldRemembersWhereCharactersAre` |
| `DeadlandsStoryGameTest` (Milestone 8) | `loreIsCompleteAndWritten`, `documentsCarryTheirEntryAndTurnUpWhereTheyBelong`, `storyStateIsSaved`, `questsFollowTheStory`, `theSecretEndingNeedsEverything`, `radioAndStoryTextExist`, `keycardReadersHaveThreeLevels`, `bossesAreRegisteredAndScale` |

## 2. Automated: end-to-end client test

```bash
powershell -ExecutionPolicy Bypass -File .\dev.ps1 runClientGameTest
```

A game window opens and drives itself for about four minutes, **with sound**: Fabric's test
defaults turn the music slider to 0 (the soundtrack starts, silently, which is what the music checks
look for), but ambience and the infected play through the speakers. Expected: 64 `PASS:` lines, a
`PERFORMANCE:` line and two `LIGHT` lines, then `DEADLANDS client game test passed` and
`BUILD SUCCESSFUL`.

What it does, in order:

1. Title screen, settings screen.
2. A new flat test world: arrival card, one journal, the journal opened with the real **J** key and
   a tab clicked with the real mouse, `/deadlands day next`.
3. Milestone 2 scenes in the same world: a road with decals and a wall of every material; one of
   each container, each opened with a real right-click (loot rolls once, the screen opens, the lid,
   drawer or doors animate); the locked medical cabinet refuses to open and survives two seconds
   of the real attack button, then opens once the key is picked from hotbar slot 2 with the real
   number key; both structures placed with
   `/place template`.
4. The world is saved and reopened: day, chronicle, journal count, the crate's exact contents
   (no re-roll) and the structure's lock and unrolled loot are all unchanged.
5. A normal world (seed `deadlands`, structures on): the world generator itself finds a roadside
   shack and a supply cache, each with all of its containers and its lock intact. (Every new world
   first shows the opening introduction and cutscene; the test skips them with a click.)
6. **The Stalker crash regression** (`StalkerScenes`, its own world): standing Stalkers receive
   every infected entity event over the network and start each animation; a Stalker given a target
   pounces for real, and the client plays the pounce animation and sound; ten Stalkers are spawned,
   signalled and removed five times over.
7. **Audio and looks** (`AudioScenes`, `LookScenes`, `PerformanceScenes`, one world): the music
   director owns the music and wants exploration by day; one-shots play in the open; rain on an
   iron roof and on an oak roof built over the player; three hunting walkers turn the music to
   danger (a danger track reaches the sound engine), cry out and snarl, and the danger fades once
   they are gone; two settlers call for safe-zone music; night music after dark. Then review
   screenshots (below), forty walkers hunting the survivor at night for ten seconds (frame rate,
   voices, live heap; it fails above six voices at once or under 20 fps), and F3+T in the world
   (the same daylight view is captured before and after; their mean brightness must match within
   15%, which missing textures or a stale light map would break).
8. A dedicated server with DEADLANDS, joined by this client: world status and journal on first join.

The main menu's wind-and-crows bed is checked at the very start.

Screenshots are written to `build/run/clientGameTest/screenshots/`:

| Screenshot | Shows |
|------------|-------|
| `deadlands-01-title` | DEADLANDS main menu |
| `deadlands-02-settings` | DEADLANDS Settings |
| `deadlands-03-arrival-day-card` | DAY 1 card with the arrival line, journal in the hotbar |
| `deadlands-04-journal-log`, `05-journal-threats` | journal LOG and THREAT FORECAST |
| `deadlands-06-new-day-card` | DAY 2 card after `/deadlands day next` |
| `deadlands-09-materials-and-decals` | road surfaces, line paint, decals and the material wall |
| `deadlands-10-containers-closed` | the five containers (clean, dusty, rusty, ajar and damaged, clean) |
| `deadlands-11-screen-military_crate`, `11-screen-medical_cabinet` | wood and medical container screens with rolled loot |
| `deadlands-11-open-<kind>` | each container held open: lid, drawer or doors |
| `deadlands-12-locked-cabinet` | "Medical Cabinet is locked. It needs the Ranger Station key." |
| `deadlands-13-ranger-shack`, `14-ranger-shack-inside`, `14-ranger-shack-door` | the shack from outside, looking in, and looking back at the door |
| `deadlands-15-supply-cache` | the supply cache from above |
| `deadlands-07-reloaded` | the first world after save and reload |
| `deadlands-16-natural-roadside_shack`, `16-natural-supply_cache` | both ruins where the world generator placed them |
| `deadlands-03c-harley-speaks`, `03b-harley-camp` | the opening introduction, then Harley's camp after it |
| `deadlands-30-stalkers-events`, `31-stalker-pounce` | standing Stalkers after every entity event; a Stalker's pounce |
| `deadlands-40-infected-lineup` | every infected kind in a row, and walkers in eight looks (four of the overhaul's outfits) behind |
| `deadlands-41-infected-closeup`, `42-infected-dusk` | the same, close and at dusk |
| `deadlands-43-held-<bar>-<slot>` | weapons in hand, first person: knives, machetes, nail bat, fire axe; firearms; vanilla swords |
| `deadlands-44-block-variants` | asphalt, stone and dirt floors and concrete, brick and cobblestone walls, showing the per-position variants |
| `deadlands-45-new-blocks` | the ten new blocks |
| `deadlands-47-embers-at-night` | embers glowing at night |
| `deadlands-46a-before-reload`, `46-after-reload` | the same daylight view before and after F3+T |
| `deadlands-08-multiplayer` | joined to a dedicated server running DEADLANDS |

## 3. Manual checklist (singleplayer)

Start the development client (folder `run/client`):

```bash
powershell -ExecutionPolicy Bypass -File .\dev.ps1 runClient
```

Milestone 1:

| # | Do | Expect |
|---|----|--------|
| 1 | Wait on the main menu | Fade-in from black, logo flickers, fog drifts, layers shift with the mouse, the DEADLANDS theme plays (Music volume above 0) |
| 2 | Hover and click menu entries | Rust brush stroke and amber marker; latch click sound |
| 3 | Options, then any sub-screen | DEADLANDS-styled buttons/sliders over the DEADLANDS backdrop |
| 4 | DEADLANDS SETTINGS: change *Interface > Film grain*, Done | `run/client/config/deadlands.jsonc` shows `"filmGrain": false`; grain is gone |
| 5 | Singleplayer > Create New World (Survival, cheats on) | DAY 1 card with "The outbreak began ninety days ago. You are still here."; a Survivor's Journal in the inventory |
| 6 | Press **J**, click the skull tab, then the gear tab | LOG with Day 1 and the chronicle; THREAT FORECAST; DEADLANDS Settings |
| 7 | `/deadlands day next` | DAY 2 card and bell |
| 8 | `/kill`, respawn, `/deadlands chronicle` | "Day 2: <name> died. Days survived: 1." |
| 9 | Save and Quit, reopen the world | Same day; no second journal; no arrival card |
| 10 | Put `"intervalDays": 999` under `hordes` in the config, `/deadlands config reload` | Value clamped to 60, a warning in the log, the file rewritten |

Milestone 2 (same world, creative mode is easiest: `/gamemode creative`):

| # | Do | Expect |
|---|----|--------|
| 11 | Open the creative inventory | DEADLANDS tabs: Building Materials, Containers, Custom Blocks, Quest Items and Lore |
| 12 | Place a few of every building block; build a wall of Peeling Plaster | Correct breaking sounds and tools; the plaster wall mixes three variants and shows no grid |
| 13 | Place road line blocks facing different directions | The paint runs along the direction you face |
| 14 | Place decals on the floor, a wall and the ceiling | Each sticks to the face clicked, at a random quarter turn; you can walk through them; breaking one drops nothing |
| 15 | Place each container and right-click it | Its open sound, the lid/drawer/doors animate, the DEADLANDS screen opens with loot; closing plays the close sound and animates shut |
| 16 | Break a container with loot inside, place a new one | Items drop; a new container rolls its own loot on first open |
| 17 | `/place template deadlands:roadside_shack ~ ~ ~`, then `/gamemode survival` | The shack appears; right-click its medical cabinet: "Medical Cabinet is locked. It needs the Ranger Station key." |
| 18 | Try to mine the locked cabinet with a pickaxe | No cracks, it does not break; the same message appears |
| 19 | Open the shack's supply locker, take the key, use the cabinet with the key in either hand | "Unlocked with the Ranger Station Key.", unlock click; it opens and stays unlocked (and can now be mined) |
| 20 | Save and Quit, reopen | Containers keep their contents; nothing re-rolls; the cabinet stays unlocked |
| 21 | Create a normal world and run `/locate structure deadlands:roadside_shack` (and `supply_cache`), then teleport there | A ruin on the surface, with containers and its lock |

## 4. Manual checklist (multiplayer)

1. Start a development server (folder `run/server`):
   ```bash
   powershell -ExecutionPolicy Bypass -File .\dev.ps1 runServer
   ```
   On the first run it stops and asks you to accept the EULA: set `eula=true` in
   `run/server/eula.txt` if you agree. Because the development client uses an offline account,
   also set `online-mode=false` in `run/server/server.properties`. Start it again.
2. Expect `DEADLANDS 0.2.0-m2 ready` and `World clock started on day 1` in the server console.
3. Start a client (`dev.ps1 runClient`), Multiplayer > Direct Connection > `localhost`.
4. Expect the DAY 1 arrival card and a journal; the journal shows the server's schedule.
5. In the server console run `op <PlayerName>`, then in game `/deadlands day next`: every
   connected player sees the DAY 2 card.
6. With two players: one opens a container, the other sees the lid/doors open and hears it; loot
   taken by one is gone for the other.
7. In DEADLANDS Settings, server sections are read-only ("Only client settings can be changed
   here"); Interface and Audio remain editable.

## 5. Logs to check

- `run/client/logs/latest.log`, `run/server/logs/latest.log`: no `ERROR` lines from DEADLANDS.
- Expected noise in development: Realms authorization failures, `Failed to retrieve profile key
  pair` (offline account), chat-verification toasts on servers.

## 6. Resetting

- Settings: delete `run/client/config/deadlands.jsonc` (recreated with defaults on start).
- World progress: delete the world, or `<world>/data/deadlands_world.dat` for DEADLANDS data only.
- Regenerating assets: `python tools/assetgen/build_all.py` (needs Python with numpy, scipy,
  Pillow, soundfile; ffmpeg for audio). Output is deterministic: regenerating without changing a
  generator reproduced all 682 resource files byte for byte when last checked (2026-11-06, before
  the second half of Milestone 3; not rechecked for the 1,821 files there are now).

## 7. Manual checklist: Milestone 3 (not yet performed)

Create a new world (fixed seed, for example `deadlands`) after building, then:

1. **Biomes:** fly in several directions; the land should show the DEADLANDS biomes (F3 shows
   `deadlands:...`), with drab grass, fog and particles, dead trees, ground cover patches and
   biome ambience. Oceans, rivers, beaches and peaks stay vanilla. `/locate biome
   deadlands:dry_wasteland` should find one.
2. **Towns:** `/locate structure deadlands:town`, teleport there. Check streets (lines, sidewalks,
   flat crossings, levelled verges, dead lamps), gates for checkpoint/evacuated/survivor/bandit
   towns, buildings facing their streets on foundations, furniture models and orientation, loot
   containers (some empty and open in scavenged towns, some locked), and no terrain inside or on
   top of buildings.
3. **Town card:** walking into a town shows DISCOVERED (first visit) or ENTERING, the name, size,
   condition, population, history, faction, landmark, supplies and story, with the town sting. The
   journal's chronicle shows "<name> found <town>." *Interface > Town cards* off hides the card.
4. **Roads:** follow a road out of a town gate to the next town. Check that it meets the gate, has
   no seams at chunk borders, bridges water and valleys (deck, guardrails, pillars), tunnels
   through hills, and has poles with crossarms. *Road decay* 0 and 1 should look intact and wrecked.
5. **Settings:** *World > DEADLANDS biomes* off, then create a new world: vanilla biomes, with towns
   still generating in plains, forests, taiga, savanna, desert and snowy plains.
6. **Performance:** watch the frame rate and the server tick time while flying fast over new
   terrain with roads and towns.

Second half of Milestone 3 (also not yet performed):

7. **Cities:** find a city (the town card says so; about 1 town in 15). Check the street grid,
   crossings with zebra stripes and traffic lights, multi-storey buildings with working stairs,
   and that buildings do not overlap streets or each other.
8. **Western towns:** `/locate biome deadlands:dry_wasteland`, then a town there: saloons, general
   stores, wooden houses, the sheriff's office.
9. **Household containers:** open a kitchen cupboard, fridge, dresser, safe and school desk; each
   should animate, sound right and roll loot once.
10. **Roads and rails:** compare a highway (between two large towns or cities), an ordinary road
    and a dirt road (between two settlements); look for snow, mud and flooding in the right
    biomes, fallen logs, roadblocks, pylons and signs. Follow a railway: ballast, rails, trestles
    over water, a platform near town, buffer stops, and what happens where it meets a road.
11. **Sites:** `/locate structure deadlands:school` (and `forest_outpost`, `camp`, `military_post`,
    `bunker`, `utility_site`, `power_station`, `treatment_plant`, `farm_site`,
    `research_outpost`). Check each sits on the ground, is not in a town, and has its loot. In an
    overrun school, find the basement nest.
12. **Power:** take a fuse (creative tab *Crafting Materials*, or loot) to a generator (the power
    station, substations, bunkers and the school each have one) and use it. Expect a message, a chronicle line, and at night (lamps check on random
    ticks, so give them a minute) lit street lamps in towns within 700 blocks, whose town cards
    then say "The power is back on".

## 8. Manual checklist: Milestone 4 (not yet performed)

Use a new world on normal difficulty. Game-master commands need cheats on.

1. **Every kind up close:** spawn each of the 9 kinds from its spawn egg in creative, in daylight.
   For each: the model is whole (no floating or missing parts, nothing inside-out), the texture
   lines up on every part, it stands on the ground at the right height for its hitbox, it walks
   without sliding, and its idle pose looks right. Spawn several walkers and military: they should
   differ in clothes, build and height, and optional parts (helmets, vests, backpacks, caps)
   should match the texture. Look hard at the crawler (face down, dragging itself) and the stalker
   (crouched; climbing a wall).
2. **Attacks and actions (survival):** let each kind attack. Walker swipe and bite with a wind-up;
   runner sprint, then tiring; brute ground slam and knockback; screamer scream (others come
   running) and its retreat; crawler leap and the slowness it causes; bloater swelling, bursting
   and the poison cloud; stalker stalking, pounce and silence; charred setting you alight and not
   burning itself.
3. **Senses:** crouch near an infected that has not seen you (crouching makes no footstep noise
   and shortens the range it spots you at), then sprint or break a block (it should come to look). `/deadlands noise loud` should pull in everything nearby. Hide
   after being chased: it should search where it last saw you, then give up. At night, place
   torches in the open and watch idle infected drift toward the light.
4. **Barricades:** shut yourself in a house. They should pound on the door (animation, cracks,
   wood or metal sound) and break through; several at once faster; a brute faster still.
   *Zombies > Break barricades* off should stop it.
5. **Day and night:** by day most infected should be indoors or in the dark and there should be
   about half as many; at night they come out, with more runners. Nothing should spawn in a
   well-lit room. Vanilla zombies, skeletons and creepers should not appear in DEADLANDS biomes.
6. **Nests:** find one in an overrun town (or place one from *Custom Blocks*). Make noise near it:
   after enough, it tears open and a few infected climb out toward the noise (emerge animation);
   it then stays quiet for its cooldown. Lead them away and hide: they should go back in. Break
   the nest: everything inside comes out, it drops biomass and sometimes a tissue sample, and the
   chronicle records it. Save, quit and reload near an emptied nest: it should still be empty.
7. **Horde night:** `/deadlands day set 5` in the morning, then wait for dusk (or
   `/deadlands horde start`). Expect the warning, the horde card with its sting, waves from one
   direction, the counter going down as they die, distant crowd sounds, and no sleeping. Kill them
   all ("the horde is broken") in one test; in another, survive to dawn (dawn card with the kill
   count). Check the chronicle both times. Quit mid-horde and reload: it should resume.
   `/deadlands day set 10` should **not** start a horde (boss day).
8. **Migrations:** with *World events > Migrating hordes* on, travel between towns for a while;
   groups should appear ahead of you walking toward a town, and vanish again when you leave them
   far behind. `/deadlands infected count` shows loaded infected and groups.
9. **Sound:** listen to one infected in the open, then behind a wall (muffled), then from about 30
   blocks (thin and distant), then from 2 blocks (breathing). There should never be a constant
   loop. *Audio > Distant and muffled infected* off should make every voice plain.
10. **Performance:** `/deadlands infected count` near a town at night; watch the server tick time
    with 60 horde members alive. Lower *Performance > Max active AI* and check that distant
    infected still walk sensibly.
11. **Multiplayer:** two players far apart each get their own population; a horde night scales
    with players and both see the card and counter; a player joining mid-horde gets the counter.
12. **Settings:** *Zombies > Infected appear naturally* off stops natural spawning (nests and
    hordes still work); *Nest releases* off silences nests; *Interface > Horde card and counter*
    off hides the HUD but keeps the sounds.

## 9. Manual checklist: Milestone 5, first part (not yet performed)

Survival mode, a new world, *Survival > difficulty* SURVIVOR.

1. **HUD:** four dim meters bottom left. Sprint: stamina drops and lights up; at zero "Winded"
   appears, sprinting stops and cannot restart until stamina is back above a quarter.
2. **Thirst:** it drains slowly (faster sprinting). Drink a bottle of water (you get the empty
   bottle back), fill the bottle at a pond (dirty water), purify it with a tablet in the crafting
   grid, on a campfire and in a furnace. Dirty water sometimes makes you sick (nausea).
3. **Temperature:** stand in the frozen outskirts at night, in the rain, in water; then by a
   campfire, indoors, with armour on. Freezing and overheating should hurt and say so.
4. **Fatigue:** stay awake a few days: the stamina cap shrinks, then "Exhausted". Sleep a night:
   cleared.
5. **Injuries:** fall 9+ blocks (fracture: slow; a splint cures it; painkillers hide it). Let
   infected hit you until "Bleeding" (bandage and dirty rag stop it) and "Infected" (antiseptic
   early, antibiotics, antiviral serum). Medicine you do not need should refuse to be used.
6. **Infection to the end:** let it run (NIGHTMARE is fastest). At the last stage you take damage
   and die; a walker with your name stands up. On STORY it must stop before the last stage and
   nothing here may kill you.
7. **Settings:** turn each survival system off and check its meter disappears and its effects stop.
8. **Loot:** open containers in a town: cans, bottles, bandages and pills instead of bread and potions.
9. **Structures:** `/locate structure deadlands:gas_station` and `deadlands:roadside_stop`; find a
   library and a factory in a town; check fuel pumps look right from both sides.
10. **Skills:** kill infected, bandage a wound, open fresh containers, restart a generator: a chat
    line announces each level; `/deadlands skills` lists levels and experience. Die: skills stay.
11. **Weight:** fill the inventory with stone until "Overloaded": you slow down, and the load meter
    turns red. *Survival > Carrying weight* off removes it.
12. **Weapons:** compare the four melee weapons (damage and swing speed in the tooltip). Fire the
    pistol with and without 9 mm rounds in the inventory (shot, smoke, a hit at range; an empty
    click without rounds), watch it wear, and watch what the noise brings.
13. **Journal skills:** open the journal (J): the LOG page lists the five skills with levels and
    progress bars, above the keeper line, and updates after gaining experience.
14. **Backpacks:** pick up a backpack, then a military backpack: the load meter's capacity grows
    (15 kg, then 25 kg; they do not add up).
15. **Workbench:** craft one (two planks over two logs) or find one in the factory; using it opens
    a crafting grid that stays open; craft a baseball bat, 9 mm rounds and a splint on it.
16. **Bars:** in survival, DEADLANDS health and hunger bars sit above the hotbar where the hearts
    and drumsticks were; an armour bar appears with armour on; health goes red, then blinks, as it
    drops; a golden apple shows absorption. The setting brings Minecraft's own back. Check they do
    not overlap the air bubbles or a mount's health.
17. **Shotgun and rifle:** the shotgun kills close and barely scratches at 20 blocks; the rifle
    hits at 90 blocks and takes a second and a half to fire again. Wear a gun down (creative:
    `/item modify` or keep firing) until its tooltip says Failing, then watch it jam now and then.
18. **Backpack storage:** hold a backpack and use it: 18 slots open (27 for the military one). Put
    things in, close, reopen: still there. While open, the pack's own hotbar slot cannot be
    clicked or swapped. Try to put a second pack inside: it is handed back on closing. Drop the
    pack and pick it up, put it in a chest, die with it: the contents stay with it. The load meter
    counts what is inside. Try to break it: number keys, shift-click, dropping the pack with Q
    while open, the off hand.
19. **Reloading:** a found gun is empty: using it loads up to a full magazine from the inventory,
    with a sound and a pause; then each use fires one. The tooltip shows "Loaded: n / max". Sneak
    and use tops up a part-empty magazine. With no rounds anywhere: an empty click. In creative it
    fires without rounds.
20. **Screens:** press E in survival: the DEADLANDS inventory. Check every slot sits in a well, the
    survivor is drawn in the frame, the status page fits inside the panel at GUI scales 2, 3 and
    4 (nothing overlapping the skill list), and items move exactly as in Minecraft (shift-click,
    drag, number keys, armour swap, off hand). Click the status page while holding an item: it
    must not be thrown. Open a workbench, a crafting table, a chest and a backpack. Turn the
    setting off: Minecraft's screens return, with the recipe book.
21. **Ammo counter:** hold a gun: "n / max" beside the hotbar, red when empty; gone with anything else.

## 10. Manual checklist: Milestone 6 (not yet performed)

1. **Every vehicle:** `/deadlands vehicle sedan` (and the other eleven). Check the model is whole,
   sits on its wheels on the ground, faces the way it drives, and that its texture lines up. Add
   `abandoned` a few times: faded, rusted and burned-out finishes.
2. **Driving:** get in (use it), drive with the movement keys: it should accelerate, brake, reverse
   slowly, turn wider at speed, climb one block, and be faster on asphalt than on grass. Watch the
   wheels turn and steer. Check the camera and where the driver and passengers sit, on a sedan, the
   bus, the motorcycle and the tractor. Drive in multiplayer with a passenger.
3. **Fuel:** drive until the gauge empties: it rolls to a stop. Find a gas station
   (`/locate structure deadlands:gas_station`), read both pumps empty-handed, fill a fuel can at
   one that has fuel, pour it in. A drained pump must stay dry after a restart.
4. **Restoration:** on an abandoned vehicle, use it empty-handed for the list of needs; fit parts
   with and without a wrench in the inventory; it should say when it runs. A burned-out one refuses
   everything.
5. **Storage and saving:** sneak-use to open the load space; fill it, drive off, quit and reload:
   the vehicle, its condition, fuel and cargo are where you left them.
6. **Damage:** run over infected (they are hurt and thrown; the body gauge drops). Hit the vehicle
   until the body is gone: you are thrown out and it will not move until a body panel is fitted.
7. **Noise:** drive past a town at night and watch what follows.
8. **In the world:** follow a road between towns: an abandoned vehicle every few hundred blocks, in
   a lane or in the ditch, never inside a town, never buried or floating. Check none appear twice
   at chunk borders. Turn *World > Abandoned vehicles* off for new chunks.
9. **Vehicles at sites:** gas stations, checkpoints, evacuation camps and farmsteads should each have
   a vehicle standing clear of walls. Open a found vehicle's load space: about six in ten hold cargo.
10. **Engine and wheels:** a driven vehicle hums in a loop with no click at the seam, higher when
    faster, and stops when you get out. A vehicle short of tires shows wheels missing.

## 11. Manual checklist: Milestone 7 (not yet performed)

1. **Every role:** `/deadlands npc settler` (and guard, trader, doctor, mechanic, farmer, leader,
   bandit, wanderer). Each should stand, look like a person in the right clothes, have a name over
   their head, and wander. Check arms, legs and head are textured correctly on the player model.
2. **Conversation:** use one: the panel shows name, role, faction and standing, a greeting and
   reply buttons; the world keeps running. "Heard anything?" names a real town and direction.
3. **Trading:** trade with a trader, doctor, mechanic, farmer and merchant using 9 mm rounds; stock
   runs out and stays out. Raise reputation and check prices fall; lower it and check they rise.
4. **Services:** get hurt, bleed and break a leg, then ask a doctor; damage a vehicle and ask a
   mechanic standing next to it. Both should charge unless you are in good standing.
5. **Reputation:** hit a settler: reputation drops, guards come for you; kill one: "Hostile", and
   their people attack on sight and will not talk. Kill infected near them and trade to climb back.
   Die: reputation is kept. Bandits attack until you are no longer hostile to them.
6. **Day and night:** watch a survivor town at dusk: non-fighters go home and stay near it; guards
   keep walking. Let infected in: guards fight, settlers run, and the infected go for survivors.
7. **Settlements:** find a survivor town, a military checkpoint town and a bandit town; each should
   have residents in most buildings, suited to the building. Check no markers are left behind and
   nobody spawned inside a wall.
8. **Characters:** in a new world Harley should stand a few blocks from where you arrive. Meet the
   others while exploring (section 14), or place them with the command, and hear each one's history. Put a
   64x64 skin at `config/deadlands/skins/harley.png`, restart, and check she wears it.
9. **Merchants on the road:** travel by day in the open: now and then a travelling merchant
   appears, and is gone after about ten minutes once you leave.

## 12. Manual checklist: finishing Milestones 6 and 7 (not yet performed)

1. **Inside a vehicle:** look through the windows of a sedan, SUV, bus and jeep: seats, dashboard
   and steering wheel should show through the glass, and the jeep and tractor have open sides. Get
   in and out of a sedan: both front doors swing open and shut. A burned-out shell has no glass.
2. **Lights and horn:** drive at night with a battery fitted and press G: the road ahead lights up
   and the lamps glow; press again, the light goes. Remove the light when the vehicle is broken up
   and check no light block is left behind. Press H: a horn, and nearby infected come.
3. **Wear and growth:** set a vehicle's body below 70 and below 35 (hit it): dents appear, then rust
   holes and cracked glass. Find an overgrown wreck; using it with an empty hand pulls the growth off.
4. **Towing:** craft a tow rope, tie it to a running pickup, then to a wreck; drive off: the wreck
   follows facing the pickup, the pickup is slower. Use the wreck to untie it and get the rope back.
5. **Restoration task:** look over a broken vehicle: a chat line, and the journal's TASKS page
   (third bookmark) lists it with what it needs. Fit parts and fuel it: the task finishes.
6. **Where vehicles are:** in a fled town, cars parked at the kerb along the streets; fewer in a
   survivor town; jeeps in a checkpoint town; a pickup, SUV, jeep or motorcycle beside forest
   ranger stations and hunting cabins.
7. **Work:** watch a survivor town by day: farmers at crops (watch a crop grow a stage), the
   mechanic at a damaged vehicle, the doctor at a hurt neighbour, others at a workbench or fire.
8. **People:** ask several survivors "Do you have anyone?"; siblings share a surname. Kill one
   (in a test world): the relative's greeting turns to grief, and a fighter relative attacks.
9. **Settlements over time:** note a leader's report (population, stores, walls), sleep several
   nights away from the town, come back: the report has moved on. Give the leader food, then planks.
10. **Access and theft:** drop to Wary with the settlers (hit one), walk into their town: a warning,
    then after 25 seconds the guards attack until you leave. In a military town at night with
    neutral standing: the curfew warning. Open a crate in front of a resident: a theft message.
11. **Camps:** find a survivor camp (lit fire, a merchant) and a research camp (researchers who buy
    infected tissue).
12. **Characters:** hear all of Harley's history; the next part points to Itzbreadloaf (or says she
    has heard nothing). Restart the world: the story continues where it left off.
13. **Voices:** survivors murmur by day, greet when spoken to, shout when they fight, cry out when hurt.
14. **Walls:** give a survivor town's leader planks until its walls are strong: barricades go up
    either side of each gate, one stage at a time, while you are near.
15. **Seated poses and ropes:** drive with a passenger: the driver's hands are on the wheel, the
    passenger's in their lap. Tow a wreck behind a wreck behind a truck: two ropes are drawn.

## 13. Manual checklist: Milestone 8 (not yet performed)

1. **Opening:** in a new world the introduction plays (any key skips), you wake by Harley's camp
   (tent, fire, workbench, crate) with a radio and supplies, and Harley speaks. The QUESTS tab lists
   The Missing Research with directions.
2. **Lore:** open containers in houses, schools, clinics, police stations, offices and camps:
   documents appear that fit the place. Use one: it opens to read and goes in the ARCHIVE tab.
   Photos, tapes, newspapers and journals look different in the hand.
3. **Missing Research:** find a hospital or clinic, the Kessler facility (a green door to the lab, a
   red one to Wing D: try both cards) and an evacuation camp; bring the field case from the
   expedition camp; read four of Harley's notes. Harley tells you the army knew and gives a green
   card.
4. **Cure:** collect the six documents and samples and two boss trophies; hand them over; a day
   later the cure is done.
5. **Characters:** meet each of the other three (follow trail notes and radio calls); do their
   quests; check Imlilylol reveals FelixSyn and Site Calder, FelixSyn wants the Warden dead.
6. **Radio:** listen to all five stations before and after repairing one, two and three towers.
   Check boss warnings come days ahead on 96.4 and that Site Calder's grid reference is correct.
7. **Bosses:** `/deadlands boss abomination` (and each other). Watch every attack and its
   animation, the boss bar, the title card and sting, the drops. Let one survive to dawn: it leaves.
   On day 10 (or with a short boss interval) check one comes by itself at dusk, 40 to 56 blocks away.
8. **Helicopter:** at Site Calder fit the four parts; choose each ending in a test world (Extraction
   with the cure, Rescue without, Staying Behind). Check who is named aboard and that you are set
   down safely afterwards.
9. **Secret ending:** defeat Patient Zero, give Harley his blood, read all six of his records, and
   tell her about Elias.

## 14. Manual checklist: Milestone 9, the review, textures and the four characters (not yet performed)

1. **Weather:** `/deadlands weather fog`, then `wind` and `dust` (in a desert or badlands, then in a
   forest): fog thickens and tints, grit blows, dust only in dry land; a wind loop plays. Set storm
   frequency to 0 and check it stops raining.
2. **World events:** `/deadlands event supply_drop`: a crate falls under smoke, lands as a military
   crate, infected come to the noise; loot it. Try `crash`, `convoy`, `bandits`, `distress` and
   `outage`; each is announced and the emergency radio mentions it the next day.
3. **Ambience:** stand in open country, a forest, a city street, a hospital, a school, a factory, a
   farm, a campsite, a bunker and a cave: the background sounds should change, be quieter indoors,
   and a powered building hums.
4. **Cutscenes:** a new world plays the opening shot after the introduction; the first boss, the
   first horde, finishing the missing research, the cure and the extraction each play once. Skip one
   with any key. Turn *Interface > Cutscenes* off and check none play.
5. **Particles and firearms:** shoot the pistol (recoil, muzzle flash), hit infected and a boss
   (blood), stand by a nest core (spores), walk a burned biome (ash). Check the boss bar's look.
6. **Textures:** compare blocks and items with `texture-backup/` (same names): light comes from the
   upper left everywhere, nothing looks noisier or bluer than before, grass and leaves still take the
   biome colour, glass and decals unchanged.
7. **Character skins:** Harley, Itzbreadloaf, Imlilylol and FelixSyn wear the supplied skins with slim
   arms (no gap at the shoulder, held items in the hand).
8. **Allies:** place two characters near each other (`/deadlands npc`) and bring infected: once one
   fights, the others within 24 blocks join, including Harley and Imlilylol; none ever hits another.
   Hit one yourself: the others join against you; stop and walk away: they give up beyond about 32
   blocks.
9. **Meeting them:** in a new world, explore new ground (keep moving): about every half hour one of
   the other three should appear 24 to 40 blocks away and greet you, and never later than an hour of
   exploring. Standing still or staying in one spot should not bring anyone.
10. **Once per world:** `/deadlands npc felixsyn` after he has appeared: refused. `/kill` him, then
    the command works. Save, quit and reload: still one of each.
11. **Same-name player:** join with an account called FelixSyn (or rename a test account) while the
    character is near: the player list shows only the player; the character's nametag still reads
    FelixSyn with the supplied skin; the player is unchanged. Repeat with the player joining first.

