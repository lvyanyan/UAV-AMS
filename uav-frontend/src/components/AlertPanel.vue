<template>
  <div class="alert-panel" :class="{ collapsed }">
    <div class="alert-header" @click="collapsed = !collapsed">
      <span>🚨 告警列表 ({{ alerts.length }})</span>
      <span class="toggle">{{ collapsed ? '展开' : '收起' }}</span>
      <button class="clear-btn" @click.stop="$emit('clear')">清空</button>
    </div>
    <div v-show="!collapsed" class="alert-body">
      <div v-if="alerts.length === 0" class="no-alert">暂无告警 ✅</div>
      <div v-for="(a, i) in alerts" :key="i" class="alert-item" :class="a.level" @click="$emit('focus', a.sn)">
        <span class="time">{{ a.time }}</span>
        <span class="level-tag">{{ a.level }}</span>
        <span class="sn">{{ a.sn }}</span>
        <span class="type">{{ a.type }}</span>
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
  background: rgba(0,0,0,0.85); color: #ccc; border-radius: 6px;
  font-size: 12px; z-index: 20; border: 1px solid #333;
  max-height: 260px; overflow: hidden;
}
.alert-header {
  display: flex; align-items: center; gap: 8px; padding: 6px 10px;
  background: #1a1a2e; cursor: pointer; user-select: none;
  border-bottom: 1px solid #333;
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
.time { color: #888; min-width: 60px; }
.level-tag { min-width: 50px; padding: 1px 4px; border-radius: 3px; text-align: center; font-weight: bold; }
.level-tag.WARNING   { background: #aa6600; color: #fff; }
.level-tag.MINOR     { background: #883300; color: #fff; }
.level-tag.MAJOR     { background: #cc2200; color: #fff; }
.level-tag.CRITICAL  { background: #cc0000; color: #fff; animation: alertPulse 1s infinite; }
.level-tag.EMERGENCY { background: #cc00cc; color: #fff; animation: alertPulse 0.5s infinite; }
.sn { color: #0af; min-width: 100px; }
.type { color: #fa0; min-width: 70px; }
.desc { color: #aaa; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
