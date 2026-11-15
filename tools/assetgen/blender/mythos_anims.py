"""Mythos' animations, keyed at 60 fps on the rig mythos_build.py makes (run it first, in art/blender/mythos.blend).

Clips (frames at 60 fps; the game's tick = 3 frames). The hit, cast and summon frames match MythosEntity's timings:
  idle 180 (loop)         hunched, heavy breathing through the jaw, the head jerking from point to point and holding,
                          claws flexing, the tail swaying in an S
  walk 120 (loop)         a heavy stalking stride, claws hanging and swinging against the legs, tail swaying
  chase 66 (loop)         a hunched lunging run, arms pumping claws-first, jaw open, the tail streaming behind
  slash 54                right claw drawn high and back (21), raked down and across (27: the hit), follow-through
  entangle 45             left arm drawn back (12), thrown forward pointing (24: the tether flies)
  mass_infection 60       both arms raised, jaw opening (20), heaved forward (36: the packets fly)
  summon 75               drops low, claws into the ground (30), rises with arms spread (45: they come)
  rejuvenate 60 (loop)    hunched over, arms crossed over the core, shivering (the channel)
  blink 30 (stepped)      broken frames: wrong poses snapped in and out
  phase 60                rears up and roars, shaking (at 50% and at 20%)
  entrance 180            from the tornado: coiled tight, unfolding, rising, the head snapping up, the roar
  death 150               staggers, falls to his knees, slumps, glitches, collapses face down
"""
import math

import dl_anim as A
from dl_anim import rot

TAU = math.tau


# His arms hang straighter than the keys were first written for (Mythos.glb's blocky body): every pose lowers them a
# little more.
DOWN_BIAS = 0.3


def arm(side, down, swing, out=0.0):
    """A T-posed arm: lowered by `down` (plus DOWN_BIAS), swung forward (negative) or back by `swing`, spread by `out`."""
    s = -1.0 if side == "r" else 1.0
    return rot(("z", s * (down + DOWN_BIAS)), ("y", s * out), ("x", swing))


BASE = {
    "pelvis": (0, 0, 0), "spine": (0.25, 0, 0), "chest": (0.15, 0, 0), "neck": (-0.2, 0, 0), "head": (-0.15, 0, 0),
    "jaw": (0.12, 0, 0),
    "arm_r": arm("r", 1.15, -0.3), "forearm_r": (0, -0.6, 0), "hand_r": (0, 0, -0.15), "claws_r": (0, 0, -0.4),
    "arm_l": arm("l", 1.15, -0.3), "forearm_l": (0, 0.6, 0), "hand_l": (0, 0, 0.15), "claws_l": (0, 0, 0.4),
    "leg_r": (-0.2, 0, 0.05), "shin_r": (0, 0, 0), "foot_r": (0, 0, 0),
    "leg_l": (-0.2, 0, -0.05), "shin_l": (0, 0, 0), "foot_l": (0, 0, 0),
    "tail1": (0.15, 0, 0), "tail2": (-0.15, 0.1, 0), "tail3": (-0.1, 0.15, 0), "tail4": (-0.1, 0.1, 0),
}
P = A.Poser(BASE)
pose, add, put, copy, jitter = P.pose, P.add, P.put, P.copy, P.jitter
UPPER = ["spine", "chest", "neck", "head", "jaw", "arm_r", "forearm_r", "claws_r", "arm_l", "forearm_l", "claws_l"]


def tail_sway(p, t, period, amp, lift=0.0):
    for i, b in enumerate(("tail1", "tail2", "tail3", "tail4")):
        add(p, **{b: (lift * (1 if i == 0 else -0.3), amp * math.sin(TAU * t / period - i * 0.8), 0)})


def snaps(t, targets):
    prev, cur, start = targets[-1][1], targets[0][1], targets[0][0]
    for f, v in targets:
        if t >= f:
            prev, cur, start = cur, v, f
    u = (t - start) / 6.0
    if u >= 1.0:
        return cur
    e = 1 - (1 - u) ** 3 + 0.2 * math.sin(math.pi * u)
    return tuple(A.lerp(a, b, e) for a, b in zip(prev, cur))


def idle(t):
    p = pose()
    b = math.sin(TAU * t / 90)
    add(p, chest=(0.05 * b, 0, 0), spine=(0.015 * b, 0, 0), neck=(-0.03 * b, 0, 0), jaw=(0.06 * (b + 1) * 0.5, 0, 0))
    p["loc"] = [0.3 * math.sin(TAU * t / 180), -0.15 * b, 0]
    pitch, yaw, roll = snaps(t, [(0, (0, 0, 0)), (30, (0.05, 0.4, 0.1)), (66, (0.1, 0.4, 0.45)), (100, (-0.08, -0.35, 0)),
                                 (132, (0.12, -0.3, -0.3)), (160, (0, 0, 0))])
    add(p, head=(pitch, yaw, roll), neck=(0, yaw * 0.25, 0))
    flex = 0.25 * (0.5 + 0.5 * math.sin(TAU * t / 60))
    add(p, claws_r=(0, 0, -flex), claws_l=(0, 0, flex * 0.6))
    add(p, arm_r=(0.04 * b, 0, 0), arm_l=(0.04 * b, 0, 0))
    tail_sway(p, t, 180, 0.18)
    return p


def leg(u, amp, kick, duty):
    u %= 1.0
    if u < duty:
        return A.lerp(-amp, amp, u / duty), 0.0
    k = (u - duty) / (1 - duty)
    lift = math.sin(math.pi * k)
    return A.lerp(amp, -amp, A.smooth(k)), lift * kick


def walk(t):
    u = t / 120.0
    ph = TAU * u
    c, s = math.cos(ph), math.sin(ph)
    p = pose(spine=(0.32, 0, 0), neck=(-0.25, 0, 0))
    lr, kr = leg(u, 0.42, 0.7, 0.6)
    ll, kl = leg(u + 0.5, 0.42, 0.7, 0.6)
    add(p, leg_r=(lr - 0.15 * kr, 0, 0), shin_r=(kr * 0.9, 0, 0), foot_r=(-kr * 0.5, 0, 0),
        leg_l=(ll - 0.15 * kl, 0, 0), shin_l=(kl * 0.9, 0, 0), foot_l=(-kl * 0.5, 0, 0))
    put(p, arm_r=arm("r", 1.2, -0.3 + 0.3 * c), arm_l=arm("l", 1.2, -0.3 - 0.3 * c))
    add(p, forearm_r=(0, -0.15 * max(0, -c), 0), forearm_l=(0, 0.15 * max(0, c), 0))
    add(p, pelvis=(0, -0.08 * c, 0.04 * s), spine=(0, 0.05 * c, 0), chest=(0, 0.06 * c, -0.03 * s))
    p["head"][1] -= 0.11 * c * 0.9
    p["head"][2] -= 0.01 * s
    tail_sway(p, t, 60, 0.15)
    p["loc"] = [-0.4 * math.cos(ph - 1.9), 0, 0]
    return p


def chase(t):
    u = t / 66.0
    ph = TAU * u
    c, s = math.cos(ph), math.sin(ph)
    p = pose(spine=(0.65, 0, 0), chest=(0.25, 0, 0), neck=(-0.6, 0, 0), head=(-0.35, 0, 0), jaw=(0.35, 0, 0))
    lr, kr = leg(u, 0.85, 1.0, 0.42)
    ll, kl = leg(u + 0.5, 0.85, 1.0, 0.42)
    add(p, leg_r=(lr - 0.2 * kr, 0, 0), shin_r=(kr * 1.1, 0, 0), foot_r=(-kr * 0.6, 0, 0),
        leg_l=(ll - 0.2 * kl, 0, 0), shin_l=(kl * 1.1, 0, 0), foot_l=(-kl * 0.6, 0, 0))
    put(p, arm_r=arm("r", 1.25, -0.6 + 0.9 * c), arm_l=arm("l", 1.25, -0.6 - 0.9 * c),
        claws_r=(0, 0, -0.1), claws_l=(0, 0, 0.1))
    add(p, pelvis=(0, -0.12 * c, 0.06 * s), chest=(0, 0.15 * c, -0.05 * s))
    p["head"][1] -= 0.03 * c
    flight = max(0.0, math.cos(TAU * (2 * u - 0.92))) ** 3
    p["loc"] = [-0.3 * math.cos(ph - 1.3), -2.0 * flight, 0]
    for i, b in enumerate(("tail1", "tail2", "tail3", "tail4")):
        put(p, **{b: (0.05 if i == 0 else 0.02, 0.1 * math.sin(2 * ph - i * 0.9), 0)})
    return p


def slash():
    r = A.rng(1)
    wind = pose(arm_r=arm("r", 0.15, -2.5, 0.2), forearm_r=(0, -1.0, 0), claws_r=(0, 0, 0.3), chest=(0.05, 0.45, 0),
                spine=(0.1, 0.12, 0), head=(-0.1, -0.3, 0), arm_l=arm("l", 1.0, 0.3), jaw=(0.3, 0, 0),
                leg_r=(0.1, 0, 0.05), leg_l=(-0.35, 0, -0.05))
    hit = pose(arm_r=arm("r", 1.35, -1.2, -0.3), forearm_r=(0, -0.15, 0), claws_r=(0, 0, -0.7), chest=(0.3, -0.6, 0),
               spine=(0.6, -0.15, 0), neck=(-0.45, 0.25, 0), head=(-0.1, 0.25, 0), arm_l=arm("l", 1.2, -0.7),
               jaw=(0.45, 0, 0), leg_r=(-0.55, 0, 0.05), leg_l=(0.3, 0, -0.05), loc=(0, 0, -1.5))
    follow = put(copy(hit), arm_r=arm("r", 1.3, 0.5, -0.4), chest=(0.3, -0.75, 0))
    return [(0, pose()), (15, wind), (21, jitter(wind, r, 0.03, ["arm_r", "chest", "head"])), (27, hit), (36, follow), (54, pose())]


def entangle():
    back = pose(arm_l=arm("l", 0.9, 0.7, 0.3), forearm_l=(0, 1.3, 0), claws_l=(0, 0, 0.8), chest=(0.05, 0.35, 0),
                head=(-0.15, -0.15, 0.2))
    throw = pose(arm_l=arm("l", 0.1, -1.55), forearm_l=(0, 0.05, 0), claws_l=(0, 0, -0.5), chest=(0.25, -0.45, 0),
                 spine=(0.45, -0.1, 0), head=(-0.25, 0.3, 0.15), jaw=(0.4, 0, 0), leg_l=(-0.5, 0, 0), leg_r=(0.3, 0, 0),
                 loc=(0, 0, -1.0))
    hold = put(copy(throw), claws_l=(0, 0, -0.9))
    return [(0, pose()), (12, back), (24, throw), (32, hold), (45, pose())]


def mass_infection():
    r = A.rng(3)
    up = pose(arm_r=arm("r", -0.3, -1.9), arm_l=arm("l", -0.3, -1.9), forearm_r=(0, -0.8, 0), forearm_l=(0, 0.8, 0),
              claws_r=(0, 0, 0.6), claws_l=(0, 0, -0.6), spine=(-0.15, 0, 0), chest=(-0.2, 0, 0), neck=(-0.3, 0, 0),
              head=(-0.5, 0, 0), jaw=(0.55, 0, 0), loc=(0, -0.8, 0))
    heave = pose(arm_r=arm("r", 0.9, -1.4), arm_l=arm("l", 0.9, -1.4), forearm_r=(0, -0.1, 0), forearm_l=(0, 0.1, 0),
                 claws_r=(0, 0, -0.5), claws_l=(0, 0, 0.5), spine=(0.7, 0, 0), chest=(0.3, 0, 0), neck=(-0.55, 0, 0),
                 head=(-0.1, 0, 0), jaw=(0.7, 0, 0), leg_r=(-0.4, 0, 0.05), leg_l=(-0.4, 0, -0.05))
    return [(0, pose()), (12, A.Poser.copy(up)), (20, jitter(up, r, 0.03, UPPER)), (28, jitter(up, r, 0.04, UPPER)),
            (36, heave), (44, jitter(heave, r, 0.03, UPPER)), (60, pose())]


def summon():
    r = A.rng(5)
    low = pose(spine=(0.95, 0, 0), chest=(0.3, 0, 0), neck=(-0.8, 0, 0), head=(-0.2, 0, 0), jaw=(0.3, 0, 0),
               arm_r=arm("r", 1.45, -0.7), arm_l=arm("l", 1.45, -0.7), forearm_r=(0, -0.3, 0), forearm_l=(0, 0.3, 0),
               claws_r=(0, 0, -0.9), claws_l=(0, 0, 0.9), leg_r=(-0.7, 0, 0.1), leg_l=(-0.7, 0, -0.1),
               shin_r=(0.8, 0, 0), shin_l=(0.8, 0, 0), foot_r=(-0.4, 0, 0), foot_l=(-0.4, 0, 0))
    rise = pose(spine=(-0.2, 0, 0), chest=(-0.2, 0, 0), neck=(-0.4, 0, 0), head=(-0.6, 0, 0), jaw=(0.7, 0, 0),
                arm_r=arm("r", 0.2, -0.6, 0.4), arm_l=arm("l", 0.2, -0.6, 0.4), claws_r=(0, 0, 0.5), claws_l=(0, 0, -0.5),
                loc=(0, -1.0, 0))
    return [(0, pose()), (18, low), (30, jitter(low, r, 0.04, UPPER)), (38, low), (45, rise),
            (55, jitter(rise, r, 0.05, UPPER)), (75, pose())]


def rejuvenate(t):
    p = pose(spine=(0.55, 0, 0), chest=(0.25, 0, 0), neck=(-0.2, 0, 0), head=(0.3, 0, 0), jaw=(0.05, 0, 0),
             arm_r=arm("r", 1.0, -1.1, -0.7), arm_l=arm("l", 1.0, -1.1, -0.7), forearm_r=(0, -1.4, 0), forearm_l=(0, 1.4, 0),
             claws_r=(0, 0, -0.8), claws_l=(0, 0, 0.8), leg_r=(-0.5, 0, 0.1), leg_l=(-0.5, 0, -0.1),
             shin_r=(0.5, 0, 0), shin_l=(0.5, 0, 0))
    shiver = math.sin(t * 2.1) * 0.02 + math.sin(t * 3.7) * 0.015
    add(p, chest=(shiver, shiver, 0), head=(0, shiver * 2, shiver), arm_r=(shiver, 0, 0), arm_l=(-shiver, 0, 0))
    b = math.sin(TAU * t / 60)
    add(p, spine=(0.04 * b, 0, 0), chest=(0.05 * b, 0, 0))
    tail_sway(p, t, 60, 0.1, lift=-0.1)
    return p


def blink():
    r = A.rng(9)
    keys = []
    for f in range(0, 12, 2):
        keys.append((f, jitter(pose(), r, 0.6, UPPER + ["leg_r", "leg_l", "tail1", "tail2"])))
    keys.append((12, add(pose(), head=(0, 0, 0.4))))
    keys.append((18, pose()))
    keys.append((30, pose()))
    return keys


def phase():
    r = A.rng(13)
    roar = pose(spine=(-0.35, 0, 0), chest=(-0.3, 0, 0), neck=(-0.5, 0, 0), head=(-0.8, 0, 0), jaw=(0.75, 0, 0),
                arm_r=arm("r", 0.35, -0.4, 0.3), arm_l=arm("l", 0.35, -0.4, 0.3), forearm_r=(0, -0.5, 0), forearm_l=(0, 0.5, 0),
                claws_r=(0, 0, 0.7), claws_l=(0, 0, -0.7), tail1=(-0.4, 0, 0), tail2=(-0.2, 0, 0), loc=(0, -1.5, 0))
    keys = [(0, pose()), (9, add(pose(), spine=(0.4, 0, 0), head=(0.3, 0, 0), jaw=(-0.1, 0, 0))), (15, roar)]
    for f in range(18, 48, 3):
        keys.append((f, jitter(roar, r, 0.05, UPPER + ["tail1", "tail2"])))
    keys += [(48, roar), (60, pose())]
    return keys


def entrance():
    r = A.rng(17)
    coiled = pose(spine=(1.25, 0, 0), chest=(0.5, 0, 0), neck=(-0.2, 0, 0), head=(0.6, 0, 0), jaw=(0, 0, 0),
                  arm_r=arm("r", 1.0, -1.4, -0.8), arm_l=arm("l", 1.0, -1.4, -0.8), forearm_r=(0, -1.6, 0), forearm_l=(0, 1.6, 0),
                  claws_r=(0, 0, -1.0), claws_l=(0, 0, 1.0), leg_r=(-1.1, 0, 0.15), leg_l=(-1.1, 0, -0.15),
                  shin_r=(1.3, 0, 0), shin_l=(1.3, 0, 0), foot_r=(-0.5, 0, 0), foot_l=(-0.5, 0, 0),
                  tail1=(-0.45, 0.8, 0), tail2=(-0.1, 0.9, 0), tail3=(0.0, 0.9, 0), tail4=(0.0, 0.7, 0))  # curled round him, on the ground
    unfold = pose(spine=(0.6, 0, 0), chest=(0.2, 0, 0), neck=(-0.3, 0, 0), head=(0.4, 0, 0),
                  arm_r=arm("r", 0.6, -0.5, 0.4), arm_l=arm("l", 0.6, -0.5, 0.4), claws_r=(0, 0, 0.4), claws_l=(0, 0, -0.4),
                  leg_r=(-0.5, 0, 0.1), leg_l=(-0.5, 0, -0.1), shin_r=(0.5, 0, 0), shin_l=(0.5, 0, 0))
    up = pose(spine=(0.2, 0, 0), neck=(-0.2, 0, 0), head=(0.35, 0, 0), arm_r=arm("r", 0.9, -0.2, 0.3), arm_l=arm("l", 0.9, -0.2, 0.3))
    snap = put(copy(up), head=(-0.3, 0.1, 0.25), jaw=(0.2, 0, 0))
    roar = pose(spine=(-0.3, 0, 0), chest=(-0.3, 0, 0), neck=(-0.5, 0, 0), head=(-0.75, 0, 0), jaw=(0.8, 0, 0),
                arm_r=arm("r", 0.25, -0.5, 0.4), arm_l=arm("l", 0.25, -0.5, 0.4), claws_r=(0, 0, 0.8), claws_l=(0, 0, -0.8),
                tail1=(-0.3, 0, 0), tail2=(-0.2, 0, 0), loc=(0, -1.5, 0))
    keys = [(0, coiled), (24, jitter(coiled, r, 0.04, UPPER)), (40, coiled), (75, unfold), (105, up), (114, snap), (126, snap),
            (134, roar)]
    for f in range(137, 168, 3):
        keys.append((f, jitter(roar, r, 0.05, UPPER)))
    keys += [(170, roar), (180, pose())]
    return keys


def death():
    r = A.rng(23)
    stagger = pose(spine=(-0.3, 0.2, 0.1), chest=(-0.2, 0, 0), head=(-0.5, 0.2, 0.3), jaw=(0.6, 0, 0),
                   arm_r=arm("r", 0.6, 0.3, 0.3), arm_l=arm("l", 0.9, -0.6), leg_r=(0.3, 0, 0.05), loc=(0, 0, 2.0))
    knees = pose(spine=(0.4, 0, 0), chest=(0.2, 0, 0), head=(0.3, 0, 0.2), jaw=(0.4, 0, 0),
                 arm_r=arm("r", 1.4, -0.4), arm_l=arm("l", 1.4, -0.4), claws_r=(0, 0, -0.9), claws_l=(0, 0, 0.9),
                 leg_r=(-1.2, 0, 0.15), leg_l=(-1.2, 0, -0.15), shin_r=(1.4, 0, 0), shin_l=(1.4, 0, 0),
                 foot_r=(-0.4, 0, 0), foot_l=(-0.4, 0, 0))
    slump = put(copy(knees), spine=(1.0, 0, 0), chest=(0.45, 0, 0), head=(0.6, 0, 0.3), neck=(0.2, 0, 0),
                arm_r=arm("r", 1.5, -1.0), arm_l=arm("l", 1.5, -1.0))
    down = put(copy(slump), spine=(1.45, 0, 0.1), chest=(0.4, 0, 0), head=(0.2, 0.3, 0.4), jaw=(0.5, 0, 0),
               arm_r=arm("r", 0.7, -1.6, 0.4), arm_l=arm("l", 0.7, -1.6, 0.4), tail1=(-0.35, 0.4, 0), tail2=(-0.05, 0.5, 0))  # limp on the ground
    keys = [(0, pose()), (15, stagger), (40, knees), (70, slump)]
    for f in range(90, 130, 3):
        keys.append((f, jitter(slump, r, 0.12 * (1.0 - (f - 90) / 50.0), UPPER)))
    keys += [(140, down), (150, down)]
    return keys


CLIPS = [
    A.Clip("idle", 180, gen=idle, step=3, loop=True, ground="contact"),
    A.Clip("walk", 120, gen=walk, step=3, loop=True, ground="contact"),
    A.Clip("chase", 66, gen=chase, step=3, loop=True, ground="contact"),
    A.Clip("slash", 54, keys=slash(), full=False, ground="contact"),
    A.Clip("entangle", 45, keys=entangle(), full=False, ground="contact"),
    A.Clip("mass_infection", 60, keys=mass_infection(), ground="contact"),
    A.Clip("summon", 75, keys=summon(), ground="contact"),
    A.Clip("rejuvenate", 60, gen=rejuvenate, step=3, loop=True, ground="contact"),
    A.Clip("blink", 30, keys=blink(), stepped=True, ground="contact"),
    A.Clip("phase", 60, keys=phase(), ground="contact"),
    A.Clip("entrance", 180, keys=entrance(), ground="contact"),
    A.Clip("death", 150, keys=death(), ground="contact"),
]


def build():
    return A.build("Mythos", CLIPS, "pelvis", {"foot_r", "foot_l", "shin_r", "shin_l"}, 0.35)
