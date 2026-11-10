"""DEADLANDS loot containers: body models, face textures per condition, animated-part textures,
blockstates, item models, loot tables, screen panels and English names.

Geometry mirrors com.deadlands.container.ContainerKind (shapes) and
com.deadlands.client.render.ContainerPartModels (moving parts); keep them in sync.
Front faces north; JSON face UVs use [0, 0, w, h] so each face's art sits in the top-left
w*2 x h*2 texels of its 32x32 texture.
"""
from __future__ import annotations

import shutil

import numpy as np
from scipy import ndimage

import dl_data as D
import dl_noise as N
from gen_gui import frame, nine, paint, plate, rivet, save_sprite

NS = D.NS
CONDITIONS = ["clean", "dusty", "rusty", "damaged"]
KINDS = ["military_crate", "filing_cabinet", "medical_cabinet", "supply_locker", "toolbox",
         "kitchen_cupboard", "fridge", "dresser", "safe", "school_desk"]
WOODEN = {"military_crate", "kitchen_cupboard", "dresser", "school_desk"}
ENGLISH = {
    "military_crate": "Military Crate",
    "filing_cabinet": "Filing Cabinet",
    "medical_cabinet": "Medical Cabinet",
    "supply_locker": "Supply Locker",
    "toolbox": "Toolbox",
    "kitchen_cupboard": "Kitchen Cupboard",
    "fridge": "Refrigerator",
    "dresser": "Dresser",
    "safe": "Safe",
    "school_desk": "School Desk",
}


# ----------------------------------------------------------------------------- surfaces

def painted_metal(h: int, w: int, seed: int, paint_hex: str) -> np.ndarray:
    base = N.solid(h, w, paint_hex)
    tone = N.spectral(h, w, beta=2.2, seed=seed)
    brushed = N.spectral(h, w, beta=1.0, seed=seed + 1, aniso=(1.0, 5.0))
    return base * (0.88 + 0.16 * tone + 0.06 * (brushed - 0.5))[..., None]


def painted_planks(h: int, w: int, seed: int, paint_hex: str, board: int = 5, vertical: bool = False) -> np.ndarray:
    yy, xx = np.mgrid[0:h, 0:w]
    coord = xx if vertical else yy
    grain = N.spectral(h, w, beta=1.3, seed=seed, aniso=(9.0, 1.0) if vertical else (1.0, 9.0))
    r = N.rng(seed + 1)
    tone = r.uniform(0.86, 1.08, max(1, (w if vertical else h) // board + 2))[coord // board]
    rgb = N.solid(h, w, paint_hex) * ((0.82 + 0.25 * grain) * tone)[..., None]
    gap = (coord % board) == board - 1
    rgb[gap] *= 0.45
    rgb[(coord % board) == 0] *= 1.08
    return rgb


# Per condition: share of the surface with chipped paint, dust strength, share of the surface
# rusted, and damage (dents, scratches, punctures). Shares are exact (quantile thresholds), so a
# rusty container is about a third rust rather than whatever a noise threshold happens to give.
CONDITION_WEAR = {
    "clean": (0.03, 0.0, 0.0, 0.0),
    "dusty": (0.06, 0.55, 0.03, 0.0),
    "rusty": (0.12, 0.15, 0.30, 0.0),
    "damaged": (0.22, 0.25, 0.16, 1.0),
}


def coverage(score: np.ndarray, share: float, soft: float = 0.06) -> np.ndarray:
    """A 0..1 mask covering about `share` of the pixels with the highest score, with soft edges."""
    if share <= 0:
        return np.zeros_like(score)
    t = float(np.quantile(score, 1.0 - share))
    return N.smoothstep(t - soft, t + soft, score)


def weather(rgb: np.ndarray, condition: str, seed: int, metal: bool = True, under: str | None = None,
            strength: float = 1.0) -> np.ndarray:
    """Apply wear, dust, rust and damage for a container condition. Interiors pass a low strength:
    the inside of a cabinet is sheltered and ages far less than its shell."""
    h, w = rgb.shape[:2]
    wear, dust, rust, damage = (v * strength for v in CONDITION_WEAR[condition])
    out = rgb.copy()
    chips = coverage(N.spectral(h, w, beta=1.6, seed=seed), wear, soft=0.04)
    under_rgb = N.solid(h, w, under or ("#5a5f61" if metal else "#8a7a62")) * (0.85 + 0.2 * N.spectral(h, w, beta=1.0, seed=seed + 1))[..., None]
    out = N.lerp(out, under_rgb, chips)
    if rust > 0:
        # A few coherent blooms with ragged edges and vertical run-off streaks; on metal, exposed
        # chips rust too, so the specks read as corrosion rather than dirt.
        bloom = N.spectral(h, w, beta=2.6, seed=seed + 2) + (N.spectral(h, w, beta=1.0, seed=seed + 9) - 0.5) * 0.16
        streak = N.smoothstep(0.5, 0.85, N.spectral(h, w, beta=1.6, seed=seed + 3, aniso=(6.0, 1.0)))
        mask = coverage(bloom + 0.3 * streak, rust)
        if metal:
            mask = np.maximum(mask, chips * min(0.85, rust * 3.0))
        else:
            mask *= 0.45
        if metal:
            rc = N.ramp(N.spectral(h, w, beta=1.2, seed=seed + 4), [(0.0, "#3a1d10"), (0.5, "#6b371c"), (1.0, "#9a5a2c")])
        else:  # on wood the "rusty" condition reads as water damage and rot
            rc = N.ramp(N.spectral(h, w, beta=1.2, seed=seed + 4), [(0.0, "#26231c"), (0.5, "#3e382c"), (1.0, "#5a5240")])
        out = N.lerp(out, rc, mask)
    if dust > 0:
        dm = N.smoothstep(0.3, 0.9, N.spectral(h, w, beta=2.4, seed=seed + 5)) * dust
        out = N.lerp(out, N.solid(h, w, "#b3aa93"), dm * 0.7)
    if damage >= 0.5:
        dents = N.smoothstep(0.72, 0.9, N.spectral(h, w, beta=2.0, seed=seed + 6))
        out *= (1.0 - 0.35 * dents)[..., None]
        r = N.rng(seed + 7)
        for _ in range(3):
            x0, y0 = r.integers(0, w), r.integers(0, h)
            length = r.integers(4, 12)
            dx, dy = r.choice([-1, 1]), r.choice([0, 1])
            for t in range(length):
                xx, yy = (x0 + t * dx) % w, (y0 + (t * dy) // 2) % h
                out[yy, xx] = out[yy, xx] * 0.5 + np.array(N.hex_rgb("#c8c2b4")) * 0.5
        holes = (N.spectral(h, w, beta=0.8, seed=seed + 8) > 0.96)
        out[holes] = N.hex_rgb("#141210")
    return np.clip(out, 0, 255)


def cavity_shade(rgb: np.ndarray, w: int, h: int, depth: float = 0.45, reach: float = 4.0) -> np.ndarray:
    """Darken the art region (w x h texels, top-left) toward its edges, faking the shadow where an
    interior floor meets its walls."""
    yy, xx = np.mgrid[0:rgb.shape[0], 0:rgb.shape[1]]
    d = np.minimum.reduce([xx, yy, w - 1 - xx, h - 1 - yy]).clip(0, None)
    return rgb * (1.0 - depth + depth * np.clip(d / reach, 0, 1))[..., None]


def depth_shade(rgb: np.ndarray, h: int, top: float = 1.0, bottom: float = 0.5) -> np.ndarray:
    """Darken an inner wall from its rim (row 0) down to the floor (row h)."""
    yy = np.mgrid[0:rgb.shape[0], 0:rgb.shape[1]][0]
    return rgb * (top + (bottom - top) * np.clip(yy / h, 0, 1))[..., None]


def rect(rgb, x0, y0, x1, y1, colour, a=1.0):
    paint(rgb, np.s_[y0:y1, x0:x1], colour, a)


def bevel(rgb, x0, y0, x1, y1, light=0.25, dark=0.35):
    rgb[y0, x0:x1] = np.clip(rgb[y0, x0:x1] * (1 + light), 0, 255)
    rgb[y0:y1, x0] = np.clip(rgb[y0:y1, x0] * (1 + light * 0.7), 0, 255)
    rgb[y1 - 1, x0:x1] *= 1 - dark
    rgb[y0:y1, x1 - 1] *= 1 - dark * 0.8


def bolt(rgb, x, y):
    rgb[y, x] = N.hex_rgb("#9aa0a2")
    rgb[y + 1, x + 1] = N.hex_rgb("#1e2022")
    rgb[y, x + 1] = N.hex_rgb("#5a5f62")
    rgb[y + 1, x] = N.hex_rgb("#3a3e40")


def stencil_arrows(rgb, cx, cy, colour="#d8d1bc", a=0.85):
    for ox in (-4, 4):
        x = cx + ox
        for i in range(4):
            rect(rgb, x - i, cy + i, x + i + 1, cy + i + 1, colour, a)
        rect(rgb, x - 1, cy + 4, x + 2, cy + 9, colour, a)


def red_cross(rgb, cx, cy, size=6, colour="#a8261c"):
    rect(rgb, cx - size // 3, cy - size, cx + size // 3 + 1, cy + size + 1, colour)
    rect(rgb, cx - size, cy - size // 3, cx + size + 1, cy + size // 3 + 1, colour)


# ----------------------------------------------------------------------------- per-kind faces (32x32 block textures)

def crate_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    olive = "#525a34"
    f = {}

    def planks(s, w=28, h=20):
        img = painted_planks(32, 32, s, olive)
        return weather(img, cond, s + 50, metal=False, under="#8c7b5c")

    def brackets(img, w, h):
        for (x, y) in ((0, 0), (w - 3, 0), (0, h - 3), (w - 3, h - 3)):
            rect(img, x, y, x + 3, y + 3, "#2e3133")
            bolt(img, x + 1 if x == 0 else x, y + 1 if y == 0 else y)
        rust = {"clean": 0.0, "dusty": 0.1, "rusty": 0.6, "damaged": 0.4}[cond]
        if rust:
            for (x, y) in ((0, 0), (w - 3, 0), (0, h - 3), (w - 3, h - 3)):
                rect(img, x, y, x + 3, y + 3, "#6b371c", rust)

    front = planks(seed)
    stencil_arrows(front, 14, 4)
    rect(front, 4, 16, 24, 17, "#d8d1bc", 0.7)
    brackets(front, 28, 20)
    f["front"] = front
    back = planks(seed + 1)
    brackets(back, 28, 20)
    f["back"] = back
    side = planks(seed + 2)
    rect(side, 8, 7, 16, 8, "#6a5a3c")      # rope handle
    rect(side, 7, 8, 8, 12, "#6a5a3c")
    rect(side, 16, 8, 17, 12, "#6a5a3c")
    brackets(side, 24, 20)
    f["side"] = side
    # Interior: unpainted boards, packing straw in clumps, darker toward the walls and the floor.
    floor = painted_planks(32, 32, seed + 3, "#5a4830", board=6)
    r = N.rng(seed + 4)
    clumps = N.spectral(32, 32, beta=2.2, seed=seed + 11)
    for _ in range(90):
        x, y = int(r.integers(0, 24)), int(r.integers(0, 20))
        if r.random() > clumps[y, x] ** 2 * 1.6:
            continue
        dx, dy = [(1, 0), (1, 1), (0, 1), (1, -1), (2, 1)][int(r.integers(0, 5))]
        colour = np.array(N.hex_rgb(["#b89a52", "#c8ad66", "#9c8044", "#d4bd7a"][int(r.integers(0, 4))]))
        for t in range(int(r.integers(3, 7))):
            xx, yy = (x + t * dx) % 32, (y + (t * dy) // (2 if dx == 2 else 1)) % 32
            floor[yy, xx] = colour * r.uniform(0.8, 1.05)
    f["inside_floor"] = weather(cavity_shade(floor, 24, 20), cond, seed + 5, metal=False, strength=0.35)
    wall = painted_planks(32, 32, seed + 12, "#5e4b33", board=6)
    f["inside_wall"] = weather(depth_shade(wall, 18, 0.95, 0.5), cond, seed + 13, metal=False, strength=0.35)
    f["rim"] = planks(seed + 10)
    f["bottom"] = weather(painted_planks(32, 32, seed + 6, "#3f4428"), cond, seed + 7, metal=False)
    lid = planks(seed + 8)
    rect(lid, 0, 4, 28, 6, "#3f4628")        # battens
    rect(lid, 0, 18, 28, 20, "#3f4628")
    bolt(lid, 3, 4)
    bolt(lid, 23, 18)
    f["lid_top"] = lid
    f["lid_side"] = planks(seed + 9)
    # Underside of the lid: bare vertical boards held by two nailed cross battens.
    under = painted_planks(32, 32, seed + 14, "#7a6342", board=7, vertical=True)
    for y0 in (3, 17):
        rect(under, 0, y0, 28, y0 + 4, "#5b4830")
        under[y0, 0:28] *= 1.15
        under[y0 + 3, 0:28] *= 0.75
        for x in range(3, 28, 7):
            under[y0 + 1, x] = N.hex_rgb("#2a2a28")
            under[y0 + 1, x + 1] = N.hex_rgb("#8a8d8c")
    f["lid_inside"] = weather(under, cond, seed + 15, metal=False, strength=0.5)
    return f


def filing_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    grey = "#6a7072"
    f = {}

    def metal(s):
        return weather(painted_metal(32, 32, s, grey), cond, s + 50)

    front = metal(seed)
    rect(front, 1, 1, 23, 15, "#16191a")          # cavity of the top drawer (covered by the moving drawer)
    rect(front, 2, 2, 22, 14, "#0e1011")
    rect(front, 1, 17, 23, 31, grey, 0.35)        # bottom drawer panel
    bevel(front, 1, 17, 23, 31)
    rect(front, 8, 22, 16, 24, "#2a2d2f")         # handle
    rect(front, 8, 22, 16, 23, "#8d9395", 0.6)
    rect(front, 9, 19, 15, 21, "#cfc8b4")         # label card
    f["front"] = front
    side = metal(seed + 1)
    for x in (9, 19):
        side[:, x] *= 0.8
        side[:, x + 1] *= 1.12
    f["side"] = side
    f["back"] = metal(seed + 2)
    f["top"] = metal(seed + 3)
    f["bottom"] = metal(seed + 4) * 0.6
    drawer = metal(seed + 5)
    bevel(drawer, 0, 0, 22, 14)
    rect(drawer, 7, 6, 15, 8, "#2a2d2f")
    rect(drawer, 7, 6, 15, 7, "#8d9395", 0.6)
    rect(drawer, 8, 3, 14, 5, "#cfc8b4")
    f["drawer"] = drawer
    return f


def cabinet_faces(cond: str, seed: int, enamel: str, interior: str, medical: bool) -> dict[str, np.ndarray]:
    f = {}

    def shell(s, colour=enamel):
        return weather(painted_metal(32, 32, s, colour), cond, s + 50, under="#4a4f52")

    side = shell(seed)
    if medical:
        red_cross(side, 10, 12, size=5)
    else:
        for y in range(4, 30, 3):
            rect(side, 6, y, 14, y + 1, "#1d2120", 0.8)
    f["side"] = side
    f["back"] = shell(seed + 1)
    f["top"] = shell(seed + 2)
    f["bottom"] = shell(seed + 3) * 0.6
    f["edge"] = shell(seed + 4)
    inner = weather(painted_metal(32, 32, seed + 5, interior), cond, seed + 55, under="#4a4f52", strength=0.2)
    f["inside_side"] = inner * 0.85
    f["inside_floor"] = inner * 0.9
    f["shelf"] = inner.copy()
    back = inner.copy() * 0.8
    r = N.rng(seed + 6)
    if medical:
        # Bottles and boxes on two shelves.
        for shelf_y in (8, 24):
            x = 2
            while x < 26:
                bw = int(r.integers(2, 5))
                bh = int(r.integers(4, 7))
                colour = r.choice(["#5b3a1e", "#2e4a6b", "#d8d4c6", "#7a2a22", "#4d5a3a"])
                if r.random() < 0.75:
                    rect(back, x, shelf_y - bh, x + bw, shelf_y, colour)
                    rect(back, x, shelf_y - bh, x + 1, shelf_y, "#ffffff", 0.25)
                x += bw + int(r.integers(1, 3))
    else:
        rect(back, 11, 4, 13, 6, "#2a2c2d")       # coat hook
        rect(back, 6, 8, 18, 24, "#4e4636", 0.85)  # an old jacket left behind
        rect(back, 11, 8, 13, 24, "#3a3428", 0.9)
    f["inside_back"] = np.clip(back, 0, 255)
    # Door art is drawn as seen from the front (left = viewer's left); part_texture orients it.
    door = shell(seed + 7)
    if medical:
        rect(door, 2, 2, 10, 26, "#9fb3b8")       # glass
        rect(door, 3, 3, 5, 25, "#e8f0f0", 0.35)  # reflection
        bevel(door, 2, 2, 10, 26, light=-0.2, dark=-0.15)
        red_cross(door, 6, 7, size=2)
    else:
        for y in (4, 6, 8, 19, 21, 23):           # louvred vents: dark slit, lit lip below
            rect(door, 4, y, 16, y + 1, "#101312")
            rect(door, 4, y + 1, 16, y + 2, "#ffffff", 0.16)
        rect(door, 2, 12, 4, 17, "#2a2d2b")       # latch, on the side away from the hinge
        rect(door, 2, 12, 4, 13, "#8a908d", 0.7)
    f["door"] = door
    return f


def toolbox_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    red = "#8b2c22"
    f = {}

    def shell(s):
        return weather(painted_metal(32, 32, s, red), cond, s + 50, under="#5a5f61")

    front = shell(seed)
    for x in (4, 14):
        rect(front, x, 6, x + 3, 9, "#1e2022")    # latches
        rect(front, x, 6, x + 3, 7, "#8d9395", 0.6)
    f["front"] = front
    f["back"] = shell(seed + 1)
    side = shell(seed + 2)
    bolt(side, 2, 2)
    bolt(side, 8, 2)
    f["side"] = side
    # Tray floor (8x4 px = 16x8 texels): a screwdriver, a wrench and a few loose nuts.
    tray = painted_metal(32, 32, seed + 3, "#2b2d2e")
    rect(tray, 1, 2, 5, 3, "#c9a227")             # screwdriver handle
    tray[3, 1:5] = N.hex_rgb("#7a6116")           # handle shadow
    rect(tray, 5, 2, 11, 3, "#a4aaac")            # shaft
    rect(tray, 3, 5, 11, 6, "#8d9395")            # wrench shaft
    rect(tray, 11, 4, 14, 7, "#8d9395")           # open head
    tray[5, 12:14] = N.hex_rgb("#202223")
    tray[6, 3:11] = N.hex_rgb("#1a1b1c")          # wrench shadow
    for x, y in ((13, 1), (14, 2), (1, 6)):
        tray[y, x] = N.hex_rgb("#9aa0a2")
    f["inside_floor"] = weather(cavity_shade(tray, 16, 8, depth=0.4, reach=2.5), cond, seed + 53, strength=0.35)
    wall = painted_metal(32, 32, seed + 11, "#5c1e17")
    f["inside_wall"] = weather(depth_shade(wall, 8, 0.9, 0.45), cond, seed + 54, strength=0.35)
    f["rim"] = shell(seed + 10)
    f["bottom"] = shell(seed + 5) * 0.6
    lid = shell(seed + 6)
    rect(lid, 0, 0, 20, 1, "#ffffff", 0.15)
    f["lid_top"] = lid
    f["lid_side"] = shell(seed + 7)
    under = weather(painted_metal(32, 32, seed + 9, "#6a221a"), cond, seed + 59, strength=0.5)
    rect(under, 2, 2, 18, 3, "#ffffff", 0.12)     # pressed stiffening rib
    rect(under, 2, 9, 18, 10, "#000000", 0.25)
    rect(under, 2, 3, 3, 9, "#ffffff", 0.08)
    rect(under, 17, 3, 18, 9, "#000000", 0.2)
    f["lid_inside"] = under
    handle = painted_metal(32, 32, seed + 8, "#1c1d1e")
    f["handle"] = handle
    return f



# ----------------------------------------------------------------------------- household and school (Milestone 3)

def door_panel(rgb, x0, y0, x1, y1):
    """A raised panel with a bevelled frame, as on painted cabinet doors."""
    bevel(rgb, x0, y0, x1, y1, light=0.18, dark=0.3)
    bevel(rgb, x0 + 2, y0 + 2, x1 - 2, y1 - 2, light=-0.12, dark=-0.18)


def cupboard_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    paint_hex = "#cfc6a8"
    f = {}

    def shell(s, colour=paint_hex):
        return weather(painted_planks(32, 32, s, colour, board=8, vertical=True), cond, s + 50, metal=False, under="#6b5a44")

    f["side"] = shell(seed)
    f["back"] = shell(seed + 1)
    top = weather(painted_metal(32, 32, seed + 2, "#9a9384"), cond, seed + 52, metal=False)
    rect(top, 0, 0, 32, 1, "#ffffff", 0.2)
    f["top"] = top
    f["bottom"] = shell(seed + 3) * 0.6
    f["edge"] = shell(seed + 4)
    inner = weather(painted_planks(32, 32, seed + 5, "#b8ad8e", board=6), cond, seed + 55, metal=False, strength=0.25)
    f["inside_side"] = inner * 0.8
    f["inside_floor"] = inner * 0.85
    f["shelf"] = inner.copy()
    back = inner.copy() * 0.75
    r = N.rng(seed + 6)
    for shelf_y in (12, 26):
        x = 2
        while x < 26:
            w = int(r.integers(2, 5))
            h = int(r.integers(3, 7))
            if r.random() < 0.6:
                colour = r.choice(["#e0dccf", "#8a2f22", "#2f4f6b", "#c8a24a", "#6c7a3a"])
                rect(back, x, shelf_y - h, x + w, shelf_y, colour)
                rect(back, x, shelf_y - h, x + 1, shelf_y, "#ffffff", 0.2)
            x += w + int(r.integers(1, 3))
    f["inside_back"] = np.clip(back, 0, 255)
    door = shell(seed + 7)
    door_panel(door, 1, 1, 11, 27)
    rect(door, 9, 13, 11, 15, "#3d3226")       # knob, away from the hinge
    rect(door, 9, 13, 10, 14, "#b9a77d", 0.7)
    f["door"] = door
    return f


def fridge_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    enamel = "#ddd8c4"
    f = {}

    def shell(s):
        return weather(painted_metal(32, 32, s, enamel), cond, s + 50, under="#4c4f50")

    f["side"] = shell(seed)
    f["back"] = shell(seed + 1) * 0.8
    f["top"] = shell(seed + 2)
    f["bottom"] = shell(seed + 3) * 0.55
    f["edge"] = shell(seed + 4)
    inner = weather(painted_metal(32, 32, seed + 5, "#e8e6dc"), cond, seed + 55, strength=0.3)
    f["inside_side"] = inner * 0.85
    f["inside_floor"] = inner * 0.9
    shelf = inner.copy()
    for x in range(0, 32, 3):
        rect(shelf, x, 0, x + 1, 32, "#9aa0a0", 0.6)  # wire rack
    f["shelf"] = shelf
    back = inner.copy() * 0.8
    r = N.rng(seed + 6)
    for shelf_y in (10, 26):
        x = 3
        while x < 24:
            w = int(r.integers(2, 4))
            h = int(r.integers(3, 8))
            if r.random() < 0.55:
                colour = r.choice(["#3e5a2a", "#6b4a1e", "#c9c2a8", "#7a2a22", "#4a3a2a"])  # jars gone bad
                rect(back, x, shelf_y - h, x + w, shelf_y, colour)
            x += w + int(r.integers(1, 4))
    mould = N.spectral(32, 32, beta=2.4, seed=seed + 8) > 0.72
    back[mould] = back[mould] * 0.5 + np.array(N.hex_rgb("#4d5a2e")) * 0.5
    f["inside_back"] = np.clip(back, 0, 255)
    door = shell(seed + 7)
    rect(door, 16, 4, 18, 24, "#9a9c98")       # long chrome handle
    rect(door, 16, 4, 17, 24, "#ffffff", 0.35)
    rect(door, 6, 3, 13, 5, "#8a8c88", 0.8)     # maker's plate
    rect(door, 0, 26, 20, 28, "#2a2b2a", 0.7)    # gasket
    f["door"] = door
    return f


def dresser_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    f = {}

    def wood(s):
        return weather(painted_planks(32, 32, s, "#6a4a30", board=6), cond, s + 50, metal=False, under="#3a2a1c")

    front = wood(seed)
    for y0 in (0, 10, 18):      # three drawer fronts (the top one is the moving drawer)
        door_panel(front, 1, y0 + 1, 27, y0 + 9 if y0 else y0 + 9)
        rect(front, 11, y0 + 4, 17, y0 + 6, "#2a2016")
        rect(front, 11, y0 + 4, 17, y0 + 5, "#b9995a", 0.6)
    f["front"] = front
    f["back"] = wood(seed + 1) * 0.8
    f["side"] = wood(seed + 2)
    top = wood(seed + 3)
    rect(top, 4, 6, 10, 12, "#d8d0bc", 0.35)  # a water ring under a lost picture frame
    f["top"] = top
    f["bottom"] = wood(seed + 4) * 0.6
    drawer = wood(seed + 5)
    door_panel(drawer, 0, 0, 24, 10)
    rect(drawer, 9, 3, 15, 5, "#2a2016")
    rect(drawer, 9, 3, 15, 4, "#b9995a", 0.6)
    f["drawer"] = drawer
    clothes = np.zeros((32, 32, 3))
    clothes[...] = N.hex_rgb("#2a221a")
    r = N.rng(seed + 6)
    for row in range(0, 20, 4):
        colour = np.array(N.hex_rgb(r.choice(["#5a6a7a", "#8a7a5a", "#7a3a3a", "#c8c0aa", "#3a4a3a"])))
        clothes[row + 1:row + 4, 1:21] = colour * (0.85 + 0.15 * N.spectral(3, 20, beta=1.5, seed=seed + row)[..., None])
    f["clothes"] = np.clip(clothes, 0, 255)
    return f


def safe_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    steel = "#4f5552"
    f = {}

    def shell(s):
        return weather(painted_metal(32, 32, s, steel), cond, s + 50, under="#2c2f2e")

    f["side"] = shell(seed)
    f["back"] = shell(seed + 1)
    f["top"] = shell(seed + 2)
    f["bottom"] = shell(seed + 3) * 0.6
    f["edge"] = shell(seed + 4)
    inner = weather(painted_metal(32, 32, seed + 5, "#6b5c3e"), cond, seed + 55, strength=0.25)  # felt-lined
    f["inside_side"] = inner * 0.8
    f["inside_floor"] = inner * 0.85
    f["shelf"] = shell(seed + 6)
    back = inner.copy() * 0.7
    rect(back, 4, 14, 12, 20, "#2f5a3a", 0.9)   # a bundle of old bills
    rect(back, 13, 15, 18, 20, "#c9b483", 0.9)  # papers
    f["inside_back"] = np.clip(back, 0, 255)
    door = shell(seed + 7)
    bevel(door, 1, 1, 19, 21, light=0.2, dark=0.35)
    for cy, cx in ((10, 9),):
        yy, xx = np.mgrid[0:32, 0:32]
        dial = (yy - cy) ** 2 + (xx - cx) ** 2 <= 16
        door[dial] = np.array(N.hex_rgb("#1d1f1e"))
        ring_ = ((yy - cy) ** 2 + (xx - cx) ** 2 <= 16) & ((yy - cy) ** 2 + (xx - cx) ** 2 >= 9)
        door[ring_] = np.array(N.hex_rgb("#a6a99f"))
    rect(door, 14, 9, 18, 11, "#a6a99f")        # lever handle
    rect(door, 3, 17, 16, 18, "#c2b48a", 0.6)    # maker's name strip
    f["door"] = door
    return f


def desk_faces(cond: str, seed: int) -> dict[str, np.ndarray]:
    f = {}

    def wood(s, colour="#9a7b52"):
        return weather(painted_planks(32, 32, s, colour, board=7), cond, s + 50, metal=False, under="#4a3a28")

    f["front"] = wood(seed)
    f["back"] = wood(seed + 1)
    f["side"] = wood(seed + 2)
    f["bottom"] = wood(seed + 3) * 0.6
    f["rim"] = wood(seed + 4)
    tray = wood(seed + 5, "#5e4a32") * 0.8
    rect(tray, 3, 3, 13, 9, "#d8d2c0")          # an exercise book
    for y in range(4, 9):
        rect(tray, 4, y, 12, y + 1, "#8aa0b8", 0.4)
    rect(tray, 16, 5, 25, 6, "#d8b03a")          # a pencil
    rect(tray, 24, 5, 25, 6, "#e8a0a0")
    f["inside_floor"] = weather(cavity_shade(tray, 28, 24, depth=0.4, reach=3), cond, seed + 53, strength=0.3)
    f["inside_wall"] = weather(depth_shade(wood(seed + 6, "#5e4a32"), 8, 0.9, 0.5), cond, seed + 54, strength=0.3)
    lid = wood(seed + 7)
    rect(lid, 4, 2, 24, 3, "#2a2016", 0.5)       # pen groove
    scratch = N.rng(seed + 8)
    for _ in range(6):                            # carved initials and doodles
        x = int(scratch.integers(2, 24))
        y = int(scratch.integers(6, 20))
        rect(lid, x, y, x + int(scratch.integers(2, 5)), y + 1, "#3a2c1c", 0.6)
    f["lid_top"] = lid
    f["lid_side"] = wood(seed + 9)
    under = wood(seed + 10, "#7a6040")
    rect(under, 6, 8, 14, 16, "#e6d27a", 0.8)      # a sticker
    f["lid_inside"] = under
    f["leg"] = weather(painted_metal(32, 32, seed + 11, "#4a5a5a"), cond, seed + 61)
    return f


def household_body(kind: str) -> list:
    if kind == "dresser":
        return [box((1, 0, 1), (15, 14, 14), {"north": "front", "south": "back", "east": "side", "west": "side", "up": "top", "down": "bottom"})]
    if kind == "school_desk":
        legs_ = [box((x, 0, z), (x + 1.5, 7, z + 1.5), {k: "leg" for k in ("north", "south", "east", "west", "down")})
                 for x in (1.5, 13) for z in (2.5, 12)]
        return hollow((1, 7, 2), (15, 11, 14)) + legs_
    # Cabinet-like bodies: back wall, two side walls, top, floor and one shelf.
    if kind == "kitchen_cupboard":
        x1, x2, z1, z2, top, shelf_y = 1, 15, 4, 15, 16, 7.5
    elif kind == "fridge":
        x1, x2, z1, z2, top, shelf_y = 2, 14, 3, 15, 16, 8
    else:  # safe
        x1, x2, z1, z2, top, shelf_y = 2, 14, 3, 14, 13, 6
    return [
        box((x1, 0, z2 - 1), (x2, top, z2), {"north": "inside_back", "south": "back", "east": "side", "west": "side", "up": "top", "down": "bottom"}),
        box((x1, 0, z1), (x1 + 1, top, z2 - 1), {"west": "side", "east": "inside_side", "north": "edge", "up": "top", "down": "bottom"}),
        box((x2 - 1, 0, z1), (x2, top, z2 - 1), {"east": "side", "west": "inside_side", "north": "edge", "up": "top", "down": "bottom"}),
        box((x1 + 1, top - 1, z1), (x2 - 1, top, z2 - 1), {"up": "top", "down": "inside_side", "north": "edge"}),
        box((x1 + 1, 0, z1), (x2 - 1, 1, z2 - 1), {"down": "bottom", "up": "inside_floor", "north": "edge"}),
        box((x1 + 1, shelf_y, z1 + 1), (x2 - 1, shelf_y + 1, z2 - 1), {"up": "shelf", "down": "shelf", "north": "edge"}),
    ]


def household_closed(kind: str) -> list:
    sides = {"east": "edge", "west": "edge", "up": "edge", "down": "edge"}
    if kind == "kitchen_cupboard":
        return [box((2, 1, 3), (8, 15, 4), {"north": "door", "south": "door", **sides}),
                box((8, 1, 3), (14, 15, 4), {"north": "door", "south": "door", **sides})]
    if kind == "fridge":
        return [box((3, 1, 2), (13, 15, 3), {"north": "door", "south": "door", **sides})]
    if kind == "safe":
        return [box((3, 1, 2), (13, 12, 3), {"north": "door", "south": "door", **sides})]
    if kind == "dresser":
        return [box((2, 8, 0), (14, 13, 1), {k: "drawer" for k in ("north", "south", "east", "west", "up", "down")})]
    return [box((1, 11, 2), (15, 12, 14), {"north": "lid_side", "south": "lid_side", "east": "lid_side", "west": "lid_side", "up": "lid_top", "down": "lid_inside"})]


def household_part(img, kind: str, fc: dict[str, np.ndarray]) -> None:
    sides = ("west", "north", "east", "south")
    if kind == "kitchen_cupboard":
        for u in (0, 16):
            put_all(img, cube_regions(u, 0, 6, 14, 1), {"north": fc["door"], "south": fc["door"], "west": fc["edge"],
                                                        "east": fc["edge"], "up": fc["edge"], "down": fc["edge"]})
    elif kind == "fridge":
        put_all(img, cube_regions(0, 0, 10, 14, 1), {"north": fc["door"], "south": fc["door"], "west": fc["edge"],
                                                     "east": fc["edge"], "up": fc["edge"], "down": fc["edge"]})
    elif kind == "safe":
        put_all(img, cube_regions(0, 0, 10, 11, 1), {"north": fc["door"], "south": fc["door"], "west": fc["edge"],
                                                     "east": fc["edge"], "up": fc["edge"], "down": fc["edge"]})
    elif kind == "dresser":
        put_all(img, cube_regions(0, 0, 12, 5, 1), {k: fc["drawer"] for k in ("up", "down", *sides)})
        put_all(img, cube_regions(0, 8, 11, 4, 10), {"up": fc["clothes"], "down": fc["side"], **{k: fc["side"] for k in sides}})
    else:
        put_all(img, cube_regions(0, 0, 14, 1, 12), {"up": fc["lid_top"], "down": fc["lid_inside"], **{k: fc["lid_side"] for k in sides}})


def faces(kind: str, cond: str) -> dict[str, np.ndarray]:
    seed = 6000 + KINDS.index(kind) * 100 + CONDITIONS.index(cond) * 10
    if kind == "military_crate":
        return crate_faces(cond, seed)
    if kind == "filing_cabinet":
        return filing_faces(cond, seed)
    if kind == "medical_cabinet":
        return cabinet_faces(cond, seed, "#d9d8cf", "#e6e4dc", medical=True)
    if kind == "supply_locker":
        return cabinet_faces(cond, seed, "#5a634f", "#3f453a", medical=False)
    if kind == "kitchen_cupboard":
        return cupboard_faces(cond, seed)
    if kind == "fridge":
        return fridge_faces(cond, seed)
    if kind == "dresser":
        return dresser_faces(cond, seed)
    if kind == "safe":
        return safe_faces(cond, seed)
    if kind == "school_desk":
        return desk_faces(cond, seed)
    return toolbox_faces(cond, seed)


# ----------------------------------------------------------------------------- geometry (JSON elements)

def face(tex: str, w: float, h: float, cull: str | None = None) -> dict:
    d = {"texture": f"#{tex}", "uv": [0, 0, round(w, 3), round(h, 3)]}
    if cull:
        d["cullface"] = cull
    return d


def box(frm, to, tex: dict) -> dict:
    """tex maps face -> texture variable; face sizes are derived from the box."""
    (x1, y1, z1), (x2, y2, z2) = frm, to
    w, h, d = x2 - x1, y2 - y1, z2 - z1
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    faces = {k: face(v, *sizes[k]) for k, v in tex.items()}
    return {"from": list(frm), "to": list(to), "faces": faces}


def hollow(frm, to, t: int = 1) -> list:
    """An open-topped box with real depth: the outer shell (no top), plus zero-thickness planes for
    the inner floor, the four inner walls facing into the cavity and the rim along the wall tops."""
    (x1, y1, z1), (x2, y2, z2) = frm, to
    ix1, iz1, ix2, iz2, iy = x1 + t, z1 + t, x2 - t, z2 - t, y1 + t
    return [
        box(frm, to, {"north": "front", "south": "back", "east": "side", "west": "side", "down": "bottom"}),
        box((ix1, iy, iz1), (ix2, iy, iz2), {"up": "inside_floor"}),
        box((ix1, iy, iz1), (ix2, y2, iz1), {"south": "inside_wall"}),
        box((ix1, iy, iz2), (ix2, y2, iz2), {"north": "inside_wall"}),
        box((ix1, iy, iz1), (ix1, y2, iz2), {"east": "inside_wall"}),
        box((ix2, iy, iz1), (ix2, y2, iz2), {"west": "inside_wall"}),
        box((x1, y2, z1), (x2, y2, iz1), {"up": "rim"}),
        box((x1, y2, iz2), (x2, y2, z2), {"up": "rim"}),
        box((x1, y2, iz1), (ix1, y2, iz2), {"up": "rim"}),
        box((ix2, y2, iz1), (x2, y2, iz2), {"up": "rim"}),
    ]


def body_elements(kind: str) -> list:
    if kind in ("kitchen_cupboard", "fridge", "dresser", "safe", "school_desk"):
        return household_body(kind)
    if kind == "military_crate":
        return hollow((1, 0, 2), (15, 10, 14))
    if kind == "filing_cabinet":
        return [box((2, 0, 1), (14, 16, 15), {"north": "front", "south": "back", "east": "side", "west": "side", "up": "top", "down": "bottom"})]
    if kind == "toolbox":
        return hollow((3, 0, 5), (13, 5, 11))
    if kind == "medical_cabinet":
        x1, x2, z1, z2 = 1, 15, 4, 15
    else:
        x1, x2, z1, z2 = 2, 14, 3, 15
    shelf_y = 7.5 if kind == "medical_cabinet" else 11
    return [
        box((x1, 0, z2 - 1), (x2, 16, z2), {"north": "inside_back", "south": "back", "east": "side", "west": "side", "up": "top", "down": "bottom"}),
        box((x1, 0, z1), (x1 + 1, 16, z2 - 1), {"west": "side", "east": "inside_side", "north": "edge", "up": "top", "down": "bottom"}),
        box((x2 - 1, 0, z1), (x2, 16, z2 - 1), {"east": "side", "west": "inside_side", "north": "edge", "up": "top", "down": "bottom"}),
        box((x1 + 1, 15, z1), (x2 - 1, 16, z2 - 1), {"up": "top", "down": "inside_side", "north": "edge"}),
        box((x1 + 1, 0, z1), (x2 - 1, 1, z2 - 1), {"down": "bottom", "up": "inside_floor", "north": "edge"}),
        box((x1 + 1, shelf_y, z1 + 1), (x2 - 1, shelf_y + 1, z2 - 1), {"up": "shelf", "down": "shelf", "north": "edge"}),
    ]


def closed_part_elements(kind: str) -> list:
    """The moving parts in their closed position, for the inventory/item model."""
    if kind in ("kitchen_cupboard", "fridge", "dresser", "safe", "school_desk"):
        return household_closed(kind)
    if kind == "military_crate":
        return [box((1, 10, 2), (15, 12, 14), {"north": "lid_side", "south": "lid_side", "east": "lid_side", "west": "lid_side", "up": "lid_top", "down": "lid_inside"})]
    if kind == "toolbox":
        return [box((3, 5, 5), (13, 7, 11), {"north": "lid_side", "south": "lid_side", "east": "lid_side", "west": "lid_side", "up": "lid_top", "down": "lid_inside"}),
                box((6, 7, 7.5), (10, 8, 8.5), {"north": "handle", "south": "handle", "east": "handle", "west": "handle", "up": "handle"})]
    if kind == "filing_cabinet":
        return [box((2.5, 8.5, 0), (13.5, 15.5, 1), {"north": "drawer", "south": "drawer", "east": "drawer", "west": "drawer", "up": "drawer", "down": "drawer"})]
    if kind == "medical_cabinet":
        return [box((2, 1, 3), (8, 15, 4), {"north": "door", "south": "door", "east": "edge", "west": "edge", "up": "edge", "down": "edge"}),
                box((8, 1, 3), (14, 15, 4), {"north": "door", "south": "door", "east": "edge", "west": "edge", "up": "edge", "down": "edge"})]
    return [box((3, 1, 2), (13, 15, 3), {"north": "door", "south": "door", "east": "edge", "west": "edge", "up": "edge", "down": "edge"})]


# ----------------------------------------------------------------------------- animated part textures (cube UV layout)
#
# The Java layer definitions declare a 64x32 texture; these images are 128x64. Minecraft maps model
# UVs relative to the declared size, so every part gets 2 texels per pixel, the same density as
# the block-model faces, and face art is copied in at full resolution (never downsampled).
#
# The renderer draws the parts y-up without the vanilla entity flip, so with the standard cube UV
# layout the side faces show their texture rotated 180 degrees and the top face mirrored front to
# back (vanilla's chest texture is laid out upside down for the same reason). put() undoes that,
# so art is always authored as seen from the front, top edge up, like the block-model faces.

PART_SCALE = 2
PART_TEX = (32 * PART_SCALE, 64 * PART_SCALE)


def cube_regions(u, v, w, h, d):
    """ModelPart cube UV regions (x, y, width, height) in image texels for texOffs(u, v), addBox(w, h, d)."""
    s = PART_SCALE
    return {k: tuple(int(round(c * s)) for c in reg) for k, reg in {
        "down": (u + d, v, w, d), "up": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }.items()}


def put(img, regions, face_name, src):
    x, y, w, h = regions[face_name]
    tile = src[:h, :w]
    assert tile.shape[:2] == (h, w), f"art too small for {face_name}: {tile.shape[:2]} < {(h, w)}"
    if face_name == "up":
        tile = tile[::-1]
    else:  # side faces, and the underside as seen when a lid is open
        tile = tile[::-1, ::-1]
    img[y:y + h, x:x + w] = tile


def put_all(img, regions, art: dict[str, np.ndarray]):
    for face_name, src in art.items():
        put(img, regions, face_name, src)


def part_texture(kind: str, cond: str, fc: dict[str, np.ndarray]) -> np.ndarray:
    img = np.zeros((*PART_TEX, 3))
    img[...] = N.hex_rgb("#202224")
    sides = ("west", "north", "east", "south")

    if kind in ("kitchen_cupboard", "fridge", "dresser", "safe", "school_desk"):
        household_part(img, kind, fc)
    elif kind == "military_crate":
        put_all(img, cube_regions(0, 0, 14, 2, 12), {"up": fc["lid_top"], "down": fc["lid_inside"], **{k: fc["lid_side"] for k in sides}})
    elif kind == "toolbox":
        put_all(img, cube_regions(0, 0, 10, 2, 6), {"up": fc["lid_top"], "down": fc["lid_inside"], **{k: fc["lid_side"] for k in sides}})
        put_all(img, cube_regions(0, 16, 4, 1, 1), {k: fc["handle"] for k in ("up", "down", *sides)})
    elif kind == "filing_cabinet":
        put_all(img, cube_regions(0, 0, 11, 7, 1), {k: fc["drawer"] for k in ("up", "down", *sides)})
        # The tray behind the drawer front, full of hanging folders.
        papers = np.zeros((24, 20, 3))
        papers[...] = N.hex_rgb("#1a1c1d")
        rr = N.rng(77)
        for row in range(0, 24, 3):
            colour = np.array(N.hex_rgb(rr.choice(["#c9b483", "#e2ddd0", "#b9a46f", "#d6d0bf"])))
            papers[row + 1, 2:18] = colour
            papers[row + 2, 2:18] = colour * 0.82
            tab = int(rr.integers(2, 14))
            papers[row + 1, tab:tab + 4] = colour * 1.1
        put_all(img, cube_regions(0, 8, 10, 6, 12), {"up": papers, "down": fc["side"], **{k: fc["side"] for k in sides}})
    elif kind == "medical_cabinet":
        for u in (0, 16):
            put_all(img, cube_regions(u, 0, 6, 14, 1), {"north": fc["door"], "south": fc["door"], "west": fc["edge"],
                                                        "east": fc["edge"], "up": fc["edge"], "down": fc["edge"]})
    else:
        put_all(img, cube_regions(0, 0, 10, 14, 1), {"north": fc["door"], "south": fc["door"], "west": fc["edge"],
                                                     "east": fc["edge"], "up": fc["edge"], "down": fc["edge"]})
    return np.clip(img, 0, 255)


# ----------------------------------------------------------------------------- screen sprites

def screen_sprites() -> None:
    s, b = 64, 8
    yy, xx = np.mgrid[0:s, 0:s]
    inner = (yy >= b) & (yy < s - b) & (xx >= b) & (xx < s - b)

    wood_frame = N.tile_window(painted_planks(48, 48, 7101, "#4a3826", board=6), s, s, b, b)
    wood_centre = N.tile_window(painted_planks(48, 48, 7102, "#2a2119", board=12, vertical=True), s, s, b, b)
    rgb = np.where(inner[..., None], wood_centre, wood_frame)
    frame(rgb, outline="#120c08", hi="#8a6e4c")
    alpha = np.ones((s, s))
    work = rgb.copy()
    for cy, cx in ((3.8, 3.8), (3.8, s - 3.8), (s - 3.8, 3.8), (s - 3.8, s - 3.8)):
        rivet(work, alpha, cy, cx, 2.0, rust_halo=0.4)
    rgb = np.where(inner[..., None], rgb, work)
    save_sprite(N.ASSETS, "widget/panel_wood.png", rgb, alpha, nine(s, s, b))

    enamel = N.tile_window(plate(48, 48, 7103, dark="#c8c6bc", mid="#d8d6cc", light="#e6e4da", rust=0.05), s, s, b, b)
    rgb = enamel.copy()
    frame(rgb, outline="#5d5a52", hi="#ffffff", lo="#9a978c")
    paint(rgb, np.s_[2:4, 2:-2], "#a8261c", 0.9)
    paint(rgb, np.s_[-4:-2, 2:-2], "#a8261c", 0.9)
    save_sprite(N.ASSETS, "widget/panel_medical.png", rgb, np.ones((s, s)), nine(s, s, b))

    slot = plate(18, 18, 7104, dark="#090b0c", mid="#0f1213", light="#161a1c", rust=0.0)
    frame(slot, outline="#050606", hi="#4a5357", inset=True)
    save_sprite(N.ASSETS, "widget/slot.png", slot, np.full((18, 18), 0.92))


# ----------------------------------------------------------------------------- loot

def item(name, weight, count=None, functions=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    fns = list(functions or [])
    if count:
        fns.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    if fns:
        e["functions"] = fns
    return e


def key(key_id, weight):
    return item(f"{NS}:key", weight, functions=[{"function": "minecraft:set_components", "components": {f"{NS}:key_id": key_id}}])


def damaged(lo, hi):
    return {"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def potion(p):
    return {"function": "minecraft:set_potion", "id": p}


def loot_table(path, rolls, entries):
    D.data_json(f"loot_table/{path}.json", {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0.0, "entries": entries}],
        "random_sequence": f"{NS}:{path}",
    })


def loot() -> None:
    mc = "minecraft:"
    loot_table("containers/military_crate", (3, 5), [
        item(mc + "arrow", 15, (4, 12)), item(mc + "iron_ingot", 10, (1, 3)), item(mc + "bread", 12, (1, 3)),
        item(mc + "gunpowder", 10, (1, 4)), item(mc + "leather", 8, (1, 3)), item(mc + "string", 8, (1, 4)),
        item(mc + "compass", 3), item(mc + "iron_helmet", 2, functions=[damaged(0.2, 0.7)]), item(mc + "spyglass", 2),
        item(mc + "flint_and_steel", 4, functions=[damaged(0.3, 0.8)])])
    loot_table("containers/military_crate_rare", (1, 1), [
        item(mc + "tnt", 5, (1, 2)), item(mc + "iron_chestplate", 3, functions=[damaged(0.1, 0.5)]), item(mc + "crossbow", 3, functions=[damaged(0.2, 0.6)]),
        item(f"{NS}:keycard_green", 6), item(f"{NS}:keycard_blue", 2), item(mc + "golden_apple", 1)])
    loot_table("containers/filing_cabinet", (2, 4), [
        item(mc + "paper", 20, (1, 6)), item(mc + "book", 8), item(mc + "writable_book", 4), item(mc + "map", 5),
        item(mc + "ink_sac", 6), item(mc + "name_tag", 2), item(mc + "compass", 2),
        key("records_room", 2), key("pharmacy", 2), key("maintenance", 2)])
    loot_table("containers/filing_cabinet_rare", (1, 1), [
        item(f"{NS}:keycard_green", 4), item(f"{NS}:keycard_blue", 1), item(mc + "clock", 3), item(mc + "emerald", 1)])
    loot_table("containers/medical_cabinet", (2, 4), [
        item(mc + "glass_bottle", 10, (1, 3)), item(mc + "paper", 6, (1, 2)), item(mc + "honey_bottle", 5),
        item(mc + "sugar", 6, (1, 3)), item(mc + "potion", 3, functions=[potion("minecraft:healing")]),
        item(mc + "potion", 2, functions=[potion("minecraft:regeneration")]), item(mc + "golden_carrot", 1)])
    loot_table("containers/medical_cabinet_rare", (1, 1), [
        item(mc + "golden_apple", 3), item(mc + "potion", 3, functions=[potion("minecraft:strong_healing")]), item(f"{NS}:keycard_green", 2)])
    loot_table("containers/supply_locker", (2, 4), [
        item(mc + "leather_helmet", 4, functions=[damaged(0.2, 0.8)]), item(mc + "leather_chestplate", 4, functions=[damaged(0.2, 0.8)]),
        item(mc + "leather_boots", 4, functions=[damaged(0.2, 0.8)]), item(mc + "bread", 8, (1, 2)), item(mc + "torch", 10, (2, 6)),
        item(mc + "shears", 4, functions=[damaged(0.1, 0.6)]), item(mc + "string", 8, (1, 4)), item(mc + "flint", 6, (1, 3)),
        item(mc + "iron_nugget", 8, (2, 6))])
    loot_table("containers/supply_locker_rare", (1, 1), [
        item(mc + "iron_boots", 3, functions=[damaged(0.1, 0.5)]), item(mc + "shield", 2, functions=[damaged(0.2, 0.6)]),
        item(f"{NS}:keycard_green", 3), key("maintenance", 2)])
    loot_table("containers/toolbox", (2, 3), [
        item(mc + "iron_nugget", 12, (2, 8)), item(mc + "iron_ingot", 6, (1, 2)), item(mc + "shears", 4, functions=[damaged(0.1, 0.6)]),
        item(mc + "flint_and_steel", 4, functions=[damaged(0.2, 0.7)]), item(mc + "iron_chain", 5, (1, 3)), item(mc + "string", 6, (1, 3)),
        item(mc + "lead", 3), item(mc + "iron_pickaxe", 2, functions=[damaged(0.2, 0.6)]), item(mc + "iron_axe", 2, functions=[damaged(0.2, 0.6)])])
    loot_table("containers/toolbox_rare", (1, 1), [
        item(mc + "diamond", 1), item(f"{NS}:keycard_green", 3), item(mc + "iron_pickaxe", 3, functions=[damaged(0.0, 0.3)])])
    # Household and school containers (vanilla items stand in until the survival items of Milestone 5).
    loot_table("containers/kitchen_cupboard", (2, 4), [
        item(mc + "bread", 8, (1, 2)), item(mc + "bowl", 8, (1, 3)), item(mc + "sugar", 6, (1, 3)), item(mc + "wheat", 4, (1, 3)),
        item(mc + "baked_potato", 5, (1, 2)), item(mc + "beetroot_soup", 3), item(mc + "glass_bottle", 6, (1, 2)), item(mc + "cocoa_beans", 3, (1, 2))])
    loot_table("containers/kitchen_cupboard_rare", (1, 1), [item(mc + "cake", 2), item(mc + "honey_bottle", 4), item(mc + "golden_carrot", 1)])
    loot_table("containers/fridge", (1, 3), [
        item(mc + "rotten_flesh", 10, (1, 3)), item(mc + "spider_eye", 3), item(mc + "milk_bucket", 2), item(mc + "carrot", 6, (1, 3)),
        item(mc + "apple", 5, (1, 2)), item(mc + "glass_bottle", 6, (1, 3)), item(mc + "honey_bottle", 3)])
    loot_table("containers/fridge_rare", (1, 1), [item(mc + "potion", 3, functions=[potion("minecraft:healing")]), item(mc + "cooked_beef", 3)])
    loot_table("containers/dresser", (2, 4), [
        item(mc + "leather_chestplate", 4, functions=[damaged(0.1, 0.6)]), item(mc + "leather_leggings", 4, functions=[damaged(0.1, 0.6)]),
        item(mc + "leather_boots", 4, functions=[damaged(0.1, 0.6)]), item(mc + "string", 8, (1, 4)), item(mc + "white_wool", 6, (1, 2)),
        item(mc + "paper", 6, (1, 3)), item(mc + "book", 3), item(mc + "clock", 1), key("maintenance", 1)])
    loot_table("containers/dresser_rare", (1, 1), [item(mc + "gold_nugget", 5, (2, 6)), item(mc + "emerald", 1), item(f"{NS}:keycard_green", 1)])
    loot_table("containers/safe", (2, 4), [
        item(mc + "gold_nugget", 10, (2, 8)), item(mc + "gold_ingot", 4, (1, 2)), item(mc + "emerald", 3, (1, 2)), item(mc + "paper", 8, (2, 6)),
        item(mc + "clock", 3), item(f"{NS}:keycard_green", 4), item(f"{NS}:keycard_blue", 2), key("records_room", 2)])
    loot_table("containers/safe_rare", (1, 1), [item(mc + "diamond", 2), item(f"{NS}:keycard_red", 1), item(mc + "golden_apple", 2)])
    loot_table("containers/school_desk", (1, 3), [
        item(mc + "paper", 12, (1, 4)), item(mc + "book", 4), item(mc + "writable_book", 3), item(mc + "apple", 4), item(mc + "string", 3),
        item(mc + "cookie", 4, (1, 3)), item(mc + "ink_sac", 3), item(mc + "feather", 3)])
    loot_table("containers/school_desk_rare", (1, 1), [item(mc + "map", 3), item(mc + "compass", 2), key("janitor", 1)])


# ----------------------------------------------------------------------------- build

def build() -> None:
    tags = D.TagCollector()
    lang = {}
    for kind in KINDS:
        parent = f"{NS}:block/container/{kind}"
        elements = body_elements(kind)
        D.asset_json(f"models/block/container/{kind}.json", {"parent": "minecraft:block/block", "elements": elements})
        variants = {}
        for cond in CONDITIONS:
            fc = faces(kind, cond)
            stale = N.asset(f"textures/block/container/{kind}/{cond}")
            if stale.exists():
                shutil.rmtree(stale)  # faces can be renamed between versions
            textures = {}
            for name, img in fc.items():
                N.save_png(N.asset(f"textures/block/container/{kind}/{cond}/{name}.png"), img)
                textures[name] = f"{NS}:block/container/{kind}/{cond}/{name}"
            textures["particle"] = textures["side"]
            D.asset_json(f"models/block/container/{kind}_{cond}.json", {"parent": parent, "textures": textures})
            N.save_png(N.asset(f"textures/entity/container/{kind}_{cond}.png"), part_texture(kind, cond, fc))
            for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                v = {"model": f"{NS}:block/container/{kind}_{cond}"}
                if rot:
                    v["y"] = rot
                variants[f"condition={cond},facing={facing}"] = v
            if cond == "clean":
                D.asset_json(f"models/item/{kind}.json", {
                    "parent": "minecraft:block/block",
                    "textures": textures,
                    "elements": elements + closed_part_elements(kind),
                })
        D.asset_json(f"blockstates/{kind}.json", {"variants": variants})
        D.item_model_definition(kind, f"{NS}:item/{kind}")
        D.loot_self(kind)
        tags.add("minecraft:mineable/axe" if kind in WOODEN else "minecraft:mineable/pickaxe", f"{NS}:{kind}")
        lang[f"block.{NS}.{kind}"] = ENGLISH[kind]
        lang[f"container.{NS}.{kind}"] = ENGLISH[kind]
    tags.write("containers")
    D.merge_lang(lang)
    screen_sprites()
    loot()


if __name__ == "__main__":
    build()
    print("containers written")
