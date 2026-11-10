"""Synthesised infected, nest and horde sounds (Milestone 4). Every sound is built from oscillators,
filters and noise (no samples), several variants per event so nothing repeats exactly.

Voices use a simple source-filter model: a jittery glottal sawtooth plus breath noise, shaped by
formant band-passes, with growl (sub-harmonic modulation), distortion and envelopes per kind.
"Distant" and "muffled" voices are the same groans low-passed (through air, through walls) with
reverb; the client picks them by distance and line of sight.

Writes sounds/infected/..., sounds/nest/..., sounds/event/horde_*.ogg and the matching sounds.json
entries (merged).
"""
from __future__ import annotations

import json

import numpy as np

import dl_noise as N
from gen_audio import OUT, SR, axis, bandpass, env_adsr, fade, grains, highpass, lowpass, normalize, reverb, reverb_ir, write_ogg

AH = [(800, 220, 1.0), (1200, 260, 0.6), (2600, 400, 0.25)]
UH = [(520, 160, 1.0), (920, 220, 0.55), (2400, 400, 0.2)]
AW = [(600, 180, 1.0), (850, 200, 0.7), (2500, 400, 0.2)]
EE = [(320, 120, 0.9), (2300, 380, 0.8), (3000, 500, 0.4)]


def voice(seconds, f0, f0_end, formants, seed, breath=0.25, growl=0.0, dist=0.0, tremolo=0.0, attack=0.08, release=0.35, jitter=0.03,
          f0_mid=None):
    r = N.rng(seed)
    t = axis(seconds)
    n = len(t)
    points = [0, seconds / 2, seconds] if f0_mid else [0, seconds]
    values = [f0, f0_mid, f0_end] if f0_mid else [f0, f0_end]
    f = np.interp(t, points, values)
    wobble = lowpass(r.normal(0, 1, n), 12, order=2)
    f = f * (1.0 + jitter * wobble / (np.std(wobble) + 1e-9))
    phase = 2 * np.pi * np.cumsum(f) / SR
    src = ((phase / np.pi) % 2.0) - 1.0
    src = src + breath * r.normal(0, 1, n)
    if growl > 0:
        src = src * (1.0 + growl * np.sin(phase / 2.0))
    out = np.zeros(n)
    for freq, bw, amp in formants:
        lo, hi = max(40, freq - bw / 2), min(SR / 2 - 200, freq + bw / 2)
        out += amp * bandpass(src, lo, hi, order=2)
    env = np.clip(t / attack, 0, 1) * np.clip((seconds - t) / release, 0, 1)
    if tremolo > 0:
        env *= 0.75 + 0.25 * np.sin(2 * np.pi * tremolo * t + r.uniform(0, 6))
    out *= env
    if dist > 0:
        out = np.tanh(out / (np.max(np.abs(out)) + 1e-9) * (1 + dist * 6))
    return fade(normalize(out, 0.8))


def thud(seconds, freq, seed, debris=0.4):
    r = N.rng(seed)
    t = axis(seconds)
    n = len(t)
    body = np.sin(2 * np.pi * freq * t * (1 - 0.3 * t / seconds)) * np.exp(-t * 18)
    knock = lowpass(r.normal(0, 1, n), 900) * np.exp(-t * 35)
    crumble = bandpass(r.normal(0, 1, n), 1500, 6000) * grains(n, 0.002, 0.003, seed + 1) * debris * np.exp(-t * 4)
    return fade(normalize(body + 0.6 * knock + crumble, 0.85))


def clang(seconds, seed):
    r = N.rng(seed)
    t = axis(seconds)
    out = np.zeros(len(t))
    for k in range(6):
        f = r.uniform(300, 2400)
        out += np.sin(2 * np.pi * f * t) * np.exp(-t * r.uniform(4, 12)) * r.uniform(0.3, 1.0)
    out += lowpass(r.normal(0, 1, len(t)), 2000) * np.exp(-t * 40)
    return fade(normalize(out, 0.8))


def noise_burst(seconds, lo, hi, seed, decay=6.0, attack=0.01):
    r = N.rng(seed)
    t = axis(seconds)
    env = np.clip(t / attack, 0, 1) * np.exp(-t * decay)
    return fade(normalize(bandpass(r.normal(0, 1, len(t)), lo, hi) * env, 0.8))


def wet(seconds, seed, bubbles=40):
    """Gurgling and squelching: short resonant bubbles over a low wet noise bed."""
    r = N.rng(seed)
    t = axis(seconds)
    n = len(t)
    out = lowpass(r.normal(0, 1, n), 500) * 0.3
    for _ in range(bubbles):
        start = r.uniform(0, seconds - 0.05)
        f = r.uniform(150, 600)
        k = (t >= start) & (t < start + 0.06)
        tt = t[k] - start
        out[k] += np.sin(2 * np.pi * (f + 900 * tt) * tt) * np.exp(-tt * 60) * r.uniform(0.4, 1.0)
    return fade(normalize(out, 0.75))


def with_room(x, seed, wet_amount=0.3, seconds=0.6):
    return normalize(reverb(x, reverb_ir(seconds, seed), wet_amount), 0.8)


def distant(x, seed):
    return normalize(reverb(lowpass(x, 650), reverb_ir(2.2, seed, damp=0.8), 0.65), 0.55)


def muffled(x, seed):
    return normalize(reverb(lowpass(x, 380), reverb_ir(0.5, seed, damp=0.9), 0.35), 0.6)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def scratch(seed):
    r = N.rng(seed)
    t = axis(1.2)
    n = len(t)
    out = np.zeros(n)
    for _ in range(int(r.integers(4, 8))):
        start = r.uniform(0, 1.0)
        length = r.uniform(0.08, 0.2)
        k = (t >= start) & (t < start + length)
        burst = bandpass(r.normal(0, 1, k.sum()), 2000, 6500)
        out[k] += burst * np.sin(np.linspace(0, np.pi, k.sum()))
    return fade(normalize(with_room(out, seed, 0.15, 0.3), 0.6))


def breath(seed):
    r = N.rng(seed)
    t = axis(2.2)
    n = len(t)
    env = np.clip(np.sin(2 * np.pi * t / 2.2) ** 2, 0, 1)
    noise_ = bandpass(r.normal(0, 1, n), 300, 2500) * env
    wheeze = np.sin(2 * np.pi * r.uniform(500, 800) * t) * (env ** 3) * 0.15
    return fade(normalize(noise_ + wheeze, 0.5))


def step(seed):
    r = N.rng(seed)
    t = axis(0.35)
    swish = bandpass(r.normal(0, 1, len(t)), 300, 1500) * np.exp(-t * 14)
    return fade(normalize(swish + 0.5 * thud(0.35, 70, seed + 1, 0.1), 0.6))


def crowd(seconds, seed, voices=12):
    """Many infected moaning together, for horde stings."""
    r = N.rng(seed)
    n = int(seconds * SR)
    out = np.zeros(n)
    for i in range(voices):
        length = r.uniform(1.4, 3.0)
        g = voice(length, r.uniform(60, 120), r.uniform(50, 100), [UH, AW, AH][i % 3], seed + i * 7, breath=0.35, growl=0.2)
        start = int(r.uniform(0, max(0.1, seconds - length)) * SR)
        out[start:start + len(g)] += g[:n - start] * r.uniform(0.4, 1.0)
    return out


def horde_start(seed=7401):
    t = axis(6.0)
    drone = (np.sin(2 * np.pi * (45 + 15 * t / 6.0) * t) + 0.5 * np.sin(2 * np.pi * (90 + 30 * t / 6.0) * t)) * np.clip(t / 2.0, 0, 1)
    siren = np.sin(2 * np.pi * (300 + 140 * np.sin(2 * np.pi * 0.35 * t)) * t) * 0.12 * np.clip((t - 1.0) / 2.0, 0, 1)
    boom = thud(3.0, 38, seed + 1, 0.2)
    body = mix(0.5 * drone, siren, distant(crowd(6.0, seed + 2), seed + 3) * 0.9)
    body[:len(boom)] += boom * 0.9
    out = normalize(reverb(body, reverb_ir(2.5, seed + 4), 0.35), 0.85)
    return fade(out[:int(6.5 * SR)], 0.01, 1.5)


def horde_broken(seed=7411):
    t = axis(4.5)
    chord = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((110, 0.5), (164.8, 0.35), (220, 0.3), (277.2, 0.15)))
    env = np.clip(t / 1.0, 0, 1) * np.clip((4.5 - t) / 2.0, 0, 1)
    wind = lowpass(N.rng(seed).normal(0, 1, len(t)), 800) * 0.15
    return fade(normalize(reverb(chord * env + wind, reverb_ir(2.0, seed), 0.4), 0.7))


def horde_dawn(seed=7421):
    t = axis(5.0)
    r = N.rng(seed)
    wind = bandpass(r.normal(0, 1, len(t)), 200, 1600) * (0.5 + 0.5 * np.sin(2 * np.pi * t / 5.0)) * 0.4
    warm = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((130.8, 0.3), (196, 0.25), (261.6, 0.15))) * np.clip(t / 2.0, 0, 1) * np.clip((5.0 - t) / 1.5, 0, 1)
    caw = np.zeros(len(t))
    for when in (1.6, 2.1):
        k = (t >= when) & (t < when + 0.35)
        tt = t[k] - when
        caw[k] = bandpass(np.sign(np.sin(2 * np.pi * (520 - 200 * tt) * tt)) + 0.3 * r.normal(0, 1, k.sum()), 400, 2500) * np.sin(np.pi * tt / 0.35)
    return fade(normalize(reverb(wind + warm + caw * 0.3, reverb_ir(2.5, seed + 1), 0.4), 0.7))


def groan_set(seed, f0, formants, count, length=1.6, growl=0.1, breath=0.3):
    r = N.rng(seed)
    return [voice(length * r.uniform(0.8, 1.25), f0 * r.uniform(0.9, 1.15), f0 * r.uniform(0.7, 0.95), formants, seed + i * 13, breath=breath,
                  growl=growl, tremolo=r.uniform(0, 6)) for i in range(count)]


def sounds() -> dict[str, list[np.ndarray]]:
    """Event name (without the deadlands: prefix) -> variants."""
    s = {}
    s["infected.walker.ambient"] = groan_set(7001, 85, UH, 4)
    s["infected.walker.hurt"] = [voice(0.45, 150, 110, AH, 7011 + i, dist=0.4, attack=0.01) for i in range(2)]
    s["infected.walker.death"] = [voice(1.3, 110, 50, AW, 7021 + i, growl=0.4, breath=0.4) for i in range(2)]
    s["infected.runner.ambient"] = [voice(0.9, 130, 120, AH, 7031 + i, breath=0.7, dist=0.3, tremolo=9) for i in range(3)]
    s["infected.runner.alert"] = [voice(0.9, 420, 640, AH, 7041 + i, breath=0.4, dist=0.7, f0_mid=700) for i in range(2)]
    s["infected.runner.hurt"] = [voice(0.35, 220, 160, AH, 7051 + i, dist=0.5, attack=0.01) for i in range(2)]
    s["infected.runner.death"] = [voice(0.9, 260, 90, AH, 7061, dist=0.4)]
    s["infected.brute.ambient"] = groan_set(7071, 48, AW, 2, length=2.2, growl=0.6, breath=0.2)
    s["infected.brute.roar"] = [voice(1.6, 60, 55, AW, 7081 + i, growl=0.8, dist=0.8, f0_mid=95) for i in range(2)]
    s["infected.brute.hurt"] = [voice(0.5, 80, 60, AW, 7091 + i, growl=0.5, dist=0.5, attack=0.01) for i in range(2)]
    s["infected.brute.death"] = [voice(2.0, 70, 35, AW, 7101, growl=0.7, breath=0.3)]
    s["infected.brute.step"] = [thud(0.5, 42 + 6 * i, 7111 + i, 0.5) for i in range(3)]
    s["infected.brute.slam"] = [with_room(thud(1.2, 36, 7121 + i, 1.0), 7125 + i, 0.3, 1.0) for i in range(2)]
    s["infected.screamer.ambient"] = [voice(1.4, 300, 260, EE, 7131 + i, breath=0.5, tremolo=7, jitter=0.06) for i in range(2)]
    s["infected.screamer.scream"] = [with_room(voice(2.2, 620, 900, EE, 7141 + i, breath=0.5, dist=0.75, f0_mid=1150, jitter=0.05), 7145, 0.35, 1.2)
                                     for i in range(2)]
    s["infected.screamer.hurt"] = [voice(0.4, 500, 380, EE, 7151 + i, dist=0.5, attack=0.01) for i in range(2)]
    s["infected.screamer.death"] = [voice(1.2, 700, 200, EE, 7161, dist=0.6)]
    radio = [noise_burst(0.5, 800, 3500, 7171 + i, decay=2.0) * 0.5 for i in range(3)]
    s["infected.military.ambient"] = [mix(g, np.concatenate([np.zeros(int(0.3 * SR)), rb])) for g, rb in zip(groan_set(7175, 80, UH, 3), radio)]
    s["infected.crawler.ambient"] = [mix(voice(1.4, 60, 50, UH, 7181 + i, breath=0.5, growl=0.3) * 0.7, wet(1.4, 7185 + i, 20) * 0.5) for i in range(2)]
    s["infected.crawler.hurt"] = [voice(0.4, 120, 90, UH, 7191 + i, dist=0.4, attack=0.01) for i in range(2)]
    s["infected.crawler.death"] = [mix(voice(1.0, 90, 40, UH, 7201, growl=0.3), wet(1.0, 7202, 30) * 0.6)]
    s["infected.bloater.ambient"] = [wet(1.6, 7211 + i, 50) for i in range(2)]
    s["infected.bloater.burst"] = [mix(noise_burst(0.9, 60, 900, 7221 + i, decay=5), wet(0.9, 7225 + i, 60) * 0.8) for i in range(2)]
    s["infected.bloater.hurt"] = [mix(voice(0.5, 90, 70, UH, 7231, dist=0.3, attack=0.01), wet(0.5, 7232, 15) * 0.6)]
    clicks = []
    for i in range(3):
        r = N.rng(7241 + i)
        t = axis(1.2)
        c = np.zeros(len(t))
        for when in np.cumsum(r.uniform(0.05, 0.18, 10)):
            if when < 1.15:
                k = int(when * SR)
                c[k:k + 200] += bandpass(r.normal(0, 1, 200), 2500, 6000) * np.exp(-np.arange(200) / 30.0)
        clicks.append(fade(normalize(mix(c, breath(7245 + i)[:len(t)] * 0.4), 0.55)))
    s["infected.stalker.ambient"] = clicks
    s["infected.stalker.pounce"] = [voice(0.6, 340, 520, AH, 7251 + i, breath=0.8, dist=0.6) for i in range(2)]
    s["infected.stalker.hurt"] = [voice(0.35, 300, 240, EE, 7261, breath=0.6, dist=0.4, attack=0.01)]
    s["infected.stalker.death"] = [voice(1.0, 380, 120, EE, 7271, breath=0.6, dist=0.4)]
    embers = [bandpass(N.rng(7281 + i).normal(0, 1, int(1.6 * SR)), 1500, 7000) * grains(int(1.6 * SR), 0.0015, 0.002, 7283 + i) * 3 for i in range(2)]
    s["infected.charred.ambient"] = [fade(normalize(mix(g * 0.7, e), 0.75)) for g, e in zip(groan_set(7285, 75, AW, 2, breath=0.6), embers)]
    s["infected.breath"] = [breath(7291 + i) for i in range(3)]
    s["infected.scratch"] = [scratch(7301 + i) for i in range(3)]
    s["infected.bash_wood"] = [with_room(mix(thud(0.6, 95 + 15 * i, 7311 + i, 0.8), noise_burst(0.3, 1500, 5000, 7315 + i, decay=14) * 0.5), 7319, 0.2, 0.5)
                               for i in range(3)]
    s["infected.bash_metal"] = [with_room(clang(1.0, 7321 + i), 7325, 0.25, 0.7) for i in range(2)]
    s["infected.step"] = [step(7331 + i) for i in range(4)]
    s["infected.attack"] = [mix(noise_burst(0.35, 400, 3000, 7341 + i, decay=8, attack=0.05) * 0.6, voice(0.35, 160, 140, AH, 7345 + i, dist=0.5, attack=0.02) * 0.7)
                            for i in range(2)]
    base = groan_set(7351, 80, UH, 4, length=2.0)
    s["infected.distant"] = [distant(g, 7355 + i) for i, g in enumerate(base)]
    s["infected.muffled"] = [muffled(g, 7361 + i) for i, g in enumerate(groan_set(7365, 85, AW, 3))]
    s["infected.emerge"] = [mix(wet(1.2, 7371 + i, 40) * 0.7, noise_burst(1.2, 200, 2000, 7373 + i, decay=2.5) * 0.4, voice(1.2, 70, 60, UH, 7375 + i) * 0.5)
                            for i in range(2)]
    s["nest.pulse"] = [mix(thud(0.5, 50, 7381 + i, 0.0), np.concatenate([np.zeros(int(0.25 * SR)), thud(0.4, 46, 7383 + i, 0.0) * 0.6]),
                           wet(0.8, 7385 + i, 10) * 0.4) for i in range(2)]
    s["nest.release"] = [mix(wet(1.5, 7391 + i, 70), noise_burst(1.5, 300, 4000, 7393 + i, decay=2) * 0.6, voice(1.2, 400, 600, AH, 7395 + i, dist=0.6) * 0.5)
                         for i in range(2)]
    s["nest.destroyed"] = [with_room(mix(noise_burst(2.0, 50, 1500, 7401, decay=2), wet(2.0, 7402, 120), voice(1.8, 500, 150, EE, 7403, dist=0.7) * 0.6),
                                     7404, 0.3, 1.0)]
    s["nest.absorb"] = [wet(0.9, 7411, 25)]
    s["event.horde_start"] = [horde_start()]
    s["event.horde_broken"] = [horde_broken()]
    s["event.horde_dawn"] = [horde_dawn()]
    s["event.horde_distant"] = [distant(crowd(6.0, 7431 + i, voices=16), 7435 + i) for i in range(2)]
    return s


SUBTITLES = {
    "infected.walker.ambient": "groan", "infected.runner.ambient": "groan", "infected.brute.ambient": "groan", "infected.screamer.ambient": "groan",
    "infected.military.ambient": "groan", "infected.crawler.ambient": "groan", "infected.bloater.ambient": "groan", "infected.charred.ambient": "groan",
    "infected.stalker.ambient": "breath",
    "infected.walker.hurt": "hurt", "infected.runner.hurt": "hurt", "infected.brute.hurt": "hurt", "infected.screamer.hurt": "hurt",
    "infected.crawler.hurt": "hurt", "infected.bloater.hurt": "hurt", "infected.stalker.hurt": "hurt",
    "infected.walker.death": "death", "infected.runner.death": "death", "infected.brute.death": "death", "infected.screamer.death": "death",
    "infected.crawler.death": "death", "infected.stalker.death": "death",
    "infected.runner.alert": "runner_alert", "infected.brute.roar": "brute_roar", "infected.brute.step": "brute_step", "infected.brute.slam": "slam",
    "infected.screamer.scream": "scream", "infected.bloater.burst": "burst", "infected.stalker.pounce": "pounce", "infected.breath": "breath",
    "infected.scratch": "scratch", "infected.bash_wood": "bash", "infected.bash_metal": "bash", "infected.step": "step", "infected.attack": "attack",
    "infected.distant": "distant", "infected.muffled": "groan", "infected.emerge": "emerge",
}
OTHER_SUBTITLES = {"nest.pulse": "subtitles.deadlands.nest.pulse", "nest.release": "subtitles.deadlands.nest.release",
                   "nest.destroyed": "subtitles.deadlands.nest.destroyed", "nest.absorb": "subtitles.deadlands.nest.absorb",
                   "event.horde_start": "subtitles.deadlands.horde_start", "event.horde_broken": "subtitles.deadlands.horde_end",
                   "event.horde_dawn": "subtitles.deadlands.horde_end", "event.horde_distant": "subtitles.deadlands.horde_distant"}


def file_name(event: str, index: int) -> str:
    parts = event.split(".")
    folder = "nest" if parts[0] == "nest" else "event" if parts[0] == "event" else "infected"
    stem = "_".join(parts[1:]) if parts[0] in ("infected", "nest") else parts[1]
    return f"{folder}/{stem}{index + 1}"


def build() -> None:
    path = OUT.parent / "sounds.json"
    table = json.loads(path.read_text(encoding="utf-8"))
    for event, variants in sounds().items():
        names = []
        for i, data in enumerate(variants):
            rel = file_name(event, i)
            write_ogg(rel, data, quality=4)
            names.append(f"deadlands:{rel}")
        entry = {"sounds": names}
        if event in SUBTITLES:
            entry["subtitle"] = f"subtitles.deadlands.infected.{SUBTITLES[event]}"
        elif event in OTHER_SUBTITLES:
            entry["subtitle"] = OTHER_SUBTITLES[event]
        table[event] = entry
    path.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


if __name__ == "__main__":
    build()
    print("infected audio written")
