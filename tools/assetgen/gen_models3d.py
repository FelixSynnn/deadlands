"""Phase 3 (visual transformation): real 3D models for every weapon and the flashlight.

Hard rule: a weapon is never an enlarged item sprite. Each model is built from boxes (Minecraft's
JSON model elements) with `dl_models3d`, unwrapped face by face onto its own texture at 8 texels per
unit and painted by material: brushed steel with a ground bevel, rust and worn edges; chipped paint;
wood grain; rubber grip rings; tape; checkered grips; brass.

The inventory keeps the flat icon (vanilla's spyglass pattern, a `display_context` select), as the
master prompt allows 2D for practical UI. In the hands, on the ground, in item frames and on shelves
the item is the 3D model, `models/item/<name>_3d.json`.

Melee weapons and long guns are built along +X (butt at the low end) and turned 45 degrees by their
display transforms, so they sit where the tuned flat sprite sat (handle bottom-left, tip top-right);
the left hand turns the other way because Minecraft mirrors left-hand rotations when applying them.
The pistol is built facing +X like its sprite, and the flashlight along -Z like its earlier model.
Every item keeps its own display tuning (knives smaller, firearms pointing ahead).

These are good cuboid models, not artist meshes (listed in docs/ASSET_MANIFEST.md). Preview them
offline with `preview_models.py` (it renders these files; it never starts the game).
"""
from __future__ import annotations

import copy
import json

import dl_data as D
import dl_noise as N
from dl_models3d import bar, box, build as build_model, chain, rod, rod_y, rod_z, zrot

NS = "deadlands"

# ----------------------------------------------------------------------------- materials

STEEL = {"kind": "metal", "base": "#a9afb3", "dark": "#6a7074", "brush": 0.8}
BLADE = {"kind": "metal", "base": "#bcc2c6", "dark": "#7c8286", "brush": 1.0}
STEEL_DARK = {"kind": "metal", "base": "#5f6468", "dark": "#33373a", "brush": 0.4}
GUN_BLACK = {"kind": "metal", "base": "#43474b", "dark": "#1d1f22", "brush": 0.3}
GUN_GRAY = {"kind": "metal", "base": "#7b7f83", "dark": "#4b4e52", "brush": 0.25}
BRASS = {"kind": "brass", "base": "#c59b4b", "dark": "#7a5a26", "brush": 0.5}
RUBBER = {"kind": "rubber", "color": "#2a2b2d", "rings": 2.5}
RUBBER_PLAIN = {"kind": "rubber", "color": "#262729"}
POLYMER = {"kind": "polymer", "color": "#2c2e30", "stipple": True}
PALE_WOOD = {"kind": "wood", "light": "#d2bd92", "dark": "#9c8358", "rings": 18.0, "dirt": 0.25}
WALNUT = {"kind": "wood", "light": "#7a4e2e", "dark": "#3e2414", "rings": 24.0, "dirt": 0.15}
STOCK_WOOD = {"kind": "wood", "light": "#8a5a34", "dark": "#4a2c18", "rings": 20.0, "dirt": 0.2}


def mats(**extra) -> dict:
    base = {"steel": STEEL, "blade": BLADE, "steel_dark": STEEL_DARK, "gun_black": GUN_BLACK, "gun_gray": GUN_GRAY,
            "brass": BRASS, "rubber": RUBBER, "rubber_plain": RUBBER_PLAIN, "polymer": POLYMER}
    base.update(extra)
    return base


# ----------------------------------------------------------------------------- melee (built along +X, centre line y = z = 8)

def machete(rusty: bool = False):
    blood = 0.22 if rusty else 0.0
    b = [
        box(-0.1, 6.95, 7.2, 0.5, 9.05, 8.8, "grip"),                       # pommel
        bar(0.5, 5.0, 8.0, 8.0, 1.7, 1.4, "grip"),                          # handle
        box(1.4, 7.7, 7.15, 2.0, 8.3, 8.85, "brass"),                       # rivets
        box(3.5, 7.7, 7.15, 4.1, 8.3, 8.85, "brass"),
        box(5.0, 6.9, 7.25, 5.5, 9.1, 8.75, "steel_dark"),                  # bolster
        box(5.5, 6.7, 7.78, 12.2, 9.3, 8.22, "blade", bevel=0.9, blood=blood),
        box(11.8, 6.4, 7.78, 14.6, 9.3, 8.22, "blade", bevel=1.0, blood=blood),
        box(14.2, 6.4, 7.8, 15.6, 8.8, 8.2, "blade", bevel=1.0),
        box(15.0, 6.5, 7.82, 17.0, 7.9, 8.18, "blade", zrot(15.0, 6.5, 20), bevel=0.9),          # point, edge sweeping up
        box(14.2, 8.4, 7.82, 16.6, 9.3, 8.18, "blade", zrot(14.2, 9.3, -22)),                   # clipped spine
    ]
    m = mats(grip={"kind": "wood", "light": "#3a2a1e", "dark": "#1a120c", "rings": 30.0} if not rusty else
             {"kind": "tape", "color": "#6d665a", "dirt": 0.4})
    if rusty:
        m["blade"] = dict(BLADE, base="#8f8a84", dark="#55504b", rust=0.62, dirt=0.3)
        m["steel_dark"] = dict(STEEL_DARK, rust=0.7)
        m["brass"] = dict(BRASS, dirt=0.5)
    return b, m


def crowbar():
    b = [
        box(-0.1, 7.55, 7.2, 1.2, 8.45, 8.8, "steel"),                     # flattened chisel end
        *rod(1.0, 11.9, 1.15, "red"),
        *chain(11.6, 8.0, [(1.1, 25), (1.0, 70), (0.95, 120), (0.85, 165)], 1.1, 1.1, "red"),
        box(11.34, 10.1, 7.3, 12.54, 10.8, 7.88, "steel", zrot(11.64, 10.45, 195)),  # split claw, from the hook's end
        box(11.34, 10.1, 8.12, 12.54, 10.8, 8.7, "steel", zrot(11.64, 10.45, 195)),
    ]
    m = mats(red={"kind": "paint", "color": "#8e2620", "chip": 0.32, "under_rust": 0.4, "blood": 0.2, "dirt": 0.2})
    return b, m


def baseball_bat(nails: bool = False):
    b = [
        *rod(0.6, 1.1, 1.75, "tape", sides=3),                              # knob
        *rod(1.1, 6.0, 1.05, "tape", sides=3, ends=False),                  # grip tape
        *rod(6.0, 6.8, 1.15, "wood", sides=3),
        *rod(6.8, 7.6, 1.3, "wood", sides=3),
        *rod(7.6, 8.4, 1.48, "wood", sides=3),
        *rod(8.4, 9.2, 1.66, "wood", sides=3),
        *rod(9.2, 10.0, 1.84, "wood", sides=3),
        *rod(10.0, 10.8, 2.0, "wood", sides=3),
        *rod(10.8, 14.6, 2.15, "wood", sides=3, blood=0.3 if nails else 0.12),
        *rod(14.6, 15.1, 1.85, "wood", sides=3),
    ]
    if nails:
        for i, (x, angle) in enumerate([(10.6, 0), (11.3, 140), (11.9, 260), (12.5, 40), (13.1, 175), (13.6, 300), (14.1, 95), (11.0, 215)]):
            b.append(box(x, 9.0, 7.87, x + 0.25, 10.3, 8.13, "nail", {"origin": [x, 8.0, 8.0], "axis": "x", "angle": angle}))
    m = mats(tape={"kind": "tape", "color": "#2b2b2d"}, wood=PALE_WOOD, nail=dict(STEEL, rust=0.35))
    return b, m


def fire_axe():
    b = [
        box(0.0, 7.2, 7.3, 0.5, 8.8, 8.7, "rubber_plain"),                 # butt knob
        bar(0.4, 3.6, 8.0, 8.0, 1.25, 1.15, "rubber"),                     # grip
        bar(3.6, 12.9, 8.0, 8.0, 1.2, 1.1, "handle"),
        box(10.9, 7.2, 7.2, 12.7, 8.8, 8.8, "red"),                        # head eye
        box(10.85, 6.0, 7.58, 12.75, 7.25, 8.42, "red"),                   # blade cheek
        box(10.5, 5.5, 7.66, 13.1, 6.2, 8.34, "red"),                      # flare
        box(10.35, 5.15, 7.8, 13.25, 5.55, 8.2, "edge"),                   # ground edge
        box(11.15, 8.8, 7.62, 12.45, 9.6, 8.38, "red"),                    # pick base
        box(11.45, 9.6, 7.7, 12.15, 10.5, 8.3, "red"),                     # pick
        box(11.62, 10.5, 7.8, 11.98, 11.0, 8.2, "steel"),                  # pick point
    ]
    m = mats(handle={"kind": "polymer", "color": "#a88a3a", "dirt": 0.45}, red={"kind": "paint", "color": "#a3241c", "chip": 0.22, "under_rust": 0.3, "dirt": 0.15},
             edge=dict(BLADE, base="#d0d5d8"))
    return b, m


def survival_knife():
    b = [
        box(2.0, 7.0, 7.25, 2.6, 9.0, 8.75, "steel_dark"),                 # pommel
        bar(2.6, 6.6, 8.0, 8.0, 1.7, 1.4, "rubber"),                       # ridged grip
        box(6.6, 6.3, 7.35, 7.1, 9.7, 8.65, "steel_dark"),                 # guard
        box(7.1, 7.0, 7.8, 12.3, 8.9, 8.2, "blade", bevel=0.75),
        box(12.1, 7.0, 7.82, 13.6, 8.6, 8.18, "blade", zrot(12.1, 7.0, 18), bevel=0.7),
        box(11.8, 8.0, 7.82, 13.8, 8.9, 8.18, "blade", zrot(11.8, 8.9, -24)),
        *[box(7.6 + i * 0.55, 8.9, 7.85, 7.9 + i * 0.55, 9.2, 8.15, "blade") for i in range(5)],  # spine saw
    ]
    m = mats(blade=dict(BLADE, base="#9ea4a8", dark="#5f6569"))
    return b, m


def hunting_knife():
    b = [
        box(1.3, 7.05, 7.3, 1.9, 8.95, 8.7, "brass"),                      # pommel
        bar(1.9, 6.4, 8.0, 8.0, 1.8, 1.3, "leather"),                      # stacked leather
        box(6.4, 6.6, 7.3, 6.85, 9.4, 8.7, "brass"),                       # guard
        box(6.85, 6.95, 7.8, 12.0, 8.95, 8.2, "blade", bevel=0.8),
        box(11.5, 6.95, 7.82, 13.4, 8.7, 8.18, "blade", zrot(11.5, 6.95, 12), bevel=0.75),
        box(12.8, 7.35, 7.84, 14.3, 8.35, 8.16, "blade", zrot(12.8, 7.35, 22)),
        box(12.0, 8.25, 7.84, 14.3, 8.95, 8.16, "blade", zrot(12.0, 8.95, -18)),
    ]
    m = mats(leather={"kind": "wood", "light": "#9a7650", "dark": "#4a3220", "rings": 9.0, "ribs": 2.2})
    return b, m


def lead_pipe():
    b = [
        *rod(0.6, 1.6, 1.55, "fitting"),                                    # couplings
        *rod(1.6, 5.0, 1.35, "tape", ends=False),                          # duct tape grip
        *rod(0.9, 12.9, 1.25, "pipe", ends=False),
        *rod(12.6, 13.6, 1.55, "fitting"),
        *rod(13.6, 14.3, 1.25, "pipe"),
        *rod_y(8.0, 10.2, 1.25, "pipe", cx=13.1),                          # a tee going up
        *rod_y(9.8, 10.6, 1.5, "fitting", cx=13.1),
    ]
    m = mats(pipe={"kind": "metal", "base": "#8c9188", "dark": "#565b55", "brush": 0.3, "rust": 0.38, "dirt": 0.3, "blood": 0.15},
             fitting={"kind": "metal", "base": "#6c706a", "dark": "#3e423d", "brush": 0.2, "rust": 0.5, "dirt": 0.3},
             tape={"kind": "tape", "color": "#8d9092", "dirt": 0.35})
    return b, m


def pipe_wrench():
    b = [
        box(0.24, 7.1, 7.35, 0.8, 8.9, 8.65, "red"),                       # end
        bar(0.8, 9.6, 8.0, 8.0, 1.5, 1.2, "red"),                          # handle
        box(9.0, 7.0, 7.2, 11.0, 9.0, 8.8, "red"),                         # housing
        box(11.0, 8.0, 7.3, 12.0, 9.6, 8.7, "steel", teeth=True),          # heel jaw
        box(9.8, 9.6, 7.35, 13.4, 10.4, 8.65, "steel"),                    # hook jaw shank
        box(12.6, 8.3, 7.35, 13.4, 9.6, 8.65, "steel"),                    # hook jaw
        *rod(9.9, 11.0, 1.45, "knurl", cy=10.0, sides=3),                  # adjusting nut
    ]
    m = mats(red={"kind": "paint", "color": "#9c2a22", "chip": 0.28, "under_rust": 0.45, "dirt": 0.25},
             knurl=dict(STEEL, knurl=True, base="#9a9fa3"))
    return b, m


# ----------------------------------------------------------------------------- firearms

def pistol():
    """Built facing +X like the sprite (X 2..15, Y 3.5..12)."""
    grip = zrot(6.6, 8.7, -14)
    b = [
        box(3.0, 9.4, 7.1, 14.8, 11.6, 8.9, "slide", serrations=(3.2, 5.4), port=(9.0, 11.6, 10.0, 11.3)),
        box(4.6, 8.4, 7.2, 13.6, 9.4, 8.8, "polymer"),                     # frame and dust cover
        box(14.8, 9.9, 7.45, 15.05, 11.1, 8.55, "bore"),                   # muzzle
        box(14.0, 11.6, 7.8, 14.5, 12.0, 8.2, "gun_black"),                # front sight
        box(3.3, 11.6, 7.4, 4.1, 12.0, 8.6, "gun_black"),                  # rear sight
        box(3.3, 3.5, 7.15, 6.7, 8.7, 8.85, "gun_black", grip),            # grip frame
        box(3.6, 4.1, 6.95, 6.4, 8.3, 9.05, "grip", grip),                 # grip panels
        box(3.25, 3.2, 7.05, 6.3, 3.55, 8.95, "gun_black", grip),          # magazine base
        box(6.6, 6.7, 7.6, 10.2, 7.1, 8.4, "gun_black"),                   # trigger guard
        box(9.8, 6.7, 7.6, 10.2, 8.4, 8.4, "gun_black"),
        box(7.6, 7.1, 7.8, 8.1, 8.4, 8.2, "steel_dark", zrot(7.85, 8.4, -10)),  # trigger
        box(2.6, 10.4, 7.7, 3.2, 11.8, 8.3, "gun_black", zrot(3.2, 10.4, 15)),  # hammer
    ]
    m = mats(slide=GUN_GRAY, bore={"kind": "metal", "base": "#18191b", "dark": "#0b0b0c", "brush": 0.0},
             grip=dict(WALNUT, checker=True))
    return b, m


def shotgun():
    b = [
        box(-0.8, 5.8, 7.2, -0.5, 9.3, 8.8, "rubber_plain"),               # recoil pad
        box(-0.5, 5.8, 7.25, 2.5, 9.3, 8.75, "wood"),                      # butt
        box(2.5, 6.5, 7.3, 5.0, 9.0, 8.7, "wood"),
        box(5.0, 7.0, 7.35, 6.6, 8.9, 8.65, "wood"),                       # wrist
        box(6.6, 7.3, 7.2, 10.2, 9.4, 8.8, "gun_black", port=(7.4, 9.4, 8.2, 9.1)),  # receiver
        box(6.9, 6.6, 7.65, 8.9, 7.3, 8.35, "gun_black"),                  # trigger guard
        box(7.6, 6.9, 7.85, 7.95, 7.6, 8.15, "steel_dark"),                # trigger
        *rod(10.2, 17.5, 0.9, "gun_black", cy=8.85),                       # barrel
        *rod(10.2, 16.6, 0.8, "gun_black", cy=7.85),                       # magazine tube
        box(11.2, 7.15, 7.25, 14.4, 8.65, 8.75, "forend"),                 # pump
        box(16.4, 7.4, 7.55, 16.8, 9.3, 8.45, "gun_black"),                # barrel band
        box(17.2, 9.3, 7.9, 17.4, 9.5, 8.1, "brass"),                      # bead
    ]
    m = mats(wood=STOCK_WOOD, forend=dict(STOCK_WOOD, ribs=2.4))
    return b, m


def hunting_rifle():
    b = [
        box(-0.8, 5.9, 7.2, -0.5, 9.2, 8.8, "rubber_plain"),               # pad
        box(-0.5, 5.9, 7.25, 2.6, 9.2, 8.75, "wood"),                      # butt
        box(1.0, 9.0, 7.3, 4.5, 9.5, 8.7, "wood"),                         # cheek riser
        box(2.6, 6.5, 7.3, 5.2, 9.0, 8.7, "wood"),
        box(5.2, 7.1, 7.35, 6.8, 8.9, 8.65, "wood", checker=True),         # wrist
        box(6.8, 7.6, 7.3, 10.4, 9.3, 8.7, "gun_black"),                   # receiver
        box(7.4, 8.0, 8.7, 7.8, 8.4, 9.8, "gun_black"),                    # bolt handle
        box(7.3, 7.9, 9.8, 7.9, 8.5, 10.4, "gun_black"),                   # bolt knob
        box(7.2, 6.8, 7.65, 9.0, 7.6, 8.35, "gun_black"),                  # trigger guard
        box(10.4, 7.5, 7.3, 13.6, 8.7, 8.7, "wood"),                       # forend
        *rod(10.4, 17.5, 0.7, "gun_black", cy=8.75),                       # barrel
        *rod(7.6, 12.6, 0.85, "gun_black", cy=10.4, ends=False),           # scope tube
        *rod(12.6, 13.6, 1.15, "gun_black", cy=10.4),                      # objective bell
        *rod(6.8, 7.6, 1.05, "gun_black", cy=10.4),                        # ocular bell
        box(13.62, 9.95, 7.55, 13.64, 10.85, 8.45, "glass", skip=("north", "south", "west", "up", "down")),
        box(6.76, 10.0, 7.6, 6.78, 10.8, 8.4, "glass", skip=("north", "south", "east", "up", "down")),
        box(8.3, 9.3, 7.7, 8.7, 10.0, 8.3, "gun_black"),                   # mounts
        box(11.2, 9.3, 7.7, 11.6, 10.0, 8.3, "gun_black"),
        box(9.8, 10.8, 7.7, 10.4, 11.3, 8.3, "gun_black"),                 # turret
    ]
    m = mats(wood=dict(WALNUT, light="#8a5634"), glass={"kind": "lens", "color": "#24404e", "hot": "#5f8aa0"})
    return b, m


# ----------------------------------------------------------------------------- the flashlight (along -Z, like its first model)

def flashlight():
    b = [
        *rod_z(0.0, 0.6, 3.3, "alu", sides=3, skip=("north",)),  # the bezel ring is open at the front: the lens sits recessed inside
        *rod_z(0.6, 2.6, 3.0, "alu", sides=3),
        *rod_z(2.6, 3.6, 2.4, "alu", sides=3),
        *rod_z(3.6, 11.6, 2.0, "knurl", sides=3),
        *rod_z(4.4, 4.8, 2.25, "alu", sides=3),
        *rod_z(10.4, 10.8, 2.25, "alu", sides=3),
        *rod_z(11.6, 12.8, 2.2, "alu", sides=3),
        *rod_z(12.8, 13.2, 1.2, "rubber_plain", sides=3),
        box(6.65, 6.65, 0.3, 9.35, 9.35, 0.6, "lens", skip=("south", "east", "west", "up", "down")),
        box(7.5, 9.0, 5.6, 8.5, 9.4, 6.8, "switch"),
        box(8.95, 7.2, 4.0, 9.2, 8.8, 9.0, "steel"),                       # pocket clip
    ]
    m = mats(alu={"kind": "metal", "base": "#3b3e42", "dark": "#1e2023", "brush": 0.5},
             knurl={"kind": "metal", "base": "#40444a", "dark": "#1c1e21", "brush": 0.2, "knurl": True},
             lens={"kind": "lens", "color": "#cfc39a", "hot": "#fbf3d2"},
             switch={"kind": "rubber", "color": "#7a2620"})
    return b, m


# ----------------------------------------------------------------------------- writing

GENERATED = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}

GENERATED_HANDS = {  # item/generated's own hand poses (left hands copy these, as Minecraft does)
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}

# name: (builder, turn in degrees, seed)
WEAPONS = {
    "machete": (machete, 45, 9101),
    "rusty_machete": (lambda: machete(rusty=True), 45, 9102),
    "crowbar": (crowbar, 45, 9103),
    "baseball_bat": (baseball_bat, 45, 9104),
    "nail_bat": (lambda: baseball_bat(nails=True), 45, 9105),
    "fire_axe": (fire_axe, 45, 9106),
    "survival_knife": (survival_knife, 45, 9107),
    "hunting_knife": (hunting_knife, 45, 9108),
    "lead_pipe": (lead_pipe, 45, 9109),
    "pipe_wrench": (pipe_wrench, 45, 9110),
    "pistol": (pistol, 0, 9111),
    "shotgun": (shotgun, 45, 9112),
    "hunting_rifle": (hunting_rifle, 45, 9113),
}


def display_for(name: str, turn: float) -> dict:
    """The item's existing display tuning (from its 2D model), completed the way Minecraft completes
    it, then turned so a model built along +X sits where the diagonal sprite sat."""
    current_path = N.asset(f"models/item/{name}.json")
    current = json.loads(current_path.read_text(encoding="utf-8"))
    disp = copy.deepcopy(GENERATED)
    if current.get("parent", "").endswith("handheld"):
        disp.update(copy.deepcopy(HANDHELD))
    elif current.get("parent", "").endswith("generated"):
        disp.update(copy.deepcopy(GENERATED_HANDS))
    for key, value in current.get("display", {}).items():
        disp[key] = copy.deepcopy(value)
    for side in ("thirdperson", "firstperson"):
        right, left = f"{side}_righthand", f"{side}_lefthand"
        if right in disp and left not in disp:
            disp[left] = copy.deepcopy(disp[right])  # Minecraft copies the raw right-hand values
    disp.pop("gui", None)  # the inventory shows the flat icon
    for key, t in disp.items():
        rot = list(t.get("rotation", [0, 0, 0]))
        rot[2] = round(rot[2] + (-turn if key.endswith("lefthand") else turn), 3)
        t["rotation"] = rot
    return disp


def write_model(name: str, model_name: str, boxes, materials, seed: int, display: dict) -> int:
    texture = f"item/model/{name}"
    elements, tex, size, _ = build_model(name, boxes, materials, seed, texture)
    N.save_png(N.asset(f"textures/{texture}.png"), tex[..., :3], tex[..., 3] / 255.0)
    D.asset_json(f"models/item/{model_name}.json", {
        "textures": {"skin": f"{NS}:{texture}", "particle": f"{NS}:item/{name}"},
        "elements": elements,
        "display": display,
    })
    return size


def build_all() -> None:
    for name, (builder, turn, seed) in WEAPONS.items():
        boxes, materials = builder()
        display = display_for(name, turn)
        size = write_model(name, f"{name}_3d", boxes, materials, seed, display)
        D.asset_json(f"items/{name}.json", {"model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}],
            "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}_3d"},
        }})
        print(f"  {name}: {len(boxes)} boxes, {size}px texture")
    # The flashlight keeps one model for every context (its 3D icon was already designed for the inventory).
    boxes, materials = flashlight()
    current = json.loads(N.asset("models/item/flashlight.json").read_text(encoding="utf-8"))
    size = write_model("flashlight", "flashlight", boxes, materials, 9120, current["display"])
    print(f"  flashlight: {len(boxes)} boxes, {size}px texture")


def build() -> None:  # build_all.py entry point
    build_all()


if __name__ == "__main__":
    build_all()
