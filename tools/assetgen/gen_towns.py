"""DEADLANDS towns: building templates, the town structure and its structure set, the road network
feature, the loot of town containers, and the vanilla blocks towns use, restyled.

Buildings are generated from archetypes (cottage, suburban house, store, police station...) with
seeded variation in size, materials, layout and clutter. Every template:

  * is at most 14 x 14 blocks (TownPlanner's plots are 15 x 15 with a one-block gap),
  * has its front door on the north edge (z = 0 side, facing -Z); the town turns it to its street,
  * has its ground floor at y = 0, which the town sets level with the street,
  * fills its whole box: building blocks, explicit air inside and above, and worn ground around,
    so a hillside never pokes through the roof or walls.

com.deadlands.world.town.TownStructure reads the per-category lists written to
data/deadlands/worldgen/structure/town.json.
"""
from __future__ import annotations

import numpy as np

import dl_data as D
import dl_materials as M
import dl_noise as N
from dl_nbt import Structure
from gen_vanilla import save as save_vanilla

NS = D.NS
STRUCTURES = D.DATA / NS / "structure" / "town"
MAX_FRONT = 14  # TownPlanner.PLOT_FRONT - 1
MAX_DEPTH = 14  # TownPlanner.PLOT_DEPTH - 1
CATEGORIES = ("residential", "commercial", "civic", "medical", "industrial", "rural", "city", "western")

OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


# ----------------------------------------------------------------------------- template builder

class Building(Structure):
    def __init__(self, sx: int, sy: int, sz: int, seed: int) -> None:
        assert sx <= MAX_FRONT and sz <= MAX_DEPTH, (sx, sz)
        super().__init__(sx, sy, sz)
        self.r = np.random.default_rng(seed)

    # -- blocks
    def put(self, x, y, z, name, nbt=None, **props) -> None:
        self.set(x, y, z, name, {k: str(v).lower() for k, v in props.items()} or None, nbt)

    def box(self, x0, y0, z0, x1, y1, z1, name, **props) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, name, **props)

    def air(self, x0, y0, z0, x1, y1, z1) -> None:
        self.box(x0, y0, z0, x1, y1, z1, "minecraft:air")

    def chance(self, p: float) -> bool:
        return bool(self.r.random() < p)

    def pick(self, options):
        return options[int(self.r.integers(0, len(options)))]

    # -- shell
    def walls(self, x0, z0, x1, z1, y0, y1, block, corner=None) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, z0, block)
                self.put(x, y, z1, block)
            for z in range(z0, z1 + 1):
                self.put(x0, y, z, block)
                self.put(x1, y, z, block)
            if corner:
                for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
                    self.put(x, y, z, corner, **({"axis": "y"} if corner.endswith("_log") else {}))

    def shell(self, x0, z0, x1, z1, wall, floor="boarded_planks", height=3, y0=0, corner=None, footing="concrete") -> None:
        """Foundation ring, floor, four walls and an empty interior."""
        self.box(x0, y0, z0, x1, y0, z1, footing)
        self.box(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, floor)
        self.walls(x0, z0, x1, z1, y0 + 1, y0 + height, wall, corner)
        self.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + height, z1 - 1)

    def partition_x(self, x, z0, z1, y0, y1, block, door_z=None) -> None:
        """Interior wall along Z at column x, with an optional doorway."""
        self.box(x, y0, z0, x, y1, z1, block)
        if door_z is not None:
            self.air(x, y0, door_z, x, y0 + 1, door_z)

    def partition_z(self, z, x0, x1, y0, y1, block, door_x=None) -> None:
        self.box(x0, y0, z, x1, y1, z, block)
        if door_x is not None:
            self.air(door_x, y0, z, door_x, y0 + 1, z)

    def door(self, x, z, y=1, width=1, height=2, frame=None) -> None:
        self.air(x, y, z, x + width - 1, y + height - 1, z)
        if frame and self.chance(0.35):
            self.put(x, y + height - 1, z, frame)  # half barricaded

    def window(self, x, y, z, glass=0.45, boarded=0.3) -> None:
        roll = self.r.random()
        if roll < glass:
            self.put(x, y, z, "broken_glass")
        elif roll < glass + boarded:
            self.put(x, y, z, "boarded_planks")
        else:
            self.put(x, y, z, "minecraft:air")  # smashed out

    def windows(self, x0, z0, x1, z1, y, spacing=2, skip=(), sides=("north", "south", "east", "west"), tall=False, **odds) -> None:
        """Windows along the walls of a rectangle, every `spacing` blocks, never in a corner, a doorway
        or where an interior wall meets the outer wall (the `skip` positions)."""
        spots = []
        if "north" in sides:
            spots += [(x, z0) for x in range(x0 + 1, x1, spacing)]
        if "south" in sides:
            spots += [(x, z1) for x in range(x0 + 1, x1, spacing)]
        if "west" in sides:
            spots += [(x0, z) for z in range(z0 + 1, z1, spacing)]
        if "east" in sides:
            spots += [(x1, z) for z in range(z0 + 1, z1, spacing)]
        skip = set(skip)
        for x, z in spots:
            if (x, z) in skip or ((x, y, z) in self.blocks and self.blocks[(x, y, z)][0] == "minecraft:air"):
                continue
            self.window(x, y, z, **odds)
            if tall:
                self.window(x, y + 1, z, **odds)

    # -- roofs
    def flat_roof(self, x0, z0, x1, z1, y, block="corrugated_metal", parapet=None, holes=0.0) -> None:
        self.box(x0, y, z0, x1, y, z1, block)
        if parapet:
            self.walls(x0, z0, x1, z1, y + 1, y + 1, parapet)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if self.chance(holes):
                    self.put(x, y, z, "minecraft:air")

    def gable_roof(self, x0, z0, x1, z1, y, ridge="x", overhang=1, slab="asphalt_slab", fascia="cracked_asphalt", gable=None) -> None:
        """Half-slab gable roof over the rectangle (overhang included), eaves at height y.

        ridge="x": the ridge runs along X (eaves on the front and back); "z": it runs front to back
        (a gable faces the street). The gable walls fill the triangles in the two wall planes."""
        lo, hi = (z0, z1) if ridge == "x" else (x0, x1)
        planes = (x0 + overhang, x1 - overhang) if ridge == "x" else (z0 + overhang, z1 - overhang)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                across = z if ridge == "x" else x
                along = x if ridge == "x" else z
                k = min(across - lo, hi - across)
                top_y = y + k // 2
                upper = k % 2 == 1
                if gable and along in planes and k >= overhang:
                    self.box(x, y, z, x, top_y - 1, z, gable) if top_y > y else None
                    if upper:
                        self.put(x, top_y, z, gable)
                        continue
                if upper and k == overhang:
                    self.put(x, top_y, z, fascia)  # closes the slit above the wall top
                else:
                    self.put(x, top_y, z, slab, type="top" if upper else "bottom")

    @staticmethod
    def roof_height(span: int) -> int:
        """Layers a gable roof over `span` blocks needs, eave layer included."""
        return (span - 1) // 2 // 2 + 1

    # -- contents
    def decor(self, x, y, z, kind, facing="north") -> None:
        self.put(x, y, z, kind, facing=facing)

    def container(self, x, y, z, kind, facing, loot, condition=None, lock=None, ajar=None) -> None:
        nbt = {"id": f"{NS}:loot_container", "LootTable": f"{NS}:containers/{loot}"}
        if lock:
            nbt["deadlands_lock"] = lock
        condition = condition or self.pick(["clean", "dusty", "dusty", "rusty", "damaged"])
        ajar = self.chance(0.2) if ajar is None else ajar
        self.put(x, y, z, kind, nbt=nbt, facing=facing, condition=condition, ajar="true" if ajar else "false")

    def decal(self, x, y, z, name, face="floor", facing="north", turn=None) -> None:
        turn = int(self.r.integers(0, 4)) if turn is None else turn
        self.put(x, y, z, name, face=face, facing=facing, turn=turn)

    def clutter(self, x0, z0, x1, z1, y=1, blood=0.03, rubble=0.03, webs=0.0) -> None:
        """Blood, rubble and cobwebs on empty floor (and in empty upper corners)."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, y, z) in self.blocks and self.blocks[(x, y, z)][0] != "minecraft:air":
                    continue
                roll = self.r.random()
                if roll < blood:
                    self.decal(x, y, z, self.pick(["dried_blood_spots", "dried_blood_spots", "dried_blood_pool", "drag_marks", "faded_stain"]))
                elif roll < blood + rubble:
                    self.put(x, y, z, "rubble")
                elif roll < blood + rubble + webs:
                    up = (x, y + 2, z)
                    if up in self.blocks and self.blocks[up][0] == "minecraft:air":
                        self.put(x, y + 2, z, "minecraft:cobweb")

    def seal(self, ground="minecraft:coarse_dirt") -> "Building":
        """Air everywhere above ground that is still unset; worn ground around the building."""
        sx, sy, sz = self.size
        for x in range(sx):
            for z in range(sz):
                if (x, 0, z) not in self.blocks:
                    self.put(x, 0, z, ground if self.r.random() < 0.8 else self.pick(["minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"]))
                for y in range(1, sy):
                    if (x, y, z) not in self.blocks:
                        self.put(x, y, z, "minecraft:air")
        return self


# ----------------------------------------------------------------------------- shared rooms

def kitchen(b: Building, xa: int, xb: int, z: int, y: int = 1, facing: str = "north", pantry: bool = True) -> None:
    """Counter run along a wall from xa to xb: counters with a stove and a sink, and a pantry."""
    run = list(range(xa, xb + 1))
    order = ["counter", "stove", "sink", "counter", "counter", "stove"]
    for i, x in enumerate(run):
        b.decor(x, y, z, order[i % len(order)], facing)
    if pantry and len(run) >= 3:
        b.container(run[-1], y, z, "kitchen_cupboard", facing, "house_kitchen")
    if pantry and len(run) >= 4:
        b.container(run[0], y, z, "fridge", facing, "fridge")


def dining(b: Building, x: int, z: int, y: int = 1, chairs=("west", "east")) -> None:
    b.decor(x, y, z, "table")
    for side in chairs:
        dx, dz = {"west": (-1, 0), "east": (1, 0), "north": (0, -1), "south": (0, 1)}[side]
        if (x + dx, y, z + dz) in b.blocks and b.blocks[(x + dx, y, z + dz)][0] == "minecraft:air":
            b.decor(x + dx, y, z + dz, "chair", OPPOSITE[side])  # chairs face the table


def bedroom(b: Building, xa: int, xb: int, za: int, zb: int, y: int = 1) -> None:
    """Bed against the back wall, a chest of drawers beside it, a closet in the far corner."""
    bx = xb
    b.decor(bx, y, zb, "bed_frame", "north")
    if xb - 1 >= xa:
        b.container(xb - 1, y, zb, "dresser", "north", "house_drawers")
    if xa < xb - 1:
        b.container(xa, y, zb, "supply_locker", "north", "house_bedroom")
    if za < zb - 1 and b.chance(0.5):
        b.decor(xa, y, za, "desk", "east")


# ----------------------------------------------------------------------------- residential

def cottage(seed: int, sx: int, sz: int, wall: str, ridge: str) -> Building:
    height = 3
    span = sz if ridge == "x" else sx
    b = Building(sx, 1 + height + Building.roof_height(span), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, wall, corner=b.pick([None, "boarded_planks", "dead_log"]))
    px = x0 + 4
    door_x = x0 + 2
    b.door(door_x, z0, frame="boarded_planks")
    b.put(door_x, 0, 0, "concrete")
    b.partition_x(px, z0 + 1, z1 - 1, 1, height, "plaster_wall", door_z=z0 + 2)
    b.windows(x0, z0, x1, z1, 2, skip=[(door_x, z0), (px, z0), (px, z1)])
    kitchen(b, x0 + 1, px - 1, z1 - 1, pantry=False)
    dining(b, x0 + 2, z0 + 3, chairs=("west",)) if z1 - 2 > z0 + 3 else None
    b.container(x0 + 1, 1, z0 + 1, "kitchen_cupboard", "south", "house_kitchen")
    bedroom(b, px + 1, x1 - 1, z0 + 1, z1 - 1)
    if x1 - px >= 5:
        b.decor(px + 2, 1, z0 + 1, "couch", "south")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, webs=0.06)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + height, ridge, gable=wall)
    return b.seal()


def suburban_house(seed: int, sx: int, sz: int, wall: str, ridge: str) -> Building:
    height = 3
    span = sz if ridge == "x" else sx
    b = Building(sx, 1 + height + Building.roof_height(span), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, wall)
    mid_x = x0 + (x1 - x0) // 2
    mid_z = z0 + (z1 - z0) // 2
    door_x = mid_x - 1
    b.door(door_x, z0, frame="boarded_planks")
    b.box(door_x - 1, 0, 0, door_x + 1, 0, 0, "concrete")
    # Left: living room (front) and kitchen (back). Right: bedroom (back) and bathroom (front).
    b.partition_x(mid_x, z0 + 1, z1 - 1, 1, height, "plaster_wall", door_z=mid_z)
    b.partition_z(mid_z, mid_x + 1, x1 - 1, 1, height, "plaster_wall", door_x=mid_x + 1)
    b.windows(x0, z0, x1, z1, 2, skip=[(door_x, z0), (mid_x, z0), (mid_x, z1), (x1, mid_z), (x0, mid_z)])
    kitchen(b, x0 + 1, mid_x - 2, z1 - 1)
    dining(b, x0 + 2, z1 - 3) if z1 - 3 > mid_z else None
    b.decor(x0 + 1, 1, z0 + 2, "couch", "east")
    if mid_x - x0 >= 5:
        b.decor(x0 + 3, 1, z0 + 2, "table")
    bedroom(b, mid_x + 2, x1 - 1, mid_z + 1, z1 - 1)
    b.decor(x1 - 1, 1, z0 + 1, "toilet", "west")
    b.decor(x1 - 1, 1, mid_z - 1, "sink", "west") if mid_z - 1 > z0 + 1 else None
    b.container(mid_x + 1, 1, z0 + 1, "medical_cabinet", "east", "house_bathroom")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, webs=0.05)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + height, ridge, gable=wall)
    return b.seal()


def two_story_house(seed: int, sx: int, sz: int, wall: str) -> Building:
    height = 3
    b = Building(sx, 1 + 2 * (height + 1) + Building.roof_height(sz), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    up = height + 1  # upper floor level
    b.shell(x0, z0, x1, z1, wall)
    b.walls(x0, z0, x1, z1, up, up, wall)  # band between storeys
    b.box(x0 + 1, up, z0 + 1, x1 - 1, up, z1 - 1, "boarded_planks")
    b.walls(x0, z0, x1, z1, up + 1, up + height, wall)
    b.air(x0 + 1, up + 1, z0 + 1, x1 - 1, up + height, z1 - 1)
    door_x = x0 + 2
    b.door(door_x, z0, frame="boarded_planks")
    b.put(door_x, 0, 0, "concrete")
    # Half-step staircase up the east wall, front to back: slab, block, slab, block... rising half a
    # block per step from the ground floor (standing height 1) to the upper floor (standing height up + 1).
    stair_x = x1 - 1
    for j in range(2 * up):
        z = z0 + 1 + j
        assert z <= z1 - 1, "house too shallow for its staircase"
        y = 1 + j // 2
        if j % 2 == 0:
            b.put(stair_x, y, z, "concrete_slab", type="bottom")
        else:
            b.put(stair_x, y, z, "concrete")
        if y > 1:
            b.box(stair_x, 1, z, stair_x, y - 1, z, "concrete")
        b.air(stair_x, y + 1, z, stair_x, min(y + 3, up + height), z)  # headroom, through the upper floor
    b.windows(x0, z0, x1, z1, 2, skip=[(door_x, z0)], sides=("north", "west", "south"))
    b.windows(x0, z0, x1, z1, up + 2, sides=("north", "south", "east", "west"))
    kitchen(b, x0 + 1, x0 + 3, z1 - 1)
    dining(b, x0 + 2, z1 - 3, chairs=("west", "east")) if z1 - 3 > z0 + 1 else None
    b.decor(x0 + 1, 1, z0 + 2, "couch", "east")
    # Upstairs: a bedroom at the back, a bathroom at the front.
    bedroom(b, x0 + 1, x1 - 2, z0 + 3, z1 - 1, y=up + 1)
    b.decor(x0 + 1, up + 1, z0 + 1, "toilet", "east")
    b.decor(x0 + 2, up + 1, z0 + 1, "sink", "south")
    b.container(x0 + 3, up + 1, z0 + 1, "medical_cabinet", "south", "house_bathroom")
    b.clutter(x0 + 1, z0 + 1, x1 - 2, z1 - 1, webs=0.04)
    b.clutter(x0 + 1, z0 + 2, x1 - 2, z1 - 1, y=up + 1, webs=0.08)
    b.gable_roof(0, 0, sx - 1, sz - 1, up + height + 1, "x", gable=wall)
    return b.seal()


def trailer(seed: int, sx: int, rust: str) -> Building:
    sz, height = 6, 3
    b = Building(sx, 1 + height + 1, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 2
    b.shell(x0, z0, x1, z1, rust, floor="diamond_plate", footing="minecraft:cobblestone")
    door_x = x0 + sx // 2
    b.door(door_x, z0)
    b.put(door_x, 0, 0, "concrete_slab", type="bottom")
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(door_x, z0)])
    b.decor(x1 - 1, 1, z1 - 1, "bed_frame", "west")
    b.container(x1 - 1, 1, z0 + 1, "dresser", "west", "house_drawers")
    kitchen(b, x0 + 1, x0 + 3, z1 - 1, pantry=False)
    b.container(x0 + 1, 1, z0 + 1, "kitchen_cupboard", "east", "house_kitchen")
    b.decor(door_x + 2, 1, z1 - 1, "couch", "north") if door_x + 2 < x1 - 1 else None
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.06, webs=0.04)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.06)
    return b.seal()


# ----------------------------------------------------------------------------- commercial

def store(seed: int, sx: int, sz: int, wall: str, kind: str) -> Building:
    """Corner store, pharmacy or hardware store: display windows, shelf aisles, a till, a stockroom."""
    height = 4
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, wall, floor="cracked_hospital_tiles" if kind == "pharmacy" else "concrete", height=height)
    door_x = sx // 2 - 1
    b.door(door_x, z0, width=2)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    for x in range(x0 + 1, x1):
        if x not in (door_x, door_x + 1):
            for y in (1, 2):
                b.window(x, y, z0, glass=0.35, boarded=0.35)
    b.box(x0, 4, z0, x1, 4, z0, "rusted_sheet_metal")  # sign board
    stock_z = z1 - 3
    b.partition_z(stock_z, x0 + 1, x1 - 1, 1, height, wall, door_x=x1 - 2)
    # Aisles: shelves back to back, facing the walkways.
    for x in range(x0 + 2, x1 - 1, 3):
        for z in range(z0 + 3, stock_z - 1):
            b.decor(x, 1, z, "store_shelf", "west")
            if x + 1 < x1:
                b.decor(x + 1, 1, z, "store_shelf", "east")
    # Till near the door.
    b.decor(x0 + 1, 1, z0 + 2, "counter", "east")
    b.decor(x0 + 1, 1, z0 + 3, "counter", "east") if stock_z - 1 > z0 + 3 else None
    b.container(x0 + 1, 1, z0 + 1, "filing_cabinet", "east", "house_drawers")
    # Stockroom.
    loot = {"pharmacy": "pharmacy", "hardware": "workshop"}.get(kind, "store_storage")
    stock_kind = {"pharmacy": "medical_cabinet", "hardware": "toolbox"}.get(kind, "supply_locker")
    for i, x in enumerate(range(x0 + 1, x1 - 2, 2)):
        lock = None
        if kind == "pharmacy" and i == 0:
            lock = {"type": "key", "key": "pharmacy"}
        b.container(x, 1, z1 - 1, stock_kind, "north", loot, lock=lock)
    if kind == "pharmacy":
        b.container(x1 - 1, 1, z0 + 2, "medical_cabinet", "west", "pharmacy")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.05, rubble=0.04, webs=0.05)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "concrete", parapet="concrete", holes=0.03)
    return b.seal(ground="sidewalk")


def diner(seed: int, sx: int, sz: int) -> Building:
    height = 4
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "plaster_wall", floor="hospital_tiles", height=height)
    door_x = 2
    b.door(door_x, z0)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    for x in range(x0 + 1, x1):
        if x != door_x:
            b.window(x, 2, z0, glass=0.45, boarded=0.25)
    b.box(x0, 4, z0, x1, 4, z0, "weathered_siding")
    counter_z = z0 + 5
    for x in range(x0 + 1, x1 - 1):
        b.decor(x, 1, counter_z, "counter", "north")
        if x % 2 == 0:
            b.decor(x, 1, counter_z - 1, "chair", "south")
    b.air(x1 - 1, 1, counter_z, x1 - 1, 2, counter_z)  # gap to the kitchen
    for x in (x0 + 4, x0 + 8):
        if x < x1 - 1:
            dining(b, x, z0 + 1, chairs=("west", "east"))
    kitchen(b, x0 + 1, x1 - 2, z1 - 1)
    b.container(x0 + 1, 1, counter_z + 1, "supply_locker", "east", "house_kitchen")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.07, rubble=0.03, webs=0.03)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", parapet="weathered_siding", holes=0.04)
    return b.seal(ground="sidewalk")


# ----------------------------------------------------------------------------- civic

def police_station(seed: int, sx: int, sz: int) -> Building:
    height = 4
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "weathered_bricks", floor="concrete", height=height, corner="concrete")
    door_x = sx // 2 - 1
    b.door(door_x, z0, width=2)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(door_x, z0), (door_x + 1, z0)], glass=0.3, boarded=0.5)
    # Front desk.
    for x in range(x0 + 2, x1 - 1):
        if x not in (door_x, door_x + 1):
            b.decor(x, 1, z0 + 3, "desk", "north")
    b.container(x0 + 1, 1, z0 + 1, "filing_cabinet", "east", "office")
    b.container(x1 - 1, 1, z0 + 1, "filing_cabinet", "west", "office", lock={"type": "key", "key": "records_room"})
    # Cells along the back wall behind bars.
    cell_z = z1 - 3
    for x in range(x0 + 1, x1):
        b.put(x, 1, cell_z, "minecraft:iron_bars")
        b.put(x, 2, cell_z, "minecraft:iron_bars")
        b.put(x, 3, cell_z, "minecraft:iron_bars")
    for x in range(x0 + 4, x1 - 1, 4):
        b.box(x, 1, cell_z + 1, x, height, z1 - 1, "weathered_bricks")
    for x in range(x0 + 2, x1 - 1, 4):
        b.air(x, 1, cell_z, x, 2, cell_z)  # cell doors hang open
        b.decor(x, 1, z1 - 1, "bed_frame", "north") if (x, 1, z1 - 1) not in b.blocks or b.blocks[(x, 1, z1 - 1)][0] == "minecraft:air" else None
    # Armoury lockers.
    b.container(x0 + 1, 1, z0 + 5, "supply_locker", "east", "police_locker")
    b.container(x1 - 1, 1, z0 + 5, "supply_locker", "west", "police_locker", lock={"type": "keycard", "level": 1})
    b.container(x1 - 1, 1, z0 + 6, "military_crate", "west", "police_locker") if z0 + 6 < cell_z else None
    b.decal(door_x, 1, z0 + 1, "dried_blood_pool")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.06, rubble=0.04, webs=0.04)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "concrete", parapet="weathered_bricks")
    return b.seal(ground="sidewalk")


def town_hall(seed: int, sx: int, sz: int) -> Building:
    height = 4
    b = Building(sx, 1 + height + Building.roof_height(sz), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, "plaster_wall", floor="boarded_planks", height=height, corner="weathered_bricks")
    door_x = sx // 2 - 1
    b.door(door_x, z0, width=2)
    b.box(door_x - 1, 0, 0, door_x + 2, 0, 0, "concrete")
    b.windows(x0, z0, x1, z1, 2, spacing=2, skip=[(door_x, z0), (door_x + 1, z0)], tall=True)
    for x in range(x0 + 1, x1, 3):  # desks with a walkway behind them to the filing cabinets
        b.decor(x, 1, z1 - 3, "desk", "north")
        b.decor(x, 1, z1 - 4, "chair", "south") if z1 - 4 > z0 + 2 else None
        b.container(x, 1, z1 - 1, "filing_cabinet", "north", "office",
                    lock={"type": "key", "key": "records_room"} if x == x0 + 1 else None)
    b.decor(x0 + 1, 1, z0 + 1, "counter", "east")
    b.decor(x0 + 1, 1, z0 + 2, "counter", "east")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.04, webs=0.06)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + height, "x", slab="cracked_asphalt_slab", gable="plaster_wall")
    return b.seal(ground="minecraft:dirt_path")


def church(seed: int, sx: int, sz: int) -> Building:
    height = 5
    b = Building(sx, 1 + height + Building.roof_height(sx), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, "weathered_siding", floor="boarded_planks", height=height, corner="dead_log")
    door_x = sx // 2
    b.door(door_x - 1, z0, width=3 if sx % 2 == 1 else 2, height=3)
    b.box(door_x - 1, 0, 0, door_x + 1, 0, 0, "concrete")
    b.windows(x0, z0, x1, z1, 2, spacing=2, sides=("east", "west"), tall=True, glass=0.55, boarded=0.25)
    for z in range(z0 + 3, z1 - 3, 2):  # pews face the altar
        for x in range(x0 + 1, x1):
            if abs(x - door_x) > 0:
                b.decor(x, 1, z, "chair", "south")
    b.decor(door_x, 1, z1 - 1, "table", "north")
    b.container(x0 + 1, 1, z1 - 1, "filing_cabinet", "east", "church")
    b.decal(door_x, 1, z1 - 2, "dried_blood_pool")
    b.decal(door_x, 1, z1 - 3, "drag_marks", turn=0)
    b.decal(x1 - 1, 3, z0 + 3, "survivor_mark", face="wall", facing="west")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.04, rubble=0.05, webs=0.08)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + height, "z", gable="weathered_siding")
    return b.seal(ground="minecraft:dirt_path")


# ----------------------------------------------------------------------------- medical

def clinic(seed: int, sx: int, sz: int) -> Building:
    height = 4
    b = Building(sx, 1 + height + 2, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "hospital_tiles", floor="cracked_hospital_tiles", height=height, corner="concrete")
    door_x = sx // 2 - 1
    b.door(door_x, z0, width=2)
    b.box(x0, 0, 0, x1, 0, 0, "sidewalk")
    b.windows(x0, z0, x1, z1, 2, spacing=3, skip=[(door_x, z0), (door_x + 1, z0)], glass=0.5, boarded=0.3)
    ward_z = z0 + 4
    b.partition_z(ward_z, x0 + 1, x1 - 1, 1, height, "hospital_tiles", door_x=door_x)
    b.decor(x0 + 1, 1, z0 + 2, "desk", "east")
    b.decor(x0 + 2, 1, z0 + 2, "chair", "west")
    b.container(x0 + 1, 1, z0 + 1, "filing_cabinet", "east", "office")
    for x in range(x0 + 1, x1, 3):  # ward beds against the back wall
        b.decor(x, 1, z1 - 1, "bed_frame", "north")
        b.container(x + 1, 1, z1 - 1, "medical_cabinet", "north", "clinic") if x + 1 < x1 else None
    b.container(x1 - 1, 1, ward_z + 1, "medical_cabinet", "west", "pharmacy", lock={"type": "key", "key": "pharmacy"})
    b.decor(x1 - 1, 1, z0 + 1, "sink", "west")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.09, rubble=0.03, webs=0.04)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "concrete", parapet="concrete", holes=0.03)
    return b.seal(ground="sidewalk")


# ----------------------------------------------------------------------------- industrial

def garage(seed: int, sx: int, sz: int) -> Building:
    height = 4
    b = Building(sx, 1 + height + 1, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "corrugated_metal", floor="concrete", height=height, corner="rusted_sheet_metal")
    bay = 3
    b.door(x0 + 2, z0, width=bay, height=3)
    b.door(x1 - 2, z0)
    b.box(x0, 0, 0, x1, 0, 0, "cracked_concrete")
    b.box(x0 + 2, 0, z0 + 1, x0 + 1 + bay, 0, z1 - 2, "diamond_plate")
    for z in range(z0 + 2, z1 - 1, 2):
        b.decor(x1 - 1, 1, z, "store_shelf", "west")
    b.container(x1 - 1, 1, z1 - 1, "toolbox", "north", "workshop", lock={"type": "key", "key": "maintenance"})
    b.container(x0 + 1, 1, z1 - 1, "toolbox", "north", "workshop")
    b.container(x0 + 1, 1, z1 - 2, "supply_locker", "east", "workshop")
    b.decor(x0 + 6, 1, z1 - 1, "table", "north") if x0 + 6 < x1 - 1 else None
    for x, z in ((x0 + 3, z0 + 3), (x0 + 4, z0 + 5), (x0 + 3, z0 + 7)):
        if z < z1:
            b.decal(x, 1, z, "faded_stain")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.05)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.05)
    return b.seal(ground="cracked_concrete")


def warehouse(seed: int, sx: int, sz: int) -> Building:
    height = 5
    b = Building(sx, 1 + height + 1, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "corrugated_metal", floor="concrete", height=height, corner="rusted_sheet_metal")
    b.door(sx // 2 - 1, z0, width=3, height=4)
    b.box(x0, 0, 0, x1, 0, 0, "cracked_concrete")
    for x in range(x0 + 2, x1 - 1, 3):
        for z in range(z0 + 3, z1 - 1):
            b.decor(x, 1, z, "store_shelf", "west")
            b.decor(x, 2, z, "store_shelf", "west") if b.chance(0.5) else None
    crates = [(x0 + 1, z1 - 1, "north"), (x1 - 1, z1 - 1, "north"), (x1 - 1, z1 - 2, "west"), (x0 + 1, z0 + 2, "east")]
    for i, (x, z, facing) in enumerate(crates):
        b.container(x, 1, z, "military_crate" if i % 2 == 0 else "toolbox", facing, "store_storage" if i % 2 == 0 else "workshop")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.05, webs=0.05)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.08)
    return b.seal(ground="cracked_concrete")


def library(seed: int, sx: int, sz: int) -> Building:
    """The town library: stacks in rows, reading desks by the windows, a front desk and the records."""
    height = 4
    b = Building(sx, 1 + height + Building.roof_height(sz), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, "weathered_bricks", floor="boarded_planks", height=height, corner="plaster_wall")
    door_x = sx // 2 - 1
    b.door(door_x, z0, width=2)
    b.box(door_x - 1, 0, 0, door_x + 2, 0, 0, "concrete")
    b.windows(x0, z0, x1, z1, 2, spacing=2, skip=[(door_x, z0), (door_x + 1, z0)], tall=True, glass=0.5, boarded=0.25)
    # Stacks: rows of shelves back to back across the hall, with a centre aisle from the door.
    for z in range(z0 + 4, z1 - 1, 3):
        for x in range(x0 + 2, x1 - 1):
            if x in (door_x, door_x + 1):
                continue
            b.decor(x, 1, z, "bookshelf", "north")
            b.decor(x, 2, z, "bookshelf", "north") if b.chance(0.75) else None
    b.decor(x0 + 1, 1, z0 + 1, "counter", "east")
    b.decor(x0 + 1, 1, z0 + 2, "counter", "east")
    b.container(x0 + 2, 1, z0 + 1, "filing_cabinet", "south", "library")
    b.decor(x1 - 1, 1, z0 + 1, "desk", "west")
    b.decor(x1 - 2, 1, z0 + 1, "chair", "east")
    b.container(x1 - 1, 1, z1 - 1, "filing_cabinet", "north", "office", lock={"type": "key", "key": "records_room"})
    b.container(x0 + 1, 1, z1 - 1, "supply_locker", "east", "library")
    for _ in range(4):
        x, z = int(b.r.integers(x0 + 1, x1)), int(b.r.integers(z0 + 1, z1))
        if (x, 1, z) in b.blocks and b.blocks[(x, 1, z)][0] == "minecraft:air":
            b.decal(x, 1, z, "scattered_papers")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.02, rubble=0.02, webs=0.08)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + height, "x", slab="cracked_asphalt_slab", gable="weathered_bricks")
    return b.seal(ground="minecraft:dirt_path")


def factory(seed: int, sx: int, sz: int) -> Building:
    """A small works: a tall metal hall, two dead machines under a crane beam, a brick stack through the roof."""
    height = 6
    b = Building(sx, 1 + height + 4, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, "corrugated_metal", floor="diamond_plate", height=height, corner="sooty_bricks")
    b.walls(x0, z0, x1, z1, 1, 2, "sooty_bricks")  # brick up to the sills
    b.air(x0 + 1, 1, z0 + 1, x1 - 1, 2, z1 - 1)
    b.door(sx // 2 - 1, z0, width=3, height=4)
    b.box(x0, 0, 0, x1, 0, 0, "cracked_concrete")
    b.windows(x0, z0, x1, z1, 4, spacing=2, sides=("east", "west", "south"), glass=0.4, boarded=0.1)
    for mx in (x0 + 2, x1 - 4):  # machines: a housing, a hopper on top, a control cabinet beside
        b.box(mx, 1, z0 + 5, mx + 2, 2, z0 + 7, "rusted_sheet_metal")
        b.put(mx + 1, 3, z0 + 6, "minecraft:iron_bars")
        b.decor(mx + 1, 1, z0 + 4, "transformer", "north")
    b.box(x0 + 1, height, z0 + 6, x1 - 1, height, z0 + 6, "rusted_sheet_metal")  # crane beam
    for x in (sx // 2 - 1, sx // 2 + 1):
        b.box(x, height - 2, z0 + 6, x, height - 1, z0 + 6, "minecraft:iron_chain", axis="y")
    b.box(x1 - 2, 1, z1 - 2, x1 - 1, height + 4, z1 - 1, "sooty_bricks")  # the stack
    b.container(x0 + 1, 1, z1 - 1, "toolbox", "north", "workshop")
    b.container(x0 + 2, 1, z1 - 1, "toolbox", "north", "workshop")
    b.container(x0 + 1, 1, z0 + 2, "supply_locker", "east", "utility")
    b.container(x0 + 4, 1, z1 - 1, "military_crate", "north", "store_storage")
    b.decor(x1 - 1, 1, z0 + 2, "workbench", "west")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.06, webs=0.05)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.06)
    b.box(x1 - 2, 1 + height, z1 - 2, x1 - 1, 1 + height, z1 - 1, "sooty_bricks")  # the roof does not cut the stack
    return b.seal(ground="cracked_concrete")


# ----------------------------------------------------------------------------- rural

def barn(seed: int, sx: int, sz: int) -> Building:
    height = 4
    b = Building(sx, 1 + height + Building.roof_height(sx), sz, seed)
    x0, z0, x1, z1 = 1, 1, sx - 2, sz - 2
    b.shell(x0, z0, x1, z1, "boarded_planks", floor="minecraft:coarse_dirt", height=height, corner="dead_log", footing="minecraft:cobblestone")
    b.door(sx // 2 - 1, z0, width=3, height=3)
    for z in range(z0 + 1, z1, 2):
        b.put(x0 + 1, 1, z, "minecraft:hay_block", axis=b.pick(["x", "y", "z"]))
        if b.chance(0.5):
            b.put(x0 + 1, 2, z, "minecraft:hay_block", axis="y")
    b.container(x1 - 1, 1, z1 - 1, "toolbox", "north", "farm")
    b.container(x1 - 1, 1, z1 - 2, "supply_locker", "west", "farm")
    b.windows(x0, z0, x1, z1, 3, spacing=3, sides=("east", "west"), glass=0.0, boarded=0.5)
    b.clutter(x0 + 2, z0 + 1, x1 - 1, z1 - 1, blood=0.03, rubble=0.02, webs=0.08)
    b.gable_roof(0, 0, sx - 1, sz - 1, 1 + height, "z", slab="asphalt_slab", gable="boarded_planks")
    return b.seal(ground="minecraft:coarse_dirt")


def farmhouse(seed: int, sx: int, sz: int, wall: str) -> Building:
    """A cottage with a porch on dead-log posts and a vegetable plot gone to seed behind it."""
    height = 3
    b = Building(sx, 1 + height + Building.roof_height(sz), sz, seed)
    porch = 2
    garden = 3
    x0, z0, x1, z1 = 1, porch, sx - 2, sz - 1 - garden
    b.shell(x0, z0, x1, z1, wall)
    b.box(x0, 0, 0, x1, 0, porch - 1, "boarded_planks")
    for x in (x0, x1):
        b.put(x, 1, 0, "dead_log", axis="y")
        b.put(x, 2, 0, "dead_log", axis="y")
        b.put(x, 3, 0, "dead_log", axis="y")
    door_x = x0 + 2
    b.door(door_x, z0, frame="boarded_planks")
    px = x0 + 4
    b.partition_x(px, z0 + 1, z1 - 1, 1, height, "plaster_wall", door_z=z0 + 1)
    b.windows(x0, z0, x1, z1, 2, skip=[(door_x, z0), (px, z0), (px, z1)])
    kitchen(b, x0 + 1, px - 1, z1 - 1, pantry=False)
    b.container(x0 + 1, 1, z0 + 1, "kitchen_cupboard", "east", "house_kitchen")
    bedroom(b, px + 1, x1 - 1, z0 + 1, z1 - 1)
    b.decor(x1 - 1, 1, 1, "chair", "south")
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, webs=0.06)
    # Porch roof and house roof (ridge along X); the porch is covered by a flat corrugated lean-to.
    b.flat_roof(x0, 0, x1, porch - 1, 4, "corrugated_metal")
    b.gable_roof(0, porch - 1, sx - 1, z1 + 1, 1 + height, "x", gable=wall)
    # Garden behind the house.
    gz0 = z1 + 2
    for x in range(1, sx - 1):
        for z in range(gz0, sz):
            b.put(x, 0, z, "minecraft:farmland", moisture=0)
            if b.chance(0.6):
                b.put(x, 1, z, "minecraft:wheat", age=int(b.r.integers(3, 8)))
    return b.seal()


def shed(seed: int, sx: int, sz: int) -> Building:
    height = 3
    b = Building(sx, 1 + height + 1, sz, seed)
    x0, z0, x1, z1 = 0, 1, sx - 1, sz - 1
    b.shell(x0, z0, x1, z1, b.pick(["corrugated_metal", "boarded_planks", "rusted_sheet_metal"]), floor="boarded_planks")
    b.door(sx // 2, z0)
    b.container(x0 + 1, 1, z1 - 1, "toolbox", "north", "workshop")
    b.container(x1 - 1, 1, z1 - 1, "supply_locker", "north", "farm") if x1 - 1 > x0 + 1 else None
    b.decor(x0 + 1, 1, z0 + 1, "table", "east") if z1 - 1 > z0 + 1 else None
    b.clutter(x0 + 1, z0 + 1, x1 - 1, z1 - 1, blood=0.05, webs=0.1)
    b.flat_roof(x0, z0, x1, z1, 1 + height, "corrugated_metal", holes=0.08)
    return b.seal()


# ----------------------------------------------------------------------------- catalogue

def catalogue() -> dict[str, list[tuple[str, Building]]]:
    """category -> [(template name, building)]. Seeds are fixed, so output is reproducible."""
    c = {cat: [] for cat in CATEGORIES}
    c["residential"] += [
        ("cottage_siding", cottage(1001, 11, 10, "weathered_siding", "x")),
        ("cottage_brick", cottage(1002, 13, 11, "weathered_bricks", "z")),
        ("cottage_plaster", cottage(1003, 11, 11, "peeling_plaster", "x")),
        ("suburban_siding", suburban_house(1011, 14, 12, "weathered_siding", "x")),
        ("suburban_plaster", suburban_house(1012, 13, 13, "plaster_wall", "z")),
        ("suburban_brick", suburban_house(1013, 14, 13, "weathered_bricks", "x")),
        ("two_story_siding", two_story_house(1021, 12, 12, "weathered_siding")),
        ("two_story_brick", two_story_house(1022, 11, 13, "sooty_bricks")),
        ("trailer_rusted", trailer(1031, 13, "rusted_sheet_metal")),
        ("trailer_corrugated", trailer(1032, 12, "corrugated_metal")),
    ]
    c["commercial"] += [
        ("corner_store", store(2001, 13, 12, "weathered_bricks", "store")),
        ("grocery", store(2002, 14, 13, "plaster_wall", "store")),
        ("pharmacy", store(2003, 12, 12, "weathered_bricks", "pharmacy")),
        ("hardware_store", store(2004, 13, 13, "corrugated_metal", "hardware")),
        ("diner", diner(2011, 14, 11)),
    ]
    c["civic"] += [
        ("police_station", police_station(3001, 14, 13)),
        ("town_hall", town_hall(3002, 14, 12)),
        ("church", church(3003, 11, 14)),
    ]
    c["medical"] += [
        ("clinic", clinic(4001, 13, 12)),
        ("clinic_small", clinic(4002, 11, 11)),
    ]
    c["industrial"] += [
        ("auto_garage", garage(5001, 13, 12)),
        ("auto_garage_large", garage(5002, 14, 14)),
        ("warehouse", warehouse(5011, 14, 14)),
    ]
    c["rural"] += [
        ("barn", barn(6001, 12, 14)),
        ("farmhouse_siding", farmhouse(6011, 12, 14, "weathered_siding")),
        ("farmhouse_plaster", farmhouse(6012, 11, 13, "peeling_plaster")),
        ("shed_small", shed(6021, 6, 6)),
        ("shed_large", shed(6022, 8, 7)),
    ]
    # Seeded variants of the archetypes above (Milestone 3, second half): same plans, other
    # materials, sizes and layouts of furniture, damage and loot.
    c["residential"] += [
        ("cottage_siding_b", cottage(1004, 12, 10, "weathered_siding", "z")),
        ("cottage_brick_b", cottage(1005, 11, 12, "sooty_bricks", "x")),
        ("cottage_plaster_b", cottage(1006, 12, 11, "plaster_wall", "z")),
        ("suburban_siding_b", suburban_house(1014, 13, 12, "weathered_siding", "z")),
        ("suburban_plaster_b", suburban_house(1015, 14, 13, "peeling_plaster", "x")),
        ("suburban_brick_b", suburban_house(1016, 13, 13, "sooty_bricks", "z")),
        ("two_story_plaster", two_story_house(1023, 12, 13, "plaster_wall")),
        ("two_story_siding_b", two_story_house(1024, 13, 12, "weathered_siding")),
        ("trailer_rusted_b", trailer(1033, 14, "rusted_sheet_metal")),
        ("trailer_corrugated_b", trailer(1034, 11, "corrugated_metal")),
    ]
    c["commercial"] += [
        ("corner_store_b", store(2005, 12, 12, "plaster_wall", "store")),
        ("grocery_b", store(2008, 14, 12, "weathered_bricks", "store")),
        ("pharmacy_b", store(2006, 13, 12, "plaster_wall", "pharmacy")),
        ("hardware_store_b", store(2007, 14, 12, "weathered_bricks", "hardware")),
        ("diner_b", diner(2012, 13, 11)),
    ]
    c["civic"] += [
        ("police_station_b", police_station(3004, 13, 13)),
        ("town_hall_b", town_hall(3005, 13, 12)),
        ("church_b", church(3006, 12, 14)),
    ]
    c["medical"] += [("clinic_b", clinic(4003, 12, 13))]
    # Past 120 structures: the library and the works.
    c["civic"] += [("library", library(3011, 14, 13))]
    c["industrial"] += [("factory", factory(5021, 14, 14))]
    c["industrial"] += [
        ("auto_garage_b", garage(5003, 12, 12)),
        ("warehouse_b", warehouse(5012, 13, 14)),
    ]
    c["rural"] += [
        ("barn_b", barn(6002, 13, 14)),
        ("farmhouse_brick", farmhouse(6013, 12, 13, "weathered_bricks")),
        ("shed_small_b", shed(6023, 7, 6)),
        ("shed_large_b", shed(6024, 8, 8)),
    ]
    from gen_city import catalogue_additions  # late import: gen_city builds on this module
    for category, buildings in catalogue_additions().items():
        c[category] += buildings
    return c


# ----------------------------------------------------------------------------- loot

def item(name, weight, count=None, functions=None):
    e = {"type": "minecraft:item", "name": name if ":" in name else f"minecraft:{name}", "weight": weight}
    fns = list(functions or [])
    if count:
        fns.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    if fns:
        e["functions"] = fns
    return e


def key(key_id, weight):
    return item(f"{NS}:key", weight, functions=[{"function": "minecraft:set_components", "components": {f"{NS}:key_id": key_id}}])


def worn(lo=0.2, hi=0.8):
    return [{"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": lo, "max": hi}}]


def potion(p):
    return [{"function": "minecraft:set_potion", "id": f"minecraft:{p}"}]


def loot_table(name, rolls, entries) -> None:
    D.data_json(f"loot_table/containers/{name}.json", {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0.0, "entries": entries}],
        "random_sequence": f"{NS}:containers/{name}",
    })


def loot() -> None:
    loot_table("library", (2, 4), [
        item("book", 14, (1, 3)), item("paper", 12, (2, 6)), item("writable_book", 5), item("map", 4), item("ink_sac", 3),
        item("feather", 3), item("compass", 1), item("clock", 1), item("cookie", 3, (1, 2)), key("records_room", 1)])
    loot_table("house_kitchen", (2, 4), [
        item("bread", 10, (1, 2)), item("apple", 8, (1, 3)), item("potato", 8, (1, 4)), item("carrot", 8, (1, 3)),
        item("baked_potato", 4, (1, 2)), item("cookie", 5, (1, 4)), item("sugar", 6, (1, 3)), item("bowl", 6, (1, 2)),
        item("mushroom_stew", 2), item("honey_bottle", 2), item("glass_bottle", 5, (1, 2)), item("dried_kelp", 4, (2, 6))])
    loot_table("house_bathroom", (1, 3), [
        item("paper", 10, (1, 3)), item("glass_bottle", 8, (1, 2)), item("honey_bottle", 4), item("sugar", 4, (1, 2)),
        item("potion", 3, functions=potion("healing")), item("potion", 1, functions=potion("regeneration")), item("string", 5, (1, 2))])
    loot_table("house_bedroom", (2, 4), [
        item("leather_helmet", 3, functions=worn()), item("leather_chestplate", 3, functions=worn()),
        item("leather_leggings", 3, functions=worn()), item("leather_boots", 3, functions=worn()),
        item("white_wool", 6, (1, 2)), item("gray_wool", 4, (1, 2)), item("string", 6, (1, 3)), item("book", 4),
        item("clock", 1), item("gold_nugget", 4, (1, 4)), item("emerald", 1), item("lead", 2), item("name_tag", 1)])
    loot_table("house_drawers", (2, 4), [
        item("paper", 14, (1, 5)), item("book", 6), item("writable_book", 3), item("map", 3), item("ink_sac", 4),
        item("string", 6, (1, 3)), item("compass", 2), item("clock", 1), item("candle", 4, (1, 2)),
        key("records_room", 1), key("pharmacy", 1), key("maintenance", 1)])
    loot_table("store_storage", (3, 5), [
        item("bread", 8, (1, 3)), item("cookie", 6, (2, 6)), item("sugar", 6, (1, 4)), item("apple", 6, (1, 3)),
        item("dried_kelp", 6, (3, 8)), item("torch", 8, (2, 6)), item("string", 5, (1, 4)), item("paper", 5, (2, 6)),
        item("glass_bottle", 5, (1, 3)), item("leather", 4, (1, 2)), item("honey_bottle", 2), item("pumpkin_pie", 2)])
    loot_table("pharmacy", (2, 5), [
        item("potion", 6, functions=potion("healing")), item("potion", 3, functions=potion("regeneration")),
        item("potion", 2, functions=potion("strong_healing")), item("honey_bottle", 6), item("glass_bottle", 8, (1, 3)),
        item("sugar", 6, (1, 3)), item("paper", 6, (1, 4)), item("golden_carrot", 2), item("glistering_melon_slice", 1)])
    loot_table("clinic", (2, 4), [
        item("paper", 10, (1, 4)), item("glass_bottle", 8, (1, 3)), item("honey_bottle", 4),
        item("potion", 4, functions=potion("healing")), item("potion", 1, functions=potion("regeneration")),
        item("string", 4, (1, 3)), item("shears", 2, functions=worn(0.1, 0.5)), item(f"{NS}:keycard_green", 1)])
    loot_table("police_locker", (2, 4), [
        item("arrow", 12, (4, 12)), item("bow", 4, functions=worn(0.2, 0.7)), item("crossbow", 2, functions=worn(0.3, 0.7)),
        item("chainmail_helmet", 3, functions=worn()), item("chainmail_chestplate", 2, functions=worn()),
        item("iron_helmet", 2, functions=worn()), item("shield", 3, functions=worn(0.2, 0.6)),
        item("iron_sword", 2, functions=worn(0.3, 0.8)), item("leather", 5, (1, 3)), item("compass", 2),
        item(f"{NS}:keycard_green", 2)])
    loot_table("office", (2, 4), [
        item("paper", 16, (2, 6)), item("book", 6), item("writable_book", 4), item("map", 5), item("compass", 2),
        item("ink_sac", 4), item("name_tag", 2), item("clock", 1), key("records_room", 1), key("maintenance", 1),
        item(f"{NS}:keycard_green", 1)])
    loot_table("workshop", (2, 4), [
        item("iron_nugget", 12, (2, 8)), item("iron_ingot", 5, (1, 2)), item("copper_ingot", 6, (1, 3)),
        item("redstone", 5, (1, 4)), item("shears", 3, functions=worn(0.1, 0.6)), item("flint_and_steel", 3, functions=worn(0.2, 0.7)),
        item("iron_chain", 4, (1, 3)), item("bucket", 2), item("lead", 2), item("string", 5, (1, 3)),
        item("iron_shovel", 2, functions=worn(0.2, 0.6)), item("iron_axe", 1, functions=worn(0.3, 0.7)), item(f"{NS}:fuse", 2)])
    loot_table("farm", (2, 4), [
        item("wheat_seeds", 10, (2, 8)), item("beetroot_seeds", 6, (2, 6)), item("pumpkin_seeds", 4, (1, 4)),
        item("melon_seeds", 3, (1, 4)), item("wheat", 8, (2, 6)), item("bone_meal", 6, (2, 6)),
        item("iron_hoe", 2, functions=worn(0.2, 0.7)), item("shears", 2, functions=worn(0.2, 0.6)),
        item("lead", 3), item("leather", 4, (1, 3)), item("string", 4, (1, 3))])
    loot_table("fire_station", (2, 4), [
        item("leather_helmet", 4, functions=worn()), item("leather_chestplate", 4, functions=worn()), item("leather_boots", 4, functions=worn()),
        item("iron_axe", 3, functions=worn(0.2, 0.7)), item("bucket", 5), item("water_bucket", 2), item("torch", 6, (2, 6)),
        item("lead", 3), item("string", 4, (1, 3)), item("potion", 2, functions=potion("fire_resistance")), item(f"{NS}:fuse", 2)])
    loot_table("church", (1, 3), [
        item("candle", 10, (1, 4)), item("paper", 8, (1, 4)), item("book", 6), item("gold_nugget", 5, (1, 5)),
        item("emerald", 1), item("golden_apple", 1), item("bread", 4, (1, 2))])


# ----------------------------------------------------------------------------- vanilla blocks towns use

def iron_bars(seed: int) -> None:
    """Rust-streaked cell bars (alpha cut-out), replacing the vanilla texture."""
    yy, xx = np.mgrid[0:M.T, 0:M.T]
    bars = ((xx % 8) >= 2) & ((xx % 8) <= 5)
    bands = (yy <= 2) | (yy >= 29)
    solid = bars | (bands & ((xx % 8) != 0))
    rgb, _ = M.metal_base(seed, light="#6d7173", dark="#3c4042")
    rc, rmask = M.rust_layer(seed + 1, 0.45)
    rgb = N.lerp(rgb, rc, rmask)
    shade = np.where((xx % 8) == 2, 1.25, np.where((xx % 8) == 5, 0.7, 1.0))
    rgb = rgb * shade[..., None]
    save_vanilla("iron_bars", np.clip(rgb, 0, 255), solid.astype(float))


def cobweb(seed: int) -> None:
    """Dusty grey strands: radial threads and a sagging spiral."""
    def draw(d, s):
        c = (15.5 * s, 15.5 * s)
        r = N.rng(seed)
        for a in np.linspace(0, 2 * np.pi, 9, endpoint=False) + r.uniform(-0.15, 0.15, 9):
            d.line([c, (c[0] + np.cos(a) * 22 * s, c[1] + np.sin(a) * 22 * s)], fill=255, width=max(1, s // 2))
        for rad in (4, 7.5, 11, 14.5):
            pts = []
            for a in np.linspace(0, 2 * np.pi, 40):
                sag = 1.0 + 0.08 * np.sin(a * 9)
                pts.append((c[0] + np.cos(a) * rad * sag * s, c[1] + np.sin(a) * rad * sag * s))
            d.line(pts, fill=255, width=max(1, s // 2))
    alpha = N.supersample_mask(draw, M.T, M.T, 4)
    alpha = np.clip(alpha * 1.4, 0, 1) * (0.55 + 0.45 * N.spectral(M.T, M.T, beta=2.0, seed=seed + 1))
    rgb = N.solid(M.T, M.T, "#cfccc4") * (0.85 + 0.15 * N.spectral(M.T, M.T, beta=1.5, seed=seed + 2))[..., None]
    save_vanilla("cobweb", rgb, (alpha > 0.12).astype(float))


# ----------------------------------------------------------------------------- text

# Town identity text (com.deadlands.world.town.TownIdentity and the town card). History lines take
# the town's name as %s; the survivor and bandit faction lines take a group name.
TOWN_TEXT = {
    "town.deadlands.size.settlement": "Settlement",
    "town.deadlands.size.small_town": "Small town",
    "town.deadlands.size.large_town": "Large town",
    "town.deadlands.condition.evacuated": "Evacuated",
    "town.deadlands.condition.overrun": "Overrun",
    "town.deadlands.condition.military": "Military checkpoint",
    "town.deadlands.condition.survivor": "Survivor holdout",
    "town.deadlands.condition.bandit": "Bandit territory",
    "town.deadlands.condition.abandoned": "Long abandoned",
    "town.deadlands.faction.evacuated": "No one. The evacuation buses never came back",
    "town.deadlands.faction.overrun": "The dead",
    "town.deadlands.faction.military": "Quarantine Command, before it pulled out",
    "town.deadlands.faction.survivor": "%s",
    "town.deadlands.faction.bandit": "%s",
    "town.deadlands.faction.abandoned": "No one, for years",
    "town.deadlands.history.evacuated.0": "%s was emptied in a single night when the evacuation order came.",
    "town.deadlands.history.evacuated.1": "The buses left %s on the third day. Nobody came back for the rest.",
    "town.deadlands.history.evacuated.2": "%s did as it was told and left. Its doors are still open.",
    "town.deadlands.history.overrun.0": "%s held out for nine days before the barricades broke.",
    "town.deadlands.history.overrun.1": "The infection reached %s before the news did.",
    "town.deadlands.history.overrun.2": "Most of %s never left. They are still here.",
    "town.deadlands.history.military.0": "The army turned %s into a quarantine checkpoint, then pulled out without a word.",
    "town.deadlands.history.military.1": "%s was a staging post for the quarantine. The soldiers left in a hurry.",
    "town.deadlands.history.military.2": "Checkpoints still guard the roads into %s. The soldiers do not.",
    "town.deadlands.history.survivor.0": "Survivors fortified %s and held it through a winter.",
    "town.deadlands.history.survivor.1": "A group of survivors made %s their home, for a while.",
    "town.deadlands.history.survivor.2": "%s was a refuge once. Its walls are still standing.",
    "town.deadlands.history.bandit.0": "Raiders took %s and taxed everyone who passed through.",
    "town.deadlands.history.bandit.1": "%s fell to bandits soon after the collapse.",
    "town.deadlands.history.bandit.2": "Whoever held %s last built walls to keep the living out, not the dead.",
    "town.deadlands.history.abandoned.0": "%s was dying long before the outbreak.",
    "town.deadlands.history.abandoned.1": "Weeds took the streets of %s long ago.",
    "town.deadlands.history.abandoned.2": "Nobody remembers when the last family left %s.",
    "town.deadlands.loot.untouched": "barely touched",
    "town.deadlands.loot.plenty": "some left behind",
    "town.deadlands.loot.picked_over": "picked over",
    "town.deadlands.loot.stripped": "stripped by survivors",
    "town.deadlands.story.0": "A radio broadcast once promised help was coming here. It never came.",
    "town.deadlands.story.1": "Some of the houses are still locked from the inside.",
    "town.deadlands.story.2": "The last page of the town register is a list of names, all crossed out.",
    "town.deadlands.story.3": "Nobody here had time to pack.",
    "town.deadlands.story.4": "The phone lines were dead before anyone could call for help.",
    "town.deadlands.story.5": "Children's drawings still hang in a few of the windows.",
    "town.deadlands.story.6": "On quiet nights the wind carries something that sounds like voices.",
    "town.deadlands.story.7": "Whatever happened here happened fast.",
    "town.deadlands.landmark.police_station": "the police station",
    "town.deadlands.landmark.library": "the library",
    "town.deadlands.landmark.factory": "the old works",
    "town.deadlands.landmark.town_hall": "the town hall",
    "town.deadlands.landmark.church": "the old church",
    "town.deadlands.landmark.clinic": "the clinic",
    "town.deadlands.landmark.pharmacy": "the pharmacy",
    "town.deadlands.landmark.diner": "the diner",
    "town.deadlands.landmark.grocery": "the grocery store",
    "town.deadlands.landmark.corner_store": "the corner store",
    "town.deadlands.landmark.hardware_store": "the hardware store",
    "town.deadlands.landmark.warehouse": "the warehouse",
    "town.deadlands.landmark.auto_garage": "the auto garage",
    "town.deadlands.landmark.barn": "the old barn",
    "hud.deadlands.town.discovered": "DISCOVERED",
    "hud.deadlands.town.entering": "ENTERING",
    "hud.deadlands.town.summary": "%s  ·  %s  ·  %s lived here",
    "hud.deadlands.town.held_by": "Last held by: %s",
    "hud.deadlands.town.landmark": "Landmark: %s",
    "hud.deadlands.town.supplies": "Supplies: %s",
    "chronicle.deadlands.town_found": "%s found %s.",
    "subtitles.deadlands.town_enter": "Wind through an empty town",
}

# ----------------------------------------------------------------------------- worldgen data

def worldgen(templates: dict[str, list[str]]) -> None:
    D.data_json("worldgen/structure/town.json", {
        "type": f"{NS}:town",
        "biomes": f"#{NS}:has_structure/town",
        "step": "surface_structures",
        "terrain_adaptation": "none",
        "spawn_overrides": {},
        "buildings": templates,
    })
    D.data_json("worldgen/structure_set/towns.json", {
        "structures": [{"structure": f"{NS}:town", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 34, "separation": 14, "salt": 461873205},
    })
    D.data_json("worldgen/configured_feature/road_network.json", {"type": f"{NS}:road_network", "config": {}})
    D.data_json("worldgen/placed_feature/road_network.json", {"feature": f"{NS}:road_network", "placement": []})
    # Towns may also stand in the vanilla land biomes, for worlds that turn the DEADLANDS biomes off.
    D.data_json("tags/worldgen/biome/has_structure/town.json", {"replace": False, "values": [
        "#deadlands:is_deadlands", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
        "minecraft:birch_forest", "minecraft:flower_forest", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:savanna",
        "minecraft:desert"]})


# ----------------------------------------------------------------------------- light fixtures (Phase 3)
# Shops, offices, civic buildings, clinics and works get a ceiling grid of fluorescent lights (about a
# third of them faulty, so they flicker) and battery emergency lights on their walls; churches,
# apartment blocks and motels only the emergency lights. Homes, barns, sheds and the western town
# have no electric lighting: flashlight territory. Every fixture goes into an empty ceiling or wall
# spot, after the building is finished, so nothing already built moves. Fluorescents are placed dark:
# in a town they light only once its power is restored (FluorescentLightBlock), while the emergency
# lights pulse until then (EmergencyLightBlock), so a dead town stays dark and a restored one glows.
FLUORESCENT_PLANS = ("corner_store", "grocery", "pharmacy", "hardware_store", "diner", "police_station", "town_hall", "library",
                     "clinic", "auto_garage", "warehouse", "factory", "office_block", "department_store", "bank", "courthouse",
                     "fire_station", "emergency_room")
EMERGENCY_ONLY_PLANS = ("church", "apartment_block", "motel")
NOT_A_WALL = ("table", "chair", "counter", "shelf", "bed", "couch", "desk", "stove", "sink", "toilet", "bookshelf", "whiteboard",
              "crate", "cabinet", "locker", "container", "glass", "bars", "door", "rubble", "blood", "stain", "marks", "pane",
              "fence", "slab", "stairs", "cobweb", "sign", "barrel", "chest", "light", "ladder", "trapdoor", "carpet", "bench")


def light_fixtures(name: str, b: Building) -> tuple[int, int]:
    """Adds fixtures to a finished building; returns (fluorescents, emergency lights)."""
    fluorescent = name.startswith(FLUORESCENT_PLANS)
    if not fluorescent and not name.startswith(EMERGENCY_ONLY_PLANS):
        return 0, 0
    blocks = b.blocks

    def kind(p):
        entry = blocks.get(p)
        return entry[0] if entry else None

    def air(p):
        return kind(p) == "minecraft:air"

    def ceiling(p):
        k = kind(p)
        return k is not None and k not in ("minecraft:air", "minecraft:cobweb")

    def wall(p):
        k = kind(p)
        return k is not None and k != "minecraft:air" and not any(word in k for word in NOT_A_WALL)

    # Spots just under a ceiling with at least two clear blocks below (never above tall furniture).
    spots = sorted(p for p in blocks if air(p) and p[1] >= 3 and ceiling((p[0], p[1] + 1, p[2]))
                   and air((p[0], p[1] - 1, p[2])) and air((p[0], p[1] - 2, p[2])))
    sx, _, sz = b.size
    placed_f = 0
    if fluorescent and spots:
        # Rows on a 4-block grid centred in each floor's ceiling, as real fixtures are hung.
        limit = max(1, min(10, sx * sz // 30))
        grid = []
        spot_set = set(spots)
        for y in sorted({p[1] for p in spots}):
            xs = [p[0] for p in spots if p[1] == y]
            zs = [p[2] for p in spots if p[1] == y]
            x0 = min(xs) + 1 + ((max(xs) - min(xs) - 2) % 4) // 2
            z0 = min(zs) + 1 + ((max(zs) - min(zs) - 2) % 4) // 2
            grid += [(x, y, z) for z in range(z0, max(zs), 4) for x in range(x0, max(xs), 4) if (x, y, z) in spot_set]
        for x, y, z in grid:
            if placed_f >= limit:
                break
            if not all(air((x + dx, y, z + dz)) for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                continue  # open ceiling only, not against a wall or in a doorway
            run_x = sum(1 for d in range(1, 7) if air((x + d, y, z))) + sum(1 for d in range(1, 7) if air((x - d, y, z)))
            run_z = sum(1 for d in range(1, 7) if air((x, y, z + d))) + sum(1 for d in range(1, 7) if air((x, y, z - d)))
            b.put(x, y, z, "fluorescent_light", axis="x" if run_x >= run_z else "z", faulty=b.chance(0.35), lit=False)
            placed_f += 1
    # Emergency lights: on an inner wall, just under the ceiling, one per floor (two on a big floor).
    placed_e = 0
    sides = ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south"))  # wall offset -> facing away from it
    floors: dict[int, list] = {}
    for x, y, z in spots:
        for dx, dz, facing in sides:
            if wall((x + dx, y, z + dz)) and air((x - dx, y, z - dz)) and kind((x + dx, y + 1, z + dz)) is not None:
                floors.setdefault(y, []).append((x, y, z, facing))
                break
    for y in sorted(floors):
        options = floors[y]
        want = 2 if len(options) > 40 and sx * sz > 150 else 1
        chosen: list[tuple[int, int, int]] = []
        for i in b.r.permutation(len(options)):
            x, y2, z, facing = options[int(i)]
            if len(chosen) >= want:
                break
            if not air((x, y2, z)) or any(abs(c[0] - x) < 6 and abs(c[2] - z) < 6 for c in chosen):
                continue
            b.put(x, y2, z, "emergency_light", facing=facing, lit=True, pulse=True)
            chosen.append((x, y2, z))
            placed_e += 1
    return placed_f, placed_e


# ----------------------------------------------------------------------------- interior finishes (Phase 3)
# Homes, farmhouses, apartments, motels and offices get finished interiors (gen_interiors.py): each
# building picks a wall finish for its interior walls (faded or striped wallpaper, wood panelling,
# or bare), kitchens (floor within reach of a stove, sink or counter) get linoleum, and the other
# rooms carpet or the original boards, so no two houses look alike inside. Only interior wall faces
# and floors under a roof change; outer walls, furniture and loot stay as they were.
FINISH_PLANS = ("cottage", "suburban", "two_story", "trailer", "farmhouse", "apartment_block", "motel", "office_block", "wooden_house")
WALL_FINISHES = ("faded_wallpaper", "striped_wallpaper", "wood_paneling", None)
FLOOR_FINISHES = ("stained_carpet", "stained_carpet", None)
KITCHEN = ("stove", "sink", "counter")


def interior_finish(name: str, b: Building) -> tuple[int, int]:
    """Re-finishes interior walls and floors; returns (wall blocks, floor blocks) changed."""
    if not name.startswith(FINISH_PLANS):
        return 0, 0
    blocks = b.blocks
    wall_finish = b.pick(list(WALL_FINISHES))
    floor_finish = "linoleum_tiles" if name.startswith("office_block") else b.pick(list(FLOOR_FINISHES))

    def kind(p):
        entry = blocks.get(p)
        return entry[0] if entry else None

    cache: dict = {}

    def indoor(p):
        """Air inside the building: a roof within six blocks above and walls on all four sides (air under
        the eaves outside has a roof but nothing beyond it)."""
        if p in cache:
            return cache[p]
        result = kind(p) == "minecraft:air" and any(kind((p[0], p[1] + k, p[2])) not in (None, "minecraft:air") for k in range(1, 7))
        if result:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                k = 1
                while kind((p[0] + dx * k, p[1], p[2] + dz * k)) == "minecraft:air":
                    k += 1
                if kind((p[0] + dx * k, p[1], p[2] + dz * k)) is None:
                    result = False
                    break
        cache[p] = result
        return result

    def plain(p):
        k = kind(p)
        return k is not None and k != "minecraft:air" and ":" not in k.replace(f"{NS}:", "") and not any(w in k for w in NOT_A_WALL) \
            and not blocks[p][1] and blocks[p][2] is None

    kitchens = [p for p, v in blocks.items() if any(w in v[0] for w in KITCHEN)]
    walls = floors = 0
    for p in sorted(blocks):
        x, y, z = p
        if wall_finish and plain(p) and y >= 1:
            # An interior wall: indoor air on both sides along one axis.
            if (indoor((x - 1, y, z)) and indoor((x + 1, y, z))) or (indoor((x, y, z - 1)) and indoor((x, y, z + 1))):
                b.put(x, y, z, wall_finish)
                walls += 1
                continue
        # A floor: a room above it and no room below (an upper storey's floor is the ceiling of the room under it).
        if plain(p) and indoor((x, y + 1, z)) and not indoor((x, y - 1, z)) and kind(p).endswith(("boarded_planks", "concrete")):
            near_kitchen = any(abs(k[0] - x) <= 2 and abs(k[2] - z) <= 2 and 0 <= k[1] - y <= 2 for k in kitchens)
            finish = "linoleum_tiles" if near_kitchen else floor_finish
            if finish:
                b.put(x, y, z, finish)
                floors += 1
    return walls, floors


def build() -> None:
    if STRUCTURES.exists():
        for old in STRUCTURES.glob("*.nbt"):
            old.unlink()  # templates can be renamed between versions
    templates: dict[str, list[str]] = {}
    count = 0
    for category, buildings in catalogue().items():
        templates[category] = []
        for name, building in buildings:
            sx, sy, sz = building.size
            assert sx <= MAX_FRONT and sz <= MAX_DEPTH, (name, building.size)
            interior_finish(name, building)
            light_fixtures(name, building)
            building.write(STRUCTURES / f"{name}.nbt")
            templates[category].append(f"{NS}:town/{name}")
            count += 1
    worldgen(templates)
    loot()
    D.merge_lang(TOWN_TEXT)
    iron_bars(8101)
    cobweb(8102)
    return count


if __name__ == "__main__":
    n = build()
    print(f"{n} town building templates written")
