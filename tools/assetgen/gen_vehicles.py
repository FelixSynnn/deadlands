"""Vehicles (Milestone 6): twelve box-built vehicle rigs written as Java (VehicleGeometry.java), their
textures in four finishes (faded, rusted, clean, burned out) with see-through glass, four overlay
textures each (dents, a battered body with broken glass, overgrowth, and the lights), the part,
fuel-can and tow-rope icons, engine, impact, repair and horn sounds, the road-surface tag, recipes
and the English text.

Rigs have an interior (seats, dashboard, steering wheel) and, where the cab is closed, front doors
("door_l", "door_r") hinged at their front edge so the model can swing them.

Like the infected, rig and texture come from one description so UVs always agree. Model space: y
down, the ground at y = 24, the front facing -Z, 16 pixels to a block. Every rig has a "chassis"
part holding the body parts, and "wheel_<f|m|r><l|r>" parts pivoted on their axles so the model can
spin and steer them. Textures are 512x512, one texel per model pixel.
"""
from __future__ import annotations

import json
import zlib

import numpy as np
from PIL import Image, ImageDraw

import dl_data as D
import dl_noise as N
from gen_items import _outline

NS = D.NS
TEX_W, TEX_H = 512, 512
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "vehicle"
FINISHES = ("faded", "rusted", "clean", "burned")

# kind: length, width, clearance, lower body height, cabin (from, to, height), wheel radius, wheel width, axles (z from the front),
#       paint colour, extras
KINDS = {
    "sedan": dict(L=72, W=30, gc=5, low=10, cab=(22, 56, 10), r=5, ww=4, axles=(13, 58), paint="#6a7a88", extras=()),
    "pickup": dict(L=80, W=32, gc=7, low=11, cab=(20, 46, 12), r=6, ww=5, axles=(14, 64), paint="#8a3a2e", extras=("bed",)),
    "suv": dict(L=74, W=32, gc=7, low=12, cab=(18, 72, 12), r=6, ww=5, axles=(14, 60), paint="#3e4a3a", extras=("rack",)),
    "military_jeep": dict(L=64, W=30, gc=8, low=11, cab=(22, 46, 11), r=6, ww=5, axles=(12, 52), paint="#4a5236", extras=("spare", "open")),
    "ambulance": dict(L=88, W=34, gc=7, low=12, cab=(14, 34, 12), r=6, ww=5, axles=(14, 70), paint="#d8d8d0", extras=("box22", "lightbar", "stripe_red")),
    "police_car": dict(L=72, W=30, gc=5, low=10, cab=(22, 56, 10), r=5, ww=4, axles=(13, 58), paint="#1e2228", extras=("lightbar", "doors_white")),
    "delivery_van": dict(L=84, W=34, gc=6, low=12, cab=(10, 30, 14), r=6, ww=5, axles=(12, 68), paint="#b08a3a", extras=("box20", "logo")),
    "bus": dict(L=128, W=38, gc=7, low=14, cab=(4, 126, 22), r=7, ww=6, axles=(20, 104), paint="#c8a030", extras=("windows",)),
    "fire_truck": dict(L=112, W=38, gc=8, low=14, cab=(8, 36, 16), r=7, ww=6, axles=(18, 80, 96), paint="#a82a22", extras=("box16", "lightbar", "ladder")),
    "cargo_truck": dict(L=120, W=38, gc=9, low=12, cab=(8, 34, 18), r=7, ww=6, axles=(18, 86, 102), paint="#4a5a6a", extras=("box26", "canvas")),
    "motorcycle": dict(L=36, W=6, gc=6, low=6, cab=None, r=6, ww=3, axles=(5, 31), paint="#2a2a2e", extras=("bike",)),
    "tractor": dict(L=52, W=26, gc=10, low=10, cab=(26, 46, 16), r=(5, 10), ww=6, axles=(8, 40), paint="#3a7a3a", extras=("open", "exhaust")),
}
NAMES = {"sedan": "Sedan", "pickup": "Pickup Truck", "suv": "SUV", "military_jeep": "Military Jeep", "ambulance": "Ambulance",
         "police_car": "Police Car", "delivery_van": "Delivery Van", "bus": "Bus", "fire_truck": "Fire Truck", "cargo_truck": "Cargo Truck",
         "motorcycle": "Motorcycle", "tractor": "Tractor"}


class Part:
    def __init__(self, name, parent, pivot, cubes, role):
        self.name, self.parent, self.pivot, self.cubes, self.role = name, parent, pivot, cubes, role
        self.uv = []


def rig(kind: str) -> list[Part]:
    k = KINDS[kind]
    L, W, gc, low = k["L"], k["W"], k["gc"], k["low"]
    z0 = -L / 2
    top = -(gc + low)  # y of the top of the lower body (y is down, 0 is the ground)
    parts = [Part("chassis", None, (0, 24, 0), [], None)]
    add = lambda name, cubes, role: parts.append(Part(name, "chassis", (0, 0, 0), cubes, role))
    if "bike" in k["extras"]:
        add("frame", [(-1.5, -12, z0 + 6, 3, 5, L - 12)], "metal")
        add("tank", [(-2.5, -16, z0 + 9, 5, 5, 10)], "body")
        add("seat", [(-2.5, -14, z0 + 19, 5, 3, 12)], "seat")
        add("fork", [(-0.5, -19, z0 + 5, 1, 13, 1), (-5, -20, z0 + 5, 10, 1, 1)], "metal")
        add("lamp", [(-1.5, -18, z0 + 3, 3, 3, 2)], "lamp")
    else:
        add("lower", [(-W / 2, top, z0, W, low, L)], "body")
        c0, c1, ch = k["cab"]
        add("cabin", [(-W / 2 + 1, top - ch, z0 + c0, W - 2, ch, c1 - c0)], "cabin")
        for extra in k["extras"]:
            if extra == "bed":
                add("bed", [(-W / 2, top - 5, z0 + c1, 1, 5, L - c1), (W / 2 - 1, top - 5, z0 + c1, 1, 5, L - c1),
                            (-W / 2 + 1, top - 5, z0 + L - 1, W - 2, 5, 1)], "body")
            elif extra.startswith("box"):
                h = int(extra[3:])
                add("box", [(-W / 2, top - h, z0 + c1, W, h, L - c1)], "box")
            elif extra == "lightbar":
                add("lightbar", [(-W / 2 + 6, top - ch - 2, z0 + (c0 + c1) / 2 - 2, W - 12, 2, 4)], "lightbar")
            elif extra == "rack":
                add("rack", [(-W / 2 + 3, top - ch - 1, z0 + c0 + 6, W - 6, 1, c1 - c0 - 12)], "metal")
            elif extra == "spare":
                add("spare", [(-5, top - 9, z0 + L, 10, 10, 3)], "wheel")
            elif extra == "ladder":
                add("ladder", [(-4, top - 16 - 3, z0 + c1 + 4, 8, 3, L - c1 - 8)], "metal")
            elif extra == "exhaust":
                add("exhaust", [(W / 2 - 6, top - 14, z0 + 16, 2, 14, 2)], "metal")
    if "bike" not in k["extras"]:
        interior(kind, k, parts, add, z0, top)
    axle_names = ["f", "r"] if len(k["axles"]) == 2 else ["f", "m", "r"]
    for i, z in enumerate(k["axles"]):
        r = k["r"][min(i, 1)] if isinstance(k["r"], tuple) else k["r"]
        ww = k["ww"]
        if "bike" in k["extras"]:
            parts.append(Part(f"wheel_{axle_names[i]}l", "chassis", (0, -r, z0 + z), [(-ww / 2, -r, -r, ww, 2 * r, 2 * r)], "wheel"))
        else:
            x = W / 2 - ww / 2 + 1
            parts.append(Part(f"wheel_{axle_names[i]}l", "chassis", (x, -r, z0 + z), [(-ww / 2, -r, -r, ww, 2 * r, 2 * r)], "wheel"))
            parts.append(Part(f"wheel_{axle_names[i]}r", "chassis", (-x, -r, z0 + z), [(-ww / 2, -r, -r, ww, 2 * r, 2 * r)], "wheel"))
    pack(parts)
    return parts


def interior(kind, k, parts, add, z0, top):
    """Seats, a dashboard and a steering wheel inside the cab; front doors where the cab is closed."""
    W, low = k["W"], k["low"]
    c0, c1, ch = k["cab"]
    cz0, cz1 = z0 + c0, z0 + c1
    floor = top
    add("dash", [(-W / 2 + 2, floor - 6, cz0 + 1, W - 4, 4, 3)], "dash")
    sx = 0 if kind == "tractor" else W / 4
    add("steer", [(sx - 3, floor - 9, cz0 + 4, 6, 6, 1)], "steer")
    bh = max(3, min(7, ch - 5))
    spots = []
    if kind == "tractor":
        spots = [(0, cz0 + 8)]
    elif kind == "bus":
        zz = cz0 + 14
        while zz + 9 <= cz1 - 4:
            spots += [(W / 4, zz), (-W / 4, zz)]
            zz += 14
    else:
        spots = [(W / 4, cz0 + 7), (-W / 4, cz0 + 7)]
        if c1 - c0 >= 30 and cz0 + 21 + 9 <= cz1:
            spots += [(W / 4, cz0 + 21), (-W / 4, cz0 + 21)]
    cubes = []
    for x, zz in spots:
        cubes.append((x - 4, floor - 4, zz, 8, 4, 7))
        cubes.append((x - 4, floor - 4 - bh, zz + 7, 8, bh, 2))
    add("seats", cubes, "seat")
    if "open" in k["extras"] or kind == "bus":
        return
    dl = min(14, max(6, (c1 - c0) // 2 - 1))
    dh = ch - 1 + low - 3
    for name, sgn in (("door_l", 1), ("door_r", -1)):
        parts.append(Part(name, "chassis", (sgn * (W / 2 - 0.45), top - ch + 1, cz0 + 1), [(-0.5, 0, 0, 1, dh, dl)], "door"))


def footprint(c):
    _, _, _, w, h, d = c
    return int(np.ceil(2 * d + 2 * w)), int(np.ceil(d + h))


def pack(parts):
    items = [(footprint(c), p, i) for p in parts for i, c in enumerate(p.cubes)]
    items.sort(key=lambda t: (-t[0][1], -t[0][0]))
    for p in parts:
        p.uv = [None] * len(p.cubes)
    x = y = shelf = 0
    for (fw, fh), p, i in items:
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        assert y + fh <= TEX_H, f"texture full at {p.name}"
        p.uv[i] = (x, y)
        x += fw
        shelf = max(shelf, fh)


def faces(u, v, c):
    """Image rects (x, y, w, h) of a cube's faces: Minecraft's box-UV layout."""
    w, h, d = (int(np.ceil(t)) for t in c[3:])
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def jf(v):
    return f"{float(v)}F"


def java_layer(method, parts):
    lines = [f"\tpublic static LayerDefinition {method}() {{", "\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for p in parts:
        b = "CubeListBuilder.create()"
        for (u, v), (x, y, z, w, h, d) in zip(p.uv, p.cubes):
            b += f".texOffs({u}, {v}).addBox({jf(x)}, {jf(y)}, {jf(z)}, {jf(w)}, {jf(h)}, {jf(d)})"
        px, py, pz = p.pivot
        parent = "root" if p.parent is None else p.parent
        lines.append(f"\t\tPartDefinition {p.name} = {parent}.addOrReplaceChild(\"{p.name}\", {b}, PartPose.offset({jf(px)}, {jf(py)}, {jf(pz)}));")
    lines += [f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});", "\t}"]
    return "\n".join(lines)


def write_geometry(rigs):
    body = "\n\n".join(java_layer(name, parts) for name, parts in rigs.items())
    src = f"""package com.deadlands.client.render.vehicle;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/assetgen/gen_vehicles.py - do not edit; change the rigs there and rerun it.
 *
 * <p>The twelve vehicle rigs. Model space: y down, the ground at y = 24, the front facing -Z. Every
 * rig has a "chassis" part with the body parts and the wheels ("wheel_fl", "wheel_rr"...) under it.
 */
@SuppressWarnings("unused")
public final class VehicleGeometry {{
	private VehicleGeometry() {{
	}}

{body}
}}
"""
    JAVA.mkdir(parents=True, exist_ok=True)
    (JAVA / "VehicleGeometry.java").write_text(src, encoding="utf-8")


# ============================================================================= painting

def seed_of(text):
    return zlib.crc32(text.encode())


def hexrgb(c):
    return np.array(N.hex_rgb(c), dtype=float)


def fill(img, rect, colour):
    x, y, w, h = rect
    img[y:y + h, x:x + w, :3] = colour
    img[y:y + h, x:x + w, 3] = 255


def paint(kind: str, parts, finish: str) -> np.ndarray:
    k = KINDS[kind]
    r = np.random.default_rng(seed_of(kind + finish))
    img = np.zeros((TEX_H, TEX_W, 4))
    base = hexrgb(k["paint"])
    glass = hexrgb("#2a3a44")
    dark = hexrgb("#1a1a1c")
    hole = hexrgb("#010203")  # left transparent: an open cab
    for p in parts:
        for (u, v), c in zip(p.uv, p.cubes):
            f = faces(u, v, c)
            for name, rect in f.items():
                x, y, w, h = rect
                if w <= 0 or h <= 0:
                    continue
                role = p.role
                if role == "door":
                    fill(img, rect, base)
                    if name in ("left", "right"):
                        gh = max(2, int(h * 0.42))
                        fill(img, (x + 1, y + 1, max(1, w - 2), gh), glass)
                        fill(img, (x + w - 3, y + gh + 3, 2, 1), hexrgb("#8a8c8e"))  # handle
                        fill(img, (x, y + h - 1, w, 1), base * 0.6)
                    continue
                if role in ("body", "cabin", "box"):
                    colour = base.copy()
                    if role == "box":
                        colour = hexrgb({"ambulance": "#dcdcd4", "delivery_van": "#c8b890", "cargo_truck": "#6a6a4a", "fire_truck": "#a82a22"}.get(kind, k["paint"]))
                    if name == "bottom":
                        colour = dark
                    fill(img, rect, colour)
                    if role == "cabin" and name == "top" and "open" in k["extras"]:
                        fill(img, rect, hole)
                    side = name in ("left", "right")
                    if role == "cabin" and name in ("front", "back", "left", "right"):
                        # Glass over the upper two thirds, with pillars.
                        gh = max(2, int(h * 0.62))
                        if "windows" in k["extras"] and side:
                            for wx in range(x + 2, x + w - 6, 9):
                                fill(img, (wx, y + 2, 7, gh - 1), glass)
                        elif "open" in k["extras"]:
                            fill(img, (x + 1, y + 1, w - 2, gh), glass if name == "front" else hole)
                        else:
                            fill(img, (x + 1, y + 1, w - 2, gh), glass)
                            if side and w > 20:
                                fill(img, (x + w // 2, y + 1, 1, gh), colour)  # door pillar
                        if side and "doors_white" in k["extras"]:
                            fill(img, (x + 3, y + gh + 1, w - 6, h - gh - 1), hexrgb("#d8d8d0"))
                    if role == "body":
                        if name == "front":
                            fill(img, (x + 2, y + 2, 5, 3), hexrgb("#e8e0b0"))
                            fill(img, (x + w - 7, y + 2, 5, 3), hexrgb("#e8e0b0"))
                            fill(img, (x + 9, y + 2, max(1, w - 18), h - 5), dark)  # grille
                            fill(img, (x, y + h - 2, w, 2), hexrgb("#55585a"))      # bumper
                        elif name == "back":
                            fill(img, (x + 1, y + 2, 4, 3), hexrgb("#a01a14"))
                            fill(img, (x + w - 5, y + 2, 4, 3), hexrgb("#a01a14"))
                            fill(img, (x, y + h - 2, w, 2), hexrgb("#55585a"))
                        elif side:
                            fill(img, (x, y + h - 2, w, 2), base * 0.6)             # sill
                            if "doors_white" in k["extras"]:
                                fill(img, (x + w // 3, y, w // 3, h - 2), hexrgb("#d8d8d0"))
                        elif name == "top":
                            fill(img, (x + 2, y + 2, w - 4, max(1, int(h * 0.2))), base * 0.9)  # hood line
                    if role == "box" and side:
                        if "stripe_red" in k["extras"]:
                            fill(img, (x, y + h // 2 - 2, w, 4), hexrgb("#b0241e"))
                            fill(img, (x + w // 2 - 3, y + 3, 6, 2), hexrgb("#b0241e"))
                            fill(img, (x + w // 2 - 1, y + 1, 2, 6), hexrgb("#b0241e"))
                        elif "logo" in k["extras"]:
                            fill(img, (x + w // 4, y + h // 3, w // 2, h // 3), hexrgb("#7a3a22"))
                        elif "canvas" in k["extras"]:
                            for sx in range(x + 6, x + w, 12):
                                fill(img, (sx, y, 1, h), hexrgb("#4a4a34"))
                elif role == "wheel":
                    fill(img, rect, hexrgb("#1c1c1e"))
                    if name in ("left", "right") and w >= 6:
                        cx, cy = x + w // 2, y + h // 2
                        rad = max(2, min(w, h) // 3)
                        yy, xx = np.mgrid[y:y + h, x:x + w]
                        hub = (xx - cx + 0.5) ** 2 + (yy - cy + 0.5) ** 2 <= rad * rad
                        img[y:y + h, x:x + w, :3][hub] = hexrgb("#8a8c8e")
                    else:
                        for ty in range(y, y + h, 3):
                            fill(img, (x, ty, w, 1), hexrgb("#0e0e10"))  # tread
                elif role == "lightbar":
                    fill(img, (x, y, w // 2, h), hexrgb("#b01e1e"))
                    fill(img, (x + w // 2, y, w - w // 2, h), hexrgb("#2040b0"))
                elif role == "seat":
                    fill(img, rect, hexrgb("#3a4a6a") if kind == "bus" else hexrgb("#2e2218"))
                    if name == "top":
                        fill(img, (x + 1, y + 1, max(1, w - 2), max(1, h - 2)), hexrgb("#4a3a2a") if kind != "bus" else hexrgb("#4a5a7a"))
                elif role == "dash":
                    fill(img, rect, hexrgb("#2a2a2c"))
                    if name == "top":
                        fill(img, (x + 2, y, 4, h), hexrgb("#5a4a2a"))
                elif role == "steer":
                    fill(img, rect, hexrgb("#151517"))
                elif role == "lamp":
                    fill(img, rect, hexrgb("#e8e0b0") if name == "front" else hexrgb("#55585a"))
                else:
                    fill(img, rect, hexrgb("#3a3c3e"))
    glass_mask = np.all(np.abs(img[..., :3] - glass) < 0.5, axis=2) & (img[..., 3] > 0)
    hole_mask = np.all(np.abs(img[..., :3] - hole) < 0.5, axis=2) & (img[..., 3] > 0)
    GLASS[kind] = glass_mask
    rgb = img[..., :3]
    solid = img[..., 3] > 0
    n1 = N.spectral(TEX_H, TEX_W, beta=2.0, seed=seed_of(kind) + 1)
    n2 = N.spectral(TEX_H, TEX_W, beta=1.2, seed=seed_of(kind) + 2)
    if finish == "burned":
        grey = rgb.mean(axis=2, keepdims=True)
        rgb[:] = grey * 0.18 + 18
        rust = N.smoothstep(0.55, 0.75, n1)[..., None]
        rgb[:] = rgb * (1 - rust * 0.7) + hexrgb("#5a2e18") * rust * 0.7
        rgb[:] = rgb * (0.7 + 0.5 * n2[..., None])
    else:
        fade = {"faded": 0.45, "rusted": 0.3, "clean": 0.08}[finish]
        grey = rgb.mean(axis=2, keepdims=True)
        rgb[:] = rgb * (1 - fade) + (grey * 0.6 + 70) * fade
        dirt = N.smoothstep(0.5, 0.85, n2)[..., None] * {"faded": 0.35, "rusted": 0.45, "clean": 0.1}[finish]
        rgb[:] = rgb * (1 - dirt) + hexrgb("#4a4034") * dirt
        if finish == "rusted":
            rust = N.smoothstep(0.58, 0.7, n1)[..., None]
            rgb[:] = rgb * (1 - rust * 0.85) + hexrgb("#7a3e1e") * rust * 0.85
        rgb[:] = rgb * (0.9 + 0.2 * n1[..., None])
        if finish != "clean":
            # Scratches: a few thin light lines.
            for _ in range(60):
                sx, sy = int(r.integers(0, TEX_W - 12)), int(r.integers(0, TEX_H))
                rgb[sy, sx:sx + int(r.integers(3, 12))] *= 1.25
    img[..., :3] = np.clip(rgb, 0, 255)
    img[~solid] = 0
    # Glass is see-through (and gone altogether from a burned-out shell); an open cab has no sides.
    img[glass_mask, 3] = 0 if finish == "burned" else 150
    img[hole_mask] = 0
    return img


GLASS: dict[str, np.ndarray] = {}
BODY_ROLES = ("body", "cabin", "box", "door")


def face_maps(parts):
    """Per texel: is it bodywork, a top face, a wheel; and how far down its face it lies (0 top, 1 bottom)."""
    body = np.zeros((TEX_H, TEX_W), bool)
    tops = np.zeros((TEX_H, TEX_W), bool)
    wheels = np.zeros((TEX_H, TEX_W), bool)
    down = np.zeros((TEX_H, TEX_W))
    for p in parts:
        for (u, v), c in zip(p.uv, p.cubes):
            for name, (x, y, w, h) in faces(u, v, c).items():
                if w <= 0 or h <= 0:
                    continue
                if p.role in BODY_ROLES:
                    body[y:y + h, x:x + w] = True
                    if name == "top":
                        tops[y:y + h, x:x + w] = True
                    elif name != "bottom":
                        down[y:y + h, x:x + w] = np.linspace(0.0, 1.0, h)[:, None]
                elif p.role == "wheel":
                    wheels[y:y + h, x:x + w] = True
                    down[y:y + h, x:x + w] = 1.0
    return body, tops, wheels, down


def overlay_damage(kind, parts, stage: int) -> np.ndarray:
    """Dents and scratches (stage 1); more of them, rust holes and broken glass (stage 2)."""
    body, tops, wheels, down = face_maps(parts)
    out = np.zeros((TEX_H, TEX_W, 4))
    n = N.spectral(TEX_H, TEX_W, beta=2.2, seed=seed_of(kind) + 31)
    n2 = N.spectral(TEX_H, TEX_W, beta=1.6, seed=seed_of(kind) + 32)
    lo = 0.69 if stage == 1 else 0.62
    rim = body & (n > lo - 0.03) & (n <= lo)
    dent = body & (n > lo)
    out[rim] = (*hexrgb("#9a9890"), 255)
    out[dent] = (*hexrgb("#3a3430"), 255)
    r = np.random.default_rng(seed_of(kind) + stage)
    ys, xs = np.nonzero(body)
    for _ in range(40 * stage):
        i = int(r.integers(0, len(xs)))
        x, y, ln = xs[i], ys[i], int(r.integers(3, 10))
        seg = body[y, x:x + ln]
        out[y, x:x + ln][seg] = (*hexrgb("#b8b4a8"), 255)
    if stage == 2:
        holes = body & (n2 > 0.83)
        out[holes] = (*hexrgb("#120f0d"), 255)
        out[body & (n2 > 0.79) & (n2 <= 0.83)] = (*hexrgb("#6a3a1e"), 255)
        glass = GLASS[kind]
        layer = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        gy, gx = np.nonzero(glass)
        for _ in range(min(60, len(gx) // 40 + 1)):
            if len(gx) == 0:
                break
            i = int(r.integers(0, len(gx)))
            cx, cy = int(gx[i]), int(gy[i])
            for _ in range(int(r.integers(4, 8))):
                a = r.uniform(0, 2 * np.pi)
                ln = r.uniform(3, 12)
                draw.line([(cx, cy), (cx + np.cos(a) * ln, cy + np.sin(a) * ln)], fill=(205, 210, 214, 255), width=1)
        cracks = np.asarray(layer).astype(float)
        hit = (cracks[..., 3] > 0) & glass
        out[hit] = cracks[hit]
    return out


def overlay_overgrown(kind, parts) -> np.ndarray:
    """Moss and dead creeper: thick on the roof and bonnet, climbing the sides from the ground."""
    body, tops, wheels, down = face_maps(parts)
    out = np.zeros((TEX_H, TEX_W, 4))
    n = N.spectral(TEX_H, TEX_W, beta=1.8, seed=seed_of(kind) + 41)
    fine = N.spectral(TEX_H, TEX_W, beta=0.8, seed=seed_of(kind) + 42)
    reach = np.where(tops, 0.75, np.clip((down - 0.45) * 1.5, 0.0, 1.0))
    reach = np.where(wheels, 0.42, reach)
    reach = np.where(GLASS.get(kind, np.zeros_like(body)), reach * 0.35, reach)
    cover = (body | wheels) & (n * 0.6 + fine * 0.4 < reach * 0.9)
    palette = np.array([hexrgb(c) for c in ("#2e3a1e", "#3e4a26", "#55602e", "#6a6438", "#4a3e26")])
    pick = np.clip((fine * len(palette)).astype(int), 0, len(palette) - 1)
    out[cover, :3] = palette[pick[cover]]
    out[cover, 3] = 255
    # Creeper hanging from the roof line down the sides.
    r = np.random.default_rng(seed_of(kind) + 43)
    ys, xs = np.nonzero(body & (down < 0.08) & ~tops)
    for _ in range(min(140, len(xs) // 6 + 1)):
        if len(xs) == 0:
            break
        i = int(r.integers(0, len(xs)))
        x, y = xs[i], ys[i]
        for k in range(int(r.integers(2, 9))):
            if y + k < TEX_H and body[y + k, x]:
                out[y + k, x] = (*palette[int(r.integers(0, 3))], 255)
    return out


def overlay_lights(kind, parts) -> np.ndarray:
    """Head and tail lights (and an emergency light bar) at full brightness; black elsewhere."""
    out = np.zeros((TEX_H, TEX_W, 4))
    head, tail = (*hexrgb("#fff4c8"), 255), (*hexrgb("#ff3a2a"), 255)
    for p in parts:
        for (u, v), c in zip(p.uv, p.cubes):
            for name, (x, y, w, h) in faces(u, v, c).items():
                if w <= 0 or h <= 0:
                    continue
                if p.role == "body" and name == "front":
                    out[y + 2:y + 5, x + 2:x + 7] = head
                    out[y + 2:y + 5, x + w - 7:x + w - 2] = head
                elif p.role == "body" and name == "back":
                    out[y + 2:y + 5, x + 1:x + 5] = tail
                    out[y + 2:y + 5, x + w - 5:x + w - 1] = tail
                elif p.role == "lamp" and name == "front":
                    out[y:y + h, x:x + w] = head
                elif p.role == "lightbar":
                    out[y:y + h, x:x + w // 2] = (*hexrgb("#ff2a2a"), 255)
                    out[y:y + h, x + w // 2:x + w] = (*hexrgb("#3a6aff"), 255)
    return out


# ============================================================================= preview

def side_view(parts, img, scale=3):
    """A flat side elevation (looking at the vehicle's left side) for the review sheet."""
    cubes = []
    for p in parts:
        for (u, v), c in zip(p.uv, p.cubes):
            px, py, pz = p.pivot
            x, y, z, w, h, d = c
            cubes.append((x + px, y + py, z + pz, w, h, d, faces(u, v, c)["left"]))
    zmin = min(c[2] for c in cubes)
    zmax = max(c[2] + c[5] for c in cubes)
    ymin = min(c[1] for c in cubes)
    out = np.zeros((int(-ymin) + 2, int(zmax - zmin) + 2, 4))
    for x, y, z, w, h, d, (fx, fy, fw, fh) in sorted(cubes, key=lambda c: c[0] + c[3]):
        tex = img[fy:fy + fh, fx:fx + fw]
        if tex.size == 0:
            continue
        oy, oz = int(y - ymin), int(z - zmin)
        patch = out[oy:oy + tex.shape[0], oz:oz + tex.shape[1]]
        t = tex[:patch.shape[0], :patch.shape[1]]
        mask = t[..., 3] > 0
        patch[mask] = t[mask]
    im = Image.fromarray(out.astype(np.uint8), "RGBA")
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


# ============================================================================= items, sounds, text

def icon(draw_fn, seed):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im))
    a = np.asarray(im).astype(float)
    rgb, alpha = a[..., :3].copy(), (a[..., 3] > 127).astype(float)
    rgb *= (0.88 + 0.24 * N.spectral(32, 32, beta=1.6, seed=seed))[..., None]
    rgb = np.clip(rgb, 0, 255)
    _outline(rgb, alpha)
    return rgb, alpha


def fuel_can(d):
    d.rounded_rectangle([7, 9, 24, 27], radius=2, fill="#a8322a")
    d.rectangle([9, 5, 14, 9], fill="#8a2a22")
    d.polygon([(19, 9), (25, 4), (27, 6), (22, 10)], fill="#2a2a2a")
    d.line([(10, 13), (21, 24)], fill="#7a221c", width=2)
    d.line([(21, 13), (10, 24)], fill="#7a221c", width=2)


def wrench(d):
    d.line([(9, 24), (22, 11)], fill="#9a9ea2", width=3)
    d.ellipse([18, 5, 27, 14], fill="#9a9ea2")
    d.polygon([(23, 4), (28, 4), (28, 9)], fill=(0, 0, 0, 0))
    d.ellipse([5, 21, 12, 28], fill="#9a9ea2")
    d.ellipse([7, 23, 10, 26], fill=(0, 0, 0, 0))


def engine_parts(d):
    d.rectangle([7, 13, 24, 24], fill="#5a5e62")
    d.rectangle([9, 9, 13, 13], fill="#7a7e82")
    d.rectangle([17, 9, 21, 13], fill="#7a7e82")
    d.ellipse([10, 15, 16, 21], fill="#2a2c2e")
    d.rectangle([19, 16, 22, 22], fill="#b06a2a")


def tire(d):
    d.ellipse([4, 4, 27, 27], fill="#1c1c1e")
    d.ellipse([10, 10, 21, 21], fill="#8a8c8e")
    d.ellipse([13, 13, 18, 18], fill="#3a3c3e")


def battery(d):
    d.rectangle([6, 12, 25, 25], fill="#2a2e34")
    d.rectangle([6, 12, 25, 15], fill="#b02a22")
    d.rectangle([9, 8, 12, 12], fill="#c8c8c8")
    d.rectangle([19, 8, 22, 12], fill="#c8c8c8")
    d.line([(14, 20), (17, 20)], fill="#e8e0b0", width=1)


def panel(d):
    d.polygon([(6, 10), (25, 7), (26, 23), (7, 26)], fill="#7a8a98")
    d.line([(9, 13), (23, 11)], fill="#9aaab8", width=1)
    d.ellipse([19, 17, 22, 20], fill="#4a5a68")


def tow_rope(d):
    d.ellipse([4, 5, 25, 25], outline="#8a6a3a", width=3)
    d.ellipse([9, 10, 20, 20], outline="#6a4e2a", width=2)
    d.line([(21, 21), (26, 27)], fill="#8a6a3a", width=3)
    d.rectangle([24, 25, 29, 29], fill="#7a7e82")
    d.rectangle([26, 27, 27, 28], fill=(0, 0, 0, 0))


ITEMS = {
    "tow_rope": (tow_rope, 6107, "Tow Rope", "Use it on the vehicle that will pull, then on the one to be towed."),
    "fuel_can": (fuel_can, 6101, "Fuel Can", "Fill it at a pump that still has fuel. Use it on a vehicle to pour."),
    "wrench": (wrench, 6102, "Wrench", "Have one with you to fit parts to a vehicle."),
    "engine_parts": (engine_parts, 6103, "Engine Parts", "Each brings an engine back a quarter of the way."),
    "tire": (tire, 6104, "Tire", "Four make a full set."),
    "car_battery": (battery, 6105, "Car Battery", "Nothing starts without one."),
    "body_panel": (panel, 6106, "Body Panel", "Patches up bodywork."),
}

TEXT = {
    "vehicle.deadlands.needs": "This %s needs: %s.",
    "vehicle.deadlands.need.engine": "engine parts x%s",
    "vehicle.deadlands.need.tires": "tires x%s",
    "vehicle.deadlands.need.battery": "a battery",
    "vehicle.deadlands.need.body": "a body panel",
    "vehicle.deadlands.need.fuel": "fuel",
    "vehicle.deadlands.need.burned": "It is burned out. Nothing will bring it back.",
    "vehicle.deadlands.need.overgrown": "the growth pulled off it",
    "vehicle.deadlands.cleared": "You pull the growth off the %s.",
    "vehicle.deadlands.no_battery": "Nothing happens. There is no battery.",
    "vehicle.deadlands.tow.tied": "Rope tied to the %s. Now use it on the vehicle to be towed.",
    "vehicle.deadlands.tow.hitched": "The %s is on a rope behind the %s.",
    "vehicle.deadlands.tow.released": "You untie the rope.",
    "vehicle.deadlands.tow.cancelled": "You take the rope back off.",
    "vehicle.deadlands.tow.busy": "That one is already on a rope.",
    "vehicle.deadlands.tow.too_far": "They are too far apart for the rope.",
    "hud.deadlands.vehicle.lights": "LIGHTS",
    "hud.deadlands.vehicle.towing": "TOWING",
    "key.deadlands.horn": "Vehicle horn",
    "key.deadlands.headlights": "Vehicle headlights",
    "subtitles.deadlands.vehicle.horn": "Horn blares",
    "vehicle.deadlands.need_wrench": "You need a wrench to fit that.",
    "vehicle.deadlands.not_needed": "It does not need that.",
    "vehicle.deadlands.can_empty": "The fuel can is empty.",
    "vehicle.deadlands.tank_full": "The tank is full.",
    "vehicle.deadlands.fuelled": "Fuel: %s / %s litres",
    "vehicle.deadlands.no_fuel": "The tank is dry.",
    "vehicle.deadlands.runs": "The %s turns over and runs.",
    "vehicle.deadlands.runs_no_fuel": "The %s would run, with fuel.",
    "vehicle.deadlands.storage": "%s: load space",
    "hud.deadlands.vehicle.fuel": "Fuel",
    "hud.deadlands.vehicle.engine": "Engine",
    "hud.deadlands.vehicle.tires": "Tires",
    "hud.deadlands.vehicle.body": "Body",
    "item.deadlands.fuel_can.litres": "%s / %s litres",
    "message.deadlands.pump.filled": "Took %s litres. The pump has %s left.",
    "message.deadlands.pump.can_full": "The fuel can is full.",
    "message.deadlands.pump.dry": "The pump is dry.",
    "message.deadlands.pump.gauge": "The gauge shows about %s litres.",
    "config.deadlands.world.abandonedVehicles": "Abandoned vehicles",
    "command.deadlands.vehicle": "Placed a %s.",
    "command.deadlands.vehicle.unknown": "There is no vehicle called %s.",
    "itemGroup.deadlands.vehicles": "DEADLANDS: Vehicles",
    "subtitles.deadlands.vehicle.engine": "Engine runs",
    "subtitles.deadlands.vehicle.impact": "Metal crunches",
    "subtitles.deadlands.vehicle.repair": "Ratchet clicks",
}


def sounds() -> None:
    from gen_audio import OUT, SR, write_ogg
    r = np.random.default_rng(6201)
    t = np.arange(int(SR * 1.5)) / SR
    # Engine: a firing pulse train over a rumble. Every part repeats a whole number of times in the
    # clip (38, 76 and 20 Hz and a 2 Hz wobble in 1.5 s; the rumble is built from a spectrum, so it is
    # periodic too), which makes the loop seamless.
    pulse = np.sign(np.sin(2 * np.pi * 38 * t)) * 0.4 + np.sin(2 * np.pi * 76 * t) * 0.3 + np.sin(2 * np.pi * 20 * t) * 0.3
    spectrum = np.fft.rfft(r.standard_normal(t.size))
    freqs = np.fft.rfftfreq(t.size, 1.0 / SR)
    spectrum[freqs > 400.0] = 0.0
    rumble = np.fft.irfft(spectrum, n=t.size)
    rumble = rumble / np.max(np.abs(rumble)) * 0.5
    engine = np.tanh((pulse + rumble) * 1.5) * (0.85 + 0.15 * np.sin(2 * np.pi * 2 * t))
    engine = engine / np.max(np.abs(engine)) * 0.7
    t2 = np.arange(int(SR * 0.7)) / SR
    impact = r.standard_normal(t2.size) * np.exp(-t2 * 14.0) + np.sin(2 * np.pi * 90 * t2) * np.exp(-t2 * 10.0)
    impact += sum(np.sin(2 * np.pi * f * t2) * np.exp(-t2 * 9.0) * 0.2 for f in (410, 730, 1190))
    impact = np.tanh(impact * 1.5)
    impact = impact / np.max(np.abs(impact)) * 0.85
    t3 = np.arange(int(SR * 0.8)) / SR
    repair = np.zeros(t3.size)
    for at in np.arange(0.05, 0.7, 0.09):
        k = np.clip(t3 - at, 0.0, None)
        repair += (t3 >= at) * (r.standard_normal(t3.size) * np.exp(-k * 220.0) * 0.5 + np.sin(2 * np.pi * 3100 * k) * np.exp(-k * 160.0) * 0.4)
    repair = repair / np.max(np.abs(repair)) * 0.6
    t4 = np.arange(int(SR * 0.65)) / SR
    env = np.minimum(1.0, t4 / 0.012) * np.minimum(1.0, (t4[-1] - t4) / 0.09)
    horn = np.tanh(3.0 * (np.sin(2 * np.pi * 415 * t4) + 0.8 * np.sin(2 * np.pi * 523 * t4))) * env
    horn = horn / np.max(np.abs(horn)) * 0.6
    path = OUT.parent / "sounds.json"
    table = json.loads(path.read_text(encoding="utf-8"))
    for event, rel, data in (("vehicle.engine", "vehicle/engine", engine), ("vehicle.impact", "vehicle/impact", impact),
                             ("vehicle.repair", "vehicle/repair", repair), ("vehicle.horn", "vehicle/horn", horn)):
        write_ogg(rel, data, quality=4)
        table[event] = {"sounds": [f"{NS}:{rel}"], "subtitle": f"subtitles.{NS}.{event}"}
    path.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


def build() -> int:
    rigs = {kind: rig(kind) for kind in KINDS}
    write_geometry(rigs)
    lang = dict(TEXT)
    previews = []
    for kind, parts in rigs.items():
        for finish in FINISHES:
            img = paint(kind, parts, finish)
            Image.fromarray(img.astype(np.uint8), "RGBA").save(N.asset(f"textures/entity/vehicle/{kind}_{finish}.png"))
            if finish in ("faded", "burned"):
                previews.append(side_view(parts, img))
        for name, layer in (("damage_1", overlay_damage(kind, parts, 1)), ("damage_2", overlay_damage(kind, parts, 2)),
                            ("overgrown", overlay_overgrown(kind, parts)), ("lights", overlay_lights(kind, parts))):
            Image.fromarray(np.clip(layer, 0, 255).astype(np.uint8), "RGBA").save(N.asset(f"textures/entity/vehicle/{kind}_{name}.png"))
        lang[f"entity.{NS}.{kind}"] = NAMES[kind]
    for name, (painter, seed, english, desc) in ITEMS.items():
        N.save_png(N.asset(f"textures/item/{name}.png"), *icon(painter, seed))
        D.asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        D.item_model_definition(name, f"{NS}:item/{name}")
        lang[f"item.{NS}.{name}"] = english
        lang[f"item.{NS}.{name}.desc"] = desc
    D.data_json("tags/block/road_surface.json", {"replace": False, "values": [
        f"{NS}:asphalt", f"{NS}:cracked_asphalt", f"{NS}:asphalt_yellow_line", f"{NS}:asphalt_white_line", f"{NS}:asphalt_slab",
        f"{NS}:cracked_asphalt_slab", f"{NS}:sidewalk", f"{NS}:sidewalk_slab", f"{NS}:concrete", f"{NS}:cracked_concrete", f"{NS}:concrete_slab",
        "minecraft:dirt_path", "minecraft:gravel"]})
    D.data_json("recipe/fuel_can.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["I I", "I I", "III"],
                                        "key": {"I": "minecraft:iron_ingot"}, "result": {"id": f"{NS}:fuel_can", "count": 1}})
    D.data_json("recipe/wrench.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" I", "I "],
                                      "key": {"I": "minecraft:iron_ingot"}, "result": {"id": f"{NS}:wrench", "count": 1}})
    D.data_json("recipe/tow_rope.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["SSS", "S S", "SSI"],
                                       "key": {"S": "minecraft:string", "I": "minecraft:iron_ingot"}, "result": {"id": f"{NS}:tow_rope", "count": 1}})
    D.data_json("recipe/body_panel.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["II", "II"],
                                          "key": {"I": "minecraft:iron_ingot"}, "result": {"id": f"{NS}:body_panel", "count": 2}})
    sounds()
    D.merge_lang(lang)
    sheet = Image.new("RGBA", (max(p.width for p in previews) * 2 + 20, sum(p.height + 8 for p in previews[::2]) + 8), (40, 40, 44, 255))
    y = 8
    for a, b in zip(previews[::2], previews[1::2]):
        sheet.alpha_composite(a, (8, y))
        sheet.alpha_composite(b, (sheet.width // 2 + 4, y))
        y += a.height + 8
    (N.ROOT / "tools" / "assetgen" / "_cache").mkdir(exist_ok=True)
    sheet.save(N.ROOT / "tools" / "assetgen" / "_cache" / "vehicles_preview.png")
    return len(rigs)


if __name__ == "__main__":
    print(build(), "vehicles written")
