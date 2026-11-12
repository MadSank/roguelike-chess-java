"""
Gives every character a mask that covers the whole face, and hides the face behind it.

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\fit_masks.py [-- preview <folder>] [--dry]

Why: the generated human faces don't hold up, so no part of a face may show. Each character in
tools/blender/assets/characters.blend gets
  - a full-face mask of a kind real people wear: opponent 1 a real scanned gas mask (Poly Haven,
    CC0; fetch_assets.py), the others a plague doctor's mask and hat, an old hockey goalie mask, a
    burlap sack and a welder's helmet; the dealers a Venetian bauta, a fencing mask and a balaclava
    (worn_masks.py). Anyone not listed in MASK_FOR keeps the theatre mask, enlarged.
  - a matte black cowl under it (a close black covering over the front of the head), so eye holes
    and any gap at the rim show only black.
Hands, neck, ears and hair stay visible: they are what makes the figure read as a person.
Run it again after make_characters.py rebuilds anyone (it only touches characters not yet fitted).
"preview <folder>" also renders each head from the front and the side; "--dry" doesn't save.
"""
import bmesh
import bpy
import math
import os
import sys

from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import masks  # noqa: E402
import worn_masks  # noqa: E402
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

BLEND = os.path.join(kit.ASSETS, "characters.blend")
UNIT = 0.095 * realistic.MASK_SCALE
GROW = 1.22          # theatre masks: how much larger they become
BACK = 0.012         # ...and how far they move back toward the face (metres)

# Scanned masks. All of these models already face -Y with +Z up, in metres.
#   scale     enlargement to suit a head
#   eyes      where the wearer's eyes go: (metres behind the front-most point, height in the model), before scaling
#   keep      metres kept behind the front-most point (None = everything): makes a mask of a whole head
#   z_range   only this height band of the model is kept (drops plinths, necks, hoses), before scaling
#   pitch     degrees tipped forward; hide: body parts of the wearer to remove (they would poke through)
MASK_FOR = {
    # A real scanned gas mask (Poly Haven), over the whole head.
    "opponent1": {"model": "old_gas_mask", "scale": 1.12, "eyes": (0.075, 0.02), "keep": None, "z_range": (-0.3, 9), "pitch": 0,
                  "whole_head": True},
    # Masks people really wear, built to their real shapes (worn_masks.py).
    "opponent2": {"worn": "plague_doctor", "whole_head": True},
    "opponent3": {"worn": "hockey", "whole_head": False},
    "opponent4": {"worn": "sack", "whole_head": True},
    "opponent5": {"worn": "welding", "whole_head": False},
    # The dealers: a gambler's bauta, a fencing mask, a balaclava.
    "card_dealer": {"worn": "bauta", "whole_head": False},
    "piece_dealer": {"worn": "fencing", "whole_head": False},
    "reseller": {"worn": "balaclava", "whole_head": True},
}
HAIR_PARTS = ("short", "bob", "afro", "long", "ponytail", "braid", "eyebrow", "eyelash")


def void_material():
    m = bpy.data.materials.get("cowl_black")
    if m is None:
        m = kit.plain("cowl_black", (0.004, 0.004, 0.004), rough=1.0)
        m.node_tree.nodes["Principled BSDF"].inputs["Specular IOR Level"].default_value = 0.0
        m.use_fake_user = True
    return m


def load_scan(name, cfg):
    """The scanned object as one mesh: cut down, scaled, thinned for real-time use, its origin at the eye point."""
    root, new = kit.import_model(cfg["model"], Vector((0, 0, 0)))
    bpy.context.view_layer.update()
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
    for o in [root] + new:
        if o != obj and o.name in bpy.data.objects:
            bpy.data.objects.remove(o, do_unlink=True)
    me = obj.data
    me.transform(world)
    obj.matrix_world = Matrix.Identity(4)

    bm = bmesh.new()
    bm.from_mesh(me)
    z0, z1 = cfg["z_range"]
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not z0 <= v.co.z <= z1], context="VERTS")
    front = min(v.co.y for v in bm.verts)
    if cfg["keep"] is not None:
        bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.y > front + cfg["keep"]], context="VERTS")
    behind, height = cfg["eyes"]
    centre_x = (min(v.co.x for v in bm.verts) + max(v.co.x for v in bm.verts)) / 2
    bmesh.ops.translate(bm, verts=bm.verts, vec=-Vector((centre_x, front + behind, height)))
    bmesh.ops.scale(bm, verts=bm.verts, vec=(cfg["scale"],) * 3)
    bm.to_mesh(me)
    bm.free()
    if len(me.polygons) > 30000:
        thin = obj.modifiers.new("thin", "DECIMATE")
        thin.ratio = 30000 / len(me.polygons)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.modifier_apply(modifier=thin.name)
    obj.name = name + "_mask"
    return obj


def fit(col, name):
    rig = next((o for o in col.objects if o.type == "ARMATURE" and o.parent is None), None)
    mask = next((o for o in col.objects if o.name == name + "_mask"), None)
    if rig is None or mask is None:
        return False
    if mask.get("fitted"):
        print("ALREADY", name)
        return True
    # The mask's frame at the wearer's face: x right, y back, z up, metres; its origin sits 0.04 m
    # behind the nose tip and EYE_Y * UNIT below the eyes.
    s = mask.matrix_world.to_scale()
    frame = mask.matrix_world @ Matrix.Diagonal((1 / s.x, 1 / s.y, 1 / s.z, 1)) @ Matrix.Rotation(-math.pi / 2, 4, "X")
    eye_up = masks.EYE_Y * UNIT
    bone = mask.parent_bone
    cfg = MASK_FOR.get(name)

    if cfg is None:                                    # the theatre mask, enlarged
        mask.matrix_world = (frame @ Matrix.Translation((0, BACK, eye_up * (1 - GROW)))
                             @ Matrix.Rotation(math.pi / 2, 4, "X") @ Matrix.Diagonal((UNIT * GROW,) * 3 + (1,)))
    else:                                              # a scanned object in its place
        bpy.data.objects.remove(mask, do_unlink=True)
        mask = load_scan(name, cfg) if "model" in cfg else worn_masks.build(cfg["worn"], name + "_mask", seed=len(name) + ord(name[-1]))
        for c in list(mask.users_collection):
            c.objects.unlink(mask)
        col.objects.link(mask)
        world = frame @ Matrix.Translation((0, -0.058, eye_up)) @ Matrix.Rotation(math.radians(cfg.get("pitch", 0)), 4, "X")
        mask.parent = rig
        mask.parent_type = "BONE"
        mask.parent_bone = bone
        mask.matrix_world = world
        if cfg["whole_head"]:                          # hair and brows would poke through a whole-head mask
            for o in list(col.objects):
                if o.type == "MESH" and o != mask and any(part in o.name for part in HAIR_PARTS):
                    bpy.data.objects.remove(o, do_unlink=True)
    mask["fitted"] = True

    # The cowl: a black shell over the front of the head, inside the mask.
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=20, radius=1.0)
    cowl = bpy.context.active_object
    cowl.name = name + "_cowl"
    for c in list(cowl.users_collection):
        c.objects.unlink(cowl)
    col.objects.link(cowl)
    cowl.data.materials.append(void_material())
    for p in cowl.data.polygons:
        p.use_smooth = True
    # (Theatre masks sit closer to the face than the scanned ones, so their cowl sits further back.)
    back = 0.03 if cfg else 0.062
    world = frame @ Matrix.Translation((0, back, eye_up - 0.02)) @ Matrix.Diagonal((0.085, 0.085, 0.125, 1))
    if cfg and cfg["whole_head"]:                      # under a whole-head mask the cowl covers the whole head
        world = frame @ Matrix.Translation((0, 0.05, eye_up + 0.005)) @ Matrix.Diagonal((0.087, 0.104, 0.128, 1))
    cowl.parent = rig
    cowl.parent_type = "BONE"
    cowl.parent_bone = bone
    cowl.matrix_world = world
    print("FITTED", name, (cfg.get("model") or cfg.get("worn")) if cfg else "theatre mask")
    return True


def preview(folder, names):
    sc = bpy.context.scene
    kit.setup_world(sc, haze=0.0)
    kit.setup_cycles(sc, threshold=0.05, max_samples=96)
    sc.render.use_persistent_data = False
    sc.render.resolution_x, sc.render.resolution_y = 480, 480
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.lens = 70
    for name in names:
        for c in bpy.data.collections:
            c.hide_render = c.name != name
        rig = next(o for o in bpy.data.collections[name].objects if o.type == "ARMATURE" and o.parent is None)
        bpy.context.view_layer.update()
        head = rig.matrix_world @ rig.pose.bones["head"].head + Vector((0, 0, 0.08))
        lights = [kit.light("AREA", tuple(head + Vector((0.5, -1.6, 0.65))), 60, (1.0, 0.85, 0.7), size=0.5, target=tuple(head)),
                  kit.light("AREA", tuple(head + Vector((-1.3, -0.4, 0.25))), 12, (0.7, 0.8, 1.0), size=0.8, target=tuple(head))]
        for label, offset in (("front", Vector((0.0, -1.5, 0.0))), ("side", Vector((1.3, -0.75, 0.05)))):
            cam.location = head + offset
            cam.rotation_euler = (head - cam.location).to_track_quat("-Z", "Y").to_euler()
            sc.render.filepath = os.path.join(folder, "%s_%s.png" % (name, label))
            bpy.ops.render.render(write_still=True)
        for o in lights:
            bpy.data.objects.remove(o, do_unlink=True)
    bpy.data.objects.remove(cam, do_unlink=True)
    for c in bpy.data.collections:
        c.hide_render = False


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    bpy.ops.wm.open_mainfile(filepath=BLEND)
    bpy.context.scene.frame_set(1)
    done = [c.name for c in list(bpy.data.collections) if fit(c, c.name)]
    if "preview" in args:
        folder = os.path.abspath(args[args.index("preview") + 1])
        os.makedirs(folder, exist_ok=True)
        preview(folder, sorted(done))
    if "--dry" not in args:
        bpy.ops.wm.save_mainfile()
        print("SAVED", BLEND)
    print("DONE")


main()
