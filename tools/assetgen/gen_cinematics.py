"""Phase 5 cinematics and events: the intro cutscene "Ninety-Five Days" (docs/INTRO_CUTSCENE_SCRIPT.md),
the day 10 reunion, and the disasters' text, settings names and sound events.

Writes assets/deadlands/cutscene/outbreak_intro.json (camera shots, subtitles, actors), its subtitle text,
and the sound event music.intro (for now an alias of an existing track; the Pixabay score replaces it).

Story Canon (2026-11-12): FelixSyn and Harley are a couple, apart since the outbreak. FelixSyn,
Itzbreadloaf and Adam are fellow survivors. The intro ends with FelixSyn not knowing where Harley is.

Coordinates are blocks from the anchor (the survivor's spawn), +Z the way they face. Actors stand on
the ground at their x/z (y is ignored for them).
"""
from __future__ import annotations

import json

import dl_data as D
import dl_noise as N

NS = "deadlands"
SEC = 20

LINES = [
    "Ninety-five days ago, something got out of the Kessler Regional Research Laboratory.",
    "They called it NR-7. It was built to heal wounded soldiers.",
    "It healed. It just never stopped. Ashford County fell in a month.",
    "At Camp Meridian, the army closed the gates on two hundred people.",
    "One soldier put down his rifle and walked out. His name is FelixSyn.",
    "Itzbreadloaf kept an old van running and went back for anyone he could find.",
    "Adam ran a team at the lab. He knows what NR-7 was before they changed it. He is still looking for his family.",
    "Three survivors. One road.",
    "Somewhere out there is Harley.",
    "FelixSyn hasn't heard from her since the night it started. He doesn't know where she is. He hasn't stopped looking.",
]
# Seconds each line is on screen (start, end).
TIMES = [(1, 6), (6, 11), (11, 17), (17, 22), (22, 28), (28, 36), (36, 44), (44, 50), (50, 55), (55, 59.5)]


def shot(seconds, start, end, look, rise=0.0):
    out = {"ticks": int(seconds * SEC), "from": start, "to": end, "look": look}
    if rise:
        out["look_rise"] = rise
    return out


def actor(who, start, end, at, to=None, face=None, item=None, move=None):
    out = {"who": who, "start": int(start * SEC), "end": int(end * SEC), "from": at, "to": to or at}
    if face:
        out["face"] = face
    if item:
        out["item"] = item
    if move:
        out["move_start"], out["move_end"] = int(move[0] * SEC), int(move[1] * SEC)
    return out


def intro() -> dict:
    shots = [
        shot(7, [0, 22, -6], [10, 12, -16], [0, 0, 6]),                       # 1 crane over the land
        shot(8, [-8, 1.5, 8], [8, 1.5, 8], [0, 1.2, 12]),                     # 2 low dolly past the infected
        shot(7, [6, 3, 20], [3, 2.2, 15], [0, 1.6, 12]),                      # 3 push in on FelixSyn, from behind
        shot(6, [-3, 1.7, 13], [0, 1.65, 9], [0, 1.6, 12]),                   # 4 half orbit to his front
        shot(8, [-10, 2, 2], [-5, 2, 3], [-3, 1.5, 6]),                       # 5 track Itzbreadloaf
        shot(8, [6, 2, 2], [5, 1.9, 3], [3, 1.5, 5]),                         # 6 Adam with his notebook
        shot(6, [0, 4, -6], [0, 3, -2], [0, 1.2, 6]),                         # 7 the three together
        shot(10, [0.6, 2.4, 4.2], [0.6, 6, 2], [0, 1.8, 30], rise=8.0),       # 8 over FelixSyn's shoulder to the horizon
    ]
    actors = [
        actor("walker", 7, 15, [-3, 0, 12], [1, 0, 14]),
        actor("walker", 7, 15, [2, 0, 11], [4, 0, 15]),
        actor("walker", 7, 15, [5, 0, 13], [3, 0, 17]),
        actor("felixsyn", 15, 28, [0, 0, 12], face=[0, 0, 30]),
        actor("itzbreadloaf", 28, 36, [-6, 0, 4], [-2, 0, 6]),
        actor("adam", 36, 44, [3, 0, 5], face=[2, 0, 4], item=f"{NS}:adam_field_notebook"),
        actor("felixsyn", 44, 60, [0, 0, 6], face=[0, 0, 30]),
        actor("itzbreadloaf", 44, 60, [-1.6, 0, 5], face=[0, 0, 30]),
        actor("adam", 44, 60, [1.7, 0, 5.2], face=[0, 0, 30], item=f"{NS}:adam_field_notebook"),
    ]
    lines = [{"from": int(a * SEC), "to": int(b * SEC), "key": f"cutscene.{NS}.outbreak_intro.{i}"} for i, (a, b) in enumerate(TIMES)]
    return {"cue": f"{NS}:music.intro", "ambience": f"{NS}:ambient.intro", "hide_hud": True, "title": f"cutscene.{NS}.outbreak_intro.title",
            "shots": shots, "lines": lines, "actors": actors}


REUNION_LINES = [
    "FelixSyn: Harley?",
    "Harley: ...Felix?",
    "Harley: Ten days. I told myself you'd find a way. I didn't let myself believe it.",
    "FelixSyn: I walked out of Meridian with nothing. I asked every survivor I met about a doctor named Harley.",
    "Harley: You found me.",
    "FelixSyn: I'm not losing you again. Not to this.",
    "Harley: This is who kept me going out here. Felix, meet the reason I'm still standing.",
]
REUNION_TIMES = [(3, 6.5), (6.5, 9), (9.5, 14.5), (14.5, 20), (20.5, 23), (23, 27), (27.5, 33.5)]


def reunion() -> dict:
    """Day 10: Harley and FelixSyn walk in from either side and meet in front of the survivor (the anchor)."""
    shots = [
        shot(6, [0, 2.4, -2], [0, 2.0, 0], [0, 1.4, 10]),                     # 1 over the survivor: two figures far off
        shot(6, [-6, 1.8, 6], [-4, 1.7, 7], [0, 1.6, 6]),                     # 2 side on, they close in
        shot(9, [2.4, 1.7, 3], [2.0, 1.65, 3.6], [-0.4, 1.6, 6]),             # 3 over FelixSyn... onto Harley
        shot(8, [-2.4, 1.7, 9], [-2.0, 1.65, 8.4], [0.4, 1.6, 6]),            # 4 over Harley onto FelixSyn
        shot(6, [0, 1.8, 1.5], [0, 2.4, 0.5], [0, 1.5, 6]),                   # 5 both of them, turning to the survivor
    ]
    actors = [
        actor("harley", 0, 35, [6, 0, 14], [0.6, 0, 6], face=[-0.6, 0, 6], move=(0, 11)),
        actor("felixsyn", 0, 35, [-7, 0, 15], [-0.6, 0, 6], face=[0.6, 0, 6], move=(0, 11)),
    ]
    lines = [{"from": int(a * SEC), "to": int(b * SEC), "key": f"cutscene.{NS}.reunion.{i}"} for i, (a, b) in enumerate(REUNION_TIMES)]
    return {"hide_hud": True, "shots": shots, "lines": lines, "actors": actors}


# Disasters (Phase 5; world.disaster.Disaster). Original events, inspired by disaster-survival games in
# spirit only: names, text and sounds are DEADLANDS' own.
DISASTERS = {
    "twister": ("Twister", "The air has gone still and the clouds are turning. A twister is forming. Find a basement or solid walls.",
                "A twister is on the ground!", "ambient/gust1", "ambient/dry_wind_loop"),
    "flash_flood": ("Flash Flood", "The river is roaring upstream. A flash flood is coming. Get to high ground.",
                    "Flash flood! Stay off low ground!", "ambient/gust2", "ambient/marsh_loop"),
    "tremor": ("Tremor", "Birds are leaving the trees all at once. The ground hums under your feet.",
               "Tremor! Get away from anything that can fall!", "event/horde_distant1", "event/horde_distant2"),
    "skyfall": ("Skyfall", "Streaks of light are crossing the sky. Something is coming down.",
                "Skyfall! Burning rock is hitting the ground!", "event/boss_sting", "ambient/ember1"),
    "whiteout": ("Whiteout", "The temperature is dropping fast. A whiteout is rolling in. Get indoors and get warm.",
                 "Whiteout! You can't see your own hands.", "ambient/gust1", "ambient/frozen_loop"),
    "dust_wall": ("Dust Wall", "A brown wall is rising on the horizon. A dust storm is coming. Cover your face.",
                  "The dust wall hits!", "ambient/gust2", "ambient/dry_wind_loop"),
    "caustic_rain": ("Caustic Rain", "The clouds have a sick green tinge. The rain that's coming burns. Get under cover or wear a helmet.",
                     "Caustic rain! It burns your skin!", "ambient/gust2", "ambient/burned_loop"),
    "static_storm": ("Static Storm", "Your hair is standing on end. A lightning storm is building right on top of you.",
                     "Static storm! Stay away from open ground!", "event/horde_distant1", "ambient/gust1"),
}

CONFIG_TEXT = {
    "config.deadlands.section.disasters": "Disasters",
    "config.deadlands.disasters.enabled": "Disasters",
    "config.deadlands.disasters.averageDaysBetween": "Days between disasters",
    "config.deadlands.disasters.firstDay": "First disaster day",
    "config.deadlands.disasters.destructive": "World damage",
    "config.deadlands.disasters.twister": "Twisters",
    "config.deadlands.disasters.flashFlood": "Flash floods",
    "config.deadlands.disasters.tremor": "Tremors",
    "config.deadlands.disasters.skyfall": "Skyfall",
    "config.deadlands.disasters.whiteout": "Whiteouts",
    "config.deadlands.disasters.dustWall": "Dust walls",
    "config.deadlands.disasters.causticRain": "Caustic rain",
    "config.deadlands.disasters.staticStorm": "Static storms",
}


def disasters(sounds: dict) -> tuple[dict, bool]:
    """Text and (placeholder, until the Pixabay recordings) sound events for every disaster."""
    text = dict(CONFIG_TEXT)
    text[f"disaster.{NS}.all_clear"] = "The %s has passed."
    changed = False
    for key, (name, warning, start, warn_file, loop_file) in DISASTERS.items():
        text[f"disaster.{NS}.{key}"] = name.lower() if key != "skyfall" else "skyfall"
        text[f"disaster.{NS}.{key}.title"] = name
        text[f"disaster.{NS}.{key}.warning"] = warning
        text[f"disaster.{NS}.{key}.start"] = start
        text[f"subtitles.{NS}.disaster.{key}.warning"] = f"{name} warning"
        text[f"subtitles.{NS}.disaster.{key}"] = name
        for event, file, sub in ((f"disaster.{key}.warning", warn_file, f"subtitles.{NS}.disaster.{key}.warning"),
                                 (f"disaster.{key}", loop_file, f"subtitles.{NS}.disaster.{key}")):
            if event not in sounds:
                sounds[event] = {"subtitle": sub, "sounds": [{"name": f"{NS}:{file}"}]}
                changed = True
    return text, changed


HOUSE_LINES = [
    "The porch light is switched on. There has been no power here for months.",
    "Adam. If you're reading this, you came home. We waited four days.",
    "The soldiers say the north road is still open. We're going to the reservoir camp at Pinecrest.",
    "The kids made me promise to leave the porch light on. I left it switched on anyway.",
    "We are all right. We are together. Home by seven, remember? We're holding you to it.",
]
HOUSE_TIMES = [(1, 5.5), (6, 10.5), (10.5, 15.5), (15.5, 20), (20, 25.5)]


def family_house() -> dict:
    """Phase 5 step 3: the empty house. The anchor is the middle of the house (11 x 11), +Z its front."""
    shots = [
        shot(6, [-14, 3, 18], [-6, 2.2, 14], [0, 2, 5.5]),          # 1 up the path toward the porch
        shot(5, [3, 2, 10], [1.5, 2.2, 8], [0, 2.4, 5.5]),          # 2 the porch light, switch on, bulb dark
        shot(6, [7, 2.4, 2], [7, 2.4, -2], [0, 1.4, 0]),            # 3 past the windows: empty rooms
        shot(5, [0, 9, -12], [0, 12, -16], [0, 1, 0]),              # 4 high, behind: the house alone
        shot(4, [0, 14, 4], [0, 18, 20], [0, 8, 60], rise=6.0),     # 5 rise and look north, the way they went
    ]
    lines = [{"from": int(a * SEC), "to": int(b * SEC), "key": f"cutscene.{NS}.family_house.{i}"} for i, (a, b) in enumerate(HOUSE_TIMES)]
    return {"cue": f"{NS}:event.reunion", "hide_hud": True, "shots": shots, "lines": lines}


def build() -> None:
    scene = intro()
    total = sum(s["ticks"] for s in scene["shots"])
    assert total == 60 * SEC, total
    D.asset_json("cutscene/outbreak_intro.json", scene)
    text = {f"cutscene.{NS}.outbreak_intro.{i}": line for i, line in enumerate(LINES)}
    text[f"cutscene.{NS}.outbreak_intro.title"] = "DEADLANDS"
    meet = reunion()
    D.asset_json("cutscene/reunion.json", meet)
    text.update({f"cutscene.{NS}.reunion.{i}": line for i, line in enumerate(REUNION_LINES)})
    house = family_house()
    D.asset_json("cutscene/family_house.json", house)
    text.update({f"cutscene.{NS}.family_house.{i}": line for i, line in enumerate(HOUSE_LINES)})
    D.merge_lang(text)
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    changed = False
    if "music.intro" not in sounds:  # placeholder until the Pixabay score (step 5)
        sounds["music.intro"] = {"sounds": [{"name": f"{NS}:music/what_remains", "stream": True}]}
        changed = True
    if "ambient.intro" not in sounds:  # placeholder until the Pixabay wind bed (import_pixabay.py)
        sounds["ambient.intro"] = {"sounds": [{"name": f"{NS}:ambient/dry_wind_loop", "stream": True}]}
        changed = True
    if "disaster.caustic_rain.burn" not in sounds:
        sounds["disaster.caustic_rain.burn"] = {"sounds": [{"name": f"{NS}:ambient/ember1"}]}
        changed = True
    if "event.reunion" not in sounds:  # placeholder until the Pixabay cue (step 5)
        sounds["event.reunion"] = {"subtitle": f"subtitles.{NS}.reunion", "sounds": [{"name": f"{NS}:music/campfire", "stream": True}]}
        changed = True
    disaster_text, disaster_sounds = disasters(sounds)
    changed |= disaster_sounds
    D.merge_lang(disaster_text)
    if changed:
        sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    D.merge_lang({f"subtitles.{NS}.reunion": "A familiar voice"})
    print(f"  cinematics: the intro cutscene ({total // SEC} s, {len(scene['actors'])} actors), the day 10 reunion "
          f"({sum(s['ticks'] for s in meet['shots']) // SEC} s)")


if __name__ == "__main__":
    build()
