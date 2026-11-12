"""The Guest's sounds and chase music from the owner's GuestSoundsPack, its spawn egg, text and credits.

Source: ~/Downloads/GuestSoundsPack/*.ogg (skipped when missing, keeping what the mod has). The pack was
made for another move set, so each file is matched to the nearest of the Guest's own moments (SFX). Every
file is re-encoded to OGG Vorbis at 44.1 kHz: mono for its sounds (heard where it is), stereo for the music.
The Guest's music layers: Guest666_layer1-3 and the chase theme as layer 4 (see GuestMusic).
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
SRC = Path.home() / "Downloads" / "GuestSoundsPack"
OUT = N.ASSETS / "sounds" / "guest"

# event: [(file, pitch)] from the pack.
SFX = {
    "guest.ambient": [("G666_ambience", 1.0)],
    "guest.step": [("G666_footstep01", 1.0), ("G666_footstep02", 1.0)],
    "guest.swing": [("G666_swing01", 1.0), ("G666_swing02", 1.0)],
    "guest.feed": [("G666_chomp", 1.0)],
    "guest.kill": [("G666_execution", 1.0)],
    "guest.roar": [("G666_crystart", 1.0)],
    "guest.roar_blast": [("G666_cryshockwave", 1.0)],
    "guest.sense": [("G666_echolocation", 1.0)],
    "guest.grab_warn": [("G666_pursuitpreparewarn", 1.0)],
    "guest.grab_prepare": [("G666_pursuitprepare", 1.0)],
    "guest.grab_lunge": [("G666_pursuitlunge", 1.0)],
    "guest.grab_hit": [("G666_pursuitlanded", 1.0)],
    "guest.grab_miss": [("G666_pursuitcrashed", 1.0)],
    "guest.slam": [("G666_pursuitcutscene", 1.0)],
    "guest.heal": [("G666_bloodorbcollect", 1.0)],
    "guest.arrive": [("G666_bloodhunt", 1.0)],
    "guest.leave": [("G666_bloodhuntend", 1.0)],
    "guest.fever": [("G666_bloodhuntloop", 1.0)],
    "guest.hurt": [("G666_crystart", 1.35)],
    "guest.death": [("G666_bloodhuntend", 0.75)],
}
# Moments the pack has no sound for: the creature recordings already in the mod (Pixabay, credited).
BORROWED = {
    "guest.knock": [("pixabay/creature/doorbang_hit_1", 0.7), ("pixabay/creature/doorbang2_hit_1", 0.7)],
    "guest.tear": [("pixabay/creature/metaldoor_hit_1", 0.75), ("pixabay/creature/metaldoor_hit_2", 0.75)],
}
MUSIC = {"music.guest.layer1": "Guest666_layer1", "music.guest.layer2": "Guest666_layer2",
         "music.guest.layer3": "Guest666_layer3", "music.guest.layer4": "Guest666_chase"}
SUBTITLES = {
    "guest.ambient": "The Guest breathes", "guest.step": "Heavy footsteps", "guest.swing": "Claws swipe", "guest.feed": "Something feeds",
    "guest.kill": "A killing blow", "guest.roar": "The Guest screams", "guest.roar_blast": "A scream shakes the air",
    "guest.sense": "Something senses you", "guest.grab_warn": "The Guest gets ready to grab", "guest.grab_prepare": "The Guest crouches",
    "guest.grab_lunge": "The Guest lunges", "guest.grab_hit": "Slammed down", "guest.grab_miss": "The Guest crashes",
    "guest.slam": "Bones crunch", "guest.heal": "The Guest grows stronger", "guest.arrive": "Something has come for you",
    "guest.leave": "The Guest leaves", "guest.fever": "Searing heat", "guest.knock": "Knocking", "guest.tear": "Something tears through",
    "guest.hurt": "The Guest hurts", "guest.death": "The Guest dies",
}
TEXT = {
    "entity.deadlands.guest": "The Guest",
    "item.deadlands.guest_spawn_egg": "The Guest Spawn Egg",
    "config.deadlands.section.guest": "The Guest",
    "config.deadlands.guest.enabled": "The Guest",
    "config.deadlands.guest.nightOnly": "Night only",
    "config.deadlands.guest.spawnChance": "Spawn chance",
    "config.deadlands.guest.maxAlive": "Most at once",
    "config.deadlands.guest.campMinutes": "Sheltering minutes before it comes",
    "config.deadlands.guest.breaksBlocks": "Tears through walls",
    "config.deadlands.guest.health": "Health",
    "config.deadlands.guest.damage": "Claw damage",
    "config.deadlands.guest.grabDamage": "Grab damage",
    "config.deadlands.guest.speed": "Speed",
    "config.deadlands.guest.chargeSpeed": "Charge speed",
    "config.deadlands.guest.followRange": "Sight range",
    "config.deadlands.guest.armor": "Armor",
    "config.deadlands.survival.infectionChancePerHit": "Infection chance per hit",
}


def encode(name: str, channels: int) -> bool:
    source = SRC / f"{name}.ogg"
    target = OUT / f"{name.lower()}.ogg"
    if not source.exists():
        return target.exists()
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-ar", "44100", "-ac", str(channels),
                    "-c:a", "libvorbis", "-q:a", "6", str(target)], check=True)
    return True


def spawn_egg() -> None:
    icon = np.zeros((16, 16, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:16, 0:16]
    inside = ((xx - 7.5) / 5.2) ** 2 + ((yy - 8.5) / 6.8) ** 2 <= 1.0
    icon[inside] = (20, 8, 8, 255)
    spots = inside & (((xx * 5 + yy * 11) % 7) == 0)
    icon[spots] = (237, 3, 10, 255)
    icon[5, 5] = icon[5, 10] = (217, 4, 9, 255)
    Image.fromarray(icon, "RGBA").save(N.asset("textures/item/guest_spawn_egg.png"))
    D.asset_json("models/item/guest_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/guest_spawn_egg"}})
    D.asset_json("items/guest_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/guest_spawn_egg"}})


def credits() -> None:
    path = N.ROOT / "CREDITS.md"
    text = path.read_text(encoding="utf-8")
    marker = "## The Guest's 3D model"
    if marker in text:
        return
    text = text.rstrip() + f"""

{marker}

The Guest uses **"Sixer aka Guest 666 - Forsaken"** by **Fourteen** ([sketchfab.com/LFourteen_](https://sketchfab.com/LFourteen_)),
licensed under [CC BY 4.0](http://creativecommons.org/licenses/by/4.0/), from
https://sketchfab.com/3d-models/sixer-aka-guest-666-forsaken-8dc1a8bee1eb4ee38f10baf3185be6fc.
Changes: simplified, re-rigged onto DEADLANDS' animation rig, recoloured to a new palette, given a glow layer and
new animations (`tools/assetgen/import_guest.py`).
"""
    path.write_text(text + "\n", encoding="utf-8")


def build() -> None:
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    missing = []
    for event, files in SFX.items():
        entries = []
        for name, pitch in files:
            if encode(name, 1):
                entries.append({"name": f"{NS}:guest/{name.lower()}", **({"pitch": pitch} if pitch != 1.0 else {})})
            else:
                missing.append(name)
        if entries:
            sounds[event] = {"subtitle": f"subtitles.{NS}.{event}", "sounds": entries}
    for event, files in BORROWED.items():
        sounds[event] = {"subtitle": f"subtitles.{NS}.{event}", "sounds": [{"name": f"{NS}:{name}", "pitch": pitch} for name, pitch in files]}
    for event, name in MUSIC.items():
        if encode(name, 2):
            sounds[event] = {"sounds": [{"name": f"{NS}:guest/{name.lower()}", "stream": True}]}
        else:
            missing.append(name)
    sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    text = dict(TEXT)
    text.update({f"subtitles.{NS}.{event}": line for event, line in SUBTITLES.items()})
    D.merge_lang(text)
    spawn_egg()
    credits()
    used = {n for files in SFX.values() for n, _ in files} | set(MUSIC.values())
    unplaced = sorted(p.stem for p in SRC.glob("*.ogg") if p.stem not in used) if SRC.exists() else []
    print(f"  guest sounds: {len(SFX) + len(BORROWED)} events, {len(MUSIC)} music layers; missing {missing}; unplaced {unplaced}")


if __name__ == "__main__":
    build()
