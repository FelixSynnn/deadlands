"""Per-position texture variants for the blocks that cover the most ground (atmosphere overhaul).

A wall or a road of one texture repeats in a grid the eye picks out at once. Each block here gets
three more textures, and its blockstate picks one per block position (with a random quarter turn
for blocks where that is invisible), so walls, roads and fields stop repeating.

Variants are made from the block's finished texture (after dl_realism), so they match it exactly:
the outer few texels are left untouched, so any variant sits seamlessly next to any other, and
only the inside changes, by one of two means:

* ROTATE (no pattern: asphalt, concrete, dirt, gravel, stone...): the inside is blended with a
  turned or mirrored copy of the texture, then a little wear is painted on;
* WEAR (a pattern a turn would break: bricks, planks, paving slabs, siding...): the inside only
  takes wear: a crack, a stain, soot, moss, rot, rust, an oil spot, weeds.

Variant textures live in textures/block/variant/, which dl_realism leaves alone (they are already
finished), and copy the base block's LabPBR maps. Run after `realism` (build_all does).
"""
from __future__ import annotations

import json
import shutil

import numpy as np
from PIL import Image

import dl_data as D
import dl_noise as N

NS = D.NS
T = 32
VARIANTS = 3
ROTATE, WEAR = "rotate", "wear"
MC_ASSETS = N.ROOT / "src" / "main" / "resources" / "assets" / "minecraft"

DEADLANDS = {  # block: (mode, wear kinds)
    "asphalt": (ROTATE, ("stain", "crack", "oil")),
    "cracked_asphalt": (ROTATE, ("weeds", "crack", "stain")),
    "potholed_asphalt": (WEAR, ("stain", "oil")),
    "concrete": (ROTATE, ("crack", "stain", "moss")),
    "cracked_concrete": (ROTATE, ("stain", "moss")),
    "stained_concrete": (ROTATE, ("crack", "stain")),
    "dirty_concrete": (ROTATE, ("crack", "stain")),
    "rubble": (ROTATE, ("stain",)),
    "dry_dirt": (ROTATE, ("stain", "weeds")),
    "ash": (ROTATE, ("stain",)),
    "plaster_wall": (WEAR, ("crack", "stain", "soot")),
    "weathered_bricks": (WEAR, ("soot", "moss", "stain")),
    "sooty_bricks": (WEAR, ("stain", "crack")),
    "boarded_planks": (WEAR, ("rot", "stain")),
    "rotting_planks": (WEAR, ("rot", "moss")),
    "faded_planks": (WEAR, ("stain", "crack")),
    "rusted_sheet_metal": (WEAR, ("rust", "stain")),
    "corroded_metal": (WEAR, ("rust", "stain")),
    "sidewalk": (WEAR, ("crack", "stain", "moss")),
}
VANILLA = {
    "stone": (ROTATE, ("crack", "stain")), "cobblestone": (ROTATE, ("moss", "stain")), "mossy_cobblestone": (ROTATE, ("moss",)),
    "gravel": (ROTATE, ("stain",)), "dirt": (ROTATE, ("stain", "weeds")), "coarse_dirt": (ROTATE, ("stain",)), "sand": (ROTATE, ("stain",)),
    "mud": (ROTATE, ("stain",)), "oak_planks": (WEAR, ("rot", "stain")), "spruce_planks": (WEAR, ("rot", "stain")),
}


def window(margin=6.0):
    yy, xx = np.mgrid[0:T, 0:T]
    d = np.minimum(np.minimum(xx, T - 1 - xx), np.minimum(yy, T - 1 - yy)).astype(float)
    w = np.clip((d - 1.0) / margin, 0, 1)
    return w * w * (3 - 2 * w)


def turned(rgb, k):
    return [np.rot90(rgb, 1), np.rot90(rgb, 2), rgb[:, ::-1], rgb[::-1, :], np.rot90(rgb, 3)][k % 5]


def wear(rgb, kinds, rng, w):
    """Paint wear inside the window (w = 0 at the border)."""
    out = rgb.copy()
    seed = int(rng.integers(1 << 30))
    n = N.spectral(T, T, beta=1.8, seed=seed)
    fine = N.spectral(T, T, beta=0.7, seed=seed + 1)
    for kind in kinds:
        if kind == "stain":
            m = N.smoothstep(0.62, 0.78, n) * w
            out = out * (1 - 0.2 * m)[..., None]
        elif kind == "oil":
            m = N.smoothstep(0.7, 0.8, N.spectral(T, T, beta=2.6, seed=seed + 2)) * w
            out = out * (1 - 0.5 * m)[..., None] + np.array([22, 22, 24.0]) * (0.5 * m)[..., None]
        elif kind == "moss":
            m = ((fine > 0.62) & (n > 0.5)) * w
            out = out * (1 - 0.55 * m)[..., None] + np.array([62, 76, 44.0]) * (0.55 * m)[..., None]
        elif kind == "soot":
            yy = np.linspace(1.0, 0.0, T)[:, None]
            m = np.clip(yy * 0.8 + (n - 0.5) * 0.6, 0, 1) * w
            out = out * (1 - 0.35 * m)[..., None]
        elif kind == "rot":
            m = N.smoothstep(0.6, 0.78, n) * w
            out = out * (1 - 0.5 * m)[..., None] + np.array([30, 32, 22.0]) * (0.5 * m)[..., None]
        elif kind == "rust":
            m = N.smoothstep(0.6, 0.76, n) * w
            out = out * (1 - 0.55 * m)[..., None] + np.array([118, 62, 32.0]) * (0.55 * m)[..., None]
        elif kind in ("crack", "weeds"):
            x, y = rng.uniform(9, 23), rng.uniform(9, 23)
            angle = rng.uniform(0, 2 * np.pi)
            for _ in range(int(rng.integers(8, 15))):
                xi, yi = int(x), int(y)
                if 1 <= xi < T - 1 and 1 <= yi < T - 1 and w[yi, xi] > 0.3:
                    if kind == "crack":
                        out[yi, xi] *= 0.55
                        out[min(T - 1, yi + 1), min(T - 1, xi + 1)] *= 1.1
                    else:
                        out[yi, xi] = out[yi, xi] * 0.4 + np.array([58, 70, 40.0]) * 0.6
                angle += rng.normal(0, 0.5)
                x += np.cos(angle)
                y += np.sin(angle)
    return np.clip(out, 0, 255)


def make_variants(path, mode, kinds, seed):
    img = np.asarray(Image.open(path).convert("RGBA")).astype(float)
    rgb, alpha = img[..., :3], img[..., 3]
    w = window()
    rng = np.random.default_rng(seed)
    out = []
    for k in range(VARIANTS):
        v = rgb.copy()
        if mode == ROTATE:
            v = rgb * (1 - w[..., None]) + turned(rgb, k + int(rng.integers(0, 2))) * w[..., None]
        picks = [kinds[i] for i in rng.choice(len(kinds), size=min(len(kinds), 1 + k % 2), replace=False)] if kinds else []
        v = wear(v, picks, rng, w)
        out.append(np.dstack([v, alpha]))
    return out


def blockstate(base_model, variant_models, rotate):
    entries = [{"model": base_model, "weight": 3}] + [{"model": m} for m in variant_models]
    if rotate:
        entries = [dict(e, y=y) if y else dict(e) for e in entries for y in (0, 90, 180, 270)]
    return {"variants": {"": entries}}


def build() -> int:
    count = 0
    targets = [(name, mode, kinds, N.ASSETS / "textures" / "block" / f"{name}.png", f"{NS}:block/{name}", N.ASSETS / "blockstates" / f"{name}.json")
               for name, (mode, kinds) in DEADLANDS.items()]
    targets += [(name, mode, kinds, MC_ASSETS / "textures" / "block" / f"{name}.png", f"minecraft:block/{name}",
                 MC_ASSETS / "blockstates" / f"{name}.json") for name, (mode, kinds) in VANILLA.items()]
    for index, (name, mode, kinds, texture, base_model, state_path) in enumerate(targets):
        if not texture.exists():
            continue
        models = []
        for k, img in enumerate(make_variants(texture, mode, kinds, 70000 + index * 31)):
            rel = f"block/variant/{name}_{k + 1}"
            out = N.ASSETS / "textures" / f"{rel}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(img.astype(np.uint8), "RGBA").save(out, optimize=True)
            for suffix in ("_n", "_s"):  # the base block's material maps, for shader packs
                src = texture.with_name(texture.stem + suffix + ".png")
                if src.exists():
                    shutil.copyfile(src, out.with_name(out.stem + suffix + ".png"))
            D.asset_json(f"models/{rel}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:{rel}"}})
            models.append(f"{NS}:{rel}")
            count += 1
        state_path.parent.mkdir(parents=True, exist_ok=True)
        state_path.write_text(json.dumps(blockstate(base_model, models, mode == ROTATE), indent="\t") + "\n", encoding="utf-8")
    return count


if __name__ == "__main__":
    print(f"{build()} variant textures written")
