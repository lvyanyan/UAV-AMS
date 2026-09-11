package movement

import "math"

// Drone 一架无人机的最小状态（无 mutex：每个分片独占自己的切片）
type Drone struct {
	Lon      float32
	Lat      float32
	Alt      float32
	Heading  float32 // 度
	SpeedMs  float32
	HomeLon  float32
	HomeLat  float32
	RngSeed  int64
	AlertCode int8 // 0=正常 1=警告 2=危险
}

// UpdateShard 推进一个分片的无人机位置（等距矩形 dead-reckoning）。
// 调用方独占该切片，无锁。
func UpdateShard(drones []Drone, dt float64, maxRadiusKm float64) {
	for i := range drones {
		d := &drones[i]
		speed := float64(d.SpeedMs) * dt
		cosLat := math.Cos(float64(d.Lat) * math.Pi / 180.0)

		// heading 驱动平移（flat-earth 近似，几十公里内足够）
		d.Lon += float32(speed * math.Sin(float64(d.Heading)*math.Pi/180.0) / (111000.0 * cosLat))
		d.Lat += float32(speed * math.Cos(float64(d.Heading)*math.Pi/180.0) / 111000.0)

		// 确定性航向漂移（每架种子不同 → 轨迹各异但可复现）
		drift := 0.5 * dt * float64(d.RngSeed%7-3)
		d.Heading += float32(drift)
		for d.Heading < 0 {
			d.Heading += 360
		}
		for d.Heading >= 360 {
			d.Heading -= 360
		}

		// 高度小幅波动（纯视觉）
		d.Alt += float32(math.Sin(float64(d.RngSeed)*0.3) * 2 * dt)

		// 超出半径 → 折返
		dlon := float64(d.Lon - d.HomeLon)
		dlat := float64(d.Lat - d.HomeLat)
		distKm := math.Sqrt(dlon*dlon*cosLat*cosLat*111000*111000+dlat*dlat*111000*111000) / 1000.0
		if distKm > maxRadiusKm {
			d.Heading += 180
			for d.Heading >= 360 {
				d.Heading -= 360
			}
		}
	}
}
