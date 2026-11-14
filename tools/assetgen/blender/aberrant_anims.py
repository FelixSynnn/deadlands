"""The Aberrant's Blender animations (on the rig dl_rig.build("Aberrant", ...) makes), keyed at 60 fps.

dig: the loop he plays while clawing through a block (AsesinoEntity.dig): crouched into the wall, the left claw raking
down, then the machete chopping, half a beat apart, his torso twisting into each blow, his head jolting at each impact,
the coat swinging. 48 frames (16 ticks, 0.8 s) per cycle.

Angles are the game's numbers (see dl_rig.py). In the model's axes: -x raises a limb forward, +x on the torso leans
forward, the thigh swings from the hip and the shin folds back with +x. Run dl_export.export("Aberrant", "aberrant.anim")
afterwards.
"""
import bpy

RIG = bpy.data.objects["Aberrant_Rig"]
BONES = [b.name for b in RIG.data.bones]

# His crouch into the wall (every key starts from this).
CROUCH = {
    "pelvis": (0, 0, 0), "torso": (0.55, 0, 0), "neck": (-0.3, 0, 0), "head": (-0.25, 0, 0),
    "arm_r": (-1.0, 0, 0.08), "forearm_r": (-0.6, 0, 0), "hand_r": (0, 0, 0),
    "arm_l": (-1.0, 0, -0.08), "forearm_l": (-0.6, 0, 0), "hand_l": (0, 0, 0),
    "coat_front_r": (-0.3, 0, 0), "coat_front_l": (-0.4, 0, 0), "coat_back": (0.35, 0, 0),
    "leg_r": (-0.45, 0, 0.05), "shin_r": (0.8, 0, 0), "leg_l": (-0.65, 0, -0.05), "shin_l": (0.95, 0, 0),
}


def pose(loc=(0, 1.6, 0), **over):
    p = {b: list(CROUCH.get(b, (0, 0, 0))) for b in BONES}
    for k, v in over.items():
        p[k] = list(v)
    p["loc"] = list(loc)
    return p


# (frame, pose): left claw up (8), rake down (14), machete up (26), chop (32), back to the start (48).
KEYS = [
    (0, pose(arm_l=(-1.3, 0, -0.1), forearm_l=(-0.9, 0, 0), arm_r=(-0.8, 0, 0.1), torso=(0.55, 0.05, 0))),
    (8, pose(arm_l=(-2.35, 0.1, -0.25), forearm_l=(-1.1, 0, 0), hand_l=(-0.6, 0, 0), torso=(0.45, -0.2, 0),
             neck=(-0.25, 0.1, 0), arm_r=(-0.7, 0, 0.15), coat_back=(0.3, 0, 0))),
    (14, pose(arm_l=(-0.75, -0.1, 0.05), forearm_l=(-0.2, 0, 0), hand_l=(0.5, 0, 0), torso=(0.68, 0.18, 0.03),
              neck=(-0.35, -0.05, 0), head=(-0.15, 0.06, 0.08), arm_r=(-1.1, 0, 0.1), loc=(0, 2.0, -0.4),
              coat_back=(0.42, 0, 0))),
    (17, pose(arm_l=(-0.85, -0.1, 0.0), forearm_l=(-0.3, 0, 0), hand_l=(0.35, 0, 0), torso=(0.64, 0.15, 0),
              head=(-0.22, 0.0, -0.04), loc=(0, 1.8, -0.2))),
    (26, pose(arm_r=(-2.75, -0.1, 0.35), forearm_r=(-1.25, 0, 0), hand_r=(-0.4, 0, 0), torso=(0.42, 0.25, 0),
              neck=(-0.25, -0.12, 0), arm_l=(-1.15, 0, -0.1), forearm_l=(-0.7, 0, 0), coat_back=(0.3, 0, 0))),
    (32, pose(arm_r=(-0.7, 0.15, -0.15), forearm_r=(-0.15, 0, 0), hand_r=(0.45, 0, 0), torso=(0.7, -0.22, -0.03),
              neck=(-0.35, 0.08, 0), head=(-0.12, -0.08, -0.1), arm_l=(-1.25, 0, -0.1), loc=(0, 2.1, -0.5),
              coat_back=(0.45, 0, 0), coat_front_r=(-0.4, 0, 0))),
    (36, pose(arm_r=(-0.85, 0.1, -0.05), forearm_r=(-0.3, 0, 0), hand_r=(0.3, 0, 0), torso=(0.65, -0.18, 0),
              head=(-0.2, 0.0, 0.05), loc=(0, 1.8, -0.2))),
]
KEYS.append((48, KEYS[0][1]))


def fcurves_of(action):
    try:
        return list(action.fcurves)
    except AttributeError:
        from bpy_extras import anim_utils
        bag = anim_utils.action_get_channelbag_for_slot(action, RIG.animation_data.action_slot)
        return list(bag.fcurves) if bag else []


def build():
    if RIG.animation_data is None:
        RIG.animation_data_create()
    old = bpy.data.actions.get("Aberrant_dig")
    if old is not None:
        bpy.data.actions.remove(old)
    action = bpy.data.actions.new("Aberrant_dig")
    action.use_fake_user = True
    RIG.animation_data.action = action
    for f, p in KEYS:
        for b in BONES:
            pb = RIG.pose.bones[b]
            pb.rotation_euler = p[b]
            pb.keyframe_insert("rotation_euler", frame=f, group=b)
        pel = RIG.pose.bones["pelvis"]
        pel.location = [v / 16.0 for v in p["loc"]]
        pel.keyframe_insert("location", frame=f, group="pelvis")
    for fc in fcurves_of(action):
        for kp in fc.keyframe_points:
            kp.interpolation = "BEZIER"
            kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"
        fc.modifiers.new("CYCLES")
        fc.update()
    action["dl_loop"] = 1
    action["dl_fullbody"] = 1
    action["dl_stepped"] = 0
    action["dl_length"] = 48
    action.frame_range = (0, 48)
    scene = bpy.context.scene
    scene.frame_start, scene.frame_end = 0, 47
    return {"dig": 48}


result = build()
