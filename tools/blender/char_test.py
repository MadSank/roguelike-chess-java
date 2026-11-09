"""
Close-up test of the characters: a standing dealer and the seated opponent at the table,
under warm light, rendered with Cycles. Optionally a frame of the idle loop.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\char_test.py -- <out folder> [frame]
"""
import bpy
import math
import os
import sys

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import characters  # noqa: E402
import scene_kit as kit  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(args[0]) if args else os.path.join(os.path.dirname(__file__), "char_test_out")
FRAME = int(args[1]) if len(args) > 1 else 1
os.makedirs(OUT, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.resolution_x, sc.render.resolution_y = 1280, 720
kit.setup_world(sc, haze=0.0)
kit.setup_cycles(sc, threshold=0.03, max_samples=192)
sc.render.use_persistent_data = False
floor = kit.plain("floor", (0.05, 0.045, 0.04), rough=0.9)
kit.plane((8, 8), (0, 0, 0), (0, 0, 0), floor)
wall = kit.plain("wall", (0.09, 0.075, 0.06), rough=0.95)
kit.plane((8, 3), (0, 2.2, 1.5), (math.pi / 2, 0, math.pi), wall)

characters.build_character("dealer", Vector((-0.55, 0.3, 0)), math.pi + 0.25, mood="smile", seed=2, pose="offering")
characters.build_character("opponent", Vector((0.6, 0.45, 0)), math.pi - 0.3, mood="sad", seed=5, seated=True, pose="table")
table = kit.cyl((0.65, -0.05, 0.37), 0.42, 0.03, kit.plain("table", (0.12, 0.05, 0.02), rough=0.5))
table.location.z = 0.74
stool = kit.cyl((0.6, 0.45, 0.22), 0.2, 0.44, kit.plain("stool", (0.08, 0.05, 0.03), rough=0.6))

kit.light("SPOT", (0.2, -0.6, 2.4), 500, (1.0, 0.78, 0.55), size=0.15, target=(0, 0.4, 1.0), spot_deg=70)
kit.light("AREA", (1.6, 1.6, 2.0), 60, (1.0, 0.6, 0.35), size=0.8, target=(0, 0.4, 1.2))
kit.light("AREA", (-1.8, 1.4, 2.0), 50, (1.0, 0.6, 0.35), size=0.8, target=(-0.5, 0.3, 1.3))

cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
cam.data.lens = 35
cam.data.dof.use_dof = True
cam.data.dof.aperture_fstop = 4.0
bpy.context.collection.objects.link(cam)
sc.camera = cam
eye, target = Vector((0.1, -2.6, 1.3)), Vector((0.05, 0.35, 1.05))
cam.location = eye
cam.rotation_euler = (target - eye).to_track_quat("-Z", "Y").to_euler()
cam.data.dof.focus_distance = (target - eye).length

sc.frame_set(FRAME)
sc.render.filepath = os.path.join(OUT, "characters_f%02d.png" % FRAME)
bpy.ops.render.render(write_still=True)
print("DONE", sc.render.filepath)
