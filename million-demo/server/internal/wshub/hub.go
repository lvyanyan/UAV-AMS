package wshub

import (
	"encoding/json"
	"log"
	"sync"
	"time"

	"million-demo/server/internal/fleet"

	"github.com/gorilla/websocket"
)

const (
	writeTimeout = 3 * time.Second
	sendBuf      = 4
)

// FrameGen 给定 client 视野生成一帧 []byte（cell 聚合或 raw 裁剪）
type FrameGen func(vp fleet.Viewport, dst []byte, tsMs int64) int

// Client 一个 WS 连接：独立写 goroutine + 视野 + 有界 channel
type Client struct {
	conn *websocket.Conn
	send chan []byte

	vpMu sync.RWMutex
	vp   fleet.Viewport
}

func (c *Client) Viewport() fleet.Viewport {
	c.vpMu.RLock()
	defer c.vpMu.RUnlock()
	return c.vp
}

func (c *Client) setViewport(vp fleet.Viewport) {
	c.vpMu.Lock()
	c.vp = vp
	c.vpMu.Unlock()
}

// Hub 连接池。Broadcast 时为每个 client 按其视野生成独立帧。
type Hub struct {
	mu      sync.RWMutex
	clients map[*Client]struct{}
}

func NewHub() *Hub {
	return &Hub{clients: make(map[*Client]struct{})}
}

func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	h.clients[c] = struct{}{}
	h.mu.Unlock()
}

func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	if _, ok := h.clients[c]; ok {
		delete(h.clients, c)
		close(c.send)
	}
	h.mu.Unlock()
}

func (h *Hub) Count() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

// Broadcast 按每个 client 的视野生成帧并投递（高空 cell 聚合 / 低空 bbox 裁剪）。
// gen: 帧生成器；bufPool: 复用的缓冲区；满则丢帧保连接。
func (h *Hub) Broadcast(gen FrameGen, bufPool *sync.Pool, tsMs int64) {
	h.mu.RLock()
	for c := range h.clients {
		vp := c.Viewport()
		buf := bufPool.Get().([]byte)
		n := gen(vp, buf, tsMs)
		if n <= 0 {
			bufPool.Put(buf)
			continue
		}
		out := buf[:n]
		select {
		case c.send <- out:
		default:
			bufPool.Put(buf) // 慢客户端丢帧
		}
	}
	h.mu.RUnlock()
}

func WritePump(c *Client, bufPool *sync.Pool) {
	defer c.conn.Close()
	for buf := range c.send {
		_ = c.conn.SetWriteDeadline(time.Now().Add(writeTimeout))
		if err := c.conn.WriteMessage(websocket.BinaryMessage, buf); err != nil {
			bufPool.Put(buf)
			return
		}
		bufPool.Put(buf) // 写完归还
	}
}

// ReadPump 排空入站 + 解析 client 上报的 viewport
func ReadPump(c *Client, onClose func()) {
	defer onClose()
	for {
		_, msg, err := c.conn.ReadMessage()
		if err != nil {
			return
		}
		// 尝试解析为 viewport 上报
		var vp fleet.Viewport
		if json.Unmarshal(msg, &vp) == nil && vp.MaxLat >= vp.MinLat {
			c.setViewport(vp)
		} else {
			log.Printf("[ws] 无法解析 viewport: %s", string(msg))
		}
	}
}

func NewClient(conn *websocket.Conn) *Client {
	return &Client{conn: conn, send: make(chan []byte, sendBuf)}
}
