<template>
  <div class="panel-list">
    <div v-if="resolutions.length === 0" class="empty">暂无解脱指令</div>
    <div v-for="r in resolutions.slice(0, 50)" :key="r.timestamp" class="item">
      <div class="header">
        <span class="sn">{{ r.droneSn?.substring(0, 12) }}</span>
        <span class="strategy">{{ r.strategy }}</span>
      </div>
      <div class="body">
        <span>参数: {{ r.parameter }}</span>
        <span class="reason">{{ r.reason }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ResolutionCommand } from '@/stores/droneStore'
defineProps<{ resolutions: ResolutionCommand[] }>()
</script>

<style scoped>
.panel-list { overflow-y:auto; height:100%; padding:4px; }
.empty { text-align:center; color:#666; padding:40px 0; }
.item { padding:8px; margin-bottom:4px; background:rgba(33,150,243,0.1); border-left:3px solid #2196f3; border-radius:4px; }
.header { display:flex; gap:8px; margin-bottom:4px; }
.sn { color:#4fc3f7; font-family:monospace; font-size:12px; }
.strategy { color:#ff9800; font-size:11px; font-weight:bold; }
.body { font-size:11px; color:#aaa; display:flex; gap:12px; }
.reason { color:#ccc; }
</style>
