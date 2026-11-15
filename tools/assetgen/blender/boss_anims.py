"""The five bosses' animations, keyed at 60 fps in Blender on their box rigs (dl_boxrig.from_java on BossGeometry).

Every boss: idle 180 (loop), walk 72 (loop, one stride), swing 48 (the blow on tick 8 = frame 24), roar 90 (peak at
tick 15). Then each its own:
  Abomination   slam 90 (impact tick 12 = frame 36), throw 72 (release tick 12); its extra heads and arms writhe
  Butcher       hook 78 (the chain flies at tick 14), call 90 (tick 15)
  Warden        bash 60 (shield, tick 10), pound 78 (stamp, tick 13)
  Experiment    leap 90 (springs at tick 24... airborne), spit 60 (tick 10); a crouched, twitching lope
  Patient Zero  summon 90 (tick 15), blink 36 (tick 8); its back tendrils never stop moving

The game plays each attack by its progress (0 to 1 across the attack's ticks), so the clip lengths match: a swing is
16 ticks = 48 frames, and so on. Conventions: -x raises a limb forward, +x on the body leans forward, +z swings the
right arm out and -z the left; the root bone "root" carries the offset (pixels, y down).
"""
import math

import dl_anim as A

TAU = math.tau


def s(x):
    return math.sin(x)


def c(x):
    return math.cos(x)


BASES = {
    "abomination": {"body": (0.15, 0, 0), "head": (0.05, 0, 0.1), "head2": (0.1, 0, -0.15), "head3": (0.2, 0, 0.3),
                    "arm_r": (0.05, 0, 0.12), "arm_l": (0.05, 0, -0.12), "arm_r2": (-0.3, 0, 0.2), "arm_l2": (-0.4, 0, -0.2)},
    "butcher": {"body": (0.08, 0, 0), "head": (-0.05, 0, 0), "arm_r": (-0.1, 0, 0.1), "arm_l": (0.0, 0, -0.1), "cleaver": (0.2, 0, 0)},
    "warden": {"body": (0.0, 0, 0), "head": (0.0, 0, 0), "arm_r": (-0.05, 0, 0.08), "arm_l": (-0.5, 0.3, -0.1)},
    "experiment": {"body": (0.45, 0, 0), "head": (-0.35, 0, 0), "jaw": (0.2, 0, 0), "arm_r": (-0.3, 0, 0.15), "arm_l": (-0.3, 0, -0.15),
                   "leg_r": (-0.2, 0, 0.08), "leg_l": (-0.2, 0, -0.08)},
    "patient_zero": {"body": (0.05, 0, 0), "head": (0.0, 0, 0), "arm_r": (0.0, 0, 0.1), "arm_l": (0.0, 0, -0.1),
                     "tendril_1": (-0.3, -0.3, 0), "tendril_2": (-0.3, 0.3, 0), "tendril_3": (-0.2, -0.3, 0), "tendril_4": (-0.2, 0.3, 0)},
}


def poser(kind):
    base = {"root": (0, 0, 0), "leg_r": (0, 0, 0), "leg_l": (0, 0, 0)}
    base.update(BASES[kind])
    return A.Poser(base)


def extras(kind, p, t, add, amount=1.0):
    """The things that never keep still: the Abomination's other heads and arms, Patient Zero's tendrils, jaws."""
    if kind == "abomination":
        add(p, head2=(0.15 * s(TAU * t / 70) * amount, 0.2 * s(TAU * t / 110), 0), head3=(0.2 * s(TAU * t / 50 + 1) * amount, 0, 0.15 * s(TAU * t / 90)),
            arm_r2=(0.5 * s(TAU * t / 40) * amount, 0, 0.2 * s(TAU * t / 64)), arm_l2=(0.5 * s(TAU * t / 46 + 2) * amount, 0, -0.2 * s(TAU * t / 58)))
    elif kind == "patient_zero":
        for i in range(4):
            add(p, **{f"tendril_{i + 1}": (0.4 * c(TAU * t / (66 + i * 9) + i), 0.55 * s(TAU * t / (54 + i * 7) + i * 1.3), 0.2 * s(TAU * t / 80 + i))})
    elif kind == "experiment":
        add(p, jaw=(0.15 * max(0, s(TAU * t / 18)), 0, 0))


def idle(kind):
    P = poser(kind)

    def gen(t):
        p = P.pose()
        b = s(TAU * t / 90)
        w = s(TAU * t / 180)
        P.add(p, body=(0.035 * b, 0.05 * w, 0.03 * w), head=(-0.03 * b, 0.3 * s(TAU * t / 180 + 0.7), 0.05 * w),
              arm_r=(0.05 * s(TAU * t / 180 - 0.8), 0, 0.03 * b), arm_l=(0.05 * s(TAU * t / 180 - 1.1), 0, -0.03 * b))
        if kind == "experiment":
            twitch = math.sin(t * 0.7) * math.sin(t * 0.17)
            P.add(p, head=(0.1 * twitch, 0.25 * twitch, 0.15 * twitch))
        extras(kind, p, t, P.add)
        p["loc"] = [0.4 * w, -0.25 * b, 0]
        return p
    return gen


def walk(kind):
    P = poser(kind)
    amp = {"abomination": 0.45, "butcher": 0.6, "warden": 0.55, "experiment": 0.8, "patient_zero": 0.6}[kind]

    def gen(t):
        ph = TAU * t / 72
        p = P.pose()
        P.add(p, leg_r=(-amp * c(ph), 0, 0), leg_l=(amp * c(ph), 0, 0),
              body=(0.04 * abs(s(ph)), 0.1 * c(ph - 0.4), 0.06 * s(ph)), head=(0.04 * c(2 * ph - 1.2), -0.1 * c(ph - 0.6), -0.05 * s(ph - 0.7)),
              arm_r=(amp * 0.6 * c(ph - 0.5), 0, 0.04 * max(0, s(ph))), arm_l=(-amp * 0.6 * c(ph - 0.5), 0, -0.04 * max(0, -s(ph))))
        extras(kind, p, t, P.add, 1.4)
        p["loc"] = [-0.6 * s(ph - 0.3), 0, 0]
        return p
    return gen


def swing(kind):
    P = poser(kind)
    wind = P.pose(arm_r=(-2.6, 0.2, 0.5), body=(-0.05, 0.4, 0), head=(-0.1, -0.3, 0), arm_l=(0.3, 0, -0.2))
    hit = P.pose(arm_r=(-0.3, -0.3, -0.4), body=(0.35, -0.45, 0), head=(0.05, 0.3, 0), arm_l=(-0.6, 0, -0.1),
                 leg_r=(-0.4, 0, 0), leg_l=(0.3, 0, 0), loc=(0, 0.6, -1.5))
    if kind == "warden":
        hit = P.put(hit, arm_l=(-0.9, 0.5, -0.1))
    return [(0, P.pose()), (18, wind), (21, P.add(P.copy(wind), body=(-0.04, 0.05, 0))), (24, hit), (32, P.add(P.copy(hit), arm_r=(0.3, 0, 0))), (48, P.pose())]


def roar(kind):
    P = poser(kind)
    r = A.rng(3)
    draw = P.pose(body=(0.4, 0, 0), head=(0.3, 0, 0), arm_r=(-0.4, 0, -0.2), arm_l=(-0.4, 0, 0.2))
    up = P.pose(body=(-0.3, 0, 0), head=(-0.8, 0, 0), jaw=(0.9, 0, 0), arm_r=(-0.6, 0, 1.1), arm_l=(-0.6, 0, -1.1), loc=(0, -1.0, 0))
    if kind == "warden":
        up = P.put(up, arm_l=(-1.3, 0.4, -0.6))
    keys = [(0, P.pose()), (24, draw), (42, up)]
    for f in range(45, 75, 3):
        q = P.jitter(up, r, 0.06, ["body", "head", "arm_r", "arm_l"])
        extras(kind, q, f * 2, P.add, 2.0)
        keys.append((f, q))
    keys += [(90, P.pose())]
    return keys


def specials(kind):
    P = poser(kind)
    r = A.rng(7)
    out = []
    if kind == "abomination":
        over = P.pose(body=(-0.35, 0, 0), head=(-0.3, 0, 0), arm_r=(-3.0, 0, 0.25), arm_l=(-3.0, 0, -0.25), loc=(0, -1.5, 1.5))
        down = P.pose(body=(0.75, 0, 0), head=(0.1, 0, 0), arm_r=(-1.1, 0, 0.0), arm_l=(-1.1, 0, 0.0), leg_r=(-0.6, 0, 0.1), leg_l=(-0.6, 0, -0.1),
                      loc=(0, 3.5, -3.0))
        extras(kind, down, 0, P.add, 3.0)
        out.append(A.Clip("slam", 90, keys=[(0, P.pose()), (27, over), (31, P.add(P.copy(over), body=(-0.05, 0, 0))), (36, down),
                                            (44, P.add(P.copy(down), body=(0.06, 0, 0))), (90, P.pose())], ground="contact"))
        lift = P.pose(body=(-0.2, 0.3, 0), arm_r=(-1.0, 0.4, 0.8), arm_l=(-1.0, -0.4, -0.8), head=(-0.2, -0.2, 0))
        heave = P.pose(body=(0.5, -0.4, 0), arm_r=(-2.2, -0.2, 0.1), arm_l=(-2.2, 0.2, -0.1), head=(0.1, 0.3, 0), leg_r=(-0.4, 0, 0), loc=(0, 1.0, -2.0))
        out.append(A.Clip("throw", 72, keys=[(0, P.pose()), (24, lift), (30, P.add(P.copy(lift), body=(-0.05, 0, 0))), (36, heave), (48, heave), (72, P.pose())],
                          ground="contact"))
    elif kind == "butcher":
        wind = P.pose(arm_l=(-2.5, 0, -0.6), body=(-0.1, -0.4, 0), head=(-0.1, 0.2, 0), arm_r=(0.3, 0, 0.2))
        throw = P.pose(arm_l=(-1.6, -0.4, 0.1), body=(0.4, 0.45, 0), head=(0.05, -0.2, 0), arm_r=(-0.3, 0, 0.3), leg_l=(-0.5, 0, 0), leg_r=(0.3, 0, 0),
                       loc=(0, 0.8, -1.5))
        yank = P.pose(arm_l=(0.4, 0, -0.2), body=(-0.15, 0.2, 0), head=(-0.1, 0, 0), leg_l=(0.3, 0, 0), leg_r=(-0.2, 0, 0), loc=(0, 0, 1.0))
        out.append(A.Clip("hook", 78, keys=[(0, P.pose()), (30, wind), (38, P.add(P.copy(wind), arm_l=(-0.1, 0, 0))), (42, throw), (54, throw), (66, yank),
                                            (78, P.pose())], ground="contact"))
        call = P.pose(body=(-0.25, 0, 0), head=(-0.7, 0, 0), arm_r=(-2.8, 0, 0.3), arm_l=(-0.4, 0, -0.6), loc=(0, -0.8, 0))
        keys = [(0, P.pose()), (24, P.pose(body=(0.3, 0, 0), head=(0.3, 0, 0))), (45, call)]
        for f in range(48, 72, 3):
            keys.append((f, P.jitter(call, r, 0.05, ["body", "head", "arm_r", "arm_l"])))
        keys.append((90, P.pose()))
        out.append(A.Clip("call", 90, keys=keys, ground="contact"))
    elif kind == "warden":
        guard = P.pose(arm_l=(-1.3, 0.6, -0.2), body=(0.1, 0.3, 0), leg_l=(-0.3, 0, 0), leg_r=(0.2, 0, 0))
        bash = P.pose(arm_l=(-1.5, -0.5, 0.2), body=(0.35, -0.4, 0), head=(0.1, -0.2, 0), leg_l=(-0.6, 0, 0), leg_r=(0.4, 0, 0), loc=(0, 0.8, -2.5))
        out.append(A.Clip("bash", 60, keys=[(0, P.pose()), (20, guard), (26, P.add(P.copy(guard), body=(-0.05, 0.05, 0))), (30, bash), (38, bash),
                                            (60, P.pose())], ground="contact"))
        lift = P.pose(leg_r=(-1.3, 0, 0.1), body=(-0.15, 0, 0.1), arm_r=(-0.6, 0, 0.6), arm_l=(-0.8, 0.4, -0.5), head=(0.15, 0, 0))
        stamp = P.pose(leg_r=(-0.2, 0, 0.1), body=(0.35, 0, -0.05), arm_r=(0.2, 0, 0.4), arm_l=(-1.0, 0.4, -0.3), head=(0.25, 0, 0), loc=(0, 1.2, -0.5))
        out.append(A.Clip("pound", 78, keys=[(0, P.pose()), (30, lift), (35, P.add(P.copy(lift), leg_r=(-0.1, 0, 0))), (39, stamp), (48, stamp),
                                             (78, P.pose())], ground="floor"))
    elif kind == "experiment":
        coil = P.pose(body=(0.9, 0, 0), head=(-0.9, 0, 0), leg_r=(-1.0, 0, 0.15), leg_l=(-1.0, 0, -0.15), arm_r=(0.5, 0, 0.4), arm_l=(0.5, 0, -0.4),
                      loc=(0, 4.0, 0))
        fly = P.pose(body=(0.4, 0, 0), head=(-0.5, 0, 0), jaw=(0.8, 0, 0), arm_r=(-2.6, 0, 0.2), arm_l=(-2.6, 0, -0.2), leg_r=(0.9, 0, 0), leg_l=(0.7, 0, 0))
        land = P.pose(body=(0.8, 0, 0), head=(-0.6, 0, 0), jaw=(0.6, 0, 0), arm_r=(-1.3, 0, 0.3), arm_l=(-1.3, 0, -0.3), leg_r=(-0.9, 0, 0.1),
                      leg_l=(-0.6, 0, -0.1), loc=(0, 3.0, 0))
        out.append(A.Clip("leap", 90, keys=[(0, P.pose()), (12, coil), (16, P.jitter(coil, r, 0.04, ["body", "head"])), (22, coil), (28, fly), (60, fly),
                                            (72, land), (90, P.pose())], ground="floor"))
        back = P.pose(body=(0.1, 0, 0), head=(-0.9, 0, 0), jaw=(0.3, 0, 0), arm_r=(0.3, 0, 0.5), arm_l=(0.3, 0, -0.5))
        spit = P.pose(body=(0.75, 0, 0), head=(-0.1, 0, 0), jaw=(1.1, 0, 0), arm_r=(0.4, 0, 0.6), arm_l=(0.4, 0, -0.6), loc=(0, 0.5, -1.0))
        out.append(A.Clip("spit", 60, keys=[(0, P.pose()), (24, back), (30, spit), (40, P.jitter(spit, r, 0.04, ["head", "jaw"])), (60, P.pose())],
                          ground="contact"))
    elif kind == "patient_zero":
        rise = P.pose(body=(-0.3, 0, 0), head=(-0.7, 0, 0), arm_r=(-2.9, 0, 0.4), arm_l=(-2.9, 0, -0.4), loc=(0, -1.0, 0),
                      tendril_1=(-1.2, -0.6, 0), tendril_2=(-1.2, 0.6, 0), tendril_3=(-1.0, -0.8, 0), tendril_4=(-1.0, 0.8, 0))
        down = P.pose(body=(0.6, 0, 0), head=(0.3, 0, 0), arm_r=(-1.0, 0, 0.2), arm_l=(-1.0, 0, -0.2),
                      tendril_1=(0.6, -0.8, 0), tendril_2=(0.6, 0.8, 0), tendril_3=(0.6, -1.0, 0), tendril_4=(0.6, 1.0, 0))
        out.append(A.Clip("summon", 90, keys=[(0, P.pose()), (36, rise), (42, P.jitter(rise, r, 0.05, ["body", "head", "arm_r", "arm_l"])), (45, down),
                                              (60, down), (90, P.pose())], ground="contact"))
        fold = P.pose(body=(0.3, 0, 0), head=(0.4, 0, 0), arm_r=(-0.4, 0, -0.6), arm_l=(-0.4, 0, 0.6),
                      tendril_1=(-1.4, -0.2, 0), tendril_2=(-1.4, 0.2, 0), tendril_3=(-1.4, -0.2, 0), tendril_4=(-1.4, 0.2, 0))
        out.append(A.Clip("blink", 36, keys=[(0, P.pose()), (12, fold), (24, P.jitter(fold, r, 0.3, ["body", "head", "arm_r", "arm_l"])), (27, fold),
                                             (36, P.pose())], stepped=True, ground="contact"))
    return out


def clips(kind):
    return [A.Clip("idle", 180, gen=idle(kind), step=3, loop=True, ground="contact"),
            A.Clip("walk", 72, gen=walk(kind), step=3, loop=True, ground="contact"),
            A.Clip("swing", 48, keys=swing(kind), full=False, ground="contact"),
            A.Clip("roar", 90, keys=roar(kind), ground="contact")] + specials(kind)


def build(kind, name):
    return A.build(name, clips(kind), "root", {"leg_r", "leg_l"}, 0.08)
