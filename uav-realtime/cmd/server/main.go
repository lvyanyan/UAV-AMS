package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/uav-ams/uav-realtime/internal/config"
	"github.com/uav-ams/uav-realtime/internal/fleetws"
	"github.com/uav-ams/uav-realtime/internal/kafka"
	"github.com/uav-ams/uav-realtime/internal/mqtt"
	"github.com/uav-ams/uav-realtime/internal/ws"
)

func main() {
	log.Println("========================================")
	log.Println("  UAV Realtime Service                   ")
	log.Println("  MQTT -> Kafka + WebSocket Bridge       ")
	log.Println("========================================")

	cfg, err := config.Load("config/default.yaml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// --- MQTT Client ---
	mqttClient, err := mqtt.NewClient(cfg.MQTT)
	if err != nil {
		log.Fatalf("Failed to create MQTT client: %v", err)
	}

	// --- 先注册处理器，再启动 MQTT（避免时序丢数据）---
	// Kafka 转发
	kafkaProducer := kafka.NewProducer(cfg.Kafka)
	mqttClient.OnTelemetryForKafka(func(t *mqtt.Telemetry) {
		kafkaProducer.ForwardTelemetry(
			t.DeviceSN,
			t.Position.Lat, t.Position.Lon, t.Position.AltM, t.Position.Heading,
			t.SpeedMs, t.BatteryPct, t.SignalRSSI,
			t.FlightPhase, t.FlightPlanID, t.Model,
			t.WindSpeedMs, t.TemperatureC,
		)
	})
	log.Println("[Kafka] MQTT->Kafka bridge ready, topic:", cfg.Kafka.Topic)

	// WebSocket 广播（在 NewServer 里注册 OnTelemetry）
	wsServer := ws.NewServer(cfg.WebSocket, mqttClient)
	go wsServer.Start(ctx)
	log.Printf("[WS] Listening on :%d%s", cfg.WebSocket.Port, cfg.WebSocket.Path)

	// --- Kafka Alarm Consumer -> WS push ---
	go kafka.StartAlarmConsumer(ctx, cfg.Kafka.Brokers, wsServer)
	log.Println("[Kafka] Alarm->WS consumer started")

	// --- 最后启动 MQTT（带重试，连上后立即开始接收数据）---
	go mqttClient.Start(ctx)
	log.Println("[MQTT] 启动连接（重试中...） ->", cfg.MQTT.Broker)

	// --- 百万级二进制聚合通道（8091，独立于 JSON 遥测，内置模拟机群）---
	if cfg.Fleet.Enabled {
		fleetServer := fleetws.NewServer(cfg.Fleet)
		go fleetServer.Start(ctx)
	} else {
		log.Println("[Fleet] 百万级二进制通道未启用（fleet.enabled=false）")
	}

	// Wait for shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	log.Println("Shutting down...")
	cancel()
	time.Sleep(2 * time.Second)
	kafkaProducer.Close()
	log.Println("UAV Realtime Service stopped.")
}
