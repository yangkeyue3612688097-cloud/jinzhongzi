@echo off
rem ============================================================
rem  Local dev stack one-click launcher (double-click to use)
rem  1) MySQL   3307  (data dir .\mysql-data2, root pass = DB_PASSWORD in .env)
rem  2) Chain   8545  (Hardhat node)
rem  3) Deploy contracts (auto-patch anchor address into backend yml)
rem  4) Backend 8080  (Spring Boot, profile=local, chain mode=evm)
rem  5) Frontend 5173 (Vite, proxies /comfy -> 127.0.0.1:8188)
rem  6) ComfyUI 8188  (local SD-Turbo image generation, CPU mode)
rem  Demo accounts: demo / 123456 (main)   partner / 123456 (collaborator)
rem ============================================================

rem ROOT is FIXED so the script works even if launched from another folder (e.g. desktop shortcut)
set ROOT=C:\Users\YYDS\WorkBuddy\2026-10-30-20-40-09\
set BACKEND=%ROOT%friend-project\.staging\backend
set CONTRACTS=%ROOT%friend-project\outputs\work-budding-package\contracts
set FRONTEND=%ROOT%friend-project\frontend
set COMFY=%ROOT%tools\ComfyUI
set COMFYVENV=%ROOT%tools\comfy-venv\Scripts\python.exe
set MYSQLBIN=C:\Program Files\MySQL\MySQL Server 8.0\bin
set MYSQL="%MYSQLBIN%\mysql.exe"

rem Load local secrets (.env is gitignored; create it from .env.example first)
if not exist "%ROOT%friend-project\.env" (
  echo [ERROR] friend-project\.env not found. Copy friend-project\.env.example to .env first.
  pause
  exit /b 1
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%friend-project\.env") do set "%%A=%%B"

if not exist "%CONTRACTS%\hardhat.config.js" (
  echo [ERROR] contracts folder not found: %CONTRACTS%
  echo Do NOT move this .bat file. Run it from its original location.
  pause
  exit /b 1
)
if not exist "%CONTRACTS%\node_modules\hardhat" (
  echo [ERROR] contracts\node_modules missing. Run: cd /d %CONTRACTS% ^&^& npm install
  pause
  exit /b 1
)

echo [1/6] Starting MySQL on 3307...
start "mysql-3307" "%MYSQLBIN%\mysqld.exe" --basedir="C:\Program Files\MySQL\MySQL Server 8.0" --datadir="%ROOT%mysql-data2" --port=3307 --console
set /a n=0
:waitmysql
%MYSQL% -h127.0.0.1 -P3307 -uroot -p%DB_PASSWORD% -e "select 1" >nul 2>&1
if %errorlevel%==0 goto mysql_ok
set /a n+=1
if %n% geq 15 (echo [ERROR] MySQL failed to start & pause & exit /b 1)
ping -n 3 127.0.0.1 >nul
goto waitmysql
:mysql_ok
echo       MySQL is ready.

echo [2/6] Checking Hardhat chain on 8545 (reuse if alive, never reset)...
curl -s --noproxy * -m 3 -X POST -H "Content-Type: application/json" -d "{\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1,\"jsonrpc\":\"2.0\"}" http://127.0.0.1:8545 >nul 2>&1
if %errorlevel%==0 goto reuse_chain
echo       No live chain found. Starting a fresh Hardhat node...
start "hardhat-8545" cmd /k "cd /d %CONTRACTS% && node_modules\.bin\hardhat.cmd node"
set /a n=0
:waitchain
curl -s --noproxy * -m 3 -X POST -H "Content-Type: application/json" -d "{\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1,\"jsonrpc\":\"2.0\"}" http://127.0.0.1:8545 >nul 2>&1
if %errorlevel%==0 goto deploy_contracts
set /a n+=1
if %n% geq 20 (echo [ERROR] Hardhat node failed to start & pause & exit /b 1)
ping -n 3 127.0.0.1 >nul
goto waitchain

:reuse_chain
echo       [3/6] Existing chain detected - REUSING it. Anchors are preserved.
goto skip_deploy

:deploy_contracts
echo       Hardhat node is ready.
echo [3/6] Deploying contracts (deploy-anchor auto-patches backend yml)...
cd /d %CONTRACTS%
call node_modules\.bin\hardhat.cmd run scripts/deploy.js --network localhost
call node_modules\.bin\hardhat.cmd run scripts/deploy-anchor.js --network localhost
if %errorlevel% neq 0 (echo [ERROR] contract deploy failed & pause & exit /b 1)
:skip_deploy

rem --- Backend JAR sanity check (prevents the stale-JAR trap) ---
if not exist "%BACKEND%\target\backend-1.0.0.jar" (
  echo [ERROR] backend JAR not found: %BACKEND%\target\backend-1.0.0.jar
  echo          Build it first: cd /d %BACKEND% ^&^& mvn package -DskipTests
  pause
  exit /b 1
)
powershell -NoProfile -Command "$j='%BACKEND%\target\backend-1.0.0.jar'; $s=Get-ChildItem '%BACKEND%\src' -Recurse -Filter *.java | Sort-Object LastWriteTime | Select-Object -Last 1; if($s -and (Get-Item $j).LastWriteTime -lt $s.LastWriteTime){ Write-Output '[WARN] Backend source is newer than the JAR. Rebuild before demo: cd backend && mvn package -DskipTests' }"

echo [4/6] Starting backend on 8080...
start "backend-8080" cmd /k "cd /d %BACKEND% && java -jar target\backend-1.0.0.jar --spring.profiles.active=local --server.port=8080"
set /a n=0
:waitbackend
curl -s --noproxy * -m 3 -o nul http://127.0.0.1:8080/api/public/chain-status
if %errorlevel%==0 goto backend_ok
set /a n+=1
if %n% geq 25 (echo [ERROR] backend failed to start, check backend window log & pause & exit /b 1)
ping -n 3 127.0.0.1 >nul
goto waitbackend
:backend_ok
echo       Backend is ready.

echo [4.5/6] Reconciling anchors with the current chain (auto re-anchor)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%reanchor.ps1"

echo [5/6] Starting frontend on 5173...
start "frontend-5173" cmd /k "cd /d %FRONTEND% && npx vite --port 5173 --host 127.0.0.1"
ping -n 7 127.0.0.1 >nul

echo [6/6] Starting ComfyUI on 8188 (CPU mode, SD-Turbo)...
if not exist "%COMFYVENV%" (
  echo [ERROR] comfy-venv not found: %COMFYVENV%
  echo Run the ComfyUI setup first.
  pause
  exit /b 1
)
if not exist "%COMFY%\main.py" (
  echo [ERROR] ComfyUI main.py not found: %COMFY%
  pause
  exit /b 1
)
start "comfy-8188" cmd /k "cd /d %COMFY% && %COMFYVENV% main.py --cpu --listen 127.0.0.1 --port 8188 --enable-cors-header"
set /a n=0
:waitcomfy
curl -s --noproxy * -m 3 http://127.0.0.1:8188/system_stats >nul 2>&1
if %errorlevel%==0 goto comfy_ok
set /a n+=1
if %n% geq 40 (echo [ERROR] ComfyUI failed to start, check comfy-8188 window & pause & exit /b 1)
ping -n 3 127.0.0.1 >nul
goto waitcomfy
:comfy_ok
echo       ComfyUI is ready.

echo.
echo ============================================================
echo   ALL READY.  Open:  http://127.0.0.1:5173
echo   Demo login: demo / 123456   (collaborator: partner / 123456 in incognito)
echo   5 service windows will stay open - do NOT close them
echo   while demoing. Close them when done. Data is persisted.
echo   ComfyUI runs on CPU: first image ~15-30s, be patient.
echo ============================================================
pause
