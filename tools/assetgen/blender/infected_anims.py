"""The infected's animations, keyed at 60 fps in Blender: the human-sized rig ("Infected": walkers, runners, screamers,
military, crawlers, bloaters, stalkers, charred) and the Brute ("Brute"). Built on dl_boxrig.from_java rigs.

Every cycle overlaps its motion: hips lead, the spine follows a beat later, the head and arms later still (drag and
follow-through), the jaw and loose arms swing on their own. Actions wind up, strike and settle.

Rig conventions (the game's own numbers): -x raises a limb forward, +x on a shin folds the knee back, +x on the torso
leans forward, +x on the head looks down, +z swings the right arm out and -z the left, +x on the jaw opens it. The root
bone is "body" (pivot at the hips); its "loc" is in pixels, y down. One tick is three frames.

Infected clips: idle, shamble, shamble_reach1 (the right arm reaching; the game mirrors it for the left),
shamble_reach (both reaching), limp (dragging the right leg; mirrored for the left), jog, sprint, crawl, crawl_idle,
crawl_attack, stalk, stalk_idle, climb, waddle, attack (33 frames, the hit at 15), scream (120), slam (66, the impact
at 36), pounce (48), breach (60, loops), emerge (84). Brute clips: idle, lumber, attack, slam, breach, scream.
"""
import math

import dl_anim as A

TAU = math.tau


def s(x):
    return math.sin(x)


def c(x):
    return math.cos(x)


# ================================================================================================= the human-sized rig
HB = {
    "body": (0, 0, 0), "torso": (0.1, 0, 0), "chest": (0.04, 0, 0), "head": (0.05, 0, 0), "jaw": (0.15, 0, 0),
    "right_arm": (-0.1, 0, 0.1), "right_forearm": (-0.2, 0, 0), "left_arm": (-0.08, 0, -0.1), "left_forearm": (-0.2, 0, 0),
    "right_leg": (0, 0, 0.03), "right_shin": (0.05, 0, 0), "left_leg": (0, 0, -0.03), "left_shin": (0.05, 0, 0),
    "right_claws": (-0.3, 0, 0), "left_claws": (-0.3, 0, 0), "front_tatter": (0, 0, 0), "back_tatter": (0, 0, 0),
}
P = A.Poser(HB)
pose, add, put, copy, jitter = P.pose, P.add, P.put, P.copy, P.jitter
UPPER = ["torso", "chest", "head", "jaw", "right_arm", "right_forearm", "left_arm", "left_forearm"]


def stride(ph, amp, knee, lift=0.0):
    """Thigh and shin for one leg at phase ph (0 = planted forward): the thigh swings, the knee folds as the foot
    comes through, and a little lift in the swing."""
    thigh = -c(ph) * amp
    swing = max(0.0, s(ph))            # the leg coming forward
    shin = 0.08 + swing * knee + max(0.0, -s(ph)) * 0.12
    return thigh - swing * lift, shin


def idle(t):
    p = pose()
    b = s(TAU * t / 80)
    w = s(TAU * t / 240)
    add(p, chest=(0.035 * b, 0, 0), torso=(0.01 * b, 0, 0), jaw=(0.1 + 0.1 * max(0, s(TAU * t / 50)), 0, 0))
    add(p, body=(0, 0, 0.04 * w), torso=(0, 0.05 * s(TAU * t / 160), -0.03 * w))
    p["loc"] = [0.4 * w, -0.15 * b, 0]
    # The head lolls: a slow roll and drift, then a violent twitch.
    add(p, head=(0.06 * s(TAU * t / 120 + 1), 0.25 * s(TAU * t / 240 + 0.5), 0.18 * s(TAU * t / 180)))
    if 150 <= t < 158:
        k = math.sin((t - 150) / 8 * math.pi)
        add(p, head=(0.15 * k, 0.45 * k * math.sin((t - 150) * 2.0), -0.3 * k), right_arm=(-0.15 * k, 0, 0.12 * k))
    # Arms hang and swing a little, a beat behind the body.
    add(p, right_arm=(0.06 * s(TAU * t / 240 - 0.8), 0, 0.03 * w), left_arm=(0.05 * s(TAU * t / 240 - 1.1), 0, 0.03 * w),
        right_forearm=(-0.05 * s(TAU * t / 120 - 1.5), 0, 0), left_forearm=(-0.05 * s(TAU * t / 120 - 1.7), 0, 0))
    return p


def shamble_base(t, n, reach):
    ph = TAU * t / n
    p = pose(torso=(0.18, 0, 0), chest=(0.06, 0, 0), head=(0.12, 0, 0))
    rl, rs = stride(ph, 0.55, 0.95, 0.1)
    ll, ls = stride(ph + math.pi, 0.55, 0.95, 0.1)
    add(p, right_leg=(rl, 0, 0), right_shin=(rs, 0, 0), left_leg=(ll, 0, 0), left_shin=(ls, 0, 0))
    # Weight over the planted leg, the hips rolling, the chest twisting against them a beat late.
    add(p, body=(0, -0.08 * c(ph), 0.08 * s(ph)), torso=(0.04 * abs(s(ph)), 0.12 * c(ph - 0.5), -0.05 * s(ph - 0.6)),
        chest=(0.03 * abs(c(ph)), 0.05 * c(ph - 0.9), 0))
    # The head lags and bobs, rolling loose.
    add(p, head=(0.08 * c(2 * ph - 1.2), -0.1 * c(ph - 1.0), -0.12 * s(ph - 1.3)), jaw=(0.12 + 0.1 * abs(s(ph - 1.5)), 0, 0))
    p["loc"] = [-0.5 * s(ph - 0.3), 0, 0]
    if reach == 0:
        add(p, right_arm=(0.38 * c(ph + math.pi - 0.6), 0, 0.05), left_arm=(0.38 * c(ph - 0.6), 0, -0.05),
            right_forearm=(-0.15 - 0.25 * max(0, s(ph - 1.2)), 0, 0), left_forearm=(-0.15 - 0.25 * max(0, -s(ph - 1.2)), 0, 0))
    else:
        put(p, right_arm=(-1.35 + 0.12 * c(ph - 0.8) + 0.06 * s(TAU * t / 37), 0.05, 0.08), right_forearm=(-0.25 - 0.1 * s(ph - 1.4), 0, 0))
        if reach == 2:
            put(p, left_arm=(-1.25 + 0.12 * c(ph + math.pi - 0.8) - 0.06 * s(TAU * t / 41), -0.05, -0.08),
                left_forearm=(-0.3 - 0.1 * s(ph + math.pi - 1.4), 0, 0))
        else:
            add(p, left_arm=(0.38 * c(ph - 0.6), 0, -0.05), left_forearm=(-0.15 - 0.25 * max(0, -s(ph - 1.2)), 0, 0))
    return p


def limp(t):
    n = 84
    ph = TAU * t / n
    p = pose(torso=(0.22, 0, 0.06), chest=(0.06, 0, 0), head=(0.12, 0, 0.15))
    # The right leg barely swings and drags its foot; the left takes long hurried steps; the body dips onto the right.
    rl, rs = stride(ph, 0.25, 0.25)
    ll, ls = stride(ph + math.pi, 0.65, 1.1, 0.15)
    add(p, right_leg=(rl, 0.25, 0.05), right_shin=(rs, 0, 0), left_leg=(ll, 0, 0), left_shin=(ls, 0, 0))
    dip = max(0.0, c(ph))
    add(p, body=(0, -0.1 * c(ph), 0.16 * dip - 0.04), torso=(0.08 * dip, 0.1 * c(ph - 0.6), -0.1 * dip),
        head=(0.1 * dip, -0.08 * c(ph - 1.0), -0.15 * dip), jaw=(0.15 + 0.15 * dip, 0, 0))
    add(p, right_arm=(0.25 * c(ph + math.pi - 0.6), 0, 0.12 + 0.1 * dip), left_arm=(0.45 * c(ph - 0.6), 0, -0.08),
        right_forearm=(-0.1, 0, 0), left_forearm=(-0.2 - 0.3 * max(0, -s(ph - 1.2)), 0, 0))
    p["loc"] = [-0.9 * dip + 0.3, 0, 0]
    return p


def jog(t):
    n = 48
    ph = TAU * t / n
    p = pose(torso=(0.22, 0, 0), chest=(0.08, 0, 0), head=(-0.05, 0, 0))
    rl, rs = stride(ph, 0.85, 1.2, 0.15)
    ll, ls = stride(ph + math.pi, 0.85, 1.2, 0.15)
    add(p, right_leg=(rl, 0, 0), right_shin=(rs, 0, 0), left_leg=(ll, 0, 0), left_shin=(ls, 0, 0))
    add(p, body=(0, -0.12 * c(ph), 0.05 * s(ph)), torso=(0, 0.15 * c(ph - 0.4), 0), chest=(0, 0.06 * c(ph - 0.8), -0.04 * s(ph)))
    put(p, right_arm=(-0.35 + 0.8 * c(ph + math.pi - 0.3), 0, 0.12), left_arm=(-0.35 + 0.8 * c(ph - 0.3), 0, -0.12),
        right_forearm=(-0.7 - 0.3 * max(0, s(ph)), 0, 0), left_forearm=(-0.7 - 0.3 * max(0, -s(ph)), 0, 0))
    # Twitchy: the head jerks on its own.
    jerk = 0.2 * s(t * 0.9) * s(t * 0.23)
    add(p, head=(0.06 * c(2 * ph - 1), jerk - 0.05 * c(ph - 1), 0.08 * s(t * 0.6)), jaw=(0.3, 0, 0))
    flight = max(0.0, c(TAU * (2 * t / n - 0.9))) ** 3
    p["loc"] = [-0.3 * s(ph), -0.8 * flight, 0]
    return p


def sprint(t):
    n = 30
    ph = TAU * t / n
    p = pose(torso=(0.5, 0, 0), chest=(0.15, 0, 0), head=(-0.45, 0, 0), jaw=(0.45, 0, 0))
    rl, rs = stride(ph, 1.2, 1.6, 0.2)
    ll, ls = stride(ph + math.pi, 1.2, 1.6, 0.2)
    add(p, right_leg=(rl, 0, 0), right_shin=(rs, 0, 0), left_leg=(ll, 0, 0), left_shin=(ls, 0, 0))
    add(p, body=(0, -0.15 * c(ph), 0.06 * s(ph)), torso=(0.05 * abs(c(ph)), 0.18 * c(ph - 0.3), 0), chest=(0, 0.08 * c(ph - 0.7), 0))
    put(p, right_arm=(-0.3 + 1.15 * c(ph + math.pi - 0.2), 0.1, 0.1), left_arm=(-0.3 + 1.15 * c(ph - 0.2), -0.1, -0.1),
        right_forearm=(-1.1 - 0.3 * max(0, s(ph)), 0, 0), left_forearm=(-1.1 - 0.3 * max(0, -s(ph)), 0, 0))
    add(p, head=(0.06 * c(2 * ph - 1.1), -0.04 * c(ph), 0))
    flight = max(0.0, c(TAU * (2 * t / n - 0.85))) ** 2
    p["loc"] = [-0.3 * s(ph), -1.6 * flight, 0]
    return p


def crawl_base():
    return pose(body=(math.pi / 2, 0, 0), head=(-1.15, 0, 0), jaw=(0.25, 0, 0), right_leg=(0, 0, 0.12), left_leg=(0, 0, -0.18),
                right_shin=(0.15, 0, 0), left_shin=(0.35, 0, 0), right_arm=(math.pi + 0.7, 0, -0.25), left_arm=(math.pi + 0.7, 0, 0.25),
                right_forearm=(-0.6, 0, 0), left_forearm=(-0.6, 0, 0), loc=(0, 9, 5))


def crawl(t):
    n = 60
    ph = TAU * t / n
    p = crawl_base()
    # Hand over hand: reach far, dig in, haul; the body slides and rolls toward the pulling arm, legs dragging.
    add(p, right_arm=(-0.65 * s(ph), 0.1 * c(ph), 0), left_arm=(-0.65 * s(ph + math.pi), -0.1 * c(ph), 0),
        right_forearm=(-0.4 * max(0, c(ph)), 0, 0), left_forearm=(-0.4 * max(0, -c(ph)), 0, 0),
        body=(0, 0.06 * s(ph), 0.1 * s(ph)), head=(0.08 * c(2 * ph), 0.08 * s(ph - 0.8), 0),
        right_leg=(0.12 * s(ph - 1), 0.08 * c(ph), 0), left_leg=(0.12 * s(ph + math.pi - 1), -0.08 * c(ph), 0),
        right_shin=(0.15 * max(0, s(ph - 1.5)), 0, 0), left_shin=(0.15 * max(0, -s(ph - 1.5)), 0, 0),
        jaw=(0.15 * abs(s(ph)), 0, 0))
    p["loc"] = [0.4 * s(ph), 9 - 0.3 * abs(c(ph)), 5 - 0.6 * c(2 * ph)]
    return p


def crawl_idle(t):
    p = crawl_base()
    b = s(TAU * t / 90)
    add(p, body=(0.03 * b, 0, 0.04 * s(TAU * t / 120)), head=(0.08 * s(TAU * t / 120), 0.2 * s(TAU * t / 200), 0.1 * s(TAU * t / 150)),
        right_forearm=(-0.15 * max(0, s(TAU * t / 60)), 0, 0), left_forearm=(-0.15 * max(0, s(TAU * t / 60 + 2)), 0, 0),
        jaw=(0.15 * max(0, s(TAU * t / 40)), 0, 0))
    return p


def crawl_attack():
    base = crawl_base()
    lunge = add(crawl_base(), right_arm=(-0.65, 0.05, 0.1), left_arm=(-0.4, -0.05, -0.1), head=(-0.3, 0, 0), jaw=(0.6, 0, 0), loc=(0, 0, -3))
    lunge["loc"] = [0, 8.5, 2]
    return [(0, base), (6, add(crawl_base(), right_arm=(0.3, 0, 0), head=(0.15, 0, 0))), (15, lunge), (20, lunge), (30, base)]


def stalk_base():
    return pose(torso=(0.6, 0, 0), chest=(0.2, 0, 0), head=(-0.8, 0, 0), jaw=(0.05, 0, 0),
                right_leg=(-0.75, 0, 0.1), left_leg=(-0.75, 0, -0.1), right_shin=(1.3, 0, 0), left_shin=(1.3, 0, 0),
                right_arm=(-1.0, 0, 0.12), left_arm=(-1.0, 0, -0.12), right_forearm=(-0.6, 0, 0), left_forearm=(-0.6, 0, 0),
                right_claws=(-0.5, 0, 0), left_claws=(-0.5, 0, 0))


def stalk(t):
    n = 48
    ph = TAU * t / n
    p = stalk_base()
    rl, rs = stride(ph, 0.5, 0.5)
    ll, ls = stride(ph + math.pi, 0.5, 0.5)
    add(p, right_leg=(rl, 0, 0), right_shin=(rs - 0.08, 0, 0), left_leg=(ll, 0, 0), left_shin=(ls - 0.08, 0, 0))
    # Low and fluid like a cat: shoulders rolling, head locked forward, claws placed ahead.
    add(p, body=(0, -0.06 * c(ph), 0.04 * s(ph)), torso=(0, 0.1 * c(ph - 0.5), 0.05 * s(ph - 0.5)),
        right_arm=(0.4 * c(ph + math.pi - 0.4), 0, 0.03 * s(ph)), left_arm=(0.4 * c(ph - 0.4), 0, -0.03 * s(ph)),
        right_forearm=(-0.25 * max(0, s(ph + math.pi)), 0, 0), left_forearm=(-0.25 * max(0, s(ph)), 0, 0))
    p["head"][1] -= 0.1 * c(ph - 0.5)
    p["head"][2] -= 0.05 * s(ph - 0.5)
    p["loc"] = [-0.3 * s(ph), 0, 0]
    return p


def stalk_idle(t):
    p = stalk_base()
    b = s(TAU * t / 70)
    add(p, chest=(0.04 * b, 0, 0), head=(0.04 * s(TAU * t / 90), 0.3 * s(TAU * t / 240), 0.25 * s(TAU * t / 170) ** 3),
        right_claws=(0.25 * s(TAU * t / 30), 0, 0), left_claws=(-0.25 * s(TAU * t / 34), 0, 0),
        right_arm=(0.05 * s(TAU * t / 110), 0, 0), left_arm=(0.05 * s(TAU * t / 110 + 1), 0, 0))
    return p


def climb(t):
    n = 40
    ph = TAU * t / n
    return pose(torso=(-0.1, 0, 0.05 * s(ph)), head=(-0.45, 0.1 * c(ph), 0),
                right_arm=(-2.6 + 0.55 * s(ph), 0, 0.15), left_arm=(-2.6 - 0.55 * s(ph), 0, -0.15),
                right_forearm=(-0.35 - 0.45 * max(0, c(ph)), 0, 0), left_forearm=(-0.35 - 0.45 * max(0, -c(ph)), 0, 0),
                right_leg=(-0.6 + 0.45 * s(ph), 0, 0.08), left_leg=(-0.6 - 0.45 * s(ph), 0, -0.08),
                right_shin=(0.8 + 0.3 * max(0, -s(ph)), 0, 0), left_shin=(0.8 + 0.3 * max(0, s(ph)), 0, 0),
                right_claws=(-0.7, 0, 0), left_claws=(-0.7, 0, 0), loc=(0.4 * s(ph), 0.3 * abs(c(ph)), 0))


def waddle(t):
    n = 72
    ph = TAU * t / n
    p = pose(torso=(-0.1, 0, 0), head=(0.18, 0, 0), right_arm=(-0.2, 0, 0.45), left_arm=(-0.2, 0, -0.45),
             right_forearm=(-0.3, 0, 0), left_forearm=(-0.3, 0, 0))
    rl, rs = stride(ph, 0.45, 0.45)
    ll, ls = stride(ph + math.pi, 0.45, 0.45)
    add(p, right_leg=(rl, 0, 0.12), right_shin=(rs, 0, 0), left_leg=(ll, 0, -0.12), left_shin=(ls, 0, 0))
    # The weight swings hard over each leg; the belly's mass drags the torso after it; arms flap for balance.
    add(p, body=(0, -0.06 * c(ph), 0.2 * s(ph)), torso=(0.04 * abs(c(ph)), 0.06 * c(ph - 0.6), -0.12 * s(ph - 0.7)),
        head=(0.05 * c(2 * ph - 1), 0, -0.12 * s(ph - 1.2)),
        right_arm=(0.2 * c(ph - 0.5), 0, 0.12 * max(0, s(ph - 0.9))), left_arm=(0.2 * c(ph + math.pi - 0.5), 0, -0.12 * max(0, -s(ph - 0.9))),
        jaw=(0.2 + 0.1 * abs(s(ph)), 0, 0))
    p["loc"] = [-1.0 * s(ph - 0.2), 0, 0]
    return p


def attack():
    r = A.rng(3)
    wind = pose(torso=(-0.05, 0.4, 0), chest=(0, 0.15, 0), head=(-0.15, -0.2, 0), jaw=(0.35, 0, 0),
                right_arm=(-2.3, 0.2, 0.5), right_forearm=(-1.0, 0, 0), left_arm=(-0.9, 0, -0.2), left_forearm=(-0.6, 0, 0),
                right_leg=(0.15, 0, 0.05), left_leg=(-0.3, 0, -0.05), right_claws=(-0.8, 0, 0), left_claws=(-0.8, 0, 0))
    hit = pose(torso=(0.45, -0.45, 0), chest=(0.15, -0.15, 0), head=(0.1, 0.25, 0), jaw=(0.7, 0, 0),
               right_arm=(-0.4, -0.2, -0.4), right_forearm=(-0.1, 0, 0), left_arm=(-1.5, 0, -0.1), left_forearm=(-0.4, 0, 0),
               right_leg=(-0.5, 0, 0.05), left_leg=(0.35, 0, -0.05), right_shin=(0.2, 0, 0), left_shin=(0.4, 0, 0),
               right_claws=(0.2, 0, 0), left_claws=(0.1, 0, 0), loc=(0, 0.8, -2.0))
    follow = put(copy(hit), right_arm=(0.35, -0.2, -0.3), left_arm=(-0.5, 0, -0.2), torso=(0.5, -0.55, 0))
    return [(0, pose()), (10, wind), (12, jitter(wind, r, 0.04, UPPER)), (15, hit), (21, follow), (33, pose())]


def scream():
    r = A.rng(5)
    draw = pose(torso=(0.55, 0, 0), chest=(0.3, 0, 0), head=(0.4, 0, 0), jaw=(0.0, 0, 0),
                right_arm=(-0.3, 0, -0.1), left_arm=(-0.3, 0, 0.1), right_forearm=(-1.2, 0, 0), left_forearm=(-1.2, 0, 0))
    up = pose(torso=(-0.25, 0, 0), chest=(-0.3, 0, 0), head=(-0.9, 0, 0), jaw=(1.0, 0, 0),
              right_arm=(-0.6, 0, 1.1), left_arm=(-0.6, 0, -1.1), right_forearm=(-0.5, 0, 0), left_forearm=(-0.5, 0, 0),
              right_leg=(0, 0, 0.12), left_leg=(0, 0, -0.12), right_claws=(0.4, 0, 0), left_claws=(0.4, 0, 0), loc=(0, -0.6, 0))
    keys = [(0, pose()), (15, draw), (27, up)]
    for f in range(30, 96, 3):
        keys.append((f, jitter(up, r, 0.07, UPPER)))
    keys += [(100, add(copy(up), head=(0.2, 0, 0), jaw=(-0.2, 0, 0))), (110, pose(torso=(0.4, 0, 0), head=(0.2, 0, 0))), (120, pose())]
    return keys


def slam():
    over = pose(torso=(-0.3, 0, 0), chest=(-0.2, 0, 0), head=(-0.3, 0, 0), jaw=(0.4, 0, 0),
                right_arm=(-2.9, 0, 0.2), left_arm=(-2.9, 0, -0.2), right_forearm=(-0.6, 0, 0), left_forearm=(-0.6, 0, 0),
                loc=(0, -0.8, 0.8))
    down = pose(torso=(0.75, 0, 0), chest=(0.25, 0, 0), head=(0.1, 0, 0), jaw=(0.6, 0, 0),
                right_arm=(-0.9, 0, -0.1), left_arm=(-0.9, 0, 0.1), right_forearm=(-0.1, 0, 0), left_forearm=(-0.1, 0, 0),
                right_leg=(-0.5, 0, 0.1), left_leg=(-0.5, 0, -0.1), right_shin=(0.7, 0, 0), left_shin=(0.7, 0, 0), loc=(0, 1.5, -1.5))
    return [(0, pose()), (18, A.Poser.copy(over)), (24, add(copy(over), head=(-0.1, 0, 0))), (36, down),
            (42, add(copy(down), torso=(0.08, 0, 0))), (66, pose())]


def pounce():
    coil = pose(torso=(0.7, 0, 0), chest=(0.2, 0, 0), head=(-0.8, 0, 0), right_leg=(-1.1, 0, 0.12), left_leg=(-1.1, 0, -0.12),
                right_shin=(1.7, 0, 0), left_shin=(1.7, 0, 0), right_arm=(0.6, 0, 0.3), left_arm=(0.6, 0, -0.3),
                right_forearm=(-0.6, 0, 0), left_forearm=(-0.6, 0, 0))
    leap = pose(torso=(0.3, 0, 0), head=(-0.4, 0, 0), jaw=(0.8, 0, 0), right_arm=(-2.4, 0, 0.15), left_arm=(-2.4, 0, -0.15),
                right_forearm=(-0.1, 0, 0), left_forearm=(-0.1, 0, 0), right_leg=(0.8, 0, 0), left_leg=(0.6, 0, 0),
                right_shin=(0.5, 0, 0), left_shin=(0.7, 0, 0), right_claws=(0.3, 0, 0), left_claws=(0.3, 0, 0))
    land = pose(torso=(0.6, 0, 0), head=(-0.4, 0, 0), jaw=(0.5, 0, 0), right_arm=(-1.2, 0, 0.2), left_arm=(-1.2, 0, -0.2),
                right_leg=(-0.9, 0, 0.12), left_leg=(-0.5, 0, -0.12), right_shin=(1.3, 0, 0), left_shin=(1.0, 0, 0))
    return [(0, pose()), (12, coil), (16, add(copy(coil), torso=(0.05, 0, 0))), (22, leap), (34, leap), (42, land), (48, pose())]


def breach(t):
    n = 60
    ph = TAU * t / n
    # Pounding on a door: alternating overhand blows, the body throwing its weight into each.
    p = pose(torso=(0.3, 0, 0), chest=(0.1, 0, 0), head=(0.1, 0, 0), jaw=(0.4 + 0.2 * abs(s(ph)), 0, 0),
             right_leg=(-0.3, 0, 0.08), left_leg=(0.2, 0, -0.08), right_shin=(0.35, 0, 0), left_shin=(0.15, 0, 0))
    hr = max(0.0, s(ph)) ** 0.6
    hl = max(0.0, -s(ph)) ** 0.6
    put(p, right_arm=(-1.2 - 1.2 * hr + 0.9 * max(0, -c(ph)) * (1 if s(ph) > 0 else 0), 0, 0.15), right_forearm=(-0.8 + 0.6 * hr, 0, 0),
        left_arm=(-1.2 - 1.2 * hl, 0, -0.15), left_forearm=(-0.8 + 0.6 * hl, 0, 0))
    add(p, torso=(0.15 * abs(c(ph)), 0.25 * s(ph), 0), head=(0.1 * abs(c(ph)), -0.1 * s(ph), 0))
    p["loc"] = [0, 0.4 * abs(c(ph)), -0.6 * abs(c(ph))]
    return p


def emerge():
    r = A.rng(9)
    under = pose(torso=(0.9, 0, 0), chest=(0.3, 0, 0), head=(-0.6, 0, 0), jaw=(0.5, 0, 0),
                 right_arm=(-2.8, 0, 0.3), left_arm=(-2.5, 0, -0.3), right_forearm=(-0.3, 0, 0), left_forearm=(-0.3, 0, 0),
                 right_leg=(-1.4, 0, 0.1), left_leg=(-1.4, 0, -0.1), right_shin=(2.2, 0, 0), left_shin=(2.2, 0, 0), loc=(0, 9, 0))
    claw = put(copy(under), right_arm=(-1.2, 0, 0.3), left_arm=(-2.6, 0, -0.3), torso=(0.7, 0.2, 0))
    claw["loc"] = [0, 7, 0]
    knees = pose(torso=(0.5, 0, 0), head=(-0.4, 0, 0), jaw=(0.4, 0, 0), right_arm=(-0.6, 0, 0.2), left_arm=(-0.9, 0, -0.2),
                 right_leg=(-1.2, 0, 0.1), left_leg=(-0.6, 0, -0.1), right_shin=(1.6, 0, 0), left_shin=(1.0, 0, 0), loc=(0, 4, 0))
    keys = [(0, under), (12, jitter(under, r, 0.05, UPPER)), (24, claw), (36, put(copy(claw), left_arm=(-1.0, 0, -0.3))),
            (54, knees), (66, jitter(knees, r, 0.04, UPPER)), (84, pose())]
    return keys


INFECTED = [
    A.Clip("idle", 240, gen=idle, step=3, loop=True, ground="contact"),
    A.Clip("shamble", 72, gen=lambda t: shamble_base(t, 72, 0), step=3, loop=True, ground="contact"),
    A.Clip("shamble_reach1", 72, gen=lambda t: shamble_base(t, 72, 1), step=3, loop=True, ground="contact"),
    A.Clip("shamble_reach", 72, gen=lambda t: shamble_base(t, 72, 2), step=3, loop=True, ground="contact"),
    A.Clip("limp", 84, gen=limp, step=3, loop=True, ground="contact"),
    A.Clip("jog", 48, gen=jog, step=2, loop=True, ground="contact"),
    A.Clip("sprint", 30, gen=sprint, step=2, loop=True, ground="contact"),
    A.Clip("crawl", 60, gen=crawl, step=3, loop=True),
    A.Clip("crawl_idle", 120, gen=crawl_idle, step=3, loop=True),
    A.Clip("crawl_attack", 30, keys=crawl_attack()),
    A.Clip("stalk", 48, gen=stalk, step=2, loop=True, ground="contact"),
    A.Clip("stalk_idle", 120, gen=stalk_idle, step=3, loop=True, ground="contact"),
    A.Clip("climb", 40, gen=climb, step=2, loop=True),
    A.Clip("waddle", 72, gen=waddle, step=3, loop=True, ground="contact"),
    A.Clip("attack", 33, keys=attack(), full=False, ground="contact"),
    A.Clip("scream", 120, keys=scream(), ground="contact"),
    A.Clip("slam", 66, keys=slam(), ground="contact"),
    A.Clip("pounce", 48, keys=pounce(), ground="floor"),
    A.Clip("breach", 60, gen=breach, step=3, loop=True, ground="contact"),
    A.Clip("emerge", 84, keys=emerge()),
]


# ================================================================================================= the Brute
BB = {"body": (0, 0, 0), "torso": (0.25, 0, 0), "chest": (0.1, 0, 0), "head": (-0.15, 0, 0), "jaw": (0.15, 0, 0),
      "right_arm": (0.05, 0, 0.15), "right_forearm": (-0.25, 0, 0), "left_arm": (0.05, 0, -0.15), "left_forearm": (-0.3, 0, 0),
      "right_leg": (0, 0, 0.08), "right_shin": (0.08, 0, 0), "left_leg": (0, 0, -0.08), "left_shin": (0.08, 0, 0)}
Q = A.Poser(BB)
BUP = ["torso", "chest", "head", "jaw", "right_arm", "right_forearm", "left_arm", "left_forearm"]


def b_idle(t):
    p = Q.pose()
    b = s(TAU * t / 100)
    w = s(TAU * t / 200)
    Q.add(p, chest=(0.05 * b, 0, 0), torso=(0.02 * b, 0.06 * s(TAU * t / 200 + 1), -0.03 * w), body=(0, 0, 0.03 * w),
          head=(0.05 * s(TAU * t / 100 - 1), 0.3 * s(TAU * t / 200 + 0.4), 0.06 * w), jaw=(0.12 * max(0, s(TAU * t / 66)), 0, 0),
          right_arm=(0.07 * s(TAU * t / 200 - 0.9), 0, 0.03 * b), left_arm=(0.07 * s(TAU * t / 200 - 1.2), 0, -0.03 * b),
          right_forearm=(-0.08 * s(TAU * t / 100 - 1.6), 0, 0), left_forearm=(-0.08 * s(TAU * t / 100 - 1.8), 0, 0))
    p["loc"] = [0.5 * w, -0.3 * b, 0]
    return p


def b_lumber(t):
    n = 90
    ph = TAU * t / n
    p = Q.pose(torso=(0.3, 0, 0))
    rl, rs = stride(ph, 0.6, 0.6)
    ll, ls = stride(ph + math.pi, 0.6, 0.6)
    Q.add(p, right_leg=(rl, 0, 0), right_shin=(rs, 0, 0), left_leg=(ll, 0, 0), left_shin=(ls, 0, 0))
    # Every step lands like a dropped weight: the whole frame rolls onto it, the shoulders and the long arms swinging
    # after.
    Q.add(p, body=(0, -0.1 * c(ph), 0.1 * s(ph)), torso=(0.05 * abs(s(ph)), 0.14 * c(ph - 0.5), -0.07 * s(ph - 0.6)),
          chest=(0.04 * abs(c(ph)), 0.06 * c(ph - 0.9), 0), head=(0.06 * c(2 * ph - 1.3), -0.12 * c(ph - 1.0), -0.06 * s(ph - 1.2)),
          right_arm=(0.5 * c(ph + math.pi - 0.7), 0, 0.06 * max(0, s(ph))), left_arm=(0.5 * c(ph - 0.7), 0, -0.06 * max(0, -s(ph))),
          right_forearm=(-0.25 * max(0, s(ph - 1.3)), 0, 0), left_forearm=(-0.25 * max(0, -s(ph - 1.3)), 0, 0),
          jaw=(0.1 * abs(s(ph - 1.5)), 0, 0))
    p["loc"] = [-0.8 * s(ph - 0.3), 0, 0]
    return p


def b_attack():
    wind = Q.pose(torso=(0.05, 0.55, 0), chest=(0, 0.2, 0), head=(-0.2, -0.3, 0), right_arm=(-1.9, 0.3, 0.6), right_forearm=(-1.3, 0, 0),
                  left_arm=(-0.4, 0, -0.3), right_leg=(0.2, 0, 0.08), left_leg=(-0.3, 0, -0.08), jaw=(0.3, 0, 0))
    hit = Q.pose(torso=(0.55, -0.55, 0), chest=(0.2, -0.2, 0), head=(0.0, 0.3, 0), right_arm=(-1.4, -0.3, -0.3), right_forearm=(-0.1, 0, 0),
                 left_arm=(0.3, 0, -0.2), right_leg=(-0.5, 0, 0.08), left_leg=(0.4, 0, -0.08), jaw=(0.6, 0, 0), loc=(0, 1.0, -2.0))
    return [(0, Q.pose()), (12, wind), (15, Q.add(Q.copy(wind), torso=(0, 0.05, 0))), (19, hit), (25, Q.add(Q.copy(hit), torso=(0.05, -0.05, 0))),
            (33, Q.pose())]


def b_slam():
    over = Q.pose(torso=(-0.35, 0, 0), chest=(-0.2, 0, 0), head=(-0.4, 0, 0), jaw=(0.6, 0, 0), right_arm=(-3.0, 0, 0.3), left_arm=(-3.0, 0, -0.3),
                  right_forearm=(-0.7, 0, 0), left_forearm=(-0.7, 0, 0), loc=(0, -1.2, 1.0))
    down = Q.pose(torso=(0.95, 0, 0), chest=(0.3, 0, 0), head=(0.0, 0, 0), jaw=(0.7, 0, 0), right_arm=(-1.2, 0, 0.05), left_arm=(-1.2, 0, -0.05),
                  right_forearm=(0.0, 0, 0), left_forearm=(0.0, 0, 0), right_leg=(-0.7, 0, 0.15), left_leg=(-0.7, 0, -0.15),
                  right_shin=(1.0, 0, 0), left_shin=(1.0, 0, 0), loc=(0, 2.5, -2.0))
    return [(0, Q.pose()), (20, over), (26, Q.add(Q.copy(over), torso=(-0.05, 0, 0))), (36, down), (44, Q.add(Q.copy(down), torso=(0.06, 0, 0))),
            (66, Q.pose())]


def b_breach(t):
    ph = TAU * t / 60
    p = Q.pose(torso=(0.4, 0, 0), right_leg=(-0.3, 0, 0.1), left_leg=(0.25, 0, -0.1), right_shin=(0.4, 0, 0))
    hit = max(0.0, s(ph)) ** 0.5
    Q.put(p, right_arm=(-1.0 - 1.6 * hit, 0, 0.2), left_arm=(-1.0 - 1.6 * hit, 0, -0.2), right_forearm=(-0.6 + 0.5 * hit, 0, 0),
          left_forearm=(-0.6 + 0.5 * hit, 0, 0))
    Q.add(p, torso=(-0.25 * hit + 0.3 * max(0, -s(ph)), 0, 0), head=(-0.2 * hit, 0, 0), jaw=(0.3 + 0.3 * hit, 0, 0))
    p["loc"] = [0, 0.6 * (1 - hit), -1.2 * max(0, -s(ph))]
    return p


def b_scream():
    r = A.rng(11)
    up = Q.pose(torso=(-0.3, 0, 0), chest=(-0.35, 0, 0), head=(-0.9, 0, 0), jaw=(1.0, 0, 0), right_arm=(-0.8, 0, 1.0), left_arm=(-0.8, 0, -1.0),
                right_forearm=(-0.9, 0, 0), left_forearm=(-0.9, 0, 0), loc=(0, -0.8, 0))
    keys = [(0, Q.pose()), (15, Q.pose(torso=(0.6, 0, 0), head=(0.3, 0, 0), right_forearm=(-1.3, 0, 0), left_forearm=(-1.3, 0, 0))), (27, up)]
    for f in range(30, 96, 3):
        keys.append((f, Q.jitter(up, r, 0.06, BUP)))
    keys += [(108, Q.pose(torso=(0.45, 0, 0))), (120, Q.pose())]
    return keys


BRUTE = [
    A.Clip("idle", 200, gen=b_idle, step=3, loop=True, ground="contact"),
    A.Clip("lumber", 90, gen=b_lumber, step=3, loop=True, ground="contact"),
    A.Clip("attack", 33, keys=b_attack(), full=False, ground="contact"),
    A.Clip("slam", 66, keys=b_slam(), ground="contact"),
    A.Clip("breach", 60, gen=b_breach, step=3, loop=True, ground="contact"),
    A.Clip("scream", 120, keys=b_scream(), ground="contact"),
]


def build_infected():
    return A.build("Infected", INFECTED, "body", {"right_shin", "left_shin", "right_leg", "left_leg"}, 0.08)


def build_brute():
    return A.build("Brute", BRUTE, "body", {"right_shin", "left_shin", "right_leg", "left_leg"}, 0.08)
