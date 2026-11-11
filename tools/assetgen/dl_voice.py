"""A source-filter voice for the infected: what is left of a human voice after the infection.

* source   a glottal pulse train (the derivative of a Rosenberg pulse) following a pitch contour,
           with jitter (unsteady pitch), shimmer (unsteady loudness), period doubling (the growl:
           every other pulse weaker, so a second, lower pitch appears), creak (pulses dropping out)
           and aspiration noise that puffs with each opening of the folds;
* tract    four formant resonators whose frequencies glide between vowel shapes over the sound
           (moaning "uh-aa-uh"), scaled by the length of the throat (lower for big bodies), with
           wide, lossy bandwidths: a slack, damaged throat;
* damage   rasp (noise riding the pulses), a wet gurgle (bubbles bursting in fluid), a throat rattle
           (amplitude flutter of the false folds) and soft saturation.

Vocalisations are built from these with an envelope and a contour: groans, moans, inhaled groans,
snarls, panting, shrieks, hurt grunts, death rattles, breaths and hisses.
"""
from __future__ import annotations

import numpy as np
from scipy import signal

import dl_music as M

SR = M.SR

VOWELS = {
    "uh": (520, 920, 2400, 3300), "aa": (780, 1180, 2550, 3400), "oo": (360, 760, 2300, 3200), "ae": (660, 1650, 2400, 3400),
    "er": (480, 1320, 1680, 3200), "eh": (560, 1700, 2500, 3500), "aw": (600, 900, 2500, 3300), "ih": (420, 1900, 2550, 3500),
}


def contour(n, points):
    """Piecewise-linear curve through (fraction, value) points."""
    xs = np.array([p[0] for p in points]) * (n - 1)
    ys = np.array([p[1] for p in points], dtype=float)
    return np.interp(np.arange(n), xs, ys)


def glottal(f0, rng, jitter=0.02, shimmer=0.12, doubling=0.0, creak=0.0, open_quotient=0.6):
    """Pulse train for a pitch curve f0 (Hz per sample). Returns (source, openness)."""
    n = len(f0)
    f = f0 * (1.0 + jitter * M.smooth_random(n, 45.0, rng, 1.0))
    phase = np.cumsum(f) / SR
    cycle = phase.astype(np.int64)
    frac = phase - cycle
    tp, tn = open_quotient * 0.65, open_quotient
    pulse = np.where(frac < tp, 0.5 * (1 - np.cos(np.pi * frac / tp)), np.where(frac < tn, np.cos(0.5 * np.pi * (frac - tp) / (tn - tp)), 0.0))
    src = np.diff(pulse, prepend=0.0)
    cycles = int(cycle[-1]) + 2
    amp = 1.0 + shimmer * rng.normal(0, 1, cycles)
    if doubling:
        amp[::2] *= 1.0 - doubling
    if creak:
        amp *= np.where(rng.random(cycles) < creak, rng.uniform(0.0, 0.3, cycles), 1.0)
    src *= amp[cycle]
    return src / (np.max(np.abs(src)) + 1e-9), (frac < tn).astype(float)


def resonator(x, freqs, bw, block=256):
    """A two-pole resonator whose centre follows freqs (Hz per sample), filtered in blocks."""
    n = len(x)
    out = np.zeros(n)
    zi = np.zeros(2)
    for s in range(0, n, block):
        e = min(n, s + block)
        f = float(np.clip(freqs[s], 60, SR * 0.45))
        r = np.exp(-np.pi * bw / SR)
        theta = 2 * np.pi * f / SR
        a = [1.0, -2 * r * np.cos(theta), r * r]
        b = [(1 - r * r) * 0.5]
        seg, zi = signal.lfilter(b, a, x[s:e], zi=zi)
        out[s:e] = seg
    return out


def tract(x, n, vowels, scale=1.0, widen=1.0, gains=(1.0, 0.55, 0.28, 0.12)):
    """Filter through four formants gliding between vowels (list of (fraction, vowel))."""
    out = np.zeros(n)
    widths = (90, 120, 180, 260)
    for i in range(4):
        track = contour(n, [(p, VOWELS[v][i] * scale) for p, v in vowels])
        out += resonator(x, track, widths[i] * widen) * gains[i]
    return out


def gurgle(n, rng, density=25.0, wet=1.0):
    """Bubbles bursting in a throat full of fluid."""
    out = np.zeros(n)
    count = int(n / SR * density)
    for _ in range(count):
        s = int(rng.integers(0, max(1, n - 2000)))
        length = int(rng.uniform(0.008, 0.035) * SR)
        t = np.arange(length) / SR
        f = rng.uniform(250, 1100) * (1 + 1.8 * t / (length / SR))
        out[s:s + length] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / rng.uniform(0.004, 0.012)) * rng.uniform(0.3, 1.0)
    return out * wet


def vocal(seconds, rng, f0_points, vowels, scale=1.0, jitter=0.02, shimmer=0.12, doubling=0.0, creak=0.0, breath=0.3, rasp=0.3,
          wet=0.2, rattle=0.0, drive=1.5, attack=0.1, release=0.3, widen=1.4, tremolo=0.0, inhale=False):
    """One vocalisation. f0_points: (fraction, Hz) contour; vowels: (fraction, vowel) glide."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    f0 = contour(n, f0_points)
    src, opening = glottal(f0, rng, jitter, shimmer, doubling, creak)
    noise = rng.normal(0, 1, n)
    aspiration = M.bandpass(noise, 300, 4500) * (0.35 + 0.65 * opening) * breath * 0.6
    rough = M.lowpass(noise, 3500, 2) * np.abs(src) * rasp * 1.6
    excitation = src + aspiration + rough
    voice = tract(excitation, n, vowels, scale, widen)
    # A slack, damaged throat: the voice sits low, its top dulled (not hiss over it).
    voice = M.lowpass(voice, 5200, 2) + M.bandpass(src, 90, 260, 2) * 0.15
    if inhale:  # an ingressive groan: more air, a higher tract, the folds barely meeting
        voice = voice * 0.6 + tract(M.bandpass(noise, 300, 5000) * breath * 2.5, n, vowels, scale * 1.1, widen * 1.3)
    if wet:
        voice += M.bandpass(gurgle(n, rng, 18 + 30 * wet, 1.0), 200, 2500) * wet * 0.35
    if rattle:
        voice *= 1.0 + rattle * np.sin(2 * np.pi * rng.uniform(22, 38) * t + rng.uniform(0, 6)) * (0.5 + 0.5 * M.smooth_random(n, 3.0, rng))
    if tremolo:
        voice *= 1.0 + tremolo * np.sin(2 * np.pi * rng.uniform(3.5, 6.0) * t)
    voice = np.tanh(voice / (np.max(np.abs(voice)) + 1e-9) * drive)
    e = np.clip(t / max(attack, 1e-3), 0, 1) ** 1.5 * np.clip((seconds - t) / max(release, 1e-3), 0, 1) ** 1.2
    if inhale:
        e = np.clip(t / (seconds * 0.85), 0, 1) ** 1.8 * np.clip((seconds - t) / 0.05, 0, 1)
    return M.highpass(voice * e, 60, 2)


def breath(seconds, rng, scale=1.0, wet=0.4, wheeze=0.2):
    """Laboured breathing: in, out, a wet crackle, sometimes a whistle in the airway."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    cycle = rng.uniform(1.1, 1.8)
    phase = (t / cycle) % 1.0
    flow = np.where(phase < 0.42, np.sin(np.pi * phase / 0.42) * 0.8, np.where(phase < 0.9, np.sin(np.pi * (phase - 0.42) / 0.48), 0.0))
    noise = rng.normal(0, 1, n)
    air = tract(noise, n, [(0, "uh"), (0.5, "aw"), (1, "uh")], scale, widen=2.5, gains=(1.0, 0.7, 0.4, 0.2)) * flow
    out = air + M.bandpass(gurgle(n, rng, 40, 1.0), 300, 3000) * wet * flow * 0.5
    if wheeze:
        w = M.bandpass(noise, 1400 * scale, 1500 * scale, 2) * flow * wheeze * 6
        out += w
    out = out / (np.max(np.abs(out)) + 1e-9)
    return M.highpass(out * np.clip(t / 0.05, 0, 1) * np.clip((seconds - t) / 0.2, 0, 1), 80, 2)


def hiss(seconds, rng, rattle=0.5):
    """The stalker: air forced through teeth, a dry rattle deep in the chest."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    noise = rng.normal(0, 1, n)
    s = M.bandpass(noise, 3500, 9000, 2) * 0.8 + M.bandpass(noise, 1800, 3000, 2) * 0.3
    clicks = (rng.random(n) < 0.004) * rng.uniform(0.3, 1.0, n)
    chest = M.bandpass(np.convolve(clicks, np.exp(-np.arange(200) / 25.0), mode="same"), 150, 900) * rattle * 3
    e = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 0.7
    out = (s + chest) * e
    return out / (np.max(np.abs(out)) + 1e-9)


def distant(x, rng, seed, distance=40.0):
    """Far away: the highs gone, the street's echo instead."""
    y = M.lowpass(x, 9000.0 / (1.0 + distance / 15.0), 2)
    ir = M.hall_ir(2.8, seed, predelay=0.04, brightness=0.5)[:, 0]
    wet = signal.fftconvolve(y, ir)
    out = np.zeros(len(wet))
    out[:len(y)] += y * 0.55
    out += wet * 0.6
    return out


def muffled(x, rng, seed):
    """Through a wall: only the body of the sound, and the room it is in."""
    y = M.lowpass(x, 650, 4)
    ir = M.hall_ir(0.6, seed, predelay=0.005, brightness=0.3)[:, 0]
    wet = signal.fftconvolve(y, ir)
    out = np.zeros(len(wet))
    out[:len(y)] += y * 0.8
    out += wet * 0.35
    return out


def normalize(x, peak=0.85, fade_ms=4.0):
    n = int(fade_ms / 1000 * SR)
    x = x.copy()
    if n and len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n * 4:] *= np.linspace(1, 0, n * 4)
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x
