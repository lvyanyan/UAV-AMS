/**
 * 演示动线剧本 —— 相机自动巡航 + 分段解说
 * 纯前端驱动：Cesium camera.flyTo 串联；告警叙事基于协议自带 alert 字段，不改服务端。
 * gen 代际号机制：skip 时 +1，旧异步链全部失效，避免跳过后残余步骤"复活"。
 */
import * as Cesium from 'cesium'

// 场景中心（与服务端 -lat/-lon 默认一致：北京）
const CENTER = { lon: 116.4074, lat: 39.9042 }

const STEPS = [
  {
    title: '低空空域数字孪生监管平台',
    body: '面向政府监管场景：实名登记、飞行计划、告警、冲突检测全业务链。当前为缩放规模模拟数据实时推流，完整百万级压测报告见 GitHub 仓库。',
    cam: { lon: CENTER.lon, lat: CENTER.lat, h: 18000000, heading: 0, pitch: -90, duration: 5 }
  },
  {
    title: '服务端视锥聚合',
    body: '高空视角自动网格化渲染：点大小 ∝ 密度，单帧带宽降低 99% 以上。相机降低到 10km 以下会自动切换为逐架原始点流。',
    cam: { lon: CENTER.lon, lat: CENTER.lat, h: 1200000, heading: 0, pitch: -90, duration: 6 }
  },
  {
    title: '5Hz 实时二进制推流',
    body: 'WebSocket ArrayBuffer 零拷贝 + Transferable 移交 Web Worker：解析与 ECEF 坐标转换全部离主线程，rAF 只消费最新一帧。',
    cam: { lon: CENTER.lon - 0.35, lat: CENTER.lat - 0.2, h: 150000, heading: 30, pitch: -50, duration: 6 }
  },
  {
    title: '万级点云逐架渲染',
    body: '红色 = 严重告警 · 金色 = 一般告警 · 蓝色 = 正常巡航。低空（<10km）自动切换原始点 + 航向 Billboard，机头方向实时可读。',
    cam: { lon: CENTER.lon + 0.05, lat: CENTER.lat - 0.02, h: 9000, heading: 130, pitch: -35, duration: 7 }
  },
  {
    title: '冲突检测与解脱',
    body: '近距俯瞰机队微观态势。按 rAF 合批更新而非逐条 upsert，实测 119 FPS（120Hz 屏）；丢帧计数是主动背压保护，宁可丢旧帧也只画最新。',
    cam: { lon: CENTER.lon - 0.08, lat: CENTER.lat + 0.03, h: 4500, heading: 250, pitch: -28, duration: 7 }
  },
  {
    title: '演示结束 · 自由探索',
    body: '滚轮缩放体验不同层级的聚合/原始渲染切换；右下角面板可调机队规模与抽稀因子。源码与压测报告：github.com/lvyanyan/UAV-AMS',
    cam: { lon: CENTER.lon, lat: CENTER.lat, h: 15000, heading: 0, pitch: -30, duration: 5 }
  }
]

export function useDemoScript(viewerRef) {
  let gen = 0

  function flyStep(cam) {
    return new Promise(resolve => {
      const v = viewerRef.value
      if (!v) return resolve()
      v.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(cam.lon, cam.lat, cam.h),
        orientation: {
          heading: Cesium.Math.toRadians(cam.heading || 0),
          pitch: Cesium.Math.toRadians(cam.pitch ?? -90),
          roll: 0
        },
        duration: cam.duration,
        complete: resolve,
        cancel: resolve
      })
    })
  }

  /**
   * 运行剧本。onStep(index, step) 每步开始时回调（更新解说卡片）。
   * 返回 true = 完整播完；false = 被跳过。
   */
  async function run(onStep) {
    const myGen = ++gen
    for (let i = 0; i < STEPS.length; i++) {
      if (gen !== myGen) return false
      onStep(i, STEPS[i])
      await flyStep(STEPS[i].cam)
      if (gen !== myGen) return false
      if (i < STEPS.length - 1) {
        await new Promise(r => setTimeout(r, 1200))
        if (gen !== myGen) return false
      }
    }
    return true
  }

  function skip() {
    gen++
    const v = viewerRef.value
    if (v) v.camera.cancelFlight()
  }

  return { STEPS, run, skip }
}
