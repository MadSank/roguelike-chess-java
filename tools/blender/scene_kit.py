"""
Shared Blender helpers for the Ashes scenes: Poly Haven materials and models, simple geometry,
lights, the masked figures and render settings. Used by build_scenes.py and look_test.py.
"""
import bpy
import math
import os

from mathutils import Vector

import masks

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")


# ─── MATERIALS ───────────────────────────────────────────────────────────────
def pbr(name, tex_id):
    """Poly Haven texture set (colour, normal, roughness), mapped with the mesh's UVs."""
    folder = os.path.join(ASSETS, "textures", tex_id)
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]

    def tex(suffix, colour):
        n = nt.nodes.new("ShaderNodeTexImage")
        n.image = bpy.data.images.load(os.path.join(folder, "%s_%s.jpg" % (tex_id, suffix)), check_existing=True)
        if not colour:
            n.image.colorspace_settings.name = "Non-Color"
        return n

    nt.links.new(tex("diff", True).outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex("rough", False).outputs["Color"], bsdf.inputs["Roughness"])
    nmap = nt.nodes.new("ShaderNodeNormalMap")
    nt.links.new(tex("nor_gl", False).outputs["Color"], nmap.inputs["Color"])
    nt.links.new(nmap.outputs["Normal"], bsdf.inputs["Normal"])
    return m


def plain(name, colour, rough=0.8, metallic=0.0, sheen=0.0, coat=0.0, emission=None, strength=0.0):
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
    if emission:
        b.inputs["Emission Color"].default_value = (*emission, 1)
        b.inputs["Emission Strength"].default_value = strength
    return m


# ─── GEOMETRY ────────────────────────────────────────────────────────────────
def plane(size, loc, rot, mat, tile=2.0):
    """A wall/floor panel whose texture tiles every `tile` metres."""
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = (size[0], size[1], 1)
    bpy.ops.object.transform_apply(scale=True)
    for loop in o.data.uv_layers.active.data:
        loop.uv = (loop.uv[0] * size[0] / tile, loop.uv[1] * size[1] / tile)
    o.data.materials.append(mat)
    return o


def box(loc, size, mat, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = (size[0] / 2, size[1] / 2, size[2] / 2)
    o.data.materials.append(mat)
    return o


def cyl(loc, r, h, mat, verts=24, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cylinder_add(location=loc, radius=r, depth=h, vertices=verts, rotation=rot)
    o = bpy.context.active_object
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


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


def import_model(asset_id, loc, rot_z=0.0, scale=1.0, sit_on=None):
    """Imports a Poly Haven glTF under an empty and places it. sit_on: z height for the model's base."""
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


# ─── FIGURES ─────────────────────────────────────────────────────────────────
def masked_figure(pos, facing, mats, seated=False, mood="smile", seed=1, height=1.0):
    """
    Stand-in masked character: robe, shoulders, hanging sleeves, hood and a theatre mask.
    facing = Z rotation (0 faces +Y). mats: dict with "wool" and "void" materials.
    """
    pos = Vector(pos)
    fx, fy = -math.sin(facing), math.cos(facing)
    sx, sy = math.cos(facing), math.sin(facing)
    base = 0.42 if seated else 0.0
    h = (1.0 if seated else 1.42) * height
    bpy.ops.mesh.primitive_cone_add(vertices=32, radius1=0.36, radius2=0.16, depth=h,
                                    location=(pos.x, pos.y, base + h / 2))
    robe = bpy.context.active_object
    robe.data.materials.append(mats["wool"])
    bpy.ops.object.shade_smooth()
    shoulder_z = base + h - 0.02
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(pos.x, pos.y, shoulder_z))
    sh = bpy.context.active_object
    sh.scale = (0.23, 0.17, 0.09)
    sh.rotation_euler = (0, 0, facing)
    sh.data.materials.append(mats["wool"])
    bpy.ops.object.shade_smooth()
    for side in (-1, 1):
        ax, ay = pos.x + sx * 0.22 * side + fx * 0.05, pos.y + sy * 0.22 * side + fy * 0.05
        arm_len = 0.62 * height
        bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=0.075, radius2=0.045, depth=arm_len,
                                        location=(ax, ay, shoulder_z - arm_len / 2))
        arm = bpy.context.active_object
        arm.data.materials.append(mats["wool"])
        bpy.ops.object.shade_smooth()
    head_z = shoulder_z + 0.2
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(pos.x - fx * 0.03, pos.y - fy * 0.03, head_z + 0.02))
    hood = bpy.context.active_object
    hood.scale = (0.17, 0.17, 0.22)
    hood.data.materials.append(mats["wool"])
    bpy.ops.object.shade_smooth()
    mask = masks.make_mask("mask_%s_%d" % (mood, seed), mood, seed=seed)
    mask.location = (pos.x + fx * 0.115, pos.y + fy * 0.115, head_z)
    mask.rotation_euler = (math.pi / 2, 0, facing + math.pi)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(pos.x + fx * 0.05, pos.y + fy * 0.05, head_z))
    void = bpy.context.active_object
    void.scale = (0.1, 0.1, 0.13)
    void.data.materials.append(mats["void"])
    return head_z


# ─── LIGHTS ──────────────────────────────────────────────────────────────────
def light(kind, loc, energy, colour, size=0.1, target=None, spot_deg=None, blend=0.5):
    data = bpy.data.lights.new(kind.lower(), kind)
    data.energy = energy
    data.color = colour
    if kind == "AREA":
        data.size = size
    else:
        data.shadow_soft_size = size
    if kind == "SPOT":
        data.spot_size = math.radians(spot_deg or 60)
        data.spot_blend = blend
    o = bpy.data.objects.new(data.name, data)
    o.location = loc
    if target is not None:
        o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    bpy.context.collection.objects.link(o)
    return o


# ─── RENDER SETTINGS ─────────────────────────────────────────────────────────
def setup_world(sc, haze=0.01):
    world = bpy.data.worlds.new("World")
    sc.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.002, 0.002, 0.002, 1)
    if haze > 0:
        vol = world.node_tree.nodes.new("ShaderNodeVolumePrincipled")
        vol.inputs["Density"].default_value = haze
        world.node_tree.links.new(vol.outputs[0], world.node_tree.nodes["World Output"].inputs["Volume"])
    sc.view_settings.view_transform = "AgX"
    sc.view_settings.look = "AgX - Medium High Contrast"


def setup_cycles(sc, threshold=0.04, max_samples=192):
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
                break
        except Exception as ex:
            print("device", kind, ex)
    c = sc.cycles
    c.use_adaptive_sampling = True
    c.adaptive_threshold = threshold
    c.samples = max_samples
    c.use_denoising = True
    c.max_bounces = 6
    c.volume_bounces = 1
    sc.render.use_persistent_data = True  # keeps the scene loaded between animation frames


def setup_eevee(sc):
    sc.render.engine = "BLENDER_EEVEE"
    for attr, value in (("taa_render_samples", 48), ("use_raytracing", True),
                        ("volumetric_tile_size", "4"), ("volumetric_samples", 64)):
        try:
            setattr(sc.eevee, attr, value)
        except Exception as ex:
            print("eevee setting skipped:", attr, ex)
