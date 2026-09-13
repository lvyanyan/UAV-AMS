package drone

import (
	"fmt"
	"math"
	"math/rand"
	"sync"
	"time"

	"github.com/google/uuid"
)

// Simulator 无人机仿真引擎
type Simulator struct {
	drones  map[string]*Drone
	rng     *rand.Rand
	pathGen *FlightPathGenerator
	config  SimulatorConfig

	// 遥测发送周期（秒），由 TelemetryRateHz 换算
	telemetryPeriod float64

	// 飞行事件队列（起飞/降落完成/紧急），由主循环排空后经 MQTT 发布
	muEvents      sync.Mutex
	pendingEvents []FlightEvent

	// 统计
	stats SimulatorStats
}

// SimulatorConfig 仿真引擎配置
type SimulatorConfig struct {
	TelemetryRateHz  float64
	EnableViolations bool
	ViolationProb    float64
	BatchCreateSize  int
}

// SimulatorStats 仿真统计
type SimulatorStats struct {
	TotalDrones    int
	FlyingDrones   int
	IdleDrones     int
	ViolationCount int
	TotalTelemetry int64
}

// NewSimulator 创建仿真引擎
func NewSimulator(cfg SimulatorConfig, seed int64) *Simulator {
	rng := rand.New(rand.NewSource(seed))
	period := 1.0
	if cfg.TelemetryRateHz > 0 {
		period = 1.0 / cfg.TelemetryRateHz
	}
	return &Simulator{
		drones:          make(map[string]*Drone),
		rng:             rng,
		pathGen:         NewFlightPathGenerator(rng),
		config:          cfg,
		telemetryPeriod: period,
	}
}

// CreateDroneFleet 批量创建无人机群
func (s *Simulator) CreateDroneFleet(count int, modelPrefix string, centerLat, centerLon, radiusKm, minAlt, maxAlt, minSpeed, maxSpeed float64, maxFlightMin, minFlightMin int, models []string) {
	for i := 0; i < count; i++ {
		sn := fmt.Sprintf("%s-%04d", modelPrefix, i+1)
		model := models[s.rng.Intn(len(models))]

		// 在场景中心附近生成起飞点
		angle := s.rng.Float64() * 2 * math.Pi
		distKm := s.rng.Float64() * radiusKm * 0.5 // 起飞点分布在内圈
		latOffset := distKm / 111.0 * math.Cos(angle)
		lonOffset := distKm / (111.0 * math.Cos(centerLat*math.Pi/180.0)) * math.Sin(angle)

		homePos := Position{
			Lat:  centerLat + latOffset,
			Lon:  centerLon + lonOffset,
			AltM: 0,
		}

		d := NewDrone(sn, model, homePos, rand.New(rand.NewSource(s.rng.Int63())))
		d.SpeedMs = minSpeed + s.rng.Float64()*(maxSpeed-minSpeed)
		s.drones[sn] = d
	}
}

// Tick 执行一次仿真时钟推进
// 返回本次需要发送遥测的无人机列表
func (s *Simulator) Tick(deltaTime float64) []Telemetry {
	var telemetryList []Telemetry
	var eventList []FlightEvent

	flyingCount := 0
	violationCount := 0

	for _, d := range s.drones {
		d.Lock()

		// 遥测发送节流：每架无人机按 TelemetryRateHz 独立计频
		d.teleAccum += deltaTime
		sendNow := d.teleAccum >= s.telemetryPeriod
		if sendNow {
			d.teleAccum = 0
		}

		switch d.FlightPhase {
		case PhaseIdle:
			s.handleIdle(d)

		case PhaseTakeoff:
			s.handleTakeoff(d, deltaTime)

		case PhaseFlying:
			s.handleFlying(d, deltaTime)
			flyingCount++

		case PhaseReturning:
			s.handleReturning(d, deltaTime)

		case PhaseLanding:
			s.handleLanding(d, deltaTime)

		case PhaseLanded:
			s.handleLanded(d, deltaTime)

		case PhaseEmergency:
			s.handleEmergency(d, deltaTime)
			flyingCount++
		}

		if d.violation != ViolationNone {
			violationCount++
		}

		// 聚合本次推进产生的飞行事件（起飞离地/降落完成/紧急降落）
		if evts := d.DrainEvents(); len(evts) > 0 {
			eventList = append(eventList, evts...)
		}

		d.Unlock()

		// 按频率决定是否输出遥测
		if sendNow && s.shouldSendTelemetry(d) {
			d.Lock()
			t := d.GetTelemetry()
			d.Unlock()
			telemetryList = append(telemetryList, t)
			s.stats.TotalTelemetry++
		}
	}

	s.stats.TotalDrones = len(s.drones)
	s.stats.FlyingDrones = flyingCount
	s.stats.ViolationCount = violationCount

	// 事件先入队，遥测后再统一交给调用方
	s.muEvents.Lock()
	s.pendingEvents = append(s.pendingEvents, eventList...)
	s.muEvents.Unlock()

	return telemetryList
}

// DrainFlightEvents 取走累计的飞行事件（起飞/降落/紧急），由主循环经 MQTT uav/{sn}/event 发布
func (s *Simulator) DrainFlightEvents() []FlightEvent {
	s.muEvents.Lock()
	defer s.muEvents.Unlock()
	if len(s.pendingEvents) == 0 {
		return nil
	}
	evts := s.pendingEvents
	s.pendingEvents = nil
	return evts
}

// ExecuteCommand 处理平台指令（uav/{sn}/cmd）。
// 返回执行结果描述（用于日志）。指令契约：TAKEOFF/RTL/LAND/ABORT。
func (s *Simulator) ExecuteCommand(sn, action, planCode string) string {
	d, ok := s.drones[sn]
	if !ok {
		return "unknown drone " + sn
	}

	d.Lock()
	defer d.Unlock()

	switch action {
	case "TAKEOFF":
		if !d.IsRealFlowDrone() {
			return sn + " 非真实流程机，忽略受控起飞"
		}
		if d.FlightPhase != PhaseIdle && d.FlightPhase != PhaseLanded {
			return sn + " 不在地面，拒绝起飞指令"
		}
		if !d.BeginMissionByPlanCode(planCode) {
			return sn + " 无匹配计划 " + planCode + " 或任务非待命状态"
		}
		return sn + " 受控起飞，计划 " + planCode

	case "RTL":
		if d.FlightPhase == PhaseIdle || d.FlightPhase == PhaseLanded || d.FlightPhase == PhaseLanding {
			return sn + " 不在巡航/返航状态，忽略 RTL"
		}
		d.ForceRTL("platform RTL")
		return sn + " 指令返航"

	case "LAND":
		if d.FlightPhase == PhaseIdle || d.FlightPhase == PhaseLanded {
			return sn + " 已在地面，忽略 LAND"
		}
		// 原地降落
		d.FlightPhase = PhaseLanding
		d.SpeedMs = 0
		return sn + " 原地降落"

	case "ABORT":
		if d.FlightPhase == PhaseFlying || d.FlightPhase == PhaseReturning || d.FlightPhase == PhaseTakeoff {
			// 紧急场景：原地悬停后快速降落
			d.BeginEmergency()
			return sn + " 紧急中止（EMERGENCY：悬停后快速降落）"
		}
		return sn + " 不在飞，忽略 ABORT"

	default:
		return sn + " 未知指令 " + action
	}
}

// GetDronesForHeartbeat 获取需要发送心跳的无人机 (所有非Idle的)
func (s *Simulator) GetDronesForHeartbeat() []*Drone {
	var result []*Drone
	for _, d := range s.drones {
		if d.FlightPhase != PhaseIdle && d.FlightPhase != PhaseLanded {
			result = append(result, d)
		}
	}
	return result
}

// GetStats 获取统计信息
func (s *Simulator) GetStats() SimulatorStats {
	return s.stats
}

// ForceRTLOnDrone 对指定无人机强制返航
func (s *Simulator) ForceRTLOnDrone(sn, reason string) bool {
	d, ok := s.drones[sn]
	if !ok {
		return false
	}
	d.Lock()
	defer d.Unlock()
	d.ForceRTL(reason)
	return true
}

// GetDrone 获取无人机实例
func (s *Simulator) GetDrone(sn string) (*Drone, bool) {
	d, ok := s.drones[sn]
	return d, ok
}

// handleIdle 待命状态：
//   真实流程机 autoStart=true（旧模式）→ 按绑定计划轮换重飞；
//   真实流程机 autoStart=false（受控）→ 原地待命，等待平台 TAKEOFF 指令；
//   其余压测机随机起飞。
func (s *Simulator) handleIdle(d *Drone) {
	if len(d.realMissions) > 0 {
		if d.realAutoStart {
			d.startNextRealMission()
		}
		return // 受控模式下待命，不起飞
	}
	// 30% 概率起飞 (每秒)
	if s.rng.Float64() < 0.3 {
		flightDuration := float64(60 * (d.rng.Intn(30) + 10)) // 10-40 分钟
		planID := fmt.Sprintf("FP-%s-%s", time.Now().Format("20060102"), uuid.New().String()[:8])

		// 生成巡逻路径
		waypoints := s.pathGen.GeneratePatrolPath(d.homePos, 5.0, 50, 200)
		d.TakeOff(planID, flightDuration, waypoints)
	}
}

// handleTakeoff 起飞阶段（细分两段）：
//   1) 旋翼启动 1.5s：speed=0，电量微降，仍在地面；
//   2) 垂直爬升至首航点高度 → 离地即发布 TAKEOFF 事件，转 FLYING 前按航点推进。
func (s *Simulator) handleTakeoff(d *Drone, dt float64) {
	// --- 旋翼启动段 ---
	if d.spinupSec < rotorSpinupSec {
		d.spinupSec += dt
		d.SpeedMs = 0
		d.BatteryPct -= 0.02 * dt // 旋翼启动耗电
		if d.spinupSec < rotorSpinupSec {
			return
		}
		// 启动完成 → 离地爬升开始，发布离地事件
		if !d.takeoffEventDone {
			d.takeoffEventDone = true
			d.pushEvent("TAKEOFF", d.flightPlanID)
		}
	}

	// --- 垂直爬升段 ---
	targetAlt := d.waypoints[0].AltM
	climbRate := 5.0 // 5 m/s 爬升率
	d.Position.AltM += climbRate * dt
	d.SpeedMs = climbRate

	if d.Position.AltM >= targetAlt {
		d.Position.AltM = targetAlt
		d.FlightPhase = PhaseFlying
	}
	d.Position = Position{Lat: d.homePos.Lat, Lon: d.homePos.Lon, AltM: d.Position.AltM}

	// 电量消耗
	d.BatteryPct -= 0.01 * dt
}

// handleEmergency 紧急中止（收到 ABORT 且在飞）：原地悬停 2s → 快速降落（1.5x 降落速度）→ 落地发 FLIGHT_ABORTED
func (s *Simulator) handleEmergency(d *Drone, dt float64) {
	if d.emergencyHoverSec < emergencyHoverSec {
		d.emergencyHoverSec += dt
		d.SpeedMs = 0 // 原地悬停
		return
	}
	// 快速降落（1.5 倍标称降落速度）
	d.SpeedMs = landingRateMs * 1.5
	d.Position.AltM -= d.SpeedMs * dt
	if d.Position.AltM <= 0 {
		d.Position.AltM = 0
		d.Position = d.homePos
		d.FlightPhase = PhaseLanded
		d.landedElapsed = 0
		d.violation = ViolationNone
		d.pushEvent("FLIGHT_ABORTED", d.flightPlanID)
	}
}

// handleFlying 飞行中 → 沿航点移动
func (s *Simulator) handleFlying(d *Drone, dt float64) {
	if d.currentWP >= len(d.waypoints) {
		// 所有航点完成，开始返航
		d.FlightPhase = PhaseReturning
		return
	}

	target := d.waypoints[d.currentWP]
	dist := CalculateDistance(d.Position, target)

	if dist < 5.0 { // 到达航点（5米误差）
		d.currentWP++
		if d.currentWP >= len(d.waypoints) {
			d.FlightPhase = PhaseReturning
			return
		}
		target = d.waypoints[d.currentWP]
		dist = CalculateDistance(d.Position, target)
	}

	speed := d.SpeedMs
	step := speed * dt

	// 先计算航向（基于当前位置到目标点），保存下来
	heading := CalculateHeading(d.Position, target)

	if step >= dist {
		d.Position = target
	} else {
		t := step / dist
		d.Position = InterpolatePosition(d.Position, target, t)
	}

	// ★ 恢复航向 — InterpolatePosition 会重置 Heading 为 0
	d.Position.Heading = heading

	// 电量消耗
	d.BatteryPct -= 0.005 * dt

	// 电池过低自动返航
	if d.BatteryPct < 20 {
		d.FlightPhase = PhaseReturning
		d.waypoints = []Position{d.homePos}
		d.currentWP = 0
	}

	// 违规模拟
	if s.config.EnableViolations {
		s.simulateViolation(d, dt)
	}
}

// handleReturning 返航中 → 飞回 home
func (s *Simulator) handleReturning(d *Drone, dt float64) {
	// 确保目标为 home
	target := d.homePos
	dist := CalculateDistance(d.Position, target)

	if dist < 5.0 {
		d.FlightPhase = PhaseLanding
		return
	}

	// 先计算航向，保存下来
	heading := CalculateHeading(d.Position, target)
	speed := d.SpeedMs
	step := speed * dt

	if step >= dist {
		d.Position = target
		d.Position.AltM = 0
	} else {
		t := step / dist
		d.Position = InterpolatePosition(d.Position, target, t)
		// 逐渐降低高度
		d.Position.AltM -= 2.0 * dt
		if d.Position.AltM < 0 {
			d.Position.AltM = 0
		}
	}

	// ★ 恢复航向
	d.Position.Heading = heading

	d.BatteryPct -= 0.005 * dt
}

// 起飞/紧急/降落过程常量
const (
	rotorSpinupSec    = 1.5 // 起飞前旋翼启动时长（秒）
	emergencyHoverSec = 2.0 // 紧急中止原地悬停时长（秒）
	landingRateMs     = 3.0 // 标称降落下降率 (m/s)
	landedDwellSec    = 6.0 // 降落后保持 LANDED 相位时长（秒，确保遥测采样到降落边沿）
)

// handleLanding 降落中
func (s *Simulator) handleLanding(d *Drone, dt float64) {
	d.SpeedMs = landingRateMs
	d.Position.AltM -= landingRateMs * dt
	if d.Position.AltM <= 0 {
		d.Position.AltM = 0
		d.Position = d.homePos
		d.FlightPhase = PhaseLanded
		d.landedElapsed = 0
		d.violation = ViolationNone
		// 降落完成：发布 FLIGHT_COMPLETED 事件；受控模式下任务标记 DONE，不再自动重飞
		d.pushEvent("FLIGHT_COMPLETED", d.flightPlanID)
		d.MarkMissionDone()
	}
}

// handleLanded 已降落 → 保持 LANDED 数秒（保证遥测采到降落边沿）→ 充电重置回 IDLE
func (s *Simulator) handleLanded(d *Drone, dt float64) {
	d.SpeedMs = 0
	d.landedElapsed += dt
	if d.landedElapsed < landedDwellSec {
		return
	}
	// 自动充电重置
	d.BatteryPct = 95.0 + d.rng.Float64()*5.0
	d.SignalRSSI = -30 - d.rng.Intn(40)
	d.landedElapsed = 0
	// 回到 IDLE：受控模式原地待命等新指令；autoStart 旧模式/压测机自行再次起飞
	d.FlightPhase = PhaseIdle
}

// simulateViolation 模拟违规行为
func (s *Simulator) simulateViolation(d *Drone, dt float64) {
	if d.violation != ViolationNone {
		// 已有违规，持续一段时间后解除
		d.violationTimer += dt
		if d.violationTimer > 30+float64(d.rng.Intn(60)) { // 30-90秒后解除
			d.violation = ViolationNone
			d.violationTimer = 0
		}
		return
	}

	// 随机触发违规
	if s.rng.Float64() < s.config.ViolationProb*dt {
		violations := []ViolationType{
			ViolationAltitude,
			ViolationSpeed,
			ViolationGeoFence,
			ViolationNoPlan,
		}
		d.violation = violations[s.rng.Intn(len(violations))]
		d.violationTimer = 0

		// 模拟违规行为
		switch d.violation {
		case ViolationAltitude:
			d.Position.AltM += 50 // 瞬间爬升违规
		case ViolationSpeed:
			d.SpeedMs = 30 // 超速
		case ViolationGeoFence:
			// 偏移到围栏外 (随机偏移)
			d.Position.Lat += (s.rng.Float64() - 0.5) * 0.01
			d.Position.Lon += (s.rng.Float64() - 0.5) * 0.01
		}
	}
}

// shouldSendTelemetry 判断是否应发送遥测
func (s *Simulator) shouldSendTelemetry(d *Drone) bool {
	if d.FlightPhase == PhaseIdle || d.FlightPhase == PhaseLanded {
		// 地面状态每 5 秒报一次
		return time.Now().UnixNano()%int64(5*time.Second) < int64(s.config.TelemetryRateHz*float64(time.Second))
	}
	return true
}
