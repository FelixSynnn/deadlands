"""DEADLANDS restyle of the core vanilla environment (written under assets/minecraft, 32x32).

Vegetation textures stay greyscale because the game tints them with the biome colour maps; the
DEADLANDS colour maps below are drabber (olive, straw, grey-green), which mutes every vanilla
biome at once. Planned for later passes: water, ores, the remaining wood types, flowers, crops.
"""
from __future__ import annotations

import numpy as np
from scipy import ndimage

import dl_materials as M
import dl_noise as N

T = M.T
MC = N.ASSETS.parent / "minecraft"


def save(name: str, rgb: np.ndarray, alpha: np.ndarray | None = None) -> None:
    N.save_png(MC / "textures" / "block" / f"{name}.png", rgb, alpha)


def grey(v: np.ndarray, lo: float = 60, hi: float = 200) -> np.ndarray:
    g = lo + (hi - lo) * np.clip(v, 0, 1)
    return np.repeat(g[..., None], 3, axis=2)


def blades(seed: int, density: float = 1.0) -> np.ndarray:
    """Greyscale tangle of grass blades for the (tinted) grass block top."""
    fine = N.spectral(T, T, beta=0.6, seed=seed, aniso=(2.5, 1.0))
    clump = N.spectral(T, T, beta=2.0, seed=seed + 1)
    v = 0.55 * fine + 0.45 * clump
    r = N.rng(seed + 2)
    tips = r.random((T, T)) > 0.9
    v[tips] += 0.25
    return np.clip(v, 0, 1)


def soil(seed: int, coarse: bool = False) -> M.Material:
    base = N.spectral(T, T, beta=1.6, seed=seed)
    fine = N.spectral(T, T, beta=0.6, seed=seed + 1)
    rgb = N.ramp(0.6 * base + 0.4 * fine, [(0.0, "#3f3225"), (0.5, "#56442f"), (1.0, "#6d583e")])
    r = N.rng(seed + 2)
    pebbles = r.random((T, T)) > (0.86 if coarse else 0.94)
    rgb[pebbles] = rgb[pebbles] * 0.5 + np.array(N.hex_rgb("#8a8478")) * 0.5
    roots = (N.spectral(T, T, beta=0.9, seed=seed + 3, aniso=(1.0, 4.0)) > 0.86)
    rgb[roots] = rgb[roots] * 0.7 + np.array(N.hex_rgb("#2a2018")) * 0.3
    m = M.Material(rgb, 0.5 + 0.2 * fine + 0.3 * pebbles, smooth=0.05, porosity=0.7)
    return M.relief(m, 1.4)


def rock(seed: int) -> M.Material:
    strata = N.spectral(T, T, beta=2.0, seed=seed, aniso=(1.0, 3.0))
    fine = N.spectral(T, T, beta=0.8, seed=seed + 1)
    rgb = N.ramp(0.6 * strata + 0.4 * fine, [(0.0, "#55575a"), (0.5, "#6c6e70"), (1.0, "#86888a")])
    cr = M.crack_mask(T, T, seed + 2, density=4, width=0.03, warp=1.5)
    rgb = N.lerp(rgb, N.solid(T, T, "#2c2d2f"), cr * 0.8)
    m = M.Material(rgb, 0.5 + 0.25 * strata - 0.3 * cr, smooth=0.12, porosity=0.2)
    return M.relief(m, 1.6)


def cobble(seed: int) -> M.Material:
    d = N.worley(T, T, 11, seed=seed, k=2)
    edge = d[..., 1] - d[..., 0]
    cells = N.worley(T, T, 11, seed=seed)
    tone = N.spectral(T, T, beta=2.6, seed=seed + 1)
    rgb = N.ramp(0.5 * tone + 0.5 * N.spectral(T, T, beta=0.9, seed=seed + 2), [(0.0, "#4e5052"), (0.6, "#6e7072"), (1.0, "#8a8c8c")])
    gap = edge < 0.13
    rgb[gap] = N.hex_rgb("#26272a")
    moss = N.smoothstep(0.7, 0.9, N.spectral(T, T, beta=2.2, seed=seed + 3)) * gap
    rgb = N.lerp(rgb, N.solid(T, T, "#46512e"), moss)
    m = M.Material(rgb, np.clip(1.0 - cells, 0, 1) * (~gap), smooth=0.1, porosity=0.3)
    return M.relief(m, 2.0, ambient=0.72)


def gravel(seed: int) -> M.Material:
    cells = N.worley(T, T, 60, seed=seed)
    tone = N.rng(seed + 1).random((T, T))
    rgb = N.ramp(ndimage.uniform_filter(tone, 2, mode="wrap"), [(0.0, "#4f4b47"), (0.5, "#6d6862"), (1.0, "#8d877e")])
    m = M.Material(rgb, np.clip(1.0 - cells, 0, 1), smooth=0.08, porosity=0.5)
    return M.relief(m, 2.2, ambient=0.7)


def sand(seed: int) -> M.Material:
    ripple = N.spectral(T, T, beta=2.4, seed=seed, aniso=(1.0, 4.0))
    grain = N.rng(seed + 1).random((T, T))
    rgb = N.ramp(0.7 * ripple + 0.3 * grain, [(0.0, "#9c8f6c"), (0.5, "#b3a580"), (1.0, "#c7b994")])
    m = M.Material(rgb, 0.5 + 0.3 * ripple, smooth=0.05, porosity=0.6)
    return M.relief(m, 0.9, ambient=0.86)


def bark(seed: int, light: str, dark: str, birch: bool = False) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    ridges = N.spectral(T, T, beta=1.4, seed=seed, aniso=(8.0, 1.0))
    furrow = np.sin(xx / T * 2 * np.pi * 5 + ridges * 4) * 0.5 + 0.5
    if birch:
        rgb = N.ramp(0.7 * ridges + 0.3 * furrow, [(0.0, "#a9a69c"), (1.0, "#d6d2c6")])
        marks = (N.spectral(T, T, beta=1.5, seed=seed + 1, aniso=(1.0, 5.0)) > 0.78)
        rgb[marks] = N.hex_rgb("#2b2824")
        h = 0.5 + 0.2 * ridges - 0.3 * marks
    else:
        rgb = N.ramp(0.5 * ridges + 0.5 * furrow, [(0.0, dark), (1.0, light)])
        h = 0.3 + 0.6 * furrow
    lichen = N.smoothstep(0.75, 0.92, N.spectral(T, T, beta=2.2, seed=seed + 2))
    rgb = N.lerp(rgb, N.solid(T, T, "#7d8462"), lichen * 0.5)
    m = M.Material(rgb, h, smooth=0.06, porosity=0.6)
    return M.relief(m, 1.8)


def log_top(seed: int, wood: str, bark_hex: str) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    d = np.hypot(xx - 15.5, yy - 15.5)
    wob = N.spectral(T, T, beta=2.0, seed=seed)
    rings = np.sin((d + wob * 3) * 1.6) * 0.5 + 0.5
    rgb = N.ramp(rings, [(0.0, "#5e4a33"), (1.0, wood)])
    rim = d > 13.5
    rgb[rim] = N.solid(T, T, bark_hex)[rim] * (0.8 + 0.2 * wob[rim])[..., None]
    cr = (np.abs(np.arctan2(yy - 15.5, xx - 15.5) - 0.6) < 0.05) & (d < 12)
    rgb[cr] *= 0.5
    m = M.Material(rgb, 0.5 + 0.1 * rings, smooth=0.08, porosity=0.5)
    return M.relief(m, 1.0)


def leaves(seed: int, holes: float = 0.36) -> tuple[np.ndarray, np.ndarray]:
    cells = N.worley(T, T, 40, seed=seed)
    tone = N.spectral(T, T, beta=1.2, seed=seed + 1)
    v = np.clip(0.35 + 0.5 * tone + 0.25 * (1 - cells), 0, 1)
    alpha = (N.spectral(T, T, beta=1.0, seed=seed + 2) > holes).astype(float)
    dead = N.rng(seed + 3).random((T, T)) > 0.93
    v[dead] = 0.95
    return grey(v, 40, 190), alpha


def grass_plant(seed: int, tall: str | None = None) -> tuple[np.ndarray, np.ndarray]:
    r = N.rng(seed)
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    alpha = np.zeros((T, T))
    v = np.zeros((T, T))
    for _ in range(14):
        x0 = r.uniform(2, T - 2)
        top = r.uniform(4 if tall != "bottom" else -4, 20)
        lean = r.uniform(-0.35, 0.35)
        for y in range(int(max(0, top)), T):
            x = x0 + lean * (y - top) * 0.6
            ix = int(round(x)) % T
            alpha[y, ix] = 1.0
            v[y, ix] = 0.4 + 0.5 * (1 - (y - top) / max(1.0, T - top))
    if tall == "top":
        alpha[int(T * 0.9):, :] = alpha[int(T * 0.9):, :]
    return grey(v, 50, 210), alpha


def colormap(foliage: bool) -> np.ndarray:
    s = 256
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    temperature = 1.0 - xx / 255.0
    humidity = 1.0 - yy / 255.0
    straw = N.solid(s, s, "#8f8455" if not foliage else "#6f6a3e")
    olive = N.solid(s, s, "#6b7a45" if not foliage else "#4e5c32")
    cold = N.solid(s, s, "#6f7a6a" if not foliage else "#56604f")
    col = N.lerp(straw, olive, np.clip(humidity * 1.3, 0, 1))
    col = N.lerp(cold, col, np.clip(temperature * 1.6, 0, 1))
    return col


def build() -> None:
    top = blades(9101)
    save("grass_block_top", grey(top, 70, 205))
    dirt = soil(9102)
    save("dirt", dirt.rgb)
    save("coarse_dirt", soil(9103, coarse=True).rgb)
    yy = np.mgrid[0:T, 0:T][0]
    r = N.rng(9104)
    fringe = ndimage.uniform_filter1d(5 + r.integers(0, 6, T).astype(float), 3, mode="wrap")
    grass_rows = yy < fringe[None, :]
    side = dirt.rgb.copy()
    side[grass_rows] = N.hex_rgb("#6f7348")
    save("grass_block_side", side)
    overlay = np.where(grass_rows, top, 0.0)
    save("grass_block_side_overlay", grey(overlay, 70, 205), grass_rows.astype(float))
    save("stone", rock(9105).rgb)
    save("cobblestone", cobble(9106).rgb)
    save("gravel", gravel(9107).rgb)
    save("sand", sand(9108).rgb)
    save("oak_log", bark(9109, "#6e6250", "#2f281f").rgb)
    save("oak_log_top", log_top(9110, "#8c7856", "#3a3025").rgb)
    save("oak_planks", M.boards(9111, weathered="#7c6a52", dark="#3d3226", nails=False).rgb)
    save("spruce_log", bark(9112, "#4a3f33", "#1f1914").rgb)
    save("spruce_log_top", log_top(9113, "#6e5a40", "#2a221a").rgb)
    save("spruce_planks", M.boards(9114, weathered="#5d4c3a", dark="#2a2119", nails=False).rgb)
    save("birch_log", bark(9115, "#d6d2c6", "#a9a69c", birch=True).rgb)
    save("birch_log_top", log_top(9116, "#b9a985", "#cfcabd").rgb)
    for name, seed in (("oak_leaves", 9117), ("spruce_leaves", 9118), ("birch_leaves", 9119)):
        save(name, *leaves(seed))
    save("short_grass", *grass_plant(9120))
    save("fern", *grass_plant(9121))
    save("tall_grass_bottom", *grass_plant(9122, "bottom"))
    save("tall_grass_top", *grass_plant(9123, "top"))
    N.save_png(MC / "textures" / "colormap" / "grass.png", colormap(False))
    N.save_png(MC / "textures" / "colormap" / "foliage.png", colormap(True))


if __name__ == "__main__":
    build()
    print("vanilla environment restyled")
