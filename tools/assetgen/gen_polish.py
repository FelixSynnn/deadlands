"""Polish (Milestone 9): particle sprites, location ambience, the supply drop's model (as Java,
SupplyDropGeometry.java) and texture, the supply drop's loot, and the text for weather, world events,
cutscenes and the new settings.

Ambience is synthesised like the rest of DEADLANDS audio: noise, resonators and envelopes, many
variants per event so nothing repeats exactly. wind_loop and hum_loop are seamless loops (every
component completes a whole number of cycles in the clip).
"""
from __future__ import annotations

import json
import zlib

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N
import gen_vehicles as V

NS = D.NS
JAVA = N.ROOT / "src" / "client" / "java" / "com" / "deadlands" / "client" / "render"

PARTICLES = {
    # name: frames of an 8x8 sprite, each a (radius, softness, speckle) recipe; colour comes from the particle code
    "ash": [(2.6, 0.7, 0.4), (2.3, 0.7, 0.5), (2.0, 0.6, 0.6), (1.6, 0.5, 0.7)],
    "dust": [(1.6, 1.0, 0.2), (1.9, 1.0, 0.2), (2.2, 1.0, 0.2), (2.4, 1.0, 0.25)],
    "blood": [(2.4, 0.3, 0.0), (2.1, 0.3, 0.0), (1.7, 0.3, 0.0), (1.3, 0.3, 0.0)],
    "spore": [(1.4, 0.8, 0.1), (1.8, 0.8, 0.15), (2.1, 0.9, 0.2), (2.3, 1.0, 0.3)],
    "ember": [(1.9, 0.5, 0.0), (1.6, 0.5, 0.0), (1.2, 0.4, 0.0), (0.8, 0.4, 0.0)],
}


def particles() -> int:
    count = 0
    for name, frames in PARTICLES.items():
        textures = []
        for i, (radius, soft, speckle) in enumerate(frames):
            r = np.random.default_rng(zlib.crc32(f"{name}{i}".encode()))
            yy, xx = np.mgrid[0:8, 0:8].astype(float)
            d = np.sqrt((xx - 3.5) ** 2 + (yy - 3.5) ** 2)
            alpha = np.clip((radius + 0.5 - d) / max(0.2, soft), 0.0, 1.0)
            if speckle:
                alpha *= (r.random((8, 8)) > speckle * 0.6)
            # Greyscale: the particle tints it. Lighter in the upper left, where the light comes from.
            shade = np.clip(1.0 - 0.06 * (xx + yy) + r.normal(0, 0.04, (8, 8)), 0.55, 1.0)
            img = np.zeros((8, 8, 4))
            img[..., :3] = (shade * 255)[..., None]
            img[..., 3] = alpha * 255
            Image.fromarray(img.astype(np.uint8), "RGBA").save(N.asset(f"textures/particle/{name}_{i}.png"))
            textures.append(f"{NS}:{name}_{i}")
            count += 1
        D.asset_json(f"particles/{name}.json", {"textures": textures})
    return count


# ============================================================================= ambience

def sounds() -> int:
    from scipy.signal import lfilter
    from gen_audio import OUT, SR, write_ogg

    rng = np.random.default_rng(9901)

    def t(sec):
        return np.arange(int(SR * sec)) / SR

    def env(x, attack=0.01, release=0.2):
        n = x.size
        tt = np.arange(n) / SR
        return x * np.minimum(1, tt / attack) * np.minimum(1, (tt[-1] - tt) / release)

    def band(x, f, bw):
        rr = np.exp(-np.pi * bw / SR)
        return lfilter([1 - rr], [1, -2 * rr * np.cos(2 * np.pi * f / SR), rr * rr], x)

    def lowpass(x, cutoff):
        a = np.exp(-2 * np.pi * cutoff / SR)
        return lfilter([1 - a], [1, -a], x)

    def norm(x, peak=0.7):
        return x / (np.max(np.abs(x)) + 1e-9) * peak

    def bird(k):
        tt = t(0.9)
        out = np.zeros(tt.size)
        f0 = 2400 + k * 500
        for j in range(3 + k):
            s = int(SR * (0.05 + j * 0.16))
            n = int(SR * 0.09)
            seg = np.arange(n) / SR
            chirp = np.sin(2 * np.pi * (f0 + 1500 * seg / 0.09 * (1 if j % 2 else -0.5)) * seg) * np.sin(np.pi * seg / 0.09)
            out[s:s + n] += chirp
        return norm(out, 0.5)

    def woodpecker(k):
        tt = t(1.2)
        out = np.zeros(tt.size)
        for j in range(10 + k * 3):
            s = int(SR * j * 0.055)
            n = int(SR * 0.03)
            if s + n < out.size:
                out[s:s + n] += band(rng.standard_normal(n), 1100, 400) * np.exp(-np.arange(n) / (SR * 0.006))
        return norm(out, 0.6)

    def crickets(k):
        tt = t(2.0)
        carrier = np.sin(2 * np.pi * (4300 + k * 200) * tt)
        pulses = (np.sin(2 * np.pi * 28 * tt) > 0.3) * (np.sin(2 * np.pi * 0.9 * tt + k) > -0.2)
        return env(norm(carrier * pulses, 0.35), 0.1, 0.4)

    def owl(k):
        out = np.zeros(int(SR * 1.6))
        for j, (start, dur) in enumerate(((0.0, 0.25), (0.45, 0.6))):
            seg = t(dur)
            hoot = np.sin(2 * np.pi * (380 - k * 20 - 30 * seg / dur) * seg) * np.sin(np.pi * seg / dur) ** 2
            s = int(SR * start)
            out[s:s + seg.size] += hoot
        return norm(out, 0.45)

    def rustle(k):
        tt = t(1.4)
        x = band(rng.standard_normal(tt.size), 3200 + k * 400, 2500)
        return env(norm(x * (0.4 + 0.6 * np.abs(np.sin(2 * np.pi * (2 + k) * tt))), 0.4), 0.2, 0.5)

    def dog(k):
        out = np.zeros(int(SR * 1.5))
        for j in range(2 + k % 2):
            seg = t(0.16)
            bark = band(rng.standard_normal(seg.size), 600 + k * 60, 300) + np.sin(2 * np.pi * (420 - 600 * seg) * seg)
            s = int(SR * j * 0.35)
            out[s:s + seg.size] += bark * np.exp(-seg * 14)
        return norm(lowpass(out, 1800), 0.4)

    def chime(k):
        tt = t(2.5)
        out = np.zeros(tt.size)
        for j, f in enumerate((880, 1046, 1318, 1568)[: 2 + k % 3]):
            s = int(SR * j * 0.3 * (1 + 0.2 * k))
            seg = tt[: tt.size - s]
            out[s:] += np.sin(2 * np.pi * f * seg) * np.exp(-seg * 2.2) + 0.3 * np.sin(2 * np.pi * f * 2.76 * seg) * np.exp(-seg * 4)
        return norm(out, 0.35)

    def door_bang(k):
        tt = t(1.0)
        x = band(rng.standard_normal(tt.size), 160 + k * 30, 120) * np.exp(-tt * 9) + 0.4 * band(rng.standard_normal(tt.size), 900, 600) * np.exp(-tt * 30)
        return norm(lowpass(x, 2200), 0.6)

    def glass(k):
        tt = t(0.8)
        out = np.zeros(tt.size)
        for j in range(12 + k * 4):
            s = rng.integers(0, int(tt.size * 0.6))
            n = int(SR * 0.02)
            f = rng.uniform(3000, 7000)
            out[s:s + n] += np.sin(2 * np.pi * f * np.arange(n) / SR) * np.exp(-np.arange(n) / (SR * 0.004))
        return norm(out, 0.4)

    def car_alarm(k):
        tt = t(2.4)
        f = 900 + 500 * (np.sin(2 * np.pi * (1.6 + k * 0.4) * tt) > 0)
        x = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR))
        return env(norm(lowpass(x, 1500), 0.3), 0.05, 0.3)

    def flicker(k):
        tt = t(1.5)
        hum = np.sin(2 * np.pi * 120 * tt) + 0.5 * np.sin(2 * np.pi * 240 * tt)
        gate = (rng.random(int(tt.size / 800) + 1) > 0.35 - 0.1 * k).repeat(800)[: tt.size]
        buzz = hum * gate + 0.3 * band(rng.standard_normal(tt.size), 3000, 2000) * gate
        return env(norm(buzz, 0.3), 0.01, 0.1)

    def drip(k):
        tt = t(0.5)
        f = 1400 + k * 300
        return norm(np.sin(2 * np.pi * (f + 1200 * np.exp(-tt * 40)) * tt) * np.exp(-tt * 25), 0.4)

    def gurney(k):
        tt = t(1.2)
        squeak = np.sin(2 * np.pi * np.cumsum(900 + 300 * np.sin(2 * np.pi * 3 * tt + k)) / SR)
        return env(norm(squeak * (0.5 + 0.5 * np.sin(2 * np.pi * 2.5 * tt)) + 0.3 * band(rng.standard_normal(tt.size), 200, 100), 0.35), 0.1, 0.3)

    def paper(k):
        tt = t(0.9)
        return env(norm(band(rng.standard_normal(tt.size), 5000, 3500) * np.abs(np.sin(2 * np.pi * (5 + k) * tt)), 0.35), 0.05, 0.3)

    def locker(k):
        tt = t(1.0)
        x = band(rng.standard_normal(tt.size), 700 + k * 80, 200) * np.exp(-tt * 12)
        x += sum(np.sin(2 * np.pi * f * tt) * np.exp(-tt * 6) * 0.3 for f in (440 + k * 30, 1210, 2100))
        return norm(x, 0.5)

    def steam(k):
        tt = t(1.8)
        return env(norm(band(rng.standard_normal(tt.size), 4500 + k * 300, 3000), 0.4), 0.05, 0.8)

    def groan(k):
        tt = t(2.2)
        f = 55 + k * 10 + 15 * np.sin(2 * np.pi * 0.4 * tt)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * (1 + 0.5 * band(rng.standard_normal(tt.size), 300, 200))
        return env(norm(lowpass(x, 600), 0.5), 0.4, 0.8)

    def tarp(k):
        tt = t(1.3)
        flaps = np.abs(np.sin(2 * np.pi * (3.5 + k) * tt)) ** 8
        return env(norm(band(rng.standard_normal(tt.size), 900, 800) * flaps, 0.45), 0.05, 0.3)

    def vent(k):
        tt = t(2.0)
        x = lowpass(rng.standard_normal(tt.size), 300) + 0.4 * np.sin(2 * np.pi * (48 + k * 4) * tt)
        return env(norm(x, 0.4), 0.5, 0.8)

    def radio(k):
        tt = t(0.9)
        x = band(rng.standard_normal(tt.size), 2000, 1500) * (rng.random(tt.size) > 0.2) + 0.4 * np.sin(2 * np.pi * (1000 + k * 250) * tt) * (tt < 0.2)
        return env(norm(x, 0.3), 0.01, 0.2)

    def wind_loop():
        n = int(SR * 8.0)
        spectrum = np.fft.rfft(rng.standard_normal(n))
        freqs = np.fft.rfftfreq(n, 1.0 / SR)
        spectrum *= 1.0 / np.maximum(1.0, freqs / 120.0)
        spectrum[freqs > 2500] *= 0.1
        bed = np.fft.irfft(spectrum, n=n)
        tt = np.arange(n) / SR
        gust = 0.6 + 0.4 * np.sin(2 * np.pi * tt / 8.0) * np.sin(2 * np.pi * 3 * tt / 8.0)
        return norm(bed * gust, 0.55)

    def hum_loop():
        tt = t(2.0)
        return norm(np.sin(2 * np.pi * 60 * tt) + 0.5 * np.sin(2 * np.pi * 120 * tt) + 0.2 * np.sin(2 * np.pi * 180 * tt), 0.3)

    makers = {"bird": bird, "woodpecker": woodpecker, "crickets": crickets, "owl": owl, "rustle": rustle, "dog": dog, "chime": chime,
              "door_bang": door_bang, "glass": glass, "car_alarm": car_alarm, "flicker": flicker, "drip": drip, "gurney": gurney, "paper": paper,
              "locker": locker, "steam": steam, "groan": groan, "tarp": tarp, "vent": vent, "radio": radio}
    table = json.loads((OUT.parent / "sounds.json").read_text(encoding="utf-8"))
    count = 0
    for name, maker in makers.items():
        names = []
        for k in range(3):
            write_ogg(f"ambient/place/{name}_{k}", maker(k), quality=3)
            names.append(f"{NS}:ambient/place/{name}_{k}")
            count += 1
        table[f"ambient.{name}"] = {"sounds": names, "subtitle": f"subtitles.{NS}.ambient.{name}"}
    for name, data in (("wind_loop", wind_loop()), ("hum_loop", hum_loop())):
        write_ogg(f"ambient/place/{name}", data, quality=3)
        table[f"ambient.{name}"] = {"sounds": [{"name": f"{NS}:ambient/place/{name}", "stream": True}], "subtitle": f"subtitles.{NS}.ambient.{name}"}
        count += 1
    (OUT.parent / "sounds.json").write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")
    return count


# ============================================================================= supply drop

def supply_drop() -> None:
    P = V.Part
    parts = [P("chassis", None, (0, 24, 0), [], None)]
    add = lambda name, cubes, role: parts.append(P(name, "chassis", (0, 0, 0), cubes, role))
    add("crate", [(-7, -14, -7, 14, 14, 14)], "crate")
    add("canopy", [(-22, -58, -22, 44, 4, 44), (-18, -61, -18, 36, 3, 36)], "canopy")
    add("lines", [(-7, -56, -7, 1, 42, 1), (6, -56, -7, 1, 42, 1), (-7, -56, 6, 1, 42, 1), (6, -56, 6, 1, 42, 1)], "line")
    V.pack(parts)
    body = V.java_layer("supply_drop", parts)
    (JAVA / "SupplyDropGeometry.java").write_text(f"""package com.deadlands.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** GENERATED by tools/assetgen/gen_polish.py - do not edit. The supply drop: a crate, its parachute canopy and the lines. */
@SuppressWarnings("unused")
public final class SupplyDropGeometry {{
	private SupplyDropGeometry() {{
	}}

{body}
}}
""", encoding="utf-8")
    img = np.zeros((V.TEX_H, V.TEX_W, 4))
    for p in parts:
        if p.role is None:
            continue
        for (u, v), c in zip(p.uv, p.cubes):
            for name, (x, y, w, h) in V.faces(u, v, c).items():
                if w <= 0 or h <= 0:
                    continue
                region = img[y:y + h, x:x + w]
                region[..., 3] = 255
                if p.role == "crate":
                    region[..., :3] = N.hex_rgb("#4a5236")
                    region[[0, -1], :, :3] = N.hex_rgb("#3a4028")
                    region[:, [0, -1], :3] = N.hex_rgb("#3a4028")
                    if name in ("front", "back", "left", "right") and w >= 8:
                        region[h // 2 - 1:h // 2 + 1, 2:w - 2, :3] = N.hex_rgb("#d8d0b0")  # a stencil band
                elif p.role == "canopy":
                    stripes = (np.arange(w) // 6) % 2
                    region[..., :3] = np.where(stripes[None, :, None] == 0, N.hex_rgb("#c86a2a"), N.hex_rgb("#d8d0c0"))
                else:
                    region[..., :3] = N.hex_rgb("#c8c0a8")
    n = N.spectral(V.TEX_H, V.TEX_W, beta=1.6, seed=9911)
    solid = img[..., 3] > 0
    img[..., :3] *= (0.88 + 0.24 * n)[..., None]
    img[~solid] = 0
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(N.asset("textures/entity/supply_drop.png"))


def loot() -> None:
    def item(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name if ":" in name else f"{NS}:{name}", "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e
    D.data_json("loot_table/events/supply_drop.json", {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 6}, "entries": [
            item("mre", 6, 1, 3), item("water_bottle", 6, 2, 4), item("bandage", 5, 2, 4), item("pistol_ammo", 5, 8, 20), item("shotgun_shell", 3, 4, 8),
            item("rifle_round", 3, 4, 8), item("antibiotics", 2), item("medkit", 2), item("purification_tablet", 3, 2, 4), item("splint", 2),
            item("radio_parts", 2), item("fuel_can", 2), item("military_backpack", 1), item("antiviral", 1), item("pistol", 1), item("keycard_blue", 1)]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:document", "functions": [{"function": "minecraft:set_components",
            "components": {f"{NS}:lore": "mil_calder_access", "minecraft:custom_model_data": {"strings": ["document"]}}}]}]}],
        "random_sequence": f"{NS}:events/supply_drop"})


TEXT = {
    "event.deadlands.where": "%s, %s",
    "event.deadlands.supply_drop": "A supply drop is coming down near %s. Others will have heard it too.",
    "event.deadlands.crash": "Smoke on the horizon: something came down near %s.",
    "event.deadlands.convoy": "An abandoned military convoy has been spotted near %s.",
    "event.deadlands.bandits": "Bandits are on the move near %s.",
    "event.deadlands.distress": "A distress call: survivors trapped near %s.",
    "event.deadlands.outage": "The lights have gone out in %s. The generator needs fuel.",
    "hud.deadlands.event.supply_drop": "SUPPLY DROP",
    "hud.deadlands.event.crash": "CRASH SITE",
    "hud.deadlands.event.convoy": "MILITARY CONVOY",
    "hud.deadlands.event.bandits": "BANDIT PATROL",
    "hud.deadlands.event.distress": "DISTRESS SIGNAL",
    "hud.deadlands.event.outage": "POWER OUTAGE",
    "chronicle.deadlands.event.supply_drop": "A supply drop came down at %s.",
    "chronicle.deadlands.event.crash": "Something crashed at %s.",
    "chronicle.deadlands.event.convoy": "A military convoy was found at %s.",
    "chronicle.deadlands.event.bandits": "Bandits were seen near %s.",
    "chronicle.deadlands.event.distress": "Survivors called for help near %s.",
    "chronicle.deadlands.event.outage": "The power failed in %s.",
    "command.deadlands.event.unknown": "There is no world event called %s.",
    "command.deadlands.event.no_room": "There is no room for that near you.",
    "command.deadlands.weather": "The weather turns: %s.",
    "command.deadlands.weather.unknown": "There is no weather called %s.",
    "weather.deadlands.clear": "clear",
    "weather.deadlands.fog": "fog",
    "weather.deadlands.wind": "strong wind",
    "weather.deadlands.dust": "dust storm",
    "entity.deadlands.supply_drop": "Supply Drop",
    "cutscene.deadlands.title": "Cutscene",
    "cutscene.deadlands.opening.0": "Harley: Easy. You were out for two days. Fever broke last night.",
    "cutscene.deadlands.opening.1": "Harley: Welcome to what is left of Ashford County. I need your help.",
    "cutscene.deadlands.boss_reveal.0": "That is not one of the ordinary dead. Run, or make your stand.",
    "cutscene.deadlands.research.0": "Harley: They knew. Three months before the first case, they knew.",
    "cutscene.deadlands.cure.0": "Harley: It holds. The cells stop turning. We have a cure.",
    "cutscene.deadlands.horde.0": "Over the hill, something moves like a tide. They are coming.",
    "cutscene.deadlands.extraction.0": "The rotor catches. Dust everywhere. Everyone holds on.",
    "cutscene.deadlands.extraction.1": "Ashford County falls away beneath you.",
    "config.deadlands.ui.cutscenes": "Cutscenes",
    "radio.deadlands.emergency.event": "Reported: %s at grid %s, %s.",
}
for _name in ("bird", "woodpecker", "crickets", "owl", "rustle", "dog", "chime", "door_bang", "glass", "car_alarm", "flicker", "drip", "gurney",
              "paper", "locker", "steam", "groan", "tarp", "vent", "radio", "wind_loop", "hum_loop"):
    TEXT[f"subtitles.deadlands.ambient.{_name}"] = {
        "bird": "Bird sings", "woodpecker": "Woodpecker knocks", "crickets": "Crickets chirp", "owl": "Owl hoots", "rustle": "Leaves rustle",
        "dog": "Distant dog barks", "chime": "Wind chimes", "door_bang": "Door bangs", "glass": "Glass breaks", "car_alarm": "Distant car alarm",
        "flicker": "Light buzzes", "drip": "Water drips", "gurney": "Wheels squeak", "paper": "Paper rustles", "locker": "Locker rattles",
        "steam": "Steam hisses", "groan": "Metal groans", "tarp": "Tarp flaps", "vent": "Vent rumbles", "radio": "Radio crackles",
        "wind_loop": "Wind blows", "hum_loop": "Power hums"}[_name]


def build() -> int:
    count = particles()
    count += sounds()
    supply_drop()
    loot()
    D.merge_lang(TEXT)
    return count


if __name__ == "__main__":
    print(build(), "polish assets written")
