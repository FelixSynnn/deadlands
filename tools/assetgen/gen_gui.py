"""DEADLANDS interface art.

Outputs
  assets/deadlands/textures/gui/sprites/   DEADLANDS widgets, tabs, icons, HUD stroke (+ .mcmeta)
  assets/minecraft/textures/gui/sprites/   restyled vanilla widgets so every menu matches
  assets/minecraft/textures/gui/           restyled vanilla menu backgrounds and separators
  assets/deadlands/textures/gui/journal/book.png   the Survivor's Journal spread (768x480)

Nine-slice sprites are built from periodic noise so the engine can tile their centres and edges
at any size without visible seams.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

import dl_noise as N

MC_ASSETS = N.ASSETS.parent / "minecraft"


# ----------------------------------------------------------------------------- helpers

def save_sprite(root: Path, rel: str, rgb: np.ndarray, alpha: np.ndarray | None = None, scaling: dict | None = None) -> Path:
    path = root / "textures/gui/sprites" / rel
    N.save_png(path, rgb, alpha)
    meta = path.with_name(path.name + ".mcmeta")
    if scaling:
        meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2) + "\n", encoding="utf-8")
    elif meta.exists():
        meta.unlink()
    return path


def nine(w: int, h: int, border) -> dict:
    return {"type": "nine_slice", "width": w, "height": h, "border": border}


def paint(rgb: np.ndarray, region, colour: str, a: float = 1.0) -> None:
    c = np.array(N.hex_rgb(colour))
    rgb[region] = rgb[region] * (1.0 - a) + c * a


def plate(h: int, w: int, seed: int, dark="#1d2225", mid="#2a3034", light="#394044", rust=0.25) -> np.ndarray:
    """Periodic worn gunmetal: brushed streaks, grime, rust pitting, light relief."""
    streak = N.spectral(h, w, beta=1.1, seed=seed, aniso=(1.0, 7.0))
    grime = N.spectral(h, w, beta=2.4, seed=seed + 1)
    col = N.ramp(0.55 * streak + 0.45 * grime, [(0.0, dark), (0.55, mid), (1.0, light)])
    rn = N.spectral(h, w, beta=2.0, seed=seed + 2)
    pits = N.worley(h, w, max(4, h * w // 160), seed=seed + 3)
    rmask = np.clip(N.smoothstep(0.62, 0.86, rn) * (0.55 + 0.45 * N.smoothstep(0.15, 0.6, pits)) * rust * 2.0, 0.0, 1.0)
    rc = N.ramp(N.spectral(h, w, beta=1.5, seed=seed + 4), [(0.0, "#3a1d10"), (0.5, "#6b371c"), (1.0, "#8e4e28")])
    col = N.lerp(col, rc, rmask)
    sh = N.shade(grime * 0.6 + rmask * 0.9, strength=1.4)
    return col * (0.86 + 0.28 * sh)[..., None]


def desaturate(rgb: np.ndarray, amount: float) -> np.ndarray:
    lum = rgb @ np.array([0.299, 0.587, 0.114])
    return N.lerp(rgb, np.repeat(lum[..., None], 3, axis=2), amount)


def frame(rgb: np.ndarray, outline="#0a0c0d", hi="#6c7579", lo="#0d0f10", hi_a=0.55, lo_a=0.75, inset=False) -> None:
    """1px outline plus a 1px bevel (raised, or sunken when inset=True)."""
    top_c, bot_c = (lo, hi) if inset else (hi, lo)
    top_a, bot_a = (lo_a, hi_a * 0.6) if inset else (hi_a, lo_a)
    paint(rgb, np.s_[1, 1:-1], top_c, top_a)
    paint(rgb, np.s_[1:-1, 1], top_c, top_a * 0.7)
    paint(rgb, np.s_[-2, 1:-1], bot_c, bot_a)
    paint(rgb, np.s_[1:-1, -2], bot_c, bot_a * 0.8)
    for region in (np.s_[0, :], np.s_[-1, :], np.s_[:, 0], np.s_[:, -1]):
        paint(rgb, region, outline, 1.0)


def rivet(rgb: np.ndarray, alpha: np.ndarray, cy: float, cx: float, r: float = 2.2, rust_halo: float = 0.0) -> None:
    h, w = alpha.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.sqrt((yy + 0.5 - cy) ** 2 + (xx + 0.5 - cx) ** 2)
    if rust_halo > 0:
        halo = np.clip(1.0 - (d - r) / 2.5, 0.0, 1.0) * (d > r) * rust_halo
        rgb[:] = N.lerp(rgb, N.solid(h, w, "#5e3019"), halo)
    body = np.clip(r + 0.5 - d, 0.0, 1.0)
    light = np.clip(1.0 - np.sqrt((yy + 0.5 - (cy - r * 0.4)) ** 2 + (xx + 0.5 - (cx - r * 0.4)) ** 2) / (r * 1.6), 0.0, 1.0)
    dome = N.lerp(N.solid(h, w, "#22272a"), N.solid(h, w, "#8a9397"), light)
    rgb[:] = N.lerp(rgb, dome, body)


def ascii_icon(rows: list[str], palette: dict[str, str]) -> tuple[np.ndarray, np.ndarray]:
    h, w = len(rows), len(rows[0])
    rgb = np.zeros((h, w, 3))
    alpha = np.zeros((h, w))
    for y, row in enumerate(rows):
        assert len(row) == w, f"icon row {y} has width {len(row)}"
        for x, ch in enumerate(row):
            if ch in palette:
                rgb[y, x] = N.hex_rgb(palette[ch])
                alpha[y, x] = 1.0
    return rgb, alpha


# ----------------------------------------------------------------------------- widgets

def button_set(root: Path, prefix: str, border: int, seed: int) -> None:
    w, h = 200, 20
    base = N.tile_window(plate(h - 2 * border, w - 2 * border, seed, rust=0.18), h, w, border, border)
    ones = np.ones((h, w))

    normal = base.copy()
    frame(normal)
    save_sprite(root, prefix + "button.png", normal, ones, nine(w, h, border))

    hot = N.lerp(base, N.solid(h, w, "#4a3b24"), 0.22) * 1.1
    frame(hot, outline="#c8902e", hi="#f0c774", lo="#2a1c0c", hi_a=0.45)
    save_sprite(root, prefix + "button_highlighted.png", hot, ones, nine(w, h, border))

    off = desaturate(base, 0.7) * 0.55
    frame(off, outline="#08090a", hi="#3a4044", hi_a=0.3, lo_a=0.5)
    save_sprite(root, prefix + "button_disabled.png", off, ones, nine(w, h, border))


def slider_set(root: Path, prefix: str, border: int, seed: int) -> None:
    w, h = 200, 20
    track = N.tile_window(plate(h - 2 * border, w - 2 * border, seed, dark="#0b0d0e", mid="#13171a", light="#1c2124", rust=0.08), h, w, border, border)
    paint(track, np.s_[h // 2 - 1:h // 2 + 1, 2:-2], "#050606", 0.8)
    paint(track, np.s_[h // 2 + 1, 2:-2], "#3a4144", 0.35)
    for name, outline in (("slider.png", "#0a0c0d"), ("slider_highlighted.png", "#c8902e")):
        img = track.copy()
        frame(img, outline=outline, inset=True)
        save_sprite(root, prefix + name, img, np.ones((h, w)), nine(w, h, border))

    hw, hh = 8, 20
    knob = plate(hh, hw, seed + 5, dark="#3b4246", mid="#4c565a", light="#5f6a6e", rust=0.1)
    for y in (7, 10, 13):
        paint(knob, np.s_[y, 2:-2], "#1a1e20", 0.85)
        paint(knob, np.s_[y + 1, 2:-2], "#8a9498", 0.45)
    handle_border = {"left": 2, "top": 2, "right": 2, "bottom": 3}
    for name, outline in (("slider_handle.png", "#0a0c0d"), ("slider_handle_highlighted.png", "#e0a640")):
        img = knob.copy()
        frame(img, outline=outline, hi="#9aa4a8")
        save_sprite(root, prefix + name, img, np.ones((hh, hw)), nine(hw, hh, handle_border))


def text_field_set(seed: int) -> None:
    w, h = 200, 20
    base = N.tile_window(plate(h - 2, w - 2, seed, dark="#08090a", mid="#0d1011", light="#14181a", rust=0.0), h, w, 1, 1)
    for name, outline in (("widget/text_field.png", "#3c4447"), ("widget/text_field_highlighted.png", "#c8902e")):
        img = base.copy()
        paint(img, np.s_[0, :], outline)
        paint(img, np.s_[-1, :], outline)
        paint(img, np.s_[:, 0], outline)
        paint(img, np.s_[:, -1], outline)
        save_sprite(MC_ASSETS, name, img, np.ones((h, w)), nine(w, h, 1))


def checkbox_set(seed: int) -> None:
    s = 20
    base = plate(s, s, seed, dark="#0b0d0e", mid="#111416", light="#181c1f", rust=0.05)
    tick = N.supersample_mask(lambda d, k: d.line([(4.5 * k, 10.5 * k), (8.5 * k, 14.5 * k), (15.5 * k, 5 * k)], fill=255, width=int(2.6 * k), joint="curve"), s, s, 4)
    for selected in (False, True):
        for hot in (False, True):
            img = base.copy()
            frame(img, outline="#c8902e" if hot else "#3c4447", inset=True)
            if selected:
                img = N.lerp(img, N.solid(s, s, "#e3dccb"), tick)
            name = "widget/checkbox" + ("_selected" if selected else "") + ("_highlighted" if hot else "") + ".png"
            save_sprite(MC_ASSETS, name, img, np.ones((s, s)))


def scroller_set(seed: int) -> None:
    w, h = 6, 32
    bar = N.tile_window(plate(h - 2, w - 2, seed, dark="#4a5357", mid="#59626a", light="#6c767c", rust=0.0), h, w, 1, 1)
    frame(bar, hi="#a0a9ad")
    save_sprite(MC_ASSETS, "widget/scroller.png", bar, np.ones((h, w)), nine(w, h, 1))
    bg = N.solid(h, w, "#0b0d0e")
    save_sprite(MC_ASSETS, "widget/scroller_background.png", bg, np.full((h, w), 0.85), nine(w, h, 1))


def menu_backgrounds(seed: int) -> None:
    for i, (name, alpha, dark) in enumerate((("menu_background", 0.80, "#0d1012"), ("menu_list_background", 0.88, "#0a0c0e"),
                                             ("inworld_menu_background", 0.55, "#0d1012"), ("inworld_menu_list_background", 0.72, "#0a0c0e"))):
        tex = plate(16, 16, seed + i * 3, dark=dark, mid="#14181b", light="#1b2023", rust=0.1)
        N.save_png(MC_ASSETS / f"textures/gui/{name}.png", tex, np.full((16, 16), alpha))
    for name in ("header_separator", "inworld_header_separator"):
        rgb = np.zeros((2, 32, 3))
        rgb[0] = N.hex_rgb("#8a4a24")
        a = np.array([[0.9] * 32, [0.65] * 32])
        N.save_png(MC_ASSETS / f"textures/gui/{name}.png", rgb, a)
    for name in ("footer_separator", "inworld_footer_separator"):
        rgb = np.zeros((2, 32, 3))
        rgb[1] = N.hex_rgb("#8a4a24")
        a = np.array([[0.65] * 32, [0.9] * 32])
        N.save_png(MC_ASSETS / f"textures/gui/{name}.png", rgb, a)


def panel(seed: int) -> None:
    s, b = 64, 8
    centre = N.tile_window(plate(s - 2 * b, s - 2 * b, seed, dark="#121517", mid="#191d20", light="#22272a", rust=0.1), s, s, b, b)
    metal = N.tile_window(plate(s - 2 * b, s - 2 * b, seed + 10, dark="#283034", mid="#363e42", light="#485155", rust=0.35), s, s, b, b)
    yy, xx = np.mgrid[0:s, 0:s]
    inner = (yy >= b) & (yy < s - b) & (xx >= b) & (xx < s - b)
    rgb = np.where(inner[..., None], centre, metal)
    frame(rgb)
    # Recessed lip where the frame meets the inner plate.
    paint(rgb, np.s_[b - 1, b - 1:s - b + 1], "#060708", 0.9)
    paint(rgb, np.s_[b - 1:s - b + 1, b - 1], "#060708", 0.9)
    paint(rgb, np.s_[s - b, b - 1:s - b + 1], "#5c6569", 0.5)
    paint(rgb, np.s_[b - 1:s - b + 1, s - b], "#5c6569", 0.4)
    paint(rgb, np.s_[b - 2, b - 2:s - b + 2], "#000000", 0.30)
    paint(rgb, np.s_[b - 2:s - b + 2, b - 2], "#000000", 0.22)
    # Rivets and their rust stay inside the border so the tiled centre has no repeating marks.
    border_only = np.zeros((s, s, 3))
    alpha = np.ones((s, s))
    work = rgb.copy()
    for cy, cx in ((3.8, 3.8), (3.8, s - 3.8), (s - 3.8, 3.8), (s - 3.8, s - 3.8)):
        rivet(work, alpha, cy, cx, 2.0, rust_halo=0.5)
    for cy, cx in ((3.8, s / 2), (s - 3.8, s / 2), (s / 2, 3.8), (s / 2, s - 3.8)):
        rivet(work, alpha, cy, cx, 1.4, rust_halo=0.2)
    rgb = np.where(inner[..., None], rgb, work)
    del border_only
    alpha[inner] = 0.97
    save_sprite(N.ASSETS, "widget/panel.png", rgb, alpha, nine(s, s, b))

    t, ib = 32, 4
    ins = N.tile_window(plate(t - 2 * ib, t - 2 * ib, seed + 20, dark="#090b0c", mid="#0e1112", light="#141719", rust=0.05), t, t, ib, ib)
    frame(ins, outline="#040505", inset=True, hi="#4a5357")
    paint(ins, np.s_[2, 2:-2], "#000000", 0.35)
    paint(ins, np.s_[2:-2, 2], "#000000", 0.25)
    save_sprite(N.ASSETS, "widget/inset.png", ins, np.full((t, t), 0.93), nine(t, t, ib))


def menu_highlight(seed: int) -> None:
    h, w = 20, 200
    r = N.rng(seed)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    bristle = N.spectral(h, w, beta=0.9, seed=seed, aniso=(1.0, 14.0))
    ends = ndimage.gaussian_filter1d(r.normal(0.0, 1.0, h), 1.5)
    ends = 0.62 + 0.18 * (ends - ends.min()) / (np.ptp(ends) + 1e-9)
    xn = xx / w
    a = 1.0 - N.smoothstep(ends[:, None] - 0.42, ends[:, None], xn)
    rag = ndimage.gaussian_filter1d(r.normal(0.0, 1.0, w), 3.0)
    rag = (rag - rag.min()) / (np.ptp(rag) + 1e-9)
    a *= N.smoothstep(0.0, 2.5 + rag * 2.0, yy) * N.smoothstep(0.0, 2.5 + rag[::-1] * 2.0, h - 1 - yy)
    a *= 0.7 + 0.3 * bristle
    col = N.ramp(bristle, [(0.0, "#3e140d"), (0.5, "#6e2616"), (1.0, "#8f381e")])
    save_sprite(N.ASSETS, "widget/menu_highlight.png", col, np.clip(a * 0.9, 0, 1))

    sh, sw = 16, 64
    x = np.linspace(0.0, 1.0, sw)[None, :] * np.ones((sh, 1))
    save_sprite(N.ASSETS, "widget/menu_shade.png", N.solid(sh, sw, "#000000"), 0.80 * (1.0 - x) ** 1.4)


def lamps() -> None:
    s = 8
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    d = np.sqrt((yy + 0.5 - 4) ** 2 + (xx + 0.5 - 4) ** 2)
    body = np.clip(3.4 - d, 0.0, 1.0)
    on = N.ramp(np.clip(d / 3.4, 0, 1), [(0.0, "#fff3c0"), (0.45, "#f2b84a"), (1.0, "#9c5414")])
    halo = np.clip(1.0 - (d - 3.0) / 1.2, 0.0, 1.0) * 0.35
    save_sprite(N.ASSETS, "widget/lamp_on.png", on, np.clip(np.maximum(body, halo), 0, 1))
    off = N.ramp(np.clip(d / 3.4, 0, 1), [(0.0, "#3d3c34"), (1.0, "#16150f")])
    spec = np.clip(1.0 - np.sqrt((yy + 0.5 - 2.6) ** 2 + (xx + 0.5 - 2.6) ** 2) / 1.3, 0.0, 1.0) * 0.5
    off = N.lerp(off, N.solid(s, s, "#b8b4a4"), spec)
    save_sprite(N.ASSETS, "widget/lamp_off.png", off, body)


def tabs(seed: int) -> None:
    """Neutral cloth bookmark ribbons; JournalScreen tints them per tab."""
    w, h = 40, 22
    border = {"left": 2, "top": 3, "right": 9, "bottom": 3}
    r = N.rng(seed)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    weave = 0.86 + 0.07 * np.sin(xx * 2.2) * np.sin(yy * 0.7 + 1.0) + 0.07 * np.sin(yy * 2.4)
    tone = N.spectral(h, w, beta=1.6, seed=seed)
    for name, bright in (("journal/tab.png", 0.82), ("journal/tab_selected.png", 1.0)):
        rgb = N.solid(h, w, "#e8e2d4") * (weave * (0.9 + 0.1 * tone) * bright)[..., None]
        paint(rgb, np.s_[0, :], "#1c1410", 0.9)
        paint(rgb, np.s_[-1, :], "#1c1410", 0.9)
        paint(rgb, np.s_[1, :], "#ffffff", 0.25)
        paint(rgb, np.s_[-2, :], "#000000", 0.35)
        for y in range(3, h - 3, 3):  # stitching near the end
            paint(rgb, np.s_[y:y + 2, w - 7], "#f5efe0", 0.7)
        alpha = np.ones((h, w))
        fray = r.integers(0, 3, h)
        for y in range(h):
            alpha[y, w - fray[y]:] = 0.0
        save_sprite(N.ASSETS, name, rgb, alpha, nine(w, h, border))


def icons() -> None:
    pal = {"o": "#1b1410", "w": "#e8e1cf", "s": "#a99f88", "k": "#3a2e26"}
    clock = [
        "................",
        "................",
        ".....oooooo.....",
        "...oowwwwwwoo...",
        "..owwwwkwwwwso..",
        ".owwwwwkwwwwwso.",
        ".owwwwwkwwwwwso.",
        ".owwwwwkwwwwwso.",
        ".owwwwwkkkkwwso.",
        ".owwwwwwwwwwwso.",
        ".owwwwwwwwwwwso.",
        "..owwwwwwwwwso..",
        "...oowwwwwsoo...",
        ".....oooooo.....",
        "................",
        "................",
    ]
    skull = [
        "................",
        ".....oooooo.....",
        "...oowwwwwwoo...",
        "..owwwwwwwwwso..",
        ".owwwwwwwwwwwso.",
        ".owwwwwwwwwwwso.",
        ".owkkkwwwwkkkso.",
        ".owkkkkwwkkkkso.",
        ".owkkkwwwwkkkso.",
        ".owwwwwkkwwwwso.",
        "..owwwwkkwwwso..",
        "...owwwwwwwso...",
        "...owkwkwkwko...",
        "...owwwwwwwso...",
        "....oooooooo....",
        "................",
    ]
    gear = [
        "................",
        "......oooo......",
        "...oo.owwo.oo...",
        "..owwoowwoowso..",
        "..owwwwwwwwwso..",
        "...owwwkkwwso...",
        ".oowwwk..kwwsoo.",
        ".owwwk....kwwso.",
        ".owwwk....kwwso.",
        ".oowwwk..kwwsoo.",
        "...owwwkkwwso...",
        "..owwwwwwwwwso..",
        "..owwoowwoowso..",
        "...oo.owwo.oo...",
        "......oooo......",
        "................",
    ]
    for name, rows in (("icon/log.png", clock), ("icon/threat.png", skull), ("icon/settings.png", gear)):
        rgb, a = ascii_icon(rows, pal)
        save_sprite(N.ASSETS, name, rgb, a)


def day_stroke(seed: int) -> None:
    h, w = 48, 256
    r = N.rng(seed)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    bristle = N.spectral(h, w, beta=0.9, seed=seed, aniso=(1.0, 16.0))
    top = ndimage.gaussian_filter1d(r.normal(0, 1, w), 6.0)
    bot = ndimage.gaussian_filter1d(r.normal(0, 1, w), 6.0)
    top = 8 + 5 * (top - top.min()) / (np.ptp(top) + 1e-9)
    bot = h - 8 - 5 * (bot - bot.min()) / (np.ptp(bot) + 1e-9)
    a = N.smoothstep(top[None, :] - 1, top[None, :] + 3, yy) * (1.0 - N.smoothstep(bot[None, :] - 3, bot[None, :] + 1, yy))
    xn = xx / w
    a *= N.smoothstep(0.0, 0.10, xn) * (1.0 - N.smoothstep(0.80, 1.0, xn + 0.08 * (bristle - 0.5)))
    a *= 0.80 + 0.20 * bristle
    col = N.ramp(bristle, [(0.0, "#040404"), (1.0, "#15110e")])
    save_sprite(N.ASSETS, "hud/day_stroke.png", col, np.clip(a * 0.97, 0, 1))


# ----------------------------------------------------------------------------- journal

def journal_book(seed: int = 2401) -> None:
    W, H = 768, 480
    r = N.rng(seed)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)

    # Leather cover: pebbled grain, scuffs, darker toward the edges.
    cells = N.worley(H, W, 9000, seed=seed, tileable=False)
    grain = N.smoothstep(0.0, 0.9, cells)
    tone = N.spectral(H, W, beta=2.3, seed=seed + 1)
    leather = N.ramp(0.6 * tone + 0.4 * grain, [(0.0, "#1e110a"), (0.5, "#33200f"), (1.0, "#4d311c")])
    scuff = N.smoothstep(0.70, 0.95, N.spectral(H, W, beta=1.8, seed=seed + 2))
    leather = N.lerp(leather, N.solid(H, W, "#6a4a30"), scuff * 0.45)
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    leather *= (0.72 + 0.28 * N.smoothstep(0, 26, edge))[..., None]

    rgb = leather
    alpha = np.ones((H, W))
    rad = 18.0
    for cy, cx in ((rad, rad), (rad, W - 1 - rad), (H - 1 - rad, rad), (H - 1 - rad, W - 1 - rad)):
        corner = ((yy < rad) | (yy > H - 1 - rad)) & ((xx < rad) | (xx > W - 1 - rad))
        d = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
        near = corner & (np.abs(yy - cy) <= rad + 1) & (np.abs(xx - cx) <= rad + 1)
        alpha[near] = np.minimum(alpha[near], np.clip(rad + 0.5 - d[near], 0, 1))

    # Stitching around the cover.
    stitch = np.zeros((H, W))
    m = 10
    for x in range(m, W - m, 9):
        stitch[m - 1:m + 1, x:x + 5] = 1
        stitch[H - m - 1:H - m + 1, x:x + 5] = 1
    for y in range(m, H - m, 9):
        stitch[y:y + 5, m - 1:m + 1] = 1
        stitch[y:y + 5, W - m - 1:W - m + 1] = 1
    rgb = N.lerp(rgb, N.solid(H, W, "#b39a6e"), stitch * 0.75)

    # Pages.
    pages = [(28, 20, 380, 460), (388, 20, 740, 460)]
    fibre = N.spectral(H, W, beta=0.8, seed=seed + 3)
    blot = N.spectral(H, W, beta=2.6, seed=seed + 4)
    paper = N.ramp(0.25 * fibre + 0.75 * blot, [(0.0, "#c2b38f"), (0.5, "#d6caaa"), (1.0, "#e2d8bd")])
    for i, (x0, y0, x1, y1) in enumerate(pages):
        inside = (xx >= x0) & (xx < x1) & (yy >= y0) & (yy < y1)
        pe = np.minimum(np.minimum(xx - x0, x1 - 1 - xx), np.minimum(yy - y0, y1 - 1 - yy))
        shade_edge = 0.80 + 0.20 * N.smoothstep(0, 18, pe)
        gutter_x = x1 if i == 0 else x0
        gutter = 1.0 - 0.32 * np.exp(-np.abs(xx - gutter_x) / 16.0)
        page = paper * (shade_edge * gutter)[..., None]
        # Margin rule a few pixels left of the text column (JournalScreen LEFT_X / RIGHT_X).
        margin = x0 + 24 if i == 0 else x0 + 14
        line = (np.abs(xx - margin) < 1.0) & (yy > y0 + 14) & (yy < y1 - 14)
        page = N.lerp(page, N.solid(H, W, "#b0574a"), line * 0.32)
        rgb = np.where(inside[..., None], page, rgb)
        # Page block thickness at the outer edge.
        thick = ((xx >= x0 - 3) & (xx < x0) if i == 0 else (xx >= x1) & (xx < x1 + 3)) & (yy >= y0 + 2) & (yy < y1 - 2)
        rgb = np.where(thick[..., None], N.solid(H, W, "#a8997a") * (0.85 + 0.15 * np.sin(yy * 2.0))[..., None], rgb)

    # Spine crease.
    spine = np.exp(-((xx - 384.0) / 5.0) ** 2) * ((yy >= 16) & (yy < 464))
    rgb = N.lerp(rgb, N.solid(H, W, "#0e0805"), spine * 0.85)

    # Coffee ring on the right page, a dried smear on the left.
    cy, cx, rr = 392.0, 650.0, 44.0
    d = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
    wob = N.spectral(H, W, beta=2.0, seed=seed + 5)
    ring = np.exp(-((d - rr - 6 * (wob - 0.5)) / 2.2) ** 2) * 0.40 + (d < rr) * 0.06
    rgb = N.lerp(rgb, N.solid(H, W, "#7a5a32"), np.clip(ring, 0, 1))
    smear = N.smoothstep(0.62, 0.8, N.spectral(H, W, beta=2.2, seed=seed + 6)) * np.exp(-(((xx - 90) / 46) ** 2 + ((yy - 420) / 26) ** 2))
    rgb = N.lerp(rgb, N.solid(H, W, "#4e1611"), np.clip(smear * 0.55, 0, 1))
    # Slight grime everywhere so nothing looks freshly printed.
    rgb *= (0.94 + 0.06 * N.spectral(H, W, beta=1.0, seed=seed + 7))[..., None]
    N.save_png(N.asset("textures/gui/journal/book.png"), rgb, alpha)


def build() -> None:
    button_set(N.ASSETS, "widget/", 4, 3101)
    button_set(MC_ASSETS, "widget/", 3, 3101)
    slider_set(N.ASSETS, "widget/", 4, 3201)
    slider_set(MC_ASSETS, "widget/", 1, 3201)
    text_field_set(3301)
    checkbox_set(3351)
    scroller_set(3381)
    menu_backgrounds(3401)
    panel(3501)
    menu_highlight(3601)
    lamps()
    tabs(3701)
    icons()
    day_stroke(3801)
    journal_book()


if __name__ == "__main__":
    build()
    print("gui sprites written")
