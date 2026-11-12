extends CanvasLayer
class_name Hud
## Everything drawn over the 3D picture, in one style: dark glass panels, a serif face, bone-white
## text with crimson and old-gold accents. No operating-system dialogs anywhere.
## The hud only shows what the rules report and emits what the player asks for.

signal begin_run(player_name: String)
signal play_card(index: int)
signal continue_pressed
signal shop_command(command: String)
signal sit_down
signal quit_to_menu
signal turn(step: int)          ## in the shop: turn to the next dealer left (-1) or right (+1)

const BONE := Color(0.86, 0.82, 0.74)
const DIM := Color(0.55, 0.52, 0.48)
const CRIMSON := Color(0.72, 0.1, 0.12)
const GOLD := Color(0.83, 0.66, 0.27)
const RARITY := {"COMMON": Color(0.6, 0.58, 0.55), "UNCOMMON": Color(0.35, 0.62, 0.85), "RARE": Color(0.78, 0.45, 0.95)}

var _serif: SystemFont
var _root: Control
var _top: Label
var _gold: Label
var _status: Label
var _hand: VBoxContainer
var _toasts: VBoxContainer
var _overlay: Control           # the menu, summary or shop panel currently open (or null)
var overlay_kind := ""          ## which panel is open: "menu", "summary", "shop" or ""


func _ready() -> void:
	_serif = SystemFont.new()
	_serif.font_names = PackedStringArray(["Georgia", "Palatino Linotype", "Times New Roman", "serif"])
	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_root)

	_top = text("", 20, BONE)
	_top.position = Vector2(36, 26)
	_root.add_child(_top)
	_gold = text("", 26, GOLD)
	_gold.position = Vector2(36, 54)
	_root.add_child(_gold)

	_status = text("", 22, BONE)
	_status.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	_status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_status.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_status.position.y -= 70
	_root.add_child(_status)

	_hand = VBoxContainer.new()
	_hand.set_anchors_preset(Control.PRESET_CENTER_RIGHT)
	_hand.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_hand.grow_vertical = Control.GROW_DIRECTION_BOTH
	_hand.position.x -= 30
	_hand.add_theme_constant_override("separation", 10)
	_root.add_child(_hand)

	_toasts = VBoxContainer.new()
	_toasts.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_toasts.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_toasts.position.y += 110
	_toasts.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(_toasts)


# ─── Building blocks ─────────────────────────────────────────────────────────
func text(content: String, size: int, colour: Color) -> Label:
	var l := Label.new()
	l.text = content
	l.add_theme_font_override("font", _serif)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", colour)
	l.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.8))
	l.add_theme_constant_override("shadow_offset_y", 2)
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return l


func box(fill: Color, border: Color, radius: int = 4, pad: int = 14) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = fill
	s.border_color = border
	s.set_border_width_all(1)
	s.set_corner_radius_all(radius)
	s.set_content_margin_all(pad)
	s.shadow_color = Color(0, 0, 0, 0.55)
	s.shadow_size = 12
	return s


func panel(border: Color = Color(0.35, 0.1, 0.1), pad: int = 14) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", box(Color(0.03, 0.025, 0.03, 0.88), border, 4, pad))
	return p


func button(label: String, action: Callable, accent: Color = CRIMSON, size: int = 17) -> Button:
	var b := Button.new()
	b.text = label
	b.add_theme_font_override("font", _serif)
	b.add_theme_font_size_override("font_size", size)
	b.add_theme_color_override("font_color", BONE)
	b.add_theme_color_override("font_hover_color", Color.WHITE)
	b.add_theme_color_override("font_disabled_color", Color(0.4, 0.38, 0.36))
	b.add_theme_stylebox_override("normal", box(Color(accent.r, accent.g, accent.b, 0.18), Color(accent.r, accent.g, accent.b, 0.7), 3, 8))
	b.add_theme_stylebox_override("hover", box(Color(accent.r, accent.g, accent.b, 0.42), accent.lightened(0.3), 3, 8))
	b.add_theme_stylebox_override("pressed", box(Color(accent.r, accent.g, accent.b, 0.6), accent.lightened(0.5), 3, 8))
	b.add_theme_stylebox_override("disabled", box(Color(0.1, 0.1, 0.1, 0.4), Color(0.25, 0.24, 0.23), 3, 8))
	b.add_theme_stylebox_override("focus", StyleBoxEmpty.new())
	b.pressed.connect(action)
	return b


func _open(content: Control, width: float = 0.0) -> void:
	close_overlay()
	var holder := CenterContainer.new()
	holder.set_anchors_preset(Control.PRESET_FULL_RECT)
	if width > 0.0:
		content.custom_minimum_size.x = width
	holder.add_child(content)
	_root.add_child(holder)
	_overlay = holder
	holder.modulate.a = 0.0
	create_tween().tween_property(holder, "modulate:a", 1.0, 0.35)


func close_overlay() -> void:
	overlay_kind = ""
	if _overlay != null:
		_overlay.queue_free()
		_overlay = null


func has_overlay() -> bool:
	return _overlay != null


# ─── The always-visible parts ────────────────────────────────────────────────
func show_run(state: Dictionary, playing: bool) -> void:
	var in_run: bool = state.get("phase", "MENU") != "MENU"
	_top.visible = in_run
	_gold.visible = in_run
	_hand.visible = playing
	if not in_run:
		return
	var shop: Dictionary = state.get("shop", {})
	_top.text = "LEVEL %d    material %d / %d" % [int(state.get("level", 1)), int(shop.get("material", 0)), int(state.get("requirement", 0))]
	_gold.text = "%d gold" % int(state.get("gold", 0))
	for child in _hand.get_children():
		child.queue_free()
	if not playing:
		return
	var hand: Array = state.get("hand", [])
	var can_play: bool = state.get("whiteToMove", false) and not state.get("roundOver", false)
	for i in hand.size():
		_hand.add_child(_card(hand[i], "ACTIVE" if hand[i].get("played", false) else "Play",
			func(): play_card.emit(i), can_play and not hand[i].get("played", false), 250))


func _card(card: Dictionary, action_label: String, action: Callable, enabled: bool, width: int) -> PanelContainer:
	var colour: Color = RARITY.get(card.get("rarity", "COMMON"), DIM)
	var p := panel(colour.darkened(0.3), 10)
	p.custom_minimum_size.x = width
	var v := VBoxContainer.new()
	p.add_child(v)
	var kind := text("%s  ·  %s" % [String(card.get("rarity", "")).to_lower(), "both sides" if card.get("global", false) else "yours"], 11, colour)
	v.add_child(kind)
	v.add_child(text(card.get("name", ""), 17, BONE))
	var body := text(card.get("text", ""), 12, DIM)
	body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.custom_minimum_size.x = width - 24
	v.add_child(body)
	if action_label != "":
		var b := button(action_label, action, CRIMSON if action_label != "ACTIVE" else Color(0.3, 0.6, 0.35), 13)
		b.disabled = not enabled
		b.size_flags_horizontal = Control.SIZE_SHRINK_END
		v.add_child(b)
	return p


func status(message: String) -> void:
	_status.text = message


func toast(message: String, colour: Color = GOLD) -> void:
	var l := text(message, 22, colour)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_toasts.add_child(l)
	var tween := create_tween()
	tween.tween_interval(1.6)
	tween.tween_property(l, "modulate:a", 0.0, 0.7)
	tween.tween_callback(l.queue_free)


# ─── Panels ──────────────────────────────────────────────────────────────────
func show_menu(best_level: int) -> void:
	var v := VBoxContainer.new()
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	v.add_theme_constant_override("separation", 14)
	var title := text("A S H E S", 96, BONE)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(title)
	var sub := text("of the board", 26, CRIMSON)
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(sub)
	var gap := Control.new()
	gap.custom_minimum_size.y = 40
	v.add_child(gap)
	var name_box := LineEdit.new()
	name_box.placeholder_text = "your name"
	name_box.alignment = HORIZONTAL_ALIGNMENT_CENTER
	name_box.max_length = 20
	name_box.add_theme_font_override("font", _serif)
	name_box.add_theme_font_size_override("font_size", 20)
	name_box.add_theme_color_override("font_color", BONE)
	name_box.add_theme_stylebox_override("normal", box(Color(0, 0, 0, 0.5), Color(0.3, 0.28, 0.26), 3, 8))
	name_box.add_theme_stylebox_override("focus", box(Color(0, 0, 0, 0.6), CRIMSON, 3, 8))
	name_box.custom_minimum_size = Vector2(320, 0)
	name_box.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(name_box)
	var begin := button("Sit down", func(): begin_run.emit(name_box.text.strip_edges()), CRIMSON, 22)
	begin.custom_minimum_size = Vector2(320, 0)
	begin.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(begin)
	name_box.text_submitted.connect(func(_t): begin_run.emit(name_box.text.strip_edges()))
	if best_level > 0:
		var best := text("highest level beaten: %d" % best_level, 15, DIM)
		best.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(best)
	var leave := button("Leave", func(): get_tree().quit(), Color(0.4, 0.4, 0.4), 15)
	leave.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(leave)
	_open(v)
	overlay_kind = "menu"


func show_summary(s: Dictionary) -> void:
	var result: String = s.get("result", "")
	var p := panel(GOLD.darkened(0.4) if result == "WHITE_WINS" else CRIMSON.darkened(0.2), 28)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 8)
	p.add_child(v)
	var heading := {"WHITE_WINS": "He is beaten.", "BLACK_WINS": "Checkmate.", "DRAW": "A draw."}
	var title := text(heading.get(result, "The round is over."), 40, GOLD if result == "WHITE_WINS" else BONE)
	v.add_child(title)
	var line := ""
	if result == "WHITE_WINS":
		line = "Level %d is behind you." % int(s.get("level", 0))
	elif result == "BLACK_WINS":
		line = "Only your King is left. Rebuild, and face level %d again." % int(s.get("level", 0))
	else:
		line = "%s  You keep your pieces and replay level %d." % [s.get("text", ""), int(s.get("level", 0))]
	v.add_child(text(line, 17, BONE))
	if s.has("total"):
		var rows := [["Your material left", s.get("own", 0)], ["Enemy material defeated", s.get("defeated", 0)],
			["Good moves", s.get("bonus", 0)], ["Level bonus", s.get("base", 0)], ["Interest on savings", s.get("interest", 0)]]
		var grid := GridContainer.new()
		grid.columns = 2
		grid.add_theme_constant_override("h_separation", 60)
		for row in rows:
			grid.add_child(text(row[0], 16, DIM))
			var n := text(str(int(row[1])), 16, BONE)
			n.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
			grid.add_child(n)
		v.add_child(grid)
		v.add_child(text("+ %d gold" % int(s.get("total", 0)), 26, GOLD))
	var used: Array = s.get("cardsUsedUp", [])
	if not used.is_empty():
		v.add_child(text("Cards used up: " + ", ".join(PackedStringArray(used)), 14, DIM))
	if s.get("gameOver", false):
		v.add_child(text("You cannot field an army for level %d. The run ends here." % int(s.get("nextLevel", 0)), 17, CRIMSON.lightened(0.3)))
		if s.get("madeLeaderboard", false):
			v.add_child(text("Your name goes on the wall.", 16, GOLD))
	var b := button("Leave the table" if s.get("gameOver", false) else "Continue", func(): continue_pressed.emit(), CRIMSON, 20)
	b.size_flags_horizontal = Control.SIZE_SHRINK_END
	v.add_child(b)
	_open(p, 560)
	overlay_kind = "summary"


## The shop is the three dealers: you turn to one and his panel is beside him.
## view: "table" (what the level needs, and sitting down), "piece", "card" or "resell".
func show_shop(state: Dictionary, view: String = "table") -> void:
	var shop: Dictionary = state.get("shop", {})
	var gold := int(state.get("gold", 0))
	close_overlay()
	var holder := Control.new()
	holder.set_anchors_preset(Control.PRESET_FULL_RECT)
	holder.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(holder)
	_overlay = holder
	overlay_kind = "shop"

	# The dealer's own panel, to the right of where he stands in the picture.
	var p := panel(Color(0.35, 0.1, 0.1), 20)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 10)
	p.add_child(v)
	if view == "piece":
		v.add_child(text("THE PIECE DEALER", 22, BONE))
		v.add_child(text("Pawns allowed: %d" % int(shop.get("pawnCap", 0)), 14, DIM))
		for item in shop.get("pieces", []):
			var row := HBoxContainer.new()
			row.add_theme_constant_override("separation", 10)
			var name_label := text("%s  x%d" % [item["type"], int(item["owned"])], 17, BONE)
			name_label.custom_minimum_size.x = 150
			row.add_child(name_label)
			var kind: String = item["type"]
			var buy := button("Buy  %d" % int(item["price"]), func(): shop_command.emit("buy " + kind), CRIMSON, 14)
			buy.disabled = gold < int(item["price"])
			row.add_child(buy)
			v.add_child(row)
	elif view == "card":
		v.add_child(text("THE CARD DEALER", 22, BONE))
		var cards: Array = shop.get("offer", [])
		if cards.is_empty():
			v.add_child(text("Nothing left on the table.", 14, DIM))
		for i in cards.size():
			var price := int(cards[i].get("price", 0))
			v.add_child(_card(cards[i], "Buy  %d" % price, func(): shop_command.emit("buycard %d" % i), gold >= price, 300))
		if shop.get("packOpened", false) and not (shop.get("pack", []) as Array).is_empty():
			v.add_child(text("Take one from the pack:", 15, GOLD))
			var pack: Array = shop.get("pack", [])
			for i in pack.size():
				v.add_child(_card(pack[i], "Take", func(): shop_command.emit("draft %d" % i), true, 300))
		elif not shop.get("packOpened", false):
			var open := button("Mystery pack   %d" % int(shop.get("packPrice", 0)), func(): shop_command.emit("openpack"), Color(0.5, 0.25, 0.7), 15)
			open.disabled = gold < int(shop.get("packPrice", 0))
			v.add_child(open)
	elif view == "resell":
		v.add_child(text("THE RESELLER", 22, BONE))
		v.add_child(text("He pays half.", 14, DIM))
		for item in shop.get("pieces", []):
			if int(item["owned"]) <= 0:
				continue
			var row := HBoxContainer.new()
			row.add_theme_constant_override("separation", 10)
			var name_label := text("%s  x%d" % [item["type"], int(item["owned"])], 16, BONE)
			name_label.custom_minimum_size.x = 170
			row.add_child(name_label)
			var kind: String = item["type"]
			row.add_child(button("Sell  +%d" % int(item["sell"]), func(): shop_command.emit("sell " + kind), GOLD.darkened(0.2), 13))
			v.add_child(row)
		var stash: Array = shop.get("stash", [])
		for i in stash.size():
			var row := HBoxContainer.new()
			row.add_theme_constant_override("separation", 10)
			var label := text(stash[i].get("name", ""), 15, RARITY.get(stash[i].get("rarity", "COMMON"), DIM))
			label.custom_minimum_size.x = 170
			row.add_child(label)
			row.add_child(button("Sell  +%d" % int(stash[i].get("price", 0)), func(): shop_command.emit("sellcard %d" % i), GOLD.darkened(0.2), 13))
			v.add_child(row)
	else:
		v.add_child(text("Before level %d" % int(state.get("level", 1)), 30, BONE))
		v.add_child(text("He will not play you with less than %d material.\nYou have %d." % [int(state.get("requirement", 0)),
			int(shop.get("material", 0))], 16, BONE if shop.get("meets", false) else CRIMSON.lightened(0.35)))
		var stash: Array = shop.get("stash", [])
		var hand := mini(stash.size(), int(state.get("handSize", 5)))
		if hand > 0:
			var names: Array[String] = []
			for i in hand:
				names.append(stash[i].get("name", ""))
			var take := text("Your hand: " + ", ".join(PackedStringArray(names)), 14, DIM)
			take.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			take.custom_minimum_size.x = 300
			v.add_child(take)
		v.add_child(text("The dealers stand around you.", 14, DIM))
	p.set_anchors_preset(Control.PRESET_CENTER_RIGHT)
	p.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	p.grow_vertical = Control.GROW_DIRECTION_BOTH
	p.position.x -= 60
	holder.add_child(p)

	# Along the bottom: turn left, sit down, turn right.
	var views := ["card", "table", "piece", "resell"]
	var names := {"card": "Card dealer", "table": "The table", "piece": "Piece dealer", "resell": "Reseller"}
	var at := views.find(view)
	var bar := HBoxContainer.new()
	bar.add_theme_constant_override("separation", 16)
	bar.alignment = BoxContainer.ALIGNMENT_CENTER
	var left := button("<  " + (names[views[at - 1]] if at > 0 else ""), func(): turn.emit(-1), Color(0.45, 0.42, 0.4), 16)
	left.disabled = at <= 0
	left.custom_minimum_size.x = 190
	bar.add_child(left)
	var sit := button("Sit down" if shop.get("meets", false) else "Not enough material", func(): sit_down.emit(), CRIMSON, 20)
	sit.disabled = not shop.get("meets", false)
	sit.custom_minimum_size.x = 260
	bar.add_child(sit)
	var right := button((names[views[at + 1]] if at < views.size() - 1 else "") + "  >", func(): turn.emit(1), Color(0.45, 0.42, 0.4), 16)
	right.disabled = at >= views.size() - 1
	right.custom_minimum_size.x = 190
	bar.add_child(right)
	bar.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	bar.grow_horizontal = Control.GROW_DIRECTION_BOTH
	bar.grow_vertical = Control.GROW_DIRECTION_BEGIN
	bar.position.y -= 40
	holder.add_child(bar)
	var leave := button("Give up", func(): quit_to_menu.emit(), Color(0.4, 0.4, 0.4), 13)
	leave.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	leave.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	leave.grow_vertical = Control.GROW_DIRECTION_BEGIN
	leave.position -= Vector2(30, 30)
	holder.add_child(leave)
