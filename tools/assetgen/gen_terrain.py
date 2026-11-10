"""Milestone 3 terrain: the dead-vegetation blocks the DEADLANDS biomes grow (dead and charred
logs, dead leaves, cracked earth) and DEADLANDS restyles of the vanilla plants and ground those
biomes reuse (dry grass, dead bush, bush, leaf litter, vines, reeds, moss, mud, snow, ice...).

Tinted vanilla textures (bush, leaf litter, vine, lily pad, sugar cane) stay greyscale: the game
multiplies them by the biome's grass or foliage colour, and the DEADLANDS biomes define drab ones.
"""
from __future__ import annotations


import numpy as np
from scipy import ndimage

import dl_data as D
import dl_materials as M
import dl_noise as N
from gen_vanilla import grey, save as save_vanilla, soil

NS = D.NS
T = M.T


# ----------------------------------------------------------------------------- DEADLANDS blocks

def dead_bark(seed: int) -> M.Material:
    """Bleached, sun-split bark of a long-dead tree."""
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    ridges = N.spectral(T, T, beta=1.5, seed=seed, aniso=(8.0, 1.0))
    furrow = np.sin(xx / T * 2 * np.pi * 4 + ridges * 3.5) * 0.5 + 0.5
    rgb = N.ramp(0.45 * ridges + 0.55 * furrow, [(0.0, "#4f4b45"), (0.55, "#7f796e"), (1.0, "#a7a093")])
    splits = N.spectral(T, T, beta=1.1, seed=seed + 5, aniso=(12.0, 1.0)) > 0.8
    rgb[splits] = rgb[splits] * 0.4
    peeled = N.smoothstep(0.72, 0.8, N.spectral(T, T, beta=2.2, seed=seed + 6, aniso=(3.0, 1.0)))
    rgb = N.lerp(rgb, N.solid(T, T, "#b9b1a0") * (0.9 + 0.1 * ridges)[..., None], peeled * 0.8)
    lichen = N.smoothstep(0.78, 0.92, N.spectral(T, T, beta=2.2, seed=seed + 2))
    rgb = N.lerp(rgb, N.solid(T, T, "#7d8462"), lichen * 0.45)
    height = 0.3 + 0.6 * furrow - 0.4 * splits + 0.15 * peeled
    return M.relief(M.Material(np.clip(rgb, 0, 255), height, smooth=0.06, porosity=0.6), 1.8)


def charred_bark(seed: int) -> M.Material:
    """Fire-blackened 'alligator' bark: charcoal blocks split by deep cracks, a few embers' stains."""
    d = N.worley(T, T, 24, seed=seed, k=2)
    edge = d[..., 1] - d[..., 0]
    tone = N.spectral(T, T, beta=1.5, seed=seed + 1)
    rgb = N.ramp(tone, [(0.0, "#0e0d0c"), (0.6, "#1f1c1a"), (1.0, "#302b28")])
    crack = edge < 0.075
    sheen = (1.0 - np.clip(d[..., 0] * 3.5, 0, 1)) * 0.35
    rgb = rgb * (1.0 + sheen)[..., None]
    rgb[crack] = N.hex_rgb("#050404")
    ash = N.smoothstep(0.7, 0.85, N.spectral(T, T, beta=2.0, seed=seed + 3)) * ~crack
    rgb = N.lerp(rgb, N.solid(T, T, "#6a6662"), ash * 0.35)
    embers = crack & (N.rng(seed + 2).random((T, T)) > 0.982)
    rgb[embers] = N.hex_rgb("#5e2810")
    height = np.clip(edge * 3.0, 0, 1)
    return M.relief(M.Material(np.clip(rgb, 0, 255), height, smooth=0.3, porosity=0.4), 2.0, ambient=0.75)


def end_grain(seed: int, inner: str, outer: str, rim: str, charred: bool = False) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    d = np.hypot(xx - 15.5, yy - 15.5)
    wob = N.spectral(T, T, beta=2.0, seed=seed)
    rings = np.sin((d + wob * 3) * 1.6) * 0.5 + 0.5
    rgb = N.ramp(rings, [(0.0, inner), (1.0, outer)])
    angle = np.arctan2(yy - 15.5, xx - 15.5)
    r = N.rng(seed + 1)
    for a in r.uniform(-np.pi, np.pi, 4):  # radial drying cracks
        diff = np.abs((angle - a + np.pi) % (2 * np.pi) - np.pi)
        rgb[(diff < 0.06) & (d < r.uniform(8, 13))] *= 0.35
    bark_ring = d > 13.5
    rgb[bark_ring] = N.solid(T, T, rim)[bark_ring] * (0.8 + 0.2 * wob[bark_ring])[..., None]
    if charred:
        ash_ring = (d > 11.5) & (d <= 13.5)
        rgb[ash_ring] = N.solid(T, T, "#5c5753")[ash_ring] * (0.85 + 0.3 * wob[ash_ring])[..., None]
    return M.relief(M.Material(np.clip(rgb, 0, 255), 0.5 + 0.1 * rings, smooth=0.08, porosity=0.5), 1.0)


def dead_leaves(seed: int) -> M.Material:
    """Sparse, curled brown leaves that cling to dead branches (untinted, cut-out holes)."""
    cells = N.worley(T, T, 40, seed=seed)
    tone = N.spectral(T, T, beta=1.2, seed=seed + 1)
    v = np.clip(0.3 + 0.5 * tone + 0.3 * (1 - cells), 0, 1)
    rgb = N.ramp(v, [(0.0, "#3a2a1b"), (0.45, "#664a30"), (0.8, "#86663f"), (1.0, "#a08456")])
    grey_leaves = N.rng(seed + 3).random((T, T)) > 0.86
    rgb[grey_leaves] = rgb[grey_leaves] * 0.5 + np.array(N.hex_rgb("#77706a")) * 0.5
    alpha = (N.spectral(T, T, beta=1.0, seed=seed + 2) > 0.56).astype(float)
    m = M.Material(rgb, 0.5 + 0.3 * (1 - cells), smooth=0.05, porosity=0.5, alpha=alpha)
    return M.relief(m, 1.2)


def cracked_earth(seed: int) -> M.Material:
    """Sun-baked mud split into curling plates."""
    e = M.worley_edges(T, T, 9, seed)
    wx = (N.spectral(T, T, beta=2.5, seed=seed + 1) - 0.5) * 2.0
    wy = (N.spectral(T, T, beta=2.5, seed=seed + 2) - 0.5) * 2.0
    e = N.warp(e, wx, wy)
    tone = N.spectral(T, T, beta=1.8, seed=seed + 3)
    rgb = N.ramp(tone, [(0.0, "#75573a"), (0.5, "#8f6b48"), (1.0, "#a47f58")])
    # Thresholds by share of the tile, so the cracks always form a connected network of plates.
    crack = e < np.quantile(e, 0.13)
    rim = (e < np.quantile(e, 0.27)) & ~crack
    rgb[rim] = np.clip(rgb[rim] * 1.12, 0, 255)
    rgb[crack] = N.hex_rgb("#33251a")
    dust = N.smoothstep(0.6, 0.9, N.spectral(T, T, beta=2.2, seed=seed + 4))
    rgb = N.lerp(rgb, N.solid(T, T, "#b39a78"), dust * 0.35)
    height = np.clip(e * 6.0, 0, 1) * 0.6 + 0.3 * rim - 0.5 * crack
    return M.relief(M.Material(np.clip(rgb, 0, 255), height, smooth=0.05, porosity=0.75), 1.6)


def block_assets() -> None:
    M.save("block/dead_log", dead_bark(3101))
    M.save("block/dead_log_top", end_grain(3102, "#5f584c", "#8c8372", "#5d5850"))
    M.save("block/charred_log", charred_bark(3103))
    M.save("block/charred_log_top", end_grain(3104, "#141211", "#2c2623", "#0f0e0d", charred=True))
    M.save("block/dead_leaves", dead_leaves(3105))
    M.save("block/cracked_earth", cracked_earth(3106))

    tags = D.TagCollector()
    lang = {}
    for log, english in (("dead_log", "Dead Log"), ("charred_log", "Charred Log")):
        block = f"{NS}:block/{log}"
        D.asset_json(f"models/block/{log}.json", {"parent": "minecraft:block/cube_column",
                                                   "textures": {"end": f"{block}_top", "side": block}})
        D.asset_json(f"models/block/{log}_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
                                                              "textures": {"end": f"{block}_top", "side": block}})
        D.asset_json(f"blockstates/{log}.json", {"variants": {
            "axis=x": {"model": f"{block}_horizontal", "x": 90, "y": 90},
            "axis=y": {"model": block},
            "axis=z": {"model": f"{block}_horizontal", "x": 90},
        }})
        D.item_model_definition(log)
        D.loot_self(log)
        for tag in ("minecraft:logs", "minecraft:overworld_natural_logs", "minecraft:mineable/axe"):
            tags.add(tag, f"{NS}:{log}")
        lang[f"block.{NS}.{log}"] = english
    tags.add("minecraft:logs_that_burn", f"{NS}:dead_log")

    D.asset_json("models/block/dead_leaves.json", {"parent": "minecraft:block/leaves", "textures": {"all": f"{NS}:block/dead_leaves"}})
    D.asset_json("blockstates/dead_leaves.json", {"variants": {"": {"model": f"{NS}:block/dead_leaves"}}})
    D.item_model_definition("dead_leaves")
    D.data_json("loot_table/blocks/dead_leaves.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:dead_leaves",
             "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}]},
            {"type": "minecraft:item", "name": "minecraft:stick",
             "conditions": [{"condition": "minecraft:survives_explosion"},
                            {"condition": "minecraft:random_chance", "chance": 0.08}],
             "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}}]},
        ]}]}],
        "random_sequence": f"{NS}:blocks/dead_leaves",
    })
    tags.add("minecraft:leaves", f"{NS}:dead_leaves")
    tags.add("minecraft:mineable/hoe", f"{NS}:dead_leaves")
    lang[f"block.{NS}.dead_leaves"] = "Dead Leaves"

    D.asset_json("models/block/cracked_earth.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/cracked_earth"}})
    D.asset_json("blockstates/cracked_earth.json", {"variants": {"": {"model": f"{NS}:block/cracked_earth"}}})
    D.item_model_definition("cracked_earth")
    D.loot_self("cracked_earth")
    tags.add("minecraft:mineable/shovel", f"{NS}:cracked_earth")
    lang[f"block.{NS}.cracked_earth"] = "Cracked Earth"

    # Dry and dead soils count as dirt (saplings, grass, crops) and carry dry vegetation.
    for soil_block in ("dead_grass_block", "dry_dirt", "cracked_earth"):
        tags.add("minecraft:dirt", f"{NS}:{soil_block}")
    for dry in ("dead_grass_block", "dry_dirt", "cracked_earth", "ash"):
        tags.add("minecraft:dry_vegetation_may_place_on", f"{NS}:{dry}")
    tags.write("terrain")
    D.merge_lang(lang)


# ----------------------------------------------------------------------------- vanilla plants and ground

def stalks(seed: int, count: int, top_range: tuple[float, float], lean: float, colour_lo: str, colour_hi: str,
           heads: bool = False) -> tuple[np.ndarray, np.ndarray]:
    """Cross-plant blades rising from the bottom edge (coloured), with optional seed heads."""
    r = N.rng(seed)
    alpha = np.zeros((T, T))
    v = np.zeros((T, T))
    for _ in range(count):
        x0 = r.uniform(2, T - 2)
        top = r.uniform(*top_range)
        tilt = r.uniform(-lean, lean)
        for y in range(int(max(0, top)), T):
            x = x0 + tilt * (y - top) * 0.6
            ix = int(round(x)) % T
            alpha[y, ix] = 1.0
            v[y, ix] = 0.35 + 0.6 * (1 - (y - top) / max(1.0, T - top))
        if heads and top > 1:
            ty = int(top)
            for dy in range(-3, 1):
                ix = int(round(x0)) % T
                if 0 <= ty + dy < T:
                    alpha[ty + dy, ix] = 1.0
                    v[ty + dy, ix] = 0.95
                    if dy > -3:
                        alpha[ty + dy, (ix + 1) % T] = 1.0
                        v[ty + dy, (ix + 1) % T] = 0.8
    rgb = N.ramp(v, [(0.0, colour_lo), (1.0, colour_hi)])
    return rgb, alpha


def twigs(seed: int) -> tuple[np.ndarray, np.ndarray]:
    """A dead bush: a few branching grey-brown twigs from one base."""
    r = N.rng(seed)
    alpha = np.zeros((T, T))
    rgb = np.zeros((T, T, 3))

    def branch(x, y, angle, length, depth):
        for _ in range(int(length)):
            ix, iy = int(round(x)) % T, int(round(y))
            if 0 <= iy < T:
                colour = np.array(N.hex_rgb("#43372b" if depth == 0 else "#5e4f3e" if depth == 1 else "#7a6a55"))
                alpha[iy, ix] = 1.0
                rgb[iy, ix] = colour * r.uniform(0.85, 1.1)
                if depth == 0:  # thicker main stems
                    alpha[iy, (ix + 1) % T] = 1.0
                    rgb[iy, (ix + 1) % T] = colour * 0.8
            x += np.cos(angle) + r.uniform(-0.25, 0.25)
            y -= np.sin(angle)
        if depth < 4:
            for spread in (-0.6, 0.55):
                if r.random() < 0.9:
                    branch(x, y, angle + spread + r.uniform(-0.25, 0.25), length * r.uniform(0.55, 0.75), depth + 1)

    for start in (-0.6, -0.25, 0.05, 0.3, 0.65):
        branch(15.5 + start * 5, 31, np.pi / 2 + start, r.uniform(9, 14), 0)
    return np.clip(rgb, 0, 255), alpha


def leafy(seed: int, holes: float, silhouette) -> tuple[np.ndarray, np.ndarray]:
    """Greyscale leaf clusters (the game tints them) clipped to a silhouette function."""
    cells = N.worley(T, T, 34, seed=seed)
    tone = N.spectral(T, T, beta=1.2, seed=seed + 1)
    v = np.clip(0.3 + 0.5 * tone + 0.3 * (1 - cells), 0, 1)
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    alpha = ((N.spectral(T, T, beta=1.0, seed=seed + 2) > holes) & silhouette(xx, yy)).astype(float)
    return grey(v, 45, 200), alpha


def scattered_leaves(seed: int) -> tuple[np.ndarray, np.ndarray]:
    """Flat leaf litter: individual leaf shapes on a transparent ground (tinted)."""
    r = N.rng(seed)
    alpha = np.zeros((T, T))
    v = np.zeros((T, T))
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    for _ in range(26):
        cx, cy = r.uniform(0, T), r.uniform(0, T)
        a, b = r.uniform(1.6, 2.8), r.uniform(1.0, 1.6)
        th = r.uniform(0, np.pi)
        dx = (xx - cx + T / 2) % T - T / 2
        dy = (yy - cy + T / 2) % T - T / 2
        u = dx * np.cos(th) + dy * np.sin(th)
        w = -dx * np.sin(th) + dy * np.cos(th)
        inside = (u / a) ** 2 + (w / b) ** 2 <= 1.0
        alpha[inside] = 1.0
        v[inside] = r.uniform(0.35, 0.95) - 0.15 * (np.abs(w[inside]) < 0.4)
    return grey(np.clip(v, 0, 1), 50, 210), alpha


def hanging_vines(seed: int) -> tuple[np.ndarray, np.ndarray]:
    r = N.rng(seed)
    alpha = np.zeros((T, T))
    v = np.zeros((T, T))
    for _ in range(9):
        x = r.uniform(0, T)
        length = r.uniform(12, T)
        for y in range(int(length)):
            x += r.uniform(-0.6, 0.6)
            ix = int(round(x)) % T
            alpha[y, ix] = 1.0
            v[y, ix] = r.uniform(0.4, 0.8)
            if r.random() < 0.3:  # small leaves along the strand
                for k in (-1, 1):
                    alpha[y, (ix + k) % T] = 1.0
                    v[y, (ix + k) % T] = r.uniform(0.5, 0.95)
    return grey(v, 40, 200), alpha


def lily_pad(seed: int) -> tuple[np.ndarray, np.ndarray]:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    d = np.hypot(xx - 15.5, yy - 15.5)
    angle = np.arctan2(yy - 15.5, xx - 15.5)
    notch = (np.abs(angle - 0.4) < 0.12) & (d > 3)
    alpha = ((d < 13.5) & ~notch).astype(float)
    veins = (np.abs(np.sin(angle * 7)) < 0.12) & (d > 2)
    v = 0.55 + 0.25 * N.spectral(T, T, beta=1.5, seed=seed) - 0.2 * veins
    edge = (d > 12.0) & (alpha > 0)
    v[edge] *= 0.7
    return grey(np.clip(v, 0, 1), 50, 210), alpha


def reeds(seed: int) -> tuple[np.ndarray, np.ndarray]:
    """Segmented reed stalks for sugar cane (light grey: tinted by the biome grass colour)."""
    alpha = np.zeros((T, T))
    v = np.zeros((T, T))
    r = N.rng(seed)
    for x0 in (5, 13, 22, 28):
        x0 += r.integers(-1, 2)
        for y in range(T):
            for dx in (0, 1):
                alpha[y, (x0 + dx) % T] = 1.0
                v[y, (x0 + dx) % T] = 0.75 - 0.15 * dx
            if (y + x0) % 9 == 0:  # nodes
                v[y, x0 % T] = v[y, (x0 + 1) % T] = 0.45
        for y in range(r.integers(2, 10), T, r.integers(9, 14)):  # leaf blades peeling off
            for k in range(4):
                xx_ = (x0 + 2 + k) % T
                yy_ = y + k // 2
                if yy_ < T:
                    alpha[yy_, xx_] = 1.0
                    v[yy_, xx_] = 0.65
    return grey(v, 60, 220), alpha


def fringe_side(top_rgb: np.ndarray, dirt_rgb: np.ndarray, seed: int, depth: tuple[int, int]) -> np.ndarray:
    """A side texture: dirt with a ragged fringe of the top material along its upper edge."""
    yy = np.mgrid[0:T, 0:T][0]
    r = N.rng(seed)
    fringe = ndimage.uniform_filter1d(depth[0] + r.integers(0, depth[1], T).astype(float), 3, mode="wrap")
    rows = yy < fringe[None, :]
    return np.where(rows[..., None], top_rgb, dirt_rgb)


def vanilla_restyle() -> None:
    rgb, a = stalks(3201, 16, (6, 20), 0.35, "#6e5f3a", "#b9a46c")
    save_vanilla("short_dry_grass", rgb, a)
    rgb, a = stalks(3202, 18, (0, 12), 0.3, "#665636", "#c2ad74", heads=True)
    save_vanilla("tall_dry_grass", rgb, a)
    save_vanilla("dead_bush", *twigs(3203))
    save_vanilla("bush", *leafy(3204, 0.42, lambda x, y: (np.hypot(x - 15.5, (y - 22) * 1.3) < 14) & (y > 6)))
    save_vanilla("leaf_litter", *scattered_leaves(3205))
    save_vanilla("vine", *hanging_vines(3206))
    save_vanilla("lily_pad", *lily_pad(3207))
    save_vanilla("sugar_cane", *reeds(3208))

    moss = N.ramp(0.6 * N.spectral(T, T, beta=1.6, seed=3209) + 0.4 * N.spectral(T, T, beta=0.7, seed=3210),
                  [(0.0, "#2f3620"), (0.5, "#454f2c"), (1.0, "#5d6a3a")])
    moss_m = M.relief(M.Material(moss, 0.5 + 0.3 * N.spectral(T, T, beta=1.0, seed=3211)), 1.4)
    save_vanilla("moss_block", moss_m.rgb)

    wet = N.spectral(T, T, beta=2.2, seed=3212)
    mud = N.ramp(0.7 * wet + 0.3 * N.spectral(T, T, beta=0.8, seed=3213), [(0.0, "#241e19"), (0.5, "#332a22"), (1.0, "#43382d")])
    sheen = N.smoothstep(0.7, 0.85, wet)
    mud = N.lerp(mud, N.solid(T, T, "#5e5650"), sheen * 0.4)
    save_vanilla("mud", M.relief(M.Material(mud, 0.5 + 0.2 * wet), 1.0, ambient=0.85).rgb)

    flake = N.spectral(T, T, beta=1.4, seed=3214)
    snow = N.ramp(flake, [(0.0, "#b9bcbd"), (0.6, "#d3d5d4"), (1.0, "#e4e5e2")])
    soot = N.rng(3215).random((T, T)) > 0.985
    snow[soot] = snow[soot] * 0.6 + np.array(N.hex_rgb("#4a4744")) * 0.4
    grime = N.smoothstep(0.65, 0.9, N.spectral(T, T, beta=2.4, seed=3216))
    snow = N.lerp(snow, N.solid(T, T, "#a6a29a"), grime * 0.35)
    save_vanilla("snow", M.relief(M.Material(snow, 0.5 + 0.15 * flake), 0.8, ambient=0.9).rgb)

    cr = M.crack_mask(T, T, 3217, density=5, width=0.03, warp=1.5)
    cloud = N.spectral(T, T, beta=2.0, seed=3218)
    ice = N.ramp(cloud, [(0.0, "#6f8590"), (1.0, "#9fb3bb")])
    ice = N.lerp(ice, N.solid(T, T, "#d6e0e2"), cr * 0.7)
    save_vanilla("ice", ice, np.full((T, T), 0.78))
    packed = N.ramp(cloud, [(0.0, "#62757f"), (1.0, "#8a9da6")])
    packed = N.lerp(packed, N.solid(T, T, "#c9d4d7"), cr * 0.6)
    save_vanilla("packed_ice", packed)

    dirt = soil(9102).rgb  # the restyled dirt, so side textures match
    needles = N.ramp(N.spectral(T, T, beta=0.9, seed=3219, aniso=(1.0, 3.0)), [(0.0, "#3b2b1c"), (0.5, "#5c4128"), (1.0, "#7a5734")])
    save_vanilla("podzol_top", M.relief(M.Material(needles, 0.5 + 0.2 * N.spectral(T, T, beta=0.8, seed=3220)), 1.3).rgb)
    save_vanilla("podzol_side", fringe_side(needles, dirt, 3221, (3, 4)))
    rooted = dirt.copy()
    roots = N.spectral(T, T, beta=1.0, seed=3222, aniso=(1.0, 6.0)) > 0.8
    rooted[roots] = N.hex_rgb("#a08a6a")
    save_vanilla("rooted_dirt", rooted)
    snow_top = N.ramp(flake, [(0.0, "#c3c6c6"), (1.0, "#e2e3e0")])
    save_vanilla("grass_block_snow", fringe_side(snow_top, dirt, 3223, (5, 4)))
    path = N.ramp(N.spectral(T, T, beta=1.8, seed=3224), [(0.0, "#5a4a35"), (1.0, "#7a6648")])
    save_vanilla("dirt_path_top", M.relief(M.Material(path, 0.5 + 0.1 * N.spectral(T, T, beta=0.8, seed=3225)), 1.0).rgb)
    save_vanilla("dirt_path_side", fringe_side(path, dirt, 3226, (2, 2)))
    yy = np.mgrid[0:T, 0:T][0]
    furrows = ((yy % 8) < 2)
    farm = dirt.copy()
    farm[furrows] *= 0.65
    save_vanilla("farmland", farm)
    save_vanilla("farmland_moist", farm * 0.72)

    straw = N.spectral(T, T, beta=1.0, seed=3227, aniso=(1.0, 6.0))
    hay = N.ramp(straw, [(0.0, "#5e5232"), (0.5, "#857447"), (1.0, "#a39160")])
    rot = N.smoothstep(0.6, 0.85, N.spectral(T, T, beta=2.2, seed=3228))
    hay = N.lerp(hay, N.solid(T, T, "#4a4a3e"), rot * 0.55)
    bands = (yy % 16) < 2
    hay[bands] = hay[bands] * 0.5 + np.array(N.hex_rgb("#3c3a34")) * 0.5
    save_vanilla("hay_block_side", hay)
    save_vanilla("hay_block_top", N.lerp(hay, N.solid(T, T, "#6e6143"), 0.3))

    red = N.ramp(0.7 * N.spectral(T, T, beta=2.4, seed=3229, aniso=(1.0, 4.0)) + 0.3 * N.rng(3230).random((T, T)),
                 [(0.0, "#7a4a30"), (0.5, "#8f5a3b"), (1.0, "#a36d4a")])
    save_vanilla("red_sand", red)

    # Mossy cobblestone: the restyled cobblestone with real moss in its joints.
    import gen_vanilla
    cob = gen_vanilla.cobble(9106).rgb
    moss_mask = N.smoothstep(0.45, 0.7, N.spectral(T, T, beta=2.0, seed=3231))
    save_vanilla("mossy_cobblestone", N.lerp(cob, moss, moss_mask * 0.75))


def build() -> None:
    block_assets()
    vanilla_restyle()


if __name__ == "__main__":
    build()
    print("terrain written")
