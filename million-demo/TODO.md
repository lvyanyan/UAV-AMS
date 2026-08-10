# million-demo 待办与优化项（跨 session 续接）

> 上次进度：v5 服务端视锥聚合已实现（cell 帧 + raw 帧 + viewport 上报），
> 但聚合带来**抖动 + 高空点云效果消失**，体验不佳。决定**回退聚合**，
> 保留视窗裁剪，用回 v4 均匀采样点云。聚合单独拉分支做。

---

## 任务 1：聚合单独拉分支做

**前提**：million-demo 还没初始化 git，先建仓库。

```bash
cd D:\dronesManager\million-demo
git init
git add -A
git commit -m "baseline: 视窗裁剪 + 均匀采样点云（v4.5，不含聚合）"
# 先把聚合回退掉再 commit baseline，然后拉分支重做聚合
git checkout -b feature/server-aggregation
```

**聚合分支要做的事**：
1. 在 `fleet.go` 保留 `WriteCellFrame` / `WriteViewFrame` / `Viewport` / `CellDeg`
2. 在 `main.go` 的 `gen` 恢复 cell/raw 分流（v5 已写，可 cherry-pick）
3. 在 worker 保留 magic 分流（v5 已写）
4. 在 renderer 保留 cell 着色（`cellColor` + `renderPoints` 的 `_isCell` 分支）
5. **重点优化抖动**（上次没解决）：
   - cell 帧服务端加**滞回阈值**（hysteresis）：格子从"蓝→金"用阈值 10，"金→蓝"用阈值 6，防边界抖动
   - 或客户端**插值过渡**：cell 点大小/颜色不瞬切，用 200ms lerp
   - 或**降低 cell 帧率**：聚合数据每秒推 2 次而非 5Hz，减少跳变
6. 聚合开关化：默认关（用采样），UI 加"密度热力"开关按需开

**抖动根因记录**：无人机移动 → 格子成员变 → count 跳变 → 点大小/颜色瞬切。
真实网络部署百万级时才有必要（省带宽），localhost demo 里纯增益不明显。

---

## 任务 2：未做优化项清单

### A. 架构 / 性能

| 项 | 价值 | 工作量 |
|---|---|---|
| **视窗裁剪**（低空 bbox 过滤，已实现 raw 帧版） | 纯赚，保留 | ✅ 已做 |
| **Worker 输出 buffer 复用**（ping-pong，免每帧 alloc Float64Array） | 降 GC，百万级有用 | 半天 |
| **服务端 fleet 并行编码**（WriteViewFrame 现在 8 分片串行扫） | 低空裁剪提速 | 半天 |
| **Cesium `requestRenderMode`**（静止时不渲染，省 GPU） | 低空静止时省电 | 1h |
| **WebGL2 切换**（当前强制 WebGL1 兼容 Intel，现代 GPU 上 WebGL2 更快） | 提速，但放弃 Intel 核显兼容 | 需测 |

### B. 视觉 / 交互

| 项 | 价值 | 工作量 |
|---|---|---|
| **告警着色**（已实现 raw 帧） | 蓝/金/红 | ✅ 已做 |
| **低空 Billboard 航向**（已实现） | 带方向箭头 | ✅ 已做 |
| **点击标牌**（已实现） | 经纬高+告警+航向 | ✅ 已做 |
| **选中高亮**（点击后该点放大/描边，再点取消） | 交互完整度 | 1h |
| **轨迹尾迹**（选中无人机画最近 N 秒轨迹线） | 数字孪生核心功能 | 半天-1天 |
| **集群框选**（拖框选中一片无人机，显示统计） | 监管场景常用 | 1-2天 |
| **告警闪烁**（危险级点 pulse 动画） | 视觉警示 | 2h |
| **图例**（蓝/金/红 含义，密度档位说明） | 可读性 | 1h |

### C. 数据 / 业务

| 项 | 价值 | 工作量 |
|---|---|---|
| **接入主项目真实数据**（uav-realtime MQTT→Kafka 替代 Go 仿真器） | 真实数字孪生 | 1-2天 |
| **多源融合**（主项目 uav-track-fusion 的 RTS 平滑接入） | 轨迹质量 | 1-2天 |
| **图传视频接入**（SRS + 告警触发录像） | 主项目设计文档有 | 2-3天 |
| **飞行计划航路**（画审批通过的航线） | 监管可视化 | 1-2天 |
| **历史回放**（TimescaleDB 轨迹回放滑块） | 事后分析 | 2-3天 |

### D. 工程 / 可观测

| 项 | 价值 | 工作量 |
|---|---|---|
| **git 仓库初始化 + .gitignore** | 版本管理基础 | 10min |
| **服务端 metrics**（Prometheus：帧率/带宽/格数/客户端数） | 可观测 | 半天 |
| **前端 perf 面板**（显式画 Worker 耗时/WS 带宽/GPU 占用） | 调优依据 | 半天 |
| **压测脚本**（k6 或自研，自动 ramp 1万→100万记录 FPS） | 回归测试 | 半天 |
| **README 更新**（v5 架构图 + 跑法 + 已知限制） | 交付文档 | 1h |

---

## 当前代码状态快照（下次接手时参考）

- `server/cmd/stress/main.go` — v5，gen 分流 cell/raw（**聚合回退后改回全推 raw**）
- `server/internal/fleet/fleet.go` — 含 WriteCellFrame/WriteViewFrame（聚合分支用）
- `server/internal/wshub/hub.go` — v5，per-client viewport + ReadPump 解析 viewport
- `web/src/worker/telemetry.worker.js` — v5，magic 分流（**回退后只保留 raw 路径**）
- `web/src/composables/useLodRenderer.js` — v4+cell 着色（**回退后删 cellColor/_isCell**）
- `web/src/App.vue` — viewport 上报保留，frame() 解析 isCell

**回退步骤**（下次第一件事）：
1. `main.go` gen 函数：删 cell 分支，只调 `f.WriteViewFrame`（低空）/ 全量 WriteFrame（高空）
   或更简单：高空也用 raw 全量 + Worker 采样（回到 v4 行为）
2. worker 删 MAGIC_CELL 分支
3. renderer 删 `cellColor` / `_isCell` 分支，renderPoints 只留 raw 告警着色
4. 验证：高空满屏采样点云 + 低空视窗裁剪 billboard
