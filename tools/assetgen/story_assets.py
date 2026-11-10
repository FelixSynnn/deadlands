"""Story assets (Milestone 8), run by gen_story.py: the story's text (story_texts.py), item icons, the
document item's five looks, the keycard reader, security door and radio transmitter, the helicopter
rig as Java (HelicopterGeometry.java) and its texture, the journal's ARCHIVE icon, the radio and rotor
sounds, Harley's camp crate and the structure tags used to find story places."""
from __future__ import annotations

import json

import numpy as np
from PIL import Image, ImageDraw

import dl_data as D
import dl_noise as N
import gen_vehicles as V
from story_texts import TEXT

NS = D.NS
JAVA_CLIENT = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render"


# ============================================================================= item icons

def _doc(d, paper="#e6dcc2", lines="#6a5a46"):
    d.polygon([(8, 4), (22, 4), (26, 8), (26, 28), (8, 28)], fill=paper)
    d.polygon([(22, 4), (26, 8), (22, 8)], fill="#c9bc9a")
    for y in range(10, 26, 3):
        d.line([(11, y), (23, y)], fill=lines)


def document(d):
    _doc(d)


def journal(d):
    d.rectangle([7, 4, 25, 28], fill="#5a3a24")
    d.rectangle([9, 5, 24, 27], fill="#e6dcc2")
    d.rectangle([7, 4, 10, 28], fill="#4a2e1c")
    for y in range(9, 25, 3):
        d.line([(13, y), (22, y)], fill="#6a5a46")


def newspaper(d):
    d.rectangle([5, 7, 27, 26], fill="#d8d2c2")
    d.rectangle([7, 9, 25, 12], fill="#3a3632")
    for y in range(15, 25, 2):
        d.line([(7, y), (15, y)], fill="#6a6660")
        d.line([(17, y), (25, y)], fill="#6a6660")


def tape(d):
    d.rectangle([5, 9, 27, 24], fill="#2a2a2e")
    d.rectangle([8, 11, 24, 17], fill="#d8d0b8")
    d.ellipse([10, 12, 14, 16], fill="#3a3a3e")
    d.ellipse([18, 12, 22, 16], fill="#3a3a3e")
    d.rectangle([11, 19, 21, 23], fill="#4a4a4e")


def photo(d):
    d.rectangle([6, 6, 26, 26], fill="#e8e4dc")
    d.rectangle([8, 8, 24, 21], fill="#5a6a72")
    d.polygon([(8, 21), (14, 13), (18, 18), (21, 15), (24, 21)], fill="#3a4a3a")
    d.ellipse([18, 9, 22, 13], fill="#d8c890")


def radio(d):
    d.rectangle([7, 8, 25, 27], fill="#3a3c34")
    d.line([(21, 8), (25, 2)], fill="#8a8c8e", width=1)
    d.rectangle([9, 10, 23, 15], fill="#c8a040")
    d.line([(15, 10), (15, 15)], fill="#a02a20")
    for x in range(9, 24, 2):
        d.line([(x, 18), (x, 25)], fill="#22241e")
    d.ellipse([18, 19, 23, 24], fill="#6a6c64")


def radio_parts(d):
    d.rectangle([6, 10, 26, 24], fill="#2a6a3a")
    for x in range(8, 25, 4):
        d.rectangle([x, 12, x + 2, 15], fill="#1a1a1a")
    d.ellipse([10, 17, 15, 22], fill="#c8a040")
    d.rectangle([18, 17, 24, 21], fill="#8a8c8e")


def case(d):
    d.rectangle([5, 10, 27, 26], fill="#6a6c70")
    d.rectangle([12, 6, 20, 10], fill="#3a3c40")
    d.rectangle([5, 16, 27, 18], fill="#3a3c40")
    d.rectangle([14, 15, 18, 19], fill="#c8a040")
    d.rectangle([7, 20, 13, 24], fill="#b02a22")


def vials(d, colour="#7a1e1a"):
    for i, x in enumerate((8, 14, 20)):
        d.rectangle([x, 8, x + 4, 25], fill="#c8d4d8")
        d.rectangle([x, 14 + i * 2, x + 4, 25], fill=colour)
        d.rectangle([x - 1, 6, x + 5, 8], fill="#3a3c40")


def folder(d, tab="#c8a040", stamp=None):
    d.rectangle([5, 8, 27, 26], fill="#c8b070")
    d.rectangle([5, 6, 14, 9], fill=tab)
    d.rectangle([8, 11, 26, 24], fill="#e6dcc2")
    for y in range(14, 23, 3):
        d.line([(10, y), (22, y)], fill="#6a5a46")
    if stamp:
        d.rectangle([16, 18, 25, 23], outline=stamp)


def badge(d):
    d.rectangle([8, 7, 24, 27], fill="#d8d8d0")
    d.rectangle([8, 7, 24, 11], fill="#2a4a7a")
    d.rectangle([11, 13, 17, 20], fill="#a08470")
    d.line([(18, 15), (22, 15)], fill="#3a3a3a")
    d.line([(18, 18), (22, 18)], fill="#3a3a3a")
    d.ellipse([12, 20, 21, 27], fill="#6a1410")


def tissue(d):
    d.ellipse([6, 8, 26, 26], fill="#8a3a34")
    d.ellipse([10, 11, 18, 18], fill="#a05a50")
    d.ellipse([16, 16, 23, 23], fill="#5a1e1a")


def blade(d):
    d.polygon([(4, 24), (26, 6), (28, 8), (6, 26)], fill="#4a5236")
    d.rectangle([3, 23, 8, 28], fill="#2a2a2a")


def turbine(d):
    d.ellipse([5, 5, 27, 27], fill="#6a6c70")
    for k in range(8):
        a = k * np.pi / 4
        d.line([(16, 16), (16 + np.cos(a) * 10, 16 + np.sin(a) * 10)], fill="#3a3c40", width=2)
    d.ellipse([13, 13, 19, 19], fill="#c8a040")


def avionics(d):
    d.rectangle([5, 8, 27, 25], fill="#2a2c30")
    d.rectangle([8, 11, 18, 21], fill="#2a6a5a")
    d.line([(9, 16), (17, 16)], fill="#8ae0c0")
    d.ellipse([20, 11, 25, 16], fill="#6a6c70")
    d.ellipse([20, 18, 25, 23], fill="#6a6c70")


def avfuel(d):
    d.rounded_rectangle([8, 7, 24, 27], radius=2, fill="#4a5236")
    d.rectangle([11, 4, 16, 7], fill="#2a2a2a")
    d.rectangle([10, 13, 22, 19], fill="#c8a040")
    d.line([(12, 16), (20, 16)], fill="#2a2a2a")


def door_item(d):
    d.rectangle([9, 2, 23, 30], fill="#6a6e72")
    d.rectangle([11, 5, 21, 12], fill="#2a3a44")
    for x in range(9, 24, 4):
        d.line([(x, 26), (x + 3, 29)], fill="#c8a040", width=2)
    d.rectangle([19, 16, 21, 18], fill="#2a2a2a")


ICONS = {
    "document": document, "document_journal": journal, "document_newspaper": newspaper, "document_tape": tape, "document_photo": photo,
    "radio": radio, "radio_parts": radio_parts, "field_case": case, "medical_samples": vials,
    "hospital_records": lambda d: folder(d, "#6a8aa0"), "research_documents": lambda d: folder(d, "#c8a040", "#2a4a7a"),
    "patient_zero_records": lambda d: folder(d, "#a02a20", "#a02a20"), "military_research": lambda d: folder(d, "#4a5236", "#a02a20"),
    "experimental_formula": lambda d: folder(d, "#7a4a9a"), "evacuation_orders": lambda d: folder(d, "#4a5236"),
    "fused_tissue": tissue, "butchers_badge": badge, "serum_vial": lambda d: vials(d, "#7ac040"),
    "patient_zero_blood": lambda d: vials(d, "#4a0a2a"), "rotor_blade": blade, "turbine_parts": turbine, "avionics": avionics,
    "aviation_fuel": avfuel, "security_door": door_item,
}


def wardens_keycard(d):
    d.rounded_rectangle([5, 9, 27, 24], radius=2, fill="#a02a20")
    d.rectangle([5, 12, 27, 15], fill="#1a1a1a")
    d.rectangle([8, 17, 14, 22], fill="#d8d0b8")
    d.rectangle([17, 18, 25, 19], fill="#e8e0b0")
    d.line([(6, 23), (26, 10)], fill="#5a0e0a")


ICONS["wardens_keycard"] = wardens_keycard


def icons() -> None:
    for i, (name, painter) in enumerate(ICONS.items()):
        N.save_png(N.asset(f"textures/item/{name}.png"), *V.icon(painter, 8100 + i))
        if name.startswith("document_") or name == "security_door":
            continue
        D.asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        if name != "document":
            D.item_model_definition(name, f"{NS}:item/{name}")
    for form in ("journal", "newspaper", "tape", "photo"):
        D.asset_json(f"models/item/document_{form}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/document_{form}"}})
    D.asset_json("items/document.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": form, "model": {"type": "minecraft:model", "model": f"{NS}:item/document_{form}"}} for form in ("journal", "newspaper", "tape", "photo")],
        "fallback": {"type": "minecraft:model", "model": f"{NS}:item/document"}}})


# ============================================================================= blocks

def tex(name, painter, size=16, seed=0):
    img = np.zeros((size, size, 4))
    painter(img, np.random.default_rng(seed))
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(N.asset(f"textures/block/{name}.png"))


def rect(img, x0, y0, x1, y1, colour, alpha=255):
    img[y0:y1, x0:x1, :3] = N.hex_rgb(colour)
    img[y0:y1, x0:x1, 3] = alpha


LED = {1: "#3aa040", 2: "#3a6ad0", 3: "#d03a2a"}


def blocks() -> None:
    # Keycard reader: a dark box with a card slot and a lamp in its level's colour; lit green while open.
    for level, colour in LED.items():
        for on in (False, True):
            def paint(img, r, colour=colour, on=on):
                rect(img, 0, 0, 16, 16, "#2a2c30")
                img[..., :3] *= (0.9 + 0.2 * r.random((16, 16)))[..., None]
                rect(img, 5, 3, 11, 13, "#3a3c42")
                rect(img, 6, 5, 10, 6, "#0e0e10")
                rect(img, 7, 9, 9, 11, "#7aff7a" if on else colour)
                rect(img, 5, 3, 11, 4, "#4a4c52")
            name = f"keycard_reader_{level}" + ("_on" if on else "")
            tex(name, paint, seed=level * 10 + on)
            D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": {"face": f"{NS}:block/{name}", "side": f"{NS}:block/{name}",
                "particle": f"{NS}:block/{name}"}, "elements": [{"from": [5, 0, 3], "to": [11, 2, 13], "faces": {
                    "up": {"uv": [5, 3, 11, 13], "texture": "#face"}, "down": {"uv": [5, 3, 11, 13], "texture": "#side", "cullface": "down"},
                    "north": {"uv": [5, 0, 11, 2], "texture": "#side"}, "south": {"uv": [5, 0, 11, 2], "texture": "#side"},
                    "west": {"uv": [3, 0, 13, 2], "texture": "#side"}, "east": {"uv": [3, 0, 13, 2], "texture": "#side"}}}]})
    rot = {("floor", "east"): (0, 90), ("floor", "west"): (0, 270), ("floor", "south"): (0, 180), ("floor", "north"): (0, 0),
           ("wall", "east"): (90, 90), ("wall", "west"): (90, 270), ("wall", "south"): (90, 180), ("wall", "north"): (90, 0),
           ("ceiling", "east"): (180, 270), ("ceiling", "west"): (180, 90), ("ceiling", "south"): (180, 0), ("ceiling", "north"): (180, 180)}
    variants = {}
    for (face, facing), (x, y) in rot.items():
        for level in LED:
            for powered in ("false", "true"):
                v = {"model": f"{NS}:block/keycard_reader_{level}" + ("_on" if powered == "true" else "")}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if face == "wall":
                    v["uvlock"] = True
                variants[f"face={face},facing={facing},level={level},powered={powered}"] = v
    D.asset_json("blockstates/keycard_reader.json", {"variants": variants})
    N.save_png(N.asset("textures/item/keycard_reader.png"), *V.icon(lambda d: (d.rectangle([10, 6, 22, 26], fill="#2a2c30"),
                                                                              d.rectangle([13, 9, 19, 10], fill="#0e0e10"),
                                                                              d.rectangle([15, 16, 17, 18], fill="#3aa040")), 8301))
    D.asset_json("models/item/keycard_reader.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/keycard_reader"}})
    D.item_model_definition("keycard_reader", f"{NS}:item/keycard_reader")

    # Security door: brushed steel, a wired window above, hazard stripes below.
    def door_top(img, r):
        rect(img, 0, 0, 16, 16, "#6a6e72")
        img[..., :3] *= (0.92 + 0.12 * r.random((16, 16)))[..., None]
        rect(img, 4, 3, 12, 11, "#2a3a44")
        for k in range(4, 12, 3):
            img[3:11, k, :3] = N.hex_rgb("#5a6a72")
        rect(img, 0, 0, 16, 1, "#4a4e52")
        rect(img, 0, 0, 1, 16, "#4a4e52")
        rect(img, 15, 0, 16, 16, "#4a4e52")

    def door_bottom(img, r):
        rect(img, 0, 0, 16, 16, "#6a6e72")
        img[..., :3] *= (0.92 + 0.12 * r.random((16, 16)))[..., None]
        for x in range(-16, 16, 4):
            for k in range(2):
                for y in range(10, 15):
                    xx = x + (y - 10) + k
                    if 0 <= xx < 16:
                        img[y, xx, :3] = N.hex_rgb("#c8a040")
        rect(img, 12, 2, 14, 4, "#2a2a2a")
        rect(img, 0, 15, 16, 16, "#4a4e52")
        rect(img, 0, 0, 1, 16, "#4a4e52")
        rect(img, 15, 0, 16, 16, "#4a4e52")
    tex("security_door_top", door_top, seed=71)
    tex("security_door_bottom", door_bottom, seed=72)
    models = {}
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for opened in ("", "_open"):
                name = f"security_door_{half}_{hinge}{opened}"
                D.asset_json(f"models/block/{name}.json", {"parent": f"minecraft:block/door_{half}_{hinge}{opened}",
                    "textures": {"bottom": f"{NS}:block/security_door_bottom", "top": f"{NS}:block/security_door_top"}})
                models[(half, hinge, opened)] = f"{NS}:block/{name}"
    closed = {"east": 0, "south": 90, "west": 180, "north": 270}
    open_left = {"east": 90, "south": 180, "west": 270, "north": 0}
    open_right = {"east": 270, "south": 0, "west": 90, "north": 180}
    variants = {}
    for facing in closed:
        for half_state, half in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    model = models[(half, hinge, "_open" if opened == "true" else "")]
                    y = closed[facing] if opened == "false" else (open_left if hinge == "left" else open_right)[facing]
                    v = {"model": model}
                    if y:
                        v["y"] = y
                    for powered in ("false", "true"):
                        variants[f"facing={facing},half={half_state},hinge={hinge},open={opened},powered={powered}"] = v
    D.asset_json("blockstates/security_door.json", {"variants": variants})
    D.asset_json("models/item/security_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/security_door"}})
    D.item_model_definition("security_door", f"{NS}:item/security_door")

    # Radio transmitter: a steel cabinet with dials; lit when repaired.
    for repaired in (False, True):
        def front(img, r, repaired=repaired):
            rect(img, 0, 0, 16, 16, "#4a4e48")
            img[..., :3] *= (0.9 + 0.2 * r.random((16, 16)))[..., None]
            rect(img, 2, 2, 14, 7, "#1a1c18")
            if repaired:
                for x in range(3, 13, 2):
                    img[3:6, x, :3] = N.hex_rgb("#6ae070" if x % 4 else "#e0c060")
            rect(img, 3, 9, 6, 12, "#2a2c28")
            rect(img, 10, 9, 13, 12, "#2a2c28")
            rect(img, 4, 10, 5, 11, "#c8a040" if repaired else "#5a5a50")
            rect(img, 11, 10, 12, 11, "#c8a040" if repaired else "#5a5a50")
            rect(img, 6, 13, 10, 14, "#a02a20" if not repaired else "#3aa040")
        name = "radio_transmitter" + ("_on" if repaired else "")
        tex(name + "_front", front, seed=90 + repaired)
        D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": {"front": f"{NS}:block/{name}_front",
            "side": f"{NS}:block/corrugated_metal", "particle": f"{NS}:block/corrugated_metal"},
            "elements": [{"from": [1, 0, 2], "to": [15, 15, 14], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#front"}, "south": {"uv": [1, 1, 15, 16], "texture": "#side"},
                "east": {"uv": [2, 1, 14, 16], "texture": "#side"}, "west": {"uv": [2, 1, 14, 16], "texture": "#side"},
                "up": {"uv": [1, 2, 15, 14], "texture": "#side"}, "down": {"uv": [1, 2, 15, 14], "texture": "#side", "cullface": "down"}}}]})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for repaired in ("false", "true"):
            v = {"model": f"{NS}:block/radio_transmitter" + ("_on" if repaired == "true" else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},repaired={repaired}"] = v
    D.asset_json("blockstates/radio_transmitter.json", {"variants": variants})
    D.item_model_definition("radio_transmitter", f"{NS}:block/radio_transmitter")
    for name in ("radio_transmitter", "keycard_reader"):
        D.data_json(f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    D.data_json("loot_table/blocks/security_door.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item",
        "name": f"{NS}:security_door", "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:security_door",
        "properties": {"half": "lower"}}]}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    pick = N.ROOT / "src" / "main" / "resources" / "data" / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json"
    if pick.exists():
        table = json.loads(pick.read_text(encoding="utf-8"))
        if f"{NS}:radio_transmitter" not in table["values"]:
            table["values"].append(f"{NS}:radio_transmitter")
        pick.write_text(json.dumps(table, indent=2) + "\n", encoding="utf-8")


# ============================================================================= helicopter

def helicopter_rig():
    P = V.Part
    parts = [P("chassis", None, (0, 24, 0), [], None)]
    add = lambda name, cubes, role: parts.append(P(name, "chassis", (0, 0, 0), cubes, role))
    add("cabin", [(-12, -26, -24, 24, 22, 34)], "body")
    add("nose", [(-10, -22, -32, 20, 16, 8)], "cabin")
    add("boom", [(-3, -22, 10, 6, 6, 36)], "body")
    add("fin", [(-1, -36, 42, 2, 14, 6)], "body")
    add("skids", [(-15, -2, -22, 2, 2, 40), (13, -2, -22, 2, 2, 40), (-14, -4, -14, 1, 2, 1), (13, -4, -14, 1, 2, 1),
                  (-14, -4, 10, 1, 2, 1), (13, -4, 10, 1, 2, 1)], "metal")
    add("mast", [(-1.5, -30, -1.5, 3, 4, 3)], "metal")
    parts.append(P("rotor", "chassis", (0, -30, 0), [(-48, -1, -2, 96, 1, 4), (-2, -1, -48, 4, 1, 96)], "blade"))
    parts.append(P("tail_rotor", "chassis", (2, -30, 45), [(0, -7, -1, 1, 14, 2), (0, -1, -7, 1, 2, 14)], "blade"))
    V.pack(parts)
    return parts


def helicopter() -> None:
    parts = helicopter_rig()
    body = V.java_layer("helicopter", parts)
    src = f"""package com.deadlands.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/assetgen/story_assets.py - do not edit; change the rig there and rerun gen_story.py.
 *
 * <p>The helicopter at Site Calder: cabin, nose, tail boom and fin, skids, and the "rotor" and
 * "tail_rotor" parts the renderer spins.
 */
@SuppressWarnings("unused")
public final class HelicopterGeometry {{
	private HelicopterGeometry() {{
	}}

{body}
}}
"""
    (JAVA_CLIENT / "HelicopterGeometry.java").write_text(src, encoding="utf-8")
    img = np.zeros((V.TEX_H, V.TEX_W, 4))
    r = np.random.default_rng(7707)
    colours = {"body": "#4a5236", "cabin": "#2a3a44", "metal": "#3a3c3e", "blade": "#2a2c2a"}
    for p in parts:
        if p.role is None:
            continue
        for (u, v), c in zip(p.uv, p.cubes):
            for name, (x, y, w, h) in V.faces(u, v, c).items():
                if w <= 0 or h <= 0:
                    continue
                col = colours[p.role]
                img[y:y + h, x:x + w, :3] = N.hex_rgb(col)
                img[y:y + h, x:x + w, 3] = 255
                if p.name == "cabin" and name in ("left", "right") and h > 8:
                    img[y + 3:y + h // 2, x + 4:x + w // 2, :3] = N.hex_rgb("#2a3a44")
                    img[y + 3:y + h // 2, x + w // 2 + 3:x + w - 6, :3] = N.hex_rgb("#2a3a44")
                    img[y + h - 6:y + h - 4, x:x + w, :3] = N.hex_rgb("#e8e0c8")
                if p.name == "nose" and name == "front":
                    img[y + 2:y + h // 2 + 2, x + 2:x + w - 2, :3] = N.hex_rgb("#2a3a44")
                if p.role == "blade" and name in ("top", "bottom"):
                    img[y:y + h, x + w - 6:x + w, :3] = N.hex_rgb("#c8a040")
    n = N.spectral(V.TEX_H, V.TEX_W, beta=1.8, seed=7708)
    solid = img[..., 3] > 0
    img[..., :3] *= (0.86 + 0.28 * n)[..., None]
    dirt = n > 0.7
    img[dirt & solid, :3] = img[dirt & solid, :3] * 0.6 + np.array(N.hex_rgb("#4a4034")) * 0.4
    img[~solid] = 0
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(N.asset("textures/entity/helicopter.png"))


# ============================================================================= gui, sounds, data

def archive_icon() -> None:
    from gen_gui import ascii_icon, save_sprite
    pal = {"o": "#1b1410", "w": "#e8e1cf", "s": "#a99f88", "k": "#3a2e26"}
    rows = [
        "................",
        "................",
        ".oooooooooooooo.",
        ".owwwwwwwwwwwwo.",
        ".owssssssssssso.",
        ".owwwwwkkwwwwwo.",
        ".oooooooooooooo.",
        ".owwwwwwwwwwwwo.",
        ".owssssssssssso.",
        ".owwwwwkkwwwwwo.",
        ".oooooooooooooo.",
        ".owwwwwwwwwwwwo.",
        ".owssssssssssso.",
        ".owwwwwkkwwwwwo.",
        ".oooooooooooooo.",
        "................",
    ]
    rgb, a = ascii_icon(rows, pal)
    save_sprite(N.ASSETS, "icon/archive.png", rgb, a)


def sounds() -> None:
    from gen_audio import OUT, SR, write_ogg
    r = np.random.default_rng(8801)
    n = int(SR * 2.0)
    spectrum = np.fft.rfft(r.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    spectrum *= np.where((freqs > 300) & (freqs < 6000), 1.0, 0.15)
    hiss = np.fft.irfft(spectrum, n=n)
    hiss /= np.max(np.abs(hiss))
    crackle = np.zeros(n)
    for at in r.integers(0, n - 200, 40):
        crackle[at:at + 60] += r.standard_normal(60) * np.exp(-np.arange(60) / 10.0) * r.uniform(0.5, 1.5)
    static = np.clip(hiss * 0.5 + crackle * 0.4, -1, 1) * 0.6
    t = np.arange(int(SR * 0.45)) / SR
    tune = np.sin(2 * np.pi * np.cumsum(np.linspace(1800, 300, t.size)) / SR) * np.exp(-t * 4) * 0.4 + r.standard_normal(t.size) * 0.08
    t2 = np.arange(int(SR * 0.5)) / SR
    thump = np.sin(2 * np.pi * 38 * t2) * np.exp(-((t2 % 0.125) * 30)) + r.standard_normal(t2.size) * np.exp(-((t2 % 0.125) * 40)) * 0.3
    thump = thump / np.max(np.abs(thump)) * 0.8
    table = json.loads((OUT.parent / "sounds.json").read_text(encoding="utf-8"))
    for event, rel, data in (("radio.static", "radio/static", static), ("radio.tune", "radio/tune", tune), ("helicopter.rotor", "vehicle/rotor", thump)):
        write_ogg(rel, data, quality=4)
        table[event] = {"sounds": [f"{NS}:{rel}"], "subtitle": f"subtitles.{NS}.{event}"}
    (OUT.parent / "sounds.json").write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


def data() -> None:
    def item(name, lo=1, hi=1, weight=1):
        e = {"type": "minecraft:item", "name": name if ":" in name else f"{NS}:{name}", "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e
    D.data_json("loot_table/story/harley_camp.json", {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [item("water_bottle", 2, 2)]}, {"rolls": 1, "entries": [item("canned_beans", 2, 2)]},
        {"rolls": 1, "entries": [item("bandage", 3, 3)]}, {"rolls": 1, "entries": [item("wrench")]},
        {"rolls": 1, "entries": [item("radio_parts")]}, {"rolls": 1, "entries": [item("crowbar")]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:document", "functions": [{"function": "minecraft:set_components",
            "components": {f"{NS}:lore": "harley_research_1", "minecraft:custom_model_data": {"strings": ["document"]}}}]}]}]})
    for place, structure in (("research_facility", "research_facility"), ("military_facility", "site_calder"), ("evacuation_site", "evacuation_camp"),
                             ("expedition_camp", "expedition_camp")):
        D.data_json(f"tags/worldgen/structure/story/{place}.json", {"replace": False, "values": [f"{NS}:{structure}"]})


def build() -> None:
    icons()
    blocks()
    helicopter()
    archive_icon()
    sounds()
    data()
    D.merge_lang(TEXT)
