/**
 * 列表页前端分页：传入数据源的 getter，返回分页状态与当前页切片
 */
import { ref, computed, watch, type Ref } from 'vue'

export function usePaging<T>(source: Ref<T[]> | (() => T[]), defaultSize = 20) {
  const page = ref(1)
  const size = ref(defaultSize)

  const list = computed(() => (typeof source === 'function' ? source() : source.value))
  const total = computed(() => list.value.length)
  const paged = computed(() => {
    const start = (page.value - 1) * size.value
    return list.value.slice(start, start + size.value)
  })

  // 数据变化（筛选/刷新）时收敛页码，避免停在超出范围的空页
  watch(total, () => {
    const max = Math.max(1, Math.ceil(total.value / size.value))
    if (page.value > max) page.value = max
  })

  return { page, size, total, paged }
}
