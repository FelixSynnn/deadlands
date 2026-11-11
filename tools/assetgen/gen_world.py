"""DEADLANDS world data: the ten biomes, their vegetation and ground features, biome tags and names.

Biomes use the 1.21.11 format: climate, an `attributes` map (sky and fog colours, fog distance,
ambient particles, ambient sounds, background music), `effects` (water, grass and foliage colours),
carvers, features in the 11 generation steps, and spawners.

Feature order matters. Minecraft sorts the features of every biome in a dimension into one global
order and refuses to load a world if two biomes disagree. So every DEADLANDS biome uses the same
vanilla underground features, in vanilla's own order (copied from vanilla's plains), and adds its
own features from one global list (VEGETATION) in that list's order. See docs/ARCHITECTURE.md.
"""
from __future__ import annotations

import dl_data as D

NS = D.NS


def blk(name: str, **props) -> dict:
    state = {"Name": name if ":" in name else f"{NS}:{name}"}
    if props:
        state["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return state


def simple(state: dict) -> dict:
    return {"type": "minecraft:simple_state_provider", "state": state}


def weighted(*entries: tuple[dict, int]) -> dict:
    return {"type": "minecraft:weighted_state_provider", "entries": [{"data": s, "weight": w} for s, w in entries]}


AIR = blk("minecraft:air")
SOIL = ["minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:rooted_dirt",
        "minecraft:mud", "minecraft:moss_block", f"{NS}:dead_grass_block", f"{NS}:dry_dirt", f"{NS}:cracked_earth", f"{NS}:ash"]


# ----------------------------------------------------------------------------- placement modifiers

def count(n) -> dict:
    if isinstance(n, tuple):
        n = {"type": "minecraft:uniform", "min_inclusive": n[0], "max_inclusive": n[1]}
    return {"type": "minecraft:count", "count": n}


def rarity(chance: int) -> dict:
    return {"type": "minecraft:rarity_filter", "chance": chance}


SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}


def heightmap(kind: str) -> dict:
    return {"type": "minecraft:heightmap", "heightmap": kind}


def water_depth(depth: int) -> dict:
    return {"type": "minecraft:surface_water_depth_filter", "max_water_depth": depth}


def on_blocks(blocks: list[str]) -> dict:
    return {"type": "minecraft:block_predicate_filter",
            "predicate": {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": blocks}}


def when_empty() -> dict:
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}


def tree_placement(frequency: list[dict], max_water: int = 0) -> list[dict]:
    return [*frequency, SQUARE, water_depth(max_water), heightmap("OCEAN_FLOOR"), BIOME, on_blocks(SOIL)]


def patch_placement(frequency: list[dict]) -> list[dict]:
    return [*frequency, SQUARE, heightmap("WORLD_SURFACE_WG"), BIOME]


# ----------------------------------------------------------------------------- configured features

def tree(trunk: dict, trunk_placer: dict, foliage: dict, foliage_placer: dict, size=(1, 0, 1), decorators=None) -> dict:
    return {"type": "minecraft:tree", "config": {
        "trunk_provider": simple(trunk),
        "trunk_placer": trunk_placer,
        "foliage_provider": simple(foliage),
        "foliage_placer": foliage_placer,
        "dirt_provider": simple(blk("minecraft:dirt")),
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": size[0], "lower_size": size[1], "upper_size": size[2]},
        "decorators": decorators or [],
        "ignore_vines": True,
        "force_dirt": False,
    }}


def straight(base: int, a: int, b: int) -> dict:
    return {"type": "minecraft:straight_trunk_placer", "base_height": base, "height_rand_a": a, "height_rand_b": b}


def forking(base: int, a: int, b: int) -> dict:
    return {"type": "minecraft:forking_trunk_placer", "base_height": base, "height_rand_a": a, "height_rand_b": b}


def blob(radius: int, height: int) -> dict:
    return {"type": "minecraft:blob_foliage_placer", "radius": radius, "offset": 0, "height": height}


NO_FOLIAGE = {"type": "minecraft:blob_foliage_placer", "radius": 0, "offset": 0, "height": 0}
DEAD_LOG = blk("dead_log", axis="y")
CHARRED_LOG = blk("charred_log", axis="y")
DEAD_LEAVES = blk("dead_leaves", distance=7, persistent=False, waterlogged=False)


def inline(configured: str) -> dict:
    return {"feature": configured, "placement": []}


def selector(default: str, *options: tuple[str, float]) -> dict:
    return {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": inline(f), "chance": c} for f, c in options],
        "default": inline(default),
    }}


def random_patch(provider: dict, tries: int = 32, spread: int = 7) -> dict:
    return {"type": "minecraft:random_patch", "config": {
        "tries": tries, "xz_spread": spread, "y_spread": 3,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": provider}}, "placement": [when_empty()]},
    }}


def fallen(trunk: dict, lo: int, hi: int, mushrooms: bool) -> dict:
    decorators = [{"type": "minecraft:attached_to_logs", "probability": 0.12,
                   "block_provider": simple(blk("minecraft:brown_mushroom")), "directions": ["up"]}] if mushrooms else []
    return {"type": "minecraft:fallen_tree", "config": {
        "trunk_provider": simple(trunk),
        "log_length": {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi},
        "stump_decorators": [],
        "log_decorators": decorators,
    }}


def pile(provider: dict) -> dict:
    return {"type": "minecraft:block_pile", "config": {"state_provider": provider}}


CONFIGURED = {
    "surface_cover": {"type": f"{NS}:surface_cover", "config": {}},
    # Trees: dead, bare, charred, pines, marsh trees and overgrown oaks.
    "dead_tree": tree(DEAD_LOG, forking(4, 2, 2), DEAD_LEAVES, blob(2, 2)),
    "dead_tree_bare": tree(DEAD_LOG, forking(4, 3, 1), AIR, NO_FOLIAGE),
    "charred_tree": tree(CHARRED_LOG, forking(4, 2, 2), AIR, NO_FOLIAGE),
    "charred_stump": tree(CHARRED_LOG, straight(1, 1, 0), AIR, NO_FOLIAGE),
    "dead_pine": tree(DEAD_LOG, straight(6, 3, 1), DEAD_LEAVES,
                      {"type": "minecraft:spruce_foliage_placer",
                       "radius": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2},
                       "offset": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 2},
                       "trunk_height": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}},
                      size=(2, 0, 2)),
    "dead_pine_bare": tree(DEAD_LOG, straight(7, 3, 0), AIR, NO_FOLIAGE),
    "marsh_tree": tree(DEAD_LOG, straight(4, 2, 1), DEAD_LEAVES, blob(2, 2),
                       decorators=[{"type": "minecraft:leave_vine", "probability": 0.4}, {"type": "minecraft:trunk_vine"}]),
    "overgrown_oak": tree(blk("minecraft:oak_log", axis="y"), straight(4, 2, 0),
                          blk("minecraft:oak_leaves", distance=7, persistent=False, waterlogged=False), blob(2, 3),
                          decorators=[{"type": "minecraft:leave_vine", "probability": 0.3}, {"type": "minecraft:trunk_vine"}]),
    # Per-biome tree mixes.
    "trees_dead_forest": selector(f"{NS}:dead_tree_bare", (f"{NS}:dead_tree", 0.6)),
    "trees_burned_forest": selector(f"{NS}:charred_tree", (f"{NS}:charred_stump", 0.25)),
    "trees_dead_pine_valley": selector(f"{NS}:dead_pine", (f"{NS}:dead_pine_bare", 0.3)),
    "trees_overgrown_suburbs": selector(f"{NS}:overgrown_oak", (f"{NS}:dead_tree", 0.25)),
    "trees_flooded_marsh": selector(f"{NS}:marsh_tree", (f"{NS}:dead_tree_bare", 0.4)),
    "trees_frozen_outskirts": selector(f"{NS}:dead_pine_bare", (f"{NS}:dead_pine", 0.4)),
    # Fallen logs, boulders and debris.
    "fallen_dead_log": fallen(DEAD_LOG, 3, 6, mushrooms=True),
    "fallen_charred_log": fallen(CHARRED_LOG, 3, 6, mushrooms=False),
    "boulder": {"type": "minecraft:forest_rock", "config": {"state": blk("minecraft:cobblestone")}},
    "scrap_pile": pile(weighted((blk("rusted_sheet_metal"), 3), (blk("corrugated_metal"), 2), (blk("rubble"), 2), (blk("diamond_plate"), 1))),
    "rubble_pile": pile(weighted((blk("rubble"), 4), (blk("cracked_concrete"), 2), (blk("concrete"), 1), (blk("minecraft:gravel"), 1))),
    "hay_pile": pile({"type": "minecraft:rotated_block_provider", "state": blk("minecraft:hay_block", axis="y")}),
    # Patches of our own (the rest reuse vanilla's configured patches).
    "patch_wild_wheat": random_patch(weighted((blk("minecraft:tall_dry_grass"), 3), (blk("minecraft:short_dry_grass"), 1)), tries=48),
    "patch_moss": random_patch(simple(blk("minecraft:moss_carpet")), tries=24, spread=5),
}


# ----------------------------------------------------------------------------- placed features

# The global order of DEADLANDS vegetation. Each biome uses a subset, always in this order.
VEGETATION = {
    "trees_dead_forest": (f"{NS}:trees_dead_forest", tree_placement([count((3, 5))])),
    "trees_burned_forest": (f"{NS}:trees_burned_forest", tree_placement([count((3, 6))])),
    "trees_dead_pine_valley": (f"{NS}:trees_dead_pine_valley", tree_placement([count((5, 8))])),
    "trees_frozen_outskirts": (f"{NS}:trees_frozen_outskirts", tree_placement([count(1), rarity(2)])),
    "trees_overgrown_suburbs": (f"{NS}:trees_overgrown_suburbs", tree_placement([count((2, 4))])),
    "trees_rural_countryside": (f"{NS}:overgrown_oak", tree_placement([count(1), rarity(3)])),
    "trees_abandoned_farmland": (f"{NS}:dead_tree", tree_placement([count(1), rarity(4)])),
    "trees_flooded_marsh": (f"{NS}:trees_flooded_marsh", tree_placement([count((2, 3))])),
    "trees_dry_wasteland": (f"{NS}:dead_tree_bare", tree_placement([count(1), rarity(6)])),
    "trees_industrial_wasteland": (f"{NS}:dead_tree_bare", tree_placement([count(1), rarity(5)])),
    "fallen_dead_log": (f"{NS}:fallen_dead_log", tree_placement([rarity(5)])),
    "fallen_charred_log": (f"{NS}:fallen_charred_log", tree_placement([rarity(3)])),
    "boulder": (f"{NS}:boulder", [rarity(6), SQUARE, heightmap("MOTION_BLOCKING"), BIOME]),
    "scrap_pile": (f"{NS}:scrap_pile", [rarity(3), SQUARE, heightmap("MOTION_BLOCKING"), BIOME]),
    "rubble_pile": (f"{NS}:rubble_pile", [rarity(4), SQUARE, heightmap("MOTION_BLOCKING"), BIOME]),
    "hay_pile": (f"{NS}:hay_pile", [rarity(10), SQUARE, heightmap("MOTION_BLOCKING"), BIOME]),
    "patch_wild_wheat": (f"{NS}:patch_wild_wheat", patch_placement([count(6)])),
    "patch_dry_grass_sparse": ("minecraft:patch_dry_grass", patch_placement([count(1)])),
    "patch_dry_grass": ("minecraft:patch_dry_grass", patch_placement([count(3)])),
    "patch_dry_grass_dense": ("minecraft:patch_dry_grass", patch_placement([count(6)])),
    "patch_dead_bush": ("minecraft:patch_dead_bush", patch_placement([count(2)])),
    "patch_dead_bush_dense": ("minecraft:patch_dead_bush", patch_placement([count(5)])),
    "patch_leaf_litter": ("minecraft:patch_leaf_litter", patch_placement([count(3)])),
    "patch_grass": ("minecraft:patch_grass", patch_placement([count(4)])),
    "patch_grass_dense": ("minecraft:patch_grass", patch_placement([count(9)])),
    "patch_tall_grass": ("minecraft:patch_tall_grass", patch_placement([count(1), rarity(2)])),
    "patch_taiga_grass": ("minecraft:patch_taiga_grass", patch_placement([count(3)])),
    "patch_bush": ("minecraft:patch_bush", patch_placement([count(1), rarity(2)])),
    "patch_moss": (f"{NS}:patch_moss", patch_placement([count(2)])),
    "patch_reeds": ("minecraft:patch_sugar_cane", patch_placement([count(5)])),
    "patch_lily_pad": ("minecraft:patch_waterlily", patch_placement([count(3)])),
}

# Vanilla features every DEADLANDS biome keeps, in vanilla's order (from vanilla plains).
UNDERGROUND_ORES = [
    "ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower",
    "ore_andesite_upper", "ore_andesite_lower", "ore_tuff", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper",
    "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower",
    "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried",
    "ore_copper", "underwater_magma", "disk_sand", "disk_clay", "disk_gravel",
]


def features(vegetation: list[str]) -> list[list[str]]:
    unknown = [v for v in vegetation if v not in VEGETATION]
    assert not unknown, unknown
    ordered = [f"{NS}:{name}" for name in VEGETATION if name in vegetation]
    return [
        [],                                                                     # raw_generation
        ["minecraft:lake_lava_underground", "minecraft:lake_lava_surface"],    # lakes
        ["minecraft:amethyst_geode", f"{NS}:surface_cover"],                   # local_modifications
        [],                                                                     # underground_structures (no vanilla dungeons: Hard Rule 2)
        [],                                                                     # surface_structures
        [],                                                                     # strongholds
        [f"minecraft:{o}" for o in UNDERGROUND_ORES],                          # underground_ores
        [],                                                                     # underground_decoration
        ["minecraft:spring_water", "minecraft:spring_lava"],                   # fluid_springs
        ["minecraft:glow_lichen", *ordered, "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"],
        ["minecraft:freeze_top_layer"],                                        # top_layer_modification
    ]


# ----------------------------------------------------------------------------- biomes

def spawn(entity: str, weight: int, lo: int, hi: int) -> dict:
    return {"type": f"minecraft:{entity}", "weight": weight, "minCount": lo, "maxCount": hi}


BATS = [spawn("bat", 10, 8, 8)]


def particles(*entries: tuple[str, float]) -> list[dict]:
    return [{"particle": {"type": f"minecraft:{p}"}, "probability": prob} for p, prob in entries]


def ambience(loop: str, additions: list[tuple[str, float]]) -> dict:
    return {
        "loop": f"{NS}:ambient.{loop}.loop",
        "mood": {"sound": f"{NS}:ambient.mood", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        "additions": [{"sound": f"{NS}:ambient.{a}", "tick_chance": chance} for a, chance in additions],
    }


BIOMES = {
    "dead_forest": dict(
        english="Dead Forest", temperature=0.6, downfall=0.5,
        sky="#8e979c", fog="#a3a59c", water="#4a5a50", water_fog="#1a2420", grass="#787453", foliage="#6b6342",
        loop="dead_forest", additions=[("creak", 0.0111), ("crow", 0.006)],
        vegetation=["trees_dead_forest", "fallen_dead_log", "patch_dry_grass", "patch_dead_bush", "patch_leaf_litter", "patch_bush"],
        creatures=[spawn("wolf", 4, 2, 4), spawn("fox", 6, 2, 3), spawn("rabbit", 4, 2, 3)],
    ),
    "overgrown_suburbs": dict(
        english="Overgrown Suburbs", temperature=0.75, downfall=0.8,
        sky="#8fa0a6", fog="#a8b0a2", water="#44604e", water_fog="#15241c", grass="#6f8048", foliage="#5f7a3c",
        particles=particles(("spore_blossom_air", 0.0015)),
        loop="suburbs", additions=[("creak", 0.006), ("metal", 0.004)],
        vegetation=["trees_overgrown_suburbs", "rubble_pile", "patch_grass_dense", "patch_tall_grass", "patch_bush", "patch_moss", "patch_leaf_litter"],
        creatures=[spawn("rabbit", 4, 2, 3), spawn("cat", 3, 1, 1), spawn("chicken", 3, 1, 2)],
    ),
    "abandoned_farmland": dict(
        english="Abandoned Farmland", temperature=0.8, downfall=0.4,
        sky="#95a0a4", fog="#b1aea0", water="#4b5c52", water_fog="#182420", grass="#86804e", foliage="#7a7448",
        loop="fields", additions=[("crow", 0.0111), ("gust", 0.004)],
        vegetation=["trees_abandoned_farmland", "hay_pile", "patch_wild_wheat", "patch_dry_grass", "patch_grass", "patch_bush"],
        creatures=[spawn("chicken", 8, 2, 4), spawn("cow", 6, 2, 3), spawn("pig", 6, 2, 3), spawn("sheep", 8, 2, 4), spawn("horse", 2, 2, 3)],
    ),
    "dry_wasteland": dict(
        english="Dry Wasteland", temperature=1.6, downfall=0.0, precipitation=False,
        sky="#b2a68e", fog="#c4b394", water="#6a6a4a", water_fog="#2a2818", grass="#9b8a5a", foliage="#8f7e52",
        fog_range=(32.0, 240.0), particles=particles(("white_ash", 0.004)),
        loop="dry_wind", additions=[("gust", 0.0111)],
        vegetation=["trees_dry_wasteland", "patch_dry_grass_dense", "patch_dead_bush_dense"],
        creatures=[spawn("rabbit", 4, 2, 3)],
    ),
    "flooded_marsh": dict(
        english="Flooded Marsh", temperature=0.7, downfall=0.9,
        sky="#7f8c8c", fog="#8f9a8e", water="#3a4a32", water_fog="#1e2617", grass="#5f6a3e", foliage="#56603a",
        fog_range=(8.0, 120.0),
        loop="marsh", additions=[("frog", 0.0111), ("creak", 0.004)],
        vegetation=["trees_flooded_marsh", "patch_grass", "patch_tall_grass", "patch_moss", "patch_reeds", "patch_lily_pad"],
        creatures=[spawn("frog", 10, 2, 5)],
    ),
    "dead_pine_valley": dict(
        english="Dead Pine Valley", temperature=0.25, downfall=0.8,
        sky="#8a98a3", fog="#9ea6aa", water="#3f5258", water_fog="#14222a", grass="#6e7552", foliage="#5f6b4a",
        loop="pine", additions=[("creak", 0.0111), ("gust", 0.003)],
        vegetation=["trees_dead_pine_valley", "fallen_dead_log", "boulder", "patch_taiga_grass", "patch_leaf_litter", "patch_dead_bush"],
        creatures=[spawn("wolf", 8, 4, 4), spawn("fox", 8, 2, 4), spawn("rabbit", 4, 2, 3)],
    ),
    "frozen_outskirts": dict(
        english="Frozen Outskirts", temperature=-0.6, downfall=0.5,
        sky="#a6b1ba", fog="#c7cfd4", water="#3f5560", water_fog="#102028", grass="#8a8f7a", foliage="#7a826e",
        fog_range=(24.0, 180.0), particles=particles(("snowflake", 0.004)),
        loop="frozen", additions=[("gust", 0.0111)],
        vegetation=["trees_frozen_outskirts", "boulder", "patch_dead_bush", "patch_dry_grass_sparse"],
        creatures=[spawn("rabbit", 10, 2, 3), spawn("fox", 8, 2, 4), spawn("wolf", 4, 2, 4)],
    ),
    "industrial_wasteland": dict(
        english="Industrial Wasteland", temperature=0.7, downfall=0.3,
        sky="#8c8c88", fog="#9a958a", water="#4c4a3a", water_fog="#1f1d14", grass="#7c7550", foliage="#6e6845",
        fog_range=(24.0, 200.0), particles=particles(("ash", 0.01)),
        loop="industrial", additions=[("metal", 0.0111), ("gust", 0.003)],
        vegetation=["trees_industrial_wasteland", "scrap_pile", "rubble_pile", "patch_dry_grass", "patch_dead_bush"],
        creatures=[spawn("rabbit", 2, 1, 2)],
    ),
    "burned_forest": dict(
        english="Burned Forest", temperature=0.85, downfall=0.3,
        sky="#8a847c", fog="#8f857a", water="#4a4a40", water_fog="#1c1a14", grass="#6a604a", foliage="#4a4235",
        fog_range=(16.0, 160.0), particles=particles(("ash", 0.04), ("white_ash", 0.006)),
        loop="burned", additions=[("ember", 0.0111), ("creak", 0.003)],
        vegetation=["trees_burned_forest", "fallen_charred_log", "patch_dry_grass_sparse", "patch_dead_bush"],
        creatures=[spawn("rabbit", 2, 1, 2)],
    ),
    "rural_countryside": dict(
        english="Rural Countryside", temperature=0.7, downfall=0.6,
        sky="#8fa2ad", fog="#b4b7ab", water="#48606a", water_fog="#16242a", grass="#7d7c4f", foliage="#6d7044",
        loop="fields", additions=[("crow", 0.006), ("gust", 0.003)],
        vegetation=["trees_rural_countryside", "fallen_dead_log", "boulder", "hay_pile", "patch_grass_dense", "patch_tall_grass", "patch_bush"],
        creatures=[spawn("cow", 6, 2, 3), spawn("sheep", 8, 2, 4), spawn("horse", 4, 2, 4), spawn("chicken", 6, 2, 3),
                   spawn("rabbit", 4, 2, 3), spawn("donkey", 1, 1, 2)],
    ),
}


def biome_json(name: str, b: dict) -> dict:
    attributes = {
        "minecraft:visual/sky_color": b["sky"],
        "minecraft:visual/fog_color": b["fog"],
        "minecraft:visual/water_fog_color": b["water_fog"],
        "minecraft:audio/ambient_sounds": ambience(b["loop"], b["additions"]),
        "minecraft:audio/background_music": {},  # vanilla music never plays in a world (MinecraftMixin); DEADLANDS music is MusicDirector
    }
    if "fog_range" in b:
        attributes["minecraft:visual/fog_start_distance"] = b["fog_range"][0]
        attributes["minecraft:visual/fog_end_distance"] = b["fog_range"][1]
    if "particles" in b:
        attributes["minecraft:visual/ambient_particles"] = b["particles"]
    return {
        "has_precipitation": b.get("precipitation", True),
        "temperature": b["temperature"],
        "downfall": b["downfall"],
        "attributes": attributes,
        "effects": {"water_color": b["water"], "grass_color": b["grass"], "foliage_color": b["foliage"], "dry_foliage_color": b["foliage"]},
        "carvers": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"],
        "features": features(b["vegetation"]),
        # No vanilla creatures at all (Hard Rule 2): the infected (InfectedSpawner) are the only life here.
        "spawners": {"monster": [], "creature": [], "ambient": [],
                     "water_creature": [], "water_ambient": [], "underground_water_creature": [], "misc": [], "axolotls": []},
        "spawn_costs": {},
    }


# ----------------------------------------------------------------------------- tags and names

def biome_tag(path: str, values: list[str], ns: str = NS) -> None:
    D.data_json(f"tags/worldgen/biome/{path}.json", {"replace": False, "values": values}, ns=ns)


def build() -> None:
    for name, cfg in CONFIGURED.items():
        D.data_json(f"worldgen/configured_feature/{name}.json", cfg)
    D.data_json("worldgen/placed_feature/surface_cover.json", {"feature": f"{NS}:surface_cover", "placement": []})
    for name, (feature, placement) in VEGETATION.items():
        D.data_json(f"worldgen/placed_feature/{name}.json", {"feature": feature, "placement": placement})

    lang = {}
    for name, b in BIOMES.items():
        D.data_json(f"worldgen/biome/{name}.json", biome_json(name, b))
        lang[f"biome.{NS}.{name}"] = b["english"]

    every = [f"{NS}:{n}" for n in BIOMES]
    biome_tag("is_deadlands", every)
    # has_structure/town is written by gen_towns.py (DEADLANDS biomes plus vanilla land biomes).
    # Mineshafts and trial chambers are no longer added (their spawners): gen_immersion empties every vanilla structure tag.
    for vanilla in ("is_overworld", "stronghold_biased_to"):
        biome_tag(vanilla, every, ns="minecraft")
    for vanilla in ("spawns_snow_foxes", "spawns_white_rabbits", "spawns_cold_variant_farm_animals"):
        biome_tag(vanilla, [f"{NS}:frozen_outskirts"], ns="minecraft")
    biome_tag("spawns_warm_variant_farm_animals", [f"{NS}:dry_wasteland"], ns="minecraft")
    biome_tag("spawns_gold_rabbits", [f"{NS}:dry_wasteland"], ns="minecraft")
    biome_tag("has_closer_water_fog", [f"{NS}:flooded_marsh"], ns="minecraft")

    lang.update({
        "config.deadlands.section.world": "World",
        "config.deadlands.world.deadlandsBiomes": "DEADLANDS biomes",
        "config.deadlands.world.roadNetwork": "Road network",
        "config.deadlands.world.roadDecay": "Road decay",
        "config.deadlands.world.roadsideUtilities": "Roadside poles",
        "config.deadlands.ui.townCards": "Town cards",
    })
    D.merge_lang(lang)


if __name__ == "__main__":
    build()
    print(f"{len(BIOMES)} biomes, {len(CONFIGURED)} configured and {len(VEGETATION) + 1} placed features written")
