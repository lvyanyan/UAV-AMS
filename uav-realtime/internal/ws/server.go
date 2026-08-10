package ws

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"

	"github.com/gorilla/websocket"
	"github.com/uav-ams/uav-realtime/internal/config"
	"github.com/uav-ams/uav-realtime/internal/mqtt"
)

// wsClient 包装 WebSocket 连接，每个连接有独立写锁
type wsClient struct {
	conn    *websocket.Conn
	writeMu sync.Mutex
}

// Server WebSocket 服务器
type Server struct {
	cfg        config.WebSocketConfig
	mqttClient *mqtt.Client
	upgrader   websocket.Upgrader
	clients    map[string]*wsClient
	mu         sync.RWMutex
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

	// 注册遥测处理器：收到 MQTT 遥测后推送到所有 WebSocket 客户端
	mqttClient.OnTelemetry(func(t *mqtt.Telemetry) {
		s.broadcastTelemetry(t)
	})

	return s
}

// Start 启动 HTTP 服务
func (s *Server) Start(ctx context.Context) {
	http.HandleFunc(s.cfg.Path, s.handleConnection)

	addr := fmt.Sprintf(":%d", s.cfg.Port)
	go func() {
		if err := http.ListenAndServe(addr, nil); err != nil {
			log.Printf("[WS] Server error: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("[WS] Server stopped")
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

	wc := &wsClient{conn: conn}

	s.mu.Lock()
	s.clients[clientID] = wc
	s.mu.Unlock()

	log.Printf("[WS] Client connected: %s (total: %d)", clientID, len(s.clients))

	go func() {
		defer func() {
			s.mu.Lock()
			delete(s.clients, clientID)
			s.mu.Unlock()
			conn.Close()
			log.Printf("[WS] Client disconnected: %s", clientID)
		}()

		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				break
			}
			s.handleClientMessage(clientID, msg)
		}
	}()
}

// broadcastTelemetry 广播遥测数据到所有 WebSocket 客户端（每连接独立写锁，避免并发 panic）
func (s *Server) broadcastTelemetry(t *mqtt.Telemetry) {
	data, err := json.Marshal(map[string]interface{}{
		"type": "telemetry",
		"data": t,
	})
	if err != nil {
		return
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	for id, wc := range s.clients {
		wc.writeMu.Lock()
		err := wc.conn.WriteMessage(websocket.TextMessage, data)
		wc.writeMu.Unlock()
		if err != nil {
			log.Printf("[WS] Write error to %s: %v", id, err)
		}
	}
}

func (s *Server) handleClientMessage(clientID string, msg []byte) {
	log.Printf("[WS] Message from %s: %s", clientID, string(msg))
}

// BroadcastJSON 广播自定义 JSON 消息
func (s *Server) BroadcastJSON(v interface{}) {
	data, _ := json.Marshal(v)
	s.mu.RLock()
	defer s.mu.RUnlock()
	for id, wc := range s.clients {
		wc.writeMu.Lock()
		wc.conn.WriteMessage(websocket.TextMessage, data)
		wc.writeMu.Unlock()
		_ = id
	}
}
