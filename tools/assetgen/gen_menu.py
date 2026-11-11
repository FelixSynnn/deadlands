"""The DEADLANDS main-menu backdrop (atmosphere overhaul): a dead city street at sunset.

A one-point-perspective street runs toward a low sun behind a ruined skyline. Everything is placed in
a small 3D street (metres; camera 1.7 m above the road) and projected, so buildings, poles, wires,
cars and road markings all recede to the same vanishing point, and each takes on the haze of its
distance (atmospheric perspective). Edges that face the sun catch a warm rim of light; faces toward
the camera are backlit and dark. The left of the frame stays in shadow for the menu.

Layers (assets/deadlands/textures/gui/title/, 1920x1080; drawn back to front with parallax):
  sky.png         opaque: dusk gradient, the sun low in the haze, lit cloud banks, smoke plumes
  skyline_far.png far towers, a crane, a water tower and a broken overpass across the street
  skyline_mid.png the street's buildings: broken rooflines, empty windows, ivy, rooftop trees,
                  poles and sagging wires, a hanging traffic light
  skyline_near.png the road (cracked asphalt, faded lines, puddles holding the glow), the near
                  building on the left, abandoned cars and a bus, a leaning pole, a dead tree
  plants.png      foreground weeds and grass, drawn separately so the menu can sway them
  rays.png        light shafts from the sun (drawn at low strength, slowly breathing)
  fog.png         a horizontally tileable fog band (drifts across the street)
Everything is deterministic (fixed seeds).
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import dl_noise as N

W, H = 1920, 1080
SS = 2  # supersampling for the polygon passes
OUT = "textures/gui/title/"

VP = (1185.0, 575.0)  # vanishing point (the end of the street), screen pixels
FOCAL = 980.0
EYE = 1.7

SKY_TOP = np.array([24, 28, 36], float)
SKY_MID = np.array([86, 76, 78], float)
HORIZON = np.array([178, 118, 76], float)
SUN = np.array([246, 196, 132], float)
HAZE = np.array([152, 116, 90], float)
RIM = np.array([236, 168, 104], float)
SHADOW = np.array([14, 14, 17], float)


def proj(x, y, z):
    """World (x right, y up, z forward) to screen pixels."""
    z = max(z, 0.05)
    return VP[0] + x / z * FOCAL, VP[1] - (y - EYE) / z * FOCAL


def haze_amount(z, start=6.0, scale=115.0):
    return 1.0 - math.exp(-max(0.0, z - start) / scale)


class Canvas:
    """RGBA float layer with polygon painting through supersampled masks."""

    def __init__(self, opaque=False):
        self.rgb = np.zeros((H, W, 3))
        self.a = np.ones((H, W)) if opaque else np.zeros((H, W))

    @staticmethod
    def mask(polys, blur=0.0, lines=None, width=1):
        img = Image.new("L", (W * SS, H * SS), 0)
        d = ImageDraw.Draw(img)
        for poly in polys:
            if len(poly) >= 3:
                d.polygon([(x * SS, y * SS) for x, y in poly], fill=255)
        for line in lines or []:
            d.line([(x * SS, y * SS) for x, y in line], fill=255, width=max(1, int(width * SS)))
        img = img.resize((W, H), Image.LANCZOS)
        if blur:
            img = img.filter(ImageFilter.GaussianBlur(blur))
        return np.asarray(img, float) / 255.0

    def paint(self, m, colour, alpha=1.0):
        a = np.clip(m * alpha, 0, 1)
        col = colour if np.ndim(colour) == 3 else np.asarray(colour, float)[None, None, :]
        self.rgb = self.rgb * (1 - a[..., None]) + col * a[..., None]
        self.a = self.a + a * (1 - self.a)

    def image(self):
        out = np.dstack([np.clip(self.rgb, 0, 255), np.clip(self.a * 255, 0, 255)]).astype(np.uint8)
        return out


def noise(seed, beta=2.0, h=H, w=W):
    return N.spectral(h, w, beta=beta, seed=seed)


def rim_of(m, dx, dy, strength=1.0):
    """The part of a silhouette whose edge faces the light (light comes from +dx, +dy)."""
    shifted = np.roll(np.roll(m, -int(round(dy)), axis=0), -int(round(dx)), axis=1)
    return np.clip(m - shifted, 0, 1) * strength


def towards_sun(x, y):
    dx, dy = VP[0] - x, (VP[1] - 40) - y
    d = math.hypot(dx, dy) + 1e-6
    return dx / d, dy / d


# ============================================================================= sky

def sky(seed=4101):
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    t = yy / H
    up = np.clip((VP[1] - yy) / VP[1], 0, 1)
    rgb = np.zeros((H, W, 3))
    for c in range(3):
        rgb[..., c] = np.interp(t, [0.0, 0.32, 0.5, 0.56, 1.0], [SKY_TOP[c], SKY_MID[c], HORIZON[c] * 0.92, HORIZON[c], HORIZON[c] * 0.7])
    # the sun, low in the haze at the end of the street, and its glow
    sx, sy = VP[0] + 30, VP[1] - 60
    d = np.hypot(xx - sx, (yy - sy) * 1.35)
    glow = np.exp(-d / 360.0) * 0.75 + np.exp(-d / 110.0) * 0.6
    disc = np.clip(1.0 - (d - 26.0) / 6.0, 0, 1)
    rgb = rgb + (SUN - rgb) * np.clip(glow, 0, 1)[..., None] * 0.85
    rgb = rgb + (np.array([255, 226, 180]) - rgb) * disc[..., None] * 0.85
    # cloud banks: stretched noise, dark bodies with undersides lit from the low sun
    n1 = noise(seed, 3.1)
    n2 = noise(seed + 1, 2.2)
    stretch = np.asarray(Image.fromarray((n1 * 255).astype(np.uint8)).resize((W * 5, H), Image.BICUBIC).crop((W * 2, 0, W * 3, H)), float) / 255.0
    bands = np.clip((stretch * 0.82 + n2 * 0.18 - 0.5) * 2.4, 0, 1) * np.clip((VP[1] - 60 - yy) / 380.0, 0, 1) ** 0.6
    lit = np.clip(np.roll(bands, -6, axis=0) - bands, 0, 1)  # undersides
    cloud_col = np.array([60, 54, 58]) * (1 - up[..., None] * 0.4) + HORIZON * 0.25
    rgb = rgb * (1 - bands[..., None] * 0.7) + cloud_col * bands[..., None] * 0.7
    rgb = rgb + RIM * lit[..., None] * (0.55 * np.exp(-np.abs(xx - sx) / 700.0))[..., None]
    # smoke plumes from fires beyond the city, leaning in the wind
    for px, width, strength in ((1490, 70, 0.55), (760, 50, 0.4), (1690, 40, 0.3)):
        lean = (VP[1] - yy) * 0.35
        col = np.exp(-((xx - px - lean) / (width + (VP[1] - yy).clip(0) * 0.25)) ** 2)
        tex = noise(seed + px, 2.2)
        plume = col * np.clip((VP[1] + 10 - yy) / 520.0, 0, 1) * (0.5 + 0.6 * tex) * strength
        rgb = rgb * (1 - plume[..., None]) + np.array([34, 32, 34]) * plume[..., None]
    rgb += (noise(seed + 9, 1.0)[..., None] - 0.5) * 6.0
    return np.dstack([np.clip(rgb, 0, 255), np.full((H, W), 255.0)]).astype(np.uint8)


# ============================================================================= far skyline

def broken_top(x0, x1, y_top, rng, jag=24.0, steps=10):
    pts = []
    for i in range(steps + 1):
        x = x0 + (x1 - x0) * i / steps
        drop = rng.uniform(0, jag) if rng.random() < 0.55 else 0.0
        pts.append((x, y_top + drop))
    return pts


def skyline_far(seed=4201):
    rng = np.random.default_rng(seed)
    c = Canvas()
    polys = []
    horizon = VP[1] + 18
    x = 520.0
    while x < 1900:
        w = rng.uniform(34, 90)
        h = rng.uniform(70, 260) * (1.0 - abs(x - VP[0]) / 2400.0)
        top = horizon - h
        pts = [(x, horizon)] + broken_top(x, x + w, top, rng, jag=h * 0.15) + [(x + w, horizon)]
        polys.append(pts)
        if rng.random() < 0.25:  # a tower with a broken spire
            polys.append([(x + w * 0.4, top), (x + w * 0.5, top - rng.uniform(30, 70)), (x + w * 0.55, top)])
        x += w + rng.uniform(-8, 14)
    m = c.mask(polys, blur=1.2)
    tex = noise(seed, 1.6)
    col = HAZE * 0.82 + (tex[..., None] - 0.5) * 10
    c.paint(m, col)
    # windows catching the last light, very few
    glints = (noise(seed + 2, 0.3) > 0.985) * m
    c.paint(glints * 0.7, RIM)
    # a crane and a water tower against the glow
    crane = c.mask([], lines=[[(1560, horizon - 250), (1560, horizon)], [(1470, horizon - 245), (1690, horizon - 250)],
                              [(1560, horizon - 250), (1660, horizon - 210)], [(1640, horizon - 248), (1640, horizon - 200)]], width=3)
    c.paint(crane, HAZE * 0.72)
    tank = c.mask([[(905, horizon - 190), (965, horizon - 190), (968, horizon - 150), (902, horizon - 150)]],
                  lines=[[(910, horizon - 150), (900, horizon)], [(960, horizon - 150), (970, horizon)], [(935, horizon - 150), (935, horizon)]], width=3)
    c.paint(tank, HAZE * 0.74)
    # the overpass: a span across the street at 130 m, broken in the middle, its end hanging down
    z = 130.0
    left = [proj(-40, 9.0, z), proj(-2.5, 9.0, z), proj(-2.0, 7.4, z), proj(-40, 7.4, z)]
    right = [proj(4.5, 9.0, z), proj(60, 9.0, z), proj(60, 7.4, z), proj(5.5, 6.8, z)]
    hang = [proj(-2.5, 9.0, z), proj(-1.2, 8.6, z), proj(0.8, 3.5, z), proj(-0.2, 3.3, z), proj(-2.0, 7.4, z)]
    pillars = [[proj(px - 0.8, 7.4, z), proj(px + 0.8, 7.4, z), proj(px + 0.8, 0, z), proj(px - 0.8, 0, z)] for px in (-22, -12, 16, 28)]
    om = c.mask([left, right, hang] + pillars, blur=0.8)
    c.paint(om, HAZE * 0.6 + np.array([-8, -6, -4]))
    c.paint(rim_of(om, 0, -2, 0.9), RIM * 0.9)
    return c.image()


# ============================================================================= the street, textured in world space
#
# Walls, fronts and the road are painted per pixel: each pixel is projected back onto its plane, so
# floors, windows, bricks, soot, holes, ivy, cracks, paint and puddles are laid out in metres and
# recede correctly, and each pixel takes the haze of its own distance.

TEX = N.spectral(512, 512, beta=1.9, seed=4991)
TEX_FINE = N.spectral(512, 512, beta=1.1, seed=4992)
TEX_SOFT = N.spectral(512, 512, beta=3.3, seed=4993)  # broad, smooth shapes: puddles, patches, where cracks gather
YY, XX = np.mgrid[0:H, 0:W].astype(float)
MATERIALS = {"concrete": np.array([96, 94, 90], float), "brick": np.array([104, 74, 60], float), "plaster": np.array([114, 104, 88], float),
             "soot": np.array([66, 60, 56], float)}


def sample(tex, u, v):
    """Bilinear lookup in a tileable texture at texel coordinates."""
    size = tex.shape[0]
    u = np.mod(u, size)
    v = np.mod(v, size)
    uf, vf = np.floor(u), np.floor(v)
    fu, fv = u - uf, v - vf
    u0, v0 = uf.astype(int) % size, vf.astype(int) % size
    u1, v1 = (u0 + 1) % size, (v0 + 1) % size
    return tex[v0, u0] * (1 - fu) * (1 - fv) + tex[v0, u1] * fu * (1 - fv) + tex[v1, u0] * (1 - fu) * fv + tex[v1, u1] * fu * fv


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def box_of(polys, pad=3):
    xs = [p[0] for poly in polys for p in poly]
    ys = [p[1] for poly in polys for p in poly]
    return (int(max(0, min(xs) - pad)), int(min(W, max(xs) + pad + 1)), int(max(0, min(ys) - pad)), int(min(H, max(ys) + pad + 1)))


def mask_box(polys, box, lines=None, width=1.0):
    """Anti-aliased coverage of polygons inside a screen box (supersampled)."""
    x0, x1, y0, y1 = box
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    img = Image.new("L", (w * SS, h * SS), 0)
    d = ImageDraw.Draw(img)
    for poly in polys:
        if len(poly) >= 3:
            d.polygon([((x - x0) * SS, (y - y0) * SS) for x, y in poly], fill=255)
    for line in lines or []:
        d.line([((x - x0) * SS, (y - y0) * SS) for x, y in line], fill=255, width=max(1, int(width * SS)))
    return np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0


def put(c: "Canvas", box, m, rgb):
    x0, x1, y0, y1 = box
    a = np.clip(m, 0, 1)[..., None]
    c.rgb[y0:y1, x0:x1] = c.rgb[y0:y1, x0:x1] * (1 - a) + rgb * a
    c.a[y0:y1, x0:x1] = c.a[y0:y1, x0:x1] + a[..., 0] * (1 - c.a[y0:y1, x0:x1])


def fogged(rgb, z, strength=1.0):
    amount = (1.0 - np.exp(-np.maximum(0.0, z - 6.0) / 115.0)) * strength
    return rgb * (1 - amount[..., None]) + HAZE * amount[..., None]


class Building:
    def __init__(self, rng, side, plane, z0, length, height, shade=1.0):
        self.side, self.plane, self.z0, self.z1, self.h = side, plane, z0, z0 + length, height
        self.material = str(rng.choice(list(MATERIALS)))
        self.floor = rng.uniform(3.0, 3.6)
        self.bay = rng.uniform(2.2, 3.1)
        self.win_w = rng.uniform(0.36, 0.6)
        self.win_h = rng.uniform(0.42, 0.56)
        self.damage = rng.uniform(0.0, 1.0)
        self.ivy = rng.uniform(0.0, 1.0)
        self.seed = int(rng.integers(1 << 20))
        self.depth = rng.uniform(8, 14)
        self.shade = shade
        n = 12
        self.top_z = np.linspace(self.z0, self.z1, n)
        tops = []
        for _ in range(n):
            tops.append(height - (rng.uniform(0.4, 3.5) * (0.3 + self.damage) if rng.random() < 0.35 + 0.4 * self.damage else 0.0))
        self.top_y = np.array(tops)
        if self.damage > 0.65 and rng.random() < 0.6:
            k = int(rng.integers(n // 2, n))
            self.top_y[k:] -= rng.uniform(3, 9)
        self.top_y = np.maximum(self.top_y, 3.5)

    def top(self, z):
        return np.interp(z, self.top_z, self.top_y)

    def wall_poly(self):
        pts = [proj(self.plane, 0, self.z0), proj(self.plane, 0, self.z1)]
        pts += [proj(self.plane, y, z) for z, y in zip(self.top_z[::-1], self.top_y[::-1])]
        return pts

    def front_poly(self):
        xa, xb = self.plane, self.plane + self.side * self.depth
        h = self.top_y[0]
        return [proj(xa, 0, self.z0), proj(xb, 0, self.z0), proj(xb, h - 1.5 * self.damage, self.z0), proj(xa, h, self.z0)]


def window_kinds(b, bay, floor):
    """Hash each window to dark (most), glass catching the sky, boarded up, or burned (soot above)."""
    h = ((bay * 73856093) ^ (floor * 19349663) ^ (b.seed * 83492791)) & 0xFFFF
    r = h / 65535.0
    return np.where(r < 0.1, 1, np.where(r < 0.22, 2, np.where(r < 0.3, 3, 0)))  # 1 glass, 2 boarded, 3 burned, 0 dark


def surface(b, along, y, z, outward):
    """Shade a wall or front given along-wall metres, height and depth (arrays). Returns rgb."""
    u, v = along * 26 + b.seed % 512, y * 26
    base = MATERIALS[b.material] * (0.8 + 0.42 * (sample(TEX, u, v) - 0.5) + 0.22 * (sample(TEX_FINE, u * 3, v * 3) - 0.5))[..., None]
    if b.material in ("brick", "soot"):
        course = (y / 0.075) % 1.0 < 0.14
        base = base * (1 - 0.1 * course)[..., None]
    floor_i = np.floor(y / b.floor)
    fv = (y % b.floor) / b.floor
    base = base * (1 - 0.16 * (fv < 0.05))[..., None]
    # rain streaks: dirt run down from every sill
    streak = sample(TEX_FINE, u * 0.4, v * 0.04) - 0.5
    base = base * (1 + 0.18 * streak)[..., None]
    bay_i = np.floor(along / b.bay)
    fu = (along / b.bay) % 1.0
    centre = 0.55
    win = (np.abs(fu - 0.5) < b.win_w / 2) & (np.abs(fv - centre) < b.win_h / 2) & (y > 1.2) & (y < b.top(z) - 0.9)
    frame = (np.abs(fu - 0.5) < b.win_w / 2 + 0.035) & (np.abs(fv - centre) < b.win_h / 2 + 0.03) & ~win & (y > 1.2) & (y < b.top(z) - 0.6)
    kind = window_kinds(b, bay_i.astype(np.int64), floor_i.astype(np.int64))
    rgb = base * (1 + 0.12 * frame)[..., None]
    dark = np.array([13, 13, 15], float) + (sample(TEX, u * 2, v * 2) - 0.5)[..., None] * 10
    glass = (HAZE * 0.55 + RIM * 0.25) * (0.75 + 0.35 * (fv - centre + b.win_h / 2)[..., None])
    boards = np.array([74, 58, 44], float) * (0.85 + 0.3 * ((y * 6) % 1.0 < 0.75))[..., None]
    pane = np.where((kind == 1)[..., None], glass, np.where((kind == 2)[..., None], boards, dark))
    rgb = np.where(win[..., None], pane, rgb)
    # soot climbing above burned windows
    above = (np.abs(fu - 0.5) < b.win_w / 2 + 0.08) & (fv > centre + b.win_h / 2) & (kind == 3)
    soot = np.exp(-(fv - centre - b.win_h / 2) * 4.5) * above
    rgb = rgb * (1 - 0.55 * soot)[..., None]
    # holes torn in the wall: dark interiors with a ragged, lighter rim
    hole_n = sample(TEX, u * 0.22 + 211, v * 0.22 + 97)
    hole = smooth(0.80 - 0.12 * b.damage, 0.82 - 0.12 * b.damage, hole_n) * (y > 2.0)
    lip = smooth(0.76 - 0.12 * b.damage, 0.80 - 0.12 * b.damage, hole_n) - hole
    rgb = rgb * (1 - 0.85 * hole)[..., None] + np.array([150, 140, 126]) * (0.35 * np.clip(lip, 0, 1))[..., None]
    # ivy: climbing from the street and pouring from the roofline
    ivy_n = sample(TEX, u * 0.5 + 411, v * 0.5 + 33)
    reach_up = 2.0 + 9.0 * b.ivy * ivy_n
    reach_down = b.top(z) - (1.5 + 7.0 * b.ivy * sample(TEX, u * 0.3 + 77, v * 0.1))
    ivy = smooth(0.42, 0.5, ivy_n * (1.2 if b.ivy > 0.5 else 0.9)) * ((y < reach_up) | (y > reach_down))
    leaf = sample(TEX_FINE, u * 5, v * 5)
    green = np.array([40, 48, 32], float) * (0.6 + 0.8 * leaf)[..., None]
    rgb = rgb * (1 - ivy[..., None]) + green * ivy[..., None]
    # ground grime
    rgb = rgb * (0.7 + 0.3 * np.clip(y / 3.0, 0, 1))[..., None]
    return rgb * outward


def paint_building(c, b):
    wall = b.wall_poly()
    box = box_of([wall])
    if box[1] > box[0] and box[3] > box[2]:
        x0, x1, y0, y1 = box
        sx, sy = XX[y0:y1, x0:x1], YY[y0:y1, x0:x1]
        dx = sx - VP[0]
        dx = np.where(np.abs(dx) < 0.5, np.sign(b.plane) * 0.5, dx)
        z = np.clip(b.plane * FOCAL / dx, 0.1, 2000)
        y = EYE - (sy - VP[1]) * z / FOCAL
        light = 0.55 + 0.25 * (1.0 - np.exp(-np.maximum(0, z - 6) / 115.0))
        rgb = surface(b, z - b.z0, y, z, light[..., None]) * b.shade
        m = mask_box([wall], box)
        put(c, box, m, fogged(rgb, z))
        rim = rim_of_box(m, -b.side * 1.5, -1.2)
        put(c, box, rim * 0.75 * (0.5 + 0.5 * np.exp(-np.abs(sx - VP[0]) / 500.0)), RIM * (1.0 if b.shade > 0.7 else 0.6))
    front = b.front_poly()
    box = box_of([front])
    if box[1] > box[0] and box[3] > box[2]:
        x0, x1, y0, y1 = box
        sx, sy = XX[y0:y1, x0:x1], YY[y0:y1, x0:x1]
        x = (sx - VP[0]) * b.z0 / FOCAL
        y = EYE - (sy - VP[1]) * b.z0 / FOCAL
        zz = np.full_like(x, b.z0)
        rgb = surface(b, np.abs(x - b.plane), y, zz, 0.32) * b.shade
        m = mask_box([front], box)
        put(c, box, m, fogged(rgb, zz))
        put(c, box, rim_of_box(m, 0, -2) * 0.55, RIM * 0.8)


def rim_of_box(m, dx, dy):
    shifted = np.roll(np.roll(m, -int(round(dy)), axis=0), -int(round(dx)), axis=1)
    return np.clip(m - shifted, 0, 1)


def street_buildings(rng):
    buildings = []
    for side, plane, start in ((-1, -11.0, 26.0), (1, 11.5, 15.0)):
        z = start
        while z < 160:
            length = rng.uniform(9, 22)
            h = rng.uniform(8, 24) if z < 70 else rng.uniform(12, 34)
            buildings.append(Building(rng, side, plane, z, length, h))
            z += length + (rng.uniform(1.0, 5.0) if rng.random() < 0.4 else 0.2)
    return buildings


def skyline_mid(seed=4301):
    rng = np.random.default_rng(seed)
    c = Canvas()
    buildings = street_buildings(rng)
    buildings.sort(key=lambda b: -b.z0)
    for b in buildings:
        paint_building(c, b)
        if rng.random() < 0.22:  # a tree on the roof
            bx, by = proj(b.plane, float(b.top(b.z0 + 3)), b.z0 + 3)
            r = 3.0 / (b.z0 + 3) * FOCAL
            blobs = []
            for _ in range(10):
                ox, oy = rng.normal(0, r * 0.55), rng.normal(-r * 0.7, r * 0.4)
                rr = r * rng.uniform(0.3, 0.55)
                blobs.append([(bx + ox + rr * math.cos(a), by + oy + rr * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 16)])
            box = box_of(blobs)
            m = mask_box(blobs, box)
            amount = haze_amount(b.z0)
            tex = sample(TEX_FINE, XX[box[2]:box[3], box[0]:box[1]] * 0.8, YY[box[2]:box[3], box[0]:box[1]] * 0.8)
            col = (np.array([30, 36, 26]) * (1 - amount) + HAZE * 0.8 * amount)[None, None, :] * (0.7 + 0.6 * tex)[..., None]
            put(c, box, m * (tex > 0.32), col)
            put(c, box, rim_of_box(m, 0, -2) * 0.6, RIM * 0.85)
    # utility poles along the right kerb, sagging wires, one leaning
    tops = []
    for k, z in enumerate((30, 48, 70, 96, 128)):
        lean = 1.4 if k == 1 else 0.0
        base, top = proj(9.6, 0, z), proj(9.6 - lean, 10.5, z)
        w = max(1.4, 0.32 / z * FOCAL)
        pole = [(base[0] - w, base[1]), (base[0] + w, base[1]), (top[0] + w, top[1]), (top[0] - w, top[1])]
        arm = [proj(8.0 - lean, 9.9, z), proj(11.2 - lean, 9.9, z), proj(11.2 - lean, 9.55, z), proj(8.0 - lean, 9.55, z)]
        insulators = [[proj(xx - lean - 0.12, 9.9, z), proj(xx - lean + 0.12, 9.9, z), proj(xx - lean + 0.12, 10.25, z), proj(xx - lean - 0.12, 10.25, z)]
                      for xx in (8.3, 9.6, 10.9)]
        polys = [pole, arm] + insulators
        box = box_of(polys)
        m = mask_box(polys, box)
        amount = haze_amount(z)
        put(c, box, m, SHADOW * (1 - amount) + HAZE * 0.85 * amount)
        put(c, box, rim_of_box(m, -1, -1) * 0.7, RIM * 0.8)
        tops.append((z, lean))
    wires = []
    for (za, la), (zb, lb) in zip(tops, tops[1:]):
        for xoff in (-1.3, 0.0, 1.3):
            pts = [proj(9.6 + xoff - (la + (lb - la) * s), 10.15 - 1.4 * math.sin(math.pi * s), za + (zb - za) * s) for s in np.linspace(0, 1, 28)]
            wires.append(pts)
    full = (0, W, 0, H)
    c.paint(mask_box([], full, lines=wires, width=1.1), SHADOW * 0.55 + HAZE * 0.35, 0.9)
    # a traffic light hung over the street, tipped on its wire
    z = 44.0
    span = [proj(-11, 8.6, z), proj(-0.2, 8.0, z), proj(11.5, 8.6, z)]
    housing = [proj(-0.55, 8.0, z), proj(0.35, 8.1, z), proj(0.55, 6.2, z), proj(-0.35, 6.1, z)]
    box = box_of([housing, span])
    m = mask_box([housing], box, lines=[span], width=1.3)
    put(c, box, m, SHADOW * 0.8 + HAZE * 0.2)
    put(c, box, rim_of_box(m, 0, -1.5) * 0.6, RIM * 0.8)
    return c.image()


# ============================================================================= the road and the near ground

def ground(c, rng):
    y0 = int(VP[1]) + 2
    sx, sy = XX[y0:], YY[y0:]
    z = FOCAL * EYE / (sy - VP[1])
    x = (sx - VP[0]) * z / FOCAL
    u, v = x * 22, z * 22
    far = np.clip(z / 70.0, 0, 1)
    a = sample(TEX, u, v)
    f = sample(TEX_FINE, u * 4, v * 4)
    asphalt = np.array([46, 45, 46], float) * (0.82 + 0.38 * (a - 0.5) + 0.3 * (f - 0.5) * (1 - far))[..., None]
    patch = smooth(0.70, 0.74, sample(TEX_SOFT, u * 0.5 + 300, v * 0.5 + 120))
    asphalt = asphalt * (1 - 0.22 * patch)[..., None]
    crack_n = sample(TEX, u * 0.3 + 50, v * 0.3 + 9)
    gather = smooth(0.48, 0.66, sample(TEX_SOFT, u * 0.4 + 5, v * 0.4 + 5))
    crack = smooth(0.0075, 0.0025, np.abs(crack_n - 0.5)) * gather * (1 - far)
    asphalt = asphalt * (1 - 0.6 * crack)[..., None] + np.array([30, 36, 22]) * (0.6 * crack * (sample(TEX_FINE, u, v) > 0.6))[..., None]
    # markings: dashed centre, solid edges, worn through in places
    worn = smooth(0.35, 0.5, sample(TEX_FINE, u * 1.5 + 9, v * 1.5))
    dash = (np.abs(x) < 0.12) & ((z % 9.0) < 3.0)
    edge = np.abs(np.abs(x) - 7.7) < 0.1
    paint = (dash | edge) * worn * (1 - far * 0.6)
    asphalt = asphalt * (1 - 0.75 * paint)[..., None] + np.array([176, 160, 112]) * (0.75 * paint)[..., None]
    # puddles in the hollows, holding the sky
    pud = smooth(0.70, 0.73, sample(TEX_SOFT, u * 0.55 + 700, v * 0.55 + 40)) * (np.abs(x) < 7.5)
    glow = np.exp(-np.abs(sx - VP[0]) / 420.0)
    sky_refl = HORIZON * 0.55 + RIM * 0.35 * glow[..., None] + (SUN - HORIZON) * (0.3 * np.exp(-np.abs(sx - VP[0] - 30) / 40.0))[..., None]
    wet_rim = smooth(0.66, 0.70, sample(TEX_SOFT, u * 0.55 + 700, v * 0.55 + 40)) * (np.abs(x) < 7.5) - pud
    asphalt = asphalt * (1 - 0.35 * np.clip(wet_rim, 0, 1))[..., None]
    asphalt = asphalt * (1 - pud[..., None] * 0.8) + sky_refl * 0.62 * (pud * 0.8)[..., None]
    # pavements: slabs with dark joints, a lighter kerb, weeds in the cracks
    walk = (np.abs(x) >= 8.0) & (np.abs(x) < 11.6)
    slab = np.array([84, 80, 74], float) * (0.85 + 0.3 * (a - 0.5))[..., None]
    joints = ((z % 1.6) < 0.05) | (((np.abs(x) - 8.0) % 1.8) < 0.05)
    slab = slab * (1 - 0.45 * joints)[..., None]
    kerb = np.abs(np.abs(x) - 8.0) < 0.16
    slab = np.where(kerb[..., None], slab * 1.25, slab)
    weeds = joints & (sample(TEX_FINE, u * 2, v * 2) > 0.62)
    slab = np.where(weeds[..., None], np.array([38, 44, 28], float), slab)
    rgb = np.where(walk[..., None], slab, asphalt)
    # the wet road catches the glow toward the sun
    sheen = np.exp(-np.abs(sx - VP[0]) / (50 + (sy - VP[1]) * 0.9)) * np.exp(-(sy - VP[1]) / 380.0)
    rgb = rgb + RIM * (0.3 * sheen)[..., None]
    rgb = fogged(rgb, z)
    alpha = (np.abs(x) < 11.6).astype(float)
    c.rgb[y0:] = c.rgb[y0:] * (1 - alpha[..., None]) + rgb * alpha[..., None]
    c.a[y0:] = np.maximum(c.a[y0:], alpha)


VEHICLES = {
    "sedan": dict(length=4.7, width=1.85, profile=[(-0.5, 0.32), (-0.5, 0.78), (-0.34, 0.86), (-0.2, 1.36), (0.18, 1.4), (0.31, 0.92), (0.5, 0.82),
                                                    (0.5, 0.32)], glass=[(-0.17, 0.93), (-0.16, 1.32), (0.16, 1.35), (0.28, 0.95)]),
    "van": dict(length=5.2, width=2.0, profile=[(-0.5, 0.35), (-0.5, 1.95), (0.32, 1.98), (0.42, 1.2), (0.5, 1.1), (0.5, 0.35)],
                glass=[(0.3, 1.25), (0.32, 1.85), (0.4, 1.2)]),
    "bus": dict(length=11.5, width=2.55, profile=[(-0.5, 0.4), (-0.5, 3.0), (0.49, 3.0), (0.5, 2.6), (0.5, 0.4)],
                glass=[(-0.46, 1.6), (-0.46, 2.55), (0.47, 2.55), (0.47, 1.6)]),
}


def vehicle(c, rng, kind, x, z, heading, colour, burned=False):
    """An abandoned vehicle: both side profiles projected and joined, glass catching the sky, wheels."""
    spec = VEHICLES[kind]
    length, width = spec["length"], spec["width"]
    ca, sa = math.cos(heading), math.sin(heading)

    def at(t, y, s):
        lx, lz = s * width / 2, t * length
        return proj(x + lx * ca + lz * sa, y, z - lx * sa + lz * ca)

    near_side = 1 if (x * ca) < 0 else -1  # the side facing the street's centre line
    prof = spec["profile"]
    sides = [[at(t, y, s) for t, y in prof] for s in (-1, 1)]
    joins = [[at(t0, y0, -1), at(t1, y1, -1), at(t1, y1, 1), at(t0, y0, 1)] for (t0, y0), (t1, y1) in zip(prof, prof[1:] + prof[:1])]
    wheels = []
    for t in (-0.32, 0.32):
        for s in (-1, 1):
            cx, cy = at(t, 0.36, s)
            r = 0.38 / max(z, 1.0) * FOCAL
            wheels.append([(cx + r * 0.8 * math.cos(a), cy + r * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 18)])
    body = sides + joins
    box = box_of(body + wheels)
    near_wheels = wheels[0::2] if near_side < 0 else wheels[1::2]
    put(c, box, mask_box(near_wheels, box), np.array([8, 8, 9]) * (1 - haze_amount(z)) + HAZE * 0.8 * haze_amount(z))
    m = mask_box(body, box)
    amount = haze_amount(z)
    sx = XX[box[2]:box[3], box[0]:box[1]]
    sy = YY[box[2]:box[3], box[0]:box[1]]
    tex = sample(TEX, sx * 0.9, sy * 0.9)
    rust = smooth(0.58, 0.66, sample(TEX, sx * 0.35 + 90, sy * 0.35)) * (0.9 if burned else 0.5)
    col = colour * (0.75 + 0.4 * (tex - 0.5))[..., None]
    col = col * (1 - rust[..., None]) + np.array([86, 50, 32]) * rust[..., None]
    if burned:
        col = col * 0.45
    col = col * (0.3 + 0.55 * smooth(box[3], box[2], sy))[..., None] * 0.62
    put(c, box, m, col * (1 - amount) + HAZE * 0.85 * amount)
    glass_poly = [at(t, y, near_side) for t, y in spec["glass"]]
    if kind == "bus":
        glass_poly = [at(t, y, near_side) for t, y in spec["glass"]]
    gm = mask_box([glass_poly], box)
    glass = (HAZE * 0.6 + RIM * 0.2) * (0.6 + 0.5 * smooth(box[2], box[3], sy))[..., None] * (0.5 if burned else 1.0)
    put(c, box, gm * 0.9, glass * (1 - amount) + HAZE * amount)
    put(c, box, rim_of_box(m, -1, -1.5) * 0.8, RIM * (0.95 - 0.4 * amount))


def skyline_near(seed=4401):
    rng = np.random.default_rng(seed)
    c = Canvas()
    ground(c, rng)
    # the near buildings: deep shade on the left for the menu, a broken shell on the right
    left = Building(rng, -1, -11.0, 1.6, 24.5, 22.0, shade=0.42)
    left.damage = 0.35
    left.ivy = 0.6
    right = Building(rng, 1, 11.5, 1.6, 12.5, 11.0, shade=0.55)
    right.damage = 1.0
    right.top_y = np.minimum(right.top_y, np.linspace(5.0, 11.0, len(right.top_y)) + rng.uniform(-1, 1, len(right.top_y)))
    right.top_y = np.maximum(right.top_y, 3.5)
    paint_building(c, right)
    paint_building(c, left)
    # abandoned vehicles, far to near
    vehicle(c, rng, "sedan", -6.2, 78.0, 0.15, np.array([72, 76, 78]), burned=True)
    vehicle(c, rng, "van", 4.8, 58.0, -0.12, np.array([92, 88, 80]))
    vehicle(c, rng, "sedan", -5.6, 34.0, -0.28, np.array([96, 56, 46]), burned=True)
    vehicle(c, rng, "bus", -9.6, 46.0, 0.06, np.array([120, 104, 64]), burned=True)
    # rubble along the kerbs
    rubble = []
    for _ in range(140):
        z = rng.uniform(3, 50)
        xside = rng.choice([-1, 1])
        x = xside * rng.uniform(6.8, 10.5)
        r = rng.uniform(0.04, 0.22)
        cx, cy = proj(x, 0, z)
        rr = r / z * FOCAL
        rubble.append([(cx + rr * math.cos(a) * rng.uniform(0.6, 1.2), cy - abs(rr * math.sin(a)) * rng.uniform(0.4, 1.0)) for a in np.linspace(0, math.pi, 7)])
    box = box_of(rubble)
    m = mask_box(rubble, box)
    put(c, box, m, np.array([40, 39, 37]))
    put(c, box, rim_of_box(m, 0, -1) * 0.35, RIM * 0.55)
    # a leaning pole in the foreground, its line down across the street, and a dead tree at the right edge
    pole = [(1500, 1080), (1524, 1080), (1430, 110), (1414, 112)]
    cross = [(1380, 170), (1470, 152), (1471, 160), (1381, 178)]
    lines = [[(1420, 165), (1650, 330), (1920, 360)], [(1420, 165), (1280, 250), (1150, 320), (1020, 560)]]
    box = (0, W, 0, H)
    m = mask_box([pole, cross], box, lines=lines, width=1.8)
    c.paint(m, SHADOW)
    c.paint(rim_of(m, -2, -1, 0.6), RIM * 0.6)
    branches = []

    def grow(x, y, ang, length, depth_):
        if depth_ == 0 or length < 5:
            return
        x2, y2 = x + math.cos(ang) * length, y + math.sin(ang) * length
        branches.append(((x, y), (x2, y2), max(1.0, depth_ * 1.7)))
        for _ in range(2 if depth_ > 2 else 3):
            grow(x2, y2, ang + rng.uniform(-0.65, 0.55), length * rng.uniform(0.6, 0.78), depth_ - 1)

    grow(1845, 1080, -math.pi / 2 - 0.1, 240, 8)
    img = Image.new("L", (W * SS, H * SS), 0)
    d = ImageDraw.Draw(img)
    for (xa, ya), (xb, yb), width in branches:
        d.line([(xa * SS, ya * SS), (xb * SS, yb * SS)], fill=255, width=int(width * SS))
    tm = np.asarray(img.resize((W, H), Image.LANCZOS), float) / 255.0
    c.paint(tm, SHADOW)
    c.paint(rim_of(tm, -1.5, -1, 0.5), RIM * 0.5)
    return c.image()


def plants(seed=4501):
    """Foreground grass and weeds, backlit (their edges glow), kept low under the menu."""
    rng = np.random.default_rng(seed)
    blades = Image.new("L", (W * SS, H * SS), 0)
    glow = Image.new("L", (W * SS, H * SS), 0)
    db, dg = ImageDraw.Draw(blades), ImageDraw.Draw(glow)
    clumps = []
    for _ in range(90):
        x = rng.uniform(-40, W + 40)
        if 560 < x < 1480 and rng.random() < 0.8:
            continue
        clumps.append(x)
    for cx in clumps:
        tall = 1.0 if cx > 1450 else 0.55 if cx < 520 else 0.8
        for _ in range(int(rng.integers(10, 26))):
            x = cx + rng.normal(0, 18)
            base = H + rng.uniform(5, 40)
            height = rng.uniform(50, 200) * tall
            lean = rng.normal(0.18, 0.3)
            width = rng.uniform(2.5, 6.0)
            left, right = [], []
            for s in np.linspace(0, 1, 9):
                px = x + lean * height * s ** 1.6
                py = base - height * s
                hw = width * (1 - s) ** 0.8 / 2
                left.append(((px - hw) * SS, py * SS))
                right.append(((px + hw) * SS, py * SS))
            poly = left + right[::-1]
            db.polygon(poly, fill=255)
            dg.line(right, fill=255, width=SS)
    for _ in range(12):  # dead stalks with seed heads on the right
        x = rng.uniform(1520, 1900)
        base, height = H + 5, rng.uniform(170, 300)
        top = (x + rng.normal(20, 15), base - height)
        db.line([(x * SS, base * SS), (top[0] * SS, top[1] * SS)], fill=255, width=int(2 * SS))
        db.ellipse([(top[0] - 4) * SS, (top[1] - 10) * SS, (top[0] + 4) * SS, (top[1] + 6) * SS], fill=255)
        dg.line([(x * SS + 2, base * SS), (top[0] * SS + 2, top[1] * SS)], fill=255, width=SS)
    m = np.asarray(blades.resize((W, H), Image.LANCZOS), float) / 255.0
    g = np.asarray(glow.resize((W, H), Image.LANCZOS), float) / 255.0 * m
    tex = noise(seed, 1.2)
    rgb = np.array([16, 19, 14])[None, None, :] + (tex[..., None] - 0.5) * 10 + RIM[None, None, :] * (0.28 * g)[..., None]
    return np.dstack([np.clip(rgb, 0, 255), np.clip(m * 255, 0, 255)]).astype(np.uint8)


def rays(seed=4601):
    """Soft, dusty light shafts fanning from the sun (drawn faint, breathing slowly)."""
    sx, sy = VP[0] + 30, VP[1] - 60
    ang = np.arctan2(YY - sy, XX - sx)
    dist = np.hypot(XX - sx, YY - sy)
    rng = np.random.default_rng(seed)
    beams = np.zeros_like(ang)
    for _ in range(11):
        a = rng.uniform(-math.pi, math.pi)
        width = rng.uniform(0.04, 0.12)
        beams += np.exp(-(np.angle(np.exp(1j * (ang - a))) / width) ** 2) * rng.uniform(0.4, 1.0)
    dust = 0.6 + 0.4 * noise(seed, 1.6)
    fall = np.exp(-dist / 750.0) * np.clip(dist / 80.0, 0, 1)
    alpha = np.clip(beams * fall * dust * 0.75, 0, 1)
    rgb = np.ones((H, W, 3)) * SUN
    return np.dstack([rgb, alpha * 255]).astype(np.uint8)


def fog(seed=4701, w=1024, h=512):
    """A horizontally tileable fog band, warm-grey, thicker in the middle."""
    n = N.spectral(h, w, beta=2.4, seed=seed)
    yy = np.linspace(0, 1, h)[:, None]
    band = np.exp(-((yy - 0.55) / 0.28) ** 2)
    alpha = np.clip((n * 1.2 - 0.25), 0, 1) * band
    rgb = np.ones((h, w, 3)) * (HAZE * 0.75 + np.array([20, 20, 22]))
    return np.dstack([rgb, alpha * 255]).astype(np.uint8)


def crow_frames(seed=4801, size=32, frames=4):
    """A crow in flight, four wingbeat frames side by side (drawn small and dark across the sky)."""
    sheet = Image.new("L", (size * frames * SS, size * SS), 0)
    d = ImageDraw.Draw(sheet)
    for f in range(frames):
        ox = f * size * SS
        cx, cy = ox + size * SS / 2, size * SS / 2
        lift = [-0.55, -0.15, 0.25, -0.1][f] * size * SS * 0.5
        s = SS
        body = [(cx - 7 * s, cy), (cx + 6 * s, cy - 1.5 * s), (cx + 9 * s, cy - 0.5 * s), (cx + 6 * s, cy + 1.5 * s), (cx - 7 * s, cy + 1.2 * s)]
        tail = [(cx - 7 * s, cy - 0.5 * s), (cx - 11 * s, cy - 2.5 * s), (cx - 11 * s, cy + 2.5 * s), (cx - 7 * s, cy + 1.2 * s)]
        wing_l = [(cx - 2 * s, cy), (cx - 6 * s, cy + lift * 0.6 - 2 * s), (cx - 13 * s, cy + lift), (cx + 2 * s, cy - 0.5 * s)]
        wing_r = [(cx, cy), (cx + 3 * s, cy + lift * 0.5 - 3 * s), (cx - 1 * s, cy + lift * 1.1 - 4 * s), (cx + 3 * s, cy)]
        for poly in (body, tail, wing_l, wing_r):
            d.polygon(poly, fill=255)
    a = np.asarray(sheet.resize((size * frames, size), Image.LANCZOS), float)
    rgb = np.ones((size, size * frames, 3)) * np.array([16, 15, 17])
    return np.dstack([rgb, a]).astype(np.uint8)


def preview(layers: dict[str, np.ndarray]) -> np.ndarray:
    """The layers as the menu draws them (no parallax), for review."""
    out = layers["sky"][..., :3].astype(float)
    order = ["skyline_far", "fog", "skyline_mid", "rays", "skyline_near", "plants"]
    for name in order:
        lay = layers[name].astype(float)
        if name == "fog":
            tile = np.tile(lay, (1, 2, 1))[:, :W]
            img = np.asarray(Image.fromarray(tile.astype(np.uint8)).resize((W, int(H * 0.6))), float)
            canvas = np.zeros((H, W, 4))
            canvas[int(H * 0.38):int(H * 0.38) + img.shape[0]] = img[: H - int(H * 0.38)]
            lay = canvas
            lay[..., 3] *= 0.75
        if name == "rays":
            lay = lay.copy()
            lay[..., 3] *= 0.35
        a = lay[..., 3:4] / 255.0
        out = out * (1 - a) + lay[..., :3] * a
    return np.clip(out, 0, 255).astype(np.uint8)


def build() -> list:
    layers = {"sky": sky(), "skyline_far": skyline_far(), "skyline_mid": skyline_mid(), "skyline_near": skyline_near(), "plants": plants(),
              "rays": rays(), "fog": fog()}
    crows = N.ASSETS / (OUT + "crow.png")
    Image.fromarray(crow_frames()).save(crows, optimize=True)
    written = [crows]
    for name, img in layers.items():
        path = N.ASSETS / (OUT + name + ".png")
        Image.fromarray(img).save(path, optimize=True)
        written.append(path)
    N.ROOT.joinpath("tools", "assetgen", "_cache").mkdir(parents=True, exist_ok=True)
    Image.fromarray(preview(layers)).save(N.ROOT / "tools" / "assetgen" / "_cache" / "menu_preview.png")
    import dl_data as D
    D.merge_lang({"menu.deadlands.dev_build": "BETA · DEVELOPMENT BUILD", "menu.deadlands.tagline": "The outbreak began ninety days ago."})
    return written


if __name__ == "__main__":
    for p in build():
        print(p)
