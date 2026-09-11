package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/uav-ams/uav-fleet-press/internal/config"
	"github.com/uav-ams/uav-fleet-press/internal/press"
)

func main() {
	log.Println("========================================")
	log.Println("  UAV Fleet Press Service               ")
	log.Println("  百万级二进制聚合通道 WS :8091/fleet     ")
	log.Println("========================================")

	cfg, err := config.Load("config/default.yaml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// 百万级机群模拟 + 视锥感知二进制分发（高空 cell 聚合 / 低空 bbox 裁剪）
	srv := press.NewServer(cfg.Press)
	go srv.Start(ctx)

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	log.Println("Shutting down...")
	cancel()
	time.Sleep(500 * time.Millisecond)
	log.Println("UAV Fleet Press Service stopped.")
}
