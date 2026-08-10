package drone

import (
	"math/rand"
	"sync"
	"time"
)

// FlightPhase 飞行阶段
type FlightPhase string

const (
	PhaseIdle       FlightPhase = "IDLE"        // 待命
	PhaseTakeoff    FlightPhase = "TAKEOFF"      // 起飞中
	PhaseFlying     FlightPhase = "FLYING"       // 飞行中
	PhaseReturning  FlightPhase = "RETURNING"    // 返航中
	PhaseLanding    FlightPhase = "LANDING"      // 降落中
	PhaseLanded     FlightPhase = "LANDED"       // 已降落
	PhaseEmergency  FlightPhase = "EMERGENCY"    // 紧急状态
)

// ViolationType 违规类型
type ViolationType string

const (
	ViolationNone          ViolationType = "NONE"
	ViolationNoFlyZone     ViolationType = "NO_FLY_ZONE_INTRUSION"
	ViolationAltitude      ViolationType = "ALTITUDE_EXCEED"
	ViolationSpeed         ViolationType = "SPEED_EXCEED"
	ViolationNoPlan        ViolationType = "NO_PLAN_FLIGHT"
	ViolationGeoFence      ViolationType = "GEO_FENCE_BREACH"
	ViolationSignalLost    ViolationType = "SIGNAL_LOST"
)

// DroneModel 机型信息
type DroneModel struct {
	Name           string  // 型号名称
	Manufacturer   string  // 制造商
	WeightG        float64 // 重量 (g)
	MaxSpeedMs     float64 // 最大速度
	MaxAltM        float64 // 最大高度
	MaxFlightMin   int     // 最长飞行时间
	WindResistMs   float64 // 抗风等级
}

// 预置机型库
var ModelLibrary = map[string]DroneModel{
	"DJI-M350":    {Name: "Matrice 350 RTK", Manufacturer: "DJI", WeightG: 9200, MaxSpeedMs: 23, MaxAltM: 7000, MaxFlightMin: 55, WindResistMs: 15},
	"DJI-M300":    {Name: "Matrice 300 RTK", Manufacturer: "DJI", WeightG: 6300, MaxSpeedMs: 23, MaxAltM: 7000, MaxFlightMin: 55, WindResistMs: 15},
	"DJI-M30T":    {Name: "Matrice 30T", Manufacturer: "DJI", WeightG: 3770, MaxSpeedMs: 23, MaxAltM: 7000, MaxFlightMin: 41, WindResistMs: 15},
	"DJI-MAVIC3":  {Name: "Mavic 3", Manufacturer: "DJI", WeightG: 895, MaxSpeedMs: 21, MaxAltM: 6000, MaxFlightMin: 46, WindResistMs: 12},
	"DJI-MINI4P":  {Name: "Mini 4 Pro", Manufacturer: "DJI", WeightG: 249, MaxSpeedMs: 16, MaxAltM: 4000, MaxFlightMin: 34, WindResistMs: 10.7},
	"DJI-AIR3S":   {Name: "Air 3S", Manufacturer: "DJI", WeightG: 720, MaxSpeedMs: 21, MaxAltM: 6000, MaxFlightMin: 46, WindResistMs: 12},
	"DJI-FLYCART30": {Name: "FlyCart 30", Manufacturer: "DJI", WeightG: 25000, MaxSpeedMs: 20, MaxAltM: 6000, MaxFlightMin: 28, WindResistMs: 12},
	"CUSTOM-C300": {Name: "Cargo 300", Manufacturer: "Custom", WeightG: 15000, MaxSpeedMs: 18, MaxAltM: 3000, MaxFlightMin: 25, WindResistMs: 10},
}

// Position 位置信息
type Position struct {
	Lat     float64 `json:"lat"`
	Lon     float64 `json:"lon"`
	AltM    float64 `json:"alt_m"`
	Heading float64 `json:"heading"` // 0-360
}

// Telemetry 遥测数据
type Telemetry struct {
	DeviceSN      string        `json:"device_sn"`
	Model         string        `json:"model"`
	Timestamp     int64         `json:"timestamp"`       // Unix ms
	Position      Position      `json:"position"`
	SpeedMs       float64       `json:"speed_ms"`
	BatteryPct    float64       `json:"battery_pct"`     // 0-100
	SignalRSSI    int           `json:"signal_rssi"`     // dBm
	SatelliteCnt  int           `json:"satellite_cnt"`   // GPS 卫星数
	FlightPhase   FlightPhase   `json:"flight_phase"`
	FlightPlanID  string        `json:"flight_plan_id"`
	Violation     ViolationType `json:"violation,omitempty"`
	WindSpeedMs   float64       `json:"wind_speed_ms"`
	TemperatureC  float64       `json:"temperature_c"`
}

// Drone 无人机实例
type Drone struct {
	mu sync.Mutex

	DeviceSN    string
	Model       string
	FlightPhase FlightPhase
	Position    Position
	SpeedMs     float64
	BatteryPct  float64
	SignalRSSI  int

	// 飞行路径
	homePos     Position      // 起飞点
	waypoints   []Position    // 航点列表
	currentWP   int           // 当前目标航点索引

	// 时间控制
	flightStartTime    time.Time
	flyingElapsedSec   float64 // 已飞行时间
	totalFlightSec     float64 // 计划总飞行时间

	// 违规模拟
	violation      ViolationType
	violationTimer float64 // 违规持续计时器

	// 飞行计划
	flightPlanID string

	// 随机状态
	rng *rand.Rand
}

// NewDrone 创建无人机
func NewDrone(sn, model string, homePos Position, rng *rand.Rand) *Drone {
	d := &Drone{
		DeviceSN:    sn,
		Model:       model,
		FlightPhase: PhaseIdle,
		Position:    homePos,
		homePos:     homePos,
		BatteryPct:  95.0 + rng.Float64()*5.0, // 95-100%
		SignalRSSI:  -30 - rng.Intn(40),        // -30 ~ -70 dBm
		rng:         rng,
	}
	return d
}

// Lock 加锁
func (d *Drone) Lock() {
	d.mu.Lock()
}

// Unlock 解锁
func (d *Drone) Unlock() {
	d.mu.Unlock()
}

// TakeOff 起飞
func (d *Drone) TakeOff(flightPlanID string, totalFlightSec float64, waypoints []Position) {
	d.FlightPhase = PhaseTakeoff
	d.flightPlanID = flightPlanID
	d.totalFlightSec = totalFlightSec
	d.waypoints = waypoints
	d.currentWP = 0
	d.flightStartTime = time.Now()
	d.violation = ViolationNone
}

// ForceRTL 强制返航
func (d *Drone) ForceRTL(reason string) {
	d.FlightPhase = PhaseReturning
	// 直接飞回 home point
	d.waypoints = []Position{d.homePos}
	d.currentWP = 0
}

// GetTelemetry 生成遥测数据
func (d *Drone) GetTelemetry() Telemetry {
	return Telemetry{
		DeviceSN:     d.DeviceSN,
		Model:        d.Model,
		Timestamp:    time.Now().UnixMilli(),
		Position:     d.Position,
		SpeedMs:      d.SpeedMs,
		BatteryPct:   d.BatteryPct,
		SignalRSSI:   d.SignalRSSI,
		SatelliteCnt: 12 + d.rng.Intn(12), // 12-24
		FlightPhase:  d.FlightPhase,
		FlightPlanID: d.flightPlanID,
		Violation:    d.violation,
		WindSpeedMs:  d.rng.Float64() * 8, // 0-8 m/s
		TemperatureC: 15 + d.rng.Float64()*20, // 15-35°C
	}
}
