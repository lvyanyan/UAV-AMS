package kafka

import (
	"context"
	"log"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/uav-ams/uav-realtime/internal/ws"
)

// StartAlarmConsumer 消费告警/冲突/解脱主题，推送到 WebSocket
func StartAlarmConsumer(ctx context.Context, brokers []string, wsServer *ws.Server) {
	topics := []string{"uav.alarm.event", "uav.conflict.alert", "uav.signboard.conflict", "uav.signboard.resolution", "uav.ops-seat.resolution"}

	for _, topic := range topics {
		go consumeTopic(ctx, brokers, topic, wsServer)
	}
}

func consumeTopic(ctx context.Context, brokers []string, topic string, wsServer *ws.Server) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  brokers,
		Topic:    topic,
		GroupID:  "realtime-ws-" + topic,
		MinBytes: 1,
		MaxBytes: 10e6,
		MaxWait:  500 * time.Millisecond,
	})
	defer reader.Close()

	log.Printf("[Kafka-Consumer] Listening on topic: %s", topic)

	for {
		select {
		case <-ctx.Done():
			return
		default:
			msg, err := reader.ReadMessage(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				log.Printf("[Kafka-Consumer] Read error on %s: %v", topic, err)
				time.Sleep(time.Second)
				continue
			}

			// 封装为统一 WebSocket 消息格式（按客户端订阅条件过滤后推送）
			wsServer.BroadcastEvent(topic, msg.Value)
		}
	}
}
