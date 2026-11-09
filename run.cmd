@echo off
rem Compiles the game into the "out" folder and starts it.
rem Double-click this file, or run it from PowerShell / Command Prompt: .\run.cmd
cd /d "%~dp0"
if not exist out mkdir out
javac -d out *.java
if errorlevel 1 (
    echo Compile failed.
    pause
    exit /b 1
)
java -cp out RoguelikeChessAppSwing
