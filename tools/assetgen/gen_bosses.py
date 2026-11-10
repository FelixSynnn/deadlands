"""The bosses (Milestone 8): five box-built rigs written as Java (BossGeometry.java), their textures,
spawn eggs, sounds (roar, attack, hurt, death each, and the boss sting) and English text.

Model space as for the infected: y down, the ground at y = 24, the front facing -Z, 16 pixels to a
block. Every rig has a "root" at the ground and named parts the model animates: body, head,
arm_r, arm_l, leg_r, leg_l, and per boss: head2/head3/arm_r2/arm_l2 (Abomination), cleaver/hook
(Butcher), shield/visor (Warden), jaw (Experiment), tendril_1..4 (Patient Zero). A child's pivot is
relative to its parent's.
"""
from __future__ import annotations

import json
import zlib

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N
from gen_items import _outline

NS = D.NS
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render" / "boss"


class Part:
    def __init__(self, name, parent, pivot, cubes, role):
        self.name, self.parent, self.pivot, self.cubes, self.role = name, parent, pivot, cubes, role
        self.uv = []


def P(name, parent, pivot, cubes, role):
    return Part(name, parent, pivot, cubes, role)


RIGS = {
    "abomination": (256, [
        P("root", None, (0, 24, 0), [], None),
        P("leg_r", "root", (-8, -14, 0), [(-4, 0, -4, 8, 14, 8)], "skin"),
        P("leg_l", "root", (8, -14, 0), [(-4, 0, -4, 8, 14, 8)], "skin"),
        P("body", "root", (0, -14, 0), [(-15, -26, -11, 30, 26, 22), (-10, -30, -8, 20, 4, 16), (-8, -18, -14, 16, 12, 3)], "flesh"),
        P("head", "body", (-6, -28, -6), [(-5, -10, -5, 10, 10, 10)], "face"),
        P("head2", "body", (9, -26, -4), [(-4, -8, -4, 8, 8, 8)], "face"),
        P("head3", "body", (-14, -20, 2), [(-3, -7, -3, 7, 7, 7)], "face"),
        P("arm_r", "body", (-15, -22, 0), [(-8, -2, -4, 8, 30, 8)], "skin"),
        P("arm_l", "body", (15, -22, 0), [(0, -2, -4, 8, 30, 8)], "skin"),
        P("arm_r2", "body", (-12, -8, -8), [(-4, -1, -3, 4, 14, 4)], "skin"),
        P("arm_l2", "body", (12, -10, -8), [(0, -1, -3, 4, 12, 4)], "skin"),
    ]),
    "butcher": (128, [
        P("root", None, (0, 24, 0), [], None),
        P("leg_r", "root", (-4, -16, 0), [(-3, 0, -3, 6, 16, 6)], "trousers"),
        P("leg_l", "root", (4, -16, 0), [(-3, 0, -3, 6, 16, 6)], "trousers"),
        P("body", "root", (0, -16, 0), [(-7, -16, -4, 14, 16, 8), (-6, -12, -6, 12, 11, 2)], "apron"),
        P("head", "body", (0, -16, 0), [(-4, -9, -4, 8, 9, 8)], "sack"),
        P("arm_r", "body", (-9, -14, 0), [(-3, -2, -3, 5, 17, 6)], "skin"),
        P("cleaver", "arm_r", (-1, 14, -1), [(-1, -1, -3, 2, 2, 4), (-0.5, -5, -11, 1, 9, 8)], "metal"),
        P("arm_l", "body", (9, -14, 0), [(-2, -2, -3, 5, 17, 6)], "skin"),
        P("hook", "arm_l", (1, 15, 0), [(-0.5, 0, -0.5, 1, 7, 1), (-1.5, 7, -1.5, 3, 4, 1)], "metal"),
    ]),
    "warden": (128, [
        P("root", None, (0, 24, 0), [], None),
        P("leg_r", "root", (-3.5, -15, 0), [(-3, 0, -3, 6, 15, 6)], "armor"),
        P("leg_l", "root", (3.5, -15, 0), [(-3, 0, -3, 6, 15, 6)], "armor"),
        P("body", "root", (0, -15, 0), [(-6, -14, -4, 12, 14, 8), (-6.5, -13, -4.6, 13, 9, 1)], "armor"),
        P("head", "body", (0, -14, 0), [(-4, -8, -4, 8, 8, 8), (-4.5, -9, -4.5, 9, 4, 9)], "helmet"),
        P("visor", "head", (0, -6, -4.6), [(-4.2, 0, -0.2, 8.4, 2, 1)], "visor"),
        P("arm_r", "body", (-8, -12, 0), [(-3, -2, -3, 5, 15, 6)], "armor"),
        P("arm_l", "body", (8, -12, 0), [(-2, -2, -3, 5, 15, 6)], "armor"),
        P("shield", "arm_l", (3, 8, -2), [(0, -10, -8, 1, 20, 14)], "shield"),
    ]),
    "experiment": (128, [
        P("root", None, (0, 24, 0), [], None),
        P("leg_r", "root", (-4, -18, 0), [(-2, 0, -2, 4, 9, 4), (-1.5, 8, 0, 3, 10, 3)], "skin"),
        P("leg_l", "root", (4, -18, 0), [(-2, 0, -2, 4, 9, 4), (-1.5, 8, 0, 3, 10, 3)], "skin"),
        P("body", "root", (0, -18, 0), [(-5, -16, -3, 10, 16, 6), (-1, -16, 3, 2, 14, 2)], "flesh"),
        P("head", "body", (0, -16, -1), [(-3, -8, -5, 6, 8, 7)], "face"),
        P("jaw", "head", (0, -1, -2), [(-2.5, 0, -4, 5, 2, 5)], "flesh"),
        P("arm_r", "body", (-6, -14, 0), [(-2, -1, -2, 3, 24, 3), (-2.5, 23, -3, 4, 4, 1)], "skin"),
        P("arm_l", "body", (6, -14, 0), [(-1, -1, -2, 3, 24, 3), (-1.5, 23, -3, 4, 4, 1)], "skin"),
    ]),
    "patient_zero": (128, [
        P("root", None, (0, 24, 0), [], None),
        P("leg_r", "root", (-2.5, -13, 0), [(-2, 0, -2, 4, 13, 4)], "skin"),
        P("leg_l", "root", (2.5, -13, 0), [(-2, 0, -2, 4, 13, 4)], "skin"),
        P("body", "root", (0, -13, 0), [(-5, -13, -3, 10, 13, 6), (-5.5, -2, -3.5, 11, 8, 7)], "gown"),
        P("head", "body", (0, -13, 0), [(-4, -8, -4, 8, 8, 8)], "face"),
        P("arm_r", "body", (-6.5, -12, 0), [(-2, -1, -2, 3, 14, 3)], "skin"),
        P("arm_l", "body", (6.5, -12, 0), [(-1, -1, -2, 3, 14, 3)], "skin"),
        P("tendril_1", "body", (-3, -10, 3), [(-1, -1, 0, 2, 2, 12)], "flesh"),
        P("tendril_2", "body", (3, -10, 3), [(-1, -1, 0, 2, 2, 12)], "flesh"),
        P("tendril_3", "body", (-2, -5, 3), [(-1, -1, 0, 2, 2, 9)], "flesh"),
        P("tendril_4", "body", (2, -5, 3), [(-1, -1, 0, 2, 2, 9)], "flesh"),
    ]),
}

PALETTES = {
    "abomination": {"skin": "#8a8a72", "flesh": "#8a3a34", "face": "#9a9478", "eye": "#e8e090"},
    "butcher": {"skin": "#a08470", "trousers": "#3a3630", "apron": "#c8b898", "sack": "#a08a64", "metal": "#8a8e92", "eye": "#e0d080"},
    "warden": {"armor": "#2e3428", "helmet": "#262a22", "visor": "#1a2a30", "shield": "#3a4448", "eye": "#d0c060", "skin": "#7a7a68"},
    "experiment": {"skin": "#a8a8a0", "flesh": "#9a5a58", "face": "#b0aca0", "eye": "#c8f060"},
    "patient_zero": {"skin": "#9a9890", "gown": "#8aa0b0", "face": "#a8a69c", "flesh": "#5a2a3a", "eye": "#c890f0"},
}
NAMES = {"abomination": ("The Abomination", "What came out of Ward C"), "butcher": ("The Butcher", "Leon Vask still feeds his animals"),
         "warden": ("The Warden", "Colonel Hale holds the line"), "experiment": ("The Experiment", "Subject E-12 remembers"),
         "patient_zero": ("Patient Zero", "Where it began")}
EGGS = {"abomination": ("#7a3a32", "#9a9478"), "butcher": ("#c8b898", "#8a2a22"), "warden": ("#2e3428", "#1a2a30"),
        "experiment": ("#a8a8a0", "#c8f060"), "patient_zero": ("#8aa0b0", "#5a2a3a")}


def footprint(c):
    w, h, d = c[3:]
    return int(np.ceil(2 * d + 2 * w)), int(np.ceil(d + h))


def pack(parts, size):
    items = [(footprint(c), p, i) for p in parts for i, c in enumerate(p.cubes)]
    items.sort(key=lambda t: (-t[0][1], -t[0][0]))
    for p in parts:
        p.uv = [None] * len(p.cubes)
    x = y = shelf = 0
    for (fw, fh), p, i in items:
        if x + fw > size:
            x, y, shelf = 0, y + shelf, 0
        assert y + fh <= size, f"texture full at {p.name}"
        p.uv[i] = (x, y)
        x += fw
        shelf = max(shelf, fh)


def faces(u, v, c):
    w, h, d = (int(np.ceil(t)) for t in c[3:])
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def jf(v):
    return f"{float(v)}F"


def java_layer(method, parts, size):
    lines = [f"\tpublic static LayerDefinition {method}() {{", "\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root0 = mesh.getRoot();"]
    for p in parts:
        b = "CubeListBuilder.create()"
        for (u, v), (x, y, z, w, h, d) in zip(p.uv, p.cubes):
            b += f".texOffs({u}, {v}).addBox({jf(x)}, {jf(y)}, {jf(z)}, {jf(w)}, {jf(h)}, {jf(d)})"
        px, py, pz = p.pivot
        parent = "root0" if p.parent is None else p.parent
        lines.append(f"\t\tPartDefinition {p.name} = {parent}.addOrReplaceChild(\"{p.name}\", {b}, PartPose.offset({jf(px)}, {jf(py)}, {jf(pz)}));")
    lines += [f"\t\treturn LayerDefinition.create(mesh, {size}, {size});", "\t}"]
    return "\n".join(lines)


def write_geometry():
    body = []
    for kind, (size, parts) in RIGS.items():
        pack(parts, size)
        body.append(java_layer(kind, parts, size))
    src = """package com.deadlands.client.render.boss;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/assetgen/gen_bosses.py - do not edit; change the rigs there and rerun it.
 *
 * <p>The five boss rigs. Model space: y down, the ground at y = 24, the front facing -Z.
 */
@SuppressWarnings("unused")
public final class BossGeometry {
	private BossGeometry() {
	}

""" + "\n\n".join(body) + "\n}\n"
    JAVA.mkdir(parents=True, exist_ok=True)
    (JAVA / "BossGeometry.java").write_text(src, encoding="utf-8")


def seed_of(text):
    return zlib.crc32(text.encode())


def rgb(c):
    return np.array(N.hex_rgb(c), dtype=float)


def paint(kind):
    size, parts = RIGS[kind]
    pal = PALETTES[kind]
    r = np.random.default_rng(seed_of(kind))
    img = np.zeros((size, size, 4))
    n1 = N.spectral(size, size, beta=1.8, seed=seed_of(kind) + 1)
    n2 = N.spectral(size, size, beta=1.0, seed=seed_of(kind) + 2)
    for p in parts:
        if p.role is None:
            continue
        base = rgb(pal.get(p.role, pal.get("skin", "#888888")))
        for (u, v), c in zip(p.uv, p.cubes):
            for name, (x, y, w, h) in faces(u, v, c).items():
                if w <= 0 or h <= 0:
                    continue
                region = img[y:y + h, x:x + w]
                shade = 1.08 if name == "top" else 0.72 if name == "bottom" else 1.0
                region[..., :3] = base * shade
                region[..., 3] = 255
                if p.role in ("skin", "face", "flesh"):
                    # Veins and wounds.
                    veins = n2[y:y + h, x:x + w] > 0.82
                    region[veins, :3] = region[veins, :3] * 0.6 + rgb("#4a2a3a") * 0.4
                    sores = n1[y:y + h, x:x + w] > 0.8
                    region[sores, :3] = rgb("#6a1e1a")
                if p.role == "apron":
                    stain = n1[y:y + h, x:x + w] > 0.62
                    region[stain, :3] = region[stain, :3] * 0.45 + rgb("#6a1410") * 0.55
                if p.role in ("armor", "helmet"):
                    for sy in range(0, h, 5):
                        region[sy, :, :3] *= 0.8
                if p.role == "shield" and name in ("left", "right"):
                    region[h // 2 - 1:h // 2 + 1, :, :3] = rgb("#c8c8b0")
                    region[..., 3] = 220
                if p.role == "gown":
                    for sx in range(0, w, 4):
                        for sy in range(sx % 8 // 2, h, 6):
                            region[sy:sy + 1, sx:sx + 1, :3] = rgb("#5a7088")
                    blood = n1[y:y + h, x:x + w] > 0.7
                    region[blood, :3] = rgb("#5a1a1a")
                if p.role == "metal":
                    rust = n2[y:y + h, x:x + w] > 0.7
                    region[rust, :3] = rgb("#6a2a1a")
                if p.role == "sack" and name == "front" and w >= 6:
                    # Eye holes cut in the sack.
                    region[h // 3:h // 3 + 2, w // 4:w // 4 + 2, :3] = rgb(pal["eye"])
                    region[h // 3:h // 3 + 2, 3 * w // 4 - 2:3 * w // 4, :3] = rgb(pal["eye"])
                    region[2 * h // 3:2 * h // 3 + 1, w // 3:2 * w // 3, :3] = rgb("#3a2a1a")
                if p.role == "face" and name == "front" and w >= 5:
                    ey = h // 3
                    region[ey:ey + 1 + h // 8, max(0, w // 4 - 1):w // 4 + 1, :3] = rgb(pal["eye"])
                    region[ey:ey + 1 + h // 8, 3 * w // 4 - 1:3 * w // 4 + 1, :3] = rgb(pal["eye"])
                    region[2 * h // 3:2 * h // 3 + max(1, h // 6), w // 4:3 * w // 4, :3] = rgb("#2a1414")
                if p.role == "visor":
                    region[..., :3] = rgb(pal["visor"])
                    region[:, 1:3, :3] = rgb(pal["eye"])
                    region[:, -3:-1, :3] = rgb(pal["eye"])
    solid = img[..., 3] > 0
    img[..., :3] *= (0.86 + 0.28 * n1)[..., None]
    # Blood everywhere it fell.
    for _ in range(40 if kind != "warden" else 20):
        cx, cy = int(r.integers(0, size)), int(r.integers(0, size))
        rad = int(r.integers(1, 4))
        yy, xx = np.ogrid[-cy:size - cy, -cx:size - cx]
        drop = (xx * xx + yy * yy <= rad * rad) & solid
        img[drop, :3] = img[drop, :3] * 0.4 + rgb("#5a0e0a") * 0.6
    img[..., :3] = np.clip(img[..., :3], 0, 255)
    img[~solid] = 0
    return img


def spawn_egg(kind, seed):
    s = 32
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    egg = ((xx - 16) / 9.5) ** 2 + ((yy - 17) / 12.5) ** 2 <= 1.0
    base, spots = EGGS[kind]
    img = np.ones((s, s, 3)) * rgb(base) * (0.9 + 0.2 * N.spectral(s, s, beta=1.5, seed=seed))[..., None]
    sp = N.spectral(s, s, beta=0.8, seed=seed + 1) > 0.75
    img[sp] = rgb(spots)
    light = np.clip(1.15 - ((xx - 12) ** 2 + (yy - 11) ** 2) / 500.0, 0.7, 1.2)
    img *= light[..., None]
    alpha = egg.astype(float)
    _outline(img, alpha)
    return np.clip(img, 0, 255), alpha


# ============================================================================= sounds

def sounds():
    from scipy.signal import lfilter
    from gen_audio import OUT, SR, write_ogg

    def lowpass(x, cutoff):
        a = np.exp(-2 * np.pi * cutoff / SR)
        return lfilter([1 - a], [1, -a], x)

    def resonate(x, f, bw):
        rr = np.exp(-np.pi * bw / SR)
        return lfilter([1 - rr], [1, -2 * rr * np.cos(2 * np.pi * f / SR), rr * rr], x)

    voice = {"abomination": (55, 0.9, (400, 900)), "butcher": (85, 0.5, (550, 1100)), "warden": (105, 0.4, (650, 1400)),
             "experiment": (170, 0.7, (900, 2600)), "patient_zero": (130, 0.3, (500, 1700))}
    table = json.loads((OUT.parent / "sounds.json").read_text(encoding="utf-8"))
    for kind, (f0, grit, (fa, fb)) in voice.items():
        rng = np.random.default_rng(seed_of(kind + "voice"))
        for what, dur, bend in (("roar", 1.8, 0.85), ("attack", 0.6, 1.1), ("hurt", 0.45, 1.25), ("death", 3.0, 0.45)):
            n = int(SR * dur)
            t = np.arange(n) / SR
            pitch = f0 * np.linspace(1.0, bend, n) * (1 + 0.05 * np.sin(2 * np.pi * 7 * t))
            saw = 2 * ((np.cumsum(pitch) / SR) % 1.0) - 1
            noise = rng.standard_normal(n)
            src = saw * (1 - grit) + lowpass(noise, f0 * 6) * grit * 3
            out = resonate(src, fa, 120) + 0.6 * resonate(src, fb, 200)
            if what == "attack":
                whoosh = lowpass(rng.standard_normal(n), 900) * np.sin(np.pi * t / dur) ** 2
                out = out * 0.5 + whoosh * 2.0
            if kind == "patient_zero":
                # A second, whispering voice under the first.
                out += resonate(rng.standard_normal(n), 2400, 600) * 0.3
            env = np.minimum(1, t / 0.05) * np.minimum(1, (dur - t) / (0.4 if what == "death" else 0.15))
            out = np.tanh(out / (np.max(np.abs(out)) + 1e-9) * 2.2) * env
            out = out / (np.max(np.abs(out)) + 1e-9) * 0.85
            rel = f"boss/{kind}_{what}"
            write_ogg(rel, out, quality=4)
            table[f"boss.{kind}.{what}"] = {"sounds": [f"{NS}:{rel}"], "subtitle": f"subtitles.{NS}.boss.{what}"}
    # The sting: a low brass chord over a drum, three seconds.
    n = int(SR * 3.2)
    t = np.arange(n) / SR
    chord = sum(np.sign(np.sin(2 * np.pi * f * t)) * 0.25 + np.sin(2 * np.pi * f * 2 * t) * 0.1 for f in (55.0, 65.4, 82.4))
    chord = lowpass(chord, 900) * np.minimum(1, t / 0.3) * np.exp(-t * 0.6)
    drum = np.sin(2 * np.pi * 50 * t * np.exp(-t * 3)) * np.exp(-t * 4) * 1.5
    sting = np.tanh((chord + drum) * 1.4)
    sting = sting / np.max(np.abs(sting)) * 0.8
    write_ogg("event/boss_sting", sting, quality=4)
    table["event.boss"] = {"sounds": [f"{NS}:event/boss_sting"], "subtitle": f"subtitles.{NS}.event.boss"}
    (OUT.parent / "sounds.json").write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


def build() -> int:
    write_geometry()
    lang = {"subtitles.deadlands.boss.roar": "Something huge roars", "subtitles.deadlands.boss.attack": "Something huge attacks",
            "subtitles.deadlands.boss.hurt": "Something huge is hurt", "subtitles.deadlands.boss.death": "Something huge dies",
            "subtitles.deadlands.event.boss": "Something is coming"}
    previews = []
    for i, kind in enumerate(RIGS):
        img = paint(kind)
        Image.fromarray(img.astype(np.uint8), "RGBA").save(N.asset(f"textures/entity/boss/{kind}.png"))
        previews.append(Image.fromarray(img.astype(np.uint8), "RGBA"))
        egg_rgb, egg_a = spawn_egg(kind, 41000 + i)
        N.save_png(N.asset(f"textures/item/{kind}_spawn_egg.png"), egg_rgb, egg_a)
        D.asset_json(f"models/item/{kind}_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{kind}_spawn_egg"}})
        D.item_model_definition(f"{kind}_spawn_egg", f"{NS}:item/{kind}_spawn_egg")
        title, subtitle = NAMES[kind]
        lang[f"boss.{NS}.{kind}"] = title
        lang[f"boss.{NS}.{kind}.subtitle"] = subtitle
        lang[f"entity.{NS}.{kind}"] = title
        lang[f"item.{NS}.{kind}_spawn_egg"] = f"{title.replace('The ', '')} Spawn Egg"
    sounds()
    D.merge_lang(lang)
    sheet = Image.new("RGBA", (sum(p.width for p in previews) + 10 * len(previews), max(p.height for p in previews)), (50, 50, 54, 255))
    x = 0
    for p in previews:
        sheet.alpha_composite(p, (x, 0))
        x += p.width + 10
    (N.ROOT / "tools" / "assetgen" / "_cache").mkdir(exist_ok=True)
    sheet.save(N.ROOT / "tools" / "assetgen" / "_cache" / "bosses_textures.png")
    return len(RIGS)


if __name__ == "__main__":
    print(build(), "bosses written")
