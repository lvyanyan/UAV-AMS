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
- **二次打磨（同日，用户反馈）**：① 修全站 API 双重 /api 前缀（写操作全部 404 的根源，含告警处理按钮）；② Dashboard TOP5/最新告警接字典；③ System 拆为 用户/角色/审计/字典 四个子路由页，侧栏子菜单；④ 字典加业务分组（sys_dict_type 注册表 + /api/dict/meta + 分组折叠管理页）；⑤ 各列表表单对齐后端实体（空域类型下拉接字典、机型下拉、飞手执照等级 CAAC 真实值、计划表单 pilotId/plannedStart/plannedEnd/altCeilingM）；⑥ 全站 emoji 换 EP 图标（logo/面板头/快捷按钮/监控页）；⑦ Element Plus 中文 locale（分页「共 N 条」）。

**状态**：[x] 已完成（2026-09-11）

---

---

## 追加任务二：飞行业务闭环（航路/起降场/黑飞告警/合规审批/告警着色）

**实施记录（2026-09-12 完成验收）**
- **黑飞（无计划飞行）告警**：alarm-engine TelemetryConsumer 新增规则——维护「已登记且当前时段有已批准计划」白名单（30s 刷新），SN 不在名单即告 NO_FLIGHT_PLAN/SERIOUS，同机 10 分钟限频。实测 1005 架 STRESS 无人机被标记黑飞，已登记 REG-UAV 不误报。
- **航路管理**：flight-plan 模块新增 UavRoute 实体/服务 + InfraController（/api/route CRUD + /api/route/check 禁飞区预检），种子 3 条北京走廊航路；前端 AirRoute.vue（航点编辑器 + 禁飞区预检按钮）。
- **起降场管理**：新表 uav_airport（InfraInitializer 建表 + 4 个北京起降场种子）+ /api/airport CRUD + 预检；前端 Airport.vue。
- **审批合规校验**：提交计划时汇聚起降场坐标 + 航路点，对 NO_FLY 空域 GeoJSON 做射线法 point-in-polygon 判定，命中硬拒绝并返回违规原因（如"坐标(...) 位于禁飞区「天安门核心禁飞区」"）。
- **监控点告警着色**：useLodDroneRenderer mid 层 PointPrimitive 按 droneMeta 告警等级着色（危急红 #f56c6c / 警告橙 #e6a23c / 一般蓝），低层 Billboard 原有图标色逻辑不变。
- 网关路由 /api/route/**、/api/airport/** → 8088；字典新增 route_direction/airport_type 分组与 NO_FLIGHT_PLAN 告警类型。
- 验收：航路 3 条/起降场 4 个 CRUD 可用；黑飞告警入库；监控点出现红色告警点；e2e 无 pageerror。

**状态**：[x] 已完成（2026-09-12）

---

---

## 追加任务三：告警开关状态 + 地图可视化 + 服务域治理

**实施记录（2026-09-12 完成验收）**
- **告警开关语义**：alarm_record 增加 status(OPEN/CLOSED)+closed_time；命中时先查是否已开启（AlarmOpenStateStore 内存缓存+DB 兜底），开启则不再推送/落库，直到关闭后同机同类型可重新触发；存量重复 OPEN 已合并（每键仅保留最新）。处理动作=关闭（按键关闭该机该类型全部 OPEN）。
- **stats 语义**：total=累计全量；byLevel/byType=开启中的活跃分布。
- **violation 接口迁移**：违规台账从 uav-system 迁至 alarm-engine（/api/alarm/violation/list，属告警/监管域），uav-system 删除该接口；前端改走 alarmApi。注：Flowable 审批引擎本就在 uav-flight-plan 独立进程（system.log 里的 3.5.5 banner 是 MyBatis-Plus）。
- **MapPicker 地图组件**（Cesium+高德底图）：point/route/polygon 三模式、可编辑选点、空域叠加层（禁飞区红色）、只读 track/marks 叠加、自动视野适配、undo/clear。
- **四页集成**：空域对话框多边形绘制（GeoJSON 自动生成/解析）；航路对话框地图选点（与航点列表联动）；起降场地图选点（与坐标字段双向）；飞行计划对话框态势图（航路橙线+起降场绿点+禁飞区红色叠加）。
- 验收：关闭 STRESS-0957 告警后离开开启列表、存量重复 OPEN 清零；四页地图渲染正常；e2e 全过。

**状态**：[x] 已完成（2026-09-12）

---

---

## 追加任务四：飞行监控——图层开关 / 消息重放 / 告警抑制

**实施记录（2026-09-12 完成验收）**
- **图层滚动列表**：工具栏「图层」按钮打开面板，按 空域/航路/起降场 三组滚动列表逐项勾选（禁飞区红色标注、带类型/方向字典标签），勾选即在 Cesium 上叠加多边形（含名称标签）/航路折线+航点/起降场绿点。
- **消息重放**：标准模式滚动录制最近 10 分钟遥测（环形缓冲）；「重放」面板支持 开始/暂停/继续/停止 + 进度拖拽 + 1x/4x/16x/64x 倍速（倍速切换重锚定不跳变）；重放期间实时遥测挂起，停止后恢复；进入百万模式自动停止重放。
- **告警抑制**：按 告警类型/级别/SN 组合规则抑制（localStorage 持久化），命中的告警不进入告警面板与计数，面板显示已抑制条数。
- **AlertPanel** 级别/类型接入字典转义；面板删除 emoji。
- 验收：图层 16 项三组勾选渲染正常；重放 29767 条全部应用（进度 100% 自动暂停）；抑制规则添加生效；e2e 无 pageerror。

**状态**：[x] 已完成（2026-09-12）

---

---

## 追加任务五：监控页重构反馈修复（图层拆分/告警双通道/重放独立/抑制服务端化）

**实施记录（2026-09-12 完成验收）**
- **图层拆分**：单一图层按钮拆为 空域/航路/起降场 三个按钮，各自独立滚动列表（含全选），修复列表过长选择困难。
- **告警双通道**：初始化时接口拉取活跃告警（告警重放，一次性）驱动告警点着色；此后由 WS 实时推送增量更新——去掉轮询（用户明确轮询不可取）。
- **消息重放独立菜单页**：FlightMonitor 内嵌回放面板移除。uav-realtime 新增遥测内存仓（latest 快照 + 10 分钟环形历史）与 /snapshot、/history?seconds= 端点；新页 MessageReplay 支持 最近1/5/10分钟 时间窗查询 + 地图回放（PointPrimitiveCollection，播放/暂停/倍速/进度拖拽）。FlightMonitor 加载时调 /snapshot，新连接立即获得全量在飞态势。
- **告警抑制服务端化**：alarm_suppress 表（user_id+类型/级别/SN 组合规则）+ /api/alarm/suppress GET/POST/DELETE；生成侧 AlarmSuppressStore 过滤（命中不推送不落库）；前端改 el-dialog + el-checkbox-button 配置（勾选即调接口），废弃 localStorage。
- **其他**：黑飞告警升级 CRITICAL（监控点红色）；丢帧提示阈值≥5 且 3 秒自动隐藏（背压丢弃属正常降级）。
- 验收：快照 1006 架接入、历史 5.3 万条 1 分钟窗回放 100%、抑制规则勾选落库、图层三按钮列表正常；e2e 无 pageerror。

**状态**：[x] 已完成（2026-09-12）

---

---

## 追加任务六：告警态势一致性修复（满屏红/标牌无告警）

**实施记录（2026-09-12 完成验收）**
- **根因**：① getTerrainElevation 是返回 0 的 TODO 桩，地形告警退化为"绝对高度<30m 告警"，STRESS 机群几乎全体命中；② 4.4 万条历史告警被开关迁移回填为 OPEN，全量涌入活跃列表；③ 标牌告警文案只在 WS 事件里写入，初始化重放路径未写 → 红点+标牌"无"不一致。
- **修复**：① AlarmStaleCloseRunner 陈旧告警自动关闭（OPEN 超 30 分钟自动关闭，启动清存量 + 每 60s 增量，关闭时同步驱逐 AlarmOpenStateStore 缓存键，保证下次命中可重新触发）；② replayActiveAlarms 初始化重放时写入标牌告警文案（级别+类型字典化）；③ WS 事件文案同步字典化。
- 遗留说明：STRESS 机群 1000 架均为未登记黑飞，红点属真实态势；地形规则待接入真实 SRTM 高程后才有精确语义。
- 验收：启动清理 56759 条陈旧告警、新命中重新开启 1288 条；标牌显示字典化告警文案；抑制对话框/图层面板正常。

**状态**：[x] 已完成（2026-09-12）

---

---

## 追加任务七：代码结构组件化（页面重复模式封装）

**实施记录（2026-09-12 完成验收）**
- **修复违规处置页报错**：迁移时产生的重复 `alarmApi` 导入导致编译失败（页面白屏）。
- **组件封装**：PageHeader（主色竖条标题+副标题+操作区右对齐）、TablePagination（统一分页布局与页码尺寸）、SuppressDialog（从 FlightMonitor 抽取的告警抑制弹窗，规则增删走事件）。
- 全部列表页（Airspace/Registry/Pilot/FlightPlan/AlarmCenter/Violation/SystemUsers/Roles/Audit/Dict）统一接入，消除十余处重复的页头/分页模板。
- 验收：11 个页面全部正常渲染（含 violation 20 行真实数据），e2e 无 pageerror。

**状态**：[x] 已完成（2026-09-12）

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
