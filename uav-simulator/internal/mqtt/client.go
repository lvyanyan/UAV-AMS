package mqtt

import (
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/uav-ams/uav-simulator/internal/drone"
)

// Publisher MQTT 发布器
type Publisher struct {
	client    mqtt.Client
	config    PublisherConfig
	mu        sync.Mutex
	connected bool
	msgSent   int64
	errCount  int64

	// 平台指令回调（uav/+/cmd 订阅分发）
	onCommand CommandHandler
}

// CommandHandler 平台指令回调：sn=目标机 SN，action=TAKEOFF/RTL/LAND/ABORT，planCode=关联计划号
type CommandHandler func(sn, action, planCode string)

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

			// 订阅平台指令主题（挂在 OnConnect：重连后自动重订）
			if p.onCommand != nil {
				topic := "uav/+/cmd"
				token := client.Subscribe(topic, p.config.QoS, p.onCommandMessage)
				token.Wait()
				if token.Error() != nil {
					log.Printf("[MQTT] 订阅 %s 失败: %v", topic, token.Error())
				} else {
					log.Printf("[MQTT] 已订阅指令主题: %s", topic)
				}
			}
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

// OnCommand 注册平台指令处理器（须在 Connect 前调用，随连接建立自动订阅）
func (p *Publisher) OnCommand(handler CommandHandler) {
	p.mu.Lock()
	p.onCommand = handler
	p.mu.Unlock()
}

// onCommandMessage 解析 uav/{sn}/cmd 指令并分发
func (p *Publisher) onCommandMessage(_ mqtt.Client, msg mqtt.Message) {
	parts := strings.Split(msg.Topic(), "/")
	if len(parts) != 3 || parts[0] != "uav" || parts[2] != "cmd" || parts[1] == "" {
		log.Printf("[MQTT] 无法从主题解析 SN: %s", msg.Topic())
		return
	}
	sn := parts[1]
	var cmd struct {
		Action   string `json:"action"`
		PlanCode string `json:"planCode"`
		Ts       int64  `json:"ts"`
	}
	if err := json.Unmarshal(msg.Payload(), &cmd); err != nil {
		log.Printf("[MQTT] 指令解析失败 [%s]: %v", msg.Topic(), err)
		return
	}
	p.mu.Lock()
	handler := p.onCommand
	p.mu.Unlock()
	if handler != nil {
		handler(sn, cmd.Action, cmd.PlanCode)
	}
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
