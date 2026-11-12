# DEADLANDS: Asset Manifest

Every asset that is a placeholder, or needs an artist, composer, sound designer or actor to reach
final quality. The pipeline for each (registration, loading, triggers, subtitles) is already in
place; replacing the file at the listed path is enough unless noted. Nothing ships as a
placeholder without a line here.

| Asset | Purpose | Spec | Path | Status |
|-------|---------|------|------|--------|
| Chase music | Plays only when 8+ infected chase a survivor; looped, faded by `MusicDirector` | Original, about 45–90 s, seamless loop, tense and percussive, OGG Vorbis stereo 44.1 kHz | `music/chase_strings.ogg`, `music/chase_strikes.ogg` (`music.chase`) | Third-party (see below); the Phase 1 placeholder `music/chase.ogg` is unused |
| Exploration music | At most one piece every 10–15 minutes of play | Original, 60–120 s, sparse and cold, fades in and out, OGG Vorbis stereo | `music/explore_ambience.ogg`, `music/explore_low_tone.ogg` (`music.explore`) | Third-party (see below); the Phase 1 placeholder `music/explore.ogg` is unused |
| Menu theme | Title and menus, at 45% volume | Original, 60–120 s seamless loop, OGG Vorbis stereo | `assets/deadlands/sounds/music/title_theme.ogg` | Placeholder (synthesised, `gen_audio.py`) |
| Flashlight model | The flashlight in hand, in the inventory and on the ground | 3D, about 3.3 × 3.3 × 13 units (bezel, head, knurled body, tail cap, switch, clip), first- and third-person transforms tuned in game | `assets/deadlands/models/item/flashlight.json`, `textures/item/model/flashlight.png` | Generated cuboid model (`gen_models3d.py`, Phase 3), 51 boxes; needs an artist mesh for final quality; in-hand position untested |
| Weapon models (13) | Every weapon in hand, on the ground, in frames and on shelves (the inventory keeps the flat icon) | 3D, realistic proportions and materials; equip, inspect, reload and fire animations come in Phase 4 | `assets/deadlands/models/item/<weapon>_3d.json`, `textures/item/model/<weapon>.png` | Generated cuboid models (`gen_models3d.py`), 7 to 68 boxes each (round parts are built from thin plates), painted materials at 8 texels per unit; need artist meshes for final quality |
| Light fixtures | Fluorescent ceiling light (flickers when faulty), battery emergency light (red, pulsing) | 3D block models, emissive tubes and lens | `assets/deadlands/models/block/fluorescent_light*.json`, `emergency_light_*.json`, `textures/block/model/` | Generated cuboid models (`gen_visuals.py`); placed in 41 town building templates (`gen_towns.py`) |
| Equipment models (38) | Packs, medical supplies, radio, battery, keys and keycards, food and drink, water, ammunition, fuel can, radio parts, fuse: in hand, on the ground, in frames | 3D, realistic proportions and materials | `assets/deadlands/models/item/<item>_3d.json`, `textures/item/model/<item>.png` | Generated cuboid models (`gen_equipment3d.py`); need artist meshes for final quality |
| Furniture and roadside models (13 kinds, 7 versions) | Utility pole, street lamp and head, traffic light, transformer, guardrail, generator, whiteboard, workbench, couch, bed, bookshelf (3), desk (3), counter (2) | 3D block models within each block's collision shape | `assets/deadlands/models/block/<kind>*.json`, `textures/block/model/decor/` | Generated cuboid models (`gen_decor3d.py`); need artist meshes for final quality |
| Character gear: Itzbreadloaf | 3D backpack, bedroll, straps, belt, pouch, holstered flashlight, wrap, watch and hair over his skin, posed with the body | Box geometry on the player model's parts, 64 × 64 texture | `client/render/character/CharacterGear.java`, `textures/entity/survivor/character/itzbreadloaf_gear.png` | Generated (`gen_character3d.py`); a full character mesh needs an artist |
| Interior materials (5) | Faded and striped wallpaper, wood panelling, stained carpet, linoleum | 32 × 32 tileable with LabPBR maps | `textures/block/<name>.png` | Generated (`gen_interiors.py`) |
| Adam's skin | Adam, the scientist father (Phase 5) | 64 × 64 player skin: realistic, lab coat, glasses, greying hair | `textures/entity/survivor/character/adam.png` | Placeholder: the researcher skin with glasses and greyed hair (`gen_story5.py`) |
| Sealed sample case | Harley's object for Adam (chapter one) | 3D model and icon | `models/item/sealed_sample_case_3d.json`, `textures/item/sealed_sample_case.png` | Generated cuboid model (`gen_story5.py`) |
| Flashlight click | Switching the flashlight on and off | Short (under 0.15 s) plastic click, two variants (on, off) | uses vanilla `block.stone_button.click_on/off` | Placeholder: vanilla sound; needs its own sound event |
| Stings (day, town, horde, boss) | Played on the music channel when those events happen | Short original stingers, OGG Vorbis | `assets/deadlands/sounds/event/*.ogg` | Placeholder (synthesised, earlier milestones) |
| Horror sounds | The Horror Director's night creature, dread beds, whispers, door, horn, stingers, scream | Positional sounds mono, beds and stingers stereo, OGG Vorbis | `assets/deadlands/sounds/horror/*.ogg` (`horror.*`) | Third-party (see below) |

## Third-party sounds

Imported by `tools/assetgen/import_sfx.py` from the YouTube playlist "Horror/Thriller Sounds" by the
channel **Sound Library** (https://www.youtube.com/playlist?list=PL_2MB6_9kLAGMaFV1Vt4GE0v9dogNRf74),
chosen by the project owner on 2026-11-11 (laugh, clown and spoken clips left out). The channel's
terms: free to use; credit the original creator if there is one. The channel also states that
most of these sounds were not made by it and that creators are unknown ("royalty free and/or
Creative Commons and/or unknown creator"). Their origin is therefore uncertain: credit **Sound
Library** in the game's credits, and replace them with original or clearly licensed sounds before
any commercial release.

