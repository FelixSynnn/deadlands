"""The Aberrant's full move set, keyed at 60 fps in Blender (art/blender/aberrant.blend, the rig dl_rig.build("Aberrant",
...) makes). aberrant_anims.py keys his dig; this keys everything else, so AsesinoModel plays Blender clips for all of it.

Loops:
  idle 240        roaming stance: heavy breaths that lift the shoulders, the machete turning in his hand, weight rolling
                  from hip to hip, a slow sideways tilt of the head, the coat settling
  walk 72         a slow, heavy walk: knees folding on the back swing, the hips dipping and turning, the torso
                  countering, the blade arm swinging late and heavy, a nod at each footfall, the coat trailing
  stalk 84        hunched and low, knees bent, the blade held up, deliberate steps with a pause at each one, head level
  stalk_idle 180  hunched and waiting: shallow breaths, the blade lifting a little, the head turning slowly
  run 36          the chase: a deep lean, big pumping arms, the blade cocked back, a moment of flight, coat streaming
  search 120      sweeping the head and shoulders side to side, a pause at each end, a shift of the feet
  lurk 180        crouched in hiding, the blade held close, listening, the head turning slowly
  climb 48        hand over hand up a wall, legs pushing, coat hanging, head back to look up

Actions (AsesinoEntity's act() ticks; a chain's next clip starts where the last one ends):
  windup 10 -> swing 7, windup2 10 -> swing2 7 (the backhand), crouch 12 -> leap 16, scent 30, stare 50
  (dig is aberrant_anims.py's)

Conventions are the game's (see dl_rig.py): -x raises a limb forward, +x leans the torso forward, the shin folds back with
+x; +z moves the right arm out, -z the left. "loc" is the pelvis offset in pixels (z back); grounding keeps the lowest
foot on the floor. One tick is three frames. Run build(), then dl_export.export("Aberrant", "aberrant.anim").
"""
import math

import dl_anim as A
from guest_anims import spline  # Hermite curves through (tick, pose) keys; works on any pose dicts

TAU = math.tau
PI = math.pi
B = {"pelvis": (0, 0, 0), "torso": (0.1, 0, 0), "neck": (0, 0, 0), "head": (0, 0, 0),
     "arm_r": (0, 0, 0.08), "forearm_r": (-0.35, 0, 0), "hand_r": (0, 0, 0),
     "arm_l": (0, 0, -0.08), "forearm_l": (-0.25, 0, 0), "hand_l": (0, 0, 0),
     "coat_front_r": (0, 0, 0), "coat_front_l": (0, 0, 0), "coat_back": (0.03, 0, 0),
     "leg_r": (0, 0, 0.02), "shin_r": (0, 0, 0), "leg_l": (0, 0, -0.02), "shin_l": (0, 0, 0)}
P = A.Poser(B)
sm = A.smooth
lerp = A.lerp


def s(x):
    return math.sin(x)


def c(x):
    return math.cos(x)


def bump(t, a, b, r):
    return sm((t - a) / r) * (1 - sm((t - b) / r))


def hunch(p, k):
    """Low and coiled: knees bent, torso forward, head held level, arms drawn in, the blade raised."""
    return P.add(p, leg_r=(-0.55 * k, 0, 0), leg_l=(-0.55 * k, 0, 0), shin_r=(0.9 * k, 0, 0), shin_l=(0.9 * k, 0, 0),
                 torso=(0.5 * k, 0, 0), neck=(-0.35 * k, 0, 0), head=(-0.3 * k, 0, 0), arm_r=(-0.4 * k, 0, 0),
                 forearm_r=(-0.35 * k, 0, 0), arm_l=(-0.25 * k, 0, 0), coat_front_r=(-0.25 * k, 0, 0),
                 coat_front_l=(-0.25 * k, 0, 0), coat_back=(0.3 * k, 0, 0))


def coat(p, t, period, amp=1.0, stream=0.0):
    """The coat moving on its own a beat behind the body."""
    return P.add(p, coat_back=(stream + amp * 0.04 * s(TAU * t / period - 1.2), amp * 0.03 * s(TAU * t / period * 0.5), 0),
                 coat_front_r=(amp * 0.03 * s(TAU * t / period - 0.6), 0, 0.02 * amp * s(TAU * t / period)),
                 coat_front_l=(amp * 0.03 * s(TAU * t / period - 1.6), 0, -0.02 * amp * s(TAU * t / period + 1)))


def trail(p, legs_k=1.0, speed=0.0):
    """The coat tails following the thighs (front ones when a leg swings forward, the back one when it swings back)."""
    lr, ll = p["leg_r"][0] - B["leg_r"][0], p["leg_l"][0] - B["leg_l"][0]
    return P.add(p, coat_front_r=(min(0.0, lr) * 0.85 * legs_k, 0, 0), coat_front_l=(min(0.0, ll) * 0.85 * legs_k, 0, 0),
                 coat_back=(max(lr, ll, 0.0) * 0.5 * legs_k + speed, 0, 0))


# ---------------------------------------------------------------------------------------------------------------- loops

def idle(f):
    t = f / 3.0  # 80 ticks
    p = P.pose()
    b = s(TAU * t / 26.6667)
    roll = s(TAU * t / 80)
    P.add(p, torso=(0.025 * b, 0.03 * roll, -0.015 * roll), neck=(-0.015 * b, 0, 0), head=(-0.01 * b, 0, 0),
          arm_r=(0.02 * b, 0, 0.03 * b + 0.01), arm_l=(0.02 * b, 0, -0.03 * b), pelvis=(0, -0.03 * roll, 0.03 * roll),
          leg_r=(0, 0, -0.03 * roll), leg_l=(0, 0, -0.03 * roll), shin_r=(0.08 * max(0.0, -roll), 0, 0), shin_l=(0.08 * max(0.0, roll), 0, 0))
    # The machete turning in his hand; the head tipping slowly sideways and back (the old "now and then" tilt).
    P.add(p, hand_r=(0.14 * s(TAU * t / 20), 0.18 * s(TAU * t / 40), 0), forearm_r=(-0.06 * s(TAU * t / 40 + 1), 0, 0))
    tilt = bump(t, 30, 58, 8)
    P.add(p, head=(0.04 * tilt, 0.12 * s(TAU * t / 80 + 2), 0.42 * tilt), neck=(0, 0, 0.08 * tilt))
    p["loc"] = [0.35 * roll, 0, 0]
    return coat(p, t, 20)


def walk_cycle(ph, stride=0.65, swing=0.45, lag=0.3, knee=0.9, heavy=1.0):
    p = P.pose()
    sr, sl = s(ph), s(ph + PI)
    P.add(p, leg_r=(-sr * stride, 0, 0), leg_l=(-sl * stride, 0, 0),
          shin_r=(max(0.0, s(ph - 0.9)) * knee + 0.05, 0, 0), shin_l=(max(0.0, s(ph + PI - 0.9)) * knee + 0.05, 0, 0),
          pelvis=(0, sr * 0.09, c(ph) * 0.045), torso=(0.03 * abs(c(ph)) * heavy, -sr * 0.13, -c(ph) * 0.03),
          arm_r=(s(ph - 0.35) * swing * 0.8, 0, 0.02 * abs(sr)), arm_l=(s(ph + PI - 0.25) * swing, 0, -0.02 * abs(sr)),
          forearm_r=(-max(0.0, s(ph - 0.8)) * lag, 0, 0), forearm_l=(-max(0.0, s(ph + PI - 0.7)) * lag, 0, 0),
          head=(0.05 * abs(c(ph + 0.4)) * heavy, sr * 0.08, c(ph) * 0.02), neck=(0, sr * 0.04, 0),
          hand_r=(0.12 * s(ph - 0.9), 0, 0))
    p["loc"] = [-0.3 * c(ph), 0, 0]
    return p


def walk(f):
    ph = TAU * f / 72.0
    p = trail(walk_cycle(ph), 1.0, 0.15)
    return coat(p, f / 3.0, 12, 0.8)


def stalk(f):
    u = f / 84.0
    ph = TAU * (u - 0.035 * s(2 * TAU * u))  # slower on each planted foot
    p = hunch(walk_cycle(ph, stride=0.45, swing=0.15, lag=0.15, knee=0.75, heavy=0.4), 1.0)
    P.add(p, arm_r=(-0.15, 0, 0.05), forearm_r=(-0.35, 0, 0), hand_r=(-0.2, 0, 0))
    P.add(p, head=(-0.04 * abs(c(ph + 0.4)), -0.06 * s(ph), 0))  # held level, steadied against the steps
    p = trail(p, 0.6, 0.0)
    return coat(p, f / 3.0, 14, 0.6)


def stalk_idle(f):
    t = f / 3.0  # 60 ticks
    p = hunch(P.pose(), 1.0)
    b = s(TAU * t / 20)
    P.add(p, arm_r=(-0.15 - 0.08 * bump(t, 20, 36, 5), 0, 0.05), forearm_r=(-0.35, 0, 0), hand_r=(-0.2 + 0.1 * s(TAU * t / 30), 0, 0),
          torso=(0.02 * b, 0.05 * s(TAU * t / 60), 0), head=(-0.01 * b, 0.25 * s(TAU * t / 60 - 0.5), 0.05 * s(TAU * t / 30)),
          neck=(0, 0.08 * s(TAU * t / 60 - 0.3), 0))
    return coat(p, t, 15, 0.5)


def run(f):
    ph = TAU * f / 36.0
    p = walk_cycle(ph, stride=1.1, swing=1.25, lag=1.0, knee=1.5, heavy=0.6)
    P.add(p, torso=(0.35, -0.05 * s(ph), 0), neck=(-0.15, 0, 0), head=(-0.12, 0, 0), arm_r=(0.4, 0, 0.1), arm_l=(-0.1, 0, -0.06),
          forearm_r=(-0.5, 0, 0), forearm_l=(-0.4, 0, 0), hand_r=(-0.3, 0, 0))
    p = trail(p, 0.8, 0.55)
    P.add(p, coat_back=(0.12 * s(2 * ph - 1.0), 0.05 * s(ph), 0))
    p["loc"][1] = -1.1 * max(0.0, c(2 * ph - 0.6)) ** 2  # off the ground between strides
    return p


def search(f):
    t = f / 3.0  # 40 ticks: look left, hold, look right, hold
    sweep = lerp(-1.0, 1.0, sm((t % 40) / 10)) if t % 40 < 20 else lerp(1.0, -1.0, sm((t % 40 - 20) / 10))
    p = P.pose()
    P.add(p, head=(0.05 * abs(sweep), 0.6 * sweep, -0.06 * sweep), neck=(0, 0.2 * sweep, 0), torso=(0.05, 0.22 * sweep, 0),
          pelvis=(0, 0.06 * sweep, 0), arm_r=(-0.25, 0, 0.1), forearm_r=(-0.45, 0, 0), hand_r=(-0.1, 0, 0),
          leg_r=(0.06 * sweep, 0, 0), leg_l=(-0.06 * sweep, 0, 0))
    return coat(p, t, 20, 0.6)


def lurk(f):
    t = f / 3.0  # 60 ticks
    p = hunch(P.pose(), 1.0)
    P.add(p, leg_r=(-0.35, 0, 0.05), leg_l=(-0.25, 0, -0.05), shin_r=(0.55, 0, 0), shin_l=(0.4, 0, 0), torso=(0.15, 0, 0))
    P.put(p, arm_r=(-0.35 - 0.05 * s(TAU * t / 30), 0, 0.12), forearm_r=(-1.0, 0, 0), arm_l=(-0.2, 0, -0.1), forearm_l=(-0.9, 0, 0),
          coat_back=(0.5 + 0.03 * s(TAU * t / 20), 0, 0))
    P.add(p, head=(0.1 + 0.03 * s(TAU * t / 15), 0.4 * s(TAU * t / 60), 0.08 * s(TAU * t / 60 + 1.0)), neck=(0, 0.1 * s(TAU * t / 60 - 0.4), 0),
          torso=(0.015 * s(TAU * t / 15), 0, 0), hand_r=(0.15 * bump(t, 36, 44, 3), 0.3 * bump(t, 36, 44, 3), 0))
    return p


def climb(f):
    q = TAU * f / 48.0
    p = P.pose()
    P.put(p, torso=(-0.1, 0.05 * s(q), 0.05 * s(q)), arm_r=(-2.7 + 0.4 * s(q), 0, 0.2), arm_l=(-2.7 - 0.4 * s(q), 0, -0.2),
          forearm_r=(-0.3 - 0.4 * max(0.0, c(q)), 0, 0), forearm_l=(-0.3 - 0.4 * max(0.0, -c(q)), 0, 0),
          leg_r=(-0.6 - 0.45 * s(q), 0, 0.04), leg_l=(-0.6 + 0.45 * s(q), 0, -0.04),
          shin_r=(0.8 + 0.3 * s(q), 0, 0), shin_l=(0.8 - 0.3 * s(q), 0, 0), head=(-0.5, -0.06 * s(q), 0), neck=(-0.1, 0, 0),
          coat_back=(0.05 + 0.03 * s(q - 1), 0, 0.03 * s(q)), coat_front_r=(-0.15, 0, 0), coat_front_l=(-0.15, 0, 0))
    p["loc"] = [0, -0.6 * abs(s(q)), 0]
    return p


# -------------------------------------------------------------------------------------------------------------- actions

def pose(**over):
    p = P.pose()
    for k, v in over.items():
        p[k] = list(v)
    return p


def action(keys, layer=None):
    at = spline(keys)

    def gen(f):
        t = f / 3.0
        p = at(t)
        return layer(p, t) if layer else p
    return gen


W1 = pose(arm_r=(-2.9, 0, -0.45), forearm_r=(-1.25, 0, 0), hand_r=(-0.45, 0, 0), torso=(-0.05, -0.45, 0.04), neck=(0, 0.15, 0),
          head=(0, 0.3, 0), arm_l=(-0.7, 0, -0.15), forearm_l=(-0.8, 0, 0), leg_r=(0.25, 0, 0.04), shin_r=(0.3, 0, 0),
          leg_l=(-0.2, 0, -0.04), shin_l=(0.15, 0, 0), coat_back=(0.12, 0, 0), coat_front_l=(-0.15, 0, 0), loc=(0, 0, 0.8))
WINDUP = [(0, P.pose()), (6, P.mix(P.pose(), W1, 0.85)), (10, W1)]
S1 = pose(arm_r=(0.4, -0.5, 0.35), forearm_r=(-0.05, 0, 0), hand_r=(0.35, 0, 0), torso=(0.5, 0.6, 0.06), neck=(0, -0.2, 0),
          head=(0.05, -0.3, 0), arm_l=(0.5, 0, -0.12), forearm_l=(-0.3, 0, 0), leg_r=(-0.45, 0, 0.04), shin_r=(0.35, 0, 0),
          leg_l=(0.25, 0, -0.04), shin_l=(0.25, 0, 0), coat_back=(0.45, 0, 0), coat_front_r=(-0.4, 0, 0), loc=(0, 0, -1.0))
F1 = P.put(P.copy(S1), arm_r=(0.75, -0.35, 0.2), forearm_r=(-0.3, 0, 0), torso=(0.38, 0.5, 0.03), coat_back=(0.3, 0, 0),
           hand_r=(0.2, 0, 0), loc=(0, 0, -0.7))
SWING = [(0, W1), (1.2, P.put(P.mix(W1, S1, 0.25), arm_r=(-2.3, -0.1, -0.2))), (2.5, S1), (4, F1), (7, P.mix(F1, P.pose(), 0.45))]

W2 = pose(arm_r=(-1.5, 0.9, 0.6), forearm_r=(-1.3, 0, 0), hand_r=(-0.2, 0.3, 0), torso=(0.05, 0.55, 0), neck=(0, -0.15, 0),
          head=(0, -0.25, 0), arm_l=(-0.4, 0, -0.2), forearm_l=(-0.6, 0, 0), leg_l=(0.25, 0, -0.04), shin_l=(0.3, 0, 0),
          leg_r=(-0.15, 0, 0.04), shin_r=(0.12, 0, 0), coat_back=(0.1, 0, 0), loc=(0, 0, 0.6))
WINDUP2 = [(0, P.pose()), (6, P.mix(P.pose(), W2, 0.85)), (10, W2)]
S2 = pose(arm_r=(-1.35, -1.0, -0.3), forearm_r=(-0.15, 0, 0), hand_r=(0.15, -0.2, 0), torso=(0.35, -0.6, -0.05), neck=(0, 0.2, 0),
          head=(0.05, 0.3, 0), arm_l=(0.45, 0, -0.15), forearm_l=(-0.3, 0, 0), leg_l=(-0.4, 0, -0.04), shin_l=(0.3, 0, 0),
          leg_r=(0.2, 0, 0.04), shin_r=(0.2, 0, 0), coat_back=(0.35, 0.1, 0), coat_front_l=(-0.35, 0, 0), loc=(0, 0, -0.9))
F2 = P.put(P.copy(S2), arm_r=(-0.6, -0.7, -0.1), forearm_r=(-0.4, 0, 0), torso=(0.25, -0.45, 0), coat_back=(0.25, 0.05, 0), loc=(0, 0, -0.6))
SWING2 = [(0, W2), (1.5, P.put(P.mix(W2, S2, 0.35), arm_r=(-1.45, 0.2, 0.2))), (3, S2), (4.5, F2), (7, P.mix(F2, P.pose(), 0.45))]

CROUCHED = pose(leg_r=(-1.15, 0, 0.05), leg_l=(-0.9, 0, -0.05), shin_r=(1.75, 0, 0), shin_l=(1.5, 0, 0), torso=(0.85, 0, 0),
                neck=(-0.6, 0, 0), head=(-0.5, 0, 0), arm_r=(0.95, 0, 0.35), arm_l=(0.95, 0, -0.35), forearm_r=(-0.4, 0, 0),
                forearm_l=(-0.3, 0, 0), hand_r=(-0.3, 0, 0), coat_back=(0.35, 0, 0), coat_front_r=(-0.5, 0, 0), coat_front_l=(-0.4, 0, 0))
CROUCH = [(0, P.pose()), (4, P.mix(P.pose(), CROUCHED, 0.6)), (12, CROUCHED)]


def crouch_layer(p, t):
    k = sm((t - 4) / 8)  # quivering as the legs load
    P.add(p, shin_r=(0.025 * s(4.1 * t) * k, 0, 0), shin_l=(0.025 * s(4.1 * t + 1.3) * k, 0, 0), hand_r=(0.05 * s(3.3 * t) * k, 0, 0))
    return p


LEAP = [(0, CROUCHED),
        (2, pose(leg_r=(-0.4, 0, 0.04), leg_l=(0.5, 0, -0.04), shin_r=(0.3, 0, 0), shin_l=(0.2, 0, 0), torso=(0.45, 0, 0),
                 neck=(-0.4, 0, 0), head=(-0.4, 0, 0), arm_r=(-2.2, 0, 0.2), forearm_r=(-0.6, 0, 0), arm_l=(-1.4, 0, -0.3),
                 forearm_l=(-0.5, 0, 0), coat_back=(0.9, 0, 0), coat_front_r=(0.3, 0, 0), coat_front_l=(0.3, 0, 0))),
        (6, pose(leg_r=(-1.2, 0, 0.05), leg_l=(0.8, 0, -0.05), shin_r=(0.4, 0, 0), shin_l=(0.9, 0, 0), torso=(0.3, 0, 0),
                 neck=(-0.3, 0, 0), head=(-0.35, 0, 0), arm_r=(-2.8, 0, 0.1), forearm_r=(-0.95, 0, 0), hand_r=(-0.4, 0, 0),
                 arm_l=(-1.8, 0, -0.3), forearm_l=(-0.6, 0, 0), coat_back=(1.15, 0, 0), coat_front_r=(0.45, 0, 0), coat_front_l=(0.4, 0, 0))),
        (8.5, pose(leg_r=(-1.25, 0, 0.05), leg_l=(0.7, 0, -0.05), shin_r=(0.45, 0, 0), shin_l=(0.85, 0, 0), torso=(0.2, 0, 0),
                   neck=(-0.25, 0, 0), head=(-0.3, 0, 0), arm_r=(-3.05, 0, 0.05), forearm_r=(-1.15, 0, 0), hand_r=(-0.5, 0, 0),
                   arm_l=(-1.9, 0, -0.3), forearm_l=(-0.6, 0, 0), coat_back=(1.25, 0, 0), coat_front_r=(0.45, 0, 0), coat_front_l=(0.45, 0, 0))),
        (12, pose(leg_r=(-0.8, 0, 0.05), leg_l=(0.3, 0, -0.05), shin_r=(0.6, 0, 0), shin_l=(0.6, 0, 0), torso=(0.65, 0.3, 0),
                  neck=(-0.4, -0.1, 0), head=(-0.25, -0.15, 0), arm_r=(0.5, -0.3, 0.25), forearm_r=(-0.1, 0, 0), hand_r=(0.35, 0, 0),
                  arm_l=(0.1, 0, -0.35), forearm_l=(-0.4, 0, 0), coat_back=(0.8, 0, 0), coat_front_r=(0.2, 0, 0), coat_front_l=(0.25, 0, 0))),
        (16, pose(leg_r=(-0.7, 0, 0.05), leg_l=(0.15, 0, -0.05), shin_r=(0.95, 0, 0), shin_l=(0.6, 0, 0), torso=(0.6, 0.2, 0),
                  neck=(-0.3, 0, 0), head=(-0.25, 0, 0), arm_r=(0.6, -0.2, 0.2), forearm_r=(-0.3, 0, 0), arm_l=(0.2, 0, -0.3),
                  forearm_l=(-0.35, 0, 0), coat_back=(0.45, 0, 0), coat_front_r=(-0.3, 0, 0)))]


def scent(f):
    t = f / 3.0
    env = sm(t / 6) * (1 - sm((t - 25) / 5))
    p = P.pose()
    sniff = sum(bump(t, a, a + 1.6, 0.5) for a in (8, 11, 17, 20.5))  # short, sharp sniffs
    P.put(p, neck=(-0.35, 0.2 * s(0.22 * t), 0), head=(-0.45 + 0.07 * sniff, 0.55 * s(0.22 * t), 0.05 * s(0.22 * t)),
          torso=(0.0 - 0.03 * sniff, 0.1 * s(0.22 * t - 0.5), 0), arm_r=(-0.1, 0, 0.12), forearm_r=(-0.4, 0, 0),
          arm_l=(0, 0, -0.1 - 0.03 * sniff))
    p = P.mix(P.pose(), p, env)
    return coat(p, t, 15, 0.5)


def stare(f):
    t = f / 3.0  # 50 ticks: the tilt comes in by 18, a twitch at 31, out from 44
    k = sm(t / 18) * (1 - sm((t - 44) / 6))
    p = P.pose()
    twitch = bump(t, 31, 32.5, 0.3)
    P.put(p, head=(-0.08 - 0.05 * twitch, 0.0, 0.48 + 0.12 * twitch), neck=(0, 0, 0.05), leg_r=(0, 0, 0.05), leg_l=(0, 0, -0.05),
          hand_r=(0.15 * s(TAU * t / 26), 0, 0), arm_r=(0.05, 0, 0.1), torso=(0.08 + 0.006 * s(TAU * t / 40), 0, 0))
    return P.mix(P.pose(), p, k)


def clips():
    G = "contact"
    return [
        A.Clip("idle", 240, gen=idle, step=3, loop=True, ground=G),
        A.Clip("walk", 72, gen=walk, step=2, loop=True, ground=G),
        A.Clip("stalk", 84, gen=stalk, step=2, loop=True, ground=G),
        A.Clip("stalk_idle", 180, gen=stalk_idle, step=3, loop=True, ground=G),
        A.Clip("run", 36, gen=run, step=2, loop=True, ground=G),
        A.Clip("search", 120, gen=search, step=3, loop=True, ground=G),
        A.Clip("lurk", 180, gen=lurk, step=3, loop=True, ground=G),
        A.Clip("climb", 48, gen=climb, step=2, loop=True),
        A.Clip("windup", 30, gen=action(WINDUP), step=1, ground=G),
        A.Clip("swing", 21, gen=action(SWING), step=1, ground=G),
        A.Clip("windup2", 30, gen=action(WINDUP2), step=1, ground=G),
        A.Clip("swing2", 21, gen=action(SWING2), step=1, ground=G),
        A.Clip("crouch", 36, gen=action(CROUCH, crouch_layer), step=1, ground=G),
        A.Clip("leap", 48, gen=action(LEAP), step=1, ground=G),
        A.Clip("scent", 90, gen=scent, step=2, ground=G),
        A.Clip("stare", 150, gen=stare, step=2, ground=G),
    ]


def build():
    return A.build("Aberrant", clips(), "pelvis", {"shin_r", "shin_l"}, 0.06)
