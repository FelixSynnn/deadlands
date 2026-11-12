# DEADLANDS: Horror Transformation Progress

Tracks the phases of the master development prompt. Phase 0 (inspection and proposals) is in
[DEADLANDS_STATUS.md](DEADLANDS_STATUS.md), with the approved decisions in its section 8.

## Phase 1: Immersion blockers (2026-11-11)

### Done and server-verified

| What | How it was verified |
|------|---------------------|
| The project builds | `.\dev.ps1 build`: success; all 56 existing server game tests pass (they include every item, sound and setting having its assets and text) |
| The dedicated server boots with no errors from DEADLANDS code | `.\dev.ps1 runServer`: "Done (5.554s)"; the only error is Minecraft's own first-run notice that `server.properties` did not exist yet. Every server mixin applied |
| No vanilla mobs in freshly generated land | From the console: `forceload add 3000 3000 3100 3100` and `forceload add -4000 2000 -3900 2100` (49 new chunks each), then `/deadlands mobs`: **"Vanilla mobs loaded: 0"** after each |
| Vanilla mobs refused by every other route | `summon minecraft:cow` and `summon minecraft:zombie` from the console: neither entered the world (`/deadlands mobs` still 0; vanilla's command prints "Summoned" because it ignores the refusal). `summon deadlands:walker` worked (infected loaded: 1) |
| Raids, patrols, phantoms and wandering traders off | `/deadlands mobs` and `gamerule raids` / `gamerule spawn_patrols`: all false |
| Shaders compile and link | A throwaway harness (not in the project) compiled DEADLANDS' shader overrides in a hidden OpenGL 3.3 core context on the development PC's GPU (AMD Radeon RX 7600S), preprocessed as Minecraft does: terrain (2 variants), entity (9 variants), lightmap, and the vanilla shaders that read the enlarged `Globals` block. 18 programs linked, 0 failures; `Globals` is 80 bytes, matching the buffer the mixin allocates. **This proves the shaders compile, not how they look** |
| Flashlight grant and chase-music trigger wired to the right events | By reading the code: flashlight on `ServerPlayConnectionEvents.JOIN` and `ServerPlayerEvents.AFTER_RESPAWN` (`item/Flashlights.java`); chase music from `ChaseTracker` (every 10 ticks, infected targeting the player within 48 blocks) through `ChaseMusicPayload` to `MusicDirector` |

### What changed

- **Vanilla mobs (Hard Rule 2):** `world/VanillaMobs.java`, `mixin/ServerLevelMixin.java`.
  - Biome spawn lists are cleared of vanilla entries and vanilla dungeons are removed in every biome (Fabric biome API).
  - The DEADLANDS biomes no longer list animals or bats (`gen_world.py`).
  - All 34 vanilla structure biome tags are empty (`gen_immersion.py`).
  - Game rules are switched off at every server start.
  - A catch-all refuses any vanilla mob entering a level; vanilla mobs saved in older worlds are removed as they load.
  - Vanilla spawn eggs are out of the creative tab.
- **First person (Hard Rule 1):** `client/camera/PerspectiveLock.java`. The perspective key is swallowed every tick. On foot the camera is forced to first person; in any seat, third person.
- **Darkness (Hard Rule 3):**
  - `client/mixin/LightTextureMixin.java` and `client/light/Darkness.java`: the brightness slider and night vision are ignored, sky light fades to nothing at dusk (no moonlight), and daylight is 85% and less saturated.
  - `shaders/core/lightmap.fsh`: vanilla's floor of light is cut from 4% to 0.6%.
  - `client/mixin/SkyRendererMixin.java`: stars at a tenth of their brightness.
  - A faint "near sight" term lets the eye make out about two blocks.
- **Flashlight (Hard Rule 4, option A):**
  - `item/FlashlightItem.java`: a 3D box model; right-click toggles it.
  - Charge is 30 minutes of light. It drains only while held and on, and recharges one second per second while off, so you are never stuck in the dark.
  - `item/Flashlights.java`: given on join and respawn if missing.
  - `client/light/FlashlightClient.java` and `client/mixin/GlobalSettingsUniformMixin.java` append the beam to the shared shader globals.
  - `shaders/core/terrain.*` and `entity.*` add a cone of light centred on the view (10° full, fading to 23°, range 26 blocks).
  - Below 10% charge the beam dims and stutters.
- **Silence and music (Hard Rule 5, Audio Rules):**
  - `client/mixin/MinecraftMixin.java`: vanilla game music never plays in a world.
  - Vanilla cave and underwater ambience are silenced; the menu theme plays at 45%.
  - `client/sound/MusicDirector.java`:
    - Chase music only when the server reports 8 or more infected targeting you within 48 blocks for 2 seconds. It fades in over 2 s and fades out over 5 s once fewer than 5 have been chasing for 10 s.
    - At most one exploration piece per 10–15 minutes of active play.
  - New settings: *Audio > Music volume* (45%), *Chase music: crowd size* (8), *Chase music: ends below* (5).
  - Day, town, horde and boss stings now play on the music channel at the music volume, not at full interface volume.
- **Nether and End disabled:** `mixin/PortalShapeMixin.java` (frames never light), `EnderEyeItemMixin.java` (eyes do nothing), `EntityPortalMixin.java` (no portal travel, even through old portals). No strongholds.
- **Console check:** `/deadlands mobs` (game masters) lists:
  - vanilla mobs loaded and infected loaded;
  - the four vanilla event rules;
  - for each player, how many infected are chasing them and whether chase music is on.

### Done, needs playtest (client-side: not tested by me)

Playtest checklist for Phase 1:

- [ ] **No vanilla mobs:** walk around day and night (forest, town, river, coast, mountain, cave): no animals, monsters, bats, fish, villagers or golems appear. Try a vanilla spawn egg in creative: nothing appears.
- [ ] **First person:** on foot, F5 does nothing. Get into a vehicle as driver and as passenger: the camera switches to third person. Get out: first person again.
- [ ] **Darkness:** outdoors at midnight with the brightness slider at max and the flashlight off, the screen is near black; you can make out only what is within about two blocks. A drink of night vision changes nothing. Inside an unlit house by day it is dark too. Daytime is dimmer, greyer, still readable. Stars are faint.
- [ ] **Flashlight:**
  - A new world: you hold a flashlight in your hotbar. Die and respawn: you have one.
  - Right-click: a cone of light where you look, brightest at the centre, fading at its edges and with distance (about 26 blocks).
  - Off again: dark.
  - Drain it (the bar falls over 30 minutes; speed up by testing with a timer): below a tenth it dims and stutters, then switches off; leave it off and it recharges.
  - Check the model in hand, in the inventory and on the ground (box model; its in-hand position is a first guess).
- [ ] **Music:**
  - No Minecraft music in a world. The menu theme is quieter and stops when you join.
  - 1 to 7 infected chasing: no music. 8 or more chasing for 2 seconds: chase music starts; once fewer than 5 have been chasing for 10 seconds it fades out over about 5 seconds.
  - Roughly every 10–15 minutes of play, at most one quiet exploration piece.
  - *Audio > Music volume* and Minecraft's Music slider both change it.
- [ ] **Silence:** no vanilla cave sounds underground, no underwater ambience.
- [ ] **Nether and End:** light an obsidian frame with flint and steel: no portal. Use an eye of ender: nothing happens.

### Known limitations

- **Sodium or Iris:** the flashlight cone and the darkness floor are drawn by core-shader overrides. With **Sodium** installed, terrain uses Sodium's own shaders, so the cone will not light the ground (entities still are). An **Iris shader pack** does its own lighting and may undo both. Neither is supported.
- **Other players' beams are not visible:** the cone is drawn from your own camera. Seeing teammates' flashlights belongs to Phase 6 (co-op).
- **The beam starts at the camera,** not at the flashlight in your hand.
- **Placeholders:** the music tracks are placeholders and the flashlight click is a vanilla button sound (see `ASSET_MANIFEST.md`).
- **"Summoned" message:** a refused `/summon` of a vanilla mob still prints "Summoned"; the mob is not in the world.

### In progress

Nothing.

### Blocked

Nothing.

### Next

- **Your playtest of the checklist above.** I fix what fails before moving on.
- **Then Phase 2 (foundation):**
  - the data-driven content layer (missions, dialogue, cutscenes, achievements, jumpscares, endings);
  - Horror Director, mission system, story flags, achievement framework, cutscene skeleton;
  - the Campaign mode's first dimension.

## Phase 2: Foundation and architecture (2026-11-11)

### Done and server-verified

| What | How it was verified |
|------|---------------------|
| The project builds; the server boots | `.\dev.ps1 build`: success, all 56 server game tests pass. `.\dev.ps1 runServer`: "Done (1.473s)", no warnings or errors from DEADLANDS code |
| The data-driven content layer loads | Boot log: "DEADLANDS content: 19 jumpscare, 1 mission, 2 mission_giver, 1 dialogue, 1 ending" (every file decoded); `/deadlands content`: "Loaded: 19 scares, 1 missions, 2 mission givers, 1 dialogues, 1 endings, 16 DEADLANDS achievements; vanilla advancements left: 0" |
| Content reloads | `/reload`, then `/deadlands content`: the same counts |
| Vanilla advancements removed, DEADLANDS achievements loaded | `/deadlands content`: 16 DEADLANDS (root and 15), 0 vanilla outside recipes |
| Story flags persist | `/deadlands flag set dev_pipeline_ok`, `/deadlands flag list`: "1 story flags: dev_pipeline_ok"; `deadlands_flags.dat` written on stop |
| Mission and campaign state saved | `deadlands_missions.dat` and `deadlands_campaign.dat` written on stop; `/deadlands mission list` reports the loaded mission and givers |
| The campaign dimension exists | `/deadlands campaign status`: "dimension loaded: true"; `execute in deadlands:campaign run setblock 0 63 0 deadlands:concrete` then `execute in deadlands:campaign if block ...`: "Test passed" |
| Phase 1 still holds | `/deadlands mobs`: "Vanilla mobs loaded: 0", the four vanilla event rules false |

### What changed

- **Your sounds:** `tools/assetgen/import_sfx.py` imports the clips you chose from the "Horror/Thriller Sounds" playlist. All 6 laugh and clown clips are left out, as you asked; so is "I'm Burning", a spoken girl's line that doesn't fit and could clash with Lily's story.
  - **Night creature:** the alien rattle, as 1 long rattle and 3 short calls. It is positional, heard far off in the dark at night.
  - **Dread beds:** tense pressure, creepy bass, background suspense. Old timbers creak near water at night.
  - **Unsettling moments:** 4 whispers, a creaking door, a distant horn, the forest sting.
  - **Scare stingers:** riser with flare, fright, hit, swell, whoosh, suspense, scream, jumpscare chase.
  - **Music:** chase music is now the two violin-strike tracks; exploration music is "Typical Horror Ambience" and "Low Tone Atmosphere". They replace the Phase 1 placeholders.
- **Content layer** (`content/DLContent.java`):
  - Scares, missions, mission givers, dialogue and endings are data files in `data/<namespace>/jumpscare|mission|mission_giver|dialogue|ending/`, reloaded with `/reload`.
  - Achievements are advancements in `data/deadlands/advancement/`.
  - Cutscenes are client data in `assets/deadlands/cutscene/`; the six existing scenes moved there from Java (`CutsceneLibrary`).
- **Horror Director** (`horror/HorrorDirector.java`, client `horror/ScarePlayer.java`):
  - **Schedule:** server-authoritative, one schedule per survivor, counting only active play.
  - **Frequency, on Normal:** at night a major scare every 4–8 minutes and a minor event every 1–3; by day a major every 10–15 and a minor every 3–6. Low and Nightmare presets are under *Horror > Scares*.
  - **Never repeats** the same scare within 30 minutes. **No major scare** while infected are chasing you.
  - **Never during** conversations or cutscenes, nor for 2 minutes after them.
  - **What a scare can do:** sounds at the ear, behind, far off, ahead or everywhere; held breaths (ambience stops and music dips); flashlight flicker and blackout; a camera flinch; a nearby door slamming.
  - **Library:** 19 scares (11 minor, 8 major) in data.
  - **Accessibility:** *Horror > Photosensitivity mode* (flickers become slow fades) and *Camera shake* (0–100%). Neither stops scares.
- **Story flags** (`flags/StoryFlags.java`):
  - World flags and counters shared by everyone, plus each player's own (kept through death).
  - Data conditions can test both, including "not" (`!flag`) and player flags (`player:flag`).
- **Missions** (`mission/MissionManager.java`):
  - **Objectives:** have an item, kill a type, a story flag set, or visit a structure.
  - **Shared and host-controlled;** progress is saved and survives disconnects.
  - **Rewards** go to each survivor exactly once (offline players get theirs when they next join).
  - **Givers:** a next offer waits 10–15 minutes of active play after the last mission is completed, declined or abandoned; one active mission per giver. Harley's offers stop for good once `harley_dead` is set.
  - **HUD:** a top-right tracker (mission name and current objective, sliding in with a quiet sound).
  - **Pause screen:** a **Missions** button, shown only once you have received a mission, opens the Active / Completed / Failed tabs with giver, objectives and related lore.
  - Harley's and Adam's actual missions are Phase 5. One development mission checks the pipeline and is never offered to players.
- **Achievements** (`achievement/Achievements.java`):
  - Vanilla advancements are gone (recipe unlocks kept).
  - **15 DEADLANDS achievements so far:** first light, first dawn, week one, a town, the school, a bunker, first infected killed, a horde night, a boss, the first document, 25 documents, the first scare, 50 scares, a night without the flashlight, four survivors together.
  - None is announced in chat. Story and ending achievements will be hidden until earned.
  - DEADLANDS-styled toast. The pause menu's Advancements button opens the DEADLANDS **Achievements** screen, grouped by category, with hidden ones shown as "???".
- **Campaign mode skeleton** (`campaign/Campaign.java`):
  - The empty `deadlands:campaign` dimension, plus saved campaign, chapter and checkpoint.
  - Entering and leaving remembers where you came from. A concrete pad appears where nothing is built yet; chapters arrive in Phase 5.
- **Commands** (game masters), under `/deadlands`:
  - `horror status|play <scare>`
  - `flag list|set|clear`
  - `mission list|start|complete|fail|offer`
  - `achievement <path>`
  - `campaign enter|leave|status`
  - `content`

### Done, needs playtest (client-side, or needs a player: not tested by me)

Playtest checklist for Phase 2:

- [ ] **Night creature:** at night outdoors, in the dark, now and then a rattling call far away, from a different direction each time. You see the subtitle "Something rattles in the dark".
- [ ] **Scares:**
  - `/deadlands horror play deadlands:scream_behind`: ambience drops away, a swell, then a scream right behind you with a stinger and a flinch.
  - Try `deadlands:beam_dies` with the flashlight on: it flickers, dies, a whisper at your ear, it comes back with a hit.
  - Try `deadlands:door_slams` indoors near an open door: the door slams.
- [ ] **Scare timing:**
  - Play normally at night for 15 minutes: a few minor events, one or two major scares, and never the same one twice.
  - Open a conversation or the inventory: nothing happens while it is open, and nothing for 2 minutes after a conversation.
  - `/deadlands horror status` shows the schedule.
- [ ] **Accessibility:** *Horror > Photosensitivity mode* makes flickers slow fades. *Camera shake* at 0 stops the flinch.
- [ ] **Music:** chase music (8+ chasing) is now the violin-strike tracks; the rare exploration piece is the low ambience.
- [ ] **Missions:**
  - `/deadlands mission start deadlands:dev/pipeline_check`: "New mission" in chat, the tracker slides in top right.
  - The Missions button appears on the pause screen and the tab lists it.
  - Kill 2 walkers (the tracker counts up), then `/deadlands flag set dev_pipeline_ok`: it completes, everyone gets one bandage once, and it moves to Completed.
  - Reconnect: no second bandage.
- [ ] **Achievements:**
  - Vanilla advancement toasts never appear.
  - Picking up the flashlight gives "First Light" with the DEADLANDS toast.
  - The pause menu's Advancements button opens the DEADLANDS Achievements screen.
- [ ] **Campaign:** `/deadlands campaign enter` puts you on a concrete pad in an empty grey world; `/deadlands campaign leave` brings you back where you were.
- [ ] **Cutscenes still play** exactly as before (they now come from data files): the opening after a new world's introduction, `/deadlands boss abomination` for the boss reveal.

### Known limitations

- **Story content:** Harley's and Adam's missions, the campaign chapters, and the story and ending achievements are Phase 5. The givers and the dimension exist but are empty until then.
- **Scare library:** 19 scares now; the prompt's 25+ (with creature figures and animations) is Phase 4.
- **Placeholder files kept:** the Phase 1 placeholder tracks (`music/chase.ogg`, `music/explore.ogg`) are no longer used. They are still on disk because deleting files needs your OK.
- **Bugs:** no new contained bugs were found. The remaining known issues (trees in towns, ruins on roads) only affect Survival mode's world generation.

### Next

- **Your playtest** of the Phase 1 and Phase 2 checklists.
- **Then Phase 3 (visuals):** started; see Phase 3 below.

## Phase 3: Visual transformation, batch 1 (2026-11-11)

Phase 3 is being built in batches ("do not build everything at once if it hurts quality"). This
batch covers the ambience fix you asked for, real 3D weapon models, light fixtures and weather
visibility. Batches 2 and 3 are listed under Next.

### Done and server-verified

| What | How it was verified |
|------|---------------------|
| The project builds; every server test passes | `.\dev.ps1 build`: success, all 57 server game tests pass (asset presence for the new blocks, loot tables parse, every block has state, model, name and loot) |
| The server boots | `.\dev.ps1 runServer`: "Done (1.033s)", no warnings or errors from DEADLANDS code (one "Can't keep up" while the force-loaded test chunk generated) |
| Fluorescent light: a steady fixture is lit outside towns | `setblock 4 100 0 deadlands:fluorescent_light`, then `execute if block ... [lit=true]`: true |
| Fluorescent light: a faulty tube flickers, and keeps flickering after a restart | `setblock ... [faulty=true]`, `/deadlands lights`: 2 toggles after 5 s; after a restart: 26 |
| Emergency light pulses | Sampled once a second for 8 s: bright, dim, bright, dim, dim, bright, dim, dim (1.5 s halves) |
| Heavy rain | `/deadlands weather heavy_rain`: "The weather turns: heavy rain." (it also starts Minecraft's rain) |
| Earlier phases still hold | `/deadlands content`: 19 scares, 16 achievements, 0 vanilla advancements; `/deadlands mobs`: 0 vanilla mobs; raids, patrols, phantoms and traders off |

### What changed

- **Ambience: quieter and mostly silent.** You asked for this: "too loud, make it not always play, make the scary ambience play sometimes".
  - Spells and hushes:
    - The soundscape now alternates spells of sound (1 to 2.5 minutes) with hushes (1 to 3.5 minutes, longer at night).
    - In a hush, the calm wind, room tone and biome bed fade out and no everyday sound plays.
    - Weather is never hushed: rain on the roof and a real gale keep going.
  - Volume and spacing:
    - Everything is about half as loud.
    - Everyday sounds are at least 10 seconds apart, at most two at once, and their gaps are about 1.5 times longer.
    - The main-menu bed is quieter too.
  - Biome beds:
    - Every biome used to loop its own bed through Minecraft at full volume, nonstop, on top of the director's wind.
    - Minecraft no longer plays it. The director plays it quietly, and only in spells of sound.
    - Biome one-shots are half as frequent.
  - Scary sounds, sometimes:
    - They come every 3 to 7 minutes after dark (or underground), and every 8 to 15 minutes by day.
    - The world goes quiet for four seconds first, then one eerie sound plays: mostly the alien call at night; otherwise distant infected, an unexplained noise, a low dread swell, or water creaking in the pipes indoors.
    - They wait while a Horror Director scare is recent (2 minutes), during chases and in cutscenes.
  - Files changed:
    - `client/sound/AmbienceDirector.java` (rewritten)
    - `ScarePlayer.sinceLastScare()`, `MusicDirector.chased()`, `MenuAmbience`
    - the ten biome files and `tools/assetgen/gen_world.py`
- **3D weapon models** (Hard Rule: never an enlarged sprite).
  - All 13 weapons now have 3D models, plus a detailed flashlight:
    - melee: machete, rusty machete, crowbar, baseball bat, nail bat, fire axe, survival knife, hunting knife, lead pipe, pipe wrench;
    - firearms: pistol, shotgun, hunting rifle.
  - Built from shaped boxes with any rotation, which Minecraft 1.21.11 allows. Details include the crowbar's curled claw, the bat's taper and nails, the axe's blade and pick, the pistol's slide serrations, ejection port and checkered grip, and the rifle's scope and bolt.
  - Each face is unwrapped onto its own texture at 8 texels per unit and painted by material: brushed steel with a ground bevel, rust and worn edges, chipped paint, wood grain, rubber grip rings, tape, checkered walnut, brass, glass.
  - Where they show:
    - The inventory keeps the flat icon (2D is allowed for practical UI).
    - Hands, ground, item frames and shelves show the 3D model.
    - Each weapon keeps its tuned in-hand position.
  - New tools in `tools/assetgen/`:
    - `dl_models3d.py`: box modelling, UV unwrapping, material painting.
    - `gen_models3d.py`: the models.
    - `preview_models.py`: renders model files offline from several angles; it never starts the game.
    - The texture finishing pass skips these unwrapped textures.
- **Light fixtures** (new blocks, in the Furniture tab).
  - *Fluorescent Light*:
    - A ceiling fixture with two glowing tubes (light level 14).
    - In a town it follows the town's power: dark until power is restored. Outside towns it is lit.
    - A faulty tube stutters, sometimes dies for a few seconds, and buzzes as it comes back.
  - *Emergency Light*: a red battery lamp on a wall. It pulses bright and dim while the power is out, and goes dark when the town's power is restored.
  - Files: `block/FluorescentLightBlock.java`, `block/EmergencyLightBlock.java`, `registry/DLLights.java`, `tools/assetgen/gen_visuals.py`, and the console check `/deadlands lights`.
- **Weather reduces visibility.**
  - Visibility drops in bad weather:
    - rain: the fog closes in to about 110 blocks;
    - thunderstorm: about 70;
    - the new *heavy rain* weather: about 44, with an overcast grey.
  - Heavy rain also splashes the ground around you and drums louder on roofs.
  - Fix: weather fog used to tint toward light grey at any hour, which would have lit the horizon at night. Every tint now follows the sky's own brightness, so nights stay black (Hard Rule 3).
  - Files: `world/weather/WeatherDirector.java` (new kind `heavy_rain`), `client/weather/ClientWeather.java`, `client/mixin/AtmosphericFogMixin.java`.

### Done, needs playtest (client-side: not tested by me)

Playtest checklist for Phase 3 batch 1:

- [ ] **Ambience:**
  - Stand outdoors for 5 minutes. You should hear stretches of real silence, then quiet sound (wind, a crow, a creak), then silence again. Nothing loud, nothing nonstop.
  - At night, within about 7 minutes, everything goes quiet and then one eerie sound plays far away (often the alien call).
  - Indoors in rain: the rain on the roof keeps going through the quiet stretches.
  - If it is still too loud or too busy, *Audio > Ambience volume* and *How often ambient sounds play* still work.
- [ ] **Weapons in 3D:**
  - Hold each weapon. It should be a solid 3D object in your hand, roughly where the old icon was: handle in the hand, tip up and ahead.
  - The inventory still shows the flat icons.
  - Drop one, and put one in an item frame: both show 3D.
  - Tell me any that sit wrong (too big, too far, clipping the camera). The position is tuned per weapon.
- [ ] **Flashlight:** check the new model in hand: bezel and lens at the front, knurled body, red switch, pocket clip.
- [ ] **Light fixtures:**
  - Place a Fluorescent Light from the Furniture tab on a ceiling in a dark room. It should light the room, and its tubes should glow.
  - `/setblock ~ ~2 ~ deadlands:fluorescent_light[faulty=true]`: it flickers, sometimes dies for a few seconds, and buzzes.
  - Place an Emergency Light on a wall: a red lamp pulsing slowly.
  - In a town, both follow the town's power: the fluorescent stays dark and the emergency light pulses until you restore power.
- [ ] **Weather:**
  - `/weather rain`: the view closes in a little. `/weather thunder`: more.
  - `/deadlands weather heavy_rain`: about 44 blocks of visibility, and splashes around you.
  - At night in rain or fog, the distance stays black, with no grey glow.

### Known limitations

- **Fixtures are not yet placed in buildings.** They exist as blocks (creative tab, `/setblock`). Batch 2 puts them in town interiors, with a powered and an unpowered look for each building.
- **Weapons have no animations yet** (equip, inspect, reload, fire): that is Phase 4. They are good box models, not artist meshes; they are listed in `docs/ASSET_MANIFEST.md`.
- **Shadows and volumetric light** remain impossible in vanilla rendering (see the status document). The light fixtures and fog are the closest legitimate tools.
- **Dev test world:** the dev server's test world (`run/server`) now has three test blocks near 0, 100, 0, and chunk 0, 0 force-loaded from this check. No player world was touched.

### Next (Phase 3 batches 2 and 3)

- **Batch 2: environment.**
  - Light fixtures in town interiors, for powered and unpowered buildings.
  - 3D models for equipment: radio, backpack, medical supplies, batteries, keys, food and water.
  - A check and upgrade of the furniture and roadside models.
  - Wet surfaces in rain.
- **Batch 3: characters and textures.** The first 3D character model and the texture overhaul pass (interiors, decay, distinct rooms).
- **Phase 3 end:** build, server test, and the full Phase 3 playtest checklist.

## Phase 3: Visual transformation, batch 2 (2026-11-11)

Environment: lights inside town buildings, 3D equipment, detailed furniture and roadside models,
and wet surfaces in rain.

### Done and server-verified

| What | How it was verified |
|------|---------------------|
| The project builds; every server test passes | `.\dev.ps1 build`: success, all 57 server game tests pass, including the structure template checks (every town template loads with its new fixtures) |
| The server boots | `.\dev.ps1 runServer`: "Done (1.110s)", no warnings or errors from DEADLANDS code |
| Town buildings carry their lights | `place template deadlands:town/grocery 0 120 0`: its 3 fluorescents are there and dark, its 2 emergency lights are there |
| Fixtures react to power | With random ticks sped up (`gamerule random_tick_speed`, reset to 3 afterwards), all 3 fluorescents of the placed grocery switched on: a building outside any town has its own supply |
| Faulty tubes keep flickering | `/deadlands lights`: 18 switches in this run |
| Shaders compile | Offline check (hidden OpenGL 3.3 context, the shaders preprocessed as Minecraft does): all 18 DEADLANDS shader programs link on the AMD RX 7600S, with the uniform block now 96 bytes |
| Earlier phases still hold | `/deadlands content`: 19 scares, 16 achievements, 0 vanilla advancements; `/deadlands mobs`: 0 vanilla mobs, raids, patrols, phantoms and traders off |

### What changed

- **Lights in town buildings.**
  - 41 of the town and city building templates now have fixtures: 130 fluorescents and 88 emergency lights.
  - Which buildings:
    - Shops, the diner, police stations, town halls, the library, banks, the courthouse, fire stations, clinics, emergency rooms, garages, warehouses, the factory, offices and the department store get rows of ceiling fluorescents on a 4-block grid. About a third of them are faulty.
    - These buildings also get emergency lights on inner walls, one per floor (two on a big floor).
    - Churches, apartment blocks and motels get emergency lights only.
    - Homes, barns, sheds and the western town stay unlit: flashlight territory.
  - How they behave: fluorescents are placed dark and light up when you restore the town's power. Until then the emergency lights pulse red. So a dead town stays dark, and a restored one glows.
  - The pass only adds fixtures in empty ceiling and wall spots; nothing else in a building moved. Regenerating the templates without it reproduces the old files byte for byte.
  - Files: `tools/assetgen/gen_towns.py` (`light_fixtures`).
- **38 equipment models in 3D.**
  - Packs: backpack, military backpack.
  - Medical: medkit, bandage, splint, antiseptic, antibiotics, painkillers, antiviral syringe, dirty rag.
  - Gear and keys: radio, car battery, key with tag, the four keycards.
  - Food and drink: four kinds of can, soda, energy drink, jerky, crackers, energy bar, MRE, stale bread.
  - Water: three water bottles, purification tablets.
  - Ammunition: pistol, shotgun and rifle.
  - Other: fuel can, radio parts, fuse.
  - The inventory keeps the flat icons. Each model sits where its icon sat in the hand.
  - File: `tools/assetgen/gen_equipment3d.py`.
- **Round parts are now true polygons.**
  - Round bars used to be overlapping turned squares, which gave cans and poles a fluted, star-shaped look.
  - They are now built from thin plates whose union is exactly a regular polygon (checked numerically): flat, even facets.
  - End caps are staggered by a hair so they cannot flicker against each other.
  - This applies to all weapons and the flashlight too.
  - File: `tools/assetgen/dl_models3d.py`.
- **Furniture and roadside.** 13 kinds rebuilt in detail, keeping their names, facing and collision:
  - utility pole with step bolts;
  - round street lamp post;
  - cobra-head street lamp, lit and dark;
  - traffic light with three lenses and visors;
  - can transformer with bushings and a pole bracket;
  - W-beam guardrail on an I-beam post;
  - framed generator with its painted control panel, running and stopped;
  - whiteboard with a marker tray;
  - workbench with a vise, a toolbox and a hammer;
  - couch with cushions;
  - bed with a thrown-back blanket and a pillow.
  - Random versions per position, so rooms do not repeat:
    - bookshelves: three fills, from tightly packed to looted;
    - desks: papers, an old CRT computer with keyboard, or a desk lamp and phone;
    - counters: one in three has a microwave.
  - File: `tools/assetgen/gen_decor3d.py`.
- **Wet surfaces in rain.**
  - Surfaces under the open sky darken and deepen in colour as rain soaks in, over about 15 seconds. The ground darkens more than walls, with darker patches where water pools.
  - They dry over about a minute after the rain stops. Snow does not wet anything.
  - It only ever darkens, so nights stay black.
  - Files: `ClientWeather.wetness()`; the uniform block gains `vec4 DeadlandsWeather` (`GlobalSettingsUniformMixin`, `shaders/include/globals.glsl`); the terrain shaders `shaders/core/terrain.vsh` and `terrain.fsh`.
- **Tooling.**
  - Model faces can reuse an existing painted texture.
  - Wood and metal can run their grain vertically.
  - Large objects use fewer texels per unit instead of oversized textures.
  - Fix: the offline preview tool rendered models inside out (its face normals were reversed). It now shows the outside. Model files were not affected.

### Done, needs playtest (client-side: not tested by me)

Playtest checklist for Phase 3 batch 2:

- [ ] **Town at night, power out:**
  - Shops and offices are dark inside, with red emergency lights pulsing on the walls.
  - Houses are dark.
- [ ] **Restore a town's power** (generator plus fuse):
  - Within a minute or two the fluorescents come on, building by building.
  - Some tubes flicker and buzz. The emergency lights go dark.
- [ ] **Equipment in 3D:**
  - Hold each item: solid, about where the icon was.
  - Inventory icons are unchanged.
  - Dropped items and items in frames are 3D.
  - Tell me any that sit wrong.
- [ ] **Furniture:**
  - Desks, bookshelves and counters vary from one to the next.
  - Desks: papers, a CRT computer, or a lamp and phone.
  - Bookshelves: different fills.
  - Some counters have a microwave.
  - Street lamps, traffic lights, utility poles, transformers, guardrails and generators look solid and round where they should.
- [ ] **Rain:**
  - Stand outside in rain for 20 seconds: the ground darkens, with darker patches.
  - Walls darken less.
  - Under a roof it stays dry.
  - After the rain it dries over about a minute.

### Known limitations

- **Furniture variety** comes from random versions of the same kinds. New kinds (TVs, phones as their own blocks, computers on their own) would need new blocks, and placement in the building generator; that work is not done.
- **Wet look is darkening only.** Real reflections and puddle mirrors are not possible in vanilla rendering.
- **Shader packs:** with Iris and a shader pack installed, the pack's own shaders replace these, so the wet look and the flashlight cone (option A) do not apply.
- **Road signs, the fuel pump, toilet, sink, store shelf, table and chair** keep their earlier models, which were already acceptable.

### Next (Phase 3 batch 3)

- The first 3D character model (Itzbreadloaf) for third person and cutscenes.
- The texture overhaul pass: interiors, decay, distinct rooms.
- Then the Phase 3 end: build, server test, and the full Phase 3 playtest checklist.

## Phase 3: Visual transformation, batch 3 and phase end (2026-11-11)

This batch adds the first 3D character model and the interior texture pass. With it, Phase 3 is complete.

### Done and server-verified

| What | How it was verified |
|------|---------------------|
| The project builds; every server test passes | `.\dev.ps1 build`: success, all 57 server game tests pass (new blocks have state, model, name and loot; every town template loads) |
| The server boots | `.\dev.ps1 runServer`: "Done (1.499s)", no warnings or errors from DEADLANDS code |
| Finished interiors are in the templates | `place template deadlands:town/suburban_siding 40 120 0`, then `execute if block`: wood panelling on an interior wall, carpet in a room, linoleum in the kitchen |
| The new materials place | `setblock ... deadlands:faded_wallpaper` and `striped_wallpaper`: placed |
| Earlier phases still hold | `/deadlands content`: 19 scares, 16 achievements, 0 vanilla advancements; `/deadlands mobs`: 0 vanilla mobs |

### What changed

- **Itzbreadloaf in 3D.**
  - His NPC now wears 3D gear over his skin:
    - a canvas backpack with a bedroll;
    - tan shoulder and chest straps;
    - a belt with a buckle, a hip pouch and a holstered flashlight;
    - a bandage on his right forearm and a watch on his left wrist;
    - loose tufts of hair.
  - The gear is built on the player model's own body parts, so it follows every walk, turn and gesture.
  - His skin (the canon look) is unchanged underneath.
  - Files: the generator `tools/assetgen/gen_character3d.py` writes the geometry (`client/render/character/CharacterGear.java`) and the texture. `CharacterGearLayer` draws it, and `NpcRenderer` adds it for Itzbreadloaf.
- **Interior texture pass.**
  - Five new aged materials (`registry/DLInteriors.java`, `tools/assetgen/gen_interiors.py`), all with water stains, peeling and wear:
    - faded floral wallpaper;
    - striped wallpaper;
    - dark wood panelling;
    - stained carpet;
    - checkered linoleum.
  - The town generator finishes the inside of homes, farmhouses, apartments, motels, offices and wooden houses (63 templates):
    - each building picks one wall finish for its interior walls: either wallpaper, panelling, or bare plaster;
    - kitchens get linoleum;
    - other rooms get carpet, or keep their boards.
  - Outer walls, furniture and loot are untouched. An upper storey's carpet never shows on the ceiling below.
  - File: `gen_towns.interior_finish`.

### Done, needs playtest (client-side: not tested by me)

Playtest checklist for Phase 3 batch 3:

- [ ] **Itzbreadloaf:**
  - Find him; `/deadlands` story commands or his settlement work.
  - He wears the backpack and bedroll, straps, belt, pouch and holstered flashlight, wrap and watch.
  - The gear moves with him when he walks and turns, with no floating pieces.
- [ ] **Interiors:**
  - Walk through several houses.
  - Interior walls differ from house to house: floral, striped, panelled or bare.
  - Kitchens have linoleum; other rooms have carpet or boards.
  - Offices have linoleum.
  - No wallpaper on the outside of a building.

### Phase 3 summary

Built in three batches; each passed a build, all 57 server tests and a dedicated-server check.

- **Batch 1:**
  - 3D models for all 13 weapons and the flashlight.
  - Flickering fluorescent lights and battery emergency lights.
  - Rain, storms and heavy rain reduce visibility, with night-safe fog.
  - The ambience fix: quieter, mostly silent, eerie sounds now and then.
- **Batch 2:**
  - Lights in 41 town buildings, following town power.
  - 38 equipment models in 3D.
  - Round parts built as true polygons.
  - 13 furniture and roadside pieces rebuilt, with random desk, shelf and counter versions.
  - Rain-soaked surfaces.
- **Batch 3:** Itzbreadloaf's 3D gear and finished interiors.

The **full Phase 3 playtest checklist** is the three batch checklists above (batch 1, batch 2, batch 3). Please run them together in one session.

### Known limitations (Phase 3 as a whole)

- **Models:**
  - Every model is a generated cuboid model, not an artist mesh. They are listed in `docs/ASSET_MANIFEST.md` for replacement.
  - Weapon and character animations are Phase 4.
- **Characters:**
  - Only Itzbreadloaf has 3D gear so far.
  - Imlilylol, felixSyn, Adam and Harley follow the same pipeline (one entry each in `gen_character3d.py`) when their campaigns come (Phase 5).
  - Players get gear with character selection (Phase 6).
- **Rendering:** shadows, volumetric light and reflections are not possible in vanilla rendering. Shader packs replace the DEADLANDS shaders (wet look, flashlight cone).
- **Dev test world:** the dev server's test world has test buildings and blocks near 0 to 51, 120, 0 from these checks. No player world was touched.

### Next

- **Your playtest** of the Phase 3 checklists (and Phases 1 and 2, if not done yet). I fix what fails before moving on.
- **Then Phase 4 (gameplay):**
  - first-person arms and weapon animations;
  - interactions, inventory, weapons, infected types, vehicles and survival rules;
  - the jumpscare library to 25+.
