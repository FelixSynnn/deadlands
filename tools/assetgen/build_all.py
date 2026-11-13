"""Regenerate every DEADLANDS asset. Run from the project root:

    python tools/assetgen/build_all.py                 # everything
    python tools/assetgen/build_all.py blocks decals   # only some groups

Groups (in run order): title, gui, items, blocks, containers, decor, decals, structures, vanilla, terrain,
world, towns, sites, infected, nests, audio, ambience, infected_audio, survival, vehicles, npcs, story,
bosses, polish, weapons, menu, music, soundscape, infected_voices, realism (it finishes the textures the others paint), variants (made from the finished textures).
Output is deterministic (fixed seeds), so regenerating without changing a generator reproduces
the committed files. Tag files are merged, so groups can be run in any combination.
"""
from __future__ import annotations

import sys
import time

import gen_ambience
import gen_audio
import gen_blocks
import gen_containers
import gen_decor
import gen_decals
import gen_gui
import gen_infected
import gen_infected_audio
import gen_infected_voices
import gen_items
import gen_menu
import gen_music
import gen_soundscape
import gen_nests
import gen_npcs
import gen_story
import gen_bosses
import gen_polish
import gen_sites
import gen_survival
import gen_structures
import gen_terrain
import gen_title
import gen_towns
import gen_vanilla
import gen_weapons
import gen_vehicles
import gen_world
import gen_immersion
import import_sfx
import gen_framework
import gen_models3d
import gen_equipment3d
import gen_decor3d
import gen_character3d
import gen_interiors
import import_zombie_sfx
import gen_gameplay
import gen_weapon_sfx
import gen_story5
import gen_cinematics
import gen_story_ch4
import gen_round6
import gen_lily1
import gen_felix1
import gen_round8
import gen_subway
import gen_subway_audio
import import_menu_backgrounds
import import_pixabay
import import_asesino
import gen_asesino_model
import import_aberrant
import import_guest
import import_guest_sfx
import import_pixabay_creatures
import gen_visuals
import dl_realism
import gen_variants

GROUPS = {
    "title": gen_title.build,
    "gui": gen_gui.build,
    "items": gen_items.build,
    "blocks": gen_blocks.build,
    "containers": gen_containers.build,
    "decor": gen_decor.build,
    "decals": gen_decals.build,
    "structures": gen_structures.build,
    "vanilla": gen_vanilla.build,
    "terrain": gen_terrain.build,  # after vanilla: restyles more vanilla textures
    "world": gen_world.build,
    "towns": gen_towns.build,  # building templates (incl. gen_city's), the town structure and the road feature
    "sites": gen_sites.build,  # the school and wilderness sites, railway feature data, fuse, rail textures
    "infected": gen_infected.build,  # rigs (Java), variants (Java), textures, spawn eggs, entity loot, tags
    "nests": gen_nests.build,  # nest cores, biomass, tissue item
    "audio": gen_audio.build,
    "ambience": gen_ambience.build,  # after audio: adds its entries to sounds.json
    "infected_audio": gen_infected_audio.build,  # infected, nest and horde sounds (merged into sounds.json)
    "survival": gen_survival.build,  # after the audio groups: item icons, HUD sprites, damage types, recipes, weapon sounds, text
    "vehicles": gen_vehicles.build,  # after the audio groups: rigs (Java), textures, part icons, sounds, text
    "npcs": gen_npcs.build,  # survivor skins, spawn marker files, vehicle cargo table, dialogue text
    "story": gen_story.build,  # lore (Java), story text, story items and blocks, helicopter, radio sounds
    "bosses": gen_bosses.build,  # boss rigs (Java), textures, eggs, sounds
    "polish": gen_polish.build,  # particles, location ambience, supply drop (Java model), weather/event/cutscene text
    # Atmosphere overhaul, after every older audio group (these replace some of their sounds.json entries):
    "weapons": gen_weapons.build,  # weapon icons painted per pixel, held models, new weapons' text, recipes and loot
    "menu": gen_menu.build,  # the main-menu backdrop (replaces gen_title's sky and skylines; gen_title still draws the logo)
    "music": gen_music.build,  # the soundtrack: title theme, exploration, night, danger, horde and safe-zone pieces
    "soundscape": gen_soundscape.build,  # crows, wind, rain on roofs, distant noises, the menu bed
    "infected_voices": gen_infected_voices.build,  # every infected voice; then removes sound files nothing refers to
    "immersion": gen_immersion.build,  # horror Phase 1: vanilla structures off, vanilla ambience silenced, music placeholders, flashlight
    "sfx": import_sfx.build,  # after immersion: the chosen third-party horror sounds and music (skipped if the source folder is missing)
    "framework": gen_framework.build,  # horror Phase 2: scares, mission givers, achievements, cutscenes as data, the campaign dimension
    "zombie_sfx": import_zombie_sfx.build,  # Phase 4: the owner's zombie and stalker sounds for the infected (after infected_voices; skipped if the files are missing)
    "models3d": gen_models3d.build,  # horror Phase 3: 3D models for every weapon and the flashlight (replaces the flashlight's box model)
    "equipment3d": gen_equipment3d.build,  # horror Phase 3: 3D models for equipment, food, medicine, ammunition
    "decor3d": gen_decor3d.build,  # horror Phase 3: detailed furniture and roadside models (after gen_decor), random desk/shelf/counter versions
    "character3d": gen_character3d.build,  # horror Phase 3: 3D gear for the characters (Java geometry + texture)
    "interiors": gen_interiors.build,  # horror Phase 3: wallpapers, panelling, carpet, linoleum (before realism finishes them)
    "visuals": gen_visuals.build,  # horror Phase 3: light fixtures (models, states, loot, names), weather text
    "weapon_sfx": gen_weapon_sfx.build,  # Phase 4: melee swing and impact sounds (synthesised)
    "gameplay": gen_gameplay.build,
    "story5": gen_story5.build,  # Phase 5: chapter one (Harley's questline, Adam, the sample case)  # Phase 4: gameplay settings text
    "cinematics": gen_cinematics.build,  # Phase 5: the intro cutscene, the day 10 reunion, weather disasters text
    "story_ch4": gen_story_ch4.build,  # Phase 5: chapter four (Pinecrest: the mission, the arrival cutscene, its text)
    "lily1": gen_lily1.build,  # Phase 5 step 5: Imlilylol's campaign, chapter one (Open Road)
    "felix1": gen_felix1.build,  # Phase 5 step 5: FelixSyn's campaign, chapter one (The East Gate)
    "subway": gen_subway.build,  # Phase 5: the abandoned subway stations (templates, worldgen, loot)
    "subway_audio": gen_subway_audio.build,  # Phase 5: the subway PA announcements (Windows speech + ffmpeg)
    "menu_backgrounds": import_menu_backgrounds.build,  # the main menu's background pool (the owner's screenshots)
    "round8": gen_round8.build,  # the menu music settings text
    "pixabay": import_pixabay.build,  # Phase 5: real recordings from Pixabay (after every sound generator; see CREDITS.md)
    "pixabay_creatures": import_pixabay_creatures.build,  # Phase 5: real recordings for every infected, boss and nest sound (after pixabay: appends to CREDITS.md)
    "asesino_model": gen_asesino_model.build,  # Phase 5: the Asesino's own rig (Java) and texture
    "aberrant": import_aberrant.build,  # Phase 5: the Aberrant's imported fan-made model (Sketchfab, credited), rigged for his animations
    "guest": import_guest.build,  # Phase 5: the Guest's imported model (Fourteen, CC-BY-4.0), simplified, rigged, recoloured
    "guest_sfx": import_guest_sfx.build,  # Phase 5: the Guest's sounds and music (the owner's pack), egg, text, credits
    "asesino": import_asesino.build,  # Phase 5: the Asesino's skin, eyes, egg, sounds and music (the owner's files)
    "round6": gen_round6.build,  # Phase 5: hiding in the dark, terror radii, the lunge, the Green Tornado, safe respawn, spawn settings (after pixabay)
    "realism": dl_realism.build,  # last: the finishing pass over block and item textures (originals in texture-backup/)
    "variants": gen_variants.build,  # after realism: per-position variants blended from the finished textures
}


def main(argv: list[str]) -> None:
    wanted = argv or list(GROUPS)
    for name in wanted:
        if name not in GROUPS:
            raise SystemExit(f"unknown group {name!r}; choose from {', '.join(GROUPS)}")
        start = time.perf_counter()
        GROUPS[name]()
        print(f"{name:10s} done in {time.perf_counter() - start:5.1f}s")


if __name__ == "__main__":
    main(sys.argv[1:])
