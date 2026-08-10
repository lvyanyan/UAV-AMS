import { defineStore } from 'pinia'
import { ref } from 'vue'
import { wsClient } from '@/api/websocket'

export interface DroneState {
  droneSn: string
  latitude: number
  longitude: number
  altitude: number
  heading: number
  groundSpeed: number
  batteryPercent: number
  rssi: number
  flightPhase: string
  flightPlanId: string
  model: string
  timestamp: number
}

export interface AlarmEvent {
  droneSn: string
  alarmType: string
  alarmLevel: string
  message: string
  latitude?: number
  longitude?: number
  timestamp: number
}

export interface ConflictAlert {
  droneA: string
  droneB: string
  probability: number
  minDistance: number
  timeToCPA: number
  timestamp: number
}

export interface ResolutionCommand {
  droneSn: string
  strategy: string
  parameter: number
  reason: string
  timestamp: number
}

export const useDroneStore = defineStore('drone', () => {
  const drones = ref<Map<string, DroneState>>(new Map())
  const alarms = ref<AlarmEvent[]>([])
  const conflicts = ref<ConflictAlert[]>([])
  const resolutions = ref<ResolutionCommand[]>([])
  const connected = ref(false)

  function init() {
    wsClient.onMessage((type, data) => {
      switch (type) {
        case 'telemetry':
          handleTelemetry(data)
          break
        case 'uav.alarm.event':
          handleAlarm(data)
          break
        case 'uav.conflict.alert':
        case 'uav.signboard.conflict':
          handleConflict(data)
          break
        case 'uav.signboard.resolution':
          handleResolution(data)
          break
      }
    })
    wsClient.connect()
    connected.value = true
  }

  function handleTelemetry(data: any) {
    const d = data?.data || data
    if (!d.droneSn) {
      // 可能是包装格式: { data: { lat, lon, ... } }
      // 尝试从 device_sn 字段映射
      const sn = d.device_sn || d.droneSn
      if (!sn) return
      const state: DroneState = {
        droneSn: sn,
        latitude: d.position?.lat ?? d.latitude ?? 0,
        longitude: d.position?.lon ?? d.longitude ?? 0,
        altitude: d.position?.alt_m ?? d.altitude ?? 0,
        heading: d.position?.heading ?? d.heading ?? 0,
        groundSpeed: d.speed_ms ?? d.groundSpeed ?? 0,
        batteryPercent: d.battery_pct ?? d.batteryPercent ?? 0,
        rssi: d.signal_rssi ?? d.rssi ?? 0,
        flightPhase: d.flight_phase ?? d.flightPhase ?? 'FLYING',
        flightPlanId: d.flight_plan_id ?? d.flightPlanId ?? '',
        model: d.model ?? 'Unknown',
        timestamp: d.timestamp ?? Date.now()
      }
      drones.value.set(sn, state)
    } else {
      drones.value.set(d.droneSn, d as DroneState)
    }
    // 限制地图上的无人机数量
    if (drones.value.size > 5000) {
      const keys = [...drones.value.keys()]
      for (let i = 0; i < keys.length - 5000; i++) {
        drones.value.delete(keys[i])
      }
    }
  }

  function handleAlarm(data: any) {
    const alarm: AlarmEvent = {
      droneSn: data.droneSn || '',
      alarmType: data.alarmType || data.type || '',
      alarmLevel: data.alarmLevel || data.level || 'WARNING',
      message: data.message || data.detail || '',
      timestamp: data.timestamp || Date.now()
    }
    alarms.value.unshift(alarm)
    if (alarms.value.length > 1000) alarms.value.length = 1000
  }

  function handleConflict(data: any) {
    const conflict: ConflictAlert = {
      droneA: data.droneA || data.drone_a || '',
      droneB: data.droneB || data.drone_b || '',
      probability: data.probability || data.collisionProbability || 0,
      minDistance: data.minDistance || data.minDistanceMeters || 0,
      timeToCPA: data.timeToCPA || data.tcpaSeconds || 0,
      timestamp: data.timestamp || Date.now()
    }
    conflicts.value.unshift(conflict)
    if (conflicts.value.length > 500) conflicts.value.length = 500
  }

  function handleResolution(data: any) {
    const resolution: ResolutionCommand = {
      droneSn: data.droneSn || data.targetDroneSn || '',
      strategy: data.strategy || data.resolutionType || '',
      parameter: data.parameter || data.targetValue || 0,
      reason: data.reason || data.cause || '',
      timestamp: data.timestamp || Date.now()
    }
    resolutions.value.unshift(resolution)
    if (resolutions.value.length > 500) resolutions.value.length = 500
  }

  return { drones, alarms, conflicts, resolutions, connected, init }
})
