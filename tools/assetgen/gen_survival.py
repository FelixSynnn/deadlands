"""Survival (Milestone 5, first part): icons for food, drink and medicine, the HUD sprites for the
body meters and conditions, the damage types, a few recipes, and the English text.

Item icons are 32x32 like the other items: flat shapes drawn with Pillow, a little noise for wear,
a highlight down one side, then the same 1 px dark outline. HUD sprites are 9x9, white-on-clear
pictograms the HUD tints.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

import dl_data as D
import dl_noise as N
from gen_items import _outline

NS = D.NS
S = 32


def paint(draw_fn, seed: int, wear: float = 0.12):
    """Draw with Pillow on a 32x32 RGBA canvas, then add wear, a side highlight and the outline."""
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im))
    a = np.asarray(im).astype(float)
    rgb, alpha = a[..., :3].copy(), a[..., 3] / 255.0
    noise = N.spectral(S, S, beta=1.6, seed=seed)
    rgb *= (1.0 - wear + 2.0 * wear * noise)[..., None]
    xx = np.mgrid[0:S, 0:S][1]
    solid = alpha > 0.5
    left = solid & ~np.roll(solid, 1, axis=1)       # lit edge
    right = solid & ~np.roll(solid, -1, axis=1)     # shaded edge
    rgb[left] = rgb[left] * 0.6 + 255 * 0.4
    rgb[right] *= 0.7
    rgb *= (1.0 - 0.12 * (xx / (S - 1)))[..., None]
    rgb = np.clip(rgb, 0, 255)
    alpha = (alpha > 0.5).astype(float)
    _outline(rgb, alpha)
    return rgb, alpha


def can(body: str, label: str, mark: str, dented: bool = False):
    def draw(d):
        d.rectangle([9, 7, 22, 26], fill=body)
        d.rectangle([9, 7, 22, 8], fill="#c8c8c0")
        d.rectangle([9, 25, 22, 26], fill="#8a8a84")
        d.rectangle([9, 11, 22, 21], fill=label)
        d.ellipse([12, 13, 19, 19], fill=mark)
        if dented:
            d.polygon([(9, 9), (13, 12), (9, 15)], fill="#3a3a36")
            d.rectangle([14, 5, 18, 7], fill="#6a7a3a")  # the lid has blown
    return draw


def bottle(liquid: str, cap: str, level: int = 11):
    def draw(d):
        d.rectangle([13, 4, 18, 6], fill=cap)
        d.rectangle([14, 7, 17, 9], fill="#c8d4d8")
        d.rounded_rectangle([10, 10, 21, 27], radius=2, fill="#c8d8dc")
        if liquid:
            d.rounded_rectangle([11, 10 + (17 - level), 20, 26], radius=1, fill=liquid)
        d.rectangle([10, 16, 21, 20], fill="#e8e4d4")
    return draw


def drink_can(body: str, band: str):
    def draw(d):
        d.rounded_rectangle([11, 6, 20, 26], radius=2, fill=body)
        d.rectangle([11, 6, 20, 7], fill="#d0d0c8")
        d.rectangle([11, 13, 20, 18], fill=band)
        d.rectangle([14, 5, 17, 5], fill="#909088")
    return draw


def packet(body: str, stripe: str, torn: bool = False):
    def draw(d):
        d.polygon([(6, 12), (25, 9), (26, 20), (7, 23)], fill=body)
        d.polygon([(11, 11), (16, 10), (17, 21), (12, 22)], fill=stripe)
        if torn:
            d.polygon([(22, 9), (26, 9), (26, 14)], fill=(0, 0, 0, 0))
    return draw


def box(body: str, front: str, cross: str | None = None):
    def draw(d):
        d.rectangle([6, 10, 25, 25], fill=body)
        d.rectangle([6, 10, 25, 12], fill=front)
        if cross:
            d.rectangle([14, 14, 17, 23], fill=cross)
            d.rectangle([11, 17, 20, 20], fill=cross)
        else:
            d.rectangle([9, 15, 22, 21], fill=front)
    return draw


def bread(d):
    d.rounded_rectangle([6, 12, 25, 24], radius=4, fill="#8a7448")
    d.rounded_rectangle([7, 12, 24, 16], radius=3, fill="#6e5a36")
    for x in (11, 16, 21):
        d.line([(x, 13), (x - 2, 16)], fill="#4e3e24")
    d.ellipse([18, 19, 21, 22], fill="#5a6a3a")  # mould


def jerky(d):
    d.polygon([(6, 20), (12, 9), (20, 8), (26, 14), (22, 24), (12, 25)], fill="#5a2a1e")
    d.line([(10, 20), (20, 11)], fill="#7a3a28", width=1)
    d.line([(13, 23), (23, 15)], fill="#3e1a12", width=1)


def roll(cloth: str, core: str, stain: str | None = None):
    def draw(d):
        d.ellipse([7, 9, 22, 24], fill=cloth)
        d.ellipse([12, 14, 17, 19], fill=core)
        d.polygon([(18, 22), (27, 20), (27, 25), (19, 26)], fill=cloth)
        if stain:
            d.ellipse([9, 17, 13, 22], fill=stain)
            d.ellipse([22, 21, 25, 24], fill=stain)
    return draw


def pill_bottle(body: str, cap: str, label: str):
    def draw(d):
        d.rectangle([11, 6, 20, 10], fill=cap)
        d.rounded_rectangle([10, 11, 21, 26], radius=2, fill=body)
        d.rectangle([10, 15, 21, 21], fill=label)
        d.line([(12, 17), (19, 17)], fill="#4a4a4a")
        d.line([(12, 19), (17, 19)], fill="#4a4a4a")
    return draw


def squeeze_bottle(d):
    d.polygon([(14, 4), (17, 4), (18, 9), (13, 9)], fill="#d8d0b8")
    d.rounded_rectangle([10, 9, 21, 26], radius=3, fill="#7a4a2a")
    d.rectangle([10, 14, 21, 21], fill="#e8e0c8")
    d.rectangle([14, 15, 17, 20], fill="#a02a20")
    d.rectangle([12, 16, 19, 18], fill="#a02a20")


def syringe(d):
    d.polygon([(6, 24), (8, 26), (11, 23), (9, 21)], fill="#b8b8b8")           # needle end
    d.polygon([(9, 21), (11, 23), (22, 12), (20, 10)], fill="#d8e4e8")         # barrel
    d.polygon([(12, 20), (13, 21), (19, 15), (18, 14)], fill="#3aa0a8")        # serum
    d.polygon([(20, 10), (22, 12), (24, 10), (22, 8)], fill="#909090")
    d.polygon([(22, 8), (24, 10), (27, 7), (25, 5)], fill="#b8b8b8")           # plunger


def splint(d):
    d.polygon([(7, 24), (10, 26), (25, 8), (22, 6)], fill="#9a7a4a")
    d.polygon([(11, 26), (14, 27), (27, 11), (25, 9)], fill="#7a5e36")
    for t in (0.25, 0.55, 0.8):
        x, y = 8 + 16 * t, 25 - 17 * t
        d.polygon([(x - 2, y - 2), (x + 5, y + 1), (x + 4, y + 4), (x - 3, y + 1)], fill="#d8d0b8")


def tablet(d):
    d.rounded_rectangle([7, 11, 24, 22], radius=2, fill="#c8ccd0")
    for x in (10, 15, 20):
        for y in (13, 18):
            d.ellipse([x - 1, y, x + 2, y + 3], fill="#f4f4f0")


def crowbar(d):
    d.line([(8, 25), (22, 9)], fill="#7a2a22", width=3)
    d.line([(22, 9), (26, 8)], fill="#7a2a22", width=3)
    d.line([(8, 25), (6, 22)], fill="#8a8a88", width=2)


def bat(d):
    d.polygon([(6, 26), (9, 27), (25, 9), (21, 5)], fill="#a8854e")
    d.polygon([(6, 26), (9, 27), (12, 23), (9, 22)], fill="#3a3a3a")
    d.line([(18, 12), (21, 15)], fill="#6a4a2a")


def machete(d):
    d.polygon([(11, 20), (25, 5), (27, 8), (14, 23)], fill="#b8bcc0")
    d.polygon([(6, 27), (11, 20), (14, 23), (9, 28)], fill="#3e2a1a")
    d.line([(12, 21), (26, 7)], fill="#e8ecf0")


def fire_axe(d):
    d.line([(8, 27), (22, 8)], fill="#b8943e", width=3)
    d.polygon([(17, 6), (26, 4), (27, 13), (22, 12), (20, 9)], fill="#a02a22")
    d.polygon([(25, 4), (27, 4), (28, 13), (27, 13)], fill="#d0d4d8")


def pistol(d):
    d.rectangle([6, 10, 25, 15], fill="#2e3034")
    d.rectangle([8, 11, 22, 12], fill="#4a4d52")
    d.polygon([(16, 15), (23, 15), (25, 26), (19, 26)], fill="#3a2e24")
    d.rectangle([13, 15, 16, 19], fill="#2e3034")
    d.rectangle([5, 11, 6, 13], fill="#101010")


def ammo(d):
    for x in (8, 14, 20):
        d.rectangle([x, 12, x + 3, 25], fill="#b8903a")
        d.polygon([(x, 12), (x + 3, 12), (x + 2, 7), (x + 1, 7)], fill="#b06a3a")
        d.rectangle([x, 24, x + 3, 25], fill="#8a6a2a")


def long_gun(stock: str, metal: str, double: bool):
    def draw(d):
        d.polygon([(3, 22), (5, 25), (12, 20), (10, 17)], fill=stock)          # butt
        d.polygon([(10, 17), (12, 20), (17, 16), (15, 13)], fill=stock)        # grip and receiver
        d.line([(15, 14), (28, 4)], fill=metal, width=2 if double else 1)      # barrel
        d.line([(16, 16), (22, 11)], fill=stock, width=2)                      # fore-end
        if not double:
            d.rectangle([17, 9, 21, 10], fill="#1a1a1a")                       # scope
    return draw


def shells(case: str, tip: str, tall: int):
    def draw(d):
        for x in (8, 14, 20):
            d.rectangle([x, 26 - tall, x + 3, 25], fill=case)
            d.rectangle([x, 23, x + 3, 25], fill="#b8903a")
            if tip:
                d.polygon([(x, 26 - tall), (x + 3, 26 - tall), (x + 2, 22 - tall), (x + 1, 22 - tall)], fill=tip)
    return draw


def pack(body: str, flap: str, strap: str):
    def draw(d):
        d.rounded_rectangle([8, 8, 23, 27], radius=3, fill=body)
        d.rounded_rectangle([8, 8, 23, 15], radius=3, fill=flap)
        d.rectangle([11, 18, 20, 24], fill=flap)
        d.line([(12, 8), (12, 4), (19, 4), (19, 8)], fill=strap, width=1)
        d.rectangle([15, 13, 16, 16], fill=strap)
    return draw


ITEMS = {
    # name: (painter, seed, English name, what it does)
    "canned_beans": (can("#a8a8a0", "#8a3a22", "#c8a060"), 5101, "Canned Beans", "A filling meal. Sealed, so it is safe."),
    "canned_soup": (can("#a8a8a0", "#b08a2a", "#d8c890"), 5102, "Canned Soup", "Food and a little water in one."),
    "canned_fruit": (can("#a8a8a0", "#4a7a8a", "#e0a040"), 5103, "Canned Fruit", "Light, sweet and wet: eases thirst too."),
    "spoiled_can": (can("#8a8a7a", "#5a5a3a", "#3a4a2a", dented=True), 5104, "Blown Can", "The lid has bulged. It will probably make you sick."),
    "jerky": (jerky, 5105, "Jerky", "Keeps for ever and fills you up. Salty: makes you thirsty."),
    "crackers": (packet("#c8b888", "#a8443a"), 5106, "Crackers", "Dry rations. A little food, a little thirst."),
    "energy_bar": (packet("#3a5a8a", "#e0b030", torn=True), 5107, "Energy Bar", "A little food and a burst of stamina."),
    "mre": (box("#6a6a4a", "#4a4a34"), 5108, "Field Ration", "A full military meal."),
    "stale_bread": (bread, 5109, "Stale Bread", "Old, hard and spotted. Usually fine. Sometimes not."),
    "empty_bottle": (bottle("", "#3a6a8a"), 5110, "Empty Bottle", "Use it on water to fill it. That water is not clean."),
    "water_bottle": (bottle("#5a9cc8", "#3a6a8a"), 5111, "Bottle of Water", "Clean water. Quenches a lot of thirst."),
    "dirty_water_bottle": (bottle("#7a7a4a", "#5a5a44"), 5112, "Bottle of Dirty Water", "It will quench your thirst. It may also make you sick."),
    "soda_can": (drink_can("#a02a24", "#e8e0d0"), 5113, "Soda", "Sugar and water: some thirst, a little stamina."),
    "energy_drink": (drink_can("#2a2a2e", "#58c048"), 5114, "Energy Drink", "Stamina now, and it pushes tiredness back a while."),
    "purification_tablet": (tablet, 5115, "Purification Tablets", "Craft with a bottle of dirty water to make it safe."),
    "bandage": (roll("#e8e4d8", "#b8b4a8"), 5116, "Bandage", "Stops bleeding and closes small wounds."),
    "dirty_rag": (roll("#8a7e68", "#5e5444", stain="#5a1a14"), 5117, "Dirty Rag", "Stops bleeding. Not clean: a small risk of infection."),
    "splint": (splint, 5118, "Splint", "Sets a fracture so you can walk properly again."),
    "antiseptic": (squeeze_bottle, 5119, "Antiseptic", "Cleans a fresh infection away. Slows an older one."),
    "antibiotics": (pill_bottle("#d87a2a", "#f0f0e8", "#f0f0e8"), 5120, "Antibiotics", "Cures sickness and pushes an infection back."),
    "painkillers": (pill_bottle("#e8e8e0", "#3a6aa8", "#3a6aa8"), 5121, "Painkillers", "For a few minutes a fracture does not slow you."),
    "antiviral": (syringe, 5122, "Antiviral Serum", "Rare. Beats back an infection that nothing else can."),
    "medkit": (box("#a02a24", "#7a1e1a", cross="#f0f0e8"), 5123, "First Aid Kit", "Stops bleeding, sets fractures and heals a lot."),
    "crowbar": (crowbar, 5201, "Crowbar", "Quick and sturdy."),
    "baseball_bat": (bat, 5202, "Baseball Bat", "The fastest swing. Not the hardest hit."),
    "machete": (machete, 5203, "Machete", "A good edge and a fair reach."),
    "fire_axe": (fire_axe, 5204, "Fire Axe", "Slow, heavy, and it ends things."),
    "pistol": (pistol, 5205, "Pistol", "Holds 12 rounds. Use it empty, or sneak and use, to reload. Everything nearby will hear it."),
    "pistol_ammo": (ammo, 5206, "9 mm Rounds", "For the pistol."),
    "shotgun": (long_gun("#5a3a22", "#2e3034", True), 5209, "Shotgun", "Five shells, seven spreading pellets each. Deadly close, useless far."),
    "hunting_rifle": (long_gun("#6a4a2a", "#3a3d42", False), 5210, "Hunting Rifle", "Four heavy rounds that carry a long way. Slow to fire again."),
    "shotgun_shell": (shells("#a02a24", "", 12), 5211, "Shotgun Shells", "For the shotgun."),
    "rifle_round": (shells("#b8903a", "#b06a3a", 13), 5212, "Rifle Rounds", "For the hunting rifle."),
    "backpack": (pack("#6a5a3e", "#54462e", "#2e2618"), 5207, "Backpack", "Use it in your hand to open it: 18 slots. Carry 15 kg more while you have it."),
    "military_backpack": (pack("#4a5236", "#3a4028", "#1e2214"), 5208, "Military Backpack", "Use it in your hand to open it: 27 slots. Carry 25 kg more while you have it."),
}

# ----------------------------------------------------------------------------- HUD pictograms (9x9)

PICTOGRAMS = {
    "thirst": ["....#....", "....#....", "...###...", "..#####..", "..#####..", ".#######.", ".#######.", "..#####..", "...###..."],
    "stamina": ["....##...", "...##....", "..##.....", ".######..", "...##....", "..##.....", ".##......", "##.......", "........."],
    "temperature": ["...###...", "...#.#...", "...#.#...", "...###...", "...###...", "..#####..", "..#####..", "..#####..", "...###..."],
    "fatigue": [".........", "..####...", ".##......", "##.......", "##.......", "##....#..", ".##..##..", "..####...", "........."],
    "thirsty": ["....#....", "...#.#...", "..#...#..", "..#...#..", ".#.....#.", ".#.....#.", ".#.....#.", "..#...#..", "...###..."],
    "dehydrated": ["#...#...#", ".#..#..#.", "...###...", "..#.#.#..", "..#.#.#..", ".#..#..#.", ".#.....#.", "..#...#..", "...###..."],
    "cold": ["....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."],
    "freezing": ["#...#...#", ".#.###.#.", "..#.#.#..", ".#.###.#.", "#########", ".#.###.#.", "..#.#.#..", ".#.###.#.", "#...#...#"],
    "hot": ["....#....", ".#.....#.", "...###...", "..#####..", "#.#####.#", "..#####..", "...###...", ".#.....#.", "....#...."],
    "overheating": ["#...#...#", ".#.....#.", "..#####..", "..#####..", "#.#####.#", "..#####..", "..#####..", ".#.....#.", "#...#...#"],
    "tired": [".........", ".........", ".###.###.", "#...#...#", ".........", "..#####..", ".........", ".........", "........."],
    "exhausted": ["####.....", "..#..###.", ".#....#..", "####.###.", ".........", "#########", ".#######.", ".........", "........."],
    "bleeding": ["....#....", "....#....", "...###...", "...###...", "..#####..", "..#####..", "..#####..", "...###...", "........."],
    "heavy_bleeding": ["..#...#..", "..#...#..", ".###.###.", ".###.###.", "#########", "#########", ".###.###.", ".........", "#.#.#.#.#"],
    "fracture": ["##.......", "###......", ".###.....", "..##..#..", "...#.##..", "....##...", ".....###.", "......###", ".......##"],
    "sick": ["..#####..", ".#.....#.", "#.#...#.#", "#.......#", "#..###..#", "#.#...#.#", ".#.....#.", "..#####..", "........."],
    "infected": ["#...#...#", ".#.###.#.", "..#####..", ".##.#.##.", "#########", ".##.#.##.", "..#####..", ".#.###.#.", "#...#...#"],
    "painkillers": [".........", "..####...", ".######..", ".######..", "..####...", "...####..", "..######.", "..######.", "...####.."],
    "winded": [".........", ".####....", "#....#...", ".....#.##", "######...", ".........", "#####....", ".....#...", "#####...."],
    "health": [".........", ".##...##.", "#########", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."],
    "armour": ["#########", "#########", "#########", "#########", ".#######.", ".#######.", "..#####..", "...###...", "....#...."],
    "hunger": ["..##.....", ".####....", ".####....", "..##.#...", "...###...", "....###..", ".....##..", "......##.", ".......#."],
    "load": ["...###...", "..#...#..", "..#...#..", ".#######.", ".#######.", ".#######.", ".#######.", ".#######.", "..#####.."],
    "encumbered": ["...###...", "..#...#..", ".#######.", ".#######.", ".#######.", ".#######.", "#########", "..#...#..", ".##...##."],
}
PICTOGRAM_COLOURS = {
    "thirst": "#8cc4ec", "stamina": "#f0c774", "temperature": "#e3dccb", "fatigue": "#b8b4d8",
    "thirsty": "#8cc4ec", "dehydrated": "#c0443a", "cold": "#9cc8ec", "freezing": "#6aa8e8", "hot": "#f0a050", "overheating": "#e04a2a",
    "tired": "#b8b4d8", "exhausted": "#c0443a", "bleeding": "#c0443a", "heavy_bleeding": "#e02a20", "fracture": "#e3dccb", "sick": "#9ab04a",
    "infected": "#7ac04a", "painkillers": "#e3dccb", "winded": "#f0c774", "load": "#e3dccb", "encumbered": "#f0a050", "health": "#d0584a", "armour": "#b8c0c8", "hunger": "#d0a060",
}

DAMAGE = {
    # name: (exhaustion, death message, death message with a killer)
    "dehydration": (0.0, "%1$s died of thirst", "%1$s died of thirst while fighting %2$s"),
    "hypothermia": (0.0, "%1$s froze to death", "%1$s froze to death while fighting %2$s"),
    "heatstroke": (0.0, "%1$s collapsed in the heat", "%1$s collapsed in the heat while fighting %2$s"),
    "bleeding": (0.0, "%1$s bled out", "%1$s bled out while fighting %2$s"),
    "infection": (0.0, "%1$s turned", "%1$s turned while fighting %2$s"),
}

CONDITIONS = {
    "thirsty": ("Thirsty", "Drink something."),
    "dehydrated": ("Dehydrated", "Drink now: it is hurting you."),
    "cold": ("Cold", "Find shelter or a fire."),
    "freezing": ("Freezing", "Get to a fire, get dry, wear more."),
    "hot": ("Hot", "Find shade. You are drinking more."),
    "overheating": ("Overheating", "Get out of the heat and drink."),
    "tired": ("Tired", "Sleep when you can."),
    "exhausted": ("Exhausted", "Sleep. You are slow and weak."),
    "bleeding": ("Bleeding", "Bandage it. It may stop on its own."),
    "heavy_bleeding": ("Bleeding badly", "Bandage it now."),
    "fracture": ("Fracture", "Use a splint. Painkillers help for a while."),
    "sick": ("Sick", "Antibiotics, or wait it out."),
    "infected": ("Infected", "Antiseptic early, antibiotics, antiviral serum."),
    "painkillers": ("Painkillers", "The pain is dulled for now."),
    "winded": ("Winded", "Stop running and catch your breath."),
    "encumbered": ("Overloaded", "Drop something. You are slow and tire fast."),
}

TEXT = {
    "message.deadlands.survival.bleeding": "You are bleeding.",
    "message.deadlands.survival.infected": "The bite burns. You are infected.",
    "message.deadlands.survival.fracture": "Something cracked. You have a fracture.",
    "message.deadlands.survival.not_needed": "You do not need that right now.",
    "config.deadlands.ui.survivalHud": "Body meters and injuries",
    "itemGroup.deadlands.food_and_drink": "DEADLANDS: Food and Drink",
    "itemGroup.deadlands.medical": "DEADLANDS: Medical",
    "journal.deadlands.log.body": "Body",
    "config.deadlands.survival.weight": "Carrying weight",
    "container.deadlands.workbench": "Workbench",
    "journal.deadlands.log.skills": "Skills",
    "itemGroup.deadlands.survival_gear": "DEADLANDS: Survival Gear",
    "itemGroup.deadlands.weapons": "DEADLANDS: Weapons",
    "itemGroup.deadlands.ammunition": "DEADLANDS: Ammunition",
    "skill.deadlands.survivalist": "Survivalist",
    "skill.deadlands.medic": "Medic",
    "skill.deadlands.gunslinger": "Gunslinger",
    "skill.deadlands.engineer": "Engineer",
    "skill.deadlands.scavenger": "Scavenger",
    "message.deadlands.skill.level_up": "%s is now level %s.",
    "command.deadlands.skills.line": "%s: level %s (%s / %s)",
    "subtitles.deadlands.weapon.pistol_shot": "Gunshot",
    "subtitles.deadlands.weapon.dry_fire": "Empty click",
    "subtitles.deadlands.weapon.shotgun_shot": "Shotgun blast",
    "subtitles.deadlands.weapon.rifle_shot": "Rifle shot",
    "message.deadlands.weapon.jammed": "It jammed.",
    "subtitles.deadlands.weapon.reload": "Weapon reloaded",
    "item.deadlands.loaded": "Loaded: %s / %s",
    "item.deadlands.condition": "Condition: %s",
    "item.deadlands.condition.good": "Good",
    "item.deadlands.condition.worn": "Worn",
    "item.deadlands.condition.failing": "Failing",
    "config.deadlands.ui.vitalsHud": "DEADLANDS health and hunger bars",
    "config.deadlands.ui.inventoryScreens": "DEADLANDS inventory and crafting screens",
    "inventory.deadlands.title": "Inventory",
    "inventory.deadlands.crafting": "Field crafting",
    "inventory.deadlands.body": "Body",
    "inventory.deadlands.unaffected": "Unaffected in this mode.",
    "inventory.deadlands.thirst": "Water",
    "inventory.deadlands.stamina": "Stamina",
    "inventory.deadlands.rest": "Rest",
    "inventory.deadlands.load": "Load",
}


def pictogram(rows: list[str], colour: str):
    rgb = np.zeros((9, 9, 3))
    alpha = np.zeros((9, 9))
    c = N.hex_rgb(colour)
    for y, row in enumerate(rows):
        assert len(row) == 9, row
        for x, ch in enumerate(row):
            if ch == "#":
                rgb[y, x] = c
                alpha[y, x] = 1.0
    return rgb, alpha


def weapon_sounds() -> None:
    """A pistol shot (crack, body, tail) and the click of an empty gun; merged into sounds.json."""
    import json

    from gen_audio import OUT, SR, write_ogg
    r = np.random.default_rng(5301)
    t = np.arange(int(SR * 0.9)) / SR
    crack = r.standard_normal(t.size) * np.exp(-t * 90.0)
    body = np.sin(2 * np.pi * (140.0 * np.exp(-t * 9.0)) * t) * np.exp(-t * 14.0)
    tail = r.standard_normal(t.size) * np.exp(-t * 6.0) * 0.18
    tail = np.convolve(tail, np.ones(24) / 24.0, mode="same")
    shot = np.tanh((crack * 1.2 + body * 0.9 + tail) * 2.2)
    shot = shot / np.max(np.abs(shot)) * 0.89
    t2 = np.arange(int(SR * 0.16)) / SR
    click = r.standard_normal(t2.size) * np.exp(-t2 * 160.0) * 0.6 + np.sin(2 * np.pi * 2400 * t2) * np.exp(-t2 * 120.0) * 0.4
    click += np.roll(click, int(SR * 0.05)) * 0.5
    click = click / np.max(np.abs(click)) * 0.6
    path = OUT.parent / "sounds.json"
    table = json.loads(path.read_text(encoding="utf-8"))
    def gunshot(seconds, crack_decay, low, low_decay, tail_level, seed):
        rr = np.random.default_rng(seed)
        tt = np.arange(int(SR * seconds)) / SR
        c = rr.standard_normal(tt.size) * np.exp(-tt * crack_decay)
        bdy = np.sin(2 * np.pi * (low * np.exp(-tt * 7.0)) * tt) * np.exp(-tt * low_decay)
        tl = np.convolve(rr.standard_normal(tt.size) * np.exp(-tt * 4.5) * tail_level, np.ones(32) / 32.0, mode="same")
        out = np.tanh((c * 1.2 + bdy + tl) * 2.4)
        return out / np.max(np.abs(out)) * 0.89

    # Reload: magazine out, magazine in, slide. Three metallic clacks.
    t3 = np.arange(int(SR * 0.9)) / SR
    reload = np.zeros(t3.size)
    r3 = np.random.default_rng(5304)
    for at, freq, level in ((0.05, 1800.0, 0.6), (0.42, 1400.0, 0.8), (0.68, 2600.0, 1.0)):
        k = np.clip(t3 - at, 0.0, None)
        on = (t3 >= at).astype(float)
        reload += on * level * (r3.standard_normal(t3.size) * np.exp(-k * 110.0) * 0.6 + np.sin(2 * np.pi * freq * k) * np.exp(-k * 70.0) * 0.5)
    reload = reload / np.max(np.abs(reload)) * 0.7
    shotgun = gunshot(1.3, 45.0, 90.0, 9.0, 0.3, 5302)     # a fat boom with a long tail
    rifle = gunshot(1.5, 130.0, 180.0, 16.0, 0.24, 5303)   # a sharp crack that rolls away
    for event, rel, data in (("weapon.pistol_shot", "weapon/pistol_shot", shot), ("weapon.dry_fire", "weapon/dry_fire", click),
                             ("weapon.shotgun_shot", "weapon/shotgun_shot", shotgun), ("weapon.rifle_shot", "weapon/rifle_shot", rifle),
                             ("weapon.reload", "weapon/reload", reload)):
        write_ogg(rel, data, quality=4)
        table[event] = {"sounds": [f"{NS}:{rel}"], "subtitle": f"subtitles.{NS}.{event}"}
    path.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


def recipes() -> None:
    D.data_json("recipe/water_bottle_from_tablet.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [f"{NS}:dirty_water_bottle", f"{NS}:purification_tablet"],
        "result": {"id": f"{NS}:water_bottle", "count": 1}})
    D.data_json("recipe/water_bottle_boiled.json", {
        "type": "minecraft:campfire_cooking", "category": "food", "ingredient": f"{NS}:dirty_water_bottle",
        "result": {"id": f"{NS}:water_bottle"}, "experience": 0.0, "cookingtime": 300})
    D.data_json("recipe/water_bottle_smelted.json", {
        "type": "minecraft:smelting", "category": "food", "ingredient": f"{NS}:dirty_water_bottle",
        "result": {"id": f"{NS}:water_bottle"}, "experience": 0.0, "cookingtime": 200})
    D.data_json("recipe/dirty_rag.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ["#minecraft:wool"],
        "result": {"id": f"{NS}:dirty_rag", "count": 2}})
    D.data_json("recipe/bandage.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [f"{NS}:dirty_rag", f"{NS}:antiseptic"],
        "result": {"id": f"{NS}:bandage", "count": 2}})
    def shaped(name, pattern, key, result, count=1):
        D.data_json(f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
                                            "result": {"id": f"{NS}:{result}", "count": count}})
    shaped("baseball_bat", ["P", "P", "S"], {"P": "#minecraft:planks", "S": "minecraft:stick"}, "baseball_bat")
    shaped("machete", ["I", "I", "S"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, "machete")
    shaped("crowbar", ["II", "I ", "I "], {"I": "minecraft:iron_ingot"}, "crowbar")
    shaped("fire_axe", ["II", "IS", " S"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, "fire_axe")
    shaped("pistol_ammo", ["C", "G", "N"], {"C": "minecraft:copper_ingot", "G": "minecraft:gunpowder", "N": "minecraft:iron_nugget"}, "pistol_ammo", 4)
    shaped("backpack", ["LSL", "L L", "LLL"], {"L": "minecraft:leather", "S": "minecraft:string"}, "backpack")
    shaped("shotgun_shell", ["P", "G", "C"], {"P": "minecraft:paper", "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"}, "shotgun_shell", 2)
    shaped("rifle_round", ["I", "G", "C"], {"I": "minecraft:iron_nugget", "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"}, "rifle_round", 2)
    shaped("workbench", ["PP", "LL"], {"P": "#minecraft:planks", "L": "#minecraft:logs"}, "workbench")
    D.data_json("recipe/splint.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["SRS"],
        "key": {"S": "minecraft:stick", "R": f"{NS}:dirty_rag"}, "result": {"id": f"{NS}:splint", "count": 1}})


def build() -> int:
    lang = dict(TEXT)
    for name, (painter, seed, english, desc) in ITEMS.items():
        N.save_png(N.asset(f"textures/item/{name}.png"), *paint(painter, seed))
        D.asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        D.item_model_definition(name, f"{NS}:item/{name}")
        lang[f"item.{NS}.{name}"] = english
        lang[f"item.{NS}.{name}.desc"] = desc
    for name, rows in PICTOGRAMS.items():
        N.save_png(N.asset(f"textures/gui/sprites/survival/{name}.png"), *pictogram(rows, PICTOGRAM_COLOURS[name]))
    for name, (exhaustion, message, with_killer) in DAMAGE.items():
        D.data_json(f"damage_type/{name}.json", {"message_id": f"{NS}.{name}", "exhaustion": exhaustion, "scaling": "never"})
        lang[f"death.attack.{NS}.{name}"] = message
        lang[f"death.attack.{NS}.{name}.player"] = with_killer
    types = [f"{NS}:{name}" for name in DAMAGE]
    for tag in ("bypasses_armor", "bypasses_shield", "no_knockback", "bypasses_enchantments"):
        D.data_json(f"tags/damage_type/{tag}.json", {"replace": False, "values": types}, ns="minecraft")
    for cid, (name, hint) in CONDITIONS.items():
        lang[f"condition.{NS}.{cid}"] = name
        lang[f"condition.{NS}.{cid}.hint"] = hint
    recipes()
    weapon_sounds()
    D.merge_lang(lang)
    return len(ITEMS)


if __name__ == "__main__":
    print(build(), "survival items written")
