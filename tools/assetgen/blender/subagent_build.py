"""Mythos' sub-agents, modelled in Blender: small, boxy, six-limbed skitterers of corrupted code.

Run inside Blender (on an empty file, or one with no "SubAgent" collection). Makes the "SubAgent" collection with
"SubAgent_Rig" and "SubAgent_Body": a low body, a wedge head with two horn nubs and glowing eyes, two forward claw-arms,
four splayed legs and a short tail, every part a box, about 1 block long and half a block high. Texture: 64 x 64,
black with violet shading and magenta corruption spots; eyes, spots and the tail tip glow.

Then dl_mesh_export.export("SubAgent", "SubAgentGeometry", "sub_agent.bin", "sub_agent").
"""
import os

import bmesh
import bpy
import numpy as np
from mathutils import Vector

ROOT = os.environ.get("DEADLANDS_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
TEXDIR = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands", "textures", "entity", "sub_agent")
T = 64

# (bone, parent, pivot in blocks)
RIG = [
    ("body", "root", (0.0, 0.0, 0.47)),
    ("head", "body", (0.0, -0.33, 0.5)),
    ("claw_r", "body", (-0.27, -0.26, 0.5)),
    ("claw_l", "body", (0.27, -0.26, 0.5)),
    ("leg_fr", "body", (-0.24, -0.12, 0.38)),
    ("leg_fl", "body", (0.24, -0.12, 0.38)),
    ("leg_br", "body", (-0.24, 0.2, 0.38)),
    ("leg_bl", "body", (0.24, 0.2, 0.38)),
    ("tail", "body", (0.0, 0.34, 0.52)),
]
# UV regions on the 64x64 texture (u0, v0, u1, v1; v from the bottom).
SKIN = (0.0, 0.25, 1.0, 1.0)
EYE = (0.0, 0.0, 0.125, 0.125)
TIP = (0.25, 0.0, 0.375, 0.125)


def box(bm, uvl, lo, hi, region, parts, bone):
    """An axis-aligned box from lo to hi, every face mapped to region."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    vs = [bm.verts.new(p) for p in [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                                    (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    return quads(bm, uvl, vs, region, parts, bone)


def beam(bm, uvl, a, b, thick, region, parts, bone):
    """A square box from point a to point b, thick on a side (a leg, an arm)."""
    a, b = Vector(a), Vector(b)
    axis = (b - a).normalized()
    side = axis.cross(Vector((0, 0, 1)))
    if side.length < 1e-3:
        side = Vector((1, 0, 0))
    side.normalize()
    up = side.cross(axis).normalized()
    h = thick / 2
    corners = []
    for end in (a, b):
        for s, u in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            corners.append(end + side * s * h + up * u * h)
    vs = [bm.verts.new(c) for c in corners]
    return quads(bm, uvl, vs, region, parts, bone)


def quads(bm, uvl, vs, region, parts, bone):
    u0, v0, u1, v1 = region
    idx = [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    for f in idx:
        face = bm.faces.new([vs[i] for i in f])
        for loop, (u, v) in zip(face.loops, ((u0, v0), (u1, v0), (u1, v1), (u0, v1))):
            loop[uvl].uv = (u, v)
        parts.append((face, bone))


def texture():
    rng = np.random.default_rng(11)
    img = np.zeros((T, T, 3), np.float32)
    glow = np.zeros((T, T, 3), np.float32)
    shade = rng.random((T, T)) * 0.5
    base = np.array([0.05, 0.035, 0.07]) + np.array([0.13, 0.08, 0.2]) * shade[..., None]
    img[:] = base
    spots = rng.random((T // 4, T // 4)) > 0.8
    spots = np.kron(spots, np.ones((4, 4), bool))
    spots &= rng.random((T, T)) > 0.25
    pink = np.array([0.95, 0.48, 0.85])
    img[spots] = pink
    glow[spots] = pink * 0.6
    img[: T // 8, : T // 8] = glow[: T // 8, : T // 8] = np.array([1.0, 0.25, 0.82])      # eyes
    img[: T // 8, T // 4: 3 * T // 8] = glow[: T // 8, T // 4: 3 * T // 8] = pink          # the tail tip
    return img, glow


def save(name, rgb, alpha):
    os.makedirs(TEXDIR, exist_ok=True)
    im = bpy.data.images.get(name) or bpy.data.images.new(name, T, T, alpha=True)
    im.pixels[:] = np.concatenate([rgb, alpha[..., None]], axis=2).ravel()
    im.filepath_raw = os.path.join(TEXDIR, name + ".png")
    im.file_format = "PNG"
    im.save()
    return im


def build():
    scene = bpy.context.scene
    scene.render.fps = 60
    col = bpy.data.collections.get("SubAgent") or bpy.data.collections.new("SubAgent")
    if col.name not in scene.collection.children:
        scene.collection.children.link(col)
    for ob in list(col.objects):
        bpy.data.objects.remove(ob, do_unlink=True)

    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UV")
    parts = []
    box(bm, uvl, (-0.22, -0.32, 0.32), (0.22, 0.34, 0.62), SKIN, parts, "body")            # the body, low and long
    box(bm, uvl, (-0.12, -0.12, 0.6), (0.12, 0.22, 0.7), SKIN, parts, "body")              # a ridge along the back
    box(bm, uvl, (-0.17, -0.62, 0.36), (0.17, -0.3, 0.62), SKIN, parts, "head")            # the head, a wedge
    box(bm, uvl, (-0.12, -0.72, 0.36), (0.12, -0.6, 0.48), SKIN, parts, "head")            # the snout
    box(bm, uvl, (-0.16, -0.5, 0.62), (-0.1, -0.42, 0.76), SKIN, parts, "head")            # horn nubs
    box(bm, uvl, (0.1, -0.5, 0.62), (0.16, -0.42, 0.76), SKIN, parts, "head")
    box(bm, uvl, (-0.13, -0.635, 0.52), (-0.05, -0.615, 0.57), EYE, parts, "head")         # the eyes
    box(bm, uvl, (0.05, -0.635, 0.52), (0.13, -0.615, 0.57), EYE, parts, "head")
    for side, x in (("r", -1), ("l", 1)):
        beam(bm, uvl, (0.27 * x, -0.26, 0.5), (0.42 * x, -0.62, 0.3), 0.09, SKIN, parts, f"claw_{side}")
        beam(bm, uvl, (0.42 * x, -0.62, 0.3), (0.36 * x, -0.82, 0.04), 0.07, TIP, parts, f"claw_{side}")
        beam(bm, uvl, (0.24 * x, -0.12, 0.38), (0.5 * x, -0.22, 0.2), 0.07, SKIN, parts, f"leg_f{side}")
        beam(bm, uvl, (0.5 * x, -0.22, 0.2), (0.56 * x, -0.28, 0.0), 0.06, SKIN, parts, f"leg_f{side}")
        beam(bm, uvl, (0.24 * x, 0.2, 0.38), (0.5 * x, 0.32, 0.2), 0.07, SKIN, parts, f"leg_b{side}")
        beam(bm, uvl, (0.5 * x, 0.32, 0.2), (0.56 * x, 0.42, 0.0), 0.06, SKIN, parts, f"leg_b{side}")
    beam(bm, uvl, (0.0, 0.34, 0.52), (0.0, 0.62, 0.6), 0.1, SKIN, parts, "tail")
    beam(bm, uvl, (0.0, 0.62, 0.6), (0.0, 0.8, 0.72), 0.08, TIP, parts, "tail")
    face_bone = {f: b for f, b in parts}
    tri = bmesh.ops.triangulate(bm, faces=bm.faces[:])
    for made, original in tri["face_map"].items():   # each new triangle rides its quad's bone
        face_bone[made] = face_bone.get(original, face_bone.get(made))
    bm.faces.index_update()
    bones_by_face = [face_bone.get(f, "body") for f in bm.faces]
    me = bpy.data.meshes.new("SubAgent_Body")
    bm.to_mesh(me)
    bm.free()

    # Unshare vertices so each triangle sits on one bone.
    verts, faces, uvs = [], [], []
    uvd = me.uv_layers["UV"].data
    for p in me.polygons:
        start = len(verts)
        for li in p.loop_indices:
            verts.append(me.vertices[me.loops[li].vertex_index].co.copy())
            uvs.append(tuple(uvd[li].uv))
        faces.append(tuple(range(start, start + len(p.loop_indices))))
    bpy.data.meshes.remove(me)
    me = bpy.data.meshes.new("SubAgent_Body")
    me.from_pydata(verts, [], faces)
    uvn = me.uv_layers.new(name="UV")
    for p in me.polygons:
        for li in p.loop_indices:
            uvn.data[li].uv = uvs[me.loops[li].vertex_index]

    rgb, glow = texture()
    save("sub_agent", rgb, np.ones((T, T), np.float32))
    save("sub_agent_glow", glow, np.clip(glow.max(axis=2) * 1.5, 0, 1))
    mat = bpy.data.materials.get("SubAgent_skin") or bpy.data.materials.new("SubAgent_skin")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = bpy.data.images["sub_agent"]
    t.interpolation = "Closest"
    g = nt.nodes.new("ShaderNodeTexImage")
    g.image = bpy.data.images["sub_agent_glow"]
    g.interpolation = "Closest"
    nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(g.outputs["Color"], bsdf.inputs["Emission Color"])
    bsdf.inputs["Emission Strength"].default_value = 1.5
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    mat["dl_texture"] = "sub_agent"
    mat["dl_glow"] = 1
    me.materials.append(mat)
    body = bpy.data.objects.new("SubAgent_Body", me)
    col.objects.link(body)

    arm = bpy.data.armatures.new("SubAgent_Rig")
    rig = bpy.data.objects.new("SubAgent_Rig", arm)
    col.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="EDIT")
    eb = {}
    for name, parent, pivot in RIG:
        b = arm.edit_bones.new(name)
        b.head = Vector(pivot)
        b.tail = b.head + Vector((0, 0, -0.05))
        b.align_roll(Vector((0, 1, 0)))
        eb[name] = b
    for name, parent, _ in RIG:
        if parent != "root":
            eb[name].parent = eb[parent]
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="OBJECT")
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"
    vgs = {b: body.vertex_groups.new(name=b) for b, _, _ in RIG}
    for fi, b in enumerate(bones_by_face):
        p = me.polygons[fi]
        vgs[b].add(list(p.vertices), 1.0, "REPLACE")
    body.parent = rig
    body.modifiers.new("Armature", "ARMATURE").object = rig
    return {"tris": len(me.polygons)}
