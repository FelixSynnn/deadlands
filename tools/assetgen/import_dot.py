"""D.O.T. (Deceased On Transfer), Patient-4: the owner's model, rigged and given an original texture.

Source: ~/Downloads/Sounds + Models For DOT/D.O.T.glb (skipped when missing, keeping what the mod has). The model
is built of separate parts (head, torso, arms, legs, helmet, crown, armour, shoulder plates, leggings, a blade and
two cloak wings), so each part goes to one bone of a simple humanoid rig (pelvis, torso, head, arms, legs, the two
cloak halves, the blade in the left hand). No triangles are dropped.

The texture is painted here, not copied: every texel's colour is worked out from where it sits on the body (the
source's grey texture only adds its sculpted detail). The broad colour direction follows the owner's reference:
deep navy above, sliding to a dark poisoned green toward the hems and the cloak, with neon-green light. The details
are DEADLANDS' own: glitch scanlines, circuit traces etched on the armour, flame outlines licking up the legs, veins
in the cloak, and a white square halo. Glowing parts go to a separate emissive layer.

Writes models/entity/dot.bin, textures/entity/dot/model/*.png (+ _glow.png) and DotGeometry.java.
"""
from __future__ import annotations

import io
import struct
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

import dl_glb
import dl_noise as N

SOURCE = Path.home() / "Downloads" / "Sounds + Models For DOT" / "D.O.T.glb"
HEIGHT_PX = 44.0
TEX = 256
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "DotGeometry.java"

# The palette (hex): reported in the progress doc.
NAVY = "#101C3C"
NAVY_DEEP = "#0A1126"
TEAL = "#103036"
POISON = "#0D3A22"
CLOAK = "#0B2614"
CLOAK_TIP = "#1B5A2E"
STEEL = "#1A2230"
VOID = "#04060A"
CROWN = "#D9F2E3"
GLOW = "#3DFF6E"
GLOW_WHITE = "#ECFFF4"
PALETTE = {"navy": NAVY, "navy_deep": NAVY_DEEP, "teal": TEAL, "poison": POISON, "cloak": CLOAK, "cloak_tip": CLOAK_TIP,
           "steel": STEEL, "void": VOID, "crown": CROWN, "glow": GLOW, "glow_white": GLOW_WHITE}

# bone, parent, pivot in source units (y up, x toward his back, z toward his left; studs)
RIG = [
    ("pelvis", "root", (0.0, -1.0, 0.0)),
    ("torso", "pelvis", (0.0, -1.0, 0.0)),
    ("head", "torso", (0.0, 1.0, 0.0)),
    ("arm_r", "torso", (0.0, 0.85, -1.5)),
    ("arm_l", "torso", (0.0, 0.85, 1.5)),
    ("blade", "arm_l", (0.0, -0.1, 3.3)),
    ("leg_r", "pelvis", (0.0, -1.0, -0.5)),
    ("leg_l", "pelvis", (0.0, -1.0, 0.5)),
    ("cloak_r", "torso", (0.5, 0.8, -0.4)),
    ("cloak_l", "torso", (0.5, 0.8, 0.4)),
]
BONES = [b for b, _, _ in RIG]
PART_BONE = {"torso1": "torso", "armor1": "torso", "head1": "head", "helmet1": "head", "crown1": "head",
             "arml1": "arm_l", "armorl1": "arm_l", "armr1": "arm_r", "armorr1": "arm_r", "legl1": "leg_l",
             "leggingsl1": "leg_l", "legr1": "leg_r", "leggingsr1": "leg_r", "wingl1": "cloak_l", "wingr1": "cloak_r",
             "sword1": "blade"}


def rgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def mix(a, b, t):
    t = np.clip(t, 0.0, 1.0)[..., None]
    return a * (1.0 - t) + b * t


def hash3(p: np.ndarray, salt: int) -> np.ndarray:
    """A repeatable 0-1 value per 3D cell."""
    q = np.floor(p).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791) ^ (salt * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def paint(part: str, p: np.ndarray, n: np.ndarray, detail: np.ndarray):
    """Colour (RGB) and glow (RGB, strength 0-1) for texels at source positions p with normals n."""
    x, y, z = p[..., 0] * 100.0, p[..., 1] * 100.0, p[..., 2] * 100.0  # back to studs
    front = n[..., 0] < -0.45
    height = np.clip((y + 3.0) / 6.0, 0.0, 1.0)            # 0 at his feet, 1 at the crown
    # The gradient: navy high, poison green low.
    base = mix(rgb(POISON), rgb(NAVY), height ** 0.8)
    glow = np.zeros(p.shape[:-1] + (3,))
    strength = np.zeros(p.shape[:-1])

    def light(mask, colour, s):
        nonlocal strength
        glow[mask] = rgb(colour)
        strength = np.where(mask, np.maximum(strength, s), strength)

    base = base * np.ones(p.shape[:-1] + (1,))
    if part in ("torso1", "armor1"):
        base = mix(rgb(NAVY_DEEP), rgb(TEAL), np.clip((1.0 - height) * 1.4, 0, 1))
        # Circuit traces etched in the plates.
        cx, cy, cz = np.abs(np.modf(z * 5.0)[0]), np.abs(np.modf(y * 5.0)[0]), np.abs(np.modf(x * 5.0)[0])
        gate = hash3(np.stack([z * 5.0, y * 5.0, x * 5.0], -1), 3) > 0.45
        trace = ((cx < 0.06) | (cy < 0.06) | (cz < 0.06)) & gate
        base = np.where(trace[..., None], mix(base, rgb(TEAL) * 1.8, 0.55), base)
        # A white ring on his chest, a lens that never shuts.
        r = np.sqrt((y - 0.35) ** 2 + z ** 2)
        light(front & (r > 0.22) & (r < 0.34), GLOW_WHITE, 1.0)
        light(front & (r < 0.1), GLOW, 0.8)
        light((y < -0.95) & (np.abs(np.modf(z * 3.0)[0]) < 0.12), GLOW, 0.7)   # the hem, in dashes
    elif part in ("head1", "helmet1"):
        base = mix(rgb(NAVY_DEEP), rgb(NAVY), detail * 0.4)
        inside = (front & (np.abs(z) < 0.5) & (y > 1.05) & (y < 2.0))
        base = np.where(inside[..., None], rgb(VOID), base)      # no face: a hood with nothing in it
        light(front & (np.abs(np.abs(z) - 0.25) < 0.08) & (np.abs(y - 1.58) < 0.06), GLOW, 1.0)   # two slits
        light((y < 0.95) & (np.abs(np.modf(x * 4.0 + z * 4.0)[0]) < 0.25), GLOW, 0.6)            # the hood's rim
    elif part == "crown1":
        base = rgb(CROWN) * np.ones_like(p)
        light(np.ones(p.shape[:-1], bool), GLOW_WHITE, 0.95)
    elif part in ("arml1", "armr1", "armorl1", "armorr1"):
        base = mix(rgb(NAVY), rgb(TEAL), np.clip(-y * 0.5 + 0.3, 0, 1))
        hand = y < -0.5
        light(hand, GLOW, np.clip((-0.5 - y) * 2.0, 0.5, 1.0))   # the hands burn green
        if part.startswith("armor"):
            light(np.abs(z) > 2.3, GLOW, 0.75)                        # the shoulder plates' outer edges
    elif part in ("legl1", "legr1", "leggingsl1", "leggingsr1"):
        base = mix(rgb(NAVY_DEEP), rgb(NAVY), 0.5 + 0.2 * np.sin(z * 9.0))
        # Flames licking up from the boots: outlines only.
        top = -2.35 + 0.42 * np.abs(np.sin(z * 7.0 + x * 5.0)) + 0.18 * np.sin(z * 19.0)
        light(np.abs(y - top) < 0.045, GLOW, 1.0)
        base = np.where((y < top)[..., None], mix(base, rgb(POISON), 0.6), base)
        light(y < -2.92, GLOW, 0.8)                                   # the soles
    elif part == "sword1":
        base = mix(rgb(STEEL), rgb(NAVY), detail * 0.5)
        light(np.abs(y) > 0.27, GLOW, 1.0)                            # the edges
        light(np.abs(np.modf(x * 1.5)[0]) < 0.04, GLOW, 0.5)          # notches of light along it
    elif part in ("wingl1", "wingr1"):
        reach = np.clip((x - 0.5) / 3.2, 0, 1) * 0.6 + np.clip((y + 0.7) / 4.8, 0, 1) * 0.4
        base = mix(rgb(CLOAK), rgb(CLOAK_TIP), reach ** 1.5)
        vein = np.abs(np.sin(y * 6.0 + x * 2.0) * np.cos(z * 3.0)) < 0.06
        light(vein & (reach > 0.3), GLOW, 0.55)
        light(reach > 0.88, GLOW, 1.0)                                # the torn tips
    # Glitch scanlines: thin bands of brighter and darker colour, as if he were never quite here.
    band = hash3(np.stack([np.zeros_like(y), y * 18.0, np.zeros_like(y)], -1), 11)
    base = base * (1.0 + 0.10 * (band - 0.5))[..., None]
    base = base * (0.72 + 0.56 * detail[..., None])
    return np.clip(base, 0, 255), glow, strength


def bake(j, binary, prims_by_material):
    """Rasterise every triangle into its material's texture, painting by 3D position."""
    tex_dir = Path(N.asset("textures/entity/dot/model/x.png")).parent
    tex_dir.mkdir(parents=True, exist_ok=True)
    for name, (mat, items) in prims_by_material.items():
        part = name.replace("mtl", "")
        pbr = mat.get("pbrMetallicRoughness", {})
        src = dl_glb.image(j, binary, pbr["baseColorTexture"]["index"]) if "baseColorTexture" in pbr else Image.new("RGB", (4, 4), (160, 160, 160))
        src = np.asarray(src.convert("L").resize((TEX, TEX), Image.LANCZOS), dtype=np.float64) / 255.0
        src = (src - src.mean()) / max(src.std(), 1e-3) * 0.18 + 0.5       # its sculpt detail, centred
        colour = np.zeros((TEX, TEX, 3))
        glow = np.zeros((TEX, TEX, 4))
        filled = np.zeros((TEX, TEX), bool)
        for pos, nor, uv, tris in items:
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
                inside = (w0 >= -0.08) & (w1 >= -0.08) & (w2 >= -0.08)   # a little bleed past the edges
                if not inside.any():
                    continue
                w = np.stack([w0, w1, w2], -1)[inside]
                pts = w @ pos[t]
                nrm = w @ nor[t]
                ys, xs = (gy[inside] - 0.5).astype(int), (gx[inside] - 0.5).astype(int)
                col, gl, s = paint(part, pts, nrm, src[ys, xs])
                colour[ys, xs] = col
                glow[ys, xs, :3] = gl
                glow[ys, xs, 3] = s * 255.0
                filled[ys, xs] = True
        colour[~filled] = rgb(NAVY_DEEP)
        alpha = np.full((TEX, TEX, 1), 255.0)
        Image.fromarray(np.concatenate([colour, alpha], -1).astype(np.uint8), "RGBA").save(tex_dir / f"{name}.png")
        Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), "RGBA").save(tex_dir / f"{name}_glow.png")


def build() -> None:
    if not SOURCE.exists():
        print(f"  dot: {SOURCE.name} not found; kept what the mod has")
        return
    j, binary, prims = dl_glb.primitives(SOURCE)
    allp = np.concatenate([p[2] for p in prims])
    y0, y1 = allp[:, 1].min(), allp[:, 1].max()
    scale = HEIGHT_PX / (y1 - y0)

    def to_model(p):
        # Source: y up, x toward his back, z toward his left. Model space: y down, front is -Z, his right is -X.
        out = np.empty_like(p, dtype=np.float64)
        out[..., 0] = p[..., 2] * scale
        out[..., 1] = 24.0 - (p[..., 1] - y0) * scale
        out[..., 2] = p[..., 0] * scale
        return out

    pivots = {"root": np.array([0.0, 24.0, 0.0])}
    for bone, _, at in RIG:
        pivots[bone] = to_model(np.array(at) * 0.01)

    by_material = {}
    groups: dict[tuple[str, str], list[np.ndarray]] = {}
    for node, mat, pos, nor, uv, tris in prims:
        name = mat["name"].lower()
        part = name.replace("mtl", "")
        bone = PART_BONE[part]
        by_material.setdefault(name, (mat, []))[1].append((pos, nor, uv, tris))
        mp = to_model(pos)
        for t in tris:
            p3 = mp[t]
            nrm = np.cross(p3[1] - p3[0], p3[2] - p3[0])
            ln = np.linalg.norm(nrm)
            nrm = nrm / ln if ln > 0 else np.array([0.0, -1.0, 0.0])
            rows = np.concatenate([p3 - pivots[bone], uv[t], np.repeat(nrm[None], 3, 0)], axis=1)
            groups.setdefault((bone, name), []).append(rows)
    bake(j, binary, by_material)

    names = list(by_material)
    out = io.BytesIO()
    out.write(b"DLMESH1\0")
    out.write(struct.pack(">i", len(names)))
    for n in names:
        encoded = n.encode("utf-8")
        out.write(struct.pack(">h", len(encoded)))
        out.write(encoded)
        out.write(struct.pack(">?", True))
    out.write(struct.pack(">i", len(groups)))
    count = 0
    for (bone, name), rows in groups.items():
        data = np.stack(rows).reshape(-1, 8).astype(">f4")
        out.write(struct.pack(">hhi", BONES.index(bone), names.index(name), len(rows)))
        out.write(data.tobytes())
        count += len(rows)
    mesh = Path(N.asset("models/entity/dot.bin"))
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
    print(f"  dot: {count} triangles, {len(groups)} groups, {len(names)} materials, scale {scale:.2f} px per stud")


if __name__ == "__main__":
    build()
