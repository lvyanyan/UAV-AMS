package press

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"

	"github.com/uav-ams/uav-fleet-press/internal/config"
	"github.com/uav-ams/uav-fleet-press/internal/fleet"
	"github.com/uav-ams/uav-fleet-press/internal/wshub"
)

// Server 百万级二进制聚合通道（端口 8091）。
// 内置 fleet 模拟机群（移植自 million-demo，协议一致：UAVV 原始帧 / USAC 网格聚合帧），
// 按客户端上报的相机视野分流：高空网格聚合 / 低空视野裁剪 / full 全量。
// 与 8090 JSON 遥测链路完全隔离，互不影响。
type Server struct {
	cfg   config.PressConfig
	hub   *wshub.Hub
	fleet atomic.Pointer[fleet.Fleet]

	bufPool *sync.Pool

	upgrader websocket.Upgrader
}

// 默认 viewport（client 还没上报时用）：高空全局聚合
var defaultVP = fleet.Viewport{Height: 50000, MinLat: -90, MaxLat: 90, MinLon: -180, MaxLon: 180}

func NewServer(cfg config.PressConfig) *Server {
	return &Server{
		cfg: cfg,
		hub: wshub.NewHub(),
		// cell 帧：16头 + N×16；raw 帧：16头 + N×20。给足 24MB 上限（100万全量帧 ≈ 20MB）。
		bufPool: &sync.Pool{
			New: func() interface{} { b := make([]byte, 24*1024*1024); return b },
		},
		upgrader: websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }},
	}
}

// Start 启动 HTTP 服务（/fleet WS + /resize + /stats）与帧推送 tick。
func (s *Server) Start(ctx context.Context) {
	mux := http.NewServeMux()
	mux.HandleFunc(s.cfg.Path, s.handleConnection)
	mux.HandleFunc("/resize", s.handleResize)
	mux.HandleFunc("/stats", s.handleStats)

	// 前端(5173) 跨端口 fetch /stats /resize 需要 CORS；WS 靠 Upgrader.CheckOrigin
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		mux.ServeHTTP(w, r)
	})

	addr := fmt.Sprintf(":%d", s.cfg.Port)
	go func() {
		log.Printf("[Press] 百万级二进制通道监听 %s%s （%d 架 @ %.0fHz，中心 %.4f,%.4f 半径 %.0fkm）",
			addr, s.cfg.Path, s.cfg.Count, s.cfg.Freq, s.cfg.Lat, s.cfg.Lon, s.cfg.Radius)
		if err := http.ListenAndServe(addr, handler); err != nil {
			log.Printf("[Press] Server error: %v", err)
		}
	}()

	s.runTick(ctx)
}

func (s *Server) handleConnection(w http.ResponseWriter, r *http.Request) {
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	client := wshub.NewClient(conn)
	s.hub.Register(client)
	go wshub.WritePump(client, s.bufPool)
	go wshub.ReadPump(client, func() { s.hub.Unregister(client); conn.Close() })
	log.Printf("[Press] WS 连接接入，当前客户端=%d", s.hub.Count())
}

// handleResize 运行时调整机群规模：GET /resize?count=500000
func (s *Server) handleResize(w http.ResponseWriter, r *http.Request) {
	n, err := strconv.Atoi(r.URL.Query().Get("count"))
	if err != nil || n <= 0 || n > 5_000_000 {
		http.Error(w, "bad count", 400)
		return
	}
	s.fleet.Store(fleet.New(n, s.cfg.Lat, s.cfg.Lon, s.cfg.Radius))
	log.Printf("[Press] 机群重建为 %d 架", n)
	w.Write([]byte("ok"))
}

// handleStats 在线数查询：GET /stats → {"count":N,"clients":M}
func (s *Server) handleStats(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]int{
		"count":   s.fleet.Load().Count(),
		"clients": s.hub.Count(),
	})
}

// gen 帧生成器：按视野分流（高空聚合 / 低空裁剪）；客户端请求 full 时整帧推流
func (s *Server) gen(vp fleet.Viewport, dst []byte, tsMs int64) int {
	f := s.fleet.Load()
	if vp.Full {
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

// runTick 固定频率推进机群并按各客户端视野广播二进制帧
func (s *Server) runTick(ctx context.Context) {
	s.fleet.Store(fleet.New(s.cfg.Count, s.cfg.Lat, s.cfg.Lon, s.cfg.Radius))

	interval := time.Duration(float64(time.Second) / s.cfg.Freq)
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			// 无客户端时不推进不聚合，百万机群的常驻 CPU 开销只发生在有人观看时
			if s.hub.Count() == 0 {
				continue
			}
			dt := 1.0 / s.cfg.Freq
			s.fleet.Load().Update(dt, s.cfg.Radius)
			s.hub.Broadcast(s.gen, s.bufPool, time.Now().UnixMilli())
		}
	}
}
