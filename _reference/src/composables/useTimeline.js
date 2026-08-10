/**
 * 时间轴状态管理（V7.4 动态配置）
 * 手动时间驱动，支持倍速播放与跳转
 * 在 currentTime 达到 totalDuration 时自动暂停
 */
import { ref, computed } from 'vue'

export function useTimeline() {
  const currentTime = ref(0)
  const isPlaying = ref(false)
  const speedIndex = ref(1)
  const speedFactor = ref(1)
  const isSeeking = ref(false)

  // ★ V7.4: totalDuration 由外部设置（从 GET /api/config 获取）
  const totalDuration = ref(3600)  // 兜底值

  // 当前进度（0~1）
  const progress = computed(() => {
    if (totalDuration.value <= 0) return 0
    return Math.min(currentTime.value / totalDuration.value, 1)
  })

  const SPEED_VALUES = [0.5, 1, 2, 5, 10]

  /**
   * ★ V7.4: 设置总时长（从配置接口获取后调用）
   */
  function setTotalDuration(duration) {
    if (duration > 0) {
      totalDuration.value = duration
      console.log(`[useTimeline] 总时长设置为 ${duration}s`)
    }
  }

  function setSpeed(index) {
    if (index < 0 || index >= SPEED_VALUES.length) return
    speedIndex.value = index
    speedFactor.value = SPEED_VALUES[index]
    console.log(`[useTimeline] 倍速设置为 ${speedFactor.value}x`)
  }

  function togglePlay() {
    // ★ 如果已经播完，从头开始
    if (currentTime.value >= totalDuration.value) {
      currentTime.value = 0
    }
    isPlaying.value = !isPlaying.value
    console.log(`[useTimeline] 播放状态: ${isPlaying.value ? '播放' : '暂停'}`)
  }

  let _lastLogSecond = -1

  /**
   * 每帧更新
   * @param {number} delta - 真实时间差（秒）
   */
  function update(delta) {
    if (!isPlaying.value) return

    // 累加时间
    currentTime.value += delta * speedFactor.value

    // ★ 超过总时长时自动暂停
    if (currentTime.value >= totalDuration.value) {
      currentTime.value = totalDuration.value
      isPlaying.value = false
      console.log('[useTimeline] 回放完成，自动暂停')
    }

    // 每秒日志
    const currentSecond = Math.floor(currentTime.value)
    if (currentSecond !== _lastLogSecond) {
      if (_lastLogSecond !== -1) {
        const deltaSecond = currentSecond - _lastLogSecond
        console.log(
          `[useTimeline] 时间刻度 ${currentSecond}s, 间隔 ${deltaSecond}s (倍速 ${speedFactor.value}x, 进度 ${(progress.value * 100).toFixed(1)}%)`
        )
      }
      _lastLogSecond = currentSecond
    }
  }

  function seekTo(percent) {
    currentTime.value = Math.max(0, Math.min(totalDuration.value, percent * totalDuration.value))
    _lastLogSecond = Math.floor(currentTime.value)
    console.log(`[useTimeline] 跳转到 ${currentTime.value.toFixed(1)}s (${(percent * 100).toFixed(1)}%)`)
  }

  function reset() {
    currentTime.value = 0
    isPlaying.value = false
    speedIndex.value = 1
    speedFactor.value = 1
    isSeeking.value = false
    _lastLogSecond = -1
    console.log('[useTimeline] 重置')
  }

  return {
    currentTime,
    isPlaying,
    speedIndex,
    speedFactor,
    progress,
    totalDuration,
    isSeeking,
    update,
    setSpeed,
    togglePlay,
    seekTo,
    reset,
    setTotalDuration
  }
}
