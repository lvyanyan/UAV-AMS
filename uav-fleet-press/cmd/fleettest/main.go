// fleettest 一次性验证客户端：校验 :8091/fleet 二进制帧格式 + :8090 JSON 遥测链路
// 用法: go run ./cmd/fleettest
package main

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
)

func readFrame(c *websocket.Conn) {
	c.SetReadDeadline(time.Now().Add(5 * time.Second))
	_, msg, err := c.ReadMessage()
	if err != nil {
		log.Fatalf("读帧失败: %v", err)
	}
	if len(msg) < 16 {
		log.Fatalf("帧太短: %d", len(msg))
	}
	magic := binary.LittleEndian.Uint32(msg[0:4])
	count := binary.LittleEndian.Uint32(msg[4:8])
	switch magic {
	case 0x55534143: // cell
		expect := 16 + int(count)*16
		d0 := decodeCell(msg, 16)
		fmt.Printf("cell帧: count=%d 字节=%d 预期=%d 首格[lon=%.4f lat=%.4f cnt=%.0f alert=%.1f] ",
			count, len(msg), expect, d0[0], d0[1], d0[2], d0[3])
		if len(msg) != expect {
			log.Fatalf("❌ cell帧长度不符")
		}
		fmt.Println("✅")
	case 0x55534156: // raw
		expect := 16 + int(count)*20
		d0 := decodeDrone(msg, 16, 0)
		fmt.Printf("raw帧: count=%d 字节=%d 预期=%d 首点[lon=%.4f lat=%.4f alt=%.0f alert=%.0f] ",
			count, len(msg), expect, d0[0], d0[1], d0[2], d0[4])
		if len(msg) != expect {
			log.Fatalf("❌ raw帧长度不符")
		}
		fmt.Println("✅")
	default:
		log.Fatalf("❌ 未知魔数 0x%X", magic)
	}
}

func decodeCell(buf []byte, base int) [4]float64 {
	o := base
	return [4]float64{
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o : o+4]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+4 : o+8]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+8 : o+12]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+12 : o+16]))),
	}
}

func decodeDrone(buf []byte, base int, idx int) [5]float64 {
	o := base + idx*20
	return [5]float64{
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o : o+4]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+4 : o+8]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+8 : o+12]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+12 : o+16]))),
		float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[o+16 : o+20]))),
	}
}

func main() {
	// 1) 默认视口（未上报）→ 高空 cell 聚合帧
	c, _, err := websocket.DefaultDialer.Dial("ws://localhost:8091/fleet", nil)
	if err != nil {
		log.Fatalf("连接 8091 失败: %v", err)
	}
	defer c.Close()
	fmt.Print("[1] 默认高空视口 → ")
	readFrame(c)

	// 2) 上报低空视野 → raw 裁剪帧
	c.WriteMessage(websocket.TextMessage,
		[]byte(`{"h":3000,"minLat":39.85,"maxLat":39.95,"minLon":116.35,"maxLon":116.45,"full":false}`))
	fmt.Print("[2] 低空视野3000m → ")
	readFrame(c)

	// 3) resize 到 100 万，仍走 cell
	resp, err := http.Get("http://localhost:8091/resize?count=1000000")
	if err != nil {
		log.Fatalf("resize 失败: %v", err)
	}
	resp.Body.Close()
	time.Sleep(300 * time.Millisecond)
	fmt.Print("[3] resize→100万 后 → ")
	readFrame(c)

	// 4) stats 在线数
	resp2, _ := http.Get("http://localhost:8091/stats")
	var stats map[string]int
	json.NewDecoder(resp2.Body).Decode(&stats)
	resp2.Body.Close()
	fmt.Printf("[4] /stats → %+v", stats)
	if stats["count"] != 1000000 {
		log.Fatalf("❌ 在线数不符")
	}
	fmt.Println(" ✅")

	// 5) 8090 JSON 遥测链路（2 秒采样）
	j, _, err := websocket.DefaultDialer.Dial("ws://localhost:8090/ws", nil)
	if err != nil {
		log.Fatalf("连接 8090 失败: %v", err)
	}
	defer j.Close()
	j.SetReadDeadline(time.Now().Add(3 * time.Second))
	telemetry, alarms := 0, 0
	for {
		_, msg, err := j.ReadMessage()
		if err != nil {
			break
		}
		var m map[string]interface{}
		if json.Unmarshal(msg, &m) == nil {
			switch m["type"] {
			case "telemetry":
				telemetry++
			case "uav.alarm.event":
				alarms++
			}
		}
		if time.Now().After(time.Now().Add(-2 * time.Second)) && telemetry+alarms > 0 && telemetry+alarms >= 5 {
			break
		}
	}
	fmt.Printf("[5] 8090 JSON 2秒采样: telemetry=%d alarm=%d", telemetry, alarms)
	if telemetry+alarms == 0 {
		log.Fatalf(" → ❌ JSON 链路无数据（simulator/MQTT 未跑？）")
	}
	fmt.Println(" ✅")

	// 恢复默认 10 万
	http.Get("http://localhost:8091/resize?count=100000")
	fmt.Println("=== 全部通过（已恢复默认 10 万） ===")
}
