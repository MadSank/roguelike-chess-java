"""
Realistic masked characters for Ashes.

Each character is a real human body from MPFB 2 (the MakeHuman add-on for Blender; CC0
assets) with its game-engine skeleton, dressed in clothes that are simulated as cloth:

  - a dark knitted tunic (the body itself, in fabric) and black leather gloves on real hands
  - a long robe skirt from the waist to the floor
  - an open-front cloak from the shoulders, and a deep hood framing the theatre mask (masks.py)

How a character is made (build_character):
  1. Generate the body (build, height and so on from the spec) and fit the rig.
  2. Work out the target pose (seated at the table, hands clasped, holding cards out) with a
     small two-bone solver, then animate from the rest A-pose into it over a second and a half.
  3. Simulate the skirt, cloak and hood as one piece of cloth over that move, colliding with
     the body (and chair and table), so the cloth falls and folds naturally around the pose.
  4. Freeze the settled cloth and bind it to the body (Surface Deform), so it follows the
     breathing and the reactions afterwards without simulating again.

Everything is built in MPFB's own frame, where the character faces -Y with the feet at the
origin; place() turns and moves the finished character into the scene.

Needs: MPFB 2 installed in Blender ("blender --online-mode -c extension install mpfb --enable"),
and the fabric textures from fetch_assets.py.
"""
import addon_utils
import bmesh
import bpy
import math
import os

from mathutils import Euler, Matrix, Quaternion, Vector

import masks
import scene_kit as kit

SIM_POSE_FRAME = 40   # the body reaches its pose here...
SIM_END = 72          # ...and the cloth has settled by here
FINGERS = ("index", "middle", "ring", "pinky")


def _mpfb():
    addon_utils.enable("bl_ext.blender_org.mpfb", default_set=True)
    from bl_ext.blender_org.mpfb.services.humanservice import HumanService
    from bl_ext.blender_org.mpfb.services.targetservice import TargetService
    return HumanService, TargetService


def _update():
    bpy.context.view_layer.update()


# ─── MATERIALS ───────────────────────────────────────────────────────────────
def fabric(name, tex_id, tint=(1, 1, 1), scale=3.0, sheen=0.04, bump=1.0, rough_boost=0.0):
    """A Poly Haven fabric (colour, roughness, normal) tinted by multiplying, tiled every 1/scale metres
    of UV (garments here have UVs in metres)."""
    folder = os.path.join(kit.ASSETS, "textures", tex_id)
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N["Principled BSDF"]
    uv = N.new("ShaderNodeTexCoord")
    mapping = N.new("ShaderNodeMapping")
    mapping.inputs["Scale"].default_value = (scale, scale, scale)
    L.new(uv.outputs["UV"], mapping.inputs["Vector"])

    def tex(suffix, colour):
        n = N.new("ShaderNodeTexImage")
        n.image = bpy.data.images.load(os.path.join(folder, "%s_%s.jpg" % (tex_id, suffix)), check_existing=True)
        if not colour:
            n.image.colorspace_settings.name = "Non-Color"
        L.new(mapping.outputs["Vector"], n.inputs["Vector"])
        return n

    tint_mix = N.new("ShaderNodeMix")
    tint_mix.data_type = "RGBA"
    tint_mix.blend_type = "MULTIPLY"
    tint_mix.inputs[0].default_value = 1.0
    grey = N.new("ShaderNodeHueSaturation")
    grey.inputs["Saturation"].default_value = 0.2
    L.new(tex("diff", True).outputs["Color"], grey.inputs["Color"])
    L.new(grey.outputs["Color"], tint_mix.inputs[6])
    tint_mix.inputs[7].default_value = (*tint, 1)
    L.new(tint_mix.outputs[2], bsdf.inputs["Base Color"])
    rough = tex("rough", False)
    if rough_boost:
        add = N.new("ShaderNodeMath")
        add.operation = "ADD"
        add.use_clamp = True
        add.inputs[1].default_value = rough_boost
        L.new(rough.outputs["Color"], add.inputs[0])
        L.new(add.outputs[0], bsdf.inputs["Roughness"])
    else:
        L.new(rough.outputs["Color"], bsdf.inputs["Roughness"])
    nmap = N.new("ShaderNodeNormalMap")
    nmap.inputs["Strength"].default_value = bump
    L.new(tex("nor_gl", False).outputs["Color"], nmap.inputs["Color"])
    L.new(nmap.outputs["Normal"], bsdf.inputs["Normal"])
    if sheen:
        bsdf.inputs["Sheen Weight"].default_value = sheen
        bsdf.inputs["Sheen Roughness"].default_value = 0.6
        bsdf.inputs["Sheen Tint"].default_value = (*[min(1.0, c * 3) for c in tint], 1)
    return m


def default_materials():
    """Shared materials: knitted tunic, leather gloves and the void inside the hood."""
    return {
        "tunic": fabric("tunic", "knitted_fleece", (0.07, 0.064, 0.058), scale=7.0, sheen=0.03, bump=1.6),
        "glove": fabric("glove", "fabric_leather_02", (0.06, 0.05, 0.045), scale=8.0, sheen=0.0, rough_boost=0.08),
        "lining": _lining(),
    }


def _lining():
    """Near-black and fully matte (no specular), so nothing shows inside the hood."""
    m = kit.plain("hood_lining", (0.006, 0.005, 0.005), rough=1.0)
    m.node_tree.nodes["Principled BSDF"].inputs["Specular IOR Level"].default_value = 0.0
    return m


def morphed_coords(obj):
    """Vertex positions with the shape keys (MPFB's body morphs) applied, before any modifier."""
    saved = [(m, m.show_viewport) for m in obj.modifiers]
    for m, _ in saved:
        m.show_viewport = False
    dg = bpy.context.evaluated_depsgraph_get()
    ev = obj.evaluated_get(dg)
    me = ev.to_mesh()
    coords = [v.co.copy() for v in me.vertices]
    ev.to_mesh_clear()
    for m, shown in saved:
        m.show_viewport = shown
    return coords


def seat_height(objs, centre, radius=0.18):
    """Height of a chair's seat: the highest surface near the seat centre below 0.75 m."""
    best = 0.45
    found = []
    for o in objs:
        if o.type != "MESH":
            continue
        for v in o.data.vertices:
            w = o.matrix_world @ v.co
            if (Vector((w.x, w.y)) - Vector(centre)).length < radius and w.z < 0.75:
                found.append(w.z)
    return max(found) if found else best


# ─── BODY ────────────────────────────────────────────────────────────────────
def make_body(name, build):
    """An MPFB body with the game-engine rig. build: MPFB macro values (gender, age, muscle,
    weight, height, proportions; 0..1)."""
    HS, TS = _mpfb()
    macro = TS.get_default_macro_info_dict()
    macro.update({"gender": 1.0, "age": 0.5, "muscle": 0.55, "weight": 0.5, "height": 0.6})
    macro.update(build)
    body = HS.create_human(macro_detail_dict=macro)
    rig = HS.add_builtin_rig(body, "game_engine")
    rig.name, body.name = name, name + "_body"
    for pb in rig.pose.bones:
        pb.rotation_mode = "QUATERNION"
    return rig, body


def dress_body(body, rig, mats):
    """Tunic everywhere; gloves on the hands and a little past the wrists."""
    body.data.materials.clear()
    body.data.materials.append(mats["tunic"])
    body.data.materials.append(mats["glove"])
    body.data.materials.append(mats["lining"])
    groups = {g.index: g.name for g in body.vertex_groups}
    hand_names = ("hand_", "thumb_") + tuple(f + "_" for f in FINGERS)
    wrists = {s: rig.data.bones["hand_" + s].head_local for s in ("l", "r")}
    coords = morphed_coords(body)
    neck_z = rig.data.bones["neck_01"].head_local.z
    glove, hidden = [], []
    for v in body.data.vertices:
        w_hand = w_fore = w_head = 0.0
        side = None
        for e in v.groups:
            n = groups.get(e.group, "")
            if n.startswith(hand_names):
                w_hand += e.weight
            elif n.startswith("lowerarm_"):
                w_fore += e.weight
                side = n[-1]
            elif n in ("head", "neck_01"):
                w_head += e.weight
        hidden.append(w_head > 0.5 or coords[v.index].z > neck_z - 0.01)
        cuff = side is not None and w_fore > 0.2 and (coords[v.index] - wrists[side]).length < 0.075
        glove.append(w_hand > 0.5 or cuff)
    for p in body.data.polygons:
        if sum(glove[i] for i in p.vertices) * 2 > len(p.vertices):
            p.material_index = 1
        elif sum(hidden[i] for i in p.vertices) * 2 > len(p.vertices):
            p.material_index = 2
    for p in body.data.polygons:
        p.use_smooth = True
    sub = body.modifiers.new("smooth", "SUBSURF")
    sub.levels, sub.render_levels = 0, 1


# ─── POSING ──────────────────────────────────────────────────────────────────
def aim(rig, bone, direction):
    """Turns a bone (in the current pose) so it points along direction (armature space)."""
    _update()
    pb = rig.pose.bones[bone]
    rest = pb.bone.matrix_local.to_3x3()
    rest_dir = rest @ Vector((0, 1, 0))
    turn = rest_dir.rotation_difference(Vector(direction).normalized()).to_matrix()
    pb.matrix = Matrix.Translation(pb.head) @ (turn @ rest).to_4x4()
    _update()


def reach(rig, upper, lower, target, hint):
    """Two-bone solve: bends upper/lower so lower's tail lands on target, the joint bending toward hint."""
    _update()
    a, b = rig.pose.bones[upper], rig.pose.bones[lower]
    s = a.head.copy()
    la, lb = a.bone.length, b.bone.length
    d = Vector(target) - s
    dist = min(d.length, (la + lb) * 0.995)
    dn = d.normalized()
    h = Vector(hint)
    h = (h - dn * h.dot(dn)).normalized()
    cos_a = max(-1.0, min(1.0, (la * la + dist * dist - lb * lb) / (2 * la * dist)))
    ang = math.acos(cos_a)
    elbow = s + dn * (la * math.cos(ang)) + h * (la * math.sin(ang))
    aim(rig, upper, elbow - s)
    aim(rig, lower, s + dn * dist - rig.pose.bones[lower].head)


def curl(rig, amount, thumb=0.3):
    """Curls every finger (radians per joint) and the thumb a little."""
    for side in ("l", "r"):
        for f in FINGERS:
            for seg in (1, 2, 3):
                rig.pose.bones["%s_%02d_%s" % (f, seg, side)].rotation_quaternion = Quaternion((1, 0, 0), amount * (0.7 if seg == 1 else 1.0))
        for seg in (2, 3):
            rig.pose.bones["thumb_%02d_%s" % (seg, side)].rotation_quaternion = Quaternion((1, 0, 0), thumb)
    _update()


def pose_seated_table(rig, table_top, table_near_y, chair_y, seat_z=0.46):
    """Sitting upright on a chair (behind, +Y), forearms resting on the table (in front, -Y)."""
    hip = rig.pose.bones["pelvis"].bone.head_local
    rig.pose.bones["Root"].location = (0, chair_y - 0.04 - hip.y, seat_z + 0.1 - hip.z)
    _update()
    for s, x in (("r", -1), ("l", 1)):
        reach(rig, "thigh_" + s, "calf_" + s, (0.12 * x, chair_y - 0.5, 0.09), (0, -1, 0.3))
        aim(rig, "foot_" + s, (0.05 * x, -0.9, -0.42))
    for bone, d in (("spine_01", (0, -0.06, 1)), ("spine_02", (0, -0.12, 1)), ("spine_03", (0, -0.12, 1)),
                    ("neck_01", (0, -0.35, 1)), ("head", (0, -0.22, 1))):
        aim(rig, bone, d)
    for s, x in (("r", -1), ("l", 1)):
        wrist = (0.19 * x, table_near_y - 0.06, table_top + 0.035)
        reach(rig, "upperarm_" + s, "lowerarm_" + s, wrist, (x, 0.5, -1.0))
        aim(rig, "hand_" + s, (-0.18 * x, -1, -0.04))
    curl(rig, -0.12, thumb=0.0)            # MPFB's rest hand is already curled: straighten it


def pose_standing(rig, kind):
    """Standing; kind "clasped" (hands folded in front) or "offering" (right hand held out)."""
    for bone, d in (("spine_01", (0, 0.02, 1)), ("spine_02", (0, 0.0, 1)), ("spine_03", (0, -0.03, 1)),
                    ("neck_01", (0, -0.25, 1)), ("head", (0, -0.12, 1))):
        aim(rig, bone, d)
    chest = rig.pose.bones["spine_03"].head
    for s, x in (("r", -1), ("l", 1)):
        if kind == "offering" and s == "r":
            reach(rig, "upperarm_r", "lowerarm_r", (-0.17, -0.42, chest.z - 0.12), (-1, 0.4, -1.2))
            aim(rig, "hand_r", (0.05, -1, 0.1))
        else:
            reach(rig, "upperarm_" + s, "lowerarm_" + s, (0.035 * x, -0.2, chest.z - 0.3), (x, 0.6, -0.6))
            aim(rig, "hand_" + s, (-0.9 * x, -0.4, -0.3))
    curl(rig, 0.3, thumb=0.2)


def _snapshot(rig):
    return {pb.name: (pb.rotation_quaternion.copy(), pb.location.copy()) for pb in rig.pose.bones}


def _apply(rig, snap, t=1.0):
    for pb in rig.pose.bones:
        q, loc = snap[pb.name]
        pb.rotation_quaternion = Quaternion().slerp(q, t)
        pb.location = loc * t


def _key_pose_move(rig, snap):
    """Animates rest (frame 1..8) -> pose (SIM_POSE_FRAME) and holds it."""
    rig.animation_data_create()
    rig.animation_data.action = bpy.data.actions.new(rig.name + "_dress")
    for frame, t in ((1, 0.0), (8, 0.0), (SIM_POSE_FRAME, 1.0), (SIM_END + 5, 1.0)):
        _apply(rig, snap, t)
        for pb in rig.pose.bones:
            pb.keyframe_insert("rotation_quaternion", frame=frame)
            pb.keyframe_insert("location", frame=frame)


# ─── GARMENT ─────────────────────────────────────────────────────────────────
def _ring_mesh(bm, uv, rings, segments, gap=0.0, front_angle=-math.pi / 2, flare=None):
    """Adds a lathed sheet: rings = [(z, rx, ry, (cx, cy))...] top to bottom. gap: an opening (radians)
    centred on front_angle, so the sheet is a C (an open-front cloak). Returns the rows of verts."""
    rows = []
    for k, (z, rx, ry, centre) in enumerate(rings):
        row = []
        g = gap[k] if isinstance(gap, (list, tuple)) else gap   # an opening per ring (a V) or one for all
        for i in range(segments + (1 if gap else 0)):
            if gap:
                a = front_angle + g / 2 + (2 * math.pi - g) * i / segments
            else:
                a = front_angle + 2 * math.pi * i / segments
            row.append(bm.verts.new((centre[0] + rx * math.cos(a), centre[1] + ry * math.sin(a), z)))
        rows.append(row)
    for r in range(len(rows) - 1):
        top, bot = rows[r], rows[r + 1]
        n = len(top)
        for i in range(n if not gap else n - 1):
            j = (i + 1) % n
            f = bm.faces.new((top[i], top[j], bot[j], bot[i]))
            for loop in f.loops:
                v = loop.vert
                ang = math.atan2(v.co.y, v.co.x)
                loop[uv].uv = (ang * 0.35, v.co.z)
    return rows


def _sleeve(bm, uv, rig, side, code, pin, arm_t, segments=20, rings=16):
    """A loose sleeve around one rest arm, from just past the shoulder to a wide cuff at the wrist."""
    up, low = rig.data.bones["upperarm_" + side], rig.data.bones["lowerarm_" + side]
    shoulder, elbow, wrist = up.head_local, up.tail_local, low.tail_local
    start = shoulder.lerp(elbow, 0.5)
    cuff = wrist + (wrist - elbow).normalized() * 0.03
    path = [start, elbow, cuff]
    lengths = [(path[1] - path[0]).length, (path[2] - path[1]).length]
    total = sum(lengths)
    rows = []
    for k in range(rings + 1):
        t = k / rings
        d = t * total
        if d <= lengths[0]:
            p = path[0].lerp(path[1], d / lengths[0])
            axis = (path[1] - path[0]).normalized()
        else:
            p = path[1].lerp(path[2], (d - lengths[0]) / lengths[1])
            axis = (path[2] - path[1]).normalized()
        u = axis.cross(Vector((0, 0, 1)))
        if u.length < 1e-4:
            u = Vector((1, 0, 0))
        u.normalize()
        v_ax = axis.cross(u).normalized()
        r = 0.072 + 0.03 * t ** 1.5                      # widening toward a bell cuff
        row = []
        for i in range(segments):
            a = 2 * math.pi * i / segments
            vert = bm.verts.new(p + (u * math.cos(a) + v_ax * math.sin(a)) * r)
            vert[arm_t] = code + t
            vert[pin] = 1.0 if k == 0 else 0.3
            row.append(vert)
        rows.append(row)
    for k in range(rings):
        for i in range(segments):
            j = (i + 1) % segments
            f = bm.faces.new((rows[k][i], rows[k][j], rows[k + 1][j], rows[k + 1][i]))
            for loop in f.loops:
                loop[uv].uv = ((i + (1 if loop.vert in (rows[k][j], rows[k + 1][j]) else 0)) / segments * 0.5,
                               k / rings * total)


def make_garment(name, rig, body, mats, cloak_mat, skirt_mat, head_centre, face_y, length=0.33):
    """Skirt + open cloak + hood as one cloth object (around the rest A-pose), pinned at the neck
    and waist. Material 0 = cloak/hood, 1 = skirt."""
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    pin = bm.verts.layers.float.new("pin")
    stiff = bm.verts.layers.float.new("stiff")
    arm_t = bm.verts.layers.float.new("arm")      # 0 = not a sleeve; 1+t right arm, 3+t left arm
    c = (0.0, 0.0)
    hip_z = rig.data.bones["pelvis"].head_local.z
    neck_z = rig.data.bones["neck_01"].head_local.z

    # Under-robe: from the chest (just under the arms) to the floor, wide enough to sit in.
    chest_z = rig.data.bones["spine_03"].tail_local.z - 0.03
    skirt_rings = []
    n_sk = 38
    for k in range(n_sk + 1):
        t = k / n_sk
        z = chest_z + (0.03 - chest_z) * t
        if z > hip_z:                                         # chest to waist: close around the torso
            u = (chest_z - z) / max(0.01, chest_z - hip_z)
            rx, ry = 0.185 - 0.01 * u, 0.165 - 0.01 * u
        else:                                                 # waist to the floor: flaring
            u = (hip_z - z) / hip_z
            rx = 0.2 + 0.2 * u ** 0.8
            ry = rx * 0.85
        skirt_rings.append((z, rx, ry, (0.0, 0.0)))
    skirt_rows = _ring_mesh(bm, uv, skirt_rings, 56)
    # A yoke: the front and back rise to the base of the neck (the sides stay under the arms).
    lift_max = neck_z - 0.05 - chest_z
    for row in skirt_rows:
        z_k = row[0].co.z
        if z_k <= hip_z:
            break
        w = (z_k - hip_z) / (chest_z - hip_z)
        for v in row:
            f = abs(math.sin(math.atan2(v.co.y, v.co.x))) ** 2 * w
            v.co.z += lift_max * f
            v.co.y *= 1 + 0.06 * f                 # a little room over the chest, never inside it
    for v in skirt_rows[0]:
        v[pin] = 1.0
    skirt_top_count = len(skirt_rows[0])        # the robe's first verts (created first) are its top ring
    for side, code in (("r", 1.0), ("l", 3.0)):
        _sleeve(bm, uv, rig, side, code, pin, arm_t)
    skirt_faces = set(bm.faces)

    # Cloak: from a close collar, flaring out over the A-posed arms, open at the front.
    # It only has to clear the A-posed arms (hands at about neck - 0.45); below that it slims down.
    prof = [(neck_z - 0.01, 0.085, 0.08), (neck_z - 0.05, 0.15, 0.12), (neck_z - 0.1, 0.25, 0.17),
            (neck_z - 0.18, 0.37, 0.22), (neck_z - 0.3, 0.5, 0.3), (neck_z - 0.45, 0.6, 0.35),
            (neck_z - 0.6, 0.52, 0.34)]
    bottom = length
    z0 = prof[-1][0]
    steps = 14
    for k in range(1, steps + 1):
        prof.append((z0 + (bottom - z0) * k / steps, 0.52 - 0.06 * k / steps, 0.34))
    # A V-shaped opening: narrow at the collar, wide from the hands down, so the arms come out through it.
    gaps = []
    for z, _, _ in prof:
        t = max(0.0, min(1.0, (neck_z - 0.02 - z) / 0.5))
        gaps.append(math.radians(26 + 84 * t ** 0.8))
    cloak_rows = _ring_mesh(bm, uv, [(z, rx, ry, c) for z, rx, ry in prof], 72, gap=gaps)
    for v in cloak_rows[0]:
        v[pin] = 1.0
    for v in cloak_rows[1]:
        v[pin] = 0.6

    # Hood: a deep dome around the head, joined (welded later) to the collar, with an oval face
    # opening in front and a slight peak at the back of the crown.
    hx, hy, hz = head_centre
    hood_rows = []
    n_ang = 40
    half_open = math.radians(40)                             # the face opening, either side of the front
    # (z, radius, dome angle) per row: straight sides from the collar up to the head's middle, then a dome.
    levels = [(neck_z - 0.03 + (hz - neck_z + 0.03) * k / 6, 0.157, 0.0) for k in range(6)]
    levels += [(hz + 0.17 * math.sin(p), 0.145 * math.cos(p) + 0.012, p)
               for p in (math.pi / 2 * k / 11 for k in range(11))]
    n_up = len(levels) - 1
    for z_l, r_h, phi in levels:
        row = []
        for i in range(n_ang + 1):
            a = -math.pi / 2 + half_open + (2 * math.pi - 2 * half_open) * i / n_ang
            x = hx + r_h * math.cos(a)
            y = hy + 0.02 + r_h * 1.12 * math.sin(a)
            z = z_l
            back = max(0.0, math.sin(a))                     # 1 at the back of the head
            z += 0.035 * back * math.sin(phi) ** 4           # the peak
            y += 0.03 * back * math.sin(phi)
            row.append(bm.verts.new((x, y, z)))
        hood_rows.append(row)
    # Close the crown.
    top = bm.verts.new((hx, hy + 0.05, hz + 0.175))
    for r in range(n_up):
        for i in range(n_ang):
            f = bm.faces.new((hood_rows[r][i], hood_rows[r][i + 1], hood_rows[r + 1][i + 1], hood_rows[r + 1][i]))
            for loop in f.loops:
                loop[uv].uv = (i / n_ang * 0.9, r / n_up * 0.5 + 2.0)
    for i in range(n_ang):
        bm.faces.new((hood_rows[-1][i], hood_rows[-1][i + 1], top))
    for row in hood_rows:
        for v in row:
            v[stiff] = 1.0
    for v in hood_rows[0]:
        v[pin] = 0.9
    for k, row in enumerate(hood_rows):
        for v in row:
            # The upper hood follows the head firmly (so it stays up when the head moves); the sides drape.
            v[pin] = max(v[pin], 0.35 + 0.4 * k / n_up)
    top[pin], top[stiff] = 0.8, 1.0

    for f in bm.faces:
        f.material_index = 1 if f in skirt_faces else 0
        f.smooth = True
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(obj)
    me.materials.append(cloak_mat)
    me.materials.append(skirt_mat)

    # Vertex groups from the layers: pinning, stiffness, and the pin's bone weights.
    pins = obj.vertex_groups.new(name="pin")
    stiffs = obj.vertex_groups.new(name="stiff")
    attr_pin = me.attributes["pin"].data
    attr_stiff = me.attributes["stiff"].data
    for v in me.vertices:
        if attr_pin[v.index].value > 0:
            pins.add([v.index], attr_pin[v.index].value, "REPLACE")
        if attr_stiff[v.index].value > 0:
            stiffs.add([v.index], attr_stiff[v.index].value, "REPLACE")
    bones = {}
    for b in ("pelvis", "spine_03", "neck_01", "head", "clavicle_l", "clavicle_r",
              "upperarm_l", "upperarm_r", "lowerarm_l", "lowerarm_r"):
        bones[b] = obj.vertex_groups.new(name=b)
    attr_arm = me.attributes["arm"].data
    robe_top = {i for i in range(skirt_top_count)}
    for v in me.vertices:
        co = v.co
        a = attr_arm[v.index].value
        if a > 0:
            side = "r" if a < 3 else "l"
            t = a - (1 if a < 3 else 3)                     # 0 at the shoulder end, 1 at the cuff
            w = max(0.0, min(1.0, (t - 0.38) / 0.2))        # blend across the elbow
            bones["upperarm_" + side].add([v.index], 1.0 - w, "REPLACE")
            bones["lowerarm_" + side].add([v.index], w, "REPLACE")
        elif v.index in robe_top:
            bones["spine_03"].add([v.index], 1.0, "REPLACE")
        elif co.z < hip_z + 0.1:
            bones["pelvis"].add([v.index], 1.0, "REPLACE")
        elif co.z > neck_z + 0.04:
            bones["head"].add([v.index], 1.0, "REPLACE")
        elif co.z > neck_z - 0.06:
            bones["neck_01"].add([v.index], 1.0, "REPLACE")
        else:
            side = "l" if co.x > 0 else "r"
            w = min(1.0, abs(co.x) / 0.25)
            bones["clavicle_" + side].add([v.index], w, "REPLACE")
            bones["spine_03"].add([v.index], 1.0 - w, "REPLACE")
    arm = obj.modifiers.new("rig", "ARMATURE")
    arm.object = rig

    cloth = obj.modifiers.new("cloth", "CLOTH")
    st = cloth.settings
    st.quality = 12
    st.mass = 0.35
    st.air_damping = 1.0
    st.tension_stiffness = st.compression_stiffness = 20
    st.shear_stiffness = 8
    st.bending_stiffness = 0.6
    st.vertex_group_mass = "pin"
    st.pin_stiffness = 4.0
    st.vertex_group_bending = "stiff"
    st.bending_stiffness_max = 12.0
    st.vertex_group_structural_stiffness = "stiff"
    st.tension_stiffness_max = st.compression_stiffness_max = 40
    cs = cloth.collision_settings
    cs.distance_min = 0.006
    cs.use_self_collision = True
    cs.self_distance_min = 0.004
    cs.collision_quality = 4
    cloth.point_cache.frame_start, cloth.point_cache.frame_end = 1, SIM_END
    return obj


def add_collider(obj, thickness=0.008):
    col = obj.modifiers.new("collision", "COLLISION")
    obj.collision.thickness_outer = thickness
    obj.collision.cloth_friction = 8.0
    obj.collision.damping = 0.4
    return col


def simulate(scene):
    for f in range(1, SIM_END + 1):
        scene.frame_set(f)


def freeze(obj):
    """Replaces obj's mesh with its evaluated shape (the settled cloth) and drops the modifiers."""
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(obj.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
    obj.modifiers.clear()
    old = obj.data
    obj.data = me
    bpy.data.meshes.remove(old)
    obj.vertex_groups.clear()


def deform_proxy(body):
    """A hidden, triangulated twin of the body (same mesh, same skeleton) for Surface Deform to bind
    to: the body's own posed quads can be concave, which Surface Deform refuses."""
    proxy = body.copy()
    proxy.name = body.name + "_proxy"
    bpy.context.collection.objects.link(proxy)
    for m in [m for m in proxy.modifiers if m.type not in ("ARMATURE", "MASK")]:
        proxy.modifiers.remove(m)
    proxy.modifiers.new("tris", "TRIANGULATE")
    proxy.hide_render = True
    proxy.visible_camera = proxy.visible_shadow = False
    return proxy


def follow_body(obj, body):
    """Binds obj to the body's surface so it moves with the body from now on."""
    mod = obj.modifiers.new("follow", "SURFACE_DEFORM")
    mod.target = deform_proxy(body)
    _update()
    bpy.context.view_layer.objects.active = obj
    with bpy.context.temp_override(object=obj, active_object=obj, selected_objects=[obj], selected_editable_objects=[obj]):
        bpy.ops.object.surfacedeform_bind(modifier=mod.name)
    _update()
    if not mod.is_bound:
        raise RuntimeError("could not bind %s to %s" % (obj.name, body.name))
    thick = obj.modifiers.new("thickness", "SOLIDIFY")
    thick.thickness = 0.004
    thick.offset = 1.0
    sub = obj.modifiers.new("smooth", "SUBSURF")
    sub.levels, sub.render_levels = 0, 1


# ─── THE CHARACTER ───────────────────────────────────────────────────────────
def face_point(body, rig):
    """(head centre, front of the face y, eye height) of the rest body."""
    head = rig.data.bones["head"]
    z0, z1 = head.head_local.z, head.tail_local.z + 0.04
    pts = [c for c in morphed_coords(body) if z0 < c.z < z1 and abs(c.x) < 0.1]
    front = min(p.y for p in pts)
    back = max(p.y for p in pts)
    centre = Vector((0.0, (front + back) / 2, (z0 + z1) / 2))
    return centre, front, z0 + 0.075


def build_character(name, spec, colliders=(), mats=None):
    """
    spec keys: build (MPFB macro values), pose ("seated", "clasped", "offering"), mood, seed,
    cloak (material), skirt (material), table_top / table_near_y / chair_y for "seated".
    colliders: extra collision objects (chair, table). Returns the rig (everything is parented to it).
    """
    mats = mats or default_materials()
    sc = bpy.context.scene
    rig, body = make_body(name, spec.get("build", {}))
    dress_body(body, rig, mats)
    centre, front, eye_z = face_point(body, rig)

    garment = make_garment(name + "_clothes", rig, body, mats, spec["cloak"], spec["skirt"], centre, front,
                           length=spec.get("cloak_length", 0.33))
    add_collider(body)
    for o in colliders:
        add_collider(o, 0.025)         # thick enough that chair finials never poke through the cloak

    # Work out the pose, then animate into it while the cloth falls.
    if spec["pose"] == "seated":
        pose_seated_table(rig, spec["table_top"], spec["table_near_y"], spec["chair_y"], spec.get("seat_z", 0.46))
    else:
        pose_standing(rig, spec["pose"])
    snap = _snapshot(rig)
    _key_pose_move(rig, snap)
    simulate(sc)

    sc.frame_set(SIM_END)
    freeze(garment)
    # Hold the final pose without the dressing animation.
    rig.animation_data.action = None
    _apply(rig, snap, 1.0)
    _update()
    for o in colliders:
        for m in [m for m in o.modifiers if m.type == "COLLISION"]:
            o.modifiers.remove(m)
    for m in [m for m in body.modifiers if m.type == "COLLISION"]:
        body.modifiers.remove(m)
    follow_body(garment, body)
    garment.parent = rig
    garment.matrix_parent_inverse = rig.matrix_world.inverted()

    # The mask, on the head bone, just in front of the face.
    mask = masks.make_mask(name + "_mask", spec.get("mood", "sad"), seed=spec.get("seed", 1))
    _update()
    head_pb = rig.pose.bones["head"]
    rest_to_pose = (rig.matrix_world @ head_pb.matrix) @ (rig.matrix_world @ head_pb.bone.matrix_local).inverted()
    mask.location = rest_to_pose @ Vector((0.0, front - 0.012, eye_z - 0.035))
    mask.rotation_euler = (rest_to_pose.to_quaternion() @ mask.rotation_euler.to_quaternion()).to_euler()
    world = Matrix.LocRotScale(mask.location, mask.rotation_euler.to_quaternion(), mask.scale)
    mask.parent = rig
    mask.parent_type = "BONE"
    mask.parent_bone = "head"
    mask.matrix_world = world

    rig["base_pose"] = {k: [list(q), list(loc)] for k, (q, loc) in snap.items()}
    sc.frame_set(1)
    return rig


def place(rig, pos, facing):
    """Moves a finished character into the scene. facing: Z rotation where 0 faces +Y (the old
    convention); MPFB characters face -Y as built, hence the half turn."""
    rig.location = pos
    rig.rotation_euler = (0, 0, facing + math.pi)


# ─── ANIMATION ───────────────────────────────────────────────────────────────
# The same idle and reactions as the old characters (characters.REACTIONS), mapped onto the
# game-engine skeleton. Offsets are added on top of the saved pose, in each bone's own frame:
# +x bows forward, y turns the head, z tilts it.
IDLE_FRAMES = 48
REACTION_FRAMES = 48
OFFSET_BONES = {"spine": ("spine_01", "spine_02"), "chest": ("spine_03",), "neck": ("neck_01",), "head": ("head",)}


def _base(rig):
    return {k: Quaternion(list(v[0])) for k, v in rig["base_pose"].items()}


def _idle_offsets(f, seed, energy):
    t = 2 * math.pi * f / IDLE_FRAMES
    ph = seed * 0.7
    d = math.radians
    return {
        "spine": [0.0, d(0.8 * energy) * math.sin(t + ph), 0.0],
        "chest": [d(1.6 * energy) * math.sin(t), 0.0, 0.0],
        "neck": [0.0, 0.0, 0.0],
        "head": [d(2.5 * energy) * math.sin(t + 1.0 + ph), d(3.0 * energy) * math.sin(t + ph),
                 d(2.0 * energy) * math.sin(t + 2.0)],
    }


def _pose_offsets(rig, base, offsets, breath):
    for logical, (x, y, z) in offsets.items():
        bones = OFFSET_BONES[logical]
        share = 1.0 / len(bones)
        for b in bones:
            rig.pose.bones[b].rotation_quaternion = base[b] @ Euler((x * share, y * share, z * share)).to_quaternion()
    rig.pose.bones["spine_03"].scale = (1 + 0.012 * breath,) * 3


def _assign(rig, action):
    ad = rig.animation_data or rig.animation_data_create()
    ad.action = action
    if hasattr(ad, "action_slot") and getattr(action, "slots", None) and len(action.slots):
        ad.action_slot = action.slots[0]


def bake(rig, action_name, frames, extra_fn=None, step=2, cyclic=False):
    """Keys the idle (plus extra_fn(u) offsets, u = 0..1) into a new action and assigns it."""
    action = bpy.data.actions.new(action_name)
    _assign(rig, action)
    base = _base(rig)
    seed, energy = rig["seed"], rig["energy"]
    keyed = [b for bones in OFFSET_BONES.values() for b in bones]
    for f in range(0, frames + 1, step):
        offs = _idle_offsets(f % IDLE_FRAMES, seed, energy)
        if extra_fn:
            for k, v in extra_fn(f / frames).items():
                offs[k] = [a + b for a, b in zip(offs[k], v)]
        _pose_offsets(rig, base, offs, (1 + math.sin(2 * math.pi * (f % IDLE_FRAMES) / IDLE_FRAMES)) / 2)
        for b in keyed:
            rig.pose.bones[b].keyframe_insert("rotation_quaternion", frame=f + 1)
        rig.pose.bones["spine_03"].keyframe_insert("scale", frame=f + 1)
    if cyclic:
        for fc in _fcurves(action):
            fc.modifiers.new("CYCLES")
    return action


def _fcurves(action):
    if hasattr(action, "fcurves") and action.fcurves:
        return list(action.fcurves)
    curves = []
    for layer in getattr(action, "layers", []):
        for strip in layer.strips:
            for bag in getattr(strip, "channelbags", []):
                curves.extend(bag.fcurves)
    return curves


def animate(rig, seed, energy=1.0):
    """Gives the rig its breathing idle (a seamless 48-frame loop)."""
    rig["seed"], rig["energy"] = seed, energy
    rig["idle_action"] = bake(rig, rig.name + "_idle", IDLE_FRAMES, step=4, cyclic=True).name


def react(rig, name):
    """Switches to a reaction (characters.REACTIONS; made once, then reused). None = back to the idle."""
    import characters
    if name is None:
        _assign(rig, bpy.data.actions[rig["idle_action"]])
        return
    key = "%s_%s" % (rig.name, name)
    scale = rig.get("react_scale", 0.75)   # life-size bodies need smaller moves than the old figures
    fn = characters.REACTIONS[name]
    action = bpy.data.actions.get(key) or bake(rig, key, REACTION_FRAMES,
                                               lambda u: {k: [c * scale for c in v] for k, v in fn(u).items()})
    _assign(rig, action)


def _signed_angle(u, v, normal):
    a = u.angle(v)
    return -a if u.cross(v).dot(normal) < 0 else a


def _pole_angle(rig, side):
    """The IK pole angle that keeps the arm bending the way it does at rest (from the rest bones)."""
    up, lo = rig.data.bones["upperarm_" + side], rig.data.bones["lowerarm_" + side]
    s, e, w = up.head_local, up.tail_local, lo.tail_local
    line = w - s
    bend = e - (s + line * ((e - s).dot(line) / line.length_squared))
    pole = e + bend.normalized() * 0.4
    projected = (w - s).cross(pole - s).cross(e - s)
    x_axis = up.matrix_local.to_3x3() @ Vector((1, 0, 0))
    return _signed_angle(x_axis, projected, e - s)


def plant_hands(rig):
    """Seated characters: IK keeps each hand where it rests (on the table) while the body moves.
    The targets ("<rig>_hand_l/r") are empties parented to the rig, so they can be animated (a gesture)."""
    _update()
    for side in ("l", "r"):
        upper, lower = rig.pose.bones["upperarm_" + side], rig.pose.bones["lowerarm_" + side]
        s, e, w = upper.head.copy(), lower.head.copy(), lower.tail.copy()
        line = w - s
        bend = e - (s + line * ((e - s).dot(line) / line.length_squared))
        target = bpy.data.objects.new("%s_hand_%s" % (rig.name, side), None)
        pole = bpy.data.objects.new("%s_elbow_%s" % (rig.name, side), None)
        for o, loc in ((target, w), (pole, e + bend.normalized() * 0.4)):
            bpy.context.collection.objects.link(o)
            o.empty_display_size = 0.03
            o.parent = rig
            o.location = loc
        ik = lower.constraints.new("IK")
        ik.target, ik.pole_target = target, pole
        ik.chain_count = 2
        # Pick the pole angle that puts the elbow back exactly where the pose had it.
        def err(a):
            ik.pole_angle = a
            _update()
            return (lower.head - e).length
        best = min((math.radians(d) for d in range(-180, 180, 10)), key=err)
        best = min((best + math.radians(d) for d in range(-10, 11)), key=err)
        ik.pole_angle = best
        print("IK %s: pole angle %.0f deg, elbow off by %.1f mm" % (side, math.degrees(best), err(best) * 1000))
    _update()
