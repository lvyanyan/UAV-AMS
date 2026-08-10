@echo off
setlocal enabledelayedexpansion
title UAV-AMS One-Click Start (silent)

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
set "JAVA=%JAVA_HOME%\bin\java"
set "GO_BIN=C:\Program Files\Go\bin"
set "ROOT=d:\dronesManager"
set "LOGS=%ROOT%\logs"

echo ============================================
echo   UAV-AMS One-Click Start (silent mode)
echo ============================================
echo.

:: ===== Step 0: Kill old processes =====
echo [0/5] Cleaning up old processes...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R ":808[0-9] :8090 " 2^>nul') do taskkill /F /PID %%a 2>nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173 " 2^>nul') do taskkill /F /PID %%a 2>nul
echo   Done.
echo.

:: ===== Step 1: Check =====
echo [1/5] Checking environment...
"%JAVA%" -version >nul 2>&1 || (echo ERROR: Java not found & pause & exit /b 1)
call mvn --version >nul 2>&1 || (echo ERROR: Maven not found & pause & exit /b 1)
"%GO_BIN%\go" version >nul 2>&1 || (echo ERROR: Go not found & pause & exit /b 1)

:: --- Docker check ---
echo   Checking Docker...
docker ps >nul 2>&1
if !errorlevel! neq 0 (
    echo   Docker not running. Attempting to start Docker Desktop...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe" 2>nul
    if !errorlevel! neq 0 (
        start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" 2>nul
    )
    echo   Waiting for Docker to start ^(up to 90 seconds^)...
    for /L %%i in (1,1,30) do (
        timeout /t 3 /nobreak >nul
        docker ps >nul 2>&1
        if !errorlevel! equ 0 goto :docker_ok
        echo   ... waiting ^(%%i/30^)
    )
    echo   ERROR: Docker failed to start. Is Docker Desktop installed?
    pause & exit /b 1
)
:docker_ok
echo   Java OK, Maven OK, Go OK, Docker OK
echo.

:: ===== Step 2: Docker services =====
echo [2/5] Starting Docker services + waiting for PostgreSQL...
cd /d "%ROOT%"
docker-compose -f docker\docker-compose.dev.yml up -d 2>&1 | findstr /v "Container"

echo   Waiting for PostgreSQL...
for /L %%i in (1,1,60) do (
    docker exec uav-postgres pg_isready -U uav -d uav_ams >nul 2>&1
    if !errorlevel! equ 0 goto :pg_ok
    timeout /t 1 /nobreak >nul
)
echo   ERROR: PostgreSQL did not become ready.
pause & exit /b 1
:pg_ok
echo   PostgreSQL ready.

echo   Creating Kafka topics...
for %%t in (uav.telemetry uav.heartbeat uav.event uav.alarm uav.conflict uav.resolution uav.signboard uav.track.fitted) do (
    docker exec uav-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic %%t --partitions 3 --replication-factor 1 --if-not-exists 2>&1 | findstr /v "Created"
)
echo   Docker OK
echo.

:: ===== Step 3: Compile =====
echo [3/5] Compiling all modules...
cd /d "%ROOT%"
call mvn clean install -DskipTests -q 2>&1
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
start "" /B npm run dev --prefix "%ROOT%\uav-frontend"                                   > "%LOGS%\frontend.log" 2>&1

echo.
echo ============================================
echo   ALL SERVICES LAUNCHED (silent mode)
echo   Wait 30-60s for Spring Boot to boot
echo   Frontend:  http://localhost:5173
echo   Login:     admin / admin123
echo   Logs:      %LOGS%\
echo   To stop:   double-click stop-all.bat
echo ============================================
echo.
echo This window will close in 10 seconds...
timeout /t 10 /nobreak >nul
