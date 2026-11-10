"""
Look test for the realistic characters: one seated opponent at the real table and chair
(cloth simulated), rendered with Cycles from the player's seat and from a three-quarter view.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\real_test.py -- <out folder> [standing]
"""
import bpy
import math
import os
import sys
import time

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(args[0]) if args else os.path.join(os.path.dirname(__file__), "real_test_out")
STANDING = "standing" in args
os.makedirs(OUT, exist_ok=True)
t0 = time.time()

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.resolution_x, sc.render.resolution_y = 1280, 720
kit.setup_world(sc, haze=0.0)
floor = kit.pbr("concrete", "concrete_floor_worn_001")
kit.plane((8, 8), (0, 0, 0), (0, 0, 0), floor, 2.0)
wall = kit.pbr("brick", "plaster_brick_01")
kit.plane((8, 3), (0, 1.6, 1.5), (math.pi / 2, 0, 0), wall)

colliders = []
if STANDING:
    spec = {"pose": "offering", "mood": "smile", "seed": 2,
            "cloak": realistic.fabric("cloak", "caban", (0.12, 0.1, 0.09), scale=2.5),
            "skirt": realistic.fabric("skirt", "poly_wool_herringbone", (0.15, 0.13, 0.12), scale=4.0)}
else:
    table, table_objs = kit.import_model("round_wooden_table_01", Vector((0, -1.0, 0)), sit_on=0.0)
    table.scale.z *= 0.76                         # the model is bar height (1 m); make it a dining table
    bpy.context.view_layer.update()
    lo, hi = kit.bounds(table_objs)
    chair, chair_objs = kit.import_model("WoodenChair_01", Vector((0, 0.1, 0)), sit_on=0.0)
    chair.scale.x *= realistic.CHAIR_WIDEN
    seat = realistic.seat_height(chair_objs, (0, 0.1))
    colliders = [o for o in table_objs if o.type == "MESH"] + realistic.chair_proxy(0.1, seat)
    print("SEAT", seat)
    spec = {"pose": "seated", "mood": "sad", "seed": 5, "table_top": hi.z, "table_near_y": hi.y, "chair_y": 0.1,
            "seat_z": seat,
            "cloak": realistic.fabric("cloak", "caban", (0.1, 0.085, 0.075), scale=2.5),
            "skirt": realistic.fabric("skirt", "poly_wool_herringbone", (0.12, 0.1, 0.09), scale=4.0)}
    print("TABLE top", hi.z, "near edge y", hi.y)

rig = realistic.build_character("opponent", spec, colliders)
print("BUILT in %.0f s" % (time.time() - t0))
ANIM = "anim" in args
if ANIM:
    if not STANDING:
        realistic.plant_hands(rig)
    realistic.animate(rig, spec["seed"], 0.7 if not STANDING else 1.0)
    realistic.react(rig, "lose" if not STANDING else "yes")
    hand = rig.pose.bones["hand_r"]
    sc.frame_set(1)
    h1 = (rig.matrix_world @ hand.head).copy()
    sc.frame_set(30)
    h2 = (rig.matrix_world @ hand.head).copy()
    print("HAND drift between frame 1 and 30: %.3f m" % (h2 - h1).length)

kit.setup_cycles(sc, threshold=0.03, max_samples=192)
sc.render.use_persistent_data = False
kit.light("SPOT", (0, -0.5, 2.3), 500, (1.0, 0.8, 0.6), size=0.1, target=(0, -0.3, 0.9), spot_deg=80)
kit.light("AREA", (1.4, 1.2, 2.0), 60, (1.0, 0.6, 0.35), size=0.8, target=(0, 0, 1.2))
kit.light("AREA", (-1.6, -1.8, 1.8), 25, (0.8, 0.8, 0.9), size=1.0, target=(0, 0, 1.1))

cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
cam.data.dof.use_dof = True
cam.data.dof.aperture_fstop = 2.8
bpy.context.collection.objects.link(cam)
sc.camera = cam
shots = [("seat", Vector((0, -1.95, 1.15)), Vector((0, 0, 1.15)), 35),
         ("three_quarter", Vector((1.5, -1.6, 1.5)), Vector((0, 0, 1.1)), 40),
         ("close", Vector((0.25, -0.95, 1.35)), Vector((0, -0.1, 1.25)), 50)]
if STANDING:
    shots = [("seat", Vector((0, -2.6, 1.3)), Vector((0, 0, 1.05)), 32),
             ("three_quarter", Vector((1.6, -2.0, 1.6)), Vector((0, 0, 1.15)), 40),
             ("close", Vector((0.3, -1.1, 1.7)), Vector((0, -0.1, 1.55)), 50)]
if ANIM:
    shots = [(label + "_f%d" % f, eye, target, lens, f) for f in (1, 30) for label, eye, target, lens in shots[:1]]
else:
    shots = [(label, eye, target, lens, 1) for label, eye, target, lens in shots]
for label, eye, target, lens, frame in shots:
    sc.frame_set(frame)
    cam.data.lens = lens
    cam.location = eye
    cam.rotation_euler = (target - eye).to_track_quat("-Z", "Y").to_euler()
    cam.data.dof.focus_distance = (target - eye).length
    sc.render.filepath = os.path.join(OUT, "%s_%s.png" % ("standing" if STANDING else "seated", label))
    bpy.ops.render.render(write_still=True)
    print("SHOT", sc.render.filepath)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "standing.blend" if STANDING else "seated.blend"))
print("DONE in %.0f s" % (time.time() - t0))
