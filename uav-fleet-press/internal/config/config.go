package config

import (
	"os"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Press PressConfig `yaml:"press"`
}

// PressConfig 百万级二进制聚合通道
type PressConfig struct {
	Port   int     `yaml:"port"`
	Path   string  `yaml:"path"`
	Count  int     `yaml:"count"`
	Freq   float64 `yaml:"freq"`
	Lat    float64 `yaml:"lat"`
	Lon    float64 `yaml:"lon"`
	Radius float64 `yaml:"radius"` // 活动半径 km
}

func Load(path string) (*Config, error) {
	cfg := &Config{
		Press: PressConfig{
			Port:   8091,
			Path:   "/fleet",
			Count:  100000,
			Freq:   5.0,
			Lat:    39.9042,
			Lon:    116.4074,
			Radius: 50.0,
		},
	}

	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return cfg, nil // 无配置文件时用默认值
		}
		return nil, err
	}
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}
