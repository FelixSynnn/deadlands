"""Builds D.O.T. in Blender from the mod's own files: models/entity/dot.bin, DotGeometry.java and the textures.

Run inside Blender (Text editor > Run Script, or exec(open(path).read())). It makes (or rebuilds) a scene called "DOT"
with an armature "DOT_Rig" and the mesh "DOT_Body" bound rigidly to it, one bone per ModelPart.

The bones are set up so that Blender's numbers ARE the game's numbers: every bone's rest axes are the model's axes
(bone X = model X, bone Y = model Y (down), bone Z = model Z (back)), so a pose bone's XYZ Euler rotation equals the
ModelPart's xRot, yRot, zRot exactly (both are Rz*Ry*Rx), and the pelvis' location times 16 is its offset in pixels.
That is why every bone points straight down: it looks odd, but it makes the export exact. One Blender unit is one
block (16 pixels); he stands on z = 0 facing -Y.
"""
import os
import re
import shutil
import struct
import tempfile

import bpy
from mathutils import Matrix, Vector

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__) if "__file__" in dir() else ".", "..", "..", ".."))
if "DEADLANDS_ROOT" in os.environ:
    ROOT = os.environ["DEADLANDS_ROOT"]
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands")
GEOMETRY = os.path.join(ROOT, "src", "client", "java", "com", "deadlands", "client", "render", "DotGeometry.java")
BIN = os.path.join(ASSETS, "models", "entity", "dot.bin")
TEXDIR = os.path.join(ASSETS, "textures", "entity", "dot", "model")

def lp(path):
    """The path with Windows' long-path prefix: this project's folders run past the 260-character limit."""
    path = os.path.abspath(path)
    prefix = "\\\\?\\"
    return prefix + path if os.name == "nt" and not path.startswith(prefix) else path


# Model space (pixels; y down, front -Z) to Blender (blocks; z up, front -Y).
C = Matrix(((1, 0, 0), (0, 0, 1), (0, -1, 0)))


def to_blender(p):
    return Vector((p[0] / 16.0, p[2] / 16.0, (24.0 - p[1]) / 16.0))


def read_rig():
    src = open(lp(GEOMETRY), encoding="utf-8").read()
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


def read_mesh():
    d = open(lp(BIN), "rb").read()
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
        floats = struct.unpack(">%df" % (n * 24), d[o:o + n * 96])
        o += n * 96
        groups.append((bi, mi, floats))
    return mats, groups


def material(name):
    mat = bpy.data.materials.get("DOT_" + name) or bpy.data.materials.new("DOT_" + name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    # Blender's image loader can't open paths over 260 characters on Windows (Python can): copy to a short path, load,
    # and pack it into the .blend.
    short = os.path.join(tempfile.gettempdir(), "deadlands_dot")
    os.makedirs(short, exist_ok=True)
    copy = os.path.join(short, name + ".png")
    shutil.copyfile(lp(os.path.join(TEXDIR, name + ".png")), copy)
    old = bpy.data.images.get("DOT_" + name)
    if old is not None:
        bpy.data.images.remove(old)
    img = bpy.data.images.load(copy)
    img.name = "DOT_" + name
    img.pack()
    tex.image = img
    tex.interpolation = "Closest"
    bsdf.inputs["Roughness"].default_value = 0.9
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    tex.location, bsdf.location, out.location = (-400, 0), (-100, 0), (200, 0)
    return mat


def build():
    bones, parent, pivot = read_rig()
    mats, groups = read_mesh()

    scene = bpy.data.scenes.get("DOT") or bpy.data.scenes.new("DOT")
    bpy.context.window.scene = scene
    scene.render.fps = 20  # one frame = one game tick
    for ob in [o for o in scene.objects if o.name.startswith("DOT_")]:
        bpy.data.objects.remove(ob, do_unlink=True)

    # --- the rig
    arm_data = bpy.data.armatures.new("DOT_Rig")
    arm_data.display_type = "OCTAHEDRAL"
    rig = bpy.data.objects.new("DOT_Rig", arm_data)
    scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = {}
    for b in bones:
        bone = arm_data.edit_bones.new(b)
        head = to_blender(pivot[b])
        bone.head = head
        bone.tail = head + Vector((0, 0, -0.12))
        bone.align_roll(Vector((0, 1, 0)))  # bone Z = model Z (+Y in Blender)
        bone.use_deform = True
        eb[b] = bone
    for b in bones:
        if parent[b] != "root":
            eb[b].parent = eb[parent[b]]
            eb[b].use_connect = False
    bpy.ops.object.mode_set(mode="OBJECT")
    for b in bones:
        m = arm_data.bones[b].matrix_local.to_3x3()
        assert all(abs(m[i][j] - C[i][j]) < 1e-4 for i in range(3) for j in range(3)), (b, m)
        pb = rig.pose.bones[b]
        pb.rotation_mode = "XYZ"  # = ModelPart's Rz*Ry*Rx

    # --- the mesh, each triangle on its bone
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
    me = bpy.data.meshes.new("DOT_Body")
    me.from_pydata(verts, [], faces)
    uv = me.uv_layers.new(name="UV")
    for poly in me.polygons:
        for li in poly.loop_indices:
            uv.data[li].uv = uvs[me.loops[li].vertex_index]
    for m in mats:
        me.materials.append(material(m))
    me.polygons.foreach_set("material_index", mat_idx)
    me.update()
    body = bpy.data.objects.new("DOT_Body", me)
    scene.collection.objects.link(body)
    groups_by_bone = {b: body.vertex_groups.new(name=b) for b in bones}
    for fi, bi in enumerate(tri_bone):
        groups_by_bone[bones[bi]].add([fi * 3, fi * 3 + 1, fi * 3 + 2], 1.0, "REPLACE")
    body.parent = rig
    mod = body.modifiers.new("Armature", "ARMATURE")
    mod.object = rig
    return {"bones": len(bones), "triangles": len(faces), "materials": mats}


result = build()
