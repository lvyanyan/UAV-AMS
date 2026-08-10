/**
 * 北京市悬浮地形 —— 裁剪抬升 + 真实地形
 *
 * 效果：将北京市区域从地面"裁剪"出来悬浮至 1500m 高空，
 *       地面留下黑色空洞，悬浮块表面呈现真实地形起伏。
 *
 * 实现：
 *   - GeoJSON 边界 → 内部生成 40×40 均匀网格
 *   - Perlin 噪声模拟北京真实地形（西山 ~900m，平原 ~30m）
 *   - 每个网格 quad = Entity polygon（perPositionHeight + extrudedHeight）
 *   - 地面阴影 polygon 模拟空洞效果
 *   - 零网络请求，完全离线
 */
import * as Cesium from 'cesium'

const BASE_HEIGHT = 1500
const GRID_SIZE = 40
const CYAN = '#00d4ff'

// ──────────────────────────────────────────────
//  Perlin 噪声（标准排列表，固定种子）
// ──────────────────────────────────────────────

const P = [
  151,160,137, 91, 90, 15,131, 13,201, 95, 96, 53,194,233,  7,225,
  140, 36,103, 30, 69,142,  8, 99, 37,240, 21, 10, 23,190,  6,148,
  247,120,234, 75,  0, 26,197, 62, 94,252,219,203,117, 35, 11, 32,
   57,177, 33, 88,237,149, 56, 87,174, 20,125,136,171,168, 68,175,
   74,165, 71,134,139, 48, 27,166, 77,146,158,231, 83,111,229,122,
   60,211,133,230,220,105, 92, 41, 55, 46,245, 40,244,102,143, 54,
   65, 25, 63,161,  1,216, 80, 73,209, 76,132,187,208, 89, 18,169,
  200,196,135,130,116,188,159, 86,164,100,109,198,173,186,  3, 64,
   52,217,226,250,124,123,  5,202, 38,147,118,126,255, 82, 85,212,
  207,206, 59,227, 47, 16, 58, 17,182,189, 28, 42,223,183,170,213,
  119,248,152,  2, 44,154,163, 70,221,153,101,155,167, 43,172,  9,
  129, 22, 39,253, 19, 98,108,110, 79,113,224,232,178,185,112,104,
  218,246, 97,228,251, 34,242,193,238,210,144, 12,191,179,162,241,
   81, 51,145,235,249, 14,239,107, 49,192,214, 31,181,199,106,157,
  184, 84,204,176,115,121, 50, 45,127,  4,150,254,138,236,205, 93,
  222,114, 67, 29, 24, 72,243,141,128,195, 78, 66,215, 61,156,180
]

const perm = new Array(512)
for (let i = 0; i < 512; i++) perm[i] = P[i & 255]

function fade(t) { return t * t * t * (t * (t * 6 - 15) + 10) }
function lerp(t, a, b) { return a + t * (b - a) }

function grad(h, x, y) {
  const h2 = h & 3
  const u = h2 < 2 ? x : y
  const v = h2 < 2 ? y : x
  return ((h2 & 1) === 0 ? u : -u) + ((h2 & 2) === 0 ? v : -v)
}

function noise2(x, y) {
  const X = Math.floor(x) & 255
  const Y = Math.floor(y) & 255
  const xf = x - Math.floor(x)
  const yf = y - Math.floor(y)
  const u = fade(xf)
  const v = fade(yf)
  const aa = perm[perm[X] + Y]
  const ab = perm[perm[X] + Y + 1]
  const ba = perm[perm[X + 1] + Y]
  const bb = perm[perm[X + 1] + Y + 1]
  return lerp(v,
    lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf)),
    lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1))
  )
}

function fbm(x, y, oct = 4) {
  let v = 0; let a = 1; let f = 1; let m = 0
  for (let i = 0; i < oct; i++) {
    v += a * noise2(x * f, y * f)
    m += a; a *= 0.5; f *= 2
  }
  return v / m
}

// ──────────────────────────────────────────────
//  北京地形模型（模拟真实海拔）
//  西山 ~900m | 北部山 ~500m | 丘陵 ~200m | 平原 ~30m
// ──────────────────────────────────────────────

function getElevation(lon, lat) {
  const nx = (lon - 115.2) / (117.5 - 115.2)  // 0~1, 西→东
  const ny = (lat - 39.4) / (41.1 - 39.4)     // 0~1, 南→北

  // 西山隆起（西北部，门头沟/延庆）
  const westMtn = 880 * Math.max(0,
    Math.exp(-((nx - 0.02) ** 2 + (ny - 0.72) ** 2) / 0.025)
  )

  // 北部军都山（昌平/怀柔北）
  const northMtn = 480 * Math.max(0,
    Math.exp(-((nx - 0.35) ** 2 + (ny - 0.92) ** 2) / 0.035)
  )

  // 西部丘陵过渡带
  const westHill = 280 * Math.max(0, Math.exp(-nx * 5)) * (0.5 + 0.5 * ny)

  // 平原地基（东/南部）
  const plain = 30 * (1 - 0.25 * ny)

  // 噪声微起伏
  const detail = fbm(lon * 0.8, lat * 0.8, 4) * 60

  return Math.max(10, Math.min(1000, plain + westHill + northMtn + westMtn + detail))
}

// ──────────────────────────────────────────────
//  几何判断
// ──────────────────────────────────────────────

function pointInRing(lon, lat, ring) {
  let inside = false
  const n = ring.length
  for (let i = 0, j = n - 1; i < n; j = i++) {
    const xi = ring[i][0]; const yi = ring[i][1]
    const xj = ring[j][0]; const yj = ring[j][1]
    if ((yi > lat) !== (yj > lat) &&
        lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
      inside = !inside
    }
  }
  return inside
}

// ──────────────────────────────────────────────
//  Composable
// ──────────────────────────────────────────────

export function useBoundary(viewerRef) {
  const entities = []

  async function loadBoundary(url) {
    const viewer = viewerRef.value
    if (!viewer) return

    const resp = await fetch(url)
    const geojson = await resp.json()

    for (const feature of geojson.features || []) {
      const name = feature.properties?.name || ''
      const geometry = feature.geometry
      if (!geometry || geometry.type !== 'MultiPolygon') continue

      for (const polygon of geometry.coordinates) {
        const ring = polygon[0]
        if (!ring || ring.length < 3) continue

        // 闭合环
        const cls = [...ring]
        const f = ring[0]; const l = ring[ring.length - 1]
        if (f[0] !== l[0] || f[1] !== l[1]) cls.push(f)

        // 包围盒
        let miLon = Infinity; let maLon = -Infinity
        let miLat = Infinity; let maLat = -Infinity
        for (const c of cls) {
          if (c[0] < miLon) miLon = c[0]
          if (c[0] > maLon) maLon = c[0]
          if (c[1] < miLat) miLat = c[1]
          if (c[1] > maLat) maLat = c[1]
        }

        const sLon = (maLon - miLon) / GRID_SIZE
        const sLat = (maLat - miLat) / GRID_SIZE

        // ═══ ① 生成网格点 ═══
        const grid = []
        for (let i = 0; i <= GRID_SIZE; i++) {
          grid[i] = []
          for (let j = 0; j <= GRID_SIZE; j++) {
            const lon = miLon + i * sLon
            const lat = miLat + j * sLat
            const inside = pointInRing(lon, lat, cls)
            const h = inside ? BASE_HEIGHT + getElevation(lon, lat) : 0
            grid[i][j] = { lon, lat, h, inside }
          }
        }

        // ═══ ② 构建网格四边形 ═══
        for (let i = 0; i < GRID_SIZE; i++) {
          for (let j = 0; j < GRID_SIZE; j++) {
            const p00 = grid[i][j]
            const p10 = grid[i + 1][j]
            const p01 = grid[i][j + 1]
            const p11 = grid[i + 1][j + 1]

            if (!p00.inside || !p10.inside || !p01.inside || !p11.inside) continue

            const avgH = (p00.h + p10.h + p01.h + p11.h) / 4
            const elev = avgH - BASE_HEIGHT  // 真实海拔 0~1000
            const rel = elev / 1000          // 归一化 0~1

            // 海拔着色：低(蓝紫) → 高(青金)
            const hue = 0.68 - rel * 0.28
            const sat = 0.5 + rel * 0.4
            const lit = 0.3 + rel * 0.35
            const a = 0.06 + rel * 0.10  // 低处更透，高处稍实

            const ent = viewer.entities.add({
              polygon: {
                hierarchy: {
                  positions: Cesium.Cartesian3.fromDegreesArrayHeights([
                    p00.lon, p00.lat, p00.h,
                    p10.lon, p10.lat, p10.h,
                    p11.lon, p11.lat, p11.h,
                    p01.lon, p01.lat, p01.h
                  ])
                },
                perPositionHeight: true,
                extrudedHeight: BASE_HEIGHT,
                material: Cesium.Color.fromHsl(hue, sat, lit, a),
                outline: true,
                outlineColor: Cesium.Color.fromHsl(hue, 0.9, 0.55, 0.2),
                outlineWidth: 1,
                closeTop: true,
                closeBottom: false
              }
            })
            entities.push(ent)
          }
        }

        // ═══ ③ 地面空洞（高度 0 处的黑色阴影）═══
        const shadow = viewer.entities.add({
          polygon: {
            hierarchy: Cesium.Cartesian3.fromDegreesArray(cls.flat()),
            material: Cesium.Color.fromCssColorString('#000000').withAlpha(0.7),
            height: 0,
            perPositionHeight: false
          }
        })
        entities.push(shadow)

        // ═══ ④ 侧壁（从底面到地形顶面）═══
        const wPos = cls.map(c => Cesium.Cartesian3.fromDegrees(c[0], c[1], 0))
        const wMin = cls.map(() => BASE_HEIGHT)
        const wMax = cls.map(c => BASE_HEIGHT + getElevation(c[0], c[1]))
        const wall = viewer.entities.add({
          wall: {
            positions: wPos,
            minimumHeights: wMin,
            maximumHeights: wMax,
            material: Cesium.Color.fromCssColorString(CYAN).withAlpha(0.04)
          }
        })
        entities.push(wall)

        // ═══ ⑤ 顶部发光边框 ═══
        const tBord = viewer.entities.add({
          polyline: {
            positions: cls.map(c =>
              Cesium.Cartesian3.fromDegrees(c[0], c[1], BASE_HEIGHT + getElevation(c[0], c[1]))
            ),
            width: 3,
            material: new Cesium.PolylineGlowMaterialProperty({
              glowPower: 0.7,
              color: Cesium.Color.fromCssColorString(CYAN)
            })
          }
        })
        entities.push(tBord)

        // ═══ ⑥ 底部微光边框 ═══
        const bBord = viewer.entities.add({
          polyline: {
            positions: cls.map(c =>
              Cesium.Cartesian3.fromDegrees(c[0], c[1], BASE_HEIGHT)
            ),
            width: 2,
            material: new Cesium.PolylineGlowMaterialProperty({
              glowPower: 0.4,
              color: Cesium.Color.fromCssColorString('#6d28d9')
            })
          }
        })
        entities.push(bBord)

        // ═══ ⑦ 名称标签 ═══
        const cLon = (miLon + maLon) / 2
        const cLat = (miLat + maLat) / 2
        const cH = BASE_HEIGHT + getElevation(cLon, cLat)

        const label = viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(cLon, cLat, cH + 100),
          label: {
            text: `📍 ${name}`,
            font: '22px "Microsoft YaHei", sans-serif',
            fillColor: Cesium.Color.fromCssColorString(CYAN),
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 3,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new Cesium.Cartesian2(0, 15),
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
            showBackground: true,
            backgroundColor: Cesium.Color.fromCssColorString('rgba(0,0,0,0.7)'),
            backgroundPadding: new Cesium.Cartesian2(14, 10)
          }
        })
        entities.push(label)
      }
    }

    console.log(`[useBoundary] 加载完成, 共 ${entities.length} 个实体`)
  }

  function destroy() {
    const viewer = viewerRef.value
    if (viewer) {
      entities.forEach(e => viewer.entities.remove(e))
    }
    entities.length = 0
  }

  return { loadBoundary, destroy }
}
