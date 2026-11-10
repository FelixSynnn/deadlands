"""Biome ambience for the ten DEADLANDS biomes, synthesised from noise and oscillators.

  ambient/<name>_loop.ogg   9 seamless 32 s stereo beds (wind, insects, water, metal, embers)
  ambient/<event>N.ogg      one-shot additions the game sprinkles over the bed (creaks, crows,
                            distant metal, gusts, frogs, embers) and a shared mood sound
  event/town_enter.ogg      the sting under the town card when a survivor walks into a town

Loops are rendered a few seconds long and the tail is crossfaded into the head, so the last sample
flows into the first. Every sound event is registered in DLSounds and mapped in sounds.json here.
"""
from __future__ import annotations

import json

import numpy as np

import dl_noise as N
from gen_audio import (OUT, SR, _creak, _impact, _partials, axis, bandpass, env_adsr, fade, grains, highpass, lowpass,
                       normalize, reverb, reverb_ir, write_ogg)

LOOP_SECONDS = 32.0
XFADE = 4.0


# ----------------------------------------------------------------------------- building blocks

def pink(n: int, r) -> np.ndarray:
    spec = np.fft.rfft(r.normal(0, 1, n))
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spec[1:] /= np.sqrt(freqs[1:])
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / np.max(np.abs(x))


def gusts(t: np.ndarray, period_a: float, period_b: float, phase: float, depth: float = 0.45) -> np.ndarray:
    g = np.sin(2 * np.pi * t / period_a + phase) * np.sin(2 * np.pi * t / period_b + phase * 1.7)
    return np.clip(1.0 - depth + depth * g, 0.05, 1.0)  # between 1 - 2*depth and 1


def wind_bed(t, r, lo, hi, gust_periods=(19.0, 7.0), phase=0.0, whistle=None) -> np.ndarray:
    n = len(t)
    body = bandpass(pink(n, r), lo, hi, 2)
    g = gusts(t, *gust_periods, phase)
    out = body * g
    if whistle:
        out += 0.5 * bandpass(pink(n, r), *whistle, 4) * g ** 3
    return out


def place(out: np.ndarray, sound: np.ndarray, start: float, gain: float, pan: float) -> None:
    """Mix a mono sound into a stereo bed at a time, wrapping past the end so loops stay seamless."""
    i = int(start * SR) % len(out)
    n = len(sound)
    idx = (np.arange(n) + i) % len(out)
    out[idx, 0] += sound * gain * (1.0 - pan)
    out[idx, 1] += sound * gain * pan


def make_loop(render, seed: int, peak: float = 0.55) -> np.ndarray:
    """Render `render(t, r, out)` into a stereo buffer, add space, and close the loop seamlessly."""
    r = N.rng(seed)
    t = axis(LOOP_SECONDS + XFADE)
    out = np.zeros((len(t), 2))
    render(t, r, out)
    wet = np.zeros_like(out)
    for ch in range(2):
        wet[:, ch] = reverb(out[:, ch], reverb_ir(2.5, seed + 10 + ch, damp=0.7), 0.25, keep_tail=False)
    out = highpass(wet.T, 30).T
    xs, ls = int(XFADE * SR), int(LOOP_SECONDS * SR)
    body = out[xs:xs + ls].copy()
    c = np.sin(np.linspace(0, np.pi / 2, xs)) ** 2
    body[ls - xs:] = body[ls - xs:] * np.sqrt(1 - c)[:, None] + out[:xs] * np.sqrt(c)[:, None]
    return normalize(body, peak)


def chirps(t, r, freq, rate, burst, density) -> np.ndarray:
    """Insect chirps: bursts of a pulsed tone, in loose rhythmic groups."""
    n = len(t)
    out = np.zeros(n)
    when = 0.0
    while when < t[-1]:
        if r.random() < density:
            for k in range(int(r.integers(2, 5))):
                s = int((when + k / rate) * SR)
                m = int(burst * SR)
                if s + m >= n:
                    break
                tt = np.arange(m) / SR
                f = freq * r.uniform(0.97, 1.03)
                tone = np.sin(2 * np.pi * f * tt) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 60 * tt)))
                out[s:s + m] += tone * np.sin(np.pi * tt / burst) ** 2
        when += r.uniform(0.4, 1.4)
    return out


# ----------------------------------------------------------------------------- one-shots

def creak(seed: int, pitch: float) -> np.ndarray:
    r = N.rng(seed)
    t = axis(1.3)
    x = _creak(t, r, 40 * pitch, 75 * pitch, 0.05, 0.9, cutoff=1100)
    x += 0.25 * _creak(t, r, 70 * pitch, 55 * pitch, 0.6, 0.4, cutoff=900)
    return normalize(fade(reverb(x, reverb_ir(1.6, seed + 1, damp=0.7), 0.35, keep_tail=False), 0.01, 0.2), 0.6)


def crow(seed: int) -> np.ndarray:
    """A distant crow: two or three harsh, nasal caws falling in pitch."""
    r = N.rng(seed)
    t = axis(2.2)
    out = np.zeros_like(t)
    start = 0.05
    for _ in range(int(r.integers(2, 4))):
        length = r.uniform(0.22, 0.32)
        u = np.clip((t - start) / length, 0, 1)
        f0 = 620 - 160 * u + 25 * np.sin(2 * np.pi * 38 * t)
        phase = np.cumsum(f0) / SR
        saw = 2 * (phase % 1.0) - 1
        env = np.where((t >= start) & (t < start + length), np.sin(np.pi * u) ** 0.6, 0.0)
        voiced = saw * env + 0.25 * r.normal(0, 1, len(t)) * env
        caw = bandpass(voiced, 900, 1700, 2) + 0.6 * bandpass(voiced, 2200, 3200, 2)
        out += caw
        start += length + r.uniform(0.12, 0.25)
    out = lowpass(out, 3500)  # distance
    return normalize(fade(reverb(out, reverb_ir(2.0, seed + 2, damp=0.8), 0.45, keep_tail=False), 0.005, 0.3), 0.45)


def metal(seed: int) -> np.ndarray:
    """Far-off metal: a sheet banging or a girder groaning in the wind."""
    r = N.rng(seed)
    t = axis(3.0)
    if r.random() < 0.5:
        x = _partials(t, [(r.uniform(140, 200), 1.0, 1.4), (r.uniform(310, 380), 0.7, 1.0),
                          (r.uniform(520, 640), 0.45, 0.7), (r.uniform(900, 1100), 0.25, 0.4)], start=0.05, r=r)
        x += 0.6 * _impact(t, r, 0.05, body=90, decay=0.08, bright=1200)
    else:
        f = 70 + 25 * np.sin(np.pi * np.clip(t / 2.5, 0, 1))
        phase = np.cumsum(f) / SR
        x = sum(a * np.sin(2 * np.pi * h * phase) for h, a in ((1, 1.0), (2.01, 0.5), (3.03, 0.3), (4.1, 0.2)))
        x *= np.sin(np.pi * np.clip(t / 2.6, 0, 1)) ** 1.5
        x = bandpass(x + 0.1 * r.normal(0, 1, len(t)), 60, 1200, 2)
    x = lowpass(x, 1800)
    return normalize(fade(reverb(x, reverb_ir(3.0, seed + 3, damp=0.75), 0.5, keep_tail=False), 0.005, 0.4), 0.5)


def gust(seed: int) -> np.ndarray:
    r = N.rng(seed)
    t = axis(3.2)
    env = np.sin(np.pi * np.clip(t / 3.1, 0, 1)) ** 2
    sweep = 500 + 700 * np.sin(np.pi * np.clip(t / 3.1, 0, 1))
    x = bandpass(pink(len(t), r), 200, 2600, 2) * env
    whistle = bandpass(pink(len(t), r), 700, 1100, 4) * env ** 3
    x += 0.4 * whistle * (sweep / sweep.max())
    return normalize(fade(x, 0.05, 0.3), 0.55)


def frog(seed: int) -> np.ndarray:
    r = N.rng(seed)
    t = axis(0.9)
    out = np.zeros_like(t)
    start = 0.03
    for _ in range(int(r.integers(1, 3))):
        length = r.uniform(0.18, 0.3)
        u = np.clip((t - start) / length, 0, 1)
        pulses = (np.sin(2 * np.pi * r.uniform(22, 32) * t) > 0.3).astype(float)
        tone = np.sin(2 * np.pi * r.uniform(280, 420) * (1 - 0.1 * u) * t)
        env = np.where((t >= start) & (t < start + length), np.sin(np.pi * u), 0.0)
        out += bandpass(tone * pulses * env, 200, 1400, 2)
        start += length + r.uniform(0.05, 0.12)
    return normalize(fade(reverb(out, reverb_ir(0.8, seed + 1, damp=0.6), 0.2, keep_tail=False), 0.003, 0.08), 0.5)


def ember(seed: int) -> np.ndarray:
    r = N.rng(seed)
    t = axis(1.4)
    pops = grains(len(t), 0.0009, 0.0015, seed + 1)
    x = bandpass(r.normal(0, 1, len(t)), 1500, 7000, 2) * pops * 3.0
    x += 0.15 * bandpass(r.normal(0, 1, len(t)), 2000, 9000, 2) * np.exp(-t / 0.6)
    x += 0.4 * _impact(t, r, r.uniform(0.2, 0.8), body=70, decay=0.06, bright=600)
    return normalize(fade(x, 0.003, 0.2), 0.5)


def mood(seed: int) -> np.ndarray:
    """Something far away: a low boom rolling across the dead land, a metallic groan."""
    r = N.rng(seed)
    t = axis(6.0)
    boom = lowpass(r.normal(0, 1, len(t)), 180) * env_adsr(t, 0.05, 1.4, start=0.3)
    boom += 0.6 * np.sin(2 * np.pi * 42 * t) * env_adsr(t, 0.08, 1.8, start=0.3)
    f = 95 * (1 - 0.15 * np.clip((t - 1.5) / 3.0, 0, 1))
    groan = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * np.clip((t - 1.5) / 3.5, 0, 1)) ** 2
    x = boom + 0.25 * bandpass(groan + 0.2 * np.sin(4 * np.pi * np.cumsum(f) / SR), 70, 900, 2)
    x = reverb(x, reverb_ir(4.0, seed + 1, damp=0.85, predelay=0.05), 0.55, keep_tail=False)
    return normalize(fade(highpass(x, 25), 0.05, 1.0), 0.5)


def town_enter(seed: int = 6201) -> np.ndarray:
    """Arriving in a town: wind drops into a hollow swell, a loose sign creaks, a far chime rings once."""
    r = N.rng(seed)
    t = axis(5.5)
    swell = lowpass(pink(len(t), r), 420) * np.sin(np.pi * np.clip(t / 5.2, 0, 1)) ** 1.6
    drone = sum(a * np.sin(2 * np.pi * f * t + r.uniform(0, 6.28)) for f, a in ((55.0, 1.0), (82.4, 0.5), (110.3, 0.3)))
    drone *= np.sin(np.pi * np.clip((t - 0.2) / 4.8, 0, 1)) ** 2
    chime = _partials(t, [(523.0, 1.0, 2.6), (790.5, 0.5, 2.0), (1311.0, 0.25, 1.2), (1874.0, 0.12, 0.8)], start=0.9, r=r)
    sign = _creak(t, r, lo=55.0, hi=80.0, start=2.2, length=0.9, cutoff=1100)
    x = 0.55 * swell + 0.35 * drone + 0.28 * lowpass(chime, 2600) + 0.35 * sign
    x = reverb(x, reverb_ir(3.6, seed + 1, damp=0.8, predelay=0.05), 0.5, keep_tail=False)
    return normalize(fade(highpass(x, 28), 0.05, 1.2), 0.7)


# ----------------------------------------------------------------------------- loops

def loop_dead_forest(t, r, out):
    for ch in range(2):
        out[:, ch] += 0.7 * wind_bed(t, r, 150, 1100, (19.0, 7.3), ch * 1.3, whistle=(950, 1400))
        out[:, ch] += 0.12 * bandpass(r.normal(0, 1, len(t)), 2500, 7000, 2) * grains(len(t), 0.0006, 0.002, int(r.integers(1_000_000)))
    for when in r.uniform(0, LOOP_SECONDS, 3):
        place(out, creak(int(r.integers(1_000_000)), r.uniform(0.7, 1.2)), when, 0.18, r.uniform(0.2, 0.8))


def loop_pine(t, r, out):
    for ch in range(2):
        out[:, ch] += 0.8 * wind_bed(t, r, 300, 3200, (15.0, 6.1), ch * 0.9)
        out[:, ch] += 0.2 * wind_bed(t, r, 120, 500, (23.0, 9.0), ch * 2.1)
    for when in r.uniform(0, LOOP_SECONDS, 2):
        place(out, creak(int(r.integers(1_000_000)), r.uniform(0.6, 0.9)), when, 0.15, r.uniform(0.2, 0.8))


def loop_suburbs(t, r, out):
    for ch in range(2):
        out[:, ch] += 0.45 * wind_bed(t, r, 180, 1500, (21.0, 8.0), ch * 1.1)
        out[:, ch] += 0.18 * bandpass(r.normal(0, 1, len(t)), 2000, 6000, 2) * grains(len(t), 0.002, 0.003, int(r.integers(1_000_000)))
    notes = [523.25, 587.33, 698.46, 783.99, 880.0]
    for when in r.uniform(0, LOOP_SECONDS, 7):  # a forgotten wind chime somewhere down the street
        tt = axis(3.0)
        f = notes[int(r.integers(0, len(notes)))] * r.uniform(0.995, 1.005)
        chime = sum(a * np.sin(2 * np.pi * f * h * tt) * np.exp(-tt / d) for h, a, d in ((1, 1.0, 1.2), (2.76, 0.4, 0.5), (5.4, 0.2, 0.25)))
        place(out, lowpass(chime, 3000), when, 0.05, r.uniform(0.3, 0.7))


def loop_fields(t, r, out):
    for ch in range(2):
        out[:, ch] += 0.55 * wind_bed(t, r, 400, 4000, (17.0, 6.7), ch * 1.5)
        out[:, ch] += 0.12 * chirps(t, r, 4400, 14.0, 0.035, 0.6)


def loop_dry_wind(t, r, out):
    for ch in range(2):
        out[:, ch] += 0.75 * wind_bed(t, r, 900, 6500, (11.0, 4.3), ch * 1.2)
        out[:, ch] += 0.25 * bandpass(r.normal(0, 1, len(t)), 3000, 8000, 2) * grains(len(t), 0.004, 0.002, int(r.integers(1_000_000)))
        out[:, ch] += 0.12 * np.sin(2 * np.pi * (55 + ch * 0.4) * t) * (0.6 + 0.4 * np.sin(2 * np.pi * t / 13.0))


def loop_marsh(t, r, out):
    n = len(t)
    for ch in range(2):
        lap = bandpass(r.normal(0, 1, n), 180, 900, 2) * (0.5 + 0.5 * np.sin(2 * np.pi * t / r.uniform(2.6, 3.4) + ch)) ** 3
        out[:, ch] += 0.35 * lap
        out[:, ch] += 0.12 * chirps(t, r, 3100, 9.0, 0.05, 0.5)
        out[:, ch] += 0.25 * wind_bed(t, r, 150, 800, (25.0, 9.5), ch * 0.7)
    for when in r.uniform(0, LOOP_SECONDS, 9):
        place(out, frog(int(r.integers(1_000_000))), when, 0.12, r.uniform(0.15, 0.85))
    for when in r.uniform(0, LOOP_SECONDS, 14):  # drips
        tt = axis(0.25)
        drip = np.sin(2 * np.pi * r.uniform(900, 1500) * (1 + 0.8 * tt) * tt) * np.exp(-tt / 0.03)
        place(out, drip, when, 0.05, r.uniform(0.2, 0.8))


def loop_frozen(t, r, out):
    for ch in range(2):
        out[:, ch] += 0.55 * wind_bed(t, r, 500, 5000, (13.0, 5.2), ch * 1.4, whistle=(650, 1150))
        out[:, ch] += 0.4 * bandpass(pink(len(t), r), 1100, 1500, 6) * gusts(t, 9.0, 3.7, ch) ** 4
    for when in r.uniform(0, LOOP_SECONDS, 2):  # ice settling
        tt = axis(1.5)
        groan = np.sin(2 * np.pi * (60 + 30 * tt) * tt) * np.sin(np.pi * np.clip(tt / 1.4, 0, 1)) ** 2
        place(out, lowpass(groan, 500), when, 0.12, r.uniform(0.3, 0.7))


def loop_industrial(t, r, out):
    n = len(t)
    for ch in range(2):
        out[:, ch] += 0.4 * wind_bed(t, r, 200, 2000, (18.0, 7.7), ch * 1.6)
        hum = sum(a * np.sin(2 * np.pi * f * t + ch) for f, a in ((49.0, 1.0), (98.3, 0.5), (147.1, 0.25)))
        out[:, ch] += 0.08 * hum * (0.6 + 0.4 * np.sin(2 * np.pi * t / 11.0))
        rattle = bandpass(r.normal(0, 1, n), 800, 3000, 2) * (np.sin(2 * np.pi * 7.5 * t) > 0.6) * gusts(t, 6.0, 2.3, ch) ** 3
        out[:, ch] += 0.08 * rattle
    for when in r.uniform(0, LOOP_SECONDS, 3):
        place(out, metal(int(r.integers(1_000_000))), when, 0.2, r.uniform(0.2, 0.8))


def loop_burned(t, r, out):
    n = len(t)
    for ch in range(2):
        out[:, ch] += 0.35 * wind_bed(t, r, 120, 900, (24.0, 9.1), ch * 1.2)
        out[:, ch] += 0.3 * bandpass(r.normal(0, 1, n), 1500, 7000, 2) * grains(n, 0.0006, 0.0015, int(r.integers(1_000_000))) * 3
        out[:, ch] += 0.04 * bandpass(r.normal(0, 1, n), 3000, 9000, 2)
    for when in r.uniform(0, LOOP_SECONDS, 4):
        place(out, ember(int(r.integers(1_000_000))), when, 0.15, r.uniform(0.2, 0.8))


LOOPS = {
    "dead_forest": loop_dead_forest, "pine": loop_pine, "suburbs": loop_suburbs, "fields": loop_fields,
    "dry_wind": loop_dry_wind, "marsh": loop_marsh, "frozen": loop_frozen, "industrial": loop_industrial,
    "burned": loop_burned,
}
ONE_SHOTS = {
    "creak": [lambda: creak(6101, 1.0), lambda: creak(6102, 0.8), lambda: creak(6103, 1.25)],
    "crow": [lambda: crow(6111), lambda: crow(6112)],
    "metal": [lambda: metal(6121), lambda: metal(6122), lambda: metal(6123)],
    "gust": [lambda: gust(6131), lambda: gust(6132)],
    "frog": [lambda: frog(6141), lambda: frog(6142), lambda: frog(6143)],
    "ember": [lambda: ember(6151), lambda: ember(6152), lambda: ember(6153)],
    "mood": [lambda: mood(6161), lambda: mood(6162), lambda: mood(6163)],
}


def write_stereo_ogg(rel: str, data: np.ndarray, quality: int = 4) -> None:
    write_ogg(rel, data, quality=quality)  # soundfile writes (n, 2) arrays as stereo


def update_sounds_json() -> None:
    path = OUT.parent / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    for name in LOOPS:
        sounds[f"ambient.{name}.loop"] = {"sounds": [{"name": f"deadlands:ambient/{name}_loop", "stream": True}]}
    for name, variants in ONE_SHOTS.items():
        sounds[f"ambient.{name}"] = {"sounds": [f"deadlands:ambient/{name}{i + 1}" for i in range(len(variants))]}
    sounds["event.town_enter"] = {"subtitle": "subtitles.deadlands.town_enter", "sounds": ["deadlands:event/town_enter"]}
    path.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")


def build() -> None:
    for k, (name, render) in enumerate(LOOPS.items()):
        write_stereo_ogg(f"ambient/{name}_loop", make_loop(render, 6000 + k * 17))
    for name, variants in ONE_SHOTS.items():
        for i, make in enumerate(variants):
            write_ogg(f"ambient/{name}{i + 1}", make())
    write_ogg("event/town_enter", town_enter(), quality=5)
    update_sounds_json()


if __name__ == "__main__":
    build()
    print("ambience written")
