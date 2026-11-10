"""Item icons and the mod icon.

Item textures are 32x32 (twice vanilla's resolution) and drawn pixel-precise: shapes are placed on
whole pixels, then coloured with seeded noise so materials (leather, paper, rubber) read clearly
at inventory size.

Outputs
  assets/deadlands/textures/item/survivor_journal.png   32x32
  assets/deadlands/icon.png                             128x128 mod icon
"""
from __future__ import annotations

import math

import numpy as np

import dl_glyphs as G
import dl_noise as N


def _rect(mask: np.ndarray, x0: int, y0: int, x1: int, y1: int, value=True) -> None:
    mask[y0:y1 + 1, x0:x1 + 1] = value


def survivor_journal(seed: int = 4101) -> tuple[np.ndarray, np.ndarray]:
    s = 32
    r = N.rng(seed)
    rgb = np.zeros((s, s, 3))
    alpha = np.zeros((s, s))
    yy, xx = np.mgrid[0:s, 0:s]

    cover = np.zeros((s, s), bool)
    _rect(cover, 7, 4, 24, 28)
    for cx, cy in ((7, 4), (24, 4), (7, 28), (24, 28)):
        cover[cy, cx] = False
    pages_side = np.zeros((s, s), bool)
    _rect(pages_side, 25, 5, 26, 27)
    pages_bottom = np.zeros((s, s), bool)
    _rect(pages_bottom, 9, 29, 25, 29)
    note = np.zeros((s, s), bool)
    _rect(note, 13, 1, 18, 4)
    note[1, 18] = False

    # Leather: grain noise, lit from the top-left, darker at the binding.
    grain = r.random((s, s))
    leather = N.ramp(0.55 + 0.25 * (grain - 0.5) - (xx - 7) / 60.0 - (yy - 4) / 90.0,
                     [(0.0, "#2c170c"), (0.45, "#4f2d1a"), (0.75, "#6e4128"), (1.0, "#86563a")])
    rgb[cover] = leather[cover]
    spine = cover & (xx <= 8)
    rgb[spine] = N.ramp(grain, [(0.0, "#1e1009"), (1.0, "#2f1a0e")])[spine]
    for y in range(6, 28, 3):
        rgb[y, 8] = N.hex_rgb("#a48a62")
    rgb[cover & (yy == 4)] = rgb[cover & (yy == 4)] * 1.25
    rgb[cover & (xx == 9)] = rgb[cover & (xx == 9)] * 1.15

    # Page block.
    rgb[pages_side] = N.hex_rgb("#d8ccaa")
    rgb[pages_side & (yy % 2 == 0)] = N.hex_rgb("#bcae8c")
    rgb[pages_bottom] = N.hex_rgb("#c9bc98")

    # Taped label with scribbled writing.
    label = np.zeros((s, s), bool)
    _rect(label, 11, 8, 18, 13)
    rgb[label] = N.ramp(r.random((s, s)) * 0.3 + 0.6, [(0.0, "#b7ab8c"), (1.0, "#ddd3b6")])[label]
    for x, y in ((12, 10), (13, 10), (14, 10), (16, 10), (17, 10), (12, 12), (13, 12), (15, 12)):
        rgb[y, x] = N.hex_rgb("#4a3f36")
    rgb[8, 11:19] = N.hex_rgb("#efe7cf")

    # Elastic strap.
    strap = cover & (xx >= 20) & (xx <= 21)
    rgb[strap] = N.hex_rgb("#141414")
    rgb[cover & (xx == 20)] = N.hex_rgb("#2b2b2b")

    # Note tucked into the top.
    rgb[note] = N.hex_rgb("#e3d9bd")
    rgb[2, 14:17] = N.hex_rgb("#8e8676")

    # Dried blood flecks near the corner.
    for x, y in ((22, 25), (23, 25), (23, 26), (19, 27)):
        rgb[y, x] = N.hex_rgb("#4a120d")

    solid = cover | pages_side | pages_bottom | note
    alpha[solid] = 1.0
    # One-pixel dark outline around the whole silhouette.
    grown = np.zeros_like(solid)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        grown |= np.roll(np.roll(solid, dy, axis=0), dx, axis=1)
    outline = grown & ~solid
    rgb[outline] = N.hex_rgb("#110904")
    alpha[outline] = 1.0
    return rgb, alpha


def mod_icon(seed: int = 4201) -> tuple[np.ndarray, np.ndarray]:
    s = 128
    from gen_gui import plate, rivet
    rgb = plate(s, s, seed, dark="#20262a", mid="#2e353a", light="#414a4f", rust=0.45)
    alpha = np.ones((s, s))
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    unit = 0.62
    text = "DL"
    tw = G.text_width(text) * unit
    x0 = (s - tw) / 2.0
    y0 = (s - 100 * unit) / 2.0 - 6
    letters = N.supersample_mask(lambda d, k: G.draw_text(d, text, x0 * k, y0 * k, unit * k), s, s, 4)
    chips = N.spectral(s, s, beta=1.3, seed=seed + 1)
    paint = letters * N.smoothstep(0.22, 0.34, chips)
    r = N.rng(seed + 2)
    drips = N.supersample_mask(lambda d, k: [d.line([(x * k, (y0 + 100 * unit - 1) * k), (x * k, (y0 + 100 * unit + l) * k)], fill=255, width=int(2 * k))
                                             for x, l in ((x0 + 6, 10), (x0 + 30, 16), (x0 + 47, 7), (x0 + 71, 13))], s, s, 4)
    paint = np.clip(np.maximum(paint, drips * 0.9), 0, 1)
    rgb = N.lerp(rgb, N.solid(s, s, "#ddd2b6") * (0.85 + 0.15 * chips[..., None]), paint)
    for cy, cx in ((9, 9), (9, s - 9), (s - 9, 9), (s - 9, s - 9)):
        rivet(rgb, alpha, cy, cx, 4.0, rust_halo=0.6)
    edge = np.minimum(np.minimum(xx, s - 1 - xx), np.minimum(yy, s - 1 - yy))
    rgb *= (0.55 + 0.45 * N.smoothstep(0, 14, edge))[..., None]
    for region in (np.s_[0, :], np.s_[-1, :], np.s_[:, 0], np.s_[:, -1]):
        rgb[region] = N.hex_rgb("#08090a")
    return rgb, alpha


def _outline(rgb: np.ndarray, alpha: np.ndarray, colour: str = "#120c08") -> None:
    solid = alpha > 0.5
    grown = np.zeros_like(solid)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        grown |= np.roll(np.roll(solid, dy, axis=0), dx, axis=1)
    edge = grown & ~solid
    rgb[edge] = N.hex_rgb(colour)
    alpha[edge] = 1.0


def key_item(seed: int = 4301) -> tuple[np.ndarray, np.ndarray]:
    s = 32
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    r = N.rng(seed)
    d = np.hypot(xx - 9.5, yy - 12.5)
    ring = (d <= 5.5) & (d >= 2.6)
    shaft = (xx >= 14) & (xx <= 27) & (yy >= 11.5) & (yy <= 13.5)
    bit = (xx >= 22) & (xx <= 27) & (yy >= 13.5) & (yy <= 18.5) & ~((xx >= 24) & (xx <= 25) & (yy >= 16.5))
    metal = ring | shaft | bit
    rgb = np.zeros((s, s, 3))
    alpha = np.zeros((s, s))
    tone = N.ramp(0.55 + 0.35 * (r.random((s, s)) - 0.5) - (yy - 12) / 40.0, [(0.0, "#4e4426"), (0.5, "#8a7a4c"), (1.0, "#b39f68")])
    rgb[metal] = tone[metal]
    rust = metal & (r.random((s, s)) > 0.8)
    rgb[rust] = N.hex_rgb("#6b3a1c")
    rgb[metal & (yy == 11.5)] *= 1.25
    alpha[metal] = 1.0
    # Paper tag tied to the ring: the key's label lives here.
    tag = (xx >= 3) & (xx <= 9) & (yy >= 21) & (yy <= 28)
    rgb[tag] = N.ramp(r.random((s, s)) * 0.3 + 0.6, [(0.0, "#bdb294"), (1.0, "#e0d7bc")])[tag]
    for y in (23, 25):
        rgb[y, 4:8] = N.hex_rgb("#5a4e40")
    alpha[tag] = 1.0
    string = [(9, 17), (8, 18), (7, 19), (6, 20)]
    for x, y in string:
        rgb[y, x] = N.hex_rgb("#8c7a5a")
        alpha[y, x] = 1.0
    _outline(rgb, alpha)
    return rgb, alpha


def keycard_item(stripe: str, seed: int) -> tuple[np.ndarray, np.ndarray]:
    s = 32
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    r = N.rng(seed)
    card = (xx >= 4) & (xx <= 27) & (yy >= 8) & (yy <= 23)
    for cx, cy in ((4, 8), (27, 8), (4, 23), (27, 23)):
        card[int(cy), int(cx)] = False
    rgb = np.zeros((s, s, 3))
    alpha = np.zeros((s, s))
    plastic = N.ramp(r.random((s, s)) * 0.2 + 0.7, [(0.0, "#bdbab0"), (1.0, "#e2dfd4")])
    rgb[card] = plastic[card]
    band = card & (yy >= 9) & (yy <= 12)
    rgb[band] = N.hex_rgb(stripe) * (0.9 + 0.1 * r.random((s, s)))[band][:, None]
    chip = (xx >= 7) & (xx <= 10) & (yy >= 15) & (yy <= 18)
    rgb[chip] = N.hex_rgb("#b3974c")
    rgb[chip & ((xx == 8) | (yy == 16))] = N.hex_rgb("#7a6430")
    for y in (15, 17, 19):
        rgb[y, 13:24] = N.hex_rgb("#6a675e")
    scuff = card & (r.random((s, s)) > 0.93)
    rgb[scuff] *= 0.8
    alpha[card] = 1.0
    _outline(rgb, alpha, "#1a1a18")
    return rgb, alpha


def build() -> None:
    import dl_data as D
    N.save_png(N.asset("textures/item/survivor_journal.png"), *survivor_journal())
    N.save_png(N.asset("icon.png"), *mod_icon())
    N.save_png(N.asset("textures/item/key.png"), *key_item())
    for name, colour, seed in (("keycard_green", "#3f7a3a", 4401), ("keycard_blue", "#2f5a8c", 4402), ("keycard_red", "#9a2a22", 4403)):
        N.save_png(N.asset(f"textures/item/{name}.png"), *keycard_item(colour, seed))
    for name in ("key", "keycard_green", "keycard_blue", "keycard_red"):
        D.asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"deadlands:item/{name}"}})
        D.item_model_definition(name, f"deadlands:item/{name}")


if __name__ == "__main__":
    build()
    print("item icons written")
