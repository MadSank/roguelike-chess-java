"""
Builds every realistic character once (MPFB body + simulated cloth, realistic.py) and saves them
to tools/blender/assets/characters.blend (local, ignored by git), one collection per character.
build_scenes.py appends them from there, so renders never have to simulate cloth again.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\make_characters.py [-- name,name,...]

The roster: five masked opponents (a new one each level, cycling) and the three dealers.
Each is built in MPFB's frame (facing -Y, feet at the origin). Seated opponents are posed against
the same chair and table the cellar uses, at the same offsets (chair 0.1 m behind them, the table
centre 1 m in front), so they fit the scene exactly once placed.
Takes about 2 minutes per character.
"""
import bpy
import os
import sys
import time

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

OUT = os.path.join(kit.ASSETS, "characters.blend")
TABLE_HEIGHT_SCALE = 0.76   # the table model is bar height; the cellar shortens it to a dining table
CHAIR_Y = 0.1

# name, mask, build (MPFB macros), cloak and robe fabrics: (Poly Haven texture, tint, tiles per metre, sheen)
OPPONENTS = [
    {"name": "opponent1", "mood": "sad", "seed": 5, "build": {},
     "cloak": ("caban", (0.1, 0.085, 0.075), 2.5, 0.04), "skirt": ("poly_wool_herringbone", (0.12, 0.1, 0.09), 4.0, 0.04)},
    {"name": "opponent2", "mood": "smile", "seed": 21, "build": {"weight": 0.35, "height": 0.85, "muscle": 0.4},
     "cloak": ("velour_velvet", (0.2, 0.035, 0.04), 3.0, 0.35), "skirt": ("rough_linen", (0.05, 0.045, 0.045), 4.0, 0.04)},
    {"name": "opponent3", "mood": "sad", "seed": 33, "build": {"weight": 0.85, "muscle": 0.6, "height": 0.5},
     "cloak": ("hessian_230", (0.5, 0.42, 0.3), 2.0, 0.0), "skirt": ("knitted_fleece", (0.06, 0.05, 0.045), 6.0, 0.03)},
    {"name": "opponent4", "mood": "smile", "seed": 44, "build": {"height": 0.75, "age": 0.85, "weight": 0.4},
     "cloak": ("rough_linen", (0.05, 0.06, 0.085), 3.0, 0.04), "skirt": ("poly_wool_herringbone", (0.08, 0.08, 0.09), 4.0, 0.04)},
    {"name": "opponent5", "mood": "sad", "seed": 57, "build": {"gender": 0.0, "height": 0.55, "weight": 0.4},
     "cloak": ("knitted_fleece", (0.03, 0.028, 0.03), 5.0, 0.03), "skirt": ("velour_velvet", (0.08, 0.02, 0.065), 3.0, 0.3)},
]
DEALERS = [
    {"name": "card_dealer", "mood": "smile", "seed": 2, "pose": "offering", "build": {"height": 0.65},
     "cloak": ("caban", (0.07, 0.06, 0.055), 2.5, 0.04), "skirt": ("poly_wool_herringbone", (0.1, 0.09, 0.08), 4.0, 0.04)},
    {"name": "piece_dealer", "mood": "sad", "seed": 7, "pose": "clasped", "build": {"height": 0.7, "weight": 0.6},
     "cloak": ("hessian_230", (0.22, 0.18, 0.13), 2.0, 0.0), "skirt": ("knitted_fleece", (0.05, 0.045, 0.04), 6.0, 0.03)},
    {"name": "reseller", "mood": "smile", "seed": 11, "pose": "clasped", "build": {"height": 0.3, "weight": 0.65},
     "cloak": ("velour_velvet", (0.07, 0.05, 0.09), 3.0, 0.3), "skirt": ("rough_linen", (0.06, 0.055, 0.05), 4.0, 0.04)},
]


def fabric(name, spec):
    tex, tint, scale, sheen = spec
    return realistic.fabric(name, tex, tint, scale=scale, sheen=sheen)


def collect(rig, name):
    """Moves the rig and everything under it into its own collection."""
    col = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(col)
    for o in [rig] + list(rig.children_recursive):
        for c in list(o.users_collection):
            c.objects.unlink(o)
        col.objects.link(o)
    return col


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    only = set(args[0].split(",")) if args else None
    t0 = time.time()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    mats = realistic.default_materials()

    # The props the seated opponents are posed against (removed before saving).
    table, table_objs = kit.import_model("round_wooden_table_01", Vector((0, -1.0, 0)), sit_on=0.0)
    table.scale.z *= TABLE_HEIGHT_SCALE
    chair, chair_objs = kit.import_model("WoodenChair_01", Vector((0, CHAIR_Y, 0)), sit_on=0.0)
    bpy.context.view_layer.update()
    lo, hi = kit.bounds(table_objs)
    seat = realistic.seat_height(chair_objs, (0, CHAIR_Y))
    props = [o for o in table_objs + chair_objs if o.type == "MESH"]

    for spec in OPPONENTS + DEALERS:
        name = spec["name"]
        if only and name not in only:
            continue
        seated = name.startswith("opponent")
        full = dict(spec)
        full["cloak"] = fabric(name + "_cloak", spec["cloak"])
        full["skirt"] = fabric(name + "_robe", spec["skirt"])
        if seated:
            full.update({"pose": "seated", "table_top": hi.z, "table_near_y": hi.y, "chair_y": CHAIR_Y, "seat_z": seat})
        rig = realistic.build_character(name, full, props if seated else (), mats)
        if seated:
            realistic.plant_hands(rig)
        realistic.animate(rig, spec["seed"], 0.7 if seated else 1.0)
        collect(rig, name)
        print("CHARACTER", name, "done at %.0f s" % (time.time() - t0))

    for root in (table, chair):
        for o in [root] + list(root.children_recursive):
            bpy.data.objects.remove(o, do_unlink=True)
    sc.frame_set(1)
    bpy.ops.wm.save_as_mainfile(filepath=OUT)
    print("SAVED", OUT, "in %.0f s" % (time.time() - t0))


main()
