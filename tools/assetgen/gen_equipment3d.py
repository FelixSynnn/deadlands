"""Phase 3 batch 2: 3D models for equipment (backpacks, medical supplies, radio, battery, keys and
keycards, food and drink, water, ammunition, fuel can, radio parts, fuse).

Same pipeline as the weapons (`gen_models3d`): box models with per-face painted textures, the flat
icon kept in the inventory, 3D everywhere else. Each object is built in the plane its icon is drawn
in (front facing south, +Z) and about the icon's size, so it keeps the icon's in-hand position (these
items use Minecraft's default `item/generated` pose). Diagonal icons (splint, syringe, fuse) are
built along +X and turned 45 degrees, like the weapons.
"""
from __future__ import annotations

import math

import dl_data as D
from dl_models3d import box, chain, rod, rod_y, rod_z, zrot
from gen_models3d import BRASS, GUN_BLACK, STEEL, display_for, mats, write_model

NS = "deadlands"


def paint(color, chip=0.1, rust=0.3, **kw):
    return dict({"kind": "paint", "color": color, "chip": chip, "under_rust": rust}, **kw)


def fabric(color, **kw):
    return dict({"kind": "fabric", "color": color}, **kw)


def plastic(color, **kw):
    return dict({"kind": "polymer", "color": color}, **kw)


def glass(color, hot):
    return {"kind": "lens", "color": color, "hot": hot}


ALU = {"kind": "metal", "base": "#c3c7ca", "dark": "#8a8f93", "brush": 0.7}
TIN = {"kind": "metal", "base": "#b2b6b8", "dark": "#7b8083", "brush": 0.5}
FOIL = {"kind": "metal", "base": "#d4d8da", "dark": "#9a9fa2", "brush": 0.2}
COPPER = {"kind": "brass", "base": "#c77a4a", "dark": "#7a4426", "brush": 0.4}
LEAD = {"kind": "metal", "base": "#8d9094", "dark": "#5a5d60", "brush": 0.2}


# ----------------------------------------------------------------------------- packs

def backpack(military: bool):
    body = "#4f5a3a" if military else "#6b5a3e"
    b = [
        box(4, 1.5, 5.5, 12, 12.2, 10.5, "canvas", seam=True),            # main bag
        box(4.3, 12.0, 5.8, 11.7, 13.4, 10.2, "canvas", seam=True),       # rolled top
        box(5, 3, 10.5, 11, 8.2, 12, "canvas", seam=True),                # front pocket
        box(4.9, 7.4, 10.4, 11.1, 8.6, 12.2, "flap"),                     # pocket flap
        box(5.0, 2.5, 4.8, 6.0, 12.0, 5.5, "strap"),                      # shoulder straps
        box(10.0, 2.5, 4.8, 11.0, 12.0, 5.5, "strap"),
        box(7.2, 13.4, 7.6, 8.8, 14.2, 8.4, "strap"),                     # carry loop
        box(7.6, 5.0, 12.0, 8.4, 6.0, 12.2, "buckle"),                    # buckle
    ]
    if military:
        b += [box(2.6, 3.0, 6.5, 4.0, 8.5, 9.5, "canvas", seam=True),     # side pouches
              box(12.0, 3.0, 6.5, 13.4, 8.5, 9.5, "canvas", seam=True),
              box(4.0, 9.6, 10.45, 12.0, 10.4, 10.6, "strap"),            # webbing
              box(4.0, 11.0, 10.45, 12.0, 11.8, 10.6, "strap")]
    m = mats(canvas=fabric(body, dirt=0.35), flap=fabric(body, dirt=0.45), strap=fabric("#2f2a24"),
             buckle={"kind": "polymer", "color": "#1c1c1c"})
    return b, m


# ----------------------------------------------------------------------------- medical

def medkit():
    b = [
        box(2.5, 4.0, 6.2, 13.5, 12.0, 9.8, "case"),
        box(2.3, 7.7, 6.0, 13.7, 8.3, 10.0, "seam"),                      # lid seam band
        box(7.0, 5.5, 9.8, 9.0, 10.5, 10.0, "cross"),                     # white cross, front
        box(5.5, 7.0, 9.8, 10.5, 9.0, 10.0, "cross"),
        box(7.0, 5.5, 6.0, 9.0, 10.5, 6.2, "cross"),                      # and back
        box(5.5, 7.0, 6.0, 10.5, 9.0, 6.2, "cross"),
        box(6.0, 12.0, 7.6, 6.6, 12.9, 8.4, "seam"),                      # handle posts and grip
        box(9.4, 12.0, 7.6, 10.0, 12.9, 8.4, "seam"),
        box(6.0, 12.9, 7.5, 10.0, 13.5, 8.5, "seam"),
        box(3.2, 7.4, 9.8, 4.0, 8.6, 10.2, "latch"),
        box(12.0, 7.4, 9.8, 12.8, 8.6, 10.2, "latch"),
    ]
    m = mats(case=paint("#a8231d", chip=0.12, dirt=0.25), seam=plastic("#5e1410"), cross=plastic("#ecebe6"), latch=STEEL)
    return b, m


def bandage():
    b = [
        *rod_z(6.0, 10.0, 7.0, "gauze", cx=7.0, cy=9.0, sides=6),
        *rod_z(5.9, 10.1, 2.2, "core", cx=7.0, cy=9.0, sides=6),          # the hole in the roll
        box(9.5, 5.2, 6.4, 14.0, 5.8, 9.6, "gauze", zrot(9.5, 5.5, -12)),  # loose tail
    ]
    m = mats(gauze=fabric("#e8e4d8", dirt=0.2), core=fabric("#8f8a7c"))
    return b, m


def splint():
    b = [
        box(0.5, 7.3, 7.0, 15.5, 8.7, 7.8, "wood"),                       # two slats
        box(0.5, 7.3, 8.2, 15.5, 8.7, 9.0, "wood"),
        *[box(x, 7.0, 6.7, x + 1.6, 9.0, 9.3, "wrap") for x in (2.5, 7.2, 11.9)],  # bandage wraps
    ]
    m = mats(wood={"kind": "wood", "light": "#cdb48a", "dark": "#8c7350", "rings": 16.0}, wrap=fabric("#e3ddcc", dirt=0.3))
    return b, m


def bottle_with_label(body, label, cap, d=5.0, top=10.0):
    return [
        *rod_y(2.0, top, d, "body", sides=4),
        *rod_y(4.0, 8.0, d + 0.12, "label", sides=4, skip=("up", "down")),
        *rod_y(top, top + 1.0, d - 1.0, "body", sides=4),                 # shoulder
        *rod_y(top + 1.0, top + 2.2, 2.0, "body", sides=4),               # neck
        *rod_y(top + 2.2, top + 3.4, 2.4, "cap", sides=4),
    ]


def antiseptic():
    b = bottle_with_label("body", "label", "cap")
    b += [box(7.3, 5.0, 10.6, 8.7, 7.0, 10.66, "mark"), box(7.0, 5.3, 10.6, 9.0, 6.7, 10.66, "mark")]   # red cross on the label
    m = mats(body=glass("#4a2414", "#8a4a2a"), label=paint("#e8e2d0", chip=0.05, dirt=0.2), cap=plastic("#e2dfd6"), mark=plastic("#a82018"))
    return b, m


def pill_bottle(body_colour, cap_colour, label_colour):
    b = [
        *rod_y(2.0, 10.4, 5.4, "body", sides=4),
        *rod_y(4.2, 8.4, 5.52, "label", sides=4, skip=("up", "down")),
        *rod_y(10.4, 12.6, 5.8, "cap", sides=4),
        box(5.6, 5.4, 10.7, 10.4, 5.7, 10.78, "ink"), box(5.6, 6.4, 10.7, 9.6, 6.7, 10.78, "ink"),   # printed lines
    ]
    m = mats(body=glass(body_colour, "#f2c27a") if body_colour.startswith("#c") else plastic(body_colour),
             label=paint(label_colour, chip=0.05), cap=plastic(cap_colour), ink=plastic("#3a3a3a"))
    return b, m


def syringe():
    b = [
        *rod(2.0, 11.0, 1.4, "barrel", sides=4),
        *rod(4.5, 10.6, 1.0, "dose", sides=4, ends=False),                # the liquid inside
        box(1.6, 6.6, 7.6, 2.0, 9.4, 8.4, "plastic"),                     # finger flange
        *rod(-0.5, 2.0, 0.5, "plastic", sides=2),                         # plunger rod
        box(-1.0, 7.1, 7.1, -0.5, 8.9, 8.9, "plastic"),                   # thumb pad
        *rod(11.0, 11.8, 0.7, "plastic", sides=2),                        # hub
        *rod(11.8, 14.8, 0.22, "needle", sides=2),
    ]
    m = mats(barrel=glass("#c9d6da", "#f2f8fa"), dose=glass("#2a8a8a", "#6fd0d0"), plastic=plastic("#e8e8e4"), needle=STEEL)
    return b, m


def dirty_rag():
    b = [
        box(3.0, 4.0, 7.4, 12.5, 10.5, 8.4, "cloth", zrot(7.7, 7.2, 8)),
        box(5.0, 7.5, 7.9, 13.5, 12.5, 8.9, "cloth", zrot(9.2, 10.0, -14)),
        box(2.0, 6.5, 8.2, 8.0, 11.0, 9.1, "cloth", zrot(5.0, 8.7, 22)),
        box(9.0, 3.0, 8.0, 14.0, 6.0, 8.7, "cloth", zrot(11.5, 4.5, -30)),
    ]
    m = mats(cloth=fabric("#9a8f7c", dirt=0.5, blood=0.35))
    return b, m


# ----------------------------------------------------------------------------- tools and gear

def radio():
    b = [
        box(2.0, 3.0, 6.0, 14.0, 13.0, 10.0, "case"),
        box(3.0, 9.6, 10.0, 13.0, 12.2, 10.15, "dial"),                   # tuning window
        box(3.0, 4.0, 10.0, 9.4, 9.0, 10.12, "grille"),
        *rod_z(10.0, 10.9, 2.2, "knob", cx=11.4, cy=6.4, sides=4),
        *rod_z(10.0, 10.6, 1.2, "knob", cx=11.4, cy=8.4, sides=4),
        box(12.3, 13.0, 7.4, 12.75, 18.0, 7.85, "antenna", zrot(12.5, 13.0, -18)),
        box(2.0, 2.2, 6.4, 3.5, 3.0, 9.6, "case"), box(12.5, 2.2, 6.4, 14.0, 3.0, 9.6, "case"),   # feet
    ]
    m = mats(case=plastic("#2c2a26", dirt=0.3), dial=glass("#8a6a1e", "#e0b44a"), grille={"kind": "rubber", "color": "#1d1c1a", "rings": 3.0},
             knob=STEEL, antenna=dict(STEEL, base="#c9ced1"))
    return b, m


def car_battery():
    b = [
        box(2.0, 3.0, 4.5, 14.0, 10.0, 11.5, "case"),
        box(1.8, 9.8, 4.3, 14.2, 11.2, 11.7, "lid"),
        *rod_y(11.2, 12.6, 1.5, "terminal", cx=5.0, cz=8.0, sides=4),
        *rod_y(11.2, 12.6, 1.5, "terminal", cx=11.0, cz=8.0, sides=4),
        box(6.5, 5.6, 11.5, 9.5, 6.2, 11.6, "mark"),                      # minus sign / label
    ]
    m = mats(case=plastic("#1f2534", dirt=0.3), lid=plastic("#a02a22", dirt=0.25), terminal=LEAD, mark=plastic("#e8e4d4"))
    return b, m


def key():
    ring = []
    for k in range(8):  # the bow: eight short bars round a circle
        a = math.radians(k * 45)
        cx, cy = 5.0 + 2.2 * math.cos(a), 10.5 + 2.2 * math.sin(a)
        ring.append(box(cx - 0.9, cy - 0.35, 7.75, cx + 0.9, cy + 0.35, 8.25, "brass", zrot(cx, cy, k * 45 + 90)))
    b = ring + [
        box(7.0, 10.1, 7.8, 14.4, 10.9, 8.2, "brass"),                    # blade
        box(11.0, 9.2, 7.8, 11.6, 10.1, 8.2, "brass"), box(12.4, 9.4, 7.8, 13.0, 10.1, 8.2, "brass"),  # bitting
        box(13.6, 9.0, 7.8, 14.2, 10.1, 8.2, "brass"),
        box(4.6, 6.0, 7.95, 5.0, 8.3, 8.05, "string"),                    # tag string
        box(2.8, 1.6, 7.85, 6.8, 6.0, 8.15, "tag"),
        box(3.4, 3.6, 8.15, 6.2, 3.9, 8.2, "ink"), box(3.4, 4.6, 8.15, 6.2, 4.9, 8.2, "ink"),
    ]
    m = mats(brass=dict(BRASS, rust=0.15), string=fabric("#bfb59a"), tag=paint("#e2d8b8", chip=0.0, dirt=0.3), ink=plastic("#3a3a3a"))
    return b, m


def keycard(stripe, base="#e9e6dc", dark_stripe=False):
    b = [
        box(1.8, 4.5, 7.85, 14.2, 12.0, 8.15, "card"),
        box(1.8, 10.6, 8.15, 14.2, 11.6, 8.2, "stripe"),                  # colour band
        box(2.6, 6.0, 8.15, 5.2, 9.0, 8.2, "photo"),                      # photo / chip
        box(6.2, 8.2, 8.15, 13.0, 8.5, 8.2, "ink"), box(6.2, 7.2, 8.15, 13.0, 7.5, 8.2, "ink"), box(6.2, 6.2, 8.15, 11.0, 6.5, 8.2, "ink"),
        box(1.8, 6.5, 7.8, 14.2, 7.4, 7.85, "mag"),                       # magnetic strip on the back
    ]
    m = mats(card=plastic(base, dirt=0.25), stripe=plastic(stripe), photo=plastic("#c8a24a"), ink=plastic("#2e2e2e"), mag=plastic("#151515"))
    return b, m


# ----------------------------------------------------------------------------- food and drink

def can(label, rusty=False):
    b = [
        *rod_y(2.0, 13.0, 7.4, "tin", sides=4),
        *rod_y(2.8, 12.2, 7.52, "label", sides=4, skip=("up", "down")),
        *rod_y(12.6, 13.3, 7.8, "rim", sides=4),
        *rod_y(1.7, 2.4, 7.8, "rim", sides=4),
    ]
    m = mats(tin=dict(TIN, rust=0.6 if rusty else 0.1), rim=dict(TIN, rust=0.7 if rusty else 0.15),
             label=paint(label, chip=0.35 if rusty else 0.08, rust=0.6, dirt=0.45 if rusty else 0.15))
    return b, m


def drink_can(colour, band):
    b = [
        *rod_y(2.0, 13.4, 5.0, "body", sides=4),
        *rod_y(6.5, 8.8, 5.1, "band", sides=4, skip=("up", "down")),
        *rod_y(13.4, 14.2, 4.2, "top", sides=4),
        *rod_y(1.6, 2.0, 4.4, "top", sides=4),
        box(7.4, 14.2, 7.0, 8.6, 14.4, 8.6, "top"),                       # pull tab
    ]
    m = mats(body=paint(colour, chip=0.06, rust=0.0), band=paint(band, chip=0.06, rust=0.0), top=ALU)
    return b, m


def jerky():
    b = [box(3.0, 3.0, 7.3, 13.0, 12.0, 8.7, "foil", zrot(8.0, 7.5, -28)),
         box(3.0, 11.2, 7.25, 13.0, 12.0, 8.75, "zip", zrot(8.0, 7.5, -28)),
         box(5.0, 5.0, 8.7, 10.0, 9.0, 8.75, "window", zrot(8.0, 7.5, -28))]
    m = mats(foil={"kind": "metal", "base": "#7a2a1e", "dark": "#4a160e", "brush": 0.1}, zip=plastic("#2a1a14"), window=plastic("#5a3020"))
    return b, m


def crackers():
    b = [box(2.0, 5.0, 6.0, 14.0, 11.0, 10.0, "card"),
         box(5.5, 4.9, 5.9, 8.5, 11.1, 10.1, "band")]
    m = mats(card=paint("#d9c69a", chip=0.15, rust=0.0, dirt=0.3), band=paint("#a6281e", chip=0.1, rust=0.0))
    return b, m


def energy_bar():
    b = [box(2.5, 6.0, 7.2, 13.5, 10.0, 8.8, "wrap"),
         box(6.0, 5.95, 7.15, 9.0, 10.05, 8.85, "band"),
         box(1.4, 6.3, 7.6, 2.5, 9.7, 8.4, "wrap"), box(13.5, 6.3, 7.6, 14.6, 9.7, 8.4, "wrap")]   # crimped ends
    m = mats(wrap=paint("#2a4a9a", chip=0.05, rust=0.0), band=paint("#e0b42a", chip=0.05, rust=0.0))
    return b, m


def mre():
    b = [box(2.0, 4.0, 7.0, 14.0, 12.0, 9.0, "pouch"),
         box(3.5, 6.0, 9.0, 12.5, 10.0, 9.06, "label")]
    m = mats(pouch=fabric("#6b6a4a", dirt=0.3), label=paint("#4c4b34", chip=0.0, rust=0.0))
    return b, m


def stale_bread():
    b = [box(2.0, 5.0, 5.5, 14.0, 10.0, 10.5, "crust"),
         box(2.6, 10.0, 6.0, 13.4, 11.2, 10.0, "crust"),
         *[box(x, 11.15, 6.4, x + 0.6, 11.3, 9.6, "score") for x in (4.5, 7.5, 10.5)]]
    m = mats(crust={"kind": "polymer", "color": "#9a6c3e", "stipple": True, "dirt": 0.45},   # baked crust, going stale
             score={"kind": "polymer", "color": "#c9a46c", "stipple": True})
    return b, m


def water_bottle(fill: str | None):
    level = 7.0 if fill else 1.5  # where the water stops and the clear plastic starts
    b = []
    if fill:
        b += rod_y(1.5, level, 5.0, "water", sides=4)
    b += [
        *rod_y(level, 11.0, 5.0, "air", sides=4),
        *rod_y(5.0, 8.0, 5.12, "label", sides=4, skip=("up", "down")),
        *rod_y(11.0, 12.0, 4.0, "air", sides=4),
        *rod_y(12.0, 13.0, 2.2, "air", sides=4),
        *rod_y(13.0, 14.3, 2.6, "cap", sides=4),
    ]
    liquid = {"water": glass("#3a78b0", "#8cc4ec"), "dirty": glass("#5a6a3a", "#8c9a5a")}
    m = mats(air=glass("#b8cdd6", "#eef6fa"), label=paint("#e6e6e0", chip=0.05, rust=0.0), cap=plastic("#2a5a9a"),
             water=liquid.get(fill, liquid["water"]))
    return b, m


def purification_tablet():
    b = [box(3.0, 5.0, 7.8, 13.0, 11.0, 8.2, "foil")]
    for row in (6.4, 9.0):
        for col in (4.2, 6.6, 9.0, 11.4):
            b.append(box(col - 0.8, row - 0.8, 8.2, col + 0.8, row + 0.8, 8.65, "blister"))
    m = mats(foil=FOIL, blister=plastic("#ecece6"))
    return b, m


# ----------------------------------------------------------------------------- ammunition, fuel, parts

def cartridges(kind: str):
    b = []
    for i, x in enumerate((4.5, 8.0, 11.5)):
        if kind == "pistol":
            b += [*rod_y(2.0, 9.6, 1.7, "case", cx=x, sides=2), *rod_y(9.6, 11.6, 1.5, "bullet", cx=x, sides=2),
                  *rod_y(11.6, 12.4, 0.9, "bullet", cx=x, sides=2)]
        elif kind == "shotgun":
            b += [*rod_y(2.0, 4.0, 2.5, "case", cx=x, sides=2), *rod_y(4.0, 11.0, 2.3, "hull", cx=x, sides=2),
                  *rod_y(11.0, 11.4, 2.0, "hull", cx=x, sides=2)]
        else:
            b += [*rod_y(1.0, 10.0, 1.6, "case", cx=x, sides=2), *rod_y(10.0, 11.0, 1.0, "case", cx=x, sides=2),
                  *rod_y(11.0, 13.4, 0.9, "bullet", cx=x, sides=2), *rod_y(13.4, 14.2, 0.5, "bullet", cx=x, sides=2)]
    m = mats(case=dict(BRASS, base="#d0a650"), bullet=COPPER if kind == "pistol" else LEAD, hull=plastic("#9c231c"))
    if kind == "rifle":
        m["bullet"] = COPPER
    return b, m


def fuel_can():
    b = [
        box(3.0, 2.0, 5.0, 13.0, 13.0, 11.0, "can"),
        box(4.0, 4.0, 11.0, 12.0, 4.6, 11.3, "emboss", zrot(8.0, 7.5, 45)),   # the X pressed into the side
        box(4.0, 4.0, 11.0, 12.0, 4.6, 11.3, "emboss", zrot(8.0, 7.5, -45)),
        box(4.6, 13.0, 7.4, 5.2, 14.6, 8.6, "can"), box(7.4, 13.0, 7.4, 8.0, 14.6, 8.6, "can"),   # handle
        box(4.6, 14.6, 7.4, 8.0, 15.2, 8.6, "can"),
        *chain(10.8, 13.0, [(1.6, 55), (1.8, 35)], 1.2, 1.2, "spout"),
    ]
    b[1] = box(3.7, 7.2, 11.0, 12.3, 7.8, 11.25, "emboss", zrot(8.0, 7.5, 45))
    b[2] = box(3.7, 7.2, 11.0, 12.3, 7.8, 11.25, "emboss", zrot(8.0, 7.5, -45))
    m = mats(can=paint("#a8281e", chip=0.2, rust=0.5, dirt=0.25), emboss=paint("#8a1e16", chip=0.2), spout=GUN_BLACK)
    return b, m


def radio_parts():
    b = [box(2.0, 4.0, 7.6, 14.0, 12.0, 8.4, "board"),
         box(3.0, 9.4, 8.4, 4.6, 11.0, 9.0, "chip"), box(5.2, 9.4, 8.4, 6.8, 11.0, 9.0, "chip"), box(7.4, 9.4, 8.4, 9.0, 11.0, 9.0, "chip"),
         box(9.6, 9.4, 8.4, 11.2, 11.0, 9.0, "chip"),
         *rod_z(8.4, 9.8, 1.8, "cap", cx=5.0, cy=6.4, sides=4),
         box(9.0, 5.0, 8.4, 12.6, 7.6, 8.9, "module"),
         *[box(2.6 + i * 1.2, 4.0, 8.4, 3.0 + i * 1.2, 4.6, 8.5, "pin") for i in range(9)]]
    m = mats(board=plastic("#2a6a3a"), chip=plastic("#1a1a1a"), cap=plastic("#c8a03a"), module=TIN, pin=BRASS)
    return b, m


def fuse():
    b = [*rod(2.5, 13.5, 1.7, "glass", sides=4),
         *rod(4.0, 12.0, 0.25, "wire", sides=1),
         *rod(1.5, 3.5, 2.0, "cap", sides=4), *rod(12.5, 14.5, 2.0, "cap", sides=4)]
    m = mats(glass=glass("#b8ccd2", "#eaf4f6"), wire=COPPER, cap=dict(BRASS, base="#c8a456"))
    return b, m


# name: (builder, turn, seed)
EQUIPMENT = {
    "backpack": (lambda: backpack(False), 0, 9301),
    "military_backpack": (lambda: backpack(True), 0, 9302),
    "medkit": (medkit, 0, 9303),
    "bandage": (bandage, 0, 9304),
    "splint": (splint, 45, 9305),
    "antiseptic": (antiseptic, 0, 9306),
    "antibiotics": (lambda: pill_bottle("#c86a1e", "#ecebe6", "#ecebe6"), 0, 9307),
    "painkillers": (lambda: pill_bottle("#ecebe6", "#2a5aa8", "#2a5aa8"), 0, 9308),
    "antiviral": (syringe, 45, 9309),
    "dirty_rag": (dirty_rag, 0, 9310),
    "radio": (radio, 0, 9311),
    "car_battery": (car_battery, 0, 9312),
    "key": (key, 0, 9313),
    "keycard_green": (lambda: keycard("#2e7a3a"), 0, 9314),
    "keycard_blue": (lambda: keycard("#2a52a0"), 0, 9315),
    "keycard_red": (lambda: keycard("#a8261e"), 0, 9316),
    "wardens_keycard": (lambda: keycard("#151515", base="#a8261e"), 0, 9317),
    "canned_beans": (lambda: can("#9a2a1e"), 0, 9318),
    "canned_soup": (lambda: can("#c8a02a"), 0, 9319),
    "canned_fruit": (lambda: can("#2a7aa0"), 0, 9320),
    "spoiled_can": (lambda: can("#4a6a2a", rusty=True), 0, 9321),
    "soda_can": (lambda: drink_can("#b0201a", "#ecebe6"), 0, 9322),
    "energy_drink": (lambda: drink_can("#1a1c1e", "#3ad04a"), 0, 9323),
    "jerky": (jerky, 0, 9324),
    "crackers": (crackers, 0, 9325),
    "energy_bar": (energy_bar, 0, 9326),
    "mre": (mre, 0, 9327),
    "stale_bread": (stale_bread, 0, 9328),
    "empty_bottle": (lambda: water_bottle(None), 0, 9329),
    "water_bottle": (lambda: water_bottle("water"), 0, 9330),
    "dirty_water_bottle": (lambda: water_bottle("dirty"), 0, 9331),
    "purification_tablet": (purification_tablet, 0, 9332),
    "pistol_ammo": (lambda: cartridges("pistol"), 0, 9333),
    "shotgun_shell": (lambda: cartridges("shotgun"), 0, 9334),
    "rifle_round": (lambda: cartridges("rifle"), 0, 9335),
    "fuel_can": (fuel_can, 0, 9336),
    "radio_parts": (radio_parts, 0, 9337),
    "fuse": (fuse, 45, 9338),
}


def build() -> None:
    for name, (builder, turn, seed) in EQUIPMENT.items():
        boxes, materials = builder()
        size = write_model(name, f"{name}_3d", boxes, materials, seed, display_for(name, turn))
        D.asset_json(f"items/{name}.json", {"model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}],
            "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}_3d"},
        }})
    print(f"  equipment: {len(EQUIPMENT)} models")


if __name__ == "__main__":
    build()
