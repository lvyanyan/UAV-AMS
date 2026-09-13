/**
 * 无人机 GPU 图标渲染 — 完全参考 drone-airspace-system/useGpuPoints 模式
 *
 * ★ 预加载图片再创建 Billboard（传 Image 对象，永不传 URL）
 * ★ init() 是 async 函数，先 await loadImage 再创建 Collection
 * ★ updatePositions() 批量更新位置
 */
import * as Cesium from 'cesium'
import { i18n } from '@/locales'

const IMG_PATHS = {
  NORMAL: '/images/drones/default-fusion.png',
  WARNING: '/images/drones/warn-fusion.png',
  MINOR: '/images/drones/warn-fusion.png',
  MAJOR: '/images/drones/danger-fusion.png',
  CRITICAL: '/images/drones/danger-fusion.png',
  EMERGENCY: '/images/drones/danger-fusion.png',
  HF: '/images/drones/hf-fusion.png',
}

/** 加载图片 → Promise<Image> */
function loadImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(i18n.global.t('drone.imgLoadFailed', { url })))
    img.src = url
  })
}

export function useDroneRenderer(viewerRef) {
  let collection = null
  let _initialized = false
  let _imgNormal = null
  let _imgWarn = null
  let _imgDanger = null
  let _imgHf = null

  /** ★ 预加载所有图片 → 创建 BillboardCollection */
  async function init(count) {
    const v = viewerRef.value
    if (!v) { console.warn('[useDroneRenderer] viewerRef 为空'); return }

    // 如果已初始化，销毁旧的
    if (_initialized && collection) {
      if (!collection.isDestroyed()) {
        v.scene.primitives.remove(collection)
        collection.destroy()
      }
      _initialized = false
    }

    // ★ 预加载所有图片
    try { _imgNormal = await loadImage(IMG_PATHS.NORMAL) } catch (e) { _imgNormal = null }
    try { _imgWarn = await loadImage(IMG_PATHS.WARNING) } catch (e) { _imgWarn = null }
    try { _imgDanger = await loadImage(IMG_PATHS.MAJOR) } catch (e) { _imgDanger = null }
    try { _imgHf = await loadImage(IMG_PATHS.HF) } catch (e) { _imgHf = null }

    // ★ 创建 BillboardCollection，传已加载的 Image 对象
    collection = new Cesium.BillboardCollection({ scene: v.scene })
    v.scene.primitives.add(collection)

    const startPos = Cesium.Cartesian3.fromDegrees(116.4, 39.9, 300)
    for (let i = 0; i < count; i++) {
      collection.add({
        position: startPos.clone(),
        image: _imgNormal || IMG_PATHS.NORMAL,   // ★ 传已加载的 Image 对象
        width: 28, height: 28,
        scale: 1.0,
        color: Cesium.Color.WHITE,
        rotation: 0,
        alignedAxis: Cesium.Cartesian3.UNIT_Z,
        verticalOrigin: Cesium.VerticalOrigin.CENTER,
        horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        translucencyByDistance: new Cesium.NearFarScalar(1.5e5, 1.0, 8e5, 0.0),
        pixelOffsetScaleByDistance: new Cesium.NearFarScalar(1.5e5, 3.0, 8e5, 0.5),
        id: i
      })
    }

    _initialized = true
    console.log(`[useDroneRenderer] ✅ 初始化 ${count} 架无人机（图片预加载完成）`)
  }

  /** 批量更新位置 */
  function updatePositions(cartArray) {
    if (!collection || !cartArray) return
    if (collection.isDestroyed && collection.isDestroyed()) return
    const len = Math.min(cartArray.length, collection.length)
    for (let i = 0; i < len; i++) {
      const b = collection.get(i)
      if (!b) continue
      b.position = cartArray[i]
    }
  }

  /** 单架更新位置 + 航向（兼容原有调用方式） */
  function upsertDrone(data, slotIndex) {
    if (!collection || !data) return
    const pos = data.position || {}
    const lon = pos.lon ?? pos.longitude ?? 0
    const lat = pos.lat ?? pos.latitude ?? 0
    const alt = pos.alt ?? pos.altitude ?? 100
    const heading = pos.heading ?? data.heading ?? 0
    const alertLevel = data.alertLevel || 'NORMAL'
    const isHf = data.isHf ?? false
    if (!lon || !lat) return

    const cart = Cesium.Cartesian3.fromDegrees(lon, lat, alt)

    if (slotIndex !== undefined && slotIndex < collection.length) {
      const b = collection.get(slotIndex)
      if (b) {
        b.position = cart
        b.rotation = -Cesium.Math.toRadians(heading)

        // 切换图片
        let img = null
        if (isHf) img = _imgHf
        else if (alertLevel === 'MAJOR' || alertLevel === 'CRITICAL' || alertLevel === 'EMERGENCY') img = _imgDanger
        else if (alertLevel === 'WARNING' || alertLevel === 'MINOR') img = _imgWarn
        else img = _imgNormal

        if (img && b.image !== img) b.image = img
      }
    }
  }

  function highlight(index) {
    if (!collection) return
    for (let i = 0; i < collection.length; i++) {
      const b = collection.get(i)
      if (!b) continue
      b.scale = (i === index) ? 1.5 : 1.0
    }
  }

  function getCollection() { return collection }
  function isInitialized() { return _initialized }

  function destroy() {
    const v = viewerRef.value
    if (collection && v && v.scene) {
      if (!collection.isDestroyed()) {
        v.scene.primitives.remove(collection)
        collection.destroy()
      }
    }
    collection = null
    _imgNormal = _imgWarn = _imgDanger = _imgHf = null
    _initialized = false
  }

  return { init, updatePositions, upsertDrone, highlight, getCollection, isInitialized, destroy }
}
