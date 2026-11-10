"""City and western town buildings (Milestone 3, second half), plus helpers shared with the
wilderness sites: multi-storey stairs and overgrowth (vines, moss, leaf litter).

Every building follows the town template rules of gen_towns.py: at most 14x14, the front door on
the north edge (z = 0), the ground floor at y = 0, and the whole box filled (Building.seal()).
"""
from __future__ import annotations

from gen_towns import Building, OPPOSITE, bedroom, dining, kitchen

STOREY = 4  # floor-to-floor height of multi-storey buildings


# ----------------------------------------------------------------------------- shared helpers

def stairs(b: Building, x: int, z_start: int, stand_from: int, stand_to: int, direction: int = 1, block: str = "concrete",
           slab: str = "concrete_slab") -> int:
    """Half-step stair at column x rising from standing height `stand_from` to `stand_to`, along z in
    `direction`. Clears headroom (which also opens the stairwell in the floor above). Returns the
    z of the last step."""
    steps = 2 * (stand_to - stand_from)
    z = z_start
    for j in range(steps):
        z = z_start + direction * j
        y = stand_from + j // 2
        if j % 2 == 0:
            b.put(x, y, z, slab, type="bottom")
        else:
            b.put(x, y, z, block)
        if y > stand_from:
            b.box(x, stand_from, z, x, y - 1, z, block)
        b.air(x, y + 1, z, x, y + 3, z)
    return z


def overgrow(b: Building, x0: int, z0: int, x1: int, z1: int, y: int, amount: float, height: int = 3) -> None:
    """Nature taking a floor back: moss and leaf litter on empty floor, vines on the walls and
    hanging from the ceiling."""
    faces = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            here = b.blocks.get((x, y, z))
            if here is not None and here[0] != "minecraft:air":
                continue
            roll = b.r.random()
            if roll < amount * 0.35:
                b.put(x, y, z, "minecraft:moss_carpet")
            elif roll < amount * 0.6:
                b.put(x, y, z, "minecraft:leaf_litter", segment_amount=int(b.r.integers(1, 5)), facing=b.pick(list(faces)))
            # Vines on any wall beside this cell, at a random height.
            for face, (dx, dz) in faces.items():
                wall = b.blocks.get((x + dx, y + 1, z + dz))
                if wall is None or wall[0] == "minecraft:air" or b.r.random() > amount * 0.5:
                    continue
                for k in range(1, height):
                    cell = b.blocks.get((x, y + k, z))
                    if cell is None or cell[0] == "minecraft:air":
                        b.put(x, y + k, z, "minecraft:vine", **{face: "true"})
            ceiling = b.blocks.get((x, y + height, z))
            if ceiling is not None and ceiling[0] not in ("minecraft:air", "minecraft:vine") and b.r.random() < amount * 0.25:
                cell = b.blocks.get((x, y + height - 1, z))
                if cell is None or cell[0] == "minecraft:air":
                    b.put(x, y + height - 1, z, "minecraft:vine", up="true")


def floors(b: Building, x0: int, z0: int, x1: int, z1: int, count: int, wall: str, floor: str = "concrete") -> None:
    """Outer walls and floor slabs for `count` storeys of STOREY blocks each."""
    for k in range(count):
        base = k * STOREY
        if k > 0:
            b.box(x0, base, z0, x1, base, z1, wall)
            b.box(x0 + 1, base, z0 + 1, x1 - 1, base, z1 - 1, floor)
        b.walls(x0, z0, x1, z1, base + 1, base + STOREY - 1, wall)
        b.air(x0 + 1, base + 1, z0 + 1, x1 - 1, base + STOREY - 1, z1 - 1)


# ----------------------------------------------------------------------------- city

def apartment_block(seed: int, wall: str, count: int = 3) -> Building:
    sx = sz = 14
    b = Building(sx, 1 + count * STOREY + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="concrete", height=STOREY - 1)
    floors(b, x0, z0, x1, z1, count, wall, floor="boarded_planks")
    door_x = 5
    b.door(door_x, z0, width=2)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    # Stairwell on the east side: flights switch back between columns x1-1 and x1-2.
    sx_a, sx_b = x1 - 1, x1 - 2
    for k in range(count - 1):
        stand = 1 + k * STOREY
        if k % 2 == 0:
            stairs(b, sx_a, z0 + 1, stand, stand + STOREY, 1)
        else:
            stairs(b, sx_b, z0 + 2 * STOREY, stand, stand + STOREY, -1)
    for k in range(count):
        base = k * STOREY
        stand = base + 1
        b.partition_x(sx_b - 1, z0 + 1, z1 - 1, stand, base + STOREY - 1, "plaster_wall", door_z=z0 + 1 if k % 2 == 0 else z1 - 1)
        mid = (x0 + sx_b - 1) // 2
        b.partition_z(z0 + 6, x0 + 1, sx_b - 2, stand, base + STOREY - 1, "plaster_wall", door_x=mid)
        kitchen(b, x0 + 1, x0 + 4, z1 - 1, y=stand)
        b.container(x0 + 5, stand, z1 - 1, "fridge", "north", "fridge")
        dining(b, x0 + 3, z1 - 3, y=stand)
        bedroom(b, x0 + 1, sx_b - 3, z0 + 1, z0 + 5, y=stand) if k > 0 else None
        if k == 0:
            for x in range(x0 + 1, door_x - 1, 2):  # mailboxes in the lobby
                b.container(x, stand, z0 + 1, "filing_cabinet", "south", "house_drawers")
        b.windows(x0, z0, x1, z1, stand + 1, spacing=2, skip=[(door_x, z0), (door_x + 1, z0)], glass=0.5, boarded=0.3)
        b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y=stand, blood=0.04, rubble=0.04, webs=0.05)
    b.flat_roof(x0, z0, x1, z1, count * STOREY, "concrete", parapet=wall, holes=0.04)
    return b.seal(ground="sidewalk")


def office_block(seed: int, wall: str, count: int = 3) -> Building:
    sx, sz = 14, 13
    b = Building(sx, 1 + count * STOREY + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="cracked_hospital_tiles", height=STOREY - 1, corner="concrete")
    floors(b, x0, z0, x1, z1, count, wall, floor="cracked_hospital_tiles")
    door_x = 6
    b.door(door_x, z0, width=2)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    for k in range(count - 1):
        stand = 1 + k * STOREY
        if k % 2 == 0:
            stairs(b, x0 + 1, z0 + 1, stand, stand + STOREY, 1)
        else:
            stairs(b, x0 + 2, z0 + 2 * STOREY, stand, stand + STOREY, -1)
    for k in range(count):
        base = k * STOREY
        stand = base + 1
        for x in range(x0 + 4, x1 - 1, 3):
            for z in (z0 + 3, z0 + 7):
                if z < z1 - 1:
                    b.decor(x, stand, z, "desk", "north")
                    b.decor(x, stand, z + 1, "chair", "north")
        for x in range(x0 + 4, x1, 2):
            b.container(x, stand, z1 - 1, "filing_cabinet", "north", "office",
                        lock={"type": "key", "key": "records_room"} if (k == count - 1 and x == x0 + 4) else None)
        b.decor(x1 - 1, stand + 1, z0 + 2, "whiteboard", "west")
        if k == count - 1:
            b.container(x1 - 1, stand, z1 - 2, "safe", "west", "safe", lock={"type": "keycard", "level": 2})
        for x in range(x0 + 1, x1):  # ribbon windows of shattered glass
            for z in (z0, z1):
                if not (k == 0 and z == z0 and x in (door_x, door_x + 1)):
                    b.window(x, stand + 1, z, glass=0.6, boarded=0.1)
        b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y=stand, blood=0.03, rubble=0.05, webs=0.04)
    b.flat_roof(x0, z0, x1, z1, count * STOREY, "concrete", parapet="concrete", holes=0.03)
    return b.seal(ground="sidewalk")


def department_store(seed: int) -> Building:
    sx = sz = 14
    b = Building(sx, 1 + 2 * STOREY + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "weathered_bricks", floor="cracked_hospital_tiles", height=STOREY - 1, corner="concrete")
    floors(b, x0, z0, x1, z1, 2, "weathered_bricks", floor="cracked_hospital_tiles")
    b.door(5, z0, width=3)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    for x in range(x0 + 1, x1):
        if x not in (5, 6, 7):
            b.window(x, 1, z0, glass=0.4, boarded=0.4)
            b.window(x, 2, z0, glass=0.4, boarded=0.4)
    stairs(b, x1 - 1, z0 + 2, 1, 1 + STOREY, 1)
    # Ground floor: grocery aisles and tills.
    for x in range(x0 + 2, x1 - 3, 3):
        for z in range(z0 + 4, z1 - 1):
            b.decor(x, 1, z, "store_shelf", "west")
            b.decor(x + 1, 1, z, "store_shelf", "east")
    for x in (x0 + 1, x0 + 3):
        b.decor(x, 1, z0 + 2, "counter", "north")
    b.container(x0 + 1, 1, z1 - 1, "supply_locker", "north", "store_storage")
    # Upper floor: clothing on shelves and in display dressers, the manager's office in a corner.
    up = 1 + STOREY
    for x in range(x0 + 2, x1 - 3, 3):
        for z in range(z0 + 3, z1 - 4, 2):
            b.container(x, up, z, "dresser", "south", "house_bedroom")
    b.partition_z(z1 - 4, x0 + 1, x0 + 5, up, up + 2, "plaster_wall", door_x=x0 + 3)
    b.container(x0 + 1, up, z1 - 1, "safe", "north", "safe", lock={"type": "key", "key": "records_room"})
    b.decor(x0 + 3, up, z1 - 2, "desk", "north")
    b.windows(x0, z0, x1, z1, up + 1, spacing=2, glass=0.5, boarded=0.3)
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.05, rubble=0.05, webs=0.04)
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y=up, blood=0.03, rubble=0.04, webs=0.06)
    b.flat_roof(x0, z0, x1, z1, 2 * STOREY, "concrete", parapet="weathered_bricks", holes=0.05)
    return b.seal(ground="sidewalk")


def bank(seed: int) -> Building:
    sx, sz, height = 14, 13, 5
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "stained_concrete", floor="hospital_tiles", height=height, corner="concrete")
    b.door(6, z0, width=2, height=3)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(6, z0), (7, z0)], tall=True, glass=0.3, boarded=0.5)
    teller_z = z0 + 5
    for x in range(x0 + 1, x1):
        b.decor(x, 1, teller_z, "counter", "north")
        b.put(x, 2, teller_z, "minecraft:iron_bars")
    b.air(x1 - 1, 1, teller_z, x1 - 1, 2, teller_z)
    for x in range(x0 + 2, x1 - 1, 3):
        b.decor(x, 1, teller_z + 2, "desk", "south")
    vault_z = z1 - 3
    b.partition_z(vault_z, x0 + 1, x1 - 1, 1, height, "stained_concrete")
    b.put(6, 1, vault_z, "minecraft:iron_bars")
    b.put(6, 2, vault_z, "minecraft:iron_bars")
    for i, x in enumerate(range(x0 + 2, x1 - 1, 3)):
        b.container(x, 1, z1 - 1, "safe", "north", "safe", lock={"type": "keycard", "level": 2} if i % 2 == 0 else None)
    b.container(x0 + 1, 1, teller_z + 1, "filing_cabinet", "east", "office", lock={"type": "key", "key": "records_room"})
    b.decal(6, 1, teller_z - 1, "dried_blood_pool")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.04, rubble=0.05, webs=0.04)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "concrete", parapet="stained_concrete")
    return b.seal(ground="sidewalk")


def courthouse(seed: int) -> Building:
    sx, sz, height = 14, 14, 5
    b = Building(sx, 1 + height + Building.roof_height(sx), sz, seed)
    x0, z0, x1, z1 = 1, 2, sx - 2, sz - 1
    b.shell(x0, z0, x1, z1, "plaster_wall", floor="boarded_planks", height=height, corner="concrete")
    for x in range(x0, x1 + 1, 2):  # a portico of columns
        b.box(x, 1, 0, x, height, 0, "concrete")
    b.box(x0, 0, 0, x1, 0, z0 - 1, "concrete")
    b.box(x0, height + 1, 0, x1, height + 1, z0 - 1, "concrete_slab", type="bottom")
    b.door(6, z0, width=2, height=3)
    b.windows(x0, z0, x1, z1, 2, spacing=2, sides=("east", "west"), tall=True, glass=0.5, boarded=0.3)
    bench_end = z1 - 5
    for z in range(z0 + 2, bench_end, 2):
        for x in range(x0 + 1, x1):
            if x not in (6, 7):
                b.decor(x, 1, z, "chair", "south")
    b.box(x0 + 2, 1, z1 - 3, x1 - 2, 1, z1 - 1, "boarded_planks")  # the bench platform
    b.decor(6, 2, z1 - 2, "desk", "north")
    b.decor(7, 2, z1 - 2, "desk", "north")
    b.container(x0 + 1, 1, z1 - 1, "filing_cabinet", "north", "office", lock={"type": "key", "key": "records_room"})
    b.container(x1 - 1, 1, z1 - 1, "filing_cabinet", "north", "office")
    b.decor(x1 - 1, 1, z0 + 1, "bookshelf", "west")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.02, rubble=0.05, webs=0.07)
    b.gable_roof(0, z0 - 1, sx - 1, sz - 1, 1 + height, "z", slab="cracked_asphalt_slab", gable="plaster_wall")
    return b.seal(ground="sidewalk")


def fire_station(seed: int, wall: str) -> Building:
    sx, sz, height = 14, 13, 5
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="concrete", height=height, corner="concrete")
    b.door(x0 + 1, z0, width=4, height=4)  # the engine bay door, rolled up
    b.door(x1 - 2, z0)
    b.box(x0, 0, 0, x1, 0, 0, "cracked_concrete")
    b.partition_x(x0 + 7, z0 + 1, z1 - 1, 1, height, wall, door_z=z0 + 3)
    b.box(x0 + 1, 0, z0 + 1, x0 + 6, 0, z1 - 1, "diamond_plate")
    for z in range(z0 + 2, z1 - 1, 2):
        b.container(x0 + 6, 1, z, "supply_locker", "west", "fire_station")
    for x in range(x0 + 8, x1, 2):
        b.decor(x, 1, z1 - 1, "bed_frame", "north")
    kitchen(b, x0 + 8, x1 - 1, z0 + 1, facing="south", pantry=False)
    b.container(x1 - 1, 1, z0 + 2, "kitchen_cupboard", "west", "house_kitchen")
    dining(b, x0 + 10, z0 + 4)
    b.windows(x0, z0, x1, z1, 2, spacing=3, sides=("south", "east"), glass=0.4, boarded=0.4)
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.05, webs=0.04)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "concrete", parapet=wall)
    return b.seal(ground="cracked_concrete")


def motel(seed: int, wall: str) -> Building:
    """A row of rooms under one roof with doors on a covered walkway, the office at the west end."""
    sx, sz, height = 14, 12, 3
    b = Building(sx, 1 + height + 2, sz, seed)
    walk = 2
    x0, z0, x1, z1 = 0, walk, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="boarded_planks", height=height)
    b.box(x0, 0, 0, x1, 0, walk - 1, "cracked_concrete")
    for x in range(x0, x1 + 1, 4):
        b.box(x, 1, 0, x, height, 0, "dead_log", axis="y")
    rooms = [(x0 + 1, x0 + 3), (x0 + 5, x0 + 8), (x0 + 10, x1 - 1)]
    for i, (ra, rb) in enumerate(rooms):
        if i > 0:
            b.partition_x(ra - 1, z0 + 1, z1 - 1, 1, height, wall)
        b.door(ra + 1, z0)
        b.window(rb, 2, z0, glass=0.5, boarded=0.3)
        b.decor(rb, 1, z1 - 1, "bed_frame", "north")
        b.container(ra, 1, z1 - 1, "dresser", "north", "house_drawers")
        b.decor(ra, 1, z0 + 2, "toilet", "east") if z0 + 2 < z1 - 1 else None
        if i == 0:
            b.decor(ra + 1, 1, z0 + 2, "desk", "north")
            b.container(rb, 1, z0 + 1, "filing_cabinet", "west", "office")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.06, rubble=0.03, webs=0.06)
    b.flat_roof(x0, 0, x1, z1, 1 + height, "corrugated_metal", holes=0.05)
    return b.seal(ground="cracked_asphalt")


def emergency_room(seed: int) -> Building:
    """A clinic turned triage station in the last days: beds everywhere, a barricaded door, blood."""
    sx, sz, height = 14, 14, 4
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "hospital_tiles", floor="cracked_hospital_tiles", height=height, corner="concrete")
    b.door(6, z0, width=2)
    b.put(6, 2, z0, "boarded_planks")
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(6, z0), (7, z0)], glass=0.4, boarded=0.5)
    for x in range(x0 + 1, x1, 2):
        for z in (z0 + 3, z0 + 7, z1 - 1):
            if z <= z1 - 1 and b.chance(0.8):
                b.decor(x, 1, z, "bed_frame", "north")
    for x in range(x0 + 2, x1, 4):
        b.container(x, 1, z0 + 1, "medical_cabinet", "south", "clinic")
    b.container(x1 - 1, 1, z0 + 5, "medical_cabinet", "west", "pharmacy", lock={"type": "keycard", "level": 1})
    b.container(x0 + 1, 1, z0 + 5, "filing_cabinet", "east", "office")
    for _ in range(6):
        x, z = int(b.r.integers(x0 + 1, x1)), int(b.r.integers(z0 + 1, z1))
        if (x, 1, z) not in b.blocks or b.blocks[(x, 1, z)][0] == "minecraft:air":
            b.decal(x, 1, z, b.pick(["dried_blood_pool", "drag_marks", "dried_blood_spots"]))
    b.decal(x1 - 1, 2, z0 + 2, "bloody_handprint", face="wall", facing="west")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.1, rubble=0.03, webs=0.03)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "concrete", parapet="concrete", holes=0.03)
    return b.seal(ground="sidewalk")


# ----------------------------------------------------------------------------- western (desert towns)

def false_front(b: Building, x0: int, x1: int, height: int, wall: str) -> None:
    """The tall square facade that hides a western building's gable."""
    b.box(x0, height + 1, 0, x1, height + 2, 0, wall)
    b.box(x0, height + 3, 0, x1, height + 3, 0, "boarded_planks")


def saloon(seed: int, wall: str) -> Building:
    sx, sz, height = 13, 13, 3
    b = Building(sx, 1 + 2 * STOREY + 4, sz, seed)
    x0, z0, x1, z1 = 0, 0, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="boarded_planks", height=STOREY - 1, corner="dead_log")
    floors(b, x0, z0, x1, z1, 2, wall, floor="boarded_planks")
    b.door(5, z0, width=2)
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(5, z0), (6, z0)], sides=("north", "east", "west"), glass=0.4, boarded=0.4)
    for x in range(x0 + 1, x1 - 1):
        b.decor(x, 1, z1 - 3, "counter", "north")
    b.air(x1 - 1, 1, z1 - 3, x1 - 1, 2, z1 - 3)
    b.container(x0 + 1, 1, z1 - 1, "kitchen_cupboard", "north", "house_kitchen")
    b.decor(x0 + 2, 1, z1 - 1, "bookshelf", "north")
    for x, z in ((x0 + 3, z0 + 3), (x0 + 8, z0 + 3), (x0 + 3, z0 + 6), (x0 + 8, z0 + 6)):
        dining(b, x, z, chairs=("west", "east", "north"))
    stairs(b, x1 - 1, z0 + 1, 1, 1 + STOREY, 1, block="boarded_planks", slab="asphalt_slab")
    up = 1 + STOREY
    for x in range(x0 + 1, x1 - 3, 4):
        b.decor(x + 1, up, z1 - 1, "bed_frame", "north")
        b.container(x, up, z1 - 1, "dresser", "north", "house_drawers")
    b.windows(x0, z0, x1, z1, up + 1, spacing=3, glass=0.3, boarded=0.5)
    false_front(b, x0, x1, 2 * STOREY - 1, wall)
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.04, rubble=0.03, webs=0.06)
    b.flat_roof(x0, z0, x1, z1, 2 * STOREY, "corrugated_metal", holes=0.05)
    return b.seal(ground="minecraft:coarse_dirt")


def general_store(seed: int, wall: str) -> Building:
    sx, sz, height = 12, 12, 4
    b = Building(sx, 1 + height + 4, sz, seed)
    x0, z0, x1, z1 = 0, 2, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="boarded_planks", height=height, corner="dead_log")
    b.box(x0, 0, 0, x1, 0, z0 - 1, "boarded_planks")  # boardwalk
    for x in (x0, x1):
        b.box(x, 1, 0, x, height, 0, "dead_log", axis="y")
    b.box(x0, height + 1, 0, x1, height + 1, z0 - 1, "corrugated_metal")
    b.door(5, z0, width=2)
    b.windows(x0, z0, x1, z1, 2, spacing=2, skip=[(5, z0), (6, z0)], sides=("north",), glass=0.3, boarded=0.5)
    for x in range(x0 + 1, x1, 2):
        b.decor(x, 1, z1 - 1, "store_shelf", "north")
    for z in range(z0 + 2, z1 - 2):
        b.decor(x0 + 1, 1, z, "counter", "east")
    b.container(x1 - 1, 1, z0 + 2, "kitchen_cupboard", "west", "store_storage")
    b.container(x1 - 1, 1, z0 + 4, "toolbox", "west", "workshop")
    b.container(x0 + 1, 1, z1 - 2, "safe", "east", "safe", lock={"type": "key", "key": "maintenance"}) if z1 - 2 > z0 + 4 else None
    b.box(x0, height + 1, z0, x1, height + 3, z0, wall)  # false front
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.04, webs=0.08)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.05)
    return b.seal(ground="minecraft:coarse_dirt")


def wooden_house(seed: int, sx: int, sz: int, wall: str) -> Building:
    height = 3
    b = Building(sx, 1 + height + Building.roof_height(sx), sz, seed)
    x0, z0, x1, z1 = 1, 2, sx - 2, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="boarded_planks", corner="dead_log", footing="minecraft:cobblestone")
    b.box(x0, 0, 0, x1, 0, z0 - 1, "boarded_planks")
    for x in (x0, x1):
        b.box(x, 1, 0, x, height, 0, "dead_log", axis="y")
    b.box(x0, height + 1, 0, x1, height + 1, z0 - 1, "corrugated_metal")
    door_x = (x0 + x1) // 2
    b.door(door_x, z0, frame="boarded_planks")
    b.windows(x0, z0, x1, z1, 2, skip=[(door_x, z0)], glass=0.2, boarded=0.5)
    kitchen(b, x0 + 1, x0 + 3, z1 - 1)
    dining(b, x0 + 2, z0 + 2, chairs=("east",))
    b.decor(x1 - 1, 1, z1 - 1, "bed_frame", "west")
    b.container(x1 - 1, 1, z0 + 1, "dresser", "west", "house_drawers")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, webs=0.08)
    b.gable_roof(0, z0 - 1, sx - 1, sz - 1, 1 + height, "z", gable=wall)
    return b.seal(ground="minecraft:coarse_dirt")


def sheriff_office(seed: int) -> Building:
    sx, sz, height = 12, 12, 4
    b = Building(sx, 1 + height + 4, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "boarded_planks", floor="boarded_planks", height=height, corner="dead_log")
    b.door(3, z0)
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(3, z0)], glass=0.2, boarded=0.6)
    b.decor(x0 + 2, 1, z0 + 3, "desk", "north")
    b.decor(x0 + 2, 1, z0 + 4, "chair", "north")
    b.container(x0 + 1, 1, z0 + 1, "supply_locker", "east", "police_locker", lock={"type": "key", "key": "records_room"})
    b.container(x0 + 1, 1, z0 + 6, "filing_cabinet", "east", "office")
    cell_x = x0 + 6
    b.box(cell_x, 1, z0 + 1, cell_x, 3, z1 - 1, "minecraft:iron_bars")
    b.box(cell_x, 1, z0 + 5, x1 - 1, 3, z0 + 5, "minecraft:iron_bars")
    b.air(cell_x, 1, z0 + 3, cell_x, 2, z0 + 3)
    b.decor(x1 - 1, 1, z0 + 2, "bed_frame", "west")
    b.decor(x1 - 1, 1, z1 - 1, "bed_frame", "west")
    b.decal(x1 - 2, 1, z1 - 2, "dried_blood_spots")
    b.box(x0, height + 1, z0, x1, height + 3, z0, "weathered_siding")  # false front
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.04, rubble=0.03, webs=0.08)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.04)
    return b.seal(ground="minecraft:coarse_dirt")


def catalogue_additions() -> dict[str, list[tuple[str, Building]]]:
    return {
        "city": [
            ("apartment_block_brick", apartment_block(7001, "weathered_bricks")),
            ("apartment_block_concrete", apartment_block(7002, "stained_concrete")),
            ("apartment_block_plaster", apartment_block(7003, "peeling_plaster", count=2)),
            ("office_block_glass", office_block(7011, "concrete")),
            ("office_block_concrete", office_block(7012, "cracked_concrete")),
            ("office_block_brick", office_block(7013, "sooty_bricks", count=2)),
            ("department_store", department_store(7021)),
            ("motel", motel(7031, "plaster_wall")),
            ("motel_b", motel(7032, "weathered_siding")),
        ],
        "civic": [
            ("bank", bank(7041)),
            ("courthouse", courthouse(7051)),
            ("fire_station", fire_station(7061, "weathered_bricks")),
            ("fire_station_b", fire_station(7062, "stained_concrete")),
        ],
        "medical": [
            ("emergency_room", emergency_room(7071)),
            ("emergency_room_b", emergency_room(7072)),
        ],
        "western": [
            ("saloon", saloon(7101, "weathered_siding")),
            ("saloon_b", saloon(7102, "boarded_planks")),
            ("general_store", general_store(7111, "weathered_siding")),
            ("general_store_b", general_store(7112, "boarded_planks")),
            ("wooden_house_a", wooden_house(7121, 11, 11, "boarded_planks")),
            ("wooden_house_b", wooden_house(7122, 12, 10, "weathered_siding")),
            ("wooden_house_c", wooden_house(7123, 10, 12, "boarded_planks")),
            ("sheriff_office", sheriff_office(7131)),
        ],
    }
