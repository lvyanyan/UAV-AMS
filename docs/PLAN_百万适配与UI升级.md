# UAV-AMS 升级任务书：百万级大屏适配 + 仪表盘图表化 + 列表页 UI 统一

> 2026-09-11 立项。新会话从本文档开工，完成后逐项勾选并更新「状态」。
> 前置阅读：`交接说明.md`（D:\求职材料）第二、五、七节 + 本文。

---

## 任务一：飞行监控大屏适配百万级（核心）

**目标**：飞行监控页（uav-frontend `/flight-monitor`）支持 10 万～100 万架在线渲染，与现有 JSON 遥测/告警链路并存。

**现状与约束**
- 现有链路：simulator → MQTT(`uav/+/telemetry`, JSON) → uav-realtime(Go, WS 8090) → 前端 PointPrimitiveCollection。千级已验证稳定（1006 架 + 告警，2026-09-11），万级可用，**百万级必须走二进制/聚合通道**。
- 参考实现（同仓库）：`million-demo/` 已验证 100 万架：Go 二进制帧（20B/架，magic UAVV）+ ArrayBuffer 零拷贝 + Worker 解析抽稀 + 视锥聚合（v5：高空网格聚合/低空视野裁剪），20 万点渲染 rAF 100+。注意：v5 的 BufferPoint 协议与主应用 JSON 遥测协议不同。
- 前端渲染器：`uav-frontend/src/composables/useLodDroneRenderer.js`（2026-09-11 已修复：LOD 三集合互斥、BufferPoint 位置更新失效问题——buffer 层已停用，万级走 PointPrimitiveCollection）。

**建议方案（按序做）**
1. 后端：uav-realtime 增加二进制聚合通道（参考 million-demo 的 wshub + fleet 二进制帧生成），新端口或 /ws 二进制子协议；保留现有 JSON 遥测（告警面板依赖）。
2. 前端：FlightMonitor 增加「标准模式 / 百万模式」切换；百万模式走二进制 Worker 解析 + BufferPointCollection 渲染（渲染器 v2 已有懒加载分帧增长）。
3. 数据源：两种选择——(a) 复用 million-demo stress.exe 独立起数据源；(b) uav-simulator 增加二进制输出。建议先 (a)，不动告警链路。
4. 验收：10 万/50 万/100 万三档截屏；在线数正确；帧率 ≥30FPS；切换模式不影响告警面板。

**状态**：[ ] 未开始

---

## 任务二：仪表盘图表化（ECharts）

**目标**：Dashboard.vue 从 CSS 柱状条升级为真图表，主题与飞行监控大屏一致。

- 依赖：`npm i echarts`（uav-frontend 已有 node_modules，直接装）。
- 图表清单：
  1. 告警级别分布 → 环形图（数据 `/api/alarm/stats` 的 byLevel）
  2. 告警类型 TOP5 → 横向条形（byType）
  3. 飞行计划状态 → 环形图（flight-plan/list 按 plan_status 前端聚合）
  4. 近 30 分钟告警趋势 → 折线/面积（前端按 alarm/list 的 createTime 分桶聚合；数据不足时显示提示）
- 保留：六个核心指标卡 + 最新告警表 + 快捷操作。轮询 15s 刷新，图表 setOption 时 notMerge。
- 注意：卡片/图表配色统一（蓝 #409eff 主色、红 #f56c6c 危急、橙 #e6a23c 警告、绿 #67c23a 正常）。

**状态**：[ ] 未开始

---

## 任务三：列表页 UI 统一美化

**范围**：Airspace.vue / Registry.vue / Pilot.vue / FlightPlan.vue / AlarmCenter.vue / Violation.vue / System.vue

- 统一规范：页头（大标题 + 副标题 + 操作区右对齐）、表格卡片化（el-card 包裹、圆角、悬浮高亮）、状态 tag 配色全局一致、时间统一 `YYYY-MM-DD HH:mm` 格式化、空数据显示 el-empty、列表加分页（el-pagination，前端分页即可）。
- 字段映射（2026-09-11 已对齐，勿回退）：
  - Airspace：airspaceName/airspaceCode/airspaceType/altFloorM/altCeilingM/startTime/endTime/isActive
  - Registry：owner(idNumber/address/registerStatus)、drone(droneSn/droneModel/droneType/weightG/registrationId)
  - Pilot：pilotName/idNumber/licenseLevel/licenseNo/licenseExpire/status
  - FlightPlan：planCode/pilotId(前端映射飞手姓名)/droneSn/departure/destination/plannedStart/plannedEnd/altCeilingM/planStatus
- 已知残留：Violation.vue 与 System.vue 的后端接口可能不存在（缺控制器），若 500/404 需在 uav-system/uav-flight-plan 侧补只读接口，勿在前端造假数据。

**状态**：[ ] 未开始

---

## 任务四：环境速查（新会话直接用）

- 基础设施（WSL Ubuntu 内 docker）：`wsl -d Ubuntu -- docker start uav-postgres uav-redis uav-emqx uav-kafka`；Windows 侧经 localhost 转发或直连 WSL IP（当前 172.27.19.223，重启可能变化）。
- Java：JDK `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe`；启动顺序：gateway(18080) → system(8081) → alarm-engine(8095) → 其余按需；jar 锁文件——重启服务前必须先停进程再 build。
- Go：uav-realtime(8090，**必须在 uav-realtime/ 目录下启动**，config 相对路径)；uav-simulator（MQTT → 172.27.19.223:1883；同 client_id 会互踢，**启动前 taskkill //F //IM uav-simulator.exe 清干净**）。
- 前端：`cd uav-frontend && npm run dev`（5173；百万 demo 前端在 million-demo/web，也会抢 5173，冲突时后启动者跳 5174）。
- 登录：admin / admin123（登录接口已验证出 JWT；路由守卫只查 localStorage.token）。
- e2e 脚本模板：`%TEMP%\uav_shot\`（playwright-core + 系统 Chrome；登录→导航→断言→截图）。
- Git：`git push origin main`（Gitee）+ `git push github main`（GitHub，经 hk 中继）。
- 红线：改 jar 内配置无效——必须改 src yml 后重新 `mvn -pl <module> install`；**重建 jar 前必须先停对应服务**（Windows 文件锁）。

**状态**：[ ] 未开始
