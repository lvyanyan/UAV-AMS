package drone

import (
	"math"
	"math/rand"
)

// FlightPathGenerator 飞行路径生成器
type FlightPathGenerator struct {
	rng *rand.Rand
}

// NewFlightPathGenerator 创建路径生成器
func NewFlightPathGenerator(rng *rand.Rand) *FlightPathGenerator {
	return &FlightPathGenerator{rng: rng}
}

// GenerateWaypoints 生成随机航点序列
func (g *FlightPathGenerator) GenerateWaypoints(home Position, radiusKm, minAlt, maxAlt float64, pointCount int) []Position {
	waypoints := make([]Position, 0, pointCount)

	// 起点 = home
	waypoints = append(waypoints, home)

	for i := 1; i < pointCount; i++ {
		wp := g.randomPointNear(home, radiusKm, minAlt, maxAlt)
		waypoints = append(waypoints, wp)
	}

	// 终点 = home (闭合路径)
	waypoints = append(waypoints, home)

	return waypoints
}

// GenerateDeliveryPath 生成配送路径 (A→B→C...→返回)
func (g *FlightPathGenerator) GenerateDeliveryPath(home Position, radiusKm, minAlt, maxAlt float64) []Position {
	waypoints := make([]Position, 0, 4)
	waypoints = append(waypoints, home)

	// 2-4个配送点
	deliveryPoints := 2 + g.rng.Intn(3)
	for i := 0; i < deliveryPoints; i++ {
		wp := g.randomPointNear(home, radiusKm, minAlt, maxAlt)
		waypoints = append(waypoints, wp)
	}

	// 返回
	waypoints = append(waypoints, home)
	return waypoints
}

// GeneratePatrolPath 生成巡逻路径 (网格或环形)
func (g *FlightPathGenerator) GeneratePatrolPath(home Position, radiusKm, minAlt, maxAlt float64) []Position {
	// 螺旋/矩形巡逻路径
	steps := 4 + g.rng.Intn(4)
	angleStep := 2 * math.Pi / float64(steps)
	waypoints := make([]Position, 0, steps+2)

	waypoints = append(waypoints, home)

	radiusDeg := radiusKm / 111.0 // 近似

	for i := 0; i < steps; i++ {
		angle := float64(i) * angleStep
		// 向外螺旋
		r := radiusDeg * (0.3 + 0.7*float64(i)/float64(steps-1))
		lat := home.Lat + r*math.Sin(angle)
		lon := home.Lon + r*math.Cos(angle)
		alt := minAlt + g.rng.Float64()*(maxAlt-minAlt)

		waypoints = append(waypoints, Position{
			Lat:  lat,
			Lon:  lon,
			AltM: alt,
		})
	}

	waypoints = append(waypoints, home)
	return waypoints
}

// randomPointNear 在 home 附近生成随机点
func (g *FlightPathGenerator) randomPointNear(home Position, radiusKm, minAlt, maxAlt float64) Position {
	angle := g.rng.Float64() * 2 * math.Pi
	distanceKm := g.rng.Float64() * radiusKm

	// 近似转换 (纬度1度≈111km, 经度取决于纬度)
	latOffset := distanceKm / 111.0 * math.Cos(angle)
	lonOffset := distanceKm / (111.0 * math.Cos(home.Lat*math.Pi/180.0)) * math.Sin(angle)

	return Position{
		Lat:  home.Lat + latOffset,
		Lon:  home.Lon + lonOffset,
		AltM: minAlt + g.rng.Float64()*(maxAlt-minAlt),
	}
}

// CalculateHeading 计算两点间的航向角
func CalculateHeading(from, to Position) float64 {
	dLat := to.Lat - from.Lat
	dLon := to.Lon - from.Lon

	// 简化的平面近似
	angle := math.Atan2(dLon, dLat) * 180.0 / math.Pi
	if angle < 0 {
		angle += 360
	}
	return angle
}

// CalculateDistance 计算两点间距离 (米)
func CalculateDistance(a, b Position) float64 {
	const earthRadiusM = 6371000.0

	lat1 := a.Lat * math.Pi / 180.0
	lat2 := b.Lat * math.Pi / 180.0
	dLat := (b.Lat - a.Lat) * math.Pi / 180.0
	dLon := (b.Lon - a.Lon) * math.Pi / 180.0

	sinDLat := math.Sin(dLat / 2)
	sinDLon := math.Sin(dLon / 2)

	a2 := sinDLat*sinDLat + math.Cos(lat1)*math.Cos(lat2)*sinDLon*sinDLon
	c := 2 * math.Atan2(math.Sqrt(a2), math.Sqrt(1-a2))

	return earthRadiusM * c
}

// InterpolatePosition 线性插值位置
func InterpolatePosition(from, to Position, t float64) Position {
	return Position{
		Lat:  from.Lat + (to.Lat-from.Lat)*t,
		Lon:  from.Lon + (to.Lon-from.Lon)*t,
		AltM: from.AltM + (to.AltM-from.AltM)*t,
	}
}
