"""The infected's voices (atmosphere overhaul), from the source-filter voice in dl_voice.

Every kind has its own throat: the walker's slack groan, the runner's rasping shriek, the brute's
deep roaring chest, the screamer's thin wail, the crawler's wet choking, the bloater's bubbling,
the charred's burnt, airless rasp, and the stalker's hiss (it never groans). For each kind:

  ambient   idle: groans, a long moan, an inhaled groan (the most unsettling), a wet cough
  alert     the moment it takes a target: a sharp inhale, then a snarl or shriek
  chase     while it hunts: panting snarls (runner: rasping breaths and shrieks; brute: roaring
            breaths; stalker: hisses), short enough to come often without becoming a wall
  hurt      a choked grunt                  death   a long, gurgling exhale into creak

plus the brute's roar, the screamer's scream and the stalker's pounce, and the shared sounds the
client uses by distance: distant moans, voices muffled by walls, close breathing and the attack
snarl. Variants per event so nothing repeats; pitch and loudness vary again in game.
Finally, sound files no sounds.json refers to any more are removed.
"""
from __future__ import annotations

import json

import numpy as np

import dl_data as D
import dl_voice as V
from gen_audio import OUT, write_ogg

KINDS = {
    "walker": dict(f0=(72, 108), scale=0.95, growl=0.35, wet=0.3, rasp=0.35, creak=0.06),
    "runner": dict(f0=(120, 190), scale=1.04, growl=0.25, wet=0.15, rasp=0.7, creak=0.02),
    "brute": dict(f0=(44, 66), scale=0.76, growl=0.6, wet=0.35, rasp=0.45, creak=0.08),
    "screamer": dict(f0=(170, 300), scale=1.12, growl=0.15, wet=0.1, rasp=0.5, creak=0.03),
    "military": dict(f0=(78, 112), scale=0.93, growl=0.4, wet=0.25, rasp=0.4, creak=0.05),
    "crawler": dict(f0=(80, 118), scale=0.98, growl=0.3, wet=0.75, rasp=0.35, creak=0.1),
    "bloater": dict(f0=(58, 86), scale=0.85, growl=0.35, wet=1.0, rasp=0.25, creak=0.06),
    "stalker": dict(f0=(90, 130), scale=1.0, growl=0.2, wet=0.1, rasp=0.6, creak=0.04),
    "charred": dict(f0=(76, 110), scale=0.95, growl=0.45, wet=0.05, rasp=0.95, creak=0.12),
}
GLIDES = [[(0, "uh"), (0.45, "aa"), (1, "uh")], [(0, "oo"), (0.5, "aw"), (1, "uh")], [(0, "er"), (0.4, "aa"), (1, "aw")],
          [(0, "aw"), (0.5, "uh"), (1, "oo")], [(0, "uh"), (0.6, "ae"), (1, "er")]]


def f0(p, rng):
    return rng.uniform(*p["f0"])


def groan(p, rng, length=None, layered=False):
    base = f0(p, rng)
    length = length or rng.uniform(1.3, 2.4)
    glide = GLIDES[int(rng.integers(len(GLIDES)))]
    points = [(0, base * 0.95), (0.35, base * rng.uniform(1.04, 1.14)), (0.8, base * 0.92), (1, base * 0.82)]
    x = V.vocal(length, rng, points, glide, p["scale"], doubling=p["growl"] * 0.7, creak=p["creak"], breath=0.35, rasp=p["rasp"], wet=p["wet"],
                rattle=p["growl"] * 0.5, attack=0.25, release=0.5, tremolo=0.12)
    if layered:  # two throats a little apart: wrong, and hard to place
        y = V.vocal(length, rng, [(a, b * 0.97) for a, b in points], glide, p["scale"] * 1.03, doubling=p["growl"], creak=p["creak"],
                    breath=0.3, rasp=p["rasp"], wet=p["wet"], rattle=p["growl"] * 0.6, attack=0.3, release=0.5)
        x = x * 0.7 + y * 0.55
    return x


def moan(p, rng):
    base = f0(p, rng) * 0.95
    length = rng.uniform(2.4, 3.4)
    points = [(0, base * 0.9), (0.3, base * 1.15), (0.55, base * 1.05), (0.75, base * 1.12), (1, base * 0.8)]
    return V.vocal(length, rng, points, [(0, "oo"), (0.35, "aw"), (0.7, "aa"), (1, "uh")], p["scale"], jitter=0.015,
                   doubling=p["growl"] * 0.4, creak=p["creak"] * 0.5, breath=0.25, rasp=p["rasp"] * 0.6, wet=p["wet"] * 0.6, attack=0.5,
                   release=0.8, tremolo=0.18)


def inhaled(p, rng):
    base = f0(p, rng) * 1.25
    return V.vocal(rng.uniform(0.8, 1.3), rng, [(0, base * 0.9), (1, base * 1.1)], [(0, "oo"), (1, "aa")], p["scale"], jitter=0.05,
                   doubling=0.3, creak=0.1, breath=0.9, rasp=p["rasp"] + 0.3, wet=p["wet"] * 0.5, inhale=True)


def cough(p, rng):
    out = np.zeros(int(1.6 * V.SR))
    t = 0.0
    for _ in range(int(rng.integers(2, 4))):
        base = f0(p, rng) * 1.2
        c = V.vocal(rng.uniform(0.16, 0.26), rng, [(0, base * 1.2), (1, base * 0.8)], [(0, "aa"), (1, "uh")], p["scale"], breath=0.8,
                    rasp=p["rasp"] + 0.4, wet=p["wet"] + 0.4, attack=0.01, release=0.08, drive=2.5)
        s = int(t * V.SR)
        out[s:s + len(c)] += c[: len(out) - s]
        t += len(c) / V.SR + rng.uniform(0.08, 0.2)
    return out[: int((t + 0.1) * V.SR)]


def snarl(p, rng, length=None, pitch=1.35, drive=2.6):
    base = f0(p, rng) * pitch
    length = length or rng.uniform(0.55, 0.95)
    vowels = [(0, "ae"), (0.5, "aa"), (1, "aw")] if rng.random() < 0.5 else [(0, "eh"), (0.4, "ae"), (1, "aa")]
    return V.vocal(length, rng, [(0, base * 0.9), (0.25, base * 1.12), (1, base * 0.85)], vowels, p["scale"] * 1.03, jitter=0.04,
                   doubling=min(0.8, p["growl"] + 0.25), creak=p["creak"], breath=0.6, rasp=min(1.2, p["rasp"] + 0.35), wet=p["wet"],
                   rattle=0.35, drive=drive, attack=0.03, release=0.18)


def alert(p, rng, kind):
    breath_in = inhaled(p, rng)[: int(0.4 * V.SR)] * 0.7
    if kind == "screamer":
        cry = scream(p, rng, short=True)
    elif kind == "brute":
        cry = roar(p, rng, short=True)
    else:
        cry = snarl(p, rng, rng.uniform(0.7, 1.05), pitch=1.5 if kind == "runner" else 1.35, drive=3.0)
    return np.concatenate([breath_in, np.zeros(int(0.04 * V.SR)), cry])


def chase(p, rng, kind):
    if kind == "stalker":
        return V.hiss(rng.uniform(0.6, 1.1), rng, 0.6) * 0.8
    pieces = []
    for k in range(int(rng.integers(2, 4))):
        if kind == "brute":
            piece = roar(p, rng, short=True, length=rng.uniform(0.45, 0.7)) * 0.8
        elif kind == "runner" and rng.random() < 0.4:
            piece = snarl(p, rng, rng.uniform(0.2, 0.32), pitch=1.9, drive=3.2)
        else:
            piece = snarl(p, rng, rng.uniform(0.18, 0.34), pitch=1.25 + rng.uniform(0, 0.2))
        pieces.append(piece)
        pant = V.breath(rng.uniform(0.18, 0.3), rng, p["scale"], wet=p["wet"], wheeze=0.1) * 0.45
        pieces.append(pant)
    return np.concatenate(pieces)


def hurt(p, rng):
    base = f0(p, rng) * 1.3
    x = V.vocal(rng.uniform(0.28, 0.45), rng, [(0, base * 1.15), (1, base * 0.75)], [(0, "eh"), (1, "uh")], p["scale"], jitter=0.05,
                doubling=0.4, breath=0.6, rasp=p["rasp"] + 0.3, wet=p["wet"] + 0.2, attack=0.01, release=0.06, drive=2.8)
    return x


def death(p, rng):
    base = f0(p, rng) * 1.05
    length = rng.uniform(1.6, 2.4)
    return V.vocal(length, rng, [(0, base * 1.15), (0.3, base), (1, base * 0.5)], [(0, "aa"), (0.5, "uh"), (1, "oo")], p["scale"],
                   jitter=0.06, doubling=0.5, creak=0.35, breath=0.5, rasp=p["rasp"], wet=min(1.0, p["wet"] + 0.5), rattle=0.4, attack=0.02,
                   release=0.9)


def roar(p, rng, short=False, length=None):
    base = f0(p, rng)
    length = length or (rng.uniform(0.8, 1.1) if short else rng.uniform(1.4, 1.9))
    return V.vocal(length, rng, [(0, base * 0.9), (0.3, base * 1.35), (1, base * 1.0)], [(0, "aw"), (0.4, "aa"), (1, "aw")], p["scale"], jitter=0.05,
                   doubling=0.75, creak=0.05, breath=0.7, rasp=0.8, wet=p["wet"], rattle=0.5, drive=3.4, attack=0.06, release=0.35)


def scream(p, rng, short=False):
    length = rng.uniform(0.8, 1.1) if short else rng.uniform(1.8, 2.4)
    base = rng.uniform(260, 320)
    n_points = [(0, base), (0.25, base * 2.1), (0.6, base * 1.9), (1, base * 1.4)]
    x = V.vocal(length, rng, n_points, [(0, "ae"), (0.3, "aa"), (1, "ae")], 1.15, jitter=0.03, doubling=0.25, breath=0.8, rasp=0.85, wet=0.05,
                drive=3.2, attack=0.05, release=0.3, tremolo=0.2, widen=1.2)
    return x


def pounce(p, rng):
    return np.concatenate([V.hiss(0.25, rng, 0.3) * 0.6, snarl(p, rng, 0.35, pitch=1.6, drive=3.0)])


def attack(rng):
    p = KINDS["walker"]
    n = int(0.25 * V.SR)
    whoosh = V.M.bandpass(rng.normal(0, 1, n), 400, 2500) * np.sin(np.pi * np.arange(n) / n) ** 2 * 0.5
    return np.concatenate([whoosh, snarl(p, rng, rng.uniform(0.3, 0.45), pitch=1.4, drive=3.0)])


def sets() -> dict[str, list[np.ndarray]]:
    rng = np.random.default_rng(88001)
    out: dict[str, list[np.ndarray]] = {}
    for kind, p in KINDS.items():
        if kind == "stalker":
            out["infected.stalker.ambient"] = [V.breath(rng.uniform(2.4, 3.4), rng, 1.0, wet=0.15, wheeze=0.35) for _ in range(3)] + \
                [V.hiss(rng.uniform(0.8, 1.4), rng) for _ in range(3)]
        else:
            out[f"infected.{kind}.ambient"] = [groan(p, rng), groan(p, rng, layered=True), groan(p, rng), moan(p, rng), inhaled(p, rng), cough(p, rng)]
        if kind != "runner":
            out[f"infected.{kind}.alert"] = [alert(p, rng, kind) for _ in range(4)]
        else:
            out["infected.runner.alert"] = [alert(p, rng, kind) for _ in range(5)]
        out[f"infected.{kind}.chase"] = [chase(p, rng, kind) for _ in range(6)]
    for kind in ("walker", "runner", "brute", "screamer", "crawler", "bloater", "stalker"):
        out[f"infected.{kind}.hurt"] = [hurt(KINDS[kind], rng) for _ in range(4)]
    for kind in ("walker", "runner", "brute", "screamer", "crawler", "stalker"):
        out[f"infected.{kind}.death"] = [death(KINDS[kind], rng) for _ in range(3)]
    out["infected.brute.roar"] = [roar(KINDS["brute"], rng) for _ in range(3)]
    out["infected.screamer.scream"] = [scream(KINDS["screamer"], rng) for _ in range(3)]
    out["infected.stalker.pounce"] = [pounce(KINDS["stalker"], rng) for _ in range(3)]
    walkerish = [KINDS["walker"], KINDS["military"], KINDS["crawler"], KINDS["brute"]]
    out["infected.distant"] = [V.distant(moan(walkerish[i % 4], rng) if i % 2 else groan(walkerish[i % 4], rng), rng, 88100 + i, 40 + i * 6)
                               for i in range(6)]
    out["infected.muffled"] = [V.muffled(groan(walkerish[i % 4], rng), rng, 88200 + i) for i in range(5)]
    out["infected.breath"] = [V.breath(rng.uniform(2.4, 3.4), rng, 0.95, wet=0.5, wheeze=0.2) for _ in range(4)]
    out["infected.attack"] = [attack(rng) for _ in range(5)]
    return out


SUBTITLES = {"alert": "Infected shrieks", "chase": "Infected snarls"}


def prune_unreferenced(dry_run: bool = False) -> int:
    """Delete sound files that neither sounds.json refers to (generators that were superseded left them).
    With dry_run, only list them."""
    referenced = set()
    for path in (OUT.parent / "sounds.json", OUT.parent.parent / "minecraft" / "sounds.json"):
        if not path.exists():
            continue
        for event in json.loads(path.read_text(encoding="utf-8")).values():
            for sound in event.get("sounds", []):
                name = sound if isinstance(sound, str) else sound.get("name", "")
                if name.startswith("deadlands:") and (isinstance(sound, str) or sound.get("type", "file") == "file"):
                    referenced.add(name.split(":", 1)[1])
    removed = 0
    for file in OUT.rglob("*.ogg"):
        rel = file.relative_to(OUT).with_suffix("").as_posix()
        if rel not in referenced:
            if dry_run:
                print("    unreferenced:", rel)
            else:
                file.unlink()
            removed += 1
    return removed


def build() -> None:
    path = OUT.parent / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8"))
    for event, variants in sets().items():
        files = []
        stem = event.replace("infected.", "").replace(".", "_")
        for i, audio in enumerate(variants):
            rel = f"infected/voice/{stem}_{i + 1}"
            write_ogg(rel, V.normalize(audio).astype(np.float32), quality=4)
            files.append(f"deadlands:{rel}")
        entry = {"sounds": files}
        what = event.rsplit(".", 1)[1]
        old = sounds.get(event, {})
        if "subtitle" in old:
            entry["subtitle"] = old["subtitle"]
        elif what in SUBTITLES:
            entry["subtitle"] = f"subtitles.deadlands.infected.{what}"
        sounds[event] = entry
    path.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang({f"subtitles.deadlands.infected.{k}": v for k, v in SUBTITLES.items()})
    print(f"  removed {prune_unreferenced()} sound files nothing refers to any more")


if __name__ == "__main__":
    build()
    print("infected voices written")
