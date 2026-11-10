"""DEADLANDS building and ground blocks: textures (+ LabPBR maps), blockstates, models, item models,
loot tables, tool tags and English names, all from one list.

The block ids here must match com.deadlands.registry.DLBlocks; the asset-completeness game test
fails the build if a registered block is missing any of these files.
"""
from __future__ import annotations

import dl_data as D
import dl_materials as M

NS = D.NS

# name -> (shape, english name, mining tool)
#   shape: cube | line | bottom_top
BLOCKS: dict[str, tuple[str, str, str]] = {
    "asphalt": ("cube", "Asphalt", "pickaxe"),
    "cracked_asphalt": ("cube", "Cracked Asphalt", "pickaxe"),
    "asphalt_yellow_line": ("line", "Asphalt (Yellow Line)", "pickaxe"),
    "asphalt_white_line": ("line", "Asphalt (White Line)", "pickaxe"),
    "sidewalk": ("cube", "Sidewalk", "pickaxe"),
    "concrete": ("cube", "Weathered Concrete", "pickaxe"),
    "cracked_concrete": ("cube", "Cracked Concrete", "pickaxe"),
    "stained_concrete": ("cube", "Stained Concrete", "pickaxe"),
    "mossy_concrete": ("cube", "Mossy Concrete", "pickaxe"),
    "weathered_bricks": ("cube", "Weathered Bricks", "pickaxe"),
    "sooty_bricks": ("cube", "Soot-Stained Bricks", "pickaxe"),
    "plaster_wall": ("cube", "Plaster Wall", "pickaxe"),
    "peeling_plaster": ("cube", "Peeling Plaster", "pickaxe"),
    "hospital_tiles": ("cube", "Hospital Tiles", "pickaxe"),
    "cracked_hospital_tiles": ("cube", "Cracked Hospital Tiles", "pickaxe"),
    "boarded_planks": ("cube", "Boarded Planks", "axe"),
    "weathered_siding": ("cube", "Weathered Siding", "axe"),
    "rusted_sheet_metal": ("cube", "Rusted Sheet Metal", "pickaxe"),
    "corrugated_metal": ("cube", "Corrugated Metal", "pickaxe"),
    "diamond_plate": ("cube", "Diamond Plate", "pickaxe"),
    "rubble": ("cube", "Rubble", "pickaxe"),
    "ash": ("cube", "Ash", "shovel"),
    "dry_dirt": ("cube", "Dry Dirt", "shovel"),
    "dead_grass_block": ("bottom_top", "Dead Grass Block", "shovel"),
    "broken_glass": ("cube", "Broken Glass", "pickaxe"),
}

# slab id -> full block it is made from
SLABS: dict[str, str] = {
    "asphalt_slab": "asphalt",
    "cracked_asphalt_slab": "cracked_asphalt",
    "concrete_slab": "concrete",
    "sidewalk_slab": "sidewalk",
}

# Blocks whose features are distinct enough to repeat visibly in a grid get several textures,
# chosen per block position by the blockstate. Values are the generator seeds of each variant,
# picked so the peels sit in different places (no rows or columns form across a wall).
VARIANT_SEEDS: dict[str, list[int]] = {
    "peeling_plaster": [841, 541, 1041],
}


def variant_name(name: str, index: int) -> str:
    """Variant 0 keeps the plain name (item model, particles); later ones get _2, _3, ..."""
    return name if index == 0 else f"{name}_{index + 1}"


def textures() -> None:
    asphalt = M.asphalt(101)
    M.save("block/asphalt", asphalt)
    M.save("block/cracked_asphalt", M.asphalt(102, cracked=True, overgrown=True))
    M.save("block/asphalt_yellow_line", M.road_paint(M.asphalt(103), "#b9963c", 104))
    M.save("block/asphalt_white_line", M.road_paint(M.asphalt(105), "#c8c3b3", 106, width=6))
    M.save("block/sidewalk", M.sidewalk(110))
    M.save("block/concrete", M.concrete(120))
    M.save("block/cracked_concrete", M.concrete(121, "cracked"))
    M.save("block/stained_concrete", M.concrete(122, "stained"))
    M.save("block/mossy_concrete", M.concrete(123, "mossy"))
    M.save("block/weathered_bricks", M.bricks(130))
    M.save("block/sooty_bricks", M.bricks(131, soot=True))
    M.save("block/plaster_wall", M.plaster(140, "#c4b495"))
    for i, peel_seed in enumerate(VARIANT_SEEDS["peeling_plaster"]):
        M.save(f"block/{variant_name('peeling_plaster', i)}", M.plaster(141, "#94ab92", peeling=True, peel_seed=peel_seed))
    M.save("block/hospital_tiles", M.tiles(150))
    M.save("block/cracked_hospital_tiles", M.tiles(151, cracked=True))
    M.save("block/boarded_planks", M.boards(160))
    M.save("block/weathered_siding", M.boards(161, weathered="#857a69", painted="#8d9ba1", nails=False))
    M.save("block/rusted_sheet_metal", M.sheet_metal(170))
    M.save("block/corrugated_metal", M.corrugated(171))
    M.save("block/diamond_plate", M.diamond_plate(172))
    M.save("block/rubble", M.rubble(180))
    M.save("block/ash", M.ash(181))
    dirt = M.dry_dirt(182)
    M.save("block/dry_dirt", dirt)
    top = M.dead_grass_top(183)
    M.save("block/dead_grass_block_top", top)
    M.save("block/dead_grass_block_side", M.dead_grass_side(184, top, dirt))
    M.save("block/broken_glass", M.broken_glass(190))


def models_and_data() -> None:
    tags = D.TagCollector()
    lang = {}
    for name, (shape, english, tool) in BLOCKS.items():
        block = f"{NS}:block/{name}"
        if shape == "cube" and name in VARIANT_SEEDS:
            # Several textures picked per block position, so walls don't repeat in a grid.
            models = []
            for i in range(len(VARIANT_SEEDS[name])):
                vn = f"{NS}:block/{variant_name(name, i)}"
                D.asset_json(f"models/block/{variant_name(name, i)}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": vn}})
                models.append({"model": vn})
            D.asset_json(f"blockstates/{name}.json", {"variants": {"": models}})
        elif shape == "cube":
            D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": block}})
            D.asset_json(f"blockstates/{name}.json", {"variants": {"": {"model": block}}})
        elif shape == "line":
            D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": block, "bottom": f"{NS}:block/asphalt", "side": f"{NS}:block/asphalt"}})
            D.asset_json(f"blockstates/{name}.json", {"variants": {
                "axis=x": {"model": block, "y": 90},
                "axis=z": {"model": block},
            }})
        elif shape == "bottom_top":
            D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": f"{block}_top", "side": f"{block}_side", "bottom": f"{NS}:block/dry_dirt"}})
            D.asset_json(f"blockstates/{name}.json", {"variants": {"": {"model": block}}})
        D.item_model_definition(name)
        D.loot_self(name)
        tags.add(f"minecraft:mineable/{tool}", f"{NS}:{name}")
        lang[f"block.{NS}.{name}"] = english

    for slab, base in SLABS.items():
        tex = f"{NS}:block/{base}"
        D.asset_json(f"models/block/{slab}.json", {"parent": "minecraft:block/slab", "textures": {"bottom": tex, "top": tex, "side": tex}})
        D.asset_json(f"models/block/{slab}_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tex, "top": tex, "side": tex}})
        D.asset_json(f"blockstates/{slab}.json", {"variants": {
            "type=bottom": {"model": f"{NS}:block/{slab}"},
            "type=top": {"model": f"{NS}:block/{slab}_top"},
            "type=double": {"model": tex},
        }})
        D.item_model_definition(slab)
        D.loot_self(slab, slab=True)
        tags.add("minecraft:mineable/pickaxe", f"{NS}:{slab}")
        tags.add("minecraft:slabs", f"{NS}:{slab}")
        lang[f"block.{NS}.{slab}"] = BLOCKS[base][1] + " Slab"
    tags.write("blocks")
    D.merge_lang(lang)


def build() -> None:
    textures()
    models_and_data()


if __name__ == "__main__":
    build()
    print(f"{len(BLOCKS)} blocks and {len(SLABS)} slabs written")
