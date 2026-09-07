package mqtt

import (
	"context"
	"encoding/json"
	"log"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/uav-ams/uav-realtime/internal/config"
)

// Telemetry 无人机遥测数据（来自仿真器/真实设备）
type Telemetry struct {
	DeviceSN    string   `json:"device_sn"`
	Model       string   `json:"model"`
	Timestamp   int64    `json:"timestamp"`
	Position    Position `json:"position"`
	SpeedMs     float64  `json:"speed_ms"`
	BatteryPct  float64  `json:"battery_pct"`
	SignalRSSI  int      `json:"signal_rssi"`
	FlightPhase string   `json:"flight_phase"`
	FlightPlanID string  `json:"flight_plan_id"`
	WindSpeedMs float64  `json:"wind_speed_ms"`
	TemperatureC float64 `json:"temperature_c"`
}

// Position 位置
type Position struct {
	Lat     float64 `json:"lat"`
	Lon     float64 `json:"lon"`
	AltM    float64 `json:"alt_m"`
	Heading float64 `json:"heading"`
}

// Client MQTT 客户端封装
type Client struct {
	cfg           config.MQTTConfig
	client        mqtt.Client
	telemetryHandlers []TelemetryHandler
	kafkaForwarders  []KafkaForwarder
	connected     bool
}

// TelemetryHandler WebSocket 广播回调
type TelemetryHandler func(t *Telemetry)

// KafkaForwarder Kafka 转发回调
type KafkaForwarder func(t *Telemetry)

// NewClient 创建 MQTT 客户端
func NewClient(cfg config.MQTTConfig) (*Client, error) {
	c := &Client{cfg: cfg}
	return c, nil
}

// Start 连接并订阅遥测主题（带重试）
func (c *Client) Start(ctx context.Context) {
	opts := mqtt.NewClientOptions().
		AddBroker(c.cfg.Broker).
		SetClientID(c.cfg.ClientID).
		SetUsername(c.cfg.Username).
		SetPassword(c.cfg.Password).
		SetAutoReconnect(true).
		SetMaxReconnectInterval(30 * time.Second).
		SetConnectTimeout(10 * time.Second).
		SetOnConnectHandler(func(client mqtt.Client) {
			// 订阅必须挂在 OnConnect：clean_start 会话在每次重连后都需重新订阅
			topic := "uav/+/telemetry"
			token := client.Subscribe(topic, c.cfg.QoS, c.onTelemetryMessage)
			token.Wait()
			if token.Error() != nil {
				log.Printf("[MQTT] 订阅失败: %v", token.Error())
			} else {
				log.Printf("[MQTT] 已订阅: %s（连接/重连自动重订）", topic)
			}
		})

	c.client = mqtt.NewClient(opts)

	// 重试连接（最多 30 次，每次间隔 2 秒 = 60 秒总超时）
	for i := 0; i < 30; i++ {
		select {
		case <-ctx.Done():
			return
		default:
		}

		token := c.client.Connect()
		token.Wait()
		if token.Error() == nil {
			c.connected = true
			log.Printf("[MQTT] ✅ 已连接: %s", c.cfg.Broker)
			break
		}
		log.Printf("[MQTT] ⚠️ 连接失败 (第%d次): %v, 2秒后重试...", i+1, token.Error())
		time.Sleep(2 * time.Second)
	}

	if !c.connected {
		log.Printf("[MQTT] ❌ 30次连接均失败，放弃")
		return
	}

}

// OnTelemetry 注册 WebSocket 广播处理器
func (c *Client) OnTelemetry(handler TelemetryHandler) {
	c.telemetryHandlers = append(c.telemetryHandlers, handler)
}

// OnTelemetryForKafka 注册 Kafka 转发处理器
func (c *Client) OnTelemetryForKafka(forwarder KafkaForwarder) {
	c.kafkaForwarders = append(c.kafkaForwarders, forwarder)
}

func (c *Client) onTelemetryMessage(_ mqtt.Client, msg mqtt.Message) {
	var t Telemetry
	if err := json.Unmarshal(msg.Payload(), &t); err != nil {
		log.Printf("[MQTT] Failed to parse telemetry: %v", err)
		return
	}
	for _, h := range c.telemetryHandlers {
		h(&t)
	}
	for _, f := range c.kafkaForwarders {
		f(&t)
	}
}

// IsConnected 检查连接状态
func (c *Client) IsConnected() bool {
	return c.connected && c.client != nil && c.client.IsConnected()
}

// Disconnect 断开连接
func (c *Client) Disconnect() {
	if c.client != nil && c.client.IsConnected() {
		c.client.Disconnect(250)
	}
}
