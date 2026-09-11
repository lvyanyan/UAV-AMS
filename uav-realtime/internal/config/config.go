package config

import (
	"os"

	"gopkg.in/yaml.v3"
)

type Config struct {
	MQTT      MQTTConfig      `yaml:"mqtt"`
	WebSocket WebSocketConfig `yaml:"websocket"`
	Kafka     KafkaConfig     `yaml:"kafka"`
	Redis     RedisConfig     `yaml:"redis"`
}

type MQTTConfig struct {
	Broker   string `yaml:"broker"`
	ClientID string `yaml:"client_id"`
	Username string `yaml:"username"`
	Password string `yaml:"password"`
	QoS      byte   `yaml:"qos"`
}

type WebSocketConfig struct {
	Port    int    `yaml:"port"`
	Path    string `yaml:"path"`
	Origins string `yaml:"origins"`
}

type KafkaConfig struct {
	Brokers []string `yaml:"brokers"`
	Topic   string   `yaml:"topic"`
}

type RedisConfig struct {
	Addr     string `yaml:"addr"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
}

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	cfg := &Config{
		MQTT: MQTTConfig{
			Broker:   "tcp://localhost:1883",
			ClientID: "uav-realtime",
			QoS:      1,
		},
		WebSocket: WebSocketConfig{
			Port:    8090,
			Path:    "/ws",
			Origins: "*",
		},
		Kafka: KafkaConfig{
			Brokers: []string{"localhost:9092"},
			Topic:   "uav.telemetry",
		},
		Redis: RedisConfig{
			Addr: "localhost:6379",
			DB:   0,
		},
	}

	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}
