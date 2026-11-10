"""JSON helpers shared by the generators: blockstates, models, loot tables, tags and language files."""
from __future__ import annotations

import json
from pathlib import Path

import dl_noise as N

NS = "deadlands"
DATA = N.ROOT / "src" / "main" / "resources" / "data"
LANG = N.ASSETS / "lang" / "en_us.json"


def write_json(path: Path, obj) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent="\t") + "\n", encoding="utf-8")
    return path


def asset_json(rel: str, obj) -> Path:
    return write_json(N.ASSETS / rel, obj)


# ----------------------------------------------------------------------------- survival items in loot (Milestone 5)
# The loot generators were written with vanilla items standing in for DEADLANDS food, drink and
# medicine. Every container and entity loot table passes through here on its way to disk, where the
# stand-ins are swapped for the real items and a few items no stand-in covered are added.
STAND_INS = {
    "minecraft:bread": "crackers", "minecraft:apple": "canned_fruit", "minecraft:cookie": "energy_bar", "minecraft:dried_kelp": "jerky",
    "minecraft:baked_potato": "canned_beans", "minecraft:honey_bottle": "soda_can", "minecraft:glass_bottle": "empty_bottle",
    "minecraft:mushroom_stew": "canned_soup", "minecraft:beetroot_soup": "canned_soup", "minecraft:golden_carrot": "mre",
    "minecraft:cooked_beef": "jerky", "minecraft:cooked_mutton": "jerky", "minecraft:cooked_rabbit": "jerky",
    "minecraft:pumpkin_pie": "canned_fruit", "minecraft:cake": "canned_fruit", "minecraft:golden_apple": "medkit",
    "minecraft:milk_bucket": "water_bottle", "minecraft:rotten_flesh": "spoiled_can", "minecraft:spider_eye": "spoiled_can",
    "minecraft:sugar": "energy_drink", "minecraft:fermented_spider_eye": "antiseptic", "minecraft:glistering_melon_slice": "antibiotics",
}
POTION_STAND_INS = {"minecraft:healing": "bandage", "minecraft:strong_healing": "medkit", "minecraft:regeneration": "antibiotics",
                    "minecraft:fire_resistance": "painkillers"}
# (words in the table's name, [(item, weight)]) : extra entries for the table's main pool.
LOOT_EXTRAS = [
    (("medical_cabinet", "clinic", "pharmacy", "research_lab", "outfit/medical", "outfit/lab"),
     [("splint", 3), ("painkillers", 4), ("antiseptic", 4), ("dirty_rag", 2), ("antiviral", 1)]),
    (("kitchen", "fridge", "store_storage", "gas_station", "campsite", "house_"),
     [("water_bottle", 6), ("canned_soup", 4), ("stale_bread", 3), ("spoiled_can", 2), ("dirty_water_bottle", 2), ("purification_tablet", 2)]),
    (("gas_station",), [("fuel_can", 4), ("tire", 3), ("car_battery", 2), ("engine_parts", 2), ("wrench", 2)]),
    (("military_crate", "police", "hunting", "outfit/military", "fire_station"),
     [("mre", 4), ("water_bottle", 4), ("bandage", 3), ("purification_tablet", 3), ("pistol_ammo", 5), ("pistol", 1), ("machete", 1)]),
    (("toolbox", "workshop", "fire_station", "farm", "utility"), [("crowbar", 2), ("fire_axe", 1), ("wrench", 3), ("engine_parts", 4), ("tire", 3),
                                                                  ("car_battery", 2), ("body_panel", 3), ("fuel_can", 2)]),
    (("house_bedroom", "school_locker", "supply_locker"), [("baseball_bat", 1), ("pistol_ammo", 1), ("backpack", 1)]),
    (("military_crate", "outfit/military"), [("military_backpack", 1), ("rifle_round", 2)]),
    (("hunting", "farm", "police"), [("shotgun", 1), ("shotgun_shell", 4), ("hunting_rifle", 1), ("rifle_round", 3)]),
]


def survival_loot(rel: str, table: dict) -> dict:
    def swap(node):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:item":
                name = node.get("name")
                if name == "minecraft:potion":
                    potion = next((f.get("id") for f in node.get("functions", []) if f.get("function") == "minecraft:set_potion"), None)
                    node["name"] = f"{NS}:{POTION_STAND_INS.get(potion, 'water_bottle')}"
                    node["functions"] = [f for f in node.get("functions", []) if f.get("function") != "minecraft:set_potion"]
                    if not node["functions"]:
                        del node["functions"]
                elif name in STAND_INS:
                    node["name"] = f"{NS}:{STAND_INS[name]}"
            for value in node.values():
                swap(value)
        elif isinstance(node, list):
            for value in node:
                swap(value)

    swap(table)
    pools = [p for p in table.get("pools", []) if any("weight" in e for e in p.get("entries", []))]
    if pools:
        main = max(pools, key=lambda p: len(p["entries"]))
        present = {e.get("name") for e in main["entries"]}
        for words, extras in LOOT_EXTRAS:
            if any(w in rel for w in words):
                for item, weight in extras:
                    if f"{NS}:{item}" not in present:
                        main["entries"].append({"type": "minecraft:item", "name": f"{NS}:{item}", "weight": weight})
                        present.add(f"{NS}:{item}")
    return table


def data_json(rel: str, obj, ns: str = NS) -> Path:
    if ns == NS and rel.startswith("loot_table/") and not rel.startswith("loot_table/blocks/"):
        obj = survival_loot(rel, obj)
    return write_json(DATA / ns / rel, obj)


def item_model_definition(name: str, model: str | None = None) -> None:
    asset_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": model or f"{NS}:block/{name}"}})


def loot_self(name: str, slab: bool = False) -> None:
    entry = {"type": "minecraft:item", "name": f"{NS}:{name}"}
    functions = []
    if slab:
        functions.append({
            "function": "minecraft:set_count",
            "count": 2.0,
            "add": False,
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:{name}", "properties": {"type": "double"}}],
        })
    functions.append({"function": "minecraft:explosion_decay"})
    entry["functions"] = functions
    data_json(f"loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}] if not slab else []}],
        "random_sequence": f"{NS}:blocks/{name}",
    })


class TagCollector:
    """Collects block ids per tag and writes merged tag files (replace: false)."""

    def __init__(self) -> None:
        self.tags: dict[str, list[str]] = {}

    def add(self, tag: str, block_id: str) -> None:
        self.tags.setdefault(tag, [])
        if block_id not in self.tags[tag]:
            self.tags[tag].append(block_id)

    def write(self, owner: str) -> None:
        """Merge into existing tag files so several generators can contribute to the same tag."""
        for tag, values in self.tags.items():
            ns, path = tag.split(":", 1)
            file = DATA / ns / "tags" / "block" / f"{path}.json"
            existing = []
            if file.exists():
                existing = json.loads(file.read_text(encoding="utf-8")).get("values", [])
            merged = existing + [v for v in values if v not in existing]
            write_json(file, {"replace": False, "values": merged})


def merge_lang(entries: dict[str, str]) -> None:
    """Add or update English strings, keeping the existing order and hand-written entries."""
    lang = json.loads(LANG.read_text(encoding="utf-8")) if LANG.exists() else {}
    lang.update(entries)
    LANG.write_text(json.dumps(lang, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")


def title(name: str) -> str:
    return " ".join(w.capitalize() for w in name.split("_"))
