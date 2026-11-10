"""The infected (Milestone 4): model rigs, visual variants, textures, spawn eggs, loot and names.

One description drives both sides, so they can never disagree:

* RIGS: the two jointed models (HUMANOID, used by seven kinds with per-kind proportions, gear and
  poses, and BRUTE). From them this writes the Java layer definitions
  (src/client/.../render/infected/InfectedGeometry.java), packing every cube's box UVs into the
  texture automatically, and paints each variant's texture into exactly those regions.
* VARIANTS: 58 looks across the nine kinds (outfit, skin, hair, damage, optional gear). From them
  this writes src/main/.../entity/InfectedVariants.java (names, loot outfit group, optional parts,
  preferred biomes) and the textures/entity/infected/<kind>/<variant>.png images.

Textures are painted at two texels per model pixel (the layer definitions declare 128x64; images
are 256x128), the density of DEADLANDS block textures. Generated files carry a "do not edit" header.
"""
from __future__ import annotations

import zlib

import numpy as np

import dl_data as D
import dl_noise as N

NS = D.NS
S = 2  # texels per model pixel
TEX_W, TEX_H = 128, 64  # declared texture size of both rigs
JAVA_CLIENT = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "infected"
JAVA_MAIN = N.ROOT / "src" / "main" / "java" / "com" / "deadlands" / "entity"

# Optional part flags (must match InfectedVariants constants, written below).
FLAGS = {"helmet": 1, "vest": 2, "belt": 4, "mask": 8, "cap": 16, "claws": 32, "belly": 64, "growths": 128,
         "no_left_forearm": 256, "no_right_forearm": 512, "no_shins": 1024, "tatters": 2048, "backpack": 4096}


# ============================================================================= rigs

class Part:
    def __init__(self, name, parent, pivot, cubes, role, optional=None, rot=(0.0, 0.0, 0.0)):
        self.name, self.parent, self.pivot, self.cubes, self.role, self.optional, self.rot = name, parent, pivot, cubes, role, optional, rot
        self.uv = []  # (u, v) per cube, filled by the packer


def humanoid_rig() -> list[Part]:
    P = Part
    return [
        P("body", None, (0, 12, 0), [], None),
        P("torso", "body", (0, 0, 0), [(-4, -6, -2, 8, 6, 4)], "torso"),
        P("belly", "torso", (0, 0, 0), [(-4.5, -6.5, -5.5, 9, 7, 5)], "belly", "belly"),
        P("belt", "torso", (0, 0, 0), [(-4.5, -1.5, -2.5, 9, 2, 5)], "belt", "belt"),
        P("front_tatter", "torso", (0, 0, -2.05), [(-4, 0, 0, 8, 3, 0)], "tatter", "tatters"),
        P("back_tatter", "torso", (0, 0, 2.05), [(-4, 0, 0, 8, 3, 0)], "tatter", "tatters"),
        P("chest", "torso", (0, -6, 0), [(-4, -6, -2, 8, 6, 4)], "chest"),
        P("vest", "chest", (0, 0, 0), [(-4.5, -6.25, -2.5, 9, 6, 5)], "vest", "vest"),
        P("backpack", "chest", (0, 0, 0), [(-3.5, -5.5, 2, 7, 6, 3)], "backpack", "backpack"),
        P("growths", "chest", (0, 0, 0), [(1.5, -5, -2.6, 3, 3, 1), (-4.6, -3, -1, 1, 2, 3)], "growth", "growths"),
        P("head", "chest", (0, -6, 0), [(-4, -8, -4, 8, 6, 8)], "head"),
        P("jaw", "head", (0, -2, 3), [(-3.5, 0, -7, 7, 2, 7)], "jaw"),
        P("helmet", "head", (0, 0, 0), [(-4.5, -8.6, -4.5, 9, 4, 9)], "helmet", "helmet"),
        P("cap", "head", (0, 0, 0), [(-4.5, -8.75, -4.5, 9, 3, 9)], "cap", "cap"),
        P("brim", "head", (0, 0, 0), [(-4, -6, -7.5, 8, 1, 3)], "cap", "cap"),
        P("mask", "head", (0, 0, 0), [(-3, -4.5, -5.5, 6, 4, 2)], "mask", "mask"),
        P("right_arm", "chest", (-5, -5, 0), [(-2, -1, -2, 4, 6, 4)], "upper_arm"),
        P("right_forearm", "right_arm", (0, 5, 0), [(-2, 0, -2, 4, 6, 4)], "forearm", "no_right_forearm"),
        P("right_claws", "right_forearm", (0, 6, 0), [(-2, 0, -1, 4, 3, 1)], "claw", "claws"),
        P("left_arm", "chest", (5, -5, 0), [(-2, -1, -2, 4, 6, 4)], "upper_arm"),
        P("left_forearm", "left_arm", (0, 5, 0), [(-2, 0, -2, 4, 6, 4)], "forearm", "no_left_forearm"),
        P("left_claws", "left_forearm", (0, 6, 0), [(-2, 0, -1, 4, 3, 1)], "claw", "claws"),
        P("right_leg", "body", (-2, 0, 0), [(-2, 0, -2, 4, 6, 4)], "thigh"),
        P("right_shin", "right_leg", (0, 6, 0), [(-2, 0, -2, 4, 6, 4)], "shin", "no_shins"),
        P("left_leg", "body", (2, 0, 0), [(-2, 0, -2, 4, 6, 4)], "thigh"),
        P("left_shin", "left_leg", (0, 6, 0), [(-2, 0, -2, 4, 6, 4)], "shin", "no_shins"),
    ]


def brute_rig() -> list[Part]:
    P = Part
    return [
        P("body", None, (0, 10, 0), [], None),
        P("torso", "body", (0, 0, 0), [(-7, -8, -4.5, 14, 8, 9)], "torso"),
        P("chest", "torso", (0, -8, -1), [(-8, -10, -5, 16, 10, 10)], "chest"),
        P("head", "chest", (0, -9, -4), [(-4, -7, -4.5, 8, 6, 8)], "head"),
        P("jaw", "head", (0, -1, 2.5), [(-3.5, 0, -7, 7, 2, 7)], "jaw"),
        P("helmet", "head", (0, 0, 0), [(-4.5, -7.6, -5, 9, 4, 9)], "helmet", "helmet"),
        P("right_arm", "chest", (-10, -8, 0), [(-3, -2, -3, 6, 10, 6)], "upper_arm"),
        P("right_forearm", "right_arm", (0, 8, 0), [(-3.5, 0, -3.5, 7, 10, 7)], "forearm"),
        P("left_arm", "chest", (10, -8, 0), [(-3, -2, -3, 6, 10, 6)], "upper_arm"),
        P("left_forearm", "left_arm", (0, 8, 0), [(-3.5, 0, -3.5, 7, 10, 7)], "forearm"),
        P("right_leg", "body", (-4, 0, 0), [(-3, 0, -3, 6, 7, 6)], "thigh"),
        P("right_shin", "right_leg", (0, 7, 0), [(-3, 0, -3, 6, 7, 6)], "shin"),
        P("left_leg", "body", (4, 0, 0), [(-3, 0, -3, 6, 7, 6)], "thigh"),
        P("left_shin", "left_leg", (0, 7, 0), [(-3, 0, -3, 6, 7, 6)], "shin"),
    ]


def footprint(cube) -> tuple[int, int]:
    _, _, _, w, h, d = cube
    return int(2 * d + 2 * w), int(d + h)


def pack(parts: list[Part]) -> None:
    """Shelf-pack every cube's box-UV footprint into the declared texture."""
    items = [(footprint(c), p, i) for p in parts for i, c in enumerate(p.cubes)]
    items.sort(key=lambda t: (-t[0][1], -t[0][0]))
    for p in parts:
        p.uv = [None] * len(p.cubes)
    x = y = shelf = 0
    for (fw, fh), p, i in items:
        if x + fw > TEX_W:
            x, y = 0, y + shelf
            shelf = 0
        assert y + fh <= TEX_H, f"texture full at {p.name}"
        p.uv[i] = (x, y)
        x += fw
        shelf = max(shelf, fh)


def face_rects(u, v, cube) -> dict[str, tuple[int, int, int, int]]:
    """Image-pixel rects (x, y, w, h) of a cube's faces, matching ModelPart.Cube's UV layout."""
    _, _, _, w, h, d = (int(c) if i >= 3 else c for i, c in enumerate(cube))
    r = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
         "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    return {k: (int(a * S), int(b * S), int(c * S), int(e * S)) for k, (a, b, c, e) in r.items()}


def java_float(v) -> str:
    return f"{float(v)}F"


def java_layer(method: str, parts: list[Part]) -> str:
    lines = [f"\tpublic static LayerDefinition {method}() {{",
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot();"]
    for p in parts:
        builder = "CubeListBuilder.create()"
        for (u, v), c in zip(p.uv, p.cubes):
            x, y, z, w, h, d = c
            builder += f".texOffs({u}, {v}).addBox({java_float(x)}, {java_float(y)}, {java_float(z)}, {java_float(w)}, {java_float(h)}, {java_float(d)})"
        px, py, pz = p.pivot
        rx, ry, rz = p.rot
        pose = f"PartPose.offsetAndRotation({java_float(px)}, {java_float(py)}, {java_float(pz)}, {java_float(rx)}, {java_float(ry)}, {java_float(rz)})"
        parent = "root" if p.parent is None else p.parent
        lines.append(f"\t\tPartDefinition {p.name} = {parent}.addOrReplaceChild(\"{p.name}\", {builder}, {pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    lines.append("\t}")
    return "\n".join(lines)


def write_geometry(rigs: dict[str, list[Part]]) -> None:
    body = "\n\n".join(java_layer(name, parts) for name, parts in rigs.items())
    src = f"""package com.deadlands.client.render.infected;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/assetgen/gen_infected.py - do not edit; change the rigs there and rerun it.
 *
 * <p>The two infected rigs. Cube UVs are packed by the generator, which paints every variant
 * texture into the same regions. Model space: y down, the feet at y = 24, the front facing -Z.
 */
@SuppressWarnings("unused")
public final class InfectedGeometry {{
	private InfectedGeometry() {{
	}}

{body}
}}
"""
    JAVA_CLIENT.mkdir(parents=True, exist_ok=True)
    (JAVA_CLIENT / "InfectedGeometry.java").write_text(src, encoding="utf-8")


# ============================================================================= variants

SKINS = {
    "ashen": "#8f8a7c", "greygreen": "#7d8670", "pale": "#a49e8c", "sallow": "#9a8f6a",
    "dark": "#5e5448", "bluish": "#7a8088", "brown": "#6e5e4c",
}
HAIR = {"black": "#1d1a17", "brown": "#3e2e20", "blonde": "#8a7a52", "grey": "#77746c", "ginger": "#6a3a20", "none": None}


def outfit(top, bottom, sleeves="long", legs="long", shoes="#2a2622", detail=None, flags=(), group="civilian", biomes=()):
    return {"top": top, "bottom": bottom, "sleeves": sleeves, "legs": legs, "shoes": shoes, "detail": detail,
            "flags": list(flags), "group": group, "biomes": list(biomes)}


def F(colour, pattern="plain", second=None):
    return {"colour": colour, "pattern": pattern, "second": second}


WALKER_OUTFITS = {
    "tshirt_jeans": outfit(F("#6a3a34"), F("#3a4a62", "denim"), sleeves="short", detail="logo"),
    "flannel": outfit(F("#7a2a24", "plaid", "#2a2a2a"), F("#5a5040"), detail=None, group="farmer", biomes=("rural_countryside", "dead_pine_valley")),
    "office_suit": outfit(F("#2e3240", "suit"), F("#2e3240", "suit"), shoes="#151515", detail="tie", group="office", biomes=("overgrown_suburbs",)),
    "hoodie": outfit(F("#4a5048", "knit"), F("#3a4a62", "denim"), detail="pocket"),
    "scrubs": outfit(F("#3a6a6a", "scrubs"), F("#3a6a6a", "scrubs"), sleeves="short", shoes="#d8d4c8", detail="vneck", group="medical"),
    "overalls": outfit(F("#6a3a2a", "plaid", "#3a2a1a"), F("#3e5070", "denim"), detail="bib", group="farmer",
                       biomes=("abandoned_farmland", "rural_countryside")),
    "police": outfit(F("#22283a"), F("#1e2232"), shoes="#111111", detail="badge", flags=("cap", "belt"), group="police"),
    "hiviz_worker": outfit(F("#c8661e", "hiviz"), F("#6a5a40"), shoes="#3a2e20", flags=("helmet",), group="worker",
                           biomes=("industrial_wasteland",)),
    "sundress": outfit(F("#8a6a7a", "floral", "#d8c8a0"), F("#8a6a7a", "floral", "#d8c8a0"), sleeves="none", legs="short", shoes="#5a3a2a"),
    "tank_shorts": outfit(F("#8a8470"), F("#4a5a6a", "denim"), sleeves="none", legs="short", group="athlete", biomes=("dry_wasteland",)),
    "winter_parka": outfit(F("#3a4a3a", "padded"), F("#3a4a62", "denim"), shoes="#2a2018", flags=("backpack",), group="winter",
                           biomes=("frozen_outskirts",)),
    "pajamas": outfit(F("#7a8a9a", "stripes", "#c8ccd0"), F("#7a8a9a", "stripes", "#c8ccd0"), shoes="#6a6a6a"),
}

RUNNER_OUTFITS = {
    "track_suit": outfit(F("#2a3a6a", "stripes", "#c8c8c8"), F("#2a3a6a"), group="athlete"),
    "jogger": outfit(F("#c8c0a8"), F("#2a2a2a"), sleeves="none", legs="short", group="athlete"),
    "torn_tee": outfit(F("#5a6a4a"), F("#3a4a62", "denim"), sleeves="short", flags=("tatters",)),
    "graphic_tee": outfit(F("#2a2a2a"), F("#3a3a4a", "denim"), sleeves="short", detail="logo"),
    "delivery": outfit(F("#5a3e22"), F("#5a3e22"), sleeves="short", detail="badge", group="worker"),
    "cyclist": outfit(F("#c8a020", "stripes", "#2a2a2a"), F("#1a1a1a"), legs="short", group="athlete"),
    "jersey": outfit(F("#7a1e1e", "stripes", "#d8d0c0"), F("#7a1e1e"), sleeves="none", legs="short", group="athlete"),
    "hoodie_torn": outfit(F("#6a2a2a", "knit"), F("#2a2a2a"), flags=("tatters",)),
}

BRUTE_OUTFITS = {
    "construction": outfit(F("#c8661e", "hiviz"), F("#4a4a3a"), sleeves="short", flags=("helmet",), group="worker"),
    "bouncer": outfit(F("#1a1a1a"), F("#2a2a2e"), sleeves="short", group="civilian"),
    "prisoner": outfit(F("#c86a1e"), F("#c86a1e"), sleeves="short", detail="number", group="prisoner"),
    "butcher": outfit(F("#c8c4b8", "apron", "#8a7a6a"), F("#3a3a3a"), sleeves="short", group="butcher"),
    "farmer": outfit(F("#5a3a2a", "plaid", "#2a2018"), F("#3e5070", "denim"), detail="bib", group="farmer"),
}

SCREAMER_OUTFITS = {
    "hospital_gown": outfit(F("#8aa2b0", "dots", "#5a7a8a"), F("#8aa2b0", "dots", "#5a7a8a"), sleeves="short", legs="short", shoes="#8f8a7c",
                            group="medical"),
    "nightgown": outfit(F("#c8c4b4"), F("#c8c4b4"), sleeves="none", legs="short", shoes="#8f8a7c"),
    "rags": outfit(F("#5a5040"), F("#4a4234"), sleeves="none", legs="short", shoes="#7a7466", flags=("tatters",)),
    "lab_coat": outfit(F("#d8d6cc", "coat"), F("#3a3a40"), detail="pocket", group="lab"),
}

MILITARY_OUTFITS = {
    "woodland_a": outfit(F("#4a5236", "camo_woodland"), F("#4a5236", "camo_woodland"), shoes="#1a1814", flags=("helmet", "vest", "belt", "backpack"),
                         group="military"),
    "woodland_b": outfit(F("#4a5236", "camo_woodland"), F("#4a5236", "camo_woodland"), shoes="#1a1814", flags=("helmet", "vest", "belt"),
                         group="military"),
    "desert_a": outfit(F("#a08a62", "camo_desert"), F("#a08a62", "camo_desert"), shoes="#6a5a40", flags=("helmet", "vest", "belt", "backpack"),
                       group="military", biomes=("dry_wasteland",)),
    "desert_b": outfit(F("#a08a62", "camo_desert"), F("#a08a62", "camo_desert"), shoes="#6a5a40", flags=("helmet", "belt"), group="military",
                       biomes=("dry_wasteland",)),
    "urban": outfit(F("#6a6a6e", "camo_urban"), F("#6a6a6e", "camo_urban"), shoes="#1a1a1a", flags=("helmet", "vest", "belt"), group="military",
                    biomes=("industrial_wasteland",)),
    "hazmat": outfit(F("#b8a22a", "rubber"), F("#b8a22a", "rubber"), shoes="#1a1a1a", flags=("mask",), group="hazmat"),
    "riot": outfit(F("#1e2026"), F("#1e2026"), shoes="#111111", flags=("helmet", "vest", "belt"), group="riot"),
    "medic": outfit(F("#4a5236", "camo_woodland"), F("#4a5236", "camo_woodland"), shoes="#1a1814", detail="armband", flags=("helmet", "belt", "backpack"),
                    group="military"),
}

CRAWLER_OUTFITS = {
    "tshirt": outfit(F("#4a5a6a"), F("#3a4a62", "denim"), sleeves="short", flags=("no_shins", "tatters")),
    "flannel": outfit(F("#5a2a24", "plaid", "#2a2a2a"), F("#5a5040"), flags=("tatters",), group="farmer"),
    "office": outfit(F("#c8c4b8"), F("#2e3240", "suit"), detail="tie", flags=("no_shins",), group="office"),
    "scrubs": outfit(F("#5a6a8a", "scrubs"), F("#5a6a8a", "scrubs"), sleeves="short", detail="vneck", flags=("tatters",), group="medical"),
    "hoodie": outfit(F("#2a3a2a", "knit"), F("#2a2a2a"), flags=("no_shins", "no_left_forearm")),
    "overalls": outfit(F("#4a4a3a"), F("#3e5070", "denim"), detail="bib", flags=("tatters",), group="farmer"),
}

SPECIAL = {
    "bloater": {"bloated": outfit(F("#6a6a5a"), F("#3a4a62", "denim"), sleeves="short", flags=("belly", "growths", "tatters"))},
    "stalker": {"pale": outfit(F("#5a5248"), F("#4a4234"), sleeves="none", legs="short", shoes=None, flags=("claws", "tatters"), group="special")},
    "charred": {"burned": outfit(F("#2a2420", "burnt"), F("#2a2420", "burnt"), sleeves="short", flags=("growths", "tatters"), group="special",
                                 biomes=("burned_forest",))},
}


def variant_table() -> dict[str, list[dict]]:
    """kind -> [variant spec]. Seeds and the skin/hair picks are fixed, so output is reproducible."""
    r = np.random.default_rng(424242)
    skins = list(SKINS)
    hairs = list(HAIR)
    out = {}

    def make(kind, name, spec, seed, skin=None, hair=None):
        return {"kind": kind, "name": name, "outfit": spec, "seed": seed, "skin": skin or skins[int(r.integers(len(skins)))],
                "hair": hair or hairs[int(r.integers(len(hairs)))], "damage": float(r.uniform(0.3, 0.9))}

    seed = 20000
    out["walker"] = []
    for name, spec in WALKER_OUTFITS.items():
        for k in (1, 2):
            out["walker"].append(make("walker", f"{name}_{k}", spec, seed))
            seed += 1
    for kind, table in (("runner", RUNNER_OUTFITS), ("brute", BRUTE_OUTFITS), ("screamer", SCREAMER_OUTFITS), ("military", MILITARY_OUTFITS),
                        ("crawler", CRAWLER_OUTFITS)):
        out[kind] = []
        for name, spec in table.items():
            out[kind].append(make(kind, name, spec, seed, hair="none" if kind == "screamer" and name == "rags" else None))
            seed += 1
    out["bloater"] = [make("bloater", "bloated", SPECIAL["bloater"]["bloated"], seed, skin="sallow", hair="none")]
    out["stalker"] = [make("stalker", "pale", SPECIAL["stalker"]["pale"], seed + 1, skin="pale", hair="none")]
    out["charred"] = [make("charred", "burned", SPECIAL["charred"]["burned"], seed + 2, skin="dark", hair="none")]
    return out


def write_variants(table: dict[str, list[dict]]) -> None:
    consts = "\n".join(f"\tpublic static final int {k.upper()} = {v};" for k, v in FLAGS.items())
    lists = []
    cases = []
    for kind, variants in table.items():
        rows = []
        for v in variants:
            flags = " | ".join(k.upper() for k in v["outfit"]["flags"]) or "0"
            biomes = ", ".join(f"\"{b}\"" for b in v["outfit"]["biomes"])
            rows.append(f"\t\tnew Variant(\"{v['name']}\", \"{v['outfit']['group']}\", {flags}, List.of({biomes}))")
        lists.append(f"\tprivate static final List<Variant> {kind.upper()} = List.of(\n" + ",\n".join(rows) + ");")
        cases.append(f"\t\t\tcase \"{kind}\" -> {kind.upper()};")
    src = f"""package com.deadlands.entity;

import java.util.List;

/**
 * GENERATED by tools/assetgen/gen_infected.py - do not edit; change the variants there and rerun it.
 *
 * <p>Every infected look: texture name (textures/entity/infected/&lt;kind&gt;/&lt;name&gt;.png),
 * the outfit's loot group (loot_table/entities/outfit/&lt;group&gt;.json), the optional model parts it
 * shows, and the biomes where it is more common.
 */
public final class InfectedVariants {{
{consts}

	public record Variant(String name, String outfit, int parts, List<String> biomes) {{
		public boolean has(int part) {{
			return (parts & part) != 0;
		}}
	}}

{chr(10).join(lists)}

	private InfectedVariants() {{
	}}

	public static List<Variant> forKind(String kind) {{
		return switch (kind) {{
{chr(10).join(cases)}
			default -> List.of();
		}};
	}}
}}
"""
    (JAVA_MAIN / "InfectedVariants.java").write_text(src, encoding="utf-8")


# ============================================================================= painting

def noise(h, w, seed, beta=2.0):
    return N.spectral(h, w, beta=beta, seed=int(seed) % (2 ** 32))


def stable(text: str) -> int:
    """A hash that is the same in every run (Python's str hash is salted per process)."""
    return zlib.crc32(text.encode("utf-8"))


def tone(colour, h, w, seed, var=0.12, beta=2.0):
    return N.solid(h, w, colour) * (1.0 - var + 2 * var * noise(h, w, seed, beta))[..., None]


def skin(h, w, colour, seed, decay=0.6):
    rgb = tone(colour, h, w, seed, 0.1, 2.2)
    veins = np.abs(noise(h, w, seed + 1, 1.2) - 0.5) < 0.03 * decay
    rgb[veins] = rgb[veins] * 0.6 + np.array(N.hex_rgb("#3a3048")) * 0.4
    bruise = N.smoothstep(0.65, 0.8, noise(h, w, seed + 2, 2.5)) * decay
    rgb = N.lerp(rgb, N.solid(h, w, "#4a3a4a"), bruise * 0.5)
    rot = N.smoothstep(0.75, 0.9, noise(h, w, seed + 3, 2.0)) * decay
    rgb = N.lerp(rgb, N.solid(h, w, "#5a5a3a"), rot * 0.45)
    wounds = N.smoothstep(0.86, 0.93, noise(h, w, seed + 4, 2.2)) * decay
    rgb = N.lerp(rgb, N.solid(h, w, "#3a1210"), wounds * 0.85)
    return rgb


def fabric(h, w, spec, seed, face="front"):
    c, pat, second = spec["colour"], spec["pattern"], spec.get("second")
    rgb = tone(c, h, w, seed, 0.07, 1.4)
    yy, xx = np.mgrid[0:h, 0:w]
    if pat == "plaid":
        band = ((xx // 3) % 3 == 0) | ((yy // 3) % 3 == 0)
        cross = ((xx // 3) % 3 == 0) & ((yy // 3) % 3 == 0)
        rgb[band] = rgb[band] * 0.55 + np.array(N.hex_rgb(second)) * 0.45
        rgb[cross] *= 0.7
    elif pat == "stripes":
        rgb[(yy // 2) % 3 == 0] = N.hex_rgb(second)
    elif pat == "denim":
        twill = ((xx + yy) % 3 == 0)
        rgb[twill] *= 0.85
        rgb = N.lerp(rgb, N.solid(h, w, "#8a9ab0"), N.smoothstep(0.6, 0.9, noise(h, w, seed + 5, 1.8)) * 0.3)
    elif pat == "knit":
        rgb[(xx % 2) == 0] *= 0.9
    elif pat == "suit":
        rgb[(xx % 5) == 0] *= 1.12
    elif pat == "scrubs":
        rgb = N.lerp(rgb, N.solid(h, w, "#ffffff"), noise(h, w, seed + 6, 1.0) * 0.08)
    elif pat == "hiviz":
        band = (yy % 10 >= 4) & (yy % 10 <= 5)
        rgb[band] = N.hex_rgb("#c8c8c0")
    elif pat == "floral":
        dots = noise(h, w, seed + 7, 0.4) > 0.86
        rgb[dots] = N.hex_rgb(second)
    elif pat == "dots":
        dots = ((xx % 4) == 1) & ((yy % 4) == 1)
        rgb[dots] = N.hex_rgb(second)
    elif pat.startswith("camo"):
        palettes = {"camo_woodland": ["#3a3e28", "#5a5a38", "#2a2018", "#6a6a4a"], "camo_desert": ["#a08a62", "#8a7048", "#c8b48a", "#6a5a40"],
                    "camo_urban": ["#5a5a5e", "#8a8a8e", "#3a3a3e", "#b0b0b0"]}[pat]
        f1, f2 = noise(h, w, seed + 8, 2.6), noise(h, w, seed + 9, 2.6)
        for i, col in enumerate(palettes):
            m = (f1 > 0.35 + 0.15 * i) if i % 2 == 0 else (f2 > 0.4 + 0.1 * i)
            rgb[m] = N.hex_rgb(col)
    elif pat == "padded":
        rgb[(yy % 4) == 0] *= 0.75
    elif pat == "rubber":
        rgb = N.lerp(rgb, N.solid(h, w, "#f0e06a"), N.smoothstep(0.6, 0.85, noise(h, w, seed + 10, 1.6)) * 0.3)
        rgb[(np.abs(noise(h, w, seed + 11, 1.4) - 0.5) < 0.02)] *= 0.7
    elif pat == "apron":
        rgb = N.lerp(rgb, N.solid(h, w, "#5a1a14"), N.smoothstep(0.55, 0.8, noise(h, w, seed + 12, 2.0)) * 0.7)
    elif pat == "coat":
        rgb = N.lerp(rgb, N.solid(h, w, "#bdb8a8"), noise(h, w, seed + 13, 1.2) * 0.2)
    elif pat == "burnt":
        rgb = N.lerp(rgb, N.solid(h, w, "#120e0c"), N.smoothstep(0.4, 0.7, noise(h, w, seed + 14, 2.0)))
    return rgb


def shade(rgb, top=1.06, bottom=0.82):
    h = rgb.shape[0]
    ramp = np.linspace(top, bottom, h)[:, None, None]
    return rgb * ramp


def wear(rgb, under, seed, amount, blood=0.5):
    """Tears showing what is underneath, dried blood and dirt."""
    h, w = rgb.shape[:2]
    tears = N.smoothstep(0.88 - 0.06 * amount, 0.91 - 0.06 * amount, noise(h, w, seed, 2.6))
    frayed = N.smoothstep(0.84 - 0.06 * amount, 0.88 - 0.06 * amount, noise(h, w, seed, 2.6)) - tears
    rgb = rgb * (1.0 - 0.35 * np.clip(frayed, 0, 1))[..., None]  # dark frayed edges around each tear
    rgb = N.lerp(rgb, under * 0.8, tears)
    stain = N.smoothstep(0.7 - 0.1 * blood, 0.85, noise(h, w, seed + 1, 2.0)) * amount * blood
    rgb = N.lerp(rgb, N.ramp(noise(h, w, seed + 2, 1.5), [(0.0, "#2a0e0a"), (1.0, "#4e1c14")]), stain * 0.85)
    dirt = N.smoothstep(0.5, 0.9, noise(h, w, seed + 3, 1.8)) * np.linspace(0.3, 1.0, h)[:, None]
    rgb = N.lerp(rgb, N.solid(h, w, "#3e3426"), dirt * 0.35 * amount)
    return rgb


def paint_face_features(img, x, y, w, h, v, r):
    """Eyes, brow and nose on a head front (w x h image pixels)."""
    skin_c = np.array(N.hex_rgb(SKINS[v["skin"]]))
    ey = int(h * 0.45)
    for ex in (int(w * 0.22), int(w * 0.58)):
        ew = max(3, w // 5)
        img[y + ey - 1:y + ey + 2, x + ex:x + ex + ew] = skin_c * 0.35  # sunken socket
        eye = np.array(N.hex_rgb("#1a1614" if v["kind"] in ("runner", "brute", "screamer") else r.choice(["#2a2420", "#b8b4a4"])))
        img[y + ey, x + ex + 1:x + ex + ew - 1] = eye
    img[y + ey - 2, x + 2:x + w - 2] = img[y + ey - 2, x + 2:x + w - 2] * 0.75  # brow ridge shadow
    nx = x + w // 2 - 1
    img[y + ey + 2:y + ey + 4, nx:nx + 2] = skin_c * 0.55


def paint_mouth(img, x, y, w, h, r, wide=False):
    """A slack mouth across the middle of the jaw front: dark gap, broken yellow teeth, a lower lip."""
    half = int(w * (0.38 if wide else 0.28))
    cx = x + w // 2
    rows = 2 if wide else 1
    img[y:y + rows, cx - half:cx + half] = np.array(N.hex_rgb("#2a1212"))
    for tx in range(cx - half + 1, cx + half - 1, 2):
        if r.random() < 0.7:
            img[y, tx] = np.array(N.hex_rgb("#a89a72"))
    img[y + rows, cx - half + 1:cx + half - 1] = img[y + rows, cx - half + 1:cx + half - 1] * 0.6


def paint_hair(region, colour, style_seed, face):
    if colour is None:
        return region
    h, w = region.shape[:2]
    hair = tone(colour, h, w, style_seed, 0.18, 1.0)
    patch = noise(h, w, style_seed + 1, 2.0)
    if face == "top":
        mask = patch > 0.25
    elif face == "back":
        mask = (np.arange(h)[:, None] < h * 0.8) & (patch > 0.2)
    elif face in ("left", "right"):
        mask = (np.arange(h)[:, None] < h * 0.45) & (patch > 0.25)
    else:  # front: a fringe
        mask = (np.arange(h)[:, None] < h * 0.18) & (patch > 0.4)
    region[mask] = hair[mask]
    return region


def material_for(role, v, part_name, flags):
    """Which material a part shows: 'skin', ('fabric', spec), or a gear name."""
    o = v["outfit"]
    if role in ("head", "jaw", "claw", "growth"):
        return role
    if role in ("chest", "torso", "tatter"):
        return ("fabric", o["top"])
    if role == "belly":
        return "skin"
    if role == "upper_arm":
        return ("fabric", o["top"]) if o["sleeves"] in ("long", "short") else "skin"
    if role == "forearm":
        return ("fabric", o["top"]) if o["sleeves"] == "long" else "skin"
    if role == "thigh":
        return ("fabric", o["bottom"])
    if role == "shin":
        return ("fabric", o["bottom"]) if o["legs"] == "long" else "skin"
    return role  # helmet, vest, belt, backpack, cap, mask


GEAR = {
    "vest": "#3a3e2e", "belt": "#2a2620", "backpack": "#4a4632", "mask": "#2a2a2a",
}


def paint_part(img, part, v, r):
    o = v["outfit"]
    seed = v["seed"] * 7 + stable(part.name) % 1000
    flags = set(o["flags"])
    skin_c = SKINS[v["skin"]]
    military = v["kind"] == "military"
    for (u, vv), cube in zip(part.uv, part.cubes):
        for face, (x, y, w, h) in face_rects(u, vv, cube).items():
            if w <= 0 or h <= 0:
                continue
            fseed = seed + stable(face) % 97
            mat = material_for(part.role, v, part.name, flags)
            under = skin(h, w, skin_c, fseed + 50, v["damage"])
            if mat in ("head", "jaw", "skin", "growth"):
                region = skin(h, w, skin_c, fseed, v["damage"] if mat != "growth" else 1.0)
                if mat == "growth":
                    region = N.lerp(region, N.solid(h, w, "#6a4a3a"), 0.5)
                    region *= (0.8 + 0.4 * noise(h, w, fseed + 9, 0.8))[..., None]
                if mat == "head":
                    region = paint_hair(region, HAIR[v["hair"]], v["seed"] + 77, face)
                if v["kind"] == "charred":
                    region = N.lerp(region, N.solid(h, w, "#1a1210"), N.smoothstep(0.3, 0.6, noise(h, w, fseed + 3, 1.8)))
                if v["kind"] == "stalker":
                    region = N.lerp(region, N.solid(h, w, "#b8bcbc"), 0.35)
            elif mat == "claw":
                region = tone("#3a3428", h, w, fseed, 0.2)
            elif isinstance(mat, tuple):
                region = fabric(h, w, mat[1], fseed, face)
                amount = v["damage"] * (1.3 if "tatters" in flags else 1.0)
                region = wear(region, under, fseed + 20, min(1.0, amount))
                # Shoes at the bottom of shins (and of thighs for shorts are skin shins anyway).
                if part.role == "shin" and o["shoes"] and face not in ("top",):
                    sh = max(2, h // 4)
                    if face == "bottom":
                        region[...] = np.array(N.hex_rgb(o["shoes"])) * 0.8
                    else:
                        region[h - sh:] = tone(o["shoes"], sh, w, fseed + 30, 0.1)
            elif mat == "helmet":
                colour = "#c8a020" if o["group"] in ("worker",) else "#1e2026" if o["group"] == "riot" else "#4a5236" if military else "#3a3a3a"
                region = tone(colour, h, w, fseed, 0.1)
                region = wear(region, N.solid(h, w, "#2a2a2a"), fseed + 1, v["damage"] * 0.5, blood=0.3)
            elif mat == "cap":
                region = tone("#1e2232", h, w, fseed, 0.08)
                if part.name == "cap" and face == "front":
                    region[h // 3:h // 3 + 2, w // 2 - 1:w // 2 + 1] = np.array(N.hex_rgb("#b89a3a"))
            elif mat in GEAR:
                colour = GEAR[mat]
                if mat == "vest" and o["group"] == "riot":
                    colour = "#111214"
                region = tone(colour, h, w, fseed, 0.12)
                if mat == "vest":
                    region[(np.arange(h) % 4 == 2)] *= 0.7  # MOLLE webbing
                if mat == "mask":
                    if face == "front":
                        cy = h // 2
                        region[cy - 1:cy + 1, 1:w // 2 - 1] = np.array(N.hex_rgb("#5a6a6a"))
                        region[cy - 1:cy + 1, w // 2 + 1:w - 1] = np.array(N.hex_rgb("#5a6a6a"))
                region = wear(region, N.solid(h, w, "#2a2a2a"), fseed + 1, v["damage"] * 0.4, blood=0.3)
            else:
                region = tone("#5a5248", h, w, fseed)
            region = shade(region)
            img[y:y + h, x:x + w] = region
            # Face details.
            if mat == "head" and face == "front":
                paint_face_features(img, x, y, w, h, v, r)
            if mat == "jaw" and face == "front":
                paint_mouth(img, x, y, w, h, r, wide=v["kind"] == "screamer")
                drip = r.integers(x + 2, x + w - 2)
                img[y + h - 1:y + h, drip] = np.array(N.hex_rgb("#4a1410"))
            if part.role in ("chest", "torso") and face == "front":
                paint_detail(img, x, y, w, h, o, part.role, r)
            if part.role == "upper_arm" and o["detail"] == "armband" and face in ("left", "right", "front") and part.name == "left_arm":
                img[y + 2:y + 5, x:x + w] = np.array(N.hex_rgb("#d8d4c8"))
                img[y + 3, x + w // 2 - 1:x + w // 2 + 1] = np.array(N.hex_rgb("#a8261c"))


def paint_detail(img, x, y, w, h, o, role, r):
    d = o["detail"]
    cx = x + w // 2
    if d == "tie" and role == "chest":
        img[y:y + h, cx - 1:cx + 1] = np.array(N.hex_rgb("#6a1a1a"))
        img[y:y + 2, cx - 3:cx + 3] = np.array(N.hex_rgb("#d8d4c8"))  # shirt collar
    elif d == "badge" and role == "chest":
        img[y + 3:y + 6, x + w - 6:x + w - 3] = np.array(N.hex_rgb("#c8a83a"))
    elif d == "logo" and role == "chest":
        yy, xx = np.mgrid[0:h, 0:w]
        ring_ = np.abs(np.hypot(xx - w / 2, yy - h / 2) - 3.0) < 0.9
        img[y:y + h, x:x + w][ring_] = np.array(N.hex_rgb(r.choice(["#c8c0a8", "#c84a2a", "#3a8ac8"])))
    elif d == "vneck" and role == "chest":
        for k in range(4):
            img[y + k, cx - 3 + k:cx + 3 - k] = np.array(N.hex_rgb("#8a8270"))
    elif d == "bib":
        img[y:y + h, x + 2:x + w - 2] = img[y:y + h, x + 2:x + w - 2] * 0.3 + np.array(N.hex_rgb("#3e5070")) * 0.7
        if role == "chest":
            img[y + 2, cx - 4:cx - 2] = np.array(N.hex_rgb("#b8a060"))
            img[y + 2, cx + 2:cx + 4] = np.array(N.hex_rgb("#b8a060"))
    elif d == "pocket" and role == "chest":
        img[y + 3:y + 6, x + 2:x + 6] *= 0.8
    elif d == "number" and role == "chest":
        img[y + 3:y + 5, cx - 4:cx + 4] = np.array(N.hex_rgb("#1a1a1a"))


def paint_variant(rig: list[Part], v: dict) -> np.ndarray:
    img = np.zeros((TEX_H * S, TEX_W * S, 3))
    img[...] = N.hex_rgb("#201c18")
    r = np.random.default_rng(v["seed"])
    for part in rig:
        if part.cubes:
            paint_part(img, part, v, r)
    return np.clip(img, 0, 255)


def glow_texture(rig: list[Part], v: dict) -> tuple[np.ndarray, np.ndarray]:
    """Emissive overlay: ember cracks for the Charred, pale eyes for the Stalker."""
    rgb = np.zeros((TEX_H * S, TEX_W * S, 3))
    alpha = np.zeros((TEX_H * S, TEX_W * S))
    for part in rig:
        for (u, vv), cube in zip(part.uv, part.cubes):
            for face, (x, y, w, h) in face_rects(u, vv, cube).items():
                if w <= 0 or h <= 0:
                    continue
                if v["kind"] == "charred" and part.role not in ("helmet", "cap", "vest", "belt", "mask", "backpack"):
                    cracks = np.abs(noise(h, w, v["seed"] + x * 3 + y, 1.3) - 0.5) < 0.05
                    rgb[y:y + h, x:x + w][cracks] = N.hex_rgb("#ff7a1e")
                    alpha[y:y + h, x:x + w][cracks] = 0.95
                if part.role == "head" and face == "front":
                    ey = y + int(h * 0.45)
                    colour = "#ffb040" if v["kind"] == "charred" else "#d8f0ff"
                    for ex in (int(w * 0.22), int(w * 0.58)):
                        ew = max(3, w // 5)
                        rgb[ey, x + ex + 1:x + ex + ew - 1] = N.hex_rgb(colour)
                        alpha[ey, x + ex + 1:x + ex + ew - 1] = 1.0
    return rgb, alpha


def front_preview(rig: list[Part], img: np.ndarray) -> np.ndarray:
    """Orthographic front view of the default pose (for asset review only)."""
    out = np.zeros((48 * S, 40 * S, 3))
    out[...] = 90
    world = {}
    for p in rig:
        px, py, pz = p.pivot
        if p.parent:
            bx, by, bz = world[p.parent]
            world[p.name] = (bx + px, by + py, bz + pz)
        else:
            world[p.name] = (px, py, pz)
    order = sorted(rig, key=lambda p: -min((c[2] for c in p.cubes), default=0))
    for p in order:
        if p.optional and p.optional not in ("tatters",):
            continue
        ox, oy, oz = world[p.name]
        for (u, vv), cube in zip(p.uv, p.cubes):
            x, y, z, w, h, d = cube
            fx, fy, fw, fh = face_rects(u, vv, cube)["front"]
            if fw <= 0 or fh <= 0:
                continue
            sx = int((ox + x + 20) * S)
            sy = int((oy + y + 20) * S)
            if 0 <= sx and sx + fw <= out.shape[1] and 0 <= sy and sy + fh <= out.shape[0]:
                out[sy:sy + fh, sx:sx + fw] = img[fy:fy + fh, fx:fx + fw]
    return out


# ============================================================================= spawn eggs

EGG_COLOURS = {
    "walker": ("#6a6a58", "#3a2a24"), "runner": ("#7a5a4a", "#2a1a14"), "brute": ("#4a4a3a", "#7a2a1a"),
    "screamer": ("#a8a294", "#5a1a1a"), "military": ("#4a5236", "#2a2018"), "crawler": ("#5a5040", "#1a1a14"),
    "bloater": ("#8a8a5a", "#4a5a2a"), "stalker": ("#b8bcbc", "#2a3a4a"), "charred": ("#2a2420", "#ff7a1e"),
}


def spawn_egg(kind: str, seed: int):
    s = 32
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    egg = ((xx - 16) / 9.5) ** 2 + ((yy - 17) / 12.5 + 0.08 * (yy - 17) / 12.5) ** 2 <= 1.0
    base, spots = EGG_COLOURS[kind]
    rgb = tone(base, s, s, seed, 0.1, 1.5)
    sp = noise(s, s, seed + 1, 0.8) > 0.78
    rgb[sp] = N.hex_rgb(spots)
    light = np.clip(1.15 - ((xx - 12) ** 2 + (yy - 11) ** 2) / 500.0, 0.7, 1.2)
    rgb *= light[..., None]
    alpha = egg.astype(float)
    from gen_items import _outline
    _outline(rgb, alpha)
    return np.clip(rgb, 0, 255), alpha


# ============================================================================= loot, tags, text

def loot_entry(name, weight, count=None, chance=None, player=True):
    e = {"type": "minecraft:item", "name": name if ":" in name else f"minecraft:{name}", "weight": weight}
    fns = []
    if count:
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    if fns:
        e["functions"] = fns
    return e


def pool(rolls, entries, chance=None, player=False):
    p = {"rolls": rolls, "bonus_rolls": 0.0, "entries": entries}
    conds = []
    if chance is not None:
        conds.append({"condition": "minecraft:random_chance", "chance": chance})
    if player:
        conds.append({"condition": "minecraft:killed_by_player"})
    if conds:
        p["conditions"] = conds
    return p


def entity_table(path, pools):
    D.data_json(f"loot_table/{path}.json", {"type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:{path}"})


def key_entry(key_id, weight):
    return {"type": "minecraft:item", "name": f"{NS}:key", "weight": weight,
            "functions": [{"function": "minecraft:set_components", "components": {f"{NS}:key_id": key_id}}]}


def loot() -> None:
    """Vanilla items stand in for scavenged goods until the survival items of Milestone 5."""
    scraps = [loot_entry("string", 6, (1, 2)), loot_entry("bone", 4), loot_entry("leather", 2)]
    entity_table("entities/walker", [pool(1, scraps, chance=0.5)])
    entity_table("entities/runner", [pool(1, scraps, chance=0.45)])
    entity_table("entities/brute", [pool(1, [loot_entry("bone", 4, (1, 3)), loot_entry("leather", 4, (1, 2))]),
                                    pool(1, [loot_entry(f"{NS}:infected_tissue", 1)], chance=0.35, player=True)])
    entity_table("entities/screamer", [pool(1, scraps, chance=0.4), pool(1, [loot_entry(f"{NS}:infected_tissue", 1)], chance=0.25, player=True)])
    entity_table("entities/military", [pool(1, [loot_entry("iron_nugget", 6, (1, 4)), loot_entry("arrow", 5, (2, 6)), loot_entry("gunpowder", 3, (1, 2)),
                                                loot_entry("bread", 2)], chance=0.7),
                                       pool(1, [loot_entry(f"{NS}:keycard_green", 4), loot_entry(f"{NS}:keycard_blue", 1)], chance=0.06, player=True)])
    entity_table("entities/crawler", [pool(1, scraps, chance=0.35)])
    entity_table("entities/bloater", [pool(1, [loot_entry("slime_ball", 1, (1, 2))], chance=0.5),
                                      pool(1, [loot_entry(f"{NS}:infected_tissue", 1)], chance=0.4, player=True)])
    entity_table("entities/stalker", [pool(1, [loot_entry("bone", 1, (1, 2))], chance=0.6),
                                      pool(1, [loot_entry(f"{NS}:infected_tissue", 1)], chance=0.5, player=True)])
    entity_table("entities/charred", [pool(1, [loot_entry("charcoal", 3, (1, 2)), loot_entry("gunpowder", 1)], chance=0.6)])
    outfits = {
        "civilian": [loot_entry("paper", 4), loot_entry("gold_nugget", 2, (1, 3)), loot_entry("apple", 2), key_entry("maintenance", 1)],
        "office": [loot_entry("paper", 6, (1, 3)), loot_entry("book", 2), key_entry("records_room", 1), loot_entry(f"{NS}:keycard_green", 1)],
        "medical": [loot_entry("glass_bottle", 4), loot_entry("paper", 3), key_entry("pharmacy", 2), loot_entry(f"{NS}:keycard_green", 1)],
        "farmer": [loot_entry("wheat_seeds", 5, (1, 4)), loot_entry("wheat", 3, (1, 2)), loot_entry("leather", 2)],
        "police": [loot_entry("arrow", 4, (1, 4)), key_entry("records_room", 2), loot_entry(f"{NS}:keycard_green", 1)],
        "worker": [loot_entry("iron_nugget", 5, (1, 3)), loot_entry("flint", 3), key_entry("maintenance", 2), loot_entry(f"{NS}:fuse", 1)],
        "winter": [loot_entry("leather", 4, (1, 2)), loot_entry("white_wool", 3), loot_entry("dried_kelp", 3, (1, 3))],
        "athlete": [loot_entry("sugar", 4, (1, 2)), loot_entry("apple", 3)],
        "prisoner": [loot_entry("iron_nugget", 3), loot_entry("string", 3)],
        "butcher": [loot_entry("porkchop", 4, (1, 2)), loot_entry("beef", 3), loot_entry("iron_nugget", 2)],
        "military": [loot_entry("arrow", 5, (2, 6)), loot_entry("gunpowder", 3), loot_entry("iron_ingot", 1)],
        "hazmat": [loot_entry("glass_bottle", 3), loot_entry(f"{NS}:keycard_blue", 1), loot_entry(f"{NS}:infected_tissue", 1)],
        "riot": [loot_entry("iron_nugget", 4, (2, 5)), loot_entry("shield", 1)],
        "lab": [loot_entry("paper", 5, (2, 4)), loot_entry("glass_bottle", 3), loot_entry(f"{NS}:keycard_blue", 2), loot_entry(f"{NS}:infected_tissue", 1)],
        "special": [loot_entry(f"{NS}:infected_tissue", 1)],
    }
    for group, entries in outfits.items():
        entity_table(f"entities/outfit/{group}", [pool(1, entries, chance=0.2, player=True)])


def tags() -> None:
    t = D.TagCollector()
    for v in ("#minecraft:wooden_doors", "#minecraft:wooden_trapdoors", "#minecraft:fence_gates", "#minecraft:wooden_fences",
              "minecraft:glass", "minecraft:glass_pane", f"{NS}:boarded_planks", f"{NS}:broken_glass"):
        t.add(f"{NS}:infected_breakable", v)
    for v in ("#minecraft:planks", "minecraft:iron_bars", "minecraft:iron_door", "minecraft:iron_trapdoor", f"{NS}:corrugated_metal",
              f"{NS}:rusted_sheet_metal"):
        t.add(f"{NS}:brute_breakable", v)
    t.write("infected")


TEXT = {
    "entity.deadlands.walker": "Walker", "entity.deadlands.runner": "Runner", "entity.deadlands.brute": "Brute",
    "entity.deadlands.screamer": "Screamer", "entity.deadlands.military": "Military Infected", "entity.deadlands.crawler": "Crawler",
    "entity.deadlands.bloater": "Bloater", "entity.deadlands.stalker": "Stalker", "entity.deadlands.charred": "Charred",
    "item.deadlands.walker_spawn_egg": "Walker Spawn Egg", "item.deadlands.runner_spawn_egg": "Runner Spawn Egg",
    "item.deadlands.brute_spawn_egg": "Brute Spawn Egg", "item.deadlands.screamer_spawn_egg": "Screamer Spawn Egg",
    "item.deadlands.military_spawn_egg": "Military Infected Spawn Egg", "item.deadlands.crawler_spawn_egg": "Crawler Spawn Egg",
    "item.deadlands.bloater_spawn_egg": "Bloater Spawn Egg", "item.deadlands.stalker_spawn_egg": "Stalker Spawn Egg",
    "item.deadlands.charred_spawn_egg": "Charred Spawn Egg",
    "itemGroup.deadlands.spawn_eggs": "DEADLANDS: Spawn Eggs",
    "journal.deadlands.log.killed": "Infected killed",
    "journal.deadlands.threats.dev_note": "Development build: horde nights are live; bosses arrive in Milestone 8 (no horde comes on a boss day).",
    "command.deadlands.status.pending": "(Development build: hordes are live; bosses arrive in Milestone 8, and no horde comes on a boss day.)",
    "command.deadlands.horde.started": "A horde of %s is coming.",
    "command.deadlands.horde.stopped": "The horde was called off.",
    "command.deadlands.horde.none": "There is no horde right now.",
    "command.deadlands.horde.cannot_start": "Could not start a horde (one is already on, hordes are off, or nobody is in the overworld).",
    "command.deadlands.horde.status": "Horde of day %s: %s in all, %s arrived, %s alive, %s killed.",
    "command.deadlands.infected.count": "%s infected loaded (global cap %s); %s nests loaded; %s migrating groups (%s in the world).",
    "command.deadlands.infected.cleared": "Removed %s infected.",
    "command.deadlands.noise.made": "Made a %s noise (heard up to %s blocks away).",
    "command.deadlands.noise.unknown": "Unknown loudness %s.",
    "message.deadlands.horde.no_sleep": "You can't sleep. Not tonight: the horde is coming.",
    "chronicle.deadlands.horde_began": "The horde of day %s came: %s infected.",
    "chronicle.deadlands.horde_broken": "The horde of day %s was broken: %s killed.",
    "chronicle.deadlands.horde_survived": "Dawn ended the horde of day %s: %s of %s killed.",
    "hud.deadlands.horde.title": "THE HORDE",
    "hud.deadlands.horde.coming": "They are coming. %s of them.",
    "hud.deadlands.horde.broken": "THE HORDE IS BROKEN",
    "hud.deadlands.horde.dawn": "DAWN",
    "hud.deadlands.horde.dawn_sub": "You lived through the night. %s of %s killed.",
    "hud.deadlands.horde.counter": "THE HORDE  %s left",
    "subtitles.deadlands.infected.groan": "Infected groans",
    "subtitles.deadlands.infected.hurt": "Infected hurts",
    "subtitles.deadlands.infected.death": "Infected dies",
    "subtitles.deadlands.infected.attack": "Infected lunges",
    "subtitles.deadlands.infected.runner_alert": "Runner shrieks",
    "subtitles.deadlands.infected.brute_roar": "Brute roars",
    "subtitles.deadlands.infected.brute_step": "Heavy footsteps",
    "subtitles.deadlands.infected.slam": "Brute smashes the ground",
    "subtitles.deadlands.infected.scream": "Screamer screams",
    "subtitles.deadlands.infected.burst": "Bloater bursts",
    "subtitles.deadlands.infected.pounce": "Stalker leaps",
    "subtitles.deadlands.infected.breath": "Something breathes nearby",
    "subtitles.deadlands.infected.scratch": "Scratching",
    "subtitles.deadlands.infected.bash": "Something bashes a barricade",
    "subtitles.deadlands.infected.step": "Shuffling footsteps",
    "subtitles.deadlands.infected.distant": "Distant groans",
    "subtitles.deadlands.infected.emerge": "Something crawls out",
    "subtitles.deadlands.nest.pulse": "Nest pulses",
    "subtitles.deadlands.nest.release": "Nest tears open",
    "subtitles.deadlands.nest.destroyed": "Nest bursts",
    "subtitles.deadlands.nest.absorb": "Something crawls into a nest",
    "subtitles.deadlands.horde_start": "The horde is coming",
    "subtitles.deadlands.horde_end": "The night is over",
    "subtitles.deadlands.horde_distant": "A crowd moans in the distance",
    "config.deadlands.zombies.nestReleases": "Nest releases",
    "config.deadlands.zombies.naturalSpawning": "Infected appear naturally",
    "config.deadlands.zombies.daytimePopulation": "Daytime population",
    "config.deadlands.hordes.preventSleep": "No sleeping on horde nights",
    "config.deadlands.audio.infectedVoiceEffects": "Distant and muffled infected",
    "config.deadlands.ui.hordeHud": "Horde card and counter",
    "config.deadlands.world.railways": "Railways",
}


# ============================================================================= build

def build() -> int:
    rigs = {"humanoid": humanoid_rig(), "brute": brute_rig()}
    for parts in rigs.values():
        pack(parts)
    write_geometry(rigs)
    table = variant_table()
    write_variants(table)
    count = 0
    previews = []
    for kind, variants in table.items():
        rig = rigs["brute"] if kind == "brute" else rigs["humanoid"]
        folder = N.asset(f"textures/entity/infected/{kind}")
        if folder.exists():
            for old in folder.glob("*.png"):
                old.unlink()
        for v in variants:
            img = paint_variant(rig, v)
            N.save_png(N.asset(f"textures/entity/infected/{kind}/{v['name']}.png"), img)
            if kind in ("charred", "stalker"):
                N.save_png(N.asset(f"textures/entity/infected/{kind}/{v['name']}_glow.png"), *glow_texture(rig, v))
            previews.append(front_preview(rig, img))
            count += 1
    for i, kind in enumerate(table):
        rgb, alpha = spawn_egg(kind, 30000 + i)
        N.save_png(N.asset(f"textures/item/{kind}_spawn_egg.png"), rgb, alpha)
        D.asset_json(f"models/item/{kind}_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{kind}_spawn_egg"}})
        D.item_model_definition(f"{kind}_spawn_egg", f"{NS}:item/{kind}_spawn_egg")
    loot()
    tags()
    D.merge_lang(TEXT)
    # Review sheet (not shipped): front views of every variant.
    cols = 12
    rows = (len(previews) + cols - 1) // cols
    h, w = previews[0].shape[:2]
    sheet = np.zeros((rows * h, cols * w, 3))
    for i, p in enumerate(previews):
        sheet[(i // cols) * h:(i // cols + 1) * h, (i % cols) * w:(i % cols + 1) * w] = p
    N.save_png(N.ROOT / "tools" / "assetgen" / "_cache" / "infected_preview.png", np.clip(sheet, 0, 255))
    return count


if __name__ == "__main__":
    print(f"{build()} infected textures written")
