<template>
  <div class="alert-panel" :class="{ collapsed }">
    <div class="alert-header" @click="collapsed = !collapsed">
      <span class="ap-title"><el-icon><BellFilled /></el-icon> {{ $t('alarm.panelTitle', { n: alerts.length }) }}</span>
      <span class="toggle">{{ collapsed ? $t('common.expand') : $t('common.collapse') }}</span>
      <button class="clear-btn" @click.stop="$emit('clear')">{{ $t('common.clear') }}</button>
    </div>
    <div v-show="!collapsed" class="alert-body">
      <div v-if="alerts.length === 0" class="no-alert">{{ $t('alarm.empty') }}</div>
      <div v-for="(a, i) in alerts" :key="i" class="alert-item" :class="[a.level, { blackflight: a.type === 'NO_FLIGHT_PLAN' }]" @click="$emit('focus', a.sn)">
        <span class="time">{{ a.time }}</span>
        <span class="level-tag" :class="{ hf: a.type === 'NO_FLIGHT_PLAN' }">{{ a.levelLabel || a.level }}</span>
        <span class="sn">{{ a.sn }}</span>
        <span class="type">{{ a.typeLabel || a.type }}</span>
        <span class="desc">{{ a.desc }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
defineProps({ alerts: { type: Array, default: () => [] } })
defineEmits(['focus', 'clear'])
const collapsed = ref(false)
</script>

<style scoped>
@keyframes alertPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.alert-panel {
  position: absolute; bottom: 10px; right: 10px; width: 420px;
  background: rgba(6, 13, 24, 0.88); color: #ccc; border-radius: 8px;
  font-size: 12px; z-index: 20; border: 1px solid rgba(240, 82, 79, 0.35);
  max-height: 260px; overflow: hidden; backdrop-filter: blur(8px);
  box-shadow: 0 4px 18px rgba(0,0,0,0.5), 0 0 12px rgba(240, 82, 79, 0.12);
}
.alert-header {
  display: flex; align-items: center; gap: 8px; padding: 6px 10px;
  background: linear-gradient(90deg, rgba(240, 82, 79, 0.14), transparent); cursor: pointer; user-select: none;
  border-bottom: 1px solid rgba(240, 82, 79, 0.3);
}
.alert-header span:first-child { flex: 1; color: #ff4444; font-weight: bold; }
.toggle { color: #888; cursor: pointer; }
.clear-btn {
  background: #333; color: #aaa; border: none; border-radius: 3px;
  padding: 2px 8px; cursor: pointer; font-size: 11px;
}
.clear-btn:hover { background: #555; color: #fff; }
.alert-body { max-height: 210px; overflow-y: auto; padding: 2px 0; }
.no-alert { text-align: center; color: #0a0; padding: 12px; }
.alert-item {
  display: flex; gap: 6px; padding: 4px 10px; cursor: pointer;
  align-items: center; border-bottom: 1px solid #222;
}
.alert-item:hover { background: rgba(255,255,255,0.05); }
/* 黑飞专属：黑色徽标 + 行高亮 */
.alert-item.blackflight { background: rgba(0, 0, 0, 0.55); box-shadow: inset 3px 0 0 #0d0d0d; }
.level-tag.hf { background: #0d0d0d !important; color: #fff !important; border: 1px solid #4a4a4a; animation: none !important; }
.time { color: #888; min-width: 60px; }
.level-tag { min-width: 50px; padding: 1px 4px; border-radius: 3px; text-align: center; font-weight: bold; }
.level-tag.WARNING   { background: #aa6600; color: #fff; }
.level-tag.MINOR     { background: #883300; color: #fff; }
.level-tag.MAJOR     { background: #cc2200; color: #fff; }
.level-tag.CRITICAL  { background: #cc0000; color: #fff; animation: alertPulse 1s infinite; }
.level-tag.EMERGENCY { background: #cc00cc; color: #fff; animation: alertPulse 0.5s infinite; }
.sn { color: #4da3ff; min-width: 100px; }
.type { color: #fa0; min-width: 70px; }
.desc { color: #aaa; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
