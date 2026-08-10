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

// Producer Kafka producer with retry and auto-topic-creation
type Producer struct {
	writer    *kafka.Writer
	topic     string
	mu        sync.Mutex
	errCount  int
	lastError time.Time
}

// NewProducer create Kafka producer
func NewProducer(cfg config.KafkaConfig) *Producer {
	return &Producer{
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
	}
}

// ForwardTelemetry convert telemetry to Kafka message with retry
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

	// Retry up to 3 times for "Unknown Topic" (topic being auto-created)
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		err := p.writer.WriteMessages(context.Background(),
			kafka.Message{Key: []byte(sn), Value: data},
		)
		if err == nil {
			return nil
		}
		lastErr = err

		// Only retry for topic-not-found errors
		if strings.Contains(err.Error(), "Unknown Topic") ||
			strings.Contains(err.Error(), "UNKNOWN_TOPIC") {
			if attempt == 0 {
				log.Printf("[Kafka] Topic '%s' not ready, waiting for auto-creation...", p.topic)
			}
			time.Sleep(time.Duration(attempt+1) * 500 * time.Millisecond)
			continue
		}
		break
	}

	// Rate-limited error logging (once per 30s max)
	p.mu.Lock()
	now := time.Now()
	if now.Sub(p.lastError) > 30*time.Second {
		log.Printf("[Kafka] Forward error (suppressed for 30s): %v", lastErr)
		p.lastError = now
	}
	p.errCount++
	p.mu.Unlock()

	return lastErr
}

// Close close the producer
func (p *Producer) Close() {
	if p.writer != nil {
		p.writer.Close()
	}
}
