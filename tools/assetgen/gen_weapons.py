"""DEADLANDS weapons (atmosphere overhaul): 32x32 icons painted pixel by pixel, held models, text,
recipes and loot.

Each weapon is laid along a spine from its grip (lower left) to its tip (upper right). Every pixel
is placed on that spine (how far along, how far across), which decides its material (grip, guard,
blade, head) and its shading: light from the upper left, a bright bevel along a cutting edge, a dark
spine, cylinders shaded round. Then wear: scratches along the steel, rust where it has been left
out, chipped paint, worn wood, wrapped grips, blood. A darker outline of each pixel's own colour
finishes the silhouette, as Minecraft's items have.

  repainted   crowbar, baseball bat, machete, fire axe, pistol, shotgun, hunting rifle
  new         survival knife, hunting knife, rusty machete, nail bat, lead pipe, pipe wrench
  vanilla     iron / diamond / netherite sword and axe, restyled as worn steel, tempered blue steel
              and blackened steel (assets/minecraft/textures/item)

Melee weapons use the handheld model (gripped like a tool); firearms have their own hold.
"""
from __future__ import annotations

import json
import math

import numpy as np

import dl_data as D
import dl_noise as N

NS = D.NS
SIZE = 32


def hexc(h):
    return np.array(N.hex_rgb(h), float)


STEEL = dict(base=hexc("#8e9296"), light=hexc("#cfd3d6"), dark=hexc("#53575c"))
TEMPERED = dict(base=hexc("#7c949e"), light=hexc("#c4dce4"), dark=hexc("#46585f"))
BLACKENED = dict(base=hexc("#45434a"), light=hexc("#8e8a96"), dark=hexc("#25242a"))
IRON = dict(base=hexc("#6e7174"), light=hexc("#a9adb0"), dark=hexc("#3e4144"))
WOOD = dict(base=hexc("#7a5836"), light=hexc("#a07a50"), dark=hexc("#4a3420"))
ASH_WOOD = dict(base=hexc("#a08a5c"), light=hexc("#c4ad7c"), dark=hexc("#6a5634"))
GRIP = dict(base=hexc("#2e2c2c"), light=hexc("#55524e"), dark=hexc("#161414"))
LEATHER = dict(base=hexc("#5a3a26"), light=hexc("#7e5638"), dark=hexc("#33200f"))
CLOTH = dict(base=hexc("#7c7462"), light=hexc("#a0977f"), dark=hexc("#4a4436"))
RED = dict(base=hexc("#8a2c24"), light=hexc("#b4483a"), dark=hexc("#511812"))
BRASS = dict(base=hexc("#8e7440"), light=hexc("#c0a060"), dark=hexc("#56441e"))
RUST = hexc("#7a4022")
RUST_DARK = hexc("#4a2614")
BLOOD = hexc("#4a0e0a")


class Icon:
    def __init__(self, seed, start=(5.5, 26.5), end=(26.5, 5.5)):
        self.rgb = np.zeros((SIZE, SIZE, 3))
        self.a = np.zeros((SIZE, SIZE))
        self.rng = np.random.default_rng(seed)
        self.seed = seed
        yy, xx = np.mgrid[0:SIZE, 0:SIZE] + 0.5
        d = np.array(end) - np.array(start)
        self.length = float(np.hypot(*d))
        self.dir = d / self.length
        self.normal = np.array([-self.dir[1], self.dir[0]])  # points to the lower right for a rising spine
        rel_x, rel_y = xx - start[0], yy - start[1]
        self.t = (rel_x * self.dir[0] + rel_y * self.dir[1]) / self.length
        self.s = rel_x * self.normal[0] + rel_y * self.normal[1]
        self.noise = N.spectral(SIZE, SIZE, beta=1.4, seed=seed)
        self.fine = N.spectral(SIZE, SIZE, beta=0.6, seed=seed + 1)

    def part(self, t0, t1, width, mat, offset=0.0, edge=None, round_=False, grain=False, wrap=0, taper=None):
        """Fill the stretch t0..t1 of the spine, `width` px either side (or (left, right) for blades),
        shaded as metal (edge: which side is the bevel), a cylinder, wood grain or a wrapped grip."""
        t, s = self.t, self.s
        u = np.clip((t - t0) / max(1e-6, t1 - t0), 0, 1)
        left, right = (width, width) if np.isscalar(width) else width
        if taper is not None:
            scale = taper(u)
            left, right = left * scale, right * scale
        off = offset(u) if callable(offset) else offset
        inside = (t >= t0) & (t <= t1) & (s >= off - left - 0.5) & (s <= off + right + 0.5)
        across = np.clip((s - off + left) / np.maximum(left + right, 1e-6), 0, 1)  # 0 at the upper-left side, 1 lower-right
        v = mat["base"] * (1.12 - 0.3 * across)[..., None]
        if round_:  # a highlight a third of the way across, darker toward the far side
            hl = np.exp(-((across - 0.3) / 0.16) ** 2)
            v = mat["base"] * (0.85 - 0.3 * across)[..., None] + (mat["light"] - mat["base"]) * hl[..., None] * 0.8
        if edge == "right":  # a bevel catching the light along the lower-right edge, a dark spine opposite
            v = np.where((across > 0.78)[..., None], mat["light"], v)
            v = np.where((across < 0.16)[..., None], mat["dark"], v)
        elif edge == "left":
            v = np.where((across < 0.22)[..., None], mat["light"], v)
            v = np.where((across > 0.85)[..., None], mat["dark"], v)
        if grain:
            stripes = (np.floor(s * 1.3 + self.noise * 2.0) % 2 == 0)
            v = v * (1 - 0.12 * stripes)[..., None]
        if wrap:
            bands = (np.floor(t * self.length / wrap + s * 0.6) % 2 == 0)
            v = v * (1 - 0.22 * bands)[..., None]
        v = v * (0.94 + 0.12 * self.noise)[..., None]
        self.rgb = np.where(inside[..., None], v, self.rgb)
        self.a = np.where(inside, 1.0, self.a)
        return inside

    def pixels(self, points, colour):
        for x, y in points:
            if 0 <= x < SIZE and 0 <= y < SIZE:
                self.rgb[y, x] = colour
                self.a[y, x] = 1.0

    def scratches(self, mask, count=4, strength=0.25):
        for _ in range(count):
            t0 = self.rng.uniform(0.0, 1.0)
            line = (np.abs(self.t - t0 - (self.s * self.rng.uniform(-0.03, 0.03))) < 0.012) & mask
            sign = 1 if self.rng.random() < 0.5 else -1
            self.rgb = np.where(line[..., None], self.rgb * (1 + sign * strength), self.rgb)

    def rust(self, mask, amount):
        patch = (self.noise + self.fine * 0.35) > (1.05 - amount)
        speck = (self.fine > 1.0 - amount * 0.5) & patch
        self.rgb = np.where((patch & mask)[..., None], self.rgb * 0.35 + RUST * 0.65, self.rgb)
        self.rgb = np.where((speck & mask)[..., None], RUST_DARK, self.rgb)

    def chips(self, mask, amount, colour):
        chip = (self.fine > 1.0 - amount) & mask
        self.rgb = np.where(chip[..., None], colour, self.rgb)

    def blood(self, mask, amount):
        b = (self.noise * 0.7 + self.fine * 0.5 > 1.0 - amount) & mask
        self.rgb = np.where(b[..., None], self.rgb * 0.3 + BLOOD * 0.7, self.rgb)

    def nick(self, mask, count):
        """Notches knocked out of a cutting edge."""
        edge_pixels = np.argwhere(mask & ~np.roll(mask, -1, axis=0))
        for k in self.rng.choice(len(edge_pixels), size=min(count, len(edge_pixels)), replace=False) if len(edge_pixels) else []:
            y, x = edge_pixels[k]
            self.a[y, x] = 0.0

    def finish(self):
        """A darker outline in each pixel's own colour, lighter on the lit upper-left side."""
        a = self.a > 0.5
        out = np.zeros_like(a)
        col = np.zeros_like(self.rgb)
        for dy, dx, shade in ((-1, 0, 0.55), (0, -1, 0.55), (1, 0, 0.38), (0, 1, 0.38)):
            nb = np.roll(np.roll(a, dy, axis=0), dx, axis=1)
            src = np.roll(np.roll(self.rgb, dy, axis=0), dx, axis=1)
            new = nb & ~a & ~out
            col = np.where(new[..., None], src * shade, col)
            out |= new
        rgb = np.where(out[..., None], col, self.rgb)
        alpha = np.where(out, 1.0, self.a)
        return np.clip(rgb, 0, 255), np.clip(alpha, 0, 1)


# ============================================================================= the weapons

def survival_knife(seed=6101):
    i = Icon(seed, (8.5, 24.5), (25.0, 8.0))
    i.part(0.0, 0.36, 1.5, GRIP, wrap=2)
    i.part(0.36, 0.42, 2.6, STEEL)
    blade = i.part(0.42, 1.0, (1.4, 2.0), STEEL, edge="right", taper=lambda u: np.clip(1.0 - u ** 2.4, 0.15, 1))
    serr = blade & (i.t > 0.47) & (i.t < 0.72) & (i.s < -0.6) & (np.floor(i.t * i.length) % 2 == 0)
    i.a = np.where(serr, 0.0, i.a)  # the saw back
    i.scratches(blade, 5)
    i.pixels([(9, 25)], hexc("#6a6a6a"))  # pommel
    return i.finish()


def hunting_knife(seed=6102):
    i = Icon(seed, (7.5, 25.5), (25.5, 7.5))
    i.part(0.0, 0.38, 1.7, dict(base=hexc("#6e5a44"), light=hexc("#9a8264"), dark=hexc("#3e3024")), grain=True)  # stag
    i.part(0.38, 0.43, 2.2, BRASS)
    blade = i.part(0.43, 1.0, (1.3, 2.2), STEEL, edge="right", offset=lambda u: -0.9 * np.sin(np.pi * u * 0.9),
                   taper=lambda u: np.clip(1.0 - u ** 3, 0.2, 1))
    i.scratches(blade, 3)
    i.blood(blade, 0.18)
    return i.finish()


def machete(seed=6103, rusty=False):
    i = Icon(seed, (5.0, 27.0), (27.5, 4.5))
    handle = i.part(0.0, 0.28, 1.6, CLOTH if rusty else GRIP, wrap=2 if rusty else 3)
    blade = i.part(0.28, 1.0, (1.2, 2.4), STEEL, edge="right", taper=lambda u: np.where(u > 0.86, np.clip((1 - u) / 0.14, 0.3, 1), 1 + 0.2 * u))
    i.scratches(blade, 6)
    if rusty:
        i.rust(blade, 0.55)
        i.nick(blade, 4)
        i.blood(handle, 0.25)
    else:
        i.rust(blade, 0.12)
    i.pixels([(7, 24)], hexc("#141212"))  # lanyard hole
    return i.finish()


def crowbar(seed=6104):
    i = Icon(seed, (7.0, 26.0), (23.5, 9.5))
    bar = i.part(0.0, 1.0, 1.1, RED, round_=True)
    i.chips(bar, 0.35, hexc("#6e7174"))
    i.pixels([(23, 8), (24, 8), (25, 9), (26, 10), (26, 11), (25, 12)], hexc("#7a2a22"))  # the hook
    i.pixels([(24, 7), (25, 8)], hexc("#a24038"))
    i.pixels([(5, 26), (6, 27), (5, 27)], hexc("#8a8e92"))  # the flat pry end
    i.rust(bar, 0.2)
    return i.finish()


def bat(seed=6105, nails=False):
    i = Icon(seed, (6.0, 26.5), (26.0, 6.5))
    profile = lambda u: np.clip(0.55 + 1.15 * np.clip((u - 0.3) / 0.45, 0, 1), 0.55, 1.7)
    handle = i.part(0.0, 0.32, 1.0, GRIP, wrap=2)
    barrel = i.part(0.32, 1.0, 1.4, ASH_WOOD, round_=True, grain=True, taper=lambda u: profile(0.32 + 0.68 * u))
    i.part(0.0, 0.05, 1.6, GRIP)  # knob
    i.blood(barrel, 0.22 if nails else 0.1)
    if nails:
        for k, tt in enumerate((0.6, 0.7, 0.8, 0.9)):
            cx, cy = 6.0 + 20 * tt, 26.5 - 20 * tt
            for side in (-1, 1):
                nx, ny = int(round(cx + i.normal[0] * side * 3.2)), int(round(cy + i.normal[1] * side * 3.2))
                i.pixels([(nx, ny)], hexc("#9a9ea2"))
                tip = (int(round(cx + i.normal[0] * side * 4.2)), int(round(cy + i.normal[1] * side * 4.2)))
                i.pixels([tip], hexc("#5a5e62") if k % 2 else hexc("#6a3a2a"))
    return i.finish()


def lead_pipe(seed=6106):
    i = Icon(seed, (6.5, 26.5), (25.0, 8.0))
    pipe = i.part(0.0, 0.88, 1.3, IRON, round_=True)
    i.part(0.88, 1.0, 1.9, IRON, round_=True)  # the fitting
    i.part(0.0, 0.26, 1.45, GRIP, wrap=2)  # tape
    i.rust(pipe & (i.t > 0.3), 0.3)
    i.blood(pipe & (i.t > 0.6), 0.2)
    return i.finish()


def pipe_wrench(seed=6107):
    i = Icon(seed, (6.0, 27.0), (24.0, 9.0))
    handle = i.part(0.0, 0.72, 1.3, RED, round_=True)
    i.chips(handle, 0.3, IRON["base"])
    i.part(0.68, 0.96, (2.6, 1.0), IRON, edge="left")  # the hook jaw
    i.part(0.82, 1.0, (1.0, 2.8), STEEL)  # the heel jaw
    gap = (i.t > 0.84) & (i.t < 0.9) & (np.abs(i.s) < 0.9)
    i.a = np.where(gap, 0.0, i.a)
    i.pixels([(18, 15), (19, 14)], hexc("#2a2a2a"))  # adjusting nut
    return i.finish()


def fire_axe(seed=6108):
    i = Icon(seed, (6.0, 27.0), (24.0, 9.0))
    i.part(0.0, 1.0, 1.0, dict(base=hexc("#b08a30"), light=hexc("#d4b058"), dark=hexc("#6a5018")), round_=True)  # fibreglass
    head = i.part(0.74, 0.98, (1.6, 6.0), RED, offset=lambda u: 0.0, taper=lambda u: np.clip(0.55 + 0.45 * np.sin(np.pi * u), 0.4, 1))
    edge = head & (i.s > 4.3)
    i.rgb = np.where(edge[..., None], STEEL["light"], i.rgb)
    i.part(0.8, 0.92, (3.4, 0.0), IRON, offset=-1.0, taper=lambda u: np.clip(1 - np.abs(u - 0.5) * 1.6, 0.2, 1))  # pick
    i.chips(head & ~edge, 0.25, IRON["base"])
    return i.finish()


def sword(seed, mat, grip=LEATHER):
    i = Icon(seed, (5.5, 26.5), (27.0, 5.0))
    i.part(0.0, 0.05, 1.7, BRASS if mat is STEEL else mat)  # pommel
    i.part(0.05, 0.25, 1.15, grip, wrap=2)
    i.part(0.25, 0.30, 3.8, mat)  # cross guard
    blade = i.part(0.30, 1.0, 1.6, mat, edge="right", taper=lambda u: np.where(u > 0.88, np.clip((1 - u) / 0.12, 0.2, 1), 1.0))
    fuller = blade & (np.abs(i.s) < 0.4) & (i.t < 0.85)
    i.rgb = np.where(fuller[..., None], i.rgb * 0.78, i.rgb)
    i.scratches(blade, 5, 0.2)
    if mat is STEEL:
        i.rust(blade, 0.1)
    return i.finish()


def hatchet(seed, mat):
    i = Icon(seed, (6.5, 26.5), (24.0, 9.0))
    i.part(0.0, 1.0, 1.0, WOOD, round_=True, grain=True)
    head = i.part(0.7, 1.0, (1.3, 4.6), mat, taper=lambda u: np.clip(0.6 + 0.4 * np.sin(np.pi * u), 0.5, 1))
    edge = head & (i.s > 3.4)
    i.rgb = np.where(edge[..., None], mat["light"], i.rgb)
    i.scratches(head, 3, 0.2)
    if mat is STEEL:
        i.rust(head & ~edge, 0.2)
    return i.finish()


def pistol(seed=6109):
    i = Icon(seed, (3.0, 12.0), (29.0, 12.0))  # side view, muzzle to the right
    slide = i.part(0.12, 0.98, 2.2, BLACKENED, edge="left")
    i.part(0.18, 0.62, (0.0, 4.6), BLACKENED, offset=1.0)  # frame and trigger guard
    grip = (np.abs((i.t * 26 + 3) - (8.0 + (i.s - 2.5) * 0.45)) < 2.6) & (i.s > 2.0) & (i.s < 13.0)
    i.rgb = np.where(grip[..., None], LEATHER["base"] * (0.85 + 0.2 * i.noise)[..., None], i.rgb)
    i.a = np.where(grip, 1.0, i.a)
    guard_hole = (np.abs(i.t * 26 + 3 - 15.0) < 1.6) & (i.s > 3.2) & (i.s < 5.6)
    i.a = np.where(guard_hole, 0.0, i.a)
    i.scratches(slide, 3, 0.2)
    i.pixels([(28, 11)], hexc("#0a0a0a"))
    return i.finish()


def long_gun(seed, stock_mat, double):
    i = Icon(seed, (2.5, 25.5), (29.5, 3.5))
    stock = i.part(0.0, 0.3, (1.4, 2.6), stock_mat, grain=True, taper=lambda u: np.clip(1.25 - u * 0.6, 0.6, 1.3))
    i.part(0.3, 0.46, 1.6, BLACKENED, edge="left")  # receiver
    i.part(0.46, 1.0, 0.8 if double else 0.6, BLACKENED, round_=True)  # barrel
    if double:
        i.part(0.5, 0.74, 1.3, stock_mat, grain=True, offset=0.9)  # pump
    else:
        i.part(0.34, 0.5, 1.0, BLACKENED, offset=-2.6)  # scope
        i.pixels([(12, 13)], hexc("#3a5a6a"))
    i.scratches(stock, 2, 0.2)
    return i.finish()


ICONS = {
    # name: (painter, English name, description, (bonus damage, swing speed) for melee or None)
    "survival_knife": (survival_knife, "Survival Knife", "Quick and quiet. A saw back for cutting what needs cutting.", (2.0, -1.6)),
    "hunting_knife": (hunting_knife, "Hunting Knife", "A deep belly for skinning. Fast in close.", (2.5, -1.8)),
    "machete": (machete, "Machete", "A good edge and a fair reach.", None),
    "rusty_machete": (lambda: machete(6110, rusty=True), "Rusty Machete", "Pitted and nicked, but it still bites. Wears out sooner.", (3.5, -2.5)),
    "crowbar": (crowbar, "Crowbar", "Quick and sturdy.", None),
    "baseball_bat": (bat, "Baseball Bat", "The fastest swing. Not the hardest hit.", None),
    "nail_bat": (lambda: bat(6111, nails=True), "Nail Bat", "A bat someone drove nails through. It hits harder than it swings.", (3.0, -2.2)),
    "lead_pipe": (lead_pipe, "Lead Pipe", "A length of plumbing with tape for a grip.", (3.0, -2.4)),
    "pipe_wrench": (pipe_wrench, "Pipe Wrench", "Heavy enough to end an argument.", (4.5, -2.8)),
    "fire_axe": (fire_axe, "Fire Axe", "Slow, heavy, and it ends things.", None),
    "pistol": (pistol, None, None, None),
    "shotgun": (lambda: long_gun(6112, WOOD, True), None, None, None),
    "hunting_rifle": (lambda: long_gun(6113, dict(base=hexc("#6a4a2a"), light=hexc("#8e6a44"), dark=hexc("#3a2614")), False), None, None, None),
}
VANILLA = {
    "iron_sword": lambda: sword(6201, STEEL), "diamond_sword": lambda: sword(6202, TEMPERED, GRIP), "netherite_sword": lambda: sword(6203, BLACKENED, GRIP),
    "iron_axe": lambda: hatchet(6204, STEEL), "diamond_axe": lambda: hatchet(6205, TEMPERED), "netherite_axe": lambda: hatchet(6206, BLACKENED),
}
NEW = ["survival_knife", "hunting_knife", "rusty_machete", "nail_bat", "lead_pipe", "pipe_wrench"]
FIREARMS = {"pistol", "shotgun", "hunting_rifle"}
# A knife sits lower and smaller in the hand than a sword; guns point ahead of you.
KNIFE_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 3.0, 0.75], "scale": [0.72, 0.72, 0.72]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 3.0, 0.75], "scale": [0.72, 0.72, 0.72]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.6, 1.13], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 2.6, 1.13], "scale": [0.6, 0.6, 0.6]},
}
GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, -35], "translation": [0, 1.25, -2.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, -90, 35], "translation": [0, 1.25, -2.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, 100, -15], "translation": [1.5, 3.5, -2.0], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, -100, 15], "translation": [1.5, 3.5, -2.0], "scale": [0.8, 0.8, 0.8]},
}
PISTOL_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3.0, 0.5], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3.0, 0.5], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"rotation": [0, 95, 0], "translation": [1.0, 3.6, -1.0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, -95, 0], "translation": [1.0, 3.6, -1.0], "scale": [0.55, 0.55, 0.55]},
}
LOOT = {  # where the new weapons turn up: table -> [(item, weight)]
    "containers/workshop": [("lead_pipe", 4), ("pipe_wrench", 3), ("rusty_machete", 2)],
    "containers/toolbox": [("pipe_wrench", 3), ("survival_knife", 2)],
    "containers/hunting": [("hunting_knife", 5), ("survival_knife", 3)],
    "containers/campsite": [("survival_knife", 3), ("nail_bat", 2), ("hunting_knife", 2)],
    "containers/house_kitchen": [("survival_knife", 1)],
    "containers/police_locker": [("survival_knife", 2)],
    "containers/military_crate": [("survival_knife", 3)],
    "containers/farm": [("rusty_machete", 3), ("lead_pipe", 1)],
    "containers/gas_station": [("lead_pipe", 2), ("nail_bat", 1)],
}


def write_model(name, display):
    model = {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}}
    if display:
        model["display"] = display
    D.asset_json(f"models/item/{name}.json", model)
    D.item_model_definition(name, f"{NS}:item/{name}")


def add_loot():
    root = N.ROOT / "src" / "main" / "resources" / "data" / NS / "loot_table"
    for table, entries in LOOT.items():
        path = root / f"{table}.json"
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        pool = data["pools"][0]
        names = {e.get("name") for e in pool["entries"]}
        for item, weight in entries:
            if f"{NS}:{item}" not in names:
                pool["entries"].append({"type": "minecraft:item", "name": f"{NS}:{item}", "weight": weight})
        path.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8")


def recipes():
    def shaped(name, pattern, key, result):
        D.data_json(f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
                                            "key": key, "result": {"id": f"{NS}:{result}", "count": 1}})

    shaped("survival_knife", ["I", "S"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, "survival_knife")
    shaped("lead_pipe", ["I", "I", "I"], {"I": "minecraft:iron_ingot"}, "lead_pipe")
    D.data_json("recipe/nail_bat.json", {"type": "minecraft:crafting_shapeless", "category": "equipment",
                                         "ingredients": [f"{NS}:baseball_bat", "minecraft:iron_nugget", "minecraft:iron_nugget", "minecraft:iron_nugget"],
                                         "result": {"id": f"{NS}:nail_bat", "count": 1}})


def sheet(images):
    """Review sheet: every icon at 6x on a dark ground."""
    cols = 6
    rows = (len(images) + cols - 1) // cols
    out = np.full((rows * (SIZE * 6 + 8), cols * (SIZE * 6 + 8), 3), 46.0)
    for k, (rgb, a) in enumerate(images):
        big = np.kron(rgb, np.ones((6, 6, 1)))
        al = np.kron(a, np.ones((6, 6)))[..., None]
        y, x = (k // cols) * (SIZE * 6 + 8) + 4, (k % cols) * (SIZE * 6 + 8) + 4
        out[y:y + SIZE * 6, x:x + SIZE * 6] = out[y:y + SIZE * 6, x:x + SIZE * 6] * (1 - al) + big * al
    N.save_png(N.ROOT / "tools" / "assetgen" / "_cache" / "weapons_preview.png", out)


def build() -> int:
    lang = {}
    images = []
    for name, (painter, title, desc, _) in ICONS.items():
        rgb, a = painter()
        N.save_png(N.asset(f"textures/item/{name}.png"), rgb, a)
        images.append((rgb, a))
        if name in FIREARMS:
            write_model(name, PISTOL_DISPLAY if name == "pistol" else GUN_DISPLAY)
        else:
            write_model(name, KNIFE_DISPLAY if "knife" in name else None)
        if title:
            lang[f"item.{NS}.{name}"] = title
            lang[f"item.{NS}.{name}.desc"] = desc
    for name, painter in VANILLA.items():
        rgb, a = painter()
        N.save_png(N.ROOT / "src" / "main" / "resources" / "assets" / "minecraft" / "textures" / "item" / f"{name}.png", rgb, a)
        images.append((rgb, a))
    lang.update({"item.minecraft.diamond_sword": "Tempered Steel Sword", "item.minecraft.netherite_sword": "Blackened Steel Sword",
                 "item.minecraft.diamond_axe": "Tempered Steel Axe", "item.minecraft.netherite_axe": "Blackened Steel Axe"})
    D.merge_lang(lang)
    recipes()
    add_loot()
    sheet(images)
    return len(images)


if __name__ == "__main__":
    print(f"{build()} weapon icons written")
