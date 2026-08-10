<template>
  <div class="panel-list">
    <div v-if="conflicts.length === 0" class="empty">暂无冲突</div>
    <div v-for="c in conflicts.slice(0, 50)" :key="c.timestamp" class="item" :class="c.probability > 0.5 ? 'high' : 'low'">
      <div class="pair">
        <span class="sn">{{ c.droneA?.substring(0,10) }}</span>
        <span class="vs">⟷</span>
        <span class="sn">{{ c.droneB?.substring(0,10) }}</span>
      </div>
      <div class="info">
        <span>碰撞概率: {{ (c.probability * 100).toFixed(0) }}%</span>
        <span>距: {{ c.minDistance?.toFixed(0) }}m</span>
        <span>TCPA: {{ c.timeToCPA?.toFixed(0) }}s</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ConflictAlert } from '@/stores/droneStore'
defineProps<{ conflicts: ConflictAlert[] }>()
</script>

<style scoped>
.panel-list { overflow-y:auto; height:100%; padding:4px; }
.empty { text-align:center; color:#666; padding:40px 0; }
.item { padding:8px; margin-bottom:4px; border-radius:4px; background:rgba(255,255,255,0.05); }
.item.high { background:rgba(244,67,54,0.15); border-left:3px solid #f44336; }
.item.low { border-left:3px solid #ff9800; }
.pair { font-size:12px; margin-bottom:4px; }
.sn { color:#4fc3f7; font-family:monospace; }
.vs { color:#ff9800; margin:0 6px; }
.info { font-size:11px; color:#aaa; display:flex; gap:12px; }
</style>
