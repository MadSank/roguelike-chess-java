extends Node
## The link to the game rules, which run in Java (GameServer.java, compiled into ../out).
##
## send("move 3") queues a command; the answer (the whole run as a Dictionary) arrives later through
## the `answered` signal and is kept in `state`. Commands run one at a time on a worker thread, so a
## slow one (the opponent thinking) never freezes the picture.

signal answered(command: String, state: Dictionary)
signal failed(message: String)

var state: Dictionary = {}
var busy: bool = false

var _pipe: FileAccess
var _pid: int = -1
var _thread: Thread
var _queue: Array[String] = []
var _mutex := Mutex.new()
var _wake := Semaphore.new()
var _quit := false


func _ready() -> void:
	var classes := ProjectSettings.globalize_path("res://").path_join("../out").simplify_path()
	if not FileAccess.file_exists(classes.path_join("GameServer.class")):
		failed.emit.call_deferred("The Java rules aren't compiled. Run play3d.cmd (or test.cmd) once.")
		return
	var started := OS.execute_with_pipe("java", ["-cp", classes, "GameServer"])
	if started.is_empty():
		failed.emit.call_deferred("Could not start Java. Is it installed and on the PATH?")
		return
	_pipe = started["stdio"]
	_pid = started["pid"]
	_thread = Thread.new()
	_thread.start(_work)


func running() -> bool:
	return _pipe != null


## Queues a command for the rules. The answer comes through `answered`.
func send(command: String) -> void:
	if _pipe == null:
		return
	_mutex.lock()
	_queue.append(command)
	_mutex.unlock()
	busy = true
	_wake.post()


func _work() -> void:
	while true:
		_wake.wait()
		if _quit:
			return
		_mutex.lock()
		var command: String = _queue.pop_front() if not _queue.is_empty() else ""
		_mutex.unlock()
		if command == "":
			continue
		_pipe.store_string(command + "\n")
		_pipe.flush()
		var line := ""
		while line.strip_edges() == "" and not _quit:
			line = _pipe.get_line()
			if line == "" and _pipe.get_error() != OK:
				_fail.call_deferred("The rules process stopped.")
				return
		var parsed: Variant = JSON.parse_string(line)
		if typeof(parsed) != TYPE_DICTIONARY:
			_fail.call_deferred("The rules sent something unreadable.")
			continue
		_answer.call_deferred(command, parsed)


func _answer(command: String, data: Dictionary) -> void:
	state = data
	_mutex.lock()
	busy = not _queue.is_empty()
	_mutex.unlock()
	answered.emit(command, data)


func _fail(message: String) -> void:
	busy = false
	failed.emit(message)


func _exit_tree() -> void:
	_quit = true
	if _pipe != null:
		_pipe.store_string("quit\n")
		_pipe.flush()
	_wake.post()
	if _thread != null and _thread.is_started():
		_thread.wait_to_finish()
	if _pid > 0 and OS.is_process_running(_pid):
		OS.kill(_pid)
