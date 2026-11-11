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
- **Then Phase 3 (visuals):** textures, the 3D model pipeline, first character, weapon and environment models, lighting fixtures, weather and atmosphere.
