"""
Look test for the realistic direction: the same two shots rendered with EEVEE and Cycles.

Run (after fetch_assets.py):
    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\look_test.py -- <output folder>

Writes pov_eevee.png, pov_cycles.png, dealer_eevee.png, dealer_cycles.png and times.txt.
The masked figures are stand-ins (robe, hood, porcelain mask) until real characters exist.
"""
import bpy
import math
import os
import sys
import time

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import masks  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")
args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(args[0]) if args else os.path.join(HERE, "look_test_out")
os.makedirs(OUT, exist_ok=True)

WIDTH, HEIGHT = 1280, 720
SEAT = Vector((0.0, -0.95, 0.0))
OPPONENT = Vector((0.0, 0.95, 0.0))
CARD_DEALER = Vector((-1.45, -0.55, 0.0))
CHESS_ROT = float(os.environ.get("CHESS_ROT", "0"))


# ─── MATERIALS ───────────────────────────────────────────────────────────────
def pbr(name, tex_id, scale=1.0):
    """Poly Haven texture set (colour, normal, roughness) with a tiling scale."""
    folder = os.path.join(ASSETS, "textures", tex_id)
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    coord = nt.nodes.new("ShaderNodeTexCoord")
    mapping = nt.nodes.new("ShaderNodeMapping")
    mapping.inputs["Scale"].default_value = (scale, scale, scale)
    nt.links.new(coord.outputs["UV"], mapping.inputs["Vector"])

    def tex(suffix, colour):
        n = nt.nodes.new("ShaderNodeTexImage")
        n.image = bpy.data.images.load(os.path.join(folder, "%s_%s.jpg" % (tex_id, suffix)))
        if not colour:
            n.image.colorspace_settings.name = "Non-Color"
        nt.links.new(mapping.outputs["Vector"], n.inputs["Vector"])
        return n

    nt.links.new(tex("diff", True).outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex("rough", False).outputs["Color"], bsdf.inputs["Roughness"])
    nmap = nt.nodes.new("ShaderNodeNormalMap")
    nt.links.new(tex("nor_gl", False).outputs["Color"], nmap.inputs["Color"])
    nt.links.new(nmap.outputs["Normal"], bsdf.inputs["Normal"])
    return m


def plain(name, colour, rough=0.8, metallic=0.0, sheen=0.0, coat=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*colour, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metallic
    if sheen:
        b.inputs["Sheen Weight"].default_value = sheen
    if coat:
        b.inputs["Coat Weight"].default_value = coat
    return m


# ─── SCENE ───────────────────────────────────────────────────────────────────
def plane(size, loc, rot, mat, uv_scale):
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = (size[0], size[1], 1)
    bpy.ops.object.transform_apply(scale=True)
    # Scale UVs so the texture tiles at real-world size.
    for loop in o.data.uv_layers.active.data:
        loop.uv = (loop.uv[0] * size[0] / uv_scale, loop.uv[1] * size[1] / uv_scale)
    o.data.materials.append(mat)
    return o


def import_model(asset_id, loc, rot_z=0.0, scale=1.0, sit_on=None):
    """Imports a Poly Haven glTF, parents it to an empty and places it. sit_on: z to rest the model's base on."""
    folder = os.path.join(ASSETS, "models", asset_id)
    gltf = [f for f in os.listdir(folder) if f.endswith(".gltf")][0]
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.join(folder, gltf))
    new = [o for o in bpy.data.objects if o not in before]
    root = bpy.data.objects.new(asset_id, None)
    bpy.context.collection.objects.link(root)
    for o in new:
        if o.parent is None:
            o.parent = root
    root.scale = (scale, scale, scale)
    root.rotation_euler = (0, 0, rot_z)
    root.location = loc
    bpy.context.view_layer.update()
    if sit_on is not None:
        lo, hi = bounds(new)
        root.location.z += sit_on - lo.z
        bpy.context.view_layer.update()
    return root, new


def bounds(objs):
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for o in objs:
        if o.type != "MESH":
            continue
        for c in o.bound_box:
            w = o.matrix_world @ Vector(c)
            lo = Vector((min(lo.x, w.x), min(lo.y, w.y), min(lo.z, w.z)))
            hi = Vector((max(hi.x, w.x), max(hi.y, w.y), max(hi.z, w.z)))
    return lo, hi


def masked_figure(pos, facing, wool, porcelain, dark, seated=False, mood="smile", seed=1):
    """Stand-in: hooded robe with a porcelain mask. facing = Z rotation (0 faces +Y)."""
    fx, fy = -math.sin(facing), math.cos(facing)
    base = 0.42 if seated else 0.0
    h = 1.0 if seated else 1.45
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=0.36, radius2=0.17, depth=h,
                                    location=(pos.x, pos.y, base + h / 2))
    robe = bpy.context.active_object
    robe.data.materials.append(wool)
    bpy.ops.object.shade_smooth()
    head_z = base + h + 0.14
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(pos.x - fx * 0.03, pos.y - fy * 0.03, head_z))
    hood = bpy.context.active_object
    hood.scale = (0.17, 0.17, 0.21)
    hood.data.materials.append(wool)
    bpy.ops.object.shade_smooth()
    # Theatre mask (masks.py): faces -Y as built, so turn it to face where the figure faces.
    mask = masks.make_mask("mask_" + mood, mood, seed=seed)
    mask.location = (pos.x + fx * 0.115, pos.y + fy * 0.115, head_z - 0.01)
    mask.rotation_euler = (math.pi / 2, 0, facing + math.pi)
    # A dark void behind the mask so the eye holes read as black.
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(pos.x + fx * 0.05, pos.y + fy * 0.05, head_z))
    void = bpy.context.active_object
    void.scale = (0.1, 0.1, 0.13)
    void.data.materials.append(dark)


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = WIDTH, HEIGHT
    sc.render.image_settings.file_format = "PNG"
    sc.view_settings.view_transform = "AgX"
    sc.view_settings.look = "AgX - Medium High Contrast"

    world = bpy.data.worlds.new("World")
    sc.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.002, 0.002, 0.002, 1)
    # Haze in the air, so the lamp throws a visible cone of light.
    vol = world.node_tree.nodes.new("ShaderNodeVolumePrincipled")
    vol.inputs["Density"].default_value = 0.012
    world.node_tree.links.new(vol.outputs[0], world.node_tree.nodes["World Output"].inputs["Volume"])

    walls = pbr("walls", "plaster_brick_01")
    floor = pbr("floor", "concrete_floor_worn_001")
    ceiling = plain("ceiling", (0.05, 0.048, 0.045), rough=0.95)
    R, H = 3.2, 3.0
    plane((2 * R, 2 * R), (0, 0, 0), (0, 0, 0), floor, 2.0)
    plane((2 * R, 2 * R), (0, 0, H), (math.pi, 0, 0), ceiling, 2.0)
    plane((2 * R, H), (0, R, H / 2), (math.pi / 2, 0, math.pi), walls, 2.0)
    plane((2 * R, H), (0, -R, H / 2), (math.pi / 2, 0, 0), walls, 2.0)
    plane((2 * R, H), (R, 0, H / 2), (math.pi / 2, 0, math.pi / 2), walls, 2.0)
    plane((2 * R, H), (-R, 0, H / 2), (math.pi / 2, 0, -math.pi / 2), walls, 2.0)

    table, table_objs = import_model("round_wooden_table_01", Vector((0, 0, 0)), sit_on=0.0)
    lo, hi = bounds(table_objs)
    top = hi.z
    import_model("chess_set", Vector((0, 0, 0)), rot_z=CHESS_ROT, sit_on=top)
    import_model("WoodenChair_01", Vector((OPPONENT.x, OPPONENT.y + 0.1, 0)), rot_z=0.0, sit_on=0.0)  # back behind the opponent
    import_model("hanging_industrial_lamp", Vector((0, 0, 0)), scale=0.45, sit_on=1.95)
    import_model("wooden_crate_01", Vector((-2.4, 1.9, 0)), rot_z=0.3, sit_on=0.0)
    import_model("wooden_crate_01", Vector((-2.35, 1.85, 0.55)), rot_z=-0.2, sit_on=None)
    import_model("Barrel_01", Vector((2.3, 2.1, 0)), sit_on=0.0)
    import_model("wine_bottles_01", Vector((2.35, 2.1, 0)), sit_on=1.0)
    import_model("brass_candleholders", Vector((-1.75, -0.05, 0)), scale=0.7, sit_on=0.98)

    wool = plain("wool", (0.03, 0.026, 0.022), rough=0.95, sheen=0.15)
    porcelain = plain("porcelain", (0.82, 0.79, 0.72), rough=0.22, coat=0.6)
    dark = plain("eyeholes", (0.0, 0.0, 0.0), rough=1.0)
    masked_figure(OPPONENT, math.pi, wool, porcelain, dark, seated=True, mood="sad", seed=5)
    masked_figure(CARD_DEALER, math.atan2(SEAT.y - CARD_DEALER.y, SEAT.x - CARD_DEALER.x) - math.pi / 2,
                  wool, porcelain, dark, mood="smile", seed=2)
    # A small stand for the card dealer's candles.
    bpy.ops.mesh.primitive_cylinder_add(radius=0.2, depth=0.98, location=(-1.75, -0.05, 0.49))
    bpy.context.active_object.data.materials.append(plain("stand", (0.08, 0.05, 0.03), rough=0.6))

    # Lights: a warm pool from the hanging lamp, a dim bulb glow, a candle flicker by the dealer.
    spot = bpy.data.lights.new("lamp_spot", "SPOT")
    spot.energy = 650
    spot.color = (1.0, 0.8, 0.6)
    spot.spot_size = math.radians(85)
    spot.spot_blend = 0.6
    spot.shadow_soft_size = 0.08
    o = bpy.data.objects.new("lamp_spot", spot)
    o.location = (0, 0, 1.93)
    bpy.context.collection.objects.link(o)
    bulb = bpy.data.lights.new("bulb", "POINT")
    bulb.energy = 25
    bulb.color = (1.0, 0.7, 0.4)
    bulb.shadow_soft_size = 0.05
    o = bpy.data.objects.new("bulb", bulb)
    o.location = (0, 0, 1.97)
    bpy.context.collection.objects.link(o)
    candle = bpy.data.lights.new("candle", "POINT")
    candle.energy = 12
    candle.color = (1.0, 0.55, 0.25)
    candle.shadow_soft_size = 0.02
    o = bpy.data.objects.new("candle", candle)
    o.location = (-1.75, -0.05, 1.25)
    bpy.context.collection.objects.link(o)

    # Rim lights behind the opponent and the dealer, so dark robes separate from the dark room.
    for src, dst, energy in (((0.3, 2.3, 2.1), OPPONENT + Vector((0, 0, 1.3)), 45),
                             ((-2.6, -1.1, 2.2), CARD_DEALER + Vector((0, 0, 1.4)), 30)):
        rim = bpy.data.lights.new("rim", "AREA")
        rim.energy = energy
        rim.color = (1.0, 0.6, 0.35)
        rim.size = 0.6
        o = bpy.data.objects.new("rim", rim)
        o.location = src
        o.rotation_euler = (dst - Vector(src)).to_track_quat("-Z", "Y").to_euler()
        bpy.context.collection.objects.link(o)

    cam_data = bpy.data.cameras.new("Cam")
    cam_data.lens = 32
    cam_data.dof.use_dof = True
    cam_data.dof.aperture_fstop = 2.2
    cam = bpy.data.objects.new("Cam", cam_data)
    bpy.context.collection.objects.link(cam)
    sc.camera = cam
    return sc, cam, top


def aim(cam, eye, target, focus):
    cam.location = eye
    d = (target - eye).normalized()
    cam.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    cam.data.dof.focus_distance = (focus - eye).length


def use_eevee(sc):
    sc.render.engine = "BLENDER_EEVEE"
    e = sc.eevee
    for attr, value in (("taa_render_samples", 64), ("use_raytracing", True), ("use_shadows", True),
                        ("volumetric_tile_size", "4"), ("volumetric_samples", 96)):
        try:
            setattr(e, attr, value)
        except Exception as ex:
            print("eevee setting skipped:", attr, ex)


def use_cycles(sc):
    try:
        bpy.ops.preferences.addon_enable(module="cycles")
    except Exception as ex:
        print("cycles addon:", ex)
    sc.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for kind in ("OPTIX", "CUDA"):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                for d in prefs.devices:
                    d.use = d.type == kind
                sc.cycles.device = "GPU"
                print("Cycles device:", kind)
                break
        except Exception as ex:
            print("device", kind, ex)
    c = sc.cycles
    c.use_adaptive_sampling = True
    c.adaptive_threshold = 0.03
    c.samples = 256
    c.use_denoising = True
    c.max_bounces = 6
    c.volume_bounces = 1


def main():
    sc, cam, top = build()
    shots = {
        # Seated, looking across the board at the opponent.
        "pov": (Vector((0, -1.05, 1.32)), Vector((0, 0.6, 1.12)), Vector((0, 0.84, 1.55))),
        # Turned left, toward the card dealer.
        "dealer": (Vector((0, -0.92, 1.2)), CARD_DEALER + Vector((0.05, 0, 1.45)), CARD_DEALER + Vector((0, 0, 1.55))),
    }
    times = []
    engines = os.environ.get("ENGINES", "eevee,cycles").split(",")
    for engine, setup in (("eevee", use_eevee), ("cycles", use_cycles)):
        if engine not in engines:
            continue
        setup(sc)
        for name, (eye, target, focus) in shots.items():
            aim(cam, eye, target, focus)
            sc.render.filepath = os.path.join(OUT, "%s_%s.png" % (name, engine))
            t0 = time.time()
            bpy.ops.render.render(write_still=True)
            dt = time.time() - t0
            times.append("%s %s: %.1f s" % (name, engine, dt))
            print("RENDERED", name, engine, "%.1f s" % dt)
    with open(os.path.join(OUT, "times.txt"), "w") as f:
        f.write("\n".join(times) + "\n")
    print("DONE", OUT)


main()
