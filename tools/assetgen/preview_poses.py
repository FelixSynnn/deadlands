"""Offline check of the infected poses: parses the generated rigs, applies the pose maths of
InfectedModel (transcribed), and draws side and front views with the ground line and hitbox."""
import math
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

src = open(sys.argv[1], encoding="utf-8").read()
PI = math.pi


def parse(method):
    body = src[src.index(f"public static LayerDefinition {method}()"):]
    body = body[:body.index("return LayerDefinition")]
    parts = {}
    for m in re.finditer(r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("(\w+)", CubeListBuilder\.create\(\)(.*?), PartPose\.offsetAndRotation\(([^)]*)\)\);', body):
        var, parent, name, cubes, pose = m.groups()
        boxes = [tuple(float(v.rstrip("F")) for v in c.split(",")) for c in re.findall(r'addBox\(([^)]*)\)', cubes)]
        px, py, pz = [float(v.strip().rstrip("F")) for v in pose.split(",")[:3]]
        parts[name] = dict(parent=None if parent == "root" else parent, boxes=boxes, pivot=[px, py, pz], rot=[0.0, 0.0, 0.0],
                           base=[px, py, pz], visible=True)
    return parts


def rot_matrix(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X  # Minecraft: rotationZYX


def world(parts, name):
    """4x4 transform of a part in model space (pixels, y down)."""
    p = parts[name]
    M = np.eye(4)
    M[:3, :3] = rot_matrix(*p["rot"])
    M[:3, 3] = p["pivot"]
    return M if p["parent"] is None else world(parts, p["parent"]) @ M


def hidden(parts, name):
    while name is not None:
        if not parts[name]["visible"]:
            return True
        name = parts[name]["parent"]
    return False


FACES = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
COLOURS = {"head": (190, 170, 140), "jaw": (150, 110, 100), "torso": (90, 110, 150), "chest": (100, 125, 170), "arm": (170, 150, 120),
           "forearm": (190, 165, 130), "leg": (70, 80, 110), "shin": (90, 100, 130), "claws": (220, 220, 200)}


def colour(name):
    for key in ("forearm", "arm", "shin", "leg", "claws", "jaw", "head", "chest", "torso"):
        if key in name:
            return COLOURS[key]
    return (120, 160, 120)


def draw(parts, scale_xyz, hitbox, title, size=150, ppb=44):
    """Side view (looking from the entity's right, front to the left) and front view."""
    polys = {"side": [], "front": []}
    for name, p in parts.items():
        if hidden(parts, name) or not p["boxes"]:
            continue
        M = world(parts, name)
        for (x, y, z, w, h, d) in p["boxes"]:
            corners = []
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, d):
                        v = M @ np.array([x + dx, y + dy, z + dz, 1.0])
                        # model -> blocks: renderer scales, flips y (24 px is the ground)
                        corners.append(((v[0] / 16) * scale_xyz[0], (24 - v[1]) / 16 * scale_xyz[1], (v[2] / 16) * scale_xyz[2]))
            c = colour(name)
            for f in FACES:
                pts = [corners[i] for i in f]
                cx = sum(q[0] for q in pts) / 4
                cz = sum(q[2] for q in pts) / 4
                shade = 0.75 + 0.25 * ((f[0] * 7 + f[1]) % 3) / 2
                col = tuple(int(v * shade) for v in c)
                polys["side"].append((cx, [(q[2], q[1]) for q in pts], col))    # depth by x
                polys["front"].append((-cz, [(q[0], q[1]) for q in pts], col))  # front faces -z
    out = Image.new("RGB", (size * 2, size + 14), (24, 24, 28))
    d = ImageDraw.Draw(out)
    for i, view in enumerate(("side", "front")):
        ox, oy = i * size + size // 2, size - 6
        d.line([(i * size, oy + 14), (i * size + size, oy + 14)], fill=(110, 90, 60))
        hw = hitbox[0] / 2 * ppb
        d.rectangle([ox - hw, oy + 14 - hitbox[1] * ppb, ox + hw, oy + 14], outline=(70, 70, 120))
        for _, pts, col in sorted(polys[view], key=lambda t: t[0]):
            d.polygon([(ox + px * ppb, oy + 14 - py * ppb) for px, py in pts], fill=col, outline=(20, 20, 20))
    d.text((3, 1), title, fill=(255, 255, 255))
    return out


def cos(x):
    return math.cos(x)


def sin(x):
    return math.sin(x)


def pose(parts, kind, walk, speed, age=20.0):
    P = {k: v for k, v in parts.items()}
    for v in P.values():
        v["rot"] = [0.0, 0.0, 0.0]
        v["pivot"] = list(v["base"])
    R = lambda n: P[n]["rot"]
    f = walk * (0.6662 if kind == "runner" else 0.45 if kind in ("brute", "bloater") else 0.6 if kind == "stalker" else 0.55)
    if kind in ("walker", "screamer", "military", "charred"):
        knee = 0.5 if kind == "charred" else 1.1
        R("right_leg")[0] = cos(f) * speed
        R("left_leg")[0] = cos(f + PI) * speed
        R("right_shin")[0] = 0.05 + max(0, -sin(f)) * knee * speed
        R("left_shin")[0] = 0.05 + max(0, sin(f)) * knee * speed
        P["body"]["pivot"][1] += abs(sin(f)) * 0.9 * speed
        R("torso")[0] = 0.12 + 0.06 * speed
        R("head")[0] = 0.1
        R("right_arm")[0] = -1.3 + cos(f) * 0.15 * speed
        R("right_arm")[2] = 0.08
        R("right_forearm")[0] = -0.25
        R("left_arm")[0] = -0.15 + cos(f) * 0.35 * speed
        R("left_arm")[2] = -0.12
        R("left_forearm")[0] = -0.15 - 0.2 * speed
    elif kind == "runner":
        sprint = max(0.0, min(1.0, speed * 1.6 - 0.3))
        R("right_leg")[0] = cos(f) * 1.3 * speed
        R("left_leg")[0] = cos(f + PI) * 1.3 * speed
        R("right_shin")[0] = 0.1 + max(0, -sin(f)) * 1.4 * speed
        R("left_shin")[0] = 0.1 + max(0, sin(f)) * 1.4 * speed
        P["body"]["pivot"][1] += abs(cos(f)) * 1.2 * speed
        R("torso")[0] = 0.12 + 0.25 * sprint
        R("head")[0] = 0.05 - 0.25 * sprint
        R("right_arm")[0] = cos(f + PI) * 1.1 * speed - 0.3 * sprint - 0.6 * (1 - sprint)
        R("left_arm")[0] = cos(f) * 1.1 * speed - 0.3 * sprint - 0.5 * (1 - sprint)
        R("right_forearm")[0] = R("left_forearm")[0] = -0.4 - 0.7 * sprint
    elif kind == "brute":
        R("right_leg")[0] = cos(f) * 0.7 * speed
        R("left_leg")[0] = cos(f + PI) * 0.7 * speed
        R("right_shin")[0] = 0.08 + max(0, -sin(f)) * 0.6 * speed
        R("left_shin")[0] = 0.08 + max(0, sin(f)) * 0.6 * speed
        R("right_leg")[2], R("left_leg")[2] = 0.08, -0.08
        P["body"]["pivot"][1] += abs(sin(f)) * 1.2 * speed
        R("torso")[0] = 0.2
        R("head")[0] = -0.15
        R("right_arm")[0] = 0.05 + cos(f + PI) * 0.45 * speed
        R("left_arm")[0] = 0.05 + cos(f) * 0.45 * speed
        R("right_arm")[2], R("left_arm")[2] = 0.15, -0.15
        R("right_forearm")[0], R("left_forearm")[0] = -0.25, -0.3
    elif kind == "crawler":
        f = walk * 0.5 + age * 0.01
        R("body")[0] = PI / 2
        P["body"]["pivot"][1] = 21.0
        P["body"]["pivot"][2] = 5.0
        pull = max(speed, 0.15)
        R("right_arm")[0] = PI + 0.7 - 0.5 * sin(f) * pull
        R("left_arm")[0] = PI + 0.7 - 0.5 * sin(f + PI) * pull
        R("right_arm")[2], R("left_arm")[2] = -0.25, 0.25
        R("right_forearm")[0] = -0.6 - 0.3 * max(0, cos(f)) * pull
        R("left_forearm")[0] = -0.6 - 0.3 * max(0, cos(f + PI)) * pull
        R("right_leg")[2], R("left_leg")[2] = 0.12, -0.18
        R("right_shin")[0], R("left_shin")[0] = 0.15, 0.35
        R("head")[0] = -1.15
    elif kind == "stalker":
        P["body"]["pivot"][1] = 15.0
        R("right_leg")[0] = -0.75 + cos(f) * 0.6 * speed
        R("left_leg")[0] = -0.75 + cos(f + PI) * 0.6 * speed
        R("right_shin")[0] = 1.3 + max(0, -sin(f)) * 0.5 * speed
        R("left_shin")[0] = 1.3 + max(0, sin(f)) * 0.5 * speed
        R("torso")[0] = 0.55
        R("chest")[0] = 0.2
        R("head")[0] = -0.75
        R("right_arm")[0] = -1.0 + cos(f + PI) * 0.4 * speed
        R("left_arm")[0] = -1.0 + cos(f) * 0.4 * speed
        R("right_forearm")[0] = R("left_forearm")[0] = -0.6
    elif kind == "bloater":
        R("right_leg")[0] = cos(f) * 0.6 * speed
        R("left_leg")[0] = cos(f + PI) * 0.6 * speed
        R("right_shin")[0] = 0.05 + max(0, -sin(f)) * 0.5 * speed
        R("left_shin")[0] = 0.05 + max(0, sin(f)) * 0.5 * speed
        R("torso")[0] = -0.08
        R("head")[0] = 0.15
        R("right_arm")[0] = R("left_arm")[0] = -0.2
        R("right_arm")[2], R("left_arm")[2] = 0.45, -0.45
        R("right_forearm")[0] = R("left_forearm")[0] = -0.3
    R("jaw")[0] += 0.2


OPTIONAL = {"walker": [], "runner": [], "screamer": [], "military": ["helmet", "vest", "belt", "backpack"], "crawler": ["front_tatter", "back_tatter"],
            "bloater": ["belly", "growths"], "stalker": ["right_claws", "left_claws"], "charred": ["growths"], "brute": ["helmet"]}
ALL_OPTIONAL = ["helmet", "vest", "belt", "mask", "cap", "brim", "right_claws", "left_claws", "belly", "growths", "front_tatter", "back_tatter", "backpack"]
# kind -> (width factor, height scale, hitbox w, hitbox h) as InfectedRenderer / InfectedKind have them
KINDS = {"walker": (1.0, 1.95 / 2.0, 0.6, 1.95), "runner": (0.94, 1.9 / 2.0, 0.6, 1.9), "screamer": (0.88, 2.1 / 2.0, 0.6, 2.1),
         "military": (1.0, 1.95 / 2.0, 0.6, 1.95), "crawler": (1.0, 0.95, 0.9, 0.7), "bloater": (1.12, 1.0, 0.9, 2.0),
         "stalker": (0.9, 1.0, 0.6, 1.8), "charred": (1.0, 1.95 / 2.0, 0.6, 1.95), "brute": (1.0, 2.7 / (38 / 16), 1.2, 2.7)}

rows = []
for kind, (wf, hs, bw, bh) in KINDS.items():
    parts = parse("brute" if kind == "brute" else "humanoid")
    for name in ALL_OPTIONAL:
        if name in parts:
            parts[name]["visible"] = name in OPTIONAL[kind]
    frames = []
    for label, walk, speed in (("idle", 0.0, 0.0), ("walk a", 1.2, 1.0), ("walk b", 4.2, 1.0)):
        pose(parts, kind, walk, speed)
        base = hs if kind in ("crawler", "stalker") else hs
        frames.append(draw(parts, (base * wf, hs, base * wf), (bw, bh), f"{kind} {label}"))
    row = Image.new("RGB", (sum(f.width for f in frames), frames[0].height))
    x = 0
    for fr in frames:
        row.paste(fr, (x, 0))
        x += fr.width
    rows.append(row)
sheet = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height
sheet.save(sys.argv[2])
print(sheet.size)
