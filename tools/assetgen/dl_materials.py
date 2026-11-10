"""Tileable 32x32 material generators for DEADLANDS blocks, plus LabPBR normal/specular maps.

Every generator returns a Material: colour, a height field (drives relief shading and the normal
map), and the physically based values shader packs read (smoothness, metalness, porosity).
All noise is periodic, so every texture tiles seamlessly across blocks.

LabPBR 1.3 output, next to each texture:
  <name>_n.png  R,G = normal X/Y (OpenGL, green up), B = ambient occlusion, A = height
  <name>_s.png  R = perceptual smoothness, G = F0 (230 = iron), B = porosity, A = 255 (no emission)
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy import ndimage

import dl_noise as N

T = 32  # block texture resolution


@dataclass
class Material:
    rgb: np.ndarray
    height: np.ndarray
    smooth: np.ndarray | float = 0.15
    metal: np.ndarray | float = 0.0
    porosity: np.ndarray | float = 0.3
    alpha: np.ndarray | None = None
    tags: dict = field(default_factory=dict)


# ----------------------------------------------------------------------------- shared shaping

def relief(m: Material, strength: float = 1.6, ambient: float = 0.78) -> Material:
    """Bake soft top-left lighting and cavity darkening into the colour, as vanilla-style textures do."""
    sh = N.shade(m.height, strength=strength)
    cavity = np.clip(m.height - N.blur(m.height, 2.0), -1, 1)
    light = ambient + (1 - ambient) * 1.9 * sh + 0.35 * cavity
    m.rgb = np.clip(m.rgb * light[..., None], 0, 255)
    return m


def grit(h: int, w: int, seed: int, amount: float) -> np.ndarray:
    r = N.rng(seed)
    return 1.0 + (r.random((h, w)) - 0.5) * amount


def worley_edges(h: int, w: int, points: int, seed: int) -> np.ndarray:
    """0 at cell borders, growing toward cell centres (F2 - F1)."""
    d = N.worley(h, w, points, seed=seed, k=2)
    return d[..., 1] - d[..., 0]


def crack_mask(h: int, w: int, seed: int, density: int = 6, width: float = 0.08, warp: float = 2.0) -> np.ndarray:
    """Thin, branching crack lines (1 = crack)."""
    e = worley_edges(h, w, density, seed)
    wx = (N.spectral(h, w, beta=2.5, seed=seed + 1) - 0.5) * warp * 2
    wy = (N.spectral(h, w, beta=2.5, seed=seed + 2) - 0.5) * warp * 2
    e = N.warp(e, wx, wy)
    keep = N.spectral(h, w, beta=2.0, seed=seed + 3) > 0.42  # not every cell border is cracked
    return (e < width) * keep


def stain_streaks(h: int, w: int, seed: int) -> np.ndarray:
    """Vertical run-off streaks (water, rust) fading downward from random sources."""
    s = N.spectral(h, w, beta=1.6, seed=seed, aniso=(6.0, 1.0))
    return N.smoothstep(0.55, 0.85, s)


# ----------------------------------------------------------------------------- road and stone

def asphalt(seed: int, cracked: bool = False, overgrown: bool = False) -> Material:
    base = N.spectral(T, T, beta=2.6, seed=seed)
    fine = N.spectral(T, T, beta=0.6, seed=seed + 1)
    rgb = N.ramp(0.65 * base + 0.35 * fine, [(0.0, "#232527"), (0.5, "#2e3032"), (1.0, "#3c3e3f")])
    r = N.rng(seed + 2)
    # Exposed aggregate: sparse, low-contrast grit plus a handful of larger 2x1 stones.
    agg = (r.random((T, T)) > 0.91).astype(float)
    for _ in range(7):
        x, y = int(r.integers(0, T)), int(r.integers(0, T))
        agg[y, x] = agg[y, (x + 1) % T] = 1.0
    grit_rgb = N.ramp(r.random((T, T)), [(0.0, "#4b4b48"), (1.0, "#6d6b65")])
    rgb = N.lerp(rgb, grit_rgb, agg * 0.6)
    # Sun-bleached, traffic-polished patches and old oil stains at a larger scale.
    bleach = N.smoothstep(0.55, 0.85, N.spectral(T, T, beta=2.8, seed=seed + 5))
    rgb = N.lerp(rgb, rgb * 1.12, bleach * 0.6)
    oil = N.smoothstep(0.7, 0.9, N.spectral(T, T, beta=2.6, seed=seed + 3))
    rgb = N.lerp(rgb, N.solid(T, T, "#141516"), oil * 0.55)
    height = 0.5 + 0.2 * agg + 0.15 * (fine - 0.5)
    if cracked:
        cr = crack_mask(T, T, seed + 4, density=5, width=0.07)
        rgb = N.lerp(rgb, N.solid(T, T, "#0c0d0d"), cr * 0.95)
        height = height - 0.45 * cr
        if overgrown:
            weeds = cr * (r.random((T, T)) > 0.55)
            rgb = N.lerp(rgb, N.ramp(r.random((T, T)), [(0.0, "#4c5a28"), (1.0, "#6f7d36")]), weeds)
            height = height + 0.5 * weeds
        # Loose chunks lifting at the crack edges.
        lift = ndimage.binary_dilation(cr > 0.5) & (cr < 0.5)
        rgb = N.lerp(rgb, rgb * 1.18, lift * 0.6)
    m = Material(rgb, height, smooth=0.08, porosity=0.55)
    return relief(m, 1.4)


def road_paint(base: Material, colour: str, seed: int, width: int = 8) -> Material:
    """A worn painted stripe running north-south through the middle of the block."""
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    band = (np.abs(xx + 0.5 - T / 2) < width / 2).astype(float)
    wear = N.smoothstep(0.30, 0.55, N.spectral(T, T, beta=1.4, seed=seed))
    paint = band * wear * (N.rng(seed + 1).random((T, T)) > 0.08)
    rgb = N.lerp(base.rgb, N.solid(T, T, colour) * (0.86 + 0.14 * N.spectral(T, T, beta=1.0, seed=seed + 2))[..., None], paint * 0.92)
    smooth = np.where(paint > 0.5, 0.35, 0.08)
    return Material(rgb, base.height + paint * 0.04, smooth=smooth, porosity=np.where(paint > 0.5, 0.1, 0.55))


def concrete(seed: int, variant: str = "plain") -> Material:
    mottle = N.spectral(T, T, beta=2.4, seed=seed)
    fine = N.spectral(T, T, beta=0.7, seed=seed + 1)
    rgb = N.ramp(0.65 * mottle + 0.35 * fine, [(0.0, "#73726c"), (0.5, "#8c8a83"), (1.0, "#a3a199")])
    r = N.rng(seed + 2)
    pores = (r.random((T, T)) > 0.93).astype(float)
    rgb = N.lerp(rgb, rgb * 0.62, pores)
    height = 0.55 + 0.1 * (fine - 0.5) - 0.2 * pores
    # Formwork seam along the top edge (tiles into a line every block).
    rgb[0, :] *= 0.82
    height[0, :] -= 0.15
    if variant in ("cracked", "mossy"):
        cr = crack_mask(T, T, seed + 3, density=4, width=0.032, warp=1.4)
        rgb = N.lerp(rgb, N.solid(T, T, "#33312d"), cr * 0.85)
        height -= 0.4 * cr
    if variant == "stained":
        st = stain_streaks(T, T, seed + 4)
        rust = N.smoothstep(0.6, 0.9, N.spectral(T, T, beta=2.0, seed=seed + 5))
        rgb = N.lerp(rgb, N.solid(T, T, "#5e5444"), st * 0.55)
        rgb = N.lerp(rgb, N.solid(T, T, "#7a4a28"), st * rust * 0.6)
    if variant == "mossy":
        moss = N.smoothstep(0.52, 0.72, N.spectral(T, T, beta=2.2, seed=seed + 6))
        tufts = moss * (r.random((T, T)) > 0.35)
        rgb = N.lerp(rgb, N.ramp(r.random((T, T)), [(0.0, "#3d4a22"), (0.6, "#56662c"), (1.0, "#6f7f3a")]), tufts * 0.9)
        height += 0.25 * tufts
    m = Material(rgb, height, smooth=0.12, porosity=0.6)
    return relief(m, 1.2)


def sidewalk(seed: int) -> Material:
    yy, xx = np.mgrid[0:T, 0:T]
    slab_id = (yy // 16) * 2 + (xx // 16)
    tone = N.rng(seed).uniform(0.92, 1.06, 4)[slab_id]
    base = concrete(seed + 10, "plain")
    rgb = base.rgb * tone[..., None]
    seam = (yy % 16 == 0) | (xx % 16 == 0)
    rgb[seam] = N.hex_rgb("#3d3b36")
    edge = ((yy % 16 == 1) | (xx % 16 == 1))
    rgb[edge] *= 1.1
    edge2 = ((yy % 16 == 15) | (xx % 16 == 15))
    rgb[edge2] *= 0.88
    dirt = seam * (N.rng(seed + 1).random((T, T)) > 0.5)
    rgb = N.lerp(rgb, N.solid(T, T, "#4a4030"), dirt * 0.7)
    cr = crack_mask(T, T, seed + 2, density=3, width=0.045) * (slab_id == N.rng(seed + 3).integers(0, 4))
    rgb = N.lerp(rgb, N.solid(T, T, "#2b2a26"), cr * 0.9)
    height = base.height - 0.35 * seam - 0.3 * cr
    return Material(np.clip(rgb, 0, 255), height, smooth=0.12, porosity=0.6)


def bricks(seed: int, soot: bool = False) -> Material:
    yy, xx = np.mgrid[0:T, 0:T]
    row = yy // 8
    off = (row % 2) * 8
    bx = ((xx + off) // 16) % 2
    brick_id = row * 2 + bx
    r = N.rng(seed)
    tones = r.uniform(0.0, 1.0, 8)[brick_id]
    base = N.ramp(tones * 0.7 + 0.3 * N.spectral(T, T, beta=1.2, seed=seed + 1),
                  [(0.0, "#55332a"), (0.45, "#704234"), (0.8, "#80513f"), (1.0, "#8d604c")])
    mortar = (yy % 8 == 7) | (((xx + off) % 16) == 15)
    rgb = np.where(mortar[..., None], N.ramp(N.spectral(T, T, beta=1.0, seed=seed + 2), [(0, "#6e6a60"), (1, "#8f8a7d")]), base)
    chips = (N.spectral(T, T, beta=0.8, seed=seed + 3) > 0.82) & ~mortar
    rgb[chips] = rgb[chips] * 0.75 + np.array(N.hex_rgb("#a07a62")) * 0.25
    height = np.where(mortar, 0.25, 0.7) + 0.1 * (N.spectral(T, T, beta=1.0, seed=seed + 4) - 0.5) - 0.2 * chips
    # Top bevel of each brick catches light.
    top_edge = (yy % 8 == 0) & ~mortar
    rgb[top_edge] *= 1.12
    if soot:
        s = N.smoothstep(0.25, 0.85, N.spectral(T, T, beta=2.4, seed=seed + 5)) * (1.0 - yy / T * 0.4)
        rgb = N.lerp(rgb, N.solid(T, T, "#151210"), np.clip(s * 0.8, 0, 1))
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.1, porosity=0.5)
    return relief(m, 1.5)


def peel_field(seed: int, count: int = 3) -> np.ndarray:
    """Ragged blobs, rising toward each peel's heart. Every blob stays clear of the tile edges, so
    blocks using different variants of the texture still meet seamlessly."""
    r = N.rng(seed + 7)
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    peel = np.full((T, T), -1.0)
    for i in range(count):
        rad = r.uniform(5.0, 7.0) if i == 0 else r.uniform(3.0, 5.5)
        margin = rad + 2.0
        cx, cy = r.uniform(margin, T - margin), r.uniform(margin / 1.25, T - margin / 1.25)
        dx, dy = xx - cx, yy - cy
        peel = np.maximum(peel, 1.0 - np.sqrt(dx * dx + (dy * 1.25) ** 2) / rad)
    return peel + (N.spectral(T, T, beta=1.3, seed=seed + 8) - 0.5) * 0.7


def plaster(seed: int, paint: str, peeling: bool = False, peel_seed: int | None = None) -> Material:
    trowel = N.spectral(T, T, beta=2.0, seed=seed, aniso=(1.0, 2.5))
    fine = N.spectral(T, T, beta=0.8, seed=seed + 1)
    col = N.solid(T, T, paint) * (0.9 + 0.1 * trowel + 0.06 * (fine - 0.5))[..., None]
    grime = N.smoothstep(0.55, 0.95, N.spectral(T, T, beta=2.2, seed=seed + 2))
    col = N.lerp(col, col * 0.7, grime * 0.6)
    height = 0.6 + 0.08 * trowel
    if peeling:
        # Two or three distinct, ragged peels per block (17% of the wall): the outer paint has
        # flaked off to an older cream coat, and in part of each peel down to bare grey plaster.
        # Thresholded noise reads as camouflage at this size; separate blobs read as peels. The
        # bare core is pushed off-centre by noise so peels never look like concentric rings.
        peel = peel_field(seed + 200 if peel_seed is None else peel_seed)
        t = float(np.quantile(peel, 0.83))
        bare = N.smoothstep(t - 0.03, t + 0.03, peel)
        inside = bare > 0.5
        core_score = np.where(inside, peel + (N.spectral(T, T, beta=1.8, seed=seed + 9) - 0.5) * 1.2, -9.0)
        tc = float(np.quantile(core_score[inside], 0.55))
        core = N.smoothstep(tc - 0.04, tc + 0.04, core_score) * bare
        peel = peel - t  # 0 on the peel edge, for the flakes below
        old_coat = N.ramp(fine, [(0.0, "#b3ad97"), (1.0, "#c3bca6")])
        plaster_rgb = N.ramp(fine, [(0.0, "#98928a"), (1.0, "#aaa396")])
        col = N.lerp(col, old_coat, bare)
        col = N.lerp(col, plaster_rgb, core)
        # Water run-off down the wall, and a few loose flakes along the peel edges.
        streaks = stain_streaks(T, T, seed + 5) * 0.35
        col = N.lerp(col, col * 0.78, streaks)
        flakes = (N.rng(seed + 6).random((T, T)) > 0.88) & (np.abs(peel) < 0.12)
        col[flakes] = col[flakes] * 0.6 + np.array(N.hex_rgb("#cfc6ae")) * 0.4
        height = height - 0.22 * bare - 0.12 * core
    m = Material(np.clip(col, 0, 255), height, smooth=0.2, porosity=0.4)
    return relief(m, 1.0 if not peeling else 1.35, ambient=0.85)


def tiles(seed: int, colour: str = "#d8d6cc", cracked: bool = False) -> Material:
    yy, xx = np.mgrid[0:T, 0:T]
    tile_id = (yy // 8) * 4 + (xx // 8)
    r = N.rng(seed)
    tone = r.uniform(0.93, 1.04, 16)[tile_id]
    rgb = N.solid(T, T, colour) * (tone * (0.97 + 0.05 * N.spectral(T, T, beta=1.2, seed=seed + 1)))[..., None]
    grout = (yy % 8 == 7) | (xx % 8 == 7)
    rgb[grout] = N.hex_rgb("#6f6b62")
    grime = N.smoothstep(0.5, 0.9, N.spectral(T, T, beta=2.3, seed=seed + 2))
    rgb = N.lerp(rgb, N.solid(T, T, "#8a7f66"), grime * 0.35)
    rgb[grout] = rgb[grout] * (0.8 + 0.2 * (1 - grime[grout]))[..., None]
    hl = (yy % 8 == 0) | (xx % 8 == 0)
    rgb[hl & ~grout] *= 1.05
    height = np.where(grout, 0.3, 0.7)
    if cracked:
        cr = crack_mask(T, T, seed + 3, density=4, width=0.05)
        rgb = N.lerp(rgb, N.solid(T, T, "#3a3730"), cr * 0.9)
        height = height - 0.3 * cr
        missing = (tile_id == r.integers(0, 16)) | (tile_id == r.integers(0, 16))
        rgb = np.where(missing[..., None], N.ramp(N.spectral(T, T, beta=1.0, seed=seed + 4), [(0, "#5b574e"), (1, "#7a7568")]), rgb)
        height = np.where(missing, 0.15, height)
    smooth = np.where(grout, 0.1, 0.72)
    m = Material(np.clip(rgb, 0, 255), height, smooth=smooth, porosity=np.where(grout, 0.5, 0.02))
    return relief(m, 0.8, ambient=0.86)


# ----------------------------------------------------------------------------- wood

def wood_grain(h: int, w: int, seed: int, horizontal: bool = True) -> np.ndarray:
    # Long streaks along the board. Smooth noise and wide rings: at 32x32, rough noise or tight
    # ring contours alias into speckle that reads as gravel rather than wood.
    aniso = (1.0, 9.0) if horizontal else (9.0, 1.0)
    g = N.spectral(h, w, beta=2.1, seed=seed, aniso=aniso)
    fibre = N.spectral(h, w, beta=1.2, seed=seed + 50, aniso=(1.0, 14.0) if horizontal else (14.0, 1.0))
    rings = np.sin(g * 9.0) * 0.5 + 0.5
    return np.clip(0.55 * g + 0.3 * rings + 0.15 * fibre, 0, 1)


def boards(seed: int, weathered: str = "#7b6f5f", dark: str = "#3e352b", board_h: int = 8,
           painted: str | None = None, nails: bool = True) -> Material:
    yy, xx = np.mgrid[0:T, 0:T]
    board = yy // board_h
    r = N.rng(seed)
    tone = r.uniform(0.82, 1.08, T // board_h)[board]
    grain = wood_grain(T, T, seed + 1)
    rgb = N.ramp(grain, [(0.0, dark), (0.55, weathered), (1.0, "#a59a88")]) * tone[..., None]
    gap = (yy % board_h) == board_h - 1
    rgb[gap] = N.hex_rgb("#141210")
    under = (yy % board_h) == board_h - 2
    rgb[under] *= 0.78
    top = (yy % board_h) == 0
    rgb[top] *= 1.1
    height = 0.6 + 0.15 * (grain - 0.5) - 0.5 * gap
    if painted:
        # About three quarters of the paint survives. It flakes along the grain and wears first
        # on each board's lower lip, where rain runs off.
        flake = N.spectral(T, T, beta=2.0, seed=seed + 2, aniso=(1.0, 4.0))
        lip = ((yy % board_h) >= board_h - 3).astype(float) * 0.22
        score = flake + lip
        t = float(np.quantile(score, 0.76))
        paint_keep = 1.0 - N.smoothstep(t - 0.025, t + 0.025, score)
        paint_rgb = N.solid(T, T, painted) * (0.88 + 0.12 * grain)[..., None]
        rgb = N.lerp(rgb, paint_rgb, paint_keep * (~gap))
        height = height + 0.06 * paint_keep
    if nails:
        for b in range(T // board_h):
            for x0 in (3, T - 5):
                y0 = b * board_h + board_h // 2 - 1
                rgb[y0:y0 + 2, x0:x0 + 2] = N.hex_rgb("#2a2622")
                rgb[y0, x0] = N.hex_rgb("#6e5a48")
                height[y0:y0 + 2, x0:x0 + 2] += 0.15
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.1, porosity=0.45)
    return relief(m, 1.3)


# ----------------------------------------------------------------------------- metal

def metal_base(seed: int, light: str = "#7c8184", dark: str = "#4a4f52") -> tuple[np.ndarray, np.ndarray]:
    brushed = N.spectral(T, T, beta=1.1, seed=seed, aniso=(1.0, 6.0))
    blot = N.spectral(T, T, beta=2.3, seed=seed + 1)
    rgb = N.ramp(0.5 * brushed + 0.5 * blot, [(0.0, dark), (1.0, light)])
    return rgb, blot


def rust_layer(seed: int, amount: float) -> tuple[np.ndarray, np.ndarray]:
    rn = N.spectral(T, T, beta=2.0, seed=seed)
    pits = N.worley(T, T, 24, seed=seed + 1)
    mask = np.clip(N.smoothstep(1.0 - amount, 1.0 - amount + 0.25, rn) * (0.6 + 0.4 * N.smoothstep(0.1, 0.5, pits)), 0, 1)
    col = N.ramp(N.spectral(T, T, beta=1.2, seed=seed + 2), [(0.0, "#3a1d10"), (0.45, "#6b371c"), (0.8, "#8e4e28"), (1.0, "#a5632f")])
    return col, mask


def sheet_metal(seed: int, rust: float = 0.55) -> Material:
    rgb, blot = metal_base(seed)
    rc, rmask = rust_layer(seed + 3, rust)
    rgb = N.lerp(rgb, rc, rmask)
    yy, xx = np.mgrid[0:T, 0:T]
    seam = (xx == 0) | (yy == 0)
    rgb[seam] *= 0.6
    for y0 in (3, 19):
        for x0 in (3, 19):
            rgb[y0:y0 + 2, x0:x0 + 2] = N.hex_rgb("#2a2c2d")
            rgb[y0, x0] = N.hex_rgb("#9aa0a2")
    st = stain_streaks(T, T, seed + 4)
    rgb = N.lerp(rgb, N.solid(T, T, "#5a3018"), st * rmask * 0.6)
    height = 0.6 + 0.1 * (blot - 0.5) + 0.12 * rmask - 0.3 * seam
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.55 * (1 - rmask) + 0.08, metal=(rmask < 0.35).astype(float), porosity=0.05 + 0.4 * rmask)
    return relief(m, 1.2)


def corrugated(seed: int, rust: float = 0.5) -> Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    ribs = np.sin(xx / T * 2 * np.pi * 4) * 0.5 + 0.5
    rgb, blot = metal_base(seed, light="#8a8f90", dark="#565b5d")
    rc, rmask = rust_layer(seed + 3, rust)
    rmask = np.clip(rmask + stain_streaks(T, T, seed + 5) * 0.5 * rust, 0, 1)
    rgb = N.lerp(rgb, rc, rmask)
    rgb *= (0.72 + 0.4 * ribs)[..., None]
    height = 0.3 + 0.5 * ribs + 0.05 * blot
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.5 * (1 - rmask) + 0.08, metal=(rmask < 0.35).astype(float), porosity=0.05 + 0.4 * rmask)
    return m


def diamond_plate(seed: int) -> Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    rgb, blot = metal_base(seed, light="#9ba1a4", dark="#5d6265")
    height = 0.4 + 0.05 * blot
    for cy in range(0, T, 8):
        for cx in range(0, T, 8):
            ox = 4 if (cy // 8) % 2 else 0
            px, py = (cx + ox) % T, cy + 4
            ang = 0.7 if ((cx // 8 + cy // 8) % 2) else -0.7
            dx = (xx - px + T / 2) % T - T / 2
            dy = (yy - py + T / 2) % T - T / 2
            u = dx * np.cos(ang) + dy * np.sin(ang)
            v = -dx * np.sin(ang) + dy * np.cos(ang)
            bump = np.clip(1.0 - (np.abs(u) / 3.2) ** 2 - (np.abs(v) / 0.9) ** 2, 0, 1)
            height = np.maximum(height, 0.4 + 0.5 * bump)
    wear = N.smoothstep(0.55, 0.75, height)
    rgb = N.lerp(rgb, N.solid(T, T, "#c4c8c9"), wear * 0.5)
    rc, rmask = rust_layer(seed + 3, 0.25)
    rgb = N.lerp(rgb, rc, rmask * 0.7)
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.6, metal=1.0 - rmask, porosity=0.05)
    return relief(m, 2.0)


# ----------------------------------------------------------------------------- ground

def rubble(seed: int) -> Material:
    d = N.worley(T, T, 14, seed=seed, k=2)
    cells = N.worley(T, T, 14, seed=seed)
    edge = d[..., 1] - d[..., 0]
    r = N.rng(seed + 1)
    # Each chunk is concrete or brick, decided by a coarse noise field.
    which = N.spectral(T, T, beta=3.0, seed=seed + 2) > 0.62
    conc = N.ramp(N.spectral(T, T, beta=1.0, seed=seed + 3), [(0, "#6c6a64"), (1, "#9a978e")])
    brick = N.ramp(N.spectral(T, T, beta=1.0, seed=seed + 4), [(0, "#5e2c1e"), (1, "#8c4a32")])
    rgb = np.where(which[..., None], brick, conc)
    gap = edge < 0.12
    rgb[gap] = N.hex_rgb("#1b1916")
    dust = r.random((T, T)) > 0.9
    rgb[dust] = rgb[dust] * 0.6 + np.array(N.hex_rgb("#b5ae9d")) * 0.4
    height = np.clip(1.0 - cells * 0.9, 0, 1) * (~gap)
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.08, porosity=0.6)
    return relief(m, 2.2, ambient=0.7)


def ash(seed: int) -> Material:
    ripple = N.spectral(T, T, beta=2.6, seed=seed, aniso=(1.0, 3.0))
    fine = N.spectral(T, T, beta=0.5, seed=seed + 1)
    rgb = N.ramp(0.6 * ripple + 0.4 * fine, [(0.0, "#6d6b67"), (0.6, "#8f8c86"), (1.0, "#aaa79f")])
    specks = N.rng(seed + 2).random((T, T)) > 0.94
    rgb[specks] = N.hex_rgb("#2a2826")
    m = Material(rgb, 0.5 + 0.2 * ripple, smooth=0.05, porosity=0.8)
    return relief(m, 1.0, ambient=0.85)


def dry_dirt(seed: int) -> Material:
    e = worley_edges(T, T, 22, seed)
    plate = N.spectral(T, T, beta=1.5, seed=seed + 1)
    fine = N.spectral(T, T, beta=0.6, seed=seed + 3)
    rgb = N.ramp(0.6 * plate + 0.4 * fine, [(0.0, "#664f35"), (0.6, "#7a6044"), (1.0, "#8d7151")])
    crack = (e < 0.045) & (N.spectral(T, T, beta=2.0, seed=seed + 4) > 0.35)
    rgb[crack] = N.hex_rgb("#3a2c1d")
    pebbles = N.rng(seed + 2).random((T, T)) > 0.95
    rgb[pebbles] = rgb[pebbles] * 0.7 + np.array(N.hex_rgb("#a39a8a")) * 0.3
    height = 0.5 + 0.15 * (fine - 0.5) - 0.3 * crack
    m = Material(np.clip(rgb, 0, 255), height, smooth=0.04, porosity=0.75)
    return relief(m, 1.1)


def dead_grass_top(seed: int) -> Material:
    blades = N.spectral(T, T, beta=0.7, seed=seed, aniso=(3.0, 1.0))
    clump = N.spectral(T, T, beta=2.0, seed=seed + 1)
    rgb = N.ramp(0.6 * blades + 0.4 * clump, [(0.0, "#5c4a2c"), (0.4, "#8a7444"), (0.75, "#a68f58"), (1.0, "#bba56c")])
    soil = N.smoothstep(0.62, 0.8, N.spectral(T, T, beta=2.0, seed=seed + 2))
    rgb = N.lerp(rgb, dry_dirt(seed + 5).rgb, soil * 0.7)
    m = Material(rgb, 0.5 + 0.3 * blades, smooth=0.05, porosity=0.7)
    return relief(m, 1.0)


def dead_grass_side(seed: int, top: Material, dirt: Material) -> Material:
    yy, xx = np.mgrid[0:T, 0:T]
    r = N.rng(seed)
    fringe = 5 + r.integers(0, 6, T)
    fringe = ndimage.uniform_filter1d(fringe.astype(float), 3, mode="wrap")
    grass = yy < fringe[None, :]
    rgb = np.where(grass[..., None], top.rgb, dirt.rgb)
    edge = (yy >= fringe[None, :]) & (yy < fringe[None, :] + 1.5)
    rgb[edge] *= 0.7
    return Material(rgb, np.where(grass, top.height, dirt.height), smooth=0.05, porosity=0.7)


def broken_glass(seed: int) -> Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    tint = N.solid(T, T, "#a9bcc0")
    grime = N.smoothstep(0.45, 0.9, N.spectral(T, T, beta=2.0, seed=seed))
    edge_dist = np.minimum(np.minimum(xx, T - 1 - xx), np.minimum(yy, T - 1 - yy))
    edge_grime = N.smoothstep(4, 0, edge_dist)
    cracks = crack_mask(T, T, seed + 1, density=3, width=0.022, warp=0.8)
    r = np.hypot(xx - 20, yy - 12)
    impact = np.exp(-(r ** 2) / 4.0)
    angle = np.arctan2(yy - 12, xx - 20)
    spokes = (np.abs(np.sin(angle * 4 + 0.6)) < 0.07) * (r < 13) * (r > 1.5)
    ring = (np.abs(r - 5.0) < 0.5) * (np.sin(angle * 9) > 0.2)
    crack = np.clip(cracks * 0.8 + spokes + ring * 0.8 + impact * 0.7, 0, 1)
    rgb = N.lerp(tint, N.solid(T, T, "#6d6556"), np.clip(grime * 0.6 + edge_grime * 0.7, 0, 1))
    rgb = N.lerp(rgb, N.solid(T, T, "#eef3f2"), crack * 0.85)
    frame = edge_dist < 1
    rgb[frame] = N.hex_rgb("#4a4c4a")
    alpha = np.clip(0.28 + 0.45 * grime + 0.5 * edge_grime + 0.6 * crack, 0, 1)
    alpha[frame] = 1.0
    m = Material(rgb, 0.5 + 0.3 * crack, smooth=0.9, porosity=0.0, alpha=alpha)
    return m


# ----------------------------------------------------------------------------- output

def save(rel_no_ext: str, m: Material, pbr: bool = True, root=None) -> None:
    """Write <rel>.png and (if pbr) LabPBR <rel>_n.png / <rel>_s.png under assets/<ns>/textures/."""
    base = (root or N.ASSETS) / "textures"
    N.save_png(base / f"{rel_no_ext}.png", m.rgb, m.alpha)
    if not pbr:
        return
    h = m.height
    gy = ndimage.sobel(h, axis=0, mode="wrap")
    gx = ndimage.sobel(h, axis=1, mode="wrap")
    nx, ny, nz = -gx * 2.0, gy * 2.0, np.ones_like(h)  # OpenGL convention: green points up
    n = np.sqrt(nx * nx + ny * ny + nz * nz)
    ao = np.clip(1.0 - np.clip(N.blur(h, 2.0) - h, 0, 1) * 2.5, 0, 1)
    normal = np.dstack([(nx / n * 0.5 + 0.5) * 255, (ny / n * 0.5 + 0.5) * 255, ao * 255])
    N.save_png(base / f"{rel_no_ext}_n.png", normal, np.clip(h, 0, 1))

    def field(v):
        return v if isinstance(v, np.ndarray) else np.full(h.shape, float(v))

    smooth = field(m.smooth)
    metal = field(m.metal)
    f0 = np.where(metal > 0.5, 230.0, 10.0)
    porosity = np.clip(field(m.porosity), 0, 1) * 64.0
    spec = np.dstack([np.clip(smooth, 0, 1) * 255, f0, porosity])
    N.save_png(base / f"{rel_no_ext}_s.png", spec, np.ones(h.shape))
