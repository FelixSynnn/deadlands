"""Writes D.O.T.'s Blender animations to the game: src/main/resources/assets/deadlands/models/entity/dot.anim.

Run inside Blender after dot_rig.py and dot_anims.py (or after editing the DOT_* actions by hand). Every action called
DOT_<clip> is sampled once per frame (= once per game tick): each bone's XYZ Euler (on this rig, exactly the
ModelPart's xRot/yRot/zRot) and the pelvis offset in pixels. The game (RiggedAnimation) interpolates between frames.

File (big-endian, read with DataInputStream): "DLANIM1\\0"; int bone count; per bone a short length + UTF-8 name;
int clip count; per clip: short length + UTF-8 name, a flags byte (1 loops, 2 whole body, 4 stepped: no
interpolation between frames), int frame count, then for each frame bone count x 3 floats and 3 floats of pelvis
offset. A looping clip leaves out its last frame (it is the first one again).
"""
import os
import struct

import bpy

ROOT = os.environ.get("DEADLANDS_ROOT") or os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands", "models", "entity", "dot.anim")


def lp(path):
    path = os.path.abspath(path)
    prefix = "\\\\?\\"
    return prefix + path if os.name == "nt" and not path.startswith(prefix) else path


def export():
    rig = bpy.data.objects["DOT_Rig"]
    scene = bpy.context.scene
    bones = [b.name for b in rig.data.bones]
    for pb in rig.pose.bones:
        assert not pb.constraints, f"{pb.name} has constraints: bake them first"
        assert pb.rotation_mode == "XYZ", f"{pb.name} must use XYZ Euler"
    actions = sorted((a for a in bpy.data.actions if a.name.startswith("DOT_") and "dl_length" in a),
                     key=lambda a: a.name)
    keep = rig.animation_data.action
    keep_frame = scene.frame_current
    out = bytearray(b"DLANIM1\0")
    out += struct.pack(">i", len(bones))
    for b in bones:
        n = b.encode("utf-8")
        out += struct.pack(">h", len(n)) + n
    out += struct.pack(">i", len(actions))
    summary = {}
    for action in actions:
        name = action.name[4:]
        loop, full, stepped = bool(action["dl_loop"]), bool(action["dl_fullbody"]), bool(action["dl_stepped"])
        length = int(action["dl_length"])
        frames = range(length) if loop else range(length + 1)
        rig.animation_data.action = action
        n = name.encode("utf-8")
        out += struct.pack(">h", len(n)) + n
        out += struct.pack(">bi", (1 if loop else 0) | (2 if full else 0) | (4 if stepped else 0), len(frames))
        for f in frames:
            scene.frame_set(f)
            vals = []
            for b in bones:
                vals.extend(rig.pose.bones[b].rotation_euler)
            vals.extend(v * 16.0 for v in rig.pose.bones["pelvis"].location)
            out += struct.pack(">%df" % len(vals), *vals)
        summary[name] = len(frames)
    rig.animation_data.action = keep
    scene.frame_set(keep_frame)
    with open(lp(OUT), "wb") as fh:
        fh.write(out)
    return {"file": OUT, "bytes": len(out), "clips": summary}


result = export()
