"""Mythos ("Myth"), built in Blender from the owner's Mythos.glb: the same hooded figure in the same clothes, made
bigger (about 4.2 blocks tall) and rigged for the game.

Run inside Blender on an empty file. It imports ~/Downloads/Mythos/Mythos.glb (into "Mythos_Source", hidden) and
makes the "Mythos" collection with "Mythos_Rig" and "Mythos_Body":

  * every part of the original kept, with its own textures: the hood, the head, the body, the bandages on the legs, arms
    and torso, the tattered cloak, the waist cape, the right shoulder piece, the glowing torso band, and the Haxxed
    blade (moved into his right hand, so his slashes use it, and scaled to about two blocks)
  * scaled up evenly to 4.2 blocks tall, standing on z = 0, facing -Y
  * the arms raised straight out to the sides (the rest pose mythos_anims.py expects) and split at elbow and wrist; the
    legs split at knee and ankle; the body into gut and chest; the hood and head together; the waist cape on the hips;
    the cloak with the chest (where it hangs off the left arm, with that arm)
  * the bones keep the names the game uses; jaw, claws and tail have nothing on them (he has none)
  * the pink corruption and the torso band glow in the dark (a glow map beside each texture)

Then dl_mesh_export.export("Mythos", "MythosGeometry", "mythos.bin", "mythos").
"""
import math
import os

import bpy
import numpy as np
from mathutils import Matrix, Vector

SOURCE = os.path.join(os.path.expanduser("~"), "Downloads", "Mythos", "Mythos.glb")
ROOT = os.environ.get("DEADLANDS_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
TEXDIR = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands", "textures", "entity", "mythos")
HEIGHT = 4.2
GROUND = -0.23
TOP = 5.61
SCALE = HEIGHT / (TOP - GROUND)

# Pivots in the source's units (before scaling), arms already in their raised rest pose.
SHOULDER = 3.35
RIG = [
    ("pelvis", "root", (0.0, 0.0, 1.85)),
    ("spine", "pelvis", (0.0, 0.0, 2.05)),
    ("chest", "spine", (0.0, 0.0, 2.75)),
    ("neck", "chest", (0.0, 0.0, 3.8)),
    ("head", "neck", (0.0, 0.0, 3.9)),
    ("jaw", "head", (0.0, -0.4, 4.1)),
    ("arm_r", "chest", (-1.5, 0.0, SHOULDER)),
    ("forearm_r", "arm_r", (-2.05, 0.0, SHOULDER)),
    ("hand_r", "forearm_r", (-2.7, 0.0, SHOULDER)),
    ("claws_r", "hand_r", (-3.0, 0.0, SHOULDER)),
    ("arm_l", "chest", (1.5, 0.0, SHOULDER)),
    ("forearm_l", "arm_l", (2.05, 0.0, SHOULDER)),
    ("hand_l", "forearm_l", (2.7, 0.0, SHOULDER)),
    ("claws_l", "hand_l", (3.0, 0.0, SHOULDER)),
    ("leg_r", "pelvis", (-0.5, 0.0, 1.85)),
    ("shin_r", "leg_r", (-0.5, 0.0, 0.95)),
    ("foot_r", "shin_r", (-0.5, 0.0, 0.25)),
    ("leg_l", "pelvis", (0.5, 0.0, 1.85)),
    ("shin_l", "leg_l", (0.5, 0.0, 0.95)),
    ("foot_l", "shin_l", (0.5, 0.0, 0.25)),
    ("tail1", "pelvis", (0.0, 0.6, 1.9)),
    ("tail2", "tail1", (0.0, 1.0, 1.7)),
    ("tail3", "tail2", (0.0, 1.4, 1.5)),
    ("tail4", "tail3", (0.0, 1.8, 1.3)),
]
PIVOT = {b: p for b, _, p in RIG}


def part(name):
    """Which body part an imported object is."""
    n = name.split("_")[0]
    return {
        "Rig1": "leg_r", "Rig2": "leg_l", "Bandagerl1": "leg_r", "Bandagell1": "leg_l",
        "Rig3": "arm_r", "Bandagers1": "arm_r", "Shoulderr1": "arm_r",
        "Rig4": "arm_l", "Bandagels1": "arm_l", "Bandagels21": "arm_l",
        "Rig5": "torso", "Bandaget1": "torso", "Glowytorso1": "torso",
        "Rig6": "head", "Hood1": "head",
        "WaistCape1": "pelvis", "TatteredCloak1": "cloak", "Haxxedblader21": "blade",
    }.get(n, "torso")


def raise_arm(side):
    """The hanging arm swung straight out to the side, about its shoulder."""
    sx = -1.0 if side == "r" else 1.0
    pivot = Vector((1.5 * sx, 0.0, SHOULDER))
    rot = Matrix.Rotation(math.radians(90.0 * -sx), 4, "Y")
    return Matrix.Translation(pivot) @ rot @ Matrix.Translation(-pivot)


ARM = {"r": raise_arm("r"), "l": raise_arm("l")}


def blade_into_hand():
    """The blade, its grip end (+Y, where the guard is) moved into the right hand, pointing forward, at 65% size."""
    grip = Vector((3.58, 1.6, 2.99))
    hand = Vector((-2.85, 0.0, SHOULDER))
    return Matrix.Translation(hand) @ Matrix.Scale(0.65, 4) @ Matrix.Translation(-grip)


def bone_for(kind, c):
    """The bone a triangle (centroid c, in the raised pose) rides."""
    if kind in ("arm_r", "arm_l"):
        side = kind[-1]
        out = abs(c.x) - 1.5
        return ("arm" if out < 0.55 else "forearm" if out < 1.2 else "hand") + "_" + side
    if kind in ("leg_r", "leg_l"):
        side = kind[-1]
        return ("leg" if c.z > 0.95 else "shin" if c.z > 0.25 else "foot") + "_" + side
    if kind == "torso":
        return "spine" if c.z < 2.6 else "chest"
    if kind == "cloak":
        if c.x > 1.0 and c.z > 2.3:
            out = abs(c.x) - 1.5
            return ("arm" if out < 0.55 else "forearm") + "_l"
        return "chest" if c.z > 1.9 else "pelvis"
    if kind == "blade":
        return "hand_r"
    return kind


def to_blocks(p):
    return Vector((p.x * SCALE, p.y * SCALE, (p.z - GROUND) * SCALE))


def texture_of(mat):
    for n in mat.node_tree.nodes:
        if n.type == "TEX_IMAGE" and n.image is not None:
            return n.image
    return None


def save_png(name, rgba):
    os.makedirs(TEXDIR, exist_ok=True)
    h, w = rgba.shape[:2]
    img = bpy.data.images.get("Mythos_" + name)
    if img is not None:
        bpy.data.images.remove(img)
    img = bpy.data.images.new("Mythos_" + name, w, h, alpha=True)
    img.pixels[:] = rgba.astype(np.float32).ravel()
    img.filepath_raw = os.path.join(TEXDIR, name + ".png")
    img.file_format = "PNG"
    img.save()
    return img


def textures(mats):
    """Each source material becomes mythos_<n>.png (+ _glow.png where the pink corruption is); the untextured glowing
    torso band becomes a small purple texture that glows all over."""
    made = {}
    for i, mat in enumerate(mats):
        name = f"mythos_{i}"
        img = texture_of(mat)
        if img is None:
            rgba = np.zeros((16, 16, 4), np.float32)
            rgba[...] = (0.55, 0.24, 1.0, 1.0)
            rgba[::4, :, :3] = (0.85, 0.5, 1.0)
            save_png(name, rgba)
            save_png(name + "_glow", rgba)
            made[mat.name] = (name, True)
            continue
        w, h = img.size
        px = np.array(img.pixels[:], np.float32).reshape(h, w, 4)
        save_png(name, px)
        r, g, b = px[..., 0], px[..., 1], px[..., 2]
        pink = (r > 0.55) & (b > 0.45) & (g < r * 0.8)
        glow = np.zeros_like(px)
        glow[pink] = px[pink]
        glow[pink, 3] = 1.0
        has = bool(pink.any())
        if has:
            save_png(name + "_glow", glow)
        made[mat.name] = (name, has)
    return made


def build(*_ignored):
    scene = bpy.context.scene
    scene.render.fps = 60
    win = bpy.context.window_manager.windows[0]
    area = next(a for a in win.screen.areas if a.type == "VIEW_3D")
    region = next(r for r in area.regions if r.type == "WINDOW")
    src = bpy.data.collections.get("Mythos_Source") or bpy.data.collections.new("Mythos_Source")
    if src.name not in scene.collection.children:
        scene.collection.children.link(src)
    for ob in list(src.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    bpy.context.view_layer.active_layer_collection = bpy.context.view_layer.layer_collection.children[src.name]
    before = set(bpy.data.objects)
    with bpy.context.temp_override(window=win, area=area, region=region):
        bpy.ops.import_scene.gltf(filepath=SOURCE)
    sources = [o for o in bpy.data.objects if o not in before and o.type == "MESH"]
    bpy.context.view_layer.update()

    mats = []
    for o in sources:
        for m in o.data.materials:
            if m is not None and m not in mats:
                mats.append(m)
    tex = textures(mats)

    verts, faces, uvs, mat_idx, bones = [], [], [], [], []
    for o in sources:
        kind = part(o.name)
        me = o.data
        uv = me.uv_layers.active.data if me.uv_layers.active else None
        for poly in me.polygons:
            corners = [o.matrix_world @ me.vertices[me.loops[li].vertex_index].co for li in poly.loop_indices]
            c = sum(corners, Vector()) / len(corners)
            # Arms (and the cloak where it hangs off the left arm) go up into the rest pose; the blade into the hand.
            move = None
            if kind in ("arm_r", "arm_l"):
                move = ARM[kind[-1]]
            elif kind == "cloak" and c.x > 1.0 and c.z > 2.3:
                move = ARM["l"]
            elif kind == "blade":
                move = blade_into_hand()
            if move is not None:
                corners = [move @ p for p in corners]
                c = move @ c
            bone = bone_for(kind, c)
            if len(corners) < 3:
                continue
            for k in range(1, len(corners) - 1):        # fan-triangulate anything that isn't a triangle
                start = len(verts)
                for idx in (0, k, k + 1):
                    verts.append(to_blocks(corners[idx]))
                    li = poly.loop_indices[idx]
                    uvs.append(tuple(uv[li].uv) if uv else (0.0, 0.0))
                faces.append((start, start + 1, start + 2))
                m = me.materials[poly.material_index] if me.materials else None
                mat_idx.append(mats.index(m) if m in mats else 0)
                bones.append(bone)

    col = bpy.data.collections.get("Mythos") or bpy.data.collections.new("Mythos")
    if col.name not in scene.collection.children:
        scene.collection.children.link(col)
    for ob in list(col.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    me = bpy.data.meshes.new("Mythos_Body")
    me.from_pydata(verts, [], faces)
    uvl = me.uv_layers.new(name="UV")
    for p in me.polygons:
        for li in p.loop_indices:
            uvl.data[li].uv = uvs[me.loops[li].vertex_index]
    for m in mats:
        name, glows = tex[m.name]
        new = bpy.data.materials.get("Mythos_" + name) or bpy.data.materials.new("Mythos_" + name)
        new.use_nodes = True
        nt = new.node_tree
        nt.nodes.clear()
        out = nt.nodes.new("ShaderNodeOutputMaterial")
        bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = bpy.data.images["Mythos_" + name]
        t.interpolation = "Closest"
        nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
        nt.links.new(t.outputs["Alpha"], bsdf.inputs["Alpha"])
        if glows:
            g = nt.nodes.new("ShaderNodeTexImage")
            g.image = bpy.data.images["Mythos_" + name + "_glow"]
            g.interpolation = "Closest"
            nt.links.new(g.outputs["Color"], bsdf.inputs["Emission Color"])
            bsdf.inputs["Emission Strength"].default_value = 1.5
        nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
        new["dl_texture"] = name
        new["dl_glow"] = 1 if glows else 0
        me.materials.append(new)
    me.polygons.foreach_set("material_index", mat_idx)
    me.update()
    body = bpy.data.objects.new("Mythos_Body", me)
    col.objects.link(body)

    arm = bpy.data.armatures.new("Mythos_Rig")
    rig = bpy.data.objects.new("Mythos_Rig", arm)
    col.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="EDIT")
    eb = {}
    for name, parent, pivot in RIG:
        e = arm.edit_bones.new(name)
        e.head = to_blocks(Vector(pivot))
        e.tail = e.head + Vector((0, 0, -0.12))
        e.align_roll(Vector((0, 1, 0)))
        eb[name] = e
    for name, parent, _ in RIG:
        if parent != "root":
            eb[name].parent = eb[parent]
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="OBJECT")
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"
    vgs = {name: body.vertex_groups.new(name=name) for name, _, _ in RIG}
    counts = {}
    for fi, bone in enumerate(bones):
        vgs[bone].add(list(me.polygons[fi].vertices), 1.0, "REPLACE")
        counts[bone] = counts.get(bone, 0) + 1
    body.parent = rig
    body.modifiers.new("Armature", "ARMATURE").object = rig
    bpy.context.view_layer.layer_collection.children[src.name].hide_viewport = True
    return {"tris": len(faces), "per_bone": counts, "textures": {k: v for k, v in tex.items()}}
