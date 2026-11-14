"""Phase 5 step 5: FelixSyn's campaign, chapter one, "The East Gate" (story/FelixsynCampaign.java).

Writes its three missions, the hidden achievement and every line. It builds on his field notes in the lore:
Camp Meridian, Okafor, the two hundred people left outside the wire, and Colonel Hale. Also the label for
the Guest's night delay setting (the seventh round's killer changes).
"""
from __future__ import annotations

import dl_data as D

NS = "deadlands"
K = "mission.deadlands.felixsyn"

TEXT = {
    "config.deadlands.guest.nightDelayMinutes": "Minutes into the night before it comes",
    "quest.deadlands.felixsyn.campaign": "Got a minute? I've got a list. It's not a short one.",
    # Okafor.
    f"{K}.okafor": "Okafor",
    f"{K}.okafor.description": "FelixSyn and Okafor walked out of Camp Meridian together and split up on the road. Her last call came from a military post.",
    f"{K}.okafor.scout": "Search a military post",
    f"{K}.okafor.tell": "Tell FelixSyn what you found",
    f"{K}.okafor.ask.1": "Okafor put her rifle down first. I only followed her. We split up a week out, to cover more ground.",
    f"{K}.okafor.ask.2": "Her last call came from one of the old military posts. Then nothing.",
    f"{K}.okafor.ask.3": "Go and look for me. Whatever you find there, I want to hear it from someone who saw it.",
    f"{K}.okafor.reminder": "A military post. Look for her things, her marks, anything. Then come back.",
    f"{K}.okafor.thanks.1": "Her mark on the wall. An arrow, pointing north. That's Okafor: she never leaves without telling you which way.",
    f"{K}.okafor.thanks.2": "North means Pinecrest, or past it. She's alive, or she was when she drew that.",
    f"{K}.okafor.thanks.3": "That's enough. That's more than enough. Thank you.",
    # The Two Hundred.
    f"{K}.the_two_hundred": "The Two Hundred",
    f"{K}.the_two_hundred.description": "Two hundred people were left outside the wire at Meridian. The ones who made it camp on their own now. FelixSyn wants to bring them something.",
    f"{K}.the_two_hundred.beans": "Canned beans",
    f"{K}.the_two_hundred.bandages": "Bandages",
    f"{K}.the_two_hundred.ask.1": "The night the gate closed, there were two hundred people on the wrong side of it. Some of them made it.",
    f"{K}.the_two_hundred.ask.2": "They camp by the river now, on their own. They don't want soldiers near them. I don't blame them.",
    f"{K}.the_two_hundred.ask.3": "Four cans of beans and three bandages. I'll leave them where they'll find them. They don't have to know who.",
    f"{K}.the_two_hundred.reminder": "Four cans of beans, three bandages. It doesn't fix anything. It's what I can do.",
    f"{K}.the_two_hundred.thanks.1": "I'll take them down tonight.",
    f"{K}.the_two_hundred.thanks.2": "I used to think I'd say sorry to them one day. Now I think the beans say it better.",
    f"{K}.the_two_hundred.thanks.3": "One more thing. Then I'm done looking back.",
    # Hale's Trail.
    f"{K}.hales_trail": "Hale's Trail",
    f"{K}.hales_trail.description": "Colonel Hale signed the order that closed the gate. FelixSyn wants proof of what he knew: his research files.",
    f"{K}.hales_trail.files": "Military research files",
    f"{K}.hales_trail.ask.1": "Hale read that order like he'd read it before. He knew what was coming. I want to know how long.",
    f"{K}.hales_trail.ask.2": "His people carried research files out of Kessler. They'll be in the military places, if anywhere.",
    f"{K}.hales_trail.ask.3": "Bring me one. People should know who closed that gate, and why.",
    f"{K}.hales_trail.reminder": "Military research files. One is enough. One with his name on it.",
    f"{K}.hales_trail.thanks.1": "There. Day minus ninety. He knew three months before any of us.",
    f"{K}.hales_trail.thanks.2": "I'll copy it out. Every settlement Lily talks to will hear it.",
    f"{K}.hales_trail.thanks.3": "I walked out of that gate. I'm done walking away. Thank you, for all of it.",
    "chronicle.deadlands.story.felixsyn_chapter_one": "%s helped FelixSyn find Okafor's trail and the truth about the east gate.",
    "achievement.deadlands.story.felixsyn_chapter_one.title": "The East Gate",
    "achievement.deadlands.story.felixsyn_chapter_one.description": "Finish FelixSyn's first chapter.",
}


def build() -> None:
    D.data_json("mission/felixsyn/okafor.json", {
        "title": f"{K}.okafor", "description": f"{K}.okafor.description",
        "objectives": [{"id": "scout", "type": "visit", "text": f"{K}.okafor.scout", "target": f"{NS}:military_post"},
                       {"id": "tell", "type": "flag", "text": f"{K}.okafor.tell", "flag": "felix_okafor_told"}],
        "reward_items": [], "reward_flags": [], "lore": []})
    D.data_json("mission/felixsyn/the_two_hundred.json", {
        "title": f"{K}.the_two_hundred", "description": f"{K}.the_two_hundred.description", "requires": ["felix_okafor_told"],
        "objectives": [{"id": "beans", "type": "have_item", "text": f"{K}.the_two_hundred.beans", "target": f"{NS}:canned_beans", "count": 4},
                       {"id": "bandages", "type": "have_item", "text": f"{K}.the_two_hundred.bandages", "target": f"{NS}:bandage", "count": 3}],
        "reward_items": [], "reward_flags": [], "lore": []})
    D.data_json("mission/felixsyn/hales_trail.json", {
        "title": f"{K}.hales_trail", "description": f"{K}.hales_trail.description", "requires": ["felix_supplies_done"],
        "objectives": [{"id": "files", "type": "have_item", "text": f"{K}.hales_trail.files", "target": f"{NS}:military_research", "count": 1}],
        "reward_items": [], "reward_flags": ["felixsyn_chapter_one"], "lore": []})
    D.data_json("advancement/story/felixsyn_chapter_one.json", {
        "parent": f"{NS}:root", "criteria": {"done": {"trigger": "minecraft:impossible"}}, "requirements": [["done"]],
        "display": {"icon": {"id": "minecraft:iron_door"}, "title": {"translate": "achievement.deadlands.story.felixsyn_chapter_one.title"},
                    "description": {"translate": "achievement.deadlands.story.felixsyn_chapter_one.description"},
                    "frame": "goal", "show_toast": True, "announce_to_chat": False, "hidden": True}})
    D.merge_lang(TEXT)
    print(f"  FelixSyn chapter one: 3 missions, {len(TEXT)} lines")


if __name__ == "__main__":
    build()
