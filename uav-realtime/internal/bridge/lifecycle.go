// Package bridge 计划指令桥与生命周期回流：
//   1) 消费 Kafka uav.plan.cmd → 按 action 转发 MQTT uav/{sn}/cmd（payload 原样 + ts）；
//   2) 监听遥测 flight_phase 边沿 → 发 Kafka uav.plan.lifecycle（TAKEOFF_ACK / COMPLETED）。
package bridge

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/segmentio/kafka-go"
)

// Kafka topic 契约（与 uav-flight-plan 的 PlanEventProducer/PlanLifecycleConsumer 对齐）
const (
	TopicPlanCmd       = "uav.plan.cmd"
	TopicPlanLifecycle = "uav.plan.lifecycle"

	// cmd 桥消费组名（独立于 ws 广播组，避免相互影响位点）
	cmdBridgeGroupID = "realtime-cmd-bridge"
)

// MQTTPublisher MQTT 发布最小接口（由 internal/mqtt.Client 实现）
type MQTTPublisher interface {
	Publish(topic string, payload []byte) error
}

// StartCmdBridge 消费 uav.plan.cmd，转发到 uav/{sn}/cmd。
// payload 原样透传，仅补充 ts（转发时间戳，毫秒）。
func StartCmdBridge(ctx context.Context, brokers []string, pub MQTTPublisher) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  brokers,
		Topic:    TopicPlanCmd,
		GroupID:  cmdBridgeGroupID,
		MinBytes: 1,
		MaxBytes: 10e6,
		MaxWait:  500 * time.Millisecond,
	})
	defer reader.Close()
	log.Printf("[CmdBridge] Listening %s -> uav/{sn}/cmd (group=%s)", TopicPlanCmd, cmdBridgeGroupID)

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("[CmdBridge] Read error: %v", err)
			time.Sleep(time.Second)
			continue
		}

		var cmd map[string]interface{}
		if err := json.Unmarshal(msg.Value, &cmd); err != nil {
			log.Printf("[CmdBridge] 非法指令 payload: %v", err)
			continue
		}
		sn, _ := cmd["droneSn"].(string)
		if sn == "" {
			log.Printf("[CmdBridge] 指令缺少 droneSn，丢弃: %s", string(msg.Value))
			continue
		}
		cmd["ts"] = time.Now().UnixMilli()
		data, _ := json.Marshal(cmd)

		topic := "uav/" + sn + "/cmd"
		if err := pub.Publish(topic, data); err != nil {
			log.Printf("[CmdBridge] MQTT 发布失败 [%s]: %v", topic, err)
		} else {
			log.Printf("[CmdBridge] %s -> %s: %s", TopicPlanCmd, topic, string(data))
		}
	}
}

// Telemetry 边沿检测所需的最小遥测字段（鸭子类型，避免与 internal/mqtt 结构耦合）
type Telemetry struct {
	DeviceSN     string
	FlightPhase  string
	FlightPlanID string
	Timestamp    int64 // 毫秒
}

// LifecyclePublisher 遥测相位边沿检测 → uav.plan.lifecycle 事件发布。
//   起飞边沿：前相位非 TAKEOFF/FLYING → TAKEOFF（或首次观测到 FLYING）→ TAKEOFF_ACK + actualStart
//   降落边沿：前相位非 LANDED → LANDED → COMPLETED + actualEnd
// 边沿判断基于本进程内每 SN 的上一条遥测快照（latest map 语义），planCode 取遥测里的
// flight_plan_id；未绑定计划的遥测（flight_plan_id 为空）不产生事件，压测机队不会刷屏。
type LifecyclePublisher struct {
	mu     sync.Mutex
	prev   map[string]string // sn -> 上一次 flight_phase 快照
	writer *kafka.Writer
	ch     chan kafka.Message
}

// NewLifecyclePublisher 创建边沿检测器（单后台 goroutine 异步写 Kafka，自动建 topic）
func NewLifecyclePublisher(brokers []string) *LifecyclePublisher {
	p := &LifecyclePublisher{
		prev: make(map[string]string),
		writer: &kafka.Writer{
			Addr:                   kafka.TCP(brokers...),
			Topic:                  TopicPlanLifecycle,
			Balancer:               &kafka.LeastBytes{},
			RequiredAcks:           kafka.RequireOne,
			AllowAutoTopicCreation: true,
		},
		ch: make(chan kafka.Message, 4096),
	}
	go p.loop()
	return p
}

// loop 后台写盘 worker：写 Kafka 失败只记日志（生命周期事件容忍丢失，不允许阻塞遥测链路）
func (p *LifecyclePublisher) loop() {
	for msg := range p.ch {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		if err := p.writer.WriteMessages(ctx, msg); err != nil {
			log.Printf("[Lifecycle] Kafka 写入失败: %v", err)
		}
		cancel()
	}
}

// OnTelemetry 相位边沿检测入口（线程安全；不阻塞 MQTT 路由 goroutine）
func (p *LifecyclePublisher) OnTelemetry(t Telemetry) {
	if t.DeviceSN == "" || t.FlightPlanID == "" {
		return
	}

	p.mu.Lock()
	prev := p.prev[t.DeviceSN]
	p.prev[t.DeviceSN] = t.FlightPhase
	p.mu.Unlock()

	if prev == t.FlightPhase {
		return // 非边沿
	}

	var action, tsField string
	inAirNow := t.FlightPhase == "TAKEOFF" || t.FlightPhase == "FLYING"
	inAirBefore := prev == "TAKEOFF" || prev == "FLYING"

	switch {
	case !inAirBefore && inAirNow:
		action, tsField = "TAKEOFF_ACK", "actualStart"
	case prev != "LANDED" && t.FlightPhase == "LANDED":
		action, tsField = "COMPLETED", "actualEnd"
	default:
		return
	}

	payload, _ := json.Marshal(map[string]interface{}{
		"action":   action,
		"planCode": t.FlightPlanID,
		"droneSn":  t.DeviceSN,
		tsField:    t.Timestamp,
		"phase":    t.FlightPhase,
	})

	// 队列满直接丢弃：生命周期事件允许极小概率丢失，绝不能拖垮遥测链路
	select {
	case p.ch <- kafka.Message{Key: []byte(t.DeviceSN), Value: payload}:
		log.Printf("[Lifecycle] %s plan=%s sn=%s phase=%s", action, t.FlightPlanID, t.DeviceSN, t.FlightPhase)
	default:
		log.Printf("[Lifecycle] 事件队列已满，丢弃 %s (sn=%s)", action, t.DeviceSN)
	}
}

// Close 关闭底层 writer。
// 注：不 close(p.ch)——进程退出前 OnTelemetry 仍可能并发写入，直接关 writer 即可（通道随进程回收）。
func (p *LifecyclePublisher) Close() {
	if p.writer != nil {
		_ = p.writer.Close()
	}
}
