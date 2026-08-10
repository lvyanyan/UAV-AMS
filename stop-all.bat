@echo off
title UAV-AMS Stop
echo ============================================
echo   UAV-AMS Stop (ports only, Docker stays)
echo ============================================
echo.

echo Stopping services on ports 8080-8090 + 5173...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr /R ":808[0-9] "') do taskkill /F /PID %%a 2>nul
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr /R ":8090 "') do taskkill /F /PID %%a 2>nul
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr /R ":5173 "') do taskkill /F /PID %%a 2>nul

echo Stopping Go compiled executables...
taskkill /F /IM uav-realtime.exe 2>nul
taskkill /F /IM uav-simulator.exe 2>nul
taskkill /F /IM uav-frontend.exe 2>nul

echo.
echo   Done. Docker untouched.
timeout /t 2 /nobreak >nul
