@echo off
chcp 65001 >nul
echo ================================================================
echo   UAV-AMS 开发环境一键启动
echo ================================================================
echo.
echo [1/4] 启动 Docker 基础设施 (PostgreSQL+Redis+EMQX+Kafka)...
cd /d %~dp0..\docker
docker-compose -f docker-compose.dev.yml up -d
echo   等待 PostgreSQL 就绪...
timeout /t 8 /nobreak >nul
echo.
echo [2/4] 编译 Java 模块...
cd /d %~dp0..
mvn clean compile -DskipTests -q
echo   编译完成.
echo.
echo [3/4] 启动 Java 微服务 (后台进程)...
start "uav-system" cmd /c "cd /d %~dp0.. && mvn -pl uav-system spring-boot:run"
timeout /t 5 /nobreak >nul
start "uav-alarm-engine" cmd /c "cd /d %~dp0.. && mvn -pl uav-alarm-engine spring-boot:run"
timeout /t 3 /nobreak >nul
start "uav-airspace-controller" cmd /c "cd /d %~dp0.. && mvn -pl uav-airspace-controller spring-boot:run"
timeout /t 3 /nobreak >nul
start "uav-airspace" cmd /c "cd /d %~dp0.. && mvn -pl uav-airspace spring-boot:run"
echo   Java 服务启动中...
echo.
echo [4/4] 启动 Go 实时推送服务 + 仿真器...
start "uav-realtime" cmd /c "cd /d %~dp0..\uav-realtime && go run ./cmd/server/"
timeout /t 2 /nobreak >nul
start "uav-simulator" cmd /c "cd /d %~dp0..\uav-simulator && go run ./cmd/simulator/"
echo.
echo ================================================================
echo   启动完成！访问地址:
echo   前端 : http://localhost:5173
echo   登录 : admin / admin123
echo   EMQX : http://localhost:18083 (admin/admin123)
echo   ================================================================
pause
