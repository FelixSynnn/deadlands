"""The Asesino's own 3D model (Phase 5, an original DEADLANDS design).

A tall, lank figure, about 2.3 blocks: a hood over a cracked bone-white mask with two eye holes, a
long stained work coat with a raised collar and split tails, a leather harness and belt with a pouch,
black gloves, worn trousers and heavy boots, and a long machete with a taped grip. Jointed for
animation: pelvis, torso, neck, head; shoulder, elbow and hand on each arm; hip and knee on each leg;
the coat tails hang from the pelvis.

Writes src/client/java/com/deadlands/client/render/AsesinoGeometry.java (the rig) and paints the
texture to match its box UVs: textures/entity/asesino/asesino_model.png (128 x 128), plus the eyes'
glow, asesino_model_eyes.png. Model space as the other rigs: y down, the ground at y = 24 (the root),
the front facing -Z.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import dl_noise as N

SIZE = 128
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "AsesinoGeometry.java"

# (name, parent, pivot (x, y, z) relative to the parent, [(x, y, z, w, h, d, material), ...])
PARTS = [
    ("pelvis", "root", (0, -13, 0), [(-4.5, -2, -2.5, 9, 3, 5, "trousers"), (-5, -2, -3, 10, 1, 6, "belt"), (2.5, -1, -4, 2, 2, 1, "leather")]),
    ("torso", "pelvis", (0, -2, 0), [(-5, -12, -3, 10, 12, 6, "coat"), (-4, -13, -3.5, 8, 2, 7, "collar"),
                                     (-4, -11, -4, 1, 10, 1, "strap"), (3, -11, -4, 1, 10, 1, "strap"), (-3, -7, -4, 6, 1, 1, "strap"),
                                     (-4, -9, 3, 8, 1, 1, "strap"), (-1, -7.5, -4.5, 2, 2, 1, "buckle")]),
    ("neck", "torso", (0, -12, 0), [(-1.5, -2, -1.5, 3, 2, 3, "skin")]),
    ("head", "neck", (0, -2, 0), [(-3.5, -8, -3.5, 7, 8, 7, "skin"), (-4, -9, -4, 8, 1, 9, "hood"), (-4.5, -8.5, -4, 1, 8, 9, "hood"),
                                  (3.5, -8.5, -4, 1, 8, 9, "hood"), (-4, -8.5, 4, 8, 9, 1, "hood"), (-3.5, 0, 3, 7, 3, 1, "hood"),
                                  (-3.5, -7.5, -4.5, 7, 7, 1, "mask"), (-0.5, -5, -5.5, 1, 2, 1, "mask")]),
    ("arm_r", "torso", (-5, -11, 0), [(-3.5, -1.5, -2.5, 4, 3, 5, "collar"), (-3.5, -1, -2, 4, 7, 4, "coat")]),
    ("forearm_r", "arm_r", (-1.5, 6, 0), [(-2, 0, -2, 4, 6, 4, "sleeve")]),
    ("hand_r", "forearm_r", (0, 6, 0), [(-1.5, 0, -1.5, 3, 3, 3, "glove")]),
    ("machete", "hand_r", (0, 2, -0.5), [(-0.5, -0.5, -4, 1, 1, 5, "grip"), (-0.5, -1.5, -5, 1, 3, 1, "steel"),
                                         (-0.5, -1.5, -17, 1, 3, 12, "blade"), (-0.5, -1, -19, 1, 2, 2, "blade")]),
    ("arm_l", "torso", (5, -11, 0), [(-0.5, -1.5, -2.5, 4, 3, 5, "collar"), (-0.5, -1, -2, 4, 7, 4, "coat")]),
    ("forearm_l", "arm_l", (1.5, 6, 0), [(-2, 0, -2, 4, 6, 4, "sleeve")]),
    ("hand_l", "forearm_l", (0, 6, 0), [(-1.5, 0, -1.5, 3, 3, 3, "glove")]),
    ("coat_front_r", "pelvis", (-2.4, 1, -2.6), [(-2, 0, -0.5, 4, 8, 1, "coat")]),
    ("coat_front_l", "pelvis", (2.4, 1, -2.6), [(-2, 0, -0.5, 4, 8, 1, "coat")]),
    ("coat_back", "pelvis", (0, 1, 2.6), [(-4.5, 0, -0.5, 9, 9, 1, "coat")]),
    ("leg_r", "pelvis", (-2.4, 1, 0), [(-2, 0, -2, 4, 6, 4, "trousers")]),
    ("shin_r", "leg_r", (0, 6, 0), [(-2, 0, -2, 4, 4, 4, "trousers"), (-2, 4, -3, 4, 2, 5, "boot")]),
    ("leg_l", "pelvis", (2.4, 1, 0), [(-2, 0, -2, 4, 6, 4, "trousers")]),
    ("shin_l", "leg_l", (0, 6, 0), [(-2, 0, -2, 4, 4, 4, "trousers"), (-2, 4, -3, 4, 2, 5, "boot")]),
]

PALETTE = {
    "coat": ((74, 66, 48), (46, 40, 30)), "collar": ((58, 51, 38), (36, 31, 23)), "sleeve": ((70, 62, 45), (44, 38, 28)),
    "hood": ((40, 36, 30), (24, 21, 18)), "trousers": ((52, 54, 58), (32, 33, 36)), "belt": ((72, 46, 28), (44, 27, 16)),
    "leather": ((80, 52, 31), (48, 30, 18)), "strap": ((66, 42, 25), (40, 25, 15)), "buckle": ((150, 146, 132), (92, 88, 78)),
    "skin": ((132, 120, 108), (88, 78, 70)), "mask": ((214, 206, 186), (160, 150, 128)), "glove": ((30, 28, 27), (18, 17, 16)),
    "boot": ((48, 36, 28), (26, 20, 16)), "grip": ((26, 26, 28), (60, 58, 52)), "steel": ((120, 122, 126), (70, 72, 76)),
    "blade": ((168, 172, 176), (98, 100, 104)),
}


def footprint(w, h, d):
    return 2 * (int(np.ceil(d)) + int(np.ceil(w))), int(np.ceil(h)) + int(np.ceil(d))


def pack():
    """Shelf-pack every box's UV footprint into the texture, biggest first."""
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
    assert y + row <= SIZE, "the Asesino's texture is full"
    return uv


def faces(u, v, w, h, d):
    w, h, d = int(np.ceil(w)), int(np.ceil(h)), int(np.ceil(d))
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint_face(img, eyes, rng, material, face, rect, part):
    x, y, w, h = rect
    if w <= 0 or h <= 0:
        return
    light, dark = (np.array(c, dtype=float) for c in PALETTE[material])
    yy, xx = np.mgrid[0:h, 0:w]
    grain = rng.random((h, w))
    shade = 0.55 + 0.45 * grain if material not in ("blade", "steel", "mask") else 0.8 + 0.2 * grain
    if face in ("bottom", "back"):
        shade *= 0.8
    col = dark + (light - dark) * shade[..., None]
    if material in ("coat", "sleeve", "collar", "hood"):
        col[(xx + yy * 3) % 7 == 0] *= 0.88                                   # canvas weave
        stains = rng.random((h, w)) < 0.05
        col[stains] = col[stains] * 0.55 + np.array([70, 18, 12]) * 0.45     # old blood
        if material == "coat" and face == "front" and w >= 8:
            col[:, w // 2] *= 0.6                                             # the coat's opening
    if material == "trousers":
        col[(xx * 5 + yy) % 9 == 0] *= 0.85
    if material in ("belt", "strap", "leather", "boot"):
        col[yy == 0] *= 1.2
        col[yy == h - 1] *= 0.7
    if material == "grip" and h >= 1:
        col[(yy + xx) % 3 == 0] = PALETTE["grip"][1]                          # tape wraps
    if material == "blade":
        if face in ("front", "back", "left", "right"):
            edge = (yy == h - 1)
            col[edge] = [205, 208, 212]
            rust = rng.random((h, w)) < 0.12
            col[rust] = [112, 66, 40]
            blood = (rng.random((h, w)) < 0.18) & (yy >= h - 2)
            col[blood] = [96, 14, 10]
    if material == "mask":
        col *= 1.0
        if face == "front" and w == 7 and h == 7:
            # Two eye holes, a long crack, grime under the eyes, a smear.
            for ex in (1, 4):
                col[2:4, ex:ex + 2] = [10, 8, 8]
                eyes[y + 2:y + 4, x + ex:x + ex + 2] = (255, 30, 18, 255)
                for gy, gx in ((1, ex), (1, ex + 1), (4, ex), (4, ex + 1)):
                    if 0 <= gx < w and eyes[y + gy, x + gx, 3] == 0:
                        eyes[y + gy, x + gx] = (60, 4, 2, 70)  # a faint glow just above and below each eye
                col[4:6, ex:ex + 2] *= 0.7
            for cy, cx in ((0, 5), (1, 5), (2, 6), (4, 6), (5, 5), (6, 5)):
                col[cy, cx] = [70, 62, 52]
            col[5:7, 1:4] = col[5:7, 1:4] * 0.6 + np.array([90, 16, 10]) * 0.4
            col[5, 2:5] = [40, 34, 30]                                          # a scored mouth line
    if material == "skin":
        col *= 0.85
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
             " * GENERATED by tools/assetgen/gen_asesino_model.py - do not edit; change the rig there and rerun it.",
             " *",
             " * <p>The Asesino's rig (an original design): y down, the ground at y = 24, the front facing -Z.",
             " */",
             "public final class AsesinoGeometry {",
             "\tpublic static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id(\"asesino\"), \"main\");", "",
             "\tprivate AsesinoGeometry() {", "\t}", "",
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
    rng = np.random.default_rng(4471)
    img = np.zeros((SIZE, SIZE, 4), dtype=float)
    eyes = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    for p, (name, _, _, cubes) in enumerate(PARTS):
        for c, (_, _, _, w, h, d, material) in enumerate(cubes):
            u, v = uv[(p, c)]
            for face, rect in faces(u, v, w, h, d).items():
                paint_face(img, eyes, rng, material, face, rect, name)
    out = N.asset("textures/entity/asesino/asesino_model.png")
    Image.fromarray(img.astype(np.uint8), "RGBA").save(out)
    Image.fromarray(eyes, "RGBA").save(N.asset("textures/entity/asesino/asesino_model_eyes.png"))
    JAVA.write_text(java(uv), encoding="utf-8")
    print(f"  asesino model: {sum(len(p[3]) for p in PARTS)} boxes in {len(PARTS)} parts")


if __name__ == "__main__":
    build()
