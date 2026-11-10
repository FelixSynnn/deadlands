"""First DEADLANDS structures: templates (.nbt) plus the worldgen files that place them.

  roadside_shack  an abandoned ranger shack: filing cabinet, toolbox, a supply locker that always
                  holds the Ranger Station key, and a medical cabinet locked with that key
  supply_cache    an overrun military supply point: crates (one keycard-locked, one left ajar),
                  a toolbox, a locker, scorch marks and barricades

Both are single-piece jigsaw structures (any rotation, seed-deterministic, chunk-safe) limited to
land biomes. Milestone 3 replaces this placement with the town and road generator.
"""
from __future__ import annotations

import dl_data as D
from dl_nbt import Structure

NS = D.NS
STRUCTURES = D.DATA / NS / "structure"


def container(s: Structure, x, y, z, kind, facing, condition="clean", loot=None, lock=None, ajar=False):
    nbt = {"id": f"{NS}:loot_container", "LootTable": loot or f"{NS}:containers/{kind}"}
    if lock:
        nbt["deadlands_lock"] = lock
    s.set(x, y, z, kind, {"facing": facing, "condition": condition, "ajar": "true" if ajar else "false"}, nbt)


def decal(s: Structure, x, y, z, name, face, facing="north", turn=0):
    s.set(x, y, z, name, {"face": face, "facing": facing, "turn": str(turn)})


def roadside_shack() -> Structure:
    s = Structure(9, 5, 9)
    s.fill(1, 0, 1, 7, 0, 7, "concrete")
    s.fill(2, 0, 2, 6, 0, 6, "boarded_planks")
    for y in range(1, 4):
        for i in range(1, 8):
            for x, z in ((i, 1), (i, 7), (1, i), (7, i)):
                s.set(x, y, z, "weathered_siding")
        for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
            s.set(x, y, z, "boarded_planks")
    s.fill(2, 1, 2, 6, 3, 6, "minecraft:air")
    s.set(4, 1, 1, "minecraft:air")
    s.set(4, 2, 1, "minecraft:air")
    s.set(4, 3, 1, "boarded_planks")
    s.set(2, 2, 1, "broken_glass")
    s.set(6, 2, 1, "boarded_planks")          # barricaded window
    s.set(1, 2, 4, "broken_glass")
    s.set(7, 2, 4, "broken_glass")
    s.fill(0, 4, 0, 8, 4, 8, "corrugated_metal")
    for x, z in ((2, 6), (3, 6), (6, 2)):
        s.set(x, 4, z, "rusted_sheet_metal")
    s.set(5, 4, 5, "minecraft:air")           # the roof has rusted through

    container(s, 2, 1, 6, "filing_cabinet", "north", "dusty")
    container(s, 6, 1, 6, "supply_locker", "north", "rusty", loot=f"{NS}:containers/ranger_locker")
    container(s, 6, 1, 3, "medical_cabinet", "west", "dusty", lock={"type": "key", "key": "ranger_station"})
    container(s, 2, 1, 2, "toolbox", "east", "damaged")

    decal(s, 4, 1, 4, "dried_blood_pool", "floor", turn=1)
    decal(s, 4, 1, 3, "drag_marks", "floor")
    decal(s, 4, 1, 2, "drag_marks", "floor")
    decal(s, 5, 1, 5, "dried_blood_spots", "floor", turn=3)
    decal(s, 3, 2, 2, "bloody_handprint", "wall", facing="south")
    decal(s, 3, 3, 5, "faded_stain", "ceiling", turn=2)
    decal(s, 6, 2, 0, "survivor_mark", "wall", facing="north")
    s.set(0, 1, 7, "rubble")
    s.set(8, 1, 2, "rubble")
    return s


def supply_cache() -> Structure:
    s = Structure(11, 4, 11)
    for x in range(1, 10):
        for z in range(1, 10):
            h = (x * 73856093 ^ z * 19349663) % 100
            s.set(x, 0, z, "cracked_concrete" if h < 25 else "stained_concrete" if h < 40 else "concrete")
    walls = ["corrugated_metal", "boarded_planks", "rusted_sheet_metal"]
    for i in range(1, 10):
        for n, (x, z) in enumerate(((i, 1), (i, 9), (1, i), (9, i))):
            material = walls[(i // 3 + n) % 3]
            s.set(x, 1, z, material)
            if (i + n) % 4 != 0:  # the top row is broken in places
                s.set(x, 2, z, material)
    s.fill(2, 1, 2, 8, 3, 8, "minecraft:air")
    s.set(5, 1, 1, "minecraft:air")
    s.set(5, 2, 1, "minecraft:air")
    s.set(4, 2, 1, "minecraft:air")

    container(s, 3, 1, 8, "military_crate", "north", "dusty")
    container(s, 4, 1, 8, "military_crate", "north", "rusty", lock={"type": "keycard", "level": 1})
    container(s, 8, 1, 3, "military_crate", "west", "damaged", ajar=True)
    container(s, 8, 1, 8, "toolbox", "north", "rusty")
    container(s, 2, 1, 4, "supply_locker", "east", "dusty")

    decal(s, 5, 1, 5, "scorch_mark", "floor", turn=2)
    decal(s, 4, 1, 4, "dried_blood_spots", "floor", turn=1)
    decal(s, 6, 1, 6, "dried_blood_pool", "floor")
    decal(s, 3, 1, 0, "survivor_mark", "wall", facing="north")
    s.set(2, 1, 8, "rubble")
    s.set(8, 1, 6, "rubble")
    return s


def worldgen(name: str, spacing: int, separation: int, salt: int) -> None:
    D.data_json(f"worldgen/structure/{name}.json", {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/roadside_ruin",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "spawn_overrides": {},
        "start_pool": f"{NS}:{name}/start",
        "size": 1,
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
    })
    D.data_json(f"worldgen/template_pool/{name}/start.json", {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element",
            "location": f"{NS}:{name}",
            "processors": "minecraft:empty",
            "projection": "rigid",
        }}],
    })
    D.data_json(f"worldgen/structure_set/{name}.json", {
        "structures": [{"structure": f"{NS}:{name}", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt,
                      # Keep the ruins out of towns, like every other site.
                      "exclusion_zone": {"other_set": f"{NS}:towns", "chunk_count": 9}},
    })


def build() -> None:
    roadside_shack().write(STRUCTURES / "roadside_shack.nbt")
    supply_cache().write(STRUCTURES / "supply_cache.nbt")
    worldgen("roadside_shack", 26, 9, 184726113)
    worldgen("supply_cache", 34, 12, 99231771)
    # Every DEADLANDS biome (Milestone 3 replaced the vanilla land biomes these ruins used to need).
    D.data_json("tags/worldgen/biome/has_structure/roadside_ruin.json", {"replace": False, "values": ["#deadlands:is_deadlands"]})
    D.data_json("loot_table/containers/ranger_locker.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:key", "functions": [
                {"function": "minecraft:set_components", "components": {f"{NS}:key_id": "ranger_station"}}]}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "bonus_rolls": 0.0, "entries": [
                {"type": "minecraft:item", "name": "minecraft:bread", "weight": 6},
                {"type": "minecraft:item", "name": "minecraft:torch", "weight": 8,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
                {"type": "minecraft:item", "name": "minecraft:spyglass", "weight": 2},
                {"type": "minecraft:item", "name": "minecraft:map", "weight": 4},
                {"type": "minecraft:item", "name": "minecraft:leather_boots", "weight": 3},
            ]},
        ],
        "random_sequence": f"{NS}:containers/ranger_locker",
    })


if __name__ == "__main__":
    build()
    print("structures written")
