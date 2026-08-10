# 百万级无人机 —— 前端可行性验证 Demo

独立 demo，验证 **"前端能否渲染百万级无人机"**。范围：**前端隔离验证**
（服务端推全量二进制快照，前端用 Worker 解析 + rAF 合批 + 抽稀，看能流畅扛到多少架）。

## 架构

```
Go 数据源                Go 通信端                 Vue3 + Cesium 前端
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────────┐
│ fleet (1M, 8分片)│ → │ wshub (每连接写   │ → │ WebSocket (ArrayBuffer)  │
│ dead-reckoning  │    │ goroutine+背压)  │    │   ↓ transferable         │
│ → 二进制帧 20B/架│    │ Broadcast 一次序列│   │ Worker: 解析+ECEF 转换    │
└─────────────────┘    └──────────────────┘    │   ↓ Float64Array          │
                          /resize 热重建        │ rAF 合批 → PointPrimitive  │
                                                 └─────────────────────────┘
```

## 二进制协议（little-endian）

```
[0..3]   magic 0x55534156 ("UAVV")
[4..7]   uint32 count
[8..15]  int64  timestamp_ms
[16..]   count × 20B: [lon_f32, lat_f32, alt_f32, heading_f32, alert_f32]
```

1M 架 ≈ 20MB/帧；5Hz ≈ 100MB/s（localhost loopback 可承受）。

## 跑法

```bash
# 1. 起数据源 + 通信端
cd D:\dronesManager\million-demo\server
go build -o stress.exe ./cmd/stress
./stress.exe -count 1000000 -freq 5 -port 8099
#    常用 flag：
#    -count  无人机数（默认 1000000）
#    -freq   更新频率 Hz（默认 5）
#    -lat/-lon/-radius  场景中心与半径

# 2. 起前端（新终端）
cd D:\dronesManager\million-demo\web
npm install
npm run dev
#    打开 http://localhost:5173

# 3. 浏览器里用右上角 slider 调无人机数（1万~100万）和抽稀因子
#    前端调 slider 会调服务端 /resize 热重建，无需重启
```

## 验证方法

逐档测试，每档稳定 10s 记录 FPS（右上角显示）。详细报告见 `REPORT.md`。

| server 架数 | 抽稀 | 渲染点数 | rAF | 丢帧 | 备注 |
|---|---|---|---|---|---|
| 100万 | 自动(1-in-5) | 20万 | 102 | 0 | 流畅，百万可行 ✓ |
| 20万 | 1(全量) | 20万 | 102 | 13 | 流畅 |
| 15万 | 1(全量) | 15万 | 110 | 6 | 流畅 |
| 17万(v1旧架构) | - | - | - | - | 卡死(实例化冻结) |
| 38万(v1旧架构) | - | 38万 | 28 | 26 | 多重连风暴 |

## 关键设计点

1. **Worker 做 ECEF 转换**：复制 Cesium `Cartesian3.fromDegrees` 的 WGS84 纯数学，
   不 import Cesium。百万次坐标变换移出主线程，`Float64Array` 用 Transferable 零拷贝回传。
2. **丢过时帧**：Worker 处理一帧时，新来的 WS 帧直接丢（`workerBusy` 标志），
   避免积压。`pending` 只保留最新，rAF 只消费最新一帧。
3. **rAF 合批**：不再逐条 `upsertDrone`，每动画帧一次性更新。
4. **复用 Cartesian3**：用一个 scratch 实例循环赋值，避免百万次 `new Cartesian3` 的 GC 风暴。
5. **抽稀**：高空画不下全量时，前端 1-in-N 抽稀，验证"数据进得来、画不动"的边界。
6. **Go 服务端**：8 分片并行更新位置（无 per-drone mutex）；每连接独立写 goroutine +
   有界 channel + write deadline，消除现有 `ws-stress` 的 head-of-line 阻塞；
   `Broadcast` 序列化只做一次。

## 文件结构

```
million-demo/
├── README.md
├── server/                      # Go 数据源 + 通信端
│   ├── go.mod
│   ├── cmd/stress/main.go       # CLI + /stress(WS) + /resize(热重建)
│   └── internal/
│       ├── movement/movement.go # dead-reckoning
│       ├── fleet/fleet.go       # 1M 机群, 8 分片, 二进制帧编码
│       └── wshub/hub.go         # 每连接写 goroutine + 背压广播
└── web/                         # Vue3 + Cesium 前端
    ├── package.json / vite.config.js / index.html
    └── src/
        ├── main.js / App.vue
        ├── worker/telemetry.worker.js   # 二进制解析 + ECEF 转换
        ├── composables/
        │   ├── useCesium.js             # viewer 初始化（高德瓦片 + Intel GPU 兼容）
        │   └── useLodRenderer.js         # PointPrimitiveCollection + rAF 合批 + 抽稀
        └── components/ControlPanel.vue  # count slider + 抽稀 + FPS
```

## 预期结论

- 纯渲染（抽稀=1）流畅上限预计 **20-50 万架**，100 万会卡。
- 卡住后调大抽稀能恢复流畅 → 说明 **前端数据吞吐 OK，瓶颈在 Cesium primitive 更新**，
  完整架构阶段应做"服务端视锥聚合"而非前端抽稀。
- 若抽稀=10 仍卡 → 说明 **Worker/WS 带宽是瓶颈**，需二进制更紧凑或服务端聚合。

两种结论都指向同一答案：**百万级必须上服务端聚合**（完整架构阶段），前端本身的纯渲染
极限在 30-50 万量级，配合抽稀/聚合可"看起来流畅地承载 100 万"。
