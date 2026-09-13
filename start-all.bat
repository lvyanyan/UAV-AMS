@echo off
setlocal enabledelayedexpansion
title UAV-AMS One-Click Start (silent)

:: System32 first so builtin commands (timeout etc.) never get shadowed
:: by Git-Bash / MSYS tools when run from a shell with a hijacked PATH
set "PATH=%SystemRoot%\System32;%PATH%"
:: ping-based wait: works with any stdin (timeout.exe needs a real console)
set "SLEEP=%SystemRoot%\System32\ping.exe"

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
set "JAVA=%JAVA_HOME%\bin\java"
set "GO_BIN=C:\Program Files\Go\bin"
set "ROOT=d:\dronesManager"
set "LOGS=%ROOT%\logs"
set "WSL_DISTRO=Ubuntu"
:: App listen ports (gateway 18080, NOT 8080 - that belongs to wslrelay)
set "KILLPORTS=:18080 :8081 :8084 :8085 :8086 :8087 :8088 :8089 :8090 :8095 :8096 :5173 "

echo ============================================
echo   UAV-AMS One-Click Start (silent mode)
echo ============================================
echo.

:: ===== Step 0: Kill old processes =====
echo [0/5] Cleaning up old processes (LISTENING on app ports only)...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr "LISTENING" ^| findstr /R "%KILLPORTS%"') do taskkill /F /PID %%a 2>nul
echo   Done.
echo.

:: ===== Step 1: Check =====
echo [1/5] Checking environment...
"%JAVA%" -version >nul 2>&1 || (echo ERROR: Java not found & pause & exit /b 1)
call mvn --version >nul 2>&1 || (echo ERROR: Maven not found & pause & exit /b 1)
"%GO_BIN%\go" version >nul 2>&1 || (echo ERROR: Go not found & pause & exit /b 1)

:: --- Docker inside WSL ---
echo   Checking Docker (WSL %WSL_DISTRO%)...
wsl -d %WSL_DISTRO% -- docker ps >nul 2>&1
if !errorlevel! neq 0 (
    echo   WSL Docker not running. Starting dockerd inside %WSL_DISTRO%...
    wsl -d %WSL_DISTRO% -u root -- bash -c "service docker start >/dev/null 2>&1 || systemctl start docker >/dev/null 2>&1"
    echo   Waiting for Docker ^(up to 90 seconds^)...
    for /L %%i in (1,1,30) do (
        "%SLEEP%" -n 4 127.0.0.1 >nul
        wsl -d %WSL_DISTRO% -- docker ps >nul 2>&1
        if !errorlevel! equ 0 goto :docker_ok
        echo   ... waiting ^(%%i/30^)
    )
    echo   ERROR: Docker in WSL %WSL_DISTRO% is not available.
    echo   Start it manually: wsl -d %WSL_DISTRO% then: sudo service docker start
    pause & exit /b 1
)
:docker_ok
echo   Java OK, Maven OK, Go OK, WSL Docker OK
echo.

:: ===== Step 2: Docker services =====
echo [2/5] Starting Docker infra via compose (inside WSL) + waiting for PostgreSQL...
wsl -d %WSL_DISTRO% -- bash -c "cd /mnt/d/dronesManager/docker && docker compose -f docker-compose.dev.yml up -d" >nul 2>&1

echo   Waiting for PostgreSQL...
for /L %%i in (1,1,60) do (
    wsl -d %WSL_DISTRO% -- docker exec uav-postgres pg_isready -U uav -d uav_ams >nul 2>&1
    if !errorlevel! equ 0 goto :pg_ok
    "%SLEEP%" -n 2 127.0.0.1 >nul
)
echo   ERROR: PostgreSQL did not become ready.
pause & exit /b 1
:pg_ok
echo   PostgreSQL ready.

echo   Creating Kafka topics...
for %%t in (uav.telemetry uav.heartbeat uav.event uav.alarm uav.conflict uav.resolution uav.signboard uav.track.fitted) do (
    wsl -d %WSL_DISTRO% -- docker exec uav-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic %%t --partitions 3 --replication-factor 1 --if-not-exists >nul 2>&1
)
echo   Docker OK
echo.

:: ===== Step 3: Compile =====
echo [3/5] Compiling all modules...
cd /d "%ROOT%"
call mvn clean install -DskipTests -q -T 1C 2>&1
if !errorlevel! neq 0 (
    echo   Java compile failed, retrying...
    call mvn clean install -DskipTests
    if !errorlevel! neq 0 (echo ERROR: Java compile failed & pause & exit /b 1)
)
echo   Java OK

set "PATH=%GO_BIN%;%PATH%"
set "GOPROXY=https://goproxy.cn,direct"

cd /d "%ROOT%\uav-realtime"
go build -o uav-realtime.exe ./cmd/server/ 2>&1
if !errorlevel! neq 0 (echo ERROR: Go realtime build failed & pause & exit /b 1)

cd /d "%ROOT%\uav-simulator"
go build -o uav-simulator.exe ./cmd/simulator/ 2>&1
if !errorlevel! neq 0 (echo ERROR: Go simulator build failed & pause & exit /b 1)

cd /d "%ROOT%"
echo   Go OK
echo   Compile done.
echo.

:: ===== Step 4: Start Backend (silent, logs to files) =====
echo [4/5] Starting 12 backend services in background...
if not exist "%LOGS%" mkdir "%LOGS%"

start "" /B "%JAVA%" -jar uav-gateway\target\uav-gateway-1.0.0-SNAPSHOT.jar             > "%LOGS%\gateway.log" 2>&1
start "" /B "%JAVA%" -jar uav-system\target\uav-system-1.0.0-SNAPSHOT.jar               > "%LOGS%\system.log" 2>&1
start "" /B "%JAVA%" -jar uav-alarm-engine\target\uav-alarm-engine-1.0.0-SNAPSHOT.jar   > "%LOGS%\alarm.log" 2>&1
start "" /B "%JAVA%" -jar uav-risk-assessment\target\uav-risk-assessment-1.0.0-SNAPSHOT.jar > "%LOGS%\risk.log" 2>&1
start "" /B "%JAVA%" -jar uav-airspace-controller\target\uav-airspace-controller-1.0.0-SNAPSHOT.jar > "%LOGS%\airspace-ctrl.log" 2>&1
start "" /B "%JAVA%" -jar uav-airspace\target\uav-airspace-1.0.0-SNAPSHOT.jar           > "%LOGS%\airspace.log" 2>&1
start "" /B "%JAVA%" -jar uav-registry\target\uav-registry-1.0.0-SNAPSHOT.jar           > "%LOGS%\registry.log" 2>&1
start "" /B "%JAVA%" -jar uav-pilot\target\uav-pilot-1.0.0-SNAPSHOT.jar                 > "%LOGS%\pilot.log" 2>&1
start "" /B "%JAVA%" -jar uav-flight-plan\target\uav-flight-plan-1.0.0-SNAPSHOT.jar     > "%LOGS%\flightplan.log" 2>&1
start "" /B "%JAVA%" -jar uav-track-fusion\target\uav-track-fusion-1.0.0-SNAPSHOT.jar   > "%LOGS%\trackfusion.log" 2>&1

start "" /B /D "%ROOT%\uav-realtime" "%ROOT%\uav-realtime\uav-realtime.exe"             > "%LOGS%\realtime.log" 2>&1
start "" /B /D "%ROOT%\uav-simulator" "%ROOT%\uav-simulator\uav-simulator.exe"           > "%LOGS%\simulator.log" 2>&1

echo   10 Java + 2 Go started in background
echo.

:: ===== Step 5: Frontend =====
echo [5/5] Starting frontend...
start "" /B cmd /c "npm run dev --prefix %ROOT%\uav-frontend > %LOGS%\frontend.log 2>&1"

echo.
echo ============================================
echo   ALL SERVICES LAUNCHED (silent mode)
echo   Wait 30-60s for Spring Boot to boot
echo   Frontend:  http://localhost:5173
echo   Gateway:   http://localhost:18080
echo   Login:     admin / admin123
echo   Infra:     WSL %WSL_DISTRO% docker (uav-postgres/redis/emqx/kafka)
echo   Logs:      %LOGS%\
echo   To stop:   stop-all.bat
echo ============================================
echo.
echo This window will close in 10 seconds...
"%SLEEP%" -n 11 127.0.0.1 >nul
