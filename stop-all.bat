@echo off
setlocal enabledelayedexpansion
title UAV-AMS Stop

:: System32 first so builtin commands (timeout etc.) never get shadowed
:: by Git-Bash / MSYS tools when run from a shell with a hijacked PATH
set "PATH=%SystemRoot%\System32;%PATH%"
:: ping-based wait: works with any stdin (timeout.exe needs a real console)
set "SLEEP=%SystemRoot%\System32\ping.exe"

echo ============================================
echo   UAV-AMS Stop (ports only, Docker stays)
echo ============================================
echo.

echo Stopping backend/frontend listeners (gateway 18080, services 8081/8084-8089/8090/8095/8096, frontend 5173)...
:: App ports only; never touch 8080/8082 (wslrelay) or anything else
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr "LISTENING" ^| findstr /R ":18080 :8081 :8084 :8085 :8086 :8087 :8088 :8089 :8090 :8095 :8096 :5173 "') do taskkill /F /PID %%a 2>nul

echo Stopping Go compiled executables...
taskkill /F /IM uav-realtime.exe 2>nul
taskkill /F /IM uav-simulator.exe 2>nul

echo.
echo   Done. WSL Docker containers untouched
echo   (stop them manually: wsl -d Ubuntu -- docker stop uav-postgres uav-redis uav-emqx uav-kafka)
"%SLEEP%" -n 3 127.0.0.1 >nul
