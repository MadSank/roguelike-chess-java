@echo off
rem Starts the 3D version of the game (Godot front end + the Java rules).
rem Run from PowerShell / Command Prompt: .\play3d.cmd
cd /d "%~dp0"
if not exist out mkdir out
javac -d out *.java
if errorlevel 1 (
    echo Compile failed.
    exit /b 1
)
if not exist tools\godot\Godot_v4.7.2-stable_win64.exe (
    echo Godot is missing: unzip Godot 4.7.2 for Windows into tools\godot (see ENGINE_PLAN.md^).
    exit /b 1
)
if not exist godot\assets\room.glb (
    echo The 3D assets are missing: run tools\blender\export_godot.py with Blender (see ENGINE_PLAN.md^).
    exit /b 1
)
start "" tools\godot\Godot_v4.7.2-stable_win64.exe --path godot
