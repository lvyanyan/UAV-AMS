package ws

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
	"github.com/uav-ams/uav-realtime/internal/config"
	"github.com/uav-ams/uav-realtime/internal/mqtt"
	"github.com/uav-ams/uav-realtime/internal/store"
)

// ===================== 订阅协议（v2） =====================
//
// 客户端连接后可发送 subscribe 控制消息声明订阅条件（可重复发送更新）：
//
//	{
//	  "type": "subscribe",
//	  "viewport": {"minLat":39.0,"maxLat":40.5,"minLon":116.0,"maxLon":117.5,"camAlt":8000},
//	  "alarm": {"minLevel":"SERIOUS","excludeTypes":["WEATHER_RISK"],"excludeSns":[],"viewportScoped":true},
//	  "batch": true
//	}
//
//   - viewport 视锥包围盒 + 相机高度：设置后仅推送视野内遥测（bbox 裁剪）；
//     camAlt 供服务端后续 LOD 决策预留
//   - alarm    告警订阅条件（抑制式过滤，与告警引擎生成侧抑制语义一致）：
//     minLevel 最低级别阈值、excludeLevels/excludeTypes/excludeSns 黑名单、
//     viewportScoped 仅推视野内告警
//   - batch    true 时遥测合帧：服务端把积压遥测合并为 telemetry-batch 批量帧，
//     显著降低帧率与序列化开销
//
// 服务端回执 {"type":"subscribe-ack","data":<当前生效订阅>}。
// 未发送 subscribe 的客户端保持 v1 行为：全量逐条广播（向后兼容旧消费者）。
// REST /snapshot、/history 支持 minLat/maxLat/minLon/maxLon 查询参数做服务端裁剪。

// Viewport 客户端视锥（经纬度包围盒 + 相机高度）
type Viewport struct {
	MinLat float64 `json:"minLat"`
	MaxLat float64 `json:"maxLat"`
	MinLon float64 `json:"minLon"`
	MaxLon float64 `json:"maxLon"`
	CamAlt float64 `json:"camAlt"` // 相机高度(m)
}

func (v *Viewport) contains(lat, lon float64) bool {
	return lat >= v.MinLat && lat <= v.MaxLat && lon >= v.MinLon && lon <= v.MaxLon
}

// AlarmSub 告警订阅条件（抑制式过滤：命中黑名单的告警不推给该客户端）
type AlarmSub struct {
	MinLevel       string   `json:"minLevel"`       // 最低级别阈值 GENERAL < SERIOUS < CRITICAL，空=不限
	ExcludeLevels  []string `json:"excludeLevels"`  // 级别黑名单
	ExcludeTypes   []string `json:"excludeTypes"`   // 类型黑名单
	ExcludeSns     []string `json:"excludeSns"`     // 无人机 SN 黑名单
	ViewportScoped bool     `json:"viewportScoped"` // 仅推视野内告警
}

// Subscription 客户端订阅状态
type Subscription struct {
	Viewport *Viewport `json:"viewport"` // nil = 不裁剪（全量）
	Alarm    AlarmSub  `json:"alarm"`
	Batch    bool      `json:"batch"` // 遥测合帧
}

func levelRank(l string) int {
	switch l {
	case "SERIOUS":
		return 1
	case "CRITICAL":
		return 2
	default:
		return 0
	}
}

// eventMeta 告警/冲突事件中与订阅过滤相关的字段（宽松解析，缺字段不影响推送）
type eventMeta struct {
	Latitude   *float64 `json:"latitude"`
	Longitude  *float64 `json:"longitude"`
	AlarmLevel string   `json:"alarmLevel"`
	AlarmType  string   `json:"alarmType"`
	DroneSn    string   `json:"droneSn"`
}

// ===================== 客户端 =====================

// outMsg 出站消息：kind 't'=遥测（可合帧）'e'=事件/控制帧
type outMsg struct {
	kind    byte
	payload []byte
}

const (
	sendBufferCap  = 256          // 每客户端发送队列容量，满则丢帧保连接
	writeTimeout   = 3 * time.Second
	maxBatchFrames = 64           // 单个 telemetry-batch 最大条数
)

// wsClient 单个 WebSocket 客户端会话：订阅状态 + 独立发送队列 + 独立写泵。
// 慢客户端只丢自己的帧，不会阻塞其他客户端的推送。
type wsClient struct {
	id        string
	conn      *websocket.Conn
	send      chan outMsg
	subMu     sync.RWMutex
	sub       Subscription
	dropped   uint64 // 因队列满被丢弃的帧数
	closeCh   chan struct{}
	closeOnce sync.Once
}

func (c *wsClient) enqueue(m outMsg) {
	select {
	case c.send <- m:
	default:
		atomic.AddUint64(&c.dropped, 1)
	}
}

func (c *wsClient) close() {
	c.closeOnce.Do(func() { close(c.closeCh) })
}

func (c *wsClient) subscription() Subscription {
	c.subMu.RLock()
	defer c.subMu.RUnlock()
	return c.sub
}

// writePump 独立写泵：逐帧写出，batch 开启时把连续遥测合并为 telemetry-batch。
// 每次写都带 WriteDeadline，坏连接快速失败并关闭，不拖累广播循环。
func (c *wsClient) writePump() {
	defer c.conn.Close()
	for {
		var m outMsg
		select {
		case m = <-c.send:
		case <-c.closeCh:
			return
		}
		if err := c.writeOne(m); err != nil {
			return
		}
	}
}

func (c *wsClient) writeOne(m outMsg) error {
	if m.kind == 't' && c.subscription().Batch {
		items := []json.RawMessage{json.RawMessage(m.payload)}
	drain:
		for len(items) < maxBatchFrames {
			select {
			case n := <-c.send:
				if n.kind == 't' {
					items = append(items, json.RawMessage(n.payload))
				} else {
					if err := c.flushBatch(items); err != nil {
						return err
					}
					return c.sendFrame(n.payload)
				}
			default:
				break drain
			}
		}
		return c.flushBatch(items)
	}
	return c.sendFrame(m.payload)
}

func (c *wsClient) flushBatch(items []json.RawMessage) error {
	frame, err := json.Marshal(map[string]interface{}{
		"type":  "telemetry-batch",
		"count": len(items),
		"data":  items,
	})
	if err != nil {
		return nil
	}
	return c.sendFrame(frame)
}

func (c *wsClient) sendFrame(payload []byte) error {
	c.conn.SetWriteDeadline(time.Now().Add(writeTimeout))
	return c.conn.WriteMessage(websocket.TextMessage, payload)
}

// ===================== Server =====================

// Server WebSocket 服务器
type Server struct {
	cfg        config.WebSocketConfig
	mqttClient *mqtt.Client
	upgrader   websocket.Upgrader
	clients    map[string]*wsClient
	mu         sync.RWMutex
	store      *store.TelemetryStore
}

// NewServer 创建 WebSocket 服务
func NewServer(cfg config.WebSocketConfig, mqttClient *mqtt.Client) *Server {
	s := &Server{
		cfg:        cfg,
		mqttClient: mqttClient,
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
		clients: make(map[string]*wsClient),
	}

	// 注册遥测处理器：收到 MQTT 遥测后按各客户端订阅条件推送
	mqttClient.OnTelemetry(func(t *mqtt.Telemetry) {
		s.dispatchTelemetry(t)
	})

	return s
}

// SetTelemetryStore 挂载遥测内存仓（在飞快照 / 历史回放）
func (s *Server) SetTelemetryStore(store *store.TelemetryStore) { s.store = store }

// Start 启动 HTTP 服务
func (s *Server) Start(ctx context.Context) {
	http.HandleFunc(s.cfg.Path, s.handleConnection)
	// 在飞快照：新连接接入即得全量在飞态势；支持 bbox 查询参数做服务端裁剪
	http.HandleFunc("/snapshot", func(w http.ResponseWriter, r *http.Request) {
		if s.store == nil {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "application/json")
		snaps := s.store.Snapshots()
		if vp, ok := viewportFromQuery(r); ok {
			filtered := snaps[:0]
			for _, sn := range snaps {
				if vp.contains(sn.Lat, sn.Lon) {
					filtered = append(filtered, sn)
				}
			}
			snaps = filtered
		}
		json.NewEncoder(w).Encode(map[string]interface{}{"count": len(snaps), "drones": snaps})
	})
	// 历史遥测：/history?seconds=600&minLat=&maxLat=&minLon=&maxLon=（供「消息重放」页查询回放）
	http.HandleFunc("/history", func(w http.ResponseWriter, r *http.Request) {
		if s.store == nil {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "application/json")
		sec, err := strconv.Atoi(r.URL.Query().Get("seconds"))
		if err != nil || sec <= 0 || sec > 1800 {
			sec = 600
		}
		msgs := s.store.History(sec)
		if vp, ok := viewportFromQuery(r); ok {
			filtered := msgs[:0]
			for _, sn := range msgs {
				if vp.contains(sn.Lat, sn.Lon) {
					filtered = append(filtered, sn)
				}
			}
			msgs = filtered
		}
		json.NewEncoder(w).Encode(map[string]interface{}{"seconds": sec, "count": len(msgs), "msgs": msgs})
	})
	// 连接诊断：各客户端订阅状态与丢帧计数
	http.HandleFunc("/wsstats", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "application/json")
		s.mu.RLock()
		defer s.mu.RUnlock()
		type cliStat struct {
			ID      string       `json:"id"`
			Sub     Subscription `json:"sub"`
			Pending int          `json:"pending"`
			Dropped uint64       `json:"dropped"`
		}
		stats := make([]cliStat, 0, len(s.clients))
		for _, c := range s.clients {
			stats = append(stats, cliStat{
				ID:      c.id,
				Sub:     c.subscription(),
				Pending: len(c.send),
				Dropped: atomic.LoadUint64(&c.dropped),
			})
		}
		json.NewEncoder(w).Encode(map[string]interface{}{"clients": len(s.clients), "list": stats})
	})

	addr := fmt.Sprintf(":%d", s.cfg.Port)
	go func() {
		if err := http.ListenAndServe(addr, nil); err != nil {
			log.Printf("[WS] Server error: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("[WS] Server stopped")
}

func viewportFromQuery(r *http.Request) (Viewport, bool) {
	q := r.URL.Query()
	get := func(k string) (float64, bool) {
		v, err := strconv.ParseFloat(q.Get(k), 64)
		if err != nil {
			return 0, false
		}
		return v, true
	}
	minLat, ok1 := get("minLat")
	maxLat, ok2 := get("maxLat")
	minLon, ok3 := get("minLon")
	maxLon, ok4 := get("maxLon")
	if !ok1 || !ok2 || !ok3 || !ok4 {
		return Viewport{}, false
	}
	return Viewport{MinLat: minLat, MaxLat: maxLat, MinLon: minLon, MaxLon: maxLon}, true
}

func (s *Server) handleConnection(w http.ResponseWriter, r *http.Request) {
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[WS] Upgrade error: %v", err)
		return
	}

	clientID := r.URL.Query().Get("clientId")
	if clientID == "" {
		clientID = r.RemoteAddr
	}

	// 默认订阅 = v1 行为：无视锥、无告警过滤、不合帧（全量广播，向后兼容）
	wc := &wsClient{
		id:      clientID,
		conn:    conn,
		send:    make(chan outMsg, sendBufferCap),
		closeCh: make(chan struct{}),
	}

	s.mu.Lock()
	s.clients[clientID] = wc
	s.mu.Unlock()

	log.Printf("[WS] Client connected: %s (total: %d)", clientID, len(s.clients))

	go wc.writePump()
	go func() {
		defer s.removeClient(wc)
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			s.handleClientMessage(wc, msg)
		}
	}()
}

func (s *Server) removeClient(wc *wsClient) {
	s.mu.Lock()
	if cur, ok := s.clients[wc.id]; ok && cur == wc {
		delete(s.clients, wc.id)
	}
	s.mu.Unlock()
	wc.close()
	wc.conn.Close()
	if d := atomic.LoadUint64(&wc.dropped); d > 0 {
		log.Printf("[WS] Client %s disconnected (dropped frames: %d)", wc.id, d)
	} else {
		log.Printf("[WS] Client disconnected: %s", wc.id)
	}
}

// dispatchTelemetry 遥测分发：先整体序列化一次，再按客户端视锥过滤入队
func (s *Server) dispatchTelemetry(t *mqtt.Telemetry) {
	data, err := json.Marshal(map[string]interface{}{
		"type": "telemetry",
		"data": t,
	})
	if err != nil {
		return
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	for _, wc := range s.clients {
		sub := wc.subscription()
		if sub.Viewport != nil && !sub.Viewport.contains(t.Position.Lat, t.Position.Lon) {
			continue // 视锥裁剪：视野外遥测不入队
		}
		wc.enqueue(outMsg{kind: 't', payload: data})
	}
}

// controlMsg 客户端 -> 服务端控制消息
type controlMsg struct {
	Type     string    `json:"type"`
	Viewport *Viewport `json:"viewport"`
	Alarm    *AlarmSub `json:"alarm"`
	Batch    *bool     `json:"batch"`
}

func (s *Server) handleClientMessage(wc *wsClient, msg []byte) {
	var cm controlMsg
	if err := json.Unmarshal(msg, &cm); err != nil || cm.Type == "" {
		log.Printf("[WS] Ignored message from %s: %.120s", wc.id, msg)
		return
	}

	switch cm.Type {
	case "subscribe":
		sub := wc.subscription()
		if cm.Viewport != nil {
			cp := *cm.Viewport
			sub.Viewport = &cp
		}
		if cm.Alarm != nil {
			sub.Alarm = *cm.Alarm
		}
		if cm.Batch != nil {
			sub.Batch = *cm.Batch
		}
		wc.subMu.Lock()
		wc.sub = sub
		wc.subMu.Unlock()

		log.Printf("[WS] %s subscribed: viewport=%v alarm.minLevel=%q scoped=%v batch=%v",
			wc.id, sub.Viewport != nil, sub.Alarm.MinLevel, sub.Alarm.ViewportScoped, sub.Batch)
		if ack, err := json.Marshal(map[string]interface{}{"type": "subscribe-ack", "data": sub}); err == nil {
			wc.enqueue(outMsg{kind: 'e', payload: ack})
		}
	case "ping":
		wc.enqueue(outMsg{kind: 'e', payload: []byte(`{"type":"pong"}`)})
	default:
		log.Printf("[WS] Unknown control type %q from %s", cm.Type, wc.id)
	}
}

// BroadcastJSON 广播自定义 JSON 消息（全量，不经过订阅过滤，保留给特殊场景）
func (s *Server) BroadcastJSON(v interface{}) {
	data, _ := json.Marshal(v)
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, wc := range s.clients {
		wc.enqueue(outMsg{kind: 'e', payload: data})
	}
}

// BroadcastEvent 广播 Kafka 事件（告警/冲突/解脱），按客户端订阅条件过滤：
// uav.alarm.event 受 minLevel/types 约束；开启 viewportScoped 的客户端仅收视野内事件。
func (s *Server) BroadcastEvent(topic string, payload []byte) {
	var meta eventMeta
	_ = json.Unmarshal(payload, &meta)
	lvl := levelRank(meta.AlarmLevel)

	env, err := json.Marshal(map[string]interface{}{
		"type": topic,
		"data": json.RawMessage(payload),
		"ts":   time.Now().UnixMilli(),
	})
	if err != nil {
		return
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	for _, wc := range s.clients {
		sub := wc.subscription()
		ok := true
		if topic == "uav.alarm.event" {
			if sub.Alarm.MinLevel != "" && lvl < levelRank(sub.Alarm.MinLevel) {
				ok = false
			}
			if ok && containsStr(sub.Alarm.ExcludeLevels, meta.AlarmLevel) {
				ok = false
			}
			if ok && meta.AlarmType != "" && containsStr(sub.Alarm.ExcludeTypes, meta.AlarmType) {
				ok = false
			}
			if ok && containsStr(sub.Alarm.ExcludeSns, meta.DroneSn) {
				ok = false
			}
		}
		if ok && sub.Alarm.ViewportScoped && sub.Viewport != nil &&
			meta.Latitude != nil && meta.Longitude != nil &&
			!sub.Viewport.contains(*meta.Latitude, *meta.Longitude) {
			ok = false
		}
		if ok {
			wc.enqueue(outMsg{kind: 'e', payload: env})
		}
	}
}

func containsStr(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}
