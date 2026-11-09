"""
Close-up of the two masks (smiling and sad) under warm lamp light.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\mask_test.py -- <output.png>
"""
import bpy
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import masks  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(args[0]) if args else os.path.join(os.path.dirname(__file__), "masks.png")

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.resolution_x, sc.render.resolution_y = 1280, 720
sc.view_settings.view_transform = "AgX"
sc.view_settings.look = "AgX - Medium High Contrast"
world = bpy.data.worlds.new("W")
sc.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.004, 0.0035, 0.003, 1)

smile = masks.make_mask("smile", "smile", seed=2)
smile.location = (-0.13, 0, 0)
smile.rotation_euler = (math.pi / 2, 0, math.radians(12))
sad = masks.make_mask("sad", "sad", seed=5)
sad.location = (0.13, 0, 0)
sad.rotation_euler = (math.pi / 2, 0, math.radians(-12))

for loc, energy, colour in (((-0.6, -0.8, 0.7), 60, (1.0, 0.75, 0.5)), ((0.7, 0.6, 0.3), 25, (1.0, 0.45, 0.2))):
    data = bpy.data.lights.new("L", "AREA")
    data.energy = energy
    data.color = colour
    data.size = 0.4
    o = bpy.data.objects.new("L", data)
    o.location = loc
    o.rotation_euler = (0, 0, 0)
    direction = -o.location
    o.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    bpy.context.collection.objects.link(o)

cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
cam.data.lens = 60
bpy.context.collection.objects.link(cam)
cam.location = (0, -0.75, 0.02)
cam.rotation_euler = (math.pi / 2, 0, 0)
sc.camera = cam

bpy.ops.preferences.addon_enable(module="cycles")
sc.render.engine = "CYCLES"
prefs = bpy.context.preferences.addons["cycles"].preferences
prefs.compute_device_type = "OPTIX"
prefs.get_devices()
for d in prefs.devices:
    d.use = d.type == "OPTIX"
sc.cycles.device = "GPU"
sc.cycles.samples = 256
sc.cycles.adaptive_threshold = 0.03
sc.cycles.use_denoising = True
sc.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print("DONE", OUT)
