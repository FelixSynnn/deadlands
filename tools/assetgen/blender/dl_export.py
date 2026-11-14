"""Writes a character's Blender animations to the game (the DLANIM2 file RiggedAnimation reads).

    import dl_export; dl_export.export("Aberrant", "aberrant.anim")

Every action named "<Name>_<clip>" with a "dl_length" property (its length in frames) is sampled at every frame: each
bone's XYZ Euler (= the ModelPart's xRot/yRot/zRot on a dl_rig rig) and the pelvis offset in pixels.

File (big-endian): "DLANIM2\\0"; float samples per game tick (scene fps / 20, so 3 at 60 fps); int bone count; per bone
a short length + UTF-8 name; int clip count; per clip: short length + UTF-8 name, a flags byte (1 loops, 2 whole body,
4 stepped), int sample count, then per sample bone count x 3 floats and 3 floats of pelvis offset. A looping clip leaves
out its last frame (it is the first again).
"""
import os
import struct

import bpy

ROOT = os.environ.get("DEADLANDS_ROOT") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))


def lp(path):
    path = os.path.abspath(path)
    prefix = "\\\\?\\"
    return prefix + path if os.name == "nt" and not path.startswith(prefix) else path


def export(name, filename, pelvis="pelvis"):
    rig = bpy.data.objects[f"{name}_Rig"]
    scene = bpy.context.scene
    bones = [b.name for b in rig.data.bones]
    for pb in rig.pose.bones:
        assert not pb.constraints and pb.rotation_mode == "XYZ", pb.name
    actions = sorted((a for a in bpy.data.actions if a.name.startswith(name + "_") and "dl_length" in a), key=lambda a: a.name)
    keep, keep_frame = rig.animation_data.action, scene.frame_current
    out = bytearray(b"DLANIM2\0")
    out += struct.pack(">f", scene.render.fps / 20.0)
    out += struct.pack(">i", len(bones))
    for b in bones:
        n = b.encode("utf-8")
        out += struct.pack(">h", len(n)) + n
    out += struct.pack(">i", len(actions))
    summary = {}
    for action in actions:
        clip = action.name[len(name) + 1:]
        loop = bool(action["dl_loop"])
        length = int(action["dl_length"])
        frames = range(length) if loop else range(length + 1)
        rig.animation_data.action = action
        n = clip.encode("utf-8")
        out += struct.pack(">h", len(n)) + n
        flags = (1 if loop else 0) | (2 if action.get("dl_fullbody") else 0) | (4 if action.get("dl_stepped") else 0)
        out += struct.pack(">bi", flags, len(frames))
        for f in frames:
            scene.frame_set(f)
            vals = []
            for b in bones:
                vals.extend(rig.pose.bones[b].rotation_euler)
            vals.extend(v * 16.0 for v in rig.pose.bones[pelvis].location)
            out += struct.pack(">%df" % len(vals), *vals)
        summary[clip] = len(frames)
    rig.animation_data.action = keep
    scene.frame_set(keep_frame)
    path = os.path.join(ROOT, "src", "main", "resources", "assets", "deadlands", "models", "entity", filename)
    with open(lp(path), "wb") as fh:
        fh.write(out)
    return {"file": filename, "bytes": len(out), "clips": summary}
