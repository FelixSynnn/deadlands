"""The Guest's 3D model (Phase 5, an original DEADLANDS design).

The thing that walked out of Kessler's incinerator: a hunched, skinless red giant, about 3.2 blocks with
the renderer's scale, arms long enough to drag its knuckles, a small eyeless head whose jaw splits too
wide, bone spurs down the spine, charred patches, and orange cracks where the heat shows through.

Writes src/client/java/com/deadlands/client/render/GuestGeometry.java (the rig) and paints its texture:
textures/entity/guest/guest.png (256 x 256) and the cracks' glow, guest_glow.png. Model space as the
other rigs: y down, the ground at y = 24 (the root), the front facing -Z.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import dl_noise as N
from gen_asesino_model import faces, footprint

SIZE = 256
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "GuestGeometry.java"

# (name, parent, pivot relative to the parent, [(x, y, z, w, h, d, material)])
PARTS = [
    ("pelvis", "root", (0, -20, 0), [(-7, -3, -4.5, 14, 5, 9, "muscle"), (-6, 1, -4, 12, 2, 8, "char")]),
    ("torso", "pelvis", (0, -3, 0), [(-9, -16, -6, 18, 16, 12, "chest"), (-8, -20, -3, 16, 5, 9, "muscle"),
                                     (-1, -19, 5, 2, 2, 3, "bone"), (-1, -15, 5.5, 2, 2, 3, "bone"),
                                     (-1, -11, 5.5, 2, 2, 3, "bone"), (-1, -7, 5, 2, 2, 2, "bone"),
                                     (-5, -6, -6.5, 10, 6, 1, "ribs")]),
    ("neck", "torso", (0, -16, -3), [(-3, -3, -3, 6, 4, 6, "muscle")]),
    ("head", "neck", (0, -2, -2), [(-4, -6, -6, 8, 7, 8, "skull"), (-4.5, -7, -2, 9, 2, 5, "bone")]),
    ("jaw", "head", (0, 0, -1), [(-3.5, 0, -5.5, 7, 3, 6, "jaw")]),
    ("arm_r", "torso", (-9, -14, 0), [(-6, -2, -3.5, 6, 5, 7, "char"), (-5, 0, -2.5, 5, 13, 5, "arm")]),
    ("forearm_r", "arm_r", (-2.5, 13, 0), [(-2.5, 0, -2.5, 5, 13, 5, "arm")]),
    ("hand_r", "forearm_r", (0, 13, 0), [(-3, 0, -3, 6, 4, 6, "muscle"), (-3, 4, -3, 6, 3, 2, "claw")]),
    ("arm_l", "torso", (9, -14, 0), [(0, -2, -3.5, 6, 5, 7, "char"), (0, 0, -2.5, 5, 13, 5, "arm")]),
    ("forearm_l", "arm_l", (2.5, 13, 0), [(-2.5, 0, -2.5, 5, 13, 5, "arm")]),
    ("hand_l", "forearm_l", (0, 13, 0), [(-3, 0, -3, 6, 4, 6, "muscle"), (-3, 4, -3, 6, 3, 2, "claw")]),
    ("leg_r", "pelvis", (-4.5, 2, 0), [(-3, 0, -3, 6, 9, 6, "muscle")]),
    ("shin_r", "leg_r", (0, 9, 0), [(-2.5, 0, -2.5, 5, 7, 5, "muscle"), (-3, 7, -5, 6, 2, 8, "char")]),
    ("leg_l", "pelvis", (4.5, 2, 0), [(-3, 0, -3, 6, 9, 6, "muscle")]),
    ("shin_l", "leg_l", (0, 9, 0), [(-2.5, 0, -2.5, 5, 7, 5, "muscle"), (-3, 7, -5, 6, 2, 8, "char")]),
]


def pack():
    boxes = []
    for p, (_, _, _, cubes) in enumerate(PARTS):
        for c, cube in enumerate(cubes):
            fw, fh = footprint(*cube[3:6])
            boxes.append((fh, fw, p, c))
    boxes.sort(reverse=True)
    uv, x, y, row = {}, 0, 0, 0
    for fh, fw, p, c in boxes:
        if x + fw > SIZE:
            x, y, row = 0, y + row, 0
        uv[(p, c)] = (x, y)
        x += fw
        row = max(row, fh)
    assert y + row <= SIZE, "the Guest's texture is full"
    return uv


def paint(img, glow, rng, material, face, rect):
    x, y, w, h = rect
    if w <= 0 or h <= 0:
        return
    yy, xx = np.mgrid[0:h, 0:w]
    grain = rng.random((h, w))
    if material in ("muscle", "chest", "arm"):
        # Raw muscle: dark red with vertical fibres, lighter sinew, purple veins.
        fibre = (np.sin(xx * 1.7 + rng.random() * 6) * 0.5 + 0.5)
        col = np.stack([110 + 60 * fibre + 25 * grain, 18 + 14 * fibre + 10 * grain, 16 + 10 * fibre + 8 * grain], axis=-1)
        sinew = (xx + (yy // 3)) % 9 == 0
        col[sinew] = [196, 120, 102]
        veins = (np.abs(np.sin(yy * 0.6 + np.sin(xx * 0.9) * 2.0)) < 0.08) & (grain < 0.6)
        col[veins] = [70, 16, 44]
        char = rng.random((h, w)) < 0.06
        col[char] = col[char] * 0.25
        if face in ("front", "back") and material in ("chest", "arm"):
            # Cracks where the heat shows: a jagged line or two, glowing.
            for _ in range(2 if material == "chest" else 1):
                cx = rng.integers(1, max(2, w - 1))
                for cy in range(h):
                    cx = int(np.clip(cx + rng.integers(-1, 2), 0, w - 1))
                    col[cy, cx] = [255, 150, 40]
                    glow[y + cy, x + cx] = (255, 140, 30, 255)
                    if cx + 1 < w:
                        glow[y + cy, x + cx + 1] = (140, 50, 10, 110)
    elif material == "char":
        col = np.stack([30 + 30 * grain, 14 + 10 * grain, 12 + 8 * grain], axis=-1)
        ember = rng.random((h, w)) < 0.05
        col[ember] = [200, 80, 20]
        for py, px in zip(*np.nonzero(ember)):
            glow[y + py, x + px] = (230, 100, 20, 200)
    elif material in ("bone", "claw", "ribs"):
        base = np.array([214, 196, 160]) if material != "claw" else np.array([120, 100, 80])
        col = base * (0.75 + 0.3 * grain)[..., None]
        col[(xx * 3 + yy * 5) % 11 == 0] *= 0.6
        if material == "ribs":
            muscle = (yy % 2 == 1)
            col[muscle] = [120, 22, 18]
    elif material == "skull":
        col = np.stack([118 + 40 * grain, 26 + 12 * grain, 22 + 8 * grain], axis=-1)
        if face == "front":
            col[:, :] = col * 0.9
            col[h // 2:, 1:w - 1] = [40, 8, 8]          # where a face should be: a smooth, eyeless scar
            col[h // 2, 1:w - 1] = [150, 60, 50]
    elif material == "jaw":
        col = np.stack([110 + 40 * grain, 22 + 10 * grain, 18 + 8 * grain], axis=-1)
        if face in ("front", "top", "left", "right"):
            teeth = (xx % 2 == 0) & (yy == 0)
            col[teeth] = [230, 218, 186]
            col[(yy == 0) & (xx % 2 == 1)] = [30, 6, 6]
        if face == "top":
            col[1:, 1:w - 1] = [70, 10, 14]             # the inside of the mouth
            glow[y + 1:y + h, x + 1:x + w - 1] = (120, 40, 10, 90)
    else:
        col = np.full((h, w, 3), 128.0)
    if face in ("bottom", "back"):
        col = col * 0.82
    img[y:y + h, x:x + w, :3] = np.clip(col, 0, 255)
    img[y:y + h, x:x + w, 3] = 255


def java(uv) -> str:
    lines = ["package com.deadlands.client.render;", "",
             "import com.deadlands.Deadlands;",
             "import net.minecraft.client.model.geom.ModelLayerLocation;",
             "import net.minecraft.client.model.geom.PartPose;",
             "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
             "import net.minecraft.client.model.geom.builders.LayerDefinition;",
             "import net.minecraft.client.model.geom.builders.MeshDefinition;",
             "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
             "/**",
             " * GENERATED by tools/assetgen/gen_guest_model.py - do not edit; change the rig there and rerun it.",
             " *",
             " * <p>The Guest's rig (an original design): y down, the ground at y = 24, the front facing -Z.",
             " */",
             "public final class GuestGeometry {",
             "\tpublic static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id(\"guest\"), \"main\");", "",
             "\tprivate GuestGeometry() {", "\t}", "",
             "\tpublic static LayerDefinition create() {",
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot().addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));"]
    for p, (name, parent, pivot, cubes) in enumerate(PARTS):
        builder = "CubeListBuilder.create()"
        for c, (x, y, z, w, h, d, _) in enumerate(cubes):
            u, v = uv[(p, c)]
            builder += f".texOffs({u}, {v}).addBox({x:.1f}F, {y:.1f}F, {z:.1f}F, {w:.1f}F, {h:.1f}F, {d:.1f}F)"
        px, py, pz = pivot
        lines.append(f"\t\tPartDefinition {name} = {parent}.addOrReplaceChild(\"{name}\", {builder}, PartPose.offset({px:.1f}F, {py:.1f}F, {pz:.1f}F));")
    lines += [f"\t\treturn LayerDefinition.create(mesh, {SIZE}, {SIZE});", "\t}", "}", ""]
    return "\n".join(lines)


def build() -> None:
    uv = pack()
    rng = np.random.default_rng(666)
    img = np.zeros((SIZE, SIZE, 4), dtype=float)
    glow = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    for p, (_, _, _, cubes) in enumerate(PARTS):
        for c, (_, _, _, w, h, d, material) in enumerate(cubes):
            u, v = uv[(p, c)]
            for face, rect in faces(u, v, w, h, d).items():
                paint(img, glow, rng, material, face, rect)
    out = N.asset("textures/entity/guest/guest.png")
    from pathlib import Path
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img.astype(np.uint8), "RGBA").save(out)
    Image.fromarray(glow, "RGBA").save(N.asset("textures/entity/guest/guest_glow.png"))
    JAVA.write_text(java(uv), encoding="utf-8")
    print(f"  guest model: {sum(len(p[3]) for p in PARTS)} boxes in {len(PARTS)} parts")


if __name__ == "__main__":
    build()
