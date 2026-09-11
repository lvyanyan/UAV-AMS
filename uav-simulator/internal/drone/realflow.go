package drone

import (
	"math/rand"
)

// RealMission 真实飞行流程任务（与 deploy/sql/seed_realflow.sql 的飞行计划一一对应）
type RealMission struct {
	PlanCode    string    // 飞行计划编号 = flight_plan.plan_code
	SN          string    // 已实名登记的无人机 = uav_registration.drone_sn
	Model       string
	Purpose     string    // 任务性质
	Operator    string    // 运营主体
	Departure   string    // 起飞点名称
	Destination string    // 降落点名称
	Waypoints   []Position // 起飞点 → 途经航点 → 降落点（巡航高度）
}

// DefaultRealMissions 真实流程演示任务集（北京大兴—亦庄—通州—廊坊）
var DefaultRealMissions = []RealMission{
	{
		PlanCode: "FP-20260911-0001", SN: "REG-UAV-0001", Model: "DJI-M350",
		Purpose: "物流配送", Operator: "华北低空运营集团有限公司",
		Departure: "大兴机场南起降点", Destination: "廊坊高新区物流枢纽",
		Waypoints: []Position{
			{Lat: 39.5104, Lon: 116.3907, AltM: 280},
			{Lat: 39.5520, Lon: 116.5100, AltM: 280},
			{Lat: 39.5230, Lon: 116.6850, AltM: 280},
		},
	},
	{
		PlanCode: "FP-20260911-0002", SN: "REG-UAV-0002", Model: "DJI-M30T",
		Purpose: "电力巡检", Operator: "华北低空运营集团有限公司",
		Departure: "亦庄健康监测点", Destination: "通州大运河巡检段",
		Waypoints: []Position{
			{Lat: 39.7850, Lon: 116.5020, AltM: 250},
			{Lat: 39.8120, Lon: 116.5800, AltM: 250},
			{Lat: 39.8620, Lon: 116.6500, AltM: 250},
		},
	},
	{
		PlanCode: "FP-20260911-0003", SN: "REG-UAV-0003", Model: "CUSTOM-C300",
		Purpose: "航空测绘", Operator: "中翼航空科技服务有限公司",
		Departure: "通州航测基准点", Destination: "亦庄东工业区",
		Waypoints: []Position{
			{Lat: 39.8620, Lon: 116.6300, AltM: 300},
			{Lat: 39.8250, Lon: 116.5800, AltM: 300},
			{Lat: 39.7920, Lon: 116.5320, AltM: 300},
		},
	},
	{
		PlanCode: "FP-20260911-0004", SN: "REG-UAV-0004", Model: "DJI-FLYCART30",
		Purpose: "应急通信保障", Operator: "华北低空运营集团有限公司",
		Departure: "大兴区应急物资库", Destination: "廊坊高新区物流枢纽",
		Waypoints: []Position{
			{Lat: 39.7230, Lon: 116.3520, AltM: 250},
			{Lat: 39.6350, Lon: 116.5000, AltM: 250},
			{Lat: 39.5230, Lon: 116.6850, AltM: 250},
		},
	},
	{
		PlanCode: "FP-20260911-0005", SN: "REG-UAV-0005", Model: "DJI-MAVIC3",
		Purpose: "航空测绘", Operator: "京南通用航空股份有限公司",
		Departure: "通州大运河巡检段", Destination: "大兴区观测场",
		Waypoints: []Position{
			{Lat: 39.8620, Lon: 116.6500, AltM: 320},
			{Lat: 39.7950, Lon: 116.4800, AltM: 320},
			{Lat: 39.7120, Lon: 116.3420, AltM: 320},
		},
	},
	{
		PlanCode: "FP-20260911-0006", SN: "REG-UAV-0006", Model: "DJI-AIR3S",
		Purpose: "警用巡逻", Operator: "京南通用航空股份有限公司",
		Departure: "亦庄滨河公园", Destination: "大兴区机场路沿线",
		Waypoints: []Position{
			{Lat: 39.7920, Lon: 116.5020, AltM: 180},
			{Lat: 39.7020, Lon: 116.4530, AltM: 180},
			{Lat: 39.6010, Lon: 116.4020, AltM: 180},
		},
	},
}

// CreateRealFlowFleet 创建执行真实飞行计划的机队
// 每架无人机绑定一条已审批飞行计划，按 起飞→航点巡航→降落→充电→重飞 轮换
func (s *Simulator) CreateRealFlowFleet(missions []RealMission) {
	for i := range missions {
		m := missions[i]
		home := Position{Lat: m.Waypoints[0].Lat, Lon: m.Waypoints[0].Lon, AltM: 0}
		d := NewDrone(m.SN, m.Model, home, rand.New(rand.NewSource(s.rng.Int63())))
		d.SpeedMs = 14 + s.rng.Float64()*8
		d.realMissions = []RealMission{m}
		d.startNextRealMission()
		s.drones[m.SN] = d
	}
}
