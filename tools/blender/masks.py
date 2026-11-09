"""
Procedural theatre-style masks for the Ashes characters (original design: comedy and tragedy).

make_mask(mood, seed) builds a full-face mask:
  - sculpted forehead, brow ridge, nose, cheeks, chin and eye holes
  - mood "smile": raised cheeks, arched brows, mouth corners up
    mood "sad":   hollow cheeks, brows raised in the middle, mouth corners down, a painted tear
  - painted brows and mouth line (a colour attribute the material darkens)
  - a jagged broken-off chunk at the edge, and cracks + grime in the material
  - matte, rough surface (no shine)

The mask faces -Y, is about 19 cm wide, and its origin sits at the centre of the face.
"""
import bmesh
import bpy
import math
import random


H = 1.32            # mask height relative to its half-width
EYE_X, EYE_Y = 0.36, 0.24
EYE_W, EYE_H = 0.19, 0.095


def _g(d, s):
    """Gaussian falloff."""
    return math.exp(-(d / s) ** 2)


def _eye_dist(x, y, sx, mood):
    """< 1 inside the eye hole (almond shape; the sad mask's eyes droop outward)."""
    ex, ey = x - sx * EYE_X, y - EYE_Y
    if mood == "sad":
        ey -= 0.25 * sx * ex          # outer corners lower
    else:
        ey += 0.12 * ex * ex / EYE_W  # slight smile curve
    return math.hypot(ex / EYE_W, ey / (EYE_H * (1.0 - 0.35 * abs(ex) / EYE_W)))


def _mouth_y(x, mood):
    curve = 0.55 if mood == "smile" else -0.45
    return -0.52 + curve * x * x


def _brow_y(x, mood):
    ax = abs(x)
    if mood == "smile":
        return 0.5 + 0.12 * math.sin(min(1.0, ax / 0.62) * math.pi)  # high arch
    return 0.62 - 0.32 * ax                                          # inner ends raised: sorrow


def _height(x, y, mood):
    """How far the surface sticks out at (x, y) in mask units (width 2)."""
    r = x * x + (y / H) ** 2 * 0.85
    z = 0.55 * math.sqrt(max(0.0, 1.0 - r))
    # Brow ridge along the brow line.
    if abs(x) < 0.7:
        z += 0.05 * _g(y - (_brow_y(x, mood) - 0.08), 0.08)
    # Eye sockets.
    for sx in (-1, 1):
        z -= 0.07 * _g(math.hypot((x - sx * 0.36) / 1.3, y - 0.24), 0.16)
    # Nose: a ridge widening toward the tip.
    if -0.3 < y < 0.45:
        t = (0.45 - y) / 0.75
        z += 0.26 * t * _g(x, 0.07 + 0.06 * t)
    z += 0.08 * _g(math.hypot(x, y + 0.27), 0.1)
    # Cheeks.
    for sx in (-1, 1):
        d = math.hypot(x - sx * 0.42, y + (0.12 if mood == "smile" else 0.22))
        z += (0.11 if mood == "smile" else -0.04) * _g(d, 0.2)
    # Mouth groove and lips.
    if abs(x) < 0.5:
        my = _mouth_y(x, mood)
        z -= 0.05 * _g(y - my, 0.035)
        z += 0.03 * _g(y - (my - 0.07), 0.05)
    # Chin.
    z += 0.06 * _g(math.hypot(x / 1.4, y + 0.92), 0.16)
    return z


def _stroke(d, width, soft=0.012):
    """Solid painted stroke: 1 inside half-width, fading over a short soft edge."""
    return max(0.0, min(1.0, (width - abs(d)) / soft + 0.5))


def _paint(x, y, mood):
    """0..1 how much dark paint at (x, y): bold brows, mouth line, the sad mask's tear."""
    p = 0.0
    ax = abs(x)
    if 0.12 < ax < 0.68:
        # Brows taper from thick (inner end) to a point (outer end).
        t = (ax - 0.12) / 0.56
        p = max(p, _stroke(y - _brow_y(x, mood), 0.045 * (1.0 - t) + 0.008))
    if ax < 0.47:
        t = ax / 0.47
        p = max(p, _stroke(y - _mouth_y(x, mood), 0.026 * (1.0 - 0.6 * t)))
    if mood == "sad" and x > 0 and -0.5 < y < 0.12:
        # A painted tear running down from the right eye, ending in a drop.
        tx = 0.37 - 0.06 * (0.12 - y)
        p = max(p, _stroke(x - tx, 0.016))
        p = max(p, _stroke(math.hypot(x - (0.37 - 0.06 * 0.62), (y + 0.5) * 0.8), 0.04))
    return p


def paint_image(mood, width=400):
    """The painted lines as a texture (white = paint), sharp at any mesh resolution."""
    key = "mask_paint_" + mood
    if key in bpy.data.images:
        return bpy.data.images[key]
    height = int(width * H)
    img = bpy.data.images.new(key, width, height, alpha=False, float_buffer=False)
    # Set the colour space before writing pixels: changing it afterwards regenerates the image blank.
    img.colorspace_settings.name = "Non-Color"
    px = [0.0] * (width * height * 4)
    for j in range(height):
        y = (j + 0.5) / height * 2.0 * H - H - 0.05
        row = j * width * 4
        for i in range(width):
            x = (i + 0.5) / width * 2.0 - 1.0
            p = _paint(x, y, mood)
            k = row + i * 4
            px[k] = px[k + 1] = px[k + 2] = p
            px[k + 3] = 1.0
    img.pixels = px
    img.update()
    img.pack()
    return img


def make_mask(name, mood="smile", seed=1, material=None, broken=True, res=72):
    rng = random.Random(seed)
    bm = bmesh.new()

    nx = ny = res
    grid = {}
    for j in range(ny + 1):
        for i in range(nx + 1):
            # Square grid mapped onto an oval, so the outline is smooth (no stair steps).
            u = -1.0 + 2.0 * i / nx
            w = -1.0 + 2.0 * j / ny
            x = u * math.sqrt(1.0 - w * w / 2.0)
            y = H * w * math.sqrt(1.0 - u * u / 2.0) - 0.05
            v = bm.verts.new((x, y, _height(x, y, mood)))

            grid[(i, j)] = v

    # Jagged break: a chunk missing from the upper edge on one side.
    bx = rng.choice((-1, 1)) * rng.uniform(0.45, 0.7)
    by = rng.uniform(0.55, 0.85)
    br = rng.uniform(0.22, 0.32)
    jag = [rng.uniform(0.75, 1.25) for _ in range(12)]

    def keep(x, y):
        for sx in (-1, 1):
            if _eye_dist(x, y, sx, mood) < 1.0:
                return False
        if broken:
            a = math.atan2(y - by, x - bx)
            k = jag[int((a + math.pi) / (2 * math.pi) * 12) % 12]
            if math.hypot(x - bx, y - by) < br * k:
                return False
        return True

    for j in range(ny):
        for i in range(nx):
            vs = [grid[(i, j)], grid[(i + 1, j)], grid[(i + 1, j + 1)], grid[(i, j + 1)]]
            cx = sum(v.co.x for v in vs) / 4
            cy = sum(v.co.y for v in vs) / 4
            if keep(cx, cy):
                bm.faces.new(vs)
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context="VERTS")
    # Snap the eye-hole edges onto clean almond shapes.
    for v in bm.verts:
        if not v.is_boundary:
            continue
        for sx in (-1, 1):
            d = _eye_dist(v.co.x, v.co.y, sx, mood)
            if d < 1.8:
                cx, cy = sx * EYE_X, EYE_Y
                v.co.x = cx + (v.co.x - cx) / d
                v.co.y = cy + (v.co.y - cy) / d
                v.co.z = _height(v.co.x, v.co.y, mood)

    # UVs = the mask's flat (x, y) layout, so the painted-lines texture lines up with the face.
    uv = bm.loops.layers.uv.new("UVMap")
    for f in bm.faces:
        for loop in f.loops:
            x, y = loop.vert.co.x, loop.vert.co.y
            loop[uv].uv = ((x + 1.0) / 2.0, (y + H + 0.05) / (2.0 * H))

    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()

    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    for poly in mesh.polygons:
        poly.use_smooth = True
    solid = obj.modifiers.new("thickness", "SOLIDIFY")
    solid.thickness = 0.05
    solid.offset = -1
    obj.modifiers.new("smooth", "SUBSURF").levels = 1
    obj.data.materials.append(material or mask_material(name + "_mat", seed, paint_image(mood)))
    # Mask units -> metres (19 cm wide), turned to face -Y.
    obj.scale = (0.095, 0.095, 0.095)
    obj.rotation_euler = (math.pi / 2, 0, 0)
    return obj


def mask_material(name, seed=1, paint=None):
    """Matte bone-white with grime in the hollows, cracks and painted lines.

    Mix nodes are wired by socket index: the node has separate float, vector and colour
    sockets that share the names A, B and Result (vector A/B = 4/5, colour A/B = 6/7;
    outputs: vector = 1, colour = 2).
    """
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N["Principled BSDF"]
    coord = N.new("ShaderNodeTexCoord")

    # Base colour: bone with blotchy ageing.
    blotch = N.new("ShaderNodeTexNoise")
    blotch.inputs["Scale"].default_value = 3.0
    blotch.inputs["Detail"].default_value = 6.0
    L.new(coord.outputs["Object"], blotch.inputs["Vector"])
    age = N.new("ShaderNodeValToRGB")
    age.color_ramp.elements[0].color = (0.62, 0.57, 0.48, 1)
    age.color_ramp.elements[1].color = (0.80, 0.76, 0.68, 1)
    L.new(blotch.outputs["Fac"], age.inputs["Fac"])

    # Cracks: edges of a distorted Voronoi pattern, only where a noise mask allows.
    distort = N.new("ShaderNodeTexNoise")
    distort.inputs["Scale"].default_value = 5.0
    L.new(coord.outputs["Object"], distort.inputs["Vector"])
    mixv = N.new("ShaderNodeMix")
    mixv.data_type = "VECTOR"
    mixv.inputs["Factor"].default_value = 0.12
    L.new(coord.outputs["Object"], mixv.inputs[4])
    L.new(distort.outputs["Color"], mixv.inputs[5])
    vor = N.new("ShaderNodeTexVoronoi")
    vor.feature = "DISTANCE_TO_EDGE"
    vor.inputs["Scale"].default_value = 3.2 + seed * 0.37
    L.new(mixv.outputs[1], vor.inputs["Vector"])
    crack_line = N.new("ShaderNodeMapRange")
    crack_line.inputs["From Min"].default_value = 0.0
    crack_line.inputs["From Max"].default_value = 0.025
    crack_line.inputs["To Min"].default_value = 1.0
    crack_line.inputs["To Max"].default_value = 0.0
    L.new(vor.outputs["Distance"], crack_line.inputs["Value"])
    where = N.new("ShaderNodeTexNoise")
    where.noise_dimensions = "4D"  # set first: the W input only exists in 4D mode
    where.inputs["Scale"].default_value = 1.6
    where.inputs["W"].default_value = seed
    L.new(coord.outputs["Object"], where.inputs["Vector"])
    where_ramp = N.new("ShaderNodeMapRange")
    where_ramp.inputs["From Min"].default_value = 0.5
    where_ramp.inputs["From Max"].default_value = 0.62
    L.new(where.outputs["Fac"], where_ramp.inputs["Value"])
    cracks = N.new("ShaderNodeMath")
    cracks.operation = "MULTIPLY"
    L.new(crack_line.outputs["Result"], cracks.inputs[0])
    L.new(where_ramp.outputs["Result"], cracks.inputs[1])

    # Painted lines from the paint texture (UV-mapped to the face layout).
    paint_tex = N.new("ShaderNodeTexImage")
    paint_tex.image = paint

    # Grime in hollows.
    ao = N.new("ShaderNodeAmbientOcclusion")
    ao.inputs["Distance"].default_value = 0.03

    c1 = N.new("ShaderNodeMix")
    c1.data_type = "RGBA"
    L.new(paint_tex.outputs["Color"], c1.inputs["Factor"])
    L.new(age.outputs["Color"], c1.inputs[6])
    c1.inputs[7].default_value = (0.012, 0.01, 0.009, 1)  # charcoal paint
    c2 = N.new("ShaderNodeMix")
    c2.data_type = "RGBA"
    L.new(cracks.outputs["Value"], c2.inputs["Factor"])
    L.new(c1.outputs[2], c2.inputs[6])
    c2.inputs[7].default_value = (0.03, 0.025, 0.02, 1)
    grime = N.new("ShaderNodeMix")
    grime.data_type = "RGBA"
    grime.blend_type = "MULTIPLY"
    grime.inputs["Factor"].default_value = 0.85
    L.new(c2.outputs[2], grime.inputs[6])
    L.new(ao.outputs["Color"], grime.inputs[7])
    L.new(grime.outputs[2], bsdf.inputs["Base Color"])

    # Rough, matte: no porcelain shine.
    rough = N.new("ShaderNodeMapRange")
    rough.inputs["To Min"].default_value = 0.72
    rough.inputs["To Max"].default_value = 0.95
    L.new(blotch.outputs["Fac"], rough.inputs["Value"])
    L.new(rough.outputs["Result"], bsdf.inputs["Roughness"])

    # Surface bump: fine grain plus the cracks cut in.
    grain = N.new("ShaderNodeTexNoise")
    grain.inputs["Scale"].default_value = 60.0
    L.new(coord.outputs["Object"], grain.inputs["Vector"])
    height = N.new("ShaderNodeMath")
    height.operation = "SUBTRACT"
    L.new(grain.outputs["Fac"], height.inputs[0])
    L.new(cracks.outputs["Value"], height.inputs[1])
    bump = N.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.35
    bump.inputs["Distance"].default_value = 0.002
    L.new(height.outputs["Value"], bump.inputs["Height"])
    L.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return m
