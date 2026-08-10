# 🛸 无人机仿真系统

> 百万级无人机数字孪生监管平台的仿真数据源，模拟多机型无人机发送 MQTT 遥测、心跳和事件数据。

---

## 快速开始

### 1. 仅仿真器（控制台输出）

```bash
cd uav-simulator
go run ./cmd/simulator/ -config config/default.yaml
```

不依赖任何外部服务，直接在控制台查看模拟数据。

### 2. Docker 一键部署（含 EMQX）

```bash
cd uav-simulator/docker
docker-compose -f docker-compose.sim.yml up -d
```

这会同时启动 EMQX Broker 和仿真器。可通过 `http://localhost:18083` 访问 EMQX Dashboard（admin/public）。

### 3. 对接已有 EMQX

修改 `config/default.yaml` 中的 `mqtt.broker` 指向你的 EMQX，然后启动。

---

## MQTT Topic 对照表

| 数据类型 | Topic | 频率 | QoS |
|---------|-------|------|-----|
| 遥测 | `uav/{device_sn}/telemetry` | 5Hz | 1 |
| 心跳 | `uav/{device_sn}/heartbeat` | 0.2Hz | 1 |
| 事件 | `uav/{device_sn}/event` | 按需 | 1 |

### 遥测数据 JSON 示例

```json
{
  "device_sn": "PATROL-0001",
  "model": "DJI-M350",
  "timestamp": 1737000000000,
  "position": {
    "lat": 39.9102,
    "lon": 116.4120,
    "alt_m": 85.3,
    "heading": 127.5
  },
  "speed_ms": 10.2,
  "battery_pct": 87.3,
  "signal_rssi": -45,
  "satellite_cnt": 18,
  "flight_phase": "FLYING",
  "flight_plan_id": "FP-20250115-a1b2c3d4",
  "violation": "NONE",
  "wind_speed_ms": 3.2,
  "temperature_c": 22.5
}
```

---

## 配置说明

```yaml
mqtt:
  broker: "tcp://127.0.0.1:1883"   # EMQX 地址

simulation:
  tick_interval_ms: 200              # 仿真时钟（越小越快）
  telemetry_rate_hz: 5.0             # 单机遥测频率
  enable_violations: true            # 自动模拟违规
  violation_prob: 0.02               # 违规概率

scenarios:
  - name: "城市巡检"
    enabled: true
    drone_count: 50                   # 此场景无人机数
    center_lat: 39.9042              # 北京天安门
    center_lon: 116.4074
    radius_km: 10.0
    drone_models: ["DJI-M350", "DJI-M300"]
```

**压力测试**：将 `drone_count` 调到 10000+、`telemetry_rate_hz` 设为 1Hz，可测试百万级数据管道。

---

## 项目结构

```
uav-simulator/
├── cmd/simulator/main.go          # 入口，生命周期管理
├── internal/
│   ├── drone/
│   │   ├── drone.go               # 无人机实例 + 状态机
│   │   ├── flight_path.go         # 飞行路径生成
│   │   └── simulator.go           # 仿真引擎 Tick 循环
│   ├── mqtt/client.go             # MQTT 发布器（批量异步）
│   └── config/config.go           # YAML 配置加载
├── config/default.yaml            # 默认配置
├── docker/
│   ├── Dockerfile                 # 多阶段构建
│   └── docker-compose.sim.yml     # 集成 EMQX
└── go.mod
```

---

## 机型库

| 机型 | 重量 | 最大速度 | 最大高度 | 续航 |
|------|------|---------|---------|------|
| DJI-M350 (Matrice 350) | 9.2kg | 23 m/s | 7000m | 55min |
| DJI-M300 (Matrice 300) | 6.3kg | 23 m/s | 7000m | 55min |
| DJI-M30T | 3.8kg | 23 m/s | 7000m | 41min |
| DJI-MAVIC3 | 895g | 21 m/s | 6000m | 46min |
| DJI-FLYCART30 | 25kg | 20 m/s | 6000m | 28min |

---

## 违规模拟

仿真器内置随机违规生成，可模拟：

| 违规类型 | `violation` 字段 | 表现 |
|---------|-----------------|------|
| 高度超限 | `ALTITUDE_EXCEED` | 瞬间爬升 +50m |
| 速度超限 | `SPEED_EXCEED` | 速度提升至 30m/s |
| 围栏突破 | `GEO_FENCE_BREACH` | 随机偏移出围栏 |
| 无计划飞行 | `NO_PLAN_FLIGHT` | 违规标记 |

关闭：`simulation.enable_violations: false`

---

## 生命周期状态机

```
IDLE → TAKEOFF → FLYING → RETURNING → LANDING → LANDED → (充电) → IDLE
                                           ↑
                                      低电量 / 强制RTL / 空域清场
```
