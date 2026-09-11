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

**实施记录（2026-09-11 完成验收）**
- 后端：uav-realtime 内置二进制聚合通道 **:8091/fleet**（移植 million-demo 的 fleet/movement/wshub，协议一致 UAVV/USAC），视锥感知（高空 cell 聚合 / 低空 bbox 裁剪 / full 全量），`/resize?count=N` 热调规模、`/stats` 查在线数；8090 JSON 遥测/告警链路零改动。配置段 `fleet:`（default.yaml）。
- 数据源：未采用 (a) 独立 stress.exe，改为**机群生成器直接并入 uav-realtime**（等效于任务书第 1 步的 "fleet 二进制帧生成"）——省掉跨进程 100MB/s 转发，单服务即可验收，告警链路不受影响。
- 前端：`src/worker/fleetWorker.js`（二进制解析+抽稀+ECEF，零拷贝）+ `src/composables/useMillionRenderer.js`（v4 懒加载分帧增长，Point 20万/Billboard 1万）；FlightMonitor 工具栏「🚀 百万模式/返回标准模式」切换，进入时相机拉高 50km 俯瞰，在线数走 `/stats` 轮询（cell 帧的 count 是网格数非机群数）。
- 自检工具：`uav-realtime/cmd/fleettest`（校验 cell/raw 帧格式 + resize + 8090 JSON 链路）。
- e2e 验收（playwright，2026-09-11）：10万→100,000 / 50万→500,000 / 100万→1,000,000 在线数精确；**FPS 119-120**（≥30 达标）；低空原始帧模式正常；告警面板全程滚动（27→50 条，切换模式无中断）；切回标准模式恢复 1505 架真实遥测。截图：`%TEMP%\uav_shot\m_100k/500k/1000k/lowalt/back_standard.png`。
- **服务拆分（2026-09-11 追加）**：百万通道已从 uav-realtime 拆出为独立服务 **uav-fleet-press**（:8091，与 uav-simulator 平级）——四个自包含包（fleet/movement/wshub/press）整体迁移，独立 go.mod + config；uav-realtime 回归纯 JSON 遥测/告警链路。动机：100 万架时 5Hz 全量推进 + 每客户端聚合的 CPU/GC 压力与 MQTT 心跳同进程存在干扰风险（本服务曾有 7b5ae7f 心跳阻塞前科），且 OOM/panic 故障半径不应波及告警链路。端口/协议/前端零改动，回归通过（含 1M 渲染 120FPS、告警面板并行滚动）。

**状态**：[x] 已完成（2026-09-11）

---

## 任务二：仪表盘图表化（ECharts）

**实施记录（2026-09-11 完成验收）**
- `npm i echarts`；Dashboard.vue 重写为四张真图表：告警级别分布→环形（byLevel，危急红/警告橙/一般蓝）、告警类型 TOP5→横向条形（byType 排序）、飞行计划状态→环形（planStatus 前端聚合，已批准绿/拒绝红/审批中蓝橙）、近 30 分钟告警趋势→红色面积折线（alarm/list 按 createTime 逐分钟分桶，窗口内无数据时显示"近 30 分钟无告警"提示）。
- 保留六指标卡 + 最新告警表 + 快捷操作；15s 轮询；setOption(option, true) 即 notMerge；window resize 自适应；onUnmounted dispose。
- 顺带修复：旧模板引用不存在的 `stats` 变量导致 `Cannot read properties of undefined (reading 'alarmByLevel')` 页面崩溃，重写后消失。
- 验收：4 张 canvas 全部渲染，环形按级别/状态正确着色，趋势图呈现真实告警尖峰；e2e 无 pageerror。注意：运行中 dev server 装 echarts 后需重启 `npm run dev`（vite 依赖再优化），否则白屏。

**状态**：[x] 已完成（2026-09-11）

---

## 任务三：列表页 UI 统一美化

**实施记录（2026-09-11 完成验收）**
- **整体深色主题**（用户反馈"黑壳白内容不和谐"）：引入 Element Plus 官方 dark css-vars（`html.dark`）+ `src/styles/theme.css` 深海军蓝色板覆盖（页面 #0a111d / 卡片 #101a2c / 边框 #24344f 系），全站内容区与深色外壳同色系；`src/main.ts` 注册全部 Element Plus 图标（修复侧栏图标一直未渲染的旧问题）。
- 公共组件：`composables/usePaging.ts` 前端分页（页码越界自动收敛）；theme.css 提供 page-header/page-title(主色竖条)/page-subtitle/header-actions/table-card/table-footer 公共类。
- 七页统一规范落地：大标题+副标题+操作区右对齐页头、el-card 表格卡片、el-empty 空态、el-pagination、时间 `YYYY-MM-DD HH:mm`、tag 语义配色（字段映射未动）。Registry/System 双 tab 页分别独立分页实例。
- **Violation 假数据清除**：uav-system SysQueryController 新增 `/api/violation/list` 只读接口（同库派生 alarm_record 危急/严重告警为违规台账，处置联动 `/api/alarm/{id}/handle`），网关路由补 `/api/violation/**`；System 页删除前端假数据兜底，并修复 user/audit 返回字段与前端契约的映射。
- **顺带修复存量 403**：SysQueryController 的 JdbcTemplate lambda 误配 ResultSetExtractor 重载（缺 rs.next()）导致 user/role/audit 一直 500→错误转发被安全层转成 403、前端永远走假数据兜底——本会话重写时修正为 RowCallbackHandler。
- 验收：8 页 e2e 全部渲染正常（airspace 9 行/pilot 6/flight-plan 7/alarm 20/violation 20 真实数据/system 5/registry 3），无 pageerror，截图 `%TEMP%\uav_shot\t_*.png`。

**状态**：[x] 已完成（2026-09-11）

---

---

## 追加任务：字典服务 + 全局日期格式化（2026-09-11，用户反馈"类型露英文、日期未格式化"）

- **字典服务**：uav-system 新增 `sys_dict` 表（DictInitializer 幂等建表+种子，枚举值盘点自生产库：airspace_type 实际含 CTR/RESTRICTED/TEST 等 9 种）+ `SysDictController`（/api/dict/all 一次全量、/data/{type}、增删改），网关路由补 `/api/dict/**`。
- **前端**：`composables/useDict.ts` 全局缓存一次拉取 + 内置种子回退（字典服务挂了也不裸奔英文）；`api/dict.ts`；八页硬编码映射全部替换（空域类型/机型/登记状态/飞手状态/计划状态/告警级别与类型/违规状态/用户角色），未登记的值原样透出便于发现漏维护枚举。
- **字典管理 UI**：System 页新增「字典管理」tab（类型筛选 + 增删改，写回全局缓存即时生效）。
- **日期格式化**：uav-system 控制器与 alarm-engine JacksonConfig 在源头统一输出 `yyyy-MM-dd HH:mm:ss`（此前 ISO 串/微秒直出）；前端 `utils/format.ts`（fmtDateTime/fmtDate）替换各页自带的切片实现，表格统一 `YYYY-MM-DD HH:mm`。
- 验收：空域 9 类型全部中文（机场管制区/限制区/试验区等此前漏翻的值）、各页时间统一格式、System 字典 tab 增删改查可用。

**状态**：[x] 已完成（2026-09-11）

---

## 任务四：环境速查（新会话直接用）

- 基础设施（WSL Ubuntu 内 docker）：`wsl -d Ubuntu -- docker start uav-postgres uav-redis uav-emqx uav-kafka`；Windows 侧经 localhost 转发或直连 WSL IP（当前 172.27.19.223，重启可能变化）。
- Java：JDK `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe`；启动顺序：gateway(18080) → system(8081) → alarm-engine(8095) → 其余按需；jar 锁文件——重启服务前必须先停进程再 build。
- Go：uav-realtime(8090，**必须在 uav-realtime/ 目录下启动**，config 相对路径)；uav-fleet-press(8091 百万级二进制通道，**必须在 uav-fleet-press/ 目录下启动**，独立进程与告警链路隔离)；uav-simulator（MQTT → 172.27.19.223:1883；同 client_id 会互踢，**启动前 taskkill //F //IM uav-simulator.exe 清干净**）。
- 前端：`cd uav-frontend && npm run dev`（5173；百万 demo 前端在 million-demo/web，也会抢 5173，冲突时后启动者跳 5174）。
- 登录：admin / admin123（登录接口已验证出 JWT；路由守卫只查 localStorage.token）。
- e2e 脚本模板：`%TEMP%\uav_shot\`（playwright-core + 系统 Chrome；登录→导航→断言→截图）。
- Git：`git push origin main`（Gitee）+ `git push github main`（GitHub，经 hk 中继）。
- 红线：改 jar 内配置无效——必须改 src yml 后重新 `mvn -pl <module> install`；**重建 jar 前必须先停对应服务**（Windows 文件锁）。

**状态**：[ ] 未开始
