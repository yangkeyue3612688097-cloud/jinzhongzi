@echo off
rem ============================================================
rem  Pre-demo health check: run this before recording.
rem  Shows whether the 5 services are alive and what to do.
rem ============================================================
set MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

echo ================ PRE-DEMO CHECK ================
echo.

echo [1] MySQL 3307
%MYSQL% -h127.0.0.1 -P3307 -uroot -p123456 -e "select 1" >nul 2>&1
if %errorlevel%==0 (echo     OK - database reachable) else (echo     FAIL - double click start-local.bat)

echo [2] Hardhat chain 8545
curl -s --noproxy * -m 3 -X POST -H "Content-Type: application/json" -d "{\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1,\"jsonrpc\":\"2.0\"}" http://127.0.0.1:8545 >nul 2>&1
if %errorlevel%==0 (echo     OK - chain running) else (echo     FAIL - double click start-local.bat)

echo [3] Backend 8080
curl -s --noproxy * -m 3 -o nul http://127.0.0.1:8080/api/public/chain-status
if %errorlevel%==0 (echo     OK - backend running) else (echo     FAIL - double click start-local.bat)

echo [4] Frontend 5173
curl -s --noproxy * -m 3 -o nul http://127.0.0.1:5173/
if %errorlevel%==0 (echo     OK - frontend running) else (echo     FAIL - double click start-local.bat)

echo [5] ComfyUI 8188 (本地 AI 生成，已随 start-local.bat 自动启动)
curl -s --noproxy * -m 2 -o nul http://127.0.0.1:8188/system_stats
if %errorlevel%==0 (echo     OK - ComfyUI running) else (echo     FAIL - double click start-local.bat)

echo.
echo ================================================
echo  If all above say OK: open http://127.0.0.1:5173
echo  Recording tips: browser zoom 125%%, F11, hide bookmarks
echo  Demo accounts: demo / 123456 (main) , partner / 123456 (collaborator)
echo ================================================
pause
