"""
Builds the Ashes scene in Blender and renders every clip the game plays.

Run (from the project folder):
    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\build_scenes.py
Options after "--":
    --quick           only a few frames per clip (to check the setup quickly)
    --only NAME,...   render only these clips (e.g. --only table,card)

Output: resources/scenes/<clip>/frame_0001.jpg ... at 320x240 (PS1-style resolution;
the game scales it up with a grain filter).

Everything here is placeholder art built from primitives. Replace objects with real
models later and re-run this script; the game only cares about the rendered frames.
"""
import bpy
import math
import os
import sys

# ─── SETTINGS ────────────────────────────────────────────────────────────────
WIDTH, HEIGHT, FPS = 320, 240, 24
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "resources", "scenes")

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
QUICK = "--quick" in args
ONLY = None
if "--only" in args:
    ONLY = set(args[args.index("--only") + 1].split(","))

# Player's seat and eye heights (metres).
SEAT = (0.0, -0.95)
EYE_SEATED = 1.15
EYE_STANDING = 1.62

# Where everyone stands. Yaw is the camera turn needed to face them from the seat.
OPPONENT = (0.0, 1.0)
CARD_DEALER = (-1.55, -0.9)
PIECE_DEALER = (1.55, -0.9)
RESELLER = (0.95, -2.45)


# ─── HELPERS ─────────────────────────────────────────────────────────────────
def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "BLENDER_EEVEE"
    sc.render.resolution_x = WIDTH
    sc.render.resolution_y = HEIGHT
    sc.render.resolution_percentage = 100
    sc.render.fps = FPS
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = 88
    try:
        sc.eevee.taa_render_samples = 16
    except AttributeError:
        pass
    world = bpy.data.worlds.new("World")
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (0.004, 0.003, 0.003, 1)
    bg.inputs[1].default_value = 1.0
    return sc


def material(name, colour, rough=0.85, emission=None, strength=0.0, metallic=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*colour, 1)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metallic
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    return m


def add(obj_op, mat=None, **kw):
    obj_op(**kw)
    o = bpy.context.active_object
    if mat:
        o.data.materials.append(mat)
    return o


def box(loc, size, mat, rot=(0, 0, 0)):
    o = add(bpy.ops.mesh.primitive_cube_add, mat, location=loc, rotation=rot)
    o.scale = (size[0] / 2, size[1] / 2, size[2] / 2)
    return o


def cyl(loc, r, h, mat, verts=8, rot=(0, 0, 0)):
    return add(bpy.ops.mesh.primitive_cylinder_add, mat, location=loc, radius=r, depth=h,
               vertices=verts, rotation=rot)


def ball(loc, r, mat, subdiv=1):
    return add(bpy.ops.mesh.primitive_ico_sphere_add, mat, location=loc, radius=r, subdivisions=subdiv)


def point_light(loc, energy, colour, radius=0.1):
    data = bpy.data.lights.new("L", "POINT")
    data.energy = energy
    data.color = colour
    data.shadow_soft_size = radius
    o = bpy.data.objects.new("L", data)
    o.location = loc
    bpy.context.collection.objects.link(o)
    return o


def yaw_to(src, dst):
    """Camera Z rotation (radians) to look from src toward dst (camera faces +Y at 0)."""
    return math.atan2(dst[1] - src[1], dst[0] - src[0]) - math.pi / 2


def pitch_to(src, dst, h_src, h_dst):
    dist = math.hypot(dst[0] - src[0], dst[1] - src[1])
    return math.pi / 2 + math.atan2(h_dst - h_src, dist)


# ─── SET DRESSING ────────────────────────────────────────────────────────────
def build_world():
    M = {
        "floor": material("floor", (0.05, 0.045, 0.04)),
        "wall": material("wall", (0.08, 0.07, 0.065)),
        "tile": material("tile", (0.35, 0.38, 0.36), rough=0.4),
        "wood": material("wood", (0.12, 0.06, 0.03)),
        "felt": material("felt", (0.025, 0.006, 0.006)),
        "sqlight": material("sqlight", (0.55, 0.5, 0.42)),
        "sqdark": material("sqdark", (0.08, 0.06, 0.05)),
        "ash": material("ash", (0.16, 0.155, 0.15)),
        "ashdark": material("ashdark", (0.06, 0.058, 0.055)),
        "cloth": material("cloth", (0.03, 0.03, 0.035)),
        "ember": material("ember", (0.1, 0.02, 0), emission=(1.0, 0.35, 0.05), strength=6.0),
        "lamp": material("lamp", (0.9, 0.7, 0.4), emission=(1.0, 0.75, 0.45), strength=8.0),
        "metal": material("metal", (0.3, 0.3, 0.3), rough=0.35, metallic=1.0),
        "mirror": material("mirror", (0.02, 0.025, 0.03), rough=0.05, metallic=1.0),
        "card": material("card", (0.7, 0.66, 0.58)),
        "crate": material("crate", (0.16, 0.1, 0.05)),
        "gold": material("gold", (0.8, 0.6, 0.2), rough=0.3, metallic=1.0),
    }

    # Main room: 8 x 8 m cellar, 3 m high. Door gap in the back wall (y = -4).
    box((0, 0, -0.05), (8, 8, 0.1), M["floor"])
    box((0, 0, 3.05), (8, 8, 0.1), M["wall"])
    box((-4.05, 0, 1.5), (0.1, 8, 3), M["wall"])
    box((4.05, 0, 1.5), (0.1, 8, 3), M["wall"])
    box((0, 4.05, 1.5), (8, 0.1, 3), M["wall"])
    box((-2.4, -4.05, 1.5), (3.2, 0.1, 3), M["wall"])
    box((2.4, -4.05, 1.5), (3.2, 0.1, 3), M["wall"])
    box((0, -4.05, 2.6), (1.6, 0.1, 0.8), M["wall"])
    # Corridor (y -4 .. -6) and bathroom (y -6 .. -8).
    box((-0.85, -5.0, 1.3), (0.1, 2.0, 2.6), M["wall"])
    box((0.85, -5.0, 1.3), (0.1, 2.0, 2.6), M["wall"])
    box((0, -5.0, 2.65), (1.8, 2.0, 0.1), M["wall"])
    box((0, -7.0, -0.04), (2.4, 2.0, 0.08), M["tile"])
    box((-1.25, -7.0, 1.3), (0.1, 2.0, 2.6), M["tile"])
    box((1.25, -7.0, 1.3), (0.1, 2.0, 2.6), M["tile"])
    box((0, -8.05, 1.3), (2.5, 0.1, 2.6), M["tile"])
    box((0, -7.0, 2.65), (2.5, 2.0, 0.1), M["wall"])
    box((-0.75, -6.05, 1.3), (0.9, 0.1, 2.6), M["tile"])
    box((0.75, -6.05, 1.3), (0.9, 0.1, 2.6), M["tile"])
    box((0, -7.96, 1.55), (0.9, 0.04, 0.7), M["mirror"])           # mirror
    box((0, -7.75, 0.85), (0.6, 0.4, 0.15), M["tile"])             # sink
    cyl((0, -7.75, 0.45), 0.08, 0.7, M["tile"])
    box((0.8, -7.55, 0.25), (0.4, 0.55, 0.5), M["tile"])           # toilet
    point_light((0, -7.0, 2.4), 22, (0.7, 0.85, 1.0))              # cold bathroom light
    point_light((0, -5.0, 2.4), 8, (0.7, 0.8, 1.0))                # dim corridor

    # The table, the board and the lamp.
    cyl((0, 0, 0.37), 0.08, 0.74, M["wood"])
    cyl((0, 0, 0.755), 0.75, 0.04, M["felt"], verts=16)
    sq = 0.055
    for r in range(8):
        for c in range(8):
            box(((c - 3.5) * sq, (r - 3.5) * sq, 0.78), (sq, sq, 0.01),
                M["sqlight"] if (r + c) % 2 == 0 else M["sqdark"])
    cyl((0, 0, 2.25), 0.18, 0.22, M["metal"], verts=8)
    ball((0, 0, 2.1), 0.07, M["lamp"])
    cyl((0, 0, 2.7), 0.005, 0.7, M["metal"], verts=4)
    point_light((0, 0, 2.0), 150, (1.0, 0.72, 0.45), radius=0.15)

    # Player's chair (just visible) and the opponent's chair.
    box((SEAT[0], SEAT[1] - 0.05, 0.45), (0.5, 0.5, 0.06), M["wood"])
    box((OPPONENT[0], OPPONENT[1] + 0.25, 0.75), (0.5, 0.06, 0.9), M["wood"])
    box((OPPONENT[0], OPPONENT[1], 0.45), (0.5, 0.5, 0.06), M["wood"])

    person(OPPONENT, math.pi, M, seated=True, mask=True)
    person(CARD_DEALER, yaw_to(CARD_DEALER, SEAT), M)
    person(PIECE_DEALER, yaw_to(PIECE_DEALER, SEAT), M)
    person(RESELLER, yaw_to(RESELLER, SEAT), M, hunched=True)
    dealer_props(M)

    # A faint light on each dealer so they read out of the dark.
    for d, e in ((CARD_DEALER, 18), (PIECE_DEALER, 18), (RESELLER, 14)):
        point_light((d[0] * 0.7, d[1] * 0.7 + 0.2, 2.2), e, (1.0, 0.55, 0.3), radius=0.3)
    return M


def person(pos, facing, M, seated=False, hunched=False, mask=False):
    """Low-poly ash figure with ember eyes. facing = Z rotation (0 faces +Y)."""
    x, y = pos
    fx, fy = -math.sin(facing), math.cos(facing)  # forward vector
    base = 0.45 if seated else 0.0
    torso_h = 0.62
    tz = base + (0.35 if seated else 0.95) + torso_h / 2
    lean = 0.12 if hunched else 0.0
    body = cyl((x + fx * lean, y + fy * lean, tz), 0.21, torso_h, M["cloth"], verts=6)
    body.rotation_euler = (math.radians(12 if hunched else 0) * fy, -math.radians(12 if hunched else 0) * fx, facing)
    hz = tz + torso_h / 2 + 0.13
    hx, hy = x + fx * (lean * 2), y + fy * (lean * 2)
    cyl((hx, hy, hz - 0.12), 0.06, 0.1, M["ashdark"], verts=6)           # neck
    box((hx, hy, hz), (0.2, 0.2, 0.24), M["ash"], rot=(0, 0, facing))      # head
    if mask:
        box((hx + fx * 0.105, hy + fy * 0.105, hz), (0.18, 0.02, 0.22), M["card"], rot=(0, 0, facing))
    else:
        # Hood: a dark cone over the head, a little behind the face.
        add(bpy.ops.mesh.primitive_cone_add, M["cloth"], location=(hx - fx * 0.03, hy - fy * 0.03, hz + 0.08),
            radius1=0.19, depth=0.42, vertices=6)
        box((hx - fx * 0.06, hy - fy * 0.06, hz - 0.02), (0.26, 0.26, 0.24), M["cloth"], rot=(0, 0, facing))
    sx, sy = math.cos(facing), math.sin(facing)  # sideways vector
    for side in (-1, 1):
        ex, ey = hx + fx * 0.118 + sx * 0.05 * side, hy + fy * 0.118 + sy * 0.05 * side
        box((ex, ey, hz + 0.03), (0.045, 0.02, 0.018), M["ember"], rot=(0, 0, facing))
        ax, ay = x + sx * 0.27 * side, y + sy * 0.27 * side
        cyl((ax, ay, tz - 0.05), 0.055, 0.6, M["ashdark"], verts=6)
        if not seated:
            cyl((x + sx * 0.1 * side, y + sy * 0.1 * side, 0.47), 0.07, 0.94, M["cloth"], verts=6)


def dealer_props(M):
    # Card dealer: a small stand with a fan of cards.
    cx, cy = CARD_DEALER
    box((cx + 0.45, cy, 0.5), (0.4, 0.5, 1.0), M["wood"])
    for i in range(5):
        a = math.radians(-30 + i * 15)  # fanned, facing the player (along +X)
        box((cx + 0.5, cy - 0.12 + i * 0.06, 1.08), (0.005, 0.09, 0.13), M["card"], rot=(a, 0, 0))
    # Piece dealer: a crate of pieces.
    px, py = PIECE_DEALER
    box((px - 0.45, py, 0.3), (0.5, 0.5, 0.6), M["crate"])
    for i in range(6):
        add(bpy.ops.mesh.primitive_cone_add, M["sqlight"] if i % 2 else M["sqdark"],
            location=(px - 0.6 + (i % 3) * 0.13, py - 0.1 + (i // 3) * 0.18, 0.68), radius1=0.04, depth=0.14, vertices=6)
    # Reseller: a scale on a stool.
    rx, ry = RESELLER
    cyl((rx - 0.35, ry + 0.35, 0.4), 0.18, 0.8, M["wood"], verts=6)
    cyl((rx - 0.35, ry + 0.35, 0.95), 0.015, 0.3, M["gold"], verts=6)
    box((rx - 0.35, ry + 0.35, 1.1), (0.4, 0.02, 0.02), M["gold"])
    for side in (-1, 1):
        cyl((rx - 0.35 + 0.18 * side, ry + 0.35, 1.02), 0.07, 0.02, M["gold"], verts=8)


# ─── CAMERA AND CLIPS ────────────────────────────────────────────────────────
def make_camera():
    data = bpy.data.cameras.new("Cam")
    data.lens = 24
    data.clip_start = 0.05
    cam = bpy.data.objects.new("Cam", data)
    bpy.context.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    return cam


SEAT_EYE = (SEAT[0], SEAT[1], EYE_SEATED)


def view(target_xy, target_h):
    """Camera pose from the seat looking at a point: (location, rotation)."""
    return (SEAT_EYE, (pitch_to(SEAT, target_xy, EYE_SEATED, target_h), 0, yaw_to(SEAT, target_xy)))


LEAN_EYE = (SEAT[0], SEAT[1] + 0.4, 1.3)   # leaning in over the board
VIEWS = {
    "table": (LEAN_EYE, (math.pi / 2 + math.atan2(0.78 - 1.3, -LEAN_EYE[1] - 0.04), 0, 0)),
    "card": view(CARD_DEALER, 1.4),
    "piece": view(PIECE_DEALER, 1.4),
    "resell": view(RESELLER, 1.3),
}
# The four views form a ring the player turns around: card <- table -> piece -> resell -> card.
RING = ["card", "table", "piece", "resell"]


def unwrap(prev, angle):
    """Pick the equivalent of angle closest to prev, so turns don't spin the long way."""
    while angle - prev > math.pi:
        angle -= 2 * math.pi
    while angle - prev < -math.pi:
        angle += 2 * math.pi
    return angle


def key(cam, frame, loc, rot):
    cam.location = loc
    cam.rotation_euler = rot
    cam.keyframe_insert("location", frame=frame)
    cam.keyframe_insert("rotation_euler", frame=frame)


def clear_keys(cam):
    cam.animation_data_clear()


def render_clip(name, frames):
    if ONLY and name not in ONLY:
        return
    sc = bpy.context.scene
    folder = os.path.join(OUT, name)
    os.makedirs(folder, exist_ok=True)
    for f in os.listdir(folder):
        if f.endswith(".jpg"):
            os.remove(os.path.join(folder, f))
    sc.frame_start, sc.frame_end = 1, frames
    sc.render.filepath = os.path.join(folder, "frame_")
    bpy.ops.render.render(animation=True)
    print("RENDERED", name, frames, "frames")


def clips(cam):
    q = 4 if QUICK else 1  # quick mode: a quarter of the frames

    # Still views: one frame each (the game adds flicker and grain).
    for name, (loc, rot) in VIEWS.items():
        clear_keys(cam)
        key(cam, 1, loc, rot)
        render_clip(name, 1)

    # Turns between neighbouring views (played backwards to turn the other way).
    for i, a in enumerate(RING):
        b = RING[(i + 1) % len(RING)]
        clear_keys(cam)
        (la, ra), (lb, rb) = VIEWS[a], VIEWS[b]
        rb = (rb[0], rb[1], unwrap(ra[2], rb[2]))
        n = max(2, 18 // q)
        key(cam, 1, la, ra)
        key(cam, n, lb, rb)
        render_clip("turn_%s_%s" % (a, b), n)

    # Intro: the mirror, the corridor, the room, sitting down, looking at the board.
    clear_keys(cam)
    face_mirror = (math.pi / 2, 0, math.pi)
    face_door = (math.pi / 2, 0, 2 * math.pi)
    beats = [
        (1,   (0, -7.1, EYE_STANDING), face_mirror),
        (36,  (0, -7.1, EYE_STANDING), face_mirror),
        (64,  (0, -6.9, EYE_STANDING), face_door),
        (112, (0, -4.4, EYE_STANDING + 0.02), face_door),
        (150, (0, -2.0, EYE_STANDING), (math.radians(86), 0, 2 * math.pi)),
        (168, (0, -1.5, EYE_STANDING), (math.radians(86), 0, 2 * math.pi + math.radians(20))),  # glance at the card dealer
        (186, (0, -1.3, EYE_STANDING), (math.radians(86), 0, 2 * math.pi - math.radians(20))),  # ...and the piece dealer
        (210, SEAT_EYE, (math.radians(88), 0, 2 * math.pi)),                                     # sit, facing the opponent
        (240, VIEWS["table"][0], (VIEWS["table"][1][0], 0, 2 * math.pi)),                         # lean in over the board
    ]
    for frame, loc, rot in beats:
        key(cam, max(1, frame // q), loc, rot)
    # A slight bob while walking.
    for frame in range(70, 150, 12):
        x, y, z = (0, -6.9 + (frame - 64) / 48 * 2.5, EYE_STANDING + (0.03 if (frame // 12) % 2 else -0.02))
        if frame < 112:
            cam.location = (x, y, z)
            cam.keyframe_insert("location", frame=max(1, frame // q))
    render_clip("intro", max(2, 240 // q))


# ─── PIECE SPRITES ───────────────────────────────────────────────────────────
PIECE_OUT = os.path.join(ROOT, "resources", "images")


def build_piece(kind, M):
    """Low-poly chess piece at the origin, about 1 unit tall."""
    parts = [cyl((0, 0, 0.05), 0.36, 0.1, M, verts=10), cyl((0, 0, 0.14), 0.28, 0.08, M, verts=10)]
    if kind == "pawn":
        parts.append(add(bpy.ops.mesh.primitive_cone_add, M, location=(0, 0, 0.4), radius1=0.22, radius2=0.1, depth=0.45, vertices=8))
        parts.append(ball((0, 0, 0.7), 0.16, M))
    elif kind == "rook":
        parts.append(cyl((0, 0, 0.45), 0.22, 0.55, M, verts=8))
        parts.append(cyl((0, 0, 0.76), 0.28, 0.1, M, verts=8))
        for i in range(4):
            a = i * math.pi / 2
            parts.append(box((math.cos(a) * 0.2, math.sin(a) * 0.2, 0.88), (0.12, 0.12, 0.14), M, rot=(0, 0, a)))
    elif kind == "knight":
        parts.append(box((0, 0, 0.42), (0.36, 0.26, 0.5), M))
        parts.append(box((0, -0.12, 0.74), (0.26, 0.42, 0.22), M, rot=(math.radians(-25), 0, 0)))
        parts.append(box((0, 0.08, 0.86), (0.2, 0.12, 0.18), M))
    elif kind == "bishop":
        parts.append(add(bpy.ops.mesh.primitive_cone_add, M, location=(0, 0, 0.5), radius1=0.22, radius2=0.08, depth=0.65, vertices=8))
        parts.append(ball((0, 0, 0.88), 0.15, M))
        parts.append(ball((0, 0, 1.06), 0.05, M))
    elif kind == "queen":
        parts.append(add(bpy.ops.mesh.primitive_cone_add, M, location=(0, 0, 0.52), radius1=0.26, radius2=0.1, depth=0.7, vertices=8))
        parts.append(cyl((0, 0, 0.9), 0.2, 0.08, M, verts=8))
        for i in range(6):
            a = i * math.pi / 3
            parts.append(ball((math.cos(a) * 0.17, math.sin(a) * 0.17, 0.98), 0.05, M, subdiv=1))
        parts.append(ball((0, 0, 1.02), 0.08, M))
    elif kind == "king":
        parts.append(add(bpy.ops.mesh.primitive_cone_add, M, location=(0, 0, 0.52), radius1=0.26, radius2=0.12, depth=0.7, vertices=8))
        parts.append(cyl((0, 0, 0.92), 0.2, 0.1, M, verts=8))
        parts.append(box((0, 0, 1.12), (0.07, 0.07, 0.3), M))
        parts.append(box((0, 0, 1.16), (0.22, 0.07, 0.07), M))
    return parts


def render_pieces():
    sc = reset_scene()
    sc.render.resolution_x = sc.render.resolution_y = 96
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    os.makedirs(PIECE_OUT, exist_ok=True)
    mats = {
        "white": material("bone", (0.78, 0.72, 0.6), rough=0.6),
        "black": material("char", (0.14, 0.115, 0.1), rough=0.45),
    }
    data = bpy.data.cameras.new("PieceCam")
    data.type = "ORTHO"
    data.ortho_scale = 1.25
    cam = bpy.data.objects.new("PieceCam", data)
    bpy.context.collection.objects.link(cam)
    sc.camera = cam
    cam.location = (0, -3.0, 1.75)
    cam.rotation_euler = (math.radians(68), 0, 0)
    point_light((-1.2, -1.5, 2.2), 120, (1.0, 0.8, 0.6), radius=0.4)    # warm key, like the table lamp
    point_light((1.4, 1.2, 1.4), 170, (1.0, 0.3, 0.1), radius=0.3)       # ember rim light
    for colour, mat in mats.items():
        for kind in ("pawn", "knight", "bishop", "rook", "queen", "king"):
            parts = build_piece(kind, mat)
            sc.render.filepath = os.path.join(PIECE_OUT, "piece_%s_%s.png" % (kind, colour))
            bpy.ops.render.render(write_still=True)
            for p in parts:
                bpy.data.objects.remove(p, do_unlink=True)
    print("RENDERED pieces")


def main():
    if ONLY is None or ONLY - {"pieces"}:
        reset_scene()
        build_world()
        cam = make_camera()
        clips(cam)
    if ONLY is None or "pieces" in ONLY:
        render_pieces()
    print("DONE", OUT)


main()
