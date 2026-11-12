"""Phase 1 of the horror transformation: the data and assets behind the immersion rules.

- Vanilla structures that bring vanilla mobs or break the setting are disabled: their biome tags
  become empty (Hard Rule 2). The code side is world/VanillaMobs.java.
- Vanilla ambience (cave and underwater sounds) is silenced, and the menu theme plays at 45%
  (Hard Rule 5, Audio Rules).
- Two placeholder music tracks: a chase loop and one exploration piece (synthesised here; listed
  in docs/ASSET_MANIFEST.md for a composer).
- The flashlight: a 3D model built from boxes, its texture, and its name (Hard Rule 4).
- Text for the new settings and the /deadlands mobs command.
"""
from __future__ import annotations

import json

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N

NS = D.NS

# Every vanilla structure's biome tag. All of them either spawn vanilla mobs (villages, outposts,
# monuments, ocean ruins, mansions, witch huts, ancient cities, strongholds, trial chambers,
# mineshafts, igloos, temples) or do not belong in DEADLANDS (shipwrecks and their treasure, ruined
# portals, trail ruins, the Nether and End structures).
VANILLA_STRUCTURES = [
    "ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "end_city", "igloo", "jungle_temple",
    "mineshaft", "mineshaft_mesa", "nether_fortress", "nether_fossil", "ocean_monument", "ocean_ruin_cold",
    "ocean_ruin_warm", "pillager_outpost", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_mountain",
    "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_standard", "ruined_portal_swamp", "shipwreck",
    "shipwreck_beached", "stronghold", "swamp_hut", "trail_ruins", "trial_chambers", "village_desert",
    "village_plains", "village_savanna", "village_snowy", "village_taiga", "woodland_mansion",
]

# Vanilla ambience that plays in the biomes DEADLANDS keeps (caves, oceans, rivers).
SILENCED = [
    "ambient.cave", "ambient.underwater.loop", "ambient.underwater.loop.additions",
    "ambient.underwater.loop.additions.rare", "ambient.underwater.loop.additions.ultra_rare",
]

MENU_MUSIC_VOLUME = 0.45


def structures() -> int:
    for name in VANILLA_STRUCTURES:
        D.data_json(f"tags/worldgen/biome/has_structure/{name}.json", {"replace": True, "values": []}, ns="minecraft")
    return len(VANILLA_STRUCTURES)


def vanilla_sounds() -> None:
    path = N.ROOT / "src" / "main" / "resources" / "assets" / "minecraft" / "sounds.json"
    sounds = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    sounds["music.menu"] = {"replace": True, "sounds": [{"name": f"{NS}:music/title_theme", "stream": True, "volume": MENU_MUSIC_VOLUME}]}
    for name in SILENCED:
        sounds[name] = {"replace": True, "sounds": []}
    path.write_text(json.dumps(sounds, indent="\t") + "\n", encoding="utf-8")


# ----------------------------------------------------------------------------- music (placeholders)

def music() -> None:
    from gen_audio import OUT, SR, write_ogg

    rng = np.random.default_rng(4401)

    def sine(freq, seconds, phase=0.0):
        t = np.arange(int(SR * seconds)) / SR
        return np.sin(2 * np.pi * freq * t + phase)

    def lowpass(x, cutoff):
        from scipy.signal import lfilter
        a = np.exp(-2 * np.pi * cutoff / SR)
        return lfilter([1 - a], [1, -a], x)

    def loop_fade(x, seconds=2.0):
        n = int(SR * seconds)
        out = x[:-n].copy()
        ramp = np.linspace(0, 1, n)
        out[:n] = out[:n] * ramp + x[-n:] * (1 - ramp)
        return out

    def stereo(left, right):
        return np.stack([left, right], axis=1)

    # Chase: a low pulse at 132 bpm under a rising, detuned drone and scraping noise. Loops.
    seconds = 48.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    beat = 60.0 / 132.0
    pulse = np.zeros(n)
    for k in range(int(seconds / beat)):
        s = int(k * beat * SR)
        m = min(n - s, int(0.32 * SR))
        env = np.exp(-np.arange(m) / (SR * 0.09))
        pitch = 46.0 if k % 4 else 41.0
        pulse[s:s + m] += np.sin(2 * np.pi * pitch * np.arange(m) / SR) * env
    drone = sum(sine(f, seconds) * w for f, w in ((55.0, 0.5), (55.6, 0.4), (82.4, 0.25), (116.5, 0.12)))
    swell = 0.5 + 0.5 * np.sin(2 * np.pi * t / 12.0 - np.pi / 2)
    scrape = lowpass(rng.standard_normal(n), 900) * (0.15 + 0.25 * swell)
    chase = 0.65 * pulse + 0.35 * drone * (0.6 + 0.4 * swell) + scrape
    chase = loop_fade(chase / (np.max(np.abs(chase)) + 1e-9) * 0.8)
    write_ogg("music/chase", stereo(chase, np.roll(chase, int(0.011 * SR))), quality=4)

    # Exploration: sparse, cold and quiet; a slow two-note figure over air, fading out.
    seconds = 80.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    pad = sum(sine(f, seconds) * w for f, w in ((110.0, 0.30), (164.8, 0.18), (220.4, 0.10)))
    pad *= 0.5 + 0.5 * np.sin(2 * np.pi * t / 20.0)
    notes = np.zeros(n)
    for k, (start, freq) in enumerate(((4, 440.0), (9, 392.0), (21, 440.0), (26, 349.2), (40, 329.6), (47, 293.7), (60, 329.6))):
        s = int(start * SR)
        m = min(n - s, int(5.0 * SR))
        env = (1 - np.exp(-np.arange(m) / (SR * 0.02))) * np.exp(-np.arange(m) / (SR * 1.6))
        notes[s:s + m] += np.sin(2 * np.pi * freq * np.arange(m) / SR) * env * (0.5 if k % 2 else 0.6)
    air = lowpass(rng.standard_normal(n), 400) * 0.05
    explore = pad * 0.6 + notes * 0.5 + air
    explore *= np.minimum(1.0, t / 4.0) * np.minimum(1.0, (seconds - t) / 8.0)
    explore = explore / (np.max(np.abs(explore)) + 1e-9) * 0.6
    write_ogg("music/explore", stereo(explore, np.roll(explore, int(0.017 * SR))), quality=4)

    # Since Phase 2 the chosen tracks (import_sfx.py) replace these placeholders: point the events
    # here only while those tracks are absent.
    if not (OUT / "music" / "chase_strings.ogg").exists():
        path = OUT.parent / "sounds.json"
        table = json.loads(path.read_text(encoding="utf-8"))
        table["music.chase"] = {"sounds": [{"name": f"{NS}:music/chase", "stream": True}]}
        table["music.explore"] = {"sounds": [{"name": f"{NS}:music/explore", "stream": True}]}
        path.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")


# ----------------------------------------------------------------------------- the flashlight

def flashlight() -> None:
    """A 32x32 texture laid out in model units (2 px each) and a box model: rubber body, metal
    head with a lens at the front (north), a switch on top."""
    tex = np.zeros((32, 32, 4))
    rng = np.random.default_rng(4411)

    def fill(x0, y0, x1, y1, rgb, grain=0.06):
        h, w = y1 - y0, x1 - x0
        base = np.array(rgb, dtype=float)
        noise = 1 + rng.normal(0, grain, (h, w, 1))
        tex[y0:y1, x0:x1, :3] = np.clip(base * noise, 0, 255)
        tex[y0:y1, x0:x1, 3] = 255

    fill(0, 0, 4, 20, (46, 48, 50))           # body sides (2 x 10 units)
    for y in range(2, 20, 3):                 # grip ridges
        tex[y, 0:4, :3] *= 0.7
    fill(4, 0, 10, 6, (150, 152, 150), 0.04)  # head sides (3 x 3 units)
    tex[0, 4:10, :3] = (188, 190, 186)        # light catching the rim
    fill(10, 0, 16, 6, (226, 214, 160), 0.03)  # lens (3 x 3 units)
    tex[1:5, 11:15, :3] = (246, 238, 196)
    fill(16, 0, 20, 4, (58, 60, 62))          # body end cap (2 x 2 units)
    fill(16, 8, 18, 10, (150, 48, 40), 0.03)  # switch
    Image.fromarray(tex.astype(np.uint8), "RGBA").save(N.asset("textures/item/flashlight.png"))

    t = f"{NS}:item/flashlight"

    def faces(side, front, back, turn=0):
        return {
            "north": {"uv": front, "texture": "#0"}, "south": {"uv": back, "texture": "#0"},
            "east": {"uv": side, "texture": "#0", "rotation": turn}, "west": {"uv": side, "texture": "#0", "rotation": turn},
            "up": {"uv": side, "texture": "#0"}, "down": {"uv": side, "texture": "#0"},
        }

    model = {
        "textures": {"0": t, "particle": t},
        "elements": [
            {"name": "body", "from": [7, 7, 3], "to": [9, 9, 13], "faces": faces([0, 0, 2, 10], [8, 0, 10, 2], [8, 0, 10, 2], 90)},
            {"name": "head", "from": [6.5, 6.5, 0], "to": [9.5, 9.5, 3], "faces": faces([2, 0, 5, 3], [5, 0, 8, 3], [2, 0, 5, 3])},
            {"name": "switch", "from": [7.6, 9, 8], "to": [8.4, 9.4, 9], "faces": faces([8, 4, 9, 5], [8, 4, 9, 5], [8, 4, 9, 5])},
        ],
        "display": {
            "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [2.5, 1.0, -1.0], "scale": [0.9, 0.9, 0.9]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [2.5, 1.0, -1.0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.0, 1.0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.0, 1.0], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [30, 225, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 90, 0], "scale": [0.8, 0.8, 0.8]},
            "head": {"translation": [0, 10, -4]},
        },
    }
    # Phase 3: gen_models3d (a later group) replaces this box model with a detailed one, keeping this display.
    D.asset_json("models/item/flashlight.json", model)
    D.item_model_definition("flashlight", t)


TEXT = {
    "item.deadlands.flashlight": "Flashlight",
    "config.deadlands.audio.musicVolume": "Music volume",
    "config.deadlands.audio.chaseMusicThreshold": "Chase music: crowd size",
    "config.deadlands.audio.chaseMusicStopBelow": "Chase music: ends below",
    "command.deadlands.mobs": "Vanilla mobs loaded: %s. Infected loaded: %s. Raids: %s, patrols: %s, phantoms: %s, wandering traders: %s.",
    "command.deadlands.mobs.player": "%s: %s infected chasing; chase music %s.",
}


def build() -> int:
    count = structures()
    vanilla_sounds()
    music()
    flashlight()
    D.merge_lang(TEXT)
    print(f"immersion: {count} vanilla structure tags emptied, {len(SILENCED)} vanilla ambient sounds silenced, 2 music tracks, flashlight")
    return count


if __name__ == "__main__":
    build()
