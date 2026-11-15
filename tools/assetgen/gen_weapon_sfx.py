"""Phase 4: melee weapon sounds, synthesised (original, no samples).

weapon.swing      a whoosh: noise through a band-pass that sweeps up then down, three variants
weapon.hit_blunt  a heavy thud: a low decaying knock under a short crunch, three variants
weapon.hit_blade  a cut: a sharp wet slice with a faint metallic ring, three variants
Written to sounds/weapon/melee/ as OGG Vorbis (mono, 44.1 kHz) and registered in sounds.json.
"""
from __future__ import annotations

import json
import subprocess

import numpy as np
from scipy import signal

import dl_data as D
import dl_noise as N

SR = 44100
OUT = N.ASSETS / "sounds" / "weapon" / "melee"


def encode(x: np.ndarray, name: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    x = x / (np.abs(x).max() + 1e-9) * 0.89
    pcm = (x * 32767).astype(np.int16).tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-", "-c:a", "libvorbis", "-q:a", "5",
                    str(OUT / f"{name}.ogg")], input=pcm, check=True)


def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1.0, t / attack) * np.exp(-t / decay)


def swing(rng) -> np.ndarray:
    n = int(SR * (0.28 + rng.random() * 0.08))
    noise = rng.normal(0, 1, n)
    out = np.zeros(n)
    block = 256
    for i in range(0, n, block):
        p = i / n
        centre = 500 + 2200 * np.sin(np.pi * p)  # sweeps up and back down as the weapon passes
        b, a = signal.butter(2, [max(80, centre * 0.6) / (SR / 2), min(0.95, centre * 1.5 / (SR / 2))], btype="band")
        out[i:i + block] = signal.lfilter(b, a, noise[i:i + block])
    shape = np.sin(np.pi * np.linspace(0, 1, n)) ** 1.5
    return out * shape


def blunt(rng) -> np.ndarray:
    n = int(SR * 0.45)
    t = np.arange(n) / SR
    f = 70 + 40 * rng.random()
    knock = np.sin(2 * np.pi * f * t * (1 - 0.3 * t)) * env(n, 0.002, 0.09)
    b, a = signal.butter(2, [300 / (SR / 2), 2500 / (SR / 2)], btype="band")
    crunch = signal.lfilter(b, a, rng.normal(0, 1, n)) * env(n, 0.001, 0.03)
    return knock * 1.0 + crunch * 0.6


def blade(rng) -> np.ndarray:
    n = int(SR * 0.4)
    t = np.arange(n) / SR
    b, a = signal.butter(2, [1500 / (SR / 2), 7000 / (SR / 2)], btype="band")
    slice_ = signal.lfilter(b, a, rng.normal(0, 1, n)) * env(n, 0.002, 0.05)
    b2, a2 = signal.butter(2, [200 / (SR / 2), 900 / (SR / 2)], btype="band")
    wet = signal.lfilter(b2, a2, rng.normal(0, 1, n)) * env(n, 0.004, 0.07)
    ring = np.sin(2 * np.pi * (2800 + 600 * rng.random()) * t) * env(n, 0.001, 0.12) * 0.12
    return slice_ * 0.8 + wet * 0.7 + ring


def build() -> None:
    rng = np.random.default_rng(9701)
    events = {"weapon.swing": (swing, "Weapon swings"), "weapon.hit_blunt": (blunt, "Heavy blow"), "weapon.hit_blade": (blade, "Blade cuts")}
    sounds_path = N.ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    text = {}
    for event, (fn, subtitle) in events.items():
        names = []
        for i in range(3):
            name = f"{event.split('.')[1]}_{i + 1}"
            encode(fn(rng), name)
            names.append(f"deadlands:weapon/melee/{name}")
        key = f"subtitles.deadlands.{event}"
        sounds[event] = {"sounds": names, "subtitle": key}
        text[key] = subtitle
    sounds_path.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang(text | {"key.deadlands.inspect": "Inspect weapon"})
    print("  weapon sfx: swing, blunt and blade hits (3 each)")


if __name__ == "__main__":
    build()
