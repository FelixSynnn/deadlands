"""Title screen art for DEADLANDS.

Outputs (assets/deadlands/textures/gui/title/):
  sky.png           opaque overcast sky, dim sun, smoke columns          1920x1080
  skyline_far.png   hazy ruined city silhouette (alpha)                   1920x1080
  skyline_near.png  near silhouettes: gutted building, poles, tree, car   1920x1080
  fog.png           horizontally tileable fog band (alpha)                1024x512
  vignette.png      radial darkening overlay (alpha)                      512x288
  grain.png         tileable film grain                                   256x256
  logo.png          spray-painted stencil DEADLANDS logo (alpha)          ~1123x250
"""
from __future__ import annotations

import math

import numpy as np
from scipy import ndimage

import dl_glyphs as G
import dl_noise as N

W, H = 1920, 1080
OUT = "textures/gui/title/"


def _grid(h: int = H, w: int = W):
    yy = np.linspace(0.0, 1.0, h)[:, None] * np.ones((1, w))
    xx = np.ones((h, 1)) * np.linspace(0.0, 1.0, w)[None, :]
    return yy, xx


# ----------------------------------------------------------------------------- sky

def sky(seed: int = 1101) -> np.ndarray:
    yy, xx = _grid()
    col = N.ramp(yy, [(0.00, "#0f1518"), (0.35, "#222a2d"), (0.58, "#47473f"),
                      (0.70, "#6c5d49"), (0.80, "#574c3e"), (1.00, "#2c2823")])
    # A dim sun smothered by smoke, low over the city.
    sx, sy = 0.30, 0.60
    d = np.sqrt(((xx - sx) * W / H) ** 2 + (yy - sy) ** 2)
    glow = np.exp(-(d / 0.24) ** 2) * 0.50 + np.exp(-(d / 0.045) ** 2) * 0.30
    col = N.lerp(col, N.solid(H, W, "#c4a06a"), np.clip(glow, 0.0, 1.0))
    # Overcast: wide, domain-warped cloud formations, heavier toward the top of the frame.
    c1 = N.spectral(H, W, beta=2.6, seed=seed, aniso=(1.0, 2.4))
    wx = (N.spectral(H, W, beta=3.0, seed=seed + 1) - 0.5) * 140.0
    wy = (N.spectral(H, W, beta=3.0, seed=seed + 2) - 0.5) * 60.0
    clouds = N.smoothstep(0.35, 0.85, N.warp(c1, wx, wy))
    weight = np.clip(1.15 - yy * 1.25, 0.0, 1.0)
    dark = N.ramp(yy, [(0.0, "#0b1012"), (0.5, "#1d2225"), (0.75, "#3b352d")])
    col = N.lerp(col, dark, clouds * weight * 0.85)
    # Cloud undersides catching the sun.
    rim = N.smoothstep(0.55, 0.90, c1) * np.exp(-(d / 0.35) ** 2) * 0.22
    col = N.lerp(col, N.solid(H, W, "#b08d63"), rim)
    # Lighter billows give the overcast some structure instead of a flat dark lid.
    billow = N.warp(N.spectral(H, W, beta=2.2, seed=seed + 3, aniso=(1.0, 1.8)), wx * 0.6, wy * 0.6)
    edge = N.smoothstep(0.50, 0.66, billow) * (1.0 - N.smoothstep(0.70, 0.92, billow))
    col = N.lerp(col, N.solid(H, W, "#434a4c"), edge * weight * 0.45)
    # Smoke columns rising out of the city: somewhere, something is still burning.
    horizon = 0.72
    t = np.clip((horizon - yy) / horizon, 0.0, 1.0)
    for i, (x0, width0, drift) in enumerate([(0.66, 0.016, 0.11), (0.81, 0.011, 0.06), (0.47, 0.007, 0.035)]):
        wobble = (N.spectral(H, W, beta=3.2, seed=seed + 10 + i) - 0.5) * 0.05 * t
        cx = x0 + drift * t ** 1.5 + wobble
        width = width0 + 0.10 * t ** 1.3
        column = np.exp(-(((xx - cx) * W / H) / width) ** 2)
        turb = N.spectral(H, W, beta=2.2, seed=seed + 20 + i)
        smoke = column * N.smoothstep(0.25, 0.75, turb) * (yy < horizon) * np.clip(1.25 - t, 0.0, 1.0)
        col = N.lerp(col, N.solid(H, W, "#141716"), np.clip(smoke * 0.78, 0.0, 1.0))
    return col


# ----------------------------------------------------------------------------- far skyline

def _windows(rects: list, h: int, w: int, seed: int, bottom: float) -> np.ndarray:
    """Per-building window grids: 1 where a (dark, empty) window is."""
    from PIL import Image, ImageDraw
    r = N.rng(seed)
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    for x0, x1, top in rects:
        sx, sy = r.integers(5, 9), r.integers(7, 11)
        ww, wh = r.integers(2, 4), r.integers(3, 5)
        ox = x0 + r.integers(2, 5)
        y = top + r.integers(5, 10)
        while y < bottom - 4:
            x = ox
            while x + ww < x1 - 2:
                if r.random() > 0.18:
                    d.rectangle([x, y, x + ww - 1, y + wh - 1], fill=int(r.integers(150, 256)))
                x += sx
            y += sy
    return np.asarray(img, dtype=float) / 255.0


def skyline_far(seed: int = 1201) -> tuple[np.ndarray, np.ndarray]:
    r = N.rng(seed)
    horizon = H * 0.72
    rects: list = []
    mid_rects: list = []

    def draw_mid(d, ss):
        # A closer, darker row of low buildings in front of downtown: gives the city depth.
        x = -30.0
        base = horizon + 22
        while x < W + 30:
            bw = r.uniform(40, 120)
            bh = r.uniform(30, 120) * (1.0 if r.random() > 0.25 else 0.4)
            top = base - bh
            if r.random() < 0.35:
                pts = [(x, base + 40), (x, top + r.uniform(0, 18)), (x + bw * r.uniform(0.3, 0.7), top + r.uniform(-6, 14)),
                       (x + bw, top + r.uniform(8, 40)), (x + bw, base + 40)]
            else:
                pts = [(x, base + 40), (x, top), (x + bw, top), (x + bw, base + 40)]
            d.polygon([(px * ss, py * ss) for px, py in pts], fill=255)
            mid_rects.append((x, x + bw, top))
            x += bw + r.uniform(4, 30)

    def draw(d, ss):
        def S(pts):
            return [(x * ss, y * ss) for x, y in pts]

        x = -40.0
        while x < W + 40:
            bw = r.uniform(22, 95)
            env = (70 + 300 * math.exp(-((x - W * 0.64) / (W * 0.17)) ** 2)
                   + 120 * math.exp(-((x - W * 0.22) / (W * 0.10)) ** 2))
            bh = env * r.uniform(0.35, 1.0) + r.uniform(8, 30)
            x0, x1, top, foot = x, x + bw, horizon - bh, horizon + 60
            kind = r.random()
            if kind < 0.30:  # jagged, shelled-out top
                n = int(r.integers(3, 7))
                pts = [(x0, foot), (x0, top + r.uniform(0, 25))]
                for xi in np.linspace(x0, x1, n + 1)[1:-1]:
                    pts.append((xi, top + r.uniform(-8, 40)))
                pts += [(x1, top + r.uniform(0, 30)), (x1, foot)]
            elif kind < 0.55:  # stepped setbacks
                st = bw * 0.18
                pts = [(x0, foot), (x0, top + bh * 0.25), (x0 + st, top + bh * 0.25), (x0 + st, top),
                       (x1 - st, top), (x1 - st, top + bh * 0.18), (x1, top + bh * 0.18), (x1, foot)]
            elif kind < 0.70:  # half collapsed
                pts = [(x0, foot), (x0, top), (x0 + bw * 0.4, top + r.uniform(5, 20)),
                       (x1, top + bh * r.uniform(0.4, 0.7)), (x1, foot)]
            else:
                pts = [(x0, foot), (x0, top), (x1, top), (x1, foot)]
            d.polygon(S(pts), fill=255)
            rects.append((x0, x1, top + 12))
            if r.random() < 0.18 and bh > 120:
                ax = r.uniform(x0 + 4, x1 - 4)
                d.line(S([(ax, top), (ax, top - r.uniform(25, 70))]), fill=255, width=max(1, int(2 * ss)))
            x = x1 + r.uniform(-6, 10)

        # Construction crane over downtown, its jib snapped and hanging.
        cx, base_y, mast_top = W * 0.71, horizon - 40, horizon - 430
        d.rectangle(S([(cx - 5, mast_top), (cx + 5, base_y)]), fill=255)
        d.polygon(S([(cx - 60, mast_top + 6), (cx + 230, mast_top + 2), (cx + 230, mast_top + 12), (cx - 60, mast_top + 14)]), fill=255)
        d.line(S([(cx + 230, mast_top + 8), (cx + 262, mast_top + 72)]), fill=255, width=int(7 * ss))
        d.line(S([(cx + 150, mast_top + 12), (cx + 150, mast_top + 165)]), fill=255, width=max(1, int(1.5 * ss)))
        d.rectangle(S([(cx + 144, mast_top + 165), (cx + 156, mast_top + 178)]), fill=255)
        # Water tower on the outskirts.
        tx, ty = W * 0.14, horizon - 150
        d.ellipse(S([(tx - 40, ty - 30), (tx + 40, ty + 10)]), fill=255)
        d.rectangle(S([(tx - 40, ty - 10), (tx + 40, ty + 20)]), fill=255)
        for lx in (-32, -12, 12, 32):
            d.line(S([(tx + lx, ty + 20), (tx + lx * 1.4, horizon + 10)]), fill=255, width=int(3 * ss))
        # Lattice radio tower: the kind the player will one day climb to repair.
        rx, rtop = W * 0.90, horizon - 380
        for side in (-1, 1):
            d.line(S([(rx, rtop), (rx + side * 28, horizon)]), fill=255, width=int(2 * ss))
        yv = rtop + 20
        flip = 1
        while yv < horizon:
            half = 28 * (yv - rtop) / (horizon - rtop)
            nxt = min(horizon, yv + 22)
            half2 = 28 * (nxt - rtop) / (horizon - rtop)
            d.line(S([(rx - half, yv), (rx + half, yv)]), fill=255, width=max(1, int(ss)))
            d.line(S([(rx - flip * half, yv), (rx + flip * half2, nxt)]), fill=255, width=max(1, int(ss)))
            flip = -flip
            yv = nxt
        d.line(S([(rx, rtop), (rx, rtop - 40)]), fill=255, width=max(1, int(ss)))

    mask = N.supersample_mask(draw, H, W, ss=2)
    mid = N.supersample_mask(draw_mid, H, W, ss=2)
    yy, _ = _grid()
    col = N.ramp(yy, [(0.0, "#2b2c2a"), (0.55, "#32312d"), (0.72, "#4b463c"), (1.0, "#4b463c")])
    facade = N.spectral(H, W, beta=1.6, seed=seed + 5)
    col *= (0.92 + 0.10 * facade)[..., None]
    # Empty windows: darker than the facade, a few catching the haze.
    win = _windows(rects, H, W, seed + 7, horizon + 10)
    col = N.lerp(col, N.solid(H, W, "#1f201f"), win * 0.55)
    # Haze eats the base of the skyline.
    haze = N.smoothstep(0.62, 0.74, yy)
    col = N.lerp(col, N.solid(H, W, "#5d5547"), haze * 0.55)

    # Mid-distance row: darker, less hazy, with its own windows.
    mid_col = N.ramp(yy, [(0.0, "#1d1e1d"), (0.70, "#232320"), (0.80, "#3a362f"), (1.0, "#3a362f")])
    mid_col *= (0.90 + 0.12 * N.spectral(H, W, beta=1.4, seed=seed + 9))[..., None]
    mid_win = _windows(mid_rects, H, W, seed + 11, horizon + 22)
    mid_col = N.lerp(mid_col, N.solid(H, W, "#121312"), mid_win * 0.6)
    mid_col = N.lerp(mid_col, N.solid(H, W, "#4f483d"), N.smoothstep(0.70, 0.80, yy) * 0.6)
    col = N.lerp(col, mid_col, mid)
    return col, np.maximum(mask, mid)


# ----------------------------------------------------------------------------- near layer

def _xf(pts, ox, oy, scale=1.0, ang=0.0):
    ca, sa = math.cos(ang), math.sin(ang)
    return [(ox + (x * ca - y * sa) * scale, oy + (x * sa + y * ca) * scale) for x, y in pts]


def skyline_near(seed: int = 1301) -> tuple[np.ndarray, np.ndarray]:
    r = N.rng(seed)
    ground = H * 0.86

    def draw(d, ss):
        def S(pts):
            return [(x * ss, y * ss) for x, y in pts]

        def line(pts, width):
            d.line(S(pts), fill=255, width=max(1, int(round(width * ss))), joint="curve")

        # Uneven ground, rubble heaps.
        pts = [(0, H + 5)]
        x = 0.0
        while x <= W + 40:
            y = ground + 16 * math.sin(x / 260.0) + r.uniform(-5, 5)
            if r.random() < 0.09:
                y -= r.uniform(10, 42)
            pts.append((x, y))
            x += r.uniform(10, 38)
        pts.append((W + 40, H + 5))
        d.polygon(S(pts), fill=255)

        # Gutted building on the left.
        btop = H * 0.30
        top_pts = [(-20, btop + 40), (60, btop), (130, btop + 30), (175, btop - 10), (230, btop + 60),
                   (290, btop + 45), (330, btop + 140), (360, btop + 170)]
        d.polygon(S([(-20, H)] + top_pts + [(360, H)]), fill=255)
        txs = np.array([p[0] for p in top_pts])
        tys = np.array([p[1] for p in top_pts])
        for _ in range(16):  # rebar bristling from the broken edge
            rx = r.uniform(30, 345)
            ry = float(np.interp(rx, txs, tys)) + r.uniform(2, 10)
            ang = r.uniform(-2.5, -0.65)
            ln = r.uniform(16, 58)
            line([(rx, ry), (rx + math.cos(ang) * ln, ry + math.sin(ang) * ln)], 2.0)
        # Blown-out windows: the sky shows through.
        for wx in np.arange(18, 330, 47):
            for wy in np.arange(btop + 70, ground - 50, 60):
                if float(np.interp(wx + 11, txs, tys)) + 24 > wy:
                    continue
                roll = r.random()
                if roll < 0.12:
                    continue  # boarded / intact: stays solid
                jit = lambda: r.uniform(-3.5, 3.5)  # noqa: E731
                win = [(wx + jit(), wy + jit()), (wx + 24 + jit(), wy + jit()),
                       (wx + 24 + jit(), wy + 32 + jit()), (wx + jit(), wy + 32 + jit())]
                d.polygon(S(win), fill=0)
                if roll < 0.35:  # a plank nailed across what is left
                    d.line(S([(wx - 3, wy + 10 + jit()), (wx + 27, wy + 18 + jit())]), fill=255, width=max(1, int(4 * ss)))

        # Leaning utility poles with sagging lines.
        def pole(px, height, lean):
            top = (px + math.sin(lean) * height, ground + 6 - math.cos(lean) * height)
            line([(px, ground + 8), top], 11)
            arm = 70
            ca, sa = math.cos(lean), math.sin(lean)
            a0 = (top[0] - ca * arm, top[1] + 18 - sa * arm)
            a1 = (top[0] + ca * arm, top[1] + 18 + sa * arm)
            line([a0, a1], 7)
            ends = []
            for k in (-0.85, -0.3, 0.3, 0.85):
                ix = top[0] + ca * arm * k
                iy = top[1] + 18 + sa * arm * k
                d.rectangle(S([(ix - 3, iy - 10), (ix + 3, iy)]), fill=255)
                ends.append((ix, iy - 9))
            return ends

        p1 = pole(1490, 470, 0.07)
        p2 = pole(1905, 420, -0.03)

        def wire(a, b, sag):
            pts = []
            for t in np.linspace(0.0, 1.0, 48):
                pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t + 4 * sag * t * (1 - t)))
            line(pts, 2.0)

        for i in range(4):
            wire(p1[i], p2[i], 26 + 4 * i)
            wire((-80, H * 0.42 + 14 * i), p1[i], 70 + 10 * i)
        # One line has snapped and hangs to the ground.
        hang = [(p1[0][0], p1[0][1])]
        for t in np.linspace(0, 1, 30):
            hang.append((p1[0][0] - 40 * t - 30 * t * t, p1[0][1] + (ground - p1[0][1] - 6) * t))
        line(hang, 2.0)

        # Dead tree.
        def branch(x0, y0, ang, length, thick, depth):
            x1 = x0 + math.cos(ang) * length
            y1 = y0 + math.sin(ang) * length
            line([(x0, y0), (x1, y1)], thick)
            if depth == 0 or length < 7:
                return
            n = 2 if r.random() < 0.7 else 3
            for i in range(n):
                na = ang + r.uniform(-0.55, 0.55) + (i - (n - 1) / 2) * 0.42
                branch(x1, y1, na, length * r.uniform(0.62, 0.80), max(1.2, thick * 0.62), depth - 1)

        branch(1185, ground + 4, -math.pi / 2 + 0.06, 115, 17, 7)
        branch(1185, ground - 60, -math.pi / 2 - 0.9, 60, 7, 4)

        # Abandoned sedan: front wheel gone, nose in the dirt, windows smashed.
        body = [(0, -12), (4, -24), (30, -28), (48, -44), (92, -46), (112, -30), (140, -27), (146, -14),
                (144, -6), (0, -6)]
        car = _xf(body, 735, ground + 8, 2.2, -0.05)
        d.polygon(S(car), fill=255)
        for wx_, wy_ in ((118, -6),):
            cx_, cy_ = _xf([(wx_, wy_)], 735, ground + 8, 2.2, -0.05)[0]
            d.ellipse(S([(cx_ - 24, cy_ - 24), (cx_ + 24, cy_ + 24)]), fill=255)
        for win in ([(52, -40), (70, -42), (69, -30), (46, -30)], [(74, -42), (90, -41), (103, -30), (74, -30)]):
            jag = [(x + r.uniform(-1.5, 1.5), y + r.uniform(-1.5, 1.5)) for x, y in win]
            d.polygon(S(_xf(jag, 735, ground + 8, 2.2, -0.05)), fill=0)

        # Bent road sign.
        line([(1012, ground + 6), (1006, ground - 70), (1030, ground - 150)], 5)
        sign = _xf([(-34, -22), (34, -22), (34, 22), (-34, 22)], 1032, ground - 172, 1.0, 0.21)
        d.polygon(S(sign), fill=255)
        holes = _xf([(-6, -4), (2, -6), (4, 3), (-4, 5)], 1040, ground - 168, 1.0, 0.21)
        d.polygon(S(holes), fill=0)  # bullet-punched hole

    mask = N.supersample_mask(draw, H, W, ss=2)
    yy, _ = _grid()
    col = N.ramp(yy, [(0.0, "#131617"), (1.0, "#090b0c")])
    # Faint warm rim where silhouettes meet the sky glow.
    above = np.roll(mask, 1, axis=0)
    rim = N.blur(np.clip(mask * (1.0 - above), 0, 1), 0.8, wrap=False)
    col = N.lerp(col, N.solid(H, W, "#4a3f31"), np.clip(rim * 1.4, 0, 0.7))
    return col, mask


# ----------------------------------------------------------------------------- overlays

def fog(seed: int = 1401) -> tuple[np.ndarray, np.ndarray]:
    h, w = 512, 1024
    n1 = N.spectral(h, w, beta=2.4, seed=seed, aniso=(1.0, 3.0))
    n2 = N.spectral(h, w, beta=1.8, seed=seed + 1, aniso=(1.0, 2.0))
    dens = N.smoothstep(0.30, 0.80, 0.7 * n1 + 0.3 * n2)
    yy = np.linspace(0.0, 1.0, h)[:, None] * np.ones((1, w))
    profile = N.smoothstep(0.0, 0.6, yy)
    alpha = dens * profile * 0.78
    col = N.solid(h, w, "#8a867b") * (0.88 + 0.12 * n2[..., None])
    return col, alpha


def vignette() -> tuple[np.ndarray, np.ndarray]:
    h, w = 288, 512
    yy, xx = _grid(h, w)
    rr = np.sqrt(((xx - 0.5) / 0.5) ** 2 + ((yy - 0.5) / 0.5) ** 2) / math.sqrt(2)
    alpha = N.smoothstep(0.35, 1.0, rr) ** 1.25 * 0.88
    return N.solid(h, w, "#000000"), alpha


def grain(seed: int = 1601) -> tuple[np.ndarray, np.ndarray]:
    h = w = 256
    g = N.rng(seed).normal(0.5, 0.18, (h, w))
    g = np.clip(g, 0.0, 1.0)
    rgb = np.repeat((g * 255.0)[..., None], 3, axis=2)
    return rgb, np.ones((h, w))


# ----------------------------------------------------------------------------- logo

def logo(seed: int = 1501) -> tuple[np.ndarray, np.ndarray]:
    text = "DEADLANDS"
    unit = 1.30
    pad_x, pad_top, pad_bottom = 40, 30, 95
    w = int(math.ceil(G.text_width(text) * unit + pad_x * 2))
    h = int(100 * unit + pad_top + pad_bottom)
    r = N.rng(seed)

    letters = N.supersample_mask(lambda d, s: G.draw_text(d, text, pad_x * s, pad_top * s, unit * s), h, w, 4)
    solid_m = letters > 0.5
    dist = ndimage.distance_transform_edt(solid_m)
    chips = N.spectral(h, w, beta=1.4, seed=seed)
    keep = N.smoothstep(0.30, 0.42, chips * 0.55 + np.clip(dist / 7.0, 0, 1) * 0.65)
    flakes = N.smoothstep(0.16, 0.22, N.spectral(h, w, beta=1.0, seed=seed + 1))
    paint = letters * keep * (0.30 + 0.70 * flakes)

    # Overspray halo with spatter dots.
    spatter = (r.random((h, w)) > 0.55).astype(float)
    spray = N.blur(letters, 2.6, wrap=False) * (1.0 - letters) * (0.25 + 0.45 * spatter) * 0.55

    # Paint drips from the underside of strokes.
    edge_y, edge_x = np.nonzero(solid_m & ~np.roll(solid_m, -1, axis=0))
    drips = np.zeros((h, w))
    if len(edge_x):
        def draw_drips(d, s):
            for idx in r.choice(len(edge_x), size=26, replace=False):
                x0, y0 = float(edge_x[idx]), float(edge_y[idx])
                length = float(np.clip(r.gamma(2.0, 13.0), 6, pad_bottom - 18))
                width = r.uniform(1.6, 3.4)
                d.line([(x0 * s, y0 * s), (x0 * s, (y0 + length) * s)], fill=255, width=max(1, int(width * s)))
                rad = width * 0.95
                d.ellipse([(x0 - rad) * s, (y0 + length - rad) * s, (x0 + rad) * s, (y0 + length + rad * 1.3) * s], fill=255)
        drips = N.supersample_mask(draw_drips, h, w, 4) * 0.92

    alpha_paint = np.clip(np.maximum(paint, drips * (1.0 - letters)) + spray * (1.0 - paint), 0.0, 1.0)

    dirt = N.spectral(h, w, beta=2.0, seed=seed + 3)
    streak = N.spectral(h, w, beta=2.0, seed=seed + 4, aniso=(5.0, 1.0))
    col = N.solid(h, w, "#d9cfb4") * (0.80 + 0.22 * dirt[..., None]) * (0.88 + 0.12 * streak[..., None])
    col = N.lerp(col, N.solid(h, w, "#8a5a3a"), N.smoothstep(0.62, 0.92, dirt) * 0.38)
    col = N.lerp(col, N.solid(h, w, "#a99c80"), np.clip(drips * (1.0 - letters), 0, 1) * 0.6)

    # Soft drop shadow so the logo reads over bright fog.
    shadow = np.roll(np.roll(N.blur(alpha_paint, 5.0, wrap=False), 5, axis=0), 3, axis=1) * 0.70
    a_out = alpha_paint + shadow * (1.0 - alpha_paint)
    rgb = (col * alpha_paint[..., None]) / np.maximum(a_out, 1e-6)[..., None]
    return rgb, a_out


def build() -> list:
    out = []
    out.append(N.save_png(N.asset(OUT + "sky.png"), sky()))
    out.append(N.save_png(N.asset(OUT + "skyline_far.png"), *skyline_far()))
    out.append(N.save_png(N.asset(OUT + "skyline_near.png"), *skyline_near()))
    out.append(N.save_png(N.asset(OUT + "fog.png"), *fog()))
    out.append(N.save_png(N.asset(OUT + "vignette.png"), *vignette()))
    out.append(N.save_png(N.asset(OUT + "grain.png"), *grain()))
    out.append(N.save_png(N.asset(OUT + "logo.png"), *logo()))
    return out


if __name__ == "__main__":
    for p in build():
        print("wrote", p.relative_to(N.ROOT))
