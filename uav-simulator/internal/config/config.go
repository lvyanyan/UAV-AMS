package config

import (
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 仿真系统总配置
type Config struct {
	MQTT     MQTTConfig     `yaml:"mqtt"`
	Simulation SimulationConfig `yaml:"simulation"`
	Scenarios []ScenarioConfig  `yaml:"scenarios"`
}

// MQTTConfig MQTT 连接配置
type MQTTConfig struct {
	Broker   string `yaml:"broker"`    // tcp://emqx:1883
	ClientID string `yaml:"client_id"` // uav-simulator
	Username string `yaml:"username"`
	Password string `yaml:"password"`
	QoS      byte   `yaml:"qos"`       // 1
	Retained bool   `yaml:"retained"`  // false
}

// SimulationConfig 仿真参数
type SimulationConfig struct {
	TickIntervalMs    int     `yaml:"tick_interval_ms"`     // 仿真时钟间隔 (默认 200ms)
	TelemetryRateHz   float64 `yaml:"telemetry_rate_hz"`    // 遥测发布频率 (默认 5Hz)
	HeartbeatIntervalSec int `yaml:"heartbeat_interval_sec"` // 心跳间隔 (默认 5s)
	BatchSize         int     `yaml:"batch_size"`           // 批量创建无人机数量
	EnableViolations  bool    `yaml:"enable_violations"`     // 是否启用违规模拟
	ViolationProb     float64 `yaml:"violation_prob"`        // 违规概率 (0~1)
	RandomSeed        int64   `yaml:"random_seed"`           // 随机种子
}

// ScenarioConfig 场景配置
type ScenarioConfig struct {
	Name        string  `yaml:"name"`         // 场景名称
	Enabled     bool    `yaml:"enabled"`       // 是否启用
	DroneCount  int     `yaml:"drone_count"`   // 无人机数量
	ModelPrefix string  `yaml:"model_prefix"`  // 设备 SN 前缀
	CenterLat   float64 `yaml:"center_lat"`    // 场景中心纬度
	CenterLon   float64 `yaml:"center_lon"`    // 场景中心经度
	RadiusKm    float64 `yaml:"radius_km"`     // 活动半径 (km)
	MinAltM     float64 `yaml:"min_alt_m"`     // 最低飞行高度
	MaxAltM     float64 `yaml:"max_alt_m"`     // 最高飞行高度
	MinSpeedMs  float64 `yaml:"min_speed_ms"`  // 最低速度 (m/s)
	MaxSpeedMs  float64 `yaml:"max_speed_ms"`  // 最高速度 (m/s)
	MaxFlightMin int   `yaml:"max_flight_min"` // 最长飞行时间 (分钟)
	MinFlightMin int   `yaml:"min_flight_min"` // 最短飞行时间 (分钟)
	DroneModels  []string `yaml:"drone_models"` // 机型列表
}

// DefaultConfig 返回默认配置
func DefaultConfig() *Config {
	return &Config{
		MQTT: MQTTConfig{
			Broker:   "tcp://127.0.0.1:1883",
			ClientID: "uav-simulator",
			Username: "",
			Password: "",
			QoS:      1,
			Retained: false,
		},
		Simulation: SimulationConfig{
			TickIntervalMs:       200,
			TelemetryRateHz:      5.0,
			HeartbeatIntervalSec: 5,
			BatchSize:            100,
			EnableViolations:     true,
			ViolationProb:        0.02,
			RandomSeed:           42,
		},
		Scenarios: []ScenarioConfig{
			{
				Name:        "城市巡检",
				Enabled:     true,
				DroneCount:  50,
				ModelPrefix: "PATROL",
				CenterLat:   39.9042,
				CenterLon:   116.4074,
				RadiusKm:    10.0,
				MinAltM:     50.0,
				MaxAltM:     120.0,
				MinSpeedMs:  5.0,
				MaxSpeedMs:  15.0,
				MaxFlightMin: 30,
				MinFlightMin: 10,
				DroneModels:  []string{"DJI-M350", "DJI-M300", "DJI-M30T"},
			},
			{
				Name:        "物流配送",
				Enabled:     true,
				DroneCount:  30,
				ModelPrefix: "LOGISTICS",
				CenterLat:   39.9142,
				CenterLon:   116.4174,
				RadiusKm:    15.0,
				MinAltM:     80.0,
				MaxAltM:     200.0,
				MinSpeedMs:  10.0,
				MaxSpeedMs:  25.0,
				MaxFlightMin: 20,
				MinFlightMin: 5,
				DroneModels:  []string{"DJI-FLYCART30", "CUSTOM-C300"},
			},
			{
				Name:        "航拍摄影",
				Enabled:     false,
				DroneCount:  20,
				ModelPrefix: "AERIAL",
				CenterLat:   39.8942,
				CenterLon:   116.3874,
				RadiusKm:    5.0,
				MinAltM:     30.0,
				MaxAltM:     300.0,
				MinSpeedMs:  3.0,
				MaxSpeedMs:  12.0,
				MaxFlightMin: 25,
				MinFlightMin: 15,
				DroneModels:  []string{"DJI-MAVIC3", "DJI-MINI4P", "DJI-AIR3S"},
			},
		},
	}
}

// LoadConfig 从 YAML 文件加载配置
func LoadConfig(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	cfg := DefaultConfig()
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, err
	}

	return cfg, nil
}

// TelemetryInterval 返回遥测间隔
func (s *SimulationConfig) TelemetryInterval() time.Duration {
	return time.Duration(float64(time.Second) / s.TelemetryRateHz)
}

// HeartbeatInterval 返回心跳间隔
func (s *SimulationConfig) HeartbeatInterval() time.Duration {
	return time.Duration(s.HeartbeatIntervalSec) * time.Second
}

// TickInterval 返回仿真时钟间隔
func (s *SimulationConfig) TickInterval() time.Duration {
	return time.Duration(s.TickIntervalMs) * time.Millisecond
}
