"""Infected nests (Milestone 4): the five nest cores, biomass and its floor growth, the tissue sample
item, their models, blockstates, drops, tags and names. (The wall veins are a decal: gen_decals.py.)

A nest core is a lumpy mound with sacs, built from boxes; each kind has its own surface: cloth and
mattress stuffing caught in the flesh (residential), rust and oil (industrial), camouflage netting
(military), roots and soil (underground), and the dark, vein-lit hive of an advanced nest.
"""
from __future__ import annotations

import numpy as np

import dl_data as D
import dl_materials as M
import dl_noise as N
from gen_decor import el, every

NS = D.NS
T = M.T
KINDS = {
    "residential": ("Residential Nest", "#c8b48a"),
    "industrial": ("Industrial Nest", "#7a4a2a"),
    "military": ("Military Nest", "#4a5236"),
    "underground": ("Underground Nest", "#5a4632"),
    "advanced": ("Advanced Nest", "#b03a2e"),
}


def flesh(seed: int, dark: str = "#3a1c1c", mid: str = "#6a3430", light: str = "#9a5a4a") -> M.Material:
    f = N.spectral(T, T, beta=2.2, seed=seed)
    cells = M.worley_edges(T, T, 14, seed + 1)
    rgb = N.ramp(f, [(0.0, dark), (0.6, mid), (1.0, light)])
    rgb = N.lerp(rgb, N.solid(T, T, "#2a1012"), cells * 0.55)
    veins = np.abs(N.spectral(T, T, beta=1.3, seed=seed + 2) - 0.5) < 0.035
    rgb[veins] = rgb[veins] * 0.5 + np.array(N.hex_rgb("#5a1a2a")) * 0.5
    shine = N.smoothstep(0.82, 0.95, N.spectral(T, T, beta=1.0, seed=seed + 3))
    rgb = N.lerp(rgb, N.solid(T, T, "#c88a7a"), shine * 0.4)
    m = M.Material(np.clip(rgb, 0, 255), 0.5 + 0.4 * f - 0.3 * cells, smooth=0.55 + 0.3 * shine, porosity=0.2)
    return M.relief(m, 1.4)


def nest_surface(kind: str, seed: int) -> M.Material:
    m = flesh(seed)
    rgb = m.rgb.copy()
    accent = KINDS[kind][1]
    if kind == "residential":
        fibres = N.spectral(T, T, beta=1.0, seed=seed + 10, aniso=(1.0, 5.0)) > 0.78
        rgb[fibres] = np.array(N.hex_rgb(accent)) * 0.9
    elif kind == "industrial":
        stain = N.smoothstep(0.55, 0.8, N.spectral(T, T, beta=2.0, seed=seed + 10))
        rgb = N.lerp(rgb, N.solid(T, T, "#1a1410"), stain * 0.6)
        rust = N.spectral(T, T, beta=0.8, seed=seed + 11) > 0.9
        rgb[rust] = N.hex_rgb(accent)
    elif kind == "military":
        yy, xx = np.mgrid[0:T, 0:T]
        net = ((xx + yy) % 6 == 0) | ((xx - yy) % 6 == 0)
        holes = N.spectral(T, T, beta=2.0, seed=seed + 10) > 0.55
        rgb[net & holes] = np.array(N.hex_rgb(accent))
    elif kind == "underground":
        roots = np.abs(N.spectral(T, T, beta=1.5, seed=seed + 10) - 0.5) < 0.04
        rgb[roots] = N.hex_rgb("#4a3a28")
        soil = N.smoothstep(0.6, 0.8, N.spectral(T, T, beta=2.0, seed=seed + 11))
        rgb = N.lerp(rgb, N.solid(T, T, accent), soil * 0.7)
    else:  # advanced
        rgb = rgb * 0.7
        glow = np.abs(N.spectral(T, T, beta=2.2, seed=seed + 10) - 0.5) < 0.018
        rgb[glow] = N.hex_rgb("#ff6a3a")
    return M.Material(np.clip(rgb, 0, 255), m.height, smooth=m.smooth, porosity=m.porosity)


def sac(seed: int) -> M.Material:
    """Swollen egg-like sacs: translucent-looking membrane over dark shapes."""
    f = N.spectral(T, T, beta=2.5, seed=seed)
    rgb = N.ramp(f, [(0.0, "#6a4a3a"), (1.0, "#c8a088")])
    yy, xx = np.mgrid[0:T, 0:T]
    shape = ((xx - 16) ** 2 + (yy - 16) ** 2) < 60
    rgb[shape] = rgb[shape] * 0.55
    return M.relief(M.Material(np.clip(rgb, 0, 255), 0.6 + 0.3 * f, smooth=0.75, porosity=0.1), 1.2)


def tissue_item(seed: int = 4601):
    """A sealed specimen bag with a dark sample in it."""
    s = 32
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    bag = (xx >= 7) & (xx <= 24) & (yy >= 5) & (yy <= 27)
    rgb = np.zeros((s, s, 3))
    alpha = np.zeros((s, s))
    rgb[bag] = N.ramp(N.spectral(s, s, beta=1.5, seed=seed)[bag], [(0.0, "#a8b0ae"), (1.0, "#d8e0dc")])
    seal = bag & (yy <= 8)
    rgb[seal] = N.hex_rgb("#3a6a8a")
    blob = ((xx - 15.5) / 6.0) ** 2 + ((yy - 18) / 6.5) ** 2 <= 1.0
    rgb[blob] = N.ramp(N.spectral(s, s, beta=2.0, seed=seed + 1)[blob], [(0.0, "#2a0e10"), (1.0, "#6a2a26")])
    label = (xx >= 9) & (xx <= 14) & (yy >= 10) & (yy <= 12)
    rgb[label] = N.hex_rgb("#d8d0b8")
    alpha[bag] = 1.0
    from gen_items import _outline
    _outline(rgb, alpha)
    return np.clip(rgb, 0, 255), alpha


def nest_model(kind: str) -> dict:
    t = {"flesh": f"{NS}:block/nest/{kind}", "sac": f"{NS}:block/nest/sac", "particle": f"{NS}:block/nest/{kind}"}
    elements = [
        el((1, 0, 1), (15, 6, 15), every("flesh")),
        el((2, 6, 2), (14, 10, 14), every("flesh")),
        el((4, 10, 4), (12, 13, 12), every("flesh")),
        el((3, 6, 10), (7, 10, 14), every("sac")),
        el((10, 6, 3), (13, 9, 6), every("sac")),
        el((6, 13, 6), (9, 15, 9), every("sac")),
    ]
    return {"parent": "minecraft:block/block", "textures": t, "elements": elements}


def build() -> None:
    tags = D.TagCollector()
    lang = {}
    for i, (kind, (english, _)) in enumerate(KINDS.items()):
        M.save(f"block/nest/{kind}", nest_surface(kind, 9500 + i * 10))
        name = f"{kind}_nest"
        D.asset_json(f"models/block/{name}.json", nest_model(kind))
        D.asset_json(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        D.item_model_definition(name)
        D.data_json(f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:biomass", "functions": [
                    {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}]}]},
                {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:infected_tissue"}],
                 "conditions": [{"condition": "minecraft:random_chance", "chance": 0.9 if kind == "advanced" else 0.4}]},
            ],
            "random_sequence": f"{NS}:blocks/{name}",
        })
        tags.add("minecraft:mineable/hoe", f"{NS}:{name}")
        tags.add("minecraft:mineable/axe", f"{NS}:{name}")
        tags.add(f"{NS}:nest_material", f"{NS}:{name}")
        lang[f"block.{NS}.{name}"] = english
        lang[f"nest.{NS}.{kind}"] = english
        lang[f"chronicle.{NS}.nest_destroyed.{kind}"] = f"%s destroyed {('an ' if kind[0] in 'aeiou' else 'a ') + english.lower()}."
    M.save("block/nest/sac", sac(9560))
    M.save("block/nest/biomass", flesh(9570))
    biomass_top = flesh(9571, dark="#2e1618", mid="#5a2c2a", light="#8a4a3e")
    M.save("block/nest/biomass_growth", biomass_top)
    D.asset_json("models/block/biomass.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/nest/biomass"}})
    D.asset_json("blockstates/biomass.json", {"variants": {"": {"model": f"{NS}:block/biomass"}}})
    D.item_model_definition("biomass")
    D.loot_self("biomass")
    D.asset_json("models/block/biomass_growth.json", {"parent": "minecraft:block/carpet", "textures": {"wool": f"{NS}:block/nest/biomass_growth"}})
    D.asset_json("blockstates/biomass_growth.json", {"variants": {"": {"model": f"{NS}:block/biomass_growth"}}})
    D.item_model_definition("biomass_growth")
    D.loot_self("biomass_growth")
    for block in ("biomass", "biomass_growth"):
        tags.add("minecraft:mineable/hoe", f"{NS}:{block}")
        tags.add(f"{NS}:nest_material", f"{NS}:{block}")
    tags.write("nests")
    N.save_png(N.asset("textures/item/infected_tissue.png"), *tissue_item())
    D.asset_json("models/item/infected_tissue.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/infected_tissue"}})
    D.item_model_definition("infected_tissue", f"{NS}:item/infected_tissue")
    lang.update({"block.deadlands.biomass": "Biomass", "block.deadlands.biomass_growth": "Biomass Growth",
                 "item.deadlands.infected_tissue": "Infected Tissue Sample"})
    D.merge_lang(lang)


if __name__ == "__main__":
    build()
    print("nests written")
