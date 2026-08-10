<template>
  <div class="control-panel">
    <div class="row stats">
      <span class="badge" :class="{ ok: connected, no: !connected }">
        {{ connected ? '● 已连接' : '○ 未连接' }}
      </span>
      <span>🛸 接收 <b>{{ format(droneCount) }}</b> 架</span>
      <span>🎨 渲染 <b>{{ format(drawnCount) }}</b> 点</span>
      <span>📊 rAF <b>{{ fps }}</b> FPS</span>
      <span>📡 <b>{{ mode }}</b></span>
      <span v-if="dropped > 0" class="warn">⬇丢帧 <b>{{ dropped }}</b></span>
    </div>

    <div class="row">
      <label class="lbl">无人机数（服务端重建）</label>
      <div class="slider-row">
        <input
          type="range"
          min="10000"
          max="1000000"
          step="10000"
          v-model.number="localCount"
          class="slider"
          @change="onResize"
        />
        <span class="val">{{ format(localCount) }}</span>
      </div>
    </div>

    <div class="row">
      <label class="lbl">抽稀因子（1=全画，N=画 1/N）</label>
      <div class="slider-row">
        <select v-model.number="localDecimate" class="sel" @change="onDecimate">
          <option :value="1">1（最少抽稀）</option>
          <option :value="2">2</option>
          <option :value="5">5</option>
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
        <span class="val">实际 {{ format(drawnCount) }} 点</span>
      </div>
    </div>

    <div class="row">
      <label class="chk">
        <input type="checkbox" :checked="debugFps" @change="onDebugFps" />
        Cesium 原生 debug FPS（左上角）
      </label>
    </div>

    <div class="row">
      <button class="btn" @click="$emit('reconnect')">重连 WS</button>
    </div>

    <div class="hint">
      v5 服务端视锥聚合：client 上报相机视野 →<br />
      · 高空(>10km)：server 聚合到网格(点大小∝密度)<br />
      · 低空(<10km)：server 只推视野内原始点+航向billboard<br />
      带宽从全量100MB/s 降到 ~MB/s 级。模式显示"聚合/原始"。
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  connected: Boolean,
  droneCount: { type: Number, default: 0 },
  drawnCount: { type: Number, default: 0 },
  fps: { type: Number, default: 0 },
  dropped: { type: Number, default: 0 },
  debugFps: { type: Boolean, default: true },
  mode: { type: String, default: '--' }
})

const emit = defineEmits(['reconnect', 'resize', 'set-decimate', 'toggle-debug-fps'])

const localCount = ref(100000)
const localDecimate = ref(1)

function onResize() {
  emit('resize', localCount.value)
  fetch(`/resize?count=${localCount.value}`, { method: 'GET' }).catch(() => {})
}

function onDecimate() {
  emit('set-decimate', localDecimate.value)
}

function onDebugFps(e) {
  emit('toggle-debug-fps', e.target.checked)
}

function format(n) {
  if (n >= 10000) return (n / 10000).toFixed(n % 10000 === 0 ? 0 : 1) + '万'
  return String(n)
}
</script>

<style scoped>
.control-panel {
  position: absolute; bottom: 10px; right: 10px; z-index: 10;
  background: rgba(0,0,0,0.78); color: #e0e0e0;
  padding: 12px 14px; border-radius: 8px; width: 280px;
  font-size: 13px; line-height: 1.5;
  display: flex; flex-direction: column; gap: 10px;
  max-height: calc(100% - 20px); overflow-y: auto;
}
.row { display: flex; flex-direction: column; gap: 4px; }
.row.stats { flex-direction: row; flex-wrap: wrap; gap: 12px; align-items: center; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.badge.ok { background: #1b5e20; color: #b9f6ca; }
.badge.no { background: #4a0000; color: #ffccbc; }
.lbl { color: #9dd; font-size: 12px; }
.chk { display: flex; align-items: center; gap: 6px; color: #cde; font-size: 12px; cursor: pointer; }
.chk input { margin: 0; }
.warn { color: #f96; }
.slider-row { display: flex; align-items: center; gap: 8px; }
.slider { flex: 1; }
.sel { background: #222; color: #fff; border: 1px solid #555; padding: 3px 6px; border-radius: 4px; }
.val { color: #ffd; min-width: 50px; text-align: right; font-family: monospace; }
.btn { background: #2a5; color: #fff; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; }
.btn:hover { background: #3c7; }
b { color: #6f6; }
.hint { color: #888; font-size: 11px; margin-top: 2px; }
</style>
