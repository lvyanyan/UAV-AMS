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

// MissionState 真实流程任务状态（受控起飞：PENDING 待命 → EXECUTING 执行中 → DONE 已完成）
type MissionState string

const (
	MissionPending   MissionState = "PENDING"
	MissionExecuting MissionState = "EXECUTING"
	MissionDone      MissionState = "DONE"
)

// FlightEvent 飞行事件（起飞/降落完成），由 Simulator 聚合后经 MQTT uav/{sn}/event 发布
type FlightEvent struct {
	SN        string
	EventType string // TAKEOFF / FLIGHT_COMPLETED / FLIGHT_ABORTED
	PlanCode  string
	Timestamp int64 // Unix ms
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

	// 真实流程任务（绑定已审批飞行计划，与 flight_plan 表对应）
	realMissions  []RealMission
	missionIdx    int
	realAutoStart bool         // true=启动即自动轮飞（旧行为）；false=等待平台指令受控起飞
	missionState  MissionState // 当前任务状态（受控起飞用）
	takeoffEventDone bool      // 本次起飞是否已发离地事件

	// 起飞细分：旋翼启动计时（秒）
	spinupSec float64

	// 紧急降落：原地悬停计时（秒）
	emergencyHoverSec float64

	// 降落地面停留计时（秒）：保持 LANDED 相位一小段时间，保证遥测采到降落边沿
	landedElapsed float64

	teleAccum    float64 // 遥测发送节流累计器（秒）
	pendingEvents []FlightEvent // 待发布事件（Tick 时由 Simulator 聚合）

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
	d.spinupSec = 0
	d.emergencyHoverSec = 0
	d.landedElapsed = 0
	d.takeoffEventDone = false
	d.SpeedMs = 0
}

// ForceRTL 强制返航
func (d *Drone) ForceRTL(reason string) {
	d.FlightPhase = PhaseReturning
	d.emergencyHoverSec = 0
	// 直接飞回 home point
	d.waypoints = []Position{d.homePos}
	d.currentWP = 0
}

// BeginEmergency 进入紧急状态：原地悬停后快速降落（收到 ABORT 且在飞时触发）
func (d *Drone) BeginEmergency() {
	d.FlightPhase = PhaseEmergency
	d.emergencyHoverSec = 0
	d.SpeedMs = 0
}

// startNextRealMission 开始下一个真实流程任务（降落充电后轮换重飞；仅 autoStart 旧模式调用）
func (d *Drone) startNextRealMission() {
	if len(d.realMissions) == 0 {
		return
	}
	m := d.realMissions[d.missionIdx%len(d.realMissions)]
	d.realMissions[d.missionIdx%len(d.realMissions)].State = MissionExecuting
	d.missionIdx++
	d.missionState = MissionExecuting
	d.TakeOff(m.PlanCode, 3600, append([]Position{}, m.Waypoints...))
}

// BeginMissionByPlanCode 受控起飞：按计划号匹配待命任务并开始执行。
// 返回 false 表示任务不存在 / 无人机不在地面。
func (d *Drone) BeginMissionByPlanCode(planCode string) bool {
	if d.FlightPhase != PhaseIdle && d.FlightPhase != PhaseLanded {
		return false // 在飞不接受起飞指令
	}
	for i := range d.realMissions {
		if d.realMissions[i].PlanCode != planCode {
			continue
		}
		// 重置轮换指针到该任务，标记执行中（同一 planCode 再次收到 TAKEOFF 可重新执行）
		d.realMissions[i].State = MissionExecuting
		d.missionIdx = i + 1
		d.missionState = MissionExecuting
		d.TakeOff(planCode, 3600, append([]Position{}, d.realMissions[i].Waypoints...))
		return true
	}
	return false
}

// MarkMissionDone 当前任务标记完成（降落后；受控模式下不再自动重飞）
func (d *Drone) MarkMissionDone() {
	if len(d.realMissions) > 0 {
		d.missionState = MissionDone
		// 同步任务结构体状态（当前任务 = missionIdx-1）
		idx := (d.missionIdx - 1) % len(d.realMissions)
		if idx < 0 {
			idx += len(d.realMissions)
		}
		d.realMissions[idx].State = MissionDone
	}
}

// IsRealFlowDrone 是否为真实流程机（绑定已审批计划的机队）
func (d *Drone) IsRealFlowDrone() bool {
	return len(d.realMissions) > 0
}

// pushEvent 追加待发布事件（调用方须持有 d.mu）
func (d *Drone) pushEvent(eventType, planCode string) {
	d.pendingEvents = append(d.pendingEvents, FlightEvent{
		SN: d.DeviceSN, EventType: eventType, PlanCode: planCode,
		Timestamp: time.Now().UnixMilli(),
	})
}

// DrainEvents 取走待发布事件（调用方须持有 d.mu）
func (d *Drone) DrainEvents() []FlightEvent {
	if len(d.pendingEvents) == 0 {
		return nil
	}
	evts := d.pendingEvents
	d.pendingEvents = nil
	return evts
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
