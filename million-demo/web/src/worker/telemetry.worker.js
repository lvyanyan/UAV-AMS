/**
 * 遥测 Worker v5 —— 双协议分流（服务端视锥聚合）
 *
 * 服务端按相机视野推两种帧：
 *   raw  (magic 0x55534156 "USAV")：低空视野内原始点，16头 + N×20B [lon,lat,alt,hdg,alert]
 *   cell (magic 0x55534143 "USAC")：高空网格聚合，16头 + M×16B [lon,lat,count,alertAvg]
 *
 * Worker 按 magic 分流，统一输出 pos+meta(+ll)：
 *   raw  → ECEF + 告警/航向/经纬度（同 v4）
 *   cell → ECEF + [count当作alert, 0 heading]（点大小∝count 由渲染器处理）
 *
 * 全部 Transferable 零拷贝，不 import Cesium。
 */

const A = 6378137.0
const E2 = 0.0066943799901413165
const DEG = Math.PI / 180.0

const MAGIC_RAW = 0x55534156
const MAGIC_CELL = 0x55534143

function ecef(lonDeg, latDeg, alt) {
  const lon = lonDeg * DEG
  const lat = latDeg * DEG
  const sinLat = Math.sin(lat)
  const cosLat = Math.cos(lat)
  const sinLon = Math.sin(lon)
  const cosLon = Math.cos(lon)
  const N = A / Math.sqrt(1 - E2 * sinLat * sinLat)
  return [
    (N + alt) * cosLat * cosLon,
    (N + alt) * cosLat * sinLon,
    (N * (1 - E2) + alt) * sinLat
  ]
}

self.onmessage = (e) => {
  const buf = e.data
  if (!buf || buf.byteLength < 16) {
    self.postMessage({ pos: new ArrayBuffer(0), meta: new ArrayBuffer(0), ll: new ArrayBuffer(0), count: 0, serverCount: 0, isCell: false })
    return
  }
  const dv = new DataView(buf)
  const magic = dv.getUint32(0, true)
  const count = dv.getUint32(4, true)
  if (count === 0 || (magic !== MAGIC_RAW && magic !== MAGIC_CELL)) {
    self.postMessage({ pos: new ArrayBuffer(0), meta: new ArrayBuffer(0), ll: new ArrayBuffer(0), count: 0, serverCount: 0, isCell: false })
    return
  }

  const pos = new Float64Array(count * 3)
  const meta = new Float32Array(count * 2)
  const ll = new Float32Array(count * 2)
  const isCell = magic === MAGIC_CELL

  if (isCell) {
    // cell：[lon,lat,count,alertAvg] ×16B → 点大小∝count
    for (let i = 0; i < count; i++) {
      const o = 16 + i * 16
      const lon = dv.getFloat32(o, true)
      const lat = dv.getFloat32(o + 4, true)
      const cellCount = dv.getFloat32(o + 8, true)
      const alertAvg = dv.getFloat32(o + 12, true)
      const [x, y, z] = ecef(lon, lat, 0)
      pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z
      // meta: [encodedAlert, heading=0] —— 用 count 编码进 meta 让渲染器画大点
      // count 映射到 [0,100] 区间作为"密度信号"
      meta[i * 2] = Math.min(100, cellCount)
      meta[i * 2 + 1] = 0
      ll[i * 2] = lat; ll[i * 2 + 1] = lon
    }
  } else {
    // raw：[lon,lat,alt,hdg,alert] ×20B
    for (let i = 0; i < count; i++) {
      const o = 16 + i * 20
      const lon = dv.getFloat32(o, true)
      const lat = dv.getFloat32(o + 4, true)
      const alt = dv.getFloat32(o + 8, true)
      const heading = dv.getFloat32(o + 12, true)
      const alert = dv.getFloat32(o + 16, true)
      const [x, y, z] = ecef(lon, lat, alt)
      pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z
      meta[i * 2] = alert
      meta[i * 2 + 1] = heading
      ll[i * 2] = lat; ll[i * 2 + 1] = lon
    }
  }

  self.postMessage(
    { pos: pos.buffer, meta: meta.buffer, ll: ll.buffer, count, serverCount: count, isCell },
    [pos.buffer, meta.buffer, ll.buffer]
  )
}
