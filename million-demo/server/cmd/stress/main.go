package main

import (
	"flag"
	"log"
	"net/http"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
	"million-demo/server/internal/fleet"
	"million-demo/server/internal/wshub"
)

// 默认 viewport（client 还没上报时用）：高空全局聚合
var defaultVP = fleet.Viewport{Height: 50000,
	MinLat: -90, MaxLat: 90, MinLon: -180, MaxLon: 180}

func main() {
	port := flag.Int("port", 8099, "WebSocket 端口")
	count := flag.Int("count", 1000000, "无人机数量")
	freq := flag.Float64("freq", 5.0, "更新频率 Hz")
	lat := flag.Float64("lat", 39.9042, "中心纬度")
	lon := flag.Float64("lon", 116.4074, "中心经度")
	radius := flag.Float64("radius", 50.0, "活动半径 km")
	full := flag.Bool("full", false, "全量广播模式：忽略视锥，每帧推全部原始点（100万×20B@5Hz≈100MB/s）")
	flag.Parse()

	var fleetPtr atomic.Pointer[fleet.Fleet]
	fleetPtr.Store(fleet.New(*count, *lat, *lon, *radius))

	hub := wshub.NewHub()

	// 帧缓冲池（复用大 buffer，避免每帧分配）
	// cell 帧：16头 + N×16；raw 帧：16头 + N×20。给足 24MB 上限。
	bufPool := &sync.Pool{
		New: func() interface{} { b := make([]byte, 24*1024*1024); return b },
	}

	// 帧生成器：按视野分流（高空聚合 / 低空裁剪）；-full 或客户端请求 full 时整帧推流
	gen := func(vp fleet.Viewport, dst []byte, tsMs int64) int {
		f := fleetPtr.Load()
		if *full || vp.Full {
			f.WriteFrame(dst, tsMs)
			return f.FrameSize()
		}
		// 没上报过视野（Height==0）→ 用默认全局高空
		if vp.Height <= 0 {
			vp = defaultVP
		}
		if vp.IsHigh() {
			return f.WriteCellFrame(dst, tsMs, vp.CellDeg())
		}
		return f.WriteViewFrame(dst, tsMs, vp)
	}

	http.HandleFunc("/stress", func(w http.ResponseWriter, r *http.Request) {
		up := websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
		conn, err := up.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		client := wshub.NewClient(conn)
		hub.Register(client)
		go wshub.WritePump(client, bufPool)
		go wshub.ReadPump(client, func() { hub.Unregister(client); conn.Close() })
		log.Printf("[ws] 连接接入，当前客户端=%d", hub.Count())
	})

	http.HandleFunc("/resize", func(w http.ResponseWriter, r *http.Request) {
		n, err := strconv.Atoi(r.URL.Query().Get("count"))
		if err != nil || n <= 0 {
			http.Error(w, "bad count", 400)
			return
		}
		fleetPtr.Store(fleet.New(n, *lat, *lon, *radius))
		log.Printf("[fleet] 重建为 %d 架", n)
		w.Write([]byte("ok"))
	})

	go func() {
		addr := ":" + strconv.Itoa(*port)
		log.Printf(">>> 监听 %s/stress (viewport-aware: 高空聚合/低空裁剪)", addr)
		log.Fatal(http.ListenAndServe(addr, nil))
	}()

	interval := time.Duration(float64(time.Second) / *freq)
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	var sentBytes int64
	last := time.Now()
	for range ticker.C {
		dt := 1.0 / *freq
		fleetPtr.Load().Update(dt, *radius)

		if hub.Count() == 0 {
			continue
		}
		ts := time.Now().UnixMilli()
		before := atomic.LoadInt64(&sentBytes)
		hub.Broadcast(gen, bufPool, ts)
		// 近似带宽：pool 借出归还，无法精确，用 fleet 帧估算
		_ = before
		if time.Since(last) >= 5*time.Second {
			log.Printf("[stats] 客户端=%d  架数=%d  帧率=%.1fHz  (每client独立viewport帧)",
				hub.Count(), fleetPtr.Load().Count(), *freq)
			sentBytes = 0
			last = time.Now()
		}
	}
}
