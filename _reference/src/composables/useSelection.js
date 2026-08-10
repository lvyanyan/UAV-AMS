/**
 * 点选交互与信息跟随
 * 通过 SceneTransforms.wgs84ToWindowCoordinates 实现 DOM 标牌跟随
 */

import { ref, shallowRef } from 'vue'
import * as Cesium from 'cesium'

export function useSelection(viewer) {
  const selectedId = ref(null)
  const selectedIndex = ref(-1)
  const selectedPosition = ref({ x: 0, y: 0 })
  const isSelected = ref(false)

  let screenSpaceEventHandler = null

  /**
   * 注册点击事件
   * @param {Function} onPick - 拾取回调，返回索引
   */
  function init(onPick) {
    if (!viewer.value) return

    const v = viewer.value
    screenSpaceEventHandler = new Cesium.ScreenSpaceEventHandler(v.scene.canvas)

    // 左键点击拾取
    screenSpaceEventHandler.setInputAction((event) => {
      const picked = v.scene.pick(event.position)

      if (picked && picked.primitive && picked.primitive.id !== undefined) {
        const index = picked.primitive.id
        selectedIndex.value = index
        selectedId.value = `UAV-${String(index + 1).padStart(4, '0')}`
        isSelected.value = true

        // 回调通知外部
        if (onPick) onPick(index)
      } else {
        // 点击空白区域取消选中
        if (!event.position || !event.position.x) {
          // 检查是否点击了其他UI元素
          return
        }
        selectedId.value = null
        selectedIndex.value = -1
        isSelected.value = false
        if (onPick) onPick(-1)
      }
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK)
  }

  /**
   * 更新 DOM 标牌位置（在 postRender 中调用）
   */
  function updateLabelPosition(cartesianPosition) {
    if (!viewer.value || !cartesianPosition) return

    const screenPos = Cesium.SceneTransforms.wgs84ToWindowCoordinates(
      viewer.value.scene,
      cartesianPosition
    )

    if (screenPos) {
      selectedPosition.value = {
        x: screenPos.x,
        y: screenPos.y
      }
    }
  }

  /**
   * 清除选中
   */
  function clearSelection() {
    selectedId.value = null
    selectedIndex.value = -1
    isSelected.value = false
  }

  /**
   * 销毁
   */
  function destroy() {
    if (screenSpaceEventHandler) {
      screenSpaceEventHandler.destroy()
      screenSpaceEventHandler = null
    }
  }

  return {
    selectedId,
    selectedIndex,
    selectedPosition,
    isSelected,
    init,
    updateLabelPosition,
    clearSelection,
    destroy
  }
}
