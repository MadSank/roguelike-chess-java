"""
The masked characters of Ashes, built procedurally (no downloads needed):

  - a long robe shaped on a lathe (shoulders, waist, a flared hem) with soft vertical folds
  - a capelet over the shoulders and a deep hood framing the theatre mask (masks.py)
  - sleeves that follow the arm pose, ending in black leather gloves
  - a simple skeleton (spine, chest, neck, head, arms) with weights set by position,
    and a seamless idle loop: breathing, a slight sway, a slow tilt of the head

build_character(...) returns the armature object; everything else is parented to it.
Characters face +Y at facing = 0, with their feet at the given position.
"""
import bmesh
import bpy
import math

from mathutils import Matrix, Vector

import masks

IDLE_FRAMES = 48  # one breath; frame 49 equals frame 1, so clips of 48 frames loop seamlessly

# Arm poses: (elbow, wrist) offsets for the right arm, relative to the shoulder, in metres
# (x outward, y forward, z up). The left arm mirrors x unless the pose gives its own.
POSES = {
    "clasped": {"R": (Vector((0.04, 0.06, -0.3)), Vector((-0.13, 0.2, -0.43))),
                "L": (Vector((0.04, 0.06, -0.3)), Vector((-0.13, 0.2, -0.45)))},
    "offering": {"R": (Vector((0.05, 0.12, -0.27)), Vector((-0.02, 0.38, -0.22))),
                 "L": (Vector((0.04, 0.06, -0.3)), Vector((-0.12, 0.2, -0.44)))},
    "table": {"R": (Vector((0.06, 0.18, -0.25)), Vector((-0.02, 0.45, -0.33))),
              "L": (Vector((0.06, 0.18, -0.25)), Vector((-0.02, 0.45, -0.33)))},
}


# ─── MATERIALS ───────────────────────────────────────────────────────────────
def wool_material(name, tint=(0.014, 0.012, 0.011)):
    """Dark, coarse wool: colour variation, a fine weave bump and a little sheen."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    b = N["Principled BSDF"]
    coord = N.new("ShaderNodeTexCoord")
    var = N.new("ShaderNodeTexNoise")
    var.inputs["Scale"].default_value = 4.0
    var.inputs["Detail"].default_value = 4.0
    L.new(coord.outputs["Object"], var.inputs["Vector"])
    ramp = N.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (tint[0] * 0.6, tint[1] * 0.6, tint[2] * 0.6, 1)
    ramp.color_ramp.elements[1].color = (tint[0] * 1.6, tint[1] * 1.5, tint[2] * 1.4, 1)
    L.new(var.outputs["Fac"], ramp.inputs["Fac"])
    L.new(ramp.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = 0.95
    b.inputs["Sheen Weight"].default_value = 0.08
    b.inputs["Sheen Roughness"].default_value = 0.6
    weave = N.new("ShaderNodeTexNoise")
    weave.inputs["Scale"].default_value = 420.0
    L.new(coord.outputs["Object"], weave.inputs["Vector"])
    bump = N.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.25
    bump.inputs["Distance"].default_value = 0.001
    L.new(weave.outputs["Fac"], bump.inputs["Height"])
    # Crumples and creases: a larger, stretched noise so the cloth doesn't look like smooth clay.
    crease_coords = N.new("ShaderNodeMapping")
    crease_coords.inputs["Scale"].default_value = (1.0, 1.0, 0.25)  # long vertical creases
    L.new(coord.outputs["Object"], crease_coords.inputs["Vector"])
    crease = N.new("ShaderNodeTexNoise")
    crease.inputs["Scale"].default_value = 18.0
    crease.inputs["Detail"].default_value = 6.0
    L.new(crease_coords.outputs["Vector"], crease.inputs["Vector"])
    bump2 = N.new("ShaderNodeBump")
    bump2.inputs["Strength"].default_value = 0.45
    bump2.inputs["Distance"].default_value = 0.01
    L.new(crease.outputs["Fac"], bump2.inputs["Height"])
    L.new(bump.outputs["Normal"], bump2.inputs["Normal"])
    L.new(bump2.outputs["Normal"], b.inputs["Normal"])
    return m


def leather_material(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (0.012, 0.01, 0.009, 1)
    b.inputs["Roughness"].default_value = 0.42
    b.inputs["Coat Weight"].default_value = 0.2
    grain = nt.nodes.new("ShaderNodeTexNoise")
    grain.inputs["Scale"].default_value = 300.0
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.2
    nt.links.new(grain.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    return m


def void_material(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0, 0, 0, 1)
    m.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 1.0
    return m


# ─── MESH BUILDERS (all in the character's own space: feet at origin, facing +Y) ────
def _mesh_object(name, bm, mat, smooth=True, subsurf=2):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    if smooth:
        for p in me.polygons:
            p.use_smooth = True
    if subsurf:
        o.modifiers.new("smooth", "SUBSURF").levels = subsurf
    me.materials.append(mat)
    return o


def lathe(name, rings, mat, segments=40, folds=0.0, fold_count=9, seed=0.0, close_top=True):
    """
    rings: list of (z, rx, ry) from bottom to top. Folds push the surface in and out around
    the circumference, strongest at the bottom (cloth hanging from the shoulders).
    """
    bm = bmesh.new()
    loops = []
    zmax = rings[-1][0]
    for z, rx, ry in rings:
        loop = []
        strength = folds * max(0.0, 1.0 - z / zmax) ** 1.5
        for i in range(segments):
            a = 2 * math.pi * i / segments
            wobble = 1.0 + strength * (math.sin(a * fold_count + seed) + 0.5 * math.sin(a * (fold_count * 2 + 1) + seed * 2.3))
            loop.append(bm.verts.new((math.sin(a) * rx * wobble, math.cos(a) * ry * wobble, z)))
        loops.append(loop)
    for k in range(len(loops) - 1):
        a, b = loops[k], loops[k + 1]
        for i in range(segments):
            j = (i + 1) % segments
            bm.faces.new((a[i], a[j], b[j], b[i]))
    if close_top:
        bm.faces.new(list(reversed(loops[-1])))
    return _mesh_object(name, bm, mat)


def tube(name, points, radii, mat, segments=16, open_end=True):
    """A tube through points (list of Vector) with a radius per point (sleeves)."""
    bm = bmesh.new()
    loops = []
    for k, p in enumerate(points):
        t = (points[min(k + 1, len(points) - 1)] - points[max(k - 1, 0)]).normalized()
        side = t.cross(Vector((0, 0, 1)))
        if side.length < 1e-4:
            side = t.cross(Vector((0, 1, 0)))
        side.normalize()
        up = side.cross(t).normalized()
        loop = []
        for i in range(segments):
            a = 2 * math.pi * i / segments
            loop.append(bm.verts.new(p + (side * math.cos(a) + up * math.sin(a)) * radii[k]))
        loops.append(loop)
    for k in range(len(loops) - 1):
        a, b = loops[k], loops[k + 1]
        for i in range(segments):
            j = (i + 1) % segments
            bm.faces.new((a[i], a[j], b[j], b[i]))
    if not open_end:
        bm.faces.new(list(reversed(loops[-1])))
    o = _mesh_object(name, bm, mat)
    o.modifiers.new("thick", "SOLIDIFY").thickness = 0.006
    o.modifiers.move(1, 0)  # thickness before smoothing
    return o


def hood(name, centre, mat):
    """A deep hood: an egg-shaped shell with the face opening cut out, draping toward the back."""
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=32, v_segments=20, radius=1.0)
    for v in bm.verts:
        x, y, z = v.co
        # Bigger than a head, with a peak at the top-back and the lower edge flaring out.
        peak = 0.06 * max(0.0, z) ** 3 * max(0.0, -y + 0.3)
        flare = 1.0 + 0.25 * max(0.0, -z - 0.3)
        v.co = Vector((x * 0.19 * flare, y * 0.21 * flare - 0.03 * max(0, -y) - peak * 0.5, z * 0.24 + peak - 0.03 * max(0, -y)))
    # Cut the face opening: front-facing faces in the middle band.
    def in_opening(c):
        return c.y > 0.07 and (c.x / 0.115) ** 2 + ((c.z + 0.01) / 0.15) ** 2 < 1.0  # an oval face opening

    cut = [f for f in bm.faces if in_opening(f.calc_center_median())]
    bmesh.ops.delete(bm, geom=cut, context="FACES")
    for v in bm.verts:
        v.co += centre
    o = _mesh_object(name, bm, mat)
    sol = o.modifiers.new("thick", "SOLIDIFY")
    sol.thickness = 0.018
    o.modifiers.move(1, 0)
    return o


def glove(name, wrist, direction, mat, fist=False):
    """A gloved hand: a flattened mitten pointing along direction from the wrist."""
    d = direction.normalized()
    centre = wrist + d * 0.07
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=16, v_segments=10, radius=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * 0.045, v.co.y * (0.06 if fist else 0.085), v.co.z * 0.022))
    # Thumb, built in the hand's own space (beside the palm, angled forward).
    t = bmesh.new()
    bmesh.ops.create_uvsphere(t, u_segments=10, v_segments=6, radius=1.0)
    for v in t.verts:
        v.co = Vector((v.co.x * 0.016 + 0.04, v.co.y * 0.04 - 0.02, v.co.z * 0.016 + 0.006))
    me_t = bpy.data.meshes.new("thumb")
    t.to_mesh(me_t)
    t.free()
    bm.from_mesh(me_t)
    bpy.data.meshes.remove(me_t)
    # Then turn the whole hand to point along the forearm and move it to the wrist.
    rot = Vector((0, 1, 0)).rotation_difference(d).to_matrix().to_4x4()
    bmesh.ops.transform(bm, matrix=Matrix.Translation(centre) @ rot, verts=bm.verts)
    return _mesh_object(name, bm, mat, subsurf=1)


# ─── ARMATURE ────────────────────────────────────────────────────────────────
def _armature(name, scale_z, seated):
    arm_data = bpy.data.armatures.new(name)
    rig = bpy.data.objects.new(name, arm_data)
    bpy.context.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    base = 0.45 if seated else 0.0
    s = scale_z

    def bone(n, head, tail, parent=None):
        b = eb.new(n)
        b.head, b.tail = head, tail
        if parent:
            b.parent = eb[parent]
        return b

    bone("spine", (0, 0, base + (0.1 if seated else 0.9) * s), (0, 0, base + (0.55 if seated else 1.15) * s))
    bone("chest", eb["spine"].tail, (0, 0, base + (0.92 if seated else 1.42) * s), "spine")
    bone("neck", eb["chest"].tail, (0, 0.01, eb["chest"].tail.z + 0.1), "chest")
    bone("head", eb["neck"].tail, (0, 0.02, eb["neck"].tail.z + 0.22), "neck")
    for side, sx in (("R", 1), ("L", -1)):
        sh = Vector((0.2 * sx, 0, eb["chest"].tail.z - 0.04))
        bone("upper." + side, sh, sh + Vector((0.02 * sx, 0.02, -0.28)), "chest")
        bone("lower." + side, eb["upper." + side].tail, eb["upper." + side].tail + Vector((0, 0.1, -0.2)), "upper." + side)
    bpy.ops.object.mode_set(mode="OBJECT")
    return rig


def _bind(obj, rig, weights_fn):
    """Skins obj to rig using weights_fn(local_co) -> {bone: weight}."""
    obj.parent = rig
    for b in rig.data.bones:
        obj.vertex_groups.new(name=b.name)
    for v in obj.data.vertices:
        for bone_name, w in weights_fn(v.co).items():
            if w > 0:
                obj.vertex_groups[bone_name].add([v.index], w, "REPLACE")
    mod = obj.modifiers.new("rig", "ARMATURE")
    mod.object = rig
    obj.modifiers.move(len(obj.modifiers) - 1, 0)  # deform before smoothing


def _rigid(obj, rig, bone_name):
    """Attaches obj to one bone (it moves with it without bending)."""
    _bind(obj, rig, lambda co: {bone_name: 1.0})


def _plant_hands(rig):
    """Keeps the hands where they rest (on the table) while the body leans: IK on each arm,
    aiming at an empty placed at the wrist and parented to the rig (not to a bone)."""
    for side in ("R", "L"):
        bone = rig.data.bones["lower." + side]
        target = bpy.data.objects.new("%s_hand_%s" % (rig.name, side), None)
        bpy.context.collection.objects.link(target)
        target.empty_display_size = 0.03
        target.parent = rig
        target.location = bone.tail_local
        ik = rig.pose.bones["lower." + side].constraints.new("IK")
        ik.target = target
        ik.chain_count = 2


def _idle_pose(pb, f, seed, energy, extra=None):
    """Sets the idle pose for frame offset f (0..48), plus extra {bone: (x, y, z) radians}.
    For the spine, chest, neck and head (bones pointing up): positive x bows forward,
    y turns the head left/right, z tilts it to the side."""
    t = 2 * math.pi * f / IDLE_FRAMES
    phase = seed * 0.7
    rots = {
        "spine": (0, math.radians(0.8 * energy) * math.sin(t + phase), 0),
        "chest": (math.radians(1.6 * energy) * math.sin(t), 0, 0),
        "neck": (0, 0, 0),
        "head": (math.radians(2.5 * energy) * math.sin(t + 1.0 + phase),
                 math.radians(3.0 * energy) * math.sin(t + phase),
                 math.radians(2.0 * energy) * math.sin(t + 2.0)),
    }
    for side in ("R", "L"):
        rots["upper." + side] = (math.radians(1.0 * energy) * math.sin(t + 0.5), 0, 0)
        rots["lower." + side] = (0, 0, 0)
    for name, add in (extra or {}).items():
        add = (-add[0], add[1], add[2])  # a bone's +x rotation leans it back; extras use +x = forward
        rots[name] = tuple(a + b for a, b in zip(rots[name], add))
    for name, r in rots.items():
        pb[name].rotation_euler = r
    pb["chest"].scale = (1 + 0.012 * energy * (1 + math.sin(t)) / 2,) * 3


def _bake(rig, action_name, frames, extra_fn=None, step=2, cyclic=False):
    """Keys the idle (plus extra_fn(u) offsets, u = 0..1 over the clip) into a new action."""
    if not rig.animation_data:
        rig.animation_data_create()
    action = bpy.data.actions.new(action_name)
    _assign(rig, action)
    pb = rig.pose.bones
    for b in pb:
        b.rotation_mode = "XYZ"
    seed, energy = rig["seed"], rig["energy"]
    for f in range(0, frames + 1, step):
        _idle_pose(pb, f % IDLE_FRAMES, seed, energy, extra_fn(f / frames) if extra_fn else None)
        for b in pb:
            b.keyframe_insert("rotation_euler", frame=f + 1)
        pb["chest"].keyframe_insert("scale", frame=f + 1)
    if cyclic:  # repeat forever (so longer clips like the intro keep breathing)
        for fc in _fcurves(action):
            fc.modifiers.new("CYCLES")
    return action


def _assign(rig, action):
    rig.animation_data.action = action
    ad = rig.animation_data
    if hasattr(ad, "action_slot") and getattr(action, "slots", None) and len(action.slots):
        ad.action_slot = action.slots[0]


def _idle(rig, seed, energy=1.0):
    """A seamless 48-frame idle: breathing in the chest, a slight sway, a slow head tilt."""
    rig["seed"], rig["energy"] = seed, energy
    rig["idle_action"] = _bake(rig, rig.name + "_idle", IDLE_FRAMES, step=4, cyclic=True).name


# ─── REACTIONS ───────────────────────────────────────────────────────────────
# Each reaction lasts REACTION_FRAMES (a whole number of idle loops) and is the idle plus an
# extra motion that starts and ends at zero, so the game can cut from the idle loop into a
# reaction and back without a jump.
REACTION_FRAMES = 48
_D = math.radians


def _bell(u):
    """0 -> 1 -> 0 smoothly."""
    return math.sin(math.pi * u) ** 2


def _hold(u, rise=0.25, fall=0.8):
    """Rises quickly, holds, and settles back at the very end."""
    def smooth(a, b, x):
        x = max(0.0, min(1.0, (x - a) / (b - a)))
        return x * x * (3 - 2 * x)
    return smooth(0, rise, u) * (1 - smooth(fall, 1, u))


def _snap(u):
    """Fast reaction that peaks early (a flinch) and eases out."""
    return math.sin(math.pi * math.sqrt(u)) ** 2


REACTIONS = {
    # Dealers: a deal (two nods and a small bow) or a refusal (a slow shake of the head).
    "yes": lambda u: {"head": (_D(11) * _bell(u) * (0.5 - 0.5 * math.cos(4 * math.pi * u)), 0, 0),
                      "chest": (_D(5) * _bell(u), 0, 0)},
    "no": lambda u: {"head": (_D(-3) * _bell(u), _D(16) * _bell(u) * math.sin(6 * math.pi * u), 0),
                     "chest": (_D(-2) * _bell(u), 0, 0)},
    # Opponent.
    "think": lambda u: {"head": (_D(12) * _bell(u), _D(6) * math.sin(2 * math.pi * u) * _bell(u), _D(9) * _bell(u)),
                        "chest": (_D(4) * _bell(u), 0, 0)},
    "capture": lambda u: {"chest": (_D(7) * _hold(u), 0, 0),
                          "head": (_D(-4) * _hold(u), 0, _D(13) * _hold(u))},
    "hurt": lambda u: {"spine": (_D(-3) * _snap(u), 0, 0),
                       "chest": (_D(-6) * _snap(u), 0, 0),
                       "head": (_D(-10) * _snap(u), _D(7) * _snap(u) * math.sin(5 * math.pi * u), 0)},
    "check": lambda u: {"chest": (_D(9) * _hold(u), 0, 0),
                        "head": (_D(-6) * _hold(u), 0, _D(-10) * _hold(u))},
    "win": lambda u: {"spine": (_D(-4) * _hold(u), 0, 0),
                      "chest": (_D(-7) * _hold(u) + _D(2.5) * _hold(u) * math.sin(10 * math.pi * u), 0, 0),
                      "head": (_D(-14) * _hold(u), 0, _D(5) * _hold(u))},
    "lose": lambda u: {"spine": (_D(6) * _hold(u, 0.35, 0.85), 0, 0),
                       "chest": (_D(14) * _hold(u, 0.35, 0.85), 0, 0),
                       "head": (_D(22) * _hold(u, 0.35, 0.85), 0, _D(-6) * _hold(u, 0.35, 0.85))},
}


def react(rig, name):
    """Switches the rig to a reaction (made once, then reused). name=None goes back to the idle."""
    if name is None:
        _assign(rig, bpy.data.actions[rig["idle_action"]])
        return
    key = "%s_%s" % (rig.name, name)
    action = bpy.data.actions.get(key) or _bake(rig, key, REACTION_FRAMES, REACTIONS[name])
    _assign(rig, action)


def _fcurves(action):
    """F-curves of an action (Blender 4.4+ stores them in layers/strips/channel bags)."""
    if hasattr(action, "fcurves") and action.fcurves:
        return list(action.fcurves)
    curves = []
    for layer in getattr(action, "layers", []):
        for strip in layer.strips:
            for bag in getattr(strip, "channelbags", []):
                curves.extend(bag.fcurves)
    return curves


# ─── THE CHARACTER ───────────────────────────────────────────────────────────
def build_character(name, pos, facing, mood="smile", seed=1, seated=False, pose="clasped",
                    height=1.0, mats=None):
    """
    A masked, hooded figure. pos: feet position (Vector), facing: Z rotation (0 faces +Y).
    pose: "clasped" (hands folded in front), "offering" (right hand held out), "table" (hands on a table).
    Returns the armature; the character is parented to it.
    """
    mats = mats or {}
    wool = mats.get("wool") or wool_material(name + "_wool")
    dark_wool = mats.get("capelet") or wool_material(name + "_capelet", (0.02, 0.017, 0.015))
    glove_mat = mats.get("glove") or leather_material(name + "_glove")
    void = mats.get("void") or void_material(name + "_void")
    s = height
    base = 0.45 if seated else 0.0

    rig = _armature(name, s, seated)
    shoulder_z = base + (0.92 if seated else 1.42) * s - 0.04
    head_z = shoulder_z + 0.24

    # Robe: lathe profile (z, half-width, half-depth).
    if seated:
        rings = [(base - 0.02, 0.33, 0.36), (base + 0.15, 0.29, 0.26), (base + 0.45, 0.24, 0.18),
                 (shoulder_z - 0.12, 0.27, 0.17), (shoulder_z, 0.24, 0.15), (shoulder_z + 0.06, 0.1, 0.09)]
    else:
        rings = [(0.0, 0.42, 0.38), (0.25, 0.37, 0.32), (0.6, 0.3, 0.24), (0.95 * s, 0.26, 0.19),
                 (1.2 * s, 0.27, 0.18), (shoulder_z, 0.25, 0.16), (shoulder_z + 0.06, 0.1, 0.09)]
    robe = lathe(name + "_robe", rings, wool, folds=0.11, fold_count=7, seed=seed)

    # Capelet over the shoulders: a short, flared mantle.
    cap_rings = [(shoulder_z - 0.2, 0.33, 0.27), (shoulder_z - 0.08, 0.3, 0.22), (shoulder_z + 0.02, 0.22, 0.16),
                 (shoulder_z + 0.08, 0.11, 0.1)]
    capelet = lathe(name + "_capelet", cap_rings, dark_wool, folds=0.05, fold_count=11, seed=seed + 1)
    capelet.modifiers.new("thick", "SOLIDIFY").thickness = 0.012

    hood_obj = hood(name + "_hood", Vector((0, -0.005, head_z + 0.01)), dark_wool)

    # Head void and mask, facing +Y here (the rig is turned at the end).
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(0, 0.0, head_z))
    head = bpy.context.active_object
    head.scale = (0.095, 0.105, 0.125)
    head.data.materials.append(void)
    mask = masks.make_mask(name + "_mask", mood, seed=seed)
    mask.location = (0, 0.112, head_z - 0.005)
    mask.rotation_euler = (math.pi / 2, 0, math.pi)  # masks face -Y as built

    # Sleeves and gloves following the pose.
    arms = POSES.get(pose, POSES["clasped"])
    sleeves, gloves = [], []
    for side, sx in (("R", 1), ("L", -1)):
        elbow_off, wrist_off = arms[side]
        sh = Vector((0.2 * sx, 0.0, shoulder_z))
        elbow = sh + Vector((elbow_off.x * sx, elbow_off.y, elbow_off.z)) * s
        wrist = sh + Vector((wrist_off.x * sx, wrist_off.y, wrist_off.z)) * s
        pts = [sh + Vector((0.02 * sx, 0, -0.02)), sh.lerp(elbow, 0.5), elbow, elbow.lerp(wrist, 0.55), wrist]
        sleeves.append(tube("%s_sleeve_%s" % (name, side), pts, [0.085, 0.08, 0.075, 0.085, 0.1], wool))
        gloves.append((side, glove("%s_glove_%s" % (name, side), wrist - (wrist - elbow).normalized() * 0.03,
                                   wrist - elbow, glove_mat)))

    # Skin: robe and capelet bend with spine/chest by height; sleeves split upper/lower.
    def body_w(co):
        t = max(0.0, min(1.0, (co.z - (base + 0.6 * s)) / (shoulder_z - base - 0.6 * s)))
        return {"spine": 1.0 - t, "chest": t}

    _bind(robe, rig, body_w)
    _bind(capelet, rig, lambda co: {"chest": 1.0})
    _rigid(hood_obj, rig, "head")
    _rigid(head, rig, "head")
    _rigid(mask, rig, "head")
    for sl, (side, gl) in zip(sleeves, gloves):
        def arm_w(co, side=side):
            t = max(0.0, min(1.0, (shoulder_z - co.z) / 0.35))
            return {"upper." + side: 1.0 - t, "lower." + side: t}
        _bind(sl, rig, arm_w)
        _rigid(gl, rig, "lower." + side)

    if pose == "table":
        _plant_hands(rig)
    _idle(rig, seed, energy=0.7 if seated else 1.0)
    rig.location = pos
    rig.rotation_euler = (0, 0, facing)
    return rig
