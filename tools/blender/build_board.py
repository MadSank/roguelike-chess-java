"""
Renders the realistic board the game plays on: the real chess set on the table under the lamp,
seen from one fixed, tilted overhead camera.

Run from the project folder (after fetch_assets.py):
    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\build_board.py

Writes to resources/board/ (ignored by git):
    board.png                    the empty board on the table
    piece_<kind>_<colour>.png    each piece alone, with its contact shadow, on transparency
    board.properties             where the squares are in board.png, and where each sprite's
                                 base sits inside the sprite image

The camera is orthographic and only tilted forward, so every square appears as the same
axis-aligned rectangle, and one sprite per piece fits any square: the game just draws the
sprite with its base anchor on the square's centre, back rank first.
"""
import bpy
import math
import os
import sys

from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scene_kit as kit  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "resources", "board")
W, H = 1200, 1100              # render size (the game scales it down to fit)
ELEVATION = math.radians(56)    # camera looks down at 56 degrees
SQUARE = 0.0579                 # square size of the Poly Haven chess set (metres)
KINDS = ["pawn", "knight", "bishop", "rook", "queen", "king"]


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = W, H
    sc.render.resolution_percentage = 100
    kit.setup_world(sc, haze=0.0)
    sc.view_settings.exposure = -1.3  # the lamp is close: keep white pieces from blowing out
    kit.setup_cycles(sc, threshold=0.02, max_samples=256)
    sc.render.use_persistent_data = False

    table, table_objs = kit.import_model("round_wooden_table_01", Vector((0, 0, 0)), sit_on=0.0)
    top = kit.bounds(table_objs)[1].z
    # Rotated so White sits nearest the camera.
    # A quarter turn puts a dark square at a1 (bottom-left, as chess requires).
    chess, chess_objs = kit.import_model("chess_set", Vector((0, 0, 0)), rot_z=math.pi / 2, sit_on=top)
    board = next(o for o in chess_objs if o.name.startswith("board"))
    board_top = kit.bounds([board])[1].z
    pieces = [o for o in chess_objs if o.name.startswith("piece_")]

    # The same lamp as the room: a warm spot from above plus the bulb's glow, and a faint fill.
    kit.light("SPOT", (0, 0, 1.93), 650, (1.0, 0.8, 0.6), size=0.08, target=(0, 0, 0), spot_deg=85, blend=0.6)
    kit.light("POINT", (0, 0, 1.98), 25, (1.0, 0.7, 0.4), size=0.05)
    kit.light("AREA", (0, -1.2, 1.4), 15, (1.0, 0.85, 0.7), size=1.0, target=(0, 0, board_top))

    cam_data = bpy.data.cameras.new("BoardCam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = 0.62
    cam = bpy.data.objects.new("BoardCam", cam_data)
    bpy.context.collection.objects.link(cam)
    sc.camera = cam
    target = Vector((0, 0.025, board_top))  # a little beyond centre: room for the back rank's height
    direction = Vector((0, math.cos(ELEVATION), -math.sin(ELEVATION)))
    cam.location = target - direction * 3.0
    cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    bpy.context.view_layer.update()
    return sc, cam, table_objs, board, board_top, pieces


def to_pixels(sc, cam, point):
    v = world_to_camera_view(sc, cam, point)
    return v.x * W, (1.0 - v.y) * H


def main():
    sc, cam, table_objs, board, board_top, pieces = build()
    os.makedirs(OUT, exist_ok=True)
    half = 4 * SQUARE

    # Square geometry: a-file on the left, rank 8 at the far (top) edge.
    x0, y0 = to_pixels(sc, cam, Vector((-half, half, board_top)))
    x1, _ = to_pixels(sc, cam, Vector((half, half, board_top)))
    _, y1 = to_pixels(sc, cam, Vector((-half, -half, board_top)))
    sw, sh = (x1 - x0) / 8.0, (y1 - y0) / 8.0

    # 1. The empty board.
    for p in pieces:
        p.hide_render = True
    sc.render.film_transparent = False
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGB"
    sc.render.filepath = os.path.join(OUT, "board.png")
    bpy.ops.render.render(write_still=True)
    print("RENDERED board")

    # 2. Each piece alone at the board's centre, on transparency, keeping its shadow.
    for o in table_objs + [board]:
        if o.type == "MESH":
            o.is_shadow_catcher = True
    sc.render.film_transparent = True
    sc.render.image_settings.color_mode = "RGBA"
    ax, ay = to_pixels(sc, cam, Vector((0, 0, board_top)))
    crop_w, up, down = 1.5 * sw, 3.2 * sh, 0.55 * sh
    left, right = ax - crop_w / 2, ax + crop_w / 2
    top_px, bottom_px = ay - up, ay + down
    sc.render.use_border = True
    sc.render.use_crop_to_border = True
    sc.render.border_min_x, sc.render.border_max_x = left / W, right / W
    sc.render.border_min_y, sc.render.border_max_y = 1 - bottom_px / H, 1 - top_px / H
    for colour in ("white", "black"):
        for kind in KINDS:
            src = next(p for p in pieces if p.name.startswith("piece_%s_%s" % (kind, colour)))
            centre = (kit.bounds([src])[0] + kit.bounds([src])[1]) / 2
            saved = src.matrix_world.copy()
            src.matrix_world.translation += Vector((-centre.x, -centre.y, 0))
            for p in pieces:
                p.hide_render = p is not src
            sc.render.filepath = os.path.join(OUT, "piece_%s_%s.png" % (kind, colour))
            bpy.ops.render.render(write_still=True)
            src.matrix_world = saved
            print("RENDERED", kind, colour)

    with open(os.path.join(OUT, "board.properties"), "w") as f:
        f.write("# Written by tools/blender/build_board.py. Pixel positions in board.png.\n")
        f.write("width=%d\nheight=%d\n" % (W, H))
        f.write("squareLeft=%.2f\nsquareTop=%.2f\nsquareWidth=%.3f\nsquareHeight=%.3f\n" % (x0, y0, sw, sh))
        f.write("# Sprites: the piece's base centre is at (anchorX, anchorY) inside each sprite image.\n")
        f.write("anchorX=%.2f\nanchorY=%.2f\n" % (ax - round(left), ay - round(top_px)))
    print("DONE", OUT)


main()
