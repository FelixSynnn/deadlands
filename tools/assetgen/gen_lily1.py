"""Phase 5 step 5: Imlilylol's campaign, chapter one, "Open Road" (story/ImlilylolCampaign.java).

Writes its three missions, the hidden achievement and every line. It builds on her letters in the lore: the
clean north pumps, the broken bridge on the east road, and the scientist (Harley) she promised to find again.
"""
from __future__ import annotations

import dl_data as D

NS = "deadlands"
K = "mission.deadlands.imlilylol"

TEXT = {
    "quest.deadlands.imlilylol.campaign": "Sit. I have work, and I have stories. Same thing, mostly.",
    # The North Pumps.
    f"{K}.north_pumps": "The North Pumps",
    f"{K}.north_pumps.description": "A settlement on Imlilylol's route is drinking bad water. She can trade them through to the clean north pumps, but she needs purification tablets to bargain with.",
    f"{K}.north_pumps.tablets": "Purification tablets",
    f"{K}.north_pumps.ask.1": "There's a settlement two days down my road. They're drinking from the river, and the river is full of what's floating in it.",
    f"{K}.north_pumps.ask.2": "The north pumps are clean. I know the people who hold them. They'll share, for a price: three purification tablets.",
    f"{K}.north_pumps.ask.3": "Find me three. I'll do the walking and the talking. I always do.",
    f"{K}.north_pumps.reminder": "Three purification tablets. Every day they wait, somebody else gets sick.",
    f"{K}.north_pumps.thanks.1": "That's the deal made. Clean water by the end of the week. They'll never know your name, and that's fine.",
    f"{K}.north_pumps.thanks.2": "You know what I trade, really? Not tablets. Time. You just bought a whole town some.",
    # East Road.
    f"{K}.east_road": "East Road",
    f"{K}.east_road.description": "The bridge on the east road is down. Imlilylol's trade has to go round by the evacuation camp. Scout it for her and come back with what you saw.",
    f"{K}.east_road.scout": "Scout the evacuation camp",
    f"{K}.east_road.tell": "Tell Imlilylol what you found",
    f"{K}.east_road.ask.1": "The east bridge went down in the first week. Now everything goes round by the old evacuation camp.",
    f"{K}.east_road.ask.2": "I don't go anywhere I haven't seen with my own eyes, or with eyes I trust. You'll do.",
    f"{K}.east_road.ask.3": "Go and look at the camp. Who's there, what's there, what's waiting. Then come back and tell me.",
    f"{K}.east_road.reminder": "The evacuation camp. Look first, tell me after. That's the whole job.",
    f"{K}.east_road.thanks.1": "Dead tents and a gate nobody closed. Then the road's open, if you're quick and quiet.",
    f"{K}.east_road.thanks.2": "I'll mark it on my map. My map is the only thing I own that's worth anything.",
    # A Promise.
    f"{K}.a_promise": "A Promise",
    f"{K}.a_promise.description": "Imlilylol once promised a scientist she walked with that she would find her again. She can't leave her road. Carry her word to Harley.",
    f"{K}.a_promise.tell_harley": "Carry Imlilylol's word to Harley",
    f"{K}.a_promise.ask.1": "I walked with a scientist for a while, before. She looked at the dead like they were questions. I told her I'd find her again.",
    f"{K}.a_promise.ask.2": "Then the radio told me her name. Harley. She's alive, and I can't leave the road. Somebody has to keep the towns talking.",
    f"{K}.a_promise.ask.3": "So you'll go for me. Tell her Lily kept her word, mostly. She'll laugh. She always laughed at that.",
    f"{K}.a_promise.reminder": "Harley. Tell her Lily kept her word, mostly. Go on.",
    f"{K}.a_promise.tell": "Lily asked me to find you. She says she kept her word, mostly.",
    f"{K}.a_promise.harley.1": "Mostly. Of course she said mostly.",
    f"{K}.a_promise.harley.2": "She walked me out of the city when I couldn't tell north from south. I never thanked her properly.",
    f"{K}.a_promise.harley.3": "If you see her again, tell her the scientist owes her a drink. A real one, when there are real ones again.",
    "chronicle.deadlands.story.imlilylol_chapter_one": "%s carried Imlilylol's word to Harley, and the road stayed open.",
    "achievement.deadlands.story.imlilylol_chapter_one.title": "Mostly",
    "achievement.deadlands.story.imlilylol_chapter_one.description": "Finish Imlilylol's first chapter and carry her word to Harley.",
}


def build() -> None:
    D.data_json("mission/imlilylol/north_pumps.json", {
        "title": f"{K}.north_pumps", "description": f"{K}.north_pumps.description",
        "objectives": [{"id": "tablets", "type": "have_item", "text": f"{K}.north_pumps.tablets", "target": f"{NS}:purification_tablet", "count": 3}],
        "reward_items": [{"item": f"{NS}:bandage", "count": 3}], "reward_flags": [], "lore": []})
    D.data_json("mission/imlilylol/east_road.json", {
        "title": f"{K}.east_road", "description": f"{K}.east_road.description", "requires": ["lily_pumps_done"],
        "objectives": [{"id": "scout", "type": "visit", "text": f"{K}.east_road.scout", "target": f"{NS}:evacuation_camp"},
                       {"id": "tell", "type": "flag", "text": f"{K}.east_road.tell", "flag": "lily_east_road_told"}],
        "reward_items": [], "reward_flags": [], "lore": []})
    D.data_json("mission/imlilylol/a_promise.json", {
        "title": f"{K}.a_promise", "description": f"{K}.a_promise.description", "requires": ["lily_east_road_told"],
        "objectives": [{"id": "tell", "type": "flag", "text": f"{K}.a_promise.tell_harley", "flag": "lily_word_to_harley"}],
        "reward_items": [], "reward_flags": ["imlilylol_chapter_one"], "lore": []})
    D.data_json("advancement/story/imlilylol_chapter_one.json", {
        "parent": f"{NS}:root", "criteria": {"done": {"trigger": "minecraft:impossible"}}, "requirements": [["done"]],
        "display": {"icon": {"id": "minecraft:map"}, "title": {"translate": "achievement.deadlands.story.imlilylol_chapter_one.title"},
                    "description": {"translate": "achievement.deadlands.story.imlilylol_chapter_one.description"},
                    "frame": "goal", "show_toast": True, "announce_to_chat": False, "hidden": True}})
    D.merge_lang(TEXT)
    print(f"  Imlilylol chapter one: 3 missions, {len(TEXT)} lines")


if __name__ == "__main__":
    build()
