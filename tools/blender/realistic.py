"""
Realistic masked characters for Ashes.

Each character is a real person from MPFB 2 (the MakeHuman add-on for Blender; CC0 assets):
a body with photographed skin, eyes, eyebrows, lashes and hair, on its game-engine skeleton.
They wear a theatre mask strapped over the face (you see their eyes through its holes, and
their ears, jaw, neck, hair and hands around it) and clothes that are simulated as cloth:

  - a dark knitted undershirt (painted onto the body) and, on some, black leather gloves
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
the MakeHuman system assets (skins, hair, eyes; CC0) unzipped into MPFB's user data folder
(https://files.makehumancommunity.org/asset_packs/makehuman_system_assets/makehuman_system_assets_cc0.zip),
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
MASK_SCALE = 0.9      # the theatre masks are made a little large; this fits them to a real face
CHAIR_WIDEN = 1.3     # the opponent's chair is made this much wider (scenes and tests apply it)


def _mpfb():
    addon_utils.enable("bl_ext.blender_org.mpfb", default_set=True)
    from bl_ext.blender_org.mpfb.services.humanservice import HumanService
    from bl_ext.blender_org.mpfb.services.targetservice import TargetService
    return HumanService, TargetService


def _update():
    bpy.context.view_layer.update()


# ─── MATERIALS ───────────────────────────────────────────────────────────────
def fabric(name, tex_id, tint=(1, 1, 1), scale=3.0, sheen=0.04, bump=1.0, rough_boost=0.0, worn=True):
    """A Poly Haven fabric (colour, roughness, normal) tinted by multiplying, tiled every 1/scale metres
    of UV (garments here have UVs in metres). worn: uneven fading across the cloth, dust gathering
    toward the floor, and fine fibre bumps, so it never looks like one flat colour."""
    folder = os.path.join(kit.ASSETS, "textures", tex_id)
    m = bpy.data.materials.new(name)
    m.use_fake_user = True   # MPFB clears out unused materials when it applies a skin
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
    colour_out = tint_mix.outputs[2]
    if worn:
        obj_co = uv.outputs["Object"]
        # Uneven fading: broad patches a little lighter or darker.
        patches = N.new("ShaderNodeTexNoise")
        patches.inputs["Scale"].default_value = 2.2
        patches.inputs["Detail"].default_value = 5.0
        L.new(obj_co, patches.inputs["Vector"])
        shade = N.new("ShaderNodeMapRange")
        shade.inputs[1].default_value, shade.inputs[2].default_value = 0.3, 0.7
        shade.inputs[3].default_value, shade.inputs[4].default_value = 0.6, 1.25
        L.new(patches.outputs["Fac"], shade.inputs[0])
        faded = N.new("ShaderNodeMix")
        faded.data_type, faded.blend_type = "RGBA", "MULTIPLY"
        faded.inputs[0].default_value = 1.0
        L.new(colour_out, faded.inputs[6])
        L.new(shade.outputs[0], faded.inputs[7])
        # Dust: the hem (near the floor) picks up pale grey dirt, in streaks.
        height = N.new("ShaderNodeSeparateXYZ")
        L.new(N.new("ShaderNodeNewGeometry").outputs["Position"], height.inputs[0])
        low = N.new("ShaderNodeMapRange")
        low.inputs[1].default_value, low.inputs[2].default_value = 0.0, 0.45
        low.inputs[3].default_value, low.inputs[4].default_value = 0.55, 0.0
        L.new(height.outputs["Z"], low.inputs[0])
        streaks = N.new("ShaderNodeTexNoise")
        streaks.inputs["Scale"].default_value = 9.0
        streaks.inputs["Detail"].default_value = 6.0
        L.new(obj_co, streaks.inputs["Vector"])
        dust_amount = N.new("ShaderNodeMath")
        dust_amount.operation = "MULTIPLY"
        dust_amount.use_clamp = True
        L.new(low.outputs[0], dust_amount.inputs[0])
        L.new(streaks.outputs["Fac"], dust_amount.inputs[1])
        dusty = N.new("ShaderNodeMix")
        dusty.data_type = "RGBA"
        L.new(dust_amount.outputs[0], dusty.inputs[0])
        L.new(faded.outputs[2], dusty.inputs[6])
        dusty.inputs[7].default_value = (0.2, 0.17, 0.14, 1)
        colour_out = dusty.outputs[2]
    L.new(colour_out, bsdf.inputs["Base Color"])
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
    normal_out = nmap.outputs["Normal"]
    if worn:
        fibres = N.new("ShaderNodeTexNoise")
        fibres.inputs["Scale"].default_value = 140.0
        fibres.inputs["Detail"].default_value = 3.0
        L.new(uv.outputs["Object"], fibres.inputs["Vector"])
        fuzz = N.new("ShaderNodeBump")
        fuzz.inputs["Strength"].default_value = 0.12
        fuzz.inputs["Distance"].default_value = 0.002
        L.new(fibres.outputs["Fac"], fuzz.inputs["Height"])
        L.new(normal_out, fuzz.inputs["Normal"])
        normal_out = fuzz.outputs["Normal"]
    L.new(normal_out, bsdf.inputs["Normal"])
    if sheen:
        bsdf.inputs["Sheen Weight"].default_value = sheen
        bsdf.inputs["Sheen Roughness"].default_value = 0.6
        bsdf.inputs["Sheen Tint"].default_value = (*[min(1.0, c * 3) for c in tint], 1)
    return m


def default_materials():
    """Shared materials: knitted tunic, leather gloves and the void inside the hood."""
    return {
        "tunic": fabric("tunic", "knitted_fleece", (0.07, 0.064, 0.058), scale=7.0, sheen=0.03, bump=1.6, worn=False),
        "glove": fabric("glove", "fabric_leather_02", (0.06, 0.05, 0.045), scale=8.0, sheen=0.0, rough_boost=0.08,
                        worn=False),
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


def humanise(body, skin="middleage_caucasian_male", hair="short02", brows="eyebrow001"):
    """Photographed skin, eyes, eyebrows, lashes and (unless hair is None) hair from the MakeHuman
    system assets. Returns the hair object (or None)."""
    HS, _ = _mpfb()
    from bl_ext.blender_org.mpfb.services.locationservice import LocationService
    data = LocationService.get_user_data()
    if not os.path.isdir(os.path.join(data, "skins")):
        raise SystemExit("MakeHuman system assets are missing from " + data + " (see the top of realistic.py).")

    def asset(kind, item, asset_type):
        return HS.add_mhclo_asset(os.path.join(data, kind, item, item + ".mhclo"), body, asset_type=asset_type,
                                  subdiv_levels=0, material_type="MAKESKIN")
    asset("eyes", "high-poly", "Eyes")
    asset("eyebrows", brows, "Eyebrows")
    asset("eyelashes", "eyelashes01", "Eyelashes")
    hair_obj = asset("hair", hair, "Hair") if hair else None
    HS.set_character_skin(os.path.join(data, "skins", skin, skin + ".mhmat"), body, skin_type="ENHANCED_SSS")
    return hair_obj


def dress_body(body, rig, mats, gloves=True):
    """Keeps the skin on the head, neck and (without gloves) the hands; everything else gets a
    knitted undershirt, and gloved hands get leather a little past the wrists."""
    body.data.materials.append(mats["tunic"])
    body.data.materials.append(mats["glove"])
    tunic_i, glove_i = len(body.data.materials) - 2, len(body.data.materials) - 1
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
        hidden.append(w_head > 0.5 or coords[v.index].z > neck_z - 0.035)   # skin: head and neck
        cuff = side is not None and w_fore > 0.2 and (coords[v.index] - wrists[side]).length < 0.075
        glove.append(w_hand > 0.5 or (gloves and cuff))
    for p in body.data.polygons:
        if sum(glove[i] for i in p.vertices) * 2 > len(p.vertices):
            if gloves:
                p.material_index = glove_i       # (bare hands keep their skin)
        elif sum(hidden[i] for i in p.vertices) * 2 <= len(p.vertices):
            p.material_index = tunic_i
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
        reach(rig, "upperarm_" + s, "lowerarm_" + s, wrist, (0.25 * x, 0.7, -1.0))   # elbows in, by the ribs
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


def _apply(rig, snap, t=1.0, start=None):
    """Poses the rig t of the way from start (default: the rest pose) to snap."""
    for pb in rig.pose.bones:
        q, loc = snap[pb.name]
        q0, loc0 = start[pb.name] if start else (Quaternion(), Vector((0, 0, 0)))
        pb.rotation_quaternion = q0.slerp(q, t)
        pb.location = loc0.lerp(loc, t)


def _reset(rig):
    for pb in rig.pose.bones:
        pb.rotation_quaternion = Quaternion()
        pb.location = (0, 0, 0)
    _update()


def pose_arms_down(rig):
    """Arms hanging by the sides: the pose the clothes are put on in (so the cloak can be narrow)."""
    for s, x in (("r", -1), ("l", 1)):
        aim(rig, "upperarm_" + s, (0.1 * x, 0.02, -1))
        aim(rig, "lowerarm_" + s, (0.04 * x, -0.12, -1))


def chair_proxy(chair_y, seat_z, half_width=0.36):
    """Simple stand-ins for a chair in the cloth simulation: a seat and a back, no arm rails
    (cloth piling up on arm rails makes huge square shoulders). Returns the two objects."""
    seat = kit.box((0, chair_y, seat_z - 0.03), (2 * half_width, 0.6, 0.06), None)
    back = kit.box((0, chair_y + 0.33, seat_z + 0.6), (2 * half_width, 0.05, 1.3), None)
    for o in (seat, back):
        o.data.materials.clear()
        o.hide_render = True
    return [seat, back]


def _key_pose_move(rig, snap, start):
    """Animates start (frame 1..8) -> pose (SIM_POSE_FRAME) and holds it."""
    rig.animation_data_create()
    rig.animation_data.action = bpy.data.actions.new(rig.name + "_dress")
    for frame, t in ((1, 0.0), (8, 0.0), (SIM_POSE_FRAME, 1.0), (SIM_END + 5, 1.0)):
        _apply(rig, snap, t, start)
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


def _sleeve(bm, uv, rig, side, code, pin, arm_t, segments=28, rings=24):
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


def make_garment(name, rig, body, mats, cloak_mat, skirt_mat, head_centre, face_y, length=0.33, hood="up"):
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
    n_sk = 60
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
    skirt_rows = _ring_mesh(bm, uv, skirt_rings, 96)
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

    # Cloak: from a close collar, over the shoulders and the arms (hanging down as it is put on),
    # open at the front.
    prof = [(neck_z - 0.01, 0.085, 0.08), (neck_z - 0.05, 0.15, 0.12), (neck_z - 0.1, 0.24, 0.17),
            (neck_z - 0.18, 0.31, 0.21), (neck_z - 0.3, 0.35, 0.25), (neck_z - 0.45, 0.39, 0.28),
            (neck_z - 0.6, 0.42, 0.3)]
    bottom = length
    z0 = prof[-1][0]
    steps = 14
    for k in range(1, steps + 1):
        prof.append((z0 + (bottom - z0) * k / steps, 0.42 + 0.04 * k / steps, 0.3 + 0.03 * k / steps))
    # Fine rings (every 2.5 cm) so the cloth can form real folds.
    fine = []
    z = prof[0][0]
    while z > bottom:
        k = max(i for i in range(len(prof) - 1) if prof[i][0] >= z)
        (za, xa, ya), (zb, xb, yb) = prof[k], prof[k + 1]
        t = (za - z) / (za - zb)
        fine.append((z, xa + (xb - xa) * t, ya + (yb - ya) * t))
        z -= 0.025
    prof = fine + [prof[-1]]
    # A V-shaped opening: narrow at the collar, wide from the hands down, so the arms come out through it.
    gaps = []
    for z, _, _ in prof:
        t = max(0.0, min(1.0, (neck_z - 0.2 - z) / 0.35))   # closed over the chest, opening below it
        gaps.append(math.radians(16 + 94 * t ** 0.8))
    cloak_rows = _ring_mesh(bm, uv, [(z, rx, ry, c) for z, rx, ry in prof], 112, gap=gaps)
    for row in cloak_rows:                       # the collar holds on to the neck
        z_row = row[0].co.z
        hold = 1.0 if z_row > neck_z - 0.03 else 0.6 if z_row > neck_z - 0.065 else 0.0
        for v in row:
            v[pin] = hold

    # Hood: a deep dome around the head, joined (welded later) to the collar, with an oval face
    # opening in front and a slight peak at the back of the crown.
    # hood: "up" (deep, close around the face), "back" (pushed back: forehead, ears and jaw show)
    # or "down" (no hood at all: just the cloak's collar).
    hx, hy, hz = head_centre
    if hood == "back":
        hy += 0.035
    hood_rows = []
    n_ang = 56
    half_open = math.radians(68 if hood == "back" else 44)   # the face opening, either side of the front
    # (z, radius, dome angle) per row: straight sides from the collar up to the head's middle, then a dome.
    levels = [(neck_z - 0.03 + (hz - neck_z + 0.03) * k / 9, 0.157, 0.0) for k in range(9)]
    levels += [(hz + 0.17 * math.sin(p), 0.145 * math.cos(p) + 0.012, p)
               for p in (math.pi / 2 * k / 16 for k in range(16))]
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
    if hood == "down":
        for row in hood_rows:
            for v in row:
                bm.verts.remove(v)
        hood_rows = []
        n_up = 0
    # Close the crown.
    top = bm.verts.new((hx, hy + 0.05, hz + 0.175))
    for r in range(n_up):
        for i in range(n_ang):
            f = bm.faces.new((hood_rows[r][i], hood_rows[r][i + 1], hood_rows[r + 1][i + 1], hood_rows[r + 1][i]))
            for loop in f.loops:      # metres around the head and up it, like the cloak
                co = loop.vert.co
                loop[uv].uv = (math.atan2(co.y - hy, co.x - hx) * 0.16, co.z)
    if hood_rows:
        for i in range(n_ang):
            bm.faces.new((hood_rows[-1][i], hood_rows[-1][i + 1], top))
    else:
        bm.verts.remove(top)
        top = None
    for row in hood_rows:
        for v in row:
            v[stiff] = 1.0
    for v in (hood_rows[0] if hood_rows else []):
        v[pin] = 0.9
    for k, row in enumerate(hood_rows):
        for v in row:
            # The upper hood follows the head firmly (so it stays up when the head moves); the sides drape.
            v[pin] = max(v[pin], 0.35 + 0.4 * k / n_up)
    if top:
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
    st.quality = 10
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
    thick.thickness = 0.005
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
    # The real eye height, from the body's eye-joint markers.
    marks = [g.index for g in body.vertex_groups if g.name in ("joint-l-eye", "joint-r-eye")]
    coords = morphed_coords(body)
    eyes = [coords[v.index] for v in body.data.vertices if any(e.group in marks for e in v.groups)]
    eye_z = sum(c.z for c in eyes) / len(eyes) if eyes else z0 + 0.075
    return centre, front, eye_z


def mask_straps(name, centre, front, back_y, eye_z, mat, clearance):
    """Two leather straps from the mask's edges round the back of the head (built in the rest frame)."""
    bm = bmesh.new()
    cy = (front + back_y) / 2 + 0.012
    rx, ry = 0.079 + clearance, (back_y - front) / 2 - 0.006 + clearance
    for z_mid, tilt in ((eye_z + 0.018, 0.02), (eye_z - 0.05, -0.035)):
        rows = []
        for i in range(41):
            a = math.radians(-28) + math.radians(236) * i / 40          # from one temple, round the back
            x, y = rx * math.cos(a), cy + ry * math.sin(a)
            z = z_mid + tilt * max(0.0, math.sin(a))                    # the straps spread apart at the back
            out = Vector((math.cos(a) / rx, math.sin(a) / ry, 0)).normalized() * 0.0025
            rows.append([bm.verts.new((x + o.x, y + o.y, z + dz)) for o in (-out, out) for dz in (-0.006, 0.006)])
        for a_row, b_row in zip(rows, rows[1:]):
            for i, j in ((0, 1), (1, 3), (3, 2), (2, 0)):
                bm.faces.new((a_row[i], a_row[j], b_row[j], b_row[i]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(obj)
    me.materials.append(mat)
    return obj


def build_character(name, spec, colliders=(), mats=None):
    """
    spec keys: build (MPFB macro values), pose ("seated", "clasped", "offering"), mood, seed,
    cloak (material), skirt (material), table_top / table_near_y / chair_y for "seated".
    colliders: extra collision objects (chair, table). Returns the rig (everything is parented to it).
    """
    mats = mats or default_materials()
    sc = bpy.context.scene
    rig, body = make_body(name, spec.get("build", {}))
    hair = humanise(body, spec.get("skin", "middleage_caucasian_male"), spec.get("hair", "short02"),
                    spec.get("brows", "eyebrow001"))
    dress_body(body, rig, mats, gloves=spec.get("gloves", True))
    centre, front, eye_z = face_point(body, rig)
    hood = spec.get("hood", "up")

    garment = make_garment(name + "_clothes", rig, body, mats, spec["cloak"], spec["skirt"], centre, front,
                           length=spec.get("cloak_length", 0.33), hood=hood)
    add_collider(body)
    if hair and hood != "down":
        add_collider(hair, 0.004)      # the hood rests on the hair, not through it
    for o in colliders:
        add_collider(o, 0.012)

    # Work out the pose, then animate into it (from arms hanging down) while the cloth falls.
    pose_arms_down(rig)
    start = _snapshot(rig)
    _reset(rig)
    if spec["pose"] == "seated":
        pose_seated_table(rig, spec["table_top"], spec["table_near_y"], spec["chair_y"], spec.get("seat_z", 0.46))
    else:
        pose_standing(rig, spec["pose"])
    snap = _snapshot(rig)
    _key_pose_move(rig, snap, start)
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
    for o in (body, hair):
        for m in [m for m in (o.modifiers if o else []) if m.type == "COLLISION"]:
            o.modifiers.remove(m)
    follow_body(garment, body)
    garment.parent = rig
    garment.matrix_parent_inverse = rig.matrix_world.inverted()

    # The mask, worn: its rim sits back by the cheeks so the dome covers the face and the nose,
    # its eye holes line up with the real eyes, and two straps hold it round the back of the head.
    mask = masks.make_mask(name + "_mask", spec.get("mood", "sad"), seed=spec.get("seed", 1))
    mask.scale = tuple(c * MASK_SCALE for c in mask.scale)
    _update()
    head_pb = rig.pose.bones["head"]
    rest_to_pose = (rig.matrix_world @ head_pb.matrix) @ (rig.matrix_world @ head_pb.bone.matrix_local).inverted()
    mask.location = rest_to_pose @ Vector((0.0, front + spec.get("mask_depth", 0.04),
                                           eye_z - masks.EYE_Y * 0.095 * MASK_SCALE))
    mask.rotation_euler = (rest_to_pose.to_quaternion() @ mask.rotation_euler.to_quaternion()).to_euler()
    world = Matrix.LocRotScale(mask.location, mask.rotation_euler.to_quaternion(), mask.scale)
    mask.parent = rig
    mask.parent_type = "BONE"
    mask.parent_bone = "head"
    mask.matrix_world = world
    back_y = 2 * centre.y - front
    straps = mask_straps(name + "_straps", centre, front, back_y, eye_z, mats["glove"], 0.012 if hair else 0.002)
    straps.matrix_world = rest_to_pose
    world = straps.matrix_world.copy()
    straps.parent = rig
    straps.parent_type = "BONE"
    straps.parent_bone = "head"
    straps.matrix_world = world

    rig["base_pose"] = {k: [list(q), list(loc)] for k, (q, loc) in snap.items()}
    sc.frame_set(1)
    return rig


def place(rig, pos, facing):
    """Moves a finished character into the scene. facing: Z rotation where 0 faces +Y (the old
    convention); MPFB characters face -Y as built, hence the half turn."""
    rig.location = pos
    rig.rotation_euler = (0, 0, facing + math.pi)


# ─── ANIMATION ───────────────────────────────────────────────────────────────
# An idle loop and reactions (the moves in characters.REACTIONS) on the game-engine skeleton.
# Offsets are added on top of the saved pose, in each bone's own frame: +x bows forward,
# y turns, z tilts sideways.
#
# The idle is one slow breath (4 seconds) with slower and faster drifts layered on it, all whole
# numbers of cycles per loop so it repeats without a seam. Reactions are the idle plus a move that
# starts and ends at rest; the head follows the chest a beat late, as a real body does.
IDLE_FRAMES = 96
REACTION_FRAMES = 48
LOOPED_REACTIONS = {"think"}   # these are looped by the game, so they last one whole idle loop


def reaction_frames(name):
    return IDLE_FRAMES if name in LOOPED_REACTIONS else REACTION_FRAMES
OFFSET_BONES = {"spine": ("spine_01", "spine_02"), "chest": ("spine_03",), "neck": ("neck_01",), "head": ("head",)}


def _base(rig):
    return {k: Quaternion(list(v[0])) for k, v in rig["base_pose"].items()}


def _idle_offsets(f, seed, energy):
    t = 2 * math.pi * f / IDLE_FRAMES
    ph = seed * 0.7
    d = math.radians
    e = energy
    s = math.sin
    return {
        # A slow shift of weight: a slight turn and a slight lean to one side.
        "spine": [0.0, d(0.6 * e) * s(t + ph), d(0.5 * e) * s(t + ph + 1.3)],
        # Breathing: the chest rocks back as it fills...
        "chest": [d(-1.1 * e) * s(t), 0.0, 0.0],
        # ...and the neck gives a little the other way, so the head stays level.
        "neck": [d(0.5 * e) * s(t), 0.0, 0.0],
        # The head drifts: a slow nod and turn with smaller, quicker wanderings on top.
        "head": [d(1.1 * e) * s(t + 1.0 + ph) + d(0.5 * e) * s(2 * t + 2 * ph),
                 d(1.8 * e) * s(t + ph) + d(0.7 * e) * s(3 * t + ph),
                 d(0.9 * e) * s(2 * t + 2.0 + ph)],
    }


def _pose_offsets(rig, base, offsets, breath):
    for logical, (x, y, z) in offsets.items():
        bones = OFFSET_BONES[logical]
        share = 1.0 / len(bones)
        for b in bones:
            rig.pose.bones[b].rotation_quaternion = base[b] @ Euler((x * share, y * share, z * share)).to_quaternion()
    rig.pose.bones["spine_03"].scale = (1 + 0.014 * breath,) * 3


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
            u = f / frames
            late = u ** 1.18                      # the head and neck trail the body slightly
            lead, lag = extra_fn(u), extra_fn(late)
            for k in lead:
                v = lag[k] if k in ("head", "neck") else lead[k]
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
    """Gives the rig its breathing idle (a seamless loop of IDLE_FRAMES)."""
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
    action = bpy.data.actions.get(key) or bake(rig, key, reaction_frames(name),
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
