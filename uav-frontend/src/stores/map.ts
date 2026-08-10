import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'

export interface DroneTelemetry {
  device_sn: string
  model: string
  timestamp: number
  position: { lat: number; lon: number; alt_m: number; heading: number }
  speed_ms: number
  battery_pct: number
  signal_rssi: number
  flight_phase: string
  flight_plan_id: string
}

export interface AlarmEvent {
  drone_sn: string
  alarm_type: string
  alarm_level: string
  alarm_msg: string
  lat: number
  lon: number
  alt_m: number
  timestamp: number
}

export const useMapStore = defineStore('map', () => {
  const drones: Ref<Map<string, DroneTelemetry>> = ref(new Map())
  const alarms: Ref<AlarmEvent[]> = ref([])
  const wsConnected = ref(false)
  let ws: WebSocket | null = null

  function connectWS() {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    ws = new WebSocket(`${protocol}//${location.hostname}:8081/ws`)
    ws.onopen = () => { wsConnected.value = true; console.log('[WS] Connected') }
    ws.onclose = () => { wsConnected.value = false; setTimeout(connectWS, 3000) }
    ws.onmessage = (evt) => {
      try {
        const msg = JSON.parse(evt.data)
        if (msg.type === 'telemetry' && msg.data) {
          const t = msg.data as DroneTelemetry
          drones.value.set(t.device_sn, t)
          // 触发 Vue 响应式
          drones.value = new Map(drones.value)
        }
        if (msg.type === 'alarm' && msg.data) {
          alarms.value = [msg.data as AlarmEvent, ...alarms.value].slice(0, 200)
        }
      } catch (e) { /* ignore */ }
    }
  }

  function disconnectWS() {
    ws?.close()
    ws = null
  }

  return { drones, alarms, wsConnected, connectWS, disconnectWS }
})
