"""
Exports people from characters.blend as ordinary game models for the Godot project
(godot/assets/<name>.glb, ignored by git).

    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\export_people.py -- opponent1[,opponent2,...]

Each becomes one skeleton with skinned meshes, plain image-textured materials and a baked, looping
"idle" animation plus the reactions (think, capture, hurt, check, win, lose), seated as built: in Godot put it 1 m beyond the table (z = -1), unrotated.

What has to change on the way out of Blender:
  - MPFB's helper geometry and shape keys go (modifiers can't be applied over shape keys)
  - the pose the cloth settled in becomes the skeleton's rest pose, so the frozen cloth and the body
    agree; the cloth then gets skin weights copied from the body (instead of Surface Deform)
  - procedural materials become image textures (fabrics are tinted into PNGs in godot/assets/tex)
  - the idle is re-baked on the new rest pose with the hands planted by IK, and exported by sampling
"""
import bmesh
import bpy
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "godot", "assets")
REACTIONS = ["think", "capture", "hurt", "check", "win", "lose"]
HAIR_WORDS = ("hair", "brow", "lash", "short", "bob", "afro", "long", "ponytail", "braid")


def _active(obj):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj


def _apply_modifier(obj, mod):
    _active(obj)
    bpy.ops.object.modifier_apply(modifier=mod.name)


def simple_material(name, image=None, colour=(0.5, 0.5, 0.5), rough=0.7, normal=None, scale=1.0, alpha=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    b.inputs["Roughness"].default_value = rough
    b.inputs["Base Color"].default_value = (*colour, 1)
    mapping = None
    if scale != 1.0:
        co = nt.nodes.new("ShaderNodeTexCoord")
        mapping = nt.nodes.new("ShaderNodeMapping")
        mapping.inputs["Scale"].default_value = (scale, scale, scale)
        nt.links.new(co.outputs["UV"], mapping.inputs["Vector"])
    if image:
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = image
        if mapping:
            nt.links.new(mapping.outputs["Vector"], t.inputs["Vector"])
        nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
        if alpha:
            nt.links.new(t.outputs["Alpha"], b.inputs["Alpha"])
            m.blend_method = "HASHED"
    if normal:
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = normal
        if mapping:
            nt.links.new(mapping.outputs["Vector"], t.inputs["Vector"])
        n = nt.nodes.new("ShaderNodeNormalMap")
        nt.links.new(t.outputs["Color"], n.inputs["Color"])
        nt.links.new(n.outputs["Normal"], b.inputs["Normal"])
    return m


def save_png(name, pixels, w, h):
    """Writes float RGBA pixels (sRGB-encoded) to godot/assets/tex/<name>.png and returns the image."""
    os.makedirs(os.path.join(OUT, "tex"), exist_ok=True)
    img = bpy.data.images.new(name, w, h, alpha=False)
    img.pixels.foreach_set(pixels.ravel())
    img.filepath_raw = os.path.join(OUT, "tex", name + ".png")
    img.file_format = "PNG"
    img.save()
    return img


def tinted(name, source, tint, size=1024):
    """The fabric photo, drained of colour and multiplied by the tint (as realistic.fabric does), as a PNG."""
    img = source.copy()
    img.scale(size, size)
    px = np.empty(size * size * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    bpy.data.images.remove(img)
    px = px.reshape(-1, 4)
    lin = np.power(np.clip(px[:, :3], 0, 1), 2.2)
    lum = lin @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
    lin = lum[:, None] + (lin - lum[:, None]) * 0.2
    lin *= np.array(tint, dtype=np.float32)
    px[:, :3] = np.power(np.clip(lin, 0, 1), 1 / 2.2)
    px[:, 3] = 1.0
    return save_png(name, px, size, size)


def convert_material(mat, cache):
    """A plain image-textured copy of a material made by MPFB, realistic.fabric or masks.py."""
    if mat is None:
        return None
    if mat.name in cache:
        return cache[mat.name]
    nodes = list(mat.node_tree.nodes) if mat.use_nodes else []
    images = [n for n in nodes if n.type == "TEX_IMAGE" and n.image]
    diff = next((n.image for n in images if n.image.name.split(".")[0].endswith("_diff")), None)
    safe = mat.name.replace(".", "_")
    if diff is not None:                                   # one of our fabrics
        tint = next((tuple(n.inputs[7].default_value)[:3] for n in nodes if n.type == "MIX"
                     and n.blend_type == "MULTIPLY" and not n.inputs[7].is_linked), (1, 1, 1))
        scale = next((n.inputs["Scale"].default_value[0] for n in nodes if n.type == "MAPPING"), 1.0)
        nor = next((n.image for n in images if "_nor_gl" in n.image.name), None)
        new = simple_material(safe + "_g", tinted(safe, diff, tint), rough=0.85, normal=nor, scale=scale)
    elif any(n.image.name.startswith("mask_paint_") for n in images):   # a theatre mask: bone with painted lines
        paint = next(n.image for n in images if n.image.name.startswith("mask_paint_"))
        w, h = paint.size
        px = np.empty(w * h * 4, dtype=np.float32)
        paint.pixels.foreach_get(px)
        px = px.reshape(-1, 4)
        amount = px[:, 0:1].copy()
        bone = np.array([0.86, 0.83, 0.76], dtype=np.float32)
        ink = np.array([0.1, 0.09, 0.08], dtype=np.float32)
        px[:, :3] = bone * (1 - amount) + ink * amount
        px[:, 3] = 1.0
        new = simple_material(safe + "_g", save_png(safe, px, w, h), rough=0.88)
    elif images:                                           # MPFB skin, hair, eyes: keep the photo texture
        hairy = any(k in mat.name.lower() for k in HAIR_WORDS)
        new = simple_material(safe + "_g", images[0].image, rough=0.6 if hairy else 0.5, alpha=hairy)
    else:
        b = next((n for n in nodes if n.type == "BSDF_PRINCIPLED"), None)
        colour = tuple(b.inputs["Base Color"].default_value)[:3] if b else (0.5, 0.5, 0.5)
        new = simple_material(safe + "_g", colour=colour, rough=b.inputs["Roughness"].default_value if b else 0.7)
    cache[mat.name] = new
    return new


def character(name):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    with bpy.data.libraries.load(os.path.join(kit.ASSETS, "characters.blend"), link=False) as (src, dst):
        dst.collections = [name]
    col = dst.collections[0]
    sc = bpy.context.scene
    sc.collection.children.link(col)
    rig = next(o for o in col.objects if o.type == "ARMATURE" and o.parent is None)
    sc.frame_set(1)

    # Hold the settled pose with no animation and no IK.
    for o in list(col.objects):
        if o.name.endswith("_proxy") or (o.type == "EMPTY" and o.parent == rig):
            bpy.data.objects.remove(o, do_unlink=True)
    rig.animation_data.action = None
    base = dict(rig["base_pose"])
    for pb in rig.pose.bones:
        for c in list(pb.constraints):
            pb.constraints.remove(c)
        q, loc = base[pb.name]
        pb.rotation_mode = "QUATERNION"
        pb.rotation_quaternion = list(q)
        pb.location = list(loc)
        pb.scale = (1, 1, 1)
    bpy.context.view_layer.update()

    meshes = [o for o in col.objects if o.type == "MESH"]
    garment = next(o for o in meshes if o.name.endswith("_clothes"))
    body = next(o for o in meshes if o.name.endswith("_body"))
    skinned = [o for o in meshes if o is not garment and any(m.type == "ARMATURE" for m in o.modifiers)]
    bone_children = [(o, o.matrix_world.copy()) for o in meshes if o.parent_type == "BONE"]
    print("MESHES", [(o.name, len(o.data.vertices)) for o in meshes])

    # Skinned meshes: bake their shape in this pose.
    for o in skinned:
        _active(o)
        if o.data.shape_keys:
            bpy.ops.object.shape_key_remove(all=True, apply_mix=True)
        if o is body:                                      # MPFB's helper geometry (hidden by a Mask modifier)
            keep = o.vertex_groups["body"].index
            bm = bmesh.new()
            bm.from_mesh(o.data)
            layer = bm.verts.layers.deform.verify()
            bmesh.ops.delete(bm, geom=[v for v in bm.verts if keep not in v[layer]], context="VERTS")
            bm.to_mesh(o.data)
            bm.free()
        for m in list(o.modifiers):
            if m.type == "ARMATURE":
                _apply_modifier(o, m)
            else:
                o.modifiers.remove(m)

    # The cloth: drop Surface Deform, copy skin weights from the (now posed) body, bake its thickness.
    for m in list(garment.modifiers):
        if m.type in ("SURFACE_DEFORM", "SUBSURF"):
            garment.modifiers.remove(m)
    transfer = garment.modifiers.new("weights", "DATA_TRANSFER")
    transfer.object = body
    transfer.use_vert_data = True
    transfer.data_types_verts = {"VGROUP_WEIGHTS"}
    transfer.vert_mapping = "POLYINTERP_NEAREST"
    _active(garment)
    bpy.ops.object.modifier_move_to_index(modifier=transfer.name, index=0)
    bpy.ops.object.datalayout_transfer(modifier=transfer.name)
    bpy.ops.object.modifier_apply(modifier=transfer.name)
    for m in list(garment.modifiers):
        _apply_modifier(garment, m)

    # This pose becomes the skeleton's rest pose.
    _active(rig)
    bpy.ops.object.mode_set(mode="POSE")
    bpy.ops.pose.armature_apply(selected=False)
    bpy.ops.object.mode_set(mode="OBJECT")
    for o, world in bone_children:
        o.matrix_world = world
    for o in skinned + [garment]:
        o.modifiers.new("rig", "ARMATURE").object = rig
    rig["base_pose"] = {pb.name: [[1.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0]] for pb in rig.pose.bones}

    # Plain materials.
    cache = {}
    for o in meshes:
        for slot in o.material_slots:
            slot.material = convert_material(slot.material, cache)

    # The idle again, on the new rest pose, hands planted.
    for a in list(bpy.data.actions):
        bpy.data.actions.remove(a)
    realistic.plant_hands(rig)
    realistic.animate(rig, rig["seed"], rig["energy"])
    idle = bpy.data.actions[rig["idle_action"]]
    # The reactions too (each is the idle plus a move; "think" lasts a whole idle loop so it can loop).
    clips = [("idle", idle, realistic.IDLE_FRAMES)]
    for reaction in REACTIONS:
        realistic.react(rig, reaction)
        clips.append((reaction, rig.animation_data.action, realistic.reaction_frames(reaction)))
    rig.animation_data.action = None
    for clip_name, action, frames in clips:      # one NLA track each: the exporter writes one animation per track
        action.name = clip_name
        for fc in realistic._fcurves(action):
            for mod in list(fc.modifiers):
                fc.modifiers.remove(mod)
        track = rig.animation_data.nla_tracks.new()
        track.name = clip_name
        track.strips.new(clip_name, 1, action).action_frame_end = frames + (1 if clip_name != "idle" and clip_name != "think" else 0)
    sc.frame_start, sc.frame_end = 1, realistic.IDLE_FRAMES + 1

    path = os.path.join(OUT, name + ".glb")
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", export_apply=True, export_yup=True,
                              export_lights=False, export_cameras=False, export_animations=True,
                              export_animation_mode="NLA_TRACKS", export_force_sampling=True,
                              export_frame_range=False, export_skins=True, export_morph=False)
    print("EXPORTED", path, "%.1f MB" % (os.path.getsize(path) / 1e6),
          "verts", sum(len(o.data.vertices) for o in meshes))


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    os.makedirs(OUT, exist_ok=True)
    for name in (args[0].split(",") if args else ["opponent1"]):
        character(name)
    print("DONE")


main()
