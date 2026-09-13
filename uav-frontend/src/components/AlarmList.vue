<template>
  <div class="panel-list">
    <div v-if="alarms.length === 0" class="empty">{{ $t('alarm.empty') }}</div>
    <div v-for="alarm in alarms.slice(0, 100)" :key="alarm.timestamp" class="item" :class="alarm.alarmLevel">
      <div class="item-header">
        <span class="level-tag">{{ alarm.alarmLevel }}</span>
        <span class="drone-sn">{{ alarm.droneSn?.substring(0, 12) }}</span>
        <span class="ts">{{ fmtTime(alarm.timestamp) }}</span>
      </div>
      <div class="item-body">{{ alarm.message }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AlarmEvent } from '@/stores/droneStore'
import { i18n } from '@/locales'

defineProps<{ alarms: AlarmEvent[] }>()

function fmtTime(ts: number) {
  const d = new Date(ts)
  return d.toLocaleTimeString(i18n.global.locale.value === 'en' ? 'en-US' : 'zh-CN', { hour12: false })
}
</script>

<style scoped>
.panel-list { overflow-y:auto; height:100%; padding:4px; }
.empty { text-align:center; color:#666; padding:40px 0; }
.item { padding:8px; margin-bottom:4px; border-radius:4px; background:rgba(255,255,255,0.05); border-left:3px solid #666; }
.item.GENERAL { border-left-color:#ffeb3b; }
.item.SERIOUS { border-left-color:#ff9800; }
.item.CRITICAL { border-left-color:#f44336; background:rgba(244,67,54,0.1); }
.item-header { display:flex; gap:8px; font-size:11px; color:#aaa; margin-bottom:4px; }
.level-tag { font-weight:bold; font-size:10px; }
.item-body { font-size:12px; color:#ddd; }
.ts { margin-left:auto; }
.drone-sn { color:#4fc3f7; }
</style>
