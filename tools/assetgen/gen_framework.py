"""Phase 2 of the horror transformation: the data behind the foundation systems.

- Scares for the Horror Director (data/deadlands/jumpscare/), using the sounds import_sfx.py brought
  in: the night creature, dread beds, whispers, a creaking or slamming door, stingers.
- Mission givers (Harley: 10-15 minutes between offers, none after her death beat; her missions
  are written in Phase 5) and one development mission that checks the pipeline (never offered).
- A development dialogue and ending, so the loaders have something to read until Phase 5.
- The first DEADLANDS achievements (data/deadlands/advancement/).
- The six existing cutscenes, moved from Java into data (assets/deadlands/cutscene/).
- The Campaign mode's dimension, deadlands:campaign.
- Text for all of it.
"""
from __future__ import annotations

import dl_data as D

NS = D.NS


# ----------------------------------------------------------------------------- scares

def sound(at, name, where="everywhere", volume=1.0, pitch=1.0, distance=0.0):
    step = {"at": at, "type": "sound", "sound": f"{NS}:horror.{name}", "where": where, "volume": volume, "pitch": pitch}
    if distance:
        step["distance"] = distance
    return step


def step(at, kind, amount=0.0):
    return {"at": at, "type": kind, "amount": amount} if amount else {"at": at, "type": kind}


SCARES = {
    # Minor: something unsettling every 1 to 3 minutes at night.
    "night_creature_far": {"kind": "minor", "time": "night", "place": "outdoors", "weight": 14,
                           "steps": [sound(0, "night_creature", "far", 2.6, 0.95, 30)]},
    "night_creature_close": {"kind": "minor", "time": "night", "place": "outdoors", "dark": True, "weight": 9,
                             "steps": [step(0, "silence", 60), sound(20, "night_creature", "far", 1.6, 1.0, 14),
                                       sound(70, "night_creature", "far", 1.8, 0.9, 18)]},
    "whisper_at_ear": {"kind": "minor", "dark": True, "weight": 8,
                       "steps": [step(0, "silence", 70), sound(30, "whisper", "ear", 0.55)]},
    "door_creak_behind": {"kind": "minor", "place": "indoors", "weight": 9,
                          "steps": [sound(0, "door_creak", "behind", 1.0, 1.0, 6)]},
    "door_slams": {"kind": "minor", "place": "indoors", "weight": 6, "steps": [step(0, "slam_door")]},
    "distant_horn": {"kind": "minor", "time": "night", "place": "outdoors", "weight": 4,
                     "steps": [sound(0, "distant_horn", "far", 5.0, 0.9, 70)]},
    "dread_swell": {"kind": "minor", "time": "night", "weight": 8, "steps": [sound(0, "dread", volume=0.3)]},
    "creaking_timbers": {"kind": "minor", "time": "night", "place": "outdoors", "near_water": True, "weight": 10,
                         "steps": [sound(0, "water_creak", volume=0.35)]},
    "forest_sting": {"kind": "minor", "time": "night", "place": "outdoors", "weight": 4, "steps": [sound(0, "sting_forest", volume=0.35)]},
    "nothing_there": {"kind": "minor", "dark": True, "weight": 6,  # a fake-out: tension, then nothing
                      "steps": [sound(0, "suspense", volume=0.45), step(70, "flicker", 30)]},
    "deep_dread": {"kind": "minor", "place": "indoors", "dark": True, "weight": 7, "steps": [sound(0, "dread", volume=0.35, pitch=0.8)]},
    # Major: a real scare every 4 to 8 minutes at night, 10 to 15 by day. Anticipation, then a short, sharp payoff.
    "scream_behind": {"kind": "major", "dark": True, "weight": 10,
                      "steps": [step(0, "silence", 80), sound(30, "swell", volume=0.6), sound(80, "scream", "behind", 1.2, 1.0, 3),
                                sound(80, "stinger", volume=0.7), step(80, "jolt", 0.7)]},
    "beam_dies": {"kind": "major", "dark": True, "flashlight": True, "weight": 12,
                  "steps": [step(0, "flicker", 40), step(40, "beam_off", 70), sound(55, "whisper", "ear", 0.7),
                            sound(108, "stinger", volume=0.8), step(108, "jolt", 0.6)]},
    "something_rushes_past": {"kind": "major", "time": "night", "place": "outdoors", "weight": 10,
                              "steps": [step(0, "silence", 60), sound(60, "whoosh", volume=0.8), sound(64, "night_creature", "far", 1.4, 1.1, 5),
                                        step(64, "jolt", 0.4)]},
    "rising_dread": {"kind": "major", "place": "indoors", "dark": True, "weight": 9,
                     "steps": [step(0, "silence", 40), sound(20, "riser", volume=0.8), sound(150, "door_creak", "behind", 1.0, 0.9, 4),
                               step(150, "jolt", 0.6)]},
    "the_chase_begins": {"kind": "major", "time": "night", "place": "outdoors", "weight": 7,
                         "steps": [step(0, "silence", 70), sound(70, "jumpscare", volume=0.8), sound(75, "night_creature", "far", 1.6, 1.0, 8),
                                   step(75, "jolt", 0.8)]},
    "fright_in_the_dark": {"kind": "major", "place": "indoors", "dark": True, "weight": 8,
                           "steps": [step(0, "silence", 60), sound(60, "stinger", volume=0.8), step(60, "jolt", 0.5), step(60, "slam_door")]},
    "daylight_footfall": {"kind": "major", "time": "day", "place": "indoors", "weight": 10,
                          "steps": [sound(0, "suspense", volume=0.5), sound(100, "door_creak", "behind", 1.0, 1.0, 5),
                                    sound(120, "stinger", volume=0.7), step(120, "jolt", 0.5)]},
    "scream_far_off": {"kind": "major", "time": "day", "place": "outdoors", "weight": 10,
                       "steps": [step(0, "silence", 40), sound(40, "scream", "far", 2.5, 0.85, 30), sound(46, "swell", volume=0.5)]},
}


def scares() -> int:
    for name, scare in SCARES.items():
        D.data_json(f"jumpscare/{name}.json", scare)
    return len(SCARES)


# ----------------------------------------------------------------------------- missions, dialogue, endings

def missions() -> None:
    # Harley offers side missions 10 to 15 minutes of active play apart, never after her death beat.
    # Her missions themselves are Phase 5 (story); the list is empty until then.
    D.data_json("mission_giver/harley.json", {"cooldown_min_minutes": 10, "cooldown_max_minutes": 15, "stop_flag": "harley_dead", "missions": []})
    D.data_json("mission_giver/adam.json", {"cooldown_min_minutes": 12, "cooldown_max_minutes": 18, "missions": []})
    # Development only: started by /deadlands mission start deadlands:dev/pipeline_check, never offered to players.
    D.data_json("mission/dev/pipeline_check.json", {
        "title": "mission.deadlands.dev.pipeline_check",
        "description": "mission.deadlands.dev.pipeline_check.description",
        "objectives": [
            {"id": "flashlight", "type": "have_item", "text": "mission.deadlands.dev.pipeline_check.flashlight", "target": f"{NS}:flashlight", "count": 1},
            {"id": "walkers", "type": "kill", "text": "mission.deadlands.dev.pipeline_check.walkers", "target": f"{NS}:walker", "count": 2},
            {"id": "flag", "type": "flag", "text": "mission.deadlands.dev.pipeline_check.flag", "flag": "dev_pipeline_ok"},
        ],
        "reward_items": [{"item": f"{NS}:bandage", "count": 1}],
        "reward_flags": ["dev_pipeline_done"],
    })
    D.data_json("dialogue/dev/example.json", {"lines": [
        {"speaker": "harley", "text": "dialogue.deadlands.dev.example.0"},
        {"speaker": "harley", "text": "dialogue.deadlands.dev.example.1", "requires": ["dev_pipeline_done"], "pause": 30},
    ]})
    D.data_json("ending/dev/example.json", {"campaign": "dev", "title": "ending.deadlands.dev.example.title",
                                             "body": "ending.deadlands.dev.example.body", "requires": ["dev_pipeline_done"]})


# ----------------------------------------------------------------------------- achievements

INFECTED = ["walker", "runner", "brute", "screamer", "military", "crawler", "bloater", "stalker", "charred"]


def impossible():
    return {"done": {"trigger": "minecraft:impossible"}}


def visit(structure):
    return {"done": {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"location": {"structures": f"{NS}:{structure}"}}}]}}}


ACHIEVEMENTS = {
    # path: (icon, title, description, criteria, frame, hidden)
    "survival/first_light": ("flashlight", "First Light", "Carry a flashlight into the dark.",
                             {"done": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"{NS}:flashlight"}]}}}, "task", False),
    "survival/first_dawn": ("water_bottle", "First Dawn", "See the sun come up on your second day.", impossible(), "task", False),
    "survival/week_one": ("canned_beans", "Week One", "Live to see the eighth day.", impossible(), "goal", False),
    "exploration/town": ("survivor_journal", "Main Street", "Walk into a town.", visit("town"), "task", False),
    "exploration/school": ("research_documents", "School's Out", "Find the school.", visit("school"), "task", False),
    "exploration/bunker": ("key", "Below the Ground", "Find a bunker.", visit("bunker"), "task", False),
    "combat/first_blood": ("crowbar", "Not Anymore", "Put down one of the infected.",
                           {kind: {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
                               {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": f"{NS}:{kind}"}}]}}
                            for kind in INFECTED}, "task", False),
    "combat/horde_night": ("pistol_ammo", "Till Dawn", "Live through a horde night.", impossible(), "goal", False),
    "combat/boss": ("machete", "It Can Die", "Be there when something worse than the infected falls.", impossible(), "challenge", False),
    "lore/first_document": ("document", "Somebody Wrote This Down", "Read your first document.", impossible(), "task", False),
    "lore/archivist": ("research_documents", "Archivist", "Keep twenty-five documents in your archive.", impossible(), "goal", False),
    "horror/first_scare": ("flashlight", "Did You Hear That?", "Live through your first scare.", impossible(), "task", False),
    "horror/fifty_scares": ("flashlight", "Nerves of Steel", "Live through fifty scares.", impossible(), "challenge", False),
    "horror/dark_night": ("flashlight", "No Light", "Get through a whole night without switching your flashlight on.", impossible(), "challenge", False),
    "coop/full_house": ("survivor_journal", "Full House", "Survive with three others in the same world.", impossible(), "task", False),
}

CATEGORIES = {"story": "Story", "exploration": "Exploration", "survival": "Survival", "combat": "Combat", "lore": "Lore and Secrets",
              "endings": "Endings", "horror": "Horror", "coop": "Co-op", "general": "General"}


def achievements() -> dict[str, str]:
    lang = {}
    D.data_json("advancement/root.json", {
        "criteria": impossible(),
        "display": {"icon": {"id": f"{NS}:flashlight"}, "title": {"translate": "achievement.deadlands.root.title"},
                    "description": {"translate": "achievement.deadlands.root.description"},
                    "background": "minecraft:gui/advancements/backgrounds/stone",
                    "show_toast": False, "announce_to_chat": False, "hidden": False},
    })
    for path, (icon, title, description, criteria, frame, hidden) in ACHIEVEMENTS.items():
        key = "achievement.deadlands." + path.replace("/", ".")
        D.data_json(f"advancement/{path}.json", {
            "parent": f"{NS}:root",
            "criteria": criteria,
            "requirements": [list(criteria)],
            "display": {"icon": {"id": f"{NS}:{icon}"}, "title": {"translate": key + ".title"}, "description": {"translate": key + ".description"},
                        "frame": frame, "show_toast": True, "announce_to_chat": False, "hidden": hidden},
        })
        lang[key + ".title"] = title
        lang[key + ".description"] = description
    for category, name in CATEGORIES.items():
        lang[f"achievement.deadlands.category.{category}"] = name
    return lang


# ----------------------------------------------------------------------------- cutscenes (moved from Java)

CUTSCENES = {
    "opening": {"cue": f"{NS}:event.day_begin", "shots": [
        {"ticks": 110, "from": [14, 9, 12], "to": [-12, 6, 12], "look": [0, 1, 0]},
        {"ticks": 90, "from": [7, 2.5, 6], "to": [2.6, 1.9, 2.4], "look": [0, 1.5, 0]}],
        "lines": [{"from": 10, "to": 95, "key": "cutscene.deadlands.opening.0"}, {"from": 105, "to": 195, "key": "cutscene.deadlands.opening.1"}]},
    "boss_reveal": {"cue": f"{NS}:event.boss", "shots": [
        {"ticks": 70, "from": [0, 6, -18], "to": [0, 3, -9], "look": [0, 2, 0]},
        {"ticks": 60, "from": [4, 1.5, -6], "to": [-4, 1.5, -6], "look": [0, 2.4, 0]}],
        "lines": [{"from": 20, "to": 125, "key": "cutscene.deadlands.boss_reveal.0"}]},
    "research": {"cue": f"{NS}:event.town_enter", "shots": [{"ticks": 120, "from": [4, 2.5, 0], "to": [0, 2.0, 4], "look": [0, 1.4, 0]}],
                 "lines": [{"from": 10, "to": 115, "key": "cutscene.deadlands.research.0"}]},
    "cure": {"cue": f"{NS}:event.town_enter", "shots": [{"ticks": 120, "from": [4, 2.5, 0], "to": [0, 2.0, 4], "look": [0, 1.4, 0]}],
             "lines": [{"from": 10, "to": 115, "key": "cutscene.deadlands.cure.0"}]},
    # In the bearing frame +X points toward where the horde comes from (30 blocks out, 2 up).
    "horde": {"cue": f"{NS}:event.horde_start", "frame": "bearing", "shots": [
        {"ticks": 60, "from": [0, 3, 0], "to": [0, 7, 0], "look": [15, 1, 0]},
        {"ticks": 80, "from": [0, 7, 0], "to": [9, 5.6, 0], "look": [30, 2, 0]}],
        "lines": [{"from": 15, "to": 135, "key": "cutscene.deadlands.horde.0"}]},
    "extraction": {"cue": f"{NS}:helicopter.rotor", "shots": [
        {"ticks": 120, "from": [14, 3, 10], "to": [-14, 6, 10], "look": [0, 2, 0], "look_rise": 10.0},
        {"ticks": 100, "from": [-14, 6, 10], "to": [-6, 22, -10], "look": [0, 12, 0], "look_rise": 16.0}],
        "lines": [{"from": 20, "to": 110, "key": "cutscene.deadlands.extraction.0"}, {"from": 130, "to": 215, "key": "cutscene.deadlands.extraction.1"}]},
}


def cutscenes() -> int:
    for name, scene in CUTSCENES.items():
        D.asset_json(f"cutscene/{name}.json", scene)
    return len(CUTSCENES)


# ----------------------------------------------------------------------------- the campaign dimension

def campaign() -> None:
    D.data_json("dimension_type/campaign.json", {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark",
                                            "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/cloud_color": "#cc9a9a9a",
            "minecraft:visual/cloud_height": 192.33,
            "minecraft:visual/fog_color": "#8e9294",
            "minecraft:visual/sky_color": "#7d8790",
        },
        "coordinate_scale": 1.0, "has_ceiling": False, "has_skylight": True, "height": 384, "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384, "min_y": -64, "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
        "timelines": "#minecraft:in_overworld",
    })
    # Empty: chapter areas are placed into it as structure templates (Phase 5).
    D.data_json("dimension/campaign.json", {"type": f"{NS}:campaign", "generator": {"type": "minecraft:flat", "settings": {
        "biome": f"{NS}:dead_forest", "features": False, "lakes": False, "layers": [], "structure_overrides": []}}})


TEXT = {
    "config.deadlands.section.horror": "Horror",
    "config.deadlands.horror.scarePreset": "Scares",
    "config.deadlands.horror.photosensitivity": "Photosensitivity mode",
    "config.deadlands.horror.cameraShake": "Camera shake",
    "mission.deadlands.screen": "Missions",
    "mission.deadlands.tab.active": "Active",
    "mission.deadlands.tab.completed": "Completed",
    "mission.deadlands.tab.failed": "Failed",
    "mission.deadlands.none": "Nothing here yet.",
    "mission.deadlands.giver": "From %s",
    "mission.deadlands.giver.harley": "Harley",
    "mission.deadlands.giver.adam": "Adam",
    "mission.deadlands.lore": "Related",
    "mission.deadlands.received": "New mission: %s",
    "mission.deadlands.completed": "Mission complete: %s",
    "mission.deadlands.tracker.progress": "%s (%s/%s)",
    "mission.deadlands.tracker.done": "Return when you are ready.",
    "mission.deadlands.dev.pipeline_check": "Pipeline Check (development)",
    "mission.deadlands.dev.pipeline_check.description": "A development mission that exercises every objective type. It is never offered to players.",
    "mission.deadlands.dev.pipeline_check.flashlight": "Carry a flashlight",
    "mission.deadlands.dev.pipeline_check.walkers": "Put down walkers",
    "mission.deadlands.dev.pipeline_check.flag": "Set the flag dev_pipeline_ok",
    "dialogue.deadlands.dev.example.0": "(Development line: the dialogue loader works.)",
    "dialogue.deadlands.dev.example.1": "(Development line: shown after the pipeline check.)",
    "ending.deadlands.dev.example.title": "DEVELOPMENT ENDING",
    "ending.deadlands.dev.example.body": "The endings loader works.",
    "achievement.deadlands.screen": "Achievements",
    "achievement.deadlands.toast": "ACHIEVEMENT",
    "achievement.deadlands.count": "%s of %s",
    "achievement.deadlands.secret": "???",
    "achievement.deadlands.secret.description": "Keep going.",
    "achievement.deadlands.root.title": "DEADLANDS",
    "achievement.deadlands.root.description": "Survive what is left.",
    "command.deadlands.unknown": "Nothing called %s.",
    "command.deadlands.horror.play": "Playing scare %s.",
    "command.deadlands.flag.list": "%s story flags: %s",
    "command.deadlands.flag.set": "Set story flag %s.",
    "command.deadlands.flag.clear": "Cleared story flag %s.",
    "command.deadlands.mission.start": "Started mission %s.",
    "command.deadlands.mission.complete": "Completed mission %s.",
    "command.deadlands.mission.fail": "Failed mission %s.",
    "command.deadlands.mission.offer": "%s offers: %s",
    "command.deadlands.mission.refused": "Mission %s cannot do that now (unknown, already active or done, or its requirements are not met).",
    "command.deadlands.achievement": "Granted achievement %s.",
    "command.deadlands.campaign.status": "Campaign %s, chapter %s, checkpoint %s, dimension loaded: %s.",
    "command.deadlands.campaign.failed": "The campaign dimension is not available.",
    "command.deadlands.content": "Loaded: %s scares, %s missions, %s mission givers, %s dialogues, %s endings, %s DEADLANDS achievements; vanilla advancements left: %s.",
}


def build() -> int:
    count = scares()
    missions()
    lang = achievements()
    scenes = cutscenes()
    campaign()
    lang.update(TEXT)
    D.merge_lang(lang)
    print(f"framework: {count} scares, {len(ACHIEVEMENTS)} achievements, {scenes} cutscenes, mission givers, campaign dimension")
    return count


if __name__ == "__main__":
    build()
