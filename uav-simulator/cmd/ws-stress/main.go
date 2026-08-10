package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"math"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
)

// -------------------- 配置 --------------------
var (
	port       = flag.Int("port", 8099, "WebSocket 监听端口")
	droneCount = flag.Int("count", 50000, "无人机数量 (默认5万)")
	freqHz     = flag.Float64("freq", 5.0, "发送频率 (Hz)")
	centerLat  = flag.Float64("lat", 39.9042, "场景中心纬度")
	centerLon  = flag.Float64("lon", 116.4074, "场景中心经度")
	radiusKm   = flag.Float64("radius", 50.0, "活动半径 (km)")
)

// -------------------- 紧凑遥测格式 --------------------
// [lon, lat, alt, heading, alertCode]
// alertCode: 0=正常 1=警告 2=危险
type CompactTelemetry struct {
	T int64       `json:"t"` // 时间戳 ms
	P [][5]float64 `json:"p"`
	C int         `json:"c"` // 本次条数
}

// -------------------- 轻量级无人机状态 --------------------
type LightDrone struct {
	mu                 sync.Mutex
	lon, lat           float64
	alt                float64
	heading            float64
	alertCode          int // 0=正常 1=警告 2=危险
	speedMs            float64
	homeLat, homeLon   float64
	rngSeed            int64
}

// -------------------- WS 连接管理 --------------------
var (
	clients   = make(map[*websocket.Conn]bool)
	clientsMu sync.Mutex
	upgrader  = websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool { return true },
	}
)

// -------------------- 主函数 --------------------
func main() {
	flag.Parse()

	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)
	log.Printf("🛰️ WebSocket 压力测试服务启动...")
	log.Printf("   无人机: %d | 频率: %.1fHz | 端口: %d | 半径: %.0fkm",
		*droneCount, *freqHz, *port, *radiusKm)

	// 创建无人机群
	drones := createFleet(*droneCount, *centerLat, *centerLon, *radiusKm)
	log.Printf("✅ %d 架无人机已创建", len(drones))

	// WebSocket 端点
	http.HandleFunc("/stress", func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Printf("⚠️ WS 升级失败: %v", err)
			return
		}
		clientsMu.Lock()
		clients[conn] = true
		clientsMu.Unlock()
		log.Printf("🔗 新客户端 (总数: %d)", len(clients))

		go func() {
			defer func() {
				clientsMu.Lock()
				delete(clients, conn)
				clientsMu.Unlock()
				conn.Close()
				log.Printf("🔌 客户端断开 (剩余: %d)", len(clients))
			}()
			for {
				if _, _, err := conn.ReadMessage(); err != nil {
					break
				}
			}
		}()
	})

	// 定时发送
	interval := time.Duration(float64(time.Second) / *freqHz)
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	statsTicker := time.NewTicker(5 * time.Second)
	defer statsTicker.Stop()

	var sentCount int64
	var lastSentCount int64
	startTime := time.Now()

	go func() {
		for range statsTicker.C {
			now := time.Now()
			elapsed := now.Sub(startTime).Seconds()
			avgRate := float64(sentCount) / elapsed
			currentRate := float64(sentCount-lastSentCount) / 5.0
			lastSentCount = sentCount
			log.Printf("📊 发送: %d | %.0f条/s | 平均:%.0f条/s | %.0fs | 连接:%d",
				sentCount, currentRate, avgRate, elapsed, len(clients))
		}
	}()

	go func() {
		addr := fmt.Sprintf("0.0.0.0:%d", *port)
		log.Printf("🌐 监听 %s/stress", addr)
		if err := http.ListenAndServe(addr, nil); err != nil {
			log.Fatalf("❌ 启动失败: %v", err)
		}
	}()

	// 主循环
	for range ticker.C {
		if len(clients) == 0 {
			// 没客户端也要更新位置，不然连上来时位置是错的
			updatePositions(drones, 1.0/ *freqHz)
			continue
		}

		// 收集位置
		data := make([][5]float64, len(drones))
		for i, d := range drones {
			d.mu.Lock()
			data[i] = [5]float64{
				math.Round(d.lon*1e6) / 1e6,
				math.Round(d.lat*1e6) / 1e6,
				math.Round(d.alt*10) / 10,
				math.Round(d.heading*10) / 10,
				float64(d.alertCode),
			}
			d.mu.Unlock()
		}

		payload, err := json.Marshal(CompactTelemetry{
			T: time.Now().UnixMilli(),
			P: data,
			C: len(data),
		})
		if err != nil {
			log.Printf("❌ JSON 失败: %v", err)
			updatePositions(drones, 1.0/ *freqHz)
			continue
		}

		// 广播
		clientsMu.Lock()
		for c := range clients {
			if err := c.WriteMessage(websocket.TextMessage, payload); err != nil {
				c.Close()
				delete(clients, c)
			}
		}
		clientsMu.Unlock()

		sentCount += int64(len(drones))
		updatePositions(drones, 1.0/ *freqHz)
	}

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	log.Println("👋 关闭")
}

// -------------------- 创建无人机群 --------------------
func createFleet(count int, centerLat, centerLon, radiusKm float64) []*LightDrone {
	drones := make([]*LightDrone, count)
	cosCenterLat := math.Cos(centerLat * math.Pi / 180.0)

	for i := 0; i < count; i++ {
		// 黄金角度螺旋分布，让无人机均匀散布在圆盘上
		angle := float64(i) * 2 * math.Pi * 1.618
		ratio := math.Sqrt(float64(i) / float64(count))
		distKm := ratio * radiusKm * 0.95

		latOffset := distKm / 111.0 * math.Cos(angle)
		lonOffset := distKm / (111.0 * cosCenterLat) * math.Sin(angle)

		alt := 50.0 + float64(i%250)*1.0

		drones[i] = &LightDrone{
			lon:      centerLon + lonOffset,
			lat:      centerLat + latOffset,
			alt:      alt,
			heading:  float64(i) * 0.73,
			speedMs:  5.0 + float64(i%15),
			homeLat:  centerLat + latOffset,
			homeLon:  centerLon + lonOffset,
			rngSeed:  int64(i * 137),
			alertCode: func(i int) int {
				if i%100 == 0 { return 2 }   // 1% 危险
				if i%20 == 0  { return 1 }   // 5% 警告
				return 0
			}(i),
		}
	}
	return drones
}

// -------------------- 更新位置（圆周运动） --------------------
func updatePositions(drones []*LightDrone, dt float64) {
	for _, d := range drones {
		d.mu.Lock()

		speed := d.speedMs * dt
		cosLat := math.Cos(d.lat * math.Pi / 180.0)

		d.lon += speed * math.Sin(d.heading*math.Pi/180.0) / (111000.0 * cosLat)
		d.lat += speed * math.Cos(d.heading*math.Pi/180.0) / 111000.0

		d.heading += 0.5 * dt * (float64(d.rngSeed%7) - 3)
		if d.heading < 0 { d.heading += 360 }
		if d.heading >= 360 { d.heading -= 360 }

		// 高度波动
		now := float64(time.Now().UnixNano()%1000000) / 100000.0
		d.alt += math.Sin(now+float64(d.rngSeed)) * 2 * dt

		// 半径限制
		dlon := d.lon - d.homeLon
		dlat := d.lat - d.homeLat
		distKm := math.Sqrt(dlon*dlon*cosLat*cosLat*111000*111000 + dlat*dlat*111000*111000) / 1000.0
		if distKm > 50.0 {
			d.heading += 180
			if d.heading >= 360 { d.heading -= 360 }
		}

		d.mu.Unlock()
	}
}
