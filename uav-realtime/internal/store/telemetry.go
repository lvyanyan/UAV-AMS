package store

import (
	"sync"
	"time"
)

// Snapshot 在飞无人机快照（每 SN 最新一条遥测）
type Snapshot struct {
	SN      string  `json:"sn"`
	Lat     float64 `json:"lat"`
	Lon     float64 `json:"lon"`
	Alt     float64 `json:"alt"`
	Heading float64 `json:"heading"`
	TS      int64   `json:"ts"` // 毫秒
}

// TelemetryStore 内存态遥测仓：
//   latest  每 SN 最新位置（新连接接入即得全量在飞态势）
//   history 环形历史（供「消息重放」按时间窗回放），按窗口时长自动裁剪
type TelemetryStore struct {
	mu     sync.RWMutex
	latest map[string]*Snapshot
	hist   []Snapshot
	window time.Duration
	pruned time.Time
}

func New(window time.Duration) *TelemetryStore {
	return &TelemetryStore{latest: make(map[string]*Snapshot), window: window, pruned: time.Now()}
}

// Record 输入一条遥测（mqtt Telemetry 结构鸭子类型，避免依赖循环）
type Record struct {
	SN      string
	Lat     float64
	Lon     float64
	Alt     float64
	Heading float64
}

func (s *TelemetryStore) Record(r Record) {
	now := time.Now().UnixMilli()
	snap := &Snapshot{SN: r.SN, Lat: r.Lat, Lon: r.Lon, Alt: r.Alt, Heading: r.Heading, TS: now}

	s.mu.Lock()
	defer s.mu.Unlock()
	s.latest[r.SN] = snap
	s.hist = append(s.hist, *snap)
	s.pruneLocked()
}

func (s *TelemetryStore) pruneLocked() {
	// 至少间隔 10s 做一次裁剪，避免每条遥测都扫描
	if time.Since(s.pruned) < 10*time.Second {
		return
	}
	s.pruned = time.Now()
	cutoff := time.Now().Add(-s.window).UnixMilli()
	i := 0
	for ; i < len(s.hist); i++ {
		if s.hist[i].TS >= cutoff {
			break
		}
	}
	if i > 0 {
		s.hist = append([]Snapshot(nil), s.hist[i:]...)
	}
}

// Snapshot 全量在飞无人机
func (s *TelemetryStore) Snapshots() []Snapshot {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]Snapshot, 0, len(s.latest))
	for _, v := range s.latest {
		out = append(out, *v)
	}
	return out
}

// History 最近 seconds 秒的遥测点（时间升序）
func (s *TelemetryStore) History(seconds int) []Snapshot {
	s.mu.RLock()
	defer s.mu.RUnlock()
	cutoff := time.Now().Add(-time.Duration(seconds) * time.Second).UnixMilli()
	i := 0
	for ; i < len(s.hist); i++ {
		if s.hist[i].TS >= cutoff {
			break
		}
	}
	out := make([]Snapshot, 0, len(s.hist)-i)
	out = append(out, s.hist[i:]...)
	return out
}

// Counts 调试用
func (s *TelemetryStore) Counts() (latest int, hist int) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return len(s.latest), len(s.hist)
}
