"""The Aberrant's 3D model (Phase 5): the fan-made "Aberrant Slasher" model from Sketchfab (free to use
with credit; see CREDITS.md), imported as an animated mesh.

Source: ~/Downloads/aberrant--slasher--fanmade--forsaken/source/Aberrant_Slasher_Fanmade_Forsaken.glb
(skipped when missing, keeping what the mod already has).

The model is a static T-pose with no skeleton, so it is rigged here:
  - converted to Minecraft model space (pixels, y down, the ground at y = 24, the front facing -Z),
    scaled to 37 pixels tall (about 2.3 blocks, the Aberrant's hitbox);
  - the arms are swung down from the T-pose to hang at the sides (the rig's rest pose);
  - every triangle is given to a bone by where it sits: head, torso, pelvis, each arm's upper arm,
    forearm and hand (the fire axe goes with the right hand), each leg's thigh and shin;
  - each material's texture has its tiling baked in (Minecraft entity textures do not wrap) and its
    glow map, if any, is kept for the emissive pass.

Writes:
  assets/deadlands/models/entity/aberrant.bin        the mesh, per bone and material (read by AberrantMesh)
  assets/deadlands/textures/entity/aberrant/*.png    one texture per material (+ *_glow.png)
  src/client/java/com/deadlands/client/render/AberrantGeometry.java   the rig: the same bones and names
      as the Asesino's, with their pivots at the model's joints, so his animations drive it
"""
from __future__ import annotations

import io
import math
import struct
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

import dl_glb
import dl_noise as N

SOURCE = Path.home() / "Downloads" / "aberrant--slasher--fanmade--forsaken" / "source" / "Aberrant_Slasher_Fanmade_Forsaken.glb"
NS = "deadlands"
HEIGHT_PX = 37.0
MAX_TEX = 512
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "AberrantGeometry.java"

# The rig: (bone, parent). The names match the Asesino's rig, which his animations use.
BONES = [("pelvis", "root"), ("torso", "pelvis"), ("neck", "torso"), ("head", "neck"),
         ("arm_r", "torso"), ("forearm_r", "arm_r"), ("hand_r", "forearm_r"),
         ("arm_l", "torso"), ("forearm_l", "arm_l"), ("hand_l", "forearm_l"),
         ("coat_front_r", "pelvis"), ("coat_front_l", "pelvis"), ("coat_back", "pelvis"),
         ("leg_r", "pelvis"), ("shin_r", "leg_r"), ("leg_l", "pelvis"), ("shin_l", "leg_l")]
BONE_INDEX = {name: i for i, (name, _) in enumerate(BONES)}


def material_name(mat) -> str:
    return mat["name"].lower().replace(" ", "_")


def texture_for(j, binary, mat):
    pbr = mat.get("pbrMetallicRoughness", {})
    if "baseColorTexture" in pbr:
        return dl_glb.image(j, binary, pbr["baseColorTexture"]["index"])
    f = pbr.get("baseColorFactor", [1, 1, 1, 1])
    return Image.new("RGBA", (4, 4), tuple(int(round(max(0.0, min(1.0, c)) ** (1 / 2.2) * 255)) if i < 3 else int(c * 255) for i, c in enumerate(f)))


def tiled(image: Image.Image, u0: int, v0: int, nu: int, nv: int) -> Image.Image:
    """The image repeated nu x nv times (each tile shrunk so the whole stays within MAX_TEX)."""
    w, h = image.size
    tile_w = max(1, min(w, MAX_TEX // nu))
    tile_h = max(1, min(h, MAX_TEX // nv))
    tile = image.resize((tile_w, tile_h), Image.LANCZOS) if (tile_w, tile_h) != (w, h) else image
    out = Image.new("RGBA", (tile_w * nu, tile_h * nv))
    for i in range(nu):
        for k in range(nv):
            out.paste(tile, (i * tile_w, k * tile_h))
    return out


# Every material gets its own look. The source textures are near-white overlays meant to sit on tints
# the model file does not carry, so each is dyed and detailed here: (style, colour, how much of the
# original texture's light and shade shows through, blood).
COAT = (122.0, 82.0, 48.0)
STYLES = {
    "leftlowerarm1mtl": ("coat", COAT, 0.30, 0.07),            # the long coat
    "leftupperarm1mtl": ("coat", COAT, 0.30, 0.06),            # its sleeves
    "cylinder21mtl": ("fur", (150.0, 120.0, 84.0), 0.45, 0.03), # fur trim: cuffs, collar
    "righthand1mtl": ("leather", (52.0, 38.0, 30.0), 0.25, 0.08),  # gloves
    "leftfoot1mtl": ("leather", (86.0, 56.0, 34.0), 0.25, 0.03),   # boots
    "quadsphere42mtl": ("leather", (70.0, 46.0, 28.0), 0.25, 0.0),  # belt pouch
    "lowertorso1mtl": ("cloth", (62.0, 56.0, 50.0), 0.30, 0.04),    # trousers
    "cylinder22mtl": ("cloth", (96.0, 30.0, 26.0), 0.30, 0.10),     # the shirt under the coat
    "quadsphere32mtl": ("cloth", (96.0, 30.0, 26.0), 0.30, 0.06),
    "box2mtl": ("metal", (120.0, 116.0, 110.0), 0.40, 0.04),        # chains: rusted iron
    "tube21mtl": ("hair", (178.0, 172.0, 160.0), 0.55, 0.05),       # matted, dirty hair
    "aberrantslasher1mtl": ("worn", None, 1.0, 0.04),               # the head
    "mask1mtl": ("worn", None, 1.0, 0.02),                          # the mask: kept, scratched and grimed
    "box1mtl": ("worn", None, 1.0, 0.0),                            # teeth
    "mesh1mtl": ("worn", None, 1.0, 0.02),                          # the tag on his chest
    "uppertorso1mtl": ("worn", None, 1.0, 0.0),
    "fire_axe_txtr": ("worn", None, 1.0, 0.03),                     # the axe: chipped, scuffed
}


def _noise(rng, h, w, scales):
    out = np.zeros((h, w))
    for scale, weight in scales:
        small = rng.random((max(1, h // scale), max(1, w // scale)))
        out += np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), dtype=np.float64) / 255.0 * weight
    return out


def weather(image: Image.Image, name: str) -> Image.Image:
    if name not in STYLES:
        return image
    style, dye, keep, blood = STYLES[name]
    rng = np.random.default_rng(zlib.crc32(name.encode()))
    a = np.asarray(image.convert("RGBA"), dtype=np.float64)
    rgb, alpha = a[..., :3], a[..., 3:4]
    h, w = rgb.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    if dye is not None:
        # Dye: the original's light and shade (detail drawn out) carried onto the colour.
        mean = rgb.mean(axis=(0, 1), keepdims=True)
        rgb = mean + (rgb - mean) * 2.2
        base = np.array(dye)
        shade = (rgb.mean(axis=-1, keepdims=True) / 200.0).clip(0.35, 1.4)
        rgb = base * shade * (1.0 - keep) + rgb * keep * (base / 200.0)
    if style == "coat":
        weave = ((xx % 4 < 2) ^ (yy % 4 < 2)) * 0.06 - 0.03
        rgb *= (1.0 + weave)[..., None]
        seam = (yy % max(24, h // 6) < 2) | (xx % max(32, w // 5) < 2)
        stitch = seam & ((xx + yy) % 6 < 3)
        rgb[seam] *= 0.62
        rgb[stitch] = rgb[stitch] * 0.4 + np.array([200.0, 170.0, 120.0]) * 0.6
        near = (yy % max(24, h // 6) < 5) | (xx % max(32, w // 5) < 5)
        rgb[near & ~seam] *= 1.12
        tears = _noise(rng, h, w, ((6, 1.0),)) > 0.86                    # frayed, torn patches
        rgb[tears] *= 0.55
    elif style == "fur":
        strands = np.sin(xx * 0.9 + np.sin(yy * 0.21) * 4.0 + _noise(rng, h, w, ((4, 6.0),))) * 0.5 + 0.5
        rgb *= (0.72 + 0.4 * strands)[..., None]
        clumps = _noise(rng, h, w, ((16, 1.0),))
        rgb *= (0.8 + 0.35 * clumps)[..., None]
    elif style == "leather":
        crease = np.sin(xx * 0.09 + np.sin(yy * 0.05) * 3.0) * np.sin(yy * 0.13)
        rgb *= (1.0 + crease * 0.14)[..., None]
        grain = _noise(rng, h, w, ((2, 1.0),))
        rgb *= (0.9 + 0.2 * grain)[..., None]
        scuff = rng.random((h, w)) < 0.012
        rgb[scuff] = rgb[scuff] * 0.45 + np.array([170.0, 140.0, 110.0]) * 0.55
        if name == "leftfoot1mtl":
            rgb[yy > h * 0.88] *= 0.55                                     # the sole
            lace = ((yy % 18) < 2) & (np.abs(xx - w / 2) < w * 0.12)
            rgb[lace] = rgb[lace] * 0.3 + np.array([40.0, 30.0, 22.0]) * 0.7
    elif style == "cloth":
        twill = ((xx + yy) % 5 < 2) * 0.07 - 0.035
        rgb *= (1.0 + twill)[..., None]
        patch = _noise(rng, h, w, ((48, 1.0),)) > 0.8                      # faded, worn patches
        rgb[patch] = rgb[patch] * 0.75 + np.array([150.0, 140.0, 125.0]) * 0.25
    elif style == "metal":
        rust = _noise(rng, h, w, ((3, 0.5), (12, 0.5)))
        rusty = np.clip((rust - 0.45) * 2.5, 0.0, 1.0)[..., None]
        rgb = rgb * (1.0 - 0.75 * rusty) + np.array([120.0, 62.0, 30.0]) * 0.75 * rusty
        shine = (rng.random((h, w)) < 0.02) & (rusty[..., 0] < 0.3)
        rgb[shine] = rgb[shine] * 0.4 + 200.0 * 0.6
    elif style == "hair":
        strands = np.sin(xx * 1.3 + np.sin(yy * 0.08) * 6.0 + _noise(rng, h, w, ((3, 8.0),))) * 0.5 + 0.5
        rgb *= (0.6 + 0.55 * strands)[..., None]
        matted = _noise(rng, h, w, ((24, 1.0),)) > 0.7
        rgb[matted] = rgb[matted] * 0.55 + np.array([70.0, 58.0, 44.0]) * 0.45
    elif style == "worn":
        scratches = np.zeros((h, w), dtype=bool)
        for _ in range(int(14 + w / 40)):                                # thin scratches
            x0, y0 = rng.integers(0, w), rng.integers(0, h)
            ang, length = rng.random() * np.pi, rng.integers(w // 20 + 2, w // 6 + 3)
            for t in range(length):
                xs, ys = int(x0 + np.cos(ang) * t), int(y0 + np.sin(ang) * t)
                if 0 <= xs < w and 0 <= ys < h:
                    scratches[ys, xs] = True
        rgb[scratches] = rgb[scratches] * 0.55 + np.array([60.0, 50.0, 40.0]) * 0.45
    # Grime everywhere, heavier toward the bottom; blood stains.
    noise = _noise(rng, h, w, ((8, 0.5), (32, 0.3), (96, 0.2)))
    ramp = np.linspace(0.0, 1.0, h)[:, None]
    grime = np.clip((noise - 0.45) * 1.8 + ramp * 0.3, 0.0, 1.0)[..., None] * (0.35 if style == "worn" else 1.0)
    rgb = rgb * (1.0 - 0.45 * grime) + np.array([58.0, 46.0, 34.0]) * 0.45 * grime
    for _ in range(int(blood * 120)):
        cx, cy, r = rng.integers(0, w), rng.integers(0, h), rng.integers(max(2, w // 60), max(3, w // 14))
        d = np.sqrt((xx - cx) ** 2 + ((yy - cy) * 1.3) ** 2) / r
        spot = np.clip(1.0 - d, 0.0, 1.0) ** 0.6 * (rng.random((h, w)) < 0.85)
        rgb = rgb * (1.0 - 0.7 * spot[..., None]) + np.array([92.0, 14.0, 10.0]) * 0.7 * spot[..., None]
    out = np.concatenate([np.clip(rgb, 0, 255), alpha if style == "worn" else np.maximum(alpha, 235.0)], axis=-1)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def glow_map(image: Image.Image) -> Image.Image:
    """A glow map for the emissive pass: see-through where it is dark (an opaque black glow paints things black)."""
    a = np.asarray(image.convert("RGBA"), dtype=np.float64)
    lum = a[..., :3].max(axis=-1)
    a[..., 3] = np.clip(lum * 1.5, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def build() -> None:
    if not SOURCE.exists():
        print(f"  aberrant: {SOURCE.name} not found; kept what the mod has")
        return
    j, binary, prims = dl_glb.primitives(SOURCE)
    body = [p for p in prims if p[0] == "Abberanr"]
    allp = np.concatenate([p[2] for p in body])
    y0, y1 = allp[:, 1].min(), allp[:, 1].max()
    scale = HEIGHT_PX / (y1 - y0)
    # The torso's middle (x and z) is the body's centre line.
    torso = np.concatenate([p[2] for p in body if p[1]["name"] in ("Uppertorso1Mtl", "Lowertorso1Mtl")])
    cx = (torso[:, 0].min() + torso[:, 0].max()) / 2.0
    cz = (torso[:, 2].min() + torso[:, 2].max()) / 2.0
    half = (torso[:, 0].max() - torso[:, 0].min()) / 2.0
    arms = np.concatenate([p[2] for p in body if "arm" in p[1]["name"].lower() or "hand" in p[1]["name"].lower()])
    arm_y = float(np.median(arms[:, 1]))                       # the T-pose arms' centre line
    h = y1 - y0
    joints = {  # world space (y up), in the T-pose
        "hip_y": y0 + 0.385 * h, "knee_y": y0 + 0.19 * h, "pelvis_y": y0 + 0.43 * h, "torso_y": y0 + 0.53 * h,
        "neck_y": y0 + 0.795 * h, "head_y": y0 + 0.82 * h,
    }
    edge = half * 0.98                                          # where the arms leave the torso
    shoulder_out = half + 0.55 * (arms[:, 2].max() - arms[:, 2].min()) / 2.0 * 0.6
    hip_dx = half * 0.45
    arm_len = max(abs(allp[:, 0].max() - cx), abs(allp[:, 0].min() - cx)) - edge

    def bone_of(c: np.ndarray, is_axe: bool) -> str:
        x, y = c[0] - cx, c[1]
        if is_axe:
            return "hand_r"
        if abs(x) > edge and y > joints["torso_y"]:
            side = "r" if x > 0 else "l"   # the model's right hand is +X (it faces -Z)
            d = (abs(x) - edge) / arm_len
            return ("arm_" if d < 0.45 else "forearm_" if d < 0.82 else "hand_") + side
        if y >= joints["head_y"] and abs(x) < half * 0.9:
            return "head"
        if y >= joints["neck_y"] and abs(x) < half * 0.6:
            return "neck"
        if y >= joints["torso_y"]:
            return "torso"
        if y >= joints["hip_y"]:
            return "pelvis"
        side = "r" if x > 0 else "l"
        return ("leg_" if y >= joints["knee_y"] else "shin_") + side

    def to_rest(points: np.ndarray, normals: np.ndarray, bone: str):
        """Swing an arm bone's points down from the T-pose: about Z, around the arm's root at the torso's edge."""
        if not bone.endswith(("_r", "_l")) or not bone.startswith(("arm", "forearm", "hand")):
            return points, normals
        right = bone.endswith("_r")
        theta = -math.pi / 2 if right else math.pi / 2
        c, s = math.cos(theta), math.sin(theta)
        rot = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
        pivot = np.array([cx + (edge if right else -edge), arm_y, 0.0])
        p = points - pivot
        p = p @ rot.T
        p[:, 0] += (shoulder_out - edge) * (1 if right else -1)
        p += pivot
        return p, normals @ rot.T

    def to_model(points: np.ndarray) -> np.ndarray:
        """World (y up, front -Z, right +X) to model space (pixels, y down, ground 24, front -Z, right -X)."""
        out = np.empty_like(points)
        out[:, 0] = -(points[:, 0] - cx) * scale
        out[:, 1] = 24.0 - (points[:, 1] - y0) * scale
        out[:, 2] = (points[:, 2] - cz) * scale
        return out

    # Bone pivots, in model space (absolute), in the rest pose.
    sx = shoulder_out
    pivots_world = {
        "pelvis": (cx, joints["pelvis_y"], cz), "torso": (cx, joints["torso_y"], cz), "neck": (cx, joints["neck_y"], cz),
        "head": (cx, joints["head_y"], cz),
        "arm_r": (cx + sx, arm_y, cz), "forearm_r": (cx + sx, arm_y - 0.45 * arm_len, cz), "hand_r": (cx + sx, arm_y - 0.82 * arm_len, cz),
        "arm_l": (cx - sx, arm_y, cz), "forearm_l": (cx - sx, arm_y - 0.45 * arm_len, cz), "hand_l": (cx - sx, arm_y - 0.82 * arm_len, cz),
        "coat_front_r": (cx + hip_dx, joints["hip_y"], cz - half * 0.6), "coat_front_l": (cx - hip_dx, joints["hip_y"], cz - half * 0.6),
        "coat_back": (cx, joints["hip_y"], cz + half * 0.6),
        "leg_r": (cx + hip_dx, joints["hip_y"], cz), "shin_r": (cx + hip_dx, joints["knee_y"], cz),
        "leg_l": (cx - hip_dx, joints["hip_y"], cz), "shin_l": (cx - hip_dx, joints["knee_y"], cz),
    }
    pivots = {b: to_model(np.array([v]))[0] for b, v in pivots_world.items()}

    # Triangles per (bone, material); textures with tiling baked in.
    groups: dict[tuple[str, str], list[np.ndarray]] = {}
    materials: dict[str, tuple[bool, bool]] = {}
    tex_dir = Path(N.asset("textures/entity/aberrant/x.png")).parent
    tex_dir.mkdir(parents=True, exist_ok=True)
    for node, mat, wp, wn, uv, idx in prims:
        name = material_name(mat)
        u0, v0 = math.floor(uv[:, 0].min() + 1e-6), math.floor(uv[:, 1].min() + 1e-6)
        nu = max(1, math.ceil(uv[:, 0].max() - 1e-6) - u0)
        nv = max(1, math.ceil(uv[:, 1].max() - 1e-6) - v0)
        uvn = np.stack([(uv[:, 0] - u0) / nu, (uv[:, 1] - v0) / nv], axis=1)
        if name not in materials:
            weather(tiled(texture_for(j, binary, mat), u0, v0, nu, nv), name).save(tex_dir / f"{name}.png")
            glow = "emissiveTexture" in mat and "index" in mat["emissiveTexture"]
            if glow:
                glow_map(tiled(dl_glb.image(j, binary, mat["emissiveTexture"]["index"]), u0, v0, nu, nv)).save(tex_dir / f"{name}_glow.png")
            materials[name] = (glow, mat.get("alphaMode") == "BLEND")
        is_axe = node != "Abberanr"
        for tri in idx:
            centre = wp[tri].mean(axis=0)
            bone = bone_of(centre, is_axe)
            pts, nrm = to_rest(wp[tri], wn[tri], bone)
            m = to_model(pts) - pivots[bone]
            n_model = nrm * np.array([-1.0, -1.0, 1.0])
            row = np.concatenate([m, uvn[tri], n_model], axis=1)  # 3 x 8
            groups.setdefault((bone, name), []).append(row)

    names = list(materials)
    out = io.BytesIO()
    out.write(b"DLMESH1\0")
    out.write(struct.pack(">i", len(names)))
    for n in names:
        glow, _ = materials[n]
        encoded = n.encode("utf-8")
        out.write(struct.pack(">h", len(encoded)))
        out.write(encoded)
        out.write(struct.pack(">?", glow))
    out.write(struct.pack(">i", len(groups)))
    total = 0
    for (bone, name), rows in groups.items():
        data = np.stack(rows).reshape(-1, 8).astype(">f4")
        out.write(struct.pack(">hhi", BONE_INDEX[bone], names.index(name), len(rows)))
        out.write(data.tobytes())
        total += len(rows)
    mesh = Path(N.asset("models/entity/aberrant.bin"))
    mesh.parent.mkdir(parents=True, exist_ok=True)
    mesh.write_bytes(out.getvalue())

    # The rig (no boxes: the mesh is drawn by AberrantMeshLayer at each bone).
    lines = ["package com.deadlands.client.render;", "",
             "import com.deadlands.Deadlands;",
             "import net.minecraft.client.model.geom.ModelLayerLocation;",
             "import net.minecraft.client.model.geom.PartPose;",
             "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
             "import net.minecraft.client.model.geom.builders.LayerDefinition;",
             "import net.minecraft.client.model.geom.builders.MeshDefinition;",
             "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
             "/**",
             " * GENERATED by tools/assetgen/import_aberrant.py - do not edit; change the rig there and rerun it.",
             " *",
             " * <p>The Aberrant's rig: the Asesino rig's bones (so the same animations drive it) with their pivots at the",
             " * imported model's joints, and no boxes: {@code AberrantMeshLayer} draws the mesh at each bone.",
             " */",
             "public final class AberrantGeometry {",
             "\tpublic static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id(\"aberrant\"), \"main\");",
             "\t/** The bones in the mesh file's order. */",
             "\tpublic static final String[] BONES = {" + ", ".join(f'"{b}"' for b, _ in BONES) + "};",
             "\t/** Each bone's parent (\"root\" for the rig's root). */",
             "\tpublic static final String[] PARENTS = {" + ", ".join(f'"{p}"' for _, p in BONES) + "};", "",
             "\tprivate AberrantGeometry() {", "\t}", "",
             "\tpublic static LayerDefinition create() {",
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot().addOrReplaceChild(\"root\", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));"]
    absolute = {"root": np.array([0.0, 24.0, 0.0])}
    for bone, parent in BONES:
        rel = pivots[bone] - absolute[parent]
        absolute[bone] = pivots[bone]
        lines.append(f"\t\tPartDefinition {bone} = {parent}.addOrReplaceChild(\"{bone}\", CubeListBuilder.create(), "
                     f"PartPose.offset({rel[0]:.3f}F, {rel[1]:.3f}F, {rel[2]:.3f}F));")
    lines += ["\t\treturn LayerDefinition.create(mesh, 16, 16);", "\t}", "}", ""]
    JAVA.write_text("\n".join(lines), encoding="utf-8")
    print(f"  aberrant: {total} triangles in {len(groups)} groups, {len(names)} materials, scale {scale:.2f} px/unit")


if __name__ == "__main__":
    build()
