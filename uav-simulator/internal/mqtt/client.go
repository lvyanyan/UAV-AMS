package mqtt

import (
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/uav-ams/uav-simulator/internal/drone"
)

// Publisher MQTT 发布器
type Publisher struct {
	client   mqtt.Client
	config   PublisherConfig
	mu       sync.Mutex
	connected bool
	msgSent   int64
	errCount  int64
}

// PublisherConfig 发布器配置
type PublisherConfig struct {
	Broker   string
	ClientID string
	Username string
	Password string
	QoS      byte
	Retained bool
}

// NewPublisher 创建 MQTT 发布器
func NewPublisher(cfg PublisherConfig) *Publisher {
	return &Publisher{
		config: cfg,
	}
}

// Connect 连接 MQTT Broker
func (p *Publisher) Connect() error {
	opts := mqtt.NewClientOptions().
		AddBroker(p.config.Broker).
		SetClientID(p.config.ClientID).
		SetCleanSession(true).
		SetKeepAlive(30 * time.Second).
		SetPingTimeout(10 * time.Second).
		SetConnectTimeout(15 * time.Second).
		SetAutoReconnect(true).
		SetMaxReconnectInterval(10 * time.Second).
		SetConnectionLostHandler(func(client mqtt.Client, err error) {
			log.Printf("[MQTT] 连接断开: %v, 自动重连中...", err)
			p.mu.Lock()
			p.connected = false
			p.mu.Unlock()
		}).
		SetOnConnectHandler(func(client mqtt.Client) {
			log.Printf("[MQTT] 已连接至 %s", p.config.Broker)
			p.mu.Lock()
			p.connected = true
			p.mu.Unlock()
		})

	if p.config.Username != "" {
		opts.SetUsername(p.config.Username)
	}
	if p.config.Password != "" {
		opts.SetPassword(p.config.Password)
	}

	p.client = mqtt.NewClient(opts)
	token := p.client.Connect()
	if token.Wait() && token.Error() != nil {
		return fmt.Errorf("MQTT 连接失败: %w", token.Error())
	}

	return nil
}

// Disconnect 断开连接
func (p *Publisher) Disconnect() {
	if p.client != nil && p.client.IsConnected() {
		p.client.Disconnect(500)
	}
}

// IsConnected 检查连接状态
func (p *Publisher) IsConnected() bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.connected
}

// PublishTelemetry 发布遥测数据
func (p *Publisher) PublishTelemetry(t drone.Telemetry) error {
	topic := fmt.Sprintf("uav/%s/telemetry", t.DeviceSN)
	return p.publish(topic, t)
}

// PublishHeartbeat 发布心跳
func (p *Publisher) PublishHeartbeat(d *drone.Drone) error {
	d.Lock()
	t := d.GetTelemetry()
	d.Unlock()

	topic := fmt.Sprintf("uav/%s/heartbeat", d.DeviceSN)
	hb := map[string]interface{}{
		"device_sn":    d.DeviceSN,
		"flight_phase": d.FlightPhase,
		"battery_pct":  d.BatteryPct,
		"signal_rssi":  d.SignalRSSI,
		"timestamp":    time.Now().UnixMilli(),
	}
	_ = t // 可复用遥测数据
	return p.publish(topic, hb)
}

// PublishEvent 发布飞行事件
func (p *Publisher) PublishEvent(deviceSN, eventType string, data map[string]interface{}) error {
	topic := fmt.Sprintf("uav/%s/event", deviceSN)
	event := map[string]interface{}{
		"device_sn":  deviceSN,
		"event_type": eventType,
		"data":       data,
		"timestamp":  time.Now().UnixMilli(),
	}
	return p.publish(topic, event)
}

// PublishViolation 发布违规事件
func (p *Publisher) PublishViolation(deviceSN string, violationType drone.ViolationType, detail string) error {
	topic := fmt.Sprintf("uav/%s/event", deviceSN)
	event := map[string]interface{}{
		"device_sn":      deviceSN,
		"event_type":     "VIOLATION",
		"violation_type": violationType,
		"detail":         detail,
		"timestamp":      time.Now().UnixMilli(),
	}
	return p.publish(topic, event)
}

// PublishBatch 批量发布遥测（使用 MQTT 异步发送提升吞吐量）
func (p *Publisher) PublishBatch(telemetryList []drone.Telemetry) {
	var wg sync.WaitGroup
	sem := make(chan struct{}, 100) // 并发100个发布

	for _, t := range telemetryList {
		wg.Add(1)
		sem <- struct{}{}

		go func(tel drone.Telemetry) {
			defer wg.Done()
			defer func() { <-sem }()

			if err := p.PublishTelemetry(tel); err != nil {
				p.mu.Lock()
				p.errCount++
				p.mu.Unlock()
			} else {
				p.mu.Lock()
				p.msgSent++
				p.mu.Unlock()
			}
		}(t)
	}

	wg.Wait()
}

// GetStats 获取统计
func (p *Publisher) GetStats() (sent, errors int64) {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.msgSent, p.errCount
}

// publish 内部发布方法
func (p *Publisher) publish(topic string, payload interface{}) error {
	data, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("序列化失败: %w", err)
	}

	token := p.client.Publish(topic, p.config.QoS, p.config.Retained, data)
	if token.Wait() && token.Error() != nil {
		return fmt.Errorf("发布失败 [%s]: %w", topic, token.Error())
	}

	return nil
}
