/**
 * 3D Tiles 加载演示 — 本地空域 3D Tiles
 * 几何体在局部 ENU 坐标系中，通过 modelMatrix 定位到北京
 */
import { ref } from 'vue'
import * as Cesium from 'cesium'

export function use3DTiles(viewer) {
  let tileset = null
  const isVisible = ref(true)
  const isLoading = ref(false)
  const errorMsg = ref('')

  async function load() {
    if (!viewer.value) return
    unload()

    isLoading.value = true
    errorMsg.value = ''

    try {
      tileset = await Cesium.Cesium3DTileset.fromUrl('/tilesets/tileset.json', {
        maximumScreenSpaceError: 16,
        maximumNumberOfLoadedTiles: 1000
      })

      // ★ 关键：将局部 ENU 坐标系定位到北京 (116.397°E, 39.908°N)
      const origin = Cesium.Cartesian3.fromDegrees(116.397, 39.908, 0)
      const modelMatrix = Cesium.Transforms.eastNorthUpToFixedFrame(origin)
      tileset.modelMatrix = modelMatrix

      // ★ Debug：显示包围盒
      tileset.debugShowBoundingVolume = false  // 先关掉，等找到位置再开

      tileset.show = isVisible.value
      viewer.value.scene.primitives.add(tileset)

      // ★ 输出调试信息
      console.log('[use3DTiles] 已加载 tileset, 根瓦片数:', tileset.root ? 1 : 0)
      if (tileset.root) {
        const bb = tileset.root.boundingVolume
        console.log('[use3DTiles] 包围盒:', JSON.stringify(bb))
      }

      viewer.value.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(116.397, 39.908, 3000),
        orientation: {
          heading: Cesium.Math.toRadians(0),
          pitch: Cesium.Math.toRadians(-35),
          roll: 0
        },
        duration: 2
      })

      console.log('[use3DTiles] 已加载本地空域 3D Tiles')
    } catch (e) {
      errorMsg.value = `加载失败: ${e.message}`
      console.error('[use3DTiles] 错误:', e)
    } finally {
      isLoading.value = false
    }
  }

  function unload() {
    if (tileset && viewer.value) {
      viewer.value.scene.primitives.remove(tileset)
      tileset = null
    }
  }

  function setVisible(v) {
    isVisible.value = v
    if (tileset) tileset.show = v
  }

  function destroy() {
    unload()
  }

  function getCurrentName() {
    return '本地空域 3D Tiles'
  }

  return {
    isVisible,
    isLoading,
    errorMsg,
    load,
    unload,
    setVisible,
    destroy,
    getCurrentName,
    TILESETS_AVAILABLE: ['default']
  }
}
