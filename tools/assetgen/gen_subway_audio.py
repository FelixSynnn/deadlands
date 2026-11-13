"""The subway's public announcements (world/subway/SubwayAmbience): old station loudspeakers.

Each line is spoken by a Windows speech voice (System.Speech, offline), then made into a dying PA system with
ffmpeg: telephone band (300-3200 Hz), overdriven, a bit-crushed edge, a long concrete echo, mains hum and
static under it, and dropouts. Some lines cut off mid-sentence. Three more files are a broken speaker: static
and hum only. All mono OGG (they come from a place: a speaker), in sounds/subway/. Also the sound events and
their subtitles. Windows only (the speech voices); skipped elsewhere, keeping what is there.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import dl_data as D
import dl_noise as N

NS = "deadlands"
OUT = N.ASSETS / "sounds" / "subway"

# (file, voice, text, cut off after this many seconds or None)
LINES = [
    ("pa_evacuate_1", "David", "Attention all passengers. This station is closing. Proceed calmly to the nearest exit and follow the instructions of staff.", None),
    ("pa_evacuate_2", "Zira", "Due to an emergency, all services are suspended. Do not wait on the platform. Leave the station now.", None),
    ("pa_missing", "Zira", "Would the parents of a girl, about eight years old, wearing a yellow coat, please come to the ticket hall. Your daughter is waiting for you.", None),
    ("pa_doors", "David", "Stand clear of the doors. Stand clear of the doors. This train will not depart. I repeat, this train will not", 5.6),
    ("pa_north", "David", "Military transport to the north camps leaves from the street entrance only. Do not enter the tunnels. Do not enter the", 6.2),
    ("pa_checks", "Zira", "All passengers must be checked before boarding. Anyone with a bite or scratch must report to staff immediately.", None),
    ("pa_lights", "David", "Power to this station will be cut at midnight. Emergency lighting will remain. Do not attempt to walk the tunnels.", None),
    ("pa_sorry", "Zira", "We are sorry. There are no more trains. There are no more", 4.2),
]
STATIC = ["pa_static_1", "pa_static_2", "pa_static_3"]
SUBTITLES = {"subway.pa": "A station announcement crackles", "subway.pa_static": "A dead loudspeaker hisses"}


def speak(voice: str, text: str, wav: Path) -> None:
    script = (f"Add-Type -AssemblyName System.Speech; $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
              f"$s.SelectVoice('Microsoft {voice} Desktop'); $s.Rate = -1; $s.SetOutputToWaveFile('{wav}'); "
              f"$s.Speak(\"{text}\"); $s.Dispose()")
    subprocess.run(["powershell", "-NoProfile", "-Command", script], check=True)


def degrade(wav: Path, out: Path, cut: float | None, seed: int) -> None:
    """A dying PA: band-limited, driven, crushed, echoing, with hum, static and dropouts."""
    trim = f"atrim=0:{cut}," if cut else ""
    graph = (
        f"[0:a]{trim}aresample=44100,highpass=f=320,lowpass=f=3100,volume=2.2,acrusher=bits=9:mix=0.35,"
        f"asoftclip=type=atan,aecho=0.8:0.6:180|420:0.35|0.18,"
        f"volume='if(lt(mod(t*7.3+{seed % 5},11),0.35),0.08,1)':eval=frame[v];"
        f"anoisesrc=color=white:amplitude=0.05:seed={seed}:sample_rate=44100,highpass=f=900,lowpass=f=5200[n];"
        f"sine=frequency=60:sample_rate=44100,volume=0.03[h];"
        f"[v][n][h]amix=inputs=3:duration=first:normalize=0,afade=t=in:d=0.25[o]"
    )
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-filter_complex", graph, "-map", "[o]",
                    "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", str(out)], check=True)


def static(out: Path, seed: int, seconds: float) -> None:
    graph = (f"anoisesrc=color=pink:amplitude=0.35:seed={seed}:duration={seconds}:sample_rate=44100,highpass=f=400,lowpass=f=4500,"
             f"volume='0.25+0.75*gt(sin(t*{3 + seed % 4}.1)*sin(t*1.7),0.2)':eval=frame,acrusher=bits=6:mix=0.5[n];"
             f"sine=frequency=60:sample_rate=44100:duration={seconds},volume=0.06[h];"
             f"[n][h]amix=inputs=2:normalize=0,afade=t=in:d=0.15,afade=t=out:st={seconds - 0.4}:d=0.4[o]")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-filter_complex", graph, "-map", "[o]", "-ac", "1",
                    "-c:a", "libvorbis", "-q:a", "5", str(out)], check=True)


def build() -> None:
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    if sys.platform == "win32" and shutil.which("ffmpeg"):
        OUT.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            for i, (name, voice, text, cut) in enumerate(LINES):
                wav = Path(tmp) / f"{name}.wav"
                speak(voice, text, wav)
                degrade(wav, OUT / f"{name}.ogg", cut, 7001 + i)
        for i, name in enumerate(STATIC):
            static(OUT / f"{name}.ogg", 7101 + i, 5.0 + 2.0 * i)
    present = [name for name, *_ in LINES if (OUT / f"{name}.ogg").exists()]
    if present:
        sounds.pop("subway.pa", None)
        sounds.pop("subway.pa_static", None)
        for n in present:  # one event per announcement: each station has its own set (SubwayAmbience)
            sounds[f"subway.{n}"] = {"subtitle": f"subtitles.{NS}.subway.pa", "sounds": [{"name": f"{NS}:subway/{n}", "attenuation_distance": 40}]}
        for n in STATIC:
            sounds[f"subway.{n}"] = {"subtitle": f"subtitles.{NS}.subway.pa_static", "sounds": [{"name": f"{NS}:subway/{n}", "attenuation_distance": 32}]}
        sounds_path.write_text(json.dumps(sounds, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    text = {f"subtitles.{NS}.{k}": v for k, v in SUBTITLES.items()}
    D.merge_lang(text)
    print(f"  subway audio: {len(present)} announcements, {len(STATIC)} broken-speaker loops")


if __name__ == "__main__":
    build()
