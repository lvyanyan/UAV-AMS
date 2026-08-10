package drone

import (
	"fmt"
	"math"
	"math/rand"
	"time"

	"github.com/google/uuid"
)

// Simulator 无人机仿真引擎
type Simulator struct {
	drones  map[string]*Drone
	rng     *rand.Rand
	pathGen *FlightPathGenerator
	config  SimulatorConfig

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
	return &Simulator{
		drones:  make(map[string]*Drone),
		rng:     rng,
		pathGen: NewFlightPathGenerator(rng),
		config:  cfg,
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

	flyingCount := 0
	violationCount := 0

	for _, d := range s.drones {
		d.Lock()

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
			s.handleLanded(d)
		}

		if d.violation != ViolationNone {
			violationCount++
		}

		d.Unlock()

		// 按频率决定是否输出遥测
		if s.shouldSendTelemetry(d) {
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

	return telemetryList
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

// handleIdle 待命状态 → 随机起飞
func (s *Simulator) handleIdle(d *Drone) {
	// 30% 概率起飞 (每秒)
	if s.rng.Float64() < 0.3 {
		flightDuration := float64(60 * (d.rng.Intn(30) + 10)) // 10-40 分钟
		planID := fmt.Sprintf("FP-%s-%s", time.Now().Format("20060102"), uuid.New().String()[:8])

		// 生成巡逻路径
		waypoints := s.pathGen.GeneratePatrolPath(d.homePos, 5.0, 50, 200)
		d.TakeOff(planID, flightDuration, waypoints)
	}
}

// handleTakeoff 起飞阶段 → 上升到巡航高度
func (s *Simulator) handleTakeoff(d *Drone, dt float64) {
	targetAlt := d.waypoints[0].AltM
	climbRate := 5.0 // 5 m/s 爬升率
	d.Position.AltM += climbRate * dt

	if d.Position.AltM >= targetAlt {
		d.Position.AltM = targetAlt
		d.FlightPhase = PhaseFlying
	}
	d.Position = Position{Lat: d.homePos.Lat, Lon: d.homePos.Lon, AltM: d.Position.AltM}

	// 电量消耗
	d.BatteryPct -= 0.01 * dt
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

// handleLanding 降落中
func (s *Simulator) handleLanding(d *Drone, dt float64) {
	d.Position.AltM -= 3.0 * dt
	if d.Position.AltM <= 0 {
		d.Position.AltM = 0
		d.Position = d.homePos
		d.FlightPhase = PhaseLanded
		d.violation = ViolationNone
	}
}

// handleLanded 已降落 → 充电重置
func (s *Simulator) handleLanded(d *Drone) {
	// 自动充电重置
	d.BatteryPct = 95.0 + d.rng.Float64()*5.0
	d.SignalRSSI = -30 - d.rng.Intn(40)

	// 短暂休息后重新变为 Idle
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
