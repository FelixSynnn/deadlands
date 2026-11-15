"""Cuboid 3D model toolkit (Phase 3): boxes, per-face UV unwrapping and material painting.

A model is a list of `Box`es in Minecraft model units (16 = one block). Each emitted face gets its
own region of the model's texture at `PPU` texels per unit (so every face keeps the same texel
size), padded against mipmap bleeding, and is painted by its box's material with model-space
coordinates, so features such as grip rings, slide serrations or a blade's bevel line up across
faces. Minecraft 1.21.11 accepts any element rotation angle (and x/y/z Euler rotations), which the
helpers here use for bends, tapers and round bars.

Face UV orientation follows vanilla's default UVs (FaceBakery), so a painted texture lands the way
the per-face pixel-to-model mapping below says it does.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np

import dl_noise as N

PPU = 8   # texels per model unit
PAD = 2   # texels of padding around each face region
DIRS = ("north", "south", "east", "west", "up", "down")


@dataclass
class Box:
    frm: tuple
    to: tuple
    mat: str
    rot: dict | None = None
    skip: tuple = ()
    tags: dict = field(default_factory=dict)


def box(x0, y0, z0, x1, y1, z1, mat, rot=None, skip=(), **tags) -> Box:
    return Box((float(x0), float(y0), float(z0)), (float(x1), float(y1), float(z1)), mat, rot, tuple(skip), tags)


def zrot(ox, oy, angle, oz=8.0) -> dict:
    return {"origin": [ox, oy, oz], "axis": "z", "angle": angle}


def bar(x0, x1, cy, cz, sy, sz, mat, rot=None, skip=(), **tags) -> Box:
    """A straight bar along X with its centre line at (cy, cz)."""
    return box(x0, cy - sy / 2, cz - sz / 2, x1, cy + sy / 2, cz + sz / 2, mat, rot, skip, **tags)


def _plates(sides: int) -> list[tuple[float, float]]:
    """(angle, plate thickness factor) for a regular polygon prism of 4 * sides faces.

    k = 2 * sides thin plates, each as wide as the bar and tan(pi / 2k) times that thick, turned by
    180 / k degrees from each other: their union is exactly the regular 2k-gon (checked
    numerically), so a round bar has flat, even facets and no ridges. Only the plates' narrow
    outer faces are on the surface; their wide faces are inside and are not emitted."""
    k = max(2, 2 * sides)
    return [(180.0 / k * i, math.tan(math.pi / (2 * k))) for i in range(k)]


# Each plate's end caps are staggered by a hair so overlapping caps never share a plane (no z-fighting).
_STAGGER = 0.012


def rod(x0, x1, d, mat, cy=8.0, cz=8.0, sides=2, ends=True, skip=(), **tags) -> list[Box]:
    """A round bar along X (a regular prism of 4 * sides faces)."""
    out = []
    for i, (angle, f) in enumerate(_plates(sides)):
        t = d * f
        e = _STAGGER * i
        rot = None if angle == 0 else {"origin": [x0, cy, cz], "axis": "x", "angle": angle}
        hide = ("north", "south") + (() if ends else ("east", "west")) + tuple(skip)
        out.append(box(x0 - e, cy - d / 2, cz - t / 2, x1 + e, cy + d / 2, cz + t / 2, mat, rot, hide, rod="x", **tags))
    return out


def rod_y(y0, y1, d, mat, cx=8.0, cz=8.0, sides=2, ends=True, skip=(), **tags) -> list[Box]:
    """A round bar along Y."""
    out = []
    for i, (angle, f) in enumerate(_plates(sides)):
        t = d * f
        e = _STAGGER * i
        rot = None if angle == 0 else {"origin": [cx, y0, cz], "axis": "y", "angle": angle}
        hide = ("north", "south") + (() if ends else ("up", "down")) + tuple(skip)
        out.append(box(cx - d / 2, y0 - e, cz - t / 2, cx + d / 2, y1 + e, cz + t / 2, mat, rot, hide, rod="y", **tags))
    return out


def rod_z(z0, z1, d, mat, cx=8.0, cy=8.0, sides=2, ends=True, skip=(), **tags) -> list[Box]:
    """A round bar along Z."""
    out = []
    for i, (angle, f) in enumerate(_plates(sides)):
        t = d * f
        e = _STAGGER * i
        rot = None if angle == 0 else {"origin": [cx, cy, z0], "axis": "z", "angle": angle}
        hide = ("up", "down") + (() if ends else ("north", "south")) + tuple(skip)
        out.append(box(cx - d / 2, cy - t / 2, z0 - e, cx + d / 2, cy + t / 2, z1 + e, mat, rot, hide, rod="z", **tags))
    return out


def chain(x, y, segments, sy, sz, mat, cz=8.0, **tags) -> list[Box]:
    """A bent bar: (length, angle in degrees about Z) segments joined end to end from (x, y).
    Each segment overlaps its neighbours by half the bar's height so bends stay closed."""
    out = []
    for length, angle in segments:
        ext = sy / 2
        out.append(box(x - ext, y - sy / 2, cz - sz / 2, x + length + ext, y + sy / 2, cz + sz / 2, mat, zrot(x, y, angle, cz), **tags))
        x += length * math.cos(math.radians(angle))
        y += length * math.sin(math.radians(angle))
    return out


# ----------------------------------------------------------------------------- unwrapping

def face_extent(b: Box, d: str) -> tuple[float, float]:
    dx, dy, dz = (b.to[i] - b.frm[i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[d]


def face_coords(b: Box, d: str, h: int, w: int):
    """Model-space (pre-rotation) coordinates of each texel centre of a face, following vanilla's
    default UV orientation (column 0 is the face's left as seen from outside, row 0 its top)."""
    (x0, y0, z0), (x1, y1, z1) = b.frm, b.to
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    one = np.ones_like(U)
    if d == "north":
        return x1 - U * (x1 - x0), y1 - V * (y1 - y0), one * z0
    if d == "south":
        return x0 + U * (x1 - x0), y1 - V * (y1 - y0), one * z1
    if d == "west":
        return one * x0, y1 - V * (y1 - y0), z0 + U * (z1 - z0)
    if d == "east":
        return one * x1, y1 - V * (y1 - y0), z1 - U * (z1 - z0)
    if d == "up":
        return x0 + U * (x1 - x0), one * y1, z0 + V * (z1 - z0)
    return x0 + U * (x1 - x0), one * y0, z1 - V * (z1 - z0)  # down


def pack(rects: list[tuple[int, int]], size: int) -> list[tuple[int, int]] | None:
    """Shelf-pack (w, h) rectangles (padding included) into size x size; None if they do not fit."""
    order = sorted(range(len(rects)), key=lambda i: (-rects[i][1], -rects[i][0]))
    pos: list[tuple[int, int] | None] = [None] * len(rects)
    x = y = shelf = 0
    for i in order:
        w, h = rects[i]
        if w > size:
            return None
        if x + w > size:
            x, y, shelf = 0, y + shelf, 0
        if y + h > size:
            return None
        pos[i] = (x, y)
        x += w
        shelf = max(shelf, h)
    return pos  # type: ignore[return-value]


# ----------------------------------------------------------------------------- materials

def _c(hexs: str) -> np.ndarray:
    return np.array(N.hex_rgb(hexs), dtype=float)


class Noise:
    """Periodic 128x128 fields shared by one model's faces, sampled through random windows so the
    feature size is the same on every face whatever its size."""

    def __init__(self, seed: int):
        s = 128
        self.fields = {
            "cloud": N.spectral(s, s, beta=2.3, seed=seed),
            "blotch": N.spectral(s, s, beta=2.8, seed=seed + 1),
            "grit": N.spectral(s, s, beta=0.9, seed=seed + 2),
            "streak": N.spectral(s, s, beta=2.0, seed=seed + 3, aniso=(1.0, 9.0)),
            "grain": N.spectral(s, s, beta=1.7, seed=seed + 4, aniso=(1.0, 14.0)),
            "chip": N.spectral(s, s, beta=1.6, seed=seed + 5),
            "drops": np.clip(N.worley(s, s, 46, seed=seed + 6), 0, 1),
        }
        self.rng = np.random.default_rng(seed + 7)

    def win(self, name: str, h: int, w: int) -> np.ndarray:
        f = self.fields[name]
        return N.tile_window(f, h, w, int(self.rng.integers(0, 128)), int(self.rng.integers(0, 128)))


def _edges(h: int, w: int) -> np.ndarray:
    e = np.zeros((h, w), dtype=bool)
    if h >= 3:
        e[0, :] = e[-1, :] = True
    if w >= 3:
        e[:, 0] = e[:, -1] = True
    return e


def _rust(rgb, nz, h, w, amount):
    if amount <= 0:
        return rgb
    mask = N.smoothstep(1.0 - amount, 1.0 - amount + 0.14, nz.win("blotch", h, w))
    col = N.ramp(nz.win("grit", h, w), [(0.0, "#3f2214"), (0.5, "#7a3d1c"), (1.0, "#a9622e")])
    return N.lerp(rgb, col, mask * 0.92)


def _blood(rgb, nz, h, w, amount):
    if amount <= 0:
        return rgb
    drops = N.smoothstep(0.32, 0.12, nz.win("drops", h, w))
    smear = N.smoothstep(1.0 - amount, 1.0 - amount + 0.1, nz.win("cloud", h, w))
    mask = np.clip(drops * (nz.win("blotch", h, w) > 1.0 - amount * 1.4) + smear * 0.85, 0, 1)
    col = N.ramp(nz.win("grit", h, w), [(0.0, "#3a0806"), (0.6, "#5e110c"), (1.0, "#7c1b13")])
    return N.lerp(rgb, col, mask * 0.9)


def _dirt(rgb, nz, h, w, amount):
    if amount <= 0:
        return rgb
    return rgb * (1.0 - amount * N.smoothstep(0.45, 0.9, nz.win("cloud", h, w)))[..., None]


def paint(mat: dict, b: Box, d: str, h: int, w: int, nz: Noise, ppu: int = PPU) -> np.ndarray:
    """Paint one face: float RGB 0..255, (h, w, 3)."""
    X, Y, Z = face_coords(b, d, h, w)
    kind = mat["kind"]
    edges = _edges(h, w)
    rod_cap = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}.get(b.tags.get("rod"), ())
    if d in rod_cap:
        edges[:] = False  # a round bar's end is many overlapping plate caps: no dark seams between them
    cloud, grit = nz.win("cloud", h, w), nz.win("grit", h, w)
    if kind in ("metal", "brass"):
        base, dark = _c(mat["base"]), _c(mat["dark"])
        streak = nz.win("streak", w, h).T if mat.get("vertical") else nz.win("streak", h, w)
        t = 0.55 + mat.get("brush", 0.6) * 0.5 * (streak - 0.5) + 0.25 * (cloud - 0.5) + 0.14 * (grit - 0.5)
        rgb = dark + (base - dark) * np.clip(t, 0, 1)[..., None]
        bevel = b.tags.get("bevel")
        if bevel and d in ("north", "south"):
            # The ground edge: brighter, finer steel below the bevel line, a dark line where it starts.
            low = Y < b.frm[1] + bevel
            line = np.abs(Y - (b.frm[1] + bevel)) < 0.5 / ppu * 1.2
            rgb = np.where(low[..., None], np.clip(rgb * 1.18 + 26, 0, 255), rgb)
            rgb = np.where(line[..., None], rgb * 0.62, rgb)
        if bevel and d == "down":
            rgb = np.clip(rgb * 1.28 + 30, 0, 255)
        if mat.get("knurl"):
            ii, jj = np.meshgrid(np.arange(w), np.arange(h))
            rgb = np.where((((ii + jj) % 3 == 0) | ((ii - jj) % 3 == 0))[..., None], rgb * 0.66, rgb)
        serr = b.tags.get("serrations")
        if serr and d in ("north", "south"):
            groove = (X > serr[0]) & (X < serr[1]) & ((X * 4.0) % 1.0 < 0.42) & (Y > b.frm[1] + 0.35)
            rgb = np.where(groove[..., None], rgb * 0.45, rgb)
        port = b.tags.get("port")
        if port and d == "south":
            hole = (X > port[0]) & (X < port[1]) & (Y > port[2]) & (Y < port[3])
            rgb = np.where(hole[..., None], rgb * 0.25, rgb)
        rgb = _rust(rgb, nz, h, w, mat.get("rust", 0.0))
        rgb = np.where(edges[..., None], np.clip(rgb * 1.14 + 14, 0, 255), rgb)  # worn, light-catching edges
    elif kind == "paint":
        col = _c(mat["color"])
        rgb = col * (1.0 + 0.07 * (cloud - 0.5) + 0.06 * (grit - 0.5))[..., None] * np.ones((h, w, 3))
        under = N.lerp(N.solid(h, w, "#8a8e90"), N.ramp(grit, [(0, "#4a2a18"), (1, "#8c4a22")]), mat.get("under_rust", 0.5))
        chip_amt = mat.get("chip", 0.25)
        chip_field = nz.win("chip", h, w) + np.where(edges, 0.22, 0.0)
        chips = N.smoothstep(1.0 - chip_amt, 1.0 - chip_amt + 0.06, chip_field)
        rgb = N.lerp(rgb, under, chips)
        rgb = np.where(edges[..., None], rgb * 0.86, rgb)
    elif kind == "wood":
        grain = nz.win("grain", w, h).T if mat.get("vertical") else nz.win("grain", h, w)
        rings = 0.5 + 0.5 * np.sin(grain * mat.get("rings", 22.0))
        t = 0.48 + 0.34 * (rings - 0.5) + 0.22 * (cloud - 0.5) + 0.08 * (grit - 0.5)
        rgb = N.ramp(t, [(0.0, mat["dark"]), (1.0, mat["light"])])
        if mat.get("ribs"):
            groove = (X * mat["ribs"]) % 1.0 < 0.3
            rgb = np.where(groove[..., None], rgb * 0.6, rgb)
        if mat.get("checker"):
            ii, jj = np.meshgrid(np.arange(w), np.arange(h))
            hatch = ((ii + jj) % 3 == 0) | ((ii - jj) % 3 == 0)
            inside = ~edges
            rgb = np.where((hatch & inside)[..., None], rgb * 0.7, rgb)
        rgb = np.where(edges[..., None], rgb * 0.8, rgb)
    elif kind == "rubber":
        col = _c(mat["color"])
        rgb = col * (1.0 + 0.1 * (grit - 0.5))[..., None] * np.ones((h, w, 3))
        rings = mat.get("rings", 0.0)
        if rings:
            groove = (X * rings) % 1.0 < 0.34
            rgb = np.where(groove[..., None], rgb * 0.55, rgb)
        rgb = np.where(edges[..., None], np.clip(rgb * 1.2 + 6, 0, 255), rgb)
    elif kind == "tape":
        col = _c(mat["color"])
        seam = ((X * 1.5 + (Y + Z) * 0.75) % 1.0) < 0.16
        rgb = col * (1.0 + 0.12 * (grit - 0.5) + 0.08 * (cloud - 0.5))[..., None] * np.ones((h, w, 3))
        rgb = np.where(seam[..., None], rgb * 0.62, rgb)
    elif kind == "polymer":
        col = _c(mat["color"])
        rgb = col * (1.0 + 0.08 * (grit - 0.5) + 0.05 * (cloud - 0.5))[..., None] * np.ones((h, w, 3))
        if mat.get("stipple"):
            rgb = rgb * (1.0 + 0.25 * (grit - 0.5))[..., None]
        rgb = np.where(edges[..., None], np.clip(rgb * 1.25 + 8, 0, 255), rgb)
    elif kind == "fabric":
        col = _c(mat["color"])
        ii, jj = np.meshgrid(np.arange(w), np.arange(h))
        weave = np.where((ii + jj) % 2 == 0, 1.04, 0.96)
        rgb = col * (weave * (1.0 + 0.12 * (grit - 0.5) + 0.1 * (cloud - 0.5)))[..., None] * np.ones((h, w, 3))
        seam = b.tags.get("seam")
        if seam:
            rgb = np.where(edges[..., None], rgb * 0.72, rgb)  # stitched seams
        else:
            rgb = np.where(edges[..., None], rgb * 0.85, rgb)
    elif kind == "lens":
        col, hot = _c(mat["color"]), _c(mat["hot"])
        cx, cy = (w - 1) / 2, (h - 1) / 2
        ii, jj = np.meshgrid(np.arange(w), np.arange(h))
        r = np.sqrt(((ii - cx) / max(1, w / 2)) ** 2 + ((jj - cy) / max(1, h / 2)) ** 2)
        rgb = N.lerp(np.broadcast_to(hot, (h, w, 3)).copy(), np.broadcast_to(col, (h, w, 3)).copy(), np.clip(r, 0, 1))
        glint = (ii - cx < -0.15 * w) & (jj - cy < -0.15 * h) & (r < 0.75)
        rgb = np.where(glint[..., None], np.clip(rgb + 40, 0, 255), rgb)
    else:
        raise ValueError(kind)
    rgb = _dirt(rgb, nz, h, w, mat.get("dirt", 0.0))
    rgb = _blood(rgb, nz, h, w, b.tags.get("blood", mat.get("blood", 0.0)))
    return np.clip(rgb, 0, 255)


# ----------------------------------------------------------------------------- building

def build(name: str, boxes: list[Box], materials: dict[str, dict], seed: int, texture: str):
    """Unwrap, paint and return (elements JSON, RGBA texture, texture size, extra textures).

    A box tag `ext={"north": ("deadlands:block/decor/stove_front", [u0, v0, u1, v1])}` maps that face
    to an existing texture instead (painted art such as a stove front); those textures come back as
    extra texture variables for the model."""
    extras: dict[str, str] = {}

    def ext_key(ref: str) -> str:
        for key, value in extras.items():
            if value == ref:
                return key
        key = f"e{len(extras)}"
        extras[key] = ref
        return key

    def unwrap(ppu: int):
        out = []
        for bi, b in enumerate(boxes):
            for d in DIRS:
                if d in b.skip or d in b.tags.get("ext", {}):
                    continue
                ue, ve = face_extent(b, d)
                if ue <= 0 or ve <= 0:
                    continue
                out.append((bi, d, max(1, round(ue * ppu)), max(1, round(ve * ppu))))
        return out

    # 8 texels per unit when the faces fit a 256 texture; large objects (cans, packs) step down to 6
    # or 4 rather than ever needing a bigger texture.
    pos = None
    for ppu, sizes in ((PPU, (64, 128, 256)), (6, (256,)), (4, (256, 512))):
        faces = unwrap(ppu)
        for size in sizes:
            pos = pack([(w + 2 * PAD, h + 2 * PAD) for _, _, w, h in faces], size)
            if pos is not None:
                break
        if pos is not None:
            break
    if pos is None:
        raise RuntimeError(f"{name}: faces do not fit a 512 texture")
    tex = np.zeros((size, size, 4), dtype=float)
    nz = Noise(seed)
    uv_scale = 16.0 / size
    elements = []
    face_json: dict[int, dict] = {}
    for (bi, d, w, h), (px, py) in zip(faces, pos):
        b = boxes[bi]
        rgb = paint(materials[b.mat], b, d, h, w, nz, ppu)
        x0, y0 = px + PAD, py + PAD
        tex[y0:y0 + h, x0:x0 + w, :3] = rgb
        tex[y0:y0 + h, x0:x0 + w, 3] = 255
        # Padding: repeat the face's border texels outward (no transparent bleed under mipmaps).
        region = np.pad(rgb, ((PAD, PAD), (PAD, PAD), (0, 0)), mode="edge")
        tex[py:py + h + 2 * PAD, px:px + w + 2 * PAD, :3] = region
        tex[py:py + h + 2 * PAD, px:px + w + 2 * PAD, 3] = 255
        tex[y0:y0 + h, x0:x0 + w, :3] = rgb
        uv = [round(x0 * uv_scale, 4), round(y0 * uv_scale, 4), round((x0 + w) * uv_scale, 4), round((y0 + h) * uv_scale, 4)]
        face_json.setdefault(bi, {})[d] = {"uv": uv, "texture": "#skin"}
    for bi, b in enumerate(boxes):
        for d, (ref, uv) in b.tags.get("ext", {}).items():
            if d not in b.skip:
                face_json.setdefault(bi, {})[d] = {"uv": list(uv), "texture": "#" + ext_key(ref)}
        if bi not in face_json:
            continue
        for i in range(3):
            assert -16.0 <= b.frm[i] <= 32.0 and -16.0 <= b.to[i] <= 32.0, (name, bi, b)
        el = {"from": [round(v, 4) for v in b.frm], "to": [round(v, 4) for v in b.to], "faces": face_json[bi]}
        if b.rot:
            el["rotation"] = b.rot
        if b.tags.get("glow"):
            el["light_emission"] = int(b.tags["glow"])
        elements.append(el)
    return elements, tex, size, extras
