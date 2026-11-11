"""Imports the third-party horror sounds the project owner chose (Phase 2).

Source: the "Horror/Thriller Sounds" playlist by the YouTube channel "Sound Library"
(https://www.youtube.com/playlist?list=PL_2MB6_9kLAGMaFV1Vt4GE0v9dogNRf74), downloaded with
yt-dlp. The channel's terms: free to use, credit the original creator if known (the uploader says
most creators are unknown; royalty free or Creative Commons). Credited in docs/ASSET_MANIFEST.md.

The laugh and clown clips (08, 09, 10, 11, 24, 28) are not used at the owner's request, nor 27
("I'm Burning", a spoken line that does not fit).

Each clip is cut to its sound (segments measured from the waveform), faded, made mono when it is
heard at a place in the world (so it fades and pans with distance), normalised, and encoded as OGG
Vorbis like every other DEADLANDS sound. Loops get a crossfade. Sound events are added to
sounds.json.

Run: python tools/assetgen/import_sfx.py [source folder]
(default: the DEADLANDS_SFX_SOURCE environment variable, else ~/Downloads/Horror-Thriller Sounds).
Inside build_all it is skipped when the source folder is missing: the encoded files are committed.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

import dl_data as D

NS = D.NS
SR = 44100

# number prefix -> list of (output name, start s, end s, mono, peak, loop)
CLIPS = {
    # Music: chase (two variants, looped) and exploration (two pieces).
    "21": [("music/chase_strings", 3.8, 39.4, False, 0.80, True)],
    "25": [("music/chase_strikes", 0.1, 30.2, False, 0.80, True)],
    "07": [("music/explore_ambience", 0.0, 126.8, False, 0.55, False)],
    "14": [("music/explore_low_tone", 1.4, 55.5, False, 0.55, False)],
    # Night: the creature in the dark (one long rattle, three short calls).
    "22": [("horror/night_creature_long", 0.2, 5.9, True, 0.90, False),
           ("horror/night_creature_call1", 8.1, 9.0, True, 0.90, False),
           ("horror/night_creature_call2", 10.1, 10.9, True, 0.90, False),
           ("horror/night_creature_call3", 12.1, 13.0, True, 0.90, False)],
    # Dread beds: quiet swells of unease, heard everywhere at once.
    "06": [("horror/dread_pressure", 3.9, 38.5, False, 0.60, False)],
    "15": [("horror/dread_bass", 0.0, 18.5, False, 0.60, False)],
    "16": [("horror/dread_suspense", 0.0, 13.3, False, 0.55, False)],
    "23": [("horror/water_creak", 0.0, 21.9, False, 0.55, False)],
    # Unsettling moments.
    "13": [("horror/whisper1", 2.9, 7.4, True, 0.85, False),
           ("horror/whisper2", 8.0, 12.6, True, 0.85, False),
           ("horror/whisper3", 18.8, 24.2, True, 0.85, False),
           ("horror/whisper4", 25.1, 30.0, True, 0.85, False)],
    "03": [("horror/door_creak", 0.6, 2.4, True, 0.90, False)],
    "18": [("horror/distant_horn", 0.0, 2.7, True, 0.90, False)],
    "17": [("horror/sting_forest", 0.0, 2.7, False, 0.80, False)],
    # Scare stingers: anticipation and payoff.
    "02": [("horror/riser_flare", 0.4, 7.9, False, 0.90, False)],
    "12": [("horror/stinger_fright", 0.1, 7.2, False, 0.90, False)],
    "19": [("horror/stinger_hit", 0.0, 1.5, False, 0.90, False)],
    "20": [("horror/swell", 0.2, 3.1, False, 0.85, False)],
    "05": [("horror/whoosh", 1.3, 5.6, False, 0.85, False)],
    "04": [("horror/suspense", 1.3, 7.4, False, 0.75, False)],
    "26": [("horror/scream", 0.1, 3.1, True, 0.90, False)],
    "01": [("horror/jumpscare_chase", 0.4, 17.0, False, 0.90, False)],
}

# Sound events: name -> (files, stream, subtitle key or None)
EVENTS = {
    "music.chase": (["music/chase_strings", "music/chase_strikes"], True, None),
    "music.explore": (["music/explore_ambience", "music/explore_low_tone"], True, None),
    "horror.night_creature": (["horror/night_creature_long", "horror/night_creature_call1", "horror/night_creature_call2",
                               "horror/night_creature_call3"], False, "subtitles.deadlands.horror.night_creature"),
    "horror.dread": (["horror/dread_pressure", "horror/dread_bass", "horror/dread_suspense"], True, None),
    "horror.water_creak": (["horror/water_creak"], True, "subtitles.deadlands.horror.water_creak"),
    "horror.whisper": (["horror/whisper1", "horror/whisper2", "horror/whisper3", "horror/whisper4"], False, "subtitles.deadlands.horror.whisper"),
    "horror.door_creak": (["horror/door_creak"], False, "subtitles.deadlands.horror.door_creak"),
    "horror.distant_horn": (["horror/distant_horn"], False, "subtitles.deadlands.horror.distant_horn"),
    "horror.sting_forest": (["horror/sting_forest"], False, None),
    "horror.riser": (["horror/riser_flare"], False, None),
    "horror.stinger": (["horror/stinger_fright", "horror/stinger_hit"], False, None),
    "horror.swell": (["horror/swell"], False, None),
    "horror.whoosh": (["horror/whoosh"], False, "subtitles.deadlands.horror.whoosh"),
    "horror.suspense": (["horror/suspense"], False, None),
    "horror.scream": (["horror/scream"], False, "subtitles.deadlands.horror.scream"),
    "horror.jumpscare": (["horror/jumpscare_chase"], False, None),
}

SUBTITLES = {
    "subtitles.deadlands.horror.night_creature": "Something rattles in the dark",
    "subtitles.deadlands.horror.water_creak": "Old timbers creak",
    "subtitles.deadlands.horror.whisper": "Whispering",
    "subtitles.deadlands.horror.door_creak": "A door creaks",
    "subtitles.deadlands.horror.distant_horn": "A distant horn",
    "subtitles.deadlands.horror.whoosh": "Something rushes past",
    "subtitles.deadlands.horror.scream": "A scream",
}


def source_folder(argv: list[str]) -> Path:
    if argv:
        return Path(argv[0])
    env = os.environ.get("DEADLANDS_SFX_SOURCE")
    return Path(env) if env else Path.home() / "Downloads" / "Horror-Thriller Sounds"


def decode(path: Path) -> np.ndarray:
    import shutil
    ffmpeg = shutil.which("ffmpeg")
    raw = subprocess.run([ffmpeg, "-nostdin", "-loglevel", "error", "-i", str(path), "-vn", "-ac", "2", "-ar", str(SR),
                          "-f", "f32le", "-"], check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64)


def cut(audio: np.ndarray, start: float, end: float, mono: bool, peak: float, loop: bool) -> np.ndarray:
    a = audio[int(start * SR):int(end * SR)].copy()
    if mono:
        a = a.mean(axis=1, keepdims=True)
    if loop:
        n = int(1.5 * SR)
        head, tail = a[:n].copy(), a[-n:].copy()
        ramp = np.linspace(0.0, 1.0, n)[:, None]
        a = a[:-n]
        a[:n] = head * ramp + tail * (1.0 - ramp)
    else:
        fade_in, fade_out = int(0.01 * SR), int(min(0.25, (end - start) / 4) * SR)
        a[:fade_in] *= np.linspace(0.0, 1.0, fade_in)[:, None]
        a[-fade_out:] *= np.linspace(1.0, 0.0, fade_out)[:, None]
    a = a - a.mean(axis=0)
    return a / (np.max(np.abs(a)) + 1e-9) * peak


def build(argv: list[str] | None = None) -> int:
    from gen_audio import OUT, write_ogg

    folder = source_folder(argv or [])
    if not folder.is_dir():
        print(f"import_sfx: {folder} not found, keeping the committed sounds")
        return 0
    files = {p.name.split(" - ", 1)[0]: p for p in folder.iterdir() if p.is_file()}
    written = 0
    for number, outputs in CLIPS.items():
        if number not in files:
            raise SystemExit(f"import_sfx: clip {number} missing from {folder}")
        audio = decode(files[number])
        for name, start, end, mono, peak, loop in outputs:
            data = cut(audio, start, end, mono, peak, loop)
            write_ogg(name, data[:, 0] if mono else data, quality=4)
            written += 1
    path = OUT.parent / "sounds.json"
    table = json.loads(path.read_text(encoding="utf-8"))
    for event, (names, stream, subtitle) in EVENTS.items():
        entry = {"sounds": [{"name": f"{NS}:{n}", "stream": True} if stream else f"{NS}:{n}" for n in names]}
        if subtitle:
            entry["subtitle"] = subtitle
        table[event] = entry
    path.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang(SUBTITLES)
    print(f"import_sfx: {written} sounds from {len(CLIPS)} clips, {len(EVENTS)} sound events")
    return written


if __name__ == "__main__":
    build(sys.argv[1:])
