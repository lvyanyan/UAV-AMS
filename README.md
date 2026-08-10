# UAV-AMS 无人机数字孪生监管平台

## Port Map

| Service | Port | Language | Status |
|---------|:----:|:--------:|:------:|
| uav-gateway (API Gateway) | 8080 | Java | Done |
| uav-system (RBAC+JWT+MFA) | 8081 | Java | Done |
| uav-alarm-engine | 8082 | Java | Done |
| uav-risk-assessment | 8083 | Java | Done |
| uav-airspace-controller | 8084 | Java | Done |
| uav-airspace | 8085 | Java | Done |
| uav-registry | 8086 | Java | Done |
| uav-pilot | 8087 | Java | Done |
| uav-flight-plan | 8088 | Java | Done |
| uav-track-fusion | 8089 | Java | Done |
| uav-realtime (MQTT bridge) | 8090 | Go | Done |
| uav-simulator | - | Go | Done |
| uav-frontend | 5173 | Vue3 | Done |
| uav-miniapp | - | WeChat | Done |

## Infrastructure (Docker)

| Container | Port |
|-----------|:----:|
| PostgreSQL 16 + PostGIS | 5432 |
| Redis 7 | 6379 |
| EMQX 5 | 1883 |
| Kafka KRaft | 9092 |

## Quick Start

```bash
# Prerequisites: Docker Desktop, JDK 17, Go 1.21+, Node 18+, Maven 3.8+

# Double-click: start-all.bat
# OR manual:
docker-compose -f docker\docker-compose.dev.yml up -d
call mvn clean install -DskipTests
```

## Login

- URL: http://localhost:5173
- Account: admin / admin123
