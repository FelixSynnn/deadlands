"""Phase 4 (gameplay): new settings' text and the jumpscares the project owner picked (2026-11-12).

Jumpscares (data/deadlands/jumpscare/, read by the Horror Director; format in horror/Scare.java):
  ceiling_crawl      the tubes die one by one toward you; a whisper overhead; look up: the stalker on
                     the ceiling, chanting, drops at you.
  caught_in_the_beam a still figure far out in the beam, closer at each flicker, gone, laughter behind.
  emergency_strobe   the emergency light strobes; in each dark frame the stalker is closer; in your face.
  still_water        heavy rain at night: footsteps splash round you, stop behind you, then a growl.
  closed_inventory   (event) a menu kept open indoors at night: on closing, the stalker in the doorway.
  breath_on_glass    at a window at night: breathing on the other side, a face at the glass, a thud.
  feeding_turn       (event) the stinger when a feeding infected turns on you (entity/ai/FeedGoal).
  dark_building      (event) the stinger when a dark building lights up (horror/DarkBuildings).
"""
from __future__ import annotations

import numpy as np

import dl_data as D
import dl_noise as N

TEXT = {
    "config.deadlands.horror.firstPersonMotion": "First-person motion",
    "subtitles.deadlands.horror.breath": "Breathing behind the glass",
    "subtitles.deadlands.horror.stalker_chant": "Something chants",
    "subtitles.deadlands.horror.stalker_laugh": "Something laughs",
    "subtitles.deadlands.infected.feeding": "Infected feeds",
    "subtitles.deadlands.infected.feeding_screech": "Infected screeches",
    "config.deadlands.horror.shaderCompatibility": "Shader compatibility (darkness and flashlight)",
    "message.deadlands.story.harley_camp": "Harley's camp",
    "message.deadlands.story.harley_moved": "The ground here wasn't safe. Harley set up camp at %s, %s, %s.",
    "command.deadlands.lights": "Faulty light fixtures have flickered %s times since the server started; %s.",
}


def sound(at, event, where="everywhere", volume=1.0, distance=0.0, pitch=1.0):
    return {"at": at, "type": "sound", "sound": event, "where": where, "volume": volume, "pitch": pitch, "distance": distance}


def step(at, kind, amount=0.0, where=None, distance=None):
    out = {"at": at, "type": kind, "amount": amount}
    if where:
        out["where"] = where
    if distance is not None:
        out["distance"] = distance
    return out


SCARES = {
    "ceiling_crawl": {"kind": "major", "time": "night", "place": "indoors", "requires": "fixtures", "weight": 14, "steps": [
        step(0, "silence", 260), step(0, "fixtures_out", 50),
        step(55, "apparition", 600, "ceiling"),
        sound(60, "deadlands:horror.whisper", "above", 0.8, 2.5),
        step(62, "wait_look", 220),
        sound(64, "deadlands:horror.stalker_chant", "everywhere", 1.0), step(64, "jolt", 0.8),
        step(66, "apparition", 0, "drop"),
    ]},
    "caught_in_the_beam": {"kind": "major", "time": "night", "place": "outdoors", "flashlight": True, "requires": "open_ground", "weight": 12, "steps": [
        step(0, "silence", 140),
        step(0, "apparition", 130, "beam", 18.0),
        step(30, "flicker", 12), step(42, "apparition", 90, "beam", 12.0),
        step(70, "flicker", 12), step(82, "apparition", 40, "beam", 6.0),
        step(100, "beam_off", 18), step(104, "apparition_hide"),
        sound(112, "deadlands:horror.stalker_laugh", "behind", 0.9, 3.0), step(112, "jolt", 0.5),
    ]},
    "emergency_strobe": {"kind": "major", "place": "indoors", "requires": "emergency_light", "weight": 12, "steps": [
        step(0, "silence", 120), step(0, "emergency_strobe", 60),
        step(10, "blink", 4), step(11, "apparition", 80, "ahead", 7.0),
        step(22, "blink", 4), step(23, "apparition", 80, "ahead", 5.0),
        step(34, "blink", 4), step(35, "apparition", 80, "ahead", 3.0),
        step(46, "blink", 4), step(47, "apparition", 80, "ahead", 1.4),
        sound(50, "deadlands:horror.stalker_chant", "everywhere", 1.0), step(50, "jolt", 0.9),
        step(56, "apparition_hide"),
    ]},
    "still_water": {"kind": "major", "time": "night", "place": "outdoors", "requires": "heavy_rain", "weight": 14, "steps": [
        step(0, "silence", 200),
        *[sound(30 + 12 * i, "minecraft:block.mud.step", "around", 1.0, 4.0) for i in range(6)],
        sound(104, "minecraft:block.mud.step", "behind", 1.0, 1.5),
        sound(164, "deadlands:infected.walker.ambient", "ear", 1.0), step(164, "jolt", 0.7),
    ]},
    "closed_inventory": {"kind": "major", "trigger": "event", "place": "indoors", "steps": [
        step(0, "silence", 100),
        step(0, "apparition", 120, "doorway"),
        step(45, "flicker", 14), step(55, "apparition_hide"),
    ]},
    "breath_on_glass": {"kind": "major", "time": "night", "requires": "window", "weight": 12, "steps": [
        step(0, "silence", 120),
        sound(15, "deadlands:horror.breath", "ahead", 0.8, 2.0), sound(45, "deadlands:horror.breath", "ahead", 0.9, 2.0),
        step(70, "apparition", 14, "window"),
        sound(84, "minecraft:entity.zombie.attack_wooden_door", "ahead", 0.9, 2.0), step(84, "jolt", 0.6),
    ]},
    "feeding_turn": {"kind": "major", "trigger": "event", "steps": [
        sound(0, "deadlands:horror.stinger", "everywhere", 0.7), step(0, "jolt", 0.5),
    ]},
    "dark_building": {"kind": "major", "trigger": "event", "steps": [
        sound(0, "deadlands:horror.stinger", "everywhere", 1.0), step(0, "jolt", 0.8),
    ]},
}


def flashlight_support() -> None:
    """The beam's mask for client/light/ShaderDarkness (clear in the middle, dark at the edges), and the
    flashlight as a light source for Lamb's Dynamic Lights 4.x (only while switched on)."""
    s = 256
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    r = np.sqrt((xx - s / 2 + 0.5) ** 2 + (yy - s / 2 + 0.5) ** 2) / (s / 2)
    alpha = N.smoothstep(0.16, 0.42, r)  # the hot spot, then a soft falloff to full dark
    N.save_png(N.asset("textures/gui/flashlight_mask.png"), np.zeros((s, s, 3)), alpha)
    D.asset_json("dynamiclights/item/flashlight.json", {
        "match": {"items": "deadlands:flashlight", "components": {"deadlands:flashlight_on": True}},
        "luminance": {"type": "value", "value": 15},
        "water_sensitive": False,
    })


# ----------------------------------------------------------------------------- achievements (Phase 4)
# 25 more, for about 41 in all. Vanilla triggers where they fit (a biome reached, an infected kind killed,
# something drunk or used, something carried); the rest are awarded by code (Achievements.award):
# story/met_harley (NpcDialogue), exploration/lights_on (PowerGrid), the horror ones when that
# jumpscare plays (HorrorDirector.SCARE_ACHIEVEMENTS). Horror ones are hidden: the list shows "???".

BIOMES = ["dead_forest", "overgrown_suburbs", "abandoned_farmland", "rural_countryside", "burned_forest", "dead_pine_valley",
          "dry_wasteland", "flooded_marsh", "frozen_outskirts", "industrial_wasteland"]


def biome(name):
    return {"trigger": "minecraft:location", "conditions": {"player": [{"condition": "minecraft:entity_properties", "entity": "this",
            "predicate": {"location": {"biomes": f"deadlands:{name}"}}}]}}


def killed(kind):
    return {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [{"condition": "minecraft:entity_properties",
            "entity": "this", "predicate": {"type": f"deadlands:{kind}"}}]}}


def consumed(item):
    return {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": f"deadlands:{item}"}}}


def carried(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"deadlands:{item}"}]}}


CODE = {"trigger": "minecraft:impossible"}

# path: (icon, title, description, criteria, hidden, frame)
ACHIEVEMENTS = {
    "exploration/dead_forest": ("dead_bush", "Where Nothing Grows", "Walk into a dead forest.", {"done": biome("dead_forest")}, False, "task"),
    "exploration/flooded_marsh": ("water_bottle", "Sinking Feeling", "Wade into the flooded marsh.", {"done": biome("flooded_marsh")}, False, "task"),
    "exploration/burned_forest": ("charcoal", "Ash and Embers", "Find what the fires left of a forest.", {"done": biome("burned_forest")}, False, "task"),
    "exploration/frozen_outskirts": ("snowball", "Cold Comfort", "Reach the frozen outskirts.", {"done": biome("frozen_outskirts")}, False, "task"),
    "exploration/long_way_home": ("compass", "The Long Way Home", "Set foot in every kind of land the outbreak left.",
                                  {b: biome(b) for b in BIOMES}, False, "challenge"),
    "exploration/lights_on": ("fluorescent_light", "Lights On", "Restore a town's power.", {"done": CODE}, False, "goal"),
    "combat/runner_down": ("survival_knife", "Faster Than You", "Put down a runner.", {"done": killed("runner")}, False, "task"),
    "combat/brute_down": ("fire_axe", "The Bigger They Are", "Put down a brute.", {"done": killed("brute")}, False, "goal"),
    "combat/screamer_down": ("crowbar", "Silence", "Put down a screamer before it calls the rest.", {"done": killed("screamer")}, False, "task"),
    "combat/bloater_down": ("lead_pipe", "Keep Your Distance", "Put down a bloater.", {"done": killed("bloater")}, False, "task"),
    "combat/stalker_down": ("hunting_rifle", "Caught It", "Put down a stalker.", {"done": killed("stalker")}, False, "goal"),
    "combat/arsenal": ("pistol", "Arsenal", "Carry a pistol, a shotgun and a hunting rifle.",
                       {"pistol": carried("pistol"), "shotgun": carried("shotgun"), "rifle": carried("hunting_rifle")}, False, "goal"),
    "survival/clean_water": ("water_bottle", "Clean Water", "Drink clean water.", {"done": consumed("water_bottle")}, False, "task"),
    "survival/bad_water": ("dirty_water_bottle", "You'll Regret That", "Drink water you shouldn't have.", {"done": consumed("dirty_water_bottle")}, False, "task"),
    "survival/field_medic": ("medkit", "Field Medic", "Patch yourself up with a medkit.", {"done": consumed("medkit")}, False, "task"),
    "survival/packed": ("military_backpack", "Packed for the Long Haul", "Find a military backpack.", {"done": carried("military_backpack")}, False, "task"),
    "story/met_harley": ("radio", "Harley", "Find Harley's camp and talk to her.", {"done": CODE}, False, "task"),
    "horror/looked_up": ("fluorescent_light", "Don't Look Up", "Look up.", {"done": CODE}, True, "task"),
    "horror/in_the_beam": ("flashlight", "Caught in the Beam", "See what the flashlight found.", {"done": CODE}, True, "task"),
    "horror/red_light": ("emergency_light", "Red Light", "Stand under an emergency light when it changes.", {"done": CODE}, True, "task"),
    "horror/still_water": ("water_bottle", "Still Water", "Hear something walk round you in the rain.", {"done": CODE}, True, "task"),
    "horror/glass": ("glass_pane", "Breath on the Glass", "Stand at a window at night.", {"done": CODE}, True, "task"),
    "horror/doorway": ("backpack", "Don't Close It", "Close your pack in the dark.", {"done": CODE}, True, "task"),
    "horror/dark_building": ("emergency_light", "The Dark Building", "Walk into the one building that stayed dark.", {"done": CODE}, True, "goal"),
    "horror/interrupted": ("bandage", "Interrupted", "Disturb something while it eats.", {"done": CODE}, True, "task"),
}
VANILLA_ICONS = {"dead_bush", "charcoal", "snowball", "compass", "glass_pane"}


def achievements() -> dict[str, str]:
    text = {}
    for path, (icon, title, description, criteria, hidden, frame) in ACHIEVEMENTS.items():
        key = "achievement.deadlands." + path.replace("/", ".")
        D.data_json(f"advancement/{path}.json", {
            "parent": "deadlands:root",
            "criteria": criteria,
            "requirements": [list(criteria)] if len(criteria) == 1 else [[c] for c in criteria],
            "display": {
                "icon": {"id": ("minecraft:" if icon in VANILLA_ICONS else "deadlands:") + icon},
                "title": {"translate": key + ".title"},
                "description": {"translate": key + ".description"},
                "frame": frame, "show_toast": True, "announce_to_chat": False, "hidden": hidden,
            },
        })
        text[key + ".title"] = title
        text[key + ".description"] = description
    text["achievement.deadlands.category.story"] = "Story"
    return text


def build() -> None:
    D.merge_lang(TEXT)
    D.merge_lang(achievements())
    flashlight_support()
    for name, scare in SCARES.items():
        D.data_json(f"jumpscare/{name}.json", scare)
    print(f"  gameplay: {len(TEXT)} text entries, {len(SCARES)} jumpscares")


if __name__ == "__main__":
    build()
