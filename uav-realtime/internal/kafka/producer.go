package kafka

import (
	"context"
	"encoding/json"
	"log"
	"strings"
	"sync"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/uav-ams/uav-realtime/internal/config"
)

// Producer Kafka producer（异步批量写，带重试与自动建 topic）
//
// MQTT 消息处理在 paho 的路由 goroutine 内同步执行：若此处同步写 Kafka，
// 高频遥测会把路由阻塞到无法响应 EMQX 心跳，客户端被踢（30s 闪断循环）。
// 因此 ForwardTelemetry 只做非阻塞入队，写盘由后台 worker 批量完成。
type Producer struct {
	writer    *kafka.Writer
	topic     string
	mu        sync.Mutex
	errCount  int
	lastError time.Time

	ch     chan kafka.Message
	dropped int64
}

// NewProducer create Kafka producer
func NewProducer(cfg config.KafkaConfig) *Producer {
	p := &Producer{
		writer: &kafka.Writer{
			Addr:                   kafka.TCP(cfg.Brokers...),
			Topic:                  cfg.Topic,
			Balancer:               &kafka.LeastBytes{},
			BatchSize:              100,
			BatchTimeout:           10 * time.Millisecond,
			RequiredAcks:           kafka.RequireOne,
			Compression:            kafka.Snappy,
			AllowAutoTopicCreation: true,
		},
		topic: cfg.Topic,
		ch:    make(chan kafka.Message, 100000),
	}
	go p.loop()
	return p
}

// loop 后台写盘 worker：批量写 + 失败重试
func (p *Producer) loop() {
	const maxBatch = 500
	buf := make([]kafka.Message, 0, maxBatch)
	for {
		msg, ok := <-p.ch
		if !ok {
			return
		}
		buf = append(buf, msg)
		// 尽量凑批（最多等 5ms），避免逐条往返
	drain:
		for len(buf) < maxBatch {
			select {
			case m, ok := <-p.ch:
				if !ok {
					break drain
				}
				buf = append(buf, m)
			case <-time.After(5 * time.Millisecond):
				break drain
			}
		}

		var lastErr error
		for attempt := 0; attempt < 3; attempt++ {
			err := p.writer.WriteMessages(context.Background(), buf...)
			if err == nil {
				lastErr = nil
				break
			}
			lastErr = err
			// 仅对 topic 自动创建期间的错误重试
			if !strings.Contains(err.Error(), "Unknown Topic") &&
				!strings.Contains(err.Error(), "UNKNOWN_TOPIC") {
				break
			}
			time.Sleep(time.Duration(attempt+1) * 500 * time.Millisecond)
		}
		if lastErr != nil {
			p.mu.Lock()
			now := time.Now()
			if now.Sub(p.lastError) > 30*time.Second {
				log.Printf("[Kafka] Forward error (suppressed for 30s): %v", lastErr)
				p.lastError = now
			}
			p.errCount += len(buf)
			p.mu.Unlock()
		}
		buf = buf[:0]
	}
}

// ForwardTelemetry convert telemetry to Kafka message（非阻塞；队列满则丢弃并计数）
func (p *Producer) ForwardTelemetry(
	sn string, lat, lon, alt, heading float64,
	speed float64, battery float64, rssi int,
	flightPhase, flightPlanID, model string,
	windSpeedMs, temperatureC float64,
) error {
	msg := map[string]interface{}{
		"drone_sn":        sn,
		"model":           model,
		"latitude":        lat,
		"longitude":       lon,
		"altitude":        alt,
		"heading":         heading,
		"ground_speed":    speed,
		"battery_percent": int(battery),
		"rssi":            rssi,
		"flight_phase":    flightPhase,
		"flight_plan_id":  flightPlanID,
		"wind_speed_ms":   windSpeedMs,
		"temperature_c":   temperatureC,
		"timestamp":       time.Now().UnixMilli(),
	}

	data, _ := json.Marshal(msg)

	select {
	case p.ch <- kafka.Message{Key: []byte(sn), Value: data}:
	default:
		// 队列满：按背压策略丢弃，绝不能阻塞 MQTT 路由（心跳依赖它）
	}
	return nil
}

// Close close the producer
func (p *Producer) Close() {
	if p.writer != nil {
		p.writer.Close()
	}
}
