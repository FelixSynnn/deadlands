"""Environmental decals: dried blood, scorch marks and survivor graffiti (64x64, translucent).

Kept atmospheric rather than graphic: everything reads as old, dried and faded; the point is
that something happened here. Each decal gets floor, wall and ceiling models; floor and
ceiling variants are turned by a random quarter turn chosen at placement.
"""
from __future__ import annotations

import math

import numpy as np
from scipy import ndimage

import dl_data as D
import dl_noise as N

NS = D.NS
S = 64

DECALS = {
    "dried_blood_spots": "Dried Blood Spots",
    "dried_blood_pool": "Dried Blood Pool",
    "blood_smear": "Blood Smear",
    "bloody_handprint": "Bloody Handprint",
    "drag_marks": "Drag Marks",
    "faded_stain": "Faded Stain",
    "scorch_mark": "Scorch Mark",
    "survivor_mark": "Survivor Mark",
    "evacuation_notice": "Evacuation Notice",
    "scattered_papers": "Scattered Papers",
    "biomass_veins": "Biomass Veins",
}

DRIED = [(0.0, "#2a0b08"), (0.5, "#4a1510"), (1.0, "#5e2418")]


def _grid():
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    return yy, xx


def _dried_colour(seed: int) -> np.ndarray:
    return N.ramp(N.spectral(S, S, beta=1.6, seed=seed), DRIED)


def _edge_fade(alpha: np.ndarray, margin: float = 3.0) -> np.ndarray:
    yy, xx = _grid()
    edge = np.minimum(np.minimum(xx, S - 1 - xx), np.minimum(yy, S - 1 - yy))
    return alpha * N.smoothstep(0, margin, edge)


def spots(seed: int = 8101):
    r = N.rng(seed)
    yy, xx = _grid()
    a = np.zeros((S, S))
    for _ in range(22):
        cx, cy = r.uniform(6, S - 6), r.uniform(6, S - 6)
        rad = r.gamma(2.0, 1.1) + 0.6
        d = np.hypot(xx - cx, yy - cy)
        a = np.maximum(a, np.clip(rad + 0.5 - d, 0, 1) * r.uniform(0.6, 0.95))
        for _ in range(int(r.integers(0, 4))):
            ang = r.uniform(0, 2 * math.pi)
            dist = rad + r.uniform(1, 4)
            sx, sy = cx + math.cos(ang) * dist, cy + math.sin(ang) * dist
            a = np.maximum(a, np.clip(0.9 - np.hypot(xx - sx, yy - sy), 0, 1) * 0.8)
    return _dried_colour(seed + 1), _edge_fade(a)


def pool(seed: int = 8201):
    yy, xx = _grid()
    d = np.hypot((xx - 32) / 24, (yy - 34) / 19)
    blob = N.spectral(S, S, beta=2.4, seed=seed)
    shape = (1.0 - d) + 0.45 * (blob - 0.5)
    a = N.smoothstep(0.02, 0.12, shape)
    rim = np.clip(1.0 - np.abs(shape - 0.06) / 0.06, 0, 1)
    col = N.lerp(_dried_colour(seed + 1), N.solid(S, S, "#1d0705"), rim * 0.7)
    cracks = (ndimage.sobel(N.worley(S, S, 30, seed=seed + 2, tileable=False), axis=0) ** 2 > 0.002) & (a > 0.5)
    col[cracks] *= 0.6
    a = a * (0.75 + 0.2 * N.spectral(S, S, beta=1.0, seed=seed + 3))
    return col, _edge_fade(a)


def smear(seed: int = 8301):
    yy, xx = _grid()
    path_y = 30 + 10 * np.sin(xx / 18.0) - (xx - 32) * 0.25
    width = 7 + 4 * np.sin(xx / 9.0 + 1.0)
    band = np.clip(1.0 - np.abs(yy - path_y) / width, 0, 1)
    streaks = N.spectral(S, S, beta=0.8, seed=seed, aniso=(1.0, 10.0))
    taper = N.smoothstep(2, 14, xx) * (1.0 - N.smoothstep(40, 62, xx + 6 * streaks))
    a = N.smoothstep(0.05, 0.35, band) * (0.35 + 0.65 * streaks) * taper
    return _dried_colour(seed + 1), _edge_fade(np.clip(a, 0, 0.9))


def handprint(seed: int = 8401):
    yy, xx = _grid()
    a = np.zeros((S, S))

    def ellipse(cx, cy, rx, ry, ang):
        c, s = math.cos(ang), math.sin(ang)
        u = ((xx - cx) * c + (yy - cy) * s) / rx
        v = (-(xx - cx) * s + (yy - cy) * c) / ry
        return np.clip(1.0 - (u * u + v * v), 0, 1)

    a = np.maximum(a, N.smoothstep(0.0, 0.25, ellipse(32, 40, 11, 12, 0.0)))
    for cx, cy, ang, length in ((22, 22, -0.35, 9), (28, 17, -0.12, 11), (35, 16, 0.08, 11), (41, 20, 0.3, 9)):
        a = np.maximum(a, N.smoothstep(0.0, 0.3, ellipse(cx, cy, 2.6, length, ang)))
    a = np.maximum(a, N.smoothstep(0.0, 0.3, ellipse(46, 36, 2.8, 8, 1.1)))
    # Imperfect transfer: patchy contact, drips running down.
    patch = N.smoothstep(0.3, 0.55, N.spectral(S, S, beta=1.4, seed=seed))
    a *= 0.35 + 0.65 * patch
    for x in (27, 34, 38):
        drip = (np.abs(xx - x) < 0.8) & (yy > 48) & (yy < 48 + 6 + (x % 7))
        a = np.maximum(a, drip * 0.7)
    return _dried_colour(seed + 1), _edge_fade(a * 0.9)


def drag(seed: int = 8501):
    yy, xx = _grid()
    a = np.zeros((S, S))
    for cx in (24, 40):
        wobble = cx + 2 * np.sin(yy / 10.0 + cx)
        band = np.clip(1.0 - np.abs(xx - wobble) / 4.0, 0, 1)
        a = np.maximum(a, band)
    streaks = N.spectral(S, S, beta=0.7, seed=seed, aniso=(10.0, 1.0))
    a = N.smoothstep(0.1, 0.5, a) * (0.25 + 0.75 * streaks) * 0.8
    return _dried_colour(seed + 1), _edge_fade(a, 1.0)


def faded(seed: int = 8601):
    yy, xx = _grid()
    d = np.hypot((xx - 32) / 28, (yy - 30) / 26)
    blob = N.spectral(S, S, beta=2.2, seed=seed)
    a = N.smoothstep(0.0, 0.4, (1.0 - d) + 0.4 * (blob - 0.5)) * 0.32
    runs = N.smoothstep(0.6, 0.9, N.spectral(S, S, beta=1.4, seed=seed + 1, aniso=(6.0, 1.0)))
    a = np.clip(a + runs * 0.08 * (yy > 30), 0, 1)
    col = N.ramp(N.spectral(S, S, beta=1.4, seed=seed + 2), [(0.0, "#4a2e26"), (1.0, "#6e4a3a")])
    return col, _edge_fade(a)


def scorch(seed: int = 8701):
    yy, xx = _grid()
    d = np.hypot(xx - 32, yy - 32) / 30
    turb = N.spectral(S, S, beta=2.0, seed=seed)
    a = N.smoothstep(0.0, 0.5, (1.0 - d) + 0.35 * (turb - 0.5))
    rays = (np.sin(np.arctan2(yy - 32, xx - 32) * 7 + turb * 9) * 0.5 + 0.5) * (d < 1.0)
    soot = N.spectral(S, S, beta=1.2, seed=seed + 1)
    a = np.clip(a * (0.8 + 0.12 * rays + 0.15 * (soot - 0.5)), 0, 0.95)
    col = N.ramp(np.clip(1.0 - d, 0, 1), [(0.0, "#2a2420"), (0.6, "#14110f"), (1.0, "#070605")])
    return col, _edge_fade(a)


def survivor_mark(seed: int = 8801):
    yy, xx = _grid()

    def seg(x1, y1, x2, y2, w):
        px, py = xx - x1, yy - y1
        dx, dy = x2 - x1, y2 - y1
        t = np.clip((px * dx + py * dy) / (dx * dx + dy * dy), 0, 1)
        dist = np.hypot(px - t * dx, py - t * dy)
        return np.clip(1.0 - (dist - w) / 1.5, 0, 1)

    paint = np.maximum.reduce([seg(10, 34, 50, 30, 3.0), seg(50, 30, 38, 18, 3.0), seg(50, 30, 40, 43, 3.0)])
    overspray = N.blur(paint, 2.5, wrap=False) * (N.rng(seed).random((S, S)) > 0.5) * 0.4
    drips = np.zeros((S, S))
    for x, length in ((16, 8), (27, 12), (44, 7)):
        drips = np.maximum(drips, (np.abs(xx - x) < 0.9) * (yy > 34) * (yy < 34 + length))
    wear = 0.55 + 0.45 * N.smoothstep(0.25, 0.6, N.spectral(S, S, beta=1.2, seed=seed + 1))
    a = np.clip(np.maximum(paint, drips * 0.85) * wear + overspray, 0, 0.92)
    col = N.ramp(N.spectral(S, S, beta=1.0, seed=seed + 2), [(0.0, "#9a3a1c"), (1.0, "#c25a2a")])
    return col, _edge_fade(a, 1.0)


def evacuation_notice(seed: int = 8901):
    """A printed notice taped to a wall, yellowed and torn: a header bar, a block of text, a map box."""
    r = N.rng(seed)
    yy, xx = _grid()
    x0, y0, x1, y1 = 14, 8, 50, 56
    sheet = ((xx >= x0) & (xx <= x1) & (yy >= y0) & (yy <= y1)).astype(float)
    tear = N.smoothstep(0.62, 0.7, N.spectral(S, S, beta=1.8, seed=seed)) * (yy > 40)
    sheet *= 1.0 - tear
    paper = N.ramp(N.spectral(S, S, beta=2.0, seed=seed + 1), [(0.0, "#bfb38e"), (1.0, "#ddd3b0")])
    col = paper.copy()
    header = (xx >= x0 + 2) & (xx <= x1 - 2) & (yy >= y0 + 2) & (yy <= y0 + 7)
    col[header] = N.hex_rgb("#8a1e18")
    for row in range(y0 + 11, y0 + 30, 3):
        length = int(r.integers(18, 32))
        line = (yy == row) & (xx >= x0 + 3) & (xx <= x0 + 3 + length)
        col[line] = N.hex_rgb("#2a2622")
    box = (xx >= x0 + 4) & (xx <= x1 - 4) & (yy >= y0 + 33) & (yy <= y1 - 4)
    edge = box & ~((xx >= x0 + 5) & (xx <= x1 - 5) & (yy >= y0 + 34) & (yy <= y1 - 5))
    col[edge] = N.hex_rgb("#2a2622")
    route = (np.abs(yy - (y0 + 33 + (xx - x0) * 0.4)) < 0.8) & box
    col[route] = N.hex_rgb("#8a1e18")
    tape = ((np.abs(xx - x0) < 4) | (np.abs(xx - x1) < 4)) & (np.abs(yy - y0) < 2)
    col[tape] = N.hex_rgb("#d8d4c0")
    stain = N.smoothstep(0.55, 0.8, N.spectral(S, S, beta=2.4, seed=seed + 2))
    col = N.lerp(col, N.solid(S, S, "#6a5a3a"), stain * 0.4)
    a = np.clip(sheet + tape * 0.9, 0, 1) * 0.97
    return col, a


def scattered_papers(seed: int = 9001):
    """Loose pages and a notebook spread across a floor, scuffed with footprints."""
    r = N.rng(seed)
    yy, xx = _grid()
    a = np.zeros((S, S))
    col = N.solid(S, S, "#d8d2bc")
    for _ in range(9):
        cx, cy = r.uniform(10, S - 10), r.uniform(10, S - 10)
        ang = r.uniform(0, math.pi)
        w, h = r.uniform(5, 8), r.uniform(7, 10)
        u = (xx - cx) * math.cos(ang) + (yy - cy) * math.sin(ang)
        v = -(xx - cx) * math.sin(ang) + (yy - cy) * math.cos(ang)
        page = (np.abs(u) < w) & (np.abs(v) < h)
        a = np.maximum(a, page * 0.95)
        lines_ = page & ((np.floor(v + h) % 3) == 0) & (np.abs(u) < w - 1.5)
        col[lines_] = N.hex_rgb("#8a90a8")
        if r.random() < 0.25:
            cover = page & (np.abs(u) < w) & (np.abs(v) < h)
            col[cover] = N.hex_rgb(r.choice(["#2a4a7a", "#7a2a22", "#3a5a2a"]))
    dirt = N.smoothstep(0.5, 0.8, N.spectral(S, S, beta=1.6, seed=seed + 1))
    col = N.lerp(col, N.solid(S, S, "#5a4e3a"), dirt * 0.45)
    return col, _edge_fade(a, 2.0)


def biomass_veins(seed: int = 9101):
    """Dark, glistening veins of the infection creeping over a surface from a nest."""
    yy, xx = _grid()
    r = N.rng(seed)
    a = np.zeros((S, S))
    for _ in range(7):
        x, y = r.uniform(0, S), r.uniform(0, S)
        ang = r.uniform(0, 2 * math.pi)
        width = r.uniform(1.4, 2.6)
        for _ in range(40):
            a = np.maximum(a, np.clip(width - np.hypot(xx - x, yy - y), 0, 1))
            ang += r.normal(0, 0.35)
            x += math.cos(ang) * 1.2
            y += math.sin(ang) * 1.2
            width = max(0.6, width * 0.985)
    blobs = N.smoothstep(0.7, 0.85, N.spectral(S, S, beta=2.2, seed=seed + 1))
    a = np.clip(np.maximum(a, blobs * 0.8), 0, 1) * 0.95
    col = N.ramp(N.spectral(S, S, beta=1.4, seed=seed + 2), [(0.0, "#2a1418"), (0.6, "#4a2226"), (1.0, "#7a3a34")])
    shine = N.smoothstep(0.8, 0.95, N.spectral(S, S, beta=1.0, seed=seed + 3))
    col = N.lerp(col, N.solid(S, S, "#b06a5a"), shine * 0.5)
    return col, _edge_fade(a, 1.0)


GENERATORS = {
    "dried_blood_spots": spots, "dried_blood_pool": pool, "blood_smear": smear, "bloody_handprint": handprint,
    "drag_marks": drag, "faded_stain": faded, "scorch_mark": scorch, "survivor_mark": survivor_mark,
    "evacuation_notice": evacuation_notice, "scattered_papers": scattered_papers, "biomass_veins": biomass_veins,
}


def build() -> None:
    lang = {}
    for name, english in DECALS.items():
        rgb, alpha = GENERATORS[name]()
        N.save_png(N.asset(f"textures/block/decal/{name}.png"), rgb, alpha)
        tex = {"decal": f"{NS}:block/decal/{name}", "particle": f"{NS}:block/decal/{name}"}
        uv = [0, 0, 16, 16]
        D.asset_json(f"models/block/decal/{name}_floor.json", {"ambientocclusion": False, "textures": tex, "elements": [
            {"from": [0, 0.1, 0], "to": [16, 0.1, 16], "shade": False, "faces": {"up": {"texture": "#decal", "uv": uv}}}]})
        D.asset_json(f"models/block/decal/{name}_ceiling.json", {"ambientocclusion": False, "textures": tex, "elements": [
            {"from": [0, 15.9, 0], "to": [16, 15.9, 16], "shade": False, "faces": {"down": {"texture": "#decal", "uv": uv}}}]})
        D.asset_json(f"models/block/decal/{name}_wall.json", {"ambientocclusion": False, "textures": tex, "elements": [
            {"from": [0, 0, 15.9], "to": [16, 16, 15.9], "shade": False, "faces": {"north": {"texture": "#decal", "uv": uv}}}]})
        variants = {}
        for turn in range(4):
            floor = {"model": f"{NS}:block/decal/{name}_floor"}
            ceiling = {"model": f"{NS}:block/decal/{name}_ceiling"}
            if turn:
                floor["y"] = turn * 90
                ceiling["y"] = turn * 90
            variants[f"face=floor,turn={turn}"] = floor
            variants[f"face=ceiling,turn={turn}"] = ceiling
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            wall = {"model": f"{NS}:block/decal/{name}_wall"}
            if rot:
                wall["y"] = rot
            variants[f"face=wall,facing={facing}"] = wall
        D.asset_json(f"blockstates/{name}.json", {"variants": variants})
        D.asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/decal/{name}"}})
        D.item_model_definition(name, f"{NS}:item/{name}")
        lang[f"block.{NS}.{name}"] = english
    D.merge_lang(lang)


if __name__ == "__main__":
    build()
    print(f"{len(DECALS)} decals written")
