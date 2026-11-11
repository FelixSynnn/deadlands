"""DEADLANDS building and ground blocks: textures (+ LabPBR maps), blockstates, models, item models,
loot tables, tool tags and English names, all from one list.

The block ids here must match com.deadlands.registry.DLBlocks; the asset-completeness game test
fails the build if a registered block is missing any of these files.
"""
from __future__ import annotations

import numpy as np

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
    # Atmosphere overhaul: more of an abandoned town's surfaces.
    "dirty_concrete": ("cube", "Dirty Concrete", "pickaxe"),
    "broken_concrete": ("cube", "Broken Concrete", "pickaxe"),
    "potholed_asphalt": ("cube", "Potholed Asphalt", "pickaxe"),
    "rotting_planks": ("cube", "Rotting Planks", "axe"),
    "faded_planks": ("cube", "Faded Planks", "axe"),
    "corroded_metal": ("cube", "Corroded Metal", "pickaxe"),
    "industrial_panel": ("cube", "Industrial Panel", "pickaxe"),
    "dirty_glass": ("cube", "Dirty Window", "pickaxe"),
    "cracked_glass": ("cube", "Cracked Glass", "pickaxe"),
    "dusty_glass": ("cube", "Dusty Glass", "pickaxe"),
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
    overhaul_textures()


# ----------------------------------------------------------------------------- atmosphere overhaul

def _n(seed, beta=1.8):
    return M.N.spectral(M.T, M.T, beta=beta, seed=seed)


def _hex(h):
    return np.array(M.N.hex_rgb(h), float)


def _spider_cracks(seed: int, centre=(10.5, 20.5)) -> np.ndarray:
    """A stone's impact in a pane: cracks radiating from one point, joined by broken rings.

    Drawn as anti-aliased lines one texel wide (1 = crack): a noise-based crack mask is thinner
    than a texel at 32x32 and breaks up into specks.
    """
    T = M.T
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:T, 0:T].astype(float) + 0.5  # texel centres
    out = np.zeros((T, T))

    def line(x0, y0, x1, y1, strength):
        dx, dy = x1 - x0, y1 - y0
        t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / max(dx * dx + dy * dy, 1e-9), 0, 1)
        d = np.hypot(xx - (x0 + t * dx), yy - (y0 + t * dy))
        np.maximum(out, np.clip(1.0 - d / 0.85, 0, 1) * strength, out=out)

    def inside(x, y):
        return 1.0 <= x < T - 1.0 and 1.0 <= y < T - 1.0  # stop at the frame

    def walk(x, y, angle, length, strength, wander=0.18):
        points = [(x, y)]
        travelled = 0.0
        while travelled < length:
            angle += rng.normal(0.0, wander)
            nx, ny = x + np.cos(angle) * 1.6, y + np.sin(angle) * 1.6
            if not inside(nx, ny):
                break
            line(x, y, nx, ny, strength * (1.0 - 0.35 * travelled / length))
            x, y = nx, ny
            travelled += 1.6
            points.append((x, y))
        return points

    cx, cy = centre
    spokes = 7
    turn = rng.uniform(0, 2 * np.pi)
    angles = turn + (np.arange(spokes) + rng.uniform(-0.3, 0.3, spokes)) * 2 * np.pi / spokes
    paths = []
    for a in angles:
        path = walk(cx, cy, a, rng.uniform(9.0, 26.0), 1.0)
        paths.append(path)
        if len(path) > 5 and rng.random() < 0.55:  # a side crack splits off part way
            bx, by = path[int(len(path) * rng.uniform(0.4, 0.7))]
            walk(bx, by, a + rng.choice((-1, 1)) * rng.uniform(0.5, 0.9), rng.uniform(3.0, 7.0), 0.7, 0.25)
    for radius, chance in ((3.2, 0.8), (7.5, 0.6)):  # the rings that join neighbouring spokes
        for i in range(spokes):
            p, q = paths[i], paths[(i + 1) % spokes]
            k = int(round(radius / 1.6))
            if len(p) <= k or len(q) <= k or rng.random() > chance:
                continue
            (x0, y0), (x1, y1) = p[k], q[k]
            mx, my = (x0 + x1) / 2, (y0 + y1) / 2
            bow = 1.0 + 0.12 * rng.uniform(0.5, 1.5)  # bowed outward a little, as rings are
            mx, my = cx + (mx - cx) * bow, cy + (my - cy) * bow
            line(x0, y0, mx, my, 0.75)
            line(mx, my, x1, y1, 0.75)
    # Crushed glass where it was struck.
    out = np.maximum(out, np.clip(1.6 - np.hypot(xx - cx, yy - cy), 0, 1))
    return out


def overhaul_textures() -> None:
    T = M.T
    yy, xx = np.mgrid[0:T, 0:T].astype(float)

    # Dirty concrete: road spray and soil climbing from the bottom, grime in the pores.
    m = M.concrete(124, "stained")
    splash = M.N.smoothstep(0.35, 0.95, _n(1241) * 0.6 + (yy / T) * 0.6)
    m.rgb = m.rgb * (0.86 + 0.14 * _n(1242, 1.0))[..., None]
    m.rgb = m.rgb * (1 - splash[..., None] * 0.45) + _hex("#4a4034") * (splash[..., None] * 0.45)
    M.save("block/dirty_concrete", m)

    # Broken concrete: chunks gone to the rebar, cracks running off the breaks.
    m = M.concrete(125, "cracked")
    hole = M.N.smoothstep(0.66, 0.72, _n(1251, 2.4))
    rim = np.clip(M.N.smoothstep(0.6, 0.66, _n(1251, 2.4)) - hole, 0, 1)
    m.rgb = m.rgb * (1 - hole[..., None] * 0.75) + _hex("#26221e") * (hole[..., None] * 0.75)
    m.rgb = m.rgb * (1 + rim[..., None] * 0.18)
    rebar = (((np.abs(yy - 9) < 0.8) | (np.abs(xx - 21) < 0.8)) & (hole > 0.3))
    m.rgb[rebar] = _hex("#7a4224")
    m.height = m.height * (1 - hole * 0.6)
    M.save("block/broken_concrete", m)

    # Potholed asphalt: a hole worn through to the gravel, with rain still standing in it.
    m = M.asphalt(107, cracked=True)
    r = np.hypot((xx - 15.5) / 1.15, (yy - 16.5))
    edge_n = _n(1071, 1.6) * 3.0
    pit = M.N.smoothstep(9.5 + edge_n, 7.5 + edge_n, r)
    gravel = (_n(1072, 0.4) > 0.55)
    inside = m.rgb * 0.55 + _hex("#5e5a52") * 0.45 * gravel[..., None]
    m.rgb = m.rgb * (1 - pit[..., None]) + inside * pit[..., None]
    water = M.N.smoothstep(5.5, 4.0, r + _n(1073, 1.4) * 2.0)
    m.rgb = m.rgb * (1 - water[..., None] * 0.7) + _hex("#3a4044") * (water[..., None] * 0.7)
    m.height = m.height - pit * 0.35
    m.smooth = np.where(water > 0.5, 0.85, 0.12)
    M.save("block/potholed_asphalt", m)

    # Rotting planks: soft dark rot spreading along the grain, a little moss.
    m = M.boards(162, weathered="#5e5243", dark="#2c241c", nails=True)
    rot = M.N.smoothstep(0.55, 0.8, _n(1621, 2.2) * 0.7 + _n(1622, 1.0) * 0.3)
    m.rgb = m.rgb * (1 - rot[..., None] * 0.6) + _hex("#22231a") * (rot[..., None] * 0.6)
    moss = (_n(1623, 0.6) > 0.7) & (rot > 0.3)
    m.rgb[moss] = m.rgb[moss] * 0.5 + _hex("#4a5a32") * 0.5
    M.save("block/rotting_planks", m)

    # Faded planks: bleached grey by sun and rain.
    m = M.boards(163, weathered="#9a9384", dark="#5e584c", nails=True)
    m.rgb = m.rgb * 0.7 + m.rgb.mean(axis=2, keepdims=True) * 0.3
    M.save("block/faded_planks", m)

    # Corroded metal: white-green bloom over the rust, streaks run down from the seams.
    m = M.sheet_metal(173, rust=0.75)
    bloom = M.N.smoothstep(0.6, 0.8, _n(1731, 2.0))
    m.rgb = m.rgb * (1 - bloom[..., None] * 0.55) + _hex("#8c9a84") * (bloom[..., None] * 0.55)
    streak = np.maximum.accumulate((_n(1732, 0.8) > 0.72).astype(float), axis=0) * np.linspace(0.2, 1.0, T)[:, None]
    m.rgb = m.rgb * (1 - streak[..., None] * 0.25) + _hex("#6a3a1e") * (streak[..., None] * 0.25)
    # Old rust is brown, not orange: calm it to the palette of the other weathered metals.
    m.rgb = (m.rgb * 0.7 + m.rgb.mean(axis=2, keepdims=True) * 0.3) * 0.94
    M.save("block/corroded_metal", m)

    # Industrial panel: painted steel plate, seams, rivets, the paint chipped to bare and rusted metal.
    m = M.sheet_metal(174, rust=0.25)
    paint = _hex("#5a6458") * (0.9 + 0.15 * _n(1741, 1.2))[..., None]
    chip = M.N.smoothstep(0.7, 0.74, _n(1742, 1.6))
    m.rgb = paint * (1 - chip[..., None]) + m.rgb * chip[..., None]
    seam = (xx < 1) | (yy < 1) | (xx > T - 2) | (yy > T - 2)
    m.rgb[seam] = m.rgb[seam] * 0.55
    for cx in (3, 16, 28):
        for cy in (3, 28):
            m.rgb[cy, cx] = _hex("#9a9e96")
            m.rgb[cy + 1, cx] = _hex("#2e302c")
    m.height = np.where(seam, m.height * 0.6, m.height)
    M.save("block/industrial_panel", m)

    # Glass: a dirty window, cracked glass, and glass thick with dust.
    edge = np.minimum(np.minimum(xx, T - 1 - xx), np.minimum(yy, T - 1 - yy))
    frame = edge < 1
    for name, tint, dust, cracks_on, seed in (("dirty_glass", "#9aa8a6", 0.45, False, 1901), ("cracked_glass", "#a6b8bc", 0.2, True, 1902),
                                              ("dusty_glass", "#b8b09c", 0.8, False, 1903)):
        grime = M.N.smoothstep(0.35, 0.95, _n(seed, 2.0))
        runs = np.maximum.accumulate((_n(seed + 1, 0.7) > 0.8).astype(float), axis=0) * 0.5
        settle = M.N.smoothstep(T * 0.4, T, yy) * dust  # dust gathers low
        amount = np.clip(grime * 0.5 + runs + settle + M.N.smoothstep(3, 0, edge) * 0.6, 0, 1)
        rgb = M.N.lerp(M.N.solid(T, T, tint), M.N.solid(T, T, "#6e6656"), amount * 0.7)
        alpha = np.clip(0.25 + dust * 0.45 + amount * 0.45, 0, 0.97)
        if name == "dusty_glass":  # a hand wiped a clear streak through it once
            wipe = np.abs((yy - 18) - (xx - 16) * 0.25) < 2.5
            alpha = np.where(wipe, alpha * 0.45, alpha)
        crack = np.zeros((T, T))
        if cracks_on:
            # Bright where the broken faces catch the light, with a faint shadow beside each line
            # so the cracks still read against a bright sky.
            crack = _spider_cracks(seed + 2)
            shadow = np.roll(crack, (1, 1), axis=(0, 1)) * (1 - crack)
            rgb = M.N.lerp(rgb, M.N.solid(T, T, "#3c4242"), shadow * 0.35)
            rgb = M.N.lerp(rgb, M.N.solid(T, T, "#e4ebea"), crack * 0.82)
            alpha = np.clip(alpha + crack * 0.55 + shadow * 0.15, 0, 1)
        rgb[frame] = _hex("#4a4c4a")
        alpha[frame] = 1.0
        M.save(f"block/{name}", M.Material(rgb, 0.5 + 0.3 * crack, smooth=0.9, porosity=0.0, alpha=alpha))


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
