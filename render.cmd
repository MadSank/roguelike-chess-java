@echo off
rem Renders the game's scenes with Blender, carrying on from where an earlier render stopped.
rem Run from PowerShell / Command Prompt: .\render.cmd
rem Needs: tools\blender\fetch_assets.py and tools\blender\make_characters.py to have been run once.
cd /d "%~dp0"
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" -b --factory-startup -P tools\blender\build_scenes.py -- --resume
