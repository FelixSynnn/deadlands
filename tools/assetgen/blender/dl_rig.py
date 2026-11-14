"""Builds any DEADLANDS rigged-mesh character in Blender from the mod's own files (a DLMESH1 .bin, its Geometry.java,
its textures), the same way dot_rig.py builds D.O.T.

    import dl_rig; dl_rig.build("Aberrant", "AberrantGeometry.java", "aberrant.bin", "aberrant", fps=60)

Makes a collection named after the character holding "<Name>_Rig" (one bone per ModelPart) and "<Name>_Body" (bound
rigidly to it). Every bone's rest axes are the model's axes, so a pose bone's XYZ Euler is the ModelPart's
xRot/yRot/zRot exactly and the pelvis location x 16 is its offset in pixels (see dot_rig.py). One Blender unit is one
block; the character stands on z = 0 facing -Y. At 60 fps one game tick is three frames.
"""
import os
import re
import shutil
import struct
import tempfile

import bpy
from mathutils import Matrix, Vector

ROOT = os.environ.get("DEADLANDS_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands")
RENDER = os.path.join(ROOT, "src", "client", "java", "com", "deadlands", "client", "render")
C = Matrix(((1, 0, 0), (0, 0, 1), (0, -1, 0)))


def lp(path):
    path = os.path.abspath(path)
    prefix = "\\\\?\\"
    return prefix + path if os.name == "nt" and not path.startswith(prefix) else path


def to_blender(p):
    return Vector((p[0] / 16.0, p[2] / 16.0, (24.0 - p[1]) / 16.0))


def read_rig(geometry):
    src = open(lp(os.path.join(RENDER, geometry)), encoding="utf-8").read()
    bones = re.search(r'BONES = \{(.*?)\};', src).group(1).replace('"', '').replace(' ', '').split(',')
    rel, parent = {}, {}
    for b, p, x, y, z in re.findall(r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("\w+", CubeListBuilder.create\(\), '
                                    r'PartPose.offset\(([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F\)\)', src):
        rel[b] = (float(x), float(y), float(z))
        parent[b] = p
    pivot = {"root": (0.0, 24.0, 0.0)}
    for b in bones:
        q = pivot[parent[b]]
        pivot[b] = (q[0] + rel[b][0], q[1] + rel[b][1], q[2] + rel[b][2])
    return bones, parent, pivot


def read_mesh(binname):
    d = open(lp(os.path.join(ASSETS, "models", "entity", binname)), "rb").read()
    o = 8
    nm = struct.unpack(">i", d[o:o + 4])[0]
    o += 4
    mats = []
    for _ in range(nm):
        n = struct.unpack(">h", d[o:o + 2])[0]
        o += 2
        mats.append(d[o:o + n].decode())
        o += n + 1
    ng = struct.unpack(">i", d[o:o + 4])[0]
    o += 4
    groups = []
    for _ in range(ng):
        bi, mi, n = struct.unpack(">hhi", d[o:o + 8])
        o += 8
        groups.append((bi, mi, struct.unpack(">%df" % (n * 24), d[o:o + n * 96])))
        o += n * 96
    return mats, groups


def material(prefix, texdir, name):
    mat = bpy.data.materials.get(f"{prefix}_{name}") or bpy.data.materials.new(f"{prefix}_{name}")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    short = os.path.join(tempfile.gettempdir(), "deadlands_" + prefix.lower())
    os.makedirs(short, exist_ok=True)
    copy = os.path.join(short, name + ".png")
    shutil.copyfile(lp(os.path.join(ASSETS, "textures", "entity", texdir, name + ".png")), copy)
    old = bpy.data.images.get(f"{prefix}_{name}")
    if old is not None:
        bpy.data.images.remove(old)
    img = bpy.data.images.load(copy)
    img.name = f"{prefix}_{name}"
    img.pack()
    tex.image = img
    tex.interpolation = "Closest"
    bsdf.inputs["Roughness"].default_value = 0.9
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def build(name, geometry, binname, texdir, fps=60):
    bones, parent, pivot = read_rig(geometry)
    mats, groups = read_mesh(binname)
    scene = bpy.context.scene
    scene.render.fps = fps
    col = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if col.name not in scene.collection.children:
        scene.collection.children.link(col)
    for ob in list(col.objects):
        bpy.data.objects.remove(ob, do_unlink=True)

    arm = bpy.data.armatures.new(f"{name}_Rig")
    rig = bpy.data.objects.new(f"{name}_Rig", arm)
    col.objects.link(rig)
    view_layer = bpy.context.view_layer
    view_layer.objects.active = rig
    rig.select_set(True)
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="EDIT")
    eb = {}
    for b in bones:
        bone = arm.edit_bones.new(b)
        head = to_blender(pivot[b])
        bone.head = head
        bone.tail = head + Vector((0, 0, -0.12))
        bone.align_roll(Vector((0, 1, 0)))
        eb[b] = bone
    for b in bones:
        if parent[b] != "root":
            eb[b].parent = eb[parent[b]]
    with bpy.context.temp_override(active_object=rig, object=rig, selected_objects=[rig], edit_object=rig):
        bpy.ops.object.mode_set(mode="OBJECT")
    for b in bones:
        m = arm.bones[b].matrix_local.to_3x3()
        assert all(abs(m[i][j] - C[i][j]) < 1e-4 for i in range(3) for j in range(3)), (b, m)
        rig.pose.bones[b].rotation_mode = "XYZ"

    verts, faces, uvs, mat_idx, tri_bone = [], [], [], [], []
    for bi, mi, f in groups:
        q = pivot[bones[bi]]
        for t in range(0, len(f), 24):
            base = len(verts)
            for k in range(3):
                v = f[t + k * 8: t + k * 8 + 8]
                verts.append(to_blender((q[0] + v[0], q[1] + v[1], q[2] + v[2])))
                uvs.append((v[3], 1.0 - v[4]))
            faces.append((base, base + 1, base + 2))
            mat_idx.append(mi)
            tri_bone.append(bi)
    me = bpy.data.meshes.new(f"{name}_Body")
    me.from_pydata(verts, [], faces)
    uv = me.uv_layers.new(name="UV")
    for poly in me.polygons:
        for li in poly.loop_indices:
            uv.data[li].uv = uvs[me.loops[li].vertex_index]
    for m in mats:
        me.materials.append(material(name, texdir, m))
    me.polygons.foreach_set("material_index", mat_idx)
    me.update()
    body = bpy.data.objects.new(f"{name}_Body", me)
    col.objects.link(body)
    vgs = {b: body.vertex_groups.new(name=b) for b in bones}
    for fi, bi in enumerate(tri_bone):
        vgs[bones[bi]].add([fi * 3, fi * 3 + 1, fi * 3 + 2], 1.0, "REPLACE")
    body.parent = rig
    body.modifiers.new("Armature", "ARMATURE").object = rig
    return {"bones": bones, "triangles": len(faces), "materials": mats}
