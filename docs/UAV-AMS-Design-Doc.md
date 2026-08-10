# 百万级无人机数字孪生监管平台 设计文档

**文档版本**: V3.5  
**更新日期**: 2025-01

---

## 一、项目概述

### 1.1 项目定位

百万级无人机数字孪生监管平台，面向政府监管场景。支持多源位置数据融合、六维告警、冲突检测与解脱、飞行计划多级审批（含军民协调）、图传视频接入、微信小程序移动端。

### 1.2 核心功能模块

| 序号 | 功能模块 | 说明 |
|------|---------|------|
| 1 | 实名登记 | 无人机所有人 + 无人机双重登记 |
| 2 | 驾驶员资质 | 执照管理 + 体检报告上传 |
| 3 | 飞行计划审批 | 多级审批 + 军民协调一键批准 |
| 4 | 数字孪生渲染 | Cesium + 高德卫星底图 + 无人机标牌 |
| 5 | 空域管理 | H3 网格 + 航路 + GeoJSON API |
| 6 | 六维告警引擎 | 空域/冲突/气象/航路/设备/地形 |
| 7 | 轨迹融合 | 多源位置加权融合 + RTS平滑 + 告警标注 |
| 8 | 冲突检测与解脱 | 卡尔曼预测 + 包络相交检测 + 解脱指令 |
| 9 | 违规处置 | 可配置罚则 + 证据链 |
| 10 | 图传视频 | SRS 媒体服务 + 告警触发录像 |
| 11 | 军民协调 | 独立双因子登录 + 一键批准 + 空域清场 |
| 12 | 微信小程序 | 驾驶员侧移动端 |
| 13 | 双语界面 | 中文/English |

### 1.3 核心能力矩阵

| 能力域 | 能力项 | 实现方式 |
|--------|--------|---------|
| 实名管理 | 无人机所有人登记、实名登记、标识管理 | 独立登记系统 |
| 资质管理 | 驾驶员执照、合格证、培训记录、体检上传 | 本地资质库 + 自动校验 |
| 飞行计划 | 计划填报、合规校验、多级审批（含军民协调） | Flowable 工作流 |
| 数字孪生 | 高德卫星底图 + Cesium 三维 + 标牌 + H3 空域 | H3 聚合 + LOD + GPU Instance |
| 军民协调 | 一键批准、空域清场、优先级覆盖、独立双因子登录 | 特殊角色 + TOTP + 权限绕过 |
| 轨迹融合 | 多源加权融合、RTS卡尔曼平滑、贝塞尔拟合、告警耦合 | EJML + 加权平均 |
| 空域管理 | 网格划分、航路定义、容量评估 | H3 + PostGIS |
| 气象融合 | 免费多源气象、飞行影响评估 | Open-Meteo + CMA |
| 告警引擎 | 6 维融合告警 + CEP | Flink CEP |
| 违规处置 | 取证、可配置规则处置 | 规则引擎 + 证据链 |
| 无人机标牌 | Cesium 动态标牌，告警+视频实时显示 | Canvas Billboard |
| 图传视频 | 实时视频流接入，告警触发录像按等级保留 | SRS → WebRTC/HLS + 环形缓冲 |
| 权限体系 | RBAC 7 类角色（含军民协调独立登录） | Spring Security + MFA |
| 移动端 | 微信小程序（政府主体认证） | 微信小程序原生 |
| 双语界面 | 中文/English | vue-i18n |
| 外部对接 | 公安/应急/交通对接预留 | API Gateway + Webhook |

---

## 二、技术选型

### 2.1 选型总览

```
前端: Vue 3.4+ · TypeScript · CesiumJS 1.120+ · Pinia · Vite 5
      Element Plus · ECharts 5 · Turf.js · vue-i18n
      高德免费卫星瓦片 (style=6 底图 + style=8 标注) 无需 API Key

后端: Spring Boot 3.2 + MyBatis-Plus + Spring Security + JJWT
      Flowable (工作流) · EJML (矩阵运算)

实时: Go + WebSocket + MQTT (EMQX)

流计算: Apache Flink (告警/冲突检测)

存储: PostgreSQL 16 + PostGIS + TimescaleDB + Redis 7 + Kafka

媒体: SRS 5.0 (图传 + DVR 环形缓冲)
```

### 2.2 新增依赖说明

| 依赖 | 用途 | 许可证 |
|------|------|--------|
| EJML 0.43.1 | 矩阵运算（卡尔曼滤波） | Apache-2.0 |
| EMQX 5 | MQTT Broker | Apache-2.0 |
| SRS 5.0 | 媒体服务器 | MIT |

---

## 三、系统架构

### 3.1 数据流（完整链路）

```
                        ┌─── 多源位置输入 ───┐
无人机遥测 ─→ MQTT ─→  │                    │
ADS-B      ──→ Kafka ─→│ uav-track-fusion   │──→ 拟合轨迹 → Kafka → 前端渲染
雷达       ──→ Kafka ─→│ :8086              │       │
RTK        ──→ Kafka ─→│ · 多源加权融合      │       ├→ 告警标注
                        │ · RTS卡尔曼平滑     │       ├→ 飞行计划偏离
                        │ · 贝塞尔曲线拟合    │       └→ 前端标牌着色
                        └────────────────────┘
                              │
uav-simulator ─→ MQTT(EMQX) ─→ uav-realtime ─→ Kafka
                                                ├→ uav-alarm-engine ─→ Kafka(alarm) ─→ 前端
                                                │       ↑
                                                └→ uav-airspace-controller ─→ Kafka(conflict+resolution) ─→ 前端标牌 + 运服席位
```

### 3.2 模块端口规划

| 模块 | 端口 | 语言 | 说明 |
|------|:--:|------|------|
| uav-realtime | 8080 | Go | MQTT→Kafka→WebSocket |
| uav-system | 8081 | Java | RBAC + JWT + MFA |
| uav-alarm-engine | 8082 | Java | 六维告警 |
| uav-risk-assessment | 8083 | Java | 风险评估 |
| uav-airspace-controller | 8084 | Java | 冲突检测+解脱 |
| uav-airspace | 8085 | Java | 空域管理 |
| uav-track-fusion | 8086 | Java | 轨迹融合 |
| uav-frontend | 5173 | Vue3 | 前端 |

---

## 四、数据库设计

### 4.1 核心表

与 V3.3 一致：sys_user（含 MFA 字段）、sys_role、sys_organization、uav_owner、uav_registration、uav_pilot、uav_pilot_license、uav_pilot_medical、flight_plan、flight_plan_approval、approval_chain_config、sys_military_clearance、sys_mfa_log、violation_penalty_rule、video_stream_record、external_integration_log 等。

### 4.2 时序轨迹表 (TimescaleDB)

```sql
CREATE TABLE uav_trajectory (
    time        TIMESTAMPTZ NOT NULL,
    drone_sn    VARCHAR(64) NOT NULL,
    lat         DOUBLE PRECISION,
    lon         DOUBLE PRECISION,
    alt         DOUBLE PRECISION,
    heading     DOUBLE PRECISION,
    speed       DOUBLE PRECISION,
    source      VARCHAR(32),
    h3_index    VARCHAR(32),
    is_fitted   BOOLEAN DEFAULT FALSE
);
SELECT create_hypertable('uav_trajectory', 'time');
```

---

## 五、轨迹融合处理服务（V3.5 核心新增）

### 5.1 设计动机

原有架构透传位置数据。真实监管系统需要中间的轨迹处理层：平滑去噪、多源融合、告警标注、飞行计划偏离检测。

### 5.2 核心算法

| 步骤 | 算法 | 说明 |
|------|------|------|
| 多源融合 | 加权平均 | 精度权重 × 更新率权重 × 质量惩罚 |
| 轨迹平滑 | RTS 卡尔曼 | 前向滤波 + 反向平滑，6维CV模型 |
| 可视化拟合 | 二次贝塞尔 | 段间中点插值，前端渲染平滑 |
| 告警标注 | 时空匹配 | 告警时间落入段内 + 最近点索引 |
| 偏离检测 | Haversine | 点到折线大圆距离 |

### 5.3 多源位置定义

| 来源 | 精度(1σ) | 更新率 | 权重 |
|------|:--:|:--:|:--:|
| DRONE_TELEMETRY | 3m | 5Hz | 0.80 |
| RTK_BASE | 0.05m | 10Hz | 0.95 |
| ADS_B | 20m | 2Hz | 0.50 |
| REMOTE_ID | 10m | 1Hz | 0.60 |
| GROUND_RADAR | 75m | 1Hz | 0.30 |
| OPTICAL_TRACKING | 15m | 20Hz | 0.55 |

### 5.4 Kafka Topic

| Topic | 方向 | 说明 |
|-------|------|------|
| uav.telemetry | 消费 | 无人机遥测（主来源） |
| uav.adsb.position | 消费 | ADS-B |
| uav.radar.position | 消费 | 雷达 |
| uav.rtk.position | 消费 | RTK |
| uav.remoteid.position | 消费 | Remote ID |
| uav.alarm.event | 消费 | 告警事件（标注用） |
| uav.track.fitted | 生产 | 拟合统一轨迹 |

---

## 六、部署

### 6.1 Docker 基础设施

```yaml
# docker-compose.dev.yml
services:
  postgres:    postgis/postgis:16-3.4   # :5432
  redis:       redis:7-alpine           # :6379
  emqx:        emqx/emqx:5.3.2          # :1883, :18083
  kafka:       confluentinc/cp-kafka:7.6.0  # :9092
```

### 6.2 启动顺序

```bash
# 1. Docker
docker-compose -f docker/docker-compose.dev.yml up -d

# 2. Java 服务
mvn -pl uav-system spring-boot:run
mvn -pl uav-alarm-engine spring-boot:run
mvn -pl uav-airspace-controller spring-boot:run
mvn -pl uav-airspace spring-boot:run
mvn -pl uav-track-fusion spring-boot:run

# 3. Go 服务
cd uav-realtime && go run ./cmd/server/
cd uav-simulator && go run ./cmd/simulator/

# 4. 前端
cd uav-frontend && npm run dev
```

---

## 附录 A: 变更记录

| 版本 | 日期 | 内容 |
|------|------|------|
| V3.5 | 2025-01 | 新增 uav-track-fusion 多源轨迹融合模块（RTS平滑+Bezier拟合+告警标注+飞行计划关联） |
| V3.4 | 2025-01 | 开发阶段简化：固定验证码、UKey暂不启用、小程序后续申请、告警录像按等级定义 |
| V3.3 | 2025-01 | 高德免费底图、军民协调独立登录、告警录像策略、微信小程序认证 |
| V3.0 | 2025-01 | 政府级监管平台：RBAC、实名登记、飞行计划审批、军民协调、违规处置 |

## 附录 B: 开发状态

| 模块 | 状态 | 说明 |
|------|:--:|------|
| uav-common | ✅ | DTO/枚举/接口 |
| uav-system | ✅ | RBAC + JWT + MFA |
| uav-alarm-engine | ✅ | 六维告警 |
| uav-risk-assessment | ✅ | 风险评估 |
| uav-airspace-controller | ✅ | 冲突检测+解脱 |
| uav-airspace | ✅ | 空域管理 |
| uav-track-fusion | ✅ | ★轨迹融合 |
| uav-realtime | ✅ | Go MQTT→Kafka→WS |
| uav-simulator | ✅ | Go 仿真器 |
| uav-frontend | ✅ | Vue3 + Cesium |
| uav-registry | 🔜 | 实名登记 |
| uav-pilot | 🔜 | 驾驶员管理 |
| uav-flight-plan | 🔜 | 飞行计划审批 |
| uav-miniapp | 🔜 | 微信小程序 |

## 附录 C: 开发阶段简化项

| 项目 | 开发阶段 | 正式上线 |
|------|---------|---------|
| 军民协调员 TOTP | 固定验证码 `123456` | Google Authenticator |
| 军民协调员 UKey | 暂不启用 | 国密 SM2 |
| 微信小程序 | 功能完成后申请 | 政府主体认证 |
| 违规处罚标准 | 预置 7 条模拟规则 | 对接实际法规 |
| 气象数据 | Open-Meteo 免费 | 多源融合 |
| 图传视频 | 暂无真实流 | SRS 集群 |
