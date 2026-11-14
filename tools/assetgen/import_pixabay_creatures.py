"""Real recordings from Pixabay for every infected, boss and nest sound that was still synthesised
(the owner asked for all of them to be real). Same rules as import_pixabay.py: downloaded once into
tools/assetgen/_cache/pixabay, cut into short takes at the silences (import_zombie_sfx's helpers),
normalised, encoded to mono OGG Vorbis at 44.1 kHz into assets/deadlands/sounds/pixabay/creature/, and
the sound events pointed at them (the synthesised files stay where they are, unused). Variety comes
from several takes per recording and per-event pitch. Muffled and distant voices are the same takes
low-passed (through a wall, far off). Credits are appended to CREDITS.md.
"""
from __future__ import annotations

import json
import subprocess
import urllib.request
from pathlib import Path

import numpy as np

import dl_data as D
import dl_noise as N
from import_zombie_sfx import SR, decode, finish, segments

NS = "deadlands"
CACHE = N.ROOT / "tools" / "assetgen" / "_cache" / "pixabay"
OUT = N.ASSETS / "sounds" / "pixabay" / "creature"
LICENSE = "Pixabay Content License"
CDN = "https://cdn.pixabay.com/download/audio/"

# key: (title, author, page slug, cdn path)
SOURCES = {
    "growl_a": ("Horrific Zombie Growl", "freesound_community", "horror-horrific-zombie-growl-107090", "2022/03/26/audio_eb2a68a9ff.mp3"),
    "growl_b": ("Zombie Growl 3", "freesound_community", "horror-zombie-growl-3-6863", "2021/08/09/audio_49b2c3949f.mp3"),
    "growling": ("growling zombie", "freesound_community", "film-special-effects-growling-zombie-104988", "2022/03/24/audio_de8189270e.mp3"),
    "groan": ("Zombie Groan", "freesound_community", "horror-zombie-groan-95051", "2022/03/17/audio_9f61f2b395.mp3"),
    "zroar": ("zombie roar", "freesound_community", "horror-zombie-roar-104542", "2022/03/24/audio_5a96d28d93.mp3"),
    "zsound2": ("Zombie Sound 2", "DRAGON-STUDIO", "horror-zombie-sound-2-357976", "2025/06/10/audio_badebe5c0a.mp3"),
    "scream": ("Zombie Scream", "DRAGON-STUDIO", "horror-zombie-scream-324752", "2025/04/09/audio_44f25f4eda.mp3"),
    "screech": ("Zombie Screech Sound Effect", "DRAGON-STUDIO", "film-special-effects-zombie-screech-sound-effect-312865", "2025/03/13/audio_c8391fee28.mp3"),
    "mscream": ("Monster Zombie Scream", "freesound_community", "horror-monster-zombie-scream-105972", "2022/03/24/audio_a1685fd39e.mp3"),
    "attack": ("Zombie Attack", "freesound_community", "horror-zombie-attack-6419", "2021/08/04/audio_4edfa01e14.mp3"),
    "bite": ("Zombie Bite", "freesound_community", "film-special-effects-zombie-bite-96528", "2022/03/19/audio_67dc9007ca.mp3"),
    "shuffle": ("Shuffling 1 Front", "freesound_community", "horror-shuffling-1-front-88424", "2022/03/15/audio_e955e906fe.mp3"),
    "heavysteps": ("Monster Heavy Footsteps", "TanwerAman", "horror-monster-heavy-footsteps-321629", "2025/04/01/audio_982a7b230e.mp3"),
    "doorbang": ("Banging On Door", "Universfield", "film-special-effects-banging-on-door-192163", "2024/02/20/audio_a0f78c8fa6.mp3"),
    "doorbang2": ("075787_Banging On Door", "freesound_community", "household-075787-banging-on-door-37849", "2022/03/10/audio_603d6797a5.mp3"),
    "metal": ("Deep metal clang", "freesound_community", "film-special-effects-deep-metal-clang-87901", "2022/03/15/audio_abea92065f.mp3"),
    "metaldoor": ("Banging Metal Door SFX", "u_c2s90p6i67", "film-special-effects-banging-metal-door-sfx-522556", "2026/04/20/audio_42395bebee.mp3"),
    "scratch": ("Cats scratching door", "freesound_community", "nature-cats-scratching-door-98382", "2022/03/20/audio_4fe3753797.mp3"),
    "horde": ("Zombie Horde", "freesound_community", "film-special-effects-zombie-horde-56106", "2022/03/13/audio_3b11783937.mp3"),
    "splat": ("VERY LOUD Splat", "freesound_community", "film-special-effects-very-loud-splat-88998", "2022/03/15/audio_d177ae76c4.mp3"),
    "gush": ("Disgusting Gush", "freesound_community", "film-special-effects-disgusting-gush-36300", "2022/03/10/audio_4eb0b1ad53.mp3"),
    "hiss": ("Alien Hiss and Growl", "freesound_community", "film-special-effects-alien-hiss-and-growl-44024", "2022/03/10/audio_fe21aea70b.mp3"),
    "breath": ("zombie breath", "freesound_community", "horror-zombie-breath-44855", "2022/03/10/audio_ad2ed88405.mp3"),
    "slam": ("powerful ground impact", "KoiRoylers", "film-special-effects-powerful-ground-impact-355967", "2025/06/06/audio_28243d1abe.mp3"),
    "heartbeat": ("Dragon Heartbeat 1", "LazyChillZone", "film-special-effects-dragon-heartbeat-1-232449", "2024/08/14/audio_9b93aeae1f.mp3"),
    "dig": ("Hand digging dirt, leaves crunch", "freesound_community", "nature-hand-digging-dirt-leaves-crunch-32630", "2022/03/10/audio_6a97ddb488.mp3"),
    "lowroar": ("Low Monster Roar", "freesound_community", "horror-low-monster-roar-97413", "2022/03/20/audio_46f4ce3734.mp3"),
    "roar02": ("Monster Roar 02", "freesound_community", "horror-monster-roar-02-102957", "2022/03/24/audio_28c7350ac5.mp3"),
    "deeproar": ("Deep Sea Monster Roar", "DRAGON-STUDIO", "horror-deep-sea-monster-roar-329857", "2025/04/19/audio_cf96a02a02.mp3"),
    "warriorroar": ("Monster Warrior Roar", "dffdv", "horror-monster-warrior-roar-195877", "2024/03/13/audio_6b64e6b8a9.mp3"),
    "demondeath": ("Monster Demon Voice - Death / Defeat Scream", "PhatPhrogStudio", "film-special-effects-monster-demon-voice-death-defeat-scream-582531",
                   "2026/08/17/audio_bf616e9d11.mp3"),
    "deathgrunt": ("monster death grunt", "666HeroHero", "horror-monster-death-grunt-131480", "2022/12/29/audio_fd4f5e0575.mp3"),
    "deepgrowl": ("Super Deep Growl", "freesound_community", "film-special-effects-super-deep-growl-86749", "2022/03/15/audio_df62d8a295.mp3"),
    "squelch": ("Wet Squelch", "Universfield", "film-special-effects-wet-squelch-276679", "2024/12/15/audio_86aec4da89.mp3"),
}

# How long a take may be (seconds), by kind of sound.
LENGTHS = {"voice": (0.5, 3.0), "long": (1.0, 5.0), "hit": (0.15, 1.5), "step": (0.12, 0.7), "loop": (1.5, 4.0)}
MAX_TAKES = 4

# event: (pitch, [(source, kind, filter)])  filter: None, "muffled" (through a wall) or "distant".
EVENTS = {
    "infected.walker.alert": (1.0, [("growl_a", "voice", None), ("groan", "voice", None)]),
    "infected.walker.chase": (1.0, [("zsound2", "voice", None), ("growling", "voice", None)]),
    "infected.runner.alert": (1.1, [("screech", "voice", None), ("mscream", "voice", None)]),
    "infected.runner.chase": (1.1, [("mscream", "voice", None), ("scream", "voice", None)]),
    "infected.brute.alert": (0.8, [("lowroar", "long", None)]),
    "infected.brute.chase": (0.8, [("roar02", "voice", None), ("zroar", "voice", None)]),
    "infected.brute.roar": (0.75, [("warriorroar", "long", None), ("lowroar", "long", None)]),
    "infected.brute.step": (0.9, [("heavysteps", "step", None)]),
    "infected.brute.slam": (1.0, [("slam", "hit", None)]),
    "infected.screamer.alert": (1.05, [("scream", "voice", None)]),
    "infected.screamer.chase": (1.05, [("screech", "voice", None)]),
    "infected.screamer.scream": (1.0, [("scream", "long", None), ("screech", "long", None)]),
    "infected.military.alert": (0.95, [("growl_b", "voice", None)]),
    "infected.military.chase": (0.95, [("growling", "voice", None), ("zsound2", "voice", None)]),
    "infected.crawler.alert": (1.15, [("groan", "voice", None)]),
    "infected.crawler.chase": (1.15, [("growl_b", "voice", None), ("breath", "voice", None)]),
    "infected.bloater.alert": (0.85, [("deepgrowl", "voice", None)]),
    "infected.bloater.chase": (0.85, [("deepgrowl", "voice", None), ("zsound2", "voice", None)]),
    "infected.bloater.burst": (1.0, [("splat", "hit", None), ("gush", "hit", None)]),
    "infected.stalker.alert": (1.0, [("hiss", "voice", None)]),
    "infected.stalker.chase": (1.1, [("hiss", "voice", None)]),
    "infected.stalker.pounce": (1.2, [("hiss", "hit", None)]),
    "infected.charred.alert": (0.9, [("growl_a", "voice", None)]),
    "infected.charred.chase": (0.9, [("zroar", "voice", None), ("growling", "voice", None)]),
    "infected.breath": (1.0, [("breath", "voice", None)]),
    "infected.scratch": (1.0, [("scratch", "voice", None)]),
    "infected.bash_wood": (1.0, [("doorbang", "hit", None), ("doorbang2", "hit", None)]),
    "infected.bash_metal": (1.0, [("metal", "hit", None), ("metaldoor", "hit", None)]),
    "infected.step": (1.0, [("shuffle", "step", None)]),
    "infected.attack": (1.0, [("attack", "voice", None), ("bite", "hit", None)]),
    "infected.distant": (0.9, [("horde", "loop", "distant")]),
    "infected.muffled": (0.95, [("growl_a", "voice", "muffled"), ("groan", "voice", "muffled"), ("growl_b", "voice", "muffled")]),
    "infected.emerge": (1.0, [("dig", "voice", None)]),
    "boss.abomination.roar": (0.8, [("deeproar", "long", None)]),
    "boss.abomination.attack": (0.85, [("warriorroar", "voice", None)]),
    "boss.abomination.hurt": (0.8, [("deepgrowl", "voice", None)]),
    "boss.abomination.death": (0.75, [("demondeath", "long", None)]),
    "boss.butcher.roar": (0.9, [("zroar", "long", None)]),
    "boss.butcher.attack": (0.95, [("attack", "voice", None)]),
    "boss.butcher.hurt": (0.9, [("growling", "voice", None)]),
    "boss.butcher.death": (0.9, [("deathgrunt", "long", None)]),
    "boss.warden.roar": (0.85, [("roar02", "long", None)]),
    "boss.warden.attack": (1.0, [("slam", "hit", None)]),
    "boss.warden.hurt": (0.85, [("growl_b", "voice", None)]),
    "boss.warden.death": (0.8, [("deathgrunt", "long", None)]),
    "boss.experiment.roar": (1.1, [("screech", "long", None)]),
    "boss.experiment.attack": (1.15, [("hiss", "voice", None)]),
    "boss.experiment.hurt": (1.1, [("mscream", "voice", None)]),
    "boss.experiment.death": (1.05, [("demondeath", "long", None)]),
    "boss.patient_zero.roar": (0.7, [("deeproar", "long", None)]),
    "boss.patient_zero.attack": (0.8, [("warriorroar", "voice", None)]),
    "boss.patient_zero.hurt": (0.75, [("lowroar", "voice", None)]),
    "boss.patient_zero.death": (0.65, [("demondeath", "long", None)]),
    "nest.pulse": (0.9, [("heartbeat", "long", None)]),
    "nest.release": (0.9, [("gush", "voice", None)]),
    "nest.destroyed": (0.8, [("splat", "hit", None)]),
    "nest.absorb": (0.9, [("squelch", "hit", None)]),
}


def fetch(key: str) -> Path | None:
    CACHE.mkdir(parents=True, exist_ok=True)
    out = CACHE / f"creature_{key}.mp3"
    if out.exists() and out.stat().st_size > 0:
        return out
    title, author, slug, path = SOURCES[key]
    request = urllib.request.Request(CDN + path, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://pixabay.com/"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            out.write_bytes(response.read())
    except OSError as failure:
        print(f"    could not download {key}: {failure}")
        return None
    return out


def takes(x: np.ndarray, kind: str) -> list[np.ndarray]:
    lo, hi = LENGTHS[kind]
    out = []
    for a, b in segments(x, gap=0.12 if kind in ("step", "hit") else 0.25):
        pos = a
        while (b - pos) / SR >= lo and len(out) < MAX_TAKES:
            take = min(b - pos, int(hi * SR))
            out.append(x[pos:pos + take])
            pos += take
        if len(out) >= MAX_TAKES:
            break
    if not out:
        a, b = max(segments(x), key=lambda s: s[1] - s[0])
        out.append(x[a:min(len(x), a + int(hi * SR))])
    return out


def encode(clip: np.ndarray, path: Path, effect: str | None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(clip, -1, 1) * 32767).astype(np.int16).tobytes()
    command = ["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-"]
    if effect == "muffled":
        command += ["-af", "lowpass=f=700,volume=0.8"]
    elif effect == "distant":
        command += ["-af", "lowpass=f=1200,aecho=0.8:0.6:260:0.35,volume=0.7"]
    command += ["-c:a", "libvorbis", "-q:a", "5", "-ar", str(SR), "-ac", "1", str(path)]
    subprocess.run(command, input=pcm, check=True)


def credits(used: set[str]) -> None:
    path = N.ROOT / "CREDITS.md"
    text = path.read_text(encoding="utf-8") if path.exists() else "# Credits\n"
    marker = "## Creature sounds from Pixabay"
    if marker in text:
        text = text[:text.index(marker)].rstrip() + "\n"
    lines = ["", marker, "",
             "The infected, boss and nest sounds come from these Pixabay recordings, cut into short takes, normalised and",
             "converted to OGG Vorbis (`tools/assetgen/import_pixabay_creatures.py`), under the",
             "[Pixabay Content License](https://pixabay.com/service/license-summary/).", "",
             "| Title | Author | Page | License |", "|---|---|---|---|"]
    for key in sorted(used):
        title, author, slug, _ = SOURCES[key]
        lines.append(f"| {title} | {author} | https://pixabay.com/sound-effects/{slug}/ | {LICENSE} |")
    path.write_text(text + "\n".join(lines) + "\n", encoding="utf-8")


def build() -> None:
    audio: dict[str, np.ndarray] = {}
    for key in SOURCES:
        source = fetch(key)
        if source is not None:
            audio[key] = decode(source)
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    made: dict[tuple[str, str, str | None], list[str]] = {}
    used: set[str] = set()
    for event, (pitch, parts) in EVENTS.items():
        names = []
        for key, kind, effect in parts:
            if key not in audio:
                continue
            spec = (key, kind, effect)
            if spec not in made:
                files = []
                for i, clip in enumerate(takes(audio[key], kind)):
                    name = f"{key}_{kind}{'_' + effect if effect else ''}_{i + 1}"
                    encode(finish(clip), OUT / f"{name}.ogg", effect)
                    files.append(name)
                made[spec] = files
            names += made[spec]
            used.add(key)
        if not names:
            continue
        current = sounds.get(event, {})
        entries = [{"name": f"{NS}:pixabay/creature/{n}", **({"pitch": pitch} if pitch != 1.0 else {})} for n in names]
        sounds[event] = {**({"subtitle": current["subtitle"]} if "subtitle" in current else {}), "sounds": entries}
    sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    credits(used)
    files = sum(len(v) for v in made.values())
    print(f"  pixabay creatures: {len(used)} recordings, {files} takes, {len(EVENTS)} events")


if __name__ == "__main__":
    build()
