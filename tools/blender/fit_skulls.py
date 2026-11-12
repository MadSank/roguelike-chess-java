"""
Replaces the opponents' theatre masks with real skulls: 3D scans from the Smithsonian's open-access
collection (public domain, CC0), worn over the face and held by the same leather straps.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\fit_skulls.py [-- preview <folder>]

It edits tools/blender/assets/characters.blend in place: each opponent's "<name>_mask" object is
replaced by a skull on the head bone. Run it again after make_characters.py rebuilds anyone.
With "preview <folder>" it also renders a close-up of each opponent's head (front and side).

The scans (download once into tools/blender/assets/skulls/, "high" 150k glb from https://3d.si.edu):
  gorilla.glb         Gorilla gorilla gorilla: Cranium          symphalangus.glb  Symphalangus syndactylus (siamang): Cranium
  pongo.glb           Pongo pygmaeus (orangutan): cranium        archaeotherium.glb  Archaeotherium mortoni ("hell pig"): Skull
  hoplophoneus.glb    Hoplophoneus primaevus (a sabre-toothed cat)

Each scan is first turned to a common frame (snout toward -Y, crown toward +Z, metres), scaled to
suit a human face, optionally cut off behind the eye sockets (so it is a mask, not a helmet the head
would poke through), thinned out for real-time use, and placed by its eye sockets over the wearer's eyes.
"""
import bmesh
import bpy
import math
import os
import sys

from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import masks  # noqa: E402
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

BLEND = os.path.join(kit.ASSETS, "characters.blend")
SKULLS = os.path.join(kit.ASSETS, "skulls")
UNIT = 0.095 * realistic.MASK_SCALE

# to_common: rows of the matrix taking the scan's axes to (right, back, up). unit: metres per scan unit.
# scale: enlargement to mask size. sockets: where the eye sockets are, as (metres behind the snout tip,
# fraction of the height from the bottom), after scaling. keep: metres of skull kept behind the snout tip
# (None = all of it). pitch: degrees the snout is tipped downward. lift: metres it is raised (to cover the
# wearer's eyes when tipped). tint: bone colour for untextured scans.
IDENTITY = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
FLIPPED = ((-1, 0, 0), (0, 1, 0), (0, 0, -1))       # these two scans lie upside down
SKULL_FOR = {
    "opponent1": {"file": "symphalangus", "to_common": IDENTITY, "unit": 0.001, "scale": 2.0, "sockets": (0.07, 0.68),
                  "keep": 0.12, "pitch": 0, "lift": 0.0, "tint": (0.78, 0.72, 0.6)},
    "opponent2": {"file": "pongo", "to_common": FLIPPED, "unit": 0.001, "scale": 1.3, "sockets": (0.09, 0.69),
                  "keep": 0.13, "pitch": 0, "lift": 0.0, "tint": (0.8, 0.75, 0.64)},
    "opponent3": {"file": "gorilla", "to_common": FLIPPED, "unit": 0.001, "scale": 0.95, "sockets": (0.12, 0.76),
                  "keep": 0.16, "pitch": 0, "lift": 0.0, "tint": (0.7, 0.63, 0.5)},
    "opponent4": {"file": "archaeotherium", "to_common": ((0, 0, -1), (-1, 0, 0), (0, 1, 0)), "unit": 1.0, "scale": 1.0,
                  "sockets": (0.2, 0.75), "keep": None, "pitch": 32, "lift": 0.035, "tint": None},
    "opponent5": {"file": "hoplophoneus", "to_common": ((0, -1, 0), (1, 0, 0), (0, 0, 1)), "unit": 1.0, "scale": 1.25,
                  "sockets": (0.15, 0.7), "keep": None, "pitch": 26, "lift": 0.04, "tint": None},
}


def load_skull(name, cfg):
    """The scan as one mesh in the common frame, scaled, cut and thinned; origin at its eye sockets."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.join(SKULLS, cfg["file"] + ".glb"))
    new = [o for o in bpy.data.objects if o not in before]
    parts = [o for o in new if o.type == "MESH"]
    bpy.ops.object.select_all(action="DESELECT")
    for o in parts:
        o.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    if len(parts) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    world = obj.matrix_world.copy()
    obj.parent = None
    for o in new:
        if o != obj and o.name in bpy.data.objects:
            bpy.data.objects.remove(o, do_unlink=True)

    turn = Matrix(cfg["to_common"]).to_4x4()
    k = cfg["unit"] * cfg["scale"]
    me = obj.data
    me.transform(Matrix.Scale(k, 4) @ turn @ world)
    obj.matrix_world = Matrix.Identity(4)
    me.update()

    xs = [v.co.x for v in me.vertices]
    ys = [v.co.y for v in me.vertices]
    zs = [v.co.z for v in me.vertices]
    snout, low, high = min(ys), min(zs), max(zs)
    behind, up = cfg["sockets"]
    origin = Vector(((min(xs) + max(xs)) / 2, snout + behind, low + (high - low) * up))
    me.transform(Matrix.Translation(-origin))
    if cfg["keep"] is not None:                        # a mask: drop everything well behind the eye sockets
        bm = bmesh.new()
        bm.from_mesh(me)
        limit = cfg["keep"] - behind
        bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.y > limit], context="VERTS")
        bm.to_mesh(me)
        bm.free()
    # Weld the scan's seams (or thinning it tears it into shards), thin it out, and smooth the shading.
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.00005)
    bm.to_mesh(me)
    bm.free()
    thin = obj.modifiers.new("thin", "DECIMATE")
    thin.ratio = min(1.0, 24000 / max(1, len(me.polygons)))
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.modifier_apply(modifier=thin.name)
    me = obj.data
    if me.has_custom_normals:
        bpy.ops.mesh.customdata_custom_splitnormals_clear()
    for p in me.polygons:
        p.use_smooth = True
    if cfg["tint"]:                                    # CT scans come without colour: plain old bone
        obj.data.materials.clear()
        obj.data.materials.append(kit.plain(name + "_bone", cfg["tint"], rough=0.62))
    obj.name = name + "_mask"
    return obj


def fit(col, name, cfg):
    rig = next(o for o in col.objects if o.type == "ARMATURE" and o.parent is None)
    old = next((o for o in col.objects if o.name == name + "_mask"), None)
    if old is None:
        print("NO MASK on", name)
        return None
    # The old mask's frame: its origin is in front of the face, 0.04 m behind the nose tip and
    # EYE_Y * UNIT below the eyes, with the wearer's right / back / up as x / y / z.
    scale = old.matrix_world.to_scale()
    frame = old.matrix_world @ Matrix.Diagonal((1 / scale.x, 1 / scale.y, 1 / scale.z, 1)) @ Matrix.Rotation(-math.pi / 2, 4, "X")
    bone = old.parent_bone
    bpy.data.objects.remove(old, do_unlink=True)

    skull = load_skull(name, cfg)
    for c in list(skull.users_collection):
        c.objects.unlink(skull)
    col.objects.link(skull)
    eyes = Matrix.Translation((0, -0.062, masks.EYE_Y * UNIT + cfg["lift"]))   # just in front of the wearer's eyes
    tip = Matrix.Rotation(math.radians(cfg["pitch"]), 4, "X")            # snout down
    world = frame @ eyes @ tip
    skull.parent = rig
    skull.parent_type = "BONE"
    skull.parent_bone = bone
    skull.matrix_world = world
    print("FITTED", name, cfg["file"], "faces", len(skull.data.polygons))
    return rig


def preview(folder, names):
    sc = bpy.context.scene
    kit.setup_world(sc, haze=0.0)
    kit.setup_cycles(sc, threshold=0.05, max_samples=96)
    sc.render.use_persistent_data = False
    sc.render.resolution_x, sc.render.resolution_y = 560, 560
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.lens = 70
    kit.light("AREA", (0.5, -1.6, 1.9), 60, (1.0, 0.85, 0.7), size=0.5, target=(0, 0, 1.25))
    kit.light("AREA", (-1.3, -0.4, 1.5), 12, (0.7, 0.8, 1.0), size=0.8, target=(0, 0, 1.25))
    for name in names:
        for c in bpy.data.collections:
            c.hide_render = c.name.startswith(("opponent", "card_", "piece_", "resell")) and c.name != name
        rig = next(o for o in bpy.data.collections[name].objects if o.type == "ARMATURE" and o.parent is None)
        bpy.context.view_layer.update()
        head = rig.matrix_world @ rig.pose.bones["head"].head + Vector((0, 0, 0.08))
        for label, offset in (("front", Vector((0.0, -1.5, 0.0))), ("side", Vector((1.3, -0.75, 0.05)))):
            cam.location = head + offset
            cam.rotation_euler = (head - cam.location).to_track_quat("-Z", "Y").to_euler()
            sc.render.filepath = os.path.join(folder, "%s_%s.png" % (name, label))
            bpy.ops.render.render(write_still=True)


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    bpy.ops.wm.open_mainfile(filepath=BLEND)
    done = []
    for name, cfg in SKULL_FOR.items():
        col = bpy.data.collections.get(name)
        if col is not None and fit(col, name, cfg):
            done.append(name)
    bpy.context.scene.frame_set(1)
    if args and args[0] == "preview":
        os.makedirs(args[1], exist_ok=True)
        preview(os.path.abspath(args[1]), done)
        bpy.data.objects.remove(bpy.data.objects["Cam"], do_unlink=True)
        for o in [o for o in bpy.context.scene.collection.objects if o.type == "LIGHT"]:
            bpy.data.objects.remove(o, do_unlink=True)
        for c in bpy.data.collections:
            c.hide_render = False
    if "--dry" not in args:
        bpy.ops.wm.save_mainfile()
        print("SAVED", BLEND)
    print("DONE")


main()
