import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令：v-permission="'system:user:create'"
 * 当前用户权限码列表不包含该码时直接移除 DOM 元素
 * 用法示例：<el-button v-permission="'system:role:assign'">分配权限</el-button>
 */
export const permission: Directive<HTMLElement, string | undefined> = {
  mounted(el, binding) {
    const code = binding.value
    const store = useUserStore()
    if (!store.hasPerm(code)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permission
