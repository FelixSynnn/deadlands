"""Phase 4: the infected's new voices, from sound files the project owner supplied (2026-11-12).

Each source mp3 (in ~/Downloads; skipped if missing) is decoded to mono 44.1 kHz, split at its
silences into separate vocalisations, cut to the length its sound type needs (ambient 2 to 5 s,
hurt 0.4 to 1.2 s, death 1.5 to 3 s), given short fades and written as OGG Vorbis. New files go to
`sounds/infected/sfx/` and `sounds/infected/stalker_sfx/` (nothing existing is overwritten). The
`sounds.json` entries of the existing `infected.<kind>.ambient/hurt/death` events (registered in
DLSounds) are pointed at the new pools:

- every infected kind except the stalker shares one pool per sound type (several sounds per event);
- the stalker gets only the "demon spirit" files: whispers and muttering plus the sinister laughter
  while it lurks, bursts of the angry chanting when hurt, the chanting's last swell when it dies.

The five bosses keep their own sounds.
"""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

import numpy as np

import dl_noise as N

SRC = Path.home() / "Downloads"
SR = 44100
SOUNDS = N.ASSETS / "sounds"
SOUNDS_JSON = N.ASSETS / "sounds.json"

# source file -> short name
FILES = {
    "freesound_community-zombie-moan-44932.mp3": "zombie_moan",
    "freesound_community-zombie-breathing-70682.mp3": "zombie_breathing",
    "vinodadora-restrained-zombie-128358.mp3": "restrained_zombie",
    "dragon-studio-zombie-call-357977.mp3": "zombie_call",
    "phatphrogstudio-zombie-voice-growl-477834.mp3": "zombie_voice_growl",
    "dragon-studio-zombie-sound-357975.mp3": "zombie_sound",
    "dragon-studio-zombie-sound-2-357976.mp3": "zombie_sound_2",
    "dragon-studio-zombie-sfx-450450.mp3": "zombie_sfx",
    "freesound_community-zombie-horde-87204.mp3": "zombie_horde",
    "dragon-studio-zombie-yell-and-shout-sound-effect-322964.mp3": "zombie_yell_and_shout",
    "dragon-studio-zombie-screech-sound-effect-312865.mp3": "zombie_screech",
    "dragon-studio-zombie-scream-324752.mp3": "zombie_scream",
    "dragon-studio-female-zombie-screams-324744.mp3": "female_zombie_screams",
    "dragon-studio-zombie-dying-sound-357974.mp3": "zombie_dying_sound",
    "freesound_community-zombie-flesh-bites-76826.mp3": "zombie_flesh_bites",
    "phatphrogstudio-demon-spirit-voice-ghost-whispers-amp-muttering-496706.mp3": "demon_spirit_whispers",
    "phatphrogstudio-demonic-spirit-voice-sinister-laughter-487002.mp3": "demon_spirit_laughter",
    "phatphrogstudio-demon-spirit-angry-chanting-no-ai-479754.mp3": "demon_spirit_chanting",
}

LENGTHS = {"ambient": (2.0, 5.0), "hurt": (0.4, 1.2), "death": (1.5, 3.0)}
MAX_CUTS = 4  # per source and sound type

ZOMBIE = {
    "ambient": ["zombie_moan", "zombie_breathing", "restrained_zombie", "zombie_call", "zombie_voice_growl", "zombie_sound",
                "zombie_sound_2", "zombie_sfx", "zombie_horde"],
    "hurt": ["zombie_yell_and_shout", "zombie_screech", "zombie_scream", "female_zombie_screams", "zombie_voice_growl"],
    "death": ["zombie_dying_sound", "zombie_scream", "zombie_flesh_bites"],
}
STALKER = {
    "ambient": ["demon_spirit_whispers", "demon_spirit_laughter"],
    "hurt": ["demon_spirit_chanting"],
    "death": ["demon_spirit_chanting:end"],
}
# event -> (file name prefix, subtitle key)
EXTRA = {
    "horror.breath": ("zombie_breathing_ambient", "subtitles.deadlands.horror.breath"),
    "horror.stalker_chant": ("demon_spirit_chanting_hurt", "subtitles.deadlands.horror.stalker_chant"),
    "horror.stalker_laugh": ("demon_spirit_laughter_ambient", "subtitles.deadlands.horror.stalker_laugh"),
    "infected.feeding": ("zombie_flesh_bites_death", "subtitles.deadlands.infected.feeding"),
    "infected.feeding_screech": ("zombie_screech_hurt", "subtitles.deadlands.infected.feeding_screech"),
}
ZOMBIE_KINDS = ["walker", "runner", "brute", "screamer", "military", "crawler", "bloater", "charred"]


def decode(path: Path) -> np.ndarray:
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0


def segments(x: np.ndarray, gap: float = 0.25) -> list[tuple[int, int]]:
    """Stretches of sound between silences (20 ms windows, 35 dB under the loudest)."""
    win = SR // 50
    n = len(x) // win
    rms = np.sqrt(np.mean(x[:n * win].reshape(n, win) ** 2, axis=1) + 1e-12)
    loud = rms > rms.max() * 10 ** (-35 / 20)
    out, start, quiet = [], None, 0
    for i, on in enumerate(loud):
        if on:
            start = i if start is None else start
            quiet = 0
        elif start is not None:
            quiet += 1
            if quiet * win / SR >= gap:
                out.append((start * win, (i - quiet + 1) * win))
                start, quiet = None, 0
    if start is not None:
        out.append((start * win, n * win))
    return out


def cuts(x: np.ndarray, kind: str, from_end: bool = False) -> list[np.ndarray]:
    lo, hi = LENGTHS[kind]
    if from_end:  # the last swell: the final stretch of sound, up to the longest allowed
        a, b = segments(x)[-1]
        return [x[max(a, b - int(hi * SR)):b]]
    out = []
    for a, b in segments(x):
        length = (b - a) / SR
        if length < lo:
            continue
        pos = a
        while (b - pos) / SR >= lo and len(out) < MAX_CUTS:
            take = min(b - pos, int(hi * SR))
            out.append(x[pos:pos + take])
            pos += take
        if len(out) >= MAX_CUTS:
            break
    if not out:  # nothing long enough: the loudest stretch, padded to the minimum
        a, b = max(segments(x), key=lambda s: s[1] - s[0])
        out.append(x[a:min(len(x), max(b, a + int(lo * SR)))][:int(hi * SR)])
    return out


def finish(clip: np.ndarray) -> np.ndarray:
    fade_in, fade_out = int(0.015 * SR), int(0.06 * SR)
    clip = clip.copy()
    clip[:fade_in] *= np.linspace(0, 1, min(fade_in, len(clip)))[:len(clip[:fade_in])]
    clip[-fade_out:] *= np.linspace(1, 0, min(fade_out, len(clip)))[-len(clip[-fade_out:]):]
    peak = float(np.abs(clip).max()) or 1.0
    return clip * (0.89 / peak)  # about -1 dBFS peak


def encode(clip: np.ndarray, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(clip, -1, 1) * 32767).astype(np.int16).tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "5", "-ar", str(SR), "-ac", "1", str(path)], input=pcm, check=True)


def pool(spec: dict[str, list[str]], folder: str, audio: dict[str, np.ndarray]) -> dict[str, list[str]]:
    events = {}
    for kind, sources in spec.items():
        names = []
        for source in sources:
            short, _, mode = source.partition(":")
            for i, clip in enumerate(cuts(audio[short], kind, from_end=mode == "end")):
                name = f"{short}_{kind}_{i + 1}"
                encode(finish(clip), SOUNDS / "infected" / folder / f"{name}.ogg")
                names.append(f"deadlands:infected/{folder}/{name}")
        events[kind] = names
    return events


def build() -> None:
    missing = [f for f in FILES if not (SRC / f).exists()]
    if missing:
        print(f"  zombie sfx: skipped, {len(missing)} source files missing in {SRC}")
        return
    audio = {short: decode(SRC / f) for f, short in FILES.items()}
    zombie = pool(ZOMBIE, "sfx", audio)
    stalker = pool(STALKER, "stalker_sfx", audio)
    sounds = json.loads(SOUNDS_JSON.read_text(encoding="utf-8"))
    for kind in ZOMBIE_KINDS:
        for t, names in zombie.items():
            key = f"infected.{kind}.{t}"
            if key in sounds:  # military and charred use the walker's hurt and death events, the bloater the crawler's death
                sounds[key]["sounds"] = names
    for t, names in stalker.items():
        sounds[f"infected.stalker.{t}"]["sounds"] = names
    # Phase 4 jumpscare and feeding events, drawn from the same cuts.
    every = [n for names in list(zombie.values()) + list(stalker.values()) for n in names]
    for event, (prefix, subtitle) in EXTRA.items():
        sounds[event] = {"sounds": sorted({n for n in every if n.split("/")[-1].startswith(prefix)}), "subtitle": subtitle}
    SOUNDS_JSON.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")
    print("  zombie sfx: " + ", ".join(f"{t} {len(n)}" for t, n in zombie.items()) + "; stalker: "
          + ", ".join(f"{t} {len(n)}" for t, n in stalker.items()))


if __name__ == "__main__":
    build()
