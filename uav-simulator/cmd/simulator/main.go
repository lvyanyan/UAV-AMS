package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/uav-ams/uav-simulator/internal/config"
	"github.com/uav-ams/uav-simulator/internal/drone"
	"github.com/uav-ams/uav-simulator/internal/mqtt"
)

var (
	configPath = flag.String("config", "config/default.yaml", "配置文件路径")
	mode       = flag.String("mode", "simulation", "运行模式: simulation | stress-test")
)

func main() {
	flag.Parse()

	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds | log.Lshortfile)
	log.Printf("🛸 无人机仿真系统启动中... mode=%s", *mode)

	// 加载配置
	cfg, err := config.LoadConfig(*configPath)
	if err != nil {
		log.Printf("⚠️  加载配置文件失败: %v，使用默认配置", err)
		cfg = config.DefaultConfig()
	}

	// 创建仿真引擎
	simCfg := drone.SimulatorConfig{
		TelemetryRateHz:  cfg.Simulation.TelemetryRateHz,
		EnableViolations: cfg.Simulation.EnableViolations,
		ViolationProb:    cfg.Simulation.ViolationProb,
		BatchCreateSize:  cfg.Simulation.BatchSize,
	}
	sim := drone.NewSimulator(simCfg, cfg.Simulation.RandomSeed)

	// 根据场景创建无人机群
	totalDrones := 0
	for _, sc := range cfg.Scenarios {
		if !sc.Enabled {
			continue
		}
		log.Printf("📋 加载场景: %s → %d 架 %s", sc.Name, sc.DroneCount, sc.ModelPrefix)
		sim.CreateDroneFleet(
			sc.DroneCount, sc.ModelPrefix,
			sc.CenterLat, sc.CenterLon, sc.RadiusKm,
			sc.MinAltM, sc.MaxAltM,
			sc.MinSpeedMs, sc.MaxSpeedMs,
			sc.MaxFlightMin, sc.MinFlightMin,
			sc.DroneModels,
		)
		totalDrones += sc.DroneCount
	}

	// 真实飞行流程场景：已登记无人机执行已审批飞行计划（与 deploy/sql 种子数据对应）
	// autoStart=false 时启动后待命 IDLE，等待平台放行+起飞指令受控起飞
	sim.CreateRealFlowFleet(drone.DefaultRealMissions, cfg.Realflow.AutoStart)
	totalDrones += len(drone.DefaultRealMissions)
	if cfg.Realflow.AutoStart {
		log.Printf("📋 加载场景: 真实飞行流程 → %d 架已登记无人机（autoStart：启动即起飞轮换）", len(drone.DefaultRealMissions))
	} else {
		log.Printf("📋 加载场景: 真实飞行流程 → %d 架已登记无人机（受控模式：待命等待平台 TAKEOFF 指令）", len(drone.DefaultRealMissions))
	}
	log.Printf("✅ 共创建 %d 架仿真无人机", totalDrones)

	// 连接 MQTT
	pub := mqtt.NewPublisher(mqtt.PublisherConfig{
		Broker:   cfg.MQTT.Broker,
		ClientID: cfg.MQTT.ClientID + "-" + fmt.Sprintf("%d", time.Now().UnixNano()%100000),
		Username: cfg.MQTT.Username,
		Password: cfg.MQTT.Password,
		QoS:      cfg.MQTT.QoS,
		Retained: cfg.MQTT.Retained,
	})

	// 订阅平台指令 uav/+/cmd（TAKEOFF/RTL/LAND/ABORT），分发到仿真引擎
	// 注意：必须先注册再 Connect（订阅挂在 OnConnect 回调，连接建立时即生效）
	pub.OnCommand(func(sn, action, planCode string) {
		result := sim.ExecuteCommand(sn, action, planCode)
		log.Printf("📩 指令[%s] sn=%s plan=%s → %s", action, sn, planCode, result)
	})

	if err := pub.Connect(); err != nil {
		log.Printf("⚠️  MQTT 连接失败: %v (将仅输出到控制台)", err)
		// 允许无 MQTT 运行（仅 console 输出）
	} else {
		defer pub.Disconnect()
	}

	// 启动仿真主循环
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go runSimulationLoop(ctx, sim, pub, cfg)

	// 启动状态打印
	go runStatusReport(ctx, sim, pub)

	// 等待退出信号
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigCh
	log.Printf("收到信号 %v，正在优雅关闭...", sig)

	cancel()
	time.Sleep(2 * time.Second)

	sent, errors := pub.GetStats()
	stats := sim.GetStats()
	log.Printf("📊 最终统计: 总消息=%d 错误=%d 无人机=%d 飞行中=%d 违规=%d",
		sent, errors, stats.TotalDrones, stats.FlyingDrones, stats.ViolationCount)
	log.Println("👋 仿真系统已停止")
}

// runSimulationLoop 仿真主循环
func runSimulationLoop(ctx context.Context, sim *drone.Simulator, pub *mqtt.Publisher, cfg *config.Config) {
	ticker := time.NewTicker(cfg.Simulation.TickInterval())
	telemetryTicker := time.NewTicker(cfg.Simulation.TelemetryInterval())
	heartbeatTicker := time.NewTicker(cfg.Simulation.HeartbeatInterval())

	defer ticker.Stop()
	defer telemetryTicker.Stop()
	defer heartbeatTicker.Stop()

	dt := float64(cfg.Simulation.TickIntervalMs) / 1000.0
	telemetryBuffer := make([]drone.Telemetry, 0, 10000)

	for {
		select {
		case <-ctx.Done():
			return

		case <-ticker.C:
			// 仿真时钟推进
			telemetryList := sim.Tick(dt)
			telemetryBuffer = append(telemetryBuffer, telemetryList...)

			// 飞行事件（起飞离地/降落完成/紧急降落）经 MQTT uav/{sn}/event 发布
			for _, ev := range sim.DrainFlightEvents() {
				if pub.IsConnected() {
					_ = pub.PublishEvent(ev.SN, ev.EventType, map[string]interface{}{
						"plan_code": ev.PlanCode,
					})
				}
				log.Printf("📣 事件 %s | sn=%s | plan=%s", ev.EventType, ev.SN, ev.PlanCode)
			}

		case <-telemetryTicker.C:
			// 批量发布遥测
			if len(telemetryBuffer) > 0 {
				if pub.IsConnected() {
					pub.PublishBatch(telemetryBuffer)
				} else {
					// 控制台采样输出
					if len(telemetryBuffer) > 0 {
						sample := telemetryBuffer[0]
						log.Printf("[遥测] %s | 位置(%.4f,%.4f,%.0fm) | 电量%.0f%% | 阶段:%s | %d条待发",
							sample.DeviceSN, sample.Position.Lat, sample.Position.Lon, sample.Position.AltM,
							sample.BatteryPct, sample.FlightPhase, len(telemetryBuffer))
					}
				}
				telemetryBuffer = telemetryBuffer[:0]
			}

		case <-heartbeatTicker.C:
			// 发送心跳
			if pub.IsConnected() {
				drones := sim.GetDronesForHeartbeat()
				for _, d := range drones {
					_ = pub.PublishHeartbeat(d)
				}
			}
		}
	}
}

// runStatusReport 定期输出状态报告
func runStatusReport(ctx context.Context, sim *drone.Simulator, pub *mqtt.Publisher) {
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			stats := sim.GetStats()
			sent, errors := pub.GetStats()
			log.Printf("📊 [状态] 总数:%d 飞行:%d 违规:%d | MQTT发送:%d 错误:%d",
				stats.TotalDrones, stats.FlyingDrones, stats.ViolationCount,
				sent, errors)
		}
	}
}
