# DEADLANDS: Technical Architecture

This document describes how the code is organised today (through Milestone 4) and how each later
system plugs in. Read it before adding a system. Milestones 3 to 5 compile but have never
been run, so the parts describing them say how the code is meant to work.

## Principles

1. **Server authority.** The server owns all game state. Clients receive state through packets and
   may only *request* things; they never decide outcomes. Singleplayer runs the same code through
   the integrated server.
2. **Client code never loads on a dedicated server.** Loom's split source sets enforce it at
   compile time: `src/main` cannot reference `src/client`. Common code that needs to trigger
   client behaviour goes through `bridge/ClientBridge`.
3. **Declare once.** A setting is one annotated field (file, validation and UI follow). An item is
   one `register(...)` call (creative tab follows). A sound is one `DLSounds` line plus a
   `sounds.json` entry; the asset test fails if the file is missing.
4. **Persist deliberately.** Anything that must survive a restart lives in a `SavedData` with a
   `Codec` and `optionalFieldOf` defaults, so older saves keep loading as fields are added.
5. **Deterministic content.** Generated assets use fixed seeds; future world generation uses the
   world seed, so the same seed gives the same world.
6. **Verified APIs.** Minecraft and Fabric signatures are checked against the decompiled 1.21.11
   sources (Mojang names), never guessed.

## Source sets

| Source set | Path | Contents |
|------------|------|----------|
| main | `src/main/java`, `src/main/resources` | common and server code, all assets and data |
| client | `src/client/java`, `src/client/resources` | screens, HUD, input, client networking, client mixins |
| gametest | `src/gametest/java`, `src/gametest/resources` | server and client game tests (not shipped in the jar) |

## Packages

```
com.deadlands
├── Deadlands                 common entrypoint: boot order, MOD_ID, id(), version()
├── block/                    RoadLineBlock (oriented road paint), DecalBlock (floor/wall/ceiling marks),
│                             DecorKind + FacingDecorBlock/PostDecorBlock/AxisDecorBlock (furniture, fixtures),
│                             LampHeadBlock (lit when its town has power), PowerGeneratorBlock (takes a fuse), Shapes2
├── bridge/ClientBridge       common -> client indirection (open the journal; infected voices and effects)
├── command/                  /deadlands
├── config/                   ConfigEntry, DeadlandsConfig, ConfigManager
├── container/                ContainerKind, ContainerCondition, ContainerStyle, ContainerLock,
│                             LootContainerBlock, LootContainerBlockEntity, DLContainerMenu, ContainerOpenData
├── entity/                   InfectedKind (stats per kind), InfectedEntity (shared behaviour), the kinds with
│   │                         their own tricks (Runner, Brute, Screamer, Crawler, Bloater, Stalker, Charred),
│   │                         InfectedVariants (generated: every look)
│   └── ai/                   goals: attack, target, hunt, search, investigate, breach (+ BreachTracker), follow
│                             pack, roam, seek shelter, return to nest, migrate, scream, slam, pounce, stalk,
│                             swell; InfectedNav (path helpers)
├── event/                    DEADLANDS events (DayChangeCallback)
├── horde/                    HordeDirector (horde nights), MigrationSystem (groups travelling between towns)
├── item/                     SurvivorJournalItem, KeyItem, KeycardItem
├── mixin/                    OverworldBiomeBuilderMixin (DEADLANDS biomes in vanilla climate slots),
│                             ServerLevelMixin (game events -> noise)
├── nest/                     NestKind, NestBlock, NestBlockEntity (population, alertness, cooldown), NestRegistry
├── network/                  payloads (world_status, day_begin, town_card, horde, request_status) + DLNetworking
├── noise/                    Loudness (six levels), NoiseSystem (game events -> noises -> infected and nests)
├── persistence/              DeadlandsWorldData, PlayerRecord, ChronicleEntry, TownRecord, HordeRecord,
│                             MigrationRecord
├── player/                   SurvivorTracker (join, death, respawn)
├── progression/              WorldClock, EncounterSchedule, BossKind, ScheduleSettings, TimeOfDay
├── registry/                 DLItems, DLBlocks, DLDecor, DLContainers, DLNests, DLBlockEntities, DLMenus,
│                             DLEntities, DLDataComponents, DLSounds, DLTags, DLTab, DLCreativeTabs
├── spawning/                 InfectedManager (tracking, caps, AI budget), InfectedSpawner (natural population),
│                             InfectedSpawnTable (who appears where), SpawnFinder (where one fits),
│                             InfectedSystems (starts all of Milestone 4's server systems)
└── world/                    DLWorldgen (features, structure and piece types), WorldgenView, Hashing
    ├── biome/                DLBiomes (keys, tags), BiomeReplacement (vanilla -> DEADLANDS mapping)
    ├── feature/              SurfaceCoverFeature + SurfacePalette (per-biome ground cover, puddles)
    ├── power/                PowerGrid (which towns have power again)
    ├── road/                 RoadNetwork (which towns are linked), RoadKind (highway, road, dirt), RoadEdge
    │                         (curve, height profile), RoadFeature (draws roads, bridges, tunnels, poles, signs),
    │                         RailNetwork, RailEdge, RailFeature (railways)
    └── town/                 TownSites/TownSite/TownSize/TownCondition (where towns are and what they are),
                              TownStructure + TownPlanner (layout), TownStreetPiece, TownCrossingPiece,
                              TownGatePiece, TownBuildingPiece, TownIdentity (name, history...),
                              TownLookup (nearest town), TownTracker (arrivals)

com.deadlands.client
├── DeadlandsClient           client entrypoint (render layers, renderers, model layers, menu screen, HUD)
├── ClientState               last world status from the server
├── ClientNetworking          packet receivers, status requests
├── hud/                      DayCardHud (DAY card), TownCardHud (town card), HordeHud (horde card and counter);
│                             HudElementRegistry, before chat
├── input/DLKeyMappings       key category + J for the journal
├── mixin/                    MinecraftMixin (title screen swap), ScreenMixin (backdrop)
├── render/                   LootContainerRenderer (+ render state), ContainerPartModels (lids, drawer, doors)
│   └── infected/             InfectedGeometry (generated rigs), InfectedLayers, InfectedRenderState,
│                             InfectedModel (+ Humanoid, Brute), InfectedAnimations, InfectedRenderer
├── sound/                    InfectedVoices (what each listener hears), InfectedVoiceSound
└── ui/                       DLTheme, TitleBackdrop, DeadlandsTitleScreen, JournalScreen, SettingsScreen,
                              DLContainerScreen, widget/ (DLButton, DLMenuButton, DLToggle, DLSlider)
```

### Containers (Milestone 2)

- **One block class, many kinds.** `ContainerKind` holds everything that differs: rows, screen
  style, how it opens (lid, drawer, door, double door), open speed, rare-loot chance, shape and
  sounds. Adding a container kind is one enum entry, a part model in `ContainerPartModels`, and a
  geometry/texture entry in `tools/assetgen/gen_containers.py`.
- **Block state:** `facing`, `condition` (clean/dusty/rusty/damaged: textures and loot amount),
  `ajar` (rests slightly open; structures use it for storytelling).
- **Loot rolls once.** A structure stores only `LootTable` in the block entity. The first open (or
  break) rolls it through vanilla's `RandomizableContainer`, then applies the Loot settings
  (container fill, damaged = half, rarity multiplier for an extra roll on `<kind>_rare`), and the
  table id is cleared. From then on the contents are ordinary saved items: unloading chunks or
  restarting never regenerates loot (verified by the client test across a save and reload).
- **Locks.** `ContainerLock` (`deadlands_lock` in the block entity NBT): KEY locks need a
  `deadlands:key` whose `deadlands:key_id` component matches; KEYCARD locks need a keycard of at
  least the level. The right key in either hand unlocks the container permanently. While locked,
  `LootContainerBlock.getDestroyProgress` returns 0 for players not in creative mode, so the
  lock cannot be bypassed with a pickaxe (explosives still work). The block entity's update tag
  carries only the lock (never items), so the client predicts mining the same way as the server.
- **Animation.** The body is a baked block model; the moving part is a `ModelPart` drawn by
  `LootContainerRenderer` using 1.21.11's render-state API (`extractRenderState` + `submit`).
  Opening uses vanilla's `ContainerOpenersCounter`: the server counts viewers, sends a block event,
  the client eases openness each tick, and the renderer interpolates per frame.
- **Part textures.** `ContainerPartModels` declares 64x32 UVs, but the images are 128x64: UVs are
  relative to the declared size, so parts get 2 texels per pixel like the block faces. The parts
  are modelled y-up without vanilla's entity flip, so with the standard cube UV layout side faces
  show their texture rotated 180 degrees; `gen_containers.put()` pre-rotates the art, which is
  always authored as seen from the front.
- **Hollow bodies.** Crate and toolbox bodies are an outer shell without a top plus zero-thickness
  planes for the inner floor, inner walls and rim (`gen_containers.hollow`), so an open lid
  reveals real depth. Cavity shading is baked into the interior textures.
- **Screen.** `DLContainerMenu` extends vanilla `ChestMenu` (same slots and shift-click) with its
  own menu type (Fabric `ExtendedScreenHandlerType`, carrying rows and style), so
  `DLContainerScreen` can draw a wooden, metal or medical panel from nine-slice sprites.

### Planned packages (created when their system starts, not before)

| Package | Milestone | Hooks it will use |
|---------|-----------|-------------------|
| `survival`, `inventory`, `crafting`, `skill`, weapons | 5 | Fabric data attachments for per-player stats, `Survival` config, `NoiseSystem.emit` with `Loudness.GUNSHOT` |
| `vehicle`, `fuel` | 6 | entity + persistent storage |
| `npc`, `dialogue`, `trade`, `faction`, `settlement` | 7 | distance-based simulation (`Performance` config), `DayChangeCallback` |
| `quest`, `lore`, `radio`, `boss`, `ending` | 8 | `EncounterSchedule` bosses, `DeadlandsWorldData` story flags, `InfectedManager` caps |
| `audio`, `weather`, `worldevent`, `cutscene` | 9 | `Weather`/`WorldEvents`/`Audio` config |

## Boot order

`Deadlands.onInitialize` (both sides):

1. `ConfigManager.load()`: everything else reads config.
2. `DLSounds`, `DLDataComponents`, `DLItems`, `DLBlocks`, `DLDecor`, `DLNests`, `DLContainers`:
   content registries (blocks register their own block items).
3. `DLBlockEntities`, `DLMenus`: types that reference the container blocks.
4. `DLEntities`: the nine infected types, their attributes and spawn eggs (after `DLItems`).
5. `DLWorldgen`: features (surface cover, road and rail networks), the town structure type and its
   piece types; adds the rail and road features to every overworld biome (Fabric biome API) and
   clears the town, road and rail caches when the server stops.
6. `DLCreativeTabs`: registers one tab per category that now has content.
7. `DLNetworking`: payload types, receivers, config-change listener.
8. `WorldClock`, `SurvivorTracker`, `TownTracker`: server-side systems.
9. `InfectedSystems`: the infected manager, noise system, nest registry, breach tracker, natural
   spawner, horde director and migration system, and the death hook they share.
10. `DeadlandsCommand`.

`DeadlandsClient.onInitializeClient` (client only): key bindings, packet receivers, render layers
(glass, decals, dead leaves), the infected model layers and one renderer per
infected type, the container block-entity renderer and screen, HUD elements (DAY card, town card,
horde), the `ClientBridge` handlers (journal, infected voices), a tick handler for the journal key
and the horde's distant sounds, texture release on world join, and a reset of all of it on
disconnect.

## Data flow

```
overworld dayTime ──> WorldClock (server tick, every 20 ticks)
                         │ new day? (compared with DeadlandsWorldData.lastProcessedDay)
                         ├─> DayChangeCallback.EVENT   (future: hordes, bosses, radio, settlements)
                         ├─> day_begin packet ──> DayCardHud (client)
                         └─> world_status packet ──> ClientState ──> JournalScreen

player joins ──> SurvivorTracker: PlayerRecord, chronicle line, journal, world_status, day_begin
player dies  ──> SurvivorTracker: deaths, chronicle line
journal opens ─> request_status packet ──> server replies with world_status
config edited / reloaded ──> ConfigManager listeners ──> server re-sends world_status to everyone

player in a town ──> TownTracker (every 20 ticks: town structure start at the player's position)
                         ├─> first visit by anyone? TownRecord + chronicle line ──> world_status to all
                         └─> town_card packet ──> TownCardHud (client; waits for the DAY card)

game event (step, door, block broken...) ──> ServerLevelMixin ──> NoiseSystem
                         ├─> infected within the noise's radius (times their hearing): investigate
                         └─> nests within reach: alertness up ──> release toward the noise

every 40 ticks ──> InfectedSpawner: a pack near each survivor, if under the caps and in the dark
every 20 ticks ──> InfectedManager: rank infected by distance to survivors, set each one's AI interval

horde day, dusk ──> HordeDirector: HordeRecord saved, horde packet (START) ──> HordeHud
                         ├─> a wave every 200 ticks until the total has arrived (60 alive at most)
                         ├─> horde packet (STATUS) every second ──> counter
                         └─> all dead (BROKEN) or dawn (DAWN): chronicle line, packet, record cleared

fuse used on a generator ──> PowerGrid.restore: towns within 700 blocks saved as powered
                         └─> lamp heads light at night, fewer night spawns there, town card says so
```

## Persistence

- `<world>/data/deadlands_world.dat` (`DeadlandsWorldData`, stored on the overworld):
  `schema`, `last_processed_day`, `players` (UUID string -> record), `chronicle` (capped list),
  `towns` (discovered towns: start-chunk key -> name, first day, who found it, position, size,
  condition), `horde` (the horde in progress: day, total, how many arrived and died, direction),
  `migrations` (travelling groups: position, destination, size) with `next_migration_id`, and
  `powered_towns`. Everything else about a town (history, population, faction...) is derived from
  the seed by `TownIdentity` and never saved.
- Survivor records also count infected killed.
- Infected save their look (`Variant`, `Physique`) and their orders (`HordeDay`, `Migration`,
  `Pack`, `Nest`) with the entity. Horde and migration members persist until their system lets
  them go; everything else (wanderers, infected released by a nest) despawns far from everyone. On load, a horde member from a
  night that is over, or a migration member whose group no longer exists, is released or removed.
- Nests save `Population`, `Alert`, `CooldownUntil`, `LastGrowth` and `Released` with their block
  entity, so an emptied nest stays empty.
- Fields are added with `optionalFieldOf(name, default)`. Bump `SCHEMA_VERSION` only for changes
  that need migration code.
- Per-player state that must follow the player entity (thirst, temperature, skills) will use
  Fabric data attachments (M5); world-level state stays in `DeadlandsWorldData` or new `SavedData`.
- The game tests verify a codec round trip and an in-game save and reload.

## Networking

| Payload | Direction | Content |
|---------|-----------|---------|
| `deadlands:world_status` | server -> client | day, schedule settings, survivor record summary (with infected killed), last 16 chronicle lines |
| `deadlands:day_begin` | server -> client | day number, first-arrival flag |
| `deadlands:town_card` | server -> client | town name, size, condition, former population, translation keys for faction, history, landmark, supplies and story, first-discovery flag, whether its power is back |
| `deadlands:horde` | server -> client | kind (status, start, broken, dawn, clear), day, total, remaining |
| `deadlands:request_status` | client -> server | empty; server answers with `world_status` |

Payloads are records with explicit `StreamCodec`s and bounded strings and lists.
`ServerPlayNetworking.canSend` guards every send.

## Configuration

`config/deadlands.jsonc`: JSON with `//` comments, parsed leniently. Each field of a section
class annotated with `@ConfigEntry` provides the comment, numeric range, whether it is client-only
and, while its system is missing, `pendingMilestone`. `ConfigManager`:

- `get()` returns the live snapshot (never cache it; read it when needed).
- `replace(edited)` validates, publishes and saves atomically (settings screen).
- `load()` re-reads the file (`/deadlands config reload`); out-of-range values are clamped and
  reported; a broken file is backed up and replaced with defaults.
- Listeners run on publish; `DLNetworking` uses one to re-sync clients.

In multiplayer the server's config is authoritative. Clients receive the parts they need to
display (`ScheduleSettings`). Client-only settings (interface, audio) apply locally.

## UI system

- Everything visual is in `DLTheme` (colours, sprite ids, UI sounds).
- Sprites live in the GUI atlas (`textures/gui/sprites/...`) with nine-slice metadata; large
  images (title layers, journal) are standalone textures drawn with `blit`.
- `DeadlandsTitleScreen` extends `TitleScreen` so `instanceof TitleScreen` checks (the game,
  other mods, Fabric's test harness) keep working; `MinecraftMixin` swaps it in.
- `ScreenMixin` replaces the vanilla panorama with `TitleBackdrop` in pre-game menus.
- The backdrop textures (about 30 MB of video memory) are released when a world is joined.
- Vanilla widget sprites are overridden through `assets/minecraft/...` so every screen matches.

## Asset pipeline

`tools/assetgen` (Python), run with `build_all.py`:

| Module | Role |
|--------|------|
| `dl_noise` | periodic spectral noise, Worley cells, warps, relief shading, colour ramps, PNG output |
| `dl_materials` | tileable 32x32 materials (asphalt, concrete, bricks, plaster, tiles, boards, metals, ground, glass) plus LabPBR `_n`/`_s` maps |
| `dl_data` | blockstates, models, item model definitions, loot tables, merged tags, merged language file |
| `dl_nbt` | NBT writer and structure-template builder |
| `dl_glyphs` | stencil letterforms |
| `gen_*` | title, gui, items, blocks, containers, decor (furniture and fixtures), decals, structures, vanilla restyle, terrain (dead vegetation blocks, more vanilla restyles), world (biomes, features, biome tags), towns and city (building templates, town structure, road feature data, town loot, town text), sites (the school and wilderness sites, railway data, fuse), infected (rigs and variant list as Java, textures, spawn eggs, entity loot, tags, text), nests, audio, ambience (biome loops and one-shots, town sting), infected audio |

Block ids in `gen_blocks.py` / `gen_containers.py` / `gen_decals.py` must match the Java
registries. `gen_infected.py` is the one generator that also writes Java (`InfectedGeometry` and
`InfectedVariants`, marked GENERATED), so the rigs, UVs, looks and textures come from one
description. The server game tests fail the build when a registered block lacks its blockstate,
item model, name or loot table, or when any loot table fails to parse. Outputs are committed so
building the mod never needs Python.

Materials whose features would visibly repeat in a grid get several textures, listed in
`gen_blocks.VARIANT_SEEDS`; the blockstate lists one model per variant and the game picks one per
block position. Variant features stay clear of the tile edges so any mix of variants is seamless.
Container weathering covers an exact share of each surface per condition (quantile thresholds in
`gen_containers.coverage`), and interiors use a lower strength than shells.

## World generation (Milestone 3)

**Biomes.** The ten DEADLANDS biomes are data (`worldgen/biome`). `OverworldBiomeBuilderMixin`
intercepts vanilla's private `addSurfaceBiome` and, for each land biome, emits the same two
climate entries (surface and depth 1) with the DEADLANDS biome from `BiomeReplacement`. The climate
parameters are untouched, so the terrain shape and the non-land biomes (oceans, rivers, beaches,
peaks, caves) stay vanilla. The mapping is read when the overworld's biome source is built, so the
*DEADLANDS biomes* setting applies to worlds loaded after it changes.

**Feature order.** Minecraft sorts the features of all biomes of a dimension into one order and
refuses to load if two biomes disagree. Every DEADLANDS biome therefore uses vanilla plains'
underground features in vanilla's order, and its own features in the order of one global list
(`gen_world.VEGETATION`). The road feature is appended by Fabric to the end of `local_modifications`
in every overworld biome, after `surface_cover`, so roads paint over the ground cover and nothing
in that step can come after them.

**One view of the world.** Towns are structures (generated in `structures_starts`) and roads are a
feature (generated in `features`), but both must agree on where every town is. `WorldgenView`
(seed, chunk generator, random state, height range, registries) can be built from a structure's
generation context or from a feature's placement context, and every town and road decision is a
pure function of it, cached per world (`cacheKey` = seed and generator identity):

- `TownSites` reads the random-spread placement of `deadlands:towns` (34-chunk spacing) and accepts
  a candidate only if its centre is dry land in `#deadlands:has_structure/town` and the ground
  around it is neither flooded nor steep. Size and condition are hashes of the site.
- `RoadNetwork` links towns two regions apart at most when no third town is closer to both
  (relative neighbourhood graph). `RoadEdge` builds each road once: a cubic curve from gate to gate
  with a tapered meander, resampled every block, and a height profile sampled from the generator,
  smoothed, slope-limited and pinned to both gate heights.
- `RoadFeature` draws, per chunk, the columns closest to each nearby road: surface, shoulders,
  cuts, embankments, bridges (deck, guardrails, pillars) where the road is high above the ground
  or over water, tunnels where it is deep below the surface, and utility poles. Columns inside a
  town's gate radius are left to the town.

**Towns.** `TownStructure.findGenerationPoint` starts a town only where `TownSites` has one, with
the stub at the site centre (so vanilla's biome check agrees). `TownPlanner` lays out:

1. main streets from the centre to the gates (all four for towns; the needed ones for
   settlements), plus side streets in large towns, each with a terrain-following, slope-limited
   profile that is flat at crossings and gates;
2. gate pieces where the condition demands them;
3. buildings on 15x15 plots along every street, nearest the centre first, until the size's
   building cap: a category by distance from the centre, a random template that fits, rotated so
   its front faces the street, set at street level, skipped on steep, flooded or buried ground.

All pieces stay within about 120 blocks of the start chunk (the structure-reference reach is 8
chunks). Pieces save everything they need (street heights, condition, seed), so every chunk draws
the same town. `TownStreetPiece` also levels the verges, skipping other streets' areas.
`TownBuildingPiece` clears the hillside above the footprint, places the template, builds a
foundation, applies the condition's wear, and settles the containers (scavenged ones emptied and
left open, a `loot.lockedShare` share locked). Wear and loot rolls are hashes of block positions,
so the parts of a building in different chunks agree.

**Building templates** (`gen_towns.py`): at most 14x14, front door on the north edge (facing -Z),
ground floor at y = 0, and the whole box filled (explicit air, worn ground around), so terrain never
pokes through. Rotation `NONE` faces north, `CLOCKWISE_90` east, `CLOCKWISE_180` south,
`COUNTERCLOCKWISE_90` west; `TownPlanner.placeBuilding` turns a desired footprint into the
template origin for each rotation.

**Town identity.** `TownIdentity.of(seed, site)` derives the name, population, history, faction,
supplies and story. `TownTracker` finds the town structure start at each player's position every
second, records first discoveries in `DeadlandsWorldData`, and sends the town card with the
landmark taken from the town's actual building pieces.

**Cities and western towns.** `TownSize.CITY` adds a grid of cross streets (`TownPlanner`'s
`CITY_LINES`); where streets cross, a `TownCrossingPiece` draws the junction, zebra stripes and
traffic lights. Buildings come from a category chosen by distance from the centre, by size (the
"city" set downtown) and by biome (the "western" set in the dry wasteland).

**Road kinds, railways.** `RoadKind.between` makes links between two major towns highways, links
between two settlements dirt roads and the rest ordinary roads; `RoadFeature` draws each with its own width, markings and wear, adds
snow, mud or flooding from the biome and height, and scatters obstacles, pylons, poles and signs
from hashes of the position. `RailNetwork` picks a subset of the road links between towns larger
than settlements; `RailFeature` (added before the road feature, so roads are drawn over the track where they cross)
draws ballast, sleepers and rails with trestles, platforms and buffer stops.

**Sites outside towns.** The school, outposts, camps, military posts, bunkers, utility sites, the
power station, the treatment plant, farm sites and the research outpost are single-piece jigsaw
structures (`gen_sites.py` writes templates, pools, structures and structure sets), each set with
an exclusion zone around `deadlands:towns` so they never land in a town.

**Power.** `PowerGeneratorBlock` consumes a fuse and calls `PowerGrid.restore`, which marks every
town within 700 blocks as powered in `DeadlandsWorldData`. `LampHeadBlock` checks on random ticks
and lights at night when its town has power; the spawner and the town card read the same flag.

**Nests in buildings.** `TownBuildingPiece.infest` places a nest and biomass growth in some
buildings (about a third in overrun towns, hardly any in survivor towns): industrial nests in
garages and warehouses, military nests in military towns, residential ones elsewhere, and rarely
an advanced nest in an overrun town.

**Milestone 2 ruins.** The roadside shack and supply cache are still single-piece jigsaw structures
with their own structure sets, now allowed in every DEADLANDS biome (`#deadlands:is_deadlands`).

## Infected (Milestone 4)

**Kinds and entities.** `InfectedKind` holds everything that differs by kind as data: stats, size,
hearing, breaching power, sounds, rig and looks. All nine share `InfectedEntity`; a kind gets a
subclass only when it has behaviour of its own. `registerGoals` runs inside the `Mob` constructor,
so it looks the kind up from the entity type instead of reading fields. Look and physique are
synced data, chosen in `finalizeSpawn` (weighted by biome) and saved.

**Goals, by priority.** Float; the kind's own trick (slam, scream, pounce, swell...); breach; melee
attack with a wind-up; hunt (horde members walk toward the nearest survivor even without seeing
them); search the
last place a target was seen; investigate a noise or light; return to the nest or seek shelter;
migrate or follow the pack; roam; look around. Targeting uses the entity's own `detectionRange`
(the kind's follow range: farther at night, shorter in daylight and in rain).

**Breaching.** `BreachGoal` picks the block in the way (tag `deadlands:infected_breakable`, plus
`brute_breakable` for brutes); `BreachTracker` holds the damage per block for the whole server so
several infected add up, shows the cracks, and drops the damage of a block left alone for ten
seconds.
`zombies.breakBarricades` turns it off.

**Noise.** `ServerLevelMixin` forwards every game event to `NoiseSystem`, which maps it to a
`Loudness` (or ignores it), rate-limits footsteps per player, and calls `emit`. `emit` tells every
infected within the radius times its hearing (`hearNoise`: investigate if nothing more important
is going on) and every nest in range. Systems that make noise without a game event call
`NoiseSystem.emit` directly.

**Population and AI budget.** `InfectedManager` learns of every infected from Fabric's entity
load and unload events. It answers "is there room here?" for the spawner, nests, hordes and
migrations (local cap within 128 blocks of a survivor, global cap, plus a horde allowance), and
once a second sets each infected's AI interval. `Mob.serverAiStep` is final, so throttling works
through `isImmobile()` on skipped ticks while `travel()` replays the last movement input: distant
infected keep walking, they just think less often.

**Nests.** A `NestBlockEntity` ticks once a second: population regrows, alertness decays.
`NestRegistry` lets the noise system find loaded nests without scanning chunks. Releasing spawns
residents (`NestKind.pickResident`) next to the nest with the nest as their home; `absorb` takes
them back; `preRemoveSideEffects` (fired on break, not on unload) lets the rest out.

**Hordes.** `HordeDirector` reads `EncounterSchedule` (so a boss day never has a horde), saves a
`HordeRecord` when one starts, and spawns waves through `SpawnFinder.towards` from the horde's
bearing. Members carry the horde's day; deaths are tallied through the shared death hook.
Restarting the server mid-horde resumes it from the record.

**Migrations.** `MigrationSystem` moves `MigrationRecord`s as plain data. Near a survivor it
spawns the group's members (registered by UUID before they enter the world, so the load hook
knows them) and they walk on with `MigrateGoal`; when nobody has been near for 30 seconds it
removes them and writes the survivors back into the record.

**Client.** `InfectedRenderer` picks the rig from the kind, the texture from the variant, and
scales by kind, build and height. `InfectedModel` builds a procedural pose per kind and blends
the keyframed actions on top (`KeyframeAnimation.apply(millis, weight)`), fading the procedural
arm pose out under an action. The rigs and the variant list are generated by `gen_infected.py`
together with the textures, so they cannot drift apart. Idle voices never come from the server:
`InfectedEntity.playAmbientSound` calls `ClientBridge`, and `InfectedVoices` decides per listener
what is heard.

## Survival (Milestone 5, first part)

`SurvivalData` (a mutable object with a codec) is a Fabric data attachment on the player:
persistent, not copied on death. `SurvivalSystem` owns all the rules: stamina every tick, the rest
once a second, injuries from Fabric's after-damage event, penalties as attribute modifiers (no
potion effects, so nothing shows in the vanilla effect list except nausea). `SurvivalRules` holds
the rates and the difficulty presets; `Temperature` computes the ambient value. The server sends
`deadlands:survival` to the owner only when the HUD would look different; the client keeps it in
`LocalSurvival`, which lives in common code because `PlayerMixin` (the sprint check, which runs
on both sides) reads it. Items are `SurvivalItem`s: vanilla's `consumable` component does the
eating or drinking, then a small lambda changes the body. New ways to hurt are data-driven damage
types. Loot generators still name vanilla stand-ins; `dl_data.survival_loot` swaps them as every
loot table is written. Later systems should change the body through `SurvivalAttachments.get` and
read `Condition`s rather than add state of their own.

**Skills** are a second attachment (`Skills.DATA`, copied on death). Systems call
`Skills.award(player, skill, xp)` where the survivor does the thing, and read the bonus methods on
`Skills` where it matters; the numbers all live there. **Carrying weight** is recomputed from the
inventory once a second by `CarryWeight` and stored (unsaved) on the body, so it rides the same
payload and penalties. **Weapons**: melee weapons are plain sword-like items; `PistolItem` is a
server-side hitscan that takes a round from the inventory and calls `NoiseSystem.emit` with
`Loudness.GUNSHOT`. New firearms should follow it.

## Vehicles (Milestone 6)

`VehicleKind` is the table (as `InfectedKind` is for the infected); every kind has its own entity
type so sizes can differ, all of them `VehicleEntity` (an `Entity`, not a mob). Condition lives in
synced entity data and is saved. Movement copies Minecraft's boats: `isLocalInstanceAuthoritative()`
is true on the driver's client (and on the server when nobody drives), and only that side runs
`drive()` and `move()`; it reads the driver's own movement input (`xxa`, `zza`). The server cannot
trust or see that simulation, so it charges fuel, makes noise and runs things over from the
distance the vehicle actually moved each tick. `VehicleParts` holds the restoration rules;
`FuelPumpBlock` and its block entity hold what a pump has left. Rigs and textures come from
`gen_vehicles.py` as generated Java plus PNGs, like the infected. World generation adds vehicles as
entities from `RoadFeature.placeVehicles`, each decided by a hash of its spot and placed only by the
chunk it stands in.

## Survivors (Milestone 7)

One entity type, `NpcEntity`, covers every survivor; role, faction, look and (for the four
recurring characters) identity are synced data, and saved. The routine is Minecraft's home
restriction: `applyRoutine` sets the home radius by time of day and role, and the ordinary stroll
goals respect it. Conversations are decided on the server: `NpcDialogue` sends a `dialogue` payload
(title, subtitle and text as components, plus reply ids), the client's `DialogueScreen` shows it
and sends back a `dialogue_choice`. Trading implements Minecraft's `Merchant`, so the vanilla
trading menu and screen do the work; `NpcTrades` holds the lists and `Reputation.Standing.price()`
is applied as each offer's special price when the screen opens. `Reputation` is a third player
attachment (copied on death); other systems call `Reputation.change`. Settlements are populated by
`SpawnMarkerBlock`: an invisible block entity that, on its first tick, removes itself and spawns a
survivor or a vehicle. Town buildings place one in `TownBuildingPiece.populate`; site templates
carry them directly. `NpcWorldData` is its own saved-data file for which characters are placed.

## The story (Milestone 8)

`story/StoryState` is the world's story (saved data): flags, everything anyone has read, deliveries
to Harley, repaired towers, located places, defeated bosses, fitted helicopter parts, the cure day and
the ending. Quests are not stored as quests: `story/Quests` works them out from those facts each time
the journal is sent (`progression/Tasks`, only when the text changes) and handles what the characters
do when asked. `story/StoryDirector` runs every two seconds (story places entered) and every ten
(places located one at a time with `findNearestMapStructure`, characters given fallback places and
made to appear near survivors, the cure finishing). Lore is generated Java (`LoreEntries`) plus
English text; `story/StoryLoot` adds documents, trail notes and quest items when a DEADLANDS container
is first opened (`LootContainerBlockEntity.unpackLootTable`). The player's archive is an attachment
synced by `ArchivePayload`. The radio asks the server for a station's text (`RadioTunePayload` /
`RadioPayload`); the client garbles it by signal strength. Keycard readers are buttons that need a
card; security doors are iron-type doors. Bosses are one entity class with five types; their attacks
are a small state machine whose current attack is synced so the client animates wind-up and blow;
`horde/BossDirector` keeps the night's encounter in its own saved data. The helicopter keeps its
extraction timer in synced data; when it ends, `story/Endings` records the ending and every client
shows `EndingScreen`.

## Polish (Milestone 9)

**Weather** is a server decision (`world/weather/WeatherDirector`, saved data `deadlands_weather`)
sent to clients as `WeatherPayload`; everything visible is client-side: `AtmosphericFogMixin` moves
the fog distances and tints its colour, `ClientWeather` spawns dust, grit and ash near the camera.
**World events** (`event/WorldEvents`, saved data `deadlands_events`) roll each day at 3000 ticks;
the supply drop is its own entity that becomes a block when it lands. **Location** is worked out on
the server every two seconds from the structures the survivor stands in (`audio/LocationTracker`)
and sent only when it changes (`LocationPayload`); `client/sound/AmbienceDirector` picks sounds from
it (rewritten in the atmosphere overhaul, below). **Cutscenes** are named on the server (`cutscene/Cutscenes`, which records who has seen what as
a player attachment) and played on the client: `CutscenePlayer` holds the shots, `CameraMixin` moves
the camera at the end of `Camera.setup`, `CutsceneScreen` draws bars and subtitles and any key or
click skips. The boss bar replaces `VanillaHudElements.BOSS_BAR` and reads vanilla's events through an
accessor mixin.

**The four characters.** `NpcEntity.considersEntityAsAlly` makes them allies, so vanilla targeting
never picks one of them; `hurtServer` refuses damage between them; every half second each checks
whether another within 24 blocks is fighting and takes on the same enemy (`standByAllies`, which also
lets those who are not fighters fight while they do). `NpcSpawns` rolls the encounters
(every 600 ticks, for survivors who moved 48 blocks; `encounterChance` starts at
`CHARACTER_ENCOUNTER_CHANCE` and rises to certain, counted by `NpcWorldData.countExploring`). `NpcWorldData`
keeps who is placed, which UUID each is, who was lost and the exploring count; each character entity checks itself
against it on its first tick. `NpcRenderer` reads slim arms from the skin and swaps to the slim
player model in `submit`.

**Textures.** `tools/assetgen/dl_realism.py` runs after every other generator, backs up what it
changes to `texture-backup/` and keeps a hash manifest so it never applies twice.

## Finishing Milestones 6 and 7

**Vehicles.** Headlights are a flag on the vehicle plus an invisible `minecraft:light` block the
server moves a few blocks ahead (`VehicleEntity.tickLights`; removed when the lights go off or the
vehicle is removed, and remembered across saves so a stray one is cleaned up). Horn and lights are
asked for by the driver's client (`VehicleControlPayload`). Towing is server-side: the towed vehicle
keeps the puller's UUID and steps toward it each tick when the rope is taut. The renderer draws the
model once with a translucent render type (see-through glass) and up to three more times with the
same geometry: a cutout damage or overgrowth overlay and an emissive lights overlay. Doors are model
parts named `door_l`/`door_r`, swung by a client-only timer that starts when the passenger count
changes. `progression/Tasks` keeps the restoration task as a player attachment and sends the journal's
TASKS page (`JournalPayload`) only when its text changes.

**Settlements.** `npc/Settlements` is saved data keyed by town key. Residents register when their
marker resolves; the daily simulation runs from `DayChangeCallback`, with a random seeded by world
seed, town and day so it is repeatable. The world catches up lazily: a survivor loaded from disk asks
`claimLoss` once, and newcomers are spawned by a 10-second check when a survivor is within 128 blocks.
`npc/Access` checks each survivor once a second against the settlement they stand in; trespass is a
map the fighters' target predicate reads. Theft is reported from `LootContainerBlockEntity.startOpen`.

## Atmosphere overhaul

**Entity events.** Every byte DEADLANDS sends with `Level.broadcastEntityEvent` is declared in
`entity/DLEntityEvents` and lies above vanilla's range (100 and up). Vanilla's
`ClientPacketListener.handleEntityEvent` handles some ids itself before the entity sees them and
casts the entity on the way (21 Guardian, 35 totem, 63 Sniffer): the Stalker's pounce used to be 63,
which crashed the client. `DeadlandsInfectedGameTest.entityEventsStayClearOfVanilla` reads vanilla's
ids by reflection, so a Minecraft update that adds ids is caught. New events go in that class.

**Music.** `client/sound/MusicDirector` owns in-game music when *Audio > DEADLANDS soundtrack in the
world* is on; `client/mixin/MusicManagerMixin` cancels `MusicManager.tick` while it does (the menus
keep vanilla's manager and the title theme). Every 10 ticks it scores the threat (hunting infected by
kind and distance, bosses within 64 blocks, being hurt) and picks a mood: HORDE (the horde HUD is up),
DANGER (score 2.5 or more for 30 ticks; it lets go after 240 ticks below 1.0), SAFE (the server's
safe-zone flag in `LocationPayload`, or two friendly survivors within 20 blocks), NIGHT (13000 to
23000 or deep underground), else EXPLORE. Each mood has a pool of sound events (`DLSounds.MUSIC_*`, one
per track, streamed). Tense moods fade in fast and loop; quiet ones play one track and then leave a
silence sized by vanilla's *Music Frequency*. Tracks are `AbstractTickableSoundInstance`s in the MUSIC
source with no attenuation, so the Music slider and *Soundtrack volume* both apply; the newest
track fades in while the old one fades out, and nothing else overlaps. The server side is one flag:
`audio/LocationTracker` sets `safe` when the survivor stands in a town whose faction tolerates them.

**Soundscape.** `client/sound/AmbienceDirector` surveys the camera every 10 ticks (indoors, under a
roof, a metal roof, on asphalt outside town) and combines it with the server's location to choose a
profile (open, forest, suburb, city, highway, interior, hospital, school, industrial, farm, campsite,
bunker, underground). A profile is a list of `Layer`s: a sound, its spacing, distances, height,
loudness and conditions (day, night, dry, wet, storm, outdoors). Each layer keeps its own clock; a
one-shot starts only if no other started in the last 2 seconds, fewer than three are playing and it is
not the sound that played last. One-shots use `Attenuation.NONE` at a world position, with the
director setting the volume from distance itself (vanilla's linear roll-off silenced anything past
16 blocks), so they keep their direction. Loops (wind beds, rain on the roof or on metal, room tone,
hum, transformer) fade in and out with the conditions. `MenuAmbience` plays its bed while no world is
loaded. The DEADLANDS biomes' own one-shot additions (data) were made five times rarer to leave room
for it.

**Infected voices.** The server syncs a *hunting* flag (`InfectedEntity.FLAG_HUNTING`, set every 10
ticks from the target). On the client `client/sound/InfectedVoices` turns vanilla's ambient timer
into idle groans with one shared budget that thins out in a crowd, plays an alert cry when the flag
rises (not for the Stalker, at most once per 20 seconds per infected), chase voices at a per-kind
pace while it is set, and breath within 4.5 blocks. All of them share a limit of six playing at once
(`MAX_VOICES`, checked against the sound engine). Distance, walls (a block raycast) and the
*infected voice effects* setting pick the plain, muffled or distant variant.

**Menu backdrop.** `client/ui/TitleBackdrop` draws stacked 1920x1080 layers (sky, far and middle
skyline, light shafts, the street, weeds, fog banks, vignette, grain), each shifted by the mouse by
its own amount for depth, the fog offsets moved by time, the weeds sheared by a `Matrix3x2f` for wind
and, every 38 seconds, a few crow sprites flying across the sky. The art is painted by `gen_menu.py`.

**Infected looks.** `InfectedRenderer` picks a look as before and, from the infected's UUID, one of
six wear overlays per rig (or none) and a small skin and cloth tint (`getModelTint`).
`InfectedWearLayer` draws the overlay as a second translucent pass of the same model.

**Block variants.** No code: `gen_variants.py` writes weighted blockstate variant lists (the original
texture weight 3, three variants weight 1, random quarter turns where the texture allows). The game
picks one per block position, the same way it varies grass. Vanilla blockstates are overridden
for the ten vanilla blocks it covers. Connected textures (CTM) would need a renderer mod and are not
used.

**Vanilla biomes.** `world/DLWorldgen` gives the vanilla biomes DEADLANDS keeps (oceans, rivers,
beaches, peaks) DEADLANDS water, fog and sky colours with a Fabric biome modification, when
*World > DEADLANDS biomes* is on.

## Testing

- **Server game tests** (56, in `DeadlandsServerGameTest`, `DeadlandsContentGameTest`,
  `DeadlandsWorldGameTest`, `DeadlandsInfectedGameTest`, `DeadlandsSurvivalGameTest`,
  `DeadlandsVehicleGameTest`, `DeadlandsNpcGameTest`, `DeadlandsStoryGameTest`) run in every
  `gradlew build` on a real dedicated-server instance: registration and asset completeness, config,
  the encounter schedule, saved data, blocks and loot, locks, structures and towns, infected and
  nests, entity-event ids, survival, vehicles, survivors and settlements, the story.
- **Client game test** (`DeadlandsClientGameTest` with `MilestoneTwoScenes`, `StalkerScenes`,
  `AudioScenes`, `LookScenes` and `PerformanceScenes`; `gradlew runClientGameTest`): real client,
  real input, screenshots; the Milestone 2 scenes, save and reload, natural structures in a normal
  world, the Stalker regression, the music director, soundscape and voices, review screenshots of the
  infected, weapons, blocks and embers, a crowd of forty hunting infected (frame rate, live heap, at
  most six voices at once), a resource reload that must leave the same view looking the same, and a
  dedicated-server join.

See [TESTING.md](TESTING.md).

## Performance rules

- No per-tick work that scales with loaded chunks unless budgeted (`Performance` config).
- Spawning always has caps, cooldowns and distance limits; never unbounded loops. Anything that
  adds infected asks `InfectedManager` for room first.
- Entity AI is budgeted: the nearest `performance.maxActiveAi` infected think every tick, the rest
  on an interval. New mobs should join that budget, not add their own.
- Systems that must find things (nests, infected) keep a registry fed by load and unload events
  instead of scanning chunks or entity lists.
- Far-away settlements use a simplified simulation stepped on a timer; full AI only near players.
- Avoid entity ticking for decorative objects; prefer block entities that do not tick, or
  client-only effects.
