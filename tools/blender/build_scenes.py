"""
Builds the realistic Ashes cellar in Blender and renders every clip the game plays.

Run from the project folder (after tools\\blender\\fetch_assets.py has downloaded the assets):
    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\build_scenes.py
Options after "--":
    --draft           EEVEE at half resolution: fast previews (minutes instead of an hour)
    --quick           a quarter of the frames per clip
    --only NAME,...   render only these clips (e.g. --only table,card,intro)

Output: resources/scenes/<clip>/frame_0001.jpg ... at 1280x720 (ignored by git).
Clips: still views table / card / piece / resell, the turns between neighbouring views, and the intro.

The set: a brick cellar under one hanging lamp; the player's seat at a round table facing a
masked opponent; three masked dealers around the seat (card dealer left, piece dealer right,
reseller behind-right); a corridor and a tiled bathroom for the intro. Real props and textures
come from Poly Haven (CC0). The figures are stand-in robes with theatre masks (masks.py).
"""
import bpy
import math
import os
import sys

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scene_kit as kit  # noqa: E402

# ─── SETTINGS ────────────────────────────────────────────────────────────────
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "resources", "scenes")
args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
DRAFT = "--draft" in args
QUICK = "--quick" in args
ONLY = set(args[args.index("--only") + 1].split(",")) if "--only" in args else None
WIDTH, HEIGHT = (640, 360) if DRAFT else (1280, 720)
FPS = 24

SEAT = Vector((0.0, -0.95, 0.0))
EYE_SEATED = 1.15
EYE_STANDING = 1.62
OPPONENT = Vector((0.0, 1.0, 0.0))
CARD_DEALER = Vector((-1.55, -0.9, 0.0))
PIECE_DEALER = Vector((1.55, -0.9, 0.0))
RESELLER = Vector((0.95, -2.45, 0.0))


def yaw_to(src, dst):
    """Z rotation that turns something facing +Y toward dst."""
    return math.atan2(dst.y - src.y, dst.x - src.x) - math.pi / 2


# ─── THE SET ─────────────────────────────────────────────────────────────────
def build_set():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = WIDTH, HEIGHT
    sc.render.resolution_percentage = 100
    sc.render.fps = FPS
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = 90
    kit.setup_world(sc, haze=0.008)

    brick = kit.pbr("brick", "plaster_brick_01")
    concrete = kit.pbr("concrete", "concrete_floor_worn_001")
    tiles = kit.pbr("tiles", "long_white_tiles")
    floor_tiles = kit.pbr("floor_tiles", "dirty_tiles")
    ceiling = kit.plain("ceiling", (0.04, 0.038, 0.035), rough=0.95)
    porcelain = kit.plain("porcelain", (0.75, 0.74, 0.7), rough=0.3)
    paper = kit.plain("paper", (0.72, 0.68, 0.6), rough=0.7)
    wood = kit.plain("dark_wood", (0.07, 0.045, 0.03), rough=0.55)
    mats = {"wool": kit.plain("wool", (0.03, 0.026, 0.022), rough=0.95, sheen=0.15),
            "void": kit.plain("void", (0, 0, 0), rough=1.0)}
    P = math.pi / 2

    # Main room 8 x 8 x 3 m; a door gap in the back wall (y = -4) leads to the corridor.
    kit.plane((8, 8), (0, 0, 0), (0, 0, 0), concrete, 2.0)
    kit.plane((8, 8), (0, 0, 3), (math.pi, 0, 0), ceiling)
    kit.plane((8, 3), (0, 4, 1.5), (P, 0, math.pi), brick)
    kit.plane((8, 3), (4, 0, 1.5), (P, 0, P), brick)
    kit.plane((8, 3), (-4, 0, 1.5), (P, 0, -P), brick)
    kit.plane((3.2, 3), (-2.4, -4, 1.5), (P, 0, 0), brick)
    kit.plane((3.2, 3), (2.4, -4, 1.5), (P, 0, 0), brick)
    kit.plane((1.6, 0.8), (0, -4, 2.6), (P, 0, 0), brick)
    # Corridor (y -4 .. -6).
    kit.plane((1.7, 2.0), (0, -5, 0), (0, 0, 0), concrete)
    kit.plane((2.0, 2.6), (0.85, -5, 1.3), (P, 0, P), brick)
    kit.plane((2.0, 2.6), (-0.85, -5, 1.3), (P, 0, -P), brick)
    kit.plane((1.7, 2.0), (0, -5, 2.6), (math.pi, 0, 0), ceiling)
    # Bathroom (y -6 .. -8): tiled, with a mirror, a sink and a cold strip light.
    kit.plane((2.5, 2.0), (0, -7, 0.001), (0, 0, 0), floor_tiles, 1.0)
    kit.plane((2.5, 2.0), (0, -7, 2.6), (math.pi, 0, 0), ceiling)
    kit.plane((2.5, 2.6), (0, -8, 1.3), (P, 0, 0), tiles, 1.0)
    kit.plane((2.0, 2.6), (1.25, -7, 1.3), (P, 0, P), tiles, 1.0)
    kit.plane((2.0, 2.6), (-1.25, -7, 1.3), (P, 0, -P), tiles, 1.0)
    kit.plane((0.8, 2.6), (-0.85, -6, 1.3), (P, 0, math.pi), tiles, 1.0)
    kit.plane((0.8, 2.6), (0.85, -6, 1.3), (P, 0, math.pi), tiles, 1.0)
    kit.import_model("ornate_mirror_01", Vector((0, -7.93, 0)), rot_z=0.0, sit_on=1.05)
    kit.box((0, -7.72, 0.82), (0.6, 0.42, 0.14), porcelain)
    kit.cyl((0, -7.75, 0.4), 0.08, 0.76, porcelain)
    kit.import_model("mounted_fluorescent_lights", Vector((0, -7.0, 2.55)), scale=0.6)
    kit.light("AREA", (0, -7.0, 2.5), 22, (0.75, 0.9, 1.0), size=0.8, target=(0, -7.0, 0))
    kit.light("POINT", (0, -5.0, 2.3), 5, (0.8, 0.85, 1.0), size=0.2)

    # The table, the chess set and the lamp.
    table, table_objs = kit.import_model("round_wooden_table_01", Vector((0, 0, 0)), sit_on=0.0)
    top = kit.bounds(table_objs)[1].z
    kit.import_model("chess_set", Vector((0, 0, 0)), rot_z=math.pi, sit_on=top)
    kit.import_model("WoodenChair_01", Vector((OPPONENT.x, OPPONENT.y + 0.1, 0)), sit_on=0.0)
    kit.import_model("hanging_industrial_lamp", Vector((0, 0, 0)), scale=0.45, sit_on=1.95)
    kit.light("SPOT", (0, 0, 1.93), 650, (1.0, 0.8, 0.6), size=0.08, target=(0, 0, 0), spot_deg=85, blend=0.6)
    kit.light("POINT", (0, 0, 1.98), 25, (1.0, 0.7, 0.4), size=0.05)

    # The opponent and the three dealers.
    kit.masked_figure(OPPONENT, math.pi, mats, seated=True, mood="sad", seed=5)
    kit.masked_figure(CARD_DEALER, yaw_to(CARD_DEALER, SEAT), mats, mood="smile", seed=2)
    kit.masked_figure(PIECE_DEALER, yaw_to(PIECE_DEALER, SEAT), mats, mood="sad", seed=7)
    kit.masked_figure(RESELLER, yaw_to(RESELLER, SEAT), mats, mood="smile", seed=11, height=0.95)

    # Card dealer: a small stand with candles and a fan of cards.
    stand = Vector((-1.05, -0.62, 0))
    kit.cyl((stand.x, stand.y, 0.45), 0.2, 0.9, wood)
    kit.import_model("brass_candleholders", Vector((stand.x - 0.08, stand.y + 0.08, 0)), scale=0.55, sit_on=0.9)
    for i in range(5):
        a = math.radians(-24 + i * 12)
        kit.box((stand.x + 0.06, stand.y - 0.1 + i * 0.035, 0.92 + 0.004 * i), (0.06, 0.09, 0.002), paper, rot=(0, 0, a))
    kit.light("POINT", (stand.x - 0.08, stand.y + 0.08, 1.25), 14, (1.0, 0.55, 0.25), size=0.02)
    # Piece dealer: a crate of chess pieces for sale, lit by a lantern glow.
    crate, crate_objs = kit.import_model("old_military_crate", Vector((1.05, -0.62, 0)), rot_z=0.3, sit_on=0.0)
    kit.import_model("chess_set", Vector((1.05, -0.62, 0)), rot_z=0.9, scale=0.55, sit_on=kit.bounds(crate_objs)[1].z)
    kit.light("POINT", (1.3, -0.3, 1.5), 16, (1.0, 0.62, 0.32), size=0.1)
    # Reseller: a barrel stove glowing behind him, bottles on a barrel, a worn bookshelf.
    kit.import_model("barrel_stove", Vector((1.75, -3.2, 0)), sit_on=0.0)
    kit.light("POINT", (1.75, -3.2, 0.7), 60, (1.0, 0.4, 0.12), size=0.25)
    kit.import_model("Barrel_01", Vector((1.0, -3.55, 0)), sit_on=0.0)
    kit.import_model("wine_bottles_01", Vector((1.0, -3.55, 0)), sit_on=0.95)
    kit.import_model("wooden_bookshelf_worn", Vector((3.75, -2.2, 0)), rot_z=-P, sit_on=0.0)

    # Clutter in the corners.
    kit.import_model("wooden_crate_01", Vector((-3.3, 3.2, 0)), rot_z=0.3, sit_on=0.0)
    kit.import_model("wooden_crate_01", Vector((-3.25, 3.15, 0)), rot_z=-0.2, sit_on=0.6)
    kit.import_model("wooden_crate_01", Vector((3.2, 3.3, 0)), rot_z=0.8, sit_on=0.0)
    kit.import_model("Barrel_01", Vector((-3.3, -3.2, 0)), sit_on=0.0)

    # Rim lights so the dark robes separate from the dark walls.
    for figure, src, energy in ((OPPONENT, (0.3, 2.4, 2.1), 45), (CARD_DEALER, (-2.7, -1.3, 2.2), 30),
                                (PIECE_DEALER, (2.7, -1.3, 2.2), 30), (RESELLER, (1.6, -3.6, 2.2), 25)):
        kit.light("AREA", src, energy, (1.0, 0.6, 0.35), size=0.6, target=figure + Vector((0, 0, 1.3)))
    return sc, top


# ─── CAMERA ──────────────────────────────────────────────────────────────────
def make_camera():
    data = bpy.data.cameras.new("Cam")
    data.lens = 28
    data.clip_start = 0.05
    data.dof.use_dof = True
    data.dof.aperture_fstop = 2.8
    cam = bpy.data.objects.new("Cam", data)
    bpy.context.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    return cam


def pose(eye, target):
    """(location, rotation, focus distance) for a camera at eye looking at target."""
    d = target - eye
    rot = (math.pi / 2 + math.atan2(d.z, d.xy.length), 0, math.atan2(d.y, d.x) - math.pi / 2)
    return eye, rot, d.length


def build_views(top):
    eye = Vector((SEAT.x, SEAT.y, EYE_SEATED))
    lean = Vector((SEAT.x, SEAT.y + 0.42, 1.32))
    return {
        "table": pose(lean, Vector((0, 0.05, top))),
        "card": pose(eye, CARD_DEALER + Vector((0.2, 0.08, 1.36))),
        "piece": pose(eye, PIECE_DEALER + Vector((-0.2, 0.08, 1.36))),
        "resell": pose(eye, RESELLER + Vector((0.15, -0.15, 1.3))),
    }


RING = ["card", "table", "piece", "resell"]


def unwrap(prev, angle):
    while angle - prev > math.pi:
        angle -= 2 * math.pi
    while angle - prev < -math.pi:
        angle += 2 * math.pi
    return angle


def key(cam, frame, loc, rot, focus):
    cam.location = loc
    cam.rotation_euler = rot
    cam.data.dof.focus_distance = focus
    cam.keyframe_insert("location", frame=frame)
    cam.keyframe_insert("rotation_euler", frame=frame)
    cam.data.keyframe_insert("dof.focus_distance", frame=frame)


def clear_keys(cam):
    cam.animation_data_clear()
    cam.data.animation_data_clear()


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


def render_all(cam, views):
    q = 4 if QUICK else 1

    for name, (loc, rot, focus) in views.items():
        clear_keys(cam)
        key(cam, 1, loc, rot, focus)
        render_clip(name, 1)

    for i, a in enumerate(RING):
        b = RING[(i + 1) % len(RING)]
        clear_keys(cam)
        (la, ra, fa), (lb, rb, fb) = views[a], views[b]
        rb = (rb[0], rb[1], unwrap(ra[2], rb[2]))
        n = max(2, 18 // q)
        key(cam, 1, la, ra, fa)
        key(cam, n, lb, rb, fb)
        render_clip("turn_%s_%s" % (a, b), n)

    # Intro: the mirror, the corridor, the room, a glance at the dealers, sitting down, the board.
    clear_keys(cam)
    t_loc, t_rot, t_focus = views["table"]
    up = math.pi / 2
    beats = [
        (1,   (0, -7.15, EYE_STANDING), (up, 0, math.pi), 0.8),                         # staring into the mirror
        (36,  (0, -7.15, EYE_STANDING), (up, 0, math.pi), 0.8),
        (64,  (0, -6.95, EYE_STANDING), (up, 0, 2 * math.pi), 3.0),                    # turn to the door
        (112, (0, -4.4, EYE_STANDING + 0.02), (up, 0, 2 * math.pi), 3.5),              # down the corridor
        (150, (0, -2.0, EYE_STANDING), (math.radians(86), 0, 2 * math.pi), 3.0),       # into the room
        (168, (0, -1.6, EYE_STANDING), (math.radians(86), 0, 2 * math.pi + math.radians(28)), 1.7),  # card dealer
        (186, (0, -1.4, EYE_STANDING), (math.radians(86), 0, 2 * math.pi - math.radians(28)), 1.7),  # piece dealer
        (212, (0, -0.95, EYE_SEATED), (math.radians(88), 0, 2 * math.pi), 1.95),       # sit, facing the opponent
        (240, t_loc, (t_rot[0], 0, 2 * math.pi), t_focus),                              # lean over the board
    ]
    for frame, loc, rot, focus in beats:
        key(cam, max(1, frame // q), loc, rot, focus)
    for frame in range(70, 112, 12):  # a slight bob while walking
        z = EYE_STANDING + (0.025 if (frame // 12) % 2 else -0.015)
        cam.location = (0, -6.95 + (frame - 64) / 48 * 2.55, z)
        cam.keyframe_insert("location", frame=max(1, frame // q))
    render_clip("intro", max(2, 240 // q))


def main():
    sc, top = build_set()
    if DRAFT:
        kit.setup_eevee(sc)
    else:
        kit.setup_cycles(sc, threshold=0.05, max_samples=128)
    cam = make_camera()
    render_all(cam, build_views(top))
    print("DONE", OUT)


main()
