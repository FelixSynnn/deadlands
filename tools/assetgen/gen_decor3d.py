"""Phase 3 batch 2: detailed 3D models for furniture and roadside fixtures.

The decor blocks (`gen_decor.py`, `block/DecorKind.java`) started as one to nine boxes each, some a
plain cube (the bookshelf, the transformer). This generator rebuilds the weakest of them with
`dl_models3d` and writes over their block models, keeping their names, blockstates, facing
convention (the front faces north) and collision shapes; painted art that already works (a stove
front, a generator's control panel, a whiteboard's writing, worktops, a mattress) is kept by mapping
those faces to the existing textures.

Desks, bookshelves and counters get several versions picked at random per position (weighted
blockstate variants), so rooms do not repeat: a desk may hold an old CRT computer, or a lamp and a
phone; shelves are filled differently; some counters have a microwave. Runs after `gen_decor`.
"""
from __future__ import annotations

import numpy as np

import dl_data as D
import dl_noise as N
from dl_models3d import box, build as build_model, rod, rod_y, rod_z

NS = "deadlands"
DECOR = f"{NS}:block/decor/"


def paint(color, chip=0.15, rust=0.3, **kw):
    return dict({"kind": "paint", "color": color, "chip": chip, "under_rust": rust}, **kw)


def plastic(color, **kw):
    return dict({"kind": "polymer", "color": color}, **kw)


GALV = {"kind": "metal", "base": "#9ba1a3", "dark": "#666c6e", "brush": 0.4, "rust": 0.22, "dirt": 0.2}
GALV_V = dict(GALV, vertical=True)
STEEL = {"kind": "metal", "base": "#a9afb3", "dark": "#6a7074", "brush": 0.6}
DARK_WOOD = {"kind": "wood", "light": "#6a4a30", "dark": "#36241a", "rings": 20.0, "dirt": 0.25}
LIGHT_WOOD = {"kind": "wood", "light": "#9a7a54", "dark": "#5e4430", "rings": 18.0, "dirt": 0.25}
POLE_WOOD = {"kind": "wood", "light": "#7a6c58", "dark": "#463c30", "rings": 12.0, "vertical": True, "dirt": 0.3}


def ext(name, uv):
    return (DECOR + name, uv)


# ----------------------------------------------------------------------------- roadside

def utility_pole():
    b = [*rod_y(0, 16, 5.8, "pole", sides=3),
         box(10.6, 4.0, 7.65, 12.4, 4.5, 8.35, "bolt"), box(3.6, 11.0, 7.65, 5.4, 11.5, 8.35, "bolt")]  # step bolts
    return b, {"pole": POLE_WOOD, "bolt": dict(STEEL, rust=0.5)}


def street_lamp():
    return rod_y(0, 16, 3.0, "metal", sides=2), {"metal": GALV_V}


def street_lamp_head(lit: bool):
    b = [*rod_y(0, 9.6, 3.0, "metal", sides=2),
         box(7.1, 8.4, 2.4, 8.9, 9.8, 8.0, "metal"),                          # arm out over the street (north)
         box(4.8, 6.4, -0.4, 11.2, 8.6, 5.2, "housing"),                      # cobra-head luminaire
         box(5.4, 6.8, -1.2, 10.6, 8.2, -0.4, "housing"),
         box(5.6, 8.6, 0.4, 10.4, 9.2, 4.4, "housing"),
         box(5.4, 6.1, 0.0, 10.6, 6.4, 4.8, "lens", glow=15 if lit else 0)]
    lens = {"kind": "lens", "color": "#e8c070", "hot": "#fff4d0"} if lit else {"kind": "lens", "color": "#3a3a36", "hot": "#5a5a54"}
    return b, {"metal": GALV, "housing": paint("#5c6062", chip=0.1, rust=0.5), "lens": lens}


def traffic_light():
    b = [*rod_y(0, 16, 3.0, "metal", sides=2),
         box(4.2, 3.4, 6.4, 11.8, 15.6, 6.6, "plate"),                        # backplate against the pole
         box(5.0, 4.0, 2.5, 11.0, 15.0, 6.4, "housing")]
    for cy, lens in ((13.0, "red"), (9.5, "amber"), (6.0, "green")):
        b += rod_z(2.2, 2.5, 3.0, lens, cy=cy, sides=3, skip=("south",))
        b += [box(5.6, cy + 1.5, 0.6, 10.4, cy + 1.8, 2.5, "housing"),        # visor hood
              box(5.6, cy - 0.6, 0.6, 5.9, cy + 1.8, 2.5, "housing"), box(10.1, cy - 0.6, 0.6, 10.4, cy + 1.8, 2.5, "housing")]
    m = {"metal": GALV_V, "plate": plastic("#161616"), "housing": paint("#2a2e26", chip=0.12, rust=0.4),
         "red": {"kind": "lens", "color": "#4a1410", "hot": "#7a261c"}, "amber": {"kind": "lens", "color": "#5a3e12", "hot": "#8a6224"},
         "green": {"kind": "lens", "color": "#143a22", "hot": "#24603a"}}
    return b, m


def transformer():
    b = [*rod_y(0.5, 12.0, 9.0, "tank", cz=8.2, sides=3),
         *rod_y(12.0, 12.8, 9.4, "tank", cz=8.2, sides=3),
         *rod_y(0.0, 0.5, 9.4, "tank", cz=8.2, sides=3),
         *rod_y(12.8, 15.0, 1.4, "porcelain", cx=5.5, cz=8.2, sides=2),      # bushings
         *rod_y(12.8, 15.0, 1.4, "porcelain", cx=10.5, cz=8.2, sides=2),
         box(5.5, 3.0, 12.6, 10.5, 11.0, 13.0, "bracket"),                   # pole bracket
         box(6.5, 6.5, 3.55, 9.5, 8.5, 3.7, "label")]
    return b, {"tank": paint("#6f776c", chip=0.15, rust=0.5, dirt=0.2), "porcelain": plastic("#6a3a24"), "bracket": GALV,
               "label": plastic("#d8d4c4")}


def guardrail():
    b = [box(6.6, 0, 13.0, 9.4, 9.0, 13.4, "post"), box(7.6, 0, 13.4, 8.4, 9.0, 14.6, "post"), box(6.6, 0, 14.6, 9.4, 9.0, 15.0, "post"),  # I-beam post
         box(7.2, 4.6, 12.9, 8.8, 7.6, 13.0, "post"),                        # blockout
         box(0, 6.6, 11.6, 16, 8.0, 12.4, "beam"), box(0, 5.6, 12.1, 16, 6.6, 12.6, "beam"),   # W-beam: two bulges and the channel
         box(0, 4.2, 11.6, 16, 5.6, 12.4, "beam"), box(0, 4.2, 12.4, 16, 8.0, 12.9, "beam"),
         box(7.6, 5.85, 11.95, 8.4, 6.35, 12.1, "bolt")]
    return b, {"post": dict(GALV_V, rust=0.35), "beam": dict(GALV, rust=0.3), "bolt": STEEL}


def power_generator(running: bool):
    front = "generator_front_on" if running else "generator_front_off"
    b = []
    for cx in (1.8, 14.2):
        for cz in (2.8, 13.2):
            b += rod_y(0, 13.6, 1.2, "frame", cx=cx, cz=cz, sides=2)
    for cy in (0.6, 13.4):
        for cz in (2.8, 13.2):
            b += rod(1.2, 14.8, 1.2, "frame", cy=cy, cz=cz, sides=2, ends=False)
        for cx in (1.8, 14.2):
            b += rod_z(2.2, 13.8, 1.2, "frame", cx=cx, cy=cy, sides=2, ends=False)
    b += [box(2.8, 9.8, 3.6, 13.2, 12.8, 12.4, "tank"),                      # fuel tank
          *rod_y(12.8, 13.6, 1.4, "cap", cx=5.0, cz=6.0, sides=2),
          box(3.0, 1.2, 4.0, 9.8, 9.4, 12.0, "engine"),
          *rod(9.8, 10.6, 4.0, "engine", cy=5.2, cz=8.0, sides=3),            # recoil starter
          box(10.2, 1.5, 4.5, 13.6, 8.6, 11.5, "alternator"),
          box(3.5, 0.9, 2.6, 12.5, 9.6, 3.6, "panel", ext={"north": ext(front, [0, 0, 14, 14])})]
    return b, {"frame": paint("#2c2e30", chip=0.2, rust=0.5), "tank": paint("#c09a2a", chip=0.18, rust=0.4, dirt=0.25),
               "cap": plastic("#1a1a1a"), "engine": {"kind": "metal", "base": "#4a4e52", "dark": "#24272a", "brush": 0.3, "dirt": 0.4},
               "alternator": dict(GALV, rust=0.1), "panel": paint("#3a3c3e", chip=0.1)}


# ----------------------------------------------------------------------------- furniture

BOOK_COLOURS = ["#5a1e1a", "#1e3a5a", "#2e4a2a", "#6a5a3a", "#3a2a3a", "#8a6a2a", "#2a2a2a", "#7a3a1e", "#4a4a52"]


def bookshelf(seed: int, looted: float):
    rng = np.random.default_rng(seed)
    b = [box(0, 0, 8, 1, 16, 16, "wood"), box(15, 0, 8, 16, 16, 16, "wood"), box(1, 15, 8, 15, 16, 16, "wood"),
         box(1, 0, 8, 15, 1, 16, "wood"), box(1, 1, 15.4, 15, 15, 16, "wood")]
    for y in (5.0, 10.0):
        b.append(box(1, y, 8.4, 15, y + 0.8, 15.4, "wood"))
    for floor, room in ((1.0, 4.0), (5.8, 4.2), (10.8, 4.2)):
        x = 1.15
        while x < 14.6:
            if rng.random() < looted:
                x += 1.0 + rng.random() * 2.0  # a gap: taken
                continue
            w = 0.7 + rng.random() * 0.8
            if x + w > 14.85:
                break
            h = min(room - 0.15, 2.6 + rng.random() * 1.3)
            depth = 4.8 + rng.random() * 1.6
            colour = f"book{int(rng.integers(0, len(BOOK_COLOURS)))}"
            rot = None
            if rng.random() < 0.08:  # one leaning on its neighbour
                rot = {"origin": [x, floor, 12.0], "axis": "z", "angle": float(-10 - rng.random() * 8)}
            b.append(box(x, floor, 15.3 - depth, x + w, floor + h, 15.3, colour, rot))
            x += w + 0.04
    m = {"wood": DARK_WOOD}
    for i, c in enumerate(BOOK_COLOURS):
        m[f"book{i}"] = paint(c, chip=0.05, rust=0.0, dirt=0.3)
    return b, m


def whiteboard():
    b = [box(0.6, 3.6, 15.4, 15.4, 13.4, 15.9, "back", ext={"north": ext("whiteboard", [0, 0, 16, 11])}),
         box(0, 13.4, 15.2, 16, 14.0, 16, "frame"), box(0, 3.0, 15.2, 16, 3.6, 16, "frame"),
         box(0, 3.6, 15.2, 0.6, 13.4, 16, "frame"), box(15.4, 3.6, 15.2, 16, 13.4, 16, "frame"),
         box(1, 2.7, 14.0, 15, 3.1, 15.2, "frame"), box(1, 3.1, 14.0, 15, 3.4, 14.2, "frame"),   # marker tray
         *rod(3.0, 5.2, 0.45, "red", cy=3.35, cz=14.6, sides=1), *rod(5.8, 8.0, 0.45, "blue", cy=3.35, cz=14.6, sides=1),
         box(10, 3.1, 14.3, 12, 3.6, 15.0, "eraser")]
    return b, {"back": plastic("#d8d8d2"), "frame": dict(GALV, rust=0.05, base="#b4b8ba"), "red": plastic("#a82a20"),
               "blue": plastic("#2a4aa0"), "eraser": plastic("#2a2a2a")}


def counter(microwave: bool):
    b = [box(0, 0.8, 1.4, 16, 14, 16, "cab"), box(0, 0, 2.4, 16, 0.8, 16, "kick"),
         box(1.0, 1.6, 1.1, 7.6, 13.2, 1.4, "door"), box(8.4, 1.6, 1.1, 15.0, 13.2, 1.4, "door"),
         box(6.6, 9.5, 0.7, 7.0, 12.0, 1.1, "handle"), box(9.0, 9.5, 0.7, 9.4, 12.0, 1.1, "handle"),
         box(0, 14, 0, 16, 16, 16, "edge", ext={"up": ext("counter_top", [0, 0, 16, 16])})]
    m = {"cab": paint("#cfc5aa", chip=0.15, rust=0.0, dirt=0.35), "door": paint("#d6ccb2", chip=0.2, rust=0.0, dirt=0.3),
         "kick": plastic("#2a2622"), "handle": STEEL, "edge": plastic("#8a8678")}
    if microwave:
        b += [box(2.0, 16, 7.5, 10.5, 21.0, 15.5, "micro"),
              box(2.4, 16.6, 7.3, 8.2, 20.4, 7.5, "window"), box(8.4, 16.6, 7.3, 10.1, 20.4, 7.5, "keys")]
        m.update(micro=plastic("#d8d4cc", dirt=0.35), window={"kind": "lens", "color": "#1a1c1c", "hot": "#3a3e3e"}, keys=plastic("#3a3a3a"))
    return b, m


def workbench():
    b = [box(0, 14, 0, 16, 16, 16, "wood", ext={"up": ext("workbench_top", [0, 0, 16, 16])}),
         box(0.5, 12, 0.5, 15.5, 14, 15.5, "wood"),
         box(1, 0, 1, 3, 12, 3, "wood"), box(13, 0, 1, 15, 12, 3, "wood"), box(1, 0, 13, 3, 12, 15, "wood"), box(13, 0, 13, 15, 12, 15, "wood"),
         box(1.5, 2.5, 1.5, 14.5, 3.3, 14.5, "wood"),                         # lower shelf
         box(3.0, 3.3, 4.0, 9.0, 6.3, 8.0, "toolbox"), box(4.8, 6.3, 5.6, 7.2, 6.8, 6.4, "steel"),
         box(1.0, 16, -0.6, 5.0, 17.6, 2.6, "vise"), box(1.4, 16.4, -1.0, 4.6, 17.4, -0.6, "vise"),   # bench vise
         *rod_z(-2.8, -0.6, 0.4, "steel", cx=3.0, cy=16.8, sides=1),
         box(1.6, 16.6, -2.9, 4.4, 17.0, -2.6, "steel"),
         box(9.0, 16, 8.0, 13.5, 16.5, 8.6, "handle"), box(12.8, 16, 7.3, 13.8, 16.9, 9.3, "steel")]   # a hammer left on top
    return b, {"wood": DARK_WOOD, "toolbox": paint("#a8281e", chip=0.2), "vise": paint("#2a4a6a", chip=0.25, rust=0.5),
               "steel": STEEL, "handle": LIGHT_WOOD}


def desk(extra: str):
    b = [box(0, 14, 0, 16, 16, 16, "wood"),
         box(10, 0, 1, 15, 14, 15, "wood", ext={"north": ext("desk_drawers", [0, 0, 5, 14])}),
         box(1, 0, 1, 3, 14, 15, "wood"), box(3, 7, 14, 10, 14, 15, "wood")]
    m = {"wood": LIGHT_WOOD, "paper": plastic("#e2dccb", dirt=0.3)}
    if extra == "papers":
        b += [box(2.0, 16.0, 3.0, 6.5, 16.1, 7.0, "paper", {"origin": [4, 16, 5], "axis": "y", "angle": 12.0}),
              box(7.5, 16.0, 2.5, 11.5, 16.12, 5.5, "paper", {"origin": [9.5, 16, 4], "axis": "y", "angle": -8.0}),
              box(12.0, 16.0, 4.0, 15.0, 16.15, 4.25, "pen")]
        m["pen"] = plastic("#1e2a5a")
    elif extra == "computer":
        b += [box(4.5, 16, 7.5, 11.5, 21.5, 14.5, "beige"), box(5.5, 16.6, 14.5, 10.5, 20.8, 15.8, "beige"),   # CRT monitor
              box(5.2, 16.8, 7.3, 10.8, 20.8, 7.5, "screen"),
              box(3.5, 16, 2.5, 12.5, 16.6, 5.5, "keys"), box(13.0, 16, 3.5, 14.2, 16.5, 5.2, "beige")]
        m.update(beige=plastic("#cbc2a8", dirt=0.4), screen={"kind": "lens", "color": "#1c2220", "hot": "#34403c"}, keys=plastic("#bdb49a", stipple=True))
    else:  # lamp and phone
        b += [*rod_y(16, 16.4, 2.4, "lamp", cx=3.5, cz=12.5, sides=2),
              box(3.2, 16.4, 12.2, 3.8, 20.6, 12.8, "lamp", {"origin": [3.5, 16.4, 12.5], "axis": "x", "angle": -20.0}),
              box(2.4, 19.4, 9.0, 4.6, 20.6, 11.6, "lamp"),
              box(10.5, 16, 9.0, 14.0, 17.4, 13.0, "phone"), box(10.2, 17.4, 9.4, 14.3, 18.2, 10.4, "phone")]
        m.update(lamp=paint("#2a4a3a", chip=0.15), phone=plastic("#1a1a1a"))
    return b, m


def couch():
    b = [box(0, 1, 2, 16, 4.5, 16, "fabric"),
         box(0.8, 4.5, 2.2, 7.9, 7.6, 12.2, "cushion", seam=True), box(8.1, 4.5, 2.2, 15.2, 7.6, 12.2, "cushion", seam=True),
         box(0, 4.5, 12.2, 16, 15, 16, "fabric"),
         box(0.9, 7.6, 10.6, 7.9, 13.8, 12.4, "cushion", seam=True), box(8.1, 7.6, 10.6, 15.1, 13.8, 12.4, "cushion", seam=True),
         box(0, 4.5, 2, 1.6, 10.6, 12.2, "fabric"), box(14.4, 4.5, 2, 16, 10.6, 12.2, "fabric"),
         box(1, 0, 3, 2.2, 1, 4.2, "leg"), box(13.8, 0, 3, 15, 1, 4.2, "leg"), box(1, 0, 14, 2.2, 1, 15.2, "leg"), box(13.8, 0, 14, 15, 1, 15.2, "leg")]
    return b, {"fabric": {"kind": "fabric", "color": "#5e5e44", "dirt": 0.4}, "cushion": {"kind": "fabric", "color": "#6a6a4c", "dirt": 0.45},
               "leg": DARK_WOOD}


def bed_frame():
    b = [box(0, 1, 0, 16, 3.6, 16, "frame"),
         box(0, 0, 0, 1.4, 1, 1.4, "frame"), box(14.6, 0, 0, 16, 1, 1.4, "frame"), box(0, 0, 14.6, 1.4, 1, 16, "frame"), box(14.6, 0, 14.6, 16, 1, 16, "frame"),
         box(0, 3.6, 15, 16, 10.5, 16, "frame"), box(0, 3.6, 14.6, 1.2, 11.2, 16, "frame"), box(14.8, 3.6, 14.6, 16, 11.2, 16, "frame"),
         box(0.5, 3.6, 0.5, 15.5, 7.2, 14.9, "mattress", ext={"up": ext("mattress_top", [0, 0, 15, 14.5])}),
         box(0.3, 7.0, 0.3, 15.7, 7.8, 8.5, "blanket"), box(0.3, 7.6, 7.5, 15.7, 8.4, 9.5, "blanket"),   # a thrown-back blanket
         box(2.5, 7.2, 11.2, 13.5, 8.8, 14.4, "pillow", seam=True)]
    return b, {"frame": DARK_WOOD, "mattress": {"kind": "fabric", "color": "#cfc8b4", "dirt": 0.5}, "blanket": {"kind": "fabric", "color": "#4a5a6a", "dirt": 0.4},
               "pillow": {"kind": "fabric", "color": "#ddd6c4", "dirt": 0.45}}


# ----------------------------------------------------------------------------- writing

FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def model(name: str, boxes, materials, seed: int) -> None:
    texture = f"block/model/decor/{name}"
    elements, tex, _, extras = build_model(name, boxes, materials, seed, texture)
    N.save_png(N.asset(f"textures/{texture}.png"), tex[..., :3], tex[..., 3] / 255.0)
    textures = {"skin": f"{NS}:{texture}", "particle": f"{NS}:{texture}"}
    textures.update(extras)
    D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": textures, "elements": elements})


def variants_blockstate(kind: str, names: list[str]) -> None:
    """A facing block whose model is picked at random per position from `names`."""
    D.asset_json(f"blockstates/{kind}.json", {"variants": {
        f"facing={facing}": [dict({"model": f"{NS}:block/{n}"}, **({"y": y} if y else {})) for n in names]
        for facing, y in FACINGS}})


def build() -> None:
    model("utility_pole", *utility_pole(), 9401)
    model("street_lamp", *street_lamp(), 9402)
    model("street_lamp_head", *street_lamp_head(False), 9403)
    model("street_lamp_head_lit", *street_lamp_head(True), 9403)
    model("traffic_light", *traffic_light(), 9404)
    model("transformer", *transformer(), 9405)
    model("guardrail", *guardrail(), 9406)
    model("power_generator", *power_generator(False), 9407)
    model("power_generator_running", *power_generator(True), 9407)
    model("whiteboard", *whiteboard(), 9408)
    model("workbench", *workbench(), 9409)
    model("couch", *couch(), 9410)
    model("bed_frame", *bed_frame(), 9411)
    # Kinds with versions picked at random per position.
    model("bookshelf", *bookshelf(9420, 0.12), 9420)
    model("bookshelf_looted", *bookshelf(9421, 0.45), 9421)
    model("bookshelf_tight", *bookshelf(9422, 0.03), 9422)
    variants_blockstate("bookshelf", ["bookshelf", "bookshelf_looted", "bookshelf_tight"])
    model("desk", *desk("papers"), 9430)
    model("desk_computer", *desk("computer"), 9431)
    model("desk_lamp", *desk("lamp"), 9432)
    variants_blockstate("desk", ["desk", "desk_computer", "desk_lamp"])
    model("counter", *counter(False), 9440)
    model("counter_microwave", *counter(True), 9441)
    variants_blockstate("counter", ["counter", "counter", "counter_microwave"])
    print("  decor: 13 kinds rebuilt, 3 with random versions")


if __name__ == "__main__":
    build()
