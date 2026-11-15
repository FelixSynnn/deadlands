"""D.O.T., reanimated at 60 fps: every clip of dot_anims.py (same names, same timings, same game code), re-keyed every
three frames with a layer of life on top that never stops:

  * breathing: the chest heaving, the shoulders lifting with it, the neck easing
  * the coat-skirt and the belt cloths fluttering a beat behind the body, more as he moves
  * fingers flexing (the hands curl and splay on their own rhythms), the hat settling
  * the glitch: now and then, for a few frames, his head and one hand jerk out of place

Actions are interpolated from their keys (eased) before the layer goes on, so they move between keys instead of
holding still. Run in art/blender/dot.blend after dot_rig.py; then dl_export.export("DOT", "dot.anim", pelvis="pelvis").
"""
import math

import dl_anim as A
import dot_anims as D

TAU = math.tau
SCALE = 3  # frames a tick


def keyed(keys):
    """A pose at any tick from a clip's (tick, pose) keys, eased between them."""
    def at(t):
        if t <= keys[0][0]:
            return D.copy(keys[0][1])
        for (f0, p0), (f1, p1) in zip(keys, keys[1:]):
            if t <= f1:
                k = A.smooth((t - f0) / max(1e-6, f1 - f0))
                return {b: [A.lerp(x, y, k) for x, y in zip(p0[b], p1[b])] for b in p0}
        return D.copy(keys[-1][1])
    return at


def alive(p, t, amount, moving=0.0):
    b = math.sin(TAU * t / 34)
    D.add(p, chest=(0.03 * b * amount, 0, 0), spine=(0.01 * b * amount, 0, 0), neck=(-0.02 * b * amount, 0, 0),
          arm_r=(0, 0, -0.025 * b * amount), arm_l=(0, 0, 0.025 * b * amount))
    flutter = 0.05 + 0.12 * moving
    D.add(p, skirt=(flutter * math.sin(TAU * t / 22 - 1.1) * amount, 0.03 * math.sin(TAU * t / 31 - 0.7) * amount, 0),
          cloth=(1.4 * flutter * math.sin(TAU * t / 17 - 1.6) * amount, 0, 0.05 * math.sin(TAU * t / 26) * amount),
          hat=(0.02 * math.sin(TAU * t / 19 - 2.0) * amount, 0, 0.015 * math.sin(TAU * t / 27) * amount))
    D.add(p, hand_r=(0.12 * math.sin(TAU * t / 13) * math.sin(TAU * t / 47) * amount, 0, 0),
          hand_l=(0.1 * math.sin(TAU * t / 11 + 1) * math.sin(TAU * t / 53) * amount, 0, 0))
    # The glitch: a few frames of a jerk, every few seconds on his own beat.
    beat = int(t // 23)
    roll = (beat * 92821) % 100
    into = t - beat * 23
    if roll < 14 and into < 1.5:
        sgn = 1 if roll % 2 else -1
        D.add(p, head=(0.12 * amount, 0.3 * sgn * amount, 0.45 * sgn * amount), hand_r=(-0.4 * amount, 0, 0))
    return p


def gen_from(spec_gen, length, amount, moving):
    def gen(frame):
        t = frame / SCALE
        return alive(spec_gen(t % length if length else t), t, amount, moving)
    return gen


def clips():
    out = []
    for name, (spec, loop, full, stepped, ground) in D.CLIPS.items():
        if isinstance(spec, tuple):
            gen, length, _ = spec
            moving = 1.0 if name in ("prowl", "sprint") else 0.3 if name == "climb" else 0.0
            out.append(A.Clip(name, length * SCALE, gen=gen_from(gen, length, 1.0, moving), step=SCALE, loop=loop, full=full,
                              stepped=stepped, ground=ground))
        else:
            keys = spec()
            length = keys[-1][0]
            at = keyed(keys)
            amount = 0.35 if stepped else 0.8
            out.append(A.Clip(name, length * SCALE, gen=gen_from(at, 0, amount, 0.0), step=SCALE, loop=loop, full=full,
                              stepped=stepped, ground=ground))
    return out


def build():
    return A.build("DOT", clips(), "pelvis", {"foot_r", "foot_l", "leg_r", "leg_l"}, 0.25)
