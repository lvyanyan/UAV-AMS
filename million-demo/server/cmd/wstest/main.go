// 一次性验证客户端：连上 /stress，读一帧二进制，校验帧格式与 Worker 解析一致
package main

import (
	"encoding/binary"
	"fmt"
	"log"
	"math"
	"time"

	"github.com/gorilla/websocket"
)

func main() {
	c, _, err := websocket.DefaultDialer.Dial("ws://localhost:8099/stress", nil)
	if err != nil {
		log.Fatalf("连接失败: %v", err)
	}
	defer c.Close()

	_ = c.SetReadDeadline(time.Now().Add(5 * time.Second))
	_, msg, err := c.ReadMessage()
	if err != nil {
		log.Fatalf("读帧失败: %v", err)
	}
	fmt.Printf("收到 %d 字节\n", len(msg))
	if len(msg) < 16 {
		log.Fatalf("帧太短")
	}

	magic := binary.LittleEndian.Uint32(msg[0:4])
	count := binary.LittleEndian.Uint32(msg[4:8])
	ts := binary.LittleEndian.Uint64(msg[8:16])
	fmt.Printf("magic=0x%X  count=%d  ts=%d  预期帧长=%d\n", magic, count, ts, 16+int(count)*20)
	if magic != 0x55534156 {
		log.Fatalf("魔数不符! got 0x%X", magic)
	}
	if int(count)*20+16 != len(msg) {
		log.Fatalf("长度不符: count=%d 预期=%d 实际=%d", count, 16+int(count)*20, len(msg))
	}

	// 第一架 + 最后一架
	d0 := decodeDrone(msg, 16, 0)
	dN := decodeDrone(msg, 16, int(count)-1)
	fmt.Printf("第0架:  lon=%.6f lat=%.6f alt=%.1f hdg=%.1f alert=%v\n", d0[0], d0[1], d0[2], d0[3], d0[4])
	fmt.Printf("末架:   lon=%.6f lat=%.6f alt=%.1f hdg=%.1f alert=%v\n", dN[0], dN[1], dN[2], dN[3], dN[4])
	fmt.Println("✅ 帧格式校验通过，与 Worker 解析对齐")
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
