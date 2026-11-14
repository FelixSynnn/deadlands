"""D.O.T. (Deceased On Transfer), Patient-4: his body, from the owner's plague doctor model, in D.O.T.'s own colours.

Source: ~/Downloads/plague_doctor.glb (skipped when missing, keeping what the mod has). The model is skinned to a full
rig; each triangle goes to the bone of its heaviest joint, mapped onto a simpler predator rig (pelvis, coat-skirt,
spine, chest, neck, head, hat, arms in three parts, legs and feet, and the belt cloths). The display extras are left
out: the outline shell, the pedestal, the candle flames, the gun and the potion. It is simplified to about
{TARGET} triangles.

The texture is D.O.T.'s, not the model's: every texel is painted by where it sits on the body, in D.O.T.'s palette
(deep navy above, a dark poisoned green toward the hems; teal circuit traces on the torso; sick-green hands and flame
outlines on the boots; a pale band round the hat), with the model's own texture adding only its sculpted detail. Nothing
glows: the glow layer is gone (no emissive maps; the materials are written with no glow).

Writes models/entity/dot.bin, textures/entity/dot/model/*.png and DotGeometry.java.
"""
from __future__ import annotations

import io
import struct
from pathlib import Path

import numpy as np
from PIL import Image

import dl_glb
import dl_noise as N
from import_guest import simplify

SOURCE = Path.home() / "Downloads" / "plague_doctor.glb"
TARGET = 12000
HEIGHT_PX = 44.0
TEX = 512
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "DotGeometry.java"
SKIP_MATERIALS = {"M_Outline", "M_PlagueDoctor_Gun", "M_Potion", "defaultMat1", "M_Pedestal", "M_Flame"}

# The palette (hex), as before.
NAVY, NAVY_DEEP, TEAL, POISON = "#101C3C", "#0A1126", "#103036", "#0D3A22"
CLOAK, CLOAK_TIP, STEEL, VOID = "#0B2614", "#1B5A2E", "#1A2230", "#04060A"
BAND, SICK = "#8FA899", "#2E8F4A"

# bone, parent, the joint whose bind position is its pivot
RIG = [
    ("pelvis", "root", "J_SpineBase_05"),
    ("skirt", "pelvis", "J_SpineBase_05"),
    ("spine", "pelvis", "J_Spine01_06"),
    ("chest", "spine", "J_Spine02_07"),
    ("neck", "chest", "joint1_075"),
    ("head", "neck", "J_Spine04_076"),
    ("hat", "head", "J_Spine05_Hat_077"),
    ("arm_r", "chest", "J_Shoulder_Right_037"),
    ("forearm_r", "arm_r", "J_Elbow_Right_038"),
    ("hand_r", "forearm_r", "J_Wrist_Right_039"),
    ("arm_l", "chest", "J_Shoulder_Left_010"),
    ("forearm_l", "arm_l", "J_Elbow_Left_011"),
    ("hand_l", "forearm_l", "J_Wrist_Left_012"),
    ("leg_r", "pelvis", "J_Leg_Right_1_0122"),
    ("foot_r", "leg_r", "J_Leg_Right_2_0123"),
    ("leg_l", "pelvis", "J_Leg_Left_1_0115"),
    ("foot_l", "leg_l", "J_Leg_Left_2_0116"),
    ("cloth", "pelvis", "Cloth_Hang02_01_0103"),
]
BONES = [b for b, _, _ in RIG]


def bone_for(joint: str) -> str:
    n = joint
    for side, s in (("Left", "l"), ("Right", "r")):
        if f"_{side}" in n:
            if any(k in n for k in ("Thumb", "Index", "Middle", "Ring", "Tiny", "Wrist", "Hand", "Bottle")):
                return f"hand_{s}"
            if "Elbow" in n:
                return f"forearm_{s}"
            if "Shoulder" in n:
                return f"arm_{s}"
            if "Clavicle" in n:
                return "chest"
            if "Leg" in n and n.split(f"_{side}_")[1].startswith("1"):
                return f"leg_{s}"
            if "Leg" in n or "Buckle" in n:
                return f"foot_{s}"
    if "Gun" in n or "Shell" in n:
        return "hand_r"
    if "Hat" in n or "Feather" in n:
        return "hat"
    if "Spine04" in n:
        return "head"
    if n.startswith("joint1_"):
        return "neck"
    if "joint11" in n:
        return "foot_l" if n.endswith("19") else "foot_r"
    if "Cloth_Hang" in n:
        return "cloth"
    if "Skirt" in n:
        return "skirt"
    if "Spine02" in n or "Spine03" in n or any(k in n for k in ("Bag", "Rosemary", "Holster")):
        return "chest"
    if "Spine01" in n:
        return "spine"
    return "pelvis"


def rgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def mix(a, b, t):
    t = np.clip(t, 0.0, 1.0)[..., None]
    return a * (1.0 - t) + b * t


def hash3(p: np.ndarray, salt: int) -> np.ndarray:
    q = np.floor(p).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791) ^ (salt * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def part_of(mesh_name: str) -> str:
    n = mesh_name.lower()
    for key, part in (("mask", "mask"), ("tophat", "hat"), ("feather", "feather"), ("coat", "cloak"), ("skirt", "cloak"),
                      ("neckpiece", "cloak"), ("glove", "hands"), ("boot", "boots"), ("torso", "torso"), ("arms", "torso"),
                      ("belt", "leather"), ("bag", "leather"), ("strap", "leather"), ("rosemary", "leather"), ("cord", "leather")):
        if key in n:
            return part
    return "torso"


def paint(part: str, p: np.ndarray, n: np.ndarray, detail: np.ndarray) -> np.ndarray:
    """D.O.T.'s colours at source positions p (model units: y up, 0 at his feet, about 88 at the hat's crown)."""
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    height = np.clip(y / 88.0, 0.0, 1.0)
    base = mix(rgb(POISON), rgb(NAVY), height ** 0.8) * np.ones(p.shape[:-1] + (1,))
    if part == "mask":
        base = mix(rgb(VOID), rgb(NAVY_DEEP), np.clip((79.0 - y) / 9.0, 0, 1)) * np.ones_like(p)
        base = np.where((z > 9.0)[..., None], mix(base, rgb(VOID), 0.6), base)            # the long front, darkest
    elif part == "hat":
        base = rgb(NAVY_DEEP) * np.ones_like(p)
        base = np.where((np.abs(y - 78.6) < 0.9)[..., None], rgb(BAND), base)              # a pale band
    elif part == "feather":
        base = mix(rgb(CLOAK), rgb(CLOAK_TIP), np.clip((y - 88.0) / 10.0, 0, 1))
    elif part == "cloak":
        hem = np.clip((60.0 - y) / 45.0, 0, 1)
        base = mix(rgb(CLOAK), rgb(CLOAK_TIP), hem ** 1.4)
        vein = np.abs(np.sin(y * 0.55 + x * 0.2) * np.cos(z * 0.3)) < 0.05
        base = np.where(vein[..., None], mix(base, rgb(CLOAK_TIP), 0.7), base)
    elif part == "torso":
        base = mix(rgb(NAVY_DEEP), rgb(TEAL), np.clip((62.0 - y) / 20.0, 0, 1))
        cx, cy = np.abs(np.modf(x * 0.5)[0]), np.abs(np.modf(y * 0.5)[0])
        gate = hash3(np.stack([x * 0.5, y * 0.5, z * 0.5], -1), 3) > 0.45
        trace = ((cx < 0.06) | (cy < 0.06)) & gate
        base = np.where(trace[..., None], mix(base, rgb(TEAL) * 1.8, 0.5), base)
    elif part == "hands":
        base = mix(rgb(TEAL), rgb(SICK), np.clip((48.0 - y) / 12.0, 0, 1))                 # sick green toward the claws
    elif part == "boots":
        base = mix(rgb(NAVY_DEEP), rgb(NAVY), 0.5 + 0.2 * np.sin(x * 0.9))
        top = 9.0 + 4.0 * np.abs(np.sin(x * 0.7 + z * 0.5)) + 1.6 * np.sin(x * 1.9)
        base = np.where((np.abs(y - top) < 0.45)[..., None], rgb(SICK), base)               # flame outlines
        base = np.where((y < top)[..., None] & ~(np.abs(y - top) < 0.45)[..., None], mix(base, rgb(POISON), 0.55), base)
    elif part == "leather":
        base = mix(rgb(STEEL), rgb(VOID), 0.3) * np.ones_like(p)
    band = hash3(np.stack([np.zeros_like(y), y * 1.8, np.zeros_like(y)], -1), 11)        # glitch scanlines
    base = base * (1.0 + 0.10 * (band - 0.5))[..., None]
    base = base * (0.72 + 0.56 * detail[..., None])
    return np.clip(base, 0, 255)


def bake(name, items, source_image):
    tex_dir = Path(N.asset("textures/entity/dot/model/x.png")).parent
    tex_dir.mkdir(parents=True, exist_ok=True)
    src = np.asarray(source_image.convert("L").resize((TEX, TEX), Image.LANCZOS), dtype=np.float64) / 255.0
    src = (src - src.mean()) / max(src.std(), 1e-3) * 0.18 + 0.5
    colour = np.zeros((TEX, TEX, 3))
    filled = np.zeros((TEX, TEX), bool)
    for part, pos, nor, uv, tris in items:
        for t in tris:
            a, b, c = uv[t] * TEX
            x0, x1 = int(max(0, np.floor(min(a[0], b[0], c[0])) - 1)), int(min(TEX - 1, np.ceil(max(a[0], b[0], c[0])) + 1))
            y0, y1 = int(max(0, np.floor(min(a[1], b[1], c[1])) - 1)), int(min(TEX - 1, np.ceil(max(a[1], b[1], c[1])) + 1))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(d) < 1e-9:
                continue
            w0 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / d
            w1 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / d
            w2 = 1.0 - w0 - w1
            inside = (w0 >= -0.08) & (w1 >= -0.08) & (w2 >= -0.08)
            if not inside.any():
                continue
            w = np.stack([w0, w1, w2], -1)[inside]
            ys, xs = (gy[inside] - 0.5).astype(int), (gx[inside] - 0.5).astype(int)
            colour[ys, xs] = paint(part, w @ pos[t], w @ nor[t], src[ys, xs])
            filled[ys, xs] = True
    colour[~filled] = rgb(NAVY_DEEP)
    alpha = np.full((TEX, TEX, 1), 255.0)
    Image.fromarray(np.concatenate([colour, alpha], -1).astype(np.uint8), "RGBA").save(tex_dir / f"{name}.png")


def build() -> None:
    if not SOURCE.exists():
        print(f"  dot: {SOURCE.name} not found; kept what the mod has")
        return
    j, binary = dl_glb.load(SOURCE)
    skin = j["skins"][0]
    joint_names = [j["nodes"][n]["name"] for n in skin["joints"]]
    ibm = dl_glb.accessor(j, binary, skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)
    bind = {name: np.linalg.inv(ibm[k])[:3, 3] for k, name in enumerate(joint_names)}
    joint_bone = [BONES.index(bone_for(n)) for n in joint_names]

    prims = []
    for node in j["nodes"]:
        if "mesh" not in node or node.get("name", "").startswith("Posetest7"):
            continue
        for pr in j["meshes"][node["mesh"]]["primitives"]:
            mat = j["materials"][pr.get("material", 0)]
            at = pr["attributes"]
            if mat["name"] in SKIP_MATERIALS or "JOINTS_0" not in at:
                continue
            pos = dl_glb.accessor(j, binary, at["POSITION"])
            nor = dl_glb.accessor(j, binary, at["NORMAL"])
            uv = dl_glb.accessor(j, binary, at["TEXCOORD_0"])
            tris = dl_glb.accessor(j, binary, pr["indices"]).astype(np.int64).reshape(-1, 3)
            joints = dl_glb.accessor(j, binary, at["JOINTS_0"]).astype(np.int64)
            weights = dl_glb.accessor(j, binary, at["WEIGHTS_0"])
            vbone = np.array([joint_bone[joints[i, np.argmax(weights[i])]] for i in range(len(pos))])
            prims.append((j["meshes"][node["mesh"]].get("name", ""), mat, pos, nor, uv, tris, vbone))
    allp = np.concatenate([p[2] for p in prims])
    y0, y1 = allp[:, 1].min(), allp[:, 1].max()
    scale = HEIGHT_PX / (y1 - y0)

    def to_model(p):
        # Source: y up, facing +Z, his left at +X. Model space: y down, facing -Z, his right at -X.
        out = np.empty_like(p, dtype=np.float64)
        out[..., 0] = p[..., 0] * scale
        out[..., 1] = 24.0 - (p[..., 1] - y0) * scale
        out[..., 2] = -p[..., 2] * scale
        return out

    pivots = {"root": np.array([0.0, 24.0, 0.0])}
    for bone, _, joint in RIG:
        pivots[bone] = to_model(bind[joint][None])[0]

    # The textures, painted.
    by_material: dict[str, list] = {}
    sources = {}
    for name, mat, pos, nor, uv, tris, vbone in prims:
        key = mat["name"].lower()
        by_material.setdefault(key, []).append((part_of(name), pos, nor, uv, tris))
        if key not in sources:
            pbr = mat.get("pbrMetallicRoughness", {})
            sources[key] = dl_glb.image(j, binary, pbr["baseColorTexture"]["index"]) if "baseColorTexture" in pbr else Image.new("RGB", (4, 4), (150, 150, 150))
    for key, items in by_material.items():
        bake(key, items, sources[key])

    # Simplify to about TARGET triangles.
    cell = 0.2
    for _ in range(30):
        if sum(len(simplify(p[2], p[4], p[5], cell)[2]) for p in prims) <= TARGET:
            break
        cell *= 1.15
    names = list(by_material)
    groups: dict[tuple[str, str], list[np.ndarray]] = {}
    total = sum(len(p[5]) for p in prims)
    for name, mat, pos, nor, uv, tris, vbone in prims:
        key = mat["name"].lower()
        mpos, muv, mtris = simplify(pos, uv, tris, cell)
        korig = np.concatenate([np.floor(pos / cell).astype(np.int64), np.floor(uv * 24).astype(np.int64)], axis=1)
        _, first = np.unique(korig, axis=0, return_index=True)
        mbone = vbone[first]
        for t in mtris:
            bone = BONES[np.bincount(mbone[t], minlength=len(BONES)).argmax()]
            p3 = to_model(mpos[t])
            nrm = np.cross(p3[1] - p3[0], p3[2] - p3[0])
            ln = np.linalg.norm(nrm)
            nrm = nrm / ln if ln > 0 else np.array([0.0, -1.0, 0.0])
            groups.setdefault((bone, key), []).append(np.concatenate([p3 - pivots[bone], muv[t], np.repeat(nrm[None], 3, 0)], axis=1))

    out = io.BytesIO()
    out.write(b"DLMESH1\0")
    out.write(struct.pack(">i", len(names)))
    for n in names:
        e = n.encode("utf-8")
        out.write(struct.pack(">h", len(e)))
        out.write(e)
        out.write(struct.pack(">?", False))   # nothing glows
    out.write(struct.pack(">i", len(groups)))
    count = 0
    for (bone, key), rows in groups.items():
        data = np.stack(rows).reshape(-1, 8).astype(">f4")
        out.write(struct.pack(">hhi", BONES.index(bone), names.index(key), len(rows)))
        out.write(data.tobytes())
        count += len(rows)
    mesh = Path(N.asset("models/entity/dot.bin"))
    mesh.write_bytes(out.getvalue())

    lines = ["package com.deadlands.client.render;", "",
             "import com.deadlands.Deadlands;",
             "import net.minecraft.client.model.geom.ModelLayerLocation;",
             "import net.minecraft.client.model.geom.PartPose;",
             "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
             "import net.minecraft.client.model.geom.builders.LayerDefinition;",
             "import net.minecraft.client.model.geom.builders.MeshDefinition;",
             "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
             "/**",
             " * GENERATED by tools/assetgen/import_dot.py - do not edit; change the rig there and rerun it.",
             " *",
             " * <p>D.O.T.'s rig: pivots at the imported model's joints, no boxes ({@code RiggedMesh.Layer} draws the mesh).",
             " */",
             "public final class DotGeometry {",
             "\tpublic static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id(\"dot\"), \"main\");",
             "\tpublic static final String[] BONES = {" + ", ".join(f'"{b}"' for b in BONES) + "};",
             "\tpublic static final String[] PARENTS = {" + ", ".join(f'"{p}"' for _, p, _ in RIG) + "};", "",
             "\tprivate DotGeometry() {", "\t}", "",
             "\tpublic static LayerDefinition create() {",
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot().addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));"]
    for bone, parent, _ in RIG:
        rel = pivots[bone] - pivots[parent]
        lines.append(f"\t\tPartDefinition {bone} = {parent}.addOrReplaceChild(\"{bone}\", CubeListBuilder.create(), "
                     f"PartPose.offset({rel[0]:.3f}F, {rel[1]:.3f}F, {rel[2]:.3f}F));")
    lines += ["\t\treturn LayerDefinition.create(mesh, 16, 16);", "\t}", "}", ""]
    JAVA.write_text("\n".join(lines), encoding="utf-8")
    print(f"  dot: {total} triangles simplified to {count} (cell {cell:.2f}), {len(groups)} groups, {len(names)} materials")


if __name__ == "__main__":
    build()
