"""
Masks that real people wear, built to real-world shapes and sizes (used by fit_masks.py):

  plague_doctor   a leather hood with a long beak, round brass goggles and a black wide-brimmed hat
  hockey          an old fibreglass goalie mask: a scuffed white shell with eye holes and breathing holes
  sack            a burlap sack over the whole head, tied with rope at the neck, two torn eye holes,
                  a stitched mouth
  welding         a welder's helmet: a curved dark shield with a small glass window
  bauta           the white Venetian gambler's mask: a blank brow and nose over a jutting, angular jaw
  fencing         a fencing mask: a dark wire-mesh dome over the face with a padded white bib
  balaclava       a black knitted hood over the whole head with holes for the eyes and mouth

Everything is in the wearer's "eye frame": metres, x to the wearer's left... (x is sideways), -y is
forward (out of the face), +z is up, and the origin is on the eye line, 18 mm in front of the nose tip.
Landmarks in that frame: nose tip y = +0.018, eyes y = +0.06 at x = +-0.032, chin z = -0.115,
hairline z = +0.075, head centre about (0, 0.105, 0.0), head half-width 0.078.

Each builder returns a list of objects (the first is the main piece). Leather and burlap use the Poly
Haven photo textures (realistic.fabric); painted or moulded surfaces get a generated, scuffed texture.
"""
import bmesh
import bpy
import math
import random

import numpy as np
from mathutils import Matrix, Vector

import realistic
import scene_kit as kit

HEAD = Vector((0.0, 0.105, 0.0))      # the middle of the wearer's head
EYE_X = 0.032


# ─── Helpers ─────────────────────────────────────────────────────────────────
def _object(name, bm, mat, thickness=0.0, smooth=True, subsurf=0):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    for p in me.polygons:
        p.use_smooth = smooth
    if thickness:
        solid = obj.modifiers.new("thickness", "SOLIDIFY")
        solid.thickness, solid.offset = thickness, -1
    if subsurf:
        obj.modifiers.new("smooth", "SUBSURF").levels = subsurf
    if mat:
        me.materials.append(mat)
    return obj


def _sheet(name, point, nu, nv, mat, keep=None, wrap=False, uv_scale=(1.0, 1.0), **kw):
    """A surface from point(u, v) -> Vector with u, v in 0..1. keep(p) -> False drops faces centred at p."""
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    cols = nu if wrap else nu + 1
    grid = [[bm.verts.new(point(i / nu, j / nv)) for j in range(nv + 1)] for i in range(cols)]
    for i in range(nu):
        for j in range(nv):
            a, b = grid[i][j], grid[(i + 1) % cols][j]
            c, d = grid[(i + 1) % cols][j + 1], grid[i][j + 1]
            centre = (a.co + b.co + c.co + d.co) / 4
            if keep is not None and not keep(centre):
                continue
            f = bm.faces.new((a, b, c, d))
            for loop, (du, dv) in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
                loop[uv].uv = ((i + du) / nu * uv_scale[0], (j + dv) / nv * uv_scale[1])
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
    return _object(name, bm, mat, **kw)


def _primitive(name, kind, mat, matrix, **params):
    getattr(bpy.ops.mesh, "primitive_%s_add" % kind)(**params)
    obj = bpy.context.active_object
    obj.name = name
    obj.data.transform(matrix)
    for p in obj.data.polygons:
        p.use_smooth = True
    if mat:
        obj.data.materials.append(mat)
    return obj


def scuffed_texture(name, base, dots=(), size=1024, seed=1, wear=0.5, extent=(0.24, 0.32), centre=(0.0, -0.02)):
    """A painted or moulded surface as a picture: the base colour, blotchy with age, scratched, with
    dark round holes at `dots` ((x, z, radius) in metres; the picture covers `extent` metres around `centre`)."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / size
    img = np.empty((size, size, 3), dtype=np.float32)
    blotch = np.zeros((size, size), dtype=np.float32)
    for octave in (4, 9, 21):                                 # soft blotches at three sizes
        phase = rng.uniform(0, 6.28, 4)
        blotch += (np.sin(xx * octave * 6.28 + phase[0] + np.sin(yy * octave * 3.1 + phase[1]) * 2)
                   * np.sin(yy * octave * 6.28 + phase[2] + np.sin(xx * octave * 2.7 + phase[3]) * 2)) / octave * 3
    shade = 1.0 - wear * 0.35 * np.clip(blotch + 0.3, 0, 1)
    for c in range(3):
        img[:, :, c] = base[c] * shade
    img[:, :, 2] *= 1.0 - wear * 0.12 * np.clip(blotch + 0.5, 0, 1)   # age yellows it
    for _ in range(int(140 * wear)):                          # scratches: short dark or pale lines
        x0, y0 = rng.uniform(0, size, 2)
        angle, length = rng.uniform(0, 3.14), rng.uniform(15, 130)
        tone = rng.choice([0.55, 0.7, 1.25])
        for t in np.linspace(0, 1, int(length)):
            px, py = int(x0 + math.cos(angle) * length * t), int(y0 + math.sin(angle) * length * t)
            if 0 <= px < size and 0 <= py < size:
                img[py, px, :] *= tone
    for (dx, dz, radius) in dots:                             # holes: black, with a soft dark rim
        u = (dx - centre[0]) / extent[0] + 0.5
        v = (dz - centre[1]) / extent[1] + 0.5
        d = np.sqrt(((xx - u) * extent[0]) ** 2 + ((yy - v) * extent[1]) ** 2)
        img *= np.clip((d - radius) / 0.0025, 0.02, 1.0)[:, :, None]
    rgba = np.dstack([np.clip(img, 0, 1) ** (1 / 2.2), np.ones((size, size), dtype=np.float32)])
    image = bpy.data.images.new(name, size, size, alpha=False)
    image.pixels.foreach_set(rgba.astype(np.float32).ravel())
    image.pack()
    mat = bpy.data.materials.new(name)
    mat.use_fake_user = True
    mat.use_nodes = True
    nt = mat.node_tree
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = image
    bsdf = nt.nodes["Principled BSDF"]
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.45
    return mat


def _plain(name, colour, rough, metallic=0.0):
    m = kit.plain(name, colour, rough=rough, metallic=metallic)
    m.use_fake_user = True
    return m


def _front_uv(obj, extent=(0.24, 0.32), centre=(0.0, -0.02)):
    """UVs looking straight at the face: x across, z up, covering `extent` metres around `centre`."""
    layer = obj.data.uv_layers.active
    for loop in obj.data.loops:
        co = obj.data.vertices[loop.vertex_index].co
        layer.data[loop.index].uv = ((co.x - centre[0]) / extent[0] + 0.5, (co.z - centre[1]) / extent[1] + 0.5)


def _eye_hole(p, half_w, half_h, z=0.0):
    return any(((p.x - s * EYE_X) / half_w) ** 2 + ((p.z - z) / half_h) ** 2 < 1.0 for s in (-1, 1))


# ─── The masks ───────────────────────────────────────────────────────────────
def hockey(name, seed=1):
    centre, r = Vector((0.0, 0.105, -0.02)), Vector((0.092, 0.118, 0.14))

    def point(u, v):
        theta, phi = math.radians(-82 + 164 * u), math.radians(-72 + 150 * v)
        return centre + Vector((r.x * math.sin(theta) * math.cos(phi), -r.y * math.cos(theta) * math.cos(phi),
                                r.z * math.sin(phi)))
    holes = [(0.0, 0.085, 0.005), (-0.024, 0.07, 0.005), (0.024, 0.07, 0.005), (-0.012, 0.052, 0.0045), (0.012, 0.052, 0.0045)]
    for side in (-1, 1):
        holes += [(side * 0.058, -0.03, 0.005), (side * 0.066, -0.052, 0.005), (side * 0.05, -0.058, 0.0045),
                  (side * 0.058, -0.078, 0.0045), (side * 0.038, -0.082, 0.0045), (side * 0.018, -0.1, 0.0045),
                  (side * 0.04, -0.108, 0.004), (side * 0.012, -0.045, 0.004), (side * 0.014, -0.066, 0.004)]
    holes.append((0.0, -0.118, 0.0045))
    mat = scuffed_texture(name + "_shell", (0.82, 0.8, 0.72), dots=holes, seed=seed, wear=0.7)
    shell = _sheet(name, point, 110, 110, mat, keep=lambda p: not _eye_hole(p, 0.019, 0.013), thickness=0.004, subsurf=1)
    _front_uv(shell)
    return [shell]


def sack(name, seed=1):
    rng = random.Random(seed)
    profile = [(0.158, 0.012), (0.15, 0.045), (0.128, 0.078), (0.09, 0.1), (0.03, 0.108), (-0.04, 0.105), (-0.1, 0.096),
               (-0.15, 0.082), (-0.185, 0.064), (-0.2, 0.058), (-0.215, 0.066), (-0.255, 0.098), (-0.3, 0.128)]
    phase = [rng.uniform(0, 6.28) for _ in range(3)]

    def radius(z):
        for (z0, r0), (z1, r1) in zip(profile, profile[1:]):
            if z1 <= z <= z0:
                t = (z0 - z) / (z0 - z1)
                t = t * t * (3 - 2 * t)
                return r0 + (r1 - r0) * t
        return profile[-1][1]

    def point(u, v):
        z = profile[0][0] + (profile[-1][0] - profile[0][0]) * v
        a = 2 * math.pi * u
        r = radius(z)
        gather = max(0.0, min(1.0, (-0.08 - z) / 0.1))             # the cloth bunches toward the rope
        r *= 1 + (0.02 + 0.07 * gather) * math.sin(11 * a + phase[0]) + 0.02 * math.sin(5 * a + phase[1] + z * 20)
        corners = max(0.0, (z - 0.085) / 0.07)                      # a sack has two corners at the top
        x = r * math.sin(a) * (1 + 0.5 * corners)
        y = -r * math.cos(a) * 1.15 * (1 - 0.35 * corners)
        return HEAD + Vector((x, y, z + 0.012 * math.sin(3 * a + phase[2]) * (1 - gather)))
    jag = [rng.uniform(0.7, 1.3) for _ in range(9)]

    def keep(p):
        if p.y > HEAD.y - 0.04:
            return True
        for s in (-1, 1):
            dx, dz = p.x - s * (EYE_X + 0.004), p.z - 0.004
            k = jag[int((math.atan2(dz, dx) + math.pi) / (2 * math.pi) * 9) % 9]
            if math.hypot(dx, dz * 1.25) < 0.015 * k:
                return False
        return True
    burlap = realistic.fabric(name + "_burlap", "hessian_230", (0.5, 0.42, 0.3), scale=9.0, sheen=0.0)
    bag = _sheet(name, point, 120, 90, burlap, keep=keep, wrap=True, uv_scale=(0.7, 0.46), thickness=0.003)
    rope_mat = _plain(name + "_rope", (0.2, 0.15, 0.08), 0.95)
    rope = _primitive(name + "_rope", "torus", rope_mat,
                      Matrix.Translation(HEAD + Vector((0, 0, -0.2))) @ Matrix.Diagonal((1, 1.15, 1, 1)),
                      major_radius=0.062, minor_radius=0.0065, major_segments=48, minor_segments=10)
    # A stitched mouth: a row of crossed dark threads lying on the cloth.
    thread = _plain(name + "_thread", (0.03, 0.025, 0.02), 0.9)
    bm = bmesh.new()
    for k in range(7):
        x = (k - 3) * 0.014
        z = -0.078 - 0.012 * (1 - (k - 3) ** 2 / 9.0)
        front = HEAD.y - radius(z) * 1.15 * math.cos(math.asin(max(-1, min(1, x / radius(z))))) - 0.003
        for lean in (-1, 1):
            m = (Matrix.Translation((x, front, z)) @ Matrix.Rotation(lean * 0.6 + rng.uniform(-0.2, 0.2), 4, "Y")
                 @ Matrix.Diagonal((0.0016, 0.0016, 0.011, 1)))
            bmesh.ops.create_cube(bm, size=2.0, matrix=m)
    stitches = _object(name + "_stitches", bm, thread, smooth=False)
    return [bag, rope, stitches]


def welding(name, seed=1):
    centre, radius = Vector((0.0, 0.105)), 0.12

    def point(u, v):
        a = math.radians(-84 + 168 * u)
        if v < 0.78:                                              # the shield: a curved wall, tapering in at the chin
            z = -0.185 + 0.25 * v / 0.78
            r = radius * (0.9 + 0.1 * min(1.0, (z + 0.185) / 0.07))
        else:                                                      # the top curls back over the forehead
            t = (v - 0.78) / 0.22 * math.pi / 2
            z = 0.065 + 0.07 * math.sin(t)
            r = radius * (0.35 + 0.65 * math.cos(t))
        return Vector((centre.x + r * math.sin(a), centre.y - r * math.cos(a), z))
    body = scuffed_texture(name + "_shield", (0.07, 0.068, 0.065), seed=seed, wear=1.0)
    shield = _sheet(name, point, 70, 60, body, thickness=0.004, subsurf=1)
    _front_uv(shield, extent=(0.26, 0.36), centre=(0.0, -0.05))
    front = centre.y - radius
    frame = _plain(name + "_frame", (0.02, 0.02, 0.02), 0.5)
    window = _primitive(name + "_window", "cube", frame,
                        Matrix.Translation((0, front + 0.0, 0.0)) @ Matrix.Diagonal((0.062, 0.012, 0.034, 1)), size=2.0)
    for p in window.data.polygons:
        p.use_smooth = False
    glass_mat = _plain(name + "_glass", (0.01, 0.03, 0.02), 0.06)
    glass = _primitive(name + "_glass", "cube", glass_mat,
                       Matrix.Translation((0, front - 0.011, 0.0)) @ Matrix.Diagonal((0.052, 0.002, 0.025, 1)), size=2.0)
    for p in glass.data.polygons:
        p.use_smooth = False
    return [shield, window, glass]


def plague_doctor(name, seed=1):
    leather = realistic.fabric(name + "_leather", "brown_leather", (0.1, 0.07, 0.05), scale=7.0, sheen=0.0, worn=False)
    profile = [(0.15, 0.01), (0.14, 0.05), (0.11, 0.08), (0.05, 0.094), (-0.03, 0.094), (-0.1, 0.086), (-0.16, 0.072),
               (-0.195, 0.062), (-0.22, 0.075), (-0.27, 0.12)]

    def radius(z):
        for (z0, r0), (z1, r1) in zip(profile, profile[1:]):
            if z1 <= z <= z0:
                t = (z0 - z) / (z0 - z1)
                t = t * t * (3 - 2 * t)
                return r0 + (r1 - r0) * t
        return profile[-1][1]

    def hood_point(u, v):
        z = profile[0][0] + (profile[-1][0] - profile[0][0]) * v
        a = 2 * math.pi * u
        r = radius(z)
        return HEAD + Vector((r * math.sin(a), -r * math.cos(a) * 1.16, z))
    hood = _sheet(name, hood_point, 72, 60, leather, wrap=True, uv_scale=(0.62, 0.42), thickness=0.003)

    # The beak: a long cone from the middle of the face, curving down to a point.
    base, length = Vector((0.0, -0.005, -0.038)), 0.2

    def beak_point(u, v):
        a = 2 * math.pi * u
        t = v
        spine = base + Vector((0, -length * t, -0.075 * t * t))
        rx, rz = 0.05 * (1 - t) ** 0.85 + 0.003, 0.058 * (1 - t) ** 0.8 + 0.003
        return spine + Vector((rx * math.sin(a), 0, rz * math.cos(a)))
    beak = _sheet(name + "_beak", beak_point, 40, 34, leather, wrap=True, uv_scale=(0.3, 0.22), thickness=0.0)

    # Round goggles: brass rims with dark glass, set over the eyes and turned slightly outward.
    brass = _plain(name + "_brass", (0.6, 0.42, 0.16), 0.38, metallic=1.0)
    glass = _plain(name + "_lens", (0.015, 0.02, 0.02), 0.05)
    parts = [hood, beak]
    for s in (-1, 1):
        at = Vector((s * 0.037, HEAD.y - radius(0.008) * 1.16 * math.cos(math.asin(0.037 / radius(0.008))) - 0.006, 0.012))
        turn = Matrix.Translation(at) @ Matrix.Rotation(s * math.radians(16), 4, "Z") @ Matrix.Rotation(math.pi / 2, 4, "X")
        parts.append(_primitive("%s_rim_%d" % (name, s), "torus", brass, turn,
                                major_radius=0.023, minor_radius=0.0055, major_segments=36, minor_segments=10))
        parts.append(_primitive("%s_lens_%d" % (name, s), "cylinder", glass, turn, radius=0.021, depth=0.008, vertices=36))

    # The hat: black felt, wide flat brim, low crown.
    felt = realistic.fabric(name + "_felt", "caban", (0.012, 0.012, 0.013), scale=4.0, sheen=0.06, worn=False)
    top = HEAD + Vector((0, 0, 0.088))

    def hat_point(u, v):
        a = 2 * math.pi * u
        if v < 0.45:                                              # brim, from its edge in to the crown
            r = 0.19 - (0.19 - 0.09) * (v / 0.45)
            z = -0.012 * (r - 0.09) / 0.1 + 0.006 * math.sin(2 * a)
        elif v < 0.85:                                             # the side of the crown
            r = 0.09 - 0.008 * (v - 0.45) / 0.4
            z = 0.1 * (v - 0.45) / 0.4
        else:                                                      # its top
            r = 0.082 * (1 - (v - 0.85) / 0.15)
            z = 0.1 + 0.004 * (v - 0.85) / 0.15
        return top + Vector((r * math.sin(a), -r * math.cos(a) * 1.08, z))
    parts.append(_sheet(name + "_hat", hat_point, 64, 40, felt, wrap=True, uv_scale=(0.9, 0.5), thickness=0.004))
    return parts


def bauta(name, seed=1):
    def point(u, v):
        x = -0.088 + 0.176 * u
        z = -0.15 + 0.255 * v
        side = abs(x) / 0.088
        y = -0.016 + 0.1 * side ** 2.2                             # wraps round the cheeks (clear of the cowl)
        y += 0.03 * max(0.0, (z - 0.05) / 0.055) ** 2              # the brow leans back
        if -0.045 < z < 0.035:                                      # a sharp, straight nose
            t = (0.035 - z) / 0.08
            y -= 0.034 * t * math.exp(-(x / (0.01 + 0.012 * t)) ** 2)
        for s_ in (-1, 1):                                          # shallow sockets
            y += 0.008 * math.exp(-(((x - s_ * EYE_X) / 0.024) ** 2 + (z / 0.016) ** 2))
        if z < -0.04:                                               # the jaw juts out and flares, flat-fronted
            t = min(1.0, (-0.04 - z) / 0.085)
            y -= 0.075 * t ** 0.8 * (1 - 0.55 * side ** 2)
        return Vector((x * (1.0 + 0.12 * max(0.0, (-0.04 - z) / 0.11)), y, z))

    def keep(p):
        if _eye_hole(p, 0.021, 0.011):
            return False
        top = 0.105 - 0.05 * (abs(p.x) / 0.088) ** 2.5             # a rounded top, a square jaw
        return p.z < top
    mat = scuffed_texture(name + "_gesso", (0.86, 0.84, 0.78), seed=seed, wear=0.45)
    mask = _sheet(name, point, 96, 120, mat, keep=keep, thickness=0.004, subsurf=1)
    _front_uv(mask)
    return [mask]


def mesh_texture(name, size=512):
    """Fine woven steel wire, seen close: dark, with the wires catching a little light. Tiles."""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / size
    wires = np.maximum(np.abs(np.sin(xx * math.pi * 16)) ** 14, np.abs(np.sin(yy * math.pi * 16)) ** 14)
    value = 0.012 + 0.16 * wires
    rgba = np.dstack([value ** (1 / 2.2)] * 3 + [np.ones((size, size), dtype=np.float32)])
    image = bpy.data.images.new(name, size, size, alpha=False)
    image.pixels.foreach_set(rgba.astype(np.float32).ravel())
    image.pack()
    mat = bpy.data.materials.new(name)
    mat.use_fake_user = True
    mat.use_nodes = True
    nt = mat.node_tree
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = image
    bsdf = nt.nodes["Principled BSDF"]
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.4
    bsdf.inputs["Metallic"].default_value = 0.6
    return mat


def fencing(name, seed=1):
    centre, r = Vector((0.0, 0.1, -0.015)), Vector((0.102, 0.145, 0.165))

    def point(u, v):
        theta, phi = math.radians(-100 + 200 * u), math.radians(-78 + 160 * v)
        return centre + Vector((r.x * math.sin(theta) * math.cos(phi), -r.y * math.cos(theta) * math.cos(phi),
                                r.z * math.sin(phi)))
    dome = _sheet(name, point, 80, 70, mesh_texture(name + "_wire"), uv_scale=(5.0, 4.5), thickness=0.003)
    # The padded bib under the chin, and the band round the edge of the mesh.
    cloth = realistic.fabric(name + "_bib", "rough_linen", (0.62, 0.6, 0.55), scale=10.0, sheen=0.0, worn=True)

    def bib_point(u, v):
        a = math.radians(-78 + 156 * u)
        z = -0.165 - 0.115 * v
        rr = 0.092 + 0.05 * v ** 1.3
        return Vector((rr * math.sin(a), 0.1 - rr * math.cos(a) * 1.05, z))
    bib = _sheet(name + "_bib", bib_point, 40, 14, cloth, uv_scale=(0.3, 0.12), thickness=0.012, subsurf=1)
    band = _primitive(name + "_band", "torus", cloth,
                      Matrix.Translation((0, 0.118, -0.015)) @ Matrix.Rotation(math.radians(80), 4, "X")
                      @ Matrix.Diagonal((1.0, 1.58, 1.0, 1)),
                      major_radius=0.1, minor_radius=0.008, major_segments=48, minor_segments=8)
    return [dome, bib, band]


def balaclava(name, seed=1):
    profile = [(0.142, 0.01), (0.132, 0.05), (0.1, 0.08), (0.04, 0.09), (-0.04, 0.089), (-0.1, 0.08), (-0.145, 0.068),
               (-0.185, 0.064), (-0.23, 0.07)]

    def radius(z):
        for (z0, r0), (z1, r1) in zip(profile, profile[1:]):
            if z1 <= z <= z0:
                t = (z0 - z) / (z0 - z1)
                t = t * t * (3 - 2 * t)
                return r0 + (r1 - r0) * t
        return profile[-1][1]

    def point(u, v):
        z = profile[0][0] + (profile[-1][0] - profile[0][0]) * v
        a = 2 * math.pi * u
        rr = radius(z)
        return HEAD + Vector((rr * math.sin(a), -rr * math.cos(a) * 1.17, z))

    def keep(p):
        if p.y > HEAD.y - 0.03:
            return True
        if _eye_hole(p, 0.021, 0.0115, z=0.002):
            return False
        return (p.x / 0.027) ** 2 + ((p.z + 0.078) / 0.0105) ** 2 > 1.0       # the mouth
    knit = realistic.fabric(name + "_knit", "knitted_fleece", (0.022, 0.021, 0.022), scale=16.0, sheen=0.05, worn=False)
    return [_sheet(name, point, 110, 100, knit, keep=keep, wrap=True, uv_scale=(0.58, 0.37), thickness=0.004)]


BUILDERS = {"plague_doctor": plague_doctor, "hockey": hockey, "sack": sack, "welding": welding,
            "bauta": bauta, "fencing": fencing, "balaclava": balaclava}


def build(kind, name, seed=1):
    """Builds a mask and returns it joined into one object named <name> with its origin at the eye point."""
    parts = BUILDERS[kind](name + "_part", seed)
    bpy.ops.object.select_all(action="DESELECT")
    for o in parts:
        for m in list(o.modifiers):                               # bake thickness and smoothing before joining
            bpy.context.view_layer.objects.active = o
            bpy.ops.object.modifier_apply(modifier=m.name)
        o.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    if len(parts) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    obj.name = name
    return obj
