# DEADLANDS: Asset Status

- **Last updated:** 2026-11-10 (atmosphere, audio, texture and stability overhaul; the totals below
  are from Milestone 4 and do not include Milestones 5 to 9 or the overhaul)
- **Totals shipped:** 792 DEADLANDS textures (including 170 LabPBR maps), 78 replaced vanilla
  textures, 24 nine-slice metadata files, 141 sounds, 157 block models, 37 item models,
  101 item model definitions, 85 blockstates, 12 code-defined entity models (10 container parts,
  2 infected rigs), 140 loot tables, 115 structure templates, 107 worldgen files (10 biomes,
  13 structures) and 16 tag files
- **Origin:** every texture, model and sound below is original and produced by a seeded generator
  in [`tools/assetgen`](tools/assetgen) (`python tools/assetgen/build_all.py` rebuilds them all).
  Nothing is copied from Minecraft or third parties.

Status key: **v1** = generated, reviewed in game, good enough to ship; expected to get art passes.
**v0** = generated and reviewed from rendered previews of the files, **not yet seen in game**
(everything from Milestones 3 and 4, which have not been run). **Vanilla** = still Minecraft's asset.
**Planned** = not created yet. Since the overhaul the client test takes screenshots in the real
renderer, so assets marked **v1 (screenshot)** were reviewed there; sounds marked **v0 (measured)**
were checked by their levels, length, loops and spectrograms, **not listened to**.

## Atmosphere overhaul assets

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Soundtrack | 13 pieces in `sounds/music/` (the title theme replaced, 12 new), 27 minutes, 17 MB, streamed | v0 (measured) | `gen_music.py` on `dl_music.py`; stereo; danger tracks loop seamlessly |
| Soundscape | 61 files in `sounds/ambient/scape/`: crows and flocks, far birds, leaves, branches, creaking signs, rattling sheet metal, distant thunder, dogs and a howl, electrical buzz, wind through broken windows, a building settling, unexplained far-off noises; 7 stereo loops (wind in trees and streets, rain on a roof and on metal, room tone, transformer, the menu bed) | v0 (measured) | `gen_soundscape.py`; one-shots mono |
| Infected voices | 220 files in `sounds/infected/voice/`: idle, alert, chase, hurt and death per kind, breath, distant and muffled calls; 67 older voice files nothing referred to were removed | v0 (measured) | `gen_infected_voices.py` on `dl_voice.py` |
| Main-menu backdrop | 11 layers in `textures/gui/title/` (sky, three skylines, weeds, light shafts, fog, crow frames, vignette, grain, logo) | v1 (screenshot) | `gen_menu.py` (`gen_title.py` still draws the logo); built from a written description, the reference image was not available |
| Infected | 66 looks (8 new: hospital patient, industrial coveralls, survivor, firefighter, two of each), new skin and faces on all, 12 wear overlays (6 per rig) | v1 (screenshot) | `gen_infected.py` |
| Weapons | 13 DEADLANDS weapon icons repainted (6 of them new weapons) and the vanilla iron, diamond and netherite sword and axe; handheld models | v1 (screenshot) | `gen_weapons.py` |
| Block variants | 87 textures (29 blocks x 3) with LabPBR maps in `textures/block/variant/`, weighted blockstates (10 of the blocks are vanilla) | v1 (screenshot) | `gen_variants.py` |
| New blocks | dirty concrete, broken concrete, potholed asphalt, rotting planks, faded planks, corroded metal, industrial panel, dirty window, cracked glass, dusty glass | v1 (screenshot) | `gen_blocks.overhaul_textures` |
| Ember particle | 4 frames repainted with a hot core | v1 (screenshot) | drawn full-bright in game |

## Milestone 9 assets (v0: generated, not yet seen in game)

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Particles | ash, dust, blood, spore, ember: 8x8, 4 frames each, with their particle definitions | v0 | `gen_polish.py` |
| Location ambience | 22 one-shots and two loops (wind, electrical hum) | v0 | synthesised; `gen_polish.py` |
| Supply drop | crate-and-parachute rig as Java (`SupplyDropGeometry.java`), its texture, loot table `events/supply_drop` | v0 | `gen_polish.py` |
| Text | weather, world events, cutscene subtitles, commands, the emergency radio's event line | v0 | `gen_polish.py` |
| Character skins | the four supplied skins (`character-skins/`), copied pixel for pixel to `deadlands/textures/entity/survivor/character/` | final | slim arms; replaceable in game by `config/deadlands/skins/<id>.png` |
| Texture finish | 544 block and item textures passed through `dl_realism.py`; originals in `texture-backup/` | v1 | 87 left as painted after review (decals, glass, flat panels, radio, wrench); entity skins not touched |

## Milestone 8 assets (v0: generated, not yet seen in game)

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Lore | 134 entries' text in `en_us.json`; `story/LoreEntries.java` | v0 | written in `tools/assetgen/lore_data.py`; `gen_story.py` |
| Story text | quests, characters' answers, radio, opening, endings, messages, chronicle | v0 | `tools/assetgen/story_texts.py` |
| Document looks | 5 item textures (document, journal, newspaper, tape, photo) and a `select` item model | v0 | `story_assets.py` |
| Story items | 20 icons: radio, radio parts, field case, the six cure items, evacuation orders, five boss trophies, four helicopter parts | v0 | |
| Keycard reader | 6 textures and models (3 levels, lit and unlit), 72-variant blockstate | v0 | |
| Security door | top and bottom textures, 8 models, blockstate, item | v0 | uses Minecraft's door model shapes |
| Radio transmitter | 2 front textures, 2 models, blockstate | v0 | |
| Helicopter | rig as Java (`HelicopterGeometry.java`), 512x512 texture | v0 | |
| Bosses | 5 rigs as Java (`BossGeometry.java`), 5 textures, 5 spawn eggs, 20 sounds and a sting | v0 | `gen_bosses.py`; review sheet `_cache/bosses_textures.png` |
| Story sites | `research_facility`, `site_calder`, `expedition_camp`, evacuation camps as `evacuation_camp` | v0 | `gen_sites.py`; 49 templates in all |
| Sounds | radio static loop, tuning sweep, helicopter rotor | v0 | synthesised |
| Journal | ARCHIVE bookmark icon | v0 | |

## Milestone 7 assets (v0: generated, not yet seen in game)

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Survivor skins | 30 x 64x64 in `deadlands/textures/entity/survivor/` (10 roles x 3) | v0 | Minecraft's player layout, base layer only; role clothing (white coat and cross, overalls and grime, vest, apron, bandana, hat) |
| Character skins | 4 placeholders in `deadlands/textures/entity/survivor/character/` | replaced in M9 | the supplied skins now ship (see Milestone 9) |
| Spawn marker | blockstate and an empty model | v0 | invisible technical block |
| Dialogue text | about 110 lines in `en_us.json` | v0 | greetings by mood and temperament, rumours, services, the four characters' histories; `gen_npcs.py` |
| Vehicle cargo table | `loot_table/vehicles/cargo.json` | v0 | |
| Survivor voices | 15 files in `sounds/npc/` (greet, idle, alert, hurt, death x 3) | v0 | wordless, synthesised from vowel formants; `gen_npcs.py` |
| Journal TASKS icon | `textures/gui/sprites/icon/tasks.png` | v0 | a clipboard, drawn like the other bookmarks |
| Camp templates | `structure/site/survivor_camp_a/b.nbt`, `research_camp.nbt` | v0 | `gen_sites.py` |
| Vehicle overlays | 48 in `textures/entity/vehicle/` (12 kinds x damage_1, damage_2, overgrown, lights) | v0 | `gen_vehicles.py`; review sheet `_cache/vehicle_overlays.png` |
| Tow rope | item texture, model, recipe | v0 | |
| Horn | `sounds/vehicle/horn.ogg` | v0 | two-tone, synthesised |

## Milestone 6 assets (v0: generated, not yet seen in game)

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Vehicle rigs | 12 in `client/render/vehicle/VehicleGeometry.java` (generated) | v0 | boxes: lower body, cabin, bed or load box, light bar, rack, ladder, wheels on axles; the motorcycle has its own frame |
| Vehicle textures | 48 x 512x512 in `deadlands/textures/entity/vehicle/` | v0 | 12 kinds in faded, rusted, clean and burned-out finishes; windows, lights, grilles, stripes, livery |
| Part icons | fuel can, wrench, engine parts, tire, car battery, body panel | v0 | |
| Sounds | `sounds/vehicle/engine.ogg`, `impact.ogg`, `repair.ogg` | v0 | the engine is a seamless one-and-a-half second loop |
| Review sheet | `tools/assetgen/_cache/vehicles_preview.png` | review only | side views, faded and burned |

## Milestone 5 assets (v0: generated, not yet seen in game)

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Food, drink and medicine icons | 23 x 32x32 in `deadlands/textures/item/` | v0 | cans, bottles, packets, rolls, pill bottles, a syringe, a splint, a first aid kit; `gen_survival.py` |
| Body meter and condition pictograms | 19 x 9x9 in `deadlands/textures/gui/sprites/survival/` | v0 | thirst, stamina, temperature, fatigue and 15 conditions |
| Damage types | 5 in `data/deadlands/damage_type/` (dehydration, hypothermia, heatstroke, bleeding, infection) | v0 | they ignore armour |
| Recipes | 14 in `data/deadlands/recipe/` | v0 | purifying water (tablet, campfire, furnace), rags, bandages, splint, four melee weapons, 9 mm rounds, backpack, workbench |
| Weapon icons | 10 x 32x32: crowbar, baseball bat, machete, fire axe, pistol, shotgun, hunting rifle and their three kinds of round | v0 | flat item models (no held 3D models yet) |
| Weapon sounds | `sounds/weapon/pistol_shot.ogg`, `shotgun_shot.ogg`, `rifle_shot.ogg`, `dry_fire.ogg`, `reload.ogg` | v0 | synthesised; never heard in game |
| Load and vitals pictograms | `survival/load`, `encumbered`, `health`, `armour`, `hunger` | v0 | |
| Backpack icons | `item/backpack`, `item/military_backpack` | v0 | |
| Workbench | `block/decor/workbench_top` and a model | v0 | scarred top with a vice |
| Fuel pump | `block/decor/fuel_pump_front`, `fuel_pump_body` and a model | v0 | decoration until Milestone 6 |
| New structures | `site/gas_station_a`, `gas_station_b`, `rest_stop`, `roadside_stand`, `farmstead_a`, `farmstead_b`; `town/library`, `town/factory` | v0 | 123 templates in all |
| Pose review sheet | `tools/assetgen/_cache/infected_poses.png` | review only | drawn by `preview_poses.py` from the rigs |

## Milestone 4 assets (v0: generated, not yet seen in game)

### Infected

Generated by `gen_infected.py`, which writes the textures, the two rigs as Java
(`client/render/infected/InfectedGeometry.java`) and the variant list
(`entity/InfectedVariants.java`) from one description, so UVs and textures always agree. A review
sheet of every look is written to `tools/assetgen/_cache/infected_preview.png`.

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Humanoid rig | `InfectedGeometry.humanoid()` (code), 26 parts, 128x64 UV space | v0 | torso, chest, head and jaw, two-part arms and legs; optional helmet, cap and brim, gas mask, vest, belt, backpack, claws, bloated belly, growths, torn cloth front and back |
| Brute rig | `InfectedGeometry.brute()` (code), 14 parts | v0 | huge hunched chest, long heavy arms, optional helmet; 38 px tall before scaling |
| Infected textures | 58 x 256x128 in `deadlands/textures/entity/infected/<kind>/` | v0 | walker 24, runner 8, brute 5, screamer 4, military 8, crawler 6, bloater 1, stalker 1, charred 1; grey-green decayed skin in several tones, clothing per outfit with tears showing skin, wounds, blood, dirt; parts a variant hides are left blank |
| Glow layers | `charred/burned_glow.png`, `stalker/pale_glow.png` | v0 | embers in the burns; the stalker's eyes |
| Animations | `client/render/infected/InfectedModel`, `InfectedAnimations` (code) | v0 | procedural shamble, sprint, lumber, crawl, stalk, climb and waddle; keyframed attack, slam, scream, pounce, breach (loop), emerge, swell (loop) |
| Spawn eggs | 9 x 32x32 in `deadlands/textures/item/` | v0 | painted per kind |
| Entity loot tables | 9 per kind and 15 per outfit in `data/deadlands/loot_table/entities/` | v0 (vanilla items as stand-ins until M5) |

### Nests

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Nest cores (5) | `block/nest/<kind>.png` with LabPBR maps; box models | v0 | flesh mound with sacs: cloth caught in it (residential), rust and oil (industrial), camouflage netting (military), roots and soil (underground), dark vein-lit hive (advanced) |
| Biomass, biomass growth | `block/nest/biomass.png`, `biomass_growth.png` | v0 | block and floor carpet |
| Biomass veins | `block/decal/biomass_veins.png` | v0 | wall and floor decal |
| Infected tissue sample | `item/infected_tissue.png` | v0 | sealed specimen bag |
| Drop tables | 8 (5 nests, biomass, growth, plus tissue chances) | v0 | |

### Sounds (Milestone 4)

94 synthesised OGG files for 46 new events (`gen_infected_audio.py`; `sounds/infected`, `sounds/nest`,
`sounds/event`). Voices come from a source-filter model (a jittery glottal sawtooth and breath noise through formant
filters, with growl and distortion set per kind); distant and muffled versions are the same groans
low-passed with reverb. Every file was checked for silent or broken output after generation; none
has been heard in game.

| Event(s) | Files | Notes |
|----------|-------|-------|
| `infected.<kind>.ambient` (9) | several each | groans pitched and shaped per kind (deep for the brute, high and thin for the screamer and runner); the stalker only breathes |
| `infected.<kind>.hurt` / `death` | several each | per kind or shared by similar kinds |
| `infected.runner.alert`, `brute.roar`, `brute.slam`, `brute.step`, `screamer.scream`, `bloater.burst`, `stalker.pounce` | several each | the kinds' actions |
| `infected.distant`, `infected.muffled` | several each | far-off moans; low-passed voices for behind walls |
| `infected.breath`, `scratch`, `bash_wood`, `bash_metal`, `step`, `attack`, `emerge` | several each | close-range detail and breaching |
| `nest.pulse`, `release`, `destroyed`, `absorb` | several each | wet pulses, tearing, bursting |
| `event.horde_start`, `horde_broken`, `horde_dawn`, `horde_distant` | one or more each | stings for the horde card; a far crowd of voices for horde nights |

## Milestone 3 second-half assets (v0: generated, not yet seen in game)

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Household containers | kitchen cupboard, fridge, dresser, safe, school desk: body faces in four conditions, moving parts, models, item icons, normal and rare loot tables | v0 | wooden kinds weather with water damage and rot instead of rust |
| Decor (8) | traffic light, road signs (warning, speed, route), transformer, power generator, bookshelf, whiteboard; lit lamp head and running generator state models | v0 | `gen_decor.py` |
| Decals (2) | evacuation notice, scattered papers | v0 | `gen_decals.py` |
| Fuse | `item/fuse.png` | v0 | |
| Rails | vanilla `rail` and `rail_corner` replaced: rusted track on rotten sleepers | v0 | `gen_sites.py` |
| City buildings (15) | `apartment_block` (3), `office_block` (3), `department_store`, `motel` (2), `bank`, `courthouse`, `fire_station` (2), `emergency_room` (2) | v0 | multi-storey with stairs; `gen_city.py` |
| Western buildings (8) | `saloon` (2), `general_store` (2), `wooden_house` (3), `sheriff_office` | v0 | `gen_city.py` |
| Town building variants (25) | seeded variants of the first 28 buildings | v0 | `gen_towns.py` |
| Site templates (37) | school (3), ranger stations (3), hunting cabins (3), campsites (3), evacuation camps (2), checkpoints (3), watchtowers (3), bunkers (2), substations (3), water towers (3), radio towers (2), power station, treatment plant, silos (2), windmills (2), research outpost | v0 | `gen_sites.py` |
| Worldgen | 10 site structures with structure sets (kept away from towns), the `rail_network` feature | v0 | |
| Loot tables | fire station, school (locker, office, key, hidden), hunting, campsite, utility, research lab | v0 | vanilla stand-ins until M5 |

## Milestone 3 first-half assets (v0: generated, not yet seen in game)

### Terrain blocks

32x32 with LabPBR maps, generator `gen_terrain.py`.

| Asset | Texture(s) under `deadlands/textures/block/` | Status | Notes |
|-------|----------------------------------------------|--------|-------|
| Dead log, charred log | `dead_log`, `dead_log_top`, `charred_log`, `charred_log_top` | v0 | bleached split bark with lichen; fire-blackened alligator bark with ember stains |
| Dead leaves | `dead_leaves` | v0 | sparse curled brown leaves with cut-out holes (cutout layer) |
| Cracked earth | `cracked_earth` | v0 | dried mud plates |

### Furniture and fixtures

32 textures (32x32, with LabPBR maps) in `deadlands/textures/block/decor/`; 16 multi-part block
models; generator `gen_decor.py`. Pictures on faces (oven door, cabinet doors, drawers, lamp lens,
cooktop, basin) use explicit UVs; materials use automatic UVs so grain runs across parts.

| Block | Model parts | Status |
|-------|-------------|--------|
| Utility pole, crossarm | creosote pole with end grain; crossarm with two green glass insulators | v0 |
| Street lamp post, street lamp head | galvanised post; arm and housing with a smashed lens around a dead bulb | v0 |
| Guardrail | galvanised W-beam with splice bolts and rust runs on a steel post | v0 |
| Concrete barrier | three-step Jersey profile, faded reflective band, spray tag, road dirt | v0 |
| Table, chair, desk | worn varnished wood, scratches, water rings; desk with three drawers (one open) | v0 |
| Kitchen counter, sink | painted cabinet doors with pulls over a toe-kick, laminate top; steel basin and faucet | v0 |
| Stove | grimy enamel, oven door with smoked window, coil cooktop, backsplash with knobs and a clock | v0 |
| Store shelf | pegboard back, price-label strips, a cereal box and two cans left behind | v0 |
| Old bed | dark frame and headboard, stained ticking mattress, blanket bunched at the foot, pillow | v0 |
| Couch | faded olive fabric with tears, two sagging seat cushions, stubby legs | v0 |
| Toilet | crazed, stained porcelain; seat ring around murky water; flush handle | v0 |

### Town buildings

28 structure templates in `data/deadlands/structure/town/`, generator `gen_towns.py` (15
archetypes with seeded variation). Each is at most 14x14, front door to the north, ground floor at
y = 0, and fills its whole box (explicit air, worn ground around the building).

| Category | Templates | Status |
|----------|-----------|--------|
| Residential (10) | `cottage_siding`, `cottage_brick`, `cottage_plaster`, `suburban_siding`, `suburban_plaster`, `suburban_brick`, `two_story_siding`, `two_story_brick`, `trailer_rusted`, `trailer_corrugated` | v0 |
| Commercial (5) | `corner_store`, `grocery`, `pharmacy`, `hardware_store`, `diner` | v0 |
| Civic (3) | `police_station`, `town_hall`, `church` | v0 |
| Medical (2) | `clinic`, `clinic_small` | v0 |
| Industrial (3) | `auto_garage`, `auto_garage_large`, `warehouse` | v0 |
| Rural (5) | `barn`, `farmhouse_siding`, `farmhouse_plaster`, `shed_small`, `shed_large` | v0 |

### Replaced vanilla textures (Milestone 3)

| Asset | Files (under `minecraft/textures/block/`) | Status | Generator |
|-------|-------------------------------------------|--------|-----------|
| Plants and ground | 25: short and tall dry grass, dead bush, bush, leaf litter, vine, lily pad, sugar cane (tinted ones stay greyscale), moss block, mud, snow, ice, packed ice, podzol top and side, rooted dirt, snowy grass side, dirt path top and side, farmland (dry and moist), hay block side and top, red sand, mossy cobblestone | v0 | `gen_terrain.py` |
| Town blocks | 2: iron bars (rusted, cut-out), cobweb | v0 | `gen_towns.py` |

### Sounds (Milestone 3)

| Event | File(s) | Notes |
|-------|---------|-------|
| `deadlands:ambient.<biome>.loop` (9) | `sounds/ambient/<name>_loop.ogg` | 32 s stereo, seamless; streamed; dead forest, pine, suburbs, fields, dry wind, marsh, frozen, industrial, burned |
| `deadlands:ambient.creak` / `crow` / `metal` / `gust` / `frog` / `ember` / `mood` | `sounds/ambient/<name>N.ogg` (19 files) | one-shots the biomes add over their loops |
| `deadlands:event.town_enter` | `sounds/event/town_enter.ogg` | 5.5 s: wind swell, low drone, a far chime and a creaking sign under the town card |

### Data (Milestone 3)

| Asset | Files | Status |
|-------|-------|--------|
| Biomes | 10 in `data/deadlands/worldgen/biome/` | v0 |
| Configured and placed features | 24 and 33 in `data/deadlands/worldgen/` (incl. `surface_cover` and `road_network`) | v0 |
| Town structure and structure set | `worldgen/structure/town.json` (building lists per category), `worldgen/structure_set/towns.json` | v0 |
| Biome tags | `deadlands:is_deadlands`, `deadlands:has_structure/town`, and DEADLANDS biomes added to vanilla tags (overworld, strongholds, mineshafts, trial chambers, snow foxes, warm and cold farm animals...) | v0 |
| Town loot tables | 12 in `loot_table/containers/`: house kitchen, bathroom, bedroom and drawers, store stockroom, pharmacy, clinic, police locker, office, workshop, farm, church | v0 (vanilla items as stand-ins until M5) |
| Block drop tables | 20 new (4 terrain blocks, 16 furniture and fixtures) | v0 |

## Building and ground blocks (Milestone 2)

32x32 textures (2x vanilla density), each with LabPBR `_n` and `_s` maps. Generator:
`gen_blocks.py` using the material functions in `dl_materials.py`.

| Asset | Texture(s) under `deadlands/textures/block/` | Status | Notes |
|-------|----------------------------------------------|--------|-------|
| Asphalt, cracked asphalt | `asphalt`, `cracked_asphalt` | v1 | sparse aggregate, sun-bleached patches, oil stains; cracks with weeds |
| Road lines (yellow, white) | `asphalt_yellow_line`, `asphalt_white_line` | v1 | worn paint; the block turns to follow the player |
| Sidewalk | `sidewalk` | v1 | four slabs per block, dirty seams, one cracked slab |
| Concrete: weathered, cracked, stained, mossy | `concrete`, `cracked_concrete`, `stained_concrete`, `mossy_concrete` | v1 | formwork seam, pores, run-off stains, moss tufts |
| Bricks: weathered, soot-stained | `weathered_bricks`, `sooty_bricks` | v1 | per-brick tone, chipped faces |
| Plaster, peeling plaster | `plaster_wall`, `peeling_plaster` (+ `_2`, `_3`) | v1 | peeling plaster: 3 variants picked per block position; peels show an older cream coat and bare plaster |
| Hospital tiles, cracked | `hospital_tiles`, `cracked_hospital_tiles` | v1 | grout, grime, broken tiles |
| Boarded planks, weathered siding | `boarded_planks`, `weathered_siding` | v1 | long grain; siding keeps about 3/4 of its paint |
| Rusted sheet metal, corrugated metal, diamond plate | `rusted_sheet_metal`, `corrugated_metal`, `diamond_plate` | v1 | |
| Rubble, ash, dry dirt, dead grass block | `rubble`, `ash`, `dry_dirt`, `dead_grass_block_top/_side` | v1 | |
| Broken glass | `broken_glass` | v1 | translucent, crack star |
| 4 slabs (asphalt, cracked asphalt, concrete, sidewalk) | reuse the full-block textures | v1 | |

## Decals (Milestone 2)

64x64 RGBA textures in `deadlands/textures/block/decal/`, generator `gen_decals.py`.

| Decal | Status |
|-------|--------|
| Dried blood spots, dried blood pool, blood smear, bloody handprint, drag marks | v1 |
| Faded stain, scorch mark | v1 |
| Survivor mark (orange spray-painted arrow) | v1 |

## Containers (Milestone 2)

Generator: `gen_containers.py`. Five kinds x four conditions (clean, dusty, rusty, damaged).

| Asset | Files | Status | Notes |
|-------|-------|--------|-------|
| Body face textures | 188 x 32x32 in `deadlands/textures/block/container/<kind>/<condition>/` | v1 | weathering covers an exact share of the surface per condition; interiors are sheltered and age less |
| Moving-part textures (lid, drawer, doors) | 20 x 128x64 in `deadlands/textures/entity/container/` | v1 | 2 texels per pixel like the block faces; art oriented for the y-up part models |
| Body block models | 5 + 20 condition variants in `models/block/container/` | v1 | crate and toolbox are hollow (inner floor, inner walls, rim); cabinet and locker have shelves |
| Moving-part models | `client/render/ContainerPartModels` (code) | v1 | crate and toolbox lids, filing drawer with its tray of hanging files, medical double doors, locker door |
| Item models (inventory icons) | 5 in `models/item/` | v1 | body plus closed parts |
| Screen panels | `gui/sprites/widget/panel_wood.png`, `panel_medical.png`, `slot.png` | v1 | the metal style reuses `panel.png` |

## Item textures and models

| Asset | Texture (32x32) | Status |
|-------|-----------------|--------|
| Survivor's Journal | `deadlands/textures/item/survivor_journal.png` | v1 |
| Key (named after its key id) | `deadlands/textures/item/key.png` | v1 |
| Keycards, levels 1 to 3 | `keycard_green.png`, `keycard_blue.png`, `keycard_red.png` | v1 |
| Block items | block models (cubes, slabs, decals, containers) | v1 |

## Replaced vanilla textures

| Asset | Files (under `minecraft/textures/`) | Status | Generator |
|-------|-------------------------------------|--------|-----------|
| Terrain (Milestone 2) | 24 in `block/`: grass block top, side, side overlay; dirt, coarse dirt, stone, cobblestone, gravel, sand; oak, spruce and birch log and log top; oak and spruce planks; oak, spruce and birch leaves; short grass, fern, tall grass (2) | v1 | `gen_vanilla.py` |
| Grass and foliage colormaps (Milestone 2) | `colormap/grass.png`, `colormap/foliage.png` | v1 | dry, desaturated |
| Widgets (Milestone 1) | 15 in `gui/sprites/widget/`: button x3, slider x4, text field x2, checkbox x4, scroller x2 | v1 | `gen_gui` |
| Menu backgrounds and separators (Milestone 1) | 8 in `gui/` | v1 | `gen_gui.menu_backgrounds` |

## Interface textures (Milestone 1)

| Asset | File (under `assets/`) | Size | Status | Generator |
|-------|------------------------|------|--------|-----------|
| Title sky (overcast, smoke columns) | `deadlands/textures/gui/title/sky.png` | 1920x1080 | v1 | `gen_title.sky` |
| Far skyline (windows, crane, water tower, radio mast) | `.../title/skyline_far.png` | 1920x1080 | v1 | `gen_title.skyline_far` |
| Near ruins (gutted building, poles and wires, dead tree, car, sign) | `.../title/skyline_near.png` | 1920x1080 | v1 | `gen_title.skyline_near` |
| Drifting fog (horizontally tileable) | `.../title/fog.png` | 1024x512 | v1 | `gen_title.fog` |
| Vignette, film grain | `.../title/vignette.png`, `grain.png` | 512x288, 256x256 | v1 | `gen_title` |
| DEADLANDS logo | `.../title/logo.png` | 1123x250 | v1 | `gen_title.logo` + `dl_glyphs` |
| Survivor's Journal spread | `deadlands/textures/gui/journal/book.png` | 768x480 | v1 | `gen_gui.journal_book` |
| Buttons, sliders, riveted panel, inset, menu highlight and shade, lamps | `deadlands/textures/gui/sprites/widget/*.png` | various, nine-slice | v1 | `gen_gui` |
| Journal tabs, icons, DAY card stroke | `.../sprites/journal`, `icon`, `hud` | various | v1 | `gen_gui` |
| Mod icon | `deadlands/icon.png` | 128x128 | v1 | `gen_items.mod_icon` |

## Sounds (all synthesized, OGG Vorbis 44.1 kHz)

| Event | File | Length | Notes |
|-------|------|--------|-------|
| `deadlands:ui.click` | `sounds/ui/click.ogg` | 0.16 s | mechanical latch, double click |
| `deadlands:ui.journal_open` / `journal_close` / `page_turn` | `sounds/ui/*.ogg` | 0.85 / 0.5 / 0.42 s | leather creak and rustle; book thump; paper swish |
| `deadlands:event.day_begin` | `sounds/event/day_begin.ogg` | 7.0 s | sub boom, cracked distant bell, wind |
| `deadlands:music.title` and vanilla `music.menu` | `sounds/music/title_theme.ogg` | 96 s stereo | seamless loop |
| `deadlands:container.crate_open` / `crate_close` | `sounds/container/crate_*.ogg` | 1.0 / 0.6 s | wood creak and lid thud; heavy drop with rattle |
| `deadlands:container.drawer_open` / `drawer_close` | `sounds/container/drawer_*.ogg` | 0.7 / 0.6 s | runner roll and stop ring; roll and slam |
| `deadlands:container.cabinet_open` / `cabinet_close` | `sounds/container/cabinet_*.ogg` | 0.55 / 0.4 s | hinge squeak and glass tinkle; latch click |
| `deadlands:container.locker_open` / `locker_close` | `sounds/container/locker_*.ogg` | 0.8 / 0.8 s | rough squeak and sheet-metal boom; metal slam |
| `deadlands:container.toolbox_open` / `toolbox_close` | `sounds/container/toolbox_*.ogg` | 0.45 / 0.35 s | two latch snaps and tray rattle; lid clank |
| `deadlands:container.locked` / `unlock` | `sounds/container/locked.ogg`, `unlock.ogg` | 0.6 / 0.5 s | handle rattle; key scrape and two tumbler clicks |

## Data assets (Milestone 2)

| Asset | Files | Status |
|-------|-------|--------|
| Container loot tables (normal and rare per kind, plus the ranger locker) | 11 in `data/deadlands/loot_table/containers/` | v1 (vanilla items as stand-ins until M5) |
| Block drop tables | 34 in `data/deadlands/loot_table/blocks/` | v1 |
| Structure templates | `data/deadlands/structure/roadside_shack.nbt`, `supply_cache.nbt` | v1 |
| Worldgen (structures, template pools, structure sets, biome tag) | 7 in `data/deadlands/worldgen/` and `tags/worldgen/` | v1 |

## Animations (code-driven)

| Animation | Where | Status |
|-----------|-------|--------|
| Title fade-in, logo flicker, mouse parallax, drifting fog, film grain | `TitleBackdrop`, `DeadlandsTitleScreen` | v1 |
| Menu entry hover, DAY card | `DLMenuButton`, `DayCardHud` | v1 |
| Container open/close: lids swing, drawer slides, doors swing; eased, interpolated per frame, ajar rest pose | `LootContainerRenderer`, `ContainerPartModels` | v1 |
| Infected: procedural walk cycles and poses per kind, 7 keyframed actions | `InfectedModel`, `InfectedAnimations` | v0 (never seen in game) |
| Horde card and counter | `HordeHud` | v0 |
| Entity animations (NPCs, vehicles) | | Planned (M6, M7) |

## Still vanilla (to be replaced)

| Area | Milestone |
|------|-----------|
| Other terrain and building blocks (deepslate, ores, water, other woods, flowers, crops...) | M9 |
| Vanilla items, tools, food | M5 |
| Player inventory, creative inventory and recipe book screens | M5 |
| HUD bars (hearts, hunger, armour, hotbar, crosshair) | M5 |
| Vanilla mobs and their sounds (animals everywhere; monsters in oceans, rivers, peaks and cave biomes) | M7 / M9 |
| Font | M9 |
| Full sound occlusion (in-game music arrived in the atmosphere overhaul; ambience and voices are muffled behind walls) | later |

## Production plan for assets that cannot be fully generated by script

Scripts can produce good textures, block models, UI and sound design. Some assets need more:

1. **Entity models (NPCs, bosses, vehicles):** code-defined `ModelPart` layers (the format vanilla
   uses). The infected rigs are generated as Java by `gen_infected.py` from the same description
   that paints their textures; later rigs can follow that route or be designed in Blockbench and
   exported to Java, so they stay in the repository as source.
2. **Entity textures:** the infected's 66 looks come from a layered generator (skin tone, decay,
   clothing, tears, wounds, blood, dirt). They still need a review in game, and hand-painted touches
   where the generator falls short.
3. **Entity animations:** keyframes with Minecraft's `AnimationDefinition` system (as the Warden
   uses) plus procedural poses in the model; the infected's are written in code and need tuning
   once they can be watched.
4. **Sound effects:** synthesis first. Where realism needs field recordings (vehicles, gunfire,
   footsteps on many materials), use CC0 sources only, record the source URL and licence of every
   file in this document, and process them through the same pipeline.
5. **Music:** 13 synthesized pieces since the overhaul; a composer can replace or add tracks
   without code changes (`sounds.json` plus a line in `DLSounds.MUSIC_*` for a new track).
6. **Character skins (Harley, Itzbreadloaf, Imlilylol, FelixSyn):** configurable skin sources or
   user-provided skin files, never assumed from usernames (M7).
