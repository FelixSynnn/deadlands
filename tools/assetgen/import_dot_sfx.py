"""D.O.T.'s sounds and music (the owner's files), spawn egg, text, jumpscares, haunts and the Wrong Frame image.

Source: ~/Downloads/Sounds + Models For DOT/ (skipped when missing, keeping what the mod has). Every file is
re-encoded to OGG Vorbis at 44.1 kHz: mono for his sounds (heard where he is), stereo for the music layers.
Events with no file of their own (hurt, death) reuse one of his at another pitch; see MISSING.
"""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import dl_data as D
import dl_noise as N

NS = "deadlands"
SRC = Path.home() / "Downloads" / "Sounds + Models For DOT"
OUT = N.ASSETS / "sounds" / "dot"

# event: [(source file, pitch)]
SFX = {
    "dot.idle": [("LLIdleNew.ogg", 1.0)],
    "dot.step": [("LLFootstep1New.ogg", 1.0), ("LLFootstep2New.ogg", 1.0)],
    "dot.swing": [("LLM1New.ogg", 1.0)],
    "dot.kill": [("LLKill.ogg", 1.0)],
    "dot.provoked": [("LLUNSTOPPABLENew.ogg", 1.0)],
    "dot.static_step": [("LL404ErrorNew.ogg", 1.0)],
    "dot.restore": [("LL404ErrorErrorNew.ogg", 1.0)],
    "dot.grasp_windup": [("LLDFANew.ogg", 1.0)],
    "dot.grasp": [("LLDFNew.ogg", 1.0)],
    "dot.field_start": [("LLCEStartNew.ogg", 1.0)],
    "dot.field_loop": [("LLCESummonNew.ogg", 1.0)],
    "dot.field_end": [("LLCEDesummonNew.ogg", 1.0)],
    # No file of their own: stand-ins.
    "dot.hurt": [("LL404ErrorNew.ogg", 1.5)],
    "dot.death": [("LLCEDesummonNew.ogg", 0.6)],
}
MISSING = ["dot.hurt", "dot.death"]
MUSIC = {"music.dot.layer1": "LL_Layer1!!!.mp3.mpeg", "music.dot.layer2": "LL_Layer2!!!.mp3.mpeg",
         "music.dot.layer3": "LL_Layer3!!!.mp3.mpeg", "music.dot.layer4": "LLChase!!!.mp3.mpeg"}
SUBTITLES = {
    "dot.idle": "Static hums", "dot.step": "Slow, heavy footsteps", "dot.swing": "A blade cuts the air", "dot.kill": "A killing blow",
    "dot.provoked": "Something won't stop now", "dot.static_step": "Static jumps closer", "dot.restore": "Something rewinds",
    "dot.grasp_windup": "Static rises", "dot.grasp": "Caught", "dot.field_start": "The air corrupts", "dot.field_loop": "Corruption crackles",
    "dot.field_end": "The corruption fades", "dot.hurt": "D.O.T. glitches", "dot.death": "D.O.T. shuts down",
}
TEXT = {
    "entity.deadlands.dot": "D.O.T.",
    "item.deadlands.dot_spawn_egg": "D.O.T. Spawn Egg",
    "config.deadlands.section.dot": "D.O.T.",
    "config.deadlands.dot.enabled": "D.O.T.",
    "config.deadlands.dot.spawnChance": "Spawn chance (per minute)",
    "config.deadlands.dot.inStructures": "In big structures",
    "config.deadlands.dot.onTownRoads": "On town roads",
    "config.deadlands.dot.areaRadius": "One per area (blocks)",
    "config.deadlands.dot.damage": "Damage per hit",
    "config.deadlands.dot.health": "Health",
    "config.deadlands.dot.damageResistance": "Damage resistance",
    "config.deadlands.dot.armor": "Armor",
    "config.deadlands.dot.speed": "Walking speed",
    "config.deadlands.dot.passive": "Passive (only provoked)",
    "config.deadlands.dot.aggroSpeed": "Hunting speed",
    "config.deadlands.dot.giveUpSeconds": "Seconds before he gives up",
    "config.deadlands.dot.alwaysProvokes": "Names that always provoke him",
    "config.deadlands.dot.stareSeconds": "Seconds of staring that provoke him",
    "config.deadlands.dot.pushesThrough": "Walks through doors, fences and leaves",
    "config.deadlands.dot.climbFactor": "Climbing speed",
    "config.deadlands.dot.jumpscares": "His jumpscares",
    "config.deadlands.asesino.climbFactor": "Climbing speed",
    "config.deadlands.guest.climbFactor": "Climbing speed",
    "config.deadlands.zombies.stalkerClimbFactor": "Stalker climbing speed",
    "config.deadlands.zombies.stalkerHealth": "Stalker health",
    "config.deadlands.zombies.stalkerArmor": "Stalker armor",
    "config.deadlands.zombies.stalkerDamage": "Stalker damage",
    "config.deadlands.world.townFrequency": "Town frequency",
    "config.deadlands.audio.dotTerrorRadius": "D.O.T. terror radius",
    "scare.deadlands.dot_404": "ERROR 404: SUBJECT NOT FOUND",
    "scare.deadlands.dot_404.sub": "PATIENT-4 STATUS: DECEASED ON TRANSFER",
}
HAUNTS = ["subway_station", "research_facility", "site_calder", "school", "power_station", "treatment_plant"]


def step(at, kind, **extra):
    out = {"at": at, "type": kind}
    out.update(extra)
    return out


SCARES = {
    # Corruption Field's touch: the flashlight stutters.
    "dot_corruption": [step(0, "flicker", amount=40)],
    # Wrong Frame: one corrupted frame of his face, an error, a jolt.
    "dot_wrong_frame": [step(0, "dot_frame", amount=5), step(0, "sound", sound=f"{NS}:dot.static_step", where="everywhere", volume=1.0, pitch=1.0),
                        step(0, "jolt", amount=0.7)],
    # Behind the Light: the beam dies for a second; when it comes back he is right there.
    "dot_behind_light": [step(0, "beam_off", amount=22), step(0, "silence", amount=22),
                         step(22, "sound", sound=f"{NS}:dot.grasp_windup", where="ahead", volume=1.2, pitch=1.0, distance=3),
                         step(22, "jolt", amount=0.5)],
    # Road Ghost: the screen tears, he's in the road, and gone.
    "dot_road_ghost": [step(0, "dot_tear", amount=30), step(0, "sound", sound=f"{NS}:dot.idle", where="ahead", volume=0.9, pitch=0.8, distance=30)],
    # 404 Death: over the death screen.
    "dot_404": [step(0, "dot_404", amount=40), step(0, "sound", sound=f"{NS}:dot.restore", where="everywhere", volume=1.0, pitch=0.9)],
}


def encode(name: str, target: Path, channels: int) -> bool:
    source = SRC / name
    if not source.exists():
        return target.exists()
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-ar", "44100", "-ac", str(channels),
                    "-c:a", "libvorbis", "-q:a", "6", str(target)], check=True)
    return True


def file_id(name: str) -> str:
    stem = name.split(".")[0].replace("!", "").lower()
    return stem


def spawn_egg() -> None:
    icon = np.zeros((16, 16, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:16, 0:16]
    inside = ((xx - 7.5) / 5.2) ** 2 + ((yy - 8.5) / 6.8) ** 2 <= 1.0
    icon[inside] = (16, 28, 60, 255)
    icon[inside & (yy > 9)] = (13, 58, 34, 255)
    icon[inside & (((xx + yy * 3) % 5) == 0)] = (61, 255, 110, 255)
    icon[3:5, 6:10] = (236, 255, 244, 255)
    Image.fromarray(icon, "RGBA").save(N.asset("textures/item/dot_spawn_egg.png"))
    D.asset_json("models/item/dot_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/dot_spawn_egg"}})
    D.asset_json("items/dot_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/dot_spawn_egg"}})


def wrong_frame() -> None:
    """The Wrong Frame: his hooded face filling the screen, torn by glitch bands. Original, drawn here."""
    w, h = 512, 288
    img = Image.new("RGB", (w, h), (2, 4, 8))
    d = ImageDraw.Draw(img)
    d.ellipse((136, 30, 376, 330), fill=(14, 22, 44))                   # the hood
    d.ellipse((176, 70, 336, 300), fill=(3, 4, 7))                      # nothing inside it
    d.rectangle((214, 150, 244, 158), fill=(61, 255, 110))              # two slits
    d.rectangle((268, 150, 298, 158), fill=(61, 255, 110))
    d.rectangle((226, 8, 286, 40), outline=(236, 255, 244), width=7)    # the square halo
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    a = np.asarray(img).astype(np.int32)
    rng = np.random.default_rng(404)
    for _ in range(14):                                                 # glitch bands, shifted sideways
        y0 = int(rng.integers(0, h - 8))
        band = int(rng.integers(3, 14))
        a[y0:y0 + band] = np.roll(a[y0:y0 + band], int(rng.integers(-60, 60)), axis=1)
    a[..., 0] = np.roll(a[..., 0], 4, axis=1)                           # colour fringes
    a[..., 2] = np.roll(a[..., 2], -4, axis=1)
    a = a + rng.integers(-18, 18, a.shape)                              # static
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").save(N.asset("textures/gui/dot_wrong_frame.png"))


def build() -> None:
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    missing = []
    for event, files in SFX.items():
        entries = []
        for name, pitch in files:
            fid = file_id(name)
            if encode(name, OUT / f"{fid}.ogg", 1):
                entries.append({"name": f"{NS}:dot/{fid}", **({"pitch": pitch} if pitch != 1.0 else {})})
            else:
                missing.append(name)
        if entries:
            sounds[event] = {"subtitle": f"subtitles.{NS}.{event}", "sounds": entries}
    for event, name in MUSIC.items():
        fid = file_id(name)
        if encode(name, OUT / f"{fid}.ogg", 2):
            sounds[event] = {"sounds": [{"name": f"{NS}:dot/{fid}", "stream": True}]}
        else:
            missing.append(name)
    sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    text = dict(TEXT)
    text.update({f"subtitles.{NS}.{event}": line for event, line in SUBTITLES.items()})
    D.merge_lang(text)
    spawn_egg()
    wrong_frame()
    D.data_json("tags/worldgen/structure/dot_haunts.json", {"replace": False, "values": [f"{NS}:{s}" for s in HAUNTS]})
    for name, steps in SCARES.items():
        D.data_json(f"jumpscare/{name}.json", {"kind": "major", "trigger": "event", "weight": 1, "steps": steps})
    print(f"  dot sounds: {len(SFX)} events, {len(MUSIC)} music layers; missing files {missing}; stand-ins for {MISSING}")


if __name__ == "__main__":
    build()
