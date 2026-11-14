"""Mythos' sounds and music (the owner's files), and the rest of his non-Blender assets: spawn eggs, the Haxxed Blade,
the glitch particle, his lore file, his two cutscenes and their text.

Sources: ~/Downloads/Mythos/ (skipped when missing, keeping what the mod has). Every file is re-encoded to OGG Vorbis
at 44.1 kHz: mono for sounds (heard where he is), stereo for the music layers. Events with no file of their own reuse
one of his at another pitch; see MISSING. His body, rig, textures and animations come from Blender
(tools/assetgen/blender/mythos_build.py, mythos_anims.py, subagent_build.py, subagent_anims.py).
"""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N

NS = "deadlands"
SRC = Path.home() / "Downloads" / "Mythos"
OUT = N.ASSETS / "sounds" / "mythos"

# event: [(source file, pitch)]
SFX = {
    "mythos.idle": [("1xbreathingaud.mp3.mpeg", 1.0)],
    "mythos.step": [("1xNewFootstep1.mp3.mpeg", 1.0), ("1xNewFootstep2.mp3.mpeg", 1.0), ("1xNewFootstep3.mp3.mpeg", 1.0)],
    "mythos.slash": [("1xnewslashaud.mp3.mpeg", 1.0)],
    "mythos.slash_hit": [("1xSlashHitNew.mp3.mpeg", 1.0)],
    "mythos.entangle": [("1xEntanglementFlyby.mp3.mpeg", 1.0)],
    "mythos.entangle_hit": [("1xEntanglementHitNew.mp3.mpeg", 1.0)],
    "mythos.mass_infection": [("1xMassInfectionNewer.mp3.mpeg", 1.0)],
    "mythos.mass_infection_far": [("1xMassInfectionFarAwayNewer.mp3.mpeg", 1.0)],
    "mythos.packet": [("1x_mass_infection_projectile2.ogg", 1.0)],
    "mythos.puddle": [("Mass_infection_without_the_rattling_or_whatever.ogg", 1.0)],
    "mythos.rejuvenate": [("1xRejuvenateNewer.mp3.mpeg", 1.0)],
    "mythos.kill": [("1xKillNewer.mp3.mpeg", 1.0)],
    "mythos.summon": [("1xMinionSummonNew.mp3.mpeg", 1.0)],
    "sub_agent.idle": [("1xMinionIdleNew.mp3.mpeg", 1.0)],
    "sub_agent.step": [("1xMinionFootstep1.mp3.mpeg", 1.0), ("1xMinionFootstep2.mp3.mpeg", 1.0), ("1xMinionFootstep3.mp3.mpeg", 1.0)],
    "sub_agent.hit": [("1xMinionHitNew.mp3.mpeg", 1.0)],
    # No file of their own: stand-ins (his own sounds at another pitch).
    "mythos.hurt": [("1xEntanglementHitNew.mp3.mpeg", 1.45)],
    "mythos.death": [("1xMassInfectionFarAwayNewer.mp3.mpeg", 0.6)],
    "mythos.blink": [("1xEntanglementFlyby.mp3.mpeg", 1.6)],
    "mythos.phase": [("1xMassInfectionNewer.mp3.mpeg", 0.7)],
    "mythos.tornado_warning": [("1xMassInfectionFarAwayNewer.mp3.mpeg", 0.5)],
    "sub_agent.death": [("1xMinionHitNew.mp3.mpeg", 0.6)],
}
MISSING = ["mythos.hurt", "mythos.death", "mythos.blink", "mythos.phase", "mythos.tornado_warning", "sub_agent.death"]
UNUSED = ["Lv_0_20250713210504.mp3.mpeg", "1xEntanglemenOld.mp3.mpeg"]
MUSIC = {"music.mythos.layer1": "layer.ogg", "music.mythos.layer2": "layer 2.ogg", "music.mythos.layer3": "layer 3.ogg",
         "music.mythos.layer4": "chase.ogg"}
SUBTITLES = {
    "mythos.idle": "Something breathes through static", "mythos.step": "Heavy, clawed footsteps", "mythos.slash": "Claws rake the air",
    "mythos.slash_hit": "Claws tear", "mythos.entangle": "A code-tether whips past", "mythos.entangle_hit": "Entangled",
    "mythos.mass_infection": "Mythos spews corruption", "mythos.mass_infection_far": "Corruption, far off",
    "mythos.packet": "A corrupted packet flies", "mythos.puddle": "Infected data hisses", "mythos.rejuvenate": "Mythos rebuilds itself",
    "mythos.kill": "Process terminated", "mythos.summon": "Sub-agents spawn", "mythos.hurt": "Mythos glitches",
    "mythos.death": "Mythos crashes", "mythos.blink": "Mythos fragments", "mythos.phase": "Mythos roars",
    "mythos.tornado_warning": "A storm of static gathers", "sub_agent.idle": "A sub-agent clicks", "sub_agent.step": "Skittering",
    "sub_agent.hit": "A sub-agent bites", "sub_agent.death": "A sub-agent breaks apart",
}
TEXT = {
    "entity.deadlands.mythos": "Mythos",
    "entity.deadlands.sub_agent": "Sub-agent",
    "item.deadlands.mythos_spawn_egg": "Mythos Spawn Egg",
    "item.deadlands.sub_agent_spawn_egg": "Sub-agent Spawn Egg",
    "item.deadlands.haxxed_blade": "Haxxed Blade",
    "boss.deadlands.mythos": "MYTHOS",
    "boss.deadlands.mythos.arrives": "Something is coming down with the storm.",
    "boss.deadlands.mythos.warning": "The sky is wrong. A storm of static is forming.",
    "boss.deadlands.mythos.defeated": "Mythos is down. The storm is breaking up.",
    "boss.deadlands.mythos.escaped": "You got away. The storm loses interest and breaks up.",
    "cutscene.deadlands.mythos_arrival.0": "KESSLER REGIONAL // MYTHOS PREDICTIVE MODEL // STATUS: UNCONTAINED",
    "cutscene.deadlands.mythos_arrival.title": "MYTHOS",
    "cutscene.deadlands.mythos_defeat.0": "PROCESS TERMINATED",
    "cutscene.deadlands.mythos_defeat.title": "MYTHOS // SEGMENTATION FAULT",
    "lore.deadlands.mythos_file.title": "MYTHOS: Predictive Model, Kessler Regional",
    "lore.deadlands.mythos_file.body": ("Rourke's people built MYTHOS to do what no lab could: run NR-7 forward a million times a night "
        "and find the version of it that healed instead of hungered. It was the best model of the agent anyone ever made. By "
        "the second month it was predicting mutations the samples hadn't made yet. By the third it was making them first. "
        "When the containment failed, MYTHOS didn't fail with it: the copy of the agent living in its simulations got into the "
        "building's network, and the network ran everything. Hale ordered the servers burned. They burned. The storms started "
        "the week after: tornadoes full of static that leave something behind where they touch down. It builds itself a body "
        "out of whatever the storm carries. Every one we've killed came back taller."),
    "config.deadlands.section.mythos": "Mythos",
    "config.deadlands.mythos.enabled": "Mythos and his tornado",
    "config.deadlands.mythos.health": "Health",
    "config.deadlands.mythos.armor": "Armor",
    "config.deadlands.mythos.damage": "Slash damage",
    "config.deadlands.mythos.walkSpeed": "Walking speed",
    "config.deadlands.mythos.chaseSpeed": "Chase speed",
    "config.deadlands.mythos.slashCooldown": "Corrupted Slash cooldown (seconds)",
    "config.deadlands.mythos.entangleCooldown": "Entanglement cooldown (seconds)",
    "config.deadlands.mythos.summonCooldown": "Spawn Sub-agents cooldown (seconds)",
    "config.deadlands.mythos.massInfectionCooldown": "Mass Infection cooldown (seconds)",
    "config.deadlands.mythos.rejuvenateCooldown": "Rejuvenate cooldown (seconds)",
    "config.deadlands.mythos.blinkCooldown": "Fragment Blink cooldown (seconds)",
    "config.deadlands.mythos.maxSubAgents": "Most sub-agents alive",
    "config.deadlands.mythos.subAgentHealth": "Sub-agent health",
    "config.deadlands.mythos.subAgentDamage": "Sub-agent damage",
    "config.deadlands.mythos.escapeDistance": "Escape distance (blocks)",
    "config.deadlands.mythos.guaranteedNight": "Guaranteed on night",
    "config.deadlands.mythos.nightlyChance": "Chance each later night",
    "config.deadlands.mythos.tornadoDestroys": "Tornado breaks blocks",
    "config.deadlands.mythos.cutscenes": "Arrival and defeat cutscenes",
    "config.deadlands.audio.mythosTerrorRadius": "Mythos terror radius",
}


def step(at, kind, **extra):
    out = {"at": at, "type": kind}
    out.update(extra)
    return out


def encode(name: str, target: Path, channels: int) -> bool:
    source = SRC / name
    if not source.exists():
        return target.exists()
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-ar", "44100", "-ac", str(channels),
                    "-c:a", "libvorbis", "-q:a", "6", str(target)], check=True)
    return True


def file_id(name: str) -> str:
    return name.split(".")[0].replace(" ", "_").replace("!", "").lower()


def egg(name: str, shell, spots) -> None:
    icon = np.zeros((16, 16, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:16, 0:16]
    inside = ((xx - 7.5) / 5.2) ** 2 + ((yy - 8.5) / 6.8) ** 2 <= 1.0
    icon[inside] = (*shell, 255)
    icon[inside & (((xx * 7 + yy * 3) % 6) == 0)] = (*spots, 255)
    Image.fromarray(icon, "RGBA").save(N.asset(f"textures/item/{name}.png"))
    D.asset_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    D.asset_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def blade() -> None:
    """The Haxxed Blade: a black, broken-edged blade with magenta corruption, a violet grip (16 x 16, diagonal)."""
    icon = np.zeros((16, 16, 4), dtype=np.uint8)
    for i in range(2, 14):
        x, y = i, 15 - i
        icon[y, x] = (24, 18, 32, 255)                 # the spine of the blade
        icon[y - 1, x] = (40, 30, 54, 255)
        if i % 3 == 0:
            icon[y - 1, x] = (242, 122, 216, 255)       # corruption along the edge
        if i % 4 == 1 and y - 2 >= 0:
            icon[y - 2, x] = (255, 79, 214, 255)
    for i in range(0, 4):
        icon[15 - i, i] = (90, 31, 150, 255)            # the grip
    icon[12, 2] = icon[13, 3] = (242, 122, 216, 255)    # the guard
    Image.fromarray(icon, "RGBA").save(N.asset("textures/item/haxxed_blade.png"))
    D.asset_json("models/item/haxxed_blade.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/haxxed_blade"}})
    D.asset_json("items/haxxed_blade.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/haxxed_blade"}})


def glitch_particle() -> None:
    """Four 8 x 8 frames of a breaking pixel block, white (the particle tints it magenta or violet)."""
    rng = np.random.default_rng(3)
    names = []
    for f in range(4):
        tile = np.zeros((8, 8, 4), dtype=np.uint8)
        keep = rng.random((4, 4)) > 0.25 + f * 0.15
        for y in range(4):
            for x in range(4):
                if keep[y, x]:
                    tile[y * 2:y * 2 + 2, x * 2:x * 2 + 2] = (255, 255, 255, 255)
        Image.fromarray(tile, "RGBA").save(N.asset(f"textures/particle/glitch_{f}.png"))
        names.append(f"{NS}:glitch_{f}")
    D.asset_json("particles/glitch.json", {"textures": names})


def cutscenes() -> None:
    # (Offsets in his frame: +z is in front of him, where he faces the survivor.)
    # Arrival: wide on the funnel touching down, then in close as he unfolds and roars (he plays "entrance" meanwhile).
    D.asset_json("cutscene/mythos_arrival.json", {
        "cue": f"{NS}:mythos.tornado_warning",
        "hide_hud": True,
        "shots": [
            {"ticks": 45, "from": [0, 14, 34], "to": [0, 10, 26], "look": [0, 8, 0]},
            {"ticks": 70, "from": [6, 3, 11], "to": [-3, 3.5, 8], "look": [0, 2.8, 0], "look_rise": 0.6},
        ],
        "lines": [{"from": 10, "to": 60, "key": "cutscene.deadlands.mythos_arrival.0"}],
        "title": "cutscene.deadlands.mythos_arrival.title",
    })
    # Defeat: around him as he falls and breaks apart.
    D.asset_json("cutscene/mythos_defeat.json", {
        "cue": f"{NS}:mythos.death",
        "hide_hud": True,
        "shots": [
            {"ticks": 60, "from": [-7, 3, 7], "to": [7, 2.5, 7], "look": [0, 1.2, 0]},
            {"ticks": 30, "from": [0, 9, 12], "to": [0, 12, 16], "look": [0, 1, 0]},
        ],
        "lines": [{"from": 20, "to": 85, "key": "cutscene.deadlands.mythos_defeat.0"}],
        "title": "cutscene.deadlands.mythos_defeat.title",
    })


def build() -> None:
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    missing = []
    for event, files in SFX.items():
        entries = []
        for name, pitch in files:
            fid = file_id(name)
            if encode(name, OUT / f"{fid}.ogg", 1):
                entries.append({"name": f"{NS}:mythos/{fid}", **({"pitch": pitch} if pitch != 1.0 else {})})
            else:
                missing.append(name)
        if entries:
            sounds[event] = {"subtitle": f"subtitles.{NS}.{event}", "sounds": entries}
    for event, name in MUSIC.items():
        fid = "music_" + file_id(name)
        if encode(name, OUT / f"{fid}.ogg", 2):
            sounds[event] = {"sounds": [{"name": f"{NS}:mythos/{fid}", "stream": True}]}
        else:
            missing.append(name)
    sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    text = dict(TEXT)
    text.update({f"subtitles.{NS}.{event}": line for event, line in SUBTITLES.items()})
    D.merge_lang(text)
    egg("mythos_spawn_egg", (20, 14, 30), (242, 122, 216))
    egg("sub_agent_spawn_egg", (40, 24, 60), (255, 79, 214))
    blade()
    glitch_particle()
    cutscenes()
    print(f"  mythos sounds: {len(SFX)} events, {len(MUSIC)} music layers; missing files {missing}; stand-ins for {MISSING}; unused {UNUSED}")


if __name__ == "__main__":
    build()
