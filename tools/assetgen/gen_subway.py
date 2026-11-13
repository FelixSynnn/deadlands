"""The abandoned subway (structure deadlands:subway_station): stations with their tunnels, trains and rooms.

Each station is one template, 96 x 18 x 30, built underground with its street entrance at the template's
origin corner (where the surface height is sampled, so the entrance meets the ground):

  x 0-5     the street kiosk (ground at y = D) and the stairs down, along z 21-25;
  x 0-15    the west tunnel (z 6-14), ending in a collapse;
  x 16-63   the station hall: platforms (z 1-6 and 14-19, walked at y = 2) either side of the track
            trench (z 7-13), the rail at z 10, a stationary train on it; tiled, lit by fluorescent tubes
            (some dead, some flickering) and emergency lights;
  x 16-63   behind the south platform, a band of rooms (z 21-28): the ticket hall with its booths, a
            maintenance room, storage, an office, a survivor hideout behind a barricade, a utility room;
  x 64-95   the east tunnel, flooded, collapsed, or blocked by a train, depending on the station.

Variants differ in every room's role and state, the train (none, one car, two cars, derailed, wrecked,
a car stuck in the tunnel), which doors are open, flooding, lights, the hideout's story (recent,
long gone, ended badly), and the clutter. The trains can't be driven: they are blocks.

Also writes the loot tables (luggage, rare luggage, maintenance, ticket booths, hideouts), the structure's
worldgen files and its structure tag (story/SubwayPlaces looks stations up by it).
"""
from __future__ import annotations

import dl_data as D
from gen_sites import Site
from gen_towns import item, key, loot_table

NS = D.NS
OUT = D.DATA / NS / "structure" / "subway"
D_DEPTH = 12          # the surface is at template y = 12
SX, SY, SZ = 96, D_DEPTH + 6, 30
HALL = (16, 63)
WALL = ["dirty_concrete", "stained_concrete", "cracked_concrete", "stained_concrete"]
TILE = ["hospital_tiles", "hospital_tiles", "cracked_hospital_tiles"]


class Station(Site):
    def wall(self, x, y, z):
        self.put(x, y, z, self.pick(WALL))

    def shell(self, x0, y0, z0, x1, y1, z1):
        """Concrete walls, floor and ceiling around an air box (only where nothing else has gone)."""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    edge = x in (x0, x1) or y in (y0, y1) or z in (z0, z1)
                    if edge:
                        if (x, y, z) not in self.blocks:
                            self.wall(x, y, z)
                    else:
                        self.put(x, y, z, "minecraft:air")

    def tube(self, x, y, z, axis="x"):
        lit = not self.chance(0.3)
        self.put(x, y, z, "fluorescent_light", axis=axis, lit=lit, faulty=lit and self.chance(0.35))

    def emergency(self, x, y, z, facing):
        self.put(x, y, z, "emergency_light", facing=facing, lit=not self.chance(0.25), pulse=self.chance(0.5))


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


def loot() -> None:
    loot_table("train_luggage", (1, 2), [
        item("paper", 10, (1, 3)), item("book", 4), item("leather", 3), item("string", 3, (1, 2)), item("bread", 3),
        item("apple", 3), item(f"{NS}:bandage", 2), item(f"{NS}:water_bottle", 2), item("clock", 1), item("compass", 1),
        item("name_tag", 1), empty(12)])
    loot_table("train_luggage_rare", (1, 2), [
        item(f"{NS}:medkit", 2), item(f"{NS}:antibiotics", 2), item(f"{NS}:canned_beans", 4, (1, 2)), item("iron_ingot", 2),
        item("gold_ingot", 1), item(f"{NS}:purification_tablet", 2), empty(6)])
    loot_table("subway_maintenance", (1, 3), [
        item(f"{NS}:fuse", 4), item("redstone", 6, (1, 4)), item("iron_nugget", 6, (2, 6)), item("copper_ingot", 4, (1, 3)),
        item("torch", 6, (2, 4)), key("maintenance", 2), empty(4)])
    loot_table("subway_ticket", (1, 2), [
        item("paper", 10, (1, 4)), item("gold_nugget", 6, (1, 4)), item("map", 3), item("clock", 1), empty(6)])
    loot_table("subway_hideout", (2, 4), [
        item(f"{NS}:canned_beans", 6, (1, 2)), item(f"{NS}:water_bottle", 5), item(f"{NS}:bandage", 4, (1, 2)),
        item(f"{NS}:purification_tablet", 3), item("torch", 4, (1, 3)), item("bread", 3), item("candle", 2), empty(2)])
    loot_table("subway_hideout_old", (0, 2), [
        item("bowl", 6), item("paper", 6, (1, 3)), item("string", 3), item("rotten_flesh", 4, (1, 2)), item("bone", 2), empty(8)])


def kiosk(b: Station) -> None:
    """The street entrance: a concrete kiosk at the surface over stairs that go down eastward to the ticket hall."""
    g = D_DEPTH
    # The stairwell: x 4-15, z 22-24; floor rises one per block westward from y = 2 (x = 15) to y = 12 (x = 5).
    for x in range(4, 17):
        floor = min(g, 2 + max(0, 15 - x))
        for z in range(21, 26):
            for y in range(max(0, floor - 2), min(SY, floor + 5)):
                edge = z in (21, 25) or y == floor - 1 or y == floor + 4
                if edge and (x, y, z) not in b.blocks:
                    b.wall(x, y, z)
            for z in range(22, 25):
                b.put(x, floor - 1, z, "stained_concrete")
                for y in range(floor, min(SY, floor + 4)):
                    b.put(x, y, z, "minecraft:air")
        if x < 15 and 2 + (15 - x) <= g:
            for z in range(22, 25):
                b.put(x, floor - 1, z, "minecraft:stone_brick_stairs", facing="west", half="bottom")
    # The kiosk at the surface: walls round the top of the stairs, a slab roof, an opening to the west.
    for x in range(0, 6):
        for z in range(20, 27):
            if x in (0, 5) or z in (20, 26):
                for y in range(g, g + 4):
                    b.put(x, y, z, "stained_concrete" if y < g + 3 else "concrete")
            else:
                for y in range(g, g + 3):
                    b.put(x, y, z, "minecraft:air")
            b.put(x, g + 3, z, "concrete")
    for z in range(22, 25):
        for y in range(g, g + 3):
            b.put(0, y, z, "minecraft:air")  # the way in, from the west
    b.decal(1, g + 1, 21, "evacuation_notice", face="wall", facing="south")
    b.decal(4, g + 1, 25, b.pick(["evacuation_notice", "survivor_mark"]), face="wall", facing="north")
    b.emergency(4, g + 2, 21, "south")


def tunnel(b: Station, x0: int, x1: int, end: str, flooded: bool) -> None:
    """A track tunnel (z 6-14, y 0-7) from x0 to x1; `end` is the dead end at x0 or x1: collapse or flood."""
    for x in range(x0, x1 + 1):
        for z in range(6, 15):
            for y in range(0, 8):
                edge = z in (6, 14) or y in (0, 7) or (y == 6 and z in (7, 13))
                if edge:
                    if (x, y, z) not in b.blocks:
                        b.wall(x, y, z)
                else:
                    b.put(x, y, z, "minecraft:air")
            b.put(x, 1, z, "minecraft:gravel") if 7 <= z <= 13 else None
        if not (flooded and b.chance(0.0)):
            if b.chance(0.85):
                b.put(x, 2, 10, "minecraft:rail", shape="east_west")
        if x % 8 == 3:
            b.emergency(x, 5, 7, "south")
        if x % 8 == 7 and b.chance(0.5):
            b.put(x, 4, 13, "industrial_panel" if b.chance(0.6) else "corroded_metal")
    if flooded:
        for x in range(x0, x1 + 1):
            depth = 2 if abs(x - (x1 if end == "east" else x0)) < 12 else 1
            for z in range(7, 14):
                b.put(x, 1, z, "minecraft:mud" if depth == 1 and b.chance(0.4) else "minecraft:gravel")
                for y in range(2, 2 + depth):
                    b.put(x, y, z, "minecraft:water")
    # The dead end: a collapse of rubble and stone, or rubble in the water.
    ex = x0 if end == "west" else x1
    step = 1 if end == "west" else -1
    for i in range(0, 5):
        x = ex + i * step
        for z in range(7, 14):
            for y in range(2, 7 - i):
                if b.chance(0.85 - i * 0.12):
                    b.put(x, y, z, b.pick(["rubble", "minecraft:cobblestone", "broken_concrete", "minecraft:gravel"]))
    b.clutter(min(x0, x1) + 5, 7, max(x0, x1) - 5, 13, y=2, blood=0.01, rubble=0.03, webs=0.05)


def train_car(b: Station, x0: int, z_shift: int = 0, doors_open=0.6, wrecked=False, luggage_rare=0.15) -> None:
    """One car, 17 long, on the track (z 8-12), floor at y = 2. It can't be driven: it is blocks."""
    zn, zs = 8 + z_shift, 12 + z_shift
    x1 = x0 + 16
    body = b.pick(["industrial_panel", "corroded_metal", "rusted_sheet_metal"])
    for x in range(x0, x1 + 1):
        for z in range(zn, zs + 1):
            b.put(x, 2, z, "diamond_plate")
            b.put(x, 6, z, body if not (wrecked and b.chance(0.15)) else "minecraft:air")
            for y in range(3, 6):
                if z in (zn, zs) or x in (x0, x1):
                    if y == 4 and x not in (x0, x1) and x % 3 != 0:
                        b.put(x, y, z, "minecraft:glass_pane" if not b.chance(0.3 if wrecked else 0.12) else "broken_glass")
                    else:
                        b.put(x, y, z, body)
                else:
                    b.put(x, y, z, "minecraft:air")
    # Doors on the platform side (north), open or shut.
    for dx in (3, 8, 13):
        x = x0 + dx
        if b.chance(doors_open):
            for y in (3, 4):
                b.put(x, y, zn, "minecraft:air")
        else:
            b.put(x, 3, zn, "minecraft:iron_bars")
            b.put(x, 4, zn, "minecraft:glass_pane")
    # Inside: seats along both sides, luggage, papers, sometimes the people who never got off.
    for x in range(x0 + 1, x1):
        if x % 4 in (1, 2):
            b.decor(x, 3, zn + 1, "couch", "south")
            b.decor(x, 3, zs - 1, "couch", "north")
        elif b.chance(0.12):
            b.container(x, 3, zn + 2, "toolbox", b.pick(["east", "west"]), "train_luggage_rare" if b.chance(luggage_rare) else "train_luggage")
        elif b.chance(0.2):
            b.decal(x, 3, zn + 2, b.pick(["scattered_papers", "scattered_papers", "dried_blood_spots", "drag_marks"]))
        if b.chance(0.06):
            b.put(x, 5, zn + 2, "minecraft:cobweb")
    b.tube(x0 + 8, 5, zn + 2)
    if wrecked:
        for _ in range(6):
            x = int(b.r.integers(x0, x1 + 1))
            b.put(x, 3, zn + 2, "rubble")


def platforms(b: Station) -> None:
    hx0, hx1 = HALL
    b.shell(hx0, 0, 0, hx1, 8, 20)
    for x in range(hx0 + 1, hx1):
        for z in range(1, 20):
            if 7 <= z <= 13:
                b.put(x, 1, z, "minecraft:gravel")
                if b.chance(0.92):
                    b.put(x, 2, 10, "minecraft:rail", shape="east_west")
            else:
                b.put(x, 1, z, b.pick(TILE))
        b.put(x, 1, 6, "asphalt_yellow_line")
        b.put(x, 1, 14, "asphalt_yellow_line")
        if x % 6 == 2:
            b.tube(x, 7, 3)
            b.tube(x, 7, 17)
        if x % 12 == 5:
            b.emergency(x, 5, 1, "south")
            b.emergency(x, 5, 19, "north")
        if x % 9 == 4:
            b.decal(x, 3, 1, b.pick(["evacuation_notice", "survivor_mark", "evacuation_notice"]), face="wall", facing="south")
        if x % 7 == 0 and b.chance(0.5):
            b.decor(x, 2, 2, "chair", "south")
    # The hall's ends open into the tunnels on the track.
    for x in (hx0, hx1):
        for z in range(7, 14):
            for y in range(2, 7):
                b.put(x, y, z, "minecraft:air")
            b.put(x, 1, z, "minecraft:gravel")
    b.clutter(hx0 + 1, 1, hx1 - 1, 6, y=2, blood=0.02, rubble=0.02, webs=0.03)
    b.clutter(hx0 + 1, 14, hx1 - 1, 19, y=2, blood=0.02, rubble=0.02, webs=0.03)
    for x in range(hx0 + 1, hx1, 5):
        if b.chance(0.3):
            b.decal(x, 2, int(b.r.integers(2, 6)), "scattered_papers")


def rooms(b: Station, roles: list[str], hideout: str) -> None:
    """The band behind the south platform: six rooms, each 8 wide (x), doors onto the platform."""
    for i, role in enumerate(roles):
        x0 = HALL[0] + i * 8
        x1 = x0 + 8
        b.shell(x0, 0, 20, x1, 6, 29)
        for x in range(x0 + 1, x1):
            for z in range(21, 29):
                b.put(x, 1, z, b.pick(TILE) if role in ("ticket", "office") else b.pick(["dirty_concrete", "stained_concrete"]))
        door_x = x0 + 4
        if role == "hideout":
            # Behind a barricade: a gap in boards and crates.
            for x in range(x0 + 1, x1):
                b.put(x, 2, 20, "boarded_planks" if x != door_x else "minecraft:air")
                b.put(x, 3, 20, "boarded_planks" if x != door_x or hideout == "sealed" else "minecraft:air")
        else:
            for y in (2, 3):
                b.put(door_x, y, 20, "minecraft:air")
        b.tube(x0 + 4, 5, 25, axis="z")
        fill_room(b, role, x0, x1, hideout)


def fill_room(b: Station, role: str, x0: int, x1: int, hideout: str) -> None:
    mid = (x0 + x1) // 2
    if role == "ticket":
        for x in range(x0 + 1, x1):
            b.put(x, 2, 23, "counter" if x % 3 else "minecraft:glass_pane")
            b.put(x, 3, 23, "minecraft:glass_pane" if b.chance(0.7) else "broken_glass")
        b.container(x0 + 2, 2, 25, "filing_cabinet", "north", "subway_ticket")
        b.container(x0 + 6, 2, 25, "safe" if b.chance(0.2) else "filing_cabinet", "north", "subway_ticket")
        b.decal(x0 + 4, 3, 28, "evacuation_notice", face="wall", facing="north")
        b.decal(mid, 2, 22, "scattered_papers")
        # The way through to the stairs (x 16 room opens west onto the stairwell).
        for z in range(22, 25):
            for y in (2, 3, 4):
                b.put(x0, y, z, "minecraft:air")
    elif role == "maintenance":
        b.decor(x0 + 1, 2, 27, "workbench", "north")
        b.container(x0 + 3, 2, 28, "toolbox", "north", "subway_maintenance")
        b.container(x0 + 6, 2, 28, "supply_locker", "north", "subway_maintenance")
        b.decor(x1 - 1, 2, 22, "power_generator", "west")
        for z in (24, 26):
            b.put(x1 - 1, 3, z, "industrial_panel" if b.chance(0.5) else "corroded_metal")
    elif role == "storage":
        for x in range(x0 + 1, x1):
            if b.chance(0.5):
                b.container(x, 2, 28, b.pick(["military_crate", "supply_locker"]), "north", "subway_maintenance" if b.chance(0.4) else "train_luggage")
        b.clutter(x0 + 1, 21, x1 - 1, 27, y=2, blood=0.0, rubble=0.05, webs=0.15)
    elif role == "office":
        b.decor(mid, 2, 26, "desk", "north")
        b.decor(mid, 2, 25, "chair", "south")
        b.container(x0 + 1, 2, 28, "filing_cabinet", "north", "subway_ticket")
        b.decor(x1 - 1, 3, 25, "whiteboard", "west")
        b.decal(mid + 1, 2, 23, "scattered_papers")
    elif role == "hideout":
        hideout_room(b, x0, x1, hideout)
    else:  # utility: dark, flooded floor, panels
        for x in range(x0 + 1, x1):
            for z in range(21, 29):
                if b.chance(0.5):
                    b.put(x, 2, z, "minecraft:water")
        b.put(x0 + 2, 3, 28, "industrial_panel")
        b.put(x0 + 4, 3, 28, "corroded_metal")


def hideout_room(b: Station, x0: int, x1: int, story: str) -> None:
    """Survivors lived here. `story`: recent (warm, stocked), gone (long empty), bad (it ended badly), sealed."""
    beds = [(x0 + 1, 27), (x0 + 3, 27), (x0 + 6, 27)]
    for i, (x, z) in enumerate(beds[: 2 + (story != "gone")]):
        b.put(x, 2, z, b.pick(["minecraft:brown_carpet", "minecraft:gray_carpet", "minecraft:green_carpet"]))
        b.put(x, 2, z - 1, "minecraft:white_carpet")  # a rolled-up blanket for a pillow
    b.decal(x1 - 1, 3, 28, "survivor_mark", face="wall", facing="north")
    b.decal(x0 + 4, 2, 23, "scattered_papers")
    if story == "recent":
        b.container(x1 - 1, 2, 22, "supply_locker", "west", "subway_hideout")
        b.put(x0 + 4, 2, 25, "minecraft:lantern")
        b.put(x0 + 1, 2, 22, "minecraft:water_cauldron", level=3)
        b.put(x0 + 2, 2, 22, "minecraft:candle", candles=3, lit=True)
    elif story == "gone":
        b.container(x1 - 1, 2, 22, "supply_locker", "west", "subway_hideout_old")
        b.put(x0 + 4, 2, 25, "minecraft:candle", candles=1, lit=False)
        b.clutter(x0 + 1, 21, x1 - 1, 27, y=2, blood=0.0, rubble=0.03, webs=0.25)
    elif story == "bad":
        b.container(x1 - 1, 2, 22, "supply_locker", "west", "subway_hideout_old")
        b.clutter(x0 + 1, 21, x1 - 1, 27, y=2, blood=0.12, rubble=0.04, webs=0.05)
        b.decal(x0 + 4, 3, 20, "bloody_handprint", face="wall", facing="south")
        b.put(x0 + 3, 2, 21, "rubble")
    else:  # sealed: boarded in from inside, still stocked
        b.container(x1 - 1, 2, 22, "supply_locker", "west", "subway_hideout")
        b.put(x0 + 4, 2, 25, "minecraft:lantern")


def station(seed: int, train: str, east: str, roles: list[str], hideout: str) -> Station:
    b = Station(SX, SY, SZ, seed)
    platforms(b)
    rooms(b, roles, hideout)
    tunnel(b, 0, HALL[0] - 1, "west", flooded=False)
    tunnel(b, HALL[1] + 1, SX - 1, "east", flooded=east == "flooded")
    kiosk(b)
    if train in ("one", "two", "derailed", "wrecked"):
        train_car(b, 20, doors_open=0.7, wrecked=train == "wrecked")
    if train in ("two", "derailed"):
        train_car(b, 39, z_shift=1 if train == "derailed" else 0, doors_open=0.4)
        if train == "derailed":
            for x in range(36, 58):
                b.put(x, 2, 10, "minecraft:air") if b.chance(0.6) else None
                if b.chance(0.2):
                    b.put(x, 2, int(b.r.integers(7, 10)), "rubble")
    if east == "train":
        train_car(b, 70, doors_open=1.0, wrecked=True, luggage_rare=0.3)  # a car stuck in the tunnel: the way on is through it
    return b


VARIANTS = [
    ("station_a", 9301, "two", "collapse", ["ticket", "maintenance", "office", "hideout", "storage", "utility"], "recent"),
    ("station_b", 9302, "derailed", "flooded", ["ticket", "storage", "hideout", "maintenance", "utility", "office"], "bad"),
    ("station_c", 9303, "one", "train", ["ticket", "office", "maintenance", "storage", "hideout", "utility"], "gone"),
    ("station_d", 9304, "wrecked", "collapse", ["ticket", "hideout", "storage", "office", "maintenance", "utility"], "sealed"),
    ("station_e", 9305, "none", "flooded", ["ticket", "maintenance", "hideout", "office", "utility", "storage"], "recent"),
]


def worldgen() -> None:
    D.data_json("tags/worldgen/biome/has_structure/subway_station.json", {"replace": False, "values": ["#deadlands:is_deadlands"]})
    D.data_json("tags/worldgen/structure/subway.json", {"replace": False, "values": [f"{NS}:subway_station"]})
    D.data_json("worldgen/structure/subway_station.json", {
        "type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/subway_station", "step": "underground_structures",
        "terrain_adaptation": "none", "spawn_overrides": {}, "start_pool": f"{NS}:subway/station", "size": 1,
        "start_height": {"absolute": -D_DEPTH}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 116, "use_expansion_hack": False})
    D.data_json("worldgen/template_pool/subway/station.json", {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:subway/{name}",
                                               "processors": "minecraft:empty", "projection": "rigid"}} for name, *_ in VARIANTS]})
    D.data_json("worldgen/structure_set/subway_station.json", {
        "structures": [{"structure": f"{NS}:subway_station", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 26, "separation": 10, "salt": 77100171,
                      "exclusion_zone": {"other_set": f"{NS}:towns", "chunk_count": 6}}})


def build() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, seed, train, east, roles, hideout in VARIANTS:
        station(seed, train, east, roles, hideout).write(OUT / f"{name}.nbt")
    worldgen()
    loot()
    D.merge_lang({"structure.deadlands.subway_station": "Subway Station"})
    print(f"  subway: {len(VARIANTS)} stations")
    return len(VARIANTS)


if __name__ == "__main__":
    build()
