@echo off
rem ============================================================
rem  Restart ONLY the backend (port 8080) with the latest JAR.
rem  Does NOT touch MySQL/Hardhat/frontend/ComfyUI, so the local
rem  chain and existing anchors are preserved.
rem  Use this after a backend code change (e.g. retry / login fixes).
rem ============================================================
set ROOT=C:\Users\YYDS\WorkBuddy\2026-10-30-20-40-09\
set BACKEND=%ROOT%friend-project\.staging\backend

rem Load local secrets from friend-project/.env
if not exist "%ROOT%friend-project\.env" (
  echo [ERROR] friend-project\.env not found.
  pause
  exit /b 1
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%friend-project\.env") do set "%%A=%%B"

rem Stop any backend already bound to 8080
powershell -NoProfile -Command "$p=(Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue).OwningProcess | Sort -Unique; if($p){Stop-Process -Id $p -Force; Write-Host 'stopped old backend pid(s): ' ($p -join ',')}"

echo [backend] Starting backend on 8080 (Spring Boot, profile=local)...
start "backend-8080" cmd /k "cd /d %BACKEND% && java -jar target\backend-1.0.0.jar --spring.profiles.active=local --server.port=8080"
echo [backend] Launched. Wait until the window shows "Started" / "Tomcat started".
pause
