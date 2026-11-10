"""
Builds the realistic Ashes cellar in Blender and renders every clip the game plays.

Run from the project folder, after tools\\blender\\fetch_assets.py has downloaded the assets and
tools\\blender\\make_characters.py has built the characters (assets/characters.blend):
    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" -b --factory-startup -P tools\\blender\\build_scenes.py
Options after "--":
    --draft           EEVEE at half resolution: fast previews (minutes instead of an hour)
    --quick           a quarter of the frames per clip
    --only NAME,...   render only these clips (e.g. --only table,card,intro)
    --resume          skip clips a previous full render already finished (it leaves a "done" file in
                      each clip's folder), so an interrupted render carries on where it stopped

Output: resources/scenes/<clip>/frame_0001.jpg ... at 1280x720 (ignored by git).
Clips: still views table / card / piece / resell, the turns between neighbouring views, the intro,
the dealers' idle loops and reactions (<view>_idle, <view>_yes, <view>_no), each opponent's close-up
shown beside the board during play (opponent<n>_idle plus think / capture / hurt / check / win / lose,
half resolution because the game shows it small), and the swap after a win: the lamp dies on the
beaten opponent (swap_out_<n>) and comes back on the next one (swap_in_<n>).

The set: a brick cellar under one hanging lamp; the player's seat at a round table facing a
masked opponent; three masked dealers around the seat (card dealer left, piece dealer right,
reseller behind-right); a corridor and a tiled bathroom for the intro. Real props and textures
come from Poly Haven (CC0). The characters are realistic MPFB bodies in simulated cloth with
theatre masks (realistic.py, masks.py); five opponents share the chair, one shown at a time.
"""
import bmesh
import bpy
import math
import os
import sys

from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import realistic  # noqa: E402
import scene_kit as kit  # noqa: E402

# ─── SETTINGS ────────────────────────────────────────────────────────────────
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "resources", "scenes")
args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
DRAFT = "--draft" in args
QUICK = "--quick" in args
RESUME = "--resume" in args
ONLY = set(args[args.index("--only") + 1].split(",")) if "--only" in args else None
WIDTH, HEIGHT = (640, 360) if DRAFT else (1280, 720)
FPS = 24

SEAT = Vector((0.0, -0.95, 0.0))
EYE_SEATED = 1.15
EYE_STANDING = 1.62
OPPONENT = Vector((0.0, 1.0, 0.0))
RIGS = {}  # view name (and "opponent<n>") -> the character seen in it (for reactions)
ROSTER = 5
CHARACTERS_BLEND = os.path.join(kit.ASSETS, "characters.blend")
TABLE_HEIGHT_SCALE = 0.76   # the table model is bar height; shortened to a dining table
LIGHTS = {}  # named lights the intro animates
INTRO_FRAMES = 264
DEALER_REACTIONS = ["yes", "no"]
OPPONENT_REACTIONS = ["think", "capture", "hurt", "check", "win", "lose"]
CARD_DEALER = Vector((-1.55, -0.9, 0.0))
PIECE_DEALER = Vector((1.55, -0.9, 0.0))
RESELLER = Vector((0.95, -2.45, 0.0))


def yaw_to(src, dst):
    """Z rotation that turns something facing +Y toward dst."""
    return math.atan2(dst.y - src.y, dst.x - src.x) - math.pi / 2


# ─── THE SET ─────────────────────────────────────────────────────────────────
def build_set():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = WIDTH, HEIGHT
    sc.render.resolution_percentage = 100
    sc.render.fps = FPS
    sc.render.image_settings.file_format = "JPEG"
    sc.render.image_settings.quality = 90
    kit.setup_world(sc, haze=0.008)

    brick = kit.pbr("brick", "plaster_brick_01")
    concrete = kit.pbr("concrete", "concrete_floor_worn_001")
    tiles = kit.pbr("tiles", "long_white_tiles")
    floor_tiles = kit.pbr("floor_tiles", "dirty_tiles")
    ceiling = kit.plain("ceiling", (0.04, 0.038, 0.035), rough=0.95)
    porcelain = kit.plain("porcelain", (0.75, 0.74, 0.7), rough=0.3)
    paper = kit.plain("paper", (0.72, 0.68, 0.6), rough=0.7)
    wood = kit.plain("dark_wood", (0.07, 0.045, 0.03), rough=0.55)
    mats = {"wool": kit.plain("wool", (0.03, 0.026, 0.022), rough=0.95, sheen=0.15),
            "void": kit.plain("void", (0, 0, 0), rough=1.0)}
    P = math.pi / 2

    # Main room 8 x 8 x 3 m; a door gap in the back wall (y = -4) leads to the corridor.
    kit.plane((8, 8), (0, 0, 0), (0, 0, 0), concrete, 2.0)
    kit.plane((8, 8), (0, 0, 3), (math.pi, 0, 0), ceiling)
    kit.plane((8, 3), (0, 4, 1.5), (P, 0, math.pi), brick)
    kit.plane((8, 3), (4, 0, 1.5), (P, 0, P), brick)
    kit.plane((8, 3), (-4, 0, 1.5), (P, 0, -P), brick)
    kit.plane((3.2, 3), (-2.4, -4, 1.5), (P, 0, 0), brick)
    kit.plane((3.2, 3), (2.4, -4, 1.5), (P, 0, 0), brick)
    kit.plane((1.6, 0.8), (0, -4, 2.6), (P, 0, 0), brick)
    # Corridor (y -4 .. -6).
    kit.plane((1.7, 2.0), (0, -5, 0), (0, 0, 0), concrete)
    kit.plane((2.0, 2.6), (0.85, -5, 1.3), (P, 0, P), brick)
    kit.plane((2.0, 2.6), (-0.85, -5, 1.3), (P, 0, -P), brick)
    kit.plane((1.7, 2.0), (0, -5, 2.6), (math.pi, 0, 0), ceiling)
    # Bathroom (y -6 .. -8): tiled, with a mirror, a sink and a cold strip light.
    kit.plane((2.5, 2.0), (0, -7, 0.001), (0, 0, 0), floor_tiles, 1.0)
    kit.plane((2.5, 2.0), (0, -7, 2.6), (math.pi, 0, 0), ceiling)
    kit.plane((2.5, 2.6), (0, -8, 1.3), (P, 0, 0), tiles, 1.0)
    kit.plane((2.0, 2.6), (1.25, -7, 1.3), (P, 0, P), tiles, 1.0)
    kit.plane((2.0, 2.6), (-1.25, -7, 1.3), (P, 0, -P), tiles, 1.0)
    kit.plane((0.8, 2.6), (-0.85, -6, 1.3), (P, 0, math.pi), tiles, 1.0)
    kit.plane((0.8, 2.6), (0.85, -6, 1.3), (P, 0, math.pi), tiles, 1.0)
    kit.import_model("ornate_mirror_01", Vector((0, -7.93, 0)), rot_z=0.0, sit_on=1.05)
    cracked_glass(Vector((0, -7.9325, 0)))  # just in front of the model's own glass
    kit.box((0, -7.72, 0.82), (0.6, 0.42, 0.14), porcelain)
    kit.cyl((0, -7.75, 0.4), 0.08, 0.76, porcelain)
    kit.import_model("mounted_fluorescent_lights", Vector((0, -7.0, 2.55)), scale=0.6)
    LIGHTS["strip"] = kit.light("AREA", (0, -7.0, 2.5), 22, (0.75, 0.9, 1.0), size=0.8, target=(0, -7.0, 0))
    kit.light("POINT", (0, -5.0, 2.3), 5, (0.8, 0.85, 1.0), size=0.2)

    # The table, the chess set and the lamp.
    table, table_objs = kit.import_model("round_wooden_table_01", Vector((0, 0, 0)), sit_on=0.0)
    table.scale.z *= TABLE_HEIGHT_SCALE
    bpy.context.view_layer.update()
    top = kit.bounds(table_objs)[1].z
    kit.import_model("chess_set", Vector((0, 0, 0)), rot_z=math.pi, sit_on=top)
    chair, _ = kit.import_model("WoodenChair_01", Vector((OPPONENT.x, OPPONENT.y + 0.1, 0)), sit_on=0.0)
    chair.scale.x *= realistic.CHAIR_WIDEN   # a wider throne, so robed arms fit between its rails
    kit.import_model("hanging_industrial_lamp", Vector((0, 0, 0)), scale=0.45, sit_on=1.95)
    LIGHTS["lamp"] = kit.light("SPOT", (0, 0, 1.93), 650, (1.0, 0.8, 0.6), size=0.08, target=(0, 0, 0), spot_deg=85, blend=0.6)
    LIGHTS["bulb"] = kit.light("POINT", (0, 0, 1.98), 25, (1.0, 0.7, 0.4), size=0.05)

    # The opponents and the three dealers (built by make_characters.py).
    load_characters()

    # Card dealer: a small stand with candles and a fan of cards.
    stand = Vector((-1.05, -0.62, 0))
    kit.cyl((stand.x, stand.y, 0.45), 0.2, 0.9, wood)
    kit.import_model("brass_candleholders", Vector((stand.x - 0.08, stand.y + 0.08, 0)), scale=0.55, sit_on=0.9)
    for i in range(5):
        a = math.radians(-24 + i * 12)
        kit.box((stand.x + 0.06, stand.y - 0.1 + i * 0.035, 0.92 + 0.004 * i), (0.06, 0.09, 0.002), paper, rot=(0, 0, a))
    kit.light("POINT", (stand.x - 0.08, stand.y + 0.08, 1.25), 14, (1.0, 0.55, 0.25), size=0.02)
    # Piece dealer: a crate of chess pieces for sale, lit by a lantern glow.
    crate, crate_objs = kit.import_model("old_military_crate", Vector((1.05, -0.62, 0)), rot_z=0.3, sit_on=0.0)
    kit.import_model("chess_set", Vector((1.05, -0.62, 0)), rot_z=0.9, scale=0.55, sit_on=kit.bounds(crate_objs)[1].z)
    kit.light("POINT", (1.3, -0.3, 1.5), 16, (1.0, 0.62, 0.32), size=0.1)
    # Reseller: a barrel stove glowing behind him, bottles on a barrel, a worn bookshelf.
    kit.import_model("barrel_stove", Vector((1.75, -3.2, 0)), sit_on=0.0)
    kit.light("POINT", (1.75, -3.2, 0.7), 60, (1.0, 0.4, 0.12), size=0.25)
    kit.import_model("Barrel_01", Vector((1.0, -3.55, 0)), sit_on=0.0)
    kit.import_model("wine_bottles_01", Vector((1.0, -3.55, 0)), sit_on=0.95)
    kit.import_model("wooden_bookshelf_worn", Vector((3.75, -2.2, 0)), rot_z=-P, sit_on=0.0)

    # Clutter in the corners.
    kit.import_model("wooden_crate_01", Vector((-3.3, 3.2, 0)), rot_z=0.3, sit_on=0.0)
    kit.import_model("wooden_crate_01", Vector((-3.25, 3.15, 0)), rot_z=-0.2, sit_on=0.6)
    kit.import_model("wooden_crate_01", Vector((3.2, 3.3, 0)), rot_z=0.8, sit_on=0.0)
    kit.import_model("Barrel_01", Vector((-3.3, -3.2, 0)), sit_on=0.0)

    # Rim lights so the dark robes separate from the dark walls.
    for figure, src, energy in ((OPPONENT, (0.3, 2.4, 2.1), 45), (CARD_DEALER, (-2.7, -1.3, 2.2), 30),
                                (PIECE_DEALER, (2.7, -1.3, 2.2), 30), (RESELLER, (1.6, -3.6, 2.2), 25)):
        rim = kit.light("AREA", src, energy, (1.0, 0.6, 0.35), size=0.6, target=figure + Vector((0, 0, 1.3)))
        if figure is OPPONENT:
            LIGHTS["rim_opponent"] = rim
    return sc, top


def load_characters():
    """Appends the characters from assets/characters.blend and puts them in their places."""
    if not os.path.exists(CHARACTERS_BLEND):
        raise SystemExit("No characters yet: run tools/blender/make_characters.py first.")
    names = ["opponent%d" % n for n in range(1, ROSTER + 1)] + ["card_dealer", "piece_dealer", "reseller"]
    with bpy.data.libraries.load(CHARACTERS_BLEND, link=False) as (src, dst):
        dst.collections = [n for n in names if n in src.collections]
    for col in dst.collections:
        bpy.context.scene.collection.children.link(col)

    def rig(name):
        return next(o for o in bpy.data.collections[name].objects if o.type == "ARMATURE" and o.parent is None)

    for n in range(1, ROSTER + 1):
        r = rig("opponent%d" % n)
        realistic.place(r, OPPONENT, math.pi)
        RIGS["opponent%d" % n] = r
    RIGS["opponent"] = RIGS["opponent1"]
    for view, name, pos in (("card", "card_dealer", CARD_DEALER), ("piece", "piece_dealer", PIECE_DEALER),
                            ("resell", "reseller", RESELLER)):
        RIGS[view] = rig(name)
        realistic.place(RIGS[view], pos, yaw_to(pos, SEAT))
    show_opponent(1)


def show_opponent(n):
    """Only opponent n is rendered (they all sit in the same chair)."""
    for k in range(1, ROSTER + 1):
        bpy.data.collections["opponent%d" % k].hide_render = k != n


def head_pos(rig):
    bpy.context.view_layer.update()
    return rig.matrix_world @ rig.pose.bones["head"].head


def cracked_glass(at):
    """The mirror's glass: an arched pane set into the frame, fogged with grime and cracked
    (a shattered spot where something hit it, and a few long cracks across)."""
    w, bottom, centre_z = 0.214, 1.075, 1.552  # half width, bottom edge, centre of the arch
    bm = bmesh.new()
    pts = [(-w, bottom), (w, bottom), (w, centre_z)]
    for i in range(1, 24):
        a = math.pi * i / 24
        pts.append((w * math.cos(a), centre_z + w * math.sin(a)))
    pts.append((-w, centre_z))
    bm.faces.new([bm.verts.new((x, 0, z)) for x, z in pts])
    me = bpy.data.meshes.new("mirror_glass")
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new("mirror_glass", me)
    obj.location = at
    bpy.context.collection.objects.link(obj)

    m = bpy.data.materials.new("cracked_mirror")
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    for n in list(N):
        N.remove(n)
    out = N.new("ShaderNodeOutputMaterial")
    coord = N.new("ShaderNodeTexCoord").outputs["Object"]

    def node(kind, **inputs):
        n = N.new(kind)
        for k, v in inputs.items():
            n.inputs[k].default_value = v
        return n

    def map_range(src, a, b, c, d):
        n = node("ShaderNodeMapRange")
        n.inputs[1].default_value, n.inputs[2].default_value = a, b
        n.inputs[3].default_value, n.inputs[4].default_value = c, d
        n.clamp = True
        L.new(src, n.inputs[0])
        return n.outputs[0]

    def math_op(op, a, b):
        n = N.new("ShaderNodeMath")
        n.operation = op
        for i, v in enumerate((a, b)):
            if isinstance(v, float):
                n.inputs[i].default_value = v
            else:
                L.new(v, n.inputs[i])
        return n.outputs[0]

    def voronoi_edges(scale, width):
        v = N.new("ShaderNodeTexVoronoi")
        v.feature = "DISTANCE_TO_EDGE"
        v.inputs["Scale"].default_value = scale
        L.new(coord, v.inputs["Vector"])
        return map_range(v.outputs["Distance"], 0.0, width, 1.0, 0.0)

    # Fog: patchy haze that blurs the reflection, and grime that dulls it.
    fog_tex = node("ShaderNodeTexNoise", Scale=5.0, Detail=6.0)
    L.new(coord, fog_tex.inputs["Vector"])
    fog = map_range(fog_tex.outputs["Fac"], 0.35, 0.75, 0.1, 0.38)
    grime_tex = node("ShaderNodeTexNoise", Scale=11.0, Detail=8.0)
    L.new(coord, grime_tex.inputs["Vector"])
    grime = map_range(grime_tex.outputs["Fac"], 0.5, 0.78, 0.0, 0.65)
    # Cracks: a fine web around the impact point, and a few long cracks over the whole pane.
    impact = N.new("ShaderNodeVectorMath")
    impact.operation = "DISTANCE"
    impact.inputs[1].default_value = (0.07, 0.0, 1.47)
    L.new(coord, impact.inputs[0])
    near = map_range(impact.outputs["Value"], 0.02, 0.1, 1.0, 0.0)
    web = math_op("MULTIPLY", voronoi_edges(34.0, 0.018), near)
    long_cracks = voronoi_edges(2.6, 0.004)
    crack = math_op("MAXIMUM", web, long_cracks)

    mirror = node("ShaderNodeBsdfPrincipled", Metallic=1.0)
    mirror.inputs["Base Color"].default_value = (0.72, 0.72, 0.69, 1)
    L.new(math_op("MAXIMUM", fog, math_op("MULTIPLY", crack, 0.9)), mirror.inputs["Roughness"])
    bump = node("ShaderNodeBump", Strength=0.5)
    L.new(crack, bump.inputs["Height"])
    L.new(bump.outputs["Normal"], mirror.inputs["Normal"])
    dirt = node("ShaderNodeBsdfPrincipled", Roughness=0.9)
    dirt.inputs["Base Color"].default_value = (0.16, 0.14, 0.11, 1)
    mix = N.new("ShaderNodeMixShader")
    L.new(math_op("MAXIMUM", grime, math_op("MULTIPLY", crack, 0.7)), mix.inputs[0])
    L.new(mirror.outputs[0], mix.inputs[1])
    L.new(dirt.outputs[0], mix.inputs[2])
    L.new(mix.outputs[0], out.inputs["Surface"])
    me.materials.append(m)
    return obj


# ─── CAMERA ──────────────────────────────────────────────────────────────────
def make_camera():
    data = bpy.data.cameras.new("Cam")
    data.lens = 28
    data.clip_start = 0.05
    data.dof.use_dof = True
    data.dof.aperture_fstop = 2.8
    cam = bpy.data.objects.new("Cam", data)
    bpy.context.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    return cam


def pose(eye, target):
    """(location, rotation, focus distance) for a camera at eye looking at target."""
    d = target - eye
    rot = (math.pi / 2 + math.atan2(d.z, d.xy.length), 0, math.atan2(d.y, d.x) - math.pi / 2)
    return eye, rot, d.length


def build_views(top):
    eye = Vector((SEAT.x, SEAT.y, EYE_SEATED))
    lean = Vector((SEAT.x, SEAT.y + 0.35, top + 0.5))

    def chest(view):  # frame each dealer on his upper body
        return head_pos(RIGS[view]).z - 0.28
    return {
        "table": pose(lean, Vector((0, -0.08, top))),
        "card": pose(eye, Vector((CARD_DEALER.x + 0.2, CARD_DEALER.y + 0.08, chest("card")))),
        "piece": pose(eye, Vector((PIECE_DEALER.x - 0.2, PIECE_DEALER.y + 0.08, chest("piece")))),
        "resell": pose(eye, Vector((RESELLER.x + 0.15, RESELLER.y - 0.15, chest("resell")))),
    }


def portrait_view(n):
    """Close-up of opponent n across the board (shown small beside the board during play)."""
    face = head_pos(RIGS["opponent%d" % n]).z + 0.06
    return pose(Vector((0.0, -0.3, face)), Vector((OPPONENT.x, OPPONENT.y, face)))


RING = ["card", "table", "piece", "resell"]


def unwrap(prev, angle):
    while angle - prev > math.pi:
        angle -= 2 * math.pi
    while angle - prev < -math.pi:
        angle += 2 * math.pi
    return angle


def key(cam, frame, loc, rot, focus):
    cam.location = loc
    cam.rotation_euler = rot
    cam.data.dof.focus_distance = focus
    cam.keyframe_insert("location", frame=frame)
    cam.keyframe_insert("rotation_euler", frame=frame)
    cam.data.keyframe_insert("dof.focus_distance", frame=frame)


def clear_keys(cam):
    cam.animation_data_clear()
    cam.data.animation_data_clear()


def render_clip(name, frames, half=False):
    if ONLY and name not in ONLY:
        return
    sc = bpy.context.scene
    sc.render.resolution_percentage = 50 if half else 100
    folder = os.path.join(OUT, name)
    os.makedirs(folder, exist_ok=True)
    marker = os.path.join(folder, "done")
    if RESUME and os.path.exists(marker):
        print("SKIPPED", name, "(already rendered)")
        return
    for f in os.listdir(folder):
        if f.endswith(".jpg") or f == "done":
            os.remove(os.path.join(folder, f))
    sc.frame_start, sc.frame_end = 1, frames
    sc.render.filepath = os.path.join(folder, "frame_")
    bpy.ops.render.render(animation=True)
    if not (DRAFT or QUICK):
        open(marker, "w").close()
    print("RENDERED", name, frames, "frames")


def render_all(cam, views):
    q = 4 if QUICK else 1

    for name, (loc, rot, focus) in views.items():
        clear_keys(cam)
        key(cam, 1, loc, rot, focus)
        render_clip(name, 1)
        if name == "table":
            continue
        # The dealer views get a seamless idle loop and reactions.
        render_clip(name + "_idle", max(2, realistic.IDLE_FRAMES // q))
        rig = RIGS[name]
        for r in DEALER_REACTIONS:
            realistic.react(rig, r)
            render_clip("%s_%s" % (name, r), max(2, realistic.reaction_frames(r) // q))
        realistic.react(rig, None)

    # Each opponent's close-up: idle and reactions, at half resolution.
    cam.data.lens = 45
    for n in range(1, ROSTER + 1):
        show_opponent(n)
        clear_keys(cam)
        key(cam, 1, *portrait_view(n))
        view = "opponent%d" % n
        rig = RIGS[view]
        render_clip(view + "_idle", max(2, realistic.IDLE_FRAMES // q), half=True)
        for r in OPPONENT_REACTIONS:
            realistic.react(rig, r)
            render_clip("%s_%s" % (view, r), max(2, realistic.reaction_frames(r) // q), half=True)
        realistic.react(rig, None)
    show_opponent(1)
    cam.data.lens = 28

    for i, a in enumerate(RING):
        b = RING[(i + 1) % len(RING)]
        clear_keys(cam)
        (la, ra, fa), (lb, rb, fb) = views[a], views[b]
        rb = (rb[0], rb[1], unwrap(ra[2], rb[2]))
        n = max(2, 24 // q)   # one second, easing out of one view and into the next
        key(cam, 1, la, ra, fa)
        key(cam, n, lb, rb, fb)
        render_clip("turn_%s_%s" % (a, b), n)

    render_swaps(cam, q)
    render_intro(cam, views, q)


SWAP_FRAMES = 48


def lamp_levels(levels):
    """Keys the table lamp (and the opponent's rim light) to these (frame, brightness) steps."""
    for light in (LIGHTS["lamp"], LIGHTS["bulb"], LIGHTS["rim_opponent"]):
        d = light.data
        d["full"] = d.get("full", d.energy)
        d.animation_data_clear()
        for frame, level in levels:
            d.energy = d["full"] * level
            d.keyframe_insert("energy", frame=frame)
        for fc in realistic._fcurves(d.animation_data.action):
            for k in fc.keyframe_points:
                k.interpolation = "CONSTANT"


def lamp_restore():
    for light in (LIGHTS["lamp"], LIGHTS["bulb"], LIGHTS["rim_opponent"]):
        light.data.animation_data_clear()
        light.data.energy = light.data.get("full", light.data.energy)


def render_swaps(cam, q):
    """After a win: the lamp stutters and dies on the beaten (slumped) opponent; in the dark the next
    one takes the chair; the lamp stutters back on. Rendered as out/in halves per opponent, so any
    pair can be joined in the dark."""
    clear_keys(cam)
    key(cam, 1, *pose(Vector((0, SEAT.y, EYE_SEATED)), Vector((0, OPPONENT.y, 0.98))))
    cam.data.lens = 32
    out_levels = [(1, 1), (17, 1), (18, 0.25), (19, 1), (23, 0.1), (24, 0.85), (26, 0), (28, 0.45), (29, 0), (SWAP_FRAMES, 0)]
    in_levels = [(1, 0), (14, 0), (15, 0.6), (16, 0), (19, 1), (20, 0.2), (22, 1), (SWAP_FRAMES, 1)]
    frames = max(2, SWAP_FRAMES // q)
    for n in range(1, ROSTER + 1):
        show_opponent(n)
        rig = RIGS["opponent%d" % n]
        lamp_levels(out_levels)
        realistic.react(rig, "lose")
        render_clip("swap_out_%d" % n, frames)
        lamp_levels(in_levels)
        realistic.react(rig, None)
        render_clip("swap_in_%d" % n, frames)
    lamp_restore()
    show_opponent(1)
    cam.data.lens = 28


def intro_flicker():
    """The bathroom strip light stutters on at the start of the intro, then holds."""
    strip = LIGHTS["strip"].data
    full = strip.energy
    for frame, level in ((1, 0.0), (3, 0.0), (4, 1.0), (5, 0.1), (7, 0.9), (9, 0.0), (12, 0.0), (13, 1.0),
                         (14, 0.3), (16, 1.0), (31, 1.0), (32, 0.55), (33, 1.0)):
        strip.energy = full * level
        strip.keyframe_insert("energy", frame=frame)
    for fc in realistic._fcurves(strip.animation_data.action):
        for k in fc.keyframe_points:
            k.interpolation = "CONSTANT"  # a tube light snaps on and off


def heads_follow(cam):
    """Every head turns to watch the camera: the opponent all along, the dealers once you walk in."""
    for name in ("opponent", "card", "piece", "resell"):
        rig = RIGS[name]
        head = rig.pose.bones["head"]
        track = head.constraints.new("DAMPED_TRACK")
        track.target = cam
        track.track_axis = "TRACK_Z"  # on the MPFB skeleton the face looks along the head bone's +Z
        limit = head.constraints.new("LIMIT_ROTATION")
        limit.owner_space = "LOCAL"
        for axis, deg in (("x", 25), ("y", 70), ("z", 20)):
            setattr(limit, "use_limit_" + axis, True)
            setattr(limit, "min_" + axis, -math.radians(deg))
            setattr(limit, "max_" + axis, math.radians(deg))
        keys = ((1, 0.75),) if name == "opponent" else ((1, 0.0), (118, 0.0), (150, 0.85))
        for frame, influence in keys:
            track.influence = influence
            track.keyframe_insert("influence", frame=frame)


def opponent_gesture():
    """As you sit down, the opponent lifts his right hand and opens it toward your seat."""
    target = bpy.data.objects[RIGS["opponent"].name + "_hand_r"]
    rest = target.location.copy()
    # In the rig's own frame: forward (toward you) is -Y, and his right hand is on the -X side.
    for frame, offset in ((198, (0, 0, 0)), (214, (-0.06, -0.14, 0.24)), (234, (-0.07, -0.15, 0.25)), (250, (0, 0, 0))):
        target.location = rest + Vector(offset)
        target.keyframe_insert("location", frame=frame)


def render_intro(cam, views, q):
    """The mirror, the corridor, the room, a look at each dealer, sitting down, the board."""
    clear_keys(cam)
    intro_flicker()
    heads_follow(cam)
    opponent_gesture()
    t_loc, t_rot, t_focus = views["table"]
    up = math.pi / 2
    turn = 2 * math.pi
    look = math.radians(64)  # the dealers stand about 64 degrees off to each side
    beats = [
        (1,   (0, -6.95, EYE_STANDING), (up, 0, math.pi), 1.0),                    # fade in on the mirror
        (56,  (0, -7.22, EYE_STANDING - 0.02), (up, 0, math.pi), 0.75),            # drift closer
        (66,  (0, -7.22, EYE_STANDING - 0.02), (up, 0, math.pi), 0.75),
        (92,  (0, -6.95, EYE_STANDING), (up, 0, turn), 3.0),                       # turn to the door
        (134, (0, -4.4, EYE_STANDING + 0.02), (up, 0, turn), 3.5),                 # down the corridor
        (160, (0, -2.2, EYE_STANDING), (math.radians(86), 0, turn), 3.0),          # into the room
        (174, (0, -1.85, EYE_STANDING), (math.radians(84), 0, turn + look), 1.7),  # the card dealer
        (184, (0, -1.8, EYE_STANDING), (math.radians(84), 0, turn + look), 1.7),
        (200, (0, -1.7, EYE_STANDING), (math.radians(84), 0, turn - look), 1.7),   # the piece dealer
        (208, (0, -1.65, EYE_STANDING), (math.radians(84), 0, turn - look), 1.7),
        (226, (0, -0.95, EYE_SEATED), (math.radians(88), 0, turn), 1.95),          # sit; he gestures to the board
        (240, (0, -0.95, EYE_SEATED), (math.radians(88), 0, turn), 1.95),
        (INTRO_FRAMES, t_loc, (t_rot[0], 0, turn), t_focus),                       # lean over the board
    ]
    for frame, loc, rot, focus in beats:
        key(cam, max(1, frame // q), loc, rot, focus)
    for frame in range(98, 134, 12):  # a slight bob while walking
        z = EYE_STANDING + (0.025 if (frame // 12) % 2 else -0.015)
        cam.location = (0, -6.95 + (frame - 92) / 42 * 2.55, z)
        cam.keyframe_insert("location", frame=max(1, frame // q))
    render_clip("intro", max(2, INTRO_FRAMES // q))


def main():
    sc, top = build_set()
    if DRAFT:
        kit.setup_eevee(sc)
    else:
        kit.setup_cycles(sc, threshold=0.05, max_samples=128)
        # Motion blur (a half-open shutter, as a film camera has): moving things smear slightly
        # within each frame, which is most of what makes rendered motion look smooth.
        sc.render.use_motion_blur = True
        sc.render.motion_blur_shutter = 0.5
    cam = make_camera()
    render_all(cam, build_views(top))
    print("DONE", OUT)


main()
