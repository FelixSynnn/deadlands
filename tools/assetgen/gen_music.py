"""The DEADLANDS soundtrack: thirteen original pieces, composed here and synthesised by dl_music.

The main theme ("Ninety Days", D minor: Dm - Bb - Gm - A) keeps the harmony of the first title
theme, so the menu keeps the feeling it had, and its opening motif returns across the score.

  title          Ninety Days        menus; piano motif, strings, wind, a radio burst
  explore        Empty Roads        A minor; piano arpeggios, a plain melody, strings later
                 What Remains       D minor waltz; a cello sings over the piano
                 Overgrown          E minor; fingerpicked steel guitar, a pad, piano answers
                 Static Lullaby     F major with a borrowed minor iv; music box, static
  night          Long Night         a low D drone, far bells, a few low piano notes
                 No Lights          C phrygian; sub swells, bowed metal, a distant heartbeat
                 Hollow Hours       A minor; slow pads, string harmonics, dripping piano
  danger (loop)  Pursuit            96 bpm; string ostinato, war drums, ticking, brass
                 They Hear You      84 bpm; a heartbeat, scraping metal, tremolo clusters
  horde (loop)   Horde              104 bpm; heavy drums, a driving bass, choir and brass
  safe           Campfire           G major; guitar by the fire, a warm pad, a piano tune
                 Shelter            F major hymn; piano and strings, a minor iv at the end

Every piece is rendered through the same hall, mastered (gentle compression, peak limiter), faded
(loops are folded so they repeat without a seam) and written as stereo OGG Vorbis, streamed.
"""
from __future__ import annotations

import json

import numpy as np

import dl_data as D
import dl_music as M
from gen_audio import OUT, write_ogg

SR = M.SR
MIX = {"piano": 1.0, "strings": 0.75, "lead": 0.85, "pad": 0.5, "choir": 0.45, "guitar": 0.9, "bell": 0.35, "drone": 0.6, "drums": 0.9,
       "brass": 0.55, "air": 0.35, "ticks": 0.35, "low": 0.8}
WET = {"piano": 0.38, "strings": 0.55, "lead": 0.45, "pad": 0.6, "choir": 0.75, "guitar": 0.3, "bell": 0.85, "drone": 0.5, "drums": 0.35,
       "brass": 0.4, "air": 0.2, "ticks": 0.2, "low": 0.3}
POST = {"strings": M.strings_bus, "lead": M.strings_bus, "piano": M.piano_bus, "pad": M.soft_bus, "drone": M.soft_bus}


def C(*names):
    return M.chord(*names)


def wind(s: M.Score, gain=0.5, lo=250, hi=1600):
    left = M.noise_bed(s.seconds + 4, s.rng, lo, hi, 0.5, 0.06)
    right = M.noise_bed(s.seconds + 4, s.rng, lo, hi, 0.5, 0.07)
    s.place("air", 0.0, np.stack([left, right], axis=1) * gain)


def chord_pad(s: M.Score, start, dur, notes, vel, bus="pad", **kw):
    for i, n in enumerate(notes):
        s.pad(start, n, dur, vel * (1.0 if i == 0 else 0.8), bus=bus, pan=(i - (len(notes) - 1) / 2) * 0.25, **kw)


def chord_strings(s: M.Score, start, dur, notes, vel, bus="strings", **kw):
    for i, n in enumerate(notes):
        s.strings(start, n, dur, vel * (1.0 if i == 0 else 0.85), bus=bus, pan=(i - (len(notes) - 1) / 2) * 0.35, **kw)


def melody(s: M.Score, bar0, notes, vel, inst="piano", bus=None, **kw):
    """notes: (bar offset, beat, note, beats) tuples."""
    for bar, beat, note, beats in notes:
        t = s.beat(bar0 + bar, beat)
        dur = beats * s.spb
        if inst == "piano":
            s.piano(t, note, dur * 1.05, vel * s.rng.uniform(0.9, 1.05), bus=bus or "piano", **kw)
        else:
            s.strings(t, note, dur * 0.98, vel, bus=bus or "lead", **kw)


def finish(audio: np.ndarray, seconds: float, loop: bool, rms_db: float) -> np.ndarray:
    if loop:
        audio = M.loop_wrap(audio, seconds)
        out = M.master(audio, target_rms_db=rms_db)
        # a loop starts and ends on the same material: only the first play fades in (in game)
        return out
    out = M.master(audio[: int((seconds + 6.0) * SR)], target_rms_db=rms_db)
    return M.fade_edges(out, 1.5, 6.0)


# ============================================================================= the main theme

THEME_A = [(0, 0, "D5", 2), (0, 2, "F5", 1), (0, 3, "E5", 1), (1, 0, "C5", 3), (1, 3, "A4", 1),
           (2, 0, "Bb4", 2), (2, 2, "A4", 1), (2, 3, "G4", 1), (3, 0, "A4", 3), (3, 3, "E5", 1)]
THEME_B = [(0, 0, "D5", 1.5), (0, 1.5, "E5", 0.5), (0, 2, "F5", 1), (0, 3, "A5", 1), (1, 0, "G5", 2), (1, 2, "F5", 1), (1, 3, "E5", 1),
           (2, 0, "D5", 2), (2, 2, "C5", 1), (2, 3, "Bb4", 1), (3, 0, "A4", 4)]
THEME_CHORDS = [("D2", C("D3", "F3", "A3"), "A2"), ("Bb1", C("Bb2", "D3", "F3"), "F2"), ("G1", C("G2", "Bb2", "D3"), "D2"),
                ("A1", C("A2", "C#3", "E3"), "E2")]


def theme_bass(s, bar, i, vel=0.42):
    root, _, fifth = THEME_CHORDS[i % 4]
    s.piano(s.beat(bar, 0), root, 4 * s.spb, vel, pan=-0.35)
    s.piano(s.beat(bar, 0), M.m(root) + 12, 4 * s.spb, vel * 0.7, pan=-0.3)
    s.piano(s.beat(bar, 2), fifth, 2 * s.spb, vel * 0.55, pan=-0.2)


def title(seed=9101):
    bars = 36
    s = M.Score(bars * 4 * 60 / 66 + 4, seed, bpm=66)
    wind(s, 0.55)
    s.drone(0.0, "D2", s.beat(28), 0.5, attack=6.0, release=8.0)
    s.bell(s.beat(2, 1), "D3", 0.18, decay=7.0, pan=0.5)
    for bar in range(2, 34):
        i = bar % 4
        chord_pad(s, s.beat(bar), 4 * s.spb, THEME_CHORDS[i][1], 0.16 if bar < 12 else 0.12, attack=1.6, release=2.5)
    for bar in range(4, 30):
        theme_bass(s, bar, bar % 4, 0.34 if bar < 12 else 0.42)
    melody(s, 4, THEME_A, 0.5)
    melody(s, 8, THEME_B, 0.55)
    # B: the strings take the theme, the piano breaks the chords underneath
    for bar in range(12, 28):
        notes = THEME_CHORDS[bar % 4][1]
        pattern = [notes[0] + 12, notes[1] + 12, notes[2] + 12, notes[1] + 24, notes[2] + 12, notes[1] + 12, notes[0] + 12, notes[2]]
        for k, n in enumerate(pattern):
            s.piano(s.beat(bar, k * 0.5), n, 0.9 * s.spb, 0.24 + 0.04 * (k == 0))
        chord_strings(s, s.beat(bar), 4 * s.spb, [THEME_CHORDS[bar % 4][1][0] - 12, THEME_CHORDS[bar % 4][1][1]], 0.26, attack=1.2, release=2.2)
    melody(s, 12, THEME_A, 0.5, inst="strings", vibrato=0.0055)
    melody(s, 16, THEME_B, 0.55, inst="strings", vibrato=0.0055)
    # C: everything, the theme in octaves, a slow drum under it, a radio voice in the static
    for bar in range(20, 28):
        s.hit(s.beat(bar), M.drum(s.rng, 46, 0.5, decay=1.4), gain=0.5)
    melody(s, 20, THEME_A, 0.62)
    melody(s, 20, [(b, bt, M.m(n) + 12, l) for b, bt, n, l in THEME_A], 0.34)
    melody(s, 24, THEME_B, 0.66)
    melody(s, 24, [(b, bt, M.m(n) - 12, l) for b, bt, n, l in THEME_B], 0.45, inst="strings")
    for bar in range(20, 28):
        s.choir(s.beat(bar), THEME_CHORDS[bar % 4][1][2], 4 * s.spb, 0.1, vowel="aa")
    radio(s, s.beat(25, 2), 3.0)
    # outro: the motif alone, slower, then D minor with a ninth
    s.piano(s.beat(29, 0), "D5", 2.5, 0.42)
    s.piano(s.beat(29, 2.5), "F5", 1.4, 0.36)
    s.piano(s.beat(30, 0), "E5", 2.5, 0.34)
    s.piano(s.beat(31, 0), "C5", 3.0, 0.3)
    s.piano(s.beat(31, 3), "A4", 3.0, 0.28)
    for n, v in (("D2", 0.4), ("A2", 0.3), ("D3", 0.28), ("F3", 0.24), ("E4", 0.2), ("A4", 0.18)):
        s.piano(s.beat(33, 0), n, 9.0, v, sustain=1.3)
    s.bell(s.beat(33, 2), "A3", 0.12, decay=8.0, pan=-0.6)
    out = s.render(MIX, WET, ir_seconds=4.6, seed=seed, post=POST, tail=10)
    return finish(out, bars * 4 * 60 / 66, False, -21.0)


def radio(s: M.Score, start: float, length: float):
    """Static and a voice-like murmur that never becomes words (from the first title theme)."""
    n = int(length * SR)
    t = np.arange(n) / SR
    rng = s.rng
    window = np.clip(t / 0.15, 0, 1) * np.clip((length - t) / 0.4, 0, 1)
    static = M.bandpass(rng.normal(0, 1, n), 700, 3400) * (0.5 + 0.5 * (rng.random(n) < 0.002))
    syll = 0.5 + 0.5 * np.sin(2 * np.pi * 4.3 * t) * np.sin(2 * np.pi * 1.7 * t + 0.4)
    murmur = M.bandpass(np.sign(np.sin(2 * np.pi * (180 + 25 * np.sin(2 * np.pi * 3 * t)) * t)), 400, 2600) * syll
    s.place("air", start, (static * 0.5 + murmur * 0.3) * window * 0.5, pan=0.4)


# ============================================================================= exploration

def empty_roads(seed=9201):
    bpm, bars = 70, 40
    s = M.Score(bars * 4 * 60 / bpm + 2, seed, bpm=bpm)
    wind(s, 0.4)
    prog = [C("A2", "E3", "A3", "C4", "E4"), C("F2", "C3", "F3", "A3", "C4"), C("C3", "G3", "C4", "E4", "G4"), C("B2", "G3", "B3", "D4", "G4"),
            C("A2", "E3", "A3", "C4", "E4"), C("F2", "C3", "F3", "A3", "C4"), C("C3", "G3", "C4", "E4", "G4"), C("E2", "B2", "E3", "G#3", "B3")]
    for bar in range(bars):
        notes = prog[bar % 8]
        pattern = [notes[0], notes[1], notes[2], notes[3], notes[4], notes[3], notes[2], notes[1]]
        quiet = bar < 4 or bar >= bars - 4
        for k, n in enumerate(pattern):
            if k == 0:
                s.piano(s.beat(bar), n, 4 * s.spb, 0.4)
            else:
                s.piano(s.beat(bar, k * 0.5), n, 1.2 * s.spb, (0.22 if quiet else 0.28) + 0.03 * (k == 4))
        if 16 <= bar < 36:
            chord_strings(s, s.beat(bar), 4 * s.spb, [notes[0] - 12, notes[2] + 12], 0.2, attack=1.0)
    tune_a = [(0, 0, "E5", 2), (0, 2, "D5", 1), (0, 3, "C5", 1), (1, 0, "C5", 2), (1, 2, "A4", 2), (2, 0, "G4", 2), (2, 2, "E4", 1), (2, 3, "G4", 1),
              (3, 0, "B4", 4), (4, 0, "E5", 2), (4, 2, "D5", 1), (4, 3, "C5", 1), (5, 0, "A5", 2), (5, 2, "G5", 2), (6, 0, "E5", 3), (6, 3, "D5", 1),
              (7, 0, "E5", 4)]
    melody(s, 8, tune_a, 0.46)
    melody(s, 16, tune_a, 0.5)
    melody(s, 24, [(b, bt, M.m(n) - 12, l) for b, bt, n, l in tune_a], 0.42, inst="strings", vibrato=0.005)
    melody(s, 24, tune_a, 0.4)
    s.bell(s.beat(bars - 2), "E5", 0.1, decay=6.0, pan=0.5)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, bars * 4 * 60 / bpm, False, -22.0)


def what_remains(seed=9301):
    bpm, bars = 58, 44
    s = M.Score(bars * 3 * 60 / bpm + 2, seed, bpm=bpm, beats_per_bar=3)
    wind(s, 0.3, 200, 1200)
    prog = [("D2", C("F3", "A3", "D4")), ("Bb1", C("F3", "A3", "D4")), ("F2", C("F3", "A3", "C4")), ("E2", C("G3", "C4", "E4")),
            ("D2", C("F3", "A3", "D4")), ("G1", C("F3", "Bb3", "D4")), ("Bb1", C("F3", "Bb3", "D4")), ("A1", C("E3", "G3", "C#4"))]
    for bar in range(bars):
        root, upper = prog[bar % 8]
        s.piano(s.beat(bar, 0), root, 3 * s.spb, 0.4, pan=-0.3)
        s.piano(s.beat(bar, 0), M.m(root) + 12, 3 * s.spb, 0.25, pan=-0.25)
        if 4 <= bar < bars - 4:
            for beat in (1, 2):
                for n in upper:
                    s.piano(s.beat(bar, beat), n, 0.8 * s.spb, 0.16)
    cello = [(0, 0, "D4", 3), (1, 0, "F4", 2), (1, 2, "E4", 1), (2, 0, "C4", 3), (3, 0, "E4", 2), (3, 2, "G4", 1), (4, 0, "F4", 2), (4, 2, "E4", 1),
             (5, 0, "D4", 3), (6, 0, "Bb3", 2), (6, 2, "C4", 1), (7, 0, "A3", 3)]
    violin = [(0, 0, "A4", 2), (0, 2, "Bb4", 1), (1, 0, "A4", 3), (2, 0, "G4", 2), (2, 2, "F4", 1), (3, 0, "E4", 3), (4, 0, "D4", 2), (4, 2, "F4", 1),
              (5, 0, "Bb4", 3), (6, 0, "A4", 2), (6, 2, "G4", 1), (7, 0, "A4", 3)]
    melody(s, 8, [(b, bt, M.m(n) - 12, l) for b, bt, n, l in cello], 0.55, inst="strings", voices=2, vibrato=0.006, attack=0.35)
    melody(s, 16, violin, 0.45, inst="strings", voices=3, vibrato=0.006, attack=0.35)
    melody(s, 24, [(b, bt, M.m(n) - 12, l) for b, bt, n, l in cello], 0.5, inst="strings", voices=2, vibrato=0.006, attack=0.35)
    melody(s, 24, violin, 0.4, inst="strings", voices=3, vibrato=0.006, attack=0.35)
    for bar in range(32, 40):
        if bar % 2 == 0:
            s.piano(s.beat(bar, 1), M.m(prog[bar % 8][1][-1]) + 12, 2.5, 0.22)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, bars * 3 * 60 / bpm, False, -22.0)


GUITAR = {"Em": (C("E2", "B2"), C("G3", "B3", "E4")), "Cmaj7": (C("C3", "G2"), C("G3", "B3", "E4")), "G": (C("G2", "D3"), C("G3", "B3", "D4")),
          "D/F#": (C("F#2", "A2"), C("D3", "A3", "D4", "F#4")), "Am7": (C("A2", "E3"), C("G3", "C4", "E4")), "B7": (C("B2", "F#2"), C("A3", "D#4", "F#4")),
          "B7sus": (C("B2", "F#2"), C("A3", "E4", "F#4")), "Dsus": (C("D3", "A2"), C("A3", "D4", "G4")), "D": (C("D3", "A2"), C("A3", "D4", "F#4")),
          "C": (C("C3", "G2"), C("G3", "C4", "E4")), "Am": (C("A2", "E3"), C("A3", "C4", "E4"))}


def pick(s: M.Score, bar, name, vel=0.55):
    """Travis picking: the thumb alternates root and fifth on the beats, the fingers fill the off-beats."""
    bass, top = GUITAR[name]
    for beat in range(4):
        s.guitar(s.beat(bar, beat), bass[beat % 2], 1.6 * s.spb, vel * (1.0 if beat == 0 else 0.8), pan=-0.1)
        s.guitar(s.beat(bar, beat + 0.5), top[(beat * 2 + 1) % len(top)], 1.2 * s.spb, vel * 0.7, pan=0.25)
    s.guitar(s.beat(bar, 0), top[-1], 1.5 * s.spb, vel * 0.75, pan=0.3)


def overgrown(seed=9401):
    bpm, bars = 76, 42
    s = M.Score(bars * 4 * 60 / bpm + 2, seed, bpm=bpm)
    wind(s, 0.35)
    prog = ["Em", "Cmaj7", "G", "D/F#", "Em", "Cmaj7", "Am7", "B7sus"]
    pads = [C("E3", "G3", "B3"), C("C3", "E3", "B3"), C("G2", "D3", "B3"), C("F#2", "D3", "A3"), C("E3", "G3", "B3"), C("C3", "E3", "B3"),
            C("A2", "E3", "C4"), C("B2", "D#3", "A3")]
    for bar in range(bars):
        name = prog[bar % 8]
        if bar == bars - 1:
            name = "Em"
        pick(s, bar, name, 0.5 if bar < bars - 4 else 0.4)
        if 8 <= bar < bars - 2:
            chord_pad(s, s.beat(bar), 4 * s.spb, pads[bar % 8], 0.14, attack=1.4)
        if 24 <= bar < 34:
            s.strings(s.beat(bar), pads[bar % 8][0] - 12, 4 * s.spb, 0.22, attack=1.0)
    answers = [(0, 2, "B5", 1), (0, 3, "G5", 1), (1, 2, "E5", 2), (2, 2, "D5", 1), (2, 3, "B4", 1), (3, 2, "A4", 2), (4, 2, "B5", 1), (4, 3, "G5", 1),
               (5, 2, "E5", 2), (6, 2, "C6", 1), (6, 3, "B5", 1), (7, 2, "F#5", 2)]
    melody(s, 16, answers, 0.3)
    melody(s, 24, answers, 0.34)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, bars * 4 * 60 / bpm, False, -22.0)


def static_lullaby(seed=9501):
    bpm, bars = 64, 34
    s = M.Score(bars * 4 * 60 / bpm + 2, seed, bpm=bpm)
    prog = [C("F3", "A3", "C4"), C("E3", "A3", "C4"), C("D3", "F3", "Bb3"), C("Db3", "F3", "Bb3"), C("C3", "F3", "A3"), C("Bb2", "E3", "G3"),
            C("A2", "C3", "F3"), C("A2", "C3", "F3")]
    roots = ["F2", "A2", "Bb1", "Bb1", "C2", "C2", "F2", "F2"]
    tune = [(0, 0, "A5", 1), (0, 1, "C6", 1), (0, 2, "A5", 1), (0, 3, "G5", 1), (1, 0, "E5", 2), (1, 2, "C5", 2), (2, 0, "D5", 1), (2, 1, "F5", 1),
            (2, 2, "D5", 1), (2, 3, "C5", 1), (3, 0, "Db5", 2), (3, 2, "Bb4", 2), (4, 0, "C5", 1), (4, 1, "F5", 1), (4, 2, "A5", 1), (4, 3, "C6", 1),
            (5, 0, "Bb5", 2), (5, 2, "G5", 1), (5, 3, "E5", 1), (6, 0, "F5", 4)]
    for bar in range(bars):
        k = bar % 8
        chord_strings(s, s.beat(bar), 4 * s.spb, prog[k], 0.13, attack=1.3)
        s.piano(s.beat(bar), roots[k], 4 * s.spb, 0.3, pan=-0.3)
    for start in (2, 10, 18):
        melody(s, start, tune, 0.32, sustain=0.6)
        for bar, beat, note, beats in tune:  # the music-box shimmer two octaves up
            s.bell(s.beat(start + bar, beat), M.m(note) + 12, 0.035, decay=1.6, pan=0.3)
    melody(s, 26, tune[:12], 0.26, sustain=0.6)
    for when in (6.0, 41.0, 77.0, 102.0):
        radio(s, when, 1.6)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, bars * 4 * 60 / bpm, False, -23.0)


# ============================================================================= night

def long_night(seed=9601):
    length = 150.0
    s = M.Score(length + 2, seed)
    wind(s, 0.3, 150, 900)
    s.drone(0.0, "D1", length - 6, 0.55, attack=10.0, release=10.0)
    s.drone(0.0, "D2", length - 6, 0.35, attack=14.0, release=10.0)
    s.drone(30.0, "A1", 80.0, 0.25, attack=12.0, release=12.0)
    t = 9.0
    lows = ["D2", "F2", "A2", "C3", "D3", "E3", "A2", "D3"]
    while t < length - 14:
        note = lows[int(s.rng.integers(len(lows)))]
        s.piano(t, note, 5.0, float(s.rng.uniform(0.22, 0.36)), sustain=1.3)
        if s.rng.random() < 0.18:
            s.piano(t + 0.02, M.m(note) + 1, 4.0, 0.12)  # a minor second, for unease
        t += float(s.rng.uniform(7.0, 12.0))
    for when, note in ((24.0, "D4"), (58.0, "A3"), (96.0, "D4"), (128.0, "F3")):
        s.bell(when, note, 0.14, decay=8.0, pan=float(s.rng.uniform(-0.7, 0.7)))
    for start, notes in ((60.0, ("D3", "F3", "A3")), (78.0, ("Bb2", "D3", "F3"))):
        for n in notes:
            s.choir(start, n, 16.0, 0.08, vowel="oo")
    for start, note in ((92.0, "D6"), (104.0, "A5")):
        s.strings(start, note, 12.0, 0.05, attack=4.0, release=4.0, vibrato=0.003, bright=0.2)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, length, False, -24.0)


def no_lights(seed=9701):
    length = 140.0
    s = M.Score(length + 2, seed)
    wind(s, 0.22, 120, 700)
    s.drone(0.0, "C1", length - 6, 0.5, attack=12.0, release=10.0, grit=0.5)
    s.drone(20.0, "C2", 90.0, 0.25, attack=10.0, release=10.0)
    for when in (12.0, 64.0, 118.0):
        s.place("low", when, M.sub_drop(s.rng, 0.7, 4.0))
    for when, note in ((30.0, "G3"), (52.0, "Db4"), (96.0, "G3"), (110.0, "Ab3")):
        s.place("bell", when, M.bell(M.hz(note), 0.2, s.rng, decay=6.0, bowed=True, dur=9.0), pan=float(s.rng.uniform(-0.6, 0.6)))
    for when in (40.0, 85.0):
        chord_strings(s, when, 8.0, C("C3", "Db3", "G3"), 0.18, attack=4.0, release=4.0, vibrato=0.002)
    for k in range(5):
        s.place("low", 70.0 + k * 1.2, M.lowpass(M.heartbeat(s.rng, 0.5), 200))
    t = 8.0
    drips = ["C6", "Eb6", "G5", "Db6", "Bb5"]
    while t < length - 10:
        s.piano(t, drips[int(s.rng.integers(len(drips)))], 2.0, float(s.rng.uniform(0.12, 0.2)), sustain=0.8)
        t += float(s.rng.uniform(6.0, 14.0))
    s.choir(56.0, "C3", 22.0, 0.07, vowel="mm")
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, length, False, -24.0)


def hollow_hours(seed=9801):
    length = 132.0
    s = M.Score(length + 2, seed)
    wind(s, 0.25, 200, 1000)
    pads = [C("A2", "E3", "B3", "C4"), C("F2", "C3", "E3", "B3"), C("D2", "A2", "E3", "F3", "C4"), C("E2", "B2", "A3", "E4")]
    t, k = 2.0, 0
    while t < length - 16:
        chord_pad(s, t, 15.0, pads[k % 4], 0.18, attack=4.0, release=5.0)
        t += 16.0
        k += 1
    for start, note in ((20.0, "E6"), (52.0, "A5"), (84.0, "B5"), (100.0, "E6")):
        s.strings(start, note, 10.0, 0.06, attack=3.0, release=4.0, vibrato=0.0035, bright=0.2, voices=2)
    t = 6.0
    drips = ["A5", "E6", "C6", "B5", "G5"]
    while t < length - 10:
        s.piano(t, drips[int(s.rng.integers(len(drips)))], 2.5, float(s.rng.uniform(0.14, 0.24)), sustain=0.9)
        if s.rng.random() < 0.4:
            s.piano(t + 0.6, drips[int(s.rng.integers(len(drips)))], 2.0, 0.12, sustain=0.9)
        t += float(s.rng.uniform(5.0, 10.0))
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, length, False, -24.0)


# ============================================================================= danger (loops)

def ostinato(s: M.Score, bar, notes, vel, bus="low"):
    for k, n in enumerate(notes):
        s.strings(s.beat(bar, k * 0.5), n, 0.16, vel * (1.15 if k in (0, 3, 6) else 0.85), bus=bus, attack=0.015, release=0.12, voices=3,
                  vibrato=0.0, bright=0.7)


def pursuit(seed=9901):
    bpm, bars = 96, 32
    loop = bars * 4 * 60 / bpm
    s = M.Score(loop + 2, seed, bpm=bpm)
    figure = C("D2", "D2", "D3", "D2", "D2", "D2", "C3", "D2")
    for bar in range(bars):
        fig = figure if bar % 8 < 6 else [n + (1 if bar % 8 == 6 else -2) for n in figure]
        ostinato(s, bar, fig, 0.5)
        s.hit(s.beat(bar, 0), M.drum(s.rng, 48, 0.9, 1.0))
        s.hit(s.beat(bar, 2.5), M.drum(s.rng, 52, 0.6, 0.8))
        if bar % 4 == 3:
            for beat in (3.0, 3.5):
                s.hit(s.beat(bar, beat), M.drum(s.rng, 62, 0.55, 0.5))
        for k in range(16):
            s.hit(s.beat(bar, k * 0.25), M.tick(s.rng, 3600, 0.25 if k % 4 == 0 else 0.12), bus="ticks", pan=0.4)
        if 8 <= bar < 16 or 24 <= bar < 32:
            if bar % 2 == 0:
                for n in ("D2", "A2", "D3"):
                    s.brass(s.beat(bar), n, 0.6, 0.5)
    for start, notes in ((16, ("D4", "Eb4")), (24, ("A4", "Bb4"))):
        for n in notes:
            s.strings(s.beat(start), n, 8 * 4 * s.spb, 0.16, attack=6.0, release=2.0, vibrato=0.009)
    s.place("air", s.beat(12), M.riser(s.rng, 4 * 4 * s.spb, 0.4))
    s.place("air", s.beat(28), M.riser(s.rng, 4 * 4 * s.spb, 0.5))
    out = s.render(MIX, WET, ir_seconds=2.8, seed=seed, post=POST, tail=6)
    return finish(out, loop, True, -19.0)


def they_hear_you(seed=10001):
    bpm, bars = 84, 24
    loop = bars * 4 * 60 / bpm
    s = M.Score(loop + 2, seed, bpm=bpm)
    s.drone(0.0, "E1", loop + 2, 0.38, attack=2.0, release=3.0, grit=0.6)
    for bar in range(bars):
        for beat in (0, 2):
            s.place("low", s.beat(bar, beat), M.heartbeat(s.rng, 0.55))
        for k in range(8):
            s.hit(s.beat(bar, k * 0.5), M.tick(s.rng, 2600, 0.2 if k % 2 == 0 else 0.1), bus="ticks", pan=-0.3)
        if bar % 4 == 0:
            for n in ("E2", "F2", "B2"):
                s.piano(s.beat(bar), n, 0.6, 0.5, release=0.1)
    for start, notes in ((0, ("E3", "F3")), (8, ("B3", "C4")), (16, ("E4", "F4", "Bb4"))):
        for n in notes:
            tremolo = M.strings(M.hz(n), 8 * 4 * s.spb, 0.18, s.rng, attack=5.0, release=1.5, vibrato=0.003)
            tt = np.arange(len(tremolo)) / SR
            # bowed tremolo: fast, but with rounded edges (a square wave would click)
            s.place("strings", s.beat(start), tremolo * (0.65 + 0.35 * np.tanh(3.0 * np.sin(2 * np.pi * 8.0 * tt))))
    for when in (3.0, 21.0, 40.0, 55.0):
        s.place("bell", when, M.bell(M.hz("G#3") * s.rng.uniform(0.9, 1.1), 0.25, s.rng, decay=4.0, bowed=True, dur=5.0),
                pan=float(s.rng.uniform(-0.8, 0.8)))
    out = s.render(MIX, WET, ir_seconds=2.6, seed=seed, post=POST, tail=6)
    return finish(out, loop, True, -19.5)


def horde(seed=10101):
    bpm, bars = 104, 32
    loop = bars * 4 * 60 / bpm
    s = M.Score(loop + 2, seed, bpm=bpm)
    bassline = C("D2", "D2", "A2", "D2", "C3", "D2", "A2", "D2")
    for bar in range(bars):
        ostinato(s, bar, bassline if bar % 8 < 6 else [n - 2 for n in bassline], 0.55)
        for beat, low, vel in ((0, 46, 1.0), (1.5, 58, 0.7), (2, 50, 0.85), (3, 70, 0.6), (3.5, 64, 0.5)):
            s.hit(s.beat(bar, beat), M.drum(s.rng, low, vel, 0.7))
        for beat in (1, 3):
            body = M.drum(s.rng, 120, 0.3, 0.15)
            snare = M.bandpass(s.rng.normal(0, 1, len(body)), 900, 5000) * np.exp(-np.arange(len(body)) / SR / 0.06)
            s.hit(s.beat(bar, beat), snare * 0.5 + body)
        if bar % 2 == 0:
            chord = ("D2", "A2", "D3") if bar % 8 < 4 else ("Bb1", "F2", "Bb2")
            for n in chord:
                s.brass(s.beat(bar), n, 1.0, 0.6)
                s.brass(s.beat(bar, 2.5), n, 0.4, 0.45)
    s.strings(s.beat(0), "D5", 16 * 4 * s.spb, 0.14, attack=4.0, vibrato=0.008)
    s.strings(s.beat(16), "E5", 8 * 4 * s.spb, 0.15, attack=3.0, vibrato=0.008)
    s.strings(s.beat(24), "F5", 8 * 4 * s.spb, 0.17, attack=3.0, vibrato=0.008)
    for bar in range(16, 32, 2):
        for n in ("D4", "F4", "A4"):
            s.choir(s.beat(bar), n, 2 * 4 * s.spb, 0.12, vowel="aa", attack=0.8, release=1.5)
    out = s.render(MIX, WET, ir_seconds=2.6, seed=seed, post=POST, tail=6)
    return finish(out, loop, True, -18.0)


# ============================================================================= safe

def campfire(seed=10201):
    bpm, bars = 72, 38
    s = M.Score(bars * 4 * 60 / bpm + 2, seed, bpm=bpm)
    wind(s, 0.18, 150, 800)
    prog = ["G", "Em", "C", "D", "G", "Em", "Am7", "Dsus"]
    pads = [C("G2", "D3", "B3"), C("E2", "B2", "G3"), C("C3", "G3", "E4"), C("D3", "A3", "F#4"), C("G2", "D3", "B3"), C("E2", "B2", "G3"),
            C("A2", "E3", "C4"), C("D3", "A3", "G4")]
    for bar in range(bars):
        name = prog[bar % 8] if bar < bars - 1 else "G"
        if name == "Dsus" and bar % 8 == 7:
            pick(s, bar, "Dsus", 0.5)
        else:
            pick(s, bar, name, 0.52)
        if 8 <= bar < bars - 2:
            chord_pad(s, s.beat(bar), 4 * s.spb, pads[bar % 8], 0.13, attack=1.6)
    tune = [(0, 0, "B4", 2), (0, 2, "A4", 1), (0, 3, "G4", 1), (1, 0, "G4", 2), (1, 2, "E4", 2), (2, 0, "E4", 1), (2, 1, "G4", 1), (2, 2, "A4", 2),
            (3, 0, "F#4", 4), (4, 0, "B4", 2), (4, 2, "D5", 2), (5, 0, "E5", 2), (5, 2, "B4", 2), (6, 0, "C5", 1), (6, 1, "B4", 1), (6, 2, "A4", 2),
            (7, 0, "A4", 3), (7, 3, "D4", 1)]
    melody(s, 16, tune, 0.36)
    melody(s, 24, tune, 0.4)
    melody(s, 24, [(b, bt, M.m(n) - 12, l) for b, bt, n, l in tune], 0.3, inst="strings", vibrato=0.005)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, bars * 4 * 60 / bpm, False, -22.0)


def shelter(seed=10301):
    bpm, bars = 66, 34
    s = M.Score(bars * 4 * 60 / bpm + 2, seed, bpm=bpm)
    prog = [("F2", C("A3", "C4", "F4")), ("E2", C("G3", "C4", "E4")), ("D2", C("F3", "A3", "D4")), ("Bb1", C("F3", "Bb3", "D4")),
            ("A1", C("F3", "A3", "C4")), ("Bb1", C("F3", "Bb3", "D4")), ("C2", C("F3", "G3", "C4")), ("F2", C("F3", "A3", "C4"))]
    for bar in range(bars):
        root, upper = prog[bar % 8]
        if bar == bars - 3:
            root, upper = "Bb1", C("F3", "Bb3", "Db4")  # the borrowed minor iv
        s.piano(s.beat(bar), root, 4 * s.spb, 0.36, pan=-0.3)
        for n in upper:
            s.piano(s.beat(bar, 0), n, 2 * s.spb, 0.2)
            s.piano(s.beat(bar, 2), n, 2 * s.spb, 0.17)
        if 8 <= bar < bars - 1:
            chord_strings(s, s.beat(bar), 4 * s.spb, [M.m(root) + 12, upper[1]], 0.2, attack=1.2)
    hymn = [(0, 0, "A4", 2), (0, 2, "G4", 1), (0, 3, "F4", 1), (1, 0, "G4", 2), (1, 2, "C5", 2), (2, 0, "A4", 3), (2, 3, "F4", 1), (3, 0, "F4", 2),
            (3, 2, "D4", 2), (4, 0, "C4", 1), (4, 1, "F4", 1), (4, 2, "A4", 2), (5, 0, "D5", 2), (5, 2, "C5", 1), (5, 3, "Bb4", 1), (6, 0, "A4", 2),
            (6, 2, "G4", 2), (7, 0, "F4", 4)]
    melody(s, 8, hymn, 0.44)
    melody(s, 16, hymn, 0.48)
    melody(s, 16, [(b, bt, M.m(n) + 12, l) for b, bt, n, l in hymn], 0.3, inst="strings", vibrato=0.005)
    melody(s, 24, hymn[:9], 0.4)
    out = s.render(MIX, WET, seed=seed, post=POST)
    return finish(out, bars * 4 * 60 / bpm, False, -22.0)


# ============================================================================= build

TRACKS = {
    "title_theme": title, "empty_roads": empty_roads, "what_remains": what_remains, "overgrown": overgrown, "static_lullaby": static_lullaby,
    "long_night": long_night, "no_lights": no_lights, "hollow_hours": hollow_hours, "pursuit": pursuit, "they_hear_you": they_hear_you,
    "horde": horde, "campfire": campfire, "shelter": shelter,
}
TITLES = {"title_theme": "Ninety Days", "empty_roads": "Empty Roads", "what_remains": "What Remains", "overgrown": "Overgrown",
          "static_lullaby": "Static Lullaby", "long_night": "Long Night", "no_lights": "No Lights", "hollow_hours": "Hollow Hours",
          "pursuit": "Pursuit", "they_hear_you": "They Hear You", "horde": "Horde", "campfire": "Campfire", "shelter": "Shelter"}


def update_sounds_json() -> None:
    path = OUT.parent / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    for name in TRACKS:
        if name == "title_theme":
            continue
        sounds[f"music.{name}"] = {"sounds": [{"name": f"deadlands:music/{name}", "stream": True}]}
    path.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang({f"music.deadlands.{name}": f"DEADLANDS - {title}" for name, title in TITLES.items()})
    D.merge_lang({
        "config.deadlands.audio.dynamicMusic": "DEADLANDS soundtrack in the world",
        "config.deadlands.audio.musicVolume": "Soundtrack volume",
        "config.deadlands.audio.ambienceDensity": "How often ambient sounds play",
        "config.deadlands.audio.menuAmbience": "Wind and crows on the main menu",
    })


def build(only: list[str] | None = None) -> None:
    for name, make in TRACKS.items():
        if only and name not in only:
            continue
        audio = make()
        write_ogg(f"music/{name}", audio.astype(np.float32), quality=3)
        print(f"  music/{name}: {len(audio) / SR:.1f} s")
    update_sounds_json()


if __name__ == "__main__":
    import sys

    build(sys.argv[1:] or None)
    print("music written")
