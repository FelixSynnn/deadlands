"""Survivors (Milestone 7): skins in Minecraft's own 64x64 player layout (three per role, and a
placeholder for each recurring character), the invisible spawn marker's block files, the cargo
table for found vehicles, every line survivors say, their voices (synthesised: a sawtooth glottal
pulse through three vowel formants) and the journal's TASKS icon.

Skins use only the base layer of the player layout: head (0,0)-(32,16), body (16,16)-(40,32), right
arm (40,16)-(56,32), right leg (0,16)-(16,32), left leg (16,48)-(32,64), left arm (32,48)-(48,64).
A recurring character's skin comes from character-skins/<Name>.png in the project root (matched by
name, ignoring case; a 64x64 skin, slim or classic arms, copied pixel for pixel), with a generated
placeholder if there is none. In game, a file at config/deadlands/skins/<id>.png replaces it.
"""
from __future__ import annotations

import zlib
from pathlib import Path

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N

NS = D.NS
SKIN_TONES = ["#c89a78", "#a8785a", "#e0b898", "#7a5238", "#d8a888"]
HAIR = ["#2a1e16", "#5a3a22", "#8a6a3a", "#3a3a3a", "#a04a2a", "#c8b890"]

# role: [(shirt, sleeves, trousers, shoes, extra)], three looks each
ROLES = {
    "settler": [("#6a7a5a", "#6a7a5a", "#3a4252", "#2a221a", None), ("#8a5a42", "#8a5a42", "#4a4236", "#2a221a", None),
                ("#52627a", "#c89a78", "#5a4a36", "#3a2e22", None)],
    "guard": [("#3a4236", "#3a4236", "#2e3228", "#1a1a1a", "armband"), ("#2e3440", "#2e3440", "#2a2e36", "#1a1a1a", "vest"),
              ("#4a4a3a", "#4a4a3a", "#36362a", "#1a1a1a", "vest")],
    "trader": [("#7a5a36", "#7a5a36", "#4a3a26", "#2a1e14", "apron"), ("#5a4a5a", "#5a4a5a", "#3a3240", "#2a221a", "apron"),
               ("#8a7a4a", "#8a7a4a", "#4a4232", "#2a221a", None)],
    "doctor": [("#e0e0d8", "#e0e0d8", "#4a5a6a", "#2a2a2a", "cross"), ("#d0d8d8", "#d0d8d8", "#3a4a4a", "#2a2a2a", "cross"),
               ("#7ab0a8", "#7ab0a8", "#7ab0a8", "#e0e0d8", "cross")],
    "mechanic": [("#3a5a7a", "#3a5a7a", "#3a5a7a", "#1a1a1a", "grime"), ("#5a5a5a", "#c89a78", "#3a3e42", "#1a1a1a", "grime"),
                 ("#7a4a2a", "#7a4a2a", "#3a3a3a", "#1a1a1a", "grime")],
    "farmer": [("#a84a3a", "#a84a3a", "#4a5a7a", "#3a2a1a", "straps"), ("#c8b068", "#c8b068", "#5a4a36", "#3a2a1a", "straps"),
               ("#5a7a4a", "#5a7a4a", "#4a5a7a", "#3a2a1a", "straps")],
    "leader": [("#3a3a4a", "#3a3a4a", "#2a2a36", "#1a1a1a", "collar"), ("#5a3a2a", "#5a3a2a", "#2e2a26", "#1a1a1a", "collar"),
               ("#4a5a4a", "#4a5a4a", "#2e3a2e", "#1a1a1a", "collar")],
    "bandit": [("#1e1e22", "#1e1e22", "#2a2622", "#1a1a1a", "bandana"), ("#3a2a26", "#1e1e22", "#22221e", "#1a1a1a", "bandana"),
               ("#2a2e2a", "#2a2e2a", "#1e1e1e", "#1a1a1a", "bandana")],
    "wanderer": [("#6a5a3e", "#6a5a3e", "#3e3626", "#2a221a", "hat"), ("#4a5a5a", "#4a5a5a", "#3a3a32", "#2a221a", "hat"),
                 ("#7a6a5a", "#7a6a5a", "#4a4236", "#2a221a", "hat")],
    "researcher": [("#e8e8e0", "#e8e8e0", "#4a4a5a", "#2a2a2a", "collar"), ("#d8e0e0", "#d8e0e0", "#3a4a3a", "#2a2a2a", None),
                   ("#8ab8a0", "#8ab8a0", "#8ab8a0", "#d8d8d0", "armband")],
}
CHARACTERS = {
    "harley": ("#e0e0d8", "#e0e0d8", "#3a4a5a", "#2a2a2a", "cross", "#e0b898", "#a04a2a"),
    "itzbreadloaf": ("#b8843e", "#b8843e", "#5a4226", "#3a2a1a", None, "#d8a888", "#5a3a22"),
    "imlilylol": ("#a888c8", "#a888c8", "#4a4262", "#2a2236", None, "#e0b898", "#2a1e16"),
    "felixsyn": ("#3a7a4a", "#3a7a4a", "#2e3a36", "#1a1a1a", "vest", "#c89a78", "#3a3a3a"),
}


SUPPLIED_SKINS = N.ROOT / "character-skins"


def supplied_skin(name: str) -> Path | None:
    """character-skins/<Name>.png for a character, matched ignoring case; None if there is none."""
    if not SUPPLIED_SKINS.is_dir():
        return None
    for path in sorted(SUPPLIED_SKINS.iterdir()):
        if path.suffix.lower() == ".png" and path.stem.lower() == name.lower():
            return path
    return None


def rgb(c):
    return np.array(N.hex_rgb(c), dtype=float)


def skin(shirt, sleeves, trousers, shoes, extra, tone, hair, seed) -> np.ndarray:
    img = np.zeros((64, 64, 4))

    def fill(x0, y0, x1, y1, colour):
        img[y0:y1, x0:x1, :3] = rgb(colour) if isinstance(colour, str) else colour
        img[y0:y1, x0:x1, 3] = 255

    fill(0, 0, 32, 16, tone)                      # head
    fill(8, 0, 16, 8, hair)                       # top of the head
    fill(16, 0, 24, 8, tone)                      # underside
    fill(0, 8, 32, 11, hair)                      # hairline all round
    fill(24, 8, 32, 16, hair)                     # back of the head
    fill(0, 8, 8, 13, hair)
    fill(16, 8, 24, 13, hair)
    fill(8, 8, 16, 10, hair)                      # fringe
    fill(9, 12, 11, 13, "#f0f0e8")
    fill(13, 12, 15, 13, "#f0f0e8")
    fill(10, 12, 11, 13, "#2a2622")
    fill(13, 12, 14, 13, "#2a2622")
    fill(10, 14, 14, 15, rgb(tone) * 0.7)         # mouth
    fill(16, 16, 40, 32, shirt)                   # body
    fill(40, 16, 56, 32, sleeves)                 # right arm
    fill(32, 48, 48, 64, sleeves)                 # left arm
    fill(40, 28, 56, 32, tone)                    # hands
    fill(32, 60, 48, 64, tone)
    fill(0, 16, 16, 32, trousers)                 # right leg
    fill(16, 48, 32, 64, trousers)                # left leg
    fill(0, 29, 16, 32, shoes)
    fill(16, 61, 32, 64, shoes)
    fill(20, 30, 28, 32, rgb(trousers) * 0.8)     # belt line on the body front
    if extra == "armband":
        fill(40, 22, 56, 24, "#b02a22")
    elif extra == "vest":
        fill(20, 20, 28, 30, rgb(shirt) * 0.6)
        fill(32, 20, 40, 30, rgb(shirt) * 0.6)
    elif extra == "apron":
        fill(21, 23, 27, 32, "#c8b890")
    elif extra == "cross":
        fill(23, 21, 25, 27, "#b02a22")
        fill(21, 23, 27, 25, "#b02a22")
    elif extra == "grime":
        r = np.random.default_rng(seed)
        for _ in range(26):
            x, y = int(r.integers(16, 56)), int(r.integers(16, 32))
            img[y:y + 2, x:x + 2, :3] *= 0.6
    elif extra == "straps":
        fill(21, 20, 22, 30, rgb(trousers))
        fill(26, 20, 27, 30, rgb(trousers))
        fill(21, 26, 27, 30, rgb(trousers))
    elif extra == "collar":
        fill(22, 20, 26, 23, "#d8d0b8")
    elif extra == "bandana":
        fill(8, 13, 16, 16, "#8a1e1a")
        fill(0, 13, 8, 16, "#8a1e1a")
        fill(16, 13, 24, 16, "#8a1e1a")
    elif extra == "hat":
        fill(8, 0, 16, 8, "#3a2e1e")
        fill(0, 8, 32, 10, "#3a2e1e")
    noise = N.spectral(64, 64, beta=1.5, seed=seed)
    img[..., :3] = np.clip(img[..., :3] * (0.9 + 0.2 * noise[..., None]), 0, 255)
    return img


def seed_of(text):
    return zlib.crc32(text.encode())


TEXT = {
    "entity.deadlands.survivor": "Survivor",
    "block.deadlands.spawn_marker": "Spawn Marker",
    "npc.deadlands.subtitle": "%s  |  %s  |  %s",
    "npc.deadlands.refuses": "%s wants nothing to do with you.",
    "npc.deadlands.trading": "Trading with %s",
    "npc.deadlands.cannot_pay": "That is %s rounds. You do not have them.",
    "message.deadlands.reputation.changed": "The %s now regard you as: %s.",
    "command.deadlands.npc": "Placed %s.",
    "command.deadlands.npc.unknown": "There is no role or character called %s.",
    "command.deadlands.npc.already": "%s is already somewhere in this world. Each of them appears once; only one who was lost can be put back.",
    # roles, factions, standings
    "npc.deadlands.role.settler": "Settler", "npc.deadlands.role.guard": "Guard", "npc.deadlands.role.trader": "Trader",
    "npc.deadlands.role.doctor": "Doctor", "npc.deadlands.role.mechanic": "Mechanic", "npc.deadlands.role.farmer": "Farmer",
    "npc.deadlands.role.leader": "Leader", "npc.deadlands.role.bandit": "Bandit", "npc.deadlands.role.wanderer": "Travelling Merchant",
    "npc.deadlands.role.researcher": "Researcher",
    "npc.deadlands.faction.settlers": "Settlers", "npc.deadlands.faction.military": "Military", "npc.deadlands.faction.bandits": "Bandits",
    "npc.deadlands.faction.drifters": "Drifters", "npc.deadlands.faction.research": "Research Team",
    "npc.deadlands.standing.hostile": "Hostile", "npc.deadlands.standing.wary": "Wary", "npc.deadlands.standing.neutral": "Neutral",
    "npc.deadlands.standing.friendly": "Friendly", "npc.deadlands.standing.trusted": "Trusted",
    # replies
    "npc.deadlands.option.trade": "Let me see what you have.",
    "npc.deadlands.option.rumour": "Heard anything?",
    "npc.deadlands.option.place": "Tell me about this place.",
    "npc.deadlands.option.heal": "I need patching up.",
    "npc.deadlands.option.repair": "Can you look at my vehicle?",
    "npc.deadlands.option.story": "How did you end up here?",
    "npc.deadlands.option.leave": "Take care.",
    "npc.deadlands.option.give": "I brought something for the town.",
    "npc.deadlands.option.family": "Do you have anyone?",
    "npc.deadlands.greeting.grieving": "Sorry. I am not much company since we lost %s.",
    "npc.deadlands.give.none_here": "There is no town here to give it to.",
    "npc.deadlands.give.nothing": "We cannot use that. Food, water and medicine go in the stores; planks, logs, stone and iron go into the walls.",
    "npc.deadlands.give.supplies": "That goes in the stores. Thank you. %s",
    "npc.deadlands.give.defence": "That goes into the walls. Thank you. %s",
    "npc.deadlands.settlement.status": "There are %s of us. The stores are %s and the walls are %s. %s",
    "npc.deadlands.settlement.supplies.plenty": "full",
    "npc.deadlands.settlement.supplies.enough": "enough for now",
    "npc.deadlands.settlement.supplies.short": "running low",
    "npc.deadlands.settlement.supplies.none": "empty",
    "npc.deadlands.settlement.defence.strong": "strong",
    "npc.deadlands.settlement.defence.fair": "fair",
    "npc.deadlands.settlement.defence.weak": "weak",
    "npc.deadlands.settlement.event.quiet": "It has been quiet.",
    "npc.deadlands.settlement.event.hunger": "We went hungry yesterday.",
    "npc.deadlands.settlement.event.attack_lost": "They got in yesterday. We lost people.",
    "npc.deadlands.settlement.event.attack_held": "They came at the walls yesterday. We held.",
    "npc.deadlands.settlement.event.arrival": "Someone new walked in yesterday.",
    "npc.deadlands.settlement.event.fallen": "There is nobody left to defend.",
    "npc.deadlands.family.partner": "My partner, %s, is here with me. They keep me going.",
    "npc.deadlands.family.sibling": "%s is my sibling. We got out of the city together.",
    "npc.deadlands.family.friend": "%s and I go back to before all this. I would not have made it without them.",
    "npc.deadlands.family.grieving.partner": "%s was my partner. They are gone now. I still look for them in the mornings.",
    "npc.deadlands.family.grieving.sibling": "%s was my sibling. I should have been there.",
    "npc.deadlands.family.grieving.friend": "%s was my friend. I am still not used to the quiet.",
    "npc.deadlands.clue.unknown": "I have heard nothing of %s. If you find them, tell them I am still here.",
    "npc.deadlands.clue.near": "%s? They are close by. You must have walked past them.",
    "npc.deadlands.clue.known": "%s was seen %s of here, about %s blocks. Go carefully.",
    "npc.deadlands.rumour.condition.fallen": "Its people did not make it.",
    "message.deadlands.theft.seen": "%s saw you going through their things.",
    "message.deadlands.access.unwelcome": "You are not welcome in %s. Leave within %s seconds.",
    "message.deadlands.access.curfew": "%s keeps a curfew after dark. Leave within %s seconds.",
    "message.deadlands.access.trespass": "You are trespassing. The guards will treat you as an enemy until you leave.",
    "message.deadlands.task.started": "Restoring the %s. The journal's TASKS page lists what it needs.",
    "message.deadlands.task.done": "The %s runs. Restoration finished.",
    "message.deadlands.task.lost": "The %s is beyond saving now.",
    "journal.deadlands.tab.tasks": "Tasks",
    "journal.deadlands.tasks.title": "Restoration",
    "journal.deadlands.tasks.none": "Nothing being restored. Look over a vehicle that does not run to start.",
    "journal.deadlands.tasks.restore": "The %s",
    "journal.deadlands.tasks.where": "Left at %s, %s",
    "journal.deadlands.tasks.need": "Needs %s",
    "journal.deadlands.tasks.far": "Too far away to see what it still needs.",
    "journal.deadlands.standing.title": "Standing",
    "journal.deadlands.tasks.standing": "%s: %s (%s)",
    "subtitles.deadlands.npc.greet": "Survivor speaks",
    "subtitles.deadlands.npc.idle": "Survivor murmurs",
    "subtitles.deadlands.npc.alert": "Survivor shouts",
    "subtitles.deadlands.npc.hurt": "Survivor hurts",
    "subtitles.deadlands.npc.death": "Survivor dies",
    # greetings: mood x temperament (0 guarded, 1 warm, 2 blunt, 3 weary)
    "npc.deadlands.greeting.wary.0": "Keep your hands where I can see them, %s.",
    "npc.deadlands.greeting.wary.1": "I would like to trust you, %s. I do not yet.",
    "npc.deadlands.greeting.wary.2": "Say what you want and go.",
    "npc.deadlands.greeting.wary.3": "Another stranger. What is it?",
    "npc.deadlands.greeting.neutral.0": "You are not from here. What do you need?",
    "npc.deadlands.greeting.neutral.1": "A new face. Still breathing, too. Good for you.",
    "npc.deadlands.greeting.neutral.2": "Yes?",
    "npc.deadlands.greeting.neutral.3": "Long road, was it? They all are.",
    "npc.deadlands.greeting.friendly.0": "%s. People here speak well of you.",
    "npc.deadlands.greeting.friendly.1": "%s! Come in out of the open.",
    "npc.deadlands.greeting.friendly.2": "You again. Good. What do you need?",
    "npc.deadlands.greeting.friendly.3": "Good to see someone come back, %s.",
    # rumours
    "npc.deadlands.rumour.town": "%s lies %s of here, about %s blocks. %s",
    "npc.deadlands.rumour.condition.evacuated": "They got most people out before it fell. The shelves may still be full.",
    "npc.deadlands.rumour.condition.overrun": "Nobody got out. Do not go in by daylight thinking it is empty.",
    "npc.deadlands.rumour.condition.military": "The army held it for a while. Their stores were never emptied.",
    "npc.deadlands.rumour.condition.survivor": "There are people there. Decent ones, last I heard.",
    "npc.deadlands.rumour.condition.bandit": "Bandits have it. Go round.",
    "npc.deadlands.rumour.condition.abandoned": "Empty for a long time. Picked over, mostly.",
    "npc.deadlands.rumour.0": "They hear better than they see. Walk, do not run, and shut the doors behind you.",
    "npc.deadlands.rumour.1": "Every fifth night they come together. If you count the days, you will know when to be indoors.",
    "npc.deadlands.rumour.2": "A light at night is an invitation. So is an engine.",
    "npc.deadlands.rumour.3": "Some of the pumps still have fuel in them. Most do not. You only find out by trying.",
    "npc.deadlands.rumour.4": "If you see a mound that breathes, do not make a sound near it.",
    "npc.deadlands.rumour.5": "A bite is not the end if you clean it at once. Leave it a day and you will wish you had not.",
    "npc.deadlands.compass.north": "north", "npc.deadlands.compass.northeast": "north-east", "npc.deadlands.compass.east": "east",
    "npc.deadlands.compass.southeast": "south-east", "npc.deadlands.compass.south": "south", "npc.deadlands.compass.southwest": "south-west",
    "npc.deadlands.compass.west": "west", "npc.deadlands.compass.northwest": "north-west",
    # leaders
    "npc.deadlands.place.none": "This is nowhere. We just stopped walking.",
    "npc.deadlands.place.powered": "This is %s. %s And the lights are on again, which is more than anyone hoped for.",
    "npc.deadlands.place.dark": "This is %s. %s We sit in the dark at night. Someone with a fuse and the nerve could change that.",
    # doctors
    "npc.deadlands.heal.fine": "There is nothing wrong with you that I can fix.",
    "npc.deadlands.heal.infected": "Nothing I can stitch. But that bite needs antiseptic, or better, and soon.",
    "npc.deadlands.heal.done": "Hold still. There. You will live.",
    "npc.deadlands.heal.done_infected": "That is the wounds seen to. The infection I cannot cut out. You need medicine for that.",
    # mechanics
    "npc.deadlands.repair.none": "Bring it closer. I am not walking out there to look at it.",
    "npc.deadlands.repair.fine": "It runs. Leave it alone.",
    "npc.deadlands.repair.done": "That %s will get you where you are going now.",
    "npc.deadlands.repair.more": "Better. That %s still needs more than I have here.",
    # the recurring characters (placeholders for the story milestone)
    "npc.deadlands.character.harley.greeting": "%s. You made it. Sit down, I have a lot to tell you and not much time.",
    "npc.deadlands.character.harley.story.0": "I was a researcher at the regional lab when the first cases came in. Ninety days ago. It feels like years.",
    "npc.deadlands.character.harley.story.1": "We thought it was a fever. By the time we understood what it did to the brain, half the staff had turned.",
    "npc.deadlands.character.harley.story.2": "I kept my notes. Samples too, until the power failed. If I could get tissue from the nests, I could start again.",
    "npc.deadlands.character.harley.story.3": "There were three others who helped me get out. We were separated on the road. I do not know who is still alive.",
    "npc.deadlands.character.harley.story.4": "I am not looking for a cure for the dead. I am looking for a way to keep the living from joining them.",
    "npc.deadlands.character.itzbreadloaf.greeting": "Oh, a person. A real one. Do not touch the engine, %s, I only just got it to stop screaming.",
    "npc.deadlands.character.itzbreadloaf.story.0": "I drove a delivery van. Bread, mostly. Go on, laugh. Everyone does.",
    "npc.deadlands.character.itzbreadloaf.story.1": "When the roads jammed I learned to fix what I was driving, because nobody else was going to.",
    "npc.deadlands.character.itzbreadloaf.story.2": "I got a doctor out of the city in the back of that van. Harley. Clever. Did not sleep the whole way.",
    "npc.deadlands.character.itzbreadloaf.story.3": "Lost her at a roadblock. I keep the van ready in case I ever find the others.",
    "npc.deadlands.character.imlilylol.greeting": "%s, is it? I know a hundred names and yours is new. Come and see what I am carrying.",
    "npc.deadlands.character.imlilylol.story.0": "I never stayed anywhere long, even before. Now it is a profession.",
    "npc.deadlands.character.imlilylol.story.1": "I trade what one town has too much of for what the next one is dying without. It keeps both alive. And me.",
    "npc.deadlands.character.imlilylol.story.2": "I walked with three others for a while. A scientist, a driver and a man who never put his rifle down.",
    "npc.deadlands.character.imlilylol.story.3": "I hear things on the road. If any of them are alive, I will be the first to know. I will tell you if you ask.",
    "npc.deadlands.character.felixsyn.greeting": "Stop there. Let me look at you. All right, %s. You can come closer.",
    "npc.deadlands.character.felixsyn.story.0": "I was on the checkpoint detail when the order came to fall back. Most of the unit did not get the chance.",
    "npc.deadlands.character.felixsyn.story.1": "I stopped taking orders the day they told us to leave the civilians. I took four of them with me instead.",
    "npc.deadlands.character.felixsyn.story.2": "One of them was a scientist who thought she could stop it. I decided that was worth guarding.",
    "npc.deadlands.character.felixsyn.story.3": "We were scattered on the fifth night. I have stood watch here since, in case any of them come this way.",
}


def build() -> int:
    count = 0
    for role, looks in ROLES.items():
        for i, (shirt, sleeves, trousers, shoes, extra) in enumerate(looks):
            s = seed_of(f"{role}{i}")
            sleeve = SKIN_TONES[s % len(SKIN_TONES)] if sleeves == "#c89a78" else sleeves
            img = skin(shirt, sleeve, trousers, shoes, extra, SKIN_TONES[s % len(SKIN_TONES)], HAIR[(s // 7) % len(HAIR)], s)
            Image.fromarray(img.astype(np.uint8), "RGBA").save(N.asset(f"textures/entity/survivor/{role}_{i}.png"))
            count += 1
    for name, (shirt, sleeves, trousers, shoes, extra, tone, hair) in CHARACTERS.items():
        out = N.asset(f"textures/entity/survivor/character/{name}.png")
        supplied = supplied_skin(name)
        if supplied is not None:
            with Image.open(supplied) as im:
                if im.size != (64, 64):
                    raise SystemExit(f"{supplied} is {im.size[0]}x{im.size[1]}; a skin must be 64x64")
                im.convert("RGBA").save(out)  # same pixels; palette files become plain RGBA
        else:
            img = skin(shirt, sleeves, trousers, shoes, extra, tone, hair, seed_of(name))
            Image.fromarray(img.astype(np.uint8), "RGBA").save(out)
        count += 1
    # The spawn marker: no geometry, and a particle texture for the moment it is broken.
    D.asset_json("models/block/spawn_marker.json", {"textures": {"particle": f"{NS}:block/concrete"}})
    variants = {}
    for spawn in ("vehicle_civilian", "vehicle_military", "vehicle_emergency", "vehicle_farm", "vehicle_rugged", "helicopter", "npc_settler", "npc_guard",
                  "npc_trader", "npc_doctor", "npc_mechanic", "npc_farmer", "npc_leader", "npc_bandit", "npc_wanderer", "npc_researcher"):
        for facing in ("north", "east", "south", "west"):
            variants[f"facing={facing},spawn={spawn}"] = {"model": f"{NS}:block/spawn_marker"}
    D.asset_json("blockstates/spawn_marker.json", {"variants": variants})
    D.asset_json("models/item/spawn_marker.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/key"}})
    D.item_model_definition("spawn_marker", f"{NS}:item/spawn_marker")
    # What a found vehicle may be carrying (vanilla names are swapped for DEADLANDS items on the way out).
    def entry(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name if ":" in name else f"minecraft:{name}", "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e
    D.data_json("loot_table/vehicles/cargo.json", {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 5}, "bonus_rolls": 0.0, "entries": [
            entry("bread", 6, 1, 3), entry("dried_kelp", 5, 1, 4), entry(f"{NS}:water_bottle", 6, 1, 2), entry(f"{NS}:fuel_can", 3),
            entry(f"{NS}:tire", 3), entry(f"{NS}:wrench", 2), entry(f"{NS}:bandage", 4, 1, 2), entry(f"{NS}:pistol_ammo", 4, 3, 10),
            entry(f"{NS}:backpack", 1), entry("map", 3), entry("paper", 4, 1, 3), entry(f"{NS}:crowbar", 1), entry(f"{NS}:engine_parts", 2)]}],
        "random_sequence": f"{NS}:vehicles/cargo",
    })
    D.merge_lang(TEXT)
    voices()
    tasks_icon()
    return count


def tasks_icon() -> None:
    """The journal's TASKS bookmark: a clipboard with a checklist, in the style of the other three."""
    from gen_gui import ascii_icon, save_sprite
    pal = {"o": "#1b1410", "w": "#e8e1cf", "s": "#a99f88", "k": "#3a2e26"}
    rows = [
        "................",
        "......oooo......",
        "..oooowkkwoooo..",
        "..owwwwwwwwwwo..",
        "..owkkwwwwwwwo..",
        "..owkowssssswo..",
        "..owwkwwwwwwwo..",
        "..owwwwwwwwwwo..",
        "..owkkwwwwwwwo..",
        "..owkowssssswo..",
        "..owwkwwwwwwwo..",
        "..owwwwwwwwwwo..",
        "..owwwwssssswo..",
        "..owwwwwwwwwwo..",
        "..oooooooooooo..",
        "................",
    ]
    rgb, a = ascii_icon(rows, pal)
    save_sprite(N.ASSETS, "icon/tasks.png", rgb, a)


# vowel formants (Hz) used by the voices; "m" is a closed-mouth hum
FORMANTS = {"a": (730, 1090, 2440), "e": (530, 1840, 2480), "o": (570, 840, 2410), "u": (300, 870, 2240), "m": (250, 1000, 2200)}
# event -> variants -> syllables (vowel, seconds, pitch at start, pitch at end)
VOICE_LINES = {
    "greet": [[("e", 0.12, 150, 172), ("o", 0.22, 176, 140)], [("a", 0.2, 160, 190), ("e", 0.18, 186, 150)], [("m", 0.15, 140, 152), ("a", 0.2, 156, 134)]],
    "idle": [[("m", 0.35, 122, 110)], [("m", 0.2, 130, 140), ("m", 0.25, 140, 116)], [("u", 0.3, 126, 104)]],
    "alert": [[("e", 0.1, 200, 240), ("a", 0.25, 240, 200)], [("o", 0.3, 228, 252)], [("a", 0.12, 210, 250), ("a", 0.16, 240, 196)]],
    "hurt": [[("u", 0.16, 222, 168)], [("a", 0.14, 242, 180)], [("e", 0.12, 230, 188)]],
    "death": [[("a", 0.5, 200, 90)], [("o", 0.6, 182, 80)], [("u", 0.55, 190, 84)]],
}


def voices() -> None:
    """Wordless survivor voices: a sawtooth glottal pulse with breath, shaped by three formant resonators."""
    import json
    from scipy.signal import lfilter
    from gen_audio import OUT, SR, write_ogg

    def resonator(x, f, bw):
        r = np.exp(-np.pi * bw / SR)
        return lfilter([1.0 - r], [1.0, -2.0 * r * np.cos(2 * np.pi * f / SR), r * r], x)

    def syllable(vowel, dur, f0a, f0b, rng, breath):
        n = int(SR * dur)
        t = np.arange(n) / SR
        f0 = np.linspace(f0a, f0b, n) * (1.0 + 0.012 * np.sin(2 * np.pi * 5.5 * t))
        phase = np.cumsum(f0) / SR
        src = 2.0 * (phase % 1.0) - 1.0 + rng.standard_normal(n) * breath
        out = sum(resonator(src, f, bw) * g for f, bw, g in zip(FORMANTS[vowel], (80, 90, 120), (1.0, 0.55, 0.22)))
        env = np.minimum(1.0, t / 0.03) * np.minimum(1.0, (t[-1] - t) / 0.06)
        return out * env

    table = json.loads((OUT.parent / "sounds.json").read_text(encoding="utf-8"))
    for event, variants in VOICE_LINES.items():
        names = []
        for i, line in enumerate(variants):
            rng = np.random.default_rng(seed_of(f"voice{event}{i}"))
            breath = 0.25 if event in ("hurt", "death") else 0.08
            pieces = [np.zeros(int(SR * 0.03))]
            for vowel, dur, a, b in line:
                pieces += [syllable(vowel, dur, a, b, rng, breath), np.zeros(int(SR * 0.05))]
            data = np.concatenate(pieces)
            if event in ("hurt", "alert"):
                data = np.tanh(data / np.max(np.abs(data)) * 2.0)
            data = data / np.max(np.abs(data)) * 0.7
            rel = f"npc/{event}_{i}"
            write_ogg(rel, data, quality=4)
            names.append(f"{NS}:{rel}")
        table[f"npc.{event}"] = {"sounds": names, "subtitle": f"subtitles.{NS}.npc.{event}"}
    (OUT.parent / "sounds.json").write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


if __name__ == "__main__":
    print(build(), "survivor skins written")
