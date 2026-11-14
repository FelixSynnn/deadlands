"""Builds a DEADLANDS box-model character in Blender from its Java geometry, for animating (the infected, the Brute,
the bosses), or from a hand-written part list (Minecraft's player model, for the story people).

    import dl_boxrig; dl_boxrig.from_java("Walker", "infected/InfectedGeometry.java", "humanoid", "infected/walker_0.png")

Reads every "PartDefinition x = parent.addOrReplaceChild("x", CubeListBuilder.create().texOffs(u, v).addBox(...)...,
PartPose.offset(...))" line of the method, makes one box per addBox (UVs laid out the way Minecraft lays out a cube on
its texture) and one bone per part at its pivot, in a collection named after the character ("<Name>_Rig",
"<Name>_Body"). As in dl_rig.py the bone axes are the model's, so a pose bone's XYZ Euler is the ModelPart's
xRot/yRot/zRot, and a root bone's location x 16 is its offset in pixels. Only the animations go to the game
(dl_export.py): its box models stay in Java; the mesh here is for seeing what you animate.
"""
import os
import re
import shutil
import tempfile

import bmesh
import bpy
from mathutils import Vector

ROOT = os.environ.get("DEADLANDS_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
RENDER = os.path.join(ROOT, "src", "client", "java", "com", "deadlands", "client", "render")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands", "textures", "entity")

LINE = re.compile(r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("(\w+)", CubeListBuilder\.create\(\)(.*?), '
                  r'PartPose\.(?:offset|offsetAndRotation)\(([^)]*)\)\);')
CUBE = re.compile(r'\.texOffs\((\d+), (\d+)\)\.addBox\(([^)]*)\)')


def lp(path):
    path = os.path.abspath(path)
    prefix = "\\\\?\\"
    return prefix + path if os.name == "nt" and not path.startswith(prefix) else path


def num(s):
    return float(s.strip().rstrip("F").rstrip("f"))


def parse_java(java, method):
    """[(part, parent_part or None, (ox, oy, oz), [(u, v, x, y, z, w, h, d)])], and the texture size."""
    src = open(lp(os.path.join(RENDER, java)), encoding="utf-8").read()
    start = src.index(f"LayerDefinition {method}(")
    end = src.index("return LayerDefinition.create(mesh,", start)
    body = src[start:end]
    tw, th = (int(v) for v in re.search(r"LayerDefinition\.create\(mesh, (\d+), (\d+)\)", src[end:end + 80]).groups())
    var_to_part = {}
    parts = []
    for var, parent_var, name, cubes, pose in LINE.findall(body):
        offset = tuple(num(v) for v in pose.split(",")[:3])
        boxes = []
        for u, v, args in CUBE.findall(cubes):
            x, y, z, w, h, d = (num(a) for a in args.split(",")[:6])
            boxes.append((int(u), int(v), x, y, z, w, h, d))
        var_to_part[var] = name
        parts.append((name, var_to_part.get(parent_var), offset, boxes))
    return parts, (tw, th)


# Minecraft's player model (64 x 64 skin), for the story people: part, parent, pivot offset, boxes (+ the overlay layer).
PLAYER = [
    ("head", None, (0.0, 0.0, 0.0), [(0, 0, -4, -8, -4, 8, 8, 8), (32, 0, -4.5, -8.5, -4.5, 9, 9, 9)]),
    ("body", None, (0.0, 0.0, 0.0), [(16, 16, -4, 0, -2, 8, 12, 4), (16, 32, -4.25, -0.25, -2.25, 8.5, 12.5, 4.5)]),
    ("right_arm", None, (-5.0, 2.0, 0.0), [(40, 16, -3, -2, -2, 4, 12, 4), (40, 32, -3.25, -2.25, -2.25, 4.5, 12.5, 4.5)]),
    ("left_arm", None, (5.0, 2.0, 0.0), [(32, 48, -1, -2, -2, 4, 12, 4), (48, 48, -1.25, -2.25, -2.25, 4.5, 12.5, 4.5)]),
    ("right_leg", None, (-1.9, 12.0, 0.0), [(0, 16, -2, 0, -2, 4, 12, 4), (0, 32, -2.25, -0.25, -2.25, 4.5, 12.5, 4.5)]),
    ("left_leg", None, (1.9, 12.0, 0.0), [(16, 48, -2, 0, -2, 4, 12, 4), (0, 48, -2.25, -0.25, -2.25, 4.5, 12.5, 4.5)]),
]


def to_blender(p):
    return Vector((p[0] / 16.0, p[2] / 16.0, (24.0 - p[1]) / 16.0))


def _cube(bm, uvl, pivot, box, tw, th):
    u, v, x, y, z, w, h, d = box
    x0, y0, z0 = pivot[0] + x, pivot[1] + y, pivot[2] + z
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    # Corners in model space (y down), then to Blender.
    c = {}
    for ix, xx in ((0, x0), (1, x1)):
        for iy, yy in ((0, y0), (1, y1)):
            for iz, zz in ((0, z0), (1, z1)):
                c[(ix, iy, iz)] = bm.verts.new(to_blender((xx, yy, zz)))

    def rect(a, b, cw, ch):
        return [(a / tw, 1 - b / th), ((a + cw) / tw, 1 - b / th), ((a + cw) / tw, 1 - (b + ch) / th), (a / tw, 1 - (b + ch) / th)]

    faces = [
        # (corners top-left, top-right, bottom-right, bottom-left as seen on the texture), uv rect
        ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], rect(u + d, v, w, d)),                 # top (y0)
        ([(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)], rect(u + d + w, v, w, d)),             # bottom (y1)
        ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], rect(u + d, v + d, w, h)),             # front (-z)
        ([(1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)], rect(u + 2 * d + w, v + d, w, h)),     # back (+z)
        ([(0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)], rect(u, v + d, d, h)),                 # side (-x)
        ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], rect(u + d + w, v + d, d, h)),         # side (+x)
    ]
    made = []
    for corners, uvs in faces:
        vs = [c[k] for k in corners]
        if len({id(v) for v in vs}) < 4 or (vs[0].co - vs[2].co).length < 1e-6:
            continue
        try:
            f = bm.faces.new(vs)
        except ValueError:
            continue  # a flat box: its two big faces are the same face
        for loop, uv in zip(f.loops, uvs):
            loop[uvl].uv = uv
        made.append(f)
    return made


def build(name, parts, tex_size, texture, root_offset=(0.0, 0.0, 0.0)):
    """parts as parse_java returns. Pivots accumulate from root_offset (model space, ground at y = 24)."""
    scene = bpy.context.scene
    scene.render.fps = 60
    col = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if col.name not in scene.collection.children:
        scene.collection.children.link(col)
    for ob in list(col.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    pivot = {}
    for part, parent, offset, _ in parts:
        base = pivot[parent] if parent else root_offset
        pivot[part] = (base[0] + offset[0], base[1] + offset[1], base[2] + offset[2])
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UV")
    face_bone = {}
    tw, th = tex_size
    for part, _, _, boxes in parts:
        for box in boxes:
            for f in _cube(bm, uvl, pivot[part], box, tw, th):
                face_bone[f] = part
    tri = bmesh.ops.triangulate(bm, faces=bm.faces[:])
    for made, original in tri["face_map"].items():
        face_bone[made] = face_bone.get(original, face_bone.get(made))
    bm.faces.index_update()
    bones_by_face = [face_bone.get(f, parts[0][0]) for f in bm.faces]
    me = bpy.data.meshes.new(f"{name}_Body")
    bm.to_mesh(me)
    bm.free()
    # Unshare vertices so each face is on one bone.
    verts, faces, uvs = [], [], []
    uvd = me.uv_layers["UV"].data
    for p in me.polygons:
        s = len(verts)
        for li in p.loop_indices:
            verts.append(me.vertices[me.loops[li].vertex_index].co.copy())
            uvs.append(tuple(uvd[li].uv))
        faces.append(tuple(range(s, s + len(p.loop_indices))))
    bpy.data.meshes.remove(me)
    me = bpy.data.meshes.new(f"{name}_Body")
    me.from_pydata(verts, [], faces)
    uvn = me.uv_layers.new(name="UV")
    for p in me.polygons:
        for li in p.loop_indices:
            uvn.data[li].uv = uvs[me.loops[li].vertex_index]
    mat = bpy.data.materials.new(f"{name}_skin")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    if texture:
        short = os.path.join(tempfile.gettempdir(), "deadlands_box")
        os.makedirs(short, exist_ok=True)
        copy = os.path.join(short, name + ".png")
        shutil.copyfile(lp(os.path.join(TEXTURES, texture)), copy)
        img = bpy.data.images.load(copy)
        img.pack()
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = img
        t.interpolation = "Closest"
        nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
        nt.links.new(t.outputs["Alpha"], bsdf.inputs["Alpha"])
    me.materials.append(mat)
    body = bpy.data.objects.new(f"{name}_Body", me)
    col.objects.link(body)

    arm = bpy.data.armatures.new(f"{name}_Rig")
    rig = bpy.data.objects.new(f"{name}_Rig", arm)
    col.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="EDIT")
    eb = {}
    for part, parent, _, _ in parts:
        e = arm.edit_bones.new(part)
        e.head = to_blender(pivot[part])
        e.tail = e.head + Vector((0, 0, -0.1))
        e.align_roll(Vector((0, 1, 0)))
        eb[part] = e
    for part, parent, _, _ in parts:
        if parent:
            eb[part].parent = eb[parent]
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="OBJECT")
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"
    vgs = {part: body.vertex_groups.new(name=part) for part, _, _, _ in parts}
    for fi, part in enumerate(bones_by_face):
        vgs[part].add(list(me.polygons[fi].vertices), 1.0, "REPLACE")
    body.parent = rig
    body.modifiers.new("Armature", "ARMATURE").object = rig
    return {"parts": [p[0] for p in parts], "tris": len(me.polygons)}


def from_java(name, java, method, texture, root_offset=(0.0, 0.0, 0.0)):
    parts, size = parse_java(java, method)
    return build(name, parts, size, texture, root_offset)


def player(name, skin):
    return build(name, PLAYER, (64, 64), skin)
