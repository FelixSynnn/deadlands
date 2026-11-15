"""The sub-agents' animations, keyed at 60 fps on the rig subagent_build.py makes (art/blender/sub_agent.blend).

  idle 60 (loop)   crouched, the body bobbing, the head ticking side to side, claws flexing, the tail flicking
  run 24 (loop)    a skitter: alternating tripods (front-right, back-left and the left claw together), fast and low
  attack 30        rears up, claws raised (9), slams down onto them (15: the hit), recovers
  death 45         flips onto its back, legs curling in, twitching, still
Legs splay sideways, so they step forward and back with y and lift with z (right: +z up, left: -z up).
"""
import math

import dl_anim as A

TAU = math.tau
BASE = {"body": (0, 0, 0), "head": (0.05, 0, 0), "claw_r": (0, 0, 0), "claw_l": (0, 0, 0), "leg_fr": (0, 0, 0),
        "leg_fl": (0, 0, 0), "leg_br": (0, 0, 0), "leg_bl": (0, 0, 0), "tail": (0, 0, 0)}
P = A.Poser(BASE)
pose, add, put, copy, jitter = P.pose, P.add, P.put, P.copy, P.jitter


def step(p, bone, side, swing, lift):
    s = -1.0 if side == "r" else 1.0
    add(p, **{bone: (0, s * swing, -s * lift)})


def idle(t):
    p = pose()
    b = math.sin(TAU * t / 30)
    add(p, body=(0.03 * b, 0, 0), claw_r=(-0.1 * (b + 1) * 0.5, 0, 0), claw_l=(-0.1 * (1 - b) * 0.5, 0, 0))
    tick = [0.0, 0.35, 0.35, -0.3, -0.3, 0.0][int(t // 10) % 6]
    add(p, head=(0, tick, tick * 0.4), tail=(0.1 * math.sin(TAU * t / 15), 0.3 * math.sin(TAU * t / 20), 0))
    p["loc"] = [0, -0.3 * b, 0]
    return p


def run(t):
    ph = TAU * t / 24
    s = math.sin(ph)
    lift_a = max(0.0, math.sin(ph)) * 0.5
    lift_b = max(0.0, -math.sin(ph)) * 0.5
    p = pose(body=(0.12, 0, 0), head=(-0.1, 0, 0))
    # Tripod A: front-right, back-left, left claw. Tripod B: front-left, back-right, right claw.
    step(p, "leg_fr", "r", 0.5 * s, lift_a)
    step(p, "leg_bl", "l", 0.5 * s, lift_a)
    step(p, "leg_fl", "l", -0.5 * s, lift_b)
    step(p, "leg_br", "r", -0.5 * s, lift_b)
    add(p, claw_l=(-0.5 * s - 0.2, 0, 0), claw_r=(0.5 * s - 0.2, 0, 0))
    add(p, body=(0, 0.08 * s, 0.05 * math.cos(ph * 2)), tail=(0.2, -0.3 * s, 0))
    p["head"][1] -= 0.08 * s
    p["loc"] = [0, -0.6 * abs(math.cos(ph)), 0]
    return p


def attack():
    rear = pose(body=(-0.55, 0, 0), head=(-0.3, 0, 0), claw_r=(-1.6, 0, 0.2), claw_l=(-1.6, 0, -0.2), tail=(0.4, 0, 0),
                leg_fr=(0, 0, 0.3), leg_fl=(0, 0, -0.3), loc=(0, -1.0, 1.0))
    slam = pose(body=(0.45, 0, 0), head=(0.3, 0, 0), claw_r=(0.6, 0, -0.1), claw_l=(0.6, 0, 0.1), tail=(-0.2, 0, 0),
                loc=(0, 0.5, -2.5))
    return [(0, pose()), (9, rear), (15, slam), (20, slam), (30, pose())]


def death():
    r = A.rng(5)
    curl = {"leg_fr": (0, 0, 0.9), "leg_fl": (0, 0, -0.9), "leg_br": (0, 0, 0.9), "leg_bl": (0, 0, -0.9),
            "claw_r": (-1.2, 0, 0), "claw_l": (-1.2, 0, 0)}
    flip = pose(body=(0, 0, 2.9), head=(0.4, 0, 0), tail=(0.3, 0.4, 0), loc=(0, -4.0, 0), **curl)
    keys = [(0, pose()), (9, pose(body=(-0.4, 0, 0.8), loc=(0, -3.0, 0))), (20, flip)]
    for f in range(24, 40, 3):
        keys.append((f, jitter(flip, r, 0.25, ["leg_fr", "leg_fl", "leg_br", "leg_bl", "claw_r", "claw_l"])))
    keys.append((45, flip))
    return keys


CLIPS = [
    A.Clip("idle", 60, gen=idle, step=3, loop=True, ground="contact"),
    A.Clip("run", 24, gen=run, step=2, loop=True, ground="contact"),
    A.Clip("attack", 30, keys=attack(), ground="contact"),
    A.Clip("death", 45, keys=death(), ground="floor"),
]


def build():
    return A.build("SubAgent", CLIPS, "body", {"leg_fr", "leg_fl", "leg_br", "leg_bl", "claw_r", "claw_l"}, 0.08)
