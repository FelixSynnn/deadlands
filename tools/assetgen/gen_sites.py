"""Standalone DEADLANDS sites (Milestone 3, second half): the overgrown school, forest outposts,
campsites and evacuation camps, military posts and bunkers, utility sites (radio and water towers,
substations), a power station, a water treatment plant, farm silos and wind pumps, and a research
outpost. Each is a structure template placed by a single-piece jigsaw structure; variants share a
template pool. Also writes their worldgen files, loot tables, biome tags and English text, and
the language strings for the Milestone 3 systems written in Java (cities, power, road kinds).

Templates follow the town rules where they can (front to the north, explicit air above the
ground, worn ground at the ground level); unlike town buildings they may be larger than a plot and
reach below the ground (the school's basement, the bunker).
"""
from __future__ import annotations

import numpy as np

import dl_data as D
from dl_nbt import Structure
from gen_city import STOREY, overgrow, stairs
from gen_towns import Building, bedroom, dining, item, kitchen, key, loot_table, potion, worn

NS = D.NS
SITES = D.DATA / NS / "structure" / "site"


class Site(Building):
    """A building without the town plot limit."""

    def __init__(self, sx: int, sy: int, sz: int, seed: int) -> None:
        Structure.__init__(self, sx, sy, sz)
        self.r = np.random.default_rng(seed)

    def seal_at(self, ground_y: int, ground="minecraft:coarse_dirt") -> "Site":
        """Ground at `ground_y` and air above it wherever nothing was placed; below is left to the terrain."""
        sx, sy, sz = self.size
        for x in range(sx):
            for z in range(sz):
                if (x, ground_y, z) not in self.blocks:
                    self.put(x, ground_y, z, ground if self.r.random() < 0.8 else self.pick(["minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"]))
                for y in range(ground_y + 1, sy):
                    if (x, y, z) not in self.blocks:
                        self.put(x, y, z, "minecraft:air")
        return self


def free(b: Building, x, y, z) -> bool:
    cell = b.blocks.get((x, y, z))
    return cell is None or cell[0] == "minecraft:air"


# ----------------------------------------------------------------------------- the overgrown school

SCHOOL_W, SCHOOL_D = 40, 30
G = 4  # ground floor slab height inside the template (the basement is below)


def school(seed: int, history: str) -> Site:
    """Lincoln-style single-storey school with a basement. `history`: evacuated (orderly; notices,
    buses never came), shelter (classrooms barricaded, people lived in the gym), overrun (it fell:
    blood, a failed barricade, a nest in the basement)."""
    h = 5
    b = Site(SCHOOL_W, G + h + 3, SCHOOL_D, seed)
    x0, z0, x1, z1 = 0, 0, SCHOOL_W - 1, SCHOOL_D - 1
    # Basement: concrete box under the whole building.
    b.box(x0, 0, z0, x1, G, z1, "stained_concrete")
    b.air(x0 + 1, 1, z0 + 1, x1 - 1, G - 1, z1 - 1)
    # Ground floor shell.
    b.box(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, "cracked_hospital_tiles")
    b.walls(x0, z0, x1, z1, G + 1, G + h, "weathered_bricks", corner="concrete")
    b.air(x0 + 1, G + 1, z0 + 1, x1 - 1, G + h, z1 - 1)
    stand = G + 1
    top = G + h

    def wall_x(x, za, zb, door=None):
        b.partition_x(x, za, zb, stand, top, "peeling_plaster", door_z=door)

    def wall_z(z, xa, xb, door=None):
        b.partition_z(z, xa, xb, stand, top, "peeling_plaster", door_x=door)

    # Corridor z 10..12 across the building; rooms north and south of it.
    wall_z(9, x0 + 1, x1 - 1)
    wall_z(13, x0 + 1, x1 - 1)
    # North rooms: principal 1-6 | teachers 8-14 | lobby 16-23 | nurse 25-30 | bathrooms 32-35 | storage 37-38.
    for x, door in ((7, None), (15, None), (24, None), (31, None), (36, None)):
        wall_x(x, z0 + 1, 8)
    for x, door_x in ((3, 3), (11, 11), (27, 27), (33, 33), (37, 37)):
        b.air(door_x, stand, 9, door_x, stand + 1, 9)
    b.air(16, stand, 9, 23, top - 1, 9)  # the lobby opens onto the corridor
    b.door(19, z0, width=2, height=3)
    # South rooms: classrooms A 1-9, B 11-19, C 21-29 (z 14-21); cafeteria 1-14 and library 16-29 (z 23-28); gym 31-38 (z 14-28).
    for x in (10, 20, 30):
        wall_x(x, 14, 21)
    wall_z(22, x0 + 1, 29)
    wall_x(15, 23, z1 - 1)
    wall_x(30, 22, z1 - 1)
    for door_x in (5, 15, 25, 34, 35):
        b.air(door_x, stand, 13, door_x, stand + 1, 13)
    for door_x in (7, 22):
        b.air(door_x, stand, 22, door_x, stand + 1, 22)
    b.air(15, stand, 25, 15, stand + 1, 25)

    # Windows everywhere outside, most of them broken.
    b.windows(x0, z0, x1, z1, stand + 1, spacing=2, skip=[(19, z0), (20, z0)], tall=True, glass=0.35, boarded=0.15)
    if history == "shelter":
        b.windows(x0, z0, x1, z1, stand + 1, spacing=2, skip=[(19, z0), (20, z0)], tall=True, glass=0.05, boarded=0.85)

    # Corridor lockers along both walls, some hanging open.
    for x in range(x0 + 2, x1 - 1, 2):
        if free(b, x, stand, 10) and x not in (3, 11, 16, 17, 18, 19, 20, 21, 22, 23, 27, 33, 37):
            b.container(x, stand, 10, "supply_locker", "south", "school_locker")
        if free(b, x, stand, 12) and x not in (5, 15, 25, 34, 35):
            b.container(x, stand, 12, "supply_locker", "north", "school_locker")
    for x in (12, 26):
        b.decal(x, stand + 1, 10, "evacuation_notice", face="wall", facing="south")
    b.decal(18, stand + 1, z0 + 1, "evacuation_notice", face="wall", facing="south")

    # Principal's office: desk, records, and the safe whose key is in the teachers' room.
    b.decor(3, stand, 4, "desk", "south")
    b.decor(3, stand, 5, "chair", "north")
    b.container(1, stand, 1, "filing_cabinet", "east", "school_office")
    b.container(1, stand, 2, "filing_cabinet", "east", "school_office", lock={"type": "key", "key": "records_room"})
    b.container(6, stand, 1, "safe", "west", "safe", lock={"type": "key", "key": "principal"})
    b.decor(5, stand, 8, "bookshelf", "north")
    # Teachers' room.
    dining(b, 11, 4, y=stand, chairs=("west", "east", "north"))
    b.decor(9, stand, 1, "couch", "south")
    b.container(14, stand, 1, "kitchen_cupboard", "west", "house_kitchen")
    b.container(14, stand, 2, "fridge", "west", "fridge")
    b.container(8, stand, 8, "filing_cabinet", "east", "school_key")
    # Lobby: the front desk and benches.
    for x in (17, 18):
        b.decor(x, stand, 4, "counter", "north")
    for x in (21, 22):
        b.decor(x, stand, 7, "chair", "north")
    # Nurse.
    b.decor(29, stand, 7, "bed_frame", "west")
    b.container(25, stand, 1, "medical_cabinet", "east", "clinic")
    b.container(30, stand, 1, "medical_cabinet", "west", "clinic", lock={"type": "key", "key": "pharmacy"})
    b.decor(25, stand, 5, "sink", "east")
    # Bathrooms.
    for z in (2, 4, 6):
        b.decor(35, stand, z, "toilet", "west")
        b.decor(32, stand, z, "sink", "east")
    # Storage and maintenance, with the stairs down.
    b.container(38, stand, 1, "toolbox", "west", "workshop")
    stairs(b, 37, 1, 1, stand, 1)

    # Classrooms: rows of desks facing the whiteboard on the corridor wall.
    rooms = [(1, 9), (11, 19), (21, 29)]
    for i, (ra, rb) in enumerate(rooms):
        b.decor((ra + rb) // 2, stand + 1, 14, "whiteboard", "south")
        b.decor(ra + 1, stand, 15, "desk", "south")
        for row in range(17, 21, 2):
            for x in range(ra + 1, rb, 2):
                if history == "overrun" and b.chance(0.3):
                    b.put(x, stand, row, "rubble")  # thrown aside
                elif b.chance(0.85):
                    b.container(x, stand, row, "school_desk", "north", "school_desk")
        b.decor(rb, stand, 21, "bookshelf", "west")
        b.decal(ra + 2, stand, 16, "scattered_papers")
        b.decal(rb - 1, stand, 19, "scattered_papers")
    if history in ("shelter", "overrun"):
        # Classroom B was barricaded from the inside: boards across the doorway, desks piled against it.
        b.put(15, stand, 13, "boarded_planks")
        b.put(15, stand + 1, 13, "boarded_planks")
        b.decor(14, stand, 14, "table", "north")
        b.decal(13, stand + 1, 13, "survivor_mark", face="wall", facing="south")
        if history == "overrun":
            b.put(15, stand, 13, "minecraft:air")  # the barricade gave way
            b.decal(15, stand, 14, "dried_blood_pool")
            b.decal(15, stand, 15, "drag_marks", turn=0)
            b.decal(16, stand + 1, 14, "bloody_handprint", face="wall", facing="south")

    # Cafeteria: kitchen counter along the back wall, long tables.
    kitchen(b, 1, 6, z1 - 1, y=stand)
    b.container(7, stand, z1 - 1, "kitchen_cupboard", "north", "house_kitchen")
    b.container(8, stand, z1 - 1, "fridge", "north", "fridge")
    for x in (3, 7, 11):
        dining(b, x, 25, y=stand, chairs=("west", "east"))
    # Library: rows of shelves, some fallen.
    for x in range(17, 29, 3):
        for z in range(24, z1):
            if history == "overrun" and b.chance(0.25):
                b.decal(x, stand, z, "scattered_papers")
            else:
                b.decor(x, stand, z, "bookshelf", "east" if x % 2 else "west")
    b.decor(28, stand, 23, "desk", "south")
    b.container(29, stand, 23, "filing_cabinet", "west", "school_office")
    # Gym: bleachers along the west wall, the rest open.
    for k in range(3):
        b.box(31 + k, stand, 15, 31 + k, stand + k, 27, "boarded_planks")
    if history == "shelter":
        for x in range(34, 39, 2):
            for z in range(15, 28, 3):
                b.decor(x, stand, z, "bed_frame", "north")
        b.container(38, stand, 14, "military_crate", "west", "store_storage")
        b.container(38, stand, 28, "supply_locker", "west", "house_bedroom")
    else:
        b.container(38, stand, 28, "supply_locker", "west", "school_locker")
    # A dead tree grew up through the gym floor and out of a hole in the roof.
    tx, tz = 36, 21
    b.box(tx, 1, tz, tx, top + 2, tz, "dead_log", axis="y")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3 and (dx, dz) != (0, 0) and b.chance(0.75):
                b.put(tx + dx, top + 2, tz + dz, "dead_leaves", persistent="true")

    # Basement: boiler room with the school generator, a store room, and a hidden room.
    bstand = 1
    b.partition_x(30, z0 + 1, z1 - 1, bstand, G - 1, "stained_concrete", door_z=5)
    b.decor(34, bstand, 10, "power_generator", "west")
    b.container(38, bstand, 12, "toolbox", "west", "workshop")
    b.container(38, bstand, 13, "supply_locker", "west", "workshop")
    b.box(31, bstand, 20, 38, bstand, 20, "minecraft:iron_bars")
    for x in range(2, 28, 4):
        b.container(x, bstand, z1 - 1, "military_crate" if x % 8 == 2 else "supply_locker", "north", "store_storage")
    # The hidden room behind a boarded-up wall: someone's last refuge.
    b.box(1, bstand, 1, 8, G - 1, 1, "stained_concrete")
    b.partition_z(7, x0 + 1, 8, bstand, G - 1, "stained_concrete")
    b.put(4, bstand, 7, "boarded_planks")
    b.put(4, bstand + 1, 7, "boarded_planks")
    b.air(2, bstand, 2, 7, G - 1, 6)
    b.decor(2, bstand, 3, "bed_frame", "east")
    b.container(7, bstand, 2, "safe", "west", "school_hidden")
    b.decal(5, bstand, 4, "scattered_papers")
    b.decal(7, bstand + 1, 6, "survivor_mark", face="wall", facing="west")
    if history == "overrun":
        b.put(20, bstand, 14, "residential_nest")
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1)):
            if free(b, 20 + dx, bstand, 14 + dz):
                b.put(20 + dx, bstand, 14 + dz, "biomass_growth")

    # Decay and nature: rubble, blood, webs, and vines, moss and leaf litter everywhere.
    blood = {"evacuated": 0.01, "shelter": 0.02, "overrun": 0.07}[history]
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y=stand, blood=blood, rubble=0.04, webs=0.05)
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y=bstand, blood=blood, rubble=0.05, webs=0.1)
    overgrow(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, stand, 0.35, height=h)
    overgrow(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, bstand, 0.15, height=G - 1)
    # Cracked floor: moss and grass push through the tiles here and there.
    for _ in range(30):
        x, z = int(b.r.integers(x0 + 1, x1)), int(b.r.integers(z0 + 1, z1))
        if free(b, x, stand, z) and b.blocks.get((x, G, z), ("",))[0] == "deadlands:cracked_hospital_tiles":
            b.put(x, G, z, "minecraft:moss_block")
            b.put(x, stand, z, "minecraft:short_grass")
    b.flat_roof(x0, z0, x1, z1, top + 1, "concrete", parapet="weathered_bricks", holes=0.07)
    b.put(tx, top + 1, tz, "minecraft:air")
    return b.seal_at(G, ground="minecraft:coarse_dirt")


# ----------------------------------------------------------------------------- forest outposts

def ranger_station(seed: int, wall: str) -> Site:
    sx, sz, h = 11, 10, 3
    b = Site(sx + 6, 1 + h + Building.roof_height(sz) + 1, sz, seed)
    b.box(sx, 0, 1, sx + 4, 0, sz - 2, "minecraft:gravel")  # the ranger's parking
    b.put(sx + 2, 1, sz // 2, "spawn_marker", spawn="vehicle_rugged", facing="north") if b.chance(0.7) else None
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, wall, corner="dead_log", footing="minecraft:cobblestone")
    b.door(4, z0, frame="boarded_planks")
    b.windows(x0, z0, x1, z1, 2, skip=[(4, z0)], glass=0.3, boarded=0.4)
    b.decor(x1 - 1, 1, z0 + 1, "desk", "west")
    b.decor(x1 - 2, 1, z0 + 1, "chair", "east")
    b.container(x1 - 1, 1, z1 - 1, "supply_locker", "west", "ranger_locker")
    b.container(x0 + 1, 1, z1 - 1, "filing_cabinet", "east", "office")
    b.decor(x0 + 1, 1, z0 + 2, "bed_frame", "east")
    b.container(x0 + 3, 1, z1 - 1, "toolbox", "north", "workshop")
    b.decal(x0 + 3, 2, z0 + 1, "survivor_mark", face="wall", facing="south") if b.chance(0.5) else None
    b.box(0, 1, 0, 0, 6, 0, "minecraft:iron_bars")  # flag pole
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, webs=0.08)
    overgrow(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.2, height=h)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + h, "x", gable=wall)
    return b.seal_at(0)


def hunting_cabin(seed: int) -> Site:
    sx, sz, h = 8, 8, 3
    b = Site(sx + 6, 1 + h + Building.roof_height(sz) + 1, sz, seed)
    b.put(sx + 2, 1, sz // 2, "spawn_marker", spawn="vehicle_rugged", facing="south") if b.chance(0.5) else None
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, "dead_log", floor="boarded_planks", corner="dead_log", footing="minecraft:cobblestone")
    for (x, y, z), (name, _, _) in list(b.blocks.items()):
        if name == "deadlands:dead_log":
            b.put(x, y, z, "dead_log", axis="y" if (x in (x0, x1) and z in (z0, z1)) else ("x" if z in (z0, z1) else "z"))
    b.door(3, z0)
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(3, z0)], glass=0.1, boarded=0.6)
    b.decor(x1 - 1, 1, z1 - 1, "bed_frame", "west")
    b.decor(x0 + 1, 1, z1 - 1, "table", "north")
    b.container(x0 + 1, 1, z0 + 1, "military_crate", "east", "hunting")
    b.put(x1 - 1, 1, z0 + 1, "minecraft:campfire", lit="false", facing="west")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.06, webs=0.1)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + h, "x", gable="boarded_planks")
    return b.seal_at(0)


# ----------------------------------------------------------------------------- camps

def tent(b: Building, x, z, colour: str, length: int = 3) -> None:
    """A ridge tent of wool, open at the north end."""
    for k in range(length):
        b.put(x - 1, 1, z + k, f"minecraft:{colour}_wool")
        b.put(x + 1, 1, z + k, f"minecraft:{colour}_wool")
        b.put(x, 2, z + k, f"minecraft:{colour}_wool")
        b.put(x, 1, z + k, f"minecraft:{colour}_carpet") if k > 0 else None
    b.put(x - 1, 1, z + length, f"minecraft:{colour}_wool")
    b.put(x, 1, z + length, f"minecraft:{colour}_wool")
    b.put(x + 1, 1, z + length, f"minecraft:{colour}_wool")


def campsite(seed: int, inhabited: bool = False) -> Site:
    """A ring of tents round a fire; an inhabited one has its fire lit and people at it."""
    b = Site(13, 4, 13, seed)
    b.put(6, 1, 6, "minecraft:campfire", lit="true" if inhabited else "false", facing="north")
    if inhabited:
        b.put(4, 1, 6, "spawn_marker", spawn="npc_settler", facing="east")
        b.put(8, 1, 6, "spawn_marker", spawn="npc_wanderer", facing="west")
        b.put(6, 1, 4, "spawn_marker", spawn="npc_settler", facing="south") if b.chance(0.6) else None
    b.box(5, 0, 5, 7, 0, 7, "minecraft:gravel")
    colours = ["green", "brown", "gray", "orange", "blue"]
    for x, z in ((2, 1), (10, 1), (2, 8), (10, 8)):
        if b.chance(0.8):
            tent(b, x, z, b.pick(colours))
    b.container(6, 1, 1, "military_crate", "south", "campsite")
    b.container(6, 1, 11, "toolbox", "north", "workshop") if b.chance(0.6) else None
    for _ in range(4):
        x, z = int(b.r.integers(0, 13)), int(b.r.integers(0, 13))
        if free(b, x, 1, z) and not inhabited:
            b.decal(x, 1, z, b.pick(["dried_blood_spots", "scorch_mark", "scattered_papers", "drag_marks"]))
    return b.seal_at(0)


def evacuation_camp(seed: int) -> Site:
    """Where the buses were meant to come: a fenced compound of tents with medical and supply stores."""
    b = Site(16, 5, 16, seed)
    for i in range(16):
        for x, z in ((i, 0), (i, 15), (0, i), (15, i)):
            if not (z == 0 and 6 <= x <= 9) and b.chance(0.85):
                b.put(x, 1, z, b.pick(["minecraft:iron_bars", "concrete_barrier"]))
    for (x, y, z), (name, props, _) in list(b.blocks.items()):
        if name == "deadlands:concrete_barrier":
            b.put(x, y, z, "concrete_barrier", facing="east" if x in (0, 15) else "north")
    for x, z in ((3, 3), (8, 3), (12, 3), (3, 9), (12, 9)):
        tent(b, x, z, b.pick(["white", "light_gray", "green"]), length=3)
    b.container(8, 1, 9, "medical_cabinet", "north", "clinic")
    b.container(9, 1, 9, "military_crate", "north", "military_crate")
    b.container(7, 1, 12, "military_crate", "north", "store_storage")
    b.decor(8, 1, 12, "table", "north")
    for _ in range(6):
        x, z = int(b.r.integers(1, 15)), int(b.r.integers(1, 15))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(["scattered_papers", "dried_blood_spots", "drag_marks", "faded_stain"]))
    b.put(7, 1, 6, "spawn_marker", spawn="vehicle_emergency", facing="north")
    return b.seal_at(0, ground="minecraft:gravel")


# ----------------------------------------------------------------------------- military

def checkpoint(seed: int) -> Site:
    b = Site(13, 5, 9, seed)
    for x in range(13):
        if x not in (5, 6, 7) and b.chance(0.9):
            b.decor(x, 1, 4, "concrete_barrier", "north")
    booth_x = 9
    b.walls(booth_x, 6, booth_x + 3, 8, 1, 2, "corrugated_metal")
    b.air(booth_x + 1, 1, 7, booth_x + 2, 2, 7)
    b.air(booth_x + 1, 1, 6, booth_x + 1, 2, 6)
    b.box(booth_x, 3, 6, booth_x + 3, 3, 8, "rusted_sheet_metal")
    b.decor(booth_x + 2, 1, 7, "desk", "north")
    b.container(1, 1, 7, "military_crate", "south", "military_crate")
    b.container(2, 1, 7, "military_crate", "south", "military_crate", lock={"type": "keycard", "level": 1}) if b.chance(0.5) else None
    for x in range(0, 4):
        b.put(x, 1, 2, "minecraft:packed_mud")
        b.put(x, 2, 2, "minecraft:packed_mud") if b.chance(0.6) else None
    b.decor(4, 1, 0, "road_sign_warning", "north")
    b.decal(6, 1, 5, "dried_blood_pool") if b.chance(0.6) else None
    b.put(6, 1, 1, "spawn_marker", spawn="vehicle_military", facing="east") if b.chance(0.6) else None
    return b.seal_at(0, ground="minecraft:gravel")


def watchtower(seed: int) -> Site:
    b = Site(5, 13, 5, seed)
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
        b.box(x, 1, z, x, 9, z, "dead_log", axis="y")
    b.box(0, 8, 0, 4, 8, 4, "boarded_planks")
    for i in range(5):
        for x, z in ((i, 0), (i, 4), (0, i), (4, i)):
            if not ((x, z) in ((0, 0), (4, 0), (0, 4), (4, 4))):
                b.put(x, 9, z, "minecraft:iron_bars")
    b.box(0, 11, 0, 4, 11, 4, "corrugated_metal")
    for y in range(1, 9):  # a ladder up one corner post, through a hatch in the platform
        b.put(1, y, 0, "minecraft:ladder", facing="east")
    b.put(1, 9, 0, "minecraft:air")
    b.container(3, 9, 3, "military_crate", "west", "military_crate")
    b.decal(1, 9, 2, "dried_blood_spots") if b.chance(0.5) else None
    return b.seal_at(0)


def bunker(seed: int) -> Site:
    """An underground shelter: a concrete hatch house on the surface (ground at y = 6) over a room."""
    b = Site(11, 10, 11, seed)
    g = 6
    b.box(0, 0, 0, 10, g - 1, 10, "stained_concrete")
    b.air(1, 1, 1, 9, 4, 9)
    b.box(3, g, 3, 7, g + 2, 7, "concrete")
    b.air(4, g + 1, 4, 6, g + 2, 6)
    b.air(5, g + 1, 3, 5, g + 2, 3)  # doorway
    b.box(3, g + 3, 3, 7, g + 3, 7, "concrete_slab", type="bottom")
    for y in range(1, g + 1):  # the shaft: a ladder on a concrete pillar, up through the hatch house floor
        b.put(6, y, 7, "stained_concrete")
        b.put(6, y, 6, "minecraft:ladder", facing="north")
    for x in (1, 3, 7, 9):
        b.container(x, 1, 9, "military_crate", "north", "military_crate", lock={"type": "keycard", "level": 1} if x == 9 else None)
    b.decor(2, 1, 2, "bed_frame", "east")
    b.decor(2, 1, 4, "bed_frame", "east")
    b.container(9, 1, 1, "medical_cabinet", "west", "clinic")
    b.decor(5, 1, 1, "power_generator", "south")
    b.decor(8, 1, 5, "desk", "west")
    b.clutter(1, 1, 9, 9, blood=0.04, rubble=0.03, webs=0.1)
    return b.seal_at(g, ground="minecraft:gravel")


# ----------------------------------------------------------------------------- utility sites

def radio_tower(seed: int) -> Site:
    b = Site(9, 30, 9, seed)
    b.box(3, 1, 3, 5, 26, 5, "minecraft:air")
    for y in range(1, 27):
        b.put(4, y, 4, "minecraft:iron_bars")
        if y % 6 == 0:
            for x, z in ((3, 4), (5, 4), (4, 3), (4, 5)):
                b.put(x, y, z, "minecraft:iron_bars")
    b.put(4, 27, 4, "minecraft:lightning_rod")
    for x, z in ((0, 0), (8, 0), (0, 8), (8, 8)):
        b.put(x, 1, z, "concrete")
    b.walls(0, 5, 3, 8, 1, 3, "corrugated_metal")
    b.air(1, 1, 6, 2, 3, 7)
    b.air(2, 1, 5, 2, 2, 5)
    b.box(0, 4, 5, 3, 4, 8, "rusted_sheet_metal")
    b.decor(1, 1, 7, "desk", "east")
    b.container(1, 1, 6, "filing_cabinet", "east", "office")
    b.put(2, 1, 7, "radio_transmitter", facing="west", repaired="false")  # dead until someone brings radio parts
    return b.seal_at(0, ground="minecraft:gravel")


def water_tower(seed: int) -> Site:
    b = Site(9, 18, 9, seed)
    for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
        b.box(x, 1, z, x, 10, z, "dead_log", axis="y")
        b.put(x, 0, z, "concrete")
    b.box(1, 11, 1, 7, 15, 7, b.pick(["rusted_sheet_metal", "corrugated_metal"]))
    b.box(2, 12, 2, 6, 14, 6, "minecraft:water")
    b.box(1, 16, 1, 7, 16, 7, "rusted_sheet_metal")
    for y in range(1, 17):  # up a leg and the side of the tank
        b.put(1, y, 0, "minecraft:ladder", facing="north")
    return b.seal_at(0, ground="minecraft:gravel")


def substation(seed: int) -> Site:
    """A fenced yard of transformers and a control shed with a dead generator (power restoration)."""
    b = Site(14, 12, 14, seed)
    b.box(0, 0, 0, 13, 0, 13, "minecraft:gravel")
    for i in range(14):
        for x, z in ((i, 0), (i, 13), (0, i), (13, i)):
            if not (z == 0 and 6 <= x <= 7):
                b.put(x, 1, z, "minecraft:iron_bars")
                b.put(x, 2, z, "minecraft:iron_bars") if b.chance(0.8) else None
    for x in (3, 6, 9):
        b.decor(x, 1, 4, "transformer", "north")
        b.box(x, 2, 4, x, 7, 4, "minecraft:iron_bars") if b.chance(0.5) else None
    b.walls(2, 8, 7, 12, 1, 3, "corrugated_metal")
    b.air(3, 1, 9, 6, 3, 11)
    b.air(4, 1, 8, 4, 2, 8)
    b.box(2, 4, 8, 7, 4, 12, "rusted_sheet_metal")
    b.decor(5, 1, 11, "power_generator", "north")
    b.container(3, 1, 11, "toolbox", "north", "utility")
    b.container(6, 1, 9, "supply_locker", "west", "utility")
    for x in (10, 11):
        b.box(x, 1, 10, x, 10, 10, "minecraft:iron_bars")
    b.decor(7, 1, 0, "road_sign_warning", "north") if b.chance(0.7) else None
    return b.seal_at(0, ground="minecraft:gravel")


def power_station(seed: int) -> Site:
    """The district power station: turbine hall, control room with the main generator, a chimney."""
    sx, sz, h = 30, 22, 8
    b = Site(sx, 30, sz, seed)
    b.box(0, 0, 0, sx - 1, 0, sz - 1, "cracked_concrete")
    x0, z0, x1, z1 = 1, 4, 20, 20
    b.shell(x0, z0, x1, z1, "corrugated_metal", floor="concrete", height=h, corner="rusted_sheet_metal")
    b.door(9, z0, width=3, height=4)
    b.windows(x0, z0, x1, z1, 6, spacing=3, glass=0.4, boarded=0.2)
    for x in range(x0 + 3, x1 - 2, 5):  # turbines (large machinery blocks)
        b.box(x, 1, 9, x + 2, 3, 15, "diamond_plate")
        b.box(x + 1, 4, 10, x + 1, 4, 14, "rusted_sheet_metal")
    b.partition_x(x1 - 5, z0 + 1, z1 - 1, 1, 4, "stained_concrete", door_z=z0 + 3)
    b.decor(x1 - 2, 1, z0 + 6, "power_generator", "west")
    for z in range(z0 + 8, z1, 2):
        b.decor(x1 - 1, 1, z, "desk", "west")
    b.container(x1 - 3, 1, z1 - 1, "filing_cabinet", "north", "office")
    b.container(x1 - 1, 1, z0 + 1, "supply_locker", "west", "utility")
    b.container(x1 - 2, 1, z0 + 1, "toolbox", "south", "utility")
    b.flat_roof(x0, z0, x1, z1, 1 + h, "corrugated_metal", holes=0.06)
    for x in range(23, 29, 3):  # transformer yard
        for z in range(6, 19, 4):
            b.decor(x, 1, z, "transformer", "west")
    for y in range(1, 28):  # the chimney
        for x, z in ((24, 1), (25, 1), (24, 2), (25, 2)):
            b.put(x, y, z, "sooty_bricks")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.02, rubble=0.05, webs=0.04)
    return b.seal_at(0, ground="cracked_concrete")


def treatment_plant(seed: int) -> Site:
    b = Site(24, 8, 20, seed)
    b.box(0, 0, 0, 23, 0, 19, "cracked_concrete")
    for cx, cz in ((5, 5), (5, 14), (14, 5), (14, 14)):
        b.box(cx - 3, 0, cz - 3, cx + 3, 2, cz + 3, "stained_concrete")
        b.box(cx - 2, 1, cz - 2, cx + 2, 1, cz + 2, "minecraft:water")
        b.box(cx - 2, 2, cz - 2, cx + 2, 2, cz + 2, "minecraft:air")
        b.box(cx - 3, 3, cz, cx + 3, 3, cz, "minecraft:iron_bars")
    b.walls(19, 4, 23, 10, 1, 3, "weathered_bricks")
    b.air(20, 1, 5, 22, 3, 9)
    b.air(19, 1, 7, 19, 2, 7)
    b.box(19, 4, 4, 23, 4, 10, "corrugated_metal")
    b.decor(21, 1, 5, "transformer", "south")
    b.container(22, 1, 9, "toolbox", "north", "utility")
    b.container(20, 1, 9, "filing_cabinet", "north", "office")
    return b.seal_at(0, ground="cracked_concrete")


# ----------------------------------------------------------------------------- farms

def silo(seed: int) -> Site:
    b = Site(12, 18, 12, seed)
    cx, cz = 4, 6
    for y in range(1, 15):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                if abs(x - cx) + abs(z - cz) in (3, 4) or (abs(x - cx) == 2 and abs(z - cz) <= 1) or (abs(z - cz) == 2 and abs(x - cx) <= 1):
                    if abs(x - cx) == 2 and abs(z - cz) == 2:
                        continue
                    b.put(x, y, z, "corrugated_metal")
    b.box(cx - 1, 1, cz - 1, cx + 1, 3, cz + 1, "minecraft:hay_block", axis="y")
    b.air(cx, 1, cz - 2, cx, 2, cz - 2)
    b.box(cx - 2, 15, cz - 2, cx + 2, 15, cz + 2, "rusted_sheet_metal")
    b.walls(8, 3, 11, 8, 1, 3, "boarded_planks")
    b.air(9, 1, 4, 10, 3, 7)
    b.air(9, 1, 3, 9, 2, 3)
    b.box(8, 4, 3, 11, 4, 8, "corrugated_metal")
    b.container(10, 1, 7, "toolbox", "north", "farm")
    return b.seal_at(0)


def windmill(seed: int) -> Site:
    """A farm wind pump: lattice tower, a broken wheel of planks, and a dry trough."""
    b = Site(9, 16, 9, seed)
    for y in range(1, 12):
        b.put(4, y, 4, "minecraft:iron_bars")
        if y < 6:
            for x, z in ((3, 4), (5, 4)):
                b.put(x, y, z, "minecraft:iron_bars") if y % 2 else None
    for k in range(-3, 4):
        if b.chance(0.85):
            b.put(4 + k, 12, 3, "boarded_planks")
        if b.chance(0.85):
            b.put(4, 12 + k, 3, "boarded_planks") if 12 + k > 8 else None
    b.box(1, 1, 6, 6, 1, 7, "rusted_sheet_metal")
    b.box(2, 1, 6, 5, 1, 7, "minecraft:air")
    b.box(2, 0, 6, 5, 0, 7, "minecraft:mud")
    return b.seal_at(0)


def research_outpost(seed: int) -> Site:
    """A field laboratory left behind by the research team: a trailer lab inside a fence."""
    b = Site(16, 6, 12, seed)
    b.box(0, 0, 0, 15, 0, 11, "minecraft:gravel")
    for i in range(16):
        for x, z in ((i, 0), (i, 11)):
            if not (z == 0 and 7 <= x <= 8):
                b.put(x, 1, z, "minecraft:iron_bars")
    for i in range(12):
        for x, z in ((0, i), (15, i)):
            b.put(x, 1, z, "minecraft:iron_bars")
    b.shell(2, 3, 13, 8, "rusted_sheet_metal", floor="hospital_tiles", height=3, footing="concrete")
    b.door(7, 3)
    b.windows(2, 3, 13, 8, 2, spacing=3, skip=[(7, 3)], glass=0.5, boarded=0.3)
    for x in (3, 5, 9, 11):
        b.container(x, 1, 7, "medical_cabinet", "north", "research_lab", lock={"type": "keycard", "level": 2} if x == 11 else None)
    b.decor(4, 1, 4, "desk", "south")
    b.container(12, 1, 4, "filing_cabinet", "west", "research_lab")
    b.container(1, 1, 10, "military_crate", "east", "military_crate")
    b.decal(7, 1, 5, "dried_blood_pool")
    b.decal(6, 1, 5, "scattered_papers")
    b.flat_roof(2, 3, 13, 8, 4, "corrugated_metal", holes=0.05)
    return b.seal_at(0, ground="minecraft:gravel")


def research_camp(seed: int) -> Site:
    """Harley's people at work: a lab hut, tents and a fence, still lived in."""
    b = Site(16, 6, 14, seed)
    b.box(0, 0, 0, 15, 0, 13, "minecraft:gravel")
    for i in range(16):
        for x, z in ((i, 0), (i, 13)):
            if not (z == 0 and 4 <= x <= 6):
                b.put(x, 1, z, "minecraft:iron_bars")
    for i in range(14):
        for x, z in ((0, i), (15, i)):
            b.put(x, 1, z, "minecraft:iron_bars")
    b.shell(2, 2, 9, 7, "rusted_sheet_metal", floor="hospital_tiles", height=3, footing="concrete")
    b.door(5, 2)
    b.windows(2, 2, 9, 7, 2, spacing=3, skip=[(5, 2)], glass=0.9, boarded=0.0)
    b.container(3, 1, 6, "medical_cabinet", "north", "research_lab")
    b.container(4, 1, 6, "medical_cabinet", "north", "research_lab", lock={"type": "keycard", "level": 2})
    b.decor(7, 1, 6, "desk", "north")
    b.put(4, 1, 4, "spawn_marker", spawn="npc_researcher", facing="south")
    b.put(7, 1, 4, "spawn_marker", spawn="npc_researcher", facing="south")
    b.flat_roof(2, 2, 9, 7, 4, "corrugated_metal", holes=0.0)
    tent(b, 12, 2, "white", length=3)
    tent(b, 12, 8, "light_gray", length=3)
    b.put(12, 1, 7, "spawn_marker", spawn="npc_guard", facing="west")
    b.put(5, 1, 11, "minecraft:campfire", lit="true", facing="north")
    b.container(8, 1, 11, "military_crate", "north", "military_crate")
    return b.seal_at(0, ground="minecraft:gravel")


# ----------------------------------------------------------------------------- story sites (Milestone 8)

def security_door(b: Site, x: int, z: int, facing: str, level: int, reader_out: tuple, reader_in: tuple) -> None:
    """A security door in a doorway, with a keycard reader on the wall beside it on each side.

    reader_out/reader_in: (x, z, facing) of the air block in front of the wall beside the door; the
    reader faces away from the wall it hangs on, and powering that wall opens the door next to it."""
    for y, half in ((1, "lower"), (2, "upper")):
        b.put(x, y, z, "security_door", facing=facing, half=half, hinge="left", open="false", powered="false")
    for rx, rz, rf in (reader_out, reader_in):
        b.put(rx, 1, rz, "keycard_reader", face="wall", facing=rf, powered="false", level=str(level))


def research_facility(seed: int) -> Site:
    """The Kessler Regional Research Laboratory's field wing: a lobby, the LANTERN lab behind a green
    door, and Wing D, where Patient Zero was kept, behind a red one."""
    b = Site(26, 7, 22, seed)
    b.box(0, 0, 0, 25, 0, 21, "concrete")
    b.shell(1, 1, 24, 20, "plaster_wall", floor="hospital_tiles", height=4, footing="concrete")
    b.door(12, 1)
    b.windows(1, 1, 24, 20, 2, spacing=3, skip=[(12, 1)], glass=0.6, boarded=0.2)
    # Lobby.
    b.partition_z(8, 2, 23, 1, 4, "concrete", door_x=12)
    b.decor(5, 1, 3, "desk", "south")
    b.decor(6, 1, 3, "chair", "north")
    b.container(3, 1, 2, "filing_cabinet", "south", "office")
    b.container(20, 1, 2, "filing_cabinet", "south", "office")
    b.decal(9, 1, 4, "scattered_papers")
    b.decal(14, 1, 5, "dried_blood_spots")
    b.decal(16, 2, 2, "evacuation_notice", face="wall", facing="south")
    security_door(b, 12, 8, "south", 1, (13, 7, "north"), (13, 9, "south"))
    # The LANTERN lab.
    b.partition_x(16, 9, 19, 1, 4, "concrete", door_z=14)
    for x in (3, 6, 9):
        b.container(x, 1, 19, "medical_cabinet", "north", "research_lab")
    b.container(13, 1, 19, "filing_cabinet", "north", "research_lab")
    b.decor(5, 1, 13, "desk", "east")
    b.decor(9, 1, 13, "desk", "east")
    b.decal(7, 1, 15, "dried_blood_pool")
    b.decal(11, 1, 11, "drag_marks")
    security_door(b, 16, 14, "east", 3, (15, 15, "west"), (17, 15, "east"))
    # Wing D: the cells.
    for z in (11, 15):
        for x in range(18, 24):
            if x != 20:
                b.put(x, 1, z, "minecraft:iron_bars")
                b.put(x, 2, z, "minecraft:iron_bars")
    b.container(23, 1, 19, "medical_cabinet", "west", "research_lab", lock={"type": "keycard", "level": 3})
    b.container(23, 1, 9, "filing_cabinet", "west", "research_lab")
    b.decal(21, 1, 13, "dried_blood_pool")
    b.decal(19, 1, 17, "bloody_handprint")
    b.decal(22, 1, 10, "drag_marks")
    b.flat_roof(1, 1, 24, 20, 5, "concrete", holes=0.03)
    return b.seal_at(0, ground="minecraft:gravel")


def site_calder(seed: int) -> Site:
    """Site Calder: the army's hidden airfield. A fenced compound with a painted pad, the hangar where
    one helicopter was left behind, a command post behind a blue door and an armoury behind a red one."""
    b = Site(34, 10, 30, seed)
    b.box(0, 0, 0, 33, 0, 29, "minecraft:gravel")
    for i in range(34):
        for x, z in ((i, 0), (i, 29)):
            if not (z == 29 and 14 <= i <= 19):
                b.put(x, 1, z, "minecraft:iron_bars")
                b.put(x, 2, z, "minecraft:iron_bars")
    for i in range(30):
        for x, z in ((0, i), (33, i)):
            b.put(x, 1, z, "minecraft:iron_bars")
            b.put(x, 2, z, "minecraft:iron_bars")
    # The pad: an H painted on concrete, made to look like a car park from the air.
    b.box(3, 0, 15, 13, 0, 25, "concrete")
    for z in range(17, 24):
        b.put(5, 0, z, "asphalt_yellow_line")
        b.put(11, 0, z, "asphalt_yellow_line")
    for x in range(5, 12):
        b.put(x, 0, 20, "asphalt_yellow_line")
    # The hangar, open to the south.
    b.shell(17, 13, 32, 28, "corrugated_metal", floor="concrete", height=6, footing="concrete")
    b.air(19, 1, 28, 30, 5, 28)
    b.flat_roof(17, 13, 32, 28, 7, "corrugated_metal")
    b.put(24, 1, 20, "spawn_marker", spawn="helicopter", facing="south")
    b.container(18, 1, 14, "toolbox", "south", "workshop")
    b.container(31, 1, 14, "military_crate", "south", "military_crate")
    b.container(31, 1, 18, "toolbox", "west", "workshop")
    # The command post.
    b.shell(2, 2, 14, 11, "concrete", floor="diamond_plate", height=4, footing="concrete")
    b.windows(2, 2, 14, 11, 2, spacing=3, skip=[(8, 11)], glass=0.4, boarded=0.4)
    b.put(8, 1, 11, "minecraft:air")
    b.put(8, 2, 11, "minecraft:air")
    security_door(b, 8, 11, "north", 2, (9, 12, "south"), (9, 10, "north"))
    b.partition_x(9, 3, 10, 1, 4, "concrete", door_z=5)
    b.decor(4, 1, 8, "desk", "east")
    b.container(3, 1, 3, "filing_cabinet", "south", "office")
    b.container(6, 1, 3, "military_crate", "south", "military_crate")
    b.container(3, 1, 10, "filing_cabinet", "east", "office")
    b.decal(6, 1, 7, "scattered_papers")
    security_door(b, 9, 5, "east", 3, (8, 6, "west"), (10, 6, "east"))
    b.container(13, 1, 3, "military_crate", "west", "military_crate", lock={"type": "keycard", "level": 3})
    b.container(13, 1, 6, "military_crate", "west", "military_crate")
    b.container(13, 1, 9, "supply_locker", "west", "military_crate")
    b.flat_roof(2, 2, 14, 11, 5, "concrete")
    b.put(28, 1, 4, "spawn_marker", spawn="vehicle_military", facing="west")
    for _ in range(8):
        x, z = int(b.r.integers(1, 33)), int(b.r.integers(12, 29))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(["dried_blood_spots", "scorch_mark", "drag_marks", "scattered_papers"]))
    return b.seal_at(0, ground="minecraft:gravel")


def expedition_camp(seed: int) -> Site:
    """Harley's expedition camp, abandoned the night of the roadblock: four tents, a cold fire, sample
    crates; her field case went under the third tent."""
    b = Site(15, 5, 15, seed)
    b.put(7, 1, 7, "minecraft:campfire", lit="false", facing="north")
    b.box(6, 0, 6, 8, 0, 8, "minecraft:gravel")
    for i, (x, z) in enumerate(((2, 1), (12, 1), (2, 9), (12, 9))):
        tent(b, x, z, ["white", "light_gray", "green", "white"][i])
    b.container(2, 1, 11, "military_crate", "north", "research_lab")   # under the third tent
    b.container(7, 1, 2, "medical_cabinet", "south", "research_lab")
    b.container(12, 1, 3, "military_crate", "west", "campsite")
    b.container(7, 1, 12, "military_crate", "north", "campsite")
    b.decor(9, 1, 6, "table", "west")
    for _ in range(5):
        x, z = int(b.r.integers(0, 15)), int(b.r.integers(0, 15))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(["scattered_papers", "dried_blood_spots", "drag_marks"]))
    return b.seal_at(0)


# ----------------------------------------------------------------------------- catalogue and worldgen

# ----------------------------------------------------------------------------- roadside

def gas_station(seed: int, wrecked: bool) -> Site:
    """A forecourt with a canopy over two pump islands, and a convenience store with a back room.

    The pumps are dead (fuel arrives with vehicles). The wrecked one burned: scorched forecourt,
    a canopy full of holes, a store that was looted and fought over.
    """
    sx, sz = 18, 16
    b = Site(sx, 7, sz, seed)
    for x in range(sx):
        for z in range(9):
            b.put(x, 0, z, "asphalt" if b.chance(0.35) else "cracked_asphalt")
    for x0 in (4, 11):  # pump islands: a pump at each end, a canopy column between them
        b.box(x0, 0, 4, x0 + 2, 0, 4, "concrete")
        b.decor(x0, 1, 4, "fuel_pump", "north")
        b.decor(x0 + 2, 1, 4, "fuel_pump", "north")
        b.box(x0 + 1, 1, 4, x0 + 1, 4, 4, "concrete")
    b.box(2, 5, 2, 15, 5, 6, "corrugated_metal")
    b.walls(2, 2, 15, 6, 5, 5, "rusted_sheet_metal")
    if wrecked:
        for x in range(3, 15):
            for z in range(3, 6):
                if b.chance(0.3):
                    b.put(x, 5, z, "minecraft:air")
    # The store: sales floor to the west, a back room to the east.
    x0, z0, x1, z1 = 1, 9, 16, 15
    b.shell(x0, z0, x1, z1, "weathered_bricks", floor="hospital_tiles", height=3, corner="concrete")
    b.door(8, z0, width=2)
    b.windows(x0, z0, x1, z1, 2, spacing=2, skip=[(8, z0), (9, z0), (12, z0)], sides=("north",), tall=True,
              glass=0.2 if wrecked else 0.5, boarded=0.3)
    b.windows(x0, z0, x1, z1, 2, spacing=3, sides=("west",), glass=0.3, boarded=0.4)
    b.partition_x(12, z0 + 1, z1 - 1, 1, 3, "plaster_wall", door_z=z0 + 2)
    b.decor(10, 1, z0 + 2, "counter", "west")
    b.decor(10, 1, z0 + 3, "counter", "west")
    b.container(11, 1, z0 + 4, "filing_cabinet", "west", "gas_station")
    for z in (z0 + 2, z0 + 4):
        for x in range(x0 + 2, 8, 2):
            b.decor(x, 1, z, "store_shelf", "south" if z == z0 + 2 else "north")
    b.container(x0 + 1, 1, z1 - 1, "fridge", "north", "fridge")
    b.container(x0 + 2, 1, z1 - 1, "fridge", "north", "fridge")
    b.container(x1 - 1, 1, z1 - 1, "supply_locker", "north", "gas_station",
                lock=None if wrecked else {"type": "key", "key": "maintenance"})
    b.container(x1 - 1, 1, z0 + 1, "toolbox", "west", "workshop")
    b.container(x1 - 2, 1, z1 - 1, "military_crate", "north", "store_storage")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.09 if wrecked else 0.03, rubble=0.08 if wrecked else 0.02, webs=0.05)
    b.flat_roof(x0, z0, x1, z1, 4, "corrugated_metal", parapet="weathered_bricks", holes=0.12 if wrecked else 0.0)
    b.decor(0, 1, 0, "road_sign_route", "north")
    b.decor(17, 1, 1, "street_lamp", "north")
    b.decor(17, 2, 1, "street_lamp", "north")
    b.decor(17, 3, 1, "street_lamp_head", "west")
    marks = ["scorch_mark", "scorch_mark", "dried_blood_pool", "drag_marks"] if wrecked else ["faded_stain", "faded_stain", "dried_blood_spots"]
    for _ in range(9 if wrecked else 4):
        x, z = int(b.r.integers(0, sx)), int(b.r.integers(0, 9))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(marks))
    b.put(8, 1, 1, "spawn_marker", spawn="vehicle_civilian", facing="east")
    if not wrecked and b.chance(0.5):
        b.put(14, 1, 7, "spawn_marker", spawn="vehicle_civilian", facing="north")
    if wrecked:
        for x, z in ((6, 7), (13, 1), (2, 8)):
            if free(b, x, 1, z):
                b.put(x, 1, z, "rubble")
    return b.seal_at(0, ground="minecraft:gravel")


def rest_stop(seed: int) -> Site:
    """A lay-by: a strip of parking, a toilet block, and a picnic shelter where someone waited."""
    sx, sz = 16, 12
    b = Site(sx, 6, sz, seed)
    for x in range(sx):
        for z in range(4):
            b.put(x, 0, z, "cracked_asphalt" if b.chance(0.7) else "asphalt")
    for x in range(1, sx, 3):
        b.put(x, 0, 2, "asphalt_white_line", axis="z")
        b.put(x, 0, 3, "asphalt_white_line", axis="z")
    # Toilet block (west): two stalls, each two wide so the fixtures leave a way in.
    b.shell(1, 6, 7, 10, "concrete", floor="hospital_tiles", height=3, footing="concrete")
    b.partition_x(4, 7, 9, 1, 3, "concrete")
    for door_x, inner, sink_facing in ((2, 3, "west"), (6, 5, "east")):
        b.door(door_x, 6)
        b.decor(inner, 1, 9, "toilet", "north")
        b.decor(inner, 1, 7, "sink", sink_facing) if b.chance(0.8) else None
    b.flat_roof(1, 6, 7, 10, 4, "corrugated_metal", holes=0.05)
    b.decal(4, 2, 5, "evacuation_notice", face="wall", facing="north")
    # Picnic shelter (east): four posts and a roof over two tables.
    for x, z in ((9, 6), (14, 6), (9, 10), (14, 10)):
        b.box(x, 1, z, x, 3, z, "dead_log", axis="y")
    b.box(8, 4, 5, 15, 4, 11, "boarded_planks")
    for x in (10, 13):
        dining_x, dining_z = x, 8
        b.decor(dining_x, 1, dining_z, "table")
        b.decor(dining_x, 1, dining_z - 1, "chair", "south")
        b.decor(dining_x, 1, dining_z + 1, "chair", "north") if b.chance(0.7) else None
    b.container(12, 1, 10, "military_crate", "north", "campsite")
    b.put(11, 1, 6, "minecraft:campfire", lit="false", facing="north")
    for _ in range(5):
        x, z = int(b.r.integers(7, sx)), int(b.r.integers(4, sz))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(["scattered_papers", "dried_blood_spots", "faded_stain", "survivor_mark"]))
    b.decor(0, 1, 4, "road_sign_warning", "north")
    return b.seal_at(0)


def roadside_stand(seed: int) -> Site:
    """A farm stand at the roadside: an open-fronted stall, crates, hay, and a hand-painted sign."""
    sx, sz = 10, 8
    b = Site(sx, 6, sz, seed)
    b.box(1, 0, 2, 8, 0, 6, "boarded_planks")
    for x in (1, 8):
        for z in (2, 6):
            b.box(x, 1, z, x, 3, z, "dead_log", axis="y")
    b.box(1, 1, 6, 8, 3, 6, "boarded_planks")  # back wall
    for z in range(3, 6):
        for x in (1, 8):
            b.put(x, 1, z, "boarded_planks")
            b.put(x, 2, z, "boarded_planks") if b.chance(0.7) else None
    b.box(0, 4, 1, 9, 4, 7, "rusted_sheet_metal")
    for x in range(2, 8):
        b.decor(x, 1, 2, "counter", "north") if x not in (4, 5) else None
    for x in (2, 4, 6):
        b.decor(x, 1, 5, "store_shelf", "north")
    b.container(7, 1, 5, "military_crate", "north", "farm")
    b.container(3, 1, 4, "toolbox", "north", "store_storage") if b.chance(0.6) else None
    for x, z in ((0, 3), (9, 4), (9, 5)):
        b.put(x, 1, z, "minecraft:hay_block", axis=b.pick(["x", "z"]))
    b.decor(9, 1, 0, "road_sign_route", "north")
    b.decal(4, 2, 5, "survivor_mark", face="wall", facing="north") if b.chance(0.5) else None
    for _ in range(3):
        x, z = int(b.r.integers(0, sx)), int(b.r.integers(0, 2))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(["scattered_papers", "drag_marks", "dried_blood_spots"]))
    return b.seal_at(0)


def farmstead(seed: int, wall: str) -> Site:
    """A working farm left to rot: the farmhouse, a barn, a well, and a fenced field gone wild."""
    sx, sz = 22, 18
    b = Site(sx, 1 + 4 + Building.roof_height(9) + 1, sz, seed)
    # Farmhouse (north-west), front door on the north side.
    hx0, hz0, hx1, hz1 = 1, 1, 9, 8
    b.shell(hx0, hz0, hx1, hz1, wall, corner="dead_log", footing="minecraft:cobblestone")
    b.door(4, hz0, frame="boarded_planks")
    b.windows(hx0, hz0, hx1, hz1, 2, spacing=2, skip=[(4, hz0), (6, hz0), (6, hz1)], glass=0.35, boarded=0.4)
    b.partition_x(6, hz0 + 1, hz1 - 1, 1, 3, "plaster_wall", door_z=hz0 + 3)
    kitchen(b, hx0 + 1, hx0 + 4, hz1 - 1)
    dining(b, hx0 + 2, hz0 + 3)
    bedroom(b, 7, hx1 - 1, hz0 + 1, hz1 - 1)
    b.clutter(hx0 + 1, hz0 + 1, hx1 - 1, hz1 - 1, blood=0.04, rubble=0.02, webs=0.06)
    b.gable_roof(hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1, 4, "x", gable=wall)
    # Barn (east), doors facing the yard to the west.
    bx0, bz0, bx1, bz1 = 13, 1, 20, 10
    b.box(bx0, 0, bz0, bx1, 0, bz1, "minecraft:coarse_dirt")
    b.walls(bx0, bz0, bx1, bz1, 1, 4, "boarded_planks", corner="dead_log")
    b.air(bx0 + 1, 1, bz0 + 1, bx1 - 1, 4, bz1 - 1)
    b.air(bx0, 1, 4, bx0, 3, 6)
    for z in range(bz0 + 1, bz1, 2):
        b.put(bx1 - 1, 1, z, "minecraft:hay_block", axis=b.pick(["x", "y", "z"]))
        b.put(bx1 - 1, 2, z, "minecraft:hay_block", axis="y") if b.chance(0.5) else None
    b.container(bx0 + 1, 1, bz1 - 1, "toolbox", "north", "farm")
    b.container(bx0 + 2, 1, bz1 - 1, "supply_locker", "north", "farm")
    b.container(bx0 + 1, 1, bz0 + 1, "military_crate", "south", "store_storage") if b.chance(0.7) else None
    b.clutter(bx0 + 1, bz0 + 1, bx1 - 2, bz1 - 1, blood=0.05, rubble=0.02, webs=0.08)
    b.gable_roof(bx0 - 1, bz0 - 1, bx1 + 1, bz1 + 1, 5, "z", gable="boarded_planks")
    # Well, between the two.
    b.walls(10, 11, 12, 13, 1, 1, "minecraft:cobblestone")
    b.put(11, 0, 12, "minecraft:water")
    b.put(11, 1, 12, "minecraft:air")
    # Field (south), fenced, with what still grows.
    for x in range(1, 21):
        for z in range(12, 17):
            if 10 <= x <= 12 and z <= 13:
                continue
            b.put(x, 0, z, "minecraft:farmland", moisture=0) if b.chance(0.6) else b.put(x, 0, z, "minecraft:coarse_dirt")
            if b.blocks[(x, 0, z)][0] == "minecraft:farmland" and b.chance(0.5):
                b.put(x, 1, z, "minecraft:wheat", age=int(b.r.integers(2, 8)))
    for x in range(0, 22):
        if b.chance(0.8):
            b.put(x, 1, 17, "minecraft:oak_fence")
    for z in range(11, 17):
        for x in (0, 21):
            if b.chance(0.8):
                b.put(x, 1, z, "minecraft:oak_fence")
    b.put(11, 1, 5, "spawn_marker", spawn="vehicle_farm", facing="south")
    for _ in range(5):
        x, z = int(b.r.integers(1, 21)), int(b.r.integers(9, 12))
        if free(b, x, 1, z):
            b.decal(x, 1, z, b.pick(["drag_marks", "dried_blood_spots", "dried_blood_pool", "faded_stain"]))
    return b.seal_at(0)


def sites() -> dict[str, list[tuple[str, Site]]]:
    """Structure -> [(template name, site)]."""
    return {
        "school": [("school_evacuated", school(9001, "evacuated")), ("school_shelter", school(9002, "shelter")),
                   ("school_overrun", school(9003, "overrun"))],
        "forest_outpost": [("ranger_station_a", ranger_station(9011, "weathered_siding")), ("ranger_station_b", ranger_station(9012, "boarded_planks")),
                           ("ranger_station_c", ranger_station(9013, "weathered_siding")), ("hunting_cabin_a", hunting_cabin(9021)),
                           ("hunting_cabin_b", hunting_cabin(9022)), ("hunting_cabin_c", hunting_cabin(9023))],
        "camp": [("campsite_a", campsite(9031)), ("campsite_b", campsite(9032)), ("campsite_c", campsite(9033)),
                 ("survivor_camp_a", campsite(9034, True)), ("survivor_camp_b", campsite(9035, True))],
        "evacuation_camp": [("evacuation_camp_a", evacuation_camp(9041)), ("evacuation_camp_b", evacuation_camp(9042))],
        "expedition_camp": [("expedition_camp", expedition_camp(9043))],
        "research_facility": [("research_facility", research_facility(9201))],
        "site_calder": [("site_calder", site_calder(9211))],
        "military_post": [("checkpoint_a", checkpoint(9051)), ("checkpoint_b", checkpoint(9052)), ("checkpoint_c", checkpoint(9053)),
                          ("watchtower_a", watchtower(9061)), ("watchtower_b", watchtower(9062)), ("watchtower_c", watchtower(9063))],
        "bunker": [("bunker_a", bunker(9071)), ("bunker_b", bunker(9072))],
        "utility_site": [("radio_tower_a", radio_tower(9081)), ("radio_tower_b", radio_tower(9082)),
                         ("water_tower_a", water_tower(9091)), ("water_tower_b", water_tower(9092)), ("water_tower_c", water_tower(9093)),
                         ("substation_a", substation(9101)), ("substation_b", substation(9102)), ("substation_c", substation(9103))],
        "power_station": [("power_station", power_station(9111))],
        "treatment_plant": [("treatment_plant", treatment_plant(9121))],
        "farm_site": [("silo_a", silo(9131)), ("silo_b", silo(9132)), ("windmill_a", windmill(9141)), ("windmill_b", windmill(9142)),
                      ("farmstead_a", farmstead(9161, "weathered_siding")), ("farmstead_b", farmstead(9162, "peeling_plaster"))],
        "gas_station": [("gas_station_a", gas_station(9171, False)), ("gas_station_b", gas_station(9172, True))],
        "roadside_stop": [("rest_stop", rest_stop(9181)), ("roadside_stand", roadside_stand(9191))],
        "research_outpost": [("research_outpost", research_outpost(9151)), ("research_camp", research_camp(9154))],
    }


# structure -> (biome tag values, spacing, separation, salt, start height, terrain adaptation)
EVERYWHERE = ["#deadlands:is_deadlands"]
PLACEMENT = {
    "school": (["deadlands:overgrown_suburbs", "deadlands:rural_countryside", "deadlands:abandoned_farmland", "deadlands:dead_forest",
                "deadlands:industrial_wasteland", "minecraft:plains", "minecraft:forest"], 40, 16, 77100011, -G, "beard_thin"),
    "forest_outpost": (["deadlands:dead_forest", "deadlands:dead_pine_valley", "deadlands:burned_forest", "deadlands:frozen_outskirts",
                        "deadlands:rural_countryside", "minecraft:forest", "minecraft:taiga"], 22, 8, 77100021, 0, "beard_thin"),
    "camp": (EVERYWHERE, 28, 10, 77100031, 0, "beard_thin"),
    "military_post": (EVERYWHERE, 30, 12, 77100041, 0, "beard_thin"),
    "bunker": (EVERYWHERE, 44, 18, 77100051, -6, "none"),
    "utility_site": (EVERYWHERE, 24, 9, 77100061, 0, "beard_thin"),
    "power_station": (EVERYWHERE, 56, 24, 77100071, 0, "beard_thin"),
    "treatment_plant": (EVERYWHERE, 60, 24, 77100081, 0, "beard_thin"),
    "farm_site": (["deadlands:abandoned_farmland", "deadlands:rural_countryside", "deadlands:dry_wasteland", "minecraft:plains"],
                  20, 8, 77100091, 0, "beard_thin"),
    "research_outpost": (EVERYWHERE, 72, 28, 77100101, 0, "beard_thin"),
    "gas_station": (EVERYWHERE, 30, 12, 77100111, 0, "beard_thin"),
    "roadside_stop": (EVERYWHERE, 26, 10, 77100121, 0, "beard_thin"),
    "evacuation_camp": (EVERYWHERE, 34, 12, 77100131, 0, "beard_thin"),
    "expedition_camp": (EVERYWHERE, 40, 14, 77100161, 0, "beard_thin"),
    "research_facility": (EVERYWHERE, 60, 24, 77100141, 0, "beard_thin"),
    "site_calder": (EVERYWHERE, 70, 28, 77100151, 0, "beard_thin"),
}


def worldgen(catalogue: dict[str, list[tuple[str, Site]]]) -> None:
    for structure, entries in catalogue.items():
        biomes, spacing, separation, salt, start, adaptation = PLACEMENT[structure]
        D.data_json(f"tags/worldgen/biome/has_structure/{structure}.json", {"replace": False, "values": biomes})
        D.data_json(f"worldgen/structure/{structure}.json", {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{structure}",
            "step": "surface_structures",
            "terrain_adaptation": adaptation,
            "spawn_overrides": {},
            "start_pool": f"{NS}:site/{structure}",
            "size": 1,
            "start_height": {"absolute": start},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": False,
        })
        D.data_json(f"worldgen/template_pool/site/{structure}.json", {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element",
                "location": f"{NS}:site/{name}",
                "processors": "minecraft:empty",
                "projection": "rigid",
            }} for name, _ in entries],
        })
        D.data_json(f"worldgen/structure_set/{structure}.json", {
            "structures": [{"structure": f"{NS}:{structure}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt,
                          # Keep sites out of towns (a city reaches about eight chunks from its centre).
                          "exclusion_zone": {"other_set": f"{NS}:towns", "chunk_count": 9}},
        })
    # Railways: the configured and placed feature (the Java feature is deadlands:rail_network).
    D.data_json("worldgen/configured_feature/rail_network.json", {"type": f"{NS}:rail_network", "config": {}})
    D.data_json("worldgen/placed_feature/rail_network.json", {"feature": f"{NS}:rail_network", "placement": []})


def loot() -> None:
    loot_table("school_locker", (1, 3), [
        item("paper", 10, (1, 4)), item("book", 6), item("writable_book", 4), item("apple", 4), item("cookie", 5, (1, 3)),
        item("leather_helmet", 2, functions=worn()), item("string", 4, (1, 3)), item("feather", 3), item("ink_sac", 3),
        item("compass", 1), item("clock", 1), item("name_tag", 1)])
    loot_table("school_office", (2, 4), [
        item("paper", 16, (2, 6)), item("book", 6), item("writable_book", 4), item("map", 3), item("ink_sac", 3),
        key("janitor", 2), key("records_room", 1), item(f"{NS}:keycard_green", 1)])
    D.data_json("loot_table/containers/school_key.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [key("principal", 1)]},
            {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3}, "bonus_rolls": 0.0, "entries": [
                item("paper", 10, (1, 4)), item("book", 4), item("writable_book", 3), item("cocoa_beans", 3, (1, 2))]},
        ],
        "random_sequence": f"{NS}:containers/school_key",
    })
    loot_table("school_hidden", (3, 5), [
        item("bread", 6, (1, 3)), item("dried_kelp", 6, (2, 6)), item("potion", 3, functions=potion("healing")),
        item("torch", 6, (2, 5)), item("writable_book", 4), item("paper", 6, (2, 5)), item("golden_apple", 1),
        item(f"{NS}:keycard_blue", 1), item("iron_sword", 2, functions=worn(0.3, 0.8))])
    loot_table("hunting", (2, 4), [
        item("arrow", 12, (4, 16)), item("bow", 4, functions=worn(0.2, 0.7)), item("leather", 8, (1, 4)), item("rabbit_hide", 6, (1, 3)),
        item("cooked_rabbit", 5, (1, 3)), item("cooked_mutton", 4, (1, 2)), item("string", 6, (1, 4)), item("flint", 4, (1, 3)),
        item("spyglass", 1), item("lead", 2)])
    loot_table("campsite", (2, 4), [
        item("bread", 6, (1, 2)), item("dried_kelp", 6, (2, 6)), item("baked_potato", 5, (1, 3)), item("torch", 8, (2, 6)),
        item("string", 6, (1, 4)), item("leather", 4, (1, 2)), item("flint_and_steel", 2, functions=worn(0.3, 0.8)),
        item("compass", 2), item("map", 2), item("bowl", 4)])
    loot_table("utility", (2, 4), [
        item(f"{NS}:fuse", 6, (1, 2)), item("copper_ingot", 8, (1, 4)), item("redstone", 8, (2, 6)), item("iron_ingot", 4, (1, 2)),
        item("iron_nugget", 8, (2, 8)), item("iron_chain", 4, (1, 3)), item("shears", 2, functions=worn(0.2, 0.6)), item("paper", 4, (1, 3)),
        key("maintenance", 2)])
    loot_table("gas_station", (2, 4), [
        item("bread", 5, (1, 2)), item("cookie", 8, (1, 4)), item("dried_kelp", 8, (2, 6)), item("honey_bottle", 3),
        item("map", 4), item("paper", 4, (1, 3)), item("flint_and_steel", 3, functions=worn(0.3, 0.8)), item("torch", 6, (2, 5)),
        item(f"{NS}:fuse", 3), item("iron_nugget", 6, (2, 6)), item("bucket", 3), item("gold_nugget", 5, (1, 6)),
        key("maintenance", 1)])
    loot_table("research_lab", (2, 4), [
        item("paper", 12, (2, 6)), item("glass_bottle", 8, (1, 4)), item("potion", 3, functions=potion("healing")),
        item("potion", 2, functions=potion("regeneration")), item("fermented_spider_eye", 4), item("glistering_melon_slice", 2),
        item("writable_book", 4), item(f"{NS}:keycard_blue", 2), item(f"{NS}:keycard_red", 1)])


TEXT = {
    # Cities, road kinds and power (Java systems of Milestone 3, second half).
    "town.deadlands.size.city": "City",
    "town.deadlands.landmark.courthouse": "the courthouse",
    "town.deadlands.landmark.fire_station": "the fire station",
    "town.deadlands.landmark.bank": "the bank",
    "town.deadlands.landmark.department_store": "the department store",
    "town.deadlands.landmark.saloon": "the saloon",
    "town.deadlands.landmark.general_store": "the general store",
    "town.deadlands.landmark.motel": "the motel",
    "town.deadlands.landmark.office_block": "the office blocks",
    "town.deadlands.landmark.apartment_block": "the apartment blocks",
    "hud.deadlands.town.powered": "The power is back on",
    "message.deadlands.power.restored": "The generator coughs into life. Power restored to %s town(s).",
    "message.deadlands.power.no_town": "The generator runs, but no town is close enough to its lines.",
    "message.deadlands.power.running": "The generator hums. It is running.",
    "message.deadlands.power.needs_fuse": "The generator is dead. The fuse has blown; it needs a new one.",
    "chronicle.deadlands.power_restored": "%s restored the power to %s.",
    "item.deadlands.fuse": "Fuse",
}


# ----------------------------------------------------------------------------- the fuse and the railway track

def fuse_item(seed: int = 4501):
    """A glass cartridge fuse with brass end caps and a thin element inside (32 x 32 item icon)."""
    import dl_noise as N
    from gen_items import _outline
    s = 32
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    r = N.rng(seed)
    u = (xx - yy) / np.sqrt(2.0)  # along the diagonal
    v = (xx + yy - s) / np.sqrt(2.0)
    body = (np.abs(u) <= 8.5) & (np.abs(v) <= 3.4)
    cap = (np.abs(u) > 8.5) & (np.abs(u) <= 12.5) & (np.abs(v) <= 3.8)
    rgb = np.zeros((s, s, 3))
    alpha = np.zeros((s, s))
    glass = N.ramp(0.5 + 0.5 * (v / 3.4), [(0.0, "#8aa2a8"), (0.5, "#c8d8da"), (1.0, "#6a8086")])
    rgb[body] = glass[body]
    wire = body & (np.abs(v) < 0.6)
    rgb[wire] = N.hex_rgb("#4a4440")
    brass = N.ramp(0.5 + 0.5 * (v / 3.8) + 0.1 * r.random((s, s)), [(0.0, "#6a5420"), (0.5, "#c9a64a"), (1.0, "#8a6e2a")])
    rgb[cap] = brass[cap]
    alpha[body | cap] = 1.0
    _outline(rgb, alpha)
    return rgb, alpha


def rail_textures(seed: int = 8201) -> None:
    """Rusted track on rotten sleepers, replacing vanilla's rail and rail_corner (alpha cut-out)."""
    import dl_materials as M
    import dl_noise as N
    from gen_vanilla import save as save_vanilla
    t = M.T
    yy, xx = np.mgrid[0:t, 0:t]
    wood = N.ramp(N.spectral(t, t, beta=1.4, seed=seed, aniso=(5.0, 1.0)), [(0.0, "#2e2419"), (1.0, "#5a4632")])
    steel, _ = M.metal_base(seed + 1, light="#6d6a64", dark="#3e3b37")
    rc, rmask = M.rust_layer(seed + 2, 0.7)
    steel = N.lerp(steel, rc, rmask)
    for name, rails in (("rail", lambda: ((np.abs(xx - 7) <= 1) | (np.abs(xx - 24) <= 1))),
                        ("rail_corner", lambda: ((np.abs(np.hypot(xx - t, yy - t) - 24.5) <= 1.2) | (np.abs(np.hypot(xx - t, yy - t) - 7.5) <= 1.2)))):
        sleepers = ((yy % 8) <= 3) & (xx >= 2) & (xx <= t - 3)
        if name == "rail_corner":
            ang = np.arctan2(t - yy, t - xx)
            sleepers = (((ang * 16 / np.pi) % 4) <= 1.5) & (np.hypot(xx - t, yy - t) >= 4.5) & (np.hypot(xx - t, yy - t) <= 27.5)
        rail = rails()
        rot = N.spectral(t, t, beta=1.0, seed=seed + 3) > 0.82  # missing chunks of rotten wood
        rgb = np.where(rail[..., None], steel, wood)
        alpha = (rail | (sleepers & ~rot)).astype(float)
        save_vanilla(name, np.clip(rgb, 0, 255), alpha)


def build() -> int:
    import dl_noise as N
    N.save_png(N.asset("textures/item/fuse.png"), *fuse_item())
    D.asset_json("models/item/fuse.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/fuse"}})
    D.item_model_definition("fuse", f"{NS}:item/fuse")
    rail_textures()
    SITES.mkdir(parents=True, exist_ok=True)
    for old in SITES.glob("*.nbt"):
        old.unlink()
    catalogue = sites()
    count = 0
    for structure, entries in catalogue.items():
        for name, site in entries:
            site.write(SITES / f"{name}.nbt")
            count += 1
    worldgen(catalogue)
    loot()
    D.merge_lang(TEXT)
    return count


if __name__ == "__main__":
    n = build()
    print(f"{n} site templates written")
