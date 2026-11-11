extends Node3D
## The whole scene, built in code: the cellar, its light, the camera at the player's seat, the board
## and the interface, and the flow of a run (menu -> play -> summary -> shop -> play ...).
## Every rule question goes to the Java process through the Rules autoload.
##
## Checking without a person:  godot --path godot -- --shot out.png [--demo]
## renders the scene, saves a screenshot and quits (--demo starts a run and plays one move first).

const SEAT := Vector3(0.0, 1.15, 0.95)            ## the player's eyes when sitting back
const LOOK_OPPONENT := Vector3(0.0, 1.12, -1.0)
const LEAN := Vector3(0.0, 1.27, 0.5)             ## leaning in over the board to play
const LOOK_BOARD := Vector3(0.0, 0.78, -0.02)

var board: Board
var hud: Hud
var camera: Camera3D
var lamp: SpotLight3D
var bulb: OmniLight3D
var post: ShaderMaterial
var opponent: Node3D

var _cam_pos := SEAT
var _cam_look := LOOK_OPPONENT
var _lamp_energy := 2.6
var _pending_move: Dictionary = {}
var _summary_due := false
var _glitch := 0.0
var _time := 0.0


func _ready() -> void:
	_build_world()
	_build_camera()
	board = Board.new()
	add_child(board)
	board.move_chosen.connect(_on_move_chosen)
	_build_post()
	hud = Hud.new()
	add_child(hud)
	hud.begin_run.connect(func(n: String): Rules.send("new " + (n if n != "" else "Anonymous")))
	hud.play_card.connect(func(i: int): Rules.send("play %d" % i))
	hud.continue_pressed.connect(_on_continue)
	hud.shop_command.connect(func(c: String): Rules.send(c))
	hud.sit_down.connect(_on_sit_down)
	hud.quit_to_menu.connect(func(): Rules.send("abandon"); Rules.send("menu"))
	Rules.answered.connect(_on_answer)
	Rules.failed.connect(func(m: String): hud.status(m))
	if Rules.running():
		Rules.send("state")
	var args := OS.get_cmdline_user_args()
	if args.has("--shot"):
		_take_shot(args[args.find("--shot") + 1], args.has("--demo"))


# ─── The world ───────────────────────────────────────────────────────────────
func _build_world() -> void:
	var room_scene: PackedScene = load("res://assets/room.glb")
	var room := room_scene.instantiate()
	add_child(room)
	var board_model := room.find_child("board", true, false) as Node3D
	if board_model != null and not room.has_meta("a1_dark"):
		board_model.rotate_y(PI / 2)          # so a1 (your left corner) is a dark square

	_seat_opponent(1)

	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color.BLACK
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.25, 0.3, 0.42)       # a breath of cold light so black isn't flat
	env.ambient_light_energy = 0.035
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure = 1.0
	env.ssao_enabled = true
	env.ssao_radius = 0.6
	env.ssao_intensity = 2.5
	env.glow_enabled = true
	env.glow_intensity = 0.35
	env.glow_bloom = 0.08
	env.glow_hdr_threshold = 1.1
	env.volumetric_fog_enabled = true                        # the lamp's cone hangs in dusty air
	env.volumetric_fog_density = 0.012
	env.volumetric_fog_albedo = Color(0.9, 0.85, 0.75)
	env.volumetric_fog_anisotropy = 0.55
	env.volumetric_fog_length = 12.0
	env.adjustment_enabled = true
	env.adjustment_contrast = 1.12
	env.adjustment_saturation = 0.7
	var world := WorldEnvironment.new()
	world.environment = env
	add_child(world)

	# The one real light: the lamp over the table.
	lamp = SpotLight3D.new()
	lamp.position = Vector3(0, 1.92, 0)
	lamp.rotation_degrees = Vector3(-90, 0, 0)
	lamp.light_color = Color(1.0, 0.78, 0.55)
	lamp.light_energy = _lamp_energy
	lamp.spot_range = 5.0
	lamp.spot_angle = 60.0
	lamp.spot_angle_attenuation = 0.9
	lamp.spot_attenuation = 1.3
	lamp.shadow_enabled = true
	lamp.shadow_blur = 1.6
	lamp.light_volumetric_fog_energy = 1.3
	add_child(lamp)
	bulb = OmniLight3D.new()
	bulb.position = Vector3(0, 1.97, 0)
	bulb.light_color = Color(1.0, 0.7, 0.4)
	bulb.light_energy = 0.5
	bulb.omni_range = 2.5
	add_child(bulb)
	# Just enough spill to find his mask and hands in the dark.
	var spill := SpotLight3D.new()
	spill.position = Vector3(0.25, 1.75, 0.05)
	spill.light_color = Color(1.0, 0.72, 0.5)
	spill.light_energy = 1.1
	spill.spot_range = 3.0
	spill.spot_angle = 22.0
	spill.shadow_enabled = true
	add_child(spill)
	spill.look_at(Vector3(0, 1.15, -0.95))
	# Candles on the stand to the left, the stove's glow behind, and a cold rim on the far wall.
	_point(Vector3(-1.13, 1.22, 0.54), Color(1.0, 0.55, 0.22), 0.55, 2.2, true)
	_point(Vector3(1.75, 0.6, 3.2), Color(1.0, 0.35, 0.1), 0.9, 3.0, false)
	_point(Vector3(0.4, 2.2, -3.2), Color(0.45, 0.6, 1.0), 0.35, 5.0, false)


## Puts opponent n in the chair across the table (if that model has been exported), breathing.
func _seat_opponent(n: int) -> void:
	if opponent != null:
		opponent.queue_free()
		opponent = null
	var path := "res://assets/opponent%d.glb" % n
	if not ResourceLoader.exists(path):
		return
	opponent = (load(path) as PackedScene).instantiate()
	opponent.position = Vector3(0, 0, -1.0)
	add_child(opponent)
	var player := opponent.find_child("AnimationPlayer", true, false) as AnimationPlayer
	if player != null and player.has_animation("idle"):
		player.get_animation("idle").loop_mode = Animation.LOOP_LINEAR
		player.play("idle")


func _point(at: Vector3, colour: Color, energy: float, reach: float, shadows: bool) -> OmniLight3D:
	var l := OmniLight3D.new()
	l.position = at
	l.light_color = colour
	l.light_energy = energy
	l.omni_range = reach
	l.shadow_enabled = shadows
	add_child(l)
	return l


func _build_camera() -> void:
	camera = Camera3D.new()
	camera.fov = 58.0
	camera.near = 0.05
	camera.position = SEAT
	var attrs := CameraAttributesPractical.new()
	attrs.dof_blur_far_enabled = true
	attrs.dof_blur_far_distance = 2.6
	attrs.dof_blur_far_transition = 3.0
	attrs.dof_blur_amount = 0.06
	camera.attributes = attrs
	add_child(camera)
	camera.look_at(LOOK_OPPONENT)
	camera.make_current()


func _build_post() -> void:
	var layer := CanvasLayer.new()
	layer.layer = 0                       # under the interface (the Hud is a later CanvasLayer)
	var rect := ColorRect.new()
	rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	post = ShaderMaterial.new()
	post.shader = load("res://shaders/post.gdshader")
	rect.material = post
	layer.add_child(rect)
	add_child(layer)


func _process(delta: float) -> void:
	_time += delta
	# The camera eases toward where it should be, and never sits perfectly still.
	var sway := Vector3(sin(_time * 0.7) * 0.004, sin(_time * 0.9 + 1.0) * 0.003, 0)
	camera.position = camera.position.lerp(_cam_pos + sway, 1.0 - exp(-delta * 3.5))
	var want := camera.global_transform.looking_at(_cam_look + sway * 2.0, Vector3.UP)
	camera.global_transform.basis = camera.global_transform.basis.slerp(want.basis, 1.0 - exp(-delta * 3.5))
	# The lamp is never quite steady.
	var flick := 1.0 + sin(_time * 13.0) * 0.012 + sin(_time * 31.0) * 0.008
	if randf() < delta * 0.25:
		flick *= randf_range(0.55, 0.85)
	lamp.light_energy = lerpf(lamp.light_energy, _lamp_energy * flick, 0.4)
	bulb.light_energy = lamp.light_energy * 0.12
	_glitch = maxf(0.0, _glitch - delta * 2.5)
	post.set_shader_parameter("glitch", _glitch)


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion:
		board.hover(board.cell_at(camera, event.position))
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		board.click(board.cell_at(camera, event.position))
	elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_RIGHT:
		_sit_back(_cam_pos == LEAN)       # right click: sit back to look at him, or lean in again
	elif event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		get_tree().quit()


func _sit_back(back: bool) -> void:
	_cam_pos = SEAT if back else LEAN
	_cam_look = LOOK_OPPONENT if back else LOOK_BOARD


# ─── The run ─────────────────────────────────────────────────────────────────
func _on_move_chosen(index: int) -> void:
	if Rules.busy:
		return
	_pending_move = Rules.state["legal"][index]
	board.enabled = false
	Rules.send("move %d" % index)


func _on_answer(command: String, state: Dictionary) -> void:
	if not state.get("ok", true):
		hud.toast(state.get("error", "Refused."), Hud.CRIMSON.lightened(0.4))
	for note in state.get("notes", []):
		hud.toast(note)
	var phase: String = state.get("phase", "MENU")
	var word := command.get_slice(" ", 0)
	hud.show_run(state, phase == "PLAYING")

	if phase == "MENU":
		_sit_back(true)
		board.show_state({})
		hud.status("")
		hud.show_menu(int(state.get("bestLevel", 0)))
		return

	if phase == "PLAYING":
		if hud.has_overlay():
			hud.close_overlay()
		_sit_back(false)
		var moved: Dictionary = {}
		if word == "move" and state.get("ok", true):
			moved = _pending_move
		elif word == "ai":
			moved = state.get("aiMove", {})
			if moved.get("capture", false) or state.get("whiteInCheck", false):
				_glitch = 0.6
		board.show_state(state, moved)
		if state.get("roundOver", false):
			board.enabled = false
			hud.status("")
			if not _summary_due:                       # let the last move land before the reckoning
				_summary_due = true
				get_tree().create_timer(1.4).timeout.connect(func(): Rules.send("finish"))
		elif not state.get("whiteToMove", true):
			board.enabled = false
			hud.status("He is thinking...")
			Rules.send("ai")
		else:
			board.enabled = true
			hud.status("Check." if state.get("whiteInCheck", false) else "Your move.")
		return

	# SHOP or GAME_OVER: first the round's summary (if one is waiting), then the dealers.
	board.enabled = false
	hud.status("")
	_sit_back(true)
	if word == "finish" and state.has("summary"):
		_summary_due = false
		if state["summary"].get("result", "") == "BLACK_WINS":
			_glitch = 1.0
		hud.show_summary(state["summary"])
	elif phase == "GAME_OVER":
		Rules.send("menu")
	else:
		board.show_state({})
		hud.show_shop(state)


func _on_continue() -> void:
	var state := Rules.state
	if state.get("phase", "") == "GAME_OVER" or state.get("summary", {}).get("gameOver", false):
		Rules.send("menu")
	else:
		board.show_state({})
		hud.show_shop(state)


func _on_sit_down() -> void:
	# For now the first cards of the stash (up to the hand size) come to the table.
	var stash: Array = Rules.state.get("shop", {}).get("stash", [])
	var take: Array[String] = []
	for i in mini(stash.size(), int(Rules.state.get("handSize", 5))):
		take.append(str(i))
	Rules.send("sit " + ",".join(take))


# ─── Screenshots for checking ────────────────────────────────────────────────
func _take_shot(path: String, demo: bool) -> void:
	await get_tree().create_timer(2.0).timeout
	if demo:
		Rules.send("new Tester")
		await get_tree().create_timer(2.5).timeout
		board.click(Vector2i(6, 4))
		await get_tree().create_timer(0.6).timeout
		if "--move" in OS.get_cmdline_user_args():
			board.click(Vector2i(5, 4))
			await get_tree().create_timer(4.0).timeout
	# --panel summary / shop: show that screen with sample numbers (to check its layout).
	var args := OS.get_cmdline_user_args()
	if args.has("--panel"):
		var card := {"name": "Heavy Armor", "text": "First attack stuns the target for a turn instead of capturing.", "rarity": "UNCOMMON", "global": true, "price": 37}
		var card2 := {"name": "Pawn Surge", "text": "Your pawns may advance two squares from any rank.", "rarity": "COMMON", "global": false, "price": 15}
		var sample := {"phase": "SHOP", "level": 3, "gold": 64, "requirement": 6, "handSize": 5,
			"shop": {"material": 4, "meets": false, "pawnCap": 3, "packOpened": false, "packPrice": 14, "pack": [],
				"pieces": [{"type": "Pawn", "owned": 2, "price": 5, "sell": 2}, {"type": "Knight", "owned": 1, "price": 15, "sell": 7},
					{"type": "Bishop", "owned": 0, "price": 15, "sell": 7}, {"type": "Rook", "owned": 0, "price": 25, "sell": 12},
					{"type": "Queen", "owned": 0, "price": 45, "sell": 22}],
				"offer": [card, card2, {"name": "Time Stop", "text": "Take two moves in a row, once.", "rarity": "RARE", "global": false, "price": 60}],
				"stash": [card2, card]}}
		hud.close_overlay()
		hud.show_run(sample, false)
		if args[args.find("--panel") + 1] == "none":
			pass                                   # just the room, seen from the seat
		elif args[args.find("--panel") + 1] == "shop":
			hud.show_shop(sample)
		else:
			hud.show_summary({"result": "WHITE_WINS", "level": 2, "nextLevel": 3, "own": 4, "defeated": 6, "bonus": 7,
				"base": 21, "interest": 3, "total": 41, "goldAfter": 64, "cardsUsedUp": ["Pawn Surge"]})
		await get_tree().create_timer(1.0).timeout
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(path)
	print("SHOT ", path)
	get_tree().quit()
