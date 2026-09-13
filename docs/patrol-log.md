# 定时巡检日志

> 由每 30 分钟的巡检定时任务维护：每轮追加一行摘要；"待办"小节保持最新（做完的划掉）。下一轮先读本文件接力。

## 巡检记录

- **2026-09-12 14:55（第 1 轮）**
  - 服务完整性：✅ 全绿。4 个 Docker 容器（postgres/redis healthy、emqx/kafka）Up 2h；12 后端端口 + 前端 5173 全部监听。注意：docker CLI 在 Windows 侧不可用，基础设施跑在 WSL2 Ubuntu（`wsl -d Ubuntu -- docker ps`），服务经 172.27.x.x（WSL IP）连接，**不要用 localhost 探测基础设施端口**。
  - 日志扫描：gateway.log 13:15-13:16 有 connection refused → alarm-engine(8095) 的报错，为短暂重启窗口，已自愈（13:59 alarm-engine 正常出告警），无需处理。
  - 视觉走查（深入修复了飞行监控页，超配了）：**FlightMonitor 修复 3 处（FlightMonitor.vue），已实测验证**：
    1. **根因**：telemetry-batch 帧的 item 是完整信封 `{type:'telemetry',data:{...}}`，前端未解包 → `getDroneId` 返回空 → 全部遥测被静默丢弃 → 地图空白"在线: 0"。修复：handler 内解包 `d.data !== undefined ? d.data : d`。
    2. 初始机位：俯仰 -30°/15km 的视锥落在北郊空域（纬度 40.02+），机群全在 39.5-39.99 被服务端视锥裁剪。改为顶视 60km 对准 (116.35, 39.75)。
    3. 自愈：updateStats（2s）中增加"渲染器未就绪且槽位映射存在 → 重试 init"和"WS 消息流中断 >10s → 重挂 handler + 补发订阅"。
    - 验证：在线 1,101、slot5 位置 5 秒移动 163 米、LOD层: mid。
  - Dashboard 星期显示"星期五"为此前对截图的误读，实测渲染"星期六"正确，未改代码。
  - 遗留：本环境 IAB 截图对 WebGL 页面（Cesium）超时失败，本轮靠 DOM/组件状态验证；下轮可重试截图。
- **2026-09-12 15:25（第 2 轮）**
  - 服务完整性：✅ 全绿。13 端口 + 4 容器（healthy）全部正常；gateway.log 自 13:16 起 mtime 未变 = 零新错误（此前扫描出的"60 条 Exception"是旧堆栈中 `java.net...` 开头的行按字符串比较漏过时间过滤的假阳性，注意）。
  - FlightMonitor 复验：✅ 在线 1,078→1,093 持续流动、applyAgeSec≈1s、自愈未误触发（无 "WS 消息流中断" 刷屏）。第 1 轮修复稳定。
  - 视觉走查（FlightPlan / Airspace / Airport）：三页均健康。FlightPlan 状态列、Airspace 类型/状态列的"看不见"均为缩放截图误读，DOM + 计算样式证实文字存在且对比度达标（灰 #909399 对暗底 ≈5.5:1）。**教训：小字/标签用 getComputedStyle 验证，勿信压缩截图。**
  - 修复 1 处：**顶栏链路指示器误导**（MainLayout.vue + websocket.ts）。原逻辑 `wsClient.connected` 仅在监控页挂载后为 true，导致其他页面永远显示红色"链路中断"（把"未启用"误报成"中断"）。新增 `everConnected` 标记：本会话从未建连则隐藏指示器；建连后离开监控页保持"实时链路"；真断线才显示"链路中断"。三态已实测。
  - 环境经验（下轮直接用）：IAB 截图超时后输入管线会被卡死（点击全部无效）——**重开标签页恢复**；每次截图前先 `visibility.set(true)`，面板被隐藏时截图必失败。
  - 观察项：FlightPlan 中 STRESS 计划"飞手"列为 "--"（仿真数据缺飞手字段，数据层面问题，非 UI 缺陷）。
- **2026-09-12 16:18（第 3 轮）**
  - 服务完整性：✅ 全绿。13 端口 + 4 容器正常；15:26 以来零条带时间戳的 ERROR（日志扫描法已改为只认行首时间戳）。
  - 视觉走查（Registry / Pilot / AlarmCenter）：
    - Registry：健康。所有人表"操作"列空是 v-if 设计（审批通过不渲染按钮）；"已通过"标签正常。无人机登记 207 架分 21 页正常。
    - Pilot：健康。"状态"列"正常"绿标签渲染完好（第 4 次全页截图误读，放大+DOM 双重确认）。
    - AlarmCenter：**修复 1 处真缺陷**——"来源模块"列永远空白：后端 /api/alarm/active 无 sourceModule 字段（11 个键），前端直绑 prop 导致整列空。修复：模板回退 `row.sourceModule || $t('alarm.sourceEngine')`（所有告警确由告警引擎产生，事实正确、向前兼容），新增 i18n 键 alarm.sourceEngine（zh-CN: 告警引擎 / en: Alarm Engine）。已复验：整列显示"告警引擎"。
  - 会话过期观察：admin 登录 token 约 2 小时过期（14:12 登录，16:10 后 401 弹回 /login），属正常安全行为；巡检遇弹回登录页就重新登录即可。
  - 环境经验补充：本轮又发生 2 次截图管线卡死（点击全失效），重开标签页均恢复；截图前 vis.set(false)→set(true) 再截，可少踩坑。
- **2026-09-12 17:10（第 4 轮）**
  - 服务完整性：✅ 全绿。13 端口 + 4 容器正常；16:18 以来零 ERROR。
  - 视觉走查（Violation / MessageReplay / AirRoute）：**三页均健康，本轮零缺陷**。
    - Violation：等级"危急"、处理状态"待处理"标签均正常（el-tag 小标签在第 5 次缩放截图误读，DOM 证实）。
    - MessageReplay：功能实测通过——点击"开始回放"（16x）后虚拟时钟推进、57287/57287 条点位全部应用；黑底点阵是回放画布的设计形态，非缺陷。
    - AirRoute：方向（双向/单向）、状态（启用）标签正常（第 6 次误读，DOM 证实）。
  - 上轮链路指示器修复持续生效：新会话未开监控页时无红色"链路中断"误导。✅
  - 结论：连续两轮零新增视觉缺陷（大问题已在第 1-3 轮清完），UI 进入稳定期；下轮起除轮转走查外，可把重心放回服务完整性的深度检查（如接口探活、容器资源占用）。
- **2026-09-12 18:10（第 5 轮）**
  - 服务完整性：✅ 全绿 + 深度检查首次落地。13 端口 + 4 容器正常；17:10 以来零 ERROR。HTTP 探活：网关 401（鉴权生效）、前端 200、各 Java 服务 403/404（应用层存活）。**容器资源基线**：kafka 2.0G / emqx 298M(CPU 27%,MQTT 遥测正常负载) / postgres 163M / redis 8M，总 ~2.5G/15.5G，千机压测合理水位，下轮对比趋势。
  - 视觉走查（SystemDict / SystemAudit / Military）：**三页均健康，零缺陷**。
    - SystemDict：分组手风琴 + 值表 + 编辑/删除齐全，airport_type 等 7 组字典数据正常。
    - SystemAudit：5 条登录留痕（含第 3 轮 16:15 重新登录的记录，审计闭环正常）。
    - Military：四卡片控制台布局完好，权限徽标、禁用态按钮 UX 正确；调度日志时间线是 5 月种子示例数据（非缺陷）。
  - 截图管线经验：每页首次截图大概率失败一次，vis.set(false)→(true) 后重试即恢复——已成稳定处置套路。
- **2026-09-12 19:15（第 6 轮）**
  - 服务完整性：✅ 全绿。资源趋势 vs 第 5 轮基线**持平无泄漏**：kafka 2.02→2.10G、emqx 298→297M(CPU 28%)、pg 163→161M、redis 8.1→8.3M。18:10 以来零 ERROR。
  - 第二轮轮转开始（Dashboard / FlightMonitor / FlightPlan 复验）：**全部通过，零缺陷**。
    - Dashboard：日期星期正确；危急告警 1812→1489（处置闭环在消化存量和增量）；趋势图 19:06 有新鲜告警脉冲；图表全部正常。
    - FlightMonitor：在线 1,088、遥测 applyAgeSec 0-1s 持续流动、自愈稳定（已上线 4 小时+）、链路 chip"实时链路"正确。
    - FlightPlan：207 计划 / 每页 20 行 / 状态列正常。
  - 重要澄清：第 6 轮一度误判"登录后 token 被 401 清除"——实为标签页输入管线卡死导致登录点击未生效的连锁误判。XHR 打桩证实后端鉴权完全正常（login 200 + 全部引导 API 200）。**经验：判"鉴权问题"前先排除输入卡死（挂 click 监听器看事件是否到达页面）。**
- **2026-09-12 20:10（第 7 轮）**
  - 服务完整性：✅ 全绿。资源趋势连续 3 轮平稳：kafka 2.14G、emqx 301M(CPU 28%)、pg 161M、redis 8.1M——8 小时压测无泄漏。19:15 以来零 ERROR。
  - 视觉走查（Airspace / Airport / Registry，第二轮轮转第 2 批）：**三页均健康，零缺陷**。Airspace 本轮截图清楚呈现彩色类型标签（禁飞区红/示范区蓝/走廊绿）与"启用中/停用"状态；Airport 与 Registry 与前轮一致。
  - 会话备注：token 过期后直接 goto 目标页会经守卫弹回 /login，重新登录即可，属正常守卫行为。

## 待办（下一轮接力）

- [x] ~~第一轮全页面轮转~~（第 2-5 轮 ✅）
- [x] ~~第二轮轮转第 1-2 批~~（第 6 轮 Dashboard/FlightMonitor/FlightPlan ✅、第 7 轮 Airspace/Airport/Registry ✅）
- [ ] **第二轮轮转第 3 批**：下一轮 → **Pilot、AlarmCenter、Violation**；再下轮 MessageReplay、AirRoute、SystemDict → SystemAudit、Military，然后循环。
- [ ] 深度完整性增强（持续）：资源趋势对比每轮跟踪（3 轮持平）；HTTP 探活已纳入例行。
- [ ] 观察项：Dashboard 累计告警数字持续增长属 STRESS 压测产物（危急数在下降，闭环正常）。
- [ ] 观察项：STRESS 计划"飞手"列 "--"，修正应改 uav-simulator 造数，属后端数据问题。
- [ ] 观察项（可选，后端）：alarm 真实"来源模块"应改 uav-alarm-engine 加 sourceModule 字段；当前前端回退"告警引擎"已够用。
- [ ] 观察项：Military 调度日志时间线为种子数据，如需真实化应接后端事件流。
