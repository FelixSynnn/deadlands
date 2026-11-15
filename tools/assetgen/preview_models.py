"""Offline preview of DEADLANDS item models: renders the model JSON and its texture to a PNG.

A small software rasteriser (orthographic, z-buffered, textured, simple lighting) that reads only
the files under src/main/resources. It never starts Minecraft. Use it to review a model's shape and
texture from several angles, and with an item's display transform applied (roughly how the camera
sees it in first person).

    python preview_models.py machete pistol --out preview.png
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets"

CORNERS = {  # face corners TL, TR, BR, BL as seen from outside (vanilla's default UV orientation)
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def element_matrix(rot: dict | None) -> tuple[np.ndarray, np.ndarray]:
    if not rot:
        return np.eye(3), np.zeros(3)
    origin = np.array(rot["origin"], dtype=float)
    if "axis" in rot:
        a = math.radians(rot["angle"])
        m = {"x": rot_x, "y": rot_y, "z": rot_z}[rot["axis"]](a)
    else:  # Euler: rotationZYX(z, y, x) = Rz * Ry * Rx
        m = rot_z(math.radians(rot.get("z", 0))) @ rot_y(math.radians(rot.get("y", 0))) @ rot_x(math.radians(rot.get("x", 0)))
    return m, origin


def texture_path(ref: str) -> Path:
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    return ASSETS / ns / "textures" / f"{path}.png"


VANILLA_DISPLAY = {
    "item/handheld": {
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    },
    "item/generated": {
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    },
}


def load(name: str) -> tuple[dict, dict]:
    model = json.loads((ASSETS / "deadlands" / "models" / "item" / f"{name}.json").read_text(encoding="utf-8"))
    tex = {key: np.array(Image.open(texture_path(ref)).convert("RGBA")) for key, ref in model["textures"].items() if not ref.startswith("#")}
    if "elements" not in model and "layer0" in tex:
        # A flat sprite: extrude it as item/generated does (one texel = one box, one unit thick).
        sprite = tex["layer0"]
        sh, sw = sprite.shape[:2]
        px = 16.0 / sw
        elements = []
        for y in range(sh):
            for x in range(sw):
                if sprite[y, x, 3] == 0:
                    continue
                uv = [x * 16.0 / sw, y * 16.0 / sh, (x + 1) * 16.0 / sw, (y + 1) * 16.0 / sh]
                elements.append({"from": [x * px, 16 - (y + 1) * px, 7.5], "to": [(x + 1) * px, 16 - y * px, 8.5],
                                 "faces": {d: {"uv": uv, "texture": "#layer0"} for d in ("north", "south", "east", "west", "up", "down")}})
        model = dict(model, elements=elements)
        display = dict(VANILLA_DISPLAY.get(model.get("parent", "").replace("minecraft:", ""), {}))
        display.update(model.get("display", {}))
        model["display"] = display
    return model, tex


def quads(model: dict, tex: dict):
    for el in model["elements"]:
        a, b = el["from"], el["to"]
        m, origin = element_matrix(el.get("rotation"))
        for d, face in el["faces"].items():
            pts = np.array(CORNERS[d](a, b), dtype=float)
            pts = (pts - origin) @ m.T + origin
            img = tex[face["texture"].lstrip("#")]
            yield pts, face["uv"], img


def display_matrix(t: dict | None, left: bool = False) -> np.ndarray:
    """4x4 for an item display transform about the model centre (as ItemTransform.apply does)."""
    out = np.eye(4)
    if not t:
        return out
    rx, ry, rz = t.get("rotation", [0, 0, 0])
    tx, ty, tz = t.get("translation", [0, 0, 0])
    s = t.get("scale", [1, 1, 1])
    if left:
        tx, ry, rz = -tx, -ry, -rz
    r = rot_x(math.radians(rx)) @ rot_y(math.radians(ry)) @ rot_z(math.radians(rz))
    out[:3, :3] = r @ np.diag(s)
    out[:3, 3] = np.array([tx, ty, tz])
    return out


def render(model, tex, view: np.ndarray, size: int = 360, scale: float = 18.0, display: dict | None = None, left=False) -> Image.Image:
    img = np.zeros((size, size, 3)) + np.array([46, 50, 54])
    zbuf = np.full((size, size), -1e9)
    dm = display_matrix(display, left)
    light = np.array([-0.35, 0.8, 0.6])
    light /= np.linalg.norm(light)
    for pts, uv, timg in quads(model, tex):
        p = pts - 8.0
        p = p @ dm[:3, :3].T + dm[:3, 3]
        p = p @ view.T
        n = np.cross(p[3] - p[0], p[1] - p[0])  # corners run clockwise seen from outside
        if np.linalg.norm(n) < 1e-9 or n[2] <= 0:
            continue  # back face
        n /= np.linalg.norm(n)
        shade = 0.5 + 0.5 * max(0.0, float(n @ light))
        sx = p[:, 0] * scale + size / 2
        sy = -p[:, 1] * scale + size / 2
        th, tw = timg.shape[:2]
        u0, v0, u1, v1 = (c / 16.0 for c in uv)
        tuv = np.array([[u0 * tw, v0 * th], [u1 * tw, v0 * th], [u1 * tw, v1 * th], [u0 * tw, v1 * th]])
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], p[list(tri), 2]
            x0, x1 = max(0, int(math.floor(xs.min()))), min(size - 1, int(math.ceil(xs.max())))
            y0, y1 = max(0, int(math.floor(ys.min()))), min(size - 1, int(math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            det = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(det) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / det
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / det
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (z > sub)
            if not ok.any():
                continue
            t = tuv[list(tri)]
            tu = np.clip((w0 * t[0, 0] + w1 * t[1, 0] + w2 * t[2, 0]).astype(int), 0, tw - 1)
            tv = np.clip((w0 * t[0, 1] + w1 * t[1, 1] + w2 * t[2, 1]).astype(int), 0, th - 1)
            texel = timg[tv, tu]
            ok &= texel[..., 3] > 0
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[ok] = texel[..., :3][ok] * shade
            sub[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def render_first_person(model, tex, display: dict | None, w: int = 480, h: int = 270, fov: float = 70.0) -> Image.Image:
    """Roughly what the first-person camera sees: the display transform, the right arm's offset
    (ItemInHandRenderer.applyItemArmTransform: 0.56, -0.52, -0.72 blocks) and a 70-degree perspective."""
    img = np.zeros((h, w, 3)) + np.array([46, 50, 54])
    zbuf = np.full((h, w), -1e9)
    dm = display_matrix(display)
    f = (h / 2) / math.tan(math.radians(fov) / 2)
    light = np.array([-0.35, 0.8, 0.6])
    light /= np.linalg.norm(light)
    arm = np.array([0.56, -0.52, -0.72])
    for pts, uv, timg in quads(model, tex):
        p = pts / 16.0 - 0.5
        p = p @ dm[:3, :3].T + dm[:3, 3] / 16.0 + arm
        n = np.cross(p[3] - p[0], p[1] - p[0])  # corners run clockwise seen from outside
        if np.linalg.norm(n) < 1e-12:
            continue
        n /= np.linalg.norm(n)
        if float(n @ (-p.mean(axis=0))) <= 0:
            continue  # facing away from the eye
        shade = 0.5 + 0.5 * max(0.0, float(n @ light))
        depth = -p[:, 2]
        if (depth <= 0.01).any():
            continue
        sx = p[:, 0] / depth * f + w / 2
        sy = -p[:, 1] / depth * f + h / 2
        th, tw = timg.shape[:2]
        u0, v0, u1, v1 = (c / 16.0 for c in uv)
        tuv = np.array([[u0 * tw, v0 * th], [u1 * tw, v0 * th], [u1 * tw, v1 * th], [u0 * tw, v1 * th]])
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], -depth[list(tri)]
            x0, x1 = max(0, int(math.floor(xs.min()))), min(w - 1, int(math.ceil(xs.max())))
            y0, y1 = max(0, int(math.floor(ys.min()))), min(h - 1, int(math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            det = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(det) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / det
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / det
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (z > sub)
            if not ok.any():
                continue
            t = tuv[list(tri)]
            tu = np.clip((w0 * t[0, 0] + w1 * t[1, 0] + w2 * t[2, 0]).astype(int), 0, tw - 1)
            tv = np.clip((w0 * t[0, 1] + w1 * t[1, 1] + w2 * t[2, 1]).astype(int), 0, th - 1)
            texel = timg[tv, tu]
            ok &= texel[..., 3] > 0
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[ok] = texel[..., :3][ok] * shade
            sub[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


VIEWS = {
    "side": np.eye(3),
    "three_quarter": rot_x(math.radians(22)) @ rot_y(math.radians(-38)),
    "top": rot_x(math.radians(80)),
    "back_quarter": rot_x(math.radians(18)) @ rot_y(math.radians(140)),
}


def sheet(names: list[str], out: Path, size: int = 300) -> None:
    fpw = size * 16 // 9
    cols = len(VIEWS)
    canvas = Image.new("RGB", (cols * size + fpw, len(names) * size), (30, 33, 36))
    for r, name in enumerate(names):
        model, tex = load(name)
        for c, (label, view) in enumerate(VIEWS.items()):
            canvas.paste(render(model, tex, view, size, scale=size / 22.0), (c * size, r * size))
        fp = model.get("display", {}).get("firstperson_righthand")
        big = np.array(render_first_person(model, tex, fp, 1280, 720))
        ys, xs = np.nonzero(np.any(big != np.array([46, 50, 54]), axis=-1))
        # The first-person panel: the right half of the screen, where the item is held (as on screen).
        crop = Image.fromarray(big[180:720, 640:1280])
        crop.thumbnail((fpw, size))
        canvas.paste(crop, (cols * size, r * size))
    canvas.save(out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="+")
    ap.add_argument("--out", default="preview.png")
    ap.add_argument("--size", type=int, default=300)
    args = ap.parse_args()
    sheet(args.names, Path(args.out), args.size)
