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
| Flashlight model | The flashlight in hand, in the inventory and on the ground | 3D, roughly 2 × 2 × 13 px body with a 3 × 3 × 3 px head (Blockbench JSON or box model), 32 × 32 texture, first- and third-person transforms tuned in game | `assets/deadlands/models/item/flashlight.json`, `textures/item/flashlight.png` | Placeholder: a three-box model from `gen_immersion.py`; in-hand position untested |
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

