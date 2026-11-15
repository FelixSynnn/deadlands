"""Phase 5 step 4 (story): chapter four "Pinecrest", the bittersweet end of Harley's story.

Writes the mission (story/pinecrest), its hidden achievement, the arrival cutscene (cutscene/pinecrest.json,
50 s; Pinecrest.CUTSCENE_TICKS must match) and every line of text. The logic is story/Pinecrest.java.

Story Canon notes: Adam's wife and their two kids are unnamed. Harley was bitten at a bridge on the road
and hid it; the camp cannot take her in. FelixSyn's choice is the survivor's to make.
"""
from __future__ import annotations

import dl_data as D
from gen_cinematics import SEC, actor, shot

NS = "deadlands"
LENGTH = 50

TEXT = {
    "mission.deadlands.story.pinecrest": "Pinecrest",
    "mission.deadlands.story.pinecrest.description": "Adam's family went north to the reservoir camp at Pinecrest. Get everyone there: Adam, Harley and FelixSyn.",
    "mission.deadlands.story.pinecrest.reach": "Reach the Pinecrest reservoir camp",
    "mission.deadlands.story.pinecrest.choose": "Help FelixSyn decide",
    "mission.deadlands.story.pinecrest.ask.1": "We're going to Pinecrest. All of us. I won't have you walk me to the door and say goodbye there.",
    "mission.deadlands.story.pinecrest.ask.2": "Harley's coming. She wants to see what their medical tent has. FelixSyn won't let her out of his sight, so he's coming too.",
    "mission.deadlands.story.pinecrest.ask.3": "It's a long way north. You lead, I'll keep up. I've been keeping up for months.",
    "mission.deadlands.story.pinecrest.not_yet": "Harley won't leave while someone she's waiting for is still out there. And I won't leave without Harley. Not yet.",
    "mission.deadlands.story.pinecrest.waiting": "Pinecrest. The reservoir camp. Every day we don't go is another day they wait.",
    "mission.deadlands.story.pinecrest.where": "Adam: The gate at Pinecrest is at %s, %s, %s.",
    "mission.deadlands.story.pinecrest.where_title": "Pinecrest",
    "mission.deadlands.story.pinecrest.arrived": "Pinecrest. The gate is open.",
    "mission.deadlands.story.pinecrest.question": "You've been with us since the start. You know her, and you know me. What would you do?",
    "mission.deadlands.story.pinecrest.choice.stay": "Stay with her",
    "mission.deadlands.story.pinecrest.choice.stay.hover": "FelixSyn stays outside the fence with Harley.",
    "mission.deadlands.story.pinecrest.choice.go": "Go inside",
    "mission.deadlands.story.pinecrest.choice.go.hover": "FelixSyn goes into Pinecrest. Harley stays outside on her own.",
    "mission.deadlands.story.pinecrest.stay.felix": "Then I stay. However much time she has, she won't spend it alone.",
    "mission.deadlands.story.pinecrest.stay.harley": "You stubborn, stupid... Thank you. Get the fire going, then.",
    "mission.deadlands.story.pinecrest.stay.end": "FelixSyn stays outside the fence with Harley. Adam is home with his family. Chapter four is over.",
    "mission.deadlands.story.pinecrest.go.felix": "She's right. She's always right. I'll be at the fence every morning. Every single one.",
    "mission.deadlands.story.pinecrest.go.harley": "Every morning. I'll be here. Go on, before I change my mind.",
    "mission.deadlands.story.pinecrest.go.end": "FelixSyn goes into Pinecrest; Harley stays at the lean-to outside the gate. Adam is home with his family. Chapter four is over.",
    "mission.deadlands.story.pinecrest.after.0": "We eat together every night now. I'm still not used to it.",
    "mission.deadlands.story.pinecrest.after.1": "I take Harley her share every morning. She asks about the notebook before she'll talk about herself.",
    "mission.deadlands.story.pinecrest.after.2": "The kids keep asking who you are. I tell them you're the reason I made it home.",
    "npc.deadlands.adams_wife": "Adam's Wife",
    "npc.deadlands.adams_eldest": "Adam's Eldest",
    "npc.deadlands.adams_youngest": "Adam's Youngest",
    "command.deadlands.choose.nothing": "There is nothing to choose right now.",
    "chronicle.deadlands.story.pinecrest.stay": "%s brought everyone to Pinecrest. Adam found his family; FelixSyn stayed outside the fence with Harley.",
    "chronicle.deadlands.story.pinecrest.go": "%s brought everyone to Pinecrest. Adam found his family; FelixSyn went in, and Harley stayed outside.",
    "achievement.deadlands.story.pinecrest.title": "Home by Seven",
    "achievement.deadlands.story.pinecrest.description": "Bring Adam to his family at Pinecrest.",
}

# (from, to) in seconds, then the line.
LINES = [
    (1.5, 6.5, "Pinecrest. Smoke over the walls. Someone is up on the tower."),
    (8, 12.5, "Adam: That's... that's them. That's them!"),
    (13.5, 16, "A child: Dad! Dad, it's him!"),
    (16, 19, "Adam's wife: Home by seven. You're months late."),
    (19.5, 23.5, "Adam: I know. I know. I'm here. I'm not going anywhere."),
    (24.5, 27.5, "Guard: Everyone gets checked at the gate. No exceptions."),
    (27.5, 30.5, "Guard: You. Your arm. Roll up the sleeve."),
    (31, 34.5, "Harley: The bridge. Two days ago. I didn't want to say it in front of the kids."),
    (34.5, 37.5, "Guard: I'm sorry. She can't come in."),
    (38, 41, "Harley: I knew the second I saw the gate. Adam has the notebook and batch seven."),
    (41, 44, "Harley: If there's a cure, it's in there with him now. That's enough for me."),
    (44.5, 47, "FelixSyn: Then I'm not going in either."),
    (47, 50, "Harley: Felix. You crossed the whole state to find me. Don't waste that on a fence."),
]


def pinecrest() -> dict:
    """The anchor is just outside the gate; -Z is into the camp, +Z down the road they came by."""
    shots = [
        shot(7, [8, 12, -8], [4, 10, -4], [0, 1, 10]),                        # 1 over the wall: the group on the road
        shot(6, [3, 1.6, 20], [2, 1.6, 9], [0, 1.8, 0]),                      # 2 behind them, the gate ahead
        shot(6, [-6, 1.7, 2], [-5, 1.7, 2.5], [0, 1.3, 1.2]),                 # 3 side on: the family runs out
        shot(5, [3.5, 1.7, -1.5], [3, 1.7, -1], [0, 1.4, 1.4]),               # 4 Adam holding them
        shot(7, [-4, 1.8, 7], [-3.5, 1.8, 6.5], [-0.5, 1.5, 4]),              # 5 the guard steps up to Harley
        shot(7, [-1.5, 1.7, 1.6], [-1.4, 1.65, 2.0], [-1.5, 1.55, 4]),        # 6 Harley, her sleeve
        shot(6, [2.6, 1.7, 5.6], [2.3, 1.7, 5.2], [-1.5, 1.55, 4]),           # 7 over FelixSyn onto Harley
        shot(6, [1.3, 1.7, 1.6], [1.0, 1.75, 2.3], [1.2, 1.6, 4.2]),          # 8 FelixSyn
    ]
    actors = [
        actor("adam", 0, LENGTH, [0, 0, 16], [0, 0, 1.6], face=[0, 0, 0], move=(0, 13)),
        actor("harley", 0, LENGTH, [-1.5, 0, 17], [-1.5, 0, 4], face=[1.2, 0, 4.2], move=(0, 11)),
        actor("felixsyn", 0, LENGTH, [1.5, 0, 17], [1.2, 0, 4.2], face=[-1.5, 0, 4], move=(0, 11)),
        actor("survivor", 0, LENGTH, [0, 0, -7], [0, 0, 0.4], face=[0, 0, 2], move=(11, 15)),
        {**actor("survivor", 0, LENGTH, [-1, 0, -7], [-0.8, 0, 0.9], face=[0, 0, 2], move=(10.5, 14)), "scale": 0.7},
        {**actor("survivor", 0, LENGTH, [1, 0, -7], [0.7, 0, 1.0], face=[0, 0, 2], move=(10, 13.5)), "scale": 0.55},
        actor("survivor", 0, LENGTH, [2.6, 0, -0.5], [-0.3, 0, 3.4], face=[-1.5, 0, 4], move=(23, 26)),
    ]
    lines = [{"from": int(a * SEC), "to": int(b * SEC), "key": f"cutscene.{NS}.pinecrest.{i}"} for i, (a, b, _) in enumerate(LINES)]
    out = {"cue": f"{NS}:event.reunion", "hide_hud": True, "shots": shots, "lines": lines, "actors": actors}
    assert sum(s["ticks"] for s in shots) == LENGTH * SEC
    return out


def objective(oid, text, flag):
    return {"id": oid, "type": "flag", "text": text, "flag": flag}


def build() -> None:
    k = "mission.deadlands.story.pinecrest"
    D.data_json("mission/story/pinecrest.json", {
        "title": k, "description": k + ".description", "requires": ["chapter_three_house"],
        "objectives": [objective("reach", k + ".reach", "pinecrest_reached"), objective("choose", k + ".choose", "pinecrest_choice_made")],
        "reward_items": [], "reward_flags": ["chapter_four_pinecrest"], "lore": []})
    D.data_json("advancement/story/pinecrest.json", {
        "parent": f"{NS}:root", "criteria": {"done": {"trigger": "minecraft:impossible"}}, "requirements": [["done"]],
        "display": {"icon": {"id": "minecraft:campfire"}, "title": {"translate": "achievement.deadlands.story.pinecrest.title"},
                    "description": {"translate": "achievement.deadlands.story.pinecrest.description"},
                    "frame": "goal", "show_toast": True, "announce_to_chat": False, "hidden": True}})
    D.asset_json("cutscene/pinecrest.json", pinecrest())
    text = dict(TEXT)
    text.update({f"cutscene.{NS}.pinecrest.{i}": line for i, (_, _, line) in enumerate(LINES)})
    D.merge_lang(text)
    print(f"  story chapter four: Pinecrest mission, {LENGTH} s arrival cutscene, {len(text)} lines of text")


if __name__ == "__main__":
    build()
