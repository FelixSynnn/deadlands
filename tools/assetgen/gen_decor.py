"""DEADLANDS furniture and roadside fixtures: textures (+ LabPBR maps), block models, blockstates,
item models, loot tables, mining tags and English names for every com.deadlands.block.DecorKind.

Models are authored with the front facing north, like the collision shapes in DecorKind; keep the
two in step. Faces that carry a picture (a stove's oven door, a cabinet front) use explicit UVs
[0, 0, w, h], so the picture sits in the top-left w*2 x h*2 texels of its 32x32 texture; plain
material faces use Minecraft's automatic UVs, so wood grain and paint run continuously across the
parts of a model.
"""
from __future__ import annotations

import numpy as np

import dl_data as D
import dl_materials as M
import dl_noise as N
from gen_terrain import end_grain

NS = D.NS
T = M.T
FACES = ("north", "south", "east", "west", "up", "down")


# ----------------------------------------------------------------------------- drawing helpers

def mask(fn) -> np.ndarray:
    return N.supersample_mask(fn, T, T, 4)


def rrect(x0, y0, x1, y1, r=1.0) -> np.ndarray:
    return mask(lambda d, s: d.rounded_rectangle([x0 * s, y0 * s, x1 * s - 1, y1 * s - 1], radius=r * s, fill=255))


def ellipse(x0, y0, x1, y1) -> np.ndarray:
    return mask(lambda d, s: d.ellipse([x0 * s, y0 * s, x1 * s - 1, y1 * s - 1], fill=255))


def ring(cx, cy, radius, width) -> np.ndarray:
    return mask(lambda d, s: d.ellipse([(cx - radius) * s, (cy - radius) * s, (cx + radius) * s, (cy + radius) * s],
                                       outline=255, width=max(1, int(width * s))))


def lines(segments, width=0.6) -> np.ndarray:
    def draw(d, s):
        for x0, y0, x1, y1 in segments:
            d.line([(x0 * s, y0 * s), (x1 * s, y1 * s)], fill=255, width=max(1, int(width * s)))
    return mask(draw)


def scratches(seed, n, length=(3.0, 10.0), width=0.5) -> np.ndarray:
    r = N.rng(seed)
    segs = []
    for _ in range(n):
        x, y = r.uniform(0, T), r.uniform(0, T)
        a = r.uniform(0, np.pi)
        ln = r.uniform(*length)
        segs.append((x, y, x + np.cos(a) * ln, y + np.sin(a) * ln))
    return lines(segs, width)


def blotches(seed, share, beta=2.2) -> np.ndarray:
    """Soft stains covering about `share` of the texture."""
    f = N.spectral(T, T, beta=beta, seed=seed)
    t = float(np.quantile(f, 1.0 - share))
    return N.smoothstep(t - 0.04, t + 0.06, f)


def rows_down(lo=0.0, hi=1.0) -> np.ndarray:
    """0 at the top row, 1 at the bottom row (for grime that gathers low)."""
    yy = np.mgrid[0:T, 0:T][0].astype(float) / (T - 1)
    return lo + (hi - lo) * yy


def finish(rgb, height, smooth=0.12, metal=0.0, porosity=0.4, relief=1.3) -> M.Material:
    return M.relief(M.Material(np.clip(rgb, 0, 255), height, smooth=smooth, metal=metal, porosity=porosity), relief)


# ----------------------------------------------------------------------------- wood

def pole(seed: int) -> M.Material:
    """Creosote-dark utility pole: vertical grain, sun-bleached streaks, splits and old staples."""
    g = M.wood_grain(T, T, seed, horizontal=False)
    rgb = N.ramp(g, [(0.0, "#211812"), (0.5, "#3b2e22"), (1.0, "#5a4836")])
    splits = N.spectral(T, T, beta=1.0, seed=seed + 3, aniso=(14.0, 1.0)) > 0.84
    rgb[splits] *= 0.42
    bleach = N.smoothstep(0.55, 0.9, N.spectral(T, T, beta=2.2, seed=seed + 4, aniso=(4.0, 1.0)))
    rgb = N.lerp(rgb, N.solid(T, T, "#7a7164") * (0.85 + 0.3 * g)[..., None], bleach * 0.45)
    r = N.rng(seed + 5)
    for _ in range(6):
        x, y = int(r.integers(9, 21)), int(r.integers(0, 31))
        rgb[y, x:x + 2] = N.hex_rgb("#8d9192")
    return finish(rgb, 0.5 + 0.3 * (g - 0.5) - 0.35 * splits, smooth=0.08, porosity=0.55, relief=1.4)


def crossarm(seed: int) -> M.Material:
    g = M.wood_grain(T, T, seed)
    rgb = N.ramp(g, [(0.0, "#463e31"), (0.5, "#6a604e"), (1.0, "#8b7f69")])
    checks = N.spectral(T, T, beta=1.0, seed=seed + 2, aniso=(1.0, 14.0)) > 0.85
    rgb[checks] *= 0.5
    for x0 in (4, 26):
        rgb[14:17, x0:x0 + 3] = N.hex_rgb("#3a3a37")
        rgb[15, x0 + 1] = N.hex_rgb("#7d7f7c")
    return finish(rgb, 0.5 + 0.25 * (g - 0.5) - 0.3 * checks, smooth=0.08, porosity=0.5)


def furniture_wood(seed: int) -> M.Material:
    """Varnished wood gone dull: plank seams, scratches, water rings and a veil of dust."""
    g = M.wood_grain(T, T, seed)
    rgb = N.ramp(g, [(0.0, "#3d2a1c"), (0.5, "#694a31"), (1.0, "#8d6a48")])
    yy, xx = np.mgrid[0:T, 0:T]
    seam = (yy % 16) == 15
    rgb[seam] *= 0.55
    sc = scratches(seed + 1, 9)
    rgb = N.lerp(rgb, N.solid(T, T, "#b39a78"), sc * 0.55)
    rings = ring(9.5, 21.5, 4.0, 0.7) * 0.5 + ring(23.0, 8.0, 3.2, 0.6) * 0.35
    rgb = N.lerp(rgb, N.solid(T, T, "#a58e70"), rings)
    dust = blotches(seed + 3, 0.35)
    rgb = N.lerp(rgb, N.solid(T, T, "#8a8478"), dust * 0.32)
    return finish(rgb, 0.55 + 0.15 * (g - 0.5) - 0.3 * seam - 0.08 * sc, smooth=0.28 * (1 - dust) + 0.05, porosity=0.35, relief=1.1)


def dark_wood(seed: int) -> M.Material:
    g = M.wood_grain(T, T, seed, horizontal=False)
    rgb = N.ramp(g, [(0.0, "#24180f"), (0.5, "#3f2c1d"), (1.0, "#5c432d")])
    sc = scratches(seed + 1, 5, width=0.4)
    rgb = N.lerp(rgb, N.solid(T, T, "#8a7155"), sc * 0.45)
    return finish(rgb, 0.5 + 0.2 * (g - 0.5), smooth=0.2, porosity=0.35, relief=1.1)


def desk_drawers(seed: int) -> M.Material:
    """Three drawer fronts (10 x 28 texels, top-left) with tarnished brass pulls; one hangs open."""
    base = furniture_wood(seed)
    rgb = base.rgb.copy()
    height = base.height.copy()
    for i, (y0, y1) in enumerate(((1, 9), (10, 18), (19, 27))):
        frame = np.zeros((T, T), bool)
        frame[y0, 1:9] = frame[y1 - 1, 1:9] = True
        frame[y0:y1, 1] = frame[y0:y1, 8] = True
        rgb[frame] *= 0.62
        height[frame] -= 0.2
        rgb[y0 + 1:y1 - 1, 2:8] *= 1.06
        cy = (y0 + y1) // 2
        rgb[cy - 1:cy + 1, 3:7] = N.hex_rgb("#6f5a2a")
        rgb[cy - 1, 3:7] = N.hex_rgb("#a48a46")
        height[cy - 1:cy + 1, 3:7] += 0.25
        if i == 1:  # left hanging open: a dark gap above it
            rgb[y0, 1:9] = N.hex_rgb("#0f0b08")
    rgb[0, :10] *= 0.5
    rgb[27:, :10] *= 0.55
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.25, porosity=0.35)


# ----------------------------------------------------------------------------- metal

def galvanized(seed: int, rust: float = 0.22) -> M.Material:
    rgb, blot = M.metal_base(seed, light="#9ba1a3", dark="#5f6568")
    rc, rmask = M.rust_layer(seed + 3, rust)
    rgb = N.lerp(rgb, rc, rmask)
    st = M.stain_streaks(T, T, seed + 4)
    rgb = N.lerp(rgb, N.solid(T, T, "#5a3a22"), st * 0.35 * (0.4 + rmask))
    return finish(rgb, 0.6 + 0.1 * (blot - 0.5) + 0.1 * rmask, smooth=0.5 * (1 - rmask) + 0.1,
                  metal=(rmask < 0.35).astype(float), porosity=0.05 + 0.4 * rmask, relief=1.0)


def painted_metal(seed: int, paint: str, chips: float = 0.12) -> M.Material:
    """Enamel or powder coat over steel, chipped to rusty metal along scratches and edges."""
    tone = N.spectral(T, T, beta=2.3, seed=seed)
    rgb = N.solid(T, T, paint) * (0.9 + 0.16 * tone)[..., None]
    sc = scratches(seed + 1, 8, width=0.45)
    chip = np.clip(blotches(seed + 2, chips, beta=2.7) + sc * 0.8, 0, 1)
    rc, _ = M.rust_layer(seed + 3, 0.6)
    rgb = N.lerp(rgb, rc, chip * 0.85)
    grime = blotches(seed + 4, 0.3) * rows_down(0.3, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#3b3528"), grime * 0.35)
    return finish(rgb, 0.6 - 0.15 * chip + 0.05 * tone, smooth=0.45 * (1 - chip) + 0.08, porosity=0.1 + 0.4 * chip, relief=1.0)


def lamp_lens(seed: int) -> M.Material:
    """Underside of a street lamp housing (14 x 12 texels, top-left): a smashed lens around a dead bulb."""
    base = painted_metal(seed, "#3a3e3f", chips=0.08)
    rgb = base.rgb.copy()
    lens = rrect(1, 1, 13, 11, 2.0)
    glass = N.ramp(N.spectral(T, T, beta=2.0, seed=seed + 5), [(0.0, "#1d2324"), (1.0, "#3f4b4e")])
    rgb = N.lerp(rgb, glass, lens)
    bulb = ellipse(5, 4, 9, 8)
    rgb = N.lerp(rgb, N.solid(T, T, "#8a8364"), bulb * 0.9)
    r = N.rng(seed + 6)
    cracks = lines([(7, 6, 7 + np.cos(a) * 6, 6 + np.sin(a) * 6) for a in r.uniform(0, 2 * np.pi, 6)], 0.45)
    rgb = N.lerp(rgb, N.solid(T, T, "#9fb0b4"), cracks * lens * 0.8)
    height = base.height + 0.1 * lens - 0.1 * cracks
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.3 + 0.5 * lens, porosity=0.1)


def guardrail_beam(seed: int) -> M.Material:
    """Galvanised W-beam (the profile repeats every 8 rows; the face uses rows 0-8)."""
    base = galvanized(seed, rust=0.18)
    rgb = base.rgb.copy()
    profile = np.array([0.66, 1.12, 1.16, 0.8, 0.74, 1.1, 1.14, 0.66])
    shade = profile[np.arange(T) % 8]
    rgb *= shade[:, None, None]
    yy, xx = np.mgrid[0:T, 0:T]
    bolts = ((xx % 16 == 6) | (xx % 16 == 7)) & ((yy % 8 == 3) | (yy % 8 == 4))
    rgb[bolts] = N.hex_rgb("#34302b")
    runs = M.stain_streaks(T, T, seed + 7) * ((xx % 16 >= 5) & (xx % 16 <= 8))
    rgb = N.lerp(rgb, N.solid(T, T, "#6b3a1c"), runs * 0.55)
    road_grime = blotches(seed + 8, 0.4) * rows_down(0.2, 0.9)
    rgb = N.lerp(rgb, N.solid(T, T, "#4b4537"), road_grime * 0.3)
    height = 0.5 + 0.4 * (shade - 0.9)[:, None] * np.ones((T, T)) + 0.2 * bolts
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.45, metal=1.0, porosity=0.1)


def stove_front(seed: int) -> M.Material:
    """Oven door (28 x 30 texels, top-left): smoked window, a steel handle bar and grease runs."""
    base = enamel(seed)
    rgb = base.rgb.copy()
    height = base.height.copy()
    rgb[0:3, :28] = N.ramp(N.spectral(T, T, beta=1.1, seed=seed + 9, aniso=(1.0, 6.0)), [(0.0, "#6d706f"), (1.0, "#a3a6a4")])[0:3, :28]
    rgb[3, :28] *= 0.55
    window = rrect(5, 10, 23, 23, 2.0)
    glass = N.ramp(rows_down(), [(0.0, "#2b2a27"), (1.0, "#14120f")])
    rgb = N.lerp(rgb, glass, window)
    rgb = N.lerp(rgb, N.solid(T, T, "#8f8b80"), lines([(8, 12, 12, 20)], 0.7) * window * 0.35)
    rgb[5:7, 3:25] = N.hex_rgb("#b9bcba")
    rgb[6, 3:25] = N.hex_rgb("#6a6d6b")
    height[5:7, 3:25] += 0.3
    drips = M.stain_streaks(T, T, seed + 10) * rows_down(0.4, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#6a5330"), drips * 0.4)
    rgb[28:30, :28] = N.hex_rgb("#2a2826")
    height[28:30, :28] -= 0.3
    height -= 0.15 * window
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.5, porosity=0.1)


def stove_top(seed: int) -> M.Material:
    """Cooktop (28 x 28 texels, top-left): four coil burners crusted with old grease."""
    base = enamel(seed + 20)
    rgb = base.rgb.copy()
    height = base.height.copy()
    for cx, cy, rad in ((7.5, 7.5, 5.5), (20.5, 7.5, 4.5), (7.5, 20.5, 4.5), (20.5, 20.5, 5.5)):
        pan = ellipse(cx - rad - 1, cy - rad - 1, cx + rad + 1, cy + rad + 1)
        rgb = N.lerp(rgb, N.solid(T, T, "#5b5d5c"), pan * 0.9)
        coil = np.zeros((T, T))
        for rr in np.arange(1.2, rad, 1.5):
            coil = np.maximum(coil, ring(cx, cy, rr, 0.8))
        rgb = N.lerp(rgb, N.solid(T, T, "#1b1a19"), coil)
        height += 0.2 * coil - 0.1 * pan
    burnt = blotches(seed + 21, 0.18) * 0.7
    rgb = N.lerp(rgb, N.solid(T, T, "#3d2b18"), burnt)
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.4, porosity=0.15)


def stove_panel(seed: int) -> M.Material:
    """Backsplash control panel (28 x 6 texels, top-left): four knobs and a stopped clock."""
    base = galvanized(seed + 30, rust=0.08)
    rgb = base.rgb.copy()
    height = base.height.copy()
    for cx in (3.5, 8.5, 19.5, 24.5):
        knob = ellipse(cx - 2, 1, cx + 2, 5)
        rgb = N.lerp(rgb, N.solid(T, T, "#1e1e1d"), knob)
        rgb[1:3, int(cx)] = N.hex_rgb("#d8d4c8")
        height += 0.3 * knob
    clock = rrect(11, 1, 17, 5, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#cfc8a8"), clock)
    rgb[2:4, 14] = N.hex_rgb("#2a2622")
    rgb[3, 13:15] = N.hex_rgb("#2a2622")
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.5, metal=0.0, porosity=0.1)


def shelf_back(seed: int) -> M.Material:
    """Pegboard store-shelf back panel."""
    base = painted_metal(seed, "#a8a18c", chips=0.05)
    rgb = base.rgb.copy()
    yy, xx = np.mgrid[0:T, 0:T]
    holes = (yy % 4 == 1) & (xx % 4 == 1)
    rgb[holes] = N.hex_rgb("#2d2a24")
    return M.Material(np.clip(rgb, 0, 255), base.height - 0.3 * holes, smooth=0.3, porosity=0.2)


def shelf_edge(seed: int) -> M.Material:
    """Shelf lip (16 x 1 units): a price-label strip with torn tags (rows 0-1)."""
    base = painted_metal(seed + 40, "#a8a18c", chips=0.08)
    rgb = base.rgb.copy()
    rgb[0:2, :] = N.hex_rgb("#d9d2b4")
    r = N.rng(seed + 41)
    for x0 in range(1, T - 3, 6):
        if r.random() < 0.75:
            rgb[0:2, x0:x0 + 3] = N.hex_rgb("#f1ecd6")
            rgb[1, x0:x0 + 2] = N.hex_rgb("#2b2a28")
        if r.random() < 0.3:
            rgb[0:2, x0 + 3:x0 + 5] = N.hex_rgb("#b1352a")
    return M.Material(np.clip(rgb, 0, 255), base.height, smooth=0.3, porosity=0.2)


def goods(seed: int) -> M.Material:
    """A small atlas of what the looters left behind: a cereal box (front 0-8, side 8-16, top 0-8 x 8-16)
    and a can (label 16-19, lid 24-27). Texel coordinates; models address them in UV units."""
    rgb = N.ramp(N.spectral(T, T, beta=2.0, seed=seed), [(0.0, "#7a6243"), (1.0, "#9c8160")])
    rgb[0:8, 0:8] = N.hex_rgb("#a63a2c")
    rgb[1:7, 1:7] = N.lerp(rgb[1:7, 1:7], N.solid(6, 6, "#e6dcc0"), ellipse(1, 2, 7, 6)[1:7, 1:7] * 0.9)
    rgb[3:5, 2:6] = N.hex_rgb("#5b1d16")
    rgb[0:8, 8:16] = N.hex_rgb("#93352a")
    rgb[2:7, 9:15] = N.hex_rgb("#ddd4bb")
    rgb[3:7:2, 10:14] = N.hex_rgb("#77706a")
    rgb[0:4, 16:19] = N.hex_rgb("#35557a")
    rgb[1:3, 16:19] = N.hex_rgb("#d8d2bf")
    rgb[0:3, 24:27] = N.hex_rgb("#8e9192")
    rgb[1, 25] = N.hex_rgb("#55585a")
    faded = blotches(seed + 1, 0.4) * 0.35
    rgb = N.lerp(rgb, N.solid(T, T, "#c9c2ad"), faded)
    return M.Material(np.clip(rgb, 0, 255), np.full((T, T), 0.5), smooth=0.2, porosity=0.4)


# ----------------------------------------------------------------------------- stone, ceramic and enamel

def barrier_concrete(seed: int) -> M.Material:
    """Jersey barrier: chipped concrete, a faded reflective band, a spray-painted tag and road dirt."""
    base = M.concrete(seed, "plain")
    rgb = base.rgb.copy()
    height = base.height.copy()
    band = np.zeros((T, T))
    band[14:17, :] = 1.0
    wear = blotches(seed + 1, 0.3, beta=2.4)
    rgb = N.lerp(rgb, N.solid(T, T, "#c9a53c"), band * (1 - wear) * 0.85)
    chips = blotches(seed + 2, 0.07, beta=2.5)
    rgb = N.lerp(rgb, N.solid(T, T, "#5d5b55"), chips * 0.6)
    height -= 0.25 * chips
    tag = lines([(4, 20, 8, 18), (8, 18, 9, 22), (9, 22, 13, 19), (15, 19, 19, 22), (17, 18, 17, 23)], 0.9)
    rgb = N.lerp(rgb, N.solid(T, T, "#7e2420"), tag * 0.7)
    dirt = blotches(seed + 3, 0.6) * rows_down(-0.6, 1.0).clip(0, 1)
    rgb = N.lerp(rgb, N.solid(T, T, "#4e4636"), dirt * 0.55)
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.1, porosity=0.6)


def enamel(seed: int) -> M.Material:
    """Appliance-white enamel: yellowed, greasy, chipped to black steel here and there."""
    tone = N.spectral(T, T, beta=2.4, seed=seed)
    rgb = N.ramp(tone, [(0.0, "#c9c4b6"), (1.0, "#e2ded2")])
    yellow = blotches(seed + 1, 0.45)
    rgb = N.lerp(rgb, N.solid(T, T, "#c2b48c"), yellow * 0.35)
    r = N.rng(seed + 2)
    chips = np.zeros((T, T))
    for _ in range(4):
        x, y = r.uniform(2, 30), r.uniform(2, 30)
        chips = np.maximum(chips, ellipse(x - 1.2, y - 1.0, x + 1.2, y + 1.0))
    rgb = N.lerp(rgb, N.solid(T, T, "#26282a"), chips)
    grime = blotches(seed + 3, 0.3) * rows_down(0.2, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#6c6350"), grime * 0.3)
    return finish(rgb, 0.6 + 0.05 * tone - 0.2 * chips, smooth=0.6 * (1 - grime) + 0.1, porosity=0.08, relief=0.9)


def porcelain(seed: int) -> M.Material:
    """Bathroom porcelain: crazed glaze, limescale and the stains nobody will ever clean now."""
    tone = N.spectral(T, T, beta=2.5, seed=seed)
    rgb = N.ramp(tone, [(0.0, "#cfcbbf"), (1.0, "#e6e3da")])
    craze = M.crack_mask(T, T, seed + 1, density=10, width=0.05, warp=1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#a39f93"), craze * 0.5)
    stain = blotches(seed + 2, 0.25) * rows_down(0.0, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#a68a4f"), stain * 0.5)
    return finish(rgb, 0.6 + 0.05 * tone - 0.1 * craze, smooth=0.65, porosity=0.05, relief=0.8)


def toilet_top(seed: int) -> M.Material:
    """Bowl seen from above (16 x 20 texels, top-left): seat ring around murky water."""
    base = porcelain(seed + 10)
    rgb = base.rgb.copy()
    seat = ellipse(1, 1, 15, 18)
    rgb = N.lerp(rgb, N.solid(T, T, "#d9d3c2"), seat)
    water = ellipse(4, 4, 12, 15)
    rgb = N.lerp(rgb, N.ramp(N.spectral(T, T, beta=2.0, seed=seed + 11), [(0.0, "#3a3d2c"), (1.0, "#5e6146")]), water)
    rgb = N.lerp(rgb, N.solid(T, T, "#7a6a3e"), ring(8, 9.5, 4.3, 0.8) * 0.6)
    rgb[18:20, 5:11] = N.hex_rgb("#a9a597")
    height = base.height + 0.1 * seat - 0.3 * water
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.6, porosity=0.05)


# ----------------------------------------------------------------------------- kitchen

def cabinet_paint(seed: int) -> np.ndarray:
    tone = N.spectral(T, T, beta=2.3, seed=seed)
    return N.ramp(tone, [(0.0, "#b3a786"), (1.0, "#cdc2a2")])


def peel(rgb: np.ndarray, seed: int, share: float = 0.18) -> tuple[np.ndarray, np.ndarray]:
    flake = blotches(seed, share, beta=2.7)
    wood = N.ramp(M.wood_grain(T, T, seed + 1, horizontal=False), [(0.0, "#4a3523"), (1.0, "#7a5a3a")])
    return N.lerp(rgb, wood, flake), flake


def counter_front(seed: int) -> M.Material:
    """Two painted cabinet doors (32 x 28 texels, top-left) over a dark toe-kick."""
    rgb = cabinet_paint(seed)
    height = np.full((T, T), 0.6)
    for x0, x1, handle in ((1, 15, 13), (17, 31, 18)):
        rgb[1, x0:x1] *= 0.6
        rgb[26, x0:x1] *= 0.6
        rgb[2, x0:x1] *= 1.15
        rgb[25, x0:x1] *= 0.72
        rgb[2:26, x0] *= 1.1
        rgb[2:26, x1 - 1] *= 0.7
        rgb[5:22, x0 + 3:x1 - 3] *= 0.92
        rgb[5, x0 + 3:x1 - 3] *= 0.68
        rgb[5:22, x0 + 3] *= 0.75
        rgb[21, x0 + 3:x1 - 3] *= 1.18
        rgb[5:22, x1 - 4] *= 1.12
        height[5:22, x0 + 3:x1 - 3] -= 0.1
        rgb[10:17, handle:handle + 2] = N.hex_rgb("#77786f")
        rgb[10:17, handle] = N.hex_rgb("#a5a69c")
        height[10:17, handle:handle + 2] += 0.3
    rgb[:, 15:17] = N.hex_rgb("#1e1a15")
    rgb[0:2, :] *= 0.8
    rgb, flake = peel(rgb, seed + 3, 0.08)
    rgb[26:, :] = N.hex_rgb("#2b2620")
    scuffs = blotches(seed + 4, 0.35) * rows_down(-0.3, 1.0).clip(0, 1)
    rgb = N.lerp(rgb, N.solid(T, T, "#4f4535"), scuffs * 0.45)
    height[26:, :] -= 0.3
    return M.Material(np.clip(rgb, 0, 255), height - 0.1 * flake, smooth=0.3, porosity=0.3)


def counter_side(seed: int) -> M.Material:
    """Cabinet side panel (rows 4-32) under the laminate's aluminium edge strip (rows 0-4)."""
    rgb = cabinet_paint(seed + 50)
    rgb, flake = peel(rgb, seed + 51, 0.1)
    rgb[0:4, :] = N.ramp(N.spectral(T, T, beta=1.1, seed=seed + 52, aniso=(1.0, 8.0)), [(0.0, "#7c7e7a"), (1.0, "#a9aba6")])[0:4, :]
    rgb[3, :] *= 0.5
    grime = blotches(seed + 53, 0.4) * rows_down(-0.2, 1.0).clip(0, 1)
    rgb = N.lerp(rgb, N.solid(T, T, "#4f4535"), grime * 0.4)
    height = np.full((T, T), 0.6) - 0.1 * flake
    height[3, :] -= 0.2
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.3, porosity=0.3)


def laminate(seed: int) -> np.ndarray:
    tone = N.spectral(T, T, beta=2.0, seed=seed)
    rgb = N.ramp(tone, [(0.0, "#b3a88c"), (1.0, "#c8bd9f")])
    r = N.rng(seed + 1)
    specks = r.random((T, T))
    rgb[specks > 0.93] *= 0.7
    rgb[specks < 0.05] *= 1.12
    return rgb


def counter_top(seed: int) -> M.Material:
    rgb = laminate(seed)
    rings = ring(10, 10, 3.5, 0.8) * 0.5 + ring(24, 22, 2.8, 0.7) * 0.4
    rgb = N.lerp(rgb, N.solid(T, T, "#6d5234"), rings)
    burn = ellipse(18, 5, 25, 10) * 0.6
    rgb = N.lerp(rgb, N.solid(T, T, "#3b2c1c"), burn)
    sc = scratches(seed + 2, 10, width=0.4)
    rgb = N.lerp(rgb, N.solid(T, T, "#ddd5bd"), sc * 0.5)
    dust = blotches(seed + 3, 0.4) * 0.3
    rgb = N.lerp(rgb, N.solid(T, T, "#8f897a"), dust)
    return finish(rgb, 0.6 - 0.05 * sc, smooth=0.5 * (1 - dust) + 0.05, porosity=0.15, relief=0.8)


def sink_top(seed: int) -> M.Material:
    """Counter top with a steel basin (rows 4-22) and the faucet's base plate (rows 24-28)."""
    top = counter_top(seed + 60)
    rgb = top.rgb.copy()
    rim = rrect(4, 3, 28, 23, 3.0)
    basin = rrect(6, 5, 26, 21, 2.5)
    steel = N.ramp(N.spectral(T, T, beta=1.1, seed=seed + 61, aniso=(1.0, 6.0)), [(0.0, "#6f7476"), (1.0, "#a7acae")])
    rgb = N.lerp(rgb, steel, rim)
    depth = N.ramp(rows_down(), [(0.0, "#3e4244"), (1.0, "#6e7375")])
    rgb = N.lerp(rgb, depth, basin)
    rgb = N.lerp(rgb, N.solid(T, T, "#151617"), ellipse(14.5, 11.5, 17.5, 14.5))
    rust = blotches(seed + 62, 0.2) * basin
    rgb = N.lerp(rgb, N.solid(T, T, "#6a3a1e"), rust * 0.6)
    plate = rrect(11, 24, 21, 28, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#8d9294"), plate)
    height = top.height - 0.45 * basin + 0.1 * rim
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.45 + 0.3 * rim, metal=rim, porosity=0.1)


# ----------------------------------------------------------------------------- fabric

def weave(seed: int, light: str, dark: str) -> np.ndarray:
    yy, xx = np.mgrid[0:T, 0:T]
    tone = N.spectral(T, T, beta=2.2, seed=seed)
    rgb = N.ramp(tone, [(0.0, dark), (1.0, light)])
    rgb *= np.where((xx + yy) % 2 == 0, 1.04, 0.96)[..., None]
    return rgb


def couch_fabric(seed: int) -> M.Material:
    rgb = weave(seed, "#7c7b59", "#55553d")
    worn = blotches(seed + 1, 0.3)
    rgb = N.lerp(rgb, N.solid(T, T, "#8f8c6e"), worn * 0.5)
    tears = blotches(seed + 2, 0.035, beta=2.8)
    rgb = N.lerp(rgb, N.solid(T, T, "#c4a96a"), tears)
    stains = blotches(seed + 3, 0.15) * 0.45
    rgb = N.lerp(rgb, N.solid(T, T, "#3d3426"), stains)
    return finish(rgb, 0.55 + 0.05 * worn - 0.2 * tears, smooth=0.04, porosity=0.8, relief=1.0)


def couch_seat(seed: int) -> M.Material:
    base = couch_fabric(seed + 10)
    rgb = base.rgb.copy()
    rgb[:, 15:17] *= 0.5
    rgb[4:6, :] *= 0.7
    sag = N.lerp(np.ones((T, T)), 0.86 * np.ones((T, T)), ellipse(3, 10, 13, 28) * 0.6 + ellipse(19, 10, 29, 28) * 0.6)
    rgb *= sag[..., None]
    return M.Material(np.clip(rgb, 0, 255), base.height, smooth=0.04, porosity=0.8)


def mattress_top(seed: int) -> M.Material:
    """Ticking stripes and old stains; a grey wool blanket bunched over the foot end (rows 0-16)."""
    yy, xx = np.mgrid[0:T, 0:T]
    rgb = N.ramp(N.spectral(T, T, beta=2.2, seed=seed), [(0.0, "#c2baa6"), (1.0, "#d6cfbd")])
    rgb[(xx % 4) == 0] = rgb[(xx % 4) == 0] * 0.7 + np.array(N.hex_rgb("#7d8a93")) * 0.3
    stains = blotches(seed + 1, 0.25)
    rgb = N.lerp(rgb, N.solid(T, T, "#a88c55"), stains * 0.55)
    blanket = (yy < 15).astype(float)
    fold = np.sin(xx / T * 2 * np.pi * 2 + N.spectral(T, T, beta=2.5, seed=seed + 2) * 4) * 0.5 + 0.5
    wool = N.ramp(fold, [(0.0, "#4b5146"), (1.0, "#6c7365")])
    rgb = N.lerp(rgb, wool, blanket)
    rgb[15, :] *= 0.55
    hole = ellipse(20, 5, 24, 8)
    rgb = N.lerp(rgb, N.solid(T, T, "#c2baa6"), hole * blanket)
    return finish(rgb, 0.5 + 0.2 * blanket * fold, smooth=0.05, porosity=0.8, relief=1.2)


def mattress_side(seed: int) -> M.Material:
    """Mattress edge (15 x 3 units): piping along the top and bottom (rows 0 and 5), stripes between."""
    yy, xx = np.mgrid[0:T, 0:T]
    rgb = N.ramp(N.spectral(T, T, beta=2.2, seed=seed), [(0.0, "#bdb5a1"), (1.0, "#d2cbb9")])
    rgb[(xx % 4) == 0] *= 0.82
    rgb[yy % 6 == 0] = N.hex_rgb("#8b8371")
    rgb[yy % 6 == 5] = N.hex_rgb("#8b8371")
    stains = blotches(seed + 1, 0.2)
    rgb = N.lerp(rgb, N.solid(T, T, "#a1854f"), stains * 0.5)
    return finish(rgb, np.full((T, T), 0.5), smooth=0.05, porosity=0.8, relief=1.0)


def pillow(seed: int) -> M.Material:
    rgb = N.ramp(N.spectral(T, T, beta=2.4, seed=seed), [(0.0, "#bdb6a5"), (1.0, "#d8d2c3")])
    crease = lines([(4, 12, 28, 14)], 0.8)
    rgb = N.lerp(rgb, N.solid(T, T, "#8e8778"), crease * 0.6)
    stain = blotches(seed + 1, 0.15)
    rgb = N.lerp(rgb, N.solid(T, T, "#9c8455"), stain * 0.5)
    return finish(rgb, 0.5 - 0.1 * crease, smooth=0.05, porosity=0.8)


def insulator(seed: int) -> M.Material:
    yy = np.mgrid[0:T, 0:T][0]
    ribs = np.cos(yy / 3.0 * np.pi) * 0.5 + 0.5
    rgb = N.ramp(0.6 * ribs + 0.4 * N.spectral(T, T, beta=2.0, seed=seed), [(0.0, "#2f5446"), (0.6, "#4f8a72"), (1.0, "#8ec2a9")])
    dirt = blotches(seed + 1, 0.3) * 0.4
    rgb = N.lerp(rgb, N.solid(T, T, "#4a4a3a"), dirt)
    return M.Material(np.clip(rgb, 0, 255), 0.4 + 0.3 * ribs, smooth=0.8, porosity=0.02)


# ----------------------------------------------------------------------------- textures



# ----------------------------------------------------------------------------- Milestone 3, second half

def lamp_lens_lit(seed: int) -> M.Material:
    """The same lens with the bulb burning: warm glass, a hot core (the block itself emits light)."""
    base = painted_metal(seed, "#3a3e3f", chips=0.08)
    rgb = base.rgb.copy()
    lens = rrect(1, 1, 13, 11, 2.0)
    glow = N.ramp(N.spectral(T, T, beta=2.0, seed=seed + 5), [(0.0, "#d9b76a"), (1.0, "#f6e3a4")])
    rgb = N.lerp(rgb, glow, lens)
    bulb = ellipse(4, 3, 10, 9)
    rgb = N.lerp(rgb, N.solid(T, T, "#fff6d8"), bulb)
    return M.Material(np.clip(rgb, 0, 255), base.height + 0.1 * lens, smooth=0.8 * lens + 0.2, porosity=0.05)


def traffic_lens(seed: int) -> M.Material:
    """Signal head face (12 x 22 texels, top-left): three dead lamps under visors on yellow-painted steel."""
    base = painted_metal(seed, "#7a6420", chips=0.18)
    rgb = base.rgb.copy()
    height = base.height.copy()
    for k, colour in enumerate(("#4a1410", "#4a3410", "#143a1c")):
        cy = 3.5 + k * 7
        rim = ellipse(1, cy - 3, 11, cy + 4)
        lamp = ellipse(2.5, cy - 1.8, 9.5, cy + 2.8)
        rgb = N.lerp(rgb, N.solid(T, T, "#141515"), rim)
        rgb = N.lerp(rgb, N.solid(T, T, colour), lamp)
        rgb = N.lerp(rgb, N.solid(T, T, "#ffffff"), ellipse(3.5, cy - 1.2, 5.0, cy) * 0.25)
        height -= 0.2 * rim
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.3, porosity=0.15)


def sign_plate(seed: int, kind: str) -> M.Material:
    """Road sign faces (the art sits top-left: warning 28 x 26, speed 24 x 26, route 32 x 20 texels)."""
    base = galvanized(seed, rust=0.1)
    rgb = base.rgb.copy()
    if kind == "warning":
        diamond = mask(lambda d, sc: d.polygon([(14 * sc, 0.5 * sc), (27.5 * sc, 13 * sc), (14 * sc, 25.5 * sc), (0.5 * sc, 13 * sc)], fill=255))
        inner = mask(lambda d, sc: d.polygon([(14 * sc, 2.5 * sc), (25.5 * sc, 13 * sc), (14 * sc, 23.5 * sc), (2.5 * sc, 13 * sc)], fill=255))
        rgb = N.lerp(rgb, N.solid(T, T, "#151515"), diamond)
        rgb = N.lerp(rgb, N.solid(T, T, "#d8a920"), inner)
        mark = lines([(14, 6, 14, 15)], 2.2) + ellipse(12.8, 17.5, 15.2, 20)
        rgb = N.lerp(rgb, N.solid(T, T, "#151515"), np.clip(mark, 0, 1))
        spray = lines([(3, 20, 25, 6)], 1.2)  # someone sprayed a slash across it
        rgb = N.lerp(rgb, N.solid(T, T, "#7a1a14"), spray * 0.85)
    elif kind == "speed":
        board = rrect(0, 0, 24, 26, 1.5)
        inner = rrect(1.2, 1.2, 22.8, 24.8, 1.0)
        rgb = N.lerp(rgb, N.solid(T, T, "#1a1a1a"), board)
        rgb = N.lerp(rgb, N.solid(T, T, "#dcd9cc"), inner)
        text = lines([(4, 4.5, 20, 4.5), (5, 8.5, 19, 8.5)], 1.3)  # SPEED / LIMIT
        four = lines([(7, 12, 5, 18), (5, 18, 10, 18), (9, 13, 9, 22)], 1.6)
        five = lines([(18, 12, 13, 12), (13, 12, 13, 16.5), (13, 16.5, 17.5, 16.5), (17.5, 16.5, 18, 21.5), (18, 21.5, 13, 21.5)], 1.6)
        rgb = N.lerp(rgb, N.solid(T, T, "#1a1a1a"), np.clip(text + four + five, 0, 1))
    else:
        board = rrect(0, 0, 32, 20, 1.5)
        inner = rrect(1.0, 1.0, 31.0, 19.0, 1.0)
        rgb = N.lerp(rgb, N.solid(T, T, "#e8e6dc"), board)
        rgb = N.lerp(rgb, N.solid(T, T, "#1f5a34"), inner)
        shield = mask(lambda d, sc: d.polygon([(4 * sc, 4 * sc), (12 * sc, 4 * sc), (12 * sc, 11 * sc), (8 * sc, 15 * sc), (4 * sc, 11 * sc)], fill=255))
        rgb = N.lerp(rgb, N.solid(T, T, "#e8e6dc"), shield)
        arrow = lines([(16, 10, 27, 10), (23, 6, 27, 10), (23, 14, 27, 10)], 1.6)
        rgb = N.lerp(rgb, N.solid(T, T, "#e8e6dc"), arrow)
        rgb = N.lerp(rgb, N.solid(T, T, "#1f5a34"), lines([(6, 8, 10, 8), (6, 10.5, 10, 10.5)], 1.0))
    grime = blotches(seed + 9, 0.35) * rows_down(0.4, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#4a3f2c"), grime * 0.35)
    holes = (N.spectral(T, T, beta=0.6, seed=seed + 11) > 0.985)  # buckshot
    rgb[holes] = N.hex_rgb("#1a1714")
    return M.Material(np.clip(rgb, 0, 255), base.height, smooth=0.35, porosity=0.15)


def transformer(seed: int) -> M.Material:
    """Pad-mounted transformer: grey-green paint, cooling fins, a high-voltage label (top-left of the north face)."""
    base = painted_metal(seed, "#58624f", chips=0.15)
    rgb = base.rgb.copy()
    xx = np.mgrid[0:T, 0:T][1]
    fins = ((xx % 4) == 0).astype(float)
    rgb *= (1.0 - 0.3 * fins)[..., None]
    label = mask(lambda d, sc: d.polygon([(4 * sc, 2 * sc), (10 * sc, 2 * sc), (7 * sc, 7.5 * sc)], fill=255))
    rgb = N.lerp(rgb, N.solid(T, T, "#d8a920"), label)
    rgb = N.lerp(rgb, N.solid(T, T, "#151515"), lines([(7, 3, 6.4, 5), (6.4, 5, 7.6, 5), (7.6, 5, 7, 7)], 0.5) * label)
    return finish(rgb, base.height - 0.2 * fins, smooth=0.3, porosity=0.2, relief=1.0)


def generator_front(seed: int, running: bool) -> M.Material:
    """Generator control panel (28 x 28 texels, top-left): gauges, a status lamp, the breaker and the fuse holder."""
    base = painted_metal(seed, "#a8842a", chips=0.2)
    rgb = base.rgb.copy()
    panel = rrect(2, 2, 26, 18, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#2a2c2b"), panel)
    for cx in (7, 15):
        face = ellipse(cx - 3.5, 4, cx + 3.5, 11)
        rgb = N.lerp(rgb, N.solid(T, T, "#d6d2c2"), face)
        rgb = N.lerp(rgb, N.solid(T, T, "#7a1a14"), lines([(cx, 7.5, cx + (2.5 if running else -2.5), 5.5)], 0.6) * face)
    lamp = ellipse(19.5, 4.5, 24.5, 9.5)
    rgb = N.lerp(rgb, N.solid(T, T, "#3cff6a" if running else "#3a1410"), lamp)
    if running:
        rgb = N.lerp(rgb, N.solid(T, T, "#d8ffe0"), ellipse(20.5, 5.5, 22.5, 7.5))
    breaker = rrect(19, 12, 25, 17, 0.6)
    rgb = N.lerp(rgb, N.solid(T, T, "#151515"), breaker)
    rgb = N.lerp(rgb, N.solid(T, T, "#b02a1e"), rrect(20.5, 13 if running else 14.5, 23.5, 14.5 if running else 16, 0.3))
    fuse = rrect(4, 13, 16, 17, 0.5)
    rgb = N.lerp(rgb, N.solid(T, T, "#3d3f3e"), fuse)
    if running:
        rgb = N.lerp(rgb, N.solid(T, T, "#c9b06a"), rrect(5.5, 14, 14.5, 16, 0.4))
    vents = lines([(4, y, 24, y) for y in (21, 23, 25)], 0.7)
    rgb = N.lerp(rgb, N.solid(T, T, "#151515"), vents)
    return finish(rgb, base.height - 0.15 * panel - 0.2 * vents, smooth=0.35, porosity=0.15, relief=1.0)


def fuel_pump_front(seed: int) -> M.Material:
    """Fuel pump face (24 x 32 texels, top-left): a faded brand band, two price windows with dead
    digits, a hose socket and a kick plate. Decorative until vehicles need fuel."""
    base = painted_metal(seed, "#9a3a2c", chips=0.22)
    rgb = base.rgb.copy()
    band = rrect(0, 0, 24, 6, 0.4)
    rgb = N.lerp(rgb, N.solid(T, T, "#d8d0b8"), band)
    rgb = N.lerp(rgb, N.solid(T, T, "#3a3e3f"), lines([(4, 3, 20, 3)], 1.2) * band)
    for y in (9, 15):
        window = rrect(4, y, 20, y + 4.4, 0.6)
        rgb = N.lerp(rgb, N.solid(T, T, "#141816"), window)
        rgb = N.lerp(rgb, N.solid(T, T, "#3a4a3c"), lines([(x, y + 1, x, y + 3.4) for x in (7, 10, 13, 16)], 0.8) * window)
    socket = ellipse(14.4, 21.2, 20.4, 27.2)
    rgb = N.lerp(rgb, N.solid(T, T, "#1b1b1b"), socket)
    rgb = N.lerp(rgb, N.solid(T, T, "#55585a"), ring(17.4, 24.2, 2.8, 0.7))
    plate = rrect(0, 28, 24, 32, 0.4)
    rgb = N.lerp(rgb, N.solid(T, T, "#3d3f3e"), plate)
    grime = blotches(seed + 5, 0.25) * rows_down(0.2, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#2a221a"), grime * 0.45)
    return finish(rgb, base.height - 0.2 * socket - 0.1 * plate, smooth=0.3, porosity=0.2, relief=1.0)


def workbench_top(seed: int) -> M.Material:
    """A scarred bench top with a vice, a saw cut, and tools left where they were dropped."""
    base = furniture_wood(seed)
    rgb = base.rgb.copy()
    rgb = N.lerp(rgb, N.solid(T, T, "#2a2420"), scratches(seed + 1, 14, length=(4.0, 14.0)) * 0.6)
    vice = rrect(22, 3, 30, 11, 1.0)
    rgb = N.lerp(rgb, N.solid(T, T, "#4a5a6a"), vice)
    rgb = N.lerp(rgb, N.solid(T, T, "#1e2428"), lines([(26, 3, 26, 11)], 1.0) * vice)
    wrench = lines([(5, 22, 15, 27)], 1.6)
    rgb = N.lerp(rgb, N.solid(T, T, "#9a9c9e"), wrench)
    rgb = N.lerp(rgb, N.solid(T, T, "#3a2a1a"), blotches(seed + 2, 0.15) * 0.5)
    return finish(rgb, base.height + 0.3 * vice + 0.15 * wrench, smooth=0.15, porosity=0.4, relief=1.2)


def bookshelf_front(seed: int) -> M.Material:
    """Three shelves of books (32 x 32 texels): faded spines of every height, gaps where some were taken."""
    base = dark_wood(seed)
    rgb = base.rgb.copy()
    height = base.height.copy()
    r = N.rng(seed + 1)
    palette = ["#6b2a22", "#2a3e5a", "#3e5a2a", "#7a6a3a", "#5a2a4a", "#8a7a5a", "#2a2a2a", "#9a4a2a"]
    for shelf_top in (2, 12, 22):
        x = 2
        while x < 30:
            w = int(r.integers(1, 4))
            hgt = int(r.integers(6, 9))
            if r.random() < 0.82:
                colour = np.array(N.hex_rgb(r.choice(palette)))
                y0 = shelf_top + 9 - hgt
                rgb[y0:shelf_top + 9, x:x + w] = colour * (0.75 + 0.25 * r.random())
                rgb[y0 + 2:y0 + 3, x:x + w] = colour * 1.25
                height[y0:shelf_top + 9, x:x + w] += 0.15
            x += w
        rgb[shelf_top + 9:shelf_top + 10, 1:31] = N.hex_rgb("#2a1f15")
    dust = blotches(seed + 2, 0.3)
    rgb = N.lerp(rgb, N.solid(T, T, "#8a8070"), dust * 0.25)
    return M.Material(np.clip(rgb, 0, 255), height, smooth=0.1, porosity=0.5)


def whiteboard_face(seed: int) -> M.Material:
    """A whiteboard (32 x 22 texels, top-left) with an unfinished lesson in faded marker and a smudged
    evacuation time someone wrote across it."""
    base = galvanized(seed, rust=0.05)
    rgb = base.rgb.copy()
    board = rrect(1, 1, 31, 21, 0.5)
    rgb = N.lerp(rgb, N.solid(T, T, "#dedcd2"), board)
    r = N.rng(seed + 1)
    strokes = []
    for row in (4, 7, 10):
        x = 3
        while x < 20:
            ln = float(r.uniform(1.5, 4.0))
            strokes.append((x, row + r.uniform(-0.3, 0.3), x + ln, row + r.uniform(-0.3, 0.3)))
            x += ln + float(r.uniform(0.6, 1.4))
    rgb = N.lerp(rgb, N.solid(T, T, "#2f3f7a"), lines(strokes, 0.55) * 0.8)
    sums = lines([(22, 4, 28, 4), (22, 7, 26, 7), (22, 10, 29, 10), (21, 11.5, 29, 11.5)], 0.55)
    rgb = N.lerp(rgb, N.solid(T, T, "#202020"), sums * 0.75)
    evac = lines([(4, 15, 6, 18), (6, 18, 8, 15), (10, 15, 10, 18), (10, 15, 12, 15), (10, 18, 12, 18), (14, 18, 16, 15), (16, 15, 18, 18),
                  (21, 15, 24, 15), (21, 15, 21, 18), (21, 18, 24, 18), (26, 15.5, 29, 17.5)], 0.8)
    rgb = N.lerp(rgb, N.solid(T, T, "#9a1e18"), evac * 0.85)
    smudge = blotches(seed + 2, 0.25, beta=1.8) * board
    rgb = N.lerp(rgb, N.solid(T, T, "#b8b8b0"), smudge * 0.45)
    return M.Material(np.clip(rgb, 0, 255), base.height, smooth=0.6, porosity=0.05)


TEXTURES = {
    "pole": lambda: pole(7101),
    "pole_end": lambda: end_grain(7102, "#4a3b2b", "#6a573f", "#2a2018"),
    "crossarm": lambda: crossarm(7103),
    "insulator": lambda: insulator(7104),
    "galvanized": lambda: galvanized(7105),
    "lamp_housing": lambda: painted_metal(7106, "#3a3e3f", chips=0.1),
    "lamp_lens": lambda: lamp_lens(7107),
    "guardrail_beam": lambda: guardrail_beam(7108),
    "guardrail_post": lambda: galvanized(7109, rust=0.35),
    "barrier": lambda: barrier_concrete(7110),
    "wood": lambda: furniture_wood(7111),
    "dark_wood": lambda: dark_wood(7112),
    "desk_drawers": lambda: desk_drawers(7113),
    "counter_front": lambda: counter_front(7114),
    "counter_side": lambda: counter_side(7115),
    "counter_top": lambda: counter_top(7116),
    "sink_top": lambda: sink_top(7117),
    "shelf_metal": lambda: painted_metal(7118, "#aea791", chips=0.1),
    "shelf_back": lambda: shelf_back(7119),
    "shelf_edge": lambda: shelf_edge(7120),
    "goods": lambda: goods(7121),
    "mattress_top": lambda: mattress_top(7122),
    "mattress_side": lambda: mattress_side(7123),
    "pillow": lambda: pillow(7124),
    "couch_fabric": lambda: couch_fabric(7125),
    "couch_seat": lambda: couch_seat(7126),
    "enamel": lambda: enamel(7127),
    "stove_front": lambda: stove_front(7128),
    "stove_top": lambda: stove_top(7129),
    "stove_panel": lambda: stove_panel(7130),
    "porcelain": lambda: porcelain(7131),
    "toilet_top": lambda: toilet_top(7132),
    "lamp_lens_lit": lambda: lamp_lens_lit(7107),
    "traffic_lens": lambda: traffic_lens(7133),
    "sign_warning": lambda: sign_plate(7134, "warning"),
    "sign_speed": lambda: sign_plate(7135, "speed"),
    "sign_route": lambda: sign_plate(7136, "route"),
    "transformer": lambda: transformer(7137),
    "generator_body": lambda: painted_metal(7138, "#a8842a", chips=0.2),
    "generator_front_off": lambda: generator_front(7139, False),
    "generator_front_on": lambda: generator_front(7139, True),
    "fuel_pump_front": lambda: fuel_pump_front(7142),
    "fuel_pump_body": lambda: painted_metal(7143, "#9a3a2c", chips=0.22),
    "workbench_top": lambda: workbench_top(7144),
    "bookshelf_front": lambda: bookshelf_front(7140),
    "whiteboard": lambda: whiteboard_face(7141),
}


def tex(name: str) -> str:
    return f"{NS}:block/decor/{name}"


# ----------------------------------------------------------------------------- models

def el(frm, to, faces: dict) -> dict:
    """faces: face -> texture key, or (texture key, uv). Faces on the block's boundary cull against neighbours."""
    out = {}
    for name, spec in faces.items():
        key, uv = spec if isinstance(spec, tuple) else (spec, None)
        d = {"texture": f"#{key}"}
        if uv is not None:
            d["uv"] = [round(v, 3) for v in uv]
        boundary = {"down": frm[1] == 0, "up": to[1] == 16, "north": frm[2] == 0, "south": to[2] == 16,
                    "west": frm[0] == 0, "east": to[0] == 16}
        if boundary[name]:
            d["cullface"] = name
        out[name] = d
    return {"from": list(frm), "to": list(to), "faces": out}


def every(key: str, **overrides) -> dict:
    faces = {f: key for f in FACES}
    faces.update(overrides)
    return faces


def legs(x0, z0, x1, z1, size, top, key="legs") -> list:
    return [el((x, 0, z), (x + size, top, z + size), every(key)) for x in (x0, x1 - size) for z in (z0, z1 - size)]


def models() -> dict[str, tuple[dict, list]]:
    """kind -> (texture variables, elements)."""
    m = {}
    m["utility_pole"] = ({"side": "pole", "end": "pole_end"},
                         [el((5, 0, 5), (11, 16, 11), every("side", up="end", down="end"))])
    m["utility_pole_crossarm"] = ({"side": "pole", "end": "pole_end", "arm": "crossarm", "glass": "insulator"}, [
        el((5, 0, 5), (11, 16, 11), every("side", up="end", down="end")),
        el((0, 11, 6.5), (16, 13, 9.5), every("arm")),
        el((1.5, 13, 7), (3.5, 15.5, 9), every("glass")),
        el((12.5, 13, 7), (14.5, 15.5, 9), every("glass")),
    ])
    m["street_lamp"] = ({"metal": "galvanized"}, [el((6.5, 0, 6.5), (9.5, 16, 9.5), every("metal"))])
    m["street_lamp_head"] = ({"metal": "galvanized", "housing": "lamp_housing", "lens": "lamp_lens"}, [
        el((6.5, 0, 6.5), (9.5, 10, 9.5), every("metal")),
        el((6.5, 8, 2), (9.5, 10, 6.5), every("metal")),
        el((4.5, 5, 0), (11.5, 8, 6), every("housing", down=("lens", (0, 0, 7, 6)))),
    ])
    m["guardrail"] = ({"beam": "guardrail_beam", "post": "guardrail_post"}, [
        el((6.5, 0, 13), (9.5, 9, 15), every("post")),
        el((0, 4, 12), (16, 8, 13), every("beam", north=("beam", (0, 0, 16, 4)), south=("beam", (0, 0, 16, 4)))),
    ])
    m["concrete_barrier"] = ({"concrete": "barrier"}, [
        el((0, 0, 3), (16, 4, 13), every("concrete")),
        el((0, 4, 5), (16, 10, 11), every("concrete")),
        el((0, 10, 6), (16, 13, 10), every("concrete")),
    ])
    m["table"] = ({"top": "wood", "legs": "dark_wood"},
                  [el((0, 13, 0), (16, 16, 16), every("top"))] + legs(1, 1, 15, 15, 2, 13))
    m["chair"] = ({"seat": "wood", "legs": "dark_wood"}, [
        el((3, 7, 3), (13, 9, 13), every("seat")),
        el((3, 9, 11.5), (4.5, 16, 13), every("legs")),
        el((11.5, 9, 11.5), (13, 16, 13), every("legs")),
        el((4.5, 13, 11.5), (11.5, 15.5, 13), every("seat")),
        el((4.5, 10, 12), (11.5, 11.5, 12.5), every("seat")),
    ] + legs(3, 3, 13, 13, 2, 7))
    m["counter"] = ({"front": "counter_front", "side": "counter_side", "top": "counter_top"}, [
        el((0, 0, 1), (16, 14, 16), every("side", north=("front", (0, 0, 16, 14)))),
        el((0, 14, 0), (16, 16, 16), every("side", up="top")),
    ])
    m["store_shelf"] = ({"metal": "shelf_metal", "back": "shelf_back", "edge": "shelf_edge", "goods": "goods"}, [
        el((0, 0, 13), (16, 16, 16), every("metal", north="back")),
        el((0, 0, 2), (1, 16, 13), every("metal")),
        el((15, 0, 2), (16, 16, 13), every("metal")),
        el((1, 1, 2), (15, 2, 13), every("metal", north=("edge", (0, 0, 14, 1)))),
        el((1, 6, 2), (15, 7, 13), every("metal", north=("edge", (0, 0, 14, 1)))),
        el((1, 11, 2), (15, 12, 13), every("metal", north=("edge", (0, 0, 14, 1)))),
        # What the looters left: a cereal box and two cans.
        el((2.5, 2, 6), (6.5, 6, 8), {"north": ("goods", (0, 0, 4, 4)), "south": ("goods", (0, 0, 4, 4)),
                                      "east": ("goods", (4, 0, 5, 4)), "west": ("goods", (4, 0, 5, 4)),
                                      "up": ("goods", (0, 4, 4, 5)), "down": ("goods", (0, 4, 4, 5))}),
        el((10, 7, 5), (11.5, 9, 6.5), {"north": ("goods", (8, 0, 9.5, 2)), "south": ("goods", (8, 0, 9.5, 2)),
                                        "east": ("goods", (8, 0, 9.5, 2)), "west": ("goods", (8, 0, 9.5, 2)),
                                        "up": ("goods", (12, 0, 13.5, 1.5)), "down": ("goods", (12, 0, 13.5, 1.5))}),
        el((4, 12, 7), (5.5, 14, 8.5), {"north": ("goods", (8, 0, 9.5, 2)), "south": ("goods", (8, 0, 9.5, 2)),
                                        "east": ("goods", (8, 0, 9.5, 2)), "west": ("goods", (8, 0, 9.5, 2)),
                                        "up": ("goods", (12, 0, 13.5, 1.5)), "down": ("goods", (12, 0, 13.5, 1.5))}),
    ])
    m["bed_frame"] = ({"frame": "dark_wood", "top": "mattress_top", "side": "mattress_side", "pillow": "pillow"}, [
        el((0, 0, 0), (16, 5, 16), every("frame")),
        el((0, 5, 15), (16, 9, 16), every("frame")),
        el((0.5, 5, 0.5), (15.5, 8, 15), every(("side", (0, 0, 15, 3)), up="top", down="frame")),
        el((2.5, 8, 11.5), (13.5, 9, 14.5), every("pillow")),
    ])
    m["couch"] = ({"fabric": "couch_fabric", "seat": "couch_seat", "legs": "dark_wood"}, [
        el((0, 1, 2), (16, 8, 16), every("fabric", up="seat")),
        el((0, 8, 12), (16, 15, 16), every("fabric")),
        el((0, 8, 2), (2, 11, 12), every("fabric")),
        el((14, 8, 2), (16, 11, 12), every("fabric")),
    ] + legs(1, 3, 15, 15, 1.5, 1))
    m["desk"] = ({"wood": "wood", "drawers": "desk_drawers"}, [
        el((0, 14, 0), (16, 16, 16), every("wood")),
        el((10, 0, 1), (15, 14, 15), every("wood", north=("drawers", (0, 0, 5, 14)))),
        el((1, 0, 1), (3, 14, 15), every("wood")),
        el((3, 7, 14), (10, 14, 15), every("wood")),
    ])
    m["stove"] = ({"enamel": "enamel", "front": "stove_front", "top": "stove_top", "panel": "stove_panel"}, [
        el((1, 0, 1), (15, 15, 15), every("enamel", north=("front", (0, 0, 14, 15)), up=("top", (0, 0, 14, 14)))),
        el((1, 15, 13), (15, 18, 15), every("enamel", north=("panel", (0, 0, 14, 3)))),
    ])
    m["sink"] = ({"front": "counter_front", "side": "counter_side", "top": "sink_top", "chrome": "galvanized"}, [
        el((0, 0, 1), (16, 14, 16), every("side", north=("front", (0, 0, 16, 14)))),
        el((0, 14, 0), (16, 16, 16), every("side", up=("top", (0, 0, 16, 16)))),
        el((7, 16, 12), (9, 17, 14), every("chrome")),
        el((7.5, 17, 12.5), (8.5, 20, 13.5), every("chrome")),
        el((7.5, 19, 9), (8.5, 20, 12.5), every("chrome")),
        el((5, 16, 12.5), (6.5, 17, 13.5), every("chrome")),
        el((9.5, 16, 12.5), (11, 17, 13.5), every("chrome")),
    ])
    m["street_lamp_head_lit"] = ({"metal": "galvanized", "housing": "lamp_housing", "lens": "lamp_lens_lit"}, [
        el((6.5, 0, 6.5), (9.5, 10, 9.5), every("metal")),
        el((6.5, 8, 2), (9.5, 10, 6.5), every("metal")),
        el((4.5, 5, 0), (11.5, 8, 6), every("housing", down=("lens", (0, 0, 7, 6)))),
    ])
    m["traffic_light"] = ({"metal": "galvanized", "head": "traffic_lens"}, [
        el((6.5, 0, 6.5), (9.5, 16, 9.5), every("metal")),
        el((5, 4, 2.5), (11, 15, 6.5), every("metal", north=("head", (0, 0, 6, 11)))),
    ])
    for sign, (x1, y1, x2, y2) in (("warning", (1, 3, 15, 16)), ("speed", (2, 3, 14, 16)), ("route", (0, 6, 16, 16))):
        w, h = x2 - x1, y2 - y1
        m[f"road_sign_{sign}"] = ({"metal": "galvanized", "face": f"sign_{sign}"}, [
            el((7, 0, 8), (9, 16, 10), every("metal")),
            el((x1, y1, 7), (x2, y2, 8), every("metal", north=("face", (0, 0, w, h)))),
        ])
    m["transformer"] = ({"body": "transformer"}, [el((2, 0, 3), (14, 13, 13), every("body", north=("body", (0, 0, 12, 13))))])
    m["power_generator"] = ({"body": "generator_body", "front": "generator_front_off"}, [
        el((1, 0, 2), (15, 14, 14), every("body", north=("front", (0, 0, 14, 14)))),
        el((2, 14, 4), (6, 16, 8), every("body")),
    ])
    m["power_generator_running"] = ({"body": "generator_body", "front": "generator_front_on"}, [
        el((1, 0, 2), (15, 14, 14), every("body", north=("front", (0, 0, 14, 14)))),
        el((2, 14, 4), (6, 16, 8), every("body")),
    ])
    m["fuel_pump"] = ({"body": "fuel_pump_body", "front": "fuel_pump_front", "hose": "galvanized"}, [
        el((2, 0, 5), (14, 16, 11), every("body", north=("front", (0, 0, 12, 16)), south=("front", (0, 0, 12, 16)))),
        el((0, 0, 4), (16, 1, 12), every("hose")),
        el((14, 5, 7), (15, 12, 9), every("hose")),
    ])
    m["workbench"] = ({"wood": "dark_wood", "top": "workbench_top", "drawers": "desk_drawers"}, [
        el((0, 12, 0), (16, 16, 16), every("wood", up="top")),
        el((1, 0, 1), (15, 12, 15), every("wood", north="drawers")),
    ])
    m["bookshelf"] = ({"wood": "dark_wood", "front": "bookshelf_front"}, [
        el((0, 0, 8), (16, 16, 16), every("wood", north=("front", (0, 0, 16, 16)))),
    ])
    m["whiteboard"] = ({"frame": "galvanized", "board": "whiteboard"}, [
        el((0, 3, 15), (16, 14, 16), every("frame", north=("board", (0, 0, 16, 11)))),
    ])
    m["toilet"] = ({"porcelain": "porcelain", "bowl": "toilet_top", "chrome": "galvanized"}, [
        el((5, 0, 5), (11, 6, 13), every("porcelain")),
        el((4, 6, 3), (12, 10, 13), every("porcelain", up=("bowl", (0, 0, 8, 10)))),
        el((4, 6, 12), (12, 15, 16), every("porcelain")),
        el((3.5, 15, 11.5), (12.5, 16, 16), every("porcelain")),
        el((4.5, 13, 11), (6, 13.6, 11.5), every("chrome")),
    ])
    return m


# kind -> (placement, mining tool, English name). Placement and tool must match DecorKind.
KINDS = {
    "utility_pole": ("post", "axe", "Utility Pole"),
    "utility_pole_crossarm": ("axis", "axe", "Utility Pole Crossarm"),
    "street_lamp": ("post", "pickaxe", "Street Lamp Post"),
    "street_lamp_head": ("lamp", "pickaxe", "Street Lamp"),
    "guardrail": ("facing", "pickaxe", "Guardrail"),
    "concrete_barrier": ("facing", "pickaxe", "Concrete Barrier"),
    "table": ("facing", "axe", "Table"),
    "chair": ("facing", "axe", "Chair"),
    "counter": ("facing", "axe", "Kitchen Counter"),
    "store_shelf": ("facing", "pickaxe", "Store Shelf"),
    "bed_frame": ("facing", "axe", "Old Bed"),
    "couch": ("facing", "axe", "Couch"),
    "desk": ("facing", "axe", "Desk"),
    "stove": ("facing", "pickaxe", "Stove"),
    "sink": ("facing", "pickaxe", "Sink"),
    "toilet": ("facing", "pickaxe", "Toilet"),
    "traffic_light": ("facing", "pickaxe", "Traffic Light"),
    "road_sign_warning": ("facing", "pickaxe", "Warning Sign"),
    "road_sign_speed": ("facing", "pickaxe", "Speed Limit Sign"),
    "road_sign_route": ("facing", "pickaxe", "Route Sign"),
    "transformer": ("facing", "pickaxe", "Transformer"),
    "power_generator": ("generator", "pickaxe", "Power Generator"),
    "fuel_pump": ("facing", "pickaxe", "Fuel Pump"),
    "workbench": ("facing", "axe", "Workbench"),
    "bookshelf": ("facing", "axe", "Bookshelf"),
    "whiteboard": ("facing", "pickaxe", "Whiteboard"),
}
# Extra models for block states (not blocks of their own).
STATE_MODELS = {"street_lamp_head_lit", "power_generator_running"}


def blockstate(name: str, placement: str) -> dict:
    model = f"{NS}:block/{name}"
    if placement == "post":
        return {"variants": {"": {"model": model}}}
    if placement == "axis":
        return {"variants": {"axis=x": {"model": model}, "axis=z": {"model": model, "y": 90}}}
    variants = {}
    state = {"lamp": ("lit", "_lit"), "generator": ("running", "_running")}.get(placement)
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for on in ((False, True) if state else (None,)):
            v = {"model": model + (state[1] if on else "")}
            if rot:
                v["y"] = rot
            key = f"facing={facing}" if on is None else f"facing={facing},{state[0]}={'true' if on else 'false'}"
            variants[key] = v
    return {"variants": variants}


def build() -> None:
    for name, make in TEXTURES.items():
        M.save(f"block/decor/{name}", make())
    tags = D.TagCollector()
    lang = {}
    all_models = models()
    assert set(all_models) == set(KINDS) | STATE_MODELS, set(all_models) ^ (set(KINDS) | STATE_MODELS)
    for name in STATE_MODELS:
        variables, elements = all_models[name]
        textures = {var: tex(key) for var, key in variables.items()}
        textures["particle"] = next(iter(textures.values()))
        D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": textures, "elements": elements})
    for name, (placement, tool, english) in KINDS.items():
        variables, elements = all_models[name]
        textures = {var: tex(key) for var, key in variables.items()}
        textures["particle"] = next(iter(textures.values()))
        D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": textures, "elements": elements})
        D.asset_json(f"blockstates/{name}.json", blockstate(name, placement))
        D.item_model_definition(name)
        D.loot_self(name)
        tags.add(f"minecraft:mineable/{tool}", f"{NS}:{name}")
        lang[f"block.{NS}.{name}"] = english
    tags.write("decor")
    D.merge_lang(lang)


if __name__ == "__main__":
    build()
    print(f"{len(KINDS)} decor blocks and {len(TEXTURES)} textures written")
