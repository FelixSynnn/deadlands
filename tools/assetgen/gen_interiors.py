"""Phase 3 batch 3: interior materials, so rooms stop looking alike.

Five new tileable blocks (registry/DLInteriors.java): faded floral wallpaper, striped wallpaper,
dark wood panelling, stained carpet and checkered linoleum, each aged (water stains, peeling, grime,
wear) and written with its LabPBR maps like the other block textures. `gen_towns.interior_finish`
uses them for interior walls and floors of homes, apartments, motels and offices.
"""
from __future__ import annotations

import json

import numpy as np

import dl_data as D
import dl_materials as M
import dl_noise as N

T = M.T
NS = "deadlands"


def _stains(seed: int, amount: float) -> np.ndarray:
    """Water stains running down from the top, and grime: 0 clean, 1 stained."""
    drip = N.spectral(T, T, beta=2.0, seed=seed, aniso=(6.0, 1.0))
    blotch = N.spectral(T, T, beta=2.6, seed=seed + 1)
    return np.clip(N.smoothstep(1 - amount, 1 - amount + 0.25, 0.6 * blotch + 0.4 * drip), 0, 1)


def _finish(rgb, height, seed, stain=0.3, smooth=0.12, porosity=0.5) -> M.Material:
    s = _stains(seed, stain)
    rgb = N.lerp(rgb, rgb * np.array([0.62, 0.56, 0.44]), s * 0.8)
    return M.relief(M.Material(rgb=rgb, height=height, smooth=smooth, porosity=porosity), strength=1.0, ambient=0.85)


def wallpaper_floral(seed: int) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    base = N.solid(T, T, "#c9b991") * (0.94 + 0.06 * N.spectral(T, T, 1.0, seed))[..., None]
    motif = np.zeros((T, T))
    for cy, cx in ((8, 8), (24, 24), (8, 24), (24, 8)):
        d = np.sqrt(((yy - cy + 16) % 32 - 16) ** 2 + ((xx - cx + 16) % 32 - 16) ** 2)
        motif = np.maximum(motif, N.smoothstep(4.5, 2.5, d) * (0.5 + 0.5 * np.cos(np.arctan2(yy - cy, xx - cx) * 5)))
    rgb = N.lerp(base, N.solid(T, T, "#8a6a5a"), motif * 0.7)
    peel = N.smoothstep(0.82, 0.9, N.spectral(T, T, 2.2, seed + 5))
    rgb = N.lerp(rgb, N.solid(T, T, "#9a9488"), peel)  # torn paper shows the plaster
    return _finish(rgb, 0.3 * motif - 0.4 * peel, seed + 9, stain=0.35)


def wallpaper_striped(seed: int) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T]
    stripe = ((xx // 4) % 2 == 0).astype(float)
    rgb = N.lerp(N.solid(T, T, "#7d8a6a"), N.solid(T, T, "#5e6a4e"), stripe) * (0.95 + 0.05 * N.spectral(T, T, 1.0, seed))[..., None]
    peel = N.smoothstep(0.84, 0.92, N.spectral(T, T, 2.2, seed + 5))
    rgb = N.lerp(rgb, N.solid(T, T, "#9a9488"), peel)
    return _finish(rgb, 0.1 * stripe - 0.4 * peel, seed + 9, stain=0.4)


def wood_paneling(seed: int) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T]
    grain = N.spectral(T, T, beta=1.7, seed=seed, aniso=(14.0, 1.0))
    rgb = N.ramp(0.45 + 0.35 * (np.sin(grain * 20) * 0.5) + 0.1 * N.spectral(T, T, 1.0, seed + 1), [(0, "#2e1e14"), (1, "#6a4a30")])
    groove = (xx % 8 == 0)
    rgb = np.where(groove[..., None], rgb * 0.45, rgb)
    return _finish(rgb, np.where(groove, -1.0, 0.2 * grain), seed + 9, stain=0.25, smooth=0.3)


def stained_carpet(seed: int) -> M.Material:
    fibre = N.spectral(T, T, beta=0.6, seed=seed)
    rgb = N.solid(T, T, "#6a3a30") * (0.85 + 0.25 * fibre)[..., None]
    worn = N.smoothstep(0.55, 0.85, N.spectral(T, T, 2.4, seed + 2))
    rgb = N.lerp(rgb, N.solid(T, T, "#8a6a58"), worn * 0.6)
    return _finish(rgb, 0.4 * fibre, seed + 9, stain=0.45, smooth=0.02, porosity=0.9)


def linoleum(seed: int) -> M.Material:
    yy, xx = np.mgrid[0:T, 0:T]
    check = (((yy // 8) + (xx // 8)) % 2).astype(float)
    rgb = N.lerp(N.solid(T, T, "#c9c1a8"), N.solid(T, T, "#4c4c44"), check) * (0.94 + 0.08 * N.spectral(T, T, 1.0, seed))[..., None]
    scuff = N.smoothstep(0.7, 0.9, N.spectral(T, T, 1.4, seed + 3, aniso=(1.0, 4.0)))
    rgb = rgb * (1 - 0.25 * scuff)[..., None]
    seams = (yy % 8 == 0) | (xx % 8 == 0)
    rgb = np.where(seams[..., None], rgb * 0.8, rgb)
    return _finish(rgb, np.where(seams, -0.6, 0.0), seed + 9, stain=0.45, smooth=0.4, porosity=0.2)


BLOCKS = {
    "faded_wallpaper": ("Faded Wallpaper", wallpaper_floral, 9601, "axe"),
    "striped_wallpaper": ("Striped Wallpaper", wallpaper_striped, 9602, "axe"),
    "wood_paneling": ("Wood Paneling", wood_paneling, 9603, "axe"),
    "stained_carpet": ("Stained Carpet", stained_carpet, 9604, "axe"),
    "linoleum_tiles": ("Linoleum Tiles", linoleum, 9605, "pickaxe"),
}


def build() -> None:
    names = {}
    for name, (english, fn, seed, tool) in BLOCKS.items():
        M.save(f"block/{name}", fn(seed))
        D.asset_json(f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        D.asset_json(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        D.item_model_definition(name)
        D.loot_self(name)
        names[f"block.{NS}.{name}"] = english
        tag = N.ROOT / "src" / "main" / "resources" / "data" / "minecraft" / "tags" / "block" / "mineable" / f"{tool}.json"
        table = json.loads(tag.read_text(encoding="utf-8"))
        if f"{NS}:{name}" not in table["values"]:
            table["values"].append(f"{NS}:{name}")
        tag.write_text(json.dumps(table, indent="\t") + "\n", encoding="utf-8")
    D.merge_lang(names)
    print(f"  interiors: {len(BLOCKS)} materials")


if __name__ == "__main__":
    build()
