@echo off
rem Compiles everything into the "out" folder and runs the headless tests.
rem Run from PowerShell / Command Prompt: .\test.cmd
cd /d "%~dp0"
if not exist out mkdir out
javac -d out *.java
if errorlevel 1 (
    echo Compile failed.
    exit /b 1
)
set FAILED=0
for %%T in (TestPerft TestRules TestCards TestRunFlow) do (
    java -cp out %%T
    if errorlevel 1 set FAILED=1
)
if %FAILED%==1 (
    echo.
    echo SOME TESTS FAILED
    exit /b 1
)
echo.
echo ALL TESTS PASSED
