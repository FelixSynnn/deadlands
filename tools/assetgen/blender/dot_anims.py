"""D.O.T.'s animations, keyed in Blender on the rig dot_rig.py builds (run that first).

Each clip becomes an Action called DOT_<clip> (kept with a fake user), one frame per game tick (20 fps). Angles are the
game's own numbers: on this rig a pose bone's XYZ Euler is the ModelPart's xRot/yRot/zRot (see dot_rig.py). In the
model's axes: +x on a limb swings its bottom backward (-x raises it forward); +x on the spine leans forward; +x on the
head looks down; the right arm (at -X) comes in with -z, the left with +z; the pelvis "loc" is in pixels, y down.

Loops (idle, prowl, sprint, climb) carry a Cycles modifier so their curves wrap smoothly. prowl and sprint share one
walk phase in the game (0 = right foot down, 0.5 = left), so the two blend without the feet slipping.

After keying, every grounded clip gets its pelvis height baked frame by frame so the lowest point of his boots sits
exactly on the floor ("contact") or never sinks through it ("floor"): the legs swing from the knee, and that is what
keeps the boots planted. Edit the keys freely in Blender, then run dot_export.py to write the game's file.
"""
import math
import random

import bpy

RIG = bpy.data.objects["DOT_Rig"]
BODY = bpy.data.objects["DOT_Body"]
SCENE = bpy.context.scene
BONES = [b.name for b in RIG.data.bones]
TAU = math.tau

BASE = {
    "pelvis": (0, 0, 0), "skirt": (0.06, 0, 0), "spine": (0.22, 0, 0), "chest": (0.12, 0, 0),
    "neck": (-0.18, 0, 0), "head": (-0.12, 0, 0), "hat": (0, 0, 0),
    "arm_r": (-0.25, 0.05, -0.36), "forearm_r": (-0.55, 0, 0), "hand_r": (0.15, 0, 0),
    "arm_l": (-0.2, -0.05, 0.36), "forearm_l": (-0.5, 0, 0), "hand_l": (0.2, 0, 0),
    "leg_r": (-0.08, 0, 0.02), "foot_r": (0.06, 0, 0), "leg_l": (-0.04, 0, -0.02), "foot_l": (0.04, 0, 0),
    "cloth": (0.08, 0, -0.04),
}
UPPER = ["spine", "chest", "neck", "head", "hat", "arm_r", "forearm_r", "hand_r", "arm_l", "forearm_l", "hand_l"]


# ------------------------------------------------------------------------------------------------ pose helpers
def pose(**over):
    """BASE with some bones replaced: pose(arm_r=(x, y, z), loc=(x, y, z))."""
    p = {b: list(v) for b, v in BASE.items()}
    p["loc"] = [0.0, 0.0, 0.0]
    for k, v in over.items():
        p[k] = list(v)
    return p


def add(p, **delta):
    for k, v in delta.items():
        p[k] = [a + b for a, b in zip(p[k], v)]
    return p


def put(p, **over):
    for k, v in over.items():
        p[k] = list(v)
    return p


def copy(p):
    return {k: list(v) for k, v in p.items()}


def jitter(p, rng, amp, bones=UPPER, head_extra=1.6):
    q = copy(p)
    for b in bones:
        a = amp * (head_extra if b in ("head", "neck") else 1.0)
        q[b] = [v + rng.uniform(-a, a) for v in q[b]]
    return q


def smooth(k):
    k = min(1.0, max(0.0, k))
    return k * k * (3 - 2 * k)


def lerp(a, b, k):
    return a + (b - a) * k


def pulse(t, a, b, c, d):
    """0 before a, up to 1 by b, held to c, back to 0 by d."""
    if t <= a or t >= d:
        return 0.0
    if t < b:
        return smooth((t - a) / (b - a))
    if t <= c:
        return 1.0
    return 1.0 - smooth((t - c) / (d - c))


def snaps(t, targets):
    """A bird's head: holds, then snaps to the next target in about two frames, overshooting a little."""
    prev = targets[-1][1]
    cur = targets[0][1]
    start = targets[0][0]
    for f, v in targets:
        if t >= f:
            prev, cur, start = cur, v, f
    u = (t - start) / 2.5
    if u >= 1.0:
        return cur
    e = 1 - (1 - u) ** 3 + 0.18 * math.sin(math.pi * u)
    return tuple(lerp(a, b, e) for a, b in zip(prev, cur))


def leg(u, amp, kick, duty, push, heel):
    """One leg's shin swing and foot angle over a stride: planted from forward (u=0) to back, then kicked up and back,
    and swung forward to plant again. Returns (shin xRot, foot xRot) offsets."""
    u %= 1.0
    if u < duty:
        k = u / duty
        return lerp(-amp, amp, k), lerp(-heel, push, smooth(k))
    k = (u - duty) / (1 - duty)
    if k < 0.3:
        q = smooth(k / 0.3)
        return lerp(amp, amp + kick, q), lerp(push, push + 0.35, q)
    q = smooth((k - 0.3) / 0.7)
    return lerp(amp + kick, -amp, q), lerp(push + 0.35, -heel, q)


def steady_head(p, keep=1.0):
    """Eyes on the prey: the head undoes the body's yaw and roll."""
    yaw = p["pelvis"][1] + p["spine"][1] + p["chest"][1] + p["neck"][1] - BASE["neck"][1]
    roll = p["pelvis"][2] + p["spine"][2] + p["chest"][2]
    p["head"][1] -= yaw * keep
    p["head"][2] -= roll * keep


# ------------------------------------------------------------------------------------------------ the clips
def idle(t):
    p = pose()
    b = math.sin(TAU * t / 32)           # three breaths a loop, heavy, through the mask
    sway = math.sin(TAU * t / 96)
    add(p, chest=(0.045 * b, 0, 0), spine=(0.012 * b, 0, 0), neck=(-0.03 * b, 0, 0),
        arm_r=(0.03 * b, 0, -0.03 * b), arm_l=(0.03 * b, 0, 0.03 * b), hat=(0.01 * b, 0, 0))
    p["loc"] = [0.35 * sway, -0.1 * b, 0]
    add(p, pelvis=(0, 0, 0.025 * sway), spine=(0, 0, -0.03 * sway), skirt=(0.01 * b, 0, -0.02 * sway),
        cloth=(0.02 * math.sin(TAU * t / 48), 0, 0))
    pitch, yaw, roll = snaps(t, [(0, (0, 0, 0)), (12, (0.05, 0.35, 0.1)), (24, (0.05, 0.35, 0.45)),
                                 (38, (-0.05, -0.28, 0.0)), (50, (0.12, -0.28, -0.32)), (62, (-0.08, 0.1, 0.0)),
                                 (74, (0.0, 0.12, 0.2)), (86, (0, 0, 0))])
    add(p, head=(pitch, yaw, roll), neck=(0, yaw * 0.25, 0))
    # The right claw flexes (open wide, close), the left fingers tremble now and then.
    flex = pulse(t, 60, 66, 70, 80)
    add(p, hand_r=(-0.5 * flex + 0.05 * math.sin(t * 2.3) * flex, 0, 0), forearm_r=(-0.18 * flex, 0, 0))
    add(p, hand_l=(0.08 * math.sin(t * 3.1) * pulse(t, 18, 20, 26, 30), 0, 0))
    return p


PROWL_N = 32
SPRINT_N = 20


def prowl(t):
    u = t / PROWL_N
    ph = TAU * u
    c, s = math.cos(ph), math.sin(ph)
    p = pose(spine=(0.4, 0, 0), chest=(0.14, 0, 0), neck=(-0.32, 0, 0), head=(-0.2, 0, 0),
             arm_r=(-0.55, 0.1, -0.3), forearm_r=(-0.65, 0, 0), hand_r=(-0.1, 0, 0),
             arm_l=(-0.5, -0.1, 0.3), forearm_l=(-0.6, 0, 0), hand_l=(-0.1, 0, 0))
    lr, fr = leg(u, 0.38, 0.35, 0.62, 0.4, 0.15)
    ll, fl = leg(u + 0.5, 0.38, 0.35, 0.62, 0.4, 0.15)
    add(p, leg_r=(lr, 0, 0), foot_r=(fr, 0, 0), leg_l=(ll, 0, 0), foot_l=(fl, 0, 0))
    # Hips turn with the leading leg, the chest turns against them, the weight rolls over the planted foot.
    add(p, pelvis=(0, -0.08 * c, 0.035 * s), spine=(0, 0.04 * c, 0), chest=(0, 0.06 * c, -0.03 * s))
    # Claws held out ahead, swaying against the legs, the forearms and hands a beat behind.
    add(p, arm_r=(0.14 * c, 0, 0), arm_l=(-0.14 * c, 0, 0),
        forearm_r=(-0.1 * math.cos(ph - 0.8), 0, 0), forearm_l=(0.1 * math.cos(ph - 0.8), 0, 0),
        hand_r=(0.12 * math.cos(ph - 1.6), 0, 0), hand_l=(-0.12 * math.cos(ph - 1.6), 0, 0))
    p["loc"] = [-0.3 * math.cos(ph - 1.95), 0.0, 0.0]
    add(p, skirt=(0.05 + 0.04 * math.cos(2 * ph - 1.2), 0.06 * math.cos(ph - 0.7), -0.03 * math.sin(ph - 0.7)),
        cloth=(0.12 + 0.08 * math.cos(2 * ph - 1.5), 0, 0.03 * math.sin(ph - 1.0)),
        hat=(0.02 * math.cos(2 * ph - 1.0), 0, 0), head=(0.03 * math.cos(2 * ph), 0, 0))
    steady_head(p)
    return p


def sprint(t):
    u = t / SPRINT_N
    ph = TAU * u
    c, s = math.cos(ph), math.sin(ph)
    p = pose(spine=(0.62, 0, 0), chest=(0.3, 0, 0), neck=(-0.58, 0, 0), head=(-0.3, 0, 0))
    lr, fr = leg(u, 0.8, 0.75, 0.42, 0.75, 0.25)
    ll, fl = leg(u + 0.5, 0.8, 0.75, 0.42, 0.75, 0.25)
    add(p, leg_r=(lr, 0, 0), foot_r=(fr, 0, 0), leg_l=(ll, 0, 0), foot_l=(fl, 0, 0))
    # The arms pump hard, claws first: forward and up when the other leg reaches, the elbow folding as it comes.
    put(p, arm_r=(-0.45 + 0.85 * c, 0.1, -0.22), arm_l=(-0.45 - 0.85 * c, -0.1, 0.22),
        forearm_r=(-0.8 - 0.45 * max(0.0, -c), 0, 0), forearm_l=(-0.8 - 0.45 * max(0.0, c), 0, 0),
        hand_r=(-0.35 + 0.25 * math.cos(ph - 1.0), 0, 0), hand_l=(-0.35 - 0.25 * math.cos(ph - 1.0), 0, 0))
    add(p, pelvis=(0, -0.14 * c, 0.06 * s), spine=(0, 0.08 * c, 0), chest=(0, 0.12 * c, -0.05 * s),
        head=(0.06 * math.cos(2 * ph), 0, 0))
    # Two flights a stride, just after each push-off.
    flight = max(0.0, math.cos(TAU * (2 * u - 0.92))) ** 3
    p["loc"] = [-0.25 * math.cos(ph - 1.3), -1.0 * flight, 0.0]
    add(p, skirt=(0.5 + 0.12 * math.cos(2 * ph - 1.3), -0.1 * math.cos(ph - 0.8), 0),
        cloth=(0.85 + 0.15 * math.cos(2 * ph - 1.6), 0, -0.08), hat=(-0.08 + 0.03 * math.cos(2 * ph - 1.2), 0, 0))
    steady_head(p)
    return p


def climb(t):
    ph = TAU * t / 24
    c, s = math.cos(ph), math.sin(ph)
    p = pose(spine=(-0.08, 0, 0), chest=(0.0, 0, 0), neck=(-0.25, 0, 0), head=(-0.45, 0, 0),
             arm_r=(-2.75 + 0.4 * s, 0, -0.1), arm_l=(-2.75 - 0.4 * s, 0, 0.1),
             forearm_r=(-0.35 - 0.45 * max(0.0, c), 0, 0), forearm_l=(-0.35 - 0.45 * max(0.0, -c), 0, 0),
             hand_r=(-0.4, 0, 0), hand_l=(-0.4, 0, 0),
             leg_r=(-0.55 - 0.45 * s, 0, 0.05), leg_l=(-0.55 + 0.45 * s, 0, -0.05),
             foot_r=(-0.3, 0, 0), foot_l=(-0.3, 0, 0), skirt=(0.15, 0, 0), cloth=(0.2, 0, 0))
    add(p, pelvis=(0, 0, 0.05 * s), spine=(0, 0, -0.05 * s), head=(0, 0.12 * c, 0))
    p["loc"] = [0.4 * s, 0.25 * abs(c), 0]
    return p


def scream():
    rng = random.Random(4)
    gather = pose(spine=(0.55, 0, 0), chest=(0.25, 0, 0), neck=(-0.2, 0, 0), head=(0.25, 0, 0),
                  arm_r=(-0.4, 0, -0.55), arm_l=(-0.4, 0, 0.55), forearm_r=(-1.4, 0, 0), forearm_l=(-1.4, 0, 0),
                  hand_r=(0.5, 0, 0), hand_l=(0.5, 0, 0), skirt=(0.12, 0, 0))
    up = pose(spine=(-0.28, 0, 0), chest=(-0.3, 0, 0), neck=(-0.45, 0, 0), head=(-0.85, 0, 0), hat=(-0.15, 0, 0),
              arm_r=(-0.55, 0, 1.25), arm_l=(-0.55, 0, -1.25), forearm_r=(-0.6, 0, 0), forearm_l=(-0.6, 0, 0),
              hand_r=(-0.7, 0, 0), hand_l=(-0.7, 0, 0), leg_r=(0, 0, 0.12), leg_l=(0, 0, -0.12),
              skirt=(-0.05, 0, 0), cloth=(-0.05, 0, 0), loc=(0, -0.5, 0))
    keys = [(0, pose()), (5, gather), (9, up)]
    for f in range(11, 27, 2):
        keys.append((f, jitter(up, rng, 0.06)))
    keys += [(28, add(copy(up), head=(0.1, 0, 0), arm_r=(0.1, 0, -0.1), arm_l=(0.1, 0, 0.1))),
             (31, pose(spine=(0.7, 0, 0), chest=(0.3, 0, 0), neck=(-0.6, 0, 0), head=(-0.2, 0, 0),
                       arm_r=(-0.6, 0, -0.2), arm_l=(-0.6, 0, 0.2), forearm_r=(-0.5, 0, 0), forearm_l=(-0.5, 0, 0),
                       hand_r=(-0.2, 0, 0), hand_l=(-0.2, 0, 0))),
             (34, pose())]
    return keys


def yell():
    rng = random.Random(7)
    thrust = add(pose(), neck=(0.43, 0, 0), head=(-0.43, 0, 0), chest=(0.13, 0, 0),
                 arm_r=(0, 0, 0.3), arm_l=(0, 0, -0.3), forearm_r=(-0.25, 0, 0), forearm_l=(-0.25, 0, 0),
                 hand_r=(-0.65, 0, 0), hand_l=(-0.65, 0, 0))
    return [(0, pose()), (2, add(pose(), neck=(-0.07, 0, 0), head=(-0.15, 0, 0), chest=(-0.05, 0, 0))),
            (4, thrust), (6, jitter(thrust, rng, 0.05)), (8, jitter(thrust, rng, 0.05)),
            (10, jitter(add(copy(thrust), neck=(-0.15, 0, 0)), rng, 0.04)), (14, pose())]


def swing():
    rng = random.Random(11)
    wind = pose(arm_r=(-2.55, 0.2, 0.55), forearm_r=(-1.25, 0, 0), hand_r=(-0.7, 0, 0),
                chest=(0.12, 0.45, 0), spine=(0.05, 0.12, 0), neck=(-0.05, -0.25, 0), head=(-0.15, -0.2, 0),
                arm_l=(0.15, 0, 0.5), forearm_l=(-0.4, 0, 0), leg_r=(0.15, 0, 0.02), leg_l=(-0.2, 0, -0.02))
    hit = pose(arm_r=(-0.25, -0.1, -0.75), forearm_r=(-0.15, 0, 0), hand_r=(0.65, 0, 0),
               chest=(0.15, -0.6, 0), spine=(0.55, -0.12, 0), neck=(-0.4, 0.25, 0), head=(-0.1, 0.25, 0),
               arm_l=(-0.9, 0, 0.3), leg_r=(-0.55, 0, 0.02), leg_l=(0.35, 0, -0.02), foot_l=(0.35, 0, 0),
               skirt=(0.25, 0, 0), cloth=(0.3, 0, 0))
    follow = put(copy(hit), arm_r=(0.45, -0.1, -0.55), forearm_r=(-0.35, 0, 0), hand_r=(0.4, 0, 0),
                 chest=(0.15, -0.7, 0), spine=(0.6, -0.12, 0))
    return [(0, pose()), (4, wind), (6, jitter(put(copy(wind), arm_r=(-2.7, 0.2, 0.6)), rng, 0.025)),
            (8, hit), (11, follow), (18, pose())]


def swing2():
    rng = random.Random(12)
    coil = pose(arm_l=(-1.35, 0.35, 1.25), forearm_l=(-1.45, 0, 0), hand_l=(-0.4, 0, 0),
                chest=(0.12, -0.5, 0), spine=(0.1, -0.1, 0), neck=(-0.18, 0.25, 0), head=(-0.12, 0.2, 0),
                arm_r=(0.2, 0, -0.5), leg_l=(0.15, 0, -0.02), leg_r=(-0.2, 0, 0.02))
    hit = pose(arm_l=(-1.25, -0.4, -0.7), forearm_l=(-0.1, 0, 0), hand_l=(0.55, 0, 0),
               chest=(0.15, 0.6, 0), spine=(0.45, 0.12, 0), neck=(-0.35, -0.25, 0), head=(-0.1, -0.25, 0),
               arm_r=(-0.6, 0, -0.2), leg_l=(-0.55, 0, -0.02), leg_r=(0.35, 0, 0.02), foot_r=(0.35, 0, 0),
               skirt=(0.22, 0, 0), cloth=(0.3, 0, 0))
    follow = put(copy(hit), arm_l=(-0.7, -0.4, -1.0), forearm_l=(-0.3, 0, 0), chest=(0.15, 0.7, 0))
    return [(0, pose()), (4, coil), (6, jitter(coil, rng, 0.025)), (8, hit), (11, follow), (18, pose())]


def thrust():
    rng = random.Random(13)
    coil = pose(arm_r=(0.65, 0, -0.5), arm_l=(0.65, 0, 0.5), forearm_r=(-1.3, 0, 0), forearm_l=(-1.3, 0, 0),
                hand_r=(-0.2, 0, 0), hand_l=(-0.2, 0, 0), spine=(0.0, 0, 0), chest=(-0.1, 0, 0),
                neck=(-0.1, 0, 0), head=(0.0, 0, 0), leg_r=(0.25, 0, 0.02), leg_l=(-0.15, 0, -0.02),
                loc=(0, 0, 0.8))
    lunge = pose(arm_r=(-1.6, 0, -0.05), arm_l=(-1.6, 0, 0.05), forearm_r=(-0.05, 0, 0), forearm_l=(-0.05, 0, 0),
                 hand_r=(-0.45, 0, 0), hand_l=(-0.45, 0, 0), spine=(0.6, 0, 0), chest=(0.15, 0, 0),
                 neck=(-0.35, 0, 0), head=(-0.35, 0, 0), leg_r=(-0.7, 0, 0.02), leg_l=(0.55, 0, -0.02),
                 foot_l=(0.4, 0, 0), skirt=(0.3, 0, 0), cloth=(0.35, 0, 0), loc=(0, 0, -1.2))
    clamp = put(copy(lunge), hand_r=(0.5, 0, 0), hand_l=(0.5, 0, 0), arm_r=(-1.45, 0, -0.25), arm_l=(-1.45, 0, 0.25))
    return [(0, pose()), (4, coil), (6, jitter(coil, rng, 0.02)), (8, lunge), (11, clamp),
            (13, jitter(clamp, rng, 0.03)), (18, pose())]


def pounce_crouch():
    rng = random.Random(21)
    sink = pose(spine=(0.85, 0, 0), chest=(0.2, 0, 0), neck=(-0.8, 0, 0), head=(-0.3, 0, 0),
                arm_r=(-0.75, 0, 0.55), arm_l=(-0.75, 0, -0.55), forearm_r=(-0.7, 0, 0), forearm_l=(-0.7, 0, 0),
                hand_r=(-0.5, 0, 0), hand_l=(-0.5, 0, 0), leg_r=(-0.65, 0, 0.05), leg_l=(-0.35, 0, -0.05),
                foot_r=(-0.2, 0, 0), foot_l=(-0.2, 0, 0), skirt=(0.25, 0, 0), cloth=(0.2, 0, 0), loc=(0, 0, 0.6))
    return [(0, pose()), (4, sink), (6, jitter(sink, rng, 0.03)), (7, jitter(sink, rng, 0.03)),
            (8, jitter(sink, rng, 0.035)), (9, jitter(sink, rng, 0.035)), (10, sink)]


def pounce():
    launch = pose(spine=(0.35, 0, 0), chest=(0.1, 0, 0), neck=(-0.45, 0, 0), head=(-0.25, 0, 0), hat=(-0.2, 0, 0),
                  arm_r=(-2.5, 0, -0.25), arm_l=(-2.5, 0, 0.25), forearm_r=(-0.15, 0, 0), forearm_l=(-0.15, 0, 0),
                  hand_r=(-0.6, 0, 0), hand_l=(-0.6, 0, 0), leg_r=(0.95, 0, 0), foot_r=(0.8, 0, 0),
                  leg_l=(0.6, 0, 0), foot_l=(0.6, 0, 0), skirt=(0.85, 0, 0), cloth=(0.9, 0, 0))
    air = put(copy(launch), arm_r=(-2.2, 0, -0.3), arm_l=(-2.2, 0, 0.3), spine=(0.45, 0, 0),
              leg_r=(0.4, 0, 0), leg_l=(0.2, 0, 0), skirt=(0.6, 0, 0))
    land = pose(arm_r=(-0.9, 0, 0.35), arm_l=(-0.9, 0, -0.35), forearm_r=(-0.2, 0, 0), forearm_l=(-0.2, 0, 0),
                hand_r=(0.5, 0, 0), hand_l=(0.5, 0, 0), spine=(0.75, 0, 0), chest=(0.2, 0, 0),
                neck=(-0.7, 0, 0), head=(-0.2, 0, 0), leg_r=(-0.6, 0, 0.02), leg_l=(0.4, 0, -0.02),
                skirt=(0.3, 0, 0), cloth=(0.4, 0, 0))
    return [(0, launch), (6, air), (10, land), (14, pose(spine=(0.5, 0, 0), neck=(-0.4, 0, 0)))]


def grasp_windup():
    rng = random.Random(31)
    tilt = pose(head=(-0.05, 0, 0.45), neck=(-0.18, 0, 0.1))
    mid = put(copy(tilt), arm_r=(-1.2, 0.1, -0.2), forearm_r=(-0.5, 0, 0), hand_r=(-0.6, 0, 0))
    full = put(copy(tilt), arm_r=(-1.6, 0.05, -0.15), forearm_r=(-0.15, 0, 0), hand_r=(-0.9, 0, 0),
               spine=(0.05, 0, 0), chest=(-0.05, 0, 0), head=(-0.1, 0, 0.5))
    keys = [(0, pose()), (6, tilt), (14, mid)]
    for f in range(16, 20, 2):
        keys.append((f, jitter(full, rng, 0.03)))
    keys.append((20, full))
    return keys


def grasp():
    rng = random.Random(32)
    start = grasp_windup()[-1][1]
    hold = pose(arm_r=(-1.75, 0, -0.1), forearm_r=(0.0, 0, 0), hand_r=(0.6, 0, 0), spine=(0.5, 0, 0),
                neck=(-0.4, 0, 0), head=(0.1, 0, 0.3), leg_r=(-0.5, 0, 0.02), leg_l=(0.3, 0, -0.02),
                arm_l=(-0.5, 0, 0.6), forearm_l=(-0.9, 0, 0), hand_l=(-0.4, 0, 0), skirt=(0.2, 0, 0))
    keys = [(0, start), (3, hold)]
    for f in range(5, 27, 2):
        q = jitter(hold, rng, 0.04)
        q["hand_r"][0] += 0.25 * math.sin(f * 1.3)  # squeezing
        keys.append((f, q))
    keys.append((30, pose()))
    return keys


def static_step():
    a = pose(spine=(0.3, 0.7, 0.2), head=(0.2, 0.5, -0.7), arm_r=(-0.4, 0, 0.6), arm_l=(-1.0, 0, 0.2),
             leg_r=(-0.4, 0, 0))
    b = pose(spine=(0.1, -0.4, -0.15), head=(-0.4, -0.6, 0.6), arm_r=(-1.6, 0, -0.2), arm_l=(0.3, 0, 1.0))
    c = pose(head=(0.0, 0.0, 0.9), chest=(0.12, 0.3, 0))
    return [(0, a), (1, b), (2, c), (3, add(copy(a), spine=(-0.15, -0.35, -0.1))), (4, add(pose(), head=(0, 0, 0.3))),
            (6, add(pose(), head=(0, 0, 0.12))), (10, pose())]


def restore():
    rng = random.Random(41)
    out = pose(arm_r=(-0.3, 0, 1.35), arm_l=(-0.3, 0, -1.35), forearm_r=(-0.3, 0, 0), forearm_l=(-0.3, 0, 0),
               hand_r=(-0.6, 0, 0), hand_l=(-0.6, 0, 0), head=(-0.7, 0, 0), neck=(-0.35, 0, 0),
               spine=(-0.2, 0, 0), chest=(-0.15, 0, 0), hat=(-0.1, 0, 0))
    return [(0, pose()), (3, out), (6, jitter(out, rng, 0.04)), (8, jitter(out, rng, 0.03)),
            (11, add(copy(out), arm_r=(0, 0, -0.6), arm_l=(0, 0, 0.6), head=(0.4, 0, 0))), (16, pose())]


def field():
    rng = random.Random(51)
    raised = pose(arm_r=(-0.6, 0, 1.35), arm_l=(-0.6, 0, -1.35), forearm_r=(-0.85, 0, 0), forearm_l=(-0.85, 0, 0),
                  hand_r=(-0.5, 0, 0), hand_l=(-0.5, 0, 0), spine=(-0.12, 0, 0), chest=(-0.15, 0, 0),
                  neck=(-0.3, 0, 0), head=(-0.55, 0, 0), skirt=(0.02, 0, 0), cloth=(0.0, 0, 0))
    part = {k: [lerp(BASE.get(k, (0, 0, 0))[i] if k != "loc" else 0, v[i], 0.4) for i in range(3)] for k, v in raised.items()}
    return [(0, pose()), (10, raised), (14, jitter(raised, rng, 0.02)), (18, raised), (24, part)]


def twitch():
    return [(0, add(pose(), head=(0.15, 0.35, 0.6))), (1, add(pose(), head=(-0.1, -0.2, -0.3))),
            (2, add(pose(), head=(0.2, 0.4, 0.7), hand_r=(0.6, 0, 0))), (4, add(pose(), head=(0, 0, 0.3))),
            (5, add(pose(), hand_r=(-0.5, 0, 0))), (7, add(pose(), hand_r=(0.4, 0, 0))), (8, pose()), (12, pose())]


def loom():
    lean = pose(spine=(0.5, 0, 0), chest=(0.2, 0, 0), neck=(0.0, 0, 0), head=(0.15, 0, 0.35),
                arm_r=(-1.0, 0.2, -0.2), forearm_r=(-0.6, 0, 0), hand_r=(-0.5, 0, 0))
    reach = put(copy(lean), head=(0.15, 0, 0.65), arm_r=(-1.4, 0.25, -0.1), hand_r=(-0.8, 0, 0), forearm_r=(-0.3, 0, 0))
    return [(0, pose()), (8, lean), (20, reach), (26, put(copy(reach), hand_r=(0.2, 0, 0))),
            (30, reach), (34, put(copy(reach), head=(0.15, 0, 0.55))), (40, pose())]


# name: (keys or (generator, length, step), loop, full body, stepped, ground)
CLIPS = {
    "idle": ((idle, 96, 2), True, True, False, "contact"),
    "prowl": ((prowl, PROWL_N, 2), True, True, False, "contact"),
    "sprint": ((sprint, SPRINT_N, 1), True, True, False, "contact"),
    "climb": ((climb, 24, 2), True, True, False, None),
    "scream": (scream, False, True, False, "contact"),
    "yell": (yell, False, False, False, "contact"),
    "swing": (swing, False, False, False, "contact"),
    "swing2": (swing2, False, False, False, "contact"),
    "thrust": (thrust, False, False, False, "contact"),
    "pounce_crouch": (pounce_crouch, False, True, False, "contact"),
    "pounce": (pounce, False, True, False, "floor"),
    "grasp_windup": (grasp_windup, False, False, False, "contact"),
    "grasp": (grasp, False, True, False, "contact"),
    "static_step": (static_step, False, True, True, "contact"),
    "restore": (restore, False, True, False, "contact"),
    "field": (field, False, True, False, "contact"),
    "twitch": (twitch, False, False, True, "contact"),
    "loom": (loom, False, True, False, "contact"),
}


# ------------------------------------------------------------------------------------------------ keying
def fcurves_of(action):
    try:
        return list(action.fcurves)
    except AttributeError:
        from bpy_extras import anim_utils
        bag = anim_utils.action_get_channelbag_for_slot(action, RIG.animation_data.action_slot)
        return list(bag.fcurves) if bag else []


def key_pose(p, frame):
    for b in BONES:
        pb = RIG.pose.bones[b]
        pb.rotation_euler = p[b]
        pb.keyframe_insert("rotation_euler", frame=frame, group=b)
    pel = RIG.pose.bones["pelvis"]
    pel.location = [v / 16.0 for v in p["loc"]]
    pel.keyframe_insert("location", frame=frame, group="pelvis")


def sole_points():
    """Boot vertices near the ground at rest, as (bone, rest position): the points that must not sink."""
    me = BODY.data
    names = {g.index: g.name for g in BODY.vertex_groups}
    pts = []
    for v in me.vertices:
        if v.co.z < 0.25 and v.groups:
            bone = names[v.groups[0].group]
            if bone.startswith(("leg_", "foot_")):
                pts.append((bone, v.co.copy()))
    return pts[::3]


def lowest(points):
    z = 1e9
    for bone, co in points:
        pb = RIG.pose.bones[bone]
        d = pb.matrix @ pb.bone.matrix_local.inverted()
        z = min(z, (d @ co).z)
    return z


def ground(action, length, mode, points, target):
    """Bakes the pelvis height frame by frame: contact keeps the boots on the floor (the authored height becomes a lift
    above it), floor only stops them sinking through it."""
    pel = RIG.pose.bones["pelvis"]
    baked = []
    for f in range(length + 1):
        SCENE.frame_set(f)
        loc = pel.location.copy()
        low = lowest(points)
        if mode == "contact":
            # Blender z is -model y; the authored height (loc.y) moved the boots by -loc.y already.
            shift = target - (low + loc.y) - loc.y
        else:
            shift = -loc.y + max(0.0, target - low)
        baked.append((f, (loc.x, -shift, loc.z)))
    for fc in fcurves_of(action):
        if fc.data_path == 'pose.bones["pelvis"].location':
            fc.keyframe_points.clear()
    for f, loc in baked:
        pel.location = loc
        pel.keyframe_insert("location", frame=f, group="pelvis")


def build():
    if RIG.animation_data is None:
        RIG.animation_data_create()
    points = sole_points()
    for pb in RIG.pose.bones:
        pb.rotation_euler = (0, 0, 0)
        pb.location = (0, 0, 0)
    RIG.animation_data.action = None
    bpy.context.view_layer.update()
    target = lowest(points)
    made = {}
    for name, (spec, loop, full, stepped, grounding) in CLIPS.items():
        old = bpy.data.actions.get("DOT_" + name)
        if old is not None:
            bpy.data.actions.remove(old)
        action = bpy.data.actions.new("DOT_" + name)
        action.use_fake_user = True
        RIG.animation_data.action = action
        if isinstance(spec, tuple):
            gen, length, step = spec
            frames = list(range(0, length + 1, step))
            if frames[-1] != length:
                frames.append(length)
            keys = [(f, gen(f % length if loop else f)) for f in frames]
        else:
            keys = spec()
            length = keys[-1][0]
        for f, p in keys:
            key_pose(p, f)
        for fc in fcurves_of(action):
            for kp in fc.keyframe_points:
                kp.interpolation = "CONSTANT" if stepped else "BEZIER"
                kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"
            if loop:
                fc.modifiers.new("CYCLES")
            fc.update()
        if grounding:
            ground(action, length, grounding, points, target)
            for fc in fcurves_of(action):
                if fc.data_path == 'pose.bones["pelvis"].location':
                    for kp in fc.keyframe_points:
                        kp.interpolation = "CONSTANT" if stepped else "LINEAR"
                    if loop and not any(m.type == "CYCLES" for m in fc.modifiers):
                        fc.modifiers.new("CYCLES")
                    fc.update()
        action["dl_loop"] = int(loop)
        action["dl_fullbody"] = int(full)
        action["dl_stepped"] = int(stepped)
        action["dl_length"] = length
        action.frame_range = (0, length)
        made[name] = length
    idle_action = bpy.data.actions["DOT_idle"]
    RIG.animation_data.action = idle_action
    SCENE.frame_start, SCENE.frame_end = 0, 95
    SCENE.frame_set(0)
    return made


result = build()
