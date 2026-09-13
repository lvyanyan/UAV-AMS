<template>
  <div class="panel-list">
    <div v-if="drones.size === 0" class="empty">{{ $t('drone.waiting') }}</div>
    <div v-for="[sn, drone] in [...drones.entries()].slice(0, 200)" :key="sn" class="drone-row">
      <span class="sn">{{ sn.substring(0, 10) }}</span>
      <span class="phase" :style="{color: phaseColor(drone.flightPhase)}">{{ drone.flightPhase }}</span>
      <span class="batt">{{ drone.batteryPercent?.toFixed(0) }}%</span>
      <span class="alt">{{ drone.altitude?.toFixed(0) }}m</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DroneState } from '@/stores/droneStore'
defineProps<{ drones: Map<string, DroneState> }>()

function phaseColor(p: string) {
  const m: Record<string, string> = { FLYING: '#4caf50', TAKEOFF: '#ff9800', RETURNING: '#2196f3', EMERGENCY: '#f44336', IDLE: '#666' }
  return m[p] || '#999'
}
</script>

<style scoped>
.panel-list { overflow-y:auto; height:100%; padding:4px; }
.empty { text-align:center; color:#666; padding:40px 0; }
.drone-row { display:flex; gap:8px; padding:4px 8px; font-size:11px; border-bottom:1px solid rgba(255,255,255,0.05); align-items:center; }
.sn { color:#4fc3f7; font-family:monospace; flex:1; }
.phase { width:70px; text-align:center; }
.batt { width:40px; text-align:right; }
.alt { width:50px; text-align:right; color:#aaa; }
</style>
