"""The Guest's animations, keyed at 60 fps in Blender on the rig dl_rig.build("Guest", "GuestGeometry.java", "guest.bin",
"guest/model") makes. It moves like an animal: on all fours, its long arms as front legs.

Loops (sampled by GuestModel by time or by stride):
  idle 240          the stance, breathing hard (ribs heaving, shoulders spreading), head scanning with a sudden glance,
                    panting, claws kneading the ground, one forepaw lifting and re-planting, weight rolling, tail alive
  prowl 60          a four-beat walk (left hind, left fore, right hind, right fore), shoulders rolling, spine swaying
                    against the hips, the head held still like a stalking cat's, the tail swinging with the hips
  gallop 36         a bounding gallop: forelegs reach and land, hind legs drive, the spine coils and stretches, a real
                    moment of flight, jaw open, tail streaming
  stalk 72          belly almost on the ground, slow deliberate steps with a pause at each paw, head dead still
  stalk_idle 180    crouched and waiting: slow breaths, the tail tip twitching, a paw easing forward
  climb 48          reared against a wall, claws reaching up in turn, hind legs pushing, heaving up with each pull
  swim 60           dog-paddling, head up, tail trailing

Actions (lengths are GuestEntity's act() ticks; chains start where the clip before them ends):
  roar 40, swipe_windup 6 -> swipe 8, grab_windup 20 -> grab_lunge 8 -> grab_hold 22 -> slam 12, stun 30, knock 14,
  tear 20, feed 60. Clips that end a chain end on the stance, so nothing snaps when the action stops.

Keyed actions use spline() (Hermite curves through the keys, clamped at turning points so holds hold) and get layers of
motion on top (shaking, trembling, the tail). Conventions are the game's (see dl_rig.py): -x swings a limb forward, the
shin folds back with +x, +z moves the right arm out and -z the left. "loc" is the pelvis offset in pixels (y down);
grounding keeps the lowest paw on the floor, so an authored negative y is a lift. One tick is three frames.
Run: dl_anim.build via build(), then dl_export.export("Guest", "guest.anim").
"""
import math

import dl_anim as A

TAU = math.tau
PI = math.pi
TAIL = [f"tail_{i}" for i in range(13)]
ARM, FOREARM, HAND, LEG, SHIN, PELVIS, TORSO = -1.75, -1.1, -0.4, -1.0, 0.9, 0.75, 0.85

B = {"pelvis": (PELVIS, 0, 0), "torso": (TORSO, 0, 0), "neck": (-1.1, 0, 0), "head": (-0.2, 0, 0), "jaw": (0.12, 0, 0),
     "horn_r": (0, 0, 0), "horn_l": (0, 0, 0),
     "arm_r": (ARM, 0, 0.28), "forearm_r": (FOREARM, 0, 0), "hand_r": (HAND, 0, 0),
     "arm_l": (ARM, 0, -0.28), "forearm_l": (FOREARM, 0, 0), "hand_l": (HAND, 0, 0),
     "leg_r": (LEG, 0, 0.1), "shin_r": (SHIN, 0, 0), "leg_l": (LEG, 0, -0.1), "shin_l": (SHIN, 0, 0),
     "tail_0": (-0.5, 0, 0)}
B.update({b: (-0.015, 0, 0) for b in TAIL[1:]})
P = A.Poser(B)
sm = A.smooth
lerp = A.lerp


def s(x):
    return math.sin(x)


def c(x):
    return math.cos(x)


def bump(t, a, b, r):
    """1 between a and b, eased in and out over r."""
    return sm((t - a) / r) * (1 - sm((t - b) / r))


def toward(p, k, **vals):
    """Moves bones toward the given angles by k (None keeps an axis)."""
    for bone, v in vals.items():
        p[bone] = [x if y is None else lerp(x, y, k) for x, y in zip(p[bone], v)]
    return p


def rear(p, k, lean):
    """Up on the hind legs by k (0 on all fours, 1 upright); lean keeps some forward lean at full height."""
    return toward(p, k, pelvis=(lean * 0.4, None, None), torso=(lean * 0.6, None, None), neck=(-0.3, None, None),
                  leg_r=(-0.35, None, None), leg_l=(-0.35, None, None), shin_r=(0.45, None, None), shin_l=(0.45, None, None),
                  tail_0=(-0.15, None, None))


def crouch(p, k):
    """Sinking toward the ground by k: haunches folded, elbows out and bent, the claws kept flat."""
    return P.add(p, leg_r=(-0.3 * k, 0, 0.04 * k), leg_l=(-0.3 * k, 0, -0.04 * k), shin_r=(0.6 * k, 0, 0), shin_l=(0.6 * k, 0, 0),
                 arm_r=(0.3 * k, 0, 0.12 * k), arm_l=(0.3 * k, 0, -0.12 * k), forearm_r=(-0.4 * k, 0, 0), forearm_l=(-0.4 * k, 0, 0),
                 hand_r=(0.1 * k, 0, 0), hand_l=(0.1 * k, 0, 0), neck=(-0.1 * k, 0, 0), head=(0.1 * k, 0, 0))


def tail(p, t, period, amp=1.0, lash=0.0, flick=None):
    """A wave running down the tail (each segment a little behind the last), bigger toward the tip; lash whips it."""
    for i, b in enumerate(TAIL):
        tip = i / 12.0
        w = TAU * t / period - i * 0.45
        yaw = amp * (0.035 + 0.075 * tip) * s(w) + lash * (0.06 + 0.12 * tip) * s(2.7 * w)
        pitch = amp * 0.025 * (0.3 + tip) * s(w * 0.5 + 0.9) if i else 0.0
        P.add(p, **{b: (pitch, yaw, 0)})
    if flick is not None:
        P.add(p, tail_10=(0, 0.25 * flick, 0), tail_11=(0, 0.4 * flick, 0), tail_12=(0, 0.6 * flick, 0))
    return p


def spline(keys):
    """A pose at any tick from (tick, pose) keys: Hermite curves through them, the slope at each key the average of
    its neighbours' (zero where the motion turns or holds, and at the ends)."""
    ts = [k for k, _ in keys]
    ps = [p for _, p in keys]

    def slope(i, b, ch):
        if i == 0 or i == len(ts) - 1:
            return 0.0
        d0 = ps[i][b][ch] - ps[i - 1][b][ch]
        d1 = ps[i + 1][b][ch] - ps[i][b][ch]
        if d0 * d1 <= 0:
            return 0.0
        m = (ps[i + 1][b][ch] - ps[i - 1][b][ch]) / (ts[i + 1] - ts[i - 1])
        lim = 3 * min(abs(d0) / (ts[i] - ts[i - 1]), abs(d1) / (ts[i + 1] - ts[i]))
        return max(-lim, min(lim, m))

    def at(t):
        if t <= ts[0]:
            return P.copy(ps[0])
        if t >= ts[-1]:
            return P.copy(ps[-1])
        i = max(j for j in range(len(ts) - 1) if ts[j] <= t)
        h = ts[i + 1] - ts[i]
        u = (t - ts[i]) / h
        h00, h10, h01, h11 = 2 * u ** 3 - 3 * u ** 2 + 1, u ** 3 - 2 * u ** 2 + u, -2 * u ** 3 + 3 * u ** 2, u ** 3 - u ** 2
        return {b: [h00 * ps[i][b][ch] + h10 * h * slope(i, b, ch) + h01 * ps[i + 1][b][ch] + h11 * h * slope(i + 1, b, ch)
                    for ch in range(3)] for b in ps[i]}
    return at


# ---------------------------------------------------------------------------------------------------------------- loops

def idle(f):
    t = f / 3.0  # 80 ticks
    p = P.pose()
    b = s(TAU * t / 20)  # four heavy breaths
    roll = s(TAU * t / 80)
    P.add(p, torso=(0.05 * b, 0.03 * roll, -0.03 * roll), pelvis=(-0.015 * b, -0.02 * roll, 0.035 * roll),
          neck=(-0.04 * b, 0, 0.03 * roll), arm_r=(0, 0, 0.025 * b), arm_l=(0, 0, -0.025 * b),
          shin_r=(0.06 * b + 0.05 * max(0, roll), 0, 0), shin_l=(0.06 * b + 0.05 * max(0, -roll), 0, 0),
          forearm_r=(-0.03 * b, 0, 0), forearm_l=(-0.03 * b, 0, 0))
    # The head: a slow scan, then at 46 ticks a sudden glance aside, held, and back; a tilt now and then.
    glance = sm((t - 46) / 1.5) * (1 - sm((t - 58) / 5))
    tilt = s(TAU * t / 40) ** 3
    P.add(p, head=(0.05 * s(TAU * t / 40 + 1) - 0.08 * glance, 0.3 * s(TAU * t / 80 + 0.5) + 0.55 * glance, 0.22 * tilt),
          neck=(0, 0.12 * glance, 0))
    P.add(p, jaw=(0.16 * max(0.0, s(TAU * t / 10)) + 0.05 * b, 0, 0))
    # Claws kneading the ground; the right forepaw lifts and re-plants at 18-28 ticks.
    P.add(p, hand_r=(0.08 * s(TAU * t / 16), 0, 0), hand_l=(0.08 * s(TAU * t / 16 + 2), 0, 0))
    paw = bump(t, 18, 26, 3)
    P.add(p, arm_r=(-0.22 * paw, 0, 0.05 * paw), forearm_r=(-0.55 * paw, 0, 0), hand_r=(-0.35 * paw, 0, 0), torso=(0, 0, 0.04 * paw))
    flick = bump(t, 66, 68, 1) * s(TAU * t / 2.0)
    return tail(p, t, 40, 1.0, flick=flick)


def limb(ph):
    """A paw's swing over its cycle: forward through the air (30%), then back steadily while planted (70%)."""
    u = (ph / TAU) % 1.0
    if u < 0.3:
        return lerp(1.0, -1.0, sm(u / 0.3))
    return lerp(-1.0, 1.0, (u - 0.3) / 0.7)


def lift(ph):
    u = (ph / TAU) % 1.0
    return s(u / 0.3 * PI) if u < 0.3 else 0.0


def gait4(ph, amp=1.0, low=0.0):
    """The four-beat walk at phase ph (radians): lateral sequence, a quarter cycle between paws."""
    p = crouch(P.pose(), low)
    lh, lf, rh, rf = limb(ph), limb(ph + PI / 2), limb(ph + PI), limb(ph + 1.5 * PI)
    lhl, lfl, rhl, rfl = lift(ph), lift(ph + PI / 2), lift(ph + PI), lift(ph + 1.5 * PI)
    hi = 1.0 + 0.35 * low  # stalking paws lift higher, placed with care
    P.add(p, arm_l=(lf * 0.42 * amp, 0.06 * lf, 0), arm_r=(rf * 0.42 * amp, -0.06 * rf, 0),
          forearm_l=(-lfl * 0.75 * hi, 0, 0), forearm_r=(-rfl * 0.75 * hi, 0, 0),
          hand_l=(-lf * 0.42 * amp * (1 - lfl) - lfl * 0.55, 0, 0), hand_r=(-rf * 0.42 * amp * (1 - rfl) - rfl * 0.55, 0, 0),
          leg_l=(lh * 0.4 * amp, 0, 0), leg_r=(rh * 0.4 * amp, 0, 0),
          shin_l=(lhl * 0.7 * hi, 0, 0), shin_r=(rhl * 0.7 * hi, 0, 0))
    sway = s(ph)
    P.add(p, pelvis=(0.03 * s(2 * ph), -0.06 * c(ph), 0.05 * sway), torso=(-0.03 * s(2 * ph + 0.6), 0.07 * c(ph), -0.045 * s(ph + 0.5)))
    # The head stays put while the body moves under it.
    P.add(p, neck=(0.04 * s(2 * ph + 0.9), -0.02 * c(ph), -(0.05 * sway - 0.045 * s(ph + 0.5)) * 0.9),
          head=(-0.03 * s(2 * ph + 0.6), -0.06 * c(ph), 0))
    p["loc"] = [0.55 * sway, 0, 0]
    for i, bone in enumerate(TAIL):  # swung by the hips, a beat behind toward the tip
        tip = i / 12.0
        P.add(p, **{bone: (0.02 * s(2 * ph - i * 0.4) if i else 0.0, (0.05 + 0.08 * tip) * s(ph - 0.6 - i * 0.35), 0)})
    return p


def prowl(f):
    ph = TAU * f / 60.0
    p = gait4(ph)
    P.add(p, jaw=(0.1 + 0.05 * s(2 * ph), 0, 0))
    return p


def gallop(f):
    g = TAU * f / 36.0
    fore, hind, flex, lead = s(g), s(g + 2.2), c(g + 1.1), 0.12
    p = P.pose()
    P.put(p, arm_r=(ARM - 0.15 - fore * 0.85, 0, 0.2), arm_l=(ARM - 0.15 - s(g + lead) * 0.85, 0, -0.2),
          forearm_r=(FOREARM + 0.15 - max(0.0, c(g)) * 0.95, 0, 0), forearm_l=(FOREARM + 0.15 - max(0.0, c(g + lead)) * 0.95, 0, 0),
          hand_r=(HAND - 0.35 * max(0.0, c(g)) + 0.25 * max(0.0, -c(g)), 0, 0),
          hand_l=(HAND - 0.35 * max(0.0, c(g + lead)) + 0.25 * max(0.0, -c(g + lead)), 0, 0),
          leg_r=(LEG + 0.1 - hind * 0.95, 0, 0.08), leg_l=(LEG + 0.1 - s(g + 2.2 + lead) * 0.95, 0, -0.08),
          shin_r=(SHIN + max(0.0, c(g + 2.2)) * 0.85, 0, 0), shin_l=(SHIN + max(0.0, c(g + 2.2 + lead)) * 0.85, 0, 0),
          torso=(TORSO + 0.1 + flex * 0.2, 0, 0), pelvis=(PELVIS + 0.05 - flex * 0.14, 0, 0),
          neck=(-1.2 - flex * 0.1, 0, 0), head=(-0.2 + flex * 0.14, 0, 0), jaw=(0.45 + 0.1 * s(2 * g), 0, 0))
    for i, bone in enumerate(TAIL):  # streaming out behind, rippling with each bound
        P.put(p, **{bone: ((-0.6 + 0.08 * flex) if i == 0 else (-0.01 + 0.05 * s(g - 1.0 - i * 0.5)), 0.03 * s(g - i * 0.4), 0)})
    p["loc"] = [0, -2.0 * max(0.0, s(g - 0.2)) ** 3, 0]  # airborne after the hind legs drive
    return p


def stalk(f):
    u = f / 72.0
    ph = TAU * (u - 0.03 * s(4 * TAU * u))  # slow at each paw, quicker between: deliberate
    p = gait4(ph, 0.75, 1.0)
    P.add(p, neck=(-0.2, 0, 0), head=(0.25, 0, 0), jaw=(-0.08, 0, 0), tail_0=(-0.12, 0, 0))
    for i, bone in enumerate(TAIL[8:], 8):
        P.add(p, **{bone: (0, 0.1 * s(TAU * u * 3 - i * 0.6) * (i - 7) / 5, 0)})  # the tip twitching
    return p


def stalk_idle(f):
    t = f / 3.0  # 60 ticks
    p = crouch(P.pose(), 1.0)
    b = s(TAU * t / 30)
    P.add(p, neck=(-0.2 - 0.02 * b, 0.08 * s(TAU * t / 60), 0), head=(0.25 + 0.02 * b, 0.12 * s(TAU * t / 60 - 0.8), 0),
          torso=(0.03 * b, 0, 0), jaw=(-0.08, 0, 0), tail_0=(-0.12, 0, 0))
    paw = bump(t, 30, 44, 4)  # the left forepaw eases forward and down again
    P.add(p, arm_l=(-0.18 * paw, 0, 0), forearm_l=(-0.35 * paw, 0, 0), hand_l=(-0.2 * paw, 0, 0))
    twitch = bump(t, 12, 16, 1) + bump(t, 48, 51, 1)
    return tail(p, t, 30, 0.4, flick=twitch * s(TAU * t / 1.5))


def climb(f):
    q = TAU * f / 48.0
    p = P.pose()
    P.put(p, pelvis=(0.05, 0, 0.06 * s(q)), torso=(0.2, 0.06 * s(q), -0.08 * s(q)), neck=(-0.5, 0, 0), head=(-0.4, -0.05 * s(q), 0),
          arm_r=(-2.8 + 0.45 * s(q), 0, 0.28), arm_l=(-2.8 - 0.45 * s(q), 0, -0.28),
          forearm_r=(-0.4 - 0.5 * max(0.0, c(q)), 0, 0), forearm_l=(-0.4 - 0.5 * max(0.0, -c(q)), 0, 0),
          hand_r=(-0.5 + 0.3 * s(q), 0, 0), hand_l=(-0.5 - 0.3 * s(q), 0, 0),
          leg_r=(-0.7 - 0.5 * s(q), 0, 0.12), leg_l=(-0.7 + 0.5 * s(q), 0, -0.12), shin_r=(1.0 + 0.3 * max(0.0, s(q)), 0, 0),
          shin_l=(1.0 + 0.3 * max(0.0, -s(q)), 0, 0), jaw=(0.3 + 0.1 * s(2 * q), 0, 0), tail_0=(-1.3, 0.05 * s(q), 0))  # hanging down the wall
    for i, bone in enumerate(TAIL[1:], 1):
        P.put(p, **{bone: (0.04 * s(q * 2 - i * 0.4), 0.06 * s(q - i * 0.5) * (0.3 + i / 12), 0)})
    p["loc"] = [0, 1.0 - 0.8 * abs(s(q)), 0]  # heaving up with each pull
    return p


def swim(f):
    q = TAU * f / 60.0
    p = P.pose()
    P.put(p, pelvis=(1.35, 0, 0.04 * s(q)), torso=(0.15, 0, -0.04 * s(q)), neck=(-0.55, 0, 0), head=(-0.65 + 0.05 * s(2 * q), 0, 0),
          jaw=(0.1, 0, 0), arm_r=(-1.9 + 0.6 * s(q), 0, 0.28), arm_l=(-1.9 + 0.6 * s(q + PI), 0, -0.28),
          forearm_r=(-0.9 - 0.8 * max(0.0, c(q)), 0, 0), forearm_l=(-0.9 - 0.8 * max(0.0, c(q + PI)), 0, 0),
          leg_r=(-0.3 + 0.5 * s(q + 1), 0, 0.1), leg_l=(-0.3 + 0.5 * s(q + 1 + PI), 0, -0.1), shin_r=(0.6, 0, 0), shin_l=(0.6, 0, 0))
    for i, bone in enumerate(TAIL):
        P.put(p, **{bone: (-1.3 if i == 0 else 0.0, 0.12 * s(q - i * 0.5) * (0.3 + i / 12), 0)})  # trailing on the water
    p["loc"] = [0, 6.5 + 0.4 * s(2 * q), 0]
    return p


# -------------------------------------------------------------------------------------------------------------- actions

def pose(**over):
    return P.pose(**over)


def stance():
    return P.pose()


def reared(k, lean, **over):
    p = rear(P.pose(), k, lean)
    return P.put(p, **over)


def crouched(k, **over):
    return P.put(crouch(P.pose(), k), **over)


def action(keys, layer=None):
    at = spline(keys)

    def gen(f):
        t = f / 3.0
        p = at(t)
        return layer(p, t) if layer else p
    return gen


def roar_layer(p, t):
    k = bump(t, 12, 33, 2)
    P.add(p, head=(0.06 * s(3.1 * t) * k, 0.15 * s(2.4 * t) * k, 0.05 * s(1.9 * t) * k), jaw=(0.07 * s(5.0 * t) * k, 0, 0),
          arm_r=(0.06 * s(2.2 * t) * k, 0, 0.08 * s(2.4 * t) * k), arm_l=(0.06 * s(2.2 * t + 1) * k, 0, -0.08 * s(2.4 * t + 0.5) * k),
          hand_r=(0.15 * s(1.3 * t) * k, 0, 0), hand_l=(0.15 * s(1.3 * t + 1.5) * k, 0, 0), torso=(0.03 * s(1.6 * t) * k, 0.05 * s(1.2 * t) * k, 0.04 * s(1.7 * t) * k))
    return tail(p, t, 14, 1.0, lash=k)


ROAR = [(0, stance()),
        (4, crouched(0.6, head=(0.25, 0, 0), neck=(-1.25, 0, 0), jaw=(0.3, 0, 0), tail_0=(-0.7, 0, 0))),
        (11, reared(1.05, -0.05, head=(-0.9, 0, 0), jaw=(1.1, 0, 0), arm_r=(-2.55, 0.1, 0.9), arm_l=(-2.55, -0.1, -0.9),
                    forearm_r=(-0.55, 0, 0), forearm_l=(-0.55, 0, 0), hand_r=(-0.7, 0, 0), hand_l=(-0.7, 0, 0))),
        (15, reared(1.0, 0.15, head=(-0.75, 0, 0), jaw=(1.05, 0, 0), arm_r=(-2.4, 0, 0.75), arm_l=(-2.4, 0, -0.75),
                    forearm_r=(-0.7, 0, 0), forearm_l=(-0.7, 0, 0), hand_r=(-0.6, 0, 0), hand_l=(-0.6, 0, 0))),
        (31, reared(0.97, 0.2, head=(-0.7, 0, 0), jaw=(1.0, 0, 0), arm_r=(-2.35, 0, 0.7), arm_l=(-2.35, 0, -0.7),
                    forearm_r=(-0.75, 0, 0), forearm_l=(-0.75, 0, 0), hand_r=(-0.6, 0, 0), hand_l=(-0.6, 0, 0))),
        (36, reared(0.35, 0.4, head=(-0.1, 0, 0), jaw=(0.5, 0, 0), arm_r=(-2.1, 0, 0.35), arm_l=(-2.1, 0, -0.35),
                    forearm_r=(-0.9, 0, 0), forearm_l=(-0.9, 0, 0))),
        (38, crouched(0.35, jaw=(0.3, 0, 0))),  # landing: the forelegs take the weight
        (40, stance())]

SWIPE_W = reared(0.55, 0.5, arm_r=(-2.7, -0.2, 0.6), forearm_r=(-1.2, 0, 0), hand_r=(-0.7, 0, 0), arm_l=(-1.9, 0, -0.38),
                 torso=(0.5 * 0.6 * 0.55 + TORSO * 0.45, 0.35, 0.04), head=(-0.15, -0.25, 0), jaw=(0.6, 0, 0))
SWIPE_WINDUP = [(0, stance()),
                (4, P.mix(stance(), SWIPE_W, 0.88)),
                (6, SWIPE_W)]
SWIPE_S = reared(0.3, 0.5, arm_r=(-0.9, 0.35, -0.45), forearm_r=(-0.25, 0, 0), hand_r=(0.3, 0, 0), arm_l=(-1.85, 0, -0.32),
                 torso=(TORSO * 0.7 + 0.09, -0.5, -0.06), head=(-0.2, 0.25, 0.05), jaw=(0.85, 0, 0))
SWIPE = [(0, SWIPE_W),
         (1.5, P.mix(SWIPE_W, SWIPE_S, 0.45)),
         (3, SWIPE_S),
         (4.5, P.put(P.copy(SWIPE_S), arm_r=(-0.75, 0.4, -0.55), torso=(TORSO * 0.75 + 0.07, -0.58, -0.07), forearm_r=(-0.15, 0, 0))),
         (8, stance())]


def windup_layer(p, t):
    k = sm(t / 20)  # trembling as it coils
    P.add(p, shin_r=(0.03 * s(4.2 * t) * k, 0, 0), shin_l=(0.03 * s(4.2 * t + 1) * k, 0, 0), head=(0, 0, 0.03 * s(3.3 * t) * k),
          torso=(0.015 * s(2.8 * t) * k, 0, 0))
    return tail(p, t, 6, 0.3 + 0.7 * k, lash=0.6 * k)


GRAB_G = crouched(1.0, leg_r=(LEG - 0.65, 0, 0.14), leg_l=(LEG - 0.65, 0, -0.14), shin_r=(SHIN + 1.2, 0, 0), shin_l=(SHIN + 1.2, 0, 0),
                  arm_r=(ARM + 0.3, 0, 0.7), arm_l=(ARM + 0.3, 0, -0.7), neck=(-1.4, 0, 0), head=(-0.05, 0, 0), jaw=(0.9, 0, 0),
                  tail_0=(-0.1, 0, 0))
GRAB_G["loc"] = [0, 0, 1.2]  # weight back on the haunches
GRAB_WINDUP = [(0, stance()), (5, P.mix(stance(), GRAB_G, 0.55)), (20, GRAB_G)]

GRAB_L = pose(pelvis=(0.95, 0, 0), torso=(0.75, 0, 0), neck=(-1.0, 0, 0), head=(-0.45, 0, 0), jaw=(1.05, 0, 0),
              arm_r=(-2.9, 0, 0.35), arm_l=(-2.9, 0, -0.35), forearm_r=(-0.2, 0, 0), forearm_l=(-0.2, 0, 0), hand_r=(-0.6, 0, 0), hand_l=(-0.6, 0, 0),
              leg_r=(LEG + 0.9, 0, 0.05), leg_l=(LEG + 0.9, 0, -0.05), shin_r=(0.2, 0, 0), shin_l=(0.2, 0, 0), tail_0=(-0.7, 0, 0))
GRAB_L1 = P.copy(GRAB_L)
P.put(GRAB_L1, arm_r=(-2.5, 0, 0.45), arm_l=(-2.5, 0, -0.45), forearm_r=(-0.5, 0, 0), forearm_l=(-0.5, 0, 0), leg_r=(LEG + 0.6, 0, 0.08),
      leg_l=(LEG + 0.6, 0, -0.08), shin_r=(0.45, 0, 0), shin_l=(0.45, 0, 0))
GRAB_L1["loc"] = [0, -2.5, -2.0]
GRAB_L["loc"] = [0, -1.5, -3.5]
GRAB_LUNGE = [(0, GRAB_G), (2, GRAB_L1), (4.5, GRAB_L), (8, P.put(P.copy(GRAB_L), loc=(0, -0.3, -2.5)))]

GRAB_H = reared(1.0, 0.0, arm_r=(-2.85, 0, 0.2), arm_l=(-2.85, 0, -0.2), forearm_r=(-0.8, 0, 0), forearm_l=(-0.8, 0, 0),
                hand_r=(-0.3, 0, 0), hand_l=(-0.3, 0, 0), head=(-0.55, 0, 0), jaw=(0.95, 0, 0))


def hold_layer(p, t):
    k = sm((t - 8) / 3) * (1 - sm((t - 19) / 3))  # shaking its prey
    P.add(p, arm_r=(0.1 * s(1.7 * t) * k, 0, 0.04 * s(1.7 * t) * k), arm_l=(0.1 * s(1.7 * t) * k, 0, -0.04 * s(1.7 * t) * k),
          torso=(0.04 * s(1.7 * t + 0.5) * k, 0.06 * s(0.85 * t) * k, 0.05 * s(1.7 * t) * k), head=(0.05 * s(1.7 * t + 1) * k, 0.14 * s(0.85 * t + 0.4) * k, 0),
          jaw=(0.1 * s(3.4 * t) * k, 0, 0))
    return tail(p, t, 10, 0.5, lash=0.7 * k)


GRAB_HOLD = [(0, GRAB_LUNGE[-1][1]),
             (6, P.put(P.mix(GRAB_L, GRAB_H, 0.75), loc=(0, 0, -0.8))),
             (10, GRAB_H), (22, GRAB_H)]

SLAM = [(0, GRAB_H),
        (2, reared(1.0, -0.1, arm_r=(-3.05, 0, 0.2), arm_l=(-3.05, 0, -0.2), forearm_r=(-0.6, 0, 0), forearm_l=(-0.6, 0, 0),
                   head=(-0.6, 0, 0), jaw=(0.8, 0, 0))),
        (4, crouched(0.9, torso=(TORSO + 0.25, 0, 0), arm_r=(-1.3, 0, 0.45), arm_l=(-1.3, 0, -0.45), forearm_r=(-0.2, 0, 0),
                     forearm_l=(-0.2, 0, 0), hand_r=(0.2, 0, 0), hand_l=(0.2, 0, 0), head=(0.2, 0, 0), jaw=(1.05, 0, 0), loc=(0, 0, -1.5))),
        (6, crouched(0.6, torso=(TORSO + 0.15, 0, 0), arm_r=(-1.45, 0, 0.4), arm_l=(-1.45, 0, -0.4), head=(0.1, 0, 0), jaw=(0.7, 0, 0), loc=(0, 0, -1.0))),
        (12, stance())]


def slam_layer(p, t):
    k = bump(t, 4, 7, 0.6)
    P.add(p, head=(0.08 * s(4 * t) * k, 0.1 * s(3 * t) * k, 0))
    return tail(p, t, 8, 0.6, lash=k)


def stun_layer(p, t):
    fade = 1 - sm(t / 30)
    k = sm(t / 3) * fade
    P.add(p, head=(0.05 * s(0.9 * t) * k, 0.12 * s(0.4 * t) * k, 0.35 * s(0.55 * t) * k), torso=(0, 0, 0.15 * s(0.35 * t) * k),
          neck=(0, 0, -0.08 * s(0.35 * t) * k), pelvis=(0, 0, 0.06 * s(0.35 * t + 0.6) * k))
    p["loc"][0] += 1.2 * s(0.35 * t) * k
    return tail(p, t, 20, 0.6)


STUN = [(0, stance()),
        (2.5, pose(head=(-0.45, 0, 0.1), neck=(-1.3, 0, 0), torso=(0.7, 0, 0), jaw=(0.6, 0, 0), loc=(0, 0, 1.0))),
        (8, pose(arm_r=(ARM + 0.4, 0, 0.32), forearm_r=(FOREARM + 0.6, 0, 0), hand_r=(HAND + 0.3, 0, 0), arm_l=(ARM + 0.2, 0, -0.3),
                 head=(0.35, 0, 0), neck=(-0.8, 0, 0), jaw=(0.5, 0, 0), tail_0=(-0.75, 0, 0), loc=(0, 0, 0.4))),
        (14, pose(arm_r=(ARM + 0.3, 0, 0.3), forearm_r=(FOREARM + 0.4, 0, 0), head=(0.3, 0, 0), neck=(-0.85, 0, 0), jaw=(0.5, 0, 0),
                  tail_0=(-0.7, 0, 0))),
        (22, pose(head=(0.05, 0, 0), neck=(-1.0, 0, 0), jaw=(0.3, 0, 0))),
        (30, stance())]

KNOCK_UP = reared(0.6, 0.4, arm_r=(-2.75, 0, 0.3), forearm_r=(-1.4, 0, 0), hand_r=(-0.6, 0, 0), arm_l=(-1.95, 0, -0.35), jaw=(0.5, 0, 0))
KNOCK = [(0, stance()),
         (4, KNOCK_UP),
         (6, P.put(P.copy(KNOCK_UP), arm_r=(-2.9, 0, 0.32), forearm_r=(-1.5, 0, 0), torso=(0.3, 0.12, 0))),
         (8, P.put(P.copy(KNOCK_UP), arm_r=(-1.8, 0, 0.3), forearm_r=(-0.2, 0, 0), hand_r=(0.3, 0, 0), torso=(0.45, -0.08, 0.05),
                   head=(0.05, 0, 0), jaw=(0.7, 0, 0), loc=(0, 0, -0.5))),
         (9.5, P.put(P.copy(KNOCK_UP), arm_r=(-2.0, 0, 0.3), forearm_r=(-0.45, 0, 0), torso=(0.42, -0.04, 0.02))),
         (14, stance())]


def tear(f):
    t = f / 3.0
    env = sm(t / 3) * (1 - sm((t - 16) / 4))
    pull = s(0.95 * (t - 1))
    p = rear(P.pose(), 0.55, 0.3)
    P.put(p, arm_r=(-2.3 - pull * 0.55, 0, 0.35), arm_l=(-2.3 + pull * 0.55, 0, -0.35),
          forearm_r=(-0.6 + pull * 0.45, 0, 0), forearm_l=(-0.6 - pull * 0.45, 0, 0), hand_r=(-0.4 + 0.4 * pull, 0, 0),
          hand_l=(-0.4 - 0.4 * pull, 0, 0), jaw=(0.8 + 0.1 * s(1.9 * t), 0, 0))
    P.add(p, torso=(0.04 * abs(pull), 0.15 * pull, 0.04 * pull), head=(0.06 * abs(c(0.95 * (t - 1))), -0.12 * pull, 0))
    p = P.mix(P.pose(), p, env)
    return tail(p, t, 7, 0.4, lash=0.8 * env)


def feed(f):
    t = f / 3.0
    env = sm(t / 5) * (1 - sm((t - 54) / 6))
    p = crouch(P.pose(), 0.5)
    yank = sum(bump(t, a, a + 1.5, 1.0) for a in (9, 23, 36, 47))  # tearing a mouthful away
    P.put(p, neck=(-0.2 - 0.35 * yank, 0, 0), head=(0.55 + 0.15 * s(0.6 * t) - 0.35 * yank, 0.25 * s(0.35 * t) + 0.25 * yank * s(3 * t), 0.1 * yank),
          jaw=(0.3 + 0.75 * max(0.0, s(0.95 * t)) - 0.2 * yank, 0, 0), torso=(TORSO + 0.3 - 0.12 * yank, 0, 0),
          arm_r=(ARM + 0.3 + 0.25 * s(0.45 * t), 0, 0.4), arm_l=(ARM + 0.3 - 0.25 * s(0.45 * t + 1.0), 0, -0.4),
          hand_r=(HAND + 0.25, 0, 0), hand_l=(HAND + 0.25, 0, 0), tail_0=(-0.8, 0, 0))
    p = P.mix(P.pose(), p, env)
    return tail(p, t, 20, 0.5)


def clips():
    G = "contact"
    return [
        A.Clip("idle", 240, gen=idle, step=3, loop=True, ground=G),
        A.Clip("prowl", 60, gen=prowl, step=2, loop=True, ground=G),
        A.Clip("gallop", 36, gen=gallop, step=2, loop=True, ground=G),
        A.Clip("stalk", 72, gen=stalk, step=2, loop=True, ground=G),
        A.Clip("stalk_idle", 180, gen=stalk_idle, step=3, loop=True, ground=G),
        A.Clip("climb", 48, gen=climb, step=2, loop=True),
        A.Clip("swim", 60, gen=swim, step=3, loop=True),
        A.Clip("roar", 120, gen=action(ROAR, roar_layer), step=2, ground=G),
        A.Clip("swipe_windup", 18, gen=action(SWIPE_WINDUP), step=1, ground=G),
        A.Clip("swipe", 24, gen=action(SWIPE, lambda p, t: tail(p, t, 6, 0.5, lash=bump(t, 1, 5, 1))), step=1, ground=G),
        A.Clip("grab_windup", 60, gen=action(GRAB_WINDUP, windup_layer), step=2, ground=G),
        A.Clip("grab_lunge", 24, gen=action(GRAB_LUNGE), step=1, ground=G),
        A.Clip("grab_hold", 66, gen=action(GRAB_HOLD, hold_layer), step=2, ground=G),
        A.Clip("slam", 36, gen=action(SLAM, slam_layer), step=1, ground=G),
        A.Clip("stun", 90, gen=action(STUN, stun_layer), step=2, ground=G),
        A.Clip("knock", 42, gen=action(KNOCK, lambda p, t: tail(p, t, 10, 0.5)), step=1, ground=G),
        A.Clip("tear", 60, gen=tear, step=2, ground=G),
        A.Clip("feed", 180, gen=feed, step=2, ground=G),
    ]


def build():
    return A.build("Guest", clips(), "pelvis", {"hand_r", "hand_l", "shin_r", "shin_l"}, 0.35)
