"""The Guest's 3D model (Phase 5): "Sixer aka Guest 666 - Forsaken" by Fourteen
(https://sketchfab.com/LFourteen_), CC-BY-4.0, from
https://sketchfab.com/3d-models/sixer-aka-guest-666-forsaken-8dc1a8bee1eb4ee38f10baf3185be6fc
(credited in CREDITS.md).

Source: ~/Downloads/sixer remake.glb (skipped when missing, keeping what the mod has).

The model is skinned: every triangle goes to the bone that moves it most (its vertices' skin weights),
and the bones are gathered into the Guest's rig (pelvis, torso, neck, head, jaw, horns, arms, hands,
legs, a 13-part tail), with each part's pivot at the model's own joint. It is far too dense for a
Minecraft entity (about 221,000 triangles), so it is simplified (vertex clustering, keeping UV seams)
to about TARGET triangles. Its textures are recoloured to the owner's palette (PALETTE, from the
reference image) and the brightest reds become the glow layer.

Writes assets/deadlands/models/entity/guest.bin (read by RiggedMesh), textures/entity/guest/model/*,
and src/client/java/com/deadlands/client/render/GuestGeometry.java (the rig).
"""
from __future__ import annotations

import io
import struct
from pathlib import Path

import numpy as np
from PIL import Image

import dl_glb
import dl_noise as N

SOURCE = Path.home() / "Downloads" / "sixer remake.glb"
TARGET = 16000
HEIGHT_PX = 54.0
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "GuestGeometry.java"
# The owner's palette (from the colour reference), darkest to brightest.
PALETTE = ["#090606", "#0d0d0d", "#140808", "#231515", "#370809", "#8a2023", "#a00507", "#d90409", "#ed030a"]
GLOWING = {"#d90409", "#ed030a"}

# Model joints (name prefix) -> rig bone.
JOINT_BONE = [
    ("Bone.004.R", "arm_r"), ("Bone.005.R.", "hand_r"), ("handik.R", "hand_r"), ("Bone.005.R", "forearm_r"), ("armpole.R", "forearm_r"),
    ("Bone.004.L", "arm_l"), ("Bone.005.L.", "hand_l"), ("handik.L", "hand_l"), ("Bone.005.L", "forearm_l"), ("armpole.L", "forearm_l"),
    ("Bone.009.R", "leg_r"), ("Bone.010.R", "shin_r"), ("Bone.011.R", "shin_r"), ("Bone.012.R", "shin_r"), ("Bone.013.R", "shin_r"),
    ("Bone.014.R", "shin_r"), ("legik.R", "shin_r"), ("legpole.R", "shin_r"),
    ("Bone.009.L", "leg_l"), ("Bone.010.L", "shin_l"), ("Bone.011.L", "shin_l"), ("Bone.012.L", "shin_l"), ("Bone.013.L", "shin_l"),
    ("Bone.014.L", "shin_l"), ("legik.L", "shin_l"), ("legpole.L", "shin_l"),
    ("Bone.008.R", "pelvis"), ("Bone.008.L", "pelvis"), ("horn.R", "horn_r"), ("horn.L", "horn_l"), ("eye.", "head"),
    ("Bone.009_", "jaw"), ("Bone.007", "head"), ("Bone.006", "neck"), ("Bone.003.R", "torso"), ("Bone.003.L", "torso"),
    ("Bone.002", "torso"), ("Bone.001", "torso"), ("Bone_40", "pelvis"), ("GLTF_created_0_rootJoint", "pelvis"),
]
TAIL = ["Bone.004_39", "Bone.005_38", "Bone.008_37", "Bone.010_36", "Bone.011_35", "Bone.012_34", "Bone.013_33",
        "Bone.014_32", "Bone.015_31", "Bone.016_30", "Bone.017_29", "Bone.019_28", "Bone.018_27"]
# The rig: bone, parent, and the joint whose bind position is its pivot.
RIG = [("pelvis", "root", "Bone_40"), ("torso", "pelvis", "Bone.001_18"), ("neck", "torso", "Bone.006_13"),
       ("head", "neck", "Bone.007_12"), ("jaw", "head", "Bone.009_3"), ("horn_r", "head", "horn.R_6"), ("horn_l", "head", "horn.L_9"),
       ("arm_r", "torso", "Bone.004.R_1"), ("forearm_r", "arm_r", "Bone.005.R_0"), ("hand_r", "forearm_r", "handik.R_57"),
       ("arm_l", "torso", "Bone.004.L_15"), ("forearm_l", "arm_l", "Bone.005.L_14"), ("hand_l", "forearm_l", "handik.L_80"),
       ("leg_r", "pelvis", "Bone.009.R_21"), ("shin_r", "leg_r", "Bone.010.R_20"),
       ("leg_l", "pelvis", "Bone.009.L_25"), ("shin_l", "leg_l", "Bone.010.L_24")]
RIG += [(f"tail_{i}", "pelvis" if i == 0 else f"tail_{i - 1}", name) for i, name in enumerate(TAIL)]
BONES = [b for b, _, _ in RIG]


def bone_for(joint_name: str) -> str:
    if joint_name in TAIL:
        return f"tail_{TAIL.index(joint_name)}"
    for prefix, bone in JOINT_BONE:
        if joint_name.startswith(prefix):
            return bone
    return "pelvis"


def hex_rgb(h: str) -> np.ndarray:
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float64)


def recolour(image: Image.Image) -> tuple[Image.Image, Image.Image]:
    """Each texel to the nearest palette colour (by brightness and redness); the glowing reds also go to the glow map."""
    a = np.asarray(image.convert("RGBA"), dtype=np.float64)
    rgb = a[..., :3]
    pal = np.stack([hex_rgb(h) for h in PALETTE])
    # Compare in a space that weights redness: (luma, red excess).
    def feat(c):
        luma = c[..., 0] * 0.3 + c[..., 1] * 0.59 + c[..., 2] * 0.11
        red = c[..., 0] - (c[..., 1] + c[..., 2]) / 2.0
        return np.stack([luma, red * 0.8], axis=-1)
    f = feat(rgb)[..., None, :]
    fp = feat(pal)[None, None, :, :]
    idx = np.argmin(((f - fp) ** 2).sum(-1), axis=-1)
    out = pal[idx]
    glow = np.zeros_like(a)
    for i, h in enumerate(PALETTE):
        if h in GLOWING:
            mask = idx == i
            glow[mask, :3] = pal[i]
            glow[mask, 3] = 255
    return (Image.fromarray(np.concatenate([out, a[..., 3:]], -1).astype(np.uint8), "RGBA"),
            Image.fromarray(glow.astype(np.uint8), "RGBA"))


def simplify(pos, uv, tris, cell):
    """Vertex clustering: merge vertices in the same grid cell with the same UV patch; drop collapsed triangles."""
    key = np.concatenate([np.floor(pos / cell).astype(np.int64), np.floor(uv * 24).astype(np.int64)], axis=1)
    _, inverse = np.unique(key, axis=0, return_inverse=True)
    inverse = inverse.ravel()
    count = inverse.max() + 1
    sums = np.zeros((count, 5))
    np.add.at(sums, inverse, np.concatenate([pos, uv], axis=1))
    n = np.bincount(inverse, minlength=count)[:, None]
    merged = sums / np.maximum(n, 1)
    t = inverse[tris]
    keep = (t[:, 0] != t[:, 1]) & (t[:, 1] != t[:, 2]) & (t[:, 0] != t[:, 2])
    t = np.unique(np.sort(t[keep], axis=1), axis=0) if False else t[keep]
    return merged[:, :3], merged[:, 3:], t


def build() -> None:
    if not SOURCE.exists():
        print(f"  guest: {SOURCE.name} not found; kept what the mod has")
        return
    j, binary = dl_glb.load(SOURCE)
    skin = j["skins"][0]
    joint_nodes = skin["joints"]
    joint_names = [j["nodes"][n]["name"] for n in joint_nodes]
    ibm = dl_glb.accessor(j, binary, skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)
    bind = {name: np.linalg.inv(ibm[k])[:3, 3] for k, name in enumerate(joint_names)}
    joint_bone = [bone_for(n) for n in joint_names]

    # Every primitive: positions (bind space), UVs, triangles, material, and each triangle's bone.
    prims = []
    for mesh in j["meshes"]:
        for pr in mesh["primitives"]:
            at = pr["attributes"]
            pos = dl_glb.accessor(j, binary, at["POSITION"])
            uv = dl_glb.accessor(j, binary, at["TEXCOORD_0"])
            tris = dl_glb.accessor(j, binary, pr["indices"]).astype(np.int64).reshape(-1, 3)
            joints = dl_glb.accessor(j, binary, at["JOINTS_0"]).astype(np.int64)
            weights = dl_glb.accessor(j, binary, at["WEIGHTS_0"])
            mat = j["materials"][pr.get("material", 0)]
            prims.append((pos, uv, tris, joints, weights, mat))
    allp = np.concatenate([p[0] for p in prims])
    y0, y1 = allp[:, 1].min(), allp[:, 1].max()
    scale = HEIGHT_PX / (y1 - y0)

    def to_model(p):
        out = np.empty_like(p)
        out[..., 0] = p[..., 0] * scale              # it faces +Z; its right hand is -X, as in model space
        out[..., 1] = 24.0 - (p[..., 1] - y0) * scale
        out[..., 2] = -p[..., 2] * scale
        return out

    # The arms are modelled straight out (T-pose). Bake them hanging straight down instead, so they work
    # as front legs: each arm (with its forearm, hand and claws) turns about its shoulder, 90 degrees.
    shoulders = {"r": bind["Bone.004.R_1"], "l": bind["Bone.004.L_15"]}
    arm_bones = {side: [BONES.index(b + "_" + side) for b in ("arm", "forearm", "hand")] for side in ("r", "l")}

    def turn(points, side):
        theta = np.pi / 2 if side == "r" else -np.pi / 2   # right arm points -X: down is +90 degrees about Z
        c, s_ = np.cos(theta), np.sin(theta)
        rot = np.array([[c, -s_, 0.0], [s_, c, 0.0], [0.0, 0.0, 1.0]])
        return (points - shoulders[side]) @ rot.T + shoulders[side]

    def arms_down(points, bones_of):
        out = points.copy()
        for side, ids in arm_bones.items():
            mask = np.isin(bones_of, ids)
            if mask.any():
                out[mask] = turn(points[mask], side)
        return out

    pivots = {"root": np.array([0.0, 24.0, 0.0])}
    for bone, _, joint in RIG:
        at = bind[joint]
        side = bone[-1] if bone.startswith(("arm_", "forearm_", "hand_")) else None
        if side:
            at = turn(at[None], side)[0]
        pivots[bone] = to_model(at[None])[0]

    # Simplify: find a cell size that brings the whole model to about TARGET triangles.
    total = sum(len(p[2]) for p in prims)
    cell = 0.02
    for _ in range(30):
        n = sum(len(simplify(p[0], p[1], p[2], cell)[2]) for p in prims)
        if n <= TARGET:
            break
        cell *= 1.18

    tex_dir = Path(N.asset("textures/entity/guest/model/x.png")).parent
    tex_dir.mkdir(parents=True, exist_ok=True)
    materials: dict[str, bool] = {}
    groups: dict[tuple[str, str], list[np.ndarray]] = {}
    for pos, uv, tris, joints, weights, mat in prims:
        name = mat["name"].lower().replace(".", "_").replace(" ", "_")
        if name not in materials:
            pbr = mat.get("pbrMetallicRoughness", {})
            base = dl_glb.image(j, binary, pbr["baseColorTexture"]["index"]) if "baseColorTexture" in pbr else Image.new("RGBA", (4, 4), (20, 8, 8, 255))
            base = base.resize((min(512, base.width), min(512, base.height)), Image.LANCZOS)
            colour, glow = recolour(base)
            if "emissiveTexture" in mat and "index" in mat["emissiveTexture"]:
                emissive = dl_glb.image(j, binary, mat["emissiveTexture"]["index"]).resize(base.size, Image.LANCZOS)
                e = np.asarray(emissive, dtype=np.float64)[..., :3].max(-1)
                g = np.asarray(glow, dtype=np.float64)
                lit = e > 60
                g[lit, :3] = hex_rgb("#ed030a")
                g[lit, 3] = np.clip(e[lit] * 1.4, 0, 255)
                glow = Image.fromarray(g.astype(np.uint8), "RGBA")
            colour.save(tex_dir / f"{name}.png")
            glow.save(tex_dir / f"{name}_glow.png")
            materials[name] = True
        # The bone of each vertex: its heaviest joint.
        vbone = np.array([BONES.index(joint_bone[joints[i, np.argmax(weights[i])]]) for i in range(len(pos))])
        pos = arms_down(pos, vbone)
        mpos, muv, mtris = simplify(pos, uv, tris, cell)
        # Merged vertices keep the bone of the first original vertex that fell in them (recomputed by nearest).
        key_orig = np.concatenate([np.floor(pos / cell).astype(np.int64), np.floor(uv * 24).astype(np.int64)], axis=1)
        _, first = np.unique(key_orig, axis=0, return_index=True)
        mbone = vbone[first]
        for t in mtris:
            b = np.bincount(mbone[t], minlength=len(BONES)).argmax()
            bone = BONES[b]
            p = to_model(mpos[t])
            nrm = np.cross(p[1] - p[0], p[2] - p[0])
            ln = np.linalg.norm(nrm)
            nrm = nrm / ln if ln > 0 else np.array([0.0, -1.0, 0.0])
            rows = np.concatenate([p - pivots[bone], muv[t], np.repeat(nrm[None], 3, 0)], axis=1)
            groups.setdefault((bone, name), []).append(rows)

    names = list(materials)
    out = io.BytesIO()
    out.write(b"DLMESH1\0")
    out.write(struct.pack(">i", len(names)))
    for n in names:
        encoded = n.encode("utf-8")
        out.write(struct.pack(">h", len(encoded)))
        out.write(encoded)
        out.write(struct.pack(">?", False))  # no glow (the owner: the Guest and D.O.T. don't glow in the dark)
    out.write(struct.pack(">i", len(groups)))
    count = 0
    for (bone, name), rows in groups.items():
        data = np.stack(rows).reshape(-1, 8).astype(">f4")
        out.write(struct.pack(">hhi", BONES.index(bone), names.index(name), len(rows)))
        out.write(data.tobytes())
        count += len(rows)
    mesh = Path(N.asset("models/entity/guest.bin"))
    mesh.parent.mkdir(parents=True, exist_ok=True)
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
             " * GENERATED by tools/assetgen/import_guest.py - do not edit; change the rig there and rerun it.",
             " *",
             " * <p>The Guest's rig: pivots at the imported model's own joints, no boxes ({@code RiggedMeshLayer} draws the mesh).",
             " */",
             "public final class GuestGeometry {",
             "\tpublic static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id(\"guest\"), \"main\");",
             "\tpublic static final String[] BONES = {" + ", ".join(f'"{b}"' for b in BONES) + "};",
             "\tpublic static final String[] PARENTS = {" + ", ".join(f'"{p}"' for _, p, _ in RIG) + "};", "",
             "\tprivate GuestGeometry() {", "\t}", "",
             "\tpublic static LayerDefinition create() {",
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot().addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));"]
    for bone, parent, _ in RIG:
        rel = pivots[bone] - pivots[parent]
        lines.append(f"\t\tPartDefinition {bone} = {parent}.addOrReplaceChild(\"{bone}\", CubeListBuilder.create(), "
                     f"PartPose.offset({rel[0]:.3f}F, {rel[1]:.3f}F, {rel[2]:.3f}F));")
    lines += ["\t\treturn LayerDefinition.create(mesh, 16, 16);", "\t}", "}", ""]
    JAVA.write_text("\n".join(lines), encoding="utf-8")
    print(f"  guest: {total} triangles simplified to {count} (cell {cell:.3f}), {len(groups)} groups, {len(names)} materials, scale {scale:.2f}")


if __name__ == "__main__":
    build()
