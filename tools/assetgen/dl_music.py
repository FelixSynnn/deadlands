"""DEADLANDS music synthesis: modelled instruments, a score, a concert-hall space and mastering.

Everything is synthesised from oscillators and noise (no samples), so every track is original:

* piano      additive strings with inharmonic partials (string stiffness), a hammer strike that
             shapes the spectrum (strike position and velocity), two-stage decay, beating between
             the unison strings, hammer noise and a damper on release;
* strings    a bowed ensemble: several band-limited sawtooth players, each with its own tuning,
             drift and delayed vibrato, a bow-noise layer, and a body resonance on the bus;
* pad        slow, wide, soft-edged chords;
* choir      a formant-filtered "oo/aa" ensemble, very quiet, for the night;
* guitar     plucked steel strings (Karplus-Strong at the exact pitch) through a wooden body;
* bell       inharmonic struck metal, and the same partials bowed;
* drone      a low bowed tone with a slowly moving filter;
* drums      deep cinematic drums, ticks, heartbeats and a sub drop.

``Score`` collects notes per bus with humanised timing; ``render`` mixes the buses through the
hall and ``master`` levels the result (high-pass, gentle compression, peak limiter, loudness).
"""
from __future__ import annotations

import numpy as np
from scipy import signal

SR = 44100
NOTE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8,
        "A": 9, "A#": 10, "Bb": 10, "B": 11}


def m(name) -> int:
    """'D4' -> MIDI note number (C4 = 60). MIDI numbers pass through unchanged."""
    if not isinstance(name, str):
        return int(name)
    head = name[:-1] if name[-1].isdigit() and not name[-2].isdigit() else name[:-2]
    octave = int(name[len(head):])
    return 12 * (octave + 1) + NOTE[head]


def hz(note) -> float:
    midi = m(note) if isinstance(note, str) else note
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def chord(*names: str) -> list[int]:
    return [m(n) for n in names]


# ============================================================================= filters

def _sos(kind, freq, order=2):
    return signal.butter(order, freq, btype=kind, fs=SR, output="sos")


def lowpass(x, f, order=2):
    return signal.sosfilt(_sos("lowpass", min(f, SR * 0.45), order), x, axis=0)


def highpass(x, f, order=2):
    return signal.sosfilt(_sos("highpass", f, order), x, axis=0)


def bandpass(x, lo, hi, order=2):
    lo, hi = max(20.0, lo), min(hi, SR * 0.45)
    if hi <= lo * 1.05:
        hi = lo * 1.05
    return signal.sosfilt(_sos("bandpass", [lo, hi], order), x, axis=0)


def peak_eq(x, f, gain_db, q=1.0):
    """RBJ peaking equaliser."""
    a = 10 ** (gain_db / 40.0)
    w = 2 * np.pi * f / SR
    alpha = np.sin(w) / (2 * q)
    b = [1 + alpha * a, -2 * np.cos(w), 1 - alpha * a]
    den = [1 + alpha / a, -2 * np.cos(w), 1 - alpha / a]
    return signal.lfilter(np.array(b) / den[0], np.array(den) / den[0], x, axis=0)


def shelf_high(x, f, gain_db):
    """RBJ high shelf (slope 1)."""
    a = 10 ** (gain_db / 40.0)
    w = 2 * np.pi * f / SR
    alpha = np.sin(w) / 2 * np.sqrt(2)
    c = np.cos(w)
    sa = 2 * np.sqrt(a) * alpha
    b = [a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa)]
    den = [(a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa]
    return signal.lfilter(np.array(b) / den[0], np.array(den) / den[0], x, axis=0)


def smooth_random(n, rate_hz, rng, depth=1.0):
    """A slowly wandering random curve (about -1..1 times depth): random knots rate_hz apart, joined
    by raised-cosine segments. O(n), so it is cheap even for whole tracks."""
    pos = np.arange(n) * (rate_hz / SR)
    i = pos.astype(np.int64)
    knots = rng.normal(0, 0.7, int(i[-1]) + 3 if n else 3)
    w = (1.0 - np.cos(np.pi * (pos - i))) * 0.5
    return (knots[i] * (1.0 - w) + knots[i + 1] * w) * depth


def pan2(mono, pan):
    """Constant-power pan: -1 left, +1 right."""
    a = (pan + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)], axis=1)


def env_ar(n, attack, release, total_hold, curve=2.0):
    """Attack (s), hold until total_hold (s), release (s), all as raised-sine segments."""
    t = np.arange(n) / SR
    rise = np.clip(t / max(attack, 1e-4), 0, 1)
    rise = np.sin(rise * np.pi / 2) ** curve
    fall = np.clip(1 - (t - total_hold) / max(release, 1e-4), 0, 1)
    fall = np.sin(fall * np.pi / 2) ** curve
    return rise * fall


# ============================================================================= instruments

def piano(freq, dur, vel, rng, release=0.3, sustain=1.0):
    """A struck string group. dur = how long the key (or pedal) is held."""
    t60 = float(np.clip(13.0 * (70.0 / freq) ** 0.55, 1.0, 18.0)) * sustain
    length = min(dur + release * 4.0, t60 * 1.15)
    length = max(length, min(dur, t60) + 0.05)
    n = int(length * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    b = 0.00011 * (freq / 261.6) ** 0.9
    x0 = 0.118 + rng.uniform(-0.012, 0.012)
    slope = 2.35 - 1.25 * vel
    parts = int(min(46, 9500.0 / freq))
    for k in range(1, parts + 1):
        fk = k * freq * np.sqrt(1 + b * k * k)
        if fk > 15500:
            break
        ak = k ** -slope * (0.22 + abs(np.sin(np.pi * k * x0)))
        tk = t60 / (1.0 + 0.33 * (k - 1) ** 1.12 * (freq / 220.0) ** 0.28)
        last = min(n, int(tk * 1.6 * SR) + 1)
        tt = t[:last]
        env = 0.72 * np.exp(-6.9 * tt / tk) + 0.28 * np.exp(-6.9 * tt / (tk * 2.7))
        beat = 1.0 + 0.16 * np.cos(2 * np.pi * (0.25 + 0.11 * k * rng.uniform(0.7, 1.3)) * tt + rng.uniform(0, 6.28))
        out[:last] += ak * env * beat * np.sin(2 * np.pi * fk * tt + rng.uniform(0, 6.28))
    damper = np.where(t < dur, 1.0, np.exp(-(t - dur) / max(release, 0.02)))
    out *= damper
    hn = int(0.014 * SR)
    hammer = rng.normal(0, 1, hn) * np.exp(-np.linspace(0, 7, hn))
    hammer = bandpass(hammer, min(freq * 2.0, 7000), min(freq * 14.0, 15000))
    out[:hn] += hammer * 0.035 * (0.5 + vel)
    att = int(0.0015 * SR)
    out[:att] *= np.linspace(0, 1, att)
    return out * vel ** 1.5


def strings(freq, dur, vel, rng, attack=0.7, release=1.8, voices=4, bright=0.55, vibrato=0.0045, swell=0.0):
    """A bowed section holding one note for dur seconds."""
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    parts = int(min(26, 8200.0 / freq))
    k = np.arange(1, parts + 1)
    tilt = (1.0 / k) * np.exp(-k / max(1.0, parts * bright))
    for _ in range(voices):
        detune = rng.normal(0, 0.0028)
        rate = 5.1 + rng.uniform(-0.45, 0.45)
        onset = np.clip((t - 0.3 - rng.uniform(0, 0.3)) / 0.9, 0, 1)
        vib = vibrato * onset * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.28))
        drift = smooth_random(n, 0.7, rng, 0.0016)
        inst = freq * (1 + detune + vib + drift)
        phase = 2 * np.pi * np.cumsum(inst) / SR + rng.uniform(0, 6.28)
        voice = np.zeros(n)
        for kk, a in zip(k, tilt):
            voice += a * np.sin(kk * phase)
        out += voice * (0.85 + 0.3 * rng.random())
    env = env_ar(n, attack, release, dur)
    if swell:
        env *= 1.0 + swell * np.sin(np.clip(t / max(dur, 0.1), 0, 1) * np.pi)
    expr = 1.0 + smooth_random(n, 0.25, rng, 0.06)
    bow = bandpass(rng.normal(0, 1, n), freq * 1.5, min(freq * 9, 11000)) * 0.05
    out = (out / voices + bow) * env * expr
    return out * vel


def pad(freq, dur, vel, rng, attack=2.5, release=3.0, bright=0.3, voices=5, spread=0.006):
    """Soft, wide chord tone: detuned soft saws and a sine core."""
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    parts = int(min(14, 5000.0 / freq))
    k = np.arange(1, parts + 1)
    tilt = (1.0 / k ** 1.4) * np.exp(-k / max(1.0, parts * bright))
    out = np.zeros(n)
    for v in range(voices):
        det = (v - (voices - 1) / 2) * spread / max(1, voices - 1) * 2 + rng.normal(0, 0.0008)
        drift = smooth_random(n, 0.3, rng, 0.001)
        phase = 2 * np.pi * np.cumsum(freq * (1 + det + drift)) / SR + rng.uniform(0, 6.28)
        for kk, a in zip(k, tilt):
            out += a * np.sin(kk * phase)
    out /= voices
    out += 0.6 * np.sin(2 * np.pi * freq * t)
    return out * env_ar(n, attack, release, dur) * vel


def choir(freq, dur, vel, rng, vowel="oo", attack=2.0, release=2.5, voices=6):
    """A quiet wordless ensemble: buzzing sources through vowel formants."""
    formants = {"oo": [(320, 80, 1.0), (800, 110, 0.35), (2400, 200, 0.06)],
                "aa": [(700, 110, 1.0), (1150, 130, 0.55), (2800, 220, 0.12)],
                "mm": [(250, 70, 1.0), (1000, 200, 0.05), (2500, 300, 0.02)]}[vowel]
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    src = np.zeros(n)
    for _ in range(voices):
        det = rng.normal(0, 0.004)
        vib = 0.005 * np.sin(2 * np.pi * (4.6 + rng.uniform(-0.5, 0.5)) * t + rng.uniform(0, 6.28)) * np.clip((t - 0.5) / 1.0, 0, 1)
        phase = 2 * np.pi * np.cumsum(freq * (1 + det + vib + smooth_random(n, 0.5, rng, 0.0015))) / SR
        frac = (phase / (2 * np.pi)) % 1.0
        src += (2 * frac - 1) * (0.8 + 0.4 * rng.random())
    src /= voices
    src += rng.normal(0, 1, n) * 0.08  # breath
    out = np.zeros(n)
    for f, bw, g in formants:
        out += bandpass(src, f - bw, f + bw, 2) * g
    return out * env_ar(n, attack, release, dur) * vel * 3.0


def guitar(freq, dur, vel, rng, bright=0.5, body=True):
    """A plucked steel string (Karplus-Strong at the exact pitch) through a small wooden body."""
    length = min(dur + 0.4, 2.0 + 300.0 / freq)
    over = 4
    period = int(round(over * SR / freq))
    rate = freq * period
    nn = int(length * rate)
    burst = rng.uniform(-1, 1, period)
    burst = lowpass(burst, (0.15 + 0.6 * bright) * rate / 2, 1)
    pick = int(period * rng.uniform(0.12, 0.2))
    burst = burst - np.roll(burst, pick) * 0.8  # pluck position comb
    g = 0.9995 ** (440.0 / freq) if freq < 440 else 0.9993
    # y[n] = x[n] + g/2 (y[n-P] + y[n-P-1]): everything on the right lies a period back, so the
    # string is computed one period at a time.
    blocks = nn // period + 2
    y = np.zeros(blocks * period + 1)
    y[1:period + 1] = burst
    for b in range(1, blocks):
        s = 1 + b * period
        prev = y[s - period - 1:s]
        y[s:s + period] = 0.5 * g * (prev[1:] + prev[:-1])
    y = y[1:nn + 1]
    out = signal.resample(y, int(length * SR))
    n = len(out)
    t = np.arange(n) / SR
    damper = np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.08))
    out = out * damper
    if body:
        out = peak_eq(out, 110, 5, 1.2)
        out = peak_eq(out, 230, 4, 1.4)
        out = peak_eq(out, 2500, 2, 1.0)
        out = lowpass(out, 7000)
    fade = int(0.002 * SR)
    out[:fade] *= np.linspace(0, 1, fade)
    return out * vel * 0.6


BELL_RATIOS = [(0.5, 0.35, 1.6), (1.0, 1.0, 1.0), (1.183, 0.5, 0.8), (1.506, 0.6, 0.7), (2.0, 0.4, 0.55), (2.514, 0.3, 0.45),
               (2.662, 0.25, 0.4), (3.011, 0.2, 0.35), (4.166, 0.15, 0.25), (5.433, 0.1, 0.2)]


def bell(freq, vel, rng, decay=6.0, bowed=False, dur=6.0):
    """Struck (or bowed) inharmonic metal: a far-off church bell, a glass, a wire."""
    length = decay * 1.4 if not bowed else dur + decay * 0.5
    n = int(length * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for ratio, amp, dk in BELL_RATIOS:
        f = freq * ratio * rng.uniform(0.997, 1.003)
        if f > 12000:
            continue
        if bowed:
            env = env_ar(n, dur * 0.4, decay * 0.5 * dk, dur * 0.7) * (1 + 0.3 * smooth_random(n, 0.4, rng))
        else:
            env = np.exp(-6.9 * t / (decay * dk))
        out += amp * env * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * (1 + 0.1 * np.sin(2 * np.pi * rng.uniform(0.5, 2) * t))
    if not bowed:
        strike = rng.normal(0, 1, int(0.005 * SR)) * np.exp(-np.linspace(0, 5, int(0.005 * SR)))
        out[:len(strike)] += bandpass(strike, 2000, 9000) * 0.2
    return out * vel


def drone(freq, dur, vel, rng, attack=4.0, release=5.0, grit=0.3):
    """A low bowed tone whose brightness wanders."""
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    phase = 2 * np.pi * np.cumsum(freq * (1 + smooth_random(n, 0.15, rng, 0.0015))) / SR
    x = np.sin(phase) + 0.5 * np.sin(2 * phase + 0.3) + 0.25 * np.sin(3 * phase + 1.1) + grit * np.sign(np.sin(phase)) * 0.2
    x += 0.4 * np.sin(phase * 0.5) * 0.5
    cutoff = 300 + 500 * (0.5 + 0.5 * smooth_random(n, 0.08, rng))
    blocks = 64
    y = np.zeros(n)
    step = n // blocks + 1
    zi = None
    for b in range(blocks):
        s, e = b * step, min(n, (b + 1) * step)
        if s >= e:
            break
        sos = _sos("lowpass", float(cutoff[s]), 2)
        if zi is None:
            zi = signal.sosfilt_zi(sos) * x[s]
        y[s:e], zi = signal.sosfilt(sos, x[s:e], zi=zi)
    noise = bandpass(rng.normal(0, 1, n), freq * 2, freq * 8) * 0.05
    return (y + noise) * env_ar(n, attack, release, dur) * vel


def drum(rng, low=55.0, vel=1.0, decay=0.9, skin=0.6):
    """A big low drum: falling pitch, felt-beater thump and skin overtones."""
    n = int((decay * 2.5) * SR)
    t = np.arange(n) / SR
    f = low + (low * 2.6) * np.exp(-t / 0.035)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)
    over = np.sin(2 * np.pi * low * 2.3 * t) * np.exp(-t / (decay * 0.35)) * 0.3 * skin
    thump = lowpass(rng.normal(0, 1, n), 900) * np.exp(-t / 0.02) * 0.6
    return (body + over + thump) * vel


def tick(rng, freq=3200.0, vel=0.3):
    n = int(0.05 * SR)
    t = np.arange(n) / SR
    x = bandpass(rng.normal(0, 1, n), freq * 0.7, freq * 1.4) * np.exp(-t / 0.006)
    return x * vel


def heartbeat(rng, vel=1.0):
    n = int(0.9 * SR)
    out = np.zeros(n)
    for start, gain in ((0.0, 1.0), (0.24, 0.7)):
        s = int(start * SR)
        t = np.arange(n - s) / SR
        f = 48 + 40 * np.exp(-t / 0.03)
        out[s:] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.11) * gain
    return lowpass(out, 400) * vel


def sub_drop(rng, vel=1.0, length=3.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = 30 + 70 * np.exp(-t / 0.5)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (length * 0.4)) * vel


def riser(rng, length=4.0, vel=1.0):
    """Noise that rises into a hit: wind through a slowly opening filter."""
    n = int(length * SR)
    t = np.arange(n) / SR
    noise = rng.normal(0, 1, n)
    out = np.zeros(n)
    blocks = 48
    step = n // blocks + 1
    for b in range(blocks):
        s, e = b * step, min(n, (b + 1) * step)
        if s >= e:
            break
        c = 200 * (40 ** (s / n))
        out[s:e] = bandpass(noise[max(0, s - 2000):e], c, c * 1.8)[-(e - s):]
    return out * (t / length) ** 2.5 * vel


def brass(freq, dur, vel, rng, attack=0.08):
    """A low, dark brass stab: a saw whose filter opens with the breath."""
    n = int((dur + 0.6) * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for det in (-0.003, 0.0, 0.0035):
        phase = 2 * np.pi * np.cumsum(freq * (1 + det + smooth_random(n, 2.0, rng, 0.001))) / SR
        frac = (phase / (2 * np.pi)) % 1.0
        out += 2 * frac - 1
    out /= 3
    env = env_ar(n, attack, 0.5, dur)
    cut = 250 + 1600 * env ** 1.5 * vel
    y = np.zeros(n)
    blocks = 40
    step = n // blocks + 1
    zi = None
    for b in range(blocks):
        s, e = b * step, min(n, (b + 1) * step)
        if s >= e:
            break
        sos = _sos("lowpass", float(cut[s]), 2)
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        y[s:e], zi = signal.sosfilt(sos, out[s:e], zi=zi)
    return y * env * vel


def noise_bed(seconds, rng, lo=300, hi=2500, depth=0.5, rate=0.07):
    """Wind-like air: filtered noise with slow gusts."""
    n = int(seconds * SR)
    x = rng.normal(0, 1, n)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[1:] /= np.sqrt(f[1:])
    x = np.fft.irfft(spec, n)
    x /= np.max(np.abs(x)) + 1e-9
    x = bandpass(x, lo, hi)
    gust = 0.55 + depth * smooth_random(n, rate, rng)
    return x * np.clip(gust, 0.05, 1.5)


# ============================================================================= space and mastering

def hall_ir(seconds, seed, predelay=0.03, brightness=1.0):
    """Stereo concert-hall impulse: early reflections, then a diffuse tail whose highs die first."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros((n + int(predelay * SR), 2))
    bands = [(20, 300, 1.25), (300, 1200, 1.0), (1200, 4500, 0.72 * brightness), (4500, 16000, 0.42 * brightness)]
    for ch in range(2):
        tail = np.zeros(n)
        for lo, hi, k in bands:
            noise = bandpass(rng.normal(0, 1, n), lo, hi, 2)
            tail += noise * np.exp(-6.9 * t / (seconds * k))
        tail *= np.clip(t / 0.06, 0, 1) ** 1.5
        er = np.zeros(n)
        for _ in range(14):
            d = int(rng.uniform(0.006, 0.075) * SR)
            er[d] += rng.uniform(0.2, 0.7) * rng.choice([-1, 1]) * np.exp(-d / SR / 0.05)
        ir = tail * 0.9 + lowpass(er, 6000) * 1.6
        out[int(predelay * SR):, ch] = ir
    out /= np.sqrt(np.sum(out ** 2) / 2)
    return out


def compress(x, threshold_db=-20.0, ratio=2.0, smooth=0.12):
    """Gentle feed-forward RMS compressor (stereo linked) for glue, not for peaks."""
    from scipy.ndimage import uniform_filter1d
    level = np.mean(x ** 2, axis=1)
    level = np.sqrt(uniform_filter1d(level, size=int(0.05 * SR), mode="nearest") + 1e-12)
    over = np.maximum(0, 20 * np.log10(level) - threshold_db)
    gain_db = -over * (1 - 1 / ratio)
    a = np.exp(-1 / (smooth * SR))
    gain_db = signal.filtfilt([1 - a], [1, -a], gain_db)
    return x * (10 ** (gain_db / 20))[:, None]


def limit(x, ceiling_db=-1.0, lookahead=0.004):
    """Look-ahead peak limiter: the gain needed around each peak, held and smoothed so it never overshoots."""
    from scipy.ndimage import minimum_filter1d, uniform_filter1d
    ceil = 10 ** (ceiling_db / 20)
    peak = np.max(np.abs(x), axis=1)
    need = np.minimum(1.0, ceil / np.maximum(peak, 1e-9))
    la = max(2, int(lookahead * SR))
    held = minimum_filter1d(need, size=2 * la + 1, mode="nearest")
    gain = uniform_filter1d(held, size=la, mode="nearest")
    out = x * np.minimum(gain, 1.0)[:, None]
    # Anything a smoothed edge still lets through is rounded off above a knee, never hard-clipped.
    knee = ceil * 0.9
    mag = np.abs(out)
    soft = knee + (ceil - knee) * np.tanh((mag - knee) / (ceil - knee))
    return np.where(mag > knee, np.sign(out) * soft, out)


def master(x, target_rms_db=-21.0, ceiling_db=-1.2, lowcut=32.0, air=-1.5):
    x = highpass(x, lowcut, 2)
    x = shelf_high(x, 9000, air)
    x = compress(x, threshold_db=target_rms_db + 4, ratio=1.8)
    rms = np.sqrt(np.mean(x ** 2))
    x = x * (10 ** (target_rms_db / 20) / max(rms, 1e-9))
    x = limit(x, ceiling_db)
    return x


def fade_edges(x, fin=0.8, fout=3.0):
    nin, nout = int(fin * SR), int(fout * SR)
    if nin:
        x[:nin] *= (np.sin(np.linspace(0, np.pi / 2, nin)) ** 2)[:, None]
    if nout:
        x[-nout:] *= (np.cos(np.linspace(0, np.pi / 2, nout)) ** 2)[:, None]
    return x


# ============================================================================= score

class Score:
    """Notes on named buses, rendered into stereo stems. Times in seconds."""

    def __init__(self, seconds: float, seed: int, bpm: float = 60.0, beats_per_bar: int = 4):
        self.n = int(seconds * SR)
        self.seconds = seconds
        self.rng = np.random.default_rng(seed)
        self.bpm = bpm
        self.beats = beats_per_bar
        self.buses: dict[str, np.ndarray] = {}

    def beat(self, bar: float, beat: float = 0.0) -> float:
        return (bar * self.beats + beat) * 60.0 / self.bpm

    @property
    def spb(self) -> float:
        return 60.0 / self.bpm

    def place(self, bus: str, start: float, audio: np.ndarray, pan: float = 0.0, gain: float = 1.0) -> None:
        if bus not in self.buses:
            self.buses[bus] = np.zeros((self.n + int(12 * SR), 2), dtype=np.float32)
        s = int(max(0.0, start) * SR)
        stereo = audio if audio.ndim == 2 else pan2(audio, pan)
        buf = self.buses[bus]
        e = min(buf.shape[0], s + stereo.shape[0])
        if e > s:
            buf[s:e] += stereo[:e - s] * gain

    def human(self, t: float, amount: float = 0.012) -> float:
        return t + self.rng.normal(0, amount)

    # -- instruments ---------------------------------------------------------
    def piano(self, t, note, dur, vel, bus="piano", pan=None, release=0.35, sustain=1.0):
        f = hz(note)
        p = np.clip((m(note) - 60) / 30.0 if isinstance(note, str) else (note - 60) / 30.0, -0.6, 0.6) if pan is None else pan
        v = float(np.clip(vel * self.rng.uniform(0.92, 1.06), 0.05, 1.0))
        self.place(bus, self.human(t), piano(f, dur, v, self.rng, release, sustain), p)

    def strings(self, t, note, dur, vel, bus="strings", pan=0.0, **kw):
        self.place(bus, t, strings(hz(note), dur, vel, self.rng, **kw), pan)

    def pad(self, t, note, dur, vel, bus="pad", pan=0.0, **kw):
        self.place(bus, t, pad(hz(note), dur, vel, self.rng, **kw), pan)

    def choir(self, t, note, dur, vel, bus="choir", pan=0.0, **kw):
        self.place(bus, t, choir(hz(note), dur, vel, self.rng, **kw), pan)

    def guitar(self, t, note, dur, vel, bus="guitar", pan=0.15, **kw):
        self.place(bus, self.human(t, 0.008), guitar(hz(note), dur, vel, self.rng, **kw), pan)

    def bell(self, t, note, vel, bus="bell", pan=0.0, **kw):
        self.place(bus, t, bell(hz(note), vel, self.rng, **kw), pan)

    def drone(self, t, note, dur, vel, bus="drone", pan=0.0, **kw):
        self.place(bus, t, drone(hz(note), dur, vel, self.rng, **kw), pan)

    def brass(self, t, note, dur, vel, bus="brass", pan=0.0):
        self.place(bus, self.human(t, 0.005), brass(hz(note), dur, vel, self.rng), pan)

    def hit(self, t, audio, bus="drums", pan=0.0, gain=1.0):
        self.place(bus, self.human(t, 0.004), audio, pan, gain)

    # -- render --------------------------------------------------------------
    def render(self, mix: dict[str, float], wet: dict[str, float], ir_seconds=4.2, seed=1, post=None, tail=8.0) -> np.ndarray:
        """mix: bus -> gain; wet: bus -> reverb send. Returns stereo (seconds + tail)."""
        total = self.n + int(tail * SR)
        dry = np.zeros((total, 2))
        send = np.zeros((total, 2))
        for name, buf in self.buses.items():
            b = buf[:total] * mix.get(name, 1.0)
            if post and name in post:
                b = post[name](b)
            dry[:b.shape[0]] += b
            send[:b.shape[0]] += b * wet.get(name, 0.3)
        ir = hall_ir(ir_seconds, seed)
        verb = np.zeros((total + ir.shape[0], 2))
        for ch in range(2):
            wet_ch = signal.fftconvolve(send[:, ch] * 0.75 + send[:, 1 - ch] * 0.25, ir[:, ch])
            verb[:len(wet_ch), ch] = wet_ch
        out = verb[:total]
        out += dry
        return out


def strings_bus(x):
    x = peak_eq(x, 280, 2.0, 0.8)
    x = peak_eq(x, 1200, 1.2, 1.0)
    x = peak_eq(x, 3200, -2.0, 1.2)
    return lowpass(x, 6500, 2)


def piano_bus(x):
    x = peak_eq(x, 180, 1.5, 0.8)
    x = peak_eq(x, 3500, -1.5, 1.0)
    return lowpass(x, 11000, 2)


def soft_bus(x):
    return lowpass(x, 2800, 2)


def loop_wrap(x: np.ndarray, loop_seconds: float) -> np.ndarray:
    """Fold everything after the loop point back onto the start, so the loop is seamless."""
    n = int(loop_seconds * SR)
    out = x[:n].copy()
    rest = x[n:]
    i = 0
    while i < rest.shape[0]:
        seg = rest[i:i + n]
        out[:seg.shape[0]] += seg
        i += n
    return out
