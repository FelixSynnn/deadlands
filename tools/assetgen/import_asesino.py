"""The Asesino (Phase 5): the owner's skin and sounds, the glowing eyes, the spawn egg and the text.

Sources (the owner's own files; skipped when missing, keeping what is already in the mod):
  ~/Downloads/79daac2ae58bf441.png   the skin (64 x 64)
  ~/Downloads/Serial/*.ogg           his sounds and the four chase music layers

Every sound is re-encoded to OGG Vorbis at 44.1 kHz: mono for his sounds (heard where he is), stereo
for the music. The music layers stay in step: layer 1 (5.5 s) loops twice per layers 2 and 3 (11.03 s);
Chase_slasher (52.4 s, used as layer 4) is cut to four of their bars (44.14 s) with a short fade so its
loop lines up with theirs.

Events without a file of their own reuse the files no ability uses (the owner's choice): see REUSE.
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
SRC = Path.home() / "Downloads" / "Serial"
SKIN = Path.home() / "Downloads" / "79daac2ae58bf441.png"
OUT = N.ASSETS / "sounds" / "asesino"
BAR = 11.034376  # layers 2 and 3

# event: [(file, channels, keep seconds or None)], pitch/volume live in sounds.json entries.
SOUNDS = {
    "asesino.step": [("Slash_footstep01", 1), ("Slash_footstep02", 1)],
    "asesino.swing": [("Slash_swing", 1)],
    "asesino.hit": [("Slash_hit", 1)],
    "asesino.execution": [("Slash_execution", 1)],
    "asesino.pounce": [("Slash_gashwoundswing", 1)],
    "asesino.pounce_hit": [("Slasher_gashwoundhit", 1)],
    "asesino.pounce_miss": [("Slash_gashwoundmiss", 1)],
    "asesino.stun": [("Slash_stun", 1)],
    "asesino.frenzy": [("Slash_raging", 1)],
}
# Events with no file of their own: the files no ability uses, with a different pitch so they read apart.
REUSE = {
    "asesino.ambient": ("Slash_execution", 0.7, 0.6),
    "asesino.hurt": ("Slash_hit", 1.25, 1.0),
    "asesino.death": ("Slash_execution", 0.6, 1.0),
    "asesino.chase_start": ("Slash_execution", 1.0, 1.0),
    "asesino.breach": ("Slash_hit", 0.75, 1.0),
    "asesino.scent": ("Slash_swing", 0.6, 0.8),
    "asesino.shriek": ("Slash_execution", 0.85, 1.0),
}
MUSIC = {"music.asesino.layer1": "Layer_1_slasher", "music.asesino.layer2": "Layer_2_slasher",
         "music.asesino.layer3": "Layer_3_slasher", "music.asesino.layer4": "Chase_slasher"}
SUBTITLES = {
    "asesino.ambient": "Asesino breathes", "asesino.hurt": "Asesino hurts", "asesino.death": "Asesino dies",
    "asesino.step": "Heavy footsteps", "asesino.swing": "Blade swings", "asesino.hit": "Blade cuts",
    "asesino.execution": "A killing blow", "asesino.chase_start": "Asesino gives chase", "asesino.pounce": "Asesino leaps",
    "asesino.pounce_hit": "Asesino slashes", "asesino.pounce_miss": "Asesino misses", "asesino.stun": "Asesino reels",
    "asesino.breach": "Something smashes through", "asesino.scent": "You feel watched", "asesino.frenzy": "Asesino rages",
    "asesino.shriek": "Asesino shrieks",
}
TEXT = {
    "entity.deadlands.asesino": "Asesino",
    "item.deadlands.asesino_spawn_egg": "Asesino Spawn Egg",
    "entity.deadlands.asesino.watched": "You feel watched.",
    "config.deadlands.section.asesino": "Asesino",
    "config.deadlands.asesino.enabled": "Asesino",
    "config.deadlands.asesino.nightOnly": "Night only",
    "config.deadlands.asesino.townSpawnChance": "Town spawn chance",
    "config.deadlands.asesino.wildSpawnChance": "Wild spawn chance",
    "config.deadlands.asesino.areaRadius": "One per area (blocks)",
    "config.deadlands.asesino.maxAlive": "Most at once",
    "config.deadlands.asesino.health": "Health",
    "config.deadlands.asesino.damage": "Damage",
    "config.deadlands.asesino.stalkSpeed": "Stalking speed",
    "config.deadlands.asesino.chaseSpeedMax": "Top chase speed",
    "config.deadlands.asesino.followRange": "Follow range",
    "config.deadlands.asesino.knockbackResistance": "Knockback resistance",
    "config.deadlands.asesino.armor": "Armor",
    "config.deadlands.audio.asesinoMusic": "Asesino chase music",
    "config.deadlands.audio.asesinoLayer1": "Asesino music: layer 1 range",
    "config.deadlands.audio.asesinoLayer2": "Asesino music: layer 2 range",
    "config.deadlands.audio.asesinoLayer3": "Asesino music: layer 3 range",
}


def encode(name: str, channels: int, keep: float | None = None, fade: float = 0.0) -> bool:
    source = SRC / f"{name}.ogg"
    if not source.exists():
        print(f"    missing {source.name}; kept what the mod has")
        return (OUT / f"{name.lower()}.ogg").exists()
    OUT.mkdir(parents=True, exist_ok=True)
    command = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(source)]
    if keep:
        command += ["-af", f"atrim=0:{keep},afade=t=out:st={keep - fade}:d={fade}"]
    command += ["-ar", "44100", "-ac", str(channels), "-c:a", "libvorbis", "-q:a", "6", str(OUT / f"{name.lower()}.ogg")]
    subprocess.run(command, check=True)
    return True


def skin() -> None:
    out = N.asset("textures/entity/asesino/asesino.png")
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    if SKIN.exists():
        image = Image.open(SKIN).convert("RGBA")
        assert image.size == (64, 64), "the Asesino skin must be 64 x 64"
        image.save(out)
    # The eyes: red, on the face's eye holes (base layer and hat layer), everything else clear.
    eyes = np.zeros((64, 64, 4), dtype=np.uint8)
    for x, y in ((10, 10), (10, 11), (13, 11), (14, 11), (13, 10), (14, 10)):
        for dx in (0, 32):
            eyes[y, x + dx] = (255, 28, 18, 255)
    Image.fromarray(eyes, "RGBA").save(N.asset("textures/entity/asesino/asesino_eyes.png"))


def spawn_egg() -> None:
    icon = np.zeros((16, 16, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:16, 0:16]
    inside = ((xx - 7.5) / 5.2) ** 2 + ((yy - 8.5) / 6.8) ** 2 <= 1.0
    icon[inside] = (40, 40, 44, 255)
    spots = inside & (((xx * 7 + yy * 13) % 9) == 0)
    icon[spots] = (150, 20, 16, 255)
    icon[6, 6] = icon[6, 9] = (255, 40, 30, 255)
    Image.fromarray(icon, "RGBA").save(N.asset("textures/item/asesino_spawn_egg.png"))
    D.asset_json("models/item/asesino_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/asesino_spawn_egg"}})
    D.asset_json("items/asesino_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/asesino_spawn_egg"}})


def build() -> None:
    skin()
    spawn_egg()
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    for event, files in SOUNDS.items():
        entries = [{"name": f"{NS}:asesino/{name.lower()}"} for name, channels in files if encode(name, channels)]
        if entries:
            sounds[event] = {"subtitle": f"subtitles.{NS}.{event}", "sounds": entries}
    for event, (name, pitch, volume) in REUSE.items():
        if (OUT / f"{name.lower()}.ogg").exists() or encode(name, 1):
            sounds[event] = {"subtitle": f"subtitles.{NS}.{event}", "sounds": [{"name": f"{NS}:asesino/{name.lower()}", "pitch": pitch, "volume": volume}]}
    for event, name in MUSIC.items():
        keep = 4 * BAR if name == "Chase_slasher" else None
        if encode(name, 2, keep, 0.05 if keep else 0.0):
            sounds[event] = {"sounds": [{"name": f"{NS}:asesino/{name.lower()}", "stream": True}]}
    sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    text = dict(TEXT)
    text.update({f"subtitles.{NS}.{event}": line for event, line in SUBTITLES.items()})
    D.merge_lang(text)
    print(f"  asesino: skin, eyes, spawn egg, {len(SOUNDS) + len(REUSE)} sound events, {len(MUSIC)} music layers")


if __name__ == "__main__":
    build()
