extends Node3D
class_name Board
## The chess board on the table: shows the position the rules report, lets the player pick a piece
## and a square, and animates moves. It never decides what is legal: it only offers the moves in
## the state's "legal" list and reports the chosen one's index.

signal move_chosen(index: int)
signal choice_needed(indices: Array)   ## several moves go to one square (promotions, snipe or capture): ask

const SQUARE := 0.05789          ## metres per square (from the chess set model)
const TOP := 0.7815              ## height of the board's playing surface
const KINDS := ["K", "Q", "R", "B", "N", "P"]

var enabled := true

var _models: Dictionary = {}     # "wK" -> Mesh
var _pieces: Dictionary = {}     # Vector2i(row, col) -> MeshInstance3D
var _codes: Dictionary = {}      # Vector2i(row, col) -> "wK"
var _legal: Array = []
var _selected := Vector2i(-1, -1)
var _marks: Node3D
var _hover: MeshInstance3D
var _select_mat: StandardMaterial3D
var _target_mat: StandardMaterial3D
var _capture_mat: StandardMaterial3D
var _check_mat: StandardMaterial3D


func _ready() -> void:
	var set_scene: PackedScene = load("res://assets/pieces.glb")
	var root := set_scene.instantiate()
	for colour in ["w", "b"]:
		for kind in KINDS:
			var node := root.find_child(colour + kind, true, false) as MeshInstance3D
			if node != null:
				_models[colour + kind] = node.mesh
	root.free()
	_marks = Node3D.new()
	add_child(_marks)
	_select_mat = _glow(Color(1.0, 0.72, 0.3), 0.55)
	_target_mat = _glow(Color(0.95, 0.85, 0.6), 0.32)
	_capture_mat = _glow(Color(0.9, 0.12, 0.1), 0.55)
	_check_mat = _glow(Color(0.9, 0.05, 0.05), 0.7)
	_hover = _mark(Vector2i(0, 0), _glow(Color(1, 1, 1), 0.12))
	_hover.visible = false


func _glow(colour: Color, alpha: float) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(colour.r, colour.g, colour.b, alpha)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.emission_enabled = true
	m.emission = colour
	m.emission_energy_multiplier = 1.4
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	return m


## Where the centre of a square is, in this node's space (row 0 is the opponent's back rank).
func square_pos(cell: Vector2i) -> Vector3:
	return Vector3((cell.y - 3.5) * SQUARE, TOP, (cell.x - 3.5) * SQUARE)


func _mark(cell: Vector2i, mat: Material, size: float = 0.92) -> MeshInstance3D:
	var quad := PlaneMesh.new()
	quad.size = Vector2(SQUARE * size, SQUARE * size)
	var m := MeshInstance3D.new()
	m.mesh = quad
	m.material_override = mat
	m.position = square_pos(cell) + Vector3(0, 0.0012, 0)
	m.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_marks.add_child(m)
	return m


## Shows the position in `state`. `moved` (optional {fr, fc, tr, tc}) is slid rather than jumped.
func show_state(state: Dictionary, moved: Dictionary = {}) -> void:
	_legal = state.get("legal", [])
	_selected = Vector2i(-1, -1)
	var board: Array = state.get("board", [])
	var wanted: Dictionary = {}
	for r in board.size():
		for c in board[r].size():
			if board[r][c] != "":
				wanted[Vector2i(r, c)] = board[r][c]

	# The piece that moved slides to its new square (taking whatever stood there).
	if not moved.is_empty():
		var from := Vector2i(int(moved["fr"]), int(moved["fc"]))
		var to := Vector2i(int(moved["tr"]), int(moved["tc"]))
		if bool(moved.get("snipe", false)):
			to = from                                   # a sniper shot: the shooter stays put
		if _pieces.has(from) and wanted.has(to) and from != to:
			if _pieces.has(to):
				_remove(to)
			var node: MeshInstance3D = _pieces[from]
			_pieces.erase(from)
			_codes.erase(from)
			_pieces[to] = node
			_codes[to] = wanted[to]
			node.mesh = _models.get(wanted[to], node.mesh)   # a promotion changes the piece
			var tween := create_tween()
			var top := (node.position + square_pos(to)) / 2.0 + Vector3(0, 0.035, 0)
			tween.tween_property(node, "position", top, 0.16).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
			tween.tween_property(node, "position", square_pos(to), 0.16).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN)

	# Everything else: remove what is gone or different, add what is new.
	for cell in _pieces.keys():
		if not wanted.has(cell) or _codes[cell] != wanted[cell]:
			_remove(cell)
	for cell in wanted.keys():
		if not _pieces.has(cell):
			_add(cell, wanted[cell])
	_refresh_marks(state)


func _add(cell: Vector2i, code: String) -> void:
	var node := MeshInstance3D.new()
	node.mesh = _models.get(code)
	node.position = square_pos(cell)
	node.rotation.y = PI
	add_child(node)
	_pieces[cell] = node
	_codes[cell] = code
	node.scale = Vector3.ONE * 0.01
	create_tween().tween_property(node, "scale", Vector3.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


func _remove(cell: Vector2i) -> void:
	var node: MeshInstance3D = _pieces[cell]
	_pieces.erase(cell)
	_codes.erase(cell)
	var tween := create_tween()
	tween.tween_property(node, "scale", Vector3.ONE * 0.01, 0.2)
	tween.tween_callback(node.queue_free)


func _refresh_marks(state: Dictionary = Rules.state) -> void:
	for child in _marks.get_children():
		if child != _hover:
			child.queue_free()
	if state.get("whiteInCheck", false) or state.get("blackInCheck", false):
		var king := "wK" if state.get("whiteInCheck", false) else "bK"
		for cell in _codes.keys():
			if _codes[cell] == king:
				_mark(cell, _check_mat)
	if _selected.x < 0:
		return
	_mark(_selected, _select_mat)
	for m in _legal:
		if int(m["fr"]) == _selected.x and int(m["fc"]) == _selected.y:
			var target := _target_of(m)
			_mark(target, _capture_mat if bool(m["capture"]) else _target_mat, 0.92 if bool(m["capture"]) else 0.42)


func _target_of(m: Dictionary) -> Vector2i:
	if bool(m.get("snipe", false)):
		return Vector2i(int(m["sr"]), int(m["sc"]))
	return Vector2i(int(m["tr"]), int(m["tc"]))


## The square under a screen position, or (-1, -1).
func cell_at(camera: Camera3D, screen: Vector2) -> Vector2i:
	var origin := to_local(camera.project_ray_origin(screen))
	var dir := global_transform.basis.inverse() * camera.project_ray_normal(screen)
	if absf(dir.y) < 0.0001:
		return Vector2i(-1, -1)
	var t := (TOP - origin.y) / dir.y
	if t < 0:
		return Vector2i(-1, -1)
	var hit := origin + dir * t
	var col := int(floor(hit.x / SQUARE + 4.0))
	var row := int(floor(hit.z / SQUARE + 4.0))
	if col < 0 or col > 7 or row < 0 or row > 7:
		return Vector2i(-1, -1)
	return Vector2i(row, col)


func hover(cell: Vector2i) -> void:
	_hover.visible = enabled and cell.x >= 0
	if cell.x >= 0:
		_hover.position = square_pos(cell) + Vector3(0, 0.001, 0)


## A click on a square: select one of your pieces, or play a move to that square.
func click(cell: Vector2i) -> void:
	if not enabled or cell.x < 0:
		return
	if _selected.x >= 0:
		var matches: Array[int] = []
		for i in _legal.size():
			var m: Dictionary = _legal[i]
			if int(m["fr"]) == _selected.x and int(m["fc"]) == _selected.y and _target_of(m) == cell:
				matches.append(i)
		if not matches.is_empty():
			_selected = Vector2i(-1, -1)
			_refresh_marks()
			if matches.size() == 1:
				move_chosen.emit(matches[0])
			else:                                       # a promotion, or snipe versus capture: the player decides
				choice_needed.emit(matches)
			return
	var code: String = _codes.get(cell, "")
	_selected = cell if code.begins_with("w") else Vector2i(-1, -1)
	_refresh_marks()
