package fleet

import (
	"encoding/binary"
	"math"
	"sync"

	"million-demo/server/internal/movement"
)

// 帧头魔数 "UAVV"（little-endian 0x55534156）
const Magic uint32 = 0x55534156

// 聚合网格帧魔数 "USAC"（cell aggregation）
const CellMagic uint32 = 0x55534143

// 帧头 16B + 每架 20B（5 × float32）
const HeaderSize = 16
const BytesPerDrone = 20
const CellBytes = 16          // 每网格 16B：[lon_f32, lat_f32, count_f32, alert_f32]
const MaxRawInView = 50000    // 低空单帧最多推的原始点数

// Viewport 客户端相机视野
type Viewport struct {
	Height   float64 `json:"h"`
	MinLat   float64 `json:"minLat"`
	MaxLat   float64 `json:"maxLat"`
	MinLon   float64 `json:"minLon"`
	MaxLon   float64 `json:"maxLon"`
	Full     bool    `json:"full"` // 客户端请求全量广播（百万 demo），忽略视野
}

const cellThresholdHeight = 10000.0 // 米，高于此用网格聚合，低于此推原始点

func (vp Viewport) IsHigh() bool { return vp.Height >= cellThresholdHeight }

// cellDeg 由高度自适应：高空大格、低空小格。
// 修正：之前 height/111000 在 50km 高空给出 0.45°，百万架只落进几个格子 → 全红 + 闪烁。
// 现按"屏幕覆盖"计算：每个格子约对应屏宽 1/30，下限 0.003°(≈300m)，上限 0.05°(≈5km)。
func (vp Viewport) CellDeg() float64 {
	// 视野跨度约 = height 度（粗略），分 30~80 格
	span := vp.Height / 111000.0
	d := span / 50.0
	if d < 0.003 {
		d = 0.003
	}
	if d > 0.05 {
		d = 0.05
	}
	return d
}

const ShardCount = 8

// Fleet 百万级无人机机群，按 8 分片并行更新
type Fleet struct {
	shards [ShardCount][]movement.Drone
	count  int
}

// New 按 golden-angle 螺旋分布创建 count 架无人机
func New(count int, centerLat, centerLon, radiusKm float64) *Fleet {
	f := &Fleet{count: count}
	cosCenterLat := math.Cos(centerLat * math.Pi / 180.0)
	per := count / ShardCount

	for s := 0; s < ShardCount; s++ {
		start := s * per
		end := start + per
		if s == ShardCount-1 {
			end = count // 末片吃余数
		}
		f.shards[s] = make([]movement.Drone, end-start)
		for i := range f.shards[s] {
			idx := start + i
			angle := float64(idx) * 2 * math.Pi * 1.618 // golden angle
			ratio := math.Sqrt(float64(idx) / float64(count))
			distKm := ratio * radiusKm * 0.95
			latOff := distKm / 111.0 * math.Cos(angle)
			lonOff := distKm / (111.0 * cosCenterLat) * math.Sin(angle)

			d := &f.shards[s][i]
			d.Lon = float32(centerLon + lonOff)
			d.Lat = float32(centerLat + latOff)
			d.Alt = float32(50.0 + float64(idx%250))
			d.Heading = float32(float64(idx) * 0.73)
			d.SpeedMs = float32(5.0 + float64(idx%15))
			d.HomeLon = d.Lon
			d.HomeLat = d.Lat
			d.RngSeed = int64(idx * 137)
			switch {
			case idx%100 == 0:
				d.AlertCode = 2
			case idx%20 == 0:
				d.AlertCode = 1
			default:
				d.AlertCode = 0
			}
		}
	}
	return f
}

func (f *Fleet) Count() int { return f.count }

// FrameSize 一帧的字节数
func (f *Fleet) FrameSize() int { return HeaderSize + f.count*BytesPerDrone }

// Update 8 分片并行推进位置
func (f *Fleet) Update(dt, maxRadiusKm float64) {
	var wg sync.WaitGroup
	for s := 0; s < ShardCount; s++ {
		wg.Add(1)
		go func(shard []movement.Drone) {
			defer wg.Done()
			movement.UpdateShard(shard, dt, maxRadiusKm)
		}(f.shards[s])
	}
	wg.Wait()
}

// WriteFrame 把整个机群写成二进制帧到 dst（dst 长度需 >= FrameSize）
func (f *Fleet) WriteFrame(dst []byte, tsMs int64) {
	binary.LittleEndian.PutUint32(dst[0:4], Magic)
	binary.LittleEndian.PutUint32(dst[4:8], uint32(f.count))
	binary.LittleEndian.PutUint64(dst[8:16], uint64(tsMs))

	off := HeaderSize
	for s := 0; s < ShardCount; s++ {
		for i := range f.shards[s] {
			d := &f.shards[s][i]
			binary.LittleEndian.PutUint32(dst[off:off+4], math.Float32bits(d.Lon))
			binary.LittleEndian.PutUint32(dst[off+4:off+8], math.Float32bits(d.Lat))
			binary.LittleEndian.PutUint32(dst[off+8:off+12], math.Float32bits(d.Alt))
			binary.LittleEndian.PutUint32(dst[off+12:off+16], math.Float32bits(d.Heading))
			binary.LittleEndian.PutUint32(dst[off+16:off+20], math.Float32bits(float32(d.AlertCode)))
			off += BytesPerDrone
		}
	}
}

// WriteCellFrame 把机群聚合到网格后写出（密度热力）。cellDeg=网格度数。
// 格式：CellMagic + count + ts + count×16B [lon,lat,count,alert]
// 返回写入字节数。dst 大小不足时按 cap 截断。
func (f *Fleet) WriteCellFrame(dst []byte, tsMs int64, cellDeg float64) int {
	type key struct{ lat, lon int32 }
	type agg struct {
		sumLat, sumLon float64
		count          int
		alertSum       int
	}
	cells := make(map[key]*agg, 1<<14)
	inv := 1.0 / cellDeg
	for s := 0; s < ShardCount; s++ {
		for i := range f.shards[s] {
			d := &f.shards[s][i]
			k := key{int32(float64(d.Lat) * inv), int32(float64(d.Lon) * inv)}
			a := cells[k]
			if a == nil {
				a = &agg{}
				cells[k] = a
			}
			a.sumLat += float64(d.Lat)
			a.sumLon += float64(d.Lon)
			a.count++
			a.alertSum += int(d.AlertCode)
		}
	}

	// 头
	binary.LittleEndian.PutUint32(dst[0:4], CellMagic)
	binary.LittleEndian.PutUint32(dst[4:8], uint32(len(cells)))
	binary.LittleEndian.PutUint64(dst[8:16], uint64(tsMs))

	// 上限保护：dst 不够时按可用格子数写
	maxCells := (len(dst) - HeaderSize) / CellBytes
	off := HeaderSize
	written := 0
	for _, a := range cells {
		if written >= maxCells {
			// 修正 count 字段为实际写入数
			binary.LittleEndian.PutUint32(dst[4:8], uint32(written))
			break
		}
		binary.LittleEndian.PutUint32(dst[off:off+4], math.Float32bits(float32(a.sumLon/float64(a.count))))   // cell 中心 lon
		binary.LittleEndian.PutUint32(dst[off+4:off+8], math.Float32bits(float32(a.sumLat/float64(a.count)))) // cell 中心 lat
		binary.LittleEndian.PutUint32(dst[off+8:off+12], math.Float32bits(float32(a.count)))
		binary.LittleEndian.PutUint32(dst[off+12:off+16], math.Float32bits(float32(a.alertSum/a.count))) // 平均告警级
		off += CellBytes
		written++
	}
	return off
}

// WriteViewFrame 低空：只写视野 bbox 内的原始无人机，上限 MaxRawInView。
// 格式：Magic + count + ts + count×20B（同 raw）。返回写入字节数。
func (f *Fleet) WriteViewFrame(dst []byte, tsMs int64, vp Viewport) int {
	minLat := float32(vp.MinLat)
	maxLat := float32(vp.MaxLat)
	minLon := float32(vp.MinLon)
	maxLon := float32(vp.MaxLon)

	off := HeaderSize
	written := 0
	maxDrones := (len(dst) - HeaderSize) / BytesPerDrone
	if maxDrones > MaxRawInView {
		maxDrones = MaxRawInView
	}
	for s := 0; s < ShardCount && written < maxDrones; s++ {
		for i := range f.shards[s] {
			d := &f.shards[s][i]
			if d.Lat < minLat || d.Lat > maxLat || d.Lon < minLon || d.Lon > maxLon {
				continue
			}
			binary.LittleEndian.PutUint32(dst[off:off+4], math.Float32bits(d.Lon))
			binary.LittleEndian.PutUint32(dst[off+4:off+8], math.Float32bits(d.Lat))
			binary.LittleEndian.PutUint32(dst[off+8:off+12], math.Float32bits(d.Alt))
			binary.LittleEndian.PutUint32(dst[off+12:off+16], math.Float32bits(d.Heading))
			binary.LittleEndian.PutUint32(dst[off+16:off+20], math.Float32bits(float32(d.AlertCode)))
			off += BytesPerDrone
			written++
			if written >= maxDrones {
				break
			}
		}
	}
	binary.LittleEndian.PutUint32(dst[0:4], Magic)
	binary.LittleEndian.PutUint32(dst[4:8], uint32(written))
	binary.LittleEndian.PutUint64(dst[8:16], uint64(tsMs))
	return off
}
