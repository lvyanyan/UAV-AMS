<template>
  <div class="timeline-control">
    <div class="timeline-inner">
      <!-- 播放控制 -->
      <div class="play-controls">
        <button class="ctrl-btn" @click="$emit('toggle-play')" :title="isPlaying ? '暂停' : '播放'">
          {{ isPlaying ? '⏸️' : '▶️' }}
        </button>
      </div>

      <!-- 进度条 -->
      <div
        class="progress-bar"
        :class="{ 'progress-disabled': disabled }"
        ref="progressBar"
        @click="onProgressClick"
        :title="disabled ? '正在更新轨迹，请稍候...' : '拖动跳转时间'"
      >
        <div class="progress-fill" :style="{ width: progress * 100 + '%' }">
          <div class="progress-thumb"></div>
        </div>
        <div class="progress-time">
          <!-- ★ 显示实际累计时间（不取模），分母显示 totalDuration 周期 -->
          {{ formatTime(currentTime) }} / {{ formatTime(totalDuration) }}
        </div>
        <!-- 加载指示器 -->
        <div v-if="disabled" class="progress-loading"></div>
      </div>

      <!-- 倍速选择 -->
      <div class="speed-controls">
        <button
          v-for="(opt, idx) in SPEED_OPTIONS"
          :key="opt.value"
          class="speed-btn"
          :class="{ 'speed-active': speedIndex === idx }"
          @click="$emit('set-speed', idx)"
        >
          {{ opt.label }}
        </button>
      </div>

      <!-- 无人机统计 -->
      <div class="drone-stats">
        <span class="stat-icon">🛸</span>
        <span class="stat-count">{{ formatNumber(droneCount) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { SPEED_OPTIONS } from '../utils/constants.js'

const props = defineProps({
  currentTime: { type: Number, required: true },
  isPlaying: { type: Boolean, default: false },
  speedIndex: { type: Number, default: 1 },
  progress: { type: Number, default: 0 },
  droneCount: { type: Number, default: 0 },
  totalDuration: { type: Number, default: 300 },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-play', 'set-speed', 'seek'])

const progressBar = ref(null)

function onProgressClick(event) {
  if (props.disabled) return
  if (!progressBar.value) return
  const rect = progressBar.value.getBoundingClientRect()
  const percent = (event.clientX - rect.left) / rect.width
  emit('seek', Math.max(0, Math.min(1, percent)))
}

function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatNumber(num) {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString()
}
</script>

<style scoped>
.timeline-control {
  position: absolute;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(10, 20, 40, 0.88);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(0, 200, 255, 0.25);
  border-radius: 12px;
  padding: 10px 20px;
  min-width: 600px;
  z-index: 100;
  user-select: none;
}

.timeline-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.play-controls {
  display: flex;
  align-items: center;
}

.ctrl-btn {
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s;
  line-height: 1;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 3px;
  position: relative;
  cursor: pointer;
  min-width: 200px;
  transition: opacity 0.2s;
}

.progress-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #00c8ff, #00ff88);
  border-radius: 3px;
  position: relative;
  transition: width 0.05s linear;
}

.progress-thumb {
  width: 14px;
  height: 14px;
  background: white;
  border-radius: 50%;
  position: absolute;
  right: -7px;
  top: 50%;
  transform: translateY(-50%);
  box-shadow: 0 0 8px rgba(0, 200, 255, 0.5);
}

.progress-time {
  position: absolute;
  top: -20px;
  right: 0;
  font-size: 11px;
  color: #667788;
  font-family: 'Courier New', monospace;
}

.progress-loading {
  position: absolute;
  top: -8px;
  left: 0;
  width: 100%;
  height: 22px;
  border-radius: 3px;
  background: repeating-linear-gradient(
    90deg,
    transparent,
    transparent 10px,
    rgba(0, 200, 255, 0.08) 10px,
    rgba(0, 200, 255, 0.08) 20px
  );
  animation: loading-slide 0.6s linear infinite;
  pointer-events: none;
}

@keyframes loading-slide {
  from { background-position: 0 0; }
  to { background-position: 40px 0; }
}

.speed-controls {
  display: flex;
  gap: 4px;
}

.speed-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #8899aa;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.speed-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #e0f0ff;
}

.speed-active {
  background: rgba(0, 200, 255, 0.2);
  border-color: rgba(0, 200, 255, 0.4);
  color: #00c8ff;
  font-weight: 600;
}

.drone-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
}

.stat-icon {
  font-size: 16px;
}

.stat-count {
  color: #00ff88;
  font-size: 14px;
  font-weight: 600;
  font-family: 'Courier New', monospace;
}
</style>
