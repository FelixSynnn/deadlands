"""The realism pass (Milestone 9): a finishing step over the textures the generators paint.

Run last (build_all group "realism", or `python dl_realism.py`). For every colour texture of the
project's own (DEADLANDS blocks and items, and the vanilla blocks DEADLANDS restyles; not entity
skins, the GUI, particles or the LabPBR _n/_s maps) it:

- lights everything from the upper left: blocks get a gentle relief from their own detail (their
  brightness read as height), items a top-left highlight and a bottom-right shadow along the
  silhouette plus a soft fall-off across the icon (never centre-out "pillow" shading);
- shifts hue with light: shadows lean cool, highlights lean warm, instead of only darker and lighter;
- pulls shades toward a five-step ramp per texture (gently, so detail survives without banding);
- calms colours that are fully saturated;
- removes stray single pixels that differ sharply from all four neighbours;
- replaces near-black outlines on items with a dark, cool shade of the colour inside them.

Tiling textures (opaque square blocks) are treated as wrapping so they stay seamless. Names, paths,
sizes, transparency and UV layouts are untouched. Before a texture is changed its original is copied
to texture-backup/ (outside the resources); a manifest of what each output should hash to keeps the
pass from being applied twice. Textures listed in RESTORED are left as their generators painted
them (the pass did not clearly improve them).
"""
from __future__ import annotations

import hashlib
import json
import pathlib
import shutil

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets"
BACKUP = ROOT / "texture-backup"
MANIFEST = ROOT / "tools" / "assetgen" / "_cache" / "realism_manifest.json"

SCOPES = {
    "block": [ASSETS / "deadlands/textures/block", ASSETS / "minecraft/textures/block"],
    "item": [ASSETS / "deadlands/textures/item"],
}
# Left as their generators painted them after the before/after review (Milestone 9): soft-edged
# decals and glass (no gain, and the ramp could band their alpha-blended edges), textures the pass
# changed imperceptibly (flat or near-black panels), and two thin sprites whose new outlines read
# weaker (radio antenna, wrench). Entity skins are not in SCOPES at all: the game lights each face of
# a model itself, and the pass made no visible difference on their UV islands.
RESTORED: set[str] = {
    # Painted pixel by pixel by gen_weapons.py (atmosphere overhaul): already shaded and outlined.
    *(f"deadlands/textures/item/{name}.png" for name in ("survival_knife", "hunting_knife", "machete", "rusty_machete", "crowbar", "baseball_bat",
                                                        "nail_bat", "lead_pipe", "pipe_wrench", "fire_axe", "pistol", "shotgun", "hunting_rifle")),
    "deadlands/textures/block/broken_glass.png",
    "deadlands/textures/block/dirty_glass.png",
    "deadlands/textures/block/cracked_glass.png",
    "deadlands/textures/block/dusty_glass.png",
    "deadlands/textures/block/charred_log.png",
    "deadlands/textures/block/container/dresser/clean/bottom.png",
    "deadlands/textures/block/container/dresser/damaged/bottom.png",
    "deadlands/textures/block/container/dresser/dusty/bottom.png",
    "deadlands/textures/block/container/dresser/rusty/bottom.png",
    "deadlands/textures/block/container/filing_cabinet/clean/back.png",
    "deadlands/textures/block/container/filing_cabinet/clean/bottom.png",
    "deadlands/textures/block/container/filing_cabinet/clean/top.png",
    "deadlands/textures/block/container/filing_cabinet/dusty/bottom.png",
    "deadlands/textures/block/container/safe/clean/back.png",
    "deadlands/textures/block/container/safe/clean/bottom.png",
    "deadlands/textures/block/container/safe/clean/edge.png",
    "deadlands/textures/block/container/safe/clean/inside_back.png",
    "deadlands/textures/block/container/safe/clean/inside_floor.png",
    "deadlands/textures/block/container/safe/clean/inside_side.png",
    "deadlands/textures/block/container/safe/clean/shelf.png",
    "deadlands/textures/block/container/safe/clean/side.png",
    "deadlands/textures/block/container/safe/clean/top.png",
    "deadlands/textures/block/container/safe/damaged/bottom.png",
    "deadlands/textures/block/container/safe/damaged/inside_back.png",
    "deadlands/textures/block/container/safe/damaged/inside_floor.png",
    "deadlands/textures/block/container/safe/damaged/inside_side.png",
    "deadlands/textures/block/container/safe/dusty/bottom.png",
    "deadlands/textures/block/container/safe/dusty/inside_back.png",
    "deadlands/textures/block/container/safe/dusty/inside_floor.png",
    "deadlands/textures/block/container/safe/dusty/inside_side.png",
    "deadlands/textures/block/container/safe/rusty/bottom.png",
    "deadlands/textures/block/container/safe/rusty/inside_back.png",
    "deadlands/textures/block/container/safe/rusty/inside_floor.png",
    "deadlands/textures/block/container/safe/rusty/inside_side.png",
    "deadlands/textures/block/container/school_desk/clean/inside_wall.png",
    "deadlands/textures/block/container/school_desk/clean/leg.png",
    "deadlands/textures/block/container/supply_locker/clean/back.png",
    "deadlands/textures/block/container/supply_locker/clean/bottom.png",
    "deadlands/textures/block/container/supply_locker/clean/door.png",
    "deadlands/textures/block/container/supply_locker/clean/edge.png",
    "deadlands/textures/block/container/supply_locker/clean/inside_back.png",
    "deadlands/textures/block/container/supply_locker/clean/inside_floor.png",
    "deadlands/textures/block/container/supply_locker/clean/inside_side.png",
    "deadlands/textures/block/container/supply_locker/clean/shelf.png",
    "deadlands/textures/block/container/supply_locker/clean/top.png",
    "deadlands/textures/block/container/supply_locker/damaged/bottom.png",
    "deadlands/textures/block/container/supply_locker/damaged/inside_back.png",
    "deadlands/textures/block/container/supply_locker/damaged/inside_floor.png",
    "deadlands/textures/block/container/supply_locker/damaged/inside_side.png",
    "deadlands/textures/block/container/supply_locker/damaged/shelf.png",
    "deadlands/textures/block/container/supply_locker/dusty/bottom.png",
    "deadlands/textures/block/container/supply_locker/dusty/inside_back.png",
    "deadlands/textures/block/container/supply_locker/dusty/inside_floor.png",
    "deadlands/textures/block/container/supply_locker/dusty/inside_side.png",
    "deadlands/textures/block/container/supply_locker/dusty/shelf.png",
    "deadlands/textures/block/container/supply_locker/rusty/bottom.png",
    "deadlands/textures/block/container/supply_locker/rusty/inside_back.png",
    "deadlands/textures/block/container/supply_locker/rusty/inside_floor.png",
    "deadlands/textures/block/container/supply_locker/rusty/inside_side.png",
    "deadlands/textures/block/container/supply_locker/rusty/shelf.png",
    "deadlands/textures/block/container/toolbox/clean/bottom.png",
    "deadlands/textures/block/container/toolbox/clean/handle.png",
    "deadlands/textures/block/container/toolbox/clean/inside_floor.png",
    "deadlands/textures/block/container/toolbox/clean/inside_wall.png",
    "deadlands/textures/block/container/toolbox/damaged/bottom.png",
    "deadlands/textures/block/container/toolbox/damaged/handle.png",
    "deadlands/textures/block/container/toolbox/dusty/bottom.png",
    "deadlands/textures/block/container/toolbox/dusty/handle.png",
    "deadlands/textures/block/container/toolbox/dusty/inside_wall.png",
    "deadlands/textures/block/container/toolbox/rusty/bottom.png",
    "deadlands/textures/block/container/toolbox/rusty/handle.png",
    "deadlands/textures/block/decal/biomass_veins.png",
    "deadlands/textures/block/decal/blood_smear.png",
    "deadlands/textures/block/decal/bloody_handprint.png",
    "deadlands/textures/block/decal/drag_marks.png",
    "deadlands/textures/block/decal/dried_blood_pool.png",
    "deadlands/textures/block/decal/dried_blood_spots.png",
    "deadlands/textures/block/decal/evacuation_notice.png",
    "deadlands/textures/block/decal/faded_stain.png",
    "deadlands/textures/block/decal/scattered_papers.png",
    "deadlands/textures/block/decal/scorch_mark.png",
    "deadlands/textures/block/decal/survivor_mark.png",
    "deadlands/textures/block/decor/dark_wood.png",
    "deadlands/textures/block/decor/lamp_housing.png",
    "deadlands/textures/block/decor/lamp_lens.png",
    "deadlands/textures/block/keycard_reader_1.png",
    "deadlands/textures/item/radio.png",
    "deadlands/textures/item/wrench.png",
    "minecraft/textures/block/ice.png",
    "minecraft/textures/block/mud.png",
}


# ============================================================================= colour helpers

def rgb_to_hsv(rgb):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx = np.max(rgb, axis=-1)
    mn = np.min(rgb, axis=-1)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    rm = nz & (mx == r)
    gm = nz & (mx == g) & ~rm
    bm = nz & ~rm & ~gm
    h[rm] = ((g - b)[rm] / d[rm]) % 6.0
    h[gm] = (b - r)[gm] / d[gm] + 2.0
    h[bm] = (r - g)[bm] / d[bm] + 4.0
    h = h / 6.0
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0.0)
    return np.stack([h, s, mx], axis=-1)


def hsv_to_rgb(hsv):
    h, s, v = hsv[..., 0] % 1.0, np.clip(hsv[..., 1], 0, 1), np.clip(hsv[..., 2], 0, 1)
    i = np.floor(h * 6.0).astype(int) % 6
    f = h * 6.0 - np.floor(h * 6.0)
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    out = np.zeros(h.shape + (3,))
    for k, (a, b, c) in enumerate(((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))):
        m = i == k
        out[m, 0], out[m, 1], out[m, 2] = a[m], b[m], c[m]
    return out


WARM, COOL = 45.0 / 360.0, 212.0 / 360.0


def hue_toward(h, target, amount):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return (h + d * amount) % 1.0


def neighbours(arr, wrap):
    """Up, down, left, right copies of a 2-D (or 3-D) array; edges repeat unless wrapping."""
    if wrap:
        return [np.roll(arr, 1, 0), np.roll(arr, -1, 0), np.roll(arr, 1, 1), np.roll(arr, -1, 1)]
    pad = np.pad(arr, [(1, 1), (1, 1)] + [(0, 0)] * (arr.ndim - 2), mode="edge")
    return [pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]]


# ============================================================================= the pass

LUMA = np.array([0.299, 0.587, 0.114])


def blur(arr, wrap):
    """A small cross blur, wrapping for tiles."""
    up, down, left, right = neighbours(arr, wrap)
    return arr * 0.5 + (up + down + left + right) / 8.0


def finish(img: np.ndarray, kind: str, keep_grey: bool = False) -> np.ndarray:
    """img: H x W x 4 floats 0-255. Returns the finished image (same shape and alpha).

    kind is "block" or "item". keep_grey keeps a greyscale texture greyscale (the game
    tints those, e.g. grass and leaves, so any hue would be multiplied into the biome colour)."""
    out = img.copy()
    rgb = img[..., :3] / 255.0
    alpha = img[..., 3]
    solid = alpha > 0
    if solid.sum() < 4:
        return out
    h, w = alpha.shape
    tiling = kind == "block" and h == w and bool((alpha == 255).all())
    lum = rgb @ LUMA

    # Stray pixels: one that differs sharply from all four neighbours is pulled halfway to them.
    nb = neighbours(rgb, tiling)
    na = neighbours(alpha, tiling)
    mean = sum(nb) / 4.0
    diff = np.stack([np.abs(n - rgb).max(axis=-1) for n in nb])
    all_solid = np.stack([a > 0 for a in na]).all(axis=0)
    stray = (diff.min(axis=0) > 0.16) & solid & all_solid
    rgb = np.where(stray[..., None], rgb * 0.5 + mean * 0.5, rgb)
    edge = solid & ~all_solid  # solid pixels touching transparency

    # Near-black outlines on items become a dark, cool shade of the colour they enclose.
    if kind == "item":
        dark = edge & (lum < 0.16)
        if dark.any():
            inner = np.zeros_like(rgb)
            count = np.zeros(lum.shape)
            for n, a in zip(nb, na):
                ok = (a > 0) & (n @ LUMA >= 0.16)
                inner += np.where(ok[..., None], n, 0.0)
                count += ok
            base = np.where(count[..., None] > 0, inner / np.maximum(count, 1)[..., None], rgb + 0.2)
            hsv = rgb_to_hsv(np.clip(base, 0, 1))
            hsv[..., 0] = hue_toward(hsv[..., 0], COOL, 0.3)
            hsv[..., 1] = np.clip(hsv[..., 1] * 1.05 + 0.08, 0, 0.7)
            hsv[..., 2] = np.clip(hsv[..., 2] * 0.36, 0.08, 0.26)
            rgb = np.where(dark[..., None], hsv_to_rgb(hsv), rgb)
        lum = rgb @ LUMA

    # Light from the upper left.
    light = np.zeros(lum.shape)
    if kind == "block":
        # Brightness read as height (softened so fine grain is not sharpened into crunch): slopes
        # facing up-left catch the light, slopes facing down-right fall into shade.
        height = blur(lum, tiling)
        up, down, left, right = neighbours(height, tiling)
        light = np.clip(((left - right) + (up - down)) * -0.9, -0.12, 0.12)
        # Smooth surfaces (painted metal, tiles) get a fine, seamless grain so they read as a material.
        grain = lum - blur(lum, tiling)
        if np.abs(grain[solid]).mean() < 0.012:
            rng = np.random.default_rng(int(lum.sum() * 1000) % (2 ** 32))
            noise = rng.normal(0.0, 1.0, lum.shape)
            noise = noise - blur(noise, tiling) * 0.5
            light = light + noise * 0.018
    elif kind == "item":
        # A soft fall-off across the icon, and a rim: the inside edge facing up-left catches the
        # light, the inside edge facing down-right is shaded.
        yy, xx = np.mgrid[0:h, 0:w].astype(float)
        light = 0.06 - 0.12 * (xx + yy) / (w + h)
        e_up, e_down, e_left, e_right = neighbours(edge.astype(float), False)
        rim = solid & ~edge
        lit = rim & ((e_up > 0) | (e_left > 0)) & ~((e_down > 0) | (e_right > 0))
        shade = rim & ((e_down > 0) | (e_right > 0)) & ~((e_up > 0) | (e_left > 0))
        light = light + np.where(lit, 0.11, 0.0) - np.where(shade, 0.11, 0.0)
    light = np.where(solid, light, 0.0)

    hsv = rgb_to_hsv(np.clip(rgb, 0, 1))
    m = lum[solid].mean()
    rel = np.clip((lum - m) * 1.6 + light * 3.0, -1, 1)
    if not keep_grey:
        # Hue-shifted shading: highlights lean warm (sunlight), shadows lean cool (skylight).
        coloured = hsv[..., 1] > 0.06
        shift = np.abs(rel) * 0.10
        hsv[..., 0] = np.where(coloured & (rel > 0), hue_toward(hsv[..., 0], WARM, shift), hsv[..., 0])
        hsv[..., 0] = np.where(coloured & (rel < 0), hue_toward(hsv[..., 0], COOL, shift), hsv[..., 0])
        # Shadows slightly richer, highlights slightly paler; nothing fully saturated.
        hsv[..., 1] = np.where(coloured, hsv[..., 1] * (1.0 - rel * 0.08), hsv[..., 1])
        hsv[..., 1] = np.where(hsv[..., 1] > 0.72, 0.72 + (hsv[..., 1] - 0.72) * 0.4, hsv[..., 1])
    v = hsv[..., 2] * (1.0 + light)
    # A five-step ramp: values lean toward the nearest of five levels across the texture's range.
    lo, hi = np.percentile(v[solid], 3), np.percentile(v[solid], 97)
    if hi - lo > 0.05:
        steps = lo + np.round((np.clip(v, lo, hi) - lo) / (hi - lo) * 4.0) / 4.0 * (hi - lo)
        v = v * 0.78 + steps * 0.22
    hsv[..., 2] = np.clip(v, 0, 1)
    final = hsv_to_rgb(hsv)
    if not keep_grey:
        # Greys keep their own hue but pick up a faint warm light and cool shade the same way, as a
        # small offset (a stronger tint on near-white reads as snow).
        grey = (rgb_to_hsv(np.clip(rgb, 0, 1))[..., 1] <= 0.06)[..., None]
        final = np.where(grey, final + rel[..., None] * np.array([0.012, 0.004, -0.014]), final)
    if keep_grey:
        final = np.repeat((final @ LUMA)[..., None], 3, axis=-1)
    out[..., :3] = np.where(solid[..., None], np.clip(final * 255.0, 0, 255), img[..., :3])
    return out


def is_grey(img: np.ndarray) -> bool:
    rgb = img[..., :3] / 255.0
    solid = img[..., 3] > 0
    return bool(solid.any()) and float((rgb.max(-1) - rgb.min(-1))[solid].max()) < 0.04


def targets():
    for kind, folders in SCOPES.items():
        for folder in folders:
            if not folder.exists():
                continue
            for path in sorted(folder.rglob("*.png")):
                if path.stem.endswith("_n") or path.stem.endswith("_s"):
                    continue
                if "variant" in path.parent.parts:
                    continue  # gen_variants blends these from finished textures: never finish them twice
                if "model" in path.parent.parts:
                    continue  # UV-unwrapped 3D model textures (gen_models3d): islands, not sprites
                yield kind, path


def digest(path: pathlib.Path) -> str:
    return hashlib.sha1(path.read_bytes()).hexdigest()


def run(only_restore: bool = False) -> tuple[int, int, int]:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}
    changed = skipped = restored = 0
    for kind, path in targets():
        rel = str(path.relative_to(ASSETS)).replace("\\", "/")
        backup = BACKUP / rel
        if rel in RESTORED:
            if backup.exists() and manifest.get(rel) == digest(path):
                shutil.copyfile(backup, path)  # put the original back
                restored += 1
            manifest.pop(rel, None)
            continue
        if only_restore:
            continue
        if manifest.get(rel) == digest(path):
            skipped += 1  # already finished, unchanged since
            continue
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, backup)  # a fresh original from a generator
        with Image.open(path) as im:
            img = np.asarray(im.convert("RGBA")).astype(float)
        # Vanilla greyscale textures are tinted by the game (grass, leaves): they stay greyscale.
        result = finish(img, kind, keep_grey=rel.startswith("minecraft/") and is_grey(img))
        Image.fromarray(np.clip(result + 0.5, 0, 255).astype(np.uint8), "RGBA").save(path)
        manifest[rel] = digest(path)
        changed += 1
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps(manifest, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    return changed, skipped, restored


def build() -> int:
    changed, skipped, restored = run()
    print(f"realism: {changed} finished, {skipped} already done, {restored} restored")
    return changed


if __name__ == "__main__":
    build()
