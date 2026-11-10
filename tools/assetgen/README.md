# DEADLANDS asset generators

Every DEADLANDS texture, block model, entity rig, loot table, structure template and sound is
produced by these scripts from fixed seeds, so the output is reproducible and original. The
generated files are committed (under `src/main/resources`, plus two generated Java files for the
infected), so building the mod never requires Python.

## Requirements

- Python 3.12 with `numpy`, `scipy`, `Pillow`, `soundfile`
  (`python -m pip install --user numpy scipy pillow soundfile`)
- `ffmpeg` with libvorbis on `PATH` (sounds only)

## Running

From the project root:

```bash
python tools/assetgen/build_all.py
```

Or only some groups (`title`, `gui`, `items`, `blocks`, `containers`, `decor`, `decals`,
`structures`, `vanilla`, `terrain`, `world`, `towns`, `sites`, `infected`, `nests`, `audio`,
`ambience`, `infected_audio`, `survival`, `vehicles`, `npcs`, `story`, `bosses`, `polish`, and
`realism`, which must come last):

```bash
python tools/assetgen/build_all.py blocks containers
```

## Files

| File | Produces |
|------|----------|
| `dl_noise.py` | shared helpers: periodic spectral noise, Worley cells, domain warp, relief shading, colour ramps, PNG output |
| `dl_materials.py` | tileable 32x32 materials (asphalt, concrete, bricks, plaster, tiles, boards, metals, ground, glass) and their LabPBR `_n` / `_s` maps |
| `dl_data.py` | JSON helpers: blockstates, models, item model definitions, loot tables, merged tags and language file |
| `dl_nbt.py` | NBT writer and structure-template builder |
| `dl_glyphs.py` | the DEADLANDS stencil letterforms (polygons on a 100-unit grid) |
| `gen_title.py` | title backdrop layers, fog, vignette, grain, logo |
| `gen_gui.py` | DEADLANDS widgets, journal tabs, icons, DAY stroke, the journal spread, restyled vanilla widgets and menu backgrounds |
| `gen_items.py` | item icons (32x32: journal, key, keycards) and the mod icon |
| `gen_blocks.py` | building and ground blocks: textures (with variants), models, blockstates, slabs, loot, tool tags, names |
| `gen_containers.py` | containers: body models (hollow where they open), face textures per condition, moving-part textures, blockstates, item models, loot tables, screen panels |
| `gen_decals.py` | blood, drag, scorch and survivor-mark decals with their models |
| `gen_structures.py` | the roadside shack and supply cache templates (`.nbt`) and their worldgen files |
| `gen_vanilla.py` | DEADLANDS replacements for vanilla terrain textures and the grass/foliage colormaps |
| `gen_audio.py` | UI sounds, the DAY sting, the menu theme, container sounds (OGG Vorbis via ffmpeg) |
| `gen_decor.py` | furniture and street fixtures: textures, multi-part models, blockstates, item models, loot, tool tags, names |
| `gen_terrain.py` | dead and charred logs, dead leaves, cracked earth, and DEADLANDS restyles of more vanilla plants and ground |
| `gen_world.py` | the ten biomes, their configured and placed features (trees, logs, rocks, piles, patches, surface cover), biome tags and names |
| `gen_towns.py` | town building templates (76 with `gen_city`'s and the seeded variants), the town structure and structure set, the road feature data, town loot tables, town text, iron bars and cobweb |
| `gen_city.py` | city buildings (apartment and office blocks, department store, motels, bank, courthouse, fire stations, emergency rooms) and western buildings (saloons, general stores, wooden houses, sheriff's office); used by `gen_towns` |
| `gen_sites.py` | the 37 site templates outside towns (school, outposts, camps, military posts, bunkers, utility sites, power station, treatment plant, farm sites, research outpost), their worldgen files and loot tables, the railway feature data, the fuse and the restyled rails |
| `gen_infected.py` | the infected: both rigs and the variant list **as Java source** (`InfectedGeometry.java`, `InfectedVariants.java`), 58 textures and 2 glow layers, spawn eggs, entity and outfit loot tables, breakable-block tags, horde and infected text; also a review sheet in `_cache/infected_preview.png` |
| `gen_nests.py` | the five nest cores, biomass and its floor growth, the tissue sample, their models, drops, tags and names |
| `gen_infected_audio.py` | infected voices and actions, nest sounds, horde stings and the distant crowd (94 files, merged into `sounds.json`) |
| `gen_ambience.py` | biome ambience: 9 seamless loops, one-shot additions, the town sting |
| `gen_survival.py` | survival items, weapons, the workbench, HUD and screen art, recipes and survival sounds |
| `gen_vehicles.py` | the twelve vehicle rigs with interiors and doors **as Java source** (`VehicleGeometry.java`), 48 textures and 48 overlays (512x512), the tow rope, the engine loop, impact, repair and horn sounds |
| `gen_story.py` | the lore catalogue **as Java source** (`LoreEntries.java`) from `lore_data.py`, and (through `story_assets.py` and `story_texts.py`) all story text, item icons, the reader, door and transmitter, the helicopter rig, radio and rotor sounds, structure tags |
| `gen_bosses.py` | the five boss rigs **as Java source** (`BossGeometry.java`), their textures, spawn eggs, voices and the boss sting |
| `gen_polish.py` | Milestone 9: the five particles, location ambience sounds, the supply drop (rig **as Java source**, texture, loot), weather, event, cutscene and command text |
| `dl_realism.py` | the finishing pass over block and item textures (light from the upper left, hue-shifted shading, a five-step ramp, calmer saturation, coloured item outlines); copies each original to `texture-backup/` first, keeps a hash manifest in `_cache/` so it never applies twice, and leaves the textures in its `RESTORED` list as painted |
| `gen_npcs.py` | 34 survivor skins (30 by role, and the 4 characters' skins copied from `character-skins/<Name>.png`, matched ignoring case, or placeholders if missing), the spawn marker's files, the vehicle cargo loot table, survivor voices, the journal TASKS icon, all dialogue, settlement and access text |

## Style guide

- Palette: gunmetal `#1d2225`-`#394044`, rust `#3a1d10`-`#8e4e28`, bone `#e3dccb`, amber accent
  `#c8902e`, ink `#2a241f`, dried red `#4e1611` (sparingly).
- Materials read as weathered: grime, rust pitting, chipped paint, stains. Nothing looks new.
- Nine-slice sprites take their centre and edges from *periodic* noise (`plate()` +
  `tile_window()`), and nothing decorative is painted inside the tiled centre.
- Item textures are 32x32, placed on whole pixels, with a 1 px dark outline.
- Block textures are 32x32 (2 texels per pixel). Moving container parts use the same density.
- Weathering covers an exact share of a surface (quantile thresholds), not whatever a noise
  threshold gives; sheltered interiors age less than shells.
- Distinct features (peels, holes, stains) repeat visibly in a grid: give such blocks several
  variants (`VARIANT_SEEDS` in `gen_blocks.py`) and keep those features clear of the tile edges.
- At 32x32, rough noise reads as gravel: use smooth, stretched noise for wood grain and streaks.
- Entity textures are 256x128 for a 128x64 UV space (2 texels per pixel, like blocks). The rig
  and its texture regions come from one description in `gen_infected.py`: change the rig there,
  never in the generated Java.
- Seeds must be stable between runs: use `zlib.crc32`, never Python's salted `hash()`.
- Sounds: mono for effects, stereo only for music; peaks below -1 dBFS.

## Adding an asset

1. Add a function to the right generator (or a new `gen_*.py` registered in `build_all.py`).
2. Use a new fixed seed.
3. Regenerate, look at the result in game (or, if the game cannot be run, at rendered previews,
   and mark it v0), and add a row to `ASSET_STATUS.md`.
4. New items and sounds are checked by the asset-completeness game test.
