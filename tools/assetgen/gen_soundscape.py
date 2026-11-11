"""The DEADLANDS soundscape (atmosphere overhaul): the world's occasional sounds and its loops.

One-shots are mono, because Minecraft only places mono sounds in space; the client's
AmbienceDirector puts them around the listener and works out how loud each one is from its
distance, so each is rendered as heard from a particular distance (filtered and given its outdoor
space here). Loops are stereo, seamless and streamed.

  crows        single calls (two to four caws), and flocks: a dozen birds, wings, overlapping calls
  birds_far    songbirds far off, sparse
  leaves       a gust through dry leaves          branch      a bough creaking under its weight
  sign_creak   a loose metal sign swinging         rattle      sheet metal shaken by the wind
  thunder_far  a storm rolling far away            dog         a dog barking a street away, or howling
  buzz         a failing light or a live wire      window_wind wind whistling through a broken pane
  settle       a building settling: creaks, a knock, dust
  unexplained  far off and hard to name: a door slamming, knocking, dragging, a cry, glass
  loops        wind in the trees, wind between buildings, rain on a roof, rain on metal, the hush of
               a room, a transformer, and the bed under the main menu

Everything is synthesised from noise and oscillators (no samples), with fixed seeds.
"""
from __future__ import annotations

import json

import numpy as np

import dl_data as D
import dl_music as M
from gen_audio import OUT, write_ogg

SR = M.SR


# ============================================================================= helpers

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def env(n, attack, release, curve=1.0):
    t = np.arange(n) / SR
    total = n / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1) ** curve
    r = np.clip((total - t) / max(release, 1e-4), 0, 1) ** curve
    return a * r


def place(out, x, start, gain=1.0):
    s = int(start * SR)
    e = min(len(out), s + len(x))
    if e > s:
        out[s:e] += x[:e - s] * gain


def outdoor(x, rng, distance, seed, wet=None):
    """As heard from `distance` blocks outside: air takes the highs, buildings and trees give a tail."""
    cutoff = 9000.0 / (1.0 + distance / 18.0)
    y = M.lowpass(x, max(900.0, cutoff), 2)
    ir = M.hall_ir(2.4, seed, predelay=0.02 + distance / 340.0, brightness=0.6)[:, 0]
    w = (0.18 + min(0.45, distance / 160.0)) if wet is None else wet
    tail = np.convolve(y, ir[: int(1.6 * SR)], mode="full") if len(y) < 4000 else _fft(y, ir)
    out = np.zeros(len(tail))
    out[:len(y)] += y * (1 - w * 0.5)
    out += tail * w
    return out


def _fft(x, ir):
    from scipy.signal import fftconvolve
    return fftconvolve(x, ir)


def finish(x, peak=0.8, fin=0.004, fout=0.05):
    x = M.highpass(x, 35, 2)
    nin, nout = int(fin * SR), int(fout * SR)
    if nin:
        x[:nin] *= np.linspace(0, 1, nin)
    if nout:
        x[-nout:] *= np.linspace(1, 0, nout)
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def pink(n, rng):
    x = rng.normal(0, 1, n)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[1:] /= np.sqrt(f[1:])
    y = np.fft.irfft(spec, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def brown(n, rng):
    y = np.cumsum(rng.normal(0, 1, n))
    y -= np.convolve(y, np.ones(4410) / 4410, mode="same")
    return y / (np.max(np.abs(y)) + 1e-9)


def seamless(x, loop_s, xfade_s):
    """x is (n, 2) with n >= loop + xfade; the tail is crossfaded into the head."""
    ls, xs = int(loop_s * SR), int(xfade_s * SR)
    body = x[xs:xs + ls].copy()
    c = np.sin(np.linspace(0, np.pi / 2, xs)) ** 2
    body[ls - xs:] = body[ls - xs:] * np.sqrt(1 - c)[:, None] + x[:xs] * np.sqrt(c)[:, None]
    return body


# ============================================================================= crows

def caw(rng, f0=None, length=None, harsh=1.0):
    """One crow caw: a harsh, noisy voice with two broad resonances and a falling end."""
    f0 = f0 or rng.uniform(470, 640)
    length = length or rng.uniform(0.26, 0.42)
    n = int(length * SR)
    t = np.arange(n) / SR
    u = t / length
    contour = 1.0 + 0.08 * np.sin(np.pi * np.clip(u * 1.6, 0, 1)) - 0.16 * np.clip((u - 0.6) / 0.4, 0, 1) ** 1.5
    jitter = M.smooth_random(n, 90.0, rng, 0.05)
    f = f0 * contour * (1 + jitter)
    phase = np.cumsum(f) / SR
    frac = phase % 1.0
    saw = 2 * frac - 1
    rough = 1.0 + 0.55 * harsh * np.sin(2 * np.pi * np.cumsum(f * 0.5) / SR)  # period doubling: the rasp
    src = saw * rough + rng.normal(0, 1, n) * 0.55 * harsh
    body = M.bandpass(src, 950, 1650, 2) * 1.0 + M.bandpass(src, 1900, 2900, 2) * 0.65 + M.bandpass(src, 3200, 4200, 2) * 0.2
    e = np.clip(t / 0.018, 0, 1) * np.clip((length - t) / 0.09, 0, 1) ** 1.3 * (1 + 0.25 * np.sin(np.pi * u))
    return body * e


def crow_call(rng, seed, distance):
    count = int(rng.choice([1, 2, 2, 3, 3, 4]))
    f0 = rng.uniform(470, 640)
    pieces, t = [], 0.0
    out = np.zeros(int(3.2 * SR))
    for k in range(count):
        c = caw(rng, f0 * rng.uniform(0.96, 1.04), rng.uniform(0.26, 0.4) * (1.15 if k == count - 1 else 1.0))
        place(out, c, t, rng.uniform(0.8, 1.0))
        t += len(c) / SR + rng.uniform(0.18, 0.42)
    out = out[: int((t + 0.2) * SR)]
    return finish(outdoor(out, rng, distance, seed))


def wings(rng, seconds, rate=7.5):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    flap = np.clip(np.sin(2 * np.pi * rate * t + rng.uniform(0, 6)), 0, 1) ** 3
    return M.bandpass(rng.normal(0, 1, n), 300, 2500) * flap * env(n, 0.3, 1.2)


def crow_flock(rng, seed):
    """A flock lifting off a rooftop: wings, then a dozen birds calling over each other as they settle."""
    seconds = 9.0
    out = np.zeros(int(seconds * SR))
    place(out, wings(rng, 2.6, 8.0) * 0.5, 0.2)
    place(out, wings(rng, 2.2, 6.5) * 0.35, 0.6)
    for _ in range(int(rng.integers(10, 16))):
        when = rng.uniform(0.3, 7.0)
        call = np.zeros(int(1.6 * SR))
        f0 = rng.uniform(440, 680)
        t = 0.0
        for k in range(int(rng.integers(1, 4))):
            c = caw(rng, f0, rng.uniform(0.22, 0.38))
            place(call, c, t)
            t += len(c) / SR + rng.uniform(0.12, 0.3)
        d = rng.uniform(0.4, 1.0)
        place(out, M.lowpass(call, 3000 + 4000 * d, 2) * d, when)
    return finish(outdoor(out, rng, 45.0, seed, wet=0.35), peak=0.85, fout=0.8)


def birds_far(rng, seed):
    """A few songbird phrases a long way off: whistles, a trill."""
    seconds = rng.uniform(2.5, 4.0)
    out = np.zeros(int(seconds * SR))
    t = 0.0
    while t < seconds - 0.6:
        kind = rng.integers(3)
        if kind == 0:  # a falling whistle
            n = int(rng.uniform(0.12, 0.3) * SR)
            tt = np.arange(n) / SR
            f = rng.uniform(2600, 4200) * (1 - 0.35 * tt / (n / SR))
            x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * tt / (n / SR))
        elif kind == 1:  # a trill
            n = int(rng.uniform(0.3, 0.6) * SR)
            tt = np.arange(n) / SR
            f = rng.uniform(3000, 5000) + 400 * np.sin(2 * np.pi * rng.uniform(18, 30) * tt)
            x = np.sin(2 * np.pi * np.cumsum(f) / SR) * (0.5 + 0.5 * np.sin(2 * np.pi * 22 * tt)) * np.sin(np.pi * tt / (n / SR))
        else:  # two notes
            n = int(0.35 * SR)
            tt = np.arange(n) / SR
            f = np.where(tt < 0.15, rng.uniform(3000, 3600), rng.uniform(2200, 2800))
            x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * np.clip(tt / 0.15, 0, 1)) * np.where(tt < 0.15, 1, np.sin(np.pi * (tt - 0.15) / 0.2))
        place(out, x * rng.uniform(0.4, 1.0), t)
        t += n / SR + rng.uniform(0.15, 0.9)
    return finish(outdoor(out, rng, 45.0, seed), peak=0.6)


# ============================================================================= wind, trees, metal

def leaves(rng, seed):
    """A gust through dry leaves: thousands of tiny ticks riding a swell."""
    seconds = rng.uniform(2.0, 3.6)
    n = int(seconds * SR)
    swell = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** 1.5
    density = 0.004 + 0.03 * swell
    ticks = (rng.random(n) < density) * rng.uniform(0.2, 1.0, n)
    k = np.exp(-np.arange(int(0.003 * SR)) / (0.0006 * SR))
    crackle = M.highpass(np.convolve(ticks, k, mode="same") * rng.choice([-1, 1], n), 1800, 2)
    air = M.bandpass(pink(n, rng), 300, 2500) * swell * 0.4
    return finish(outdoor(crackle * 0.8 + air, rng, 10.0, seed, wet=0.15))


def friction(rng, seconds, rate_lo, rate_hi, modes, decay=0.004, wander=0.4):
    """Stick-slip: an irregular train of grabs exciting a few resonances (wood or metal)."""
    n = int(seconds * SR)
    rate = rate_lo + (rate_hi - rate_lo) * (0.5 + 0.5 * M.smooth_random(n, 0.8, rng, 1.0))
    phase = np.cumsum(rate) / SR
    pulses = np.diff(np.floor(phase), prepend=0) > 0
    impulses = pulses * rng.uniform(0.5, 1.0, n)
    out = np.zeros(n)
    for f, q, g in modes:
        f2 = f * (1 + wander * 0.05 * M.smooth_random(n, 0.5, rng))
        k_len = int(decay * q * SR / 10) + 32
        kt = np.arange(k_len) / SR
        kernel = np.exp(-kt * f / q) * np.sin(2 * np.pi * float(np.mean(f2)) * kt)
        out += np.convolve(impulses, kernel, mode="same") * g
    return out


def branch(rng, seed):
    seconds = rng.uniform(1.4, 2.6)
    x = friction(rng, seconds, 18, 55, [(260, 18, 1.0), (640, 22, 0.6), (1300, 25, 0.3)], wander=1.0)
    x *= env(len(x), 0.3, 0.6, 1.5)
    return finish(outdoor(x, rng, 16.0, seed))


def sign_creak(rng, seed):
    """A sign on one bolt: a slow metallic squeal, a swing back, sometimes a knock on the post."""
    seconds = rng.uniform(1.8, 3.2)
    modes = [(rng.uniform(380, 520), 120, 1.0), (rng.uniform(900, 1200), 160, 0.6), (rng.uniform(1800, 2400), 200, 0.35), (3100, 220, 0.15)]
    x = friction(rng, seconds, 6, 26, modes, decay=0.008, wander=1.5)
    x *= env(len(x), 0.25, 0.5, 1.2)
    if rng.random() < 0.6:
        knock = metal_hit(rng, rng.uniform(140, 220), 0.5) * 0.6
        place(x, knock, seconds * 0.85)
    return finish(outdoor(x, rng, 18.0, seed))


def metal_hit(rng, base, decay):
    n = int(decay * 2.5 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for ratio, amp in ((1.0, 1.0), (2.76, 0.6), (5.4, 0.35), (8.93, 0.2), (13.3, 0.1)):
        x += amp * np.sin(2 * np.pi * base * ratio * rng.uniform(0.98, 1.02) * t) * np.exp(-t / (decay / ratio ** 0.4))
    x[: int(0.003 * SR)] += rng.normal(0, 1, int(0.003 * SR)) * 0.6
    return x


def rattle(rng, seed):
    """Loose sheet metal in the wind: a flurry of light knocks over a low flexing wobble."""
    seconds = rng.uniform(1.4, 2.8)
    n = int(seconds * SR)
    out = np.zeros(n)
    t = 0.05
    burst_end = rng.uniform(0.5, 1.0)
    while t < seconds - 0.2:
        place(out, metal_hit(rng, rng.uniform(600, 1400), 0.06) * rng.uniform(0.3, 1.0), t)
        t += rng.uniform(0.03, 0.12) if t < burst_end or rng.random() < 0.6 else rng.uniform(0.25, 0.5)
    tt = np.arange(n) / SR
    wobble = np.sin(2 * np.pi * rng.uniform(70, 120) * tt) * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(5, 9) * tt)) * env(n, 0.2, 0.8)
    return finish(outdoor(out + wobble * 0.25, rng, 15.0, seed))


def thunder_far(rng, seed):
    seconds = rng.uniform(6.0, 9.0)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    rumble = M.lowpass(brown(n, rng), 220, 2)
    shape = np.zeros(n)
    for _ in range(int(rng.integers(3, 6))):
        c, w = rng.uniform(0.3, seconds * 0.7), rng.uniform(0.4, 1.4)
        shape += rng.uniform(0.4, 1.0) * np.exp(-((t - c) / w) ** 2)
    shape = np.maximum(shape, 0.15 * np.exp(-t / (seconds * 0.6)))
    crack = M.bandpass(rng.normal(0, 1, n), 300, 1500) * np.exp(-np.maximum(t - 0.25, 0) / 0.25) * (t > 0.25) * 0.25
    x = rumble * shape + crack
    return finish(outdoor(x, rng, 200.0, seed, wet=0.5), peak=0.85, fout=1.5)


def bark(rng, f0):
    n = int(rng.uniform(0.12, 0.2) * SR)
    t = np.arange(n) / SR
    f = f0 * (1.15 - 0.4 * t / (n / SR))
    phase = np.cumsum(f) / SR
    src = (2 * (phase % 1.0) - 1) + rng.normal(0, 1, n) * 0.5
    voiced = M.bandpass(src, 600, 1100, 2) + M.bandpass(src, 1400, 2200, 2) * 0.6 + M.bandpass(src, 250, 450, 2) * 0.5
    return voiced * np.clip(t / 0.008, 0, 1) * np.exp(-t / 0.07)


def dog(rng, seed):
    """A dog a long way off: a few barks with the street's echo, or now and then a howl."""
    out = np.zeros(int(4.0 * SR))
    if rng.random() < 0.2:
        n = int(2.2 * SR)
        t = np.arange(n) / SR
        f = rng.uniform(480, 620) * (1 + 0.12 * np.sin(np.pi * t / 2.2) + 0.012 * np.sin(2 * np.pi * 5.5 * t))
        phase = np.cumsum(f) / SR
        howl = M.bandpass(np.sin(2 * np.pi * phase) + 0.4 * np.sin(4 * np.pi * phase) + rng.normal(0, 1, n) * 0.1, 350, 2200)
        place(out, howl * np.sin(np.pi * t / 2.2) ** 0.7, 0.1)
    else:
        f0 = rng.uniform(380, 560)
        t = 0.1
        for _ in range(int(rng.integers(2, 6))):
            place(out, bark(rng, f0 * rng.uniform(0.95, 1.05)), t, rng.uniform(0.7, 1.0))
            t += rng.uniform(0.28, 0.75)
    echo = np.zeros(len(out) + int(0.4 * SR))
    echo[:len(out)] += out
    echo[int(0.33 * SR):int(0.33 * SR) + len(out)] += M.lowpass(out, 1200, 2) * 0.35
    return finish(outdoor(echo, rng, 90.0, seed, wet=0.4), fout=0.6)


def buzz(rng, seed):
    """Mains hum through a failing fitting: buzzing that swells, arcs, and cuts out."""
    seconds = rng.uniform(1.6, 3.0)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    hum = sum(np.sin(2 * np.pi * 60 * k * t + rng.uniform(0, 6)) / k ** 0.7 for k in range(2, 14))
    hum = np.tanh(hum * 1.5)
    flutter = (rng.random(n // 441 + 1) < 0.85).repeat(441)[:n].astype(float)
    flutter = np.convolve(flutter, np.ones(200) / 200, mode="same")
    arcs = (rng.random(n) < 0.0015) * rng.uniform(0.5, 1.0, n)
    arcs = M.highpass(np.convolve(arcs, np.exp(-np.arange(300) / 40.0), mode="same") * rng.choice([-1, 1], n), 2500)
    x = hum * flutter * 0.35 + arcs * 0.6
    return finish(outdoor(x * env(n, 0.05, 0.1), rng, 10.0, seed, wet=0.12), peak=0.6)


def window_wind(rng, seed):
    """Wind finding a broken pane: a thin, wandering whistle over a breath of air."""
    seconds = rng.uniform(3.0, 5.0)
    n = int(seconds * SR)
    noise = pink(n, rng)
    centre = rng.uniform(650, 1250) * (1 + 0.18 * M.smooth_random(n, 0.6, rng))
    out = np.zeros(n)
    blocks = 80
    step = n // blocks + 1
    for b in range(blocks):
        s, e = b * step, min(n, (b + 1) * step)
        if s >= e:
            break
        c = float(centre[s])
        seg = M.bandpass(noise[max(0, s - 1500):e], c * 0.97, c * 1.03, 2)[-(e - s):]
        out[s:e] = seg
    swell = np.sin(np.pi * np.arange(n) / n) ** 1.2
    air = M.bandpass(noise, 200, 1500) * 0.25
    x = (out * 5.0 + air) * swell
    return finish(outdoor(x, rng, 6.0, seed, wet=0.3), peak=0.65, fout=0.4)


def settle(rng, seed):
    """A house settling: a slow creak in the boards, a knock, a trickle of dust."""
    seconds = rng.uniform(1.6, 2.8)
    n = int(seconds * SR)
    x = friction(rng, seconds * 0.6, 10, 30, [(180, 15, 1.0), (450, 20, 0.5), (900, 25, 0.25)], wander=0.8)
    x = np.concatenate([x * env(len(x), 0.2, 0.4), np.zeros(n - len(x))])
    if rng.random() < 0.6:
        knock_n = int(0.25 * SR)
        kt = np.arange(knock_n) / SR
        knock = np.sin(2 * np.pi * rng.uniform(90, 160) * kt) * np.exp(-kt / 0.04) + M.lowpass(rng.normal(0, 1, knock_n), 900) * np.exp(-kt / 0.01)
        place(x, knock * 0.6, rng.uniform(0.6, seconds - 0.4))
    dust = (rng.random(n) < 0.002) * rng.uniform(0, 1, n) * np.clip((np.arange(n) / SR - seconds * 0.5) / 0.3, 0, 1)
    x += M.highpass(np.convolve(dust, np.exp(-np.arange(80) / 10.0), mode="same"), 3000) * 0.3
    return finish(outdoor(x, rng, 6.0, seed, wet=0.25))


def unexplained(rng, seed, kind):
    """Far off and hard to name."""
    if kind == 0:  # a heavy door slamming somewhere in the town
        x = np.zeros(int(1.2 * SR))
        ring = metal_hit(rng, 70, 0.35)
        thud = M.lowpass(rng.normal(0, 1, len(ring)), 600) * np.exp(-np.arange(len(ring)) / SR / 0.04)
        place(x, ring + thud, 0.0)
        return finish(outdoor(x, rng, 70.0, seed, wet=0.55), fout=1.0)
    if kind == 1:  # three slow knocks
        x = np.zeros(int(3.2 * SR))
        for k in range(3):
            n = int(0.3 * SR)
            kt = np.arange(n) / SR
            place(x, np.sin(2 * np.pi * 85 * kt) * np.exp(-kt / 0.06) + M.lowpass(rng.normal(0, 1, n), 700) * np.exp(-kt / 0.012), 0.2 + k * 0.85)
        return finish(outdoor(x, rng, 40.0, seed, wet=0.45), fout=0.8)
    if kind == 2:  # something heavy dragged across a floor
        n = int(rng.uniform(1.8, 2.8) * SR)
        x = friction(rng, n / SR, 30, 90, [(120, 10, 1.0), (300, 12, 0.6), (700, 14, 0.3)], wander=1.2) * env(n, 0.2, 0.5)
        x += M.bandpass(rng.normal(0, 1, n), 200, 1200) * env(n, 0.2, 0.5) * 0.3
        return finish(outdoor(x, rng, 35.0, seed, wet=0.45))
    if kind == 3:  # a cry, a long way off: a person, an animal, or neither
        n = int(1.6 * SR)
        t = np.arange(n) / SR
        f = rng.uniform(380, 520) * (1 + 0.25 * np.sin(np.pi * t / 1.6)) * (1 + 0.01 * np.sin(2 * np.pi * 6 * t))
        phase = np.cumsum(f) / SR
        src = (2 * (phase % 1.0) - 1) + rng.normal(0, 1, n) * 0.2
        x = (M.bandpass(src, 700, 1100, 2) + M.bandpass(src, 1500, 2300, 2) * 0.5) * np.sin(np.pi * t / 1.6) ** 0.8
        return finish(outdoor(x, rng, 110.0, seed, wet=0.6), peak=0.6, fout=1.0)
    # glass giving way in a distant building
    x = np.zeros(int(1.5 * SR))
    for _ in range(int(rng.integers(12, 24))):
        place(x, metal_hit(rng, rng.uniform(2500, 5000), 0.03) * rng.uniform(0.2, 1.0), rng.uniform(0.0, 0.5) ** 2)
    return finish(outdoor(x, rng, 60.0, seed, wet=0.5), fout=0.8)


# ============================================================================= loops (stereo)

LOOP = 36.0
XFADE = 4.0


def wind_layer(n, rng, lo, hi, rate):
    left = M.bandpass(pink(n, rng), lo, hi, 2)
    right = M.bandpass(pink(n, rng), lo, hi, 2)
    g = 0.55 + 0.45 * M.smooth_random(n, rate, rng)
    gl = np.clip(g + 0.1 * M.smooth_random(n, rate * 2, rng), 0.05, 1.5)
    gr = np.clip(g + 0.1 * M.smooth_random(n, rate * 2, rng), 0.05, 1.5)
    return np.stack([left * gl, right * gr], axis=1), g


def wind_trees_loop(rng):
    n = int((LOOP + XFADE) * SR)
    wind, gust = wind_layer(n, rng, 180, 2400, 0.12)
    ticks = (rng.random((n, 2)) < (0.002 + 0.02 * np.clip(gust, 0, 1.5))[:, None]) * rng.uniform(0.2, 1.0, (n, 2))
    k = np.exp(-np.arange(int(0.003 * SR)) / (0.0006 * SR))
    leaves_ = np.stack([np.convolve(ticks[:, c], k, mode="same") for c in range(2)], axis=1) * rng.choice([-1, 1], (n, 2))
    leaves_ = M.highpass(leaves_, 2000, 2)
    return wind * 0.7 + leaves_ * 0.5


def wind_street_loop(rng):
    n = int((LOOP + XFADE) * SR)
    low, gust = wind_layer(n, rng, 90, 1100, 0.09)
    whistle = np.zeros((n, 2))
    for c in range(2):
        noise = pink(n, rng)
        centre = rng.uniform(750, 1050) * (1 + 0.12 * M.smooth_random(n, 0.4, rng))
        blocks = 120
        step = n // blocks + 1
        for b in range(blocks):
            s, e = b * step, min(n, (b + 1) * step)
            if s >= e:
                break
            cc = float(centre[s])
            whistle[s:e, c] = M.bandpass(noise[max(0, s - 1500):e], cc * 0.985, cc * 1.015, 2)[-(e - s):]
    howl = whistle * (np.clip(gust - 0.6, 0, 1) ** 1.5)[:, None] * 3.0
    return low * 0.8 + howl


def rain_layer(n, rng, rate, lo, hi, ring=None):
    out = np.zeros((n, 2))
    for c in range(2):
        drops = (rng.random(n) < rate / SR) * rng.uniform(0.1, 1.0, n) ** 2
        if ring is None:
            k = np.exp(-np.arange(int(0.004 * SR)) / (0.0007 * SR))
            out[:, c] = M.bandpass(np.convolve(drops, k, mode="same") * rng.choice([-1, 1], n), lo, hi, 2)
        else:
            kt = np.arange(int(0.06 * SR)) / SR
            kernel = sum(np.sin(2 * np.pi * f * kt) * np.exp(-kt / d) * g for f, d, g in ring)
            out[:, c] = M.highpass(np.convolve(drops, kernel, mode="same"), lo, 2)
    return out


def rain_roof_loop(rng):
    n = int((LOOP + XFADE) * SR)
    patter = rain_layer(n, rng, 900, 400, 3000)
    wash = np.stack([M.bandpass(pink(n, rng), 200, 1600) for _ in range(2)], axis=1) * 0.35
    gutter = np.zeros((n, 2))
    t = 0.5
    while t < LOOP + XFADE - 1:
        m = int(0.08 * SR)
        mt = np.arange(m) / SR
        drip = np.sin(2 * np.pi * rng.uniform(500, 900) * mt * (1 + 2 * mt)) * np.exp(-mt / 0.02)
        place(gutter[:, 0 if rng.random() < 0.5 else 1], drip * 0.5, t)
        t += rng.uniform(0.3, 1.2)
    return M.lowpass(patter * 0.8 + wash + gutter, 3200, 2)


def rain_metal_loop(rng):
    n = int((LOOP + XFADE) * SR)
    rings = [(rng.uniform(1800, 2600), 0.012, 1.0), (rng.uniform(3100, 4200), 0.008, 0.6), (rng.uniform(5200, 6400), 0.005, 0.3),
             (rng.uniform(700, 1000), 0.02, 0.4)]
    tink = rain_layer(n, rng, 700, 500, 9000, ring=rings)
    drum = np.stack([M.bandpass(pink(n, rng), 150, 900) for _ in range(2)], axis=1) * 0.3
    return M.lowpass(tink * 0.6 + drum, 7000, 2)


def room_tone_loop(rng):
    n = int((LOOP + XFADE) * SR)
    air = np.stack([M.bandpass(pink(n, rng), 60, 500) for _ in range(2)], axis=1)
    t = np.arange(n) / SR
    hum = np.sin(2 * np.pi * 50 * t) * 0.05 + np.sin(2 * np.pi * 100 * t) * 0.03
    return air * 0.5 + hum[:, None] * (0.6 + 0.4 * M.smooth_random(n, 0.1, rng))[:, None]


def transformer_loop(rng):
    n = int((LOOP + XFADE) * SR)
    t = np.arange(n) / SR
    tone = sum(np.sin(2 * np.pi * 60 * k * t + k) / k for k in (2, 4, 6, 8, 10)) * (1 + 0.08 * M.smooth_random(n, 0.3, rng))
    crackle = (rng.random(n) < 0.0004) * rng.uniform(0.2, 1, n)
    crackle = M.highpass(np.convolve(crackle, np.exp(-np.arange(200) / 30.0), mode="same"), 3000)
    mono = np.tanh(tone * 0.8) * 0.4 + crackle * 0.25
    return np.stack([mono, np.roll(mono, 37)], axis=1)


def menu_loop(rng):
    """Under the main menu: wind through a dead street, crows somewhere on the skyline, far metal."""
    seconds = 48.0
    n = int((seconds + XFADE) * SR)
    low, gust = wind_layer(n, rng, 120, 1400, 0.07)
    out = low * 0.6
    for when, pan in ((7.0, 0.6), (21.5, -0.5), (34.0, 0.3)):
        call = crow_call(rng, 7700 + int(when), 60.0) * 0.35
        st = M.pan2(call, pan)
        out[int(when * SR):int(when * SR) + len(st)] += st[: max(0, min(len(st), n - int(when * SR)))]
    hit = outdoor(metal_hit(rng, 180, 0.5), rng, 90.0, 7799, wet=0.6) * 0.15
    st = M.pan2(hit, -0.7)
    out[int(15 * SR):int(15 * SR) + len(st)] += st
    return seamless(out, seconds, XFADE)


# ============================================================================= build

def one_shots():
    rng = np.random.default_rng(77001)
    return {
        "crow": [crow_call(rng, 77010 + i, d) for i, d in enumerate((30, 45, 60, 38, 75, 52))],
        "crow_flock": [crow_flock(rng, 77050 + i) for i in range(3)],
        "birds_far": [birds_far(rng, 77060 + i) for i in range(4)],
        "leaves": [leaves(rng, 77070 + i) for i in range(5)],
        "branch": [branch(rng, 77080 + i) for i in range(4)],
        "sign_creak": [sign_creak(rng, 77090 + i) for i in range(4)],
        "rattle": [rattle(rng, 77100 + i) for i in range(5)],
        "thunder_far": [thunder_far(rng, 77110 + i) for i in range(3)],
        "dog": [dog(rng, 77120 + i) for i in range(5)],
        "buzz": [buzz(rng, 77130 + i) for i in range(3)],
        "window_wind": [window_wind(rng, 77140 + i) for i in range(3)],
        "settle": [settle(rng, 77150 + i) for i in range(4)],
        "unexplained": [unexplained(rng, 77160 + i, i) for i in range(5)],
    }


LOOPS = {"wind_trees_loop": wind_trees_loop, "wind_street_loop": wind_street_loop, "rain_roof_loop": rain_roof_loop,
         "rain_metal_loop": rain_metal_loop, "room_tone_loop": room_tone_loop, "transformer_loop": transformer_loop, "menu_loop": menu_loop}
SUBTITLES = {"crow": "Crows caw", "crow_flock": "A flock of crows", "birds_far": "Birds call far away", "leaves": "Leaves rustle",
             "branch": "A branch creaks", "sign_creak": "A sign creaks", "rattle": "Loose metal rattles", "thunder_far": "Thunder rumbles far away",
             "dog": "A dog barks in the distance", "buzz": "Electricity buzzes", "window_wind": "Wind whistles through glass",
             "settle": "The building creaks", "unexplained": "Something in the distance"}


def build() -> None:
    path = OUT.parent / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    for name, variants in one_shots().items():
        files = []
        for i, audio in enumerate(variants):
            rel = f"ambient/scape/{name}_{i + 1}"
            write_ogg(rel, audio.astype(np.float32), quality=4)
            files.append(f"deadlands:{rel}")
        sounds[f"ambient.{name}"] = {"sounds": files, "subtitle": f"subtitles.deadlands.ambient.{name}"}
    for k, (name, make) in enumerate(LOOPS.items()):
        rng = np.random.default_rng(78000 + k)
        audio = make(rng) if name == "menu_loop" else seamless(make(rng), LOOP, XFADE)
        peak = np.max(np.abs(audio))
        audio = audio * (0.7 / peak) if peak > 0 else audio
        rel = f"ambient/scape/{name}"
        write_ogg(rel, audio.astype(np.float32), quality=3)
        sounds[f"ambient.{name}"] = {"sounds": [{"name": f"deadlands:{rel}", "stream": True}]}
    path.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang({f"subtitles.deadlands.ambient.{k}": v for k, v in SUBTITLES.items()})


if __name__ == "__main__":
    build()
    print("soundscape written")
