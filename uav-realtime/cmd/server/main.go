package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/uav-ams/uav-realtime/internal/config"
	"github.com/uav-ams/uav-realtime/internal/kafka"
	"github.com/uav-ams/uav-realtime/internal/mqtt"
	"github.com/uav-ams/uav-realtime/internal/store"
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

	// 遥测内存仓：在飞快照 + 历史环形缓冲（供快照接入与消息重放）
	teleStore := store.New(10 * time.Minute)
	mqttClient.OnTelemetry(func(t *mqtt.Telemetry) {
		teleStore.Record(store.Record{
			SN: t.DeviceSN, Lat: t.Position.Lat, Lon: t.Position.Lon,
			Alt: t.Position.AltM, Heading: t.Position.Heading,
		})
	})

	// WebSocket 广播（在 NewServer 里注册 OnTelemetry）
	wsServer := ws.NewServer(cfg.WebSocket, mqttClient)
	wsServer.SetTelemetryStore(teleStore)
	go wsServer.Start(ctx)
	log.Printf("[WS] Listening on :%d%s", cfg.WebSocket.Port, cfg.WebSocket.Path)

	// --- Kafka Alarm Consumer -> WS push ---
	go kafka.StartAlarmConsumer(ctx, cfg.Kafka.Brokers, wsServer)
	log.Println("[Kafka] Alarm->WS consumer started")

	// --- 最后启动 MQTT（带重试，连上后立即开始接收数据）---
	go mqttClient.Start(ctx)
	log.Println("[MQTT] 启动连接（重试中...） ->", cfg.MQTT.Broker)

	// 注：百万级二进制聚合通道已拆分为独立服务 uav-fleet-press（:8091/fleet），
	// 本服务专注 JSON 遥测/告警链路，故障半径互不影响。

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
