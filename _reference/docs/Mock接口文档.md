# 🚁 无人机空域管理系统 — Mock 接口文档

> **版本**: v3.0 (V6 告警解耦)
> **更新日期**: 2025-07-19
> **说明**: 当前所有数据均通过 Vite 开发服务器内置 Mock 中间件生成，无需后端服务即可独立运行。

---

## 接口总览

| # | 接口 | 说明 | 请求方式 | 调用时机 |
|:-:|------|------|:--------:|----------|
| 1 | `GET /api/drone/meta` | 无人机元数据（300架） | HTTP | 页面初始化时必调 |
| 2 | `GET /api/drone/packet/:idx` | ★ 纯轨迹帧 + **独立告警** + 时效数据 + 飞行计划（60包） | HTTP | 按播放进度加载 |
| 3 | `GET /api/airspace/data` | ★ 永久空域数据（永久分区+永久航路+围栏+热力+网格） | HTTP | 页面初始化时必调 |

---

## ★ V6 核心参数

| 参数 | 值 |
|------|:--:|
| 无人机数量 | **300 架** |
| 单包时长 | **60 秒（1 分钟）** |
| 总包数 | **60 包** |
| 总时长 | 3600 秒（1 小时） |
| 单包帧数 | 31 帧（2s 间隔） |
| 单包记录数 | 300 × 31 = **9,300 条（纯轨迹帧）** |
| 单包告警数 | ~130 条（独立实体） |
| 总飞行计划数 | ~500 个 |

---

## 1️⃣ `GET /api/drone/meta`

### 响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 300,
    "drones": [
      {
        "id": "UAV-0001",
        "centerLng": 116.397,
        "centerLat": 39.908,
        "radius": 0.01,
        "phase": 0.618,
        "heightBase": 50,
        "heightAmp": 30,
        "period": 60,
        "pattern": 0
      }
    ]
  }
}
```

> **注意**：元数据不包含 `mission` 字段。任务信息由独立的 `flight_plans` 提供。

---

## 2️⃣ `GET /api/drone/packet/:idx` ★ 核心接口

### ★ V5→V6 关键变更

```
V5:  data_list = [{ frame: {...}, alarm: null|{...} }, ...]     ← 告警嵌在帧里
V6:  data_list = [{ device_sn: "...", longitude: ..., ... }, ...]  ← 纯轨迹帧
     alarms    = [{ alarmId, droneId, alarmType, startTime, endTime, ... }]  ← ★ 独立告警
```

### 参数说明

| 参数 | 类型 | 范围 | 说明 |
|------|------|:----:|------|
| `idx` | number | `0`~`59` | 1分钟数据包索引，共 60 包覆盖 1 小时 |

### 包-时间对应表

| 包索引 | 时间范围 | 轨迹帧 | 告警数 | 飞行计划 |
|:------:|:---------:|:-----:|:-----:|:------:|
| 0 | 0~60s | 9,300 | ~120 | ~85 |
| 1 | 60~120s | 9,300 | ~125 | ~80 |
| ... | ... | ... | ... | ... |
| 59 | 3540~3600s | 9,300 | ~115 | ~72 |

### 响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "packetIndex": 0,
    "startTime": 0,
    "endTime": 60,

    "data_list": [
      {
        "device_sn": "UAV-0001",
        "envelope": { "a": 3.5123, "R": 4.2, "b": 3.8123, "c": 3.0 },
        "height": 120.5,
        "latitude": 39.908123,
        "longitude": 116.397456,
        "legality": 1,
        "loss_sn": 0,
        "source": "FUSION",
        "speed": 12.34567,
        "speedOfPitch": -0.000123,
        "speedOfYaw": 0.000456,
        "timems": 1779350400000,
        "traceId": "TRK00000000000000001",
        "traceType": 0,
        "uniqueid": "TRAJ...",
        "user": ""
      }
    ],

    "alarms": [
      {
        "alarmId": "ALARM-00042",
        "droneId": "UAV-0042",
        "alarmType": "禁飞区告警",
        "alarmLevel": "严重告警",
        "alarmTypeField": "boundary",
        "startTime": 120,
        "endTime": 600,
        "beginTime": "2026-05-21 08:02:00",
        "reason": "无人机UAV-0042: 禁飞区告警",
        "nowLatitude": 39.908123,
        "nowLongitude": 116.397456,
        "nowHeight": 120.5
      }
    ],

    "time_airspaces": [ ... ],
    "time_routes": [ ... ],
    "weather": { ... },
    "flight_plans": [ ... ]
  }
}
```

### 字段说明

#### data_list[] — ★ V6: 纯轨迹帧（不再包含 alarm 包装）

| 字段 | 类型 | 说明 |
|------|------|------|
| `device_sn` | string | 无人机编号 `UAV-0001` ~ `UAV-0300` |
| `longitude` | number | 经度（度） |
| `latitude` | number | 纬度（度） |
| `height` | number | 海拔高度（m） |
| `speed` | number | 速度（m/s） |
| `legality` | number | 1=合法, 2=未知, 3=不合法 |
| `timems` | number | Unix 毫秒时间戳 |
| `envelope` | object | 预测包络 {a, R, b, c} |

#### alarms[] — ★ V6: 独立告警实体（时段事件）

| 字段 | 类型 | 说明 |
|------|------|------|
| `alarmId` | string | 告警唯一标识 `ALARM-00001` ~ `ALARM-03000` |
| `droneId` | string | 产生告警的无人机编号 |
| `alarmType` | string | 告警类型（禁飞区告警/偏航告警/碰撞告警 等12种） |
| `alarmLevel` | string | `严重告警` \| `一般告警` |
| `alarmTypeField` | string | 告警分类字段: `boundary`/`conflict`/`deviation`/`signal_loss`/`fault`/`illegal`/… |
| `startTime` | number | 告警起始秒数 |
| `endTime` | number | 告警结束秒数 |
| `beginTime` | string | 格式化起始时间 |
| `reason` | string | 告警原因描述 |
| `nowLatitude` | number | 告警产生时的纬度 |
| `nowLongitude` | number | 告警产生时的经度 |
| `nowHeight` | number | 告警产生时的高度 |

**★ 告警分配策略：**

| 分组依据 | 告警数/架 | 单条持续时间 |
|----------|:--------:|:----------:|
| `(index*13+7) % 4` | 0~3 条 | 60~360s |

使用确定性种子分配，同一无人机在任何窗口中结果一致。**同一无人机可同时存在多条告警**。

#### time_airspaces[] / time_routes[] / weather / flight_plans[]

（与 V5 一致）

---

## 3️⃣ `GET /api/airspace/data` ★ 永久数据

### 响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "centerLng": 116.397,
    "centerLat": 39.908,
    "permanent_zones": [...],
    "permanent_routes": [...],
    "fencePts": [-600,600,...],
    "heatmapPoints": [...],
    "gridConfig": { "extent":2000, "gridSize":250, "gridHeight":120 },
    "initial_weather": { ... }
  }
}
```

### 对比 V5 的变化

| V5 字段 | V6 字段 | 说明 |
|---------|---------|------|
| `data_list[]` = `{frame, alarm}` | `data_list[]` = 纯帧 | ★ 告警从帧中解耦 |
| (无) | `alarms[]` | ★ 新增独立告警实体 |
| 其他字段 | 其他字段 | 无变化 |

---

## 4️⃣ ★ V6 时间跳转告警 diff

```
用户拖拽时间轴从 300s → 1800s

跳转前活跃告警: {ALARM-00005, ALARM-00012, ALARM-00033, ...}
跳转后活跃告警: {ALARM-00012, ALARM-00104, ALARM-00201, ...}
                        ↓
        新增: ALARM-00104, ALARM-00201 ✨
        结束: ALARM-00005, ALARM-00033 🏁
        持续: ALARM-00012
```

前端通过 `droneSim.diffAlarmsAfterJump(prevAlarmIds, newTimeSec)` 实现。

---

## 5️⃣ 数据流与调用时序

```
页面加载
  │
  ├──→ GET /api/drone/meta       → 无人机元数据（必调，初始化）
  ├──→ GET /api/airspace/data    → 永久空域数据（必调，初始化）
  └──→ GET /api/drone/packet/0   → 纯轨迹帧 + 独立告警 + 时效数据 + 飞行计划
        │
        ▼ 自动播放 → 跨包边界检测
        GET /api/drone/packet/1    ← 后台预加载/边界触发
        GET /api/drone/packet/2    ← 继续扩散加载
          │
          ▼ 用户拖拽时间轴跳转
          取消预加载 → 优先加载目标包 → 告警 diff → 恢复扩散
```

### 预加载策略

| 阶段 | 行为 |
|------|------|
| **初始加载** | 加载 `packet/0`，完成后自动预加载 `packet/1, 2, 3...` |
| **自动播放** | 每帧检测 `checkPacketBoundary()`，进入新包时加载 |
| **时间轴跳转** | 取消预加载 → 加载目标包 → **告警 diff** → 恢复扩散 |
| **时效性更新** | 包切换时自动更新 `alarms`、`time_airspaces`、`time_routes`、`weather`、`flight_plans` |

---

## 6️⃣ 开发说明

| 项目 | 说明 |
|------|------|
| **Mock 中间件位置** | `vite.config.js` → `configureServer` → 请求拦截 |
| **数据生成逻辑** | `src/utils/mockData.js` |
| **单包大小** | 约 2.8MB（300架 × 31帧 × ~300字节/条） |
| **总包数** | 60 包（1小时 / 1分钟） |
| **服务端缓存** | 包数据首次请求时生成并缓存到内存 Map，后续请求 <20ms |
| **飞行计划总数** | ~500 个（确定性分配） |
| **告警总数** | ~7,500 条（分布在 60 包中，每包 ~130 条） |
