"""Phase 3 (visual transformation): light fixtures and the new weather's text.

Fixtures (blocks in `registry/DLLights`), each a 3D box model built with `dl_models3d`:
  fluorescent_light  a ceiling fixture with two tubes; the tubes glow (element light emission) when
                     lit. Authored along X; the blockstate turns it for axis=z.
  emergency_light    a battery lamp on a wall with a red lens: off, dim and bright models for its
                     slow pulse. Authored facing north (on the wall to the south).
Also writes their blockstates, item models, loot tables, names and the pickaxe mining tag.
"""
from __future__ import annotations

import json

import dl_data as D
import dl_noise as N
from dl_models3d import box, build as build_model, rod

NS = "deadlands"

HOUSING = {"kind": "paint", "color": "#c9c6b8", "chip": 0.12, "under_rust": 0.6, "dirt": 0.45}
TUBE_LIT = {"kind": "lens", "color": "#e6eef2", "hot": "#ffffff"}
TUBE_OFF = {"kind": "lens", "color": "#7d8386", "hot": "#a3a9ac"}
CASE = {"kind": "polymer", "color": "#d8d4c8", "dirt": 0.4}
LENS_OFF = {"kind": "lens", "color": "#4a1612", "hot": "#6a241c"}
LENS_DIM = {"kind": "lens", "color": "#8a1d14", "hot": "#c8402c"}
LENS_BRIGHT = {"kind": "lens", "color": "#d8301e", "hot": "#ffb8a0"}
LED = {"kind": "lens", "color": "#2e6a2a", "hot": "#7fe07a"}


def fluorescent(lit: bool):
    tube = "tube"
    b = [
        box(1, 14.6, 4.5, 15, 16, 11.5, "housing"),
        box(1, 13.6, 5.0, 1.8, 14.6, 11.0, "housing"),
        box(14.2, 13.6, 5.0, 15, 14.6, 11.0, "housing"),
        *rod(1.8, 14.2, 0.9, tube, cy=14.1, cz=6.6, ends=False, glow=15 if lit else 0),
        *rod(1.8, 14.2, 0.9, tube, cy=14.1, cz=9.4, ends=False, glow=15 if lit else 0),
    ]
    return b, {"housing": HOUSING, "tube": TUBE_LIT if lit else TUBE_OFF}


def emergency(level: str):
    glow = {"off": 0, "dim": 8, "bright": 15}[level]
    b = [
        box(4, 9, 15.2, 12, 14, 16, "case"),                       # back plate on the wall
        box(4.5, 9.5, 13.6, 11.5, 13.5, 15.2, "case"),              # body
        box(5.2, 10.2, 13.0, 10.8, 12.8, 13.6, "lens", glow=glow),  # red lens
        box(10.0, 9.7, 13.5, 10.6, 10.1, 13.6, "led", glow=4 if level != "off" else 0),
    ]
    return b, {"case": CASE, "lens": {"off": LENS_OFF, "dim": LENS_DIM, "bright": LENS_BRIGHT}[level], "led": LED}


def block_model(name: str, boxes, materials, seed: int, gui_y: float = 0.0) -> None:
    texture = f"block/model/{name}"
    elements, tex, _, _ = build_model(name, boxes, materials, seed, texture)
    N.save_png(N.asset(f"textures/{texture}.png"), tex[..., :3], tex[..., 3] / 255.0)
    model = {"parent": "minecraft:block/block", "ambientocclusion": False,
             "textures": {"skin": f"{NS}:{texture}", "particle": f"{NS}:{texture}"}, "elements": elements}
    if gui_y:
        model["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, gui_y, 0], "scale": [0.625, 0.625, 0.625]}}
    D.asset_json(f"models/block/{name}.json", model)


def loot(name: str) -> None:
    D.data_json(f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


TEXT = {
    "block.deadlands.fluorescent_light": "Fluorescent Light",
    "block.deadlands.emergency_light": "Emergency Light",
    "weather.deadlands.heavy_rain": "heavy rain",
}


def build_all() -> None:
    # Fluorescent: lit and dark models; the blockstate turns them for axis=z.
    for lit in (True, False):
        boxes, materials = fluorescent(lit)
        block_model("fluorescent_light" if lit else "fluorescent_light_off", boxes, materials, 9201, gui_y=-3.5)
    variants = {}
    for axis in ("x", "z"):
        for faulty in ("false", "true"):
            for lit in ("false", "true"):
                v = {"model": f"{NS}:block/fluorescent_light" + ("" if lit == "true" else "_off")}
                if axis == "z":
                    v["y"] = 90
                variants[f"axis={axis},faulty={faulty},lit={lit}"] = v
    D.asset_json("blockstates/fluorescent_light.json", {"variants": variants})
    D.item_model_definition("fluorescent_light", f"{NS}:block/fluorescent_light")

    for level in ("off", "dim", "bright"):
        boxes, materials = emergency(level)
        block_model(f"emergency_light_{level}", boxes, materials, 9202)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            for pulse in ("false", "true"):
                level = "off" if lit == "false" else ("bright" if pulse == "true" else "dim")
                v = {"model": f"{NS}:block/emergency_light_{level}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},lit={lit},pulse={pulse}"] = v
    D.asset_json("blockstates/emergency_light.json", {"variants": variants})
    D.item_model_definition("emergency_light", f"{NS}:block/emergency_light_bright")

    for name in ("fluorescent_light", "emergency_light"):
        loot(name)
    pick = N.ROOT / "src" / "main" / "resources" / "data" / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json"
    table = json.loads(pick.read_text(encoding="utf-8"))
    for name in ("fluorescent_light", "emergency_light"):
        if f"{NS}:{name}" not in table["values"]:
            table["values"].append(f"{NS}:{name}")
    pick.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang(TEXT)
    print("  fixtures: fluorescent_light (2 models), emergency_light (3 models)")


def build() -> None:  # build_all.py entry point
    build_all()


if __name__ == "__main__":
    build_all()
