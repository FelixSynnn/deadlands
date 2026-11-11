# DEADLANDS: Status and Feasibility (Phase 0)

Written 2026-11-11 for the master development prompt (horror transformation). Phase 0 changes no
code: this file records what the project is, what works, what causes the problems you see, what
conflicts with the new story canon, and what each new requirement costs on this platform.

Everything below was checked by reading the project and the decompiled Minecraft 1.21.11 and
Fabric API 0.141.6 sources, or by build and test output. Anything not checked that way is marked
**unverified**.

## 1. The project

| Item | Value | Source |
|------|-------|--------|
| Minecraft | 1.21.11 | `gradle.properties` |
| Loader | Fabric Loader 0.20.1 | `gradle.properties` |
| Fabric API | 0.141.6+1.21.11 (the only dependency) | `gradle.properties`, `build.gradle` |
| Build plugin | Fabric Loom 1.19.3, Gradle 9.8.2 wrapper | `build.gradle`, `gradle/wrapper` |
| Mappings | Mojang official | `build.gradle` |
| Java | 21 for the game; Gradle itself needs JDK 25+ (`dev.ps1` finds it) | `build.gradle`, `dev.ps1` |
| Version | 0.9.0-m9-dev | `gradle.properties` |
| Build | `.\dev.ps1 build`: compiles, runs the 56 server game tests, writes `build/libs/deadlands-<version>.jar` | last run 2026-11-10: success, 56/56 |
| Dedicated server | `.\dev.ps1 runServer` (Loom run config, folder `run/server`) | never run yet; `run/server/eula.txt` does not exist |
| Client and server | Both: the mod has server code (`src/main`), client code (`src/client`) and mixins on both sides | `src/` |
| Repository | git, `main`, pushed to https://github.com/FelixSynnn/deadlands (public); release `v0.9.0-m9-dev` | |

**What this means:** every client-side requirement (perspective lock, gamma clamp, HUD, cutscene
cameras, first-person animations) is possible in principle, because the mod already ships client
code and client mixins.

## 2. What exists and works

"Works" here means it builds, the 56 server game tests pass, and the client game test passed once
(2026-11-10). Nothing has been played by hand.

| System | Main files |
|--------|------------|
| DEADLANDS title screen, settings, journal, inventory, crafting and chest screens (survival only; creative keeps vanilla screens by your request) | `client/ui/*Screen.java`, `client/mixin/MinecraftMixin.java` |
| 10 DEADLANDS land biomes replacing vanilla land biomes | `world/biome/BiomeReplacement.java`, `mixin/OverworldBiomeBuilderMixin.java`, `data/deadlands/worldgen/biome/` |
| Towns, cities, roads, railways, 120+ structure templates, sites, story places | `world/`, `tools/assetgen/gen_towns.py`, `gen_city.py`, `gen_sites.py` |
| Infected: 7 kinds (walker, runner, brute, screamer, military, crawler, bloater) plus stalker and charred classes, cuboid rigs with keyframed animations, noise-driven AI, nests, hordes, migrations | `entity/`, `spawning/InfectedSpawner.java`, `horde/`, `nest/`, `client/render/infected/` |
| Five bosses | `entity/BossEntity.java`, `horde/BossDirector.java` |
| Survival: stats, injuries, medicine, food and drink, weight, skills, melee weapons, a pistol | `survival/`, `skill/`, `item/` |
| Vehicles: 12 rigs with interiors, doors, lights, horn, towing, driving | `vehicle/`, `client/render/vehicle/` |
| Survivors, factions, settlements, dialogue, trading | `npc/`, `client/ui/DialogueScreen.java` |
| Story (Milestone 8): Harley's opening, quests, 134 lore entries, radio, keycards, bosses, helicopter, four endings | `story/`, `tools/assetgen/lore_data.py` |
| Polish (Milestone 9): weather overlay, world events, location ambience, a camera cutscene player, particles, recoil, boss bar | `world/weather/`, `event/`, `audio/`, `cutscene/`, `client/cutscene/` |
| Containers with loot, locks and keys | `container/` |
| Infected voices by distance and occlusion (never a loop, max 6 at once) | `client/sound/InfectedVoices.java` |
| Asset generators for every texture, model, sound and structure | `tools/assetgen/` |

## 3. What causes the problems you see

### 3.1 Vanilla mobs still appear

| Spawn route | Where it comes from | File |
|-------------|--------------------|------|
| Animals and bats in DEADLANDS biomes | Each biome's `spawners` list vanilla cows, pigs, sheep, chickens, horses, donkeys, rabbits, foxes, wolves, cats, frogs and bats | `data/deadlands/worldgen/biome/*.json` (written by `tools/assetgen/gen_world.py`) |
| Dungeon spawners (zombie, skeleton, spider) | Every DEADLANDS biome places `minecraft:monster_room` and `minecraft:monster_room_deep` | same biome files |
| Cave spider spawners | DEADLANDS adds its biomes to the mineshaft tag | `data/minecraft/tags/worldgen/biome/has_structure/mineshaft.json` |
| Trial spawners (breezes, zombies, skeletons...) | DEADLANDS adds its biomes to the trial chambers tag | `data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json` |
| Everything vanilla in oceans, rivers, beaches, mountain peaks and caves | These biomes are deliberately kept as vanilla, with their own spawn lists and structures (monuments and guardians, ocean ruins and drowned, shipwrecks, pillager outposts on peaks, ancient cities and the warden, strongholds and silverfish, ruined portals) | `world/biome/BiomeReplacement.java` |
| Patrols, raids, phantoms, wandering traders | Game rules `spawn_patrols`, `raids`, `spawn_phantoms`, `spawn_wandering_traders` are left on | nothing sets them |
| Vanilla spawn eggs | Still in the creative Spawn Eggs tab | nothing removes them |
| Golem building, the wither, mob conversions, `/summon` | No catch-all exists | none |
| Everything | Setting *World > DEADLANDS biomes* off restores every vanilla biome | `config/DeadlandsConfig.java` |

### 3.2 Music too often, too loud, and "chase music" with one zombie

- **Most music you hear is Minecraft's own game music.** In 1.21.11, biome audio lives in
  environment attributes. Every DEADLANDS biome sets `minecraft:audio/background_music` to `{}`
  (empty), so the game falls back to the overworld's default music, which plays every few minutes.
  File: `data/deadlands/worldgen/biome/*.json`.
- **The only DEADLANDS music track** is the title theme, which replaces `music.menu`
  (`assets/minecraft/sounds.json`) and plays only in menus.
- **There is no chase-music system in the code.** What sounds like chase music is **unverified**.
  The likely candidates:
  - vanilla game music happening to start near a fight;
  - infected alert voices, such as the runner's scream (`client/sound/InfectedVoices.java`, `sounds.json` keys `infected.*.alert`);
  - the horde stings, which play as interface sounds at full volume and ignore the music slider (`client/hud/HordeHud.java`).
- **Volume:** no music volume slider of DEADLANDS' own exists; the horde, boss and day stings
  ignore Minecraft's music slider because they play as UI sounds (`client/ui/DLTheme.playUi`).

### 3.3 It still feels like Minecraft

- **Free perspective:** F5 works (nothing locks it).
- **No darkness control:** brightness slider, night vision and moonlight all work normally (no
  mixin on `LightTexture`).
- **No flashlight item** exists.
- **Vanilla HUD:** hearts, hunger, XP bar, hotbar, crosshair (the DEADLANDS HUD adds bars but
  does not remove vanilla's) and the vanilla font.
- **Vanilla audio:** ambience, cave sounds and music in kept vanilla biomes.
- **Vanilla advancements and toasts** are untouched; there are no DEADLANDS achievements.
- **Items are flat sprites.** Weapons and tools in hand are vanilla's extruded 2D icons, which
  breaks the 3D-model rule.
- **Nether and End** are untouched.

## 4. Conflicts with the locked Story Canon (need your decision)

The prompt forbids replacing working systems without approval, so nothing below changes until you
decide.

| Existing | Conflict | Files |
|----------|----------|-------|
| Itzbreadloaf, Imlilylol and FelixSyn exist as living NPC allies in the world: met while exploring, quests, radio calls, joining the extraction | The canon makes them Adam's children and playable leads, and Lily and Felix are dead in Itzbreadloaf's campaign. Living NPC copies contradict the canon and would leak the family-house reveal | `npc/NpcCharacter.java`, `npc/NpcSpawns.java`, `npc/NpcWorldData.java`, `npc/NpcDialogue.java`, `story/Quests.java`, `story/StoryDirector.java`, `story/Radio.java`, `tools/assetgen/gen_npcs.py` |
| Their written histories: delivery driver, drifter trader, deserter soldier, all strangers who met Harley on the road | They are Adam's children, not strangers to each other | `gen_npcs.py` (`npc.deadlands.character.*`), `story_texts.py` |
| Lore that makes the four a road group with Harley ("Four of us now..."), 14 references | Same | `tools/assetgen/lore_data.py`, generated `story/LoreEntries.java` |
| Harley survives every ending: Extraction, Rescue, Staying Behind, Patient Zero | Harley is infected and Adam kills her near the end of the main campaign | `story/Endings.java`, `story/HelicopterEntity.java`, `en_us.json` `ending.deadlands.*` |
| Adam does not exist | He is the centre of the story | none |
| An open, procedural survival world: towns, settlements, hordes, migrations, world events | Hard Rule 6 asks for a guided, chapter-based campaign in bounded areas | `world/`, `npc/Settlements.java`, `horde/`, `event/` |
| Harley as a researcher at the regional lab who wants a cure | Fits: Adam's former coworker with a connection to his research. Her dialogue and quests need rewriting to the new questline | `story/`, `gen_npcs.py` |

**Proposal:** keep the open world as a separate **Survival** mode (unchanged systems, no canon
characters in it) and build the campaigns as a new **Campaign** mode in their own dimensions. That
extends instead of replacing. The recurring-character NPC system and the Milestone 8 story would
then be reworked into the campaigns (Harley, Adam) or retired from Survival (the three children).
Decision 1 in section 8.

## 5. Feasibility

Verdicts: **Native** (vanilla engine plus our own code or data), **Fabric API** (the existing
dependency), **New dependency** (named, with the version confirmed on Modrinth for 1.21.11
Fabric on 2026-11-11), **Not possible** (with the closest legitimate alternative).

### 5.1 Hard Rules

| Rule | Verdict | How | Risk |
|------|---------|-----|------|
| 1. First person only, third person in vehicles | Native | Client mixin: swallow `keyTogglePerspective` in `Minecraft.handleKeybinds` and force `Options.setCameraType` to first person each tick, except while seated in a DEADLANDS vehicle (third person back). Cutscenes already use their own camera (`client/mixin/CameraMixin.java`) | Low |
| 2. Zero vanilla mobs | Native + Fabric API | 1) Drop vanilla entries from DEADLANDS biome spawn lists. 2) Fabric `BiomeModifications` removal phase: clear vanilla spawns and remove `monster_room` features in every biome. 3) Override vanilla `has_structure/*` biome tags with empty lists for every structure that brings mobs or breaks the setting (villages, outposts, mansions, monuments, ocean ruins, igloos, witch huts, temples, ancient cities, strongholds, ruined portals, trial chambers, mineshafts). 4) Game rules raids, patrols, phantoms and wandering traders off at server start. 5) A catch-all mixin on `ServerLevel.addFreshEntity` refusing any `minecraft:` mob (covers spawners, eggs, golems, the wither, conversions, `/summon`). 6) Remove vanilla spawn eggs from the creative tab (`ItemGroupEvents`) | Low. Already-saved vanilla mobs in existing worlds would be removed when loaded, which changes existing saves: needs your OK (decision 4) |
| 3. Near-black nights, gamma and night vision ignored | Native | Client mixin on `LightTexture.updateLightTexture`: the brightness option, night vision and the sky light factor are plain inputs there (checked in the 1.21.11 source) and can be clamped. Night sky light is also a data-driven environment attribute (`visual/sky_light_factor`, `visual/sky_light_color`, `visual/star_brightness`, `visual/moon_phase`) set per biome. Day: fog and desaturated sky attributes. Lit safehouses keep block light | Low. An Iris shader pack would do its own lighting and could undo this (unsupported, documented) |
| 4. Flashlight, a directional cone, never a soft-lock | Native (option A) or New dependency (option B) | **A:** override the terrain and entity core shaders with a spotlight centred on the view and fading with distance, switched by a uniform the mod adds through a render-pipeline mixin. A true cone, cheap enough for Low. **B:** LambDynamicLights **4.9.1+1.21.11** (confirmed): dynamic light points along the beam and at the hit point. Works with Sodium, but each point lights a sphere, so the "cone" is approximate, and it costs chunk re-meshing. Batteries: a charge with slow recharge, so you are never left in the dark | **High.** A needs a prototype; it does not apply when Sodium (own terrain shaders) or an Iris pack is installed. Decision 3 |
| 5. Silence by default | Native | Biome `audio/background_music` and `audio/ambient_sounds` set deliberately; vanilla ambience, cave and underwater sounds replaced in `assets/minecraft/sounds.json`; vanilla music replaced by a DEADLANDS music director (mixin on `MusicManager`) | Low |
| 6. Guided chapters, diegetic boundaries | Native | A Campaign mode with its own dimensions (data-driven `dimension` and `dimension_type`), chapter zones built as structure templates by the existing generator, boundaries made of wrecks, rubble, fences and floodwater blocks, chapter state in saved data, checkpoints | **Very high content cost.** Level quality is limited to what the template generator can build and what is then hand-tuned |
| 7. Canon locked | Process | Section 4 lists every existing conflict | |
| 8. Honesty | Process | | |
| 9. Server testing only | Native | `.\dev.ps1 build` (also runs the 56 existing server game tests), then `.\dev.ps1 runServer`. The first server run needs `run/server/eula.txt` with `eula=true`, which accepts Minecraft's EULA: decision 5 | |

### 5.2 Systems

| System | Verdict | How | Risk / note |
|--------|---------|-----|-------------|
| Horror Director (server-authoritative tension, client presentation) | Native | Per-player state in saved data or attachments, scheduled scares sent as payloads, presentation on the client | Medium (tuning) |
| 25+ jumpscares | Native | Logic, creature poses and camera reactions on existing systems (`CameraMixin`, cuboid rigs, keyframes). Stingers are placeholders until sound design is done externally | Quality of the scare depends on sound and animation assets |
| Photosensitivity toggle, camera-shake slider | Native | Config and client checks | Low |
| Music director, chase rule (8+ targeting within 48 blocks for 2 s; fade out after 10 s below 5), music slider | Native | Server counts infected targeting each player, sends state; client plays music on the MUSIC source with its own volume setting | Low |
| Original soundtrack, emotional themes | Not producible here | Placeholder tracks (synthesised, as the title theme is) listed in `docs/ASSET_MANIFEST.md` for a composer | |
| Zombie sounds per behaviour | Native | Extend `InfectedVoices` with detect, chase, attack and feeding sets | Assets are synthesised placeholders |
| Missions (data files, cooldowns, tracker HUD, pause-screen Missions tab, co-op rules) | Native | JSON missions loaded by a resource reload listener; tracker as a HUD element (`HudElementRegistry`, Fabric API); a replaced pause screen. Today's quests are hard-coded in `story/Quests.java` and would move to data | Medium |
| About 40 achievements under `deadlands:`, vanilla ones hidden, custom toasts and screen | Native | Data-driven advancements with `hidden` and no chat announcement for story ones; vanilla advancements dropped on load by a mixin on the advancement manager; toast and screen replaced on the client | Low |
| Chapter select, checkpoints, skippable replays | Native | Saved data and a profile file | Medium |
| Replace every vanilla screen; custom HUD; no vanilla font | Native | Screen replacements (several exist); `HudElementRegistry` to remove hearts, hunger, XP and hotbar; a font provider in `assets/minecraft/font/default.json`. A font must be original or properly licensed: I can draw a bitmap font (the stencil letterforms already exist) or you supply a licensed TTF | Medium. Creative mode keeps vanilla screens unless you change your earlier instruction |
| Cinematic character selection | Native | A screen or a private scene rendering real character models with directed camera moves | Medium |
| Textures (realistic materials) | Native | Generators plus the realism pass (done for blocks and items) | Ongoing |
| Shadows | **Not possible** in vanilla rendering | Closest: baked shading in textures, ambient occlusion, careful light placement. Players may add Iris and a shader pack themselves (not shipped, not relied on) | |
| Volumetric fog, light shafts | **Not possible** as real volumetrics | Closest: fog distance and colour attributes, translucent geometry and particles for shafts | |
| Flickering fluorescents, emergency lights | Native | Light-block toggling (server) or client-side flicker | Low |
| Weather (rain, storms, fog, wind, wet look, transitions) | Native | Existing weather director, extended. No reflections: wet look from darker textures, puddle blocks and decals | Low |
| 3D models (weapons, equipment, world objects, characters) | Native for cuboid models; New dependency optional | Cuboid models in Java (entities) and JSON elements (items, blocks), as now. Arbitrary meshes are not loaded natively; a custom OBJ loader through Fabric's model loading API is possible. **GeckoLib 5.4.5** (confirmed) loads Blockbench models with animations, which suits creatures and weapons | Realistic meshes need external modelling (manifest) |
| First-person arms, weapon handling, reload and inspect animations | Native | Mixin on the first-person item renderer, keyframed poses | High effort |
| Third-person player animations | Native, or New dependency | Extending the existing `HumanoidModelMixin`, or **Player Animation Library 1.1.12** (confirmed) for keyframed player animations. The prompt says a new animation library needs approval | High effort |
| Facial expressions | **Not possible** as blend shapes | Closest: swapped face textures, head and eye movement | |
| Depth of field | Native, risky | A post-effect shader pass during cutscenes | Medium |
| Cutscene library (actors, dialogue, subtitles, skip per player, archive) | Native | Extend `CutscenePlayer`: actors are real entities moved by a server timeline, synchronized by payloads | High effort, content-heavy |
| Voice acting | Not producible here | Scripts and subtitles written by me; voices in the manifest | |
| Co-op 1-4 players, host control, sync, reconnection | Native | Minecraft multiplayer (LAN or dedicated); server-authoritative state | Medium |
| Per-player character selection | Native | Player skin and name override on the client (`AbstractClientPlayer` skin), choice stored on the server | Medium |
| Vehicles with automatic third person | Native | Existing vehicles plus rule 1's camera switch | Low |
| Graphics presets | Native | Config mapped onto vanilla video options | Low |
| Nether and End disabled | Native | Cancel portal frame lighting, inert End frames and eyes of ender, block dimension changes, no strongholds | Low |
| Campaign unlocks across worlds | Native | A small profile file in the config folder, sent to the server on join | Low |

### 5.3 Candidate new dependencies (none added; each needs your approval)

| Library | For | Confirmed build for 1.21.11 Fabric |
|---------|-----|------------------------------------|
| LambDynamicLights | Flashlight option B | 4.9.1+1.21.11 (2025-12-11) |
| GeckoLib | Animated Blockbench models (creatures, weapons, characters) | 5.4.5 (2026-03-03) |
| Player Animation Library | Third-person player animations | 1.1.12 (2026-11-04) |
| Veil, PlayerAnimator, AzureLib | Not available for 1.21.11 Fabric | none |

Sodium 0.8.15-beta.1 and Iris 1.10.8 exist for 1.21.11. They are not dependencies, but players
who install them would bypass flashlight option A and possibly the darkness clamp. This needs
deciding before Phase 1.

## 6. Size, honestly

The full prompt amounts to several games' worth of content:
- three campaigns, chapter levels and a large cutscene library;
- 25+ jumpscares, 40 achievements and 3D models for everything;
- first-person animation and voice.

Systems (camera, darkness, mob removal, music, missions, achievements, director, cutscene framework,
co-op) are realistic here. Content quality is the limiting factor: levels, models, music and
voice will reach placeholder or good-generated quality from me, and the manifest will list what
needs an artist, composer or actor.

## 7. Phased plan, adjusted

**Phase 1: immersion blockers.**
- Vanilla mob removal (5.1 rule 2).
- Perspective lock and vehicle camera.
- Darkness clamp and night attributes.
- Flashlight: item, batteries, cone prototype (option A, or B if approved).
- Silence: vanilla music replaced by a music director with the chase rule and a music slider, vanilla ambience removed, horde stings moved to a slider-controlled source.
- Nether and End disabled (if chosen).
- Server test: build, start the server, force-load a fresh area, count entities.

**Phase 2: foundation.**
- Data-driven content layer: missions, dialogue, cutscenes, achievements, jumpscares, endings.
- Horror Director, mission system, story flags, achievement framework, cutscene framework skeleton.
- The Campaign mode and its first dimension (if decision 1 is yes).

**Phase 3: visuals.**
- Textures, the model pipeline (cuboid; GeckoLib if approved), first character, weapon and environment models.
- Lighting fixtures, weather and atmosphere.

**Phase 4: gameplay.**
- First-person arms and weapon animations, interactions, inventory, weapons, infected types, vehicles, survival rules.
- The jumpscare library.

**Phase 5: story and cinematics.**
- Adam, Harley, Lily, Felix, the family mystery.
- Itzbreadloaf's campaign first, then Imlilylol's and felixSyn's.
- Chapters, missions, endings, lore, cutscene library.

**Phase 6: multiplayer.**
- Co-op, shared progression, character selection, synchronized scenes, disconnect handling.

**Phase 7: polish.**
- Performance, audio mix, animation blending, UI, bug fixing.
- A final server test and the full playtest checklist.

## 8. Decisions (approved 2026-11-11: "Approve all recommended")

Approved: all 11 horror ideas from Phase 0 (Wrong Room, Lights Ahead, Dead Air, The Call, The Shy
One, Old Routines, Phantom Objective, Split Signal, The Other Teammate, Old Voicemail, Hollow
Safehouse); Nether and End disabled; the proposed endings, campaign unlock order and co-op
character rules; and the recommended answer to each question below:

1. Survival and Campaign modes: **keep the open world as Survival; campaigns in their own dimensions.**
2. Existing characters and story: **rework into the new canon.**
3. Flashlight: **option A (shader cone) first.**
4. Existing worlds: **remove saved vanilla mobs when they load.**
5. Server test: **create `run/server/eula.txt` with `eula=true`.**
6. Creative mode: **keeps Minecraft's screens.**
7. Name: **felixSyn.**

The questions as asked:


1. **Survival and Campaign modes:** keep the open world as a Survival mode and build the campaigns
   in their own dimensions (recommended), or convert the open world itself?
2. **The existing characters and story:** may I rework the recurring-character NPCs, the
   Milestone 8 quests, lore and endings into the new canon (replacing working content)?
3. **Flashlight:** prototype option A (shader cone, no new dependency, not compatible with Sodium
   or Iris packs), or approve LambDynamicLights (option B)?
4. **Existing worlds:** may vanilla mobs already saved in existing worlds be removed when loaded?
5. **Server test:** may I create `run/server/eula.txt` with `eula=true` (this accepts Minecraft's
   EULA, https://aka.ms/MinecraftEULA) so the dedicated server can start?
6. **Creative mode:** keep vanilla screens in creative (your earlier instruction), or replace them
   too?
7. **Name casing:** "felixSyn" (this prompt) or "FelixSyn" (the current game and the skins)?
