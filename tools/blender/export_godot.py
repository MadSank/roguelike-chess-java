"""
Exports the game's 3D assets from Blender to glTF binaries for the Godot project (godot/assets/, ignored by git).

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\export_godot.py [-- room,pieces]

  room.glb     the cellar: floor, walls, ceiling, the round table with the board on it, the opponent's
               throne, the hanging lamp, candles, crates, barrels, the stove and a bookshelf. No lights
               (Godot lights the scene) and no people.
  pieces.glb   one chess piece of each kind and colour (nodes "wK", "wQ", ... "bP"), each standing on the
               origin, facing its opponent when placed unrotated on the board.

Layout (Blender, metres; Godot sees x, z, -y): the table is at the origin, the player sits at y = -0.95
looking toward +y, the opponent's chair is at y = +1.1. One board square is SQUARE metres; the board's
centre is the table's centre and its top is at the height printed as BOARD_TOP.
Needs the Poly Haven assets (fetch_assets.py).
"""
import bpy
import math
import os
import sys

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic  # noqa: E402  (only for shared sizes)
import scene_kit as kit  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "godot", "assets")
TABLE_HEIGHT_SCALE = 0.76
P = math.pi / 2
PIECES = {"king": "K", "queen": "Q", "rook": "R", "bishop": "B", "knight": "N", "pawn": "P"}


def fresh():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def export(name):
    path = os.path.join(OUT, name + ".glb")
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", export_apply=True, export_yup=True,
                              export_lights=False, export_cameras=False, export_animations=False)
    print("EXPORTED", path, "%.1f MB" % (os.path.getsize(path) / 1e6))


def room():
    fresh()
    brick = kit.pbr("brick", "plaster_brick_01")
    concrete = kit.pbr("concrete", "concrete_floor_worn_001")
    ceiling = kit.plain("ceiling", (0.04, 0.038, 0.035), rough=0.95)
    wood = kit.plain("dark_wood", (0.07, 0.045, 0.03), rough=0.55)
    paper = kit.plain("paper", (0.72, 0.68, 0.6), rough=0.7)

    kit.plane((8, 8), (0, 0, 0), (0, 0, 0), concrete, 2.0).name = "floor"
    kit.plane((8, 8), (0, 0, 3), (math.pi, 0, 0), ceiling).name = "ceiling"
    kit.plane((8, 3), (0, 4, 1.5), (P, 0, math.pi), brick).name = "wall_far"
    kit.plane((8, 3), (4, 0, 1.5), (P, 0, P), brick).name = "wall_right"
    kit.plane((8, 3), (-4, 0, 1.5), (P, 0, -P), brick).name = "wall_left"
    kit.plane((8, 3), (0, -4, 1.5), (P, 0, 0), brick).name = "wall_behind"

    table, table_objs = kit.import_model("round_wooden_table_01", Vector((0, 0, 0)), sit_on=0.0)
    table.scale.z *= TABLE_HEIGHT_SCALE
    bpy.context.view_layer.update()
    top = kit.bounds(table_objs)[1].z
    # Only the board itself (the pieces are separate, placed by the game). Turned so a1 is a dark square.
    chess, chess_objs = kit.import_model("chess_set", Vector((0, 0, 0)), rot_z=P, sit_on=top)
    for o in chess_objs:
        if o.type == "MESH" and o.name != "board":
            bpy.data.objects.remove(o, do_unlink=True)
    board = bpy.data.objects["board"]
    bpy.context.view_layer.update()
    print("BOARD_TOP %.4f" % kit.bounds([board])[1].z)

    chair, _ = kit.import_model("WoodenChair_01", Vector((0, 1.1, 0)), sit_on=0.0)
    chair.scale.x *= realistic.CHAIR_WIDEN
    kit.import_model("hanging_industrial_lamp", Vector((0, 0, 0)), scale=0.45, sit_on=1.95)

    # Left of the seat: a stand with candles and a fan of cards. Right: a crate. Behind: stove and barrels.
    stand = Vector((-1.05, -0.62, 0))
    kit.cyl((stand.x, stand.y, 0.45), 0.2, 0.9, wood).name = "card_stand"
    kit.import_model("brass_candleholders", Vector((stand.x - 0.08, stand.y + 0.08, 0)), scale=0.55, sit_on=0.9)
    for i in range(5):
        a = math.radians(-24 + i * 12)
        kit.box((stand.x + 0.06, stand.y - 0.1 + i * 0.035, 0.92 + 0.004 * i), (0.06, 0.09, 0.002), paper, rot=(0, 0, a))
    kit.import_model("old_military_crate", Vector((1.05, -0.62, 0)), rot_z=0.3, sit_on=0.0)
    kit.import_model("barrel_stove", Vector((1.75, -3.2, 0)), sit_on=0.0)
    kit.import_model("Barrel_01", Vector((1.0, -3.55, 0)), sit_on=0.0)
    kit.import_model("wine_bottles_01", Vector((1.0, -3.55, 0)), sit_on=0.95)
    kit.import_model("wooden_bookshelf_worn", Vector((3.75, -2.2, 0)), rot_z=-P, sit_on=0.0)
    kit.import_model("wooden_crate_01", Vector((-3.3, 3.2, 0)), rot_z=0.3, sit_on=0.0)
    kit.import_model("wooden_crate_01", Vector((-3.25, 3.15, 0)), rot_z=-0.2, sit_on=0.6)
    kit.import_model("wooden_crate_01", Vector((3.2, 3.3, 0)), rot_z=0.8, sit_on=0.0)
    kit.import_model("Barrel_01", Vector((-3.3, -3.2, 0)), sit_on=0.0)
    export("room")


def pieces():
    fresh()
    root, objs = kit.import_model("chess_set", Vector((0, 0, 0)))
    bpy.context.view_layer.update()
    keep = {}
    for o in objs:
        if o.type != "MESH" or not o.name.startswith("piece_"):
            continue
        _, kind, colour = o.name.split("_")[:3]
        code = colour[0] + PIECES[kind]
        if code not in keep:
            keep[code] = o
    squares = sorted(o.matrix_world.translation.x for o in objs if o.name.startswith("piece_pawn_white"))
    print("SQUARE %.5f" % ((squares[-1] - squares[0]) / 7))
    for o in objs:
        if o.type == "MESH" and o not in keep.values():
            bpy.data.objects.remove(o, do_unlink=True)
    for code, o in keep.items():
        world = o.matrix_world.copy()
        o.parent = None
        o.matrix_world = world
        bpy.context.view_layer.update()
        lo, hi = kit.bounds([o])
        # Stand it on the origin: move the mesh so its base centre is the object's origin.
        shift = o.matrix_world.inverted() @ Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z))
        for v in o.data.vertices:
            v.co -= shift
        o.location = (0, 0, 0)
        o.rotation_euler = (0, 0, 0)
        o.name = code
        print("PIECE", code, "height %.3f" % (hi.z - lo.z))
    for o in list(bpy.data.objects):
        if o.type == "EMPTY":
            bpy.data.objects.remove(o, do_unlink=True)
    export("pieces")


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    only = set(args[0].split(",")) if args else None
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (("room", room), ("pieces", pieces)):
        if not only or name in only:
            fn()
    print("DONE")


main()
