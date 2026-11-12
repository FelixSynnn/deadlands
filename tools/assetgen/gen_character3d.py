"""Phase 3 batch 3: the first 3D character model, Itzbreadloaf.

The characters are drawn with Minecraft's player model and their 64x64 skin. This adds a 3D gear
layer on top, built from boxes on the same body parts so it moves with every animation: a canvas
backpack with a bedroll, shoulder and chest straps, a belt with a hip pouch and a holstered
flashlight, a bandage wrap and a watch, and loose tufts of hair. It sits over his skin's black
outfit instead of replacing it (the skin stays the canon look).

Writes the geometry as Java (`client/render/character/CharacterGear.java`, generated: do not edit)
and its painted 64x64 texture. Each box gets Minecraft's standard box-UV footprint, packed here.
"""
from __future__ import annotations

import numpy as np

import dl_noise as N

ROOT = N.ROOT
JAVA = ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "character" / "CharacterGear.java"
TEX = 64

MATERIALS = {
    "canvas": "#59583b", "pocket": "#4e4d33", "bedroll": "#6d5a46", "strap": "#6e6246", "metal": "#8c8c84",
    "leather": "#3d2c1d", "pouch": "#47462f", "torch": "#1e1e20", "lens": "#d8cfa0", "wrap": "#cfc6ae",
    "watch": "#202022", "hair": "#a27a50",
}

# (part, material, x, y, z, w, h, d, inflate) in each part's own coordinates (Minecraft's player model).
GEAR = [
    ("body", "canvas", -3.5, 1.0, 2.0, 7, 8, 3, 0.0),        # backpack
    ("body", "bedroll", -3.5, -0.6, 2.4, 7, 2, 2, 0.1),      # bedroll on top
    ("body", "pocket", -2.5, 4.5, 5.0, 5, 3, 1, 0.0),        # front pocket of the pack
    ("body", "strap", -3.0, 0.0, -2.5, 1, 9, 1, 0.0),        # shoulder straps, front
    ("body", "strap", 2.0, 0.0, -2.5, 1, 9, 1, 0.0),
    ("body", "strap", -3.0, -0.4, -2.5, 1, 1, 5, 0.0),       # over the shoulders to the pack
    ("body", "strap", 2.0, -0.4, -2.5, 1, 1, 5, 0.0),
    ("body", "strap", -2.0, 4.0, -2.7, 4, 1, 1, 0.0),        # chest strap
    ("body", "metal", -0.5, 3.9, -2.9, 1, 1, 1, 0.0),        # its buckle
    ("body", "leather", -4.0, 10.0, -2.0, 8, 2, 4, 0.35),    # belt
    ("body", "metal", -1.0, 10.1, -2.7, 2, 2, 1, 0.0),       # buckle
    ("body", "pouch", -4.9, 9.6, -1.8, 1, 3, 3, 0.0),        # hip pouch (right side)
    ("body", "leather", 4.0, 10.0, -1.0, 1, 3, 2, 0.0),      # flashlight holster (left side)
    ("body", "torch", 4.1, 8.4, -0.5, 1, 2, 1, 0.0),         # the flashlight in it
    ("right_arm", "wrap", -3.0, 7.0, -2.0, 4, 2, 4, 0.2),    # bandage round the forearm
    ("left_arm", "watch", -1.0, 8.0, -2.0, 4, 1, 4, 0.15),   # watch
    ("head", "hair", -3.0, -9.1, -3.0, 3, 1, 3, 0.0),        # loose tufts of hair
    ("head", "hair", 0.2, -9.3, -2.2, 3, 1, 3, 0.0),
    ("head", "hair", -2.0, -9.0, 0.5, 4, 1, 3, 0.0),
    ("head", "hair", -4.0, -8.2, -4.7, 3, 2, 1, 0.0),        # fringe over the forehead
    ("head", "hair", 1.0, -8.2, -4.7, 2, 1, 1, 0.0),
]

PIVOTS = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
          "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}


def footprint(w, h, d):
    return 2 * (w + d), d + h


def pack(sizes):
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    pos = [None] * len(sizes)
    x = y = shelf = 0
    for i in order:
        fw, fh = sizes[i]
        if x + fw > TEX:
            x, y, shelf = 0, y + shelf, 0
        assert y + fh <= TEX, "gear texture full"
        pos[i] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    return pos


def paint_box(tex, u, v, w, h, d, colour, rng):
    base = np.array(N.hex_rgb(colour))
    fw, fh = footprint(w, h, d)
    noise = rng.normal(0, 0.07, (fh, fw, 1))
    region = np.clip(base * (1 + noise), 0, 255)
    # Darker seams round each face of the box-UV layout.
    faces = [(d, 0, w, d), (d + w, 0, w, d), (0, d, d, h), (d, d, w, h), (d + w, d, d, h), (2 * d + w, d, w, h)]
    for fx, fy, fw2, fh2 in faces:
        if fw2 >= 2 and fh2 >= 2:
            region[fy, fx:fx + fw2] *= 0.82
            region[fy + fh2 - 1, fx:fx + fw2] *= 0.82
            region[fy:fy + fh2, fx] *= 0.82
            region[fy:fy + fh2, fx + fw2 - 1] *= 0.82
    tex[v:v + fh, u:u + fw, :3] = region
    tex[v:v + fh, u:u + fw, 3] = 255


def build() -> None:
    rng = np.random.default_rng(9501)
    sizes = [footprint(w, h, d) for _, _, _, _, _, w, h, d, _ in GEAR]
    pos = pack(sizes)
    tex = np.zeros((TEX, TEX, 4))
    for (part, mat, x, y, z, w, h, d, inf), (u, v) in zip(GEAR, pos):
        paint_box(tex, u, v, w, h, d, MATERIALS[mat], rng)
        if mat == "torch":  # the lens at the flashlight's top
            tex[v:v + d, u + d:u + d + w, :3] = N.hex_rgb(MATERIALS["lens"])
    N.save_png(N.asset("textures/entity/survivor/character/itzbreadloaf_gear.png"), tex[..., :3], tex[..., 3] / 255.0)

    def cubes(part):
        out = []
        for (p, mat, x, y, z, w, h, d, inf), (u, v) in zip(GEAR, pos):
            if p == part:
                out.append(f".texOffs({u}, {v}).addBox({x}F, {y}F, {z}F, {w}F, {h}F, {d}F, new CubeDeformation({inf}F))")
        return "CubeListBuilder.create()" + "".join("\n\t\t\t" + c for c in out)

    def pose(part):
        px, py, pz = PIVOTS[part]
        return "PartPose.ZERO" if (px, py, pz) == (0, 0, 0) else f"PartPose.offset({px}F, {py}F, {pz}F)"

    java = f"""package com.deadlands.client.render.character;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 3D gear for the characters, on the parts of Minecraft's player model (so it follows every pose).
 * Generated by tools/assetgen/gen_character3d.py: do not edit; change the generator and rerun it.
 */
public final class CharacterGear {{
	private CharacterGear() {{
	}}

	/** Itzbreadloaf: backpack and bedroll, straps, belt with pouch and holstered flashlight, wrap, watch, hair. */
	public static LayerDefinition itzbreadloaf() {{
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", {cubes("head")}, {pose("head")});
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", {cubes("body")}, {pose("body")});
		root.addOrReplaceChild("right_arm", {cubes("right_arm")}, {pose("right_arm")});
		root.addOrReplaceChild("left_arm", {cubes("left_arm")}, {pose("left_arm")});
		root.addOrReplaceChild("right_leg", {cubes("right_leg")}, {pose("right_leg")});
		root.addOrReplaceChild("left_leg", {cubes("left_leg")}, {pose("left_leg")});
		return LayerDefinition.create(mesh, {TEX}, {TEX});
	}}
}}
"""
    JAVA.parent.mkdir(parents=True, exist_ok=True)
    JAVA.write_text(java, encoding="utf-8", newline="\n")
    print(f"  character gear: {len(GEAR)} boxes for Itzbreadloaf")


if __name__ == "__main__":
    build()
