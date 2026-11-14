"""The story people's animations (Harley, Adam, FelixSyn, the survivors...), keyed at 60 fps in Blender on Minecraft's
player model (dl_boxrig.player): head, body, arms, legs (no elbows or knees: the model has none).

  idle 240 (loop)   breathing, the weight shifting from foot to foot, a glance to one side and back, hands settling
  walk 40 (loop)    a relaxed walk: arms swinging against the legs a beat late, the body turning with the hips, the
                    head steady, a little bob
  run 28 (loop)     leaning in, arms pumping hard, a longer stride, the body rising and dropping
  look 120          a wary look over one shoulder (played now and then while idle)

Conventions: -x swings a limb forward; +z moves the right arm out, -z the left. "body" carries the offset (the game
moves the whole figure by it). One tick is three frames.
"""
import math

import dl_anim as A

TAU = math.tau
B = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (0.03, 0, 0.06), "left_arm": (0.03, 0, -0.06),
     "right_leg": (0, 0, 0.02), "left_leg": (0, 0, -0.02)}
P = A.Poser(B)


def s(x):
    return math.sin(x)


def c(x):
    return math.cos(x)


def idle(t):
    p = P.pose()
    b = s(TAU * t / 80)
    w = s(TAU * t / 240)
    glance = A.smooth((t - 90) / 12) * (1 - A.smooth((t - 150) / 14))
    P.add(p, body=(0.015 * b, 0.04 * w, 0.025 * w), head=(-0.02 * b + 0.04 * s(TAU * t / 120), 0.45 * glance + 0.06 * s(TAU * t / 240 + 1), 0.04 * glance),
          right_arm=(0.04 * s(TAU * t / 120 - 0.6), 0, 0.02 * b), left_arm=(0.04 * s(TAU * t / 120 - 0.9), 0, -0.02 * b),
          right_leg=(0, 0, 0.04 * max(0, w)), left_leg=(0, 0, 0.04 * max(0, -w)))
    p["loc"] = [0.35 * w, -0.1 * b, 0]
    return p


def walk(t):
    ph = TAU * t / 40
    p = P.pose()
    P.add(p, right_leg=(-0.62 * c(ph), 0, 0), left_leg=(0.62 * c(ph), 0, 0),
          right_arm=(0.55 * c(ph - 0.35), 0, 0.04 * max(0, s(ph))), left_arm=(-0.55 * c(ph - 0.35), 0, -0.04 * max(0, -s(ph))),
          body=(0.04 + 0.02 * abs(s(ph)), 0.09 * c(ph - 0.2), 0.03 * s(ph)), head=(0.03 * c(2 * ph - 1.2), -0.08 * c(ph - 0.4), -0.02 * s(ph - 0.5)))
    p["loc"] = [-0.25 * s(ph), 0, 0]
    return p


def run(t):
    ph = TAU * t / 28
    p = P.pose()
    P.add(p, right_leg=(-1.0 * c(ph), 0, 0), left_leg=(1.0 * c(ph), 0, 0),
          right_arm=(1.0 * c(ph - 0.25) - 0.15, 0.05, 0.08), left_arm=(-1.0 * c(ph - 0.25) - 0.15, -0.05, -0.08),
          body=(0.22 + 0.04 * abs(s(ph)), 0.14 * c(ph - 0.15), 0.04 * s(ph)), head=(-0.15 + 0.05 * c(2 * ph - 1.1), -0.12 * c(ph - 0.3), 0))
    flight = max(0.0, c(TAU * (2 * t / 28 - 0.85))) ** 2
    p["loc"] = [-0.3 * s(ph), -1.0 * flight, 0]
    return p


def look():
    over = P.pose(head=(0.05, 0.95, 0.1), body=(0, 0.35, 0), right_arm=(0.1, 0, 0.12), left_arm=(-0.05, 0, -0.04))
    return [(0, P.pose()), (24, over), (36, P.add(P.copy(over), head=(0.08, 0.1, 0))), (72, over), (96, P.pose(head=(0, -0.1, 0))), (120, P.pose())]


CLIPS = [
    A.Clip("idle", 240, gen=idle, step=3, loop=True, ground="contact"),
    A.Clip("walk", 40, gen=walk, step=2, loop=True, ground="contact"),
    A.Clip("run", 28, gen=run, step=2, loop=True, ground="contact"),
    A.Clip("look", 120, keys=look(), ground="contact"),
]


def build():
    return A.build("Npc", CLIPS, "body", {"right_leg", "left_leg"}, 0.08)
