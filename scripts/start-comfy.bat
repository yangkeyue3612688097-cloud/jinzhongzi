@echo off
rem ============================================================
rem  ComfyUI standalone launcher (use when other services already running)
rem  Includes --enable-cors-header to fix "local generate" 403 cross-origin error.
rem  Auto-kills any existing ComfyUI on port 8188, then restarts with the fix.
rem  Usage: just double-click this file.
rem ============================================================
set ROOT=C:\Users\YYDS\WorkBuddy\2026-10-30-20-40-09\
set COMFY=%ROOT%tools\ComfyUI
set COMFYVENV=%ROOT%tools\comfy-venv\Scripts\python.exe

if not exist "%COMFYVENV%" (echo [ERROR] comfy-venv not found: %COMFYVENV% & pause & exit /b 1)
if not exist "%COMFY%\main.py" (echo [ERROR] ComfyUI main.py not found: %COMFY% & pause & exit /b 1)

echo Stopping any existing ComfyUI on port 8188 ...
taskkill /fi "WINDOWTITLE eq comfy-8188*" /f >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :8188 ^| findstr LISTENING') do taskkill /f /pid %%a >nul 2>&1
ping -n 3 127.0.0.1 >nul

echo Starting ComfyUI on 8188 (CPU mode, CORS enabled)...
start "comfy-8188" cmd /k "cd /d %COMFY% && %COMFYVENV% main.py --cpu --listen 127.0.0.1 --port 8188 --enable-cors-header"

set /a n=0
:wait
curl -s --noproxy * -m 3 http://127.0.0.1:8188/system_stats >nul 2>&1
if %errorlevel%==0 goto ok
set /a n+=1
if %n% geq 40 (echo [ERROR] ComfyUI failed to start, check comfy-8188 window & pause & exit /b 1)
ping -n 3 127.0.0.1 >nul
goto wait
:ok
echo.
echo   ComfyUI is ready (with --enable-cors-header). Go back to the page and retry "local generate".
echo   On CPU, the first image takes about 15-30s. Please wait for the progress bar.
echo.
pause
