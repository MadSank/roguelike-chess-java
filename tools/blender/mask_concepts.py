"""
Concept sheet: four original mask designs, each worn by a different person, built entirely in code
(no downloads beyond what the characters already use).

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\mask_concepts.py -- <out folder>

  1. Plague doctor: black leather, a long beak, round brass-rimmed eye glasses, stitched seams
  2. Horned: worn red lacquer, heavy brow, two curved horns, a snarling slit of a mouth
  3. Porcelain half-mask: glossy white, ends above the mouth, cracks filled with gold
  4. Crowned: tarnished gold with a crown of rays, calm and blank
"""
import bmesh
import bpy
import math
import os
import random
import sys

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

H = 1.32
EYE_X, EYE_Y = 0.36, 0.24
UNIT = 0.095 * realistic.MASK_SCALE
EYE_LEVEL = 1.6


def g(d, s):
    return math.exp(-(d / s) ** 2)


def base_dome(x, y):
    r = x * x + (y / H) ** 2 * 0.85
    return 0.55 * math.sqrt(max(0.0, 1.0 - r))


# ─── SHAPES: how far each mask's surface sticks out at (x, y) ────────────────
def h_plague(x, y):
    z = base_dome(x, y)
    for sx in (-1, 1):                                    # raised rims round the eye glasses
        d = math.hypot(x - sx * EYE_X, y - EYE_Y)
        z += 0.07 * g(d - 0.2, 0.05)
    if -0.95 < y < 0.35:                                  # the beak: long, curving down to a point
        t = (0.35 - y) / 1.3
        length = 1.75 * math.sin(min(1.0, t * 1.25) * math.pi / 2) ** 1.4 * (1.0 - max(0.0, t - 0.8) * 4.0)
        z += max(0.0, length) * g(x, 0.3 * (1.0 - 0.72 * t))
    return z


def h_horned(x, y):
    z = base_dome(x, y)
    if abs(x) < 0.8:                                      # a heavy, angry brow
        brow = 0.48 - 0.3 * (0.75 - abs(x))
        z += 0.16 * g(y - brow, 0.09)
    for sx in (-1, 1):
        z -= 0.1 * g(math.hypot((x - sx * EYE_X) / 1.3, y - EYE_Y), 0.15)
        z += 0.14 * g(math.hypot(x - sx * 0.46, y + 0.12), 0.2)      # cheekbones
    if -0.25 < y < 0.42:                                  # a short, flared nose
        t = (0.42 - y) / 0.67
        z += 0.3 * t * g(x, 0.08 + 0.1 * t)
    z += 0.12 * g(math.hypot(x / 1.6, y + 0.95), 0.2)     # a jutting chin
    return z


def h_porcelain(x, y):
    z = base_dome(x, y)
    for sx in (-1, 1):
        z -= 0.06 * g(math.hypot((x - sx * EYE_X) / 1.3, y - EYE_Y), 0.16)
        z += 0.05 * g(math.hypot(x - sx * 0.42, y + 0.1), 0.2)
    if -0.28 < y < 0.45:                                  # a fine, straight nose
        t = (0.45 - y) / 0.73
        z += 0.27 * t * g(x, 0.06 + 0.05 * t)
    if abs(x) < 0.7:
        z += 0.035 * g(y - 0.5, 0.07)
    return z


def h_crowned(x, y):
    z = base_dome(x, y)
    for sx in (-1, 1):
        z -= 0.07 * g(math.hypot((x - sx * EYE_X) / 1.4, y - EYE_Y), 0.15)
    if -0.3 < y < 0.5:
        t = (0.5 - y) / 0.8
        z += 0.24 * t * g(x, 0.065 + 0.05 * t)
    if abs(x) < 0.34:                                     # closed, level lips
        z += 0.04 * g(y + 0.5, 0.045) + 0.035 * g(y + 0.6, 0.05)
        z -= 0.03 * g(y + 0.55, 0.02)
    z += 0.07 * g(math.hypot(x / 1.4, y + 0.95), 0.17)
    for k in range(5):                                    # raised bands across the brow, like a headdress
        z += 0.03 * g(y - (0.72 + 0.11 * k), 0.025) * (1.0 if abs(x) < 0.95 else 0.0)
    return z


def eye_d(x, y, sx, round_eyes):
    ex, ey = x - sx * EYE_X, y - EYE_Y
    if round_eyes:
        return math.hypot(ex, ey) / 0.17
    return math.hypot(ex / 0.19, ey / (0.095 * (1.0 - 0.35 * abs(ex) / 0.19)))


# ─── BUILDING ────────────────────────────────────────────────────────────────
def build_mask(name, height, mat, round_eyes=False, cut=None, res=96, spikes=0):
    """A mask from a height function. cut(x, y) -> True removes that part (e.g. below the nose)."""
    bm = bmesh.new()
    grid = {}
    for j in range(res + 1):
        for i in range(res + 1):
            u = -1.0 + 2.0 * i / res
            w = -1.0 + 2.0 * j / res
            x = u * math.sqrt(1.0 - w * w / 2.0)
            y = H * w * math.sqrt(1.0 - u * u / 2.0) - 0.05
            grid[(i, j)] = bm.verts.new((x, y, height(x, y)))
    for j in range(res):
        for i in range(res):
            vs = [grid[(i, j)], grid[(i + 1, j)], grid[(i + 1, j + 1)], grid[(i, j + 1)]]
            cx = sum(v.co.x for v in vs) / 4
            cy = sum(v.co.y for v in vs) / 4
            if any(eye_d(cx, cy, sx, round_eyes) < 1.0 for sx in (-1, 1)) or (cut and cut(cx, cy)):
                continue
            bm.faces.new(vs)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
    for v in bm.verts:                                    # clean eye-hole edges
        if v.is_boundary:
            for sx in (-1, 1):
                d = eye_d(v.co.x, v.co.y, sx, round_eyes)
                if d < 1.6:
                    cx, cy = sx * EYE_X, EYE_Y
                    v.co.x, v.co.y = cx + (v.co.x - cx) / d, cy + (v.co.y - cy) / d
                    v.co.z = height(v.co.x, v.co.y)
    # A crown of rays: flat blades fanning out from the top rim.
    for k in range(spikes):
        a = math.radians(22 + 136 * k / (spikes - 1))
        long_ray = k % 2 == 0
        half = math.radians(4.2)
        p0 = Vector((math.cos(a - half), H * math.sin(a - half) - 0.05, 0.0))
        p1 = Vector((math.cos(a + half), H * math.sin(a + half) - 0.05, 0.0))
        out = Vector((math.cos(a), H * math.sin(a), 0.0)).normalized()
        tip = (p0 + p1) / 2 + out * (0.72 if long_ray else 0.42) + Vector((0, 0, -0.1))
        mid0, mid1 = p0.lerp(tip, 0.55) + Vector((0, 0, 0.05)), p1.lerp(tip, 0.55) + Vector((0, 0, 0.05))
        v = [bm.verts.new(p) for p in (p0, p1, mid1, mid0, tip)]
        bm.faces.new(v[:4])
        bm.faces.new((v[3], v[2], v[4]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(obj)
    for p in me.polygons:
        p.use_smooth = True
    solid = obj.modifiers.new("thickness", "SOLIDIFY")
    solid.thickness, solid.offset = 0.05, -1
    obj.modifiers.new("smooth", "SUBSURF").levels = 1
    me.materials.append(mat)
    obj.scale = (UNIT, UNIT, UNIT)
    obj.rotation_euler = (math.pi / 2, 0, 0)
    return obj


def horn(name, side, mat):
    """A curved, ridged horn rising from the brow (mask units, joined to the mask's frame)."""
    bm = bmesh.new()
    rows = []
    n = 26
    for k in range(n + 1):
        t = k / n
        a = t * 1.9                                        # sweeps up, out and back forward
        centre = Vector((side * (0.5 + 0.42 * math.sin(a * 0.9)), 0.95 + 0.85 * t + 0.1 * math.sin(a),
                         0.38 - 0.5 * math.sin(a) + 0.42 * t * t))
        r = 0.15 * (1 - t) ** 0.8 + 0.008 + 0.012 * math.sin(t * 40) * (1 - t)   # rings along it
        rows.append([bm.verts.new(centre + Vector((math.cos(b) * r, math.sin(b) * r * 0.9, math.sin(b + 1.0) * r * 0.4)))
                     for b in (2 * math.pi * i / 14 for i in range(14))])
    for a_row, b_row in zip(rows, rows[1:]):
        for i in range(14):
            j = (i + 1) % 14
            bm.faces.new((a_row[i], a_row[j], b_row[j], b_row[i]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(obj)
    for p in me.polygons:
        p.use_smooth = True
    obj.modifiers.new("smooth", "SUBSURF").levels = 1
    me.materials.append(mat)
    return obj


# ─── MATERIALS ───────────────────────────────────────────────────────────────
def material(name, colour, rough, metallic=0.0, coat=0.0, crack_colour=None, crack_metal=0.0, crack_scale=4.0,
             wear_colour=None, seed=1.0):
    """A surface with blotchy variation, grime in the hollows, optional cracks (which can be filled
    with metal) and optional worn patches showing another colour underneath."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N["Principled BSDF"]
    co = N.new("ShaderNodeTexCoord").outputs["Object"]

    def noise(scale, detail=5.0):
        n = N.new("ShaderNodeTexNoise")
        n.inputs["Scale"].default_value, n.inputs["Detail"].default_value = scale, detail
        L.new(co, n.inputs["Vector"])
        return n.outputs["Fac"]

    def ramp(src, lo, hi, a=0.0, b=1.0):
        n = N.new("ShaderNodeMapRange")
        for i, v in zip((1, 2, 3, 4), (lo, hi, a, b)):
            n.inputs[i].default_value = v
        L.new(src, n.inputs[0])
        return n.outputs[0]

    def mix(fac, a, b, blend="MIX"):
        n = N.new("ShaderNodeMix")
        n.data_type, n.blend_type = "RGBA", blend
        for i, v in ((0, fac), (6, a), (7, b)):
            if isinstance(v, (tuple, float)):
                n.inputs[i].default_value = (*v, 1) if isinstance(v, tuple) else v
            else:
                L.new(v, n.inputs[i])
        return n.outputs[2]

    blotch = noise(3.0 + seed, 6.0)
    col = mix(ramp(blotch, 0.3, 0.7), tuple(c * 0.72 for c in colour), colour)
    metal = None
    if wear_colour:
        worn = ramp(noise(7.0 + seed, 8.0), 0.56, 0.66)
        col = mix(worn, col, wear_colour)
    if crack_colour:
        vor = N.new("ShaderNodeTexVoronoi")
        vor.feature = "DISTANCE_TO_EDGE"
        vor.inputs["Scale"].default_value = crack_scale
        L.new(co, vor.inputs["Vector"])
        line = ramp(vor.outputs["Distance"], 0.0, 0.02, 1.0, 0.0)
        where = ramp(noise(1.4 + seed, 2.0), 0.47, 0.58)
        mul = N.new("ShaderNodeMath")
        mul.operation = "MULTIPLY"
        L.new(line, mul.inputs[0])
        L.new(where, mul.inputs[1])
        col = mix(mul.outputs[0], col, crack_colour)
        if crack_metal:
            metal = ramp(mul.outputs[0], 0.0, 1.0, metallic, crack_metal)
            rough_n = ramp(mul.outputs[0], 0.0, 1.0, rough, 0.28)
            L.new(rough_n, bsdf.inputs["Roughness"])
    ao = N.new("ShaderNodeAmbientOcclusion")
    ao.inputs["Distance"].default_value = 0.025
    col = mix(0.8, col, ao.outputs["Color"], "MULTIPLY")
    L.new(col, bsdf.inputs["Base Color"])
    if metal:
        L.new(metal, bsdf.inputs["Metallic"])
    else:
        bsdf.inputs["Metallic"].default_value = metallic
        L.new(ramp(blotch, 0.0, 1.0, rough * 0.8, min(1.0, rough * 1.25)), bsdf.inputs["Roughness"])
    bsdf.inputs["Coat Weight"].default_value = coat
    return m


# ─── THE SHEET ───────────────────────────────────────────────────────────────
def person(name, x, build, skin, hair, mats):
    rig, body = realistic.make_body(name, build)
    hair_obj = realistic.humanise(body, skin, hair)
    realistic.dress_body(body, rig, mats, gloves=False)
    centre, front, eye_z = realistic.face_point(body, rig)
    rig.location.x = x
    rig.location.z = EYE_LEVEL - eye_z                    # everyone's eyes at the same height
    return rig, centre, front, eye_z, hair_obj is not None


def wear(mask, rig, x, centre, front, eye_z, has_hair, leather, extras=()):
    origin = Vector((x, front + 0.04, EYE_LEVEL - EYE_Y * UNIT))
    mask.location = origin
    for e in extras:                                       # things modelled in mask units (horns)
        e.parent = mask
    straps = realistic.mask_straps(mask.name + "_straps", centre, front, 2 * centre.y - front, eye_z, leather,
                                   0.012 if has_hair else 0.002)
    straps.location = (x, 0, EYE_LEVEL - eye_z)


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out = os.path.abspath(args[0]) if args else os.path.join(os.path.dirname(__file__), "concepts_out")
    os.makedirs(out, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    mats = realistic.default_materials()
    leather = mats["glove"]
    brass = kit.plain("brass", (0.55, 0.36, 0.12), rough=0.35, metallic=1.0)
    brass.use_fake_user = True
    glass = kit.plain("glass", (0.01, 0.012, 0.012), rough=0.05, coat=1.0)
    glass.use_fake_user = True

    m_plague = material("plague_leather", (0.02, 0.017, 0.015), 0.55, coat=0.15, crack_colour=(0.004, 0.003, 0.003),
                        crack_scale=9.0, wear_colour=(0.07, 0.05, 0.035), seed=1)
    m_horn_mask = material("red_lacquer", (0.32, 0.02, 0.015), 0.32, coat=0.5, crack_colour=(0.02, 0.01, 0.008),
                           crack_scale=6.0, wear_colour=(0.05, 0.03, 0.02), seed=2)
    m_horn = material("horn", (0.09, 0.07, 0.05), 0.5, wear_colour=(0.3, 0.25, 0.18), seed=3)
    m_porcelain = material("porcelain", (0.82, 0.8, 0.76), 0.12, coat=0.8, crack_colour=(0.83, 0.6, 0.2),
                           crack_metal=1.0, crack_scale=3.2, seed=4)
    m_gold = material("old_gold", (0.72, 0.5, 0.16), 0.38, metallic=1.0, crack_colour=(0.03, 0.025, 0.015),
                      crack_scale=7.0, wear_colour=(0.12, 0.1, 0.05), seed=5)
    for m in (m_plague, m_horn_mask, m_horn, m_porcelain, m_gold):
        m.use_fake_user = True

    xs = (-0.84, -0.28, 0.28, 0.84)
    R = {"asian": 0.0, "caucasian": 1.0, "african": 0.0}
    people = [
        person("p1", xs[0], {"age": 0.9, "height": 0.7, "weight": 0.4, "race": R}, "old_caucasian_male", "short01", mats),
        person("p2", xs[1], {"weight": 0.8, "muscle": 0.8, "race": {"asian": 0, "caucasian": 0, "african": 1.0}},
               "middleage_african_male", None, mats),
        person("p3", xs[2], {"gender": 0.0, "age": 0.5, "weight": 0.4, "race": R}, "young_caucasian_female", "ponytail01", mats),
        person("p4", xs[3], {"age": 0.5, "height": 0.8, "race": {"asian": 1.0, "caucasian": 0, "african": 0}},
               "middleage_asian_male", "short04", mats),
    ]

    plague = build_mask("plague", h_plague, m_plague, round_eyes=True)
    wear(plague, *[people[0][0], xs[0]] + list(people[0][1:]), leather)
    for sx in (-1, 1):                                     # brass rims and dark glass in the eyes
        for kind, mat_, r_minor in (("rim", brass, 0.03), ("glass", glass, None)):
            if kind == "rim":
                bpy.ops.mesh.primitive_torus_add(major_radius=0.185, minor_radius=r_minor, major_segments=40, minor_segments=12)
            else:
                bpy.ops.mesh.primitive_cylinder_add(radius=0.18, depth=0.02, vertices=40)
            o = bpy.context.active_object
            o.data.materials.append(mat_)
            o.parent = plague
            o.location = (sx * EYE_X, EYE_Y, h_plague(sx * (EYE_X + 0.17), EYE_Y) + (0.03 if kind == "rim" else 0.0))
            bpy.ops.object.shade_smooth()

    def mouth_slit(x, y):                                  # a snarling slit, wider at the corners
        return abs(x) < 0.42 and abs(y - (-0.58 + 0.25 * x * x)) < 0.035 + 0.05 * abs(x)
    horned = build_mask("horned", h_horned, m_horn_mask, cut=mouth_slit)
    horns = [horn("horn_%d" % i, sx, m_horn) for i, sx in enumerate((-1, 1))]
    wear(horned, *[people[1][0], xs[1]] + list(people[1][1:]), leather, extras=horns)

    porcelain = build_mask("porcelain", h_porcelain, m_porcelain,
                           cut=lambda x, y: y < -0.36 + 0.1 * math.cos(x * 3.0))   # ends above the mouth
    wear(porcelain, *[people[2][0], xs[2]] + list(people[2][1:]), leather)

    crowned = build_mask("crowned", h_crowned, m_gold, spikes=11)
    wear(crowned, *[people[3][0], xs[3]] + list(people[3][1:]), leather)

    # Stage: dark backdrop, a warm key from the front-left, a cool rim from behind.
    kit.setup_world(sc, haze=0.0)
    kit.setup_cycles(sc, threshold=0.02, max_samples=256)
    sc.render.use_persistent_data = False
    sc.render.resolution_x, sc.render.resolution_y = 1920, 640
    back = kit.plain("backdrop", (0.03, 0.027, 0.025), rough=0.9)
    kit.plane((8, 4), (0, 1.2, 1.5), (math.pi / 2, 0, 0), back)
    head_z = EYE_LEVEL
    kit.light("AREA", (-1.6, -2.2, head_z + 0.9), 420, (1.0, 0.82, 0.62), size=1.2, target=(0, 0, head_z))
    kit.light("AREA", (2.2, -1.2, head_z + 0.3), 90, (0.6, 0.75, 1.0), size=1.5, target=(0, 0, head_z))
    kit.light("AREA", (0.5, 1.0, head_z + 0.8), 160, (1.0, 0.6, 0.35), size=0.8, target=(0, 0, head_z))
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
    bpy.context.collection.objects.link(cam)
    sc.camera = cam
    cam.data.lens = 85
    for label, eye in (("front", Vector((0, -4.9, head_z + 0.02))), ("angle", Vector((3.1, -3.8, head_z + 0.2)))):
        target = Vector((0, 0, head_z - 0.03))
        cam.location = eye
        cam.rotation_euler = (target - eye).to_track_quat("-Z", "Y").to_euler()
        sc.render.filepath = os.path.join(out, "masks_%s.png" % label)
        bpy.ops.render.render(write_still=True)
        print("SHOT", sc.render.filepath)
    print("DONE")


main()
