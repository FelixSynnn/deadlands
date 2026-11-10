"""Regenerate every DEADLANDS asset. Run from the project root:

    python tools/assetgen/build_all.py                 # everything
    python tools/assetgen/build_all.py blocks decals   # only some groups

Groups (in run order): title, gui, items, blocks, containers, decor, decals, structures, vanilla, terrain,
world, towns, sites, infected, nests, audio, ambience, infected_audio, survival, vehicles, npcs, story,
bosses, polish, realism (always last: it finishes the textures the others paint).
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
import gen_items
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
import gen_vehicles
import gen_world
import dl_realism

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
    "realism": dl_realism.build,  # last: the finishing pass over block and item textures (originals in texture-backup/)
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
