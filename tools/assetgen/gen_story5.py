"""Phase 5 (story): Itzbreadloaf's campaign, chapter one: Harley's questline and Adam.

Writes the missions (harley/static, harley/cold_chain, story/the_case), Harley's giver list, the sealed
sample case (icon, flat and 3D models), Adam's placeholder skin (the researcher's, with glasses and
greying hair; listed in docs/ASSET_MANIFEST.md for an artist), every line of their dialogue, and the
hidden story achievement. The logic is story/HarleyQuest.java.

Story Canon notes: Harley and Adam were colleagues for years, strictly professional. Adam is a father
first; he still hopes his family is out there. Nothing here hints at what the family house reveals.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N
from dl_models3d import box
from gen_models3d import BRASS, STEEL, display_for, write_model

NS = "deadlands"

TEXT = {
    # Missions.
    "mission.deadlands.harley.static": "Static",
    "mission.deadlands.harley.static.description": "Harley's receiver is dead. With it working she could listen for the others from the lab.",
    "mission.deadlands.harley.static.parts": "Radio parts",
    "mission.deadlands.harley.static.fuse": "A fuse",
    "mission.deadlands.harley.static.ask": "My receiver's dead. If I can get it working I can listen for the others from the lab. Two sets of radio parts and a fuse. Bring them here.",
    "mission.deadlands.harley.static.reminder": "Two radio parts and a fuse. The receiver won't fix itself.",
    "mission.deadlands.harley.static.thanks": "That's it. Give me an hour with these and we'll hear more than static.",
    "mission.deadlands.harley.cold_chain": "Cold Chain",
    "mission.deadlands.harley.cold_chain.description": "Something at the lab was kept cold for as long as the power lasted. Harley needs power and clean water to move it.",
    "mission.deadlands.harley.cold_chain.battery": "A car battery",
    "mission.deadlands.harley.cold_chain.tablets": "Purification tablets",
    "mission.deadlands.harley.cold_chain.ask": "There's something at the lab I kept cold as long as the power lasted. To move it I need a car battery and two purification tablets. Please.",
    "mission.deadlands.harley.cold_chain.reminder": "A car battery and two purification tablets. It won't keep forever.",
    "mission.deadlands.harley.cold_chain.thanks": "Here. It's sealed, don't open it. The project number on the label is Adam's. He'll know what it means better than I do. Take it to him.",
    "mission.deadlands.harley.cold_chain.where": "Harley: Adam's alive. Last I heard he was holed up at %s, %s, %s.",
    "mission.deadlands.harley.cold_chain.where_title": "Adam",
    "mission.deadlands.story.the_case": "The Case",
    "mission.deadlands.story.the_case.description": "Harley gave you a sealed sample case with Adam's project number on it. Take it to him.",
    "mission.deadlands.story.the_case.deliver": "Bring the case to Adam",
    "mission.deadlands.story.the_case.waiting": "Harley sent you? Then she gave you something. Bring it here.",
    "mission.deadlands.story.the_case.adam.1": "Where did you... Harley had this?",
    "mission.deadlands.story.the_case.adam.2": "This is batch seven. I sealed it myself, the week before everything went wrong. If it's still intact...",
    "mission.deadlands.story.the_case.adam.3": "Sit down. I need to think. And then I need to show you something.",
    # The case.
    "item.deadlands.sealed_sample_case": "Sealed Sample Case",
    "item.deadlands.sealed_sample_case.desc": "From the regional lab. A project number on the seal. Do not open.",
    # Adam.
    "npc.deadlands.character.adam.greeting": "%s. You're real. Sorry. I stopped trusting what I see a while ago.",
    "npc.deadlands.character.adam.story.0": "I ran a team at the regional lab. Harley and I worked across the hall from each other for eleven years.",
    "npc.deadlands.character.adam.story.1": "I promised my wife I'd be home by seven every night. I was, the night it started. Then I went back for the samples.",
    "npc.deadlands.character.adam.story.2": "Lily would tell me I'm being an idiot. She always could. Felix would just pack a bag and come with me.",
    "npc.deadlands.character.adam.story.3": "I'm not a soldier. I keep telling myself that's an excuse. It isn't. It's just true.",
    "npc.deadlands.character.adam.talk": "Not now. When Harley's ready, she'll find you. She always does.",
    # Hidden story achievement.
    "achievement.deadlands.story.the_case.title": "Batch Seven",
    "achievement.deadlands.story.the_case.description": "Bring Harley's case to Adam.",
}


def objective(oid, kind, text, target=None, count=None, flag=None):
    out = {"id": oid, "type": kind, "text": text}
    if target:
        out["target"] = target
    if count:
        out["count"] = count
    if flag:
        out["flag"] = flag
    return out


def missions() -> None:
    k = "mission.deadlands."
    D.data_json("mission/harley/static.json", {
        "title": k + "harley.static", "description": k + "harley.static.description", "giver": "harley",
        "objectives": [objective("parts", "have_item", k + "harley.static.parts", f"{NS}:radio_parts", 2),
                       objective("fuse", "have_item", k + "harley.static.fuse", f"{NS}:fuse", 1)],
        "reward_items": [], "reward_flags": ["harley_receiver_fixed"], "lore": []})
    D.data_json("mission/harley/cold_chain.json", {
        "title": k + "harley.cold_chain", "description": k + "harley.cold_chain.description", "giver": "harley",
        "requires": ["harley_receiver_fixed"],
        "objectives": [objective("battery", "have_item", k + "harley.cold_chain.battery", f"{NS}:car_battery", 1),
                       objective("tablets", "have_item", k + "harley.cold_chain.tablets", f"{NS}:purification_tablet", 2)],
        "reward_items": [{"item": f"{NS}:sealed_sample_case", "count": 1}], "reward_flags": ["harley_gave_case"], "lore": []})
    D.data_json("mission/story/the_case.json", {
        "title": k + "story.the_case", "description": k + "story.the_case.description",
        "requires": ["harley_gave_case"],
        "objectives": [objective("deliver", "flag", k + "story.the_case.deliver", flag="adam_saw_case")],
        "reward_items": [], "reward_flags": ["chapter_one_case_delivered"], "lore": []})
    D.data_json("mission_giver/harley.json", {"cooldown_min_minutes": 10, "cooldown_max_minutes": 15, "stop_flag": "harley_dead",
                                              "missions": [f"{NS}:harley/static", f"{NS}:harley/cold_chain"]})
    D.data_json("advancement/story/the_case.json", {
        "parent": f"{NS}:root", "criteria": {"done": {"trigger": "minecraft:impossible"}}, "requirements": [["done"]],
        "display": {"icon": {"id": f"{NS}:sealed_sample_case"}, "title": {"translate": "achievement.deadlands.story.the_case.title"},
                    "description": {"translate": "achievement.deadlands.story.the_case.description"},
                    "frame": "goal", "show_toast": True, "announce_to_chat": False, "hidden": True}})


def adam_skin() -> None:
    src = np.array(Image.open(N.asset("textures/entity/survivor/researcher_0.png")).convert("RGBA")).astype(float)
    skin = src.copy()
    # Greying hair: the top and sides of the head (both layers), toward ash.
    for x0, y0, x1, y1 in ((8, 0, 24, 8), (0, 8, 32, 10), (40, 0, 56, 8), (32, 8, 64, 10)):
        region = skin[y0:y1, x0:x1, :3]
        grey = region.mean(axis=2, keepdims=True)
        skin[y0:y1, x0:x1, :3] = region * 0.45 + grey * 0.4 + 40
    # Glasses on the face (front of the head: x 8-16, y 8-16; eyes on row 12).
    frame = np.array([38, 36, 34])
    for x in range(9, 15):
        skin[11, x, :3] = frame
    for x in (9, 11, 12, 14):
        skin[12, x, :3] = frame
    out = N.asset("textures/entity/survivor/character/adam.png")
    Image.fromarray(np.clip(skin, 0, 255).astype(np.uint8), "RGBA").save(out)


def sample_case() -> None:
    # Flat icon (the inventory): a steel case with a red seal and a white label.
    icon = np.zeros((32, 32, 4))
    icon[9:25, 5:27, :3] = (150, 156, 160)
    icon[9:25, 5:27, 3] = 255
    icon[9, 5:27, :3] = (196, 200, 204)
    icon[24, 5:27, :3] = (84, 88, 92)
    icon[16:18, 5:27, :3] = (96, 100, 104)
    icon[11:15, 9:18, :3] = (230, 226, 214)
    icon[19:23, 20:24, :3] = (168, 30, 26)
    icon[5:9, 12:20, :3] = (60, 62, 64)
    icon[5:9, 12:20, 3] = 255
    icon[6:9, 13:19, 3] = 0
    N.save_png(N.asset("textures/item/sealed_sample_case.png"), icon[..., :3], icon[..., 3] / 255.0)
    D.asset_json("models/item/sealed_sample_case.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/sealed_sample_case"}})
    boxes = [
        box(3, 4, 5, 13, 11, 11, "case"),
        box(2.8, 7.3, 4.8, 13.2, 7.7, 11.2, "seam"),
        box(6, 11, 7.4, 6.6, 12.4, 8.6, "case"), box(9.4, 11, 7.4, 10, 12.4, 8.6, "case"), box(6, 12.4, 7.4, 10, 13, 8.6, "grip"),
        box(4.0, 6.8, 4.7, 5.0, 8.2, 5.0, "latch"), box(11.0, 6.8, 4.7, 12.0, 8.2, 5.0, "latch"),
        box(4.5, 8.4, 4.85, 8.5, 10.2, 5.0, "label"),
        box(9.6, 4.6, 4.85, 11.2, 6.2, 5.0, "seal"),
    ]
    materials = {"case": dict(STEEL, base="#9aa0a4", dark="#5e6468", rust=0.05), "seam": {"kind": "metal", "base": "#5c6064", "dark": "#33373a", "brush": 0.2},
                 "grip": {"kind": "rubber", "color": "#202022"}, "latch": BRASS, "label": {"kind": "paint", "color": "#e4e0d2", "chip": 0.05, "under_rust": 0.0},
                 "seal": {"kind": "polymer", "color": "#a01e1a"}}
    write_model("sealed_sample_case", "sealed_sample_case_3d", boxes, materials, 9801, display_for("sealed_sample_case", 0))
    D.asset_json("items/sealed_sample_case.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": f"{NS}:item/sealed_sample_case"}}],
        "fallback": {"type": "minecraft:model", "model": f"{NS}:item/sealed_sample_case_3d"}}})


def build() -> None:
    missions()
    adam_skin()
    sample_case()
    D.merge_lang(TEXT)
    print("  story (chapter one): 3 missions, Adam, the sealed sample case")


if __name__ == "__main__":
    build()
