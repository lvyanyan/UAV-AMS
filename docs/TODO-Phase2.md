# 无人机管控系统 — Phase 2 待办清单

> 版本: v1.0 | 更新日期: 2026-05-29

---

## 目录

- [2. 完善后端功能](#2-完善后端功能)
- [3. 文档与部署](#3-文档与部署)
- [附录：无人机图片（PNG Sprite）方案说明](#附录无人机图片png-sprite方案说明)

---

## 2. 完善后端功能

### 2.1 告警引擎全链路验证

| 属性 | 说明 |
|------|------|
| **预计时长** | 0.5 天 |
| **前置依赖** | Docker 服务正常运行、Kafka topic 已创建 |
| **涉及模块** | uav-simulator → uav-realtime → uav-alarm-engine → uav-airspace-controller → uav-airspace → uav-risk-assessment |

#### 验证步骤

```
[1] 启动仿真器 → 确认 MQTT 消息发出
    → 检查 uav-simulator 窗口日志: "Published telemetry for UAV-xxx"

[2] MQTT→Kafka 桥接
    → 检查 uav-realtime 窗口日志: "Forwarded to Kafka: uav.telemetry"

[3] 告警引擎消费 Kafka
    → 检查 uav-alarm-engine 窗口日志: "telemetry consumed → alarm:{level}"
    → 确认 Kafka topic uav.alarm.event 中有告警消息

[4] 空域控制器（冲突检测）
    → 检查 uav-airspace-controller 窗口日志: "conflict detected: UAV-A vs UAV-B"
    → 确认 Kafka topic uav.conflict.alert 中有冲突消息

[5] 解脱指令
    → 检查 uav-airspace-controller 窗口日志: "resolution dispatched to: UAV-A"
    → 确认 Kafka topic uav.signboard.resolution / uav.ops-seat.resolution 有指令

[6] WebSocket 推送到前端
    → 前端 F12 Console: "✅ WebSocket 已连接 :8090"
    → 前端看无人机标牌上是否有告警图标/颜色闪烁
```

#### 常见问题排查

| 现象 | 可能原因 | 修复方法 |
|------|---------|---------|
| 仿真器无消息 | MQTT 未连接 | 检查 EMQX 容器: `docker logs uav-emqx` |
| Kafka 无消息 | Topic 未创建 | `docker exec uav-kafka kafka-topics --list --bootstrap-server localhost:9092` |
| 告警引擎无输出 | 规则配置为空 | 检查 AlarmEngineCore 中规则列表 |
| WebSocket 无推送 | 端口冲突 | 确认 uav-realtime 监听 8090，未被其他进程占用 |

---

### 2.2 冲突检测→解脱指令验证

| 属性 | 说明 |
|------|------|
| **预计时长** | 0.5 天 |
| **前置依赖** | 2.1 告警引擎链路正常 |
| **涉及模块** | uav-airspace-controller |

#### 待验证功能点

- [ ] 两架无人机距离 < 安全阈值时触发 ConflictAlert
- [ ] 冲突告警包含 TCPA（最小接近时间）和碰撞概率
- [ ] 解脱引擎生成 Speed / Altitude / Heading / Combined 四种指令
- [ ] 指令正确分发到标牌 Topic 和运服席位 Topic
- [ ] 标牌 Topic 的指令在前端显示（颜色变化 + 解脱建议文字）

#### 测试场景

```json
// 模拟两架无人机相向飞行，预期触发冲突告警
Simulator 配置: drone_count=2, 第一架朝东, 第二架朝西
→ 预期输出: ConflictAlert { droneA: "PATROL-0001", droneB: "PATROL-0002", tCPA: 5.2s }
→ 解脱指令: ResolutionCommand { target: "PATROL-0001", type: "HEADING", value: "+15°" }
```

---

### 2.3 飞行计划审批流程

| 属性 | 说明 |
|------|------|
| **预计时长** | 2 天 |
| **前置依赖** | 2.1 告警链路可用 |
| **涉及模块** | uav-flight-plan, uav-system（权限） |

#### 审批链定义

```
操作员(OPERATOR)  →  填写飞行计划
      ↓
管理员(ADMIN)  →  初审（合规性检查）
      ↓
监管员(SUPERVISOR)  →  二审（空域冲突检查）
      ↓
军民协调席(MILITARY)  →  终审（军事空域冲突检查）
      ├── 一键批准 → 跳过所有下级审批
      ├── 强制取消 → 冲突的民用计划直接取消
      └── 空域清场 → 指定空域内所有非军用无人机 RTL
```

#### 需要测试的 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/flight-plan/create` | POST | 操作员提交计划 |
| `/api/flight-plan/{id}/approve` | POST | 管理员/监管员/军民协调审批 |
| `/api/flight-plan/{id}/reject` | POST | 驳回 |
| `/api/flight-plan/{id}/military-approve` | POST | 军民协调一键批准 |
| `/api/flight-plan/{id}/military-cancel` | POST | 强制取消民用计划 |
| `/api/flight-plan/clear-airspace` | POST | 空域清场指令 |

#### 状态机验证

```
DRAFT → PENDING_REVIEW → APPROVED → SCHEDULED → IN_PROGRESS → COMPLETED
                                          ↓
                                      REJECTED (任意阶段可驳回)
```

---

### 2.4 扩展联调

| 属性 | 说明 |
|------|------|
| **预计时长** | 1 天 |
| **前置依赖** | 2.1 ~ 2.3 |

#### 待联调模块

| 模块 | 端到端验证点 | 状态 |
|------|------------|:----:|
| uav-track-fusion | 轨迹融合后输出到 Kafka topic uav.track.fitted | ⬜ |
| uav-registry | API 创建所有人/无人机 → 写入 PG → 查询返回 | ⬜ |
| uav-pilot | API 创建驾驶员 → 关联无人机 → 查询 | ⬜ |
| uav-airspace | CRUD 空域 → GeoJSON 输出 → 前端显示 | ⬜ |

---

## 3. 文档与部署

### 3.1 更新架构文档

| 属性 | 说明 |
|------|------|
| **预计时长** | 0.5 天 |

#### 需更新的文档内容

| 文件名 | 需补充内容 |
|--------|-----------|
| `docs/UAV-AMS-Design-Doc.md` | • 端口表（8080~8090 + 5173）<br>• 模块依赖关系图<br>• 数据流图（仿真→MQTT→Kafka→告警→冲突→解脱→前端）<br>• 启动顺序说明 |
| `docs/TODO-Phase2.md` | （本文档）完善后固化 |
| `README.md` | 更新启动方式、端口表、快速验证步骤 |

#### 端口总表

| 端口 | 模块 | 类型 | 职责 |
|:----:|------|:----:|------|
| 8080 | uav-gateway | Java | API网关（前端入口） |
| 8081 | uav-system | Java | 用户/权限/登录 |
| 8082 | uav-alarm-engine | Java | 六维告警 |
| 8083 | uav-risk-assessment | Java | 风险评估 |
| 8084 | uav-airspace-controller | Java | 轨迹预测+冲突检测+解脱 |
| 8085 | uav-airspace | Java | 空域CRUD |
| 8086 | uav-registry | Java | 实名登记 |
| 8087 | uav-pilot | Java | 驾驶员管理 |
| 8088 | uav-flight-plan | Java | 飞行计划审批 |
| 8089 | uav-track-fusion | Java | 多源轨迹融合 |
| 8090 | uav-realtime | Go | MQTT→Kafka→WebSocket桥接 |
| — | uav-simulator | Go | 仿真无人机 |
| 5173 | uav-frontend | Vite | 前端页面 |

---

### 3.2 启动方式固化

| 属性 | 说明 |
|------|------|
| **预计时长** | 0.5 天 |

#### 改进目标

| 脚本 | 当前问题 | 改进方向 |
|------|---------|---------|
| `start-all.bat` | Docker 不稳定时秒退；端口残留导致冲突 | 自动重试 Docker 连接；启动前强杀残留端口 |
| `stop-all.bat` | 可能误杀 Docker Desktop 进程（已修复） | 只按端口杀，不碰 Docker / node.exe / go.exe |
| `start-silent.bat` | 日志分散在 logs/ 目录 | 考虑统一日志中心 |

---

## 附录：无人机图片（PNG Sprite）方案说明

### 使用方式

不使用 Canvas 绘制无人机图标，改用预置的 **PNG 图片** 作为 Cesium Billboard 的 image：

```javascript
// 在 Cesium 中加载 PNG 图片
const droneSprite = '/images/drone-marker.png' // 存放在 public/images/ 目录

entity.billboard = {
  image: droneSprite,
  width: 32,
  height: 32,
  rotation: Cesium.Math.toRadians(heading),  // 水平航向角
  scaleByDistance: new Cesium.NearFarScalar(500, 1.0, 50000, 0.3), // LOD缩放
  disableDepthTestDistance: Number.POSITIVE_INFINITY
}
```

### 需要的素材

| 图片 | 用途 | 数量 | 说明 |
|------|------|:---:|------|
| `drone-normal.png` | 正常飞行 | 1 | 绿色/蓝色图标，带航向箭头 |
| `drone-warning.png` | 一般告警 | 1 | 黄色图标 |
| `drone-critical.png` | 严重告警 | 1 | 红色图标 |
| `drone-offline.png` | 离线 | 1 | 灰色半透明图标 |
| `drone-military.png` | 军用无人机 | 1 | 特殊标识 |

### 性能上限

| 渲染方式 | 推估上限 | 关键瓶颈 |
|---------|:-------:|---------|
| Billboard（32×32 PNG） | **5000~8000** | GPU 纹理内存、视锥裁剪效率 |
| Billboard + Label | **800~1500** | CPU 端 Label 更新频率 |
| PointPrimitive（纯点） | **50000+** | 无纹理、无文字 |

### 水平和纵向航向角

| 角度 | 实现 | 说明 |
|:----:|------|------|
| **heading（水平航向）** | `billboard.rotation` + 图片本身带箭头 | 旋转角度 = heading，0°=北 |
| **pitch（纵向俯仰）** | 3D 模型 `orientation` + 四元数 | PNG 2D 图片无法体现俯仰，需要 glTF 模型 |

对于**纯 PNG 方案**，只能展示水平方向（heading），纵向俯仰（pitch）需要 3D 模型。

### 素材来源建议

| 来源 | 链接 | 说明 |
|------|------|------|
| 自行设计 | Figma / Photoshop | 32×32 或 64×64，PNG 透明背景 |
| 开源图标 | [Flaticon] | 搜索 "drone marker" |
| 在线生成 | [Cesium Sandcastle] 示例 | 可临时用圆形点+方向线替代 |
| 临时方案 | Canvas 生成一次 → 导出为 PNG | 开发阶段先用代码生成 |

> **推荐**：先使用 Canvas **在开发阶段生成一次 PNG 图片**（不是实时绘制，而是 `toDataURL` 导出为静态图片），后续可替换为设计师提供的正式素材。
