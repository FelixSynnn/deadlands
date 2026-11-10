"""Procedurally synthesised DEADLANDS audio. Every sound is generated from oscillators and noise,
so there are no third-party samples and no licensing questions.

Outputs (assets/deadlands/sounds/, OGG Vorbis via ffmpeg)
  ui/click.ogg            mechanical latch click (button press)
  ui/journal_open.ogg     leather creak and page rustle
  ui/journal_close.ogg    soft book thump
  ui/page_turn.ogg        paper swish
  event/day_begin.ogg     distant broken bell over a sub boom and wind (the DAY card)
  music/title_theme.ogg   ~96 s seamless ambient loop for the menus (stereo)
"""
from __future__ import annotations

import shutil
import subprocess

import numpy as np
import soundfile as sf
from scipy import signal

import dl_noise as N

SR = 44100
CACHE = N.ROOT / "tools" / "assetgen" / "_cache"
OUT = N.ASSETS / "sounds"


# ----------------------------------------------------------------------------- dsp helpers

def axis(seconds: float) -> np.ndarray:
    return np.arange(int(seconds * SR), dtype=np.float64) / SR


def sos(kind: str, freq, order: int = 4):
    return signal.butter(order, freq, btype=kind, fs=SR, output="sos")


def bandpass(x, lo, hi, order=4):
    return signal.sosfilt(sos("bandpass", [lo, hi], order), x)


def lowpass(x, f, order=4):
    return signal.sosfilt(sos("lowpass", f, order), x)


def highpass(x, f, order=2):
    return signal.sosfilt(sos("highpass", f, order), x)


def fade(x: np.ndarray, fin: float = 0.003, fout: float = 0.01) -> np.ndarray:
    n_in, n_out = int(fin * SR), int(fout * SR)
    y = x.copy()
    if n_in:
        y[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        y[-n_out:] *= np.linspace(1, 0, n_out)
    return y


def env_adsr(t: np.ndarray, attack: float, decay: float, start: float = 0.0) -> np.ndarray:
    u = t - start
    rise = np.clip(u / max(attack, 1e-4), 0, 1)
    return np.where(u < 0, 0.0, rise * np.exp(-np.maximum(u - attack, 0) / decay))


def grains(n: int, density: float, decay: float, seed: int) -> np.ndarray:
    """Sparse random impulses smeared by a short exponential: crackle."""
    r = N.rng(seed)
    imp = (r.random(n) < density) * r.uniform(0.3, 1.0, n)
    k = np.exp(-np.arange(int(decay * SR * 6)) / (decay * SR))
    return signal.fftconvolve(imp, k)[:n]


def reverb_ir(seconds: float, seed: int, damp: float = 0.6, predelay: float = 0.02) -> np.ndarray:
    r = N.rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    noise = r.normal(0, 1, n)
    decay = np.exp(-t * 6.9 / seconds)
    dark = lowpass(noise, 2500) * decay
    bright = noise * decay
    mix = np.clip(t / seconds, 0, 1) * damp
    ir = bright * (1 - mix) + dark * mix
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x: np.ndarray, ir: np.ndarray, wet: float, keep_tail: bool = True) -> np.ndarray:
    w = signal.fftconvolve(x, ir)
    out = np.zeros(len(w) if keep_tail else len(x))
    out[:len(x)] += x * (1 - wet)
    out += (w if keep_tail else w[:len(x)]) * wet * 1.4
    return out


def normalize(x: np.ndarray, peak: float) -> np.ndarray:
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def write_ogg(rel: str, data: np.ndarray, quality: int = 4) -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    wav = CACHE / (rel.replace("/", "_") + ".wav")
    sf.write(wav, data.astype(np.float32), SR, subtype="PCM_16")
    dst = OUT / (rel + ".ogg")
    dst.parent.mkdir(parents=True, exist_ok=True)
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise SystemExit("ffmpeg is required to encode OGG Vorbis")
    # bitexact: a fixed Ogg stream serial and no encoder-version tag, so re-encoding the same audio
    # reproduces the committed file byte for byte.
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", str(quality),
                    "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(dst)], check=True)


# ----------------------------------------------------------------------------- interface sounds

def click(seed: int = 5101) -> np.ndarray:
    r = N.rng(seed)
    t = axis(0.16)

    def one(scale: float, tone: float) -> np.ndarray:
        burst = bandpass(r.normal(0, 1, len(t)) * np.exp(-t / 0.0022), 1800, 7500, 2)
        ping = sum(a * np.sin(2 * np.pi * f * tone * t + r.uniform(0, 6.28)) * np.exp(-t / d)
                   for f, a, d in ((1850, 0.35, 0.030), (3120, 0.22, 0.018), (4410, 0.15, 0.012), (6230, 0.08, 0.008)))
        thunk = 0.55 * np.sin(2 * np.pi * 140 * t) * np.exp(-t / 0.012)
        return (burst * 0.9 + ping + thunk) * scale

    x = one(1.0, 1.0)
    release = one(0.32, 1.12)
    lag = int(0.034 * SR)
    x[lag:] += release[:len(x) - lag]
    return normalize(fade(x), 0.7)


def journal_open(seed: int = 5201) -> np.ndarray:
    r = N.rng(seed)
    t = axis(0.85)
    jitter = lowpass(r.normal(0, 1, len(t)), 30, 2)
    f0 = 85 + 25 * np.sin(2 * np.pi * 2.6 * t) + 120 * jitter
    phase = np.cumsum(np.maximum(f0, 20)) / SR
    saw = 2 * (phase % 1.0) - 1
    creak_env = np.clip(t / 0.05, 0, 1) * np.clip((0.45 - t) / 0.25, 0, 1)
    creak = lowpass(saw * (0.6 + 0.4 * np.abs(jitter) / (np.max(np.abs(jitter)) + 1e-9)), 1500) * creak_env
    paper = bandpass(r.normal(0, 1, len(t)), 1400, 8000, 2)
    crackle = grains(len(t), 0.004, 0.0015, seed + 1)
    rustle_env = np.clip((t - 0.12) / 0.08, 0, 1) * np.clip((0.78 - t) / 0.3, 0, 1)
    x = 0.55 * creak + 0.45 * paper * (0.25 + 1.5 * crackle) * rustle_env
    x = reverb(x, reverb_ir(0.35, seed + 2, damp=0.8), 0.18, keep_tail=False)
    return normalize(fade(x, 0.002, 0.05), 0.6)


def journal_close(seed: int = 5301) -> np.ndarray:
    r = N.rng(seed)
    t = axis(0.5)
    thump = (np.sin(2 * np.pi * 92 * t) + 0.4 * np.sin(2 * np.pi * 183 * t)) * env_adsr(t, 0.004, 0.06)
    puff = lowpass(r.normal(0, 1, len(t)), 900) * env_adsr(t, 0.003, 0.05)
    flap = bandpass(r.normal(0, 1, len(t)), 2000, 7000, 2) * env_adsr(t, 0.01, 0.04, start=0.0)
    x = 0.9 * thump + 0.5 * puff + 0.15 * flap
    x = reverb(x, reverb_ir(0.4, seed + 1, damp=0.85), 0.15, keep_tail=False)
    return normalize(fade(x, 0.001, 0.08), 0.65)


def page_turn(seed: int = 5401) -> np.ndarray:
    r = N.rng(seed)
    t = axis(0.42)
    sweep = 2500 + 3000 * np.sin(np.pi * np.clip(t / 0.35, 0, 1))
    noise = r.normal(0, 1, len(t))
    lo = bandpass(noise, 1200, 4000, 2)
    hi = bandpass(noise, 4000, 9000, 2)
    mixf = (sweep - 2500) / 3000
    swish = lo * (1 - mixf) + hi * mixf
    env = np.sin(np.pi * np.clip(t / 0.36, 0, 1)) ** 1.5
    crackle = grains(len(t), 0.003, 0.001, seed + 1)
    x = swish * env * (0.5 + 1.2 * crackle)
    return normalize(fade(x, 0.002, 0.04), 0.55)


def day_begin(seed: int = 5501) -> np.ndarray:
    r = N.rng(seed)
    t = axis(7.0)
    f = 38 + 34 * np.exp(-t / 0.18)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_adsr(t, 0.012, 1.9)
    # A cracked bell somewhere in a dead town: inharmonic, slightly beating partials.
    bell = np.zeros_like(t)
    for fr, a, d in ((196.0, 1.0, 3.6), (311.4, 0.62, 3.0), (467.3, 0.48, 2.4), (622.9, 0.36, 2.0),
                     (881.2, 0.24, 1.5), (1244.0, 0.15, 1.0), (1653.0, 0.08, 0.7)):
        beat = 1.0 + 0.0025 * np.sin(2 * np.pi * r.uniform(0.4, 1.3) * t)
        bell += a * np.sin(2 * np.pi * fr * beat * t + r.uniform(0, 6.28)) * env_adsr(t, 0.004, d, start=0.06)
    bell = lowpass(bell, 2400)  # distance
    wind = lowpass(r.normal(0, 1, len(t)), 500) * (0.5 + 0.5 * np.sin(np.pi * np.clip(t / 7.0, 0, 1)))
    x = 0.9 * boom + 0.55 * bell + 0.18 * wind
    x = reverb(x, reverb_ir(3.2, seed + 1, damp=0.7, predelay=0.04), 0.45, keep_tail=False)
    return normalize(fade(x, 0.005, 1.2), 0.85)


# ----------------------------------------------------------------------------- containers

def _partials(t, spec, start=0.0, r=None):
    out = np.zeros_like(t)
    for fr, a, d in spec:
        jitter = 1.0 if r is None else r.uniform(0.97, 1.03)
        out += a * np.sin(2 * np.pi * fr * jitter * (t - start)) * env_adsr(t, 0.002, d, start=start)
    return out


def _creak(t, r, lo=60.0, hi=95.0, start=0.0, length=0.45, cutoff=1400):
    jitter = lowpass(r.normal(0, 1, len(t)), 25, 2)
    f0 = lo + (hi - lo) * np.clip((t - start) / length, 0, 1) + 90 * jitter
    phase = np.cumsum(np.maximum(f0, 15)) / SR
    pulses = (np.mod(phase, 1.0) < 0.12).astype(float) * (0.5 + 0.5 * np.abs(jitter) / (np.max(np.abs(jitter)) + 1e-9))
    env = np.clip((t - start) / 0.04, 0, 1) * np.clip((start + length - t) / 0.15, 0, 1)
    return lowpass(bandpass(pulses, 70, 3000, 2), cutoff) * env


def _squeak(t, r, f_from, f_to, start, length, rough=0.0):
    u = np.clip((t - start) / length, 0, 1)
    f = f_from + (f_to - f_from) * u + 40 * np.sin(2 * np.pi * 7 * t)
    if rough:
        f += rough * lowpass(r.normal(0, 1, len(t)), 60, 2) * 400
    phase = np.cumsum(f) / SR
    env = np.sin(np.pi * u) ** 0.7 * (t >= start) * (t <= start + length)
    tone = np.sin(2 * np.pi * phase) + 0.35 * np.sin(4 * np.pi * phase) + 0.15 * np.sin(6 * np.pi * phase)
    return tone * env


def _impact(t, r, start, body=110.0, decay=0.08, bright=1500):
    n = lowpass(r.normal(0, 1, len(t)), bright) * env_adsr(t, 0.001, decay * 0.4, start=start)
    thump = np.sin(2 * np.pi * body * (t - start)) * env_adsr(t, 0.002, decay, start=start)
    return thump + 0.6 * n


def _finish(x, peak=0.7, room=0.25, seed=0):
    x = reverb(x, reverb_ir(0.45, seed + 900, damp=0.8), room, keep_tail=False)
    return normalize(fade(x, 0.002, 0.04), peak)


def crate_open(seed=5701):
    r = N.rng(seed)
    t = axis(1.0)
    x = 0.9 * _creak(t, r, 55, 85, 0.0, 0.6) + 0.7 * _impact(t, r, 0.62, body=95, decay=0.12, bright=900)
    x += 0.2 * bandpass(r.normal(0, 1, len(t)), 2000, 6000, 2) * grains(len(t), 0.003, 0.002, seed + 1) * (t > 0.6)
    return _finish(x, 0.75, 0.22, seed)


def crate_close(seed=5711):
    r = N.rng(seed)
    t = axis(0.6)
    x = 1.0 * _impact(t, r, 0.0, body=105, decay=0.13, bright=800) + 0.5 * _impact(t, r, 0.0, body=210, decay=0.05, bright=2500)
    x += 0.35 * bandpass(r.normal(0, 1, len(t)), 1500, 5000, 2) * grains(len(t), 0.006, 0.002, seed + 1) * np.clip((0.35 - t) / 0.3, 0, 1)
    return _finish(x, 0.8, 0.2, seed)


def drawer_open(seed=5721):
    r = N.rng(seed)
    t = axis(0.7)
    roll = bandpass(r.normal(0, 1, len(t)), 700, 3200, 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 34 * t) ** 2)
    roll *= np.clip(t / 0.05, 0, 1) * np.clip((0.48 - t) / 0.1, 0, 1)
    x = 0.6 * roll + 0.5 * _partials(t, [(610, 0.6, 0.12), (1360, 0.4, 0.08), (2130, 0.25, 0.05)], start=0.47, r=r)
    return _finish(x, 0.65, 0.18, seed)


def drawer_close(seed=5731):
    r = N.rng(seed)
    t = axis(0.6)
    roll = bandpass(r.normal(0, 1, len(t)), 900, 3500, 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 46 * t) ** 2) * np.clip((0.26 - t) / 0.08, 0, 1)
    slam = _impact(t, r, 0.26, body=170, decay=0.07, bright=3000) + 0.5 * _partials(t, [(880, 0.7, 0.18), (1710, 0.4, 0.1)], start=0.26, r=r)
    return _finish(0.5 * roll + 0.8 * slam, 0.75, 0.2, seed)


def cabinet_open(seed=5741):
    r = N.rng(seed)
    t = axis(0.55)
    x = 0.35 * _squeak(t, r, 1750, 2350, 0.0, 0.28) + 0.4 * _partials(t, [(3150, 0.5, 0.05), (4420, 0.35, 0.04)], start=0.3, r=r)
    x += 0.3 * _partials(t, [(3610, 0.4, 0.04), (5070, 0.25, 0.03)], start=0.37, r=r)
    return _finish(x, 0.55, 0.2, seed)


def cabinet_close(seed=5751):
    r = N.rng(seed)
    t = axis(0.4)
    click_ = bandpass(r.normal(0, 1, len(t)), 2000, 7000, 2) * env_adsr(t, 0.001, 0.004)
    x = 0.8 * click_ + 0.5 * _partials(t, [(2350, 0.5, 0.03), (3900, 0.3, 0.02)], r=r) + 0.35 * _partials(t, [(3300, 0.5, 0.06)], start=0.05, r=r)
    x += 0.4 * _impact(t, r, 0.0, body=160, decay=0.03)
    return _finish(x, 0.55, 0.15, seed)


def locker_open(seed=5761):
    r = N.rng(seed)
    t = axis(0.8)
    x = 0.3 * _squeak(t, r, 900, 1350, 0.02, 0.4, rough=0.6) + 0.45 * _partials(t, [(122, 1.0, 0.4), (263, 0.6, 0.3), (412, 0.4, 0.22)], start=0.0, r=r)
    x += 0.4 * _impact(t, r, 0.0, body=200, decay=0.02, bright=4000)
    return _finish(x, 0.65, 0.25, seed)


def locker_close(seed=5771):
    r = N.rng(seed)
    t = axis(0.8)
    x = _impact(t, r, 0.0, body=140, decay=0.06, bright=5000) + 0.6 * _partials(t, [(231, 1.0, 0.3), (472, 0.7, 0.22), (761, 0.5, 0.16), (1123, 0.3, 0.1)], r=r)
    x += 0.25 * bandpass(r.normal(0, 1, len(t)), 2500, 7000, 2) * grains(len(t), 0.004, 0.002, seed + 1) * np.clip((0.3 - t) / 0.3, 0, 1)
    return _finish(x, 0.8, 0.28, seed)


def toolbox_open(seed=5781):
    r = N.rng(seed)
    t = axis(0.45)
    x = np.zeros_like(t)
    for s in (0.0, 0.11):
        x += bandpass(r.normal(0, 1, len(t)), 2500, 8000, 2) * env_adsr(t, 0.0005, 0.005, start=s)
        x += 0.5 * _partials(t, [(1900, 0.6, 0.03), (2900, 0.4, 0.02)], start=s, r=r)
    x += 0.35 * _partials(t, [(480, 0.6, 0.15), (1090, 0.4, 0.1)], start=0.2, r=r)
    return _finish(x, 0.6, 0.15, seed)


def toolbox_close(seed=5791):
    r = N.rng(seed)
    t = axis(0.35)
    x = 0.8 * _impact(t, r, 0.0, body=230, decay=0.04, bright=4000) + 0.5 * _partials(t, [(1250, 0.6, 0.08), (2600, 0.3, 0.05)], r=r)
    x += 0.5 * bandpass(r.normal(0, 1, len(t)), 3000, 8000, 2) * env_adsr(t, 0.0005, 0.004, start=0.09)
    return _finish(x, 0.65, 0.15, seed)


def container_locked(seed=5801):
    r = N.rng(seed)
    t = axis(0.6)
    x = np.zeros_like(t)
    s = 0.0
    for _ in range(5):
        x += 0.7 * bandpass(r.normal(0, 1, len(t)), 1500, 6000, 2) * env_adsr(t, 0.0005, 0.006, start=s)
        x += 0.45 * _partials(t, [(r.uniform(700, 900), 0.6, 0.05), (r.uniform(1700, 2100), 0.35, 0.03)], start=s, r=r)
        s += r.uniform(0.045, 0.085)
    return _finish(x, 0.65, 0.2, seed)


def container_unlock(seed=5811):
    r = N.rng(seed)
    t = axis(0.5)
    scrape = bandpass(r.normal(0, 1, len(t)), 2500, 7000, 2) * np.clip(t / 0.02, 0, 1) * np.clip((0.14 - t) / 0.06, 0, 1)
    x = 0.35 * scrape
    for s, a in ((0.18, 0.6), (0.27, 1.0)):
        x += a * bandpass(r.normal(0, 1, len(t)), 1800, 7000, 2) * env_adsr(t, 0.0005, 0.005, start=s)
        x += a * 0.6 * _partials(t, [(1450, 0.6, 0.04), (3100, 0.35, 0.025)], start=s, r=r)
    return _finish(x, 0.65, 0.15, seed)


CONTAINER_SOUNDS = {
    "container/crate_open": crate_open, "container/crate_close": crate_close,
    "container/drawer_open": drawer_open, "container/drawer_close": drawer_close,
    "container/cabinet_open": cabinet_open, "container/cabinet_close": cabinet_close,
    "container/locker_open": locker_open, "container/locker_close": locker_close,
    "container/toolbox_open": toolbox_open, "container/toolbox_close": toolbox_close,
    "container/locked": container_locked, "container/unlock": container_unlock,
}


def build_containers() -> None:
    for rel, fn in CONTAINER_SOUNDS.items():
        write_ogg(rel, fn(), quality=5)


# ----------------------------------------------------------------------------- title theme

def title_theme(seed: int = 5601, loop_seconds: float = 96.0, xfade: float = 6.0) -> np.ndarray:
    """Seamless stereo loop: Dm - Bb - Gm - Asus2 pads, wind, distant metal and a radio burst."""
    r = N.rng(seed)
    total = loop_seconds + xfade
    t = axis(total).astype(np.float32)
    n = len(t)
    chords = [
        (73.42, 110.00, 146.83, 174.61, 220.00),
        (58.27, 87.31, 116.54, 146.83, 174.61),
        (49.00, 73.42, 98.00, 116.54, 146.83),
        (55.00, 82.41, 110.00, 123.47, 164.81),
    ]
    span = loop_seconds / len(chords)
    fade_s = 5.0
    out = np.zeros((n, 2), dtype=np.float32)
    brightness = 2.6 + 1.6 * np.sin(2 * np.pi * t / 37.0) + 0.6 * np.sin(2 * np.pi * t / 13.0 + 1.0)

    for k, notes in enumerate(chords):
        start = k * span
        env = np.zeros(n, dtype=np.float32)
        for shift in (-loop_seconds, 0.0, loop_seconds):
            u = t + shift
            rise = np.clip((u - (start - fade_s)) / (2 * fade_s), 0, 1)
            fall = np.clip(((start + span + fade_s) - u) / (2 * fade_s), 0, 1)
            env = np.maximum(env, np.minimum(rise, fall))
        env = np.sin(env * np.pi / 2) ** 2
        idx = np.nonzero(env > 1e-4)[0]
        if len(idx) == 0:
            continue
        tt = t[idx]
        for ch in range(2):
            acc = np.zeros(len(idx), dtype=np.float32)
            for j, f in enumerate(notes):
                detune = 2 ** ((r.uniform(-4, 4) + (3 if ch else -3)) / 1200.0)
                for h in range(1, 10):
                    amp = (1.0 / h ** 1.25) * np.exp(-h / brightness[idx])
                    ph = r.uniform(0, 2 * np.pi)
                    acc += (amp * np.sin(2 * np.pi * f * detune * h * tt + ph)).astype(np.float32)
            # Slow tremolo so the pad breathes.
            acc *= (0.85 + 0.15 * np.sin(2 * np.pi * (0.05 + 0.02 * ch) * tt + k)).astype(np.float32)
            out[idx, ch] += acc * env[idx] * 0.14

    # Wind: two decorrelated noise beds with drifting bands.
    for ch in range(2):
        white = r.normal(0, 1, n)
        spec = np.fft.rfft(white)
        freqs = np.fft.rfftfreq(n, 1 / SR)
        spec[1:] /= np.sqrt(freqs[1:])  # pink
        pink = np.fft.irfft(spec, n)
        pink /= np.max(np.abs(pink))
        body = bandpass(pink, 180, 1300, 2)
        whistle = bandpass(pink, 650, 900, 4)
        gust = 0.55 + 0.45 * np.sin(2 * np.pi * t / 23.0 + ch * 1.7) * np.sin(2 * np.pi * t / 9.0 + ch)
        out[:, ch] += (0.45 * body * gust + 0.25 * whistle * gust ** 3).astype(np.float32)

    # Distant metal: something creaking and banging in the wind.
    for when in (8.5, 27.0, 43.5, 61.0, 79.5):
        tt = t - when
        hit = np.zeros(n)
        for fr, a, d in ((283.0, 1.0, 1.1), (437.0, 0.7, 0.8), (611.0, 0.5, 0.6), (893.0, 0.3, 0.4)):
            hit += a * np.sin(2 * np.pi * fr * r.uniform(0.97, 1.03) * tt) * env_adsr(tt, 0.003, d)
        hit = lowpass(hit, 1400)
        pan = r.uniform(0.2, 0.8)
        out[:, 0] += (hit * 0.08 * (1 - pan)).astype(np.float32)
        out[:, 1] += (hit * 0.08 * pan).astype(np.float32)

    # A radio burst: static and a voice-like murmur that never becomes words.
    when, length = 51.0, 2.8
    tt = t - when
    window = np.clip(tt / 0.15, 0, 1) * np.clip((length - tt) / 0.4, 0, 1)
    static = bandpass(r.normal(0, 1, n), 700, 3400, 2) * (0.4 + grains(n, 0.002, 0.002, seed + 3) * 2)
    syll = 0.5 + 0.5 * np.sin(2 * np.pi * 4.3 * t) * np.sin(2 * np.pi * 1.7 * t + 0.4)
    murmur = bandpass(np.sign(np.sin(2 * np.pi * (180 + 25 * np.sin(2 * np.pi * 3 * t)) * t)), 400, 2600, 2) * syll
    radio = (static * 0.6 + murmur * 0.35) * window * 0.12
    out[:, 0] += (radio * 0.7).astype(np.float32)
    out[:, 1] += (radio * 0.35).astype(np.float32)

    # Space: a long dark hall per channel (different seeds keep the stereo wide).
    wet = np.zeros_like(out)
    for ch in range(2):
        rv = reverb(out[:, ch].astype(np.float64), reverb_ir(4.5, seed + 10 + ch, damp=0.75, predelay=0.03), 0.38, keep_tail=False)
        wet[:, ch] = rv.astype(np.float32)
    out = wet

    # Remove sub-rumble before looping: filtering afterwards would start from silence and click.
    out = highpass(out.T.astype(np.float64), 28).T

    # Seamless loop: the last `xfade` seconds blend into the material just before the loop start,
    # so the final sample flows straight into the first one.
    xs = int(xfade * SR)
    ls = int(loop_seconds * SR)
    body = out[xs:xs + ls].copy()
    c = np.sin(np.linspace(0, np.pi / 2, xs)) ** 2
    body[ls - xs:] = body[ls - xs:] * np.sqrt(1 - c)[:, None] + out[:xs] * np.sqrt(c)[:, None]
    # Minecraft plays music tracks one at a time with a pause between them, so the shipped file
    # fades in and out gently. The crossfade above still lets tools loop it without a seam.
    fin, fout = int(2.5 * SR), int(6.0 * SR)
    body[:fin] *= (np.sin(np.linspace(0, np.pi / 2, fin)) ** 2)[:, None]
    body[-fout:] *= (np.cos(np.linspace(0, np.pi / 2, fout)) ** 2)[:, None]
    return normalize(body, 0.8)


def build() -> None:
    build_containers()
    write_ogg("ui/click", click(), quality=5)
    write_ogg("ui/journal_open", journal_open())
    write_ogg("ui/journal_close", journal_close())
    write_ogg("ui/page_turn", page_turn())
    write_ogg("event/day_begin", day_begin(), quality=5)
    write_ogg("music/title_theme", title_theme(), quality=4)


if __name__ == "__main__":
    build()
    print("audio written")
