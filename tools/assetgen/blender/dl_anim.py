"""Keyframing helpers for DEADLANDS characters built with dl_rig.py / mythos_build.py / subagent_build.py (60 fps).

A pose is a dict bone -> (x, y, z), the game's own ModelPart angles, plus "loc" (the root bone's offset in pixels,
model axes: y down). rot(("z", -1.2), ("x", -0.4)) composes rotations in the order given (first z, then x, each about
the parent's axes) and returns the matching XYZ angles, for limbs modelled in odd rest poses (a T-posed arm is lowered
with z, then swung with x).

A clip: Clip(name, length_frames, keys=[(frame, pose)] or gen=f(frame)->pose, step, loop, full, stepped, ground).
Grounding bakes the root bone's height frame by frame from the lowest "sole" vertices: "contact" keeps them on the
floor (an authored loc y becomes a lift), "floor" only stops them sinking. build(...) keys them all as Actions called
"<Name>_<clip>", ready for dl_export.export.
"""
import random

import bpy
from mathutils import Matrix

AXES = {"x": "X", "y": "Y", "z": "Z"}


def rot(*steps):
    m = Matrix.Identity(3)
    for axis, angle in steps:
        m = Matrix.Rotation(angle, 3, AXES[axis]) @ m
    e = m.to_euler("XYZ")
    return (e.x, e.y, e.z)


def lerp(a, b, k):
    return a + (b - a) * k


def smooth(k):
    k = min(1.0, max(0.0, k))
    return k * k * (3 - 2 * k)


class Poser:
    def __init__(self, base):
        self.base = base

    def pose(self, **over):
        p = {b: list(v) for b, v in self.base.items()}
        p["loc"] = [0.0, 0.0, 0.0]
        for k, v in over.items():
            p[k] = list(v)
        return p

    @staticmethod
    def add(p, **delta):
        for k, v in delta.items():
            p[k] = [a + b for a, b in zip(p[k], v)]
        return p

    @staticmethod
    def put(p, **over):
        for k, v in over.items():
            p[k] = list(v)
        return p

    @staticmethod
    def copy(p):
        return {k: list(v) for k, v in p.items()}

    @staticmethod
    def jitter(p, rng, amp, bones):
        q = {k: list(v) for k, v in p.items()}
        for b in bones:
            q[b] = [v + rng.uniform(-amp, amp) for v in q[b]]
        return q

    def mix(self, a, b, k):
        return {key: [lerp(x, y, k) for x, y in zip(a[key], b[key])] for key in a}


class Clip:
    def __init__(self, name, length, keys=None, gen=None, step=3, loop=False, full=True, stepped=False, ground=None):
        self.name, self.length, self.keys, self.gen, self.step = name, length, keys, gen, step
        self.loop, self.full, self.stepped, self.ground = loop, full, stepped, ground


def _fcurves(rig, action):
    try:
        return list(action.fcurves)
    except AttributeError:
        from bpy_extras import anim_utils
        bag = anim_utils.action_get_channelbag_for_slot(action, rig.animation_data.action_slot)
        return list(bag.fcurves) if bag else []


def _soles(rig, body, bones, height):
    names = {g.index: g.name for g in body.vertex_groups}
    pts = []
    for v in body.data.vertices:
        if v.co.z < height and v.groups and names[v.groups[0].group] in bones:
            pts.append((names[v.groups[0].group], v.co.copy()))
    return pts[::2] or pts


def _lowest(rig, pts):
    z = 1e9
    for bone, co in pts:
        pb = rig.pose.bones[bone]
        z = min(z, (pb.matrix @ pb.bone.matrix_local.inverted() @ co).z)
    return z


def build(name, clips, root_bone, sole_bones, sole_height):
    rig = bpy.data.objects[f"{name}_Rig"]
    body = bpy.data.objects[f"{name}_Body"]
    scene = bpy.context.scene
    bones = [b.name for b in rig.data.bones]
    if rig.animation_data is None:
        rig.animation_data_create()
    rig.animation_data.action = None
    for pb in rig.pose.bones:
        pb.rotation_euler = (0, 0, 0)
        pb.location = (0, 0, 0)
    bpy.context.view_layer.update()
    pts = _soles(rig, body, sole_bones, sole_height)
    target = _lowest(rig, pts)
    made = {}
    for clip in clips:
        old = bpy.data.actions.get(f"{name}_{clip.name}")
        if old is not None:
            bpy.data.actions.remove(old)
        action = bpy.data.actions.new(f"{name}_{clip.name}")
        action.use_fake_user = True
        rig.animation_data.action = action
        if clip.gen is not None:
            frames = list(range(0, clip.length + 1, clip.step))
            if frames[-1] != clip.length:
                frames.append(clip.length)
            keys = [(f, clip.gen(f % clip.length if clip.loop else f)) for f in frames]
        else:
            keys = clip.keys
        for f, p in keys:
            for b in bones:
                pb = rig.pose.bones[b]
                pb.rotation_euler = p[b]
                pb.keyframe_insert("rotation_euler", frame=f, group=b)
            root = rig.pose.bones[root_bone]
            root.location = [v / 16.0 for v in p["loc"]]
            root.keyframe_insert("location", frame=f, group=root_bone)
        for fc in _fcurves(rig, action):
            for kp in fc.keyframe_points:
                kp.interpolation = "CONSTANT" if clip.stepped else "BEZIER"
                kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"
            if clip.loop:
                fc.modifiers.new("CYCLES")
            fc.update()
        if clip.ground:
            root = rig.pose.bones[root_bone]
            baked = []
            for f in range(clip.length + 1):
                scene.frame_set(f)
                loc = root.location.copy()
                low = _lowest(rig, pts)
                if clip.ground == "contact":
                    shift = target - (low + loc.y) - loc.y
                else:
                    shift = -loc.y + max(0.0, target - low)
                baked.append((f, (loc.x, -shift, loc.z)))
            for fc in _fcurves(rig, action):
                if fc.data_path == f'pose.bones["{root_bone}"].location':
                    fc.keyframe_points.clear()
                    for m in list(fc.modifiers):
                        fc.modifiers.remove(m)
            for f, loc in baked:
                root.location = loc
                root.keyframe_insert("location", frame=f, group=root_bone)
            for fc in _fcurves(rig, action):
                if fc.data_path == f'pose.bones["{root_bone}"].location':
                    for kp in fc.keyframe_points:
                        kp.interpolation = "CONSTANT" if clip.stepped else "LINEAR"
                    if clip.loop:
                        fc.modifiers.new("CYCLES")
                    fc.update()
        action["dl_loop"] = int(clip.loop)
        action["dl_fullbody"] = int(clip.full)
        action["dl_stepped"] = int(clip.stepped)
        action["dl_length"] = clip.length
        action.frame_range = (0, clip.length)
        made[clip.name] = clip.length
    return made


def rng(seed):
    return random.Random(seed)
