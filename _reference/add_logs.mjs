import fs from 'fs'

// 1. useDroneSimulation.js
let s1 = fs.readFileSync('src/composables/useDroneSimulation.js', 'utf-8')
s1 = s1.replace(
  '  function getCurrentAlerts() {\n    return _currentAlerts\n  }',
  `  function getCurrentAlerts() {
    console.log('[alerts] getCurrentAlerts() ->', _currentAlerts.length, 'packetIdx=', _loadedPacketIndex)
    return _currentAlerts
  }`
)
fs.writeFileSync('src/composables/useDroneSimulation.js', s1, 'utf-8')
console.log('1 done')

// 2. useGpuPoints.js
let s2 = fs.readFileSync('src/composables/useGpuPoints.js', 'utf-8')
s2 = s2.replace(
  '  function applyAlertIcons(alerts, droneMeta, currentTime) {\n    if (!collection || !alerts || !droneMeta) return',
  `  function applyAlertIcons(alerts, droneMeta, currentTime) {
    console.log('[alerts] applyAlertIcons(alerts=' + (alerts?.length||0) + ', meta=' + (droneMeta?.length||0) + ', time=' + currentTime + ')')
    if (!collection || !alerts || !droneMeta) return`
)
s2 = s2.replace(
  '  function _applyIconForDrone(billboard, droneIndex) {\n    const alertType = _alertOverrides.get(droneIndex)',
  `  function _applyIconForDrone(billboard, droneIndex) {
    const alertType = _alertOverrides.get(droneIndex)
    console.log('[alerts] _applyIconForDrone(idx=' + droneIndex + ', type=' + (alertType||'null') + ', hasImg=' + (alertType && _alertImages[alertType] ? 1 : 0) + ')')`
)
fs.writeFileSync('src/composables/useGpuPoints.js', s2, 'utf-8')
console.log('2 done')

// 3. CesiumViewer.vue
let s3 = fs.readFileSync('src/components/CesiumViewer.vue', 'utf-8')
s3 = s3.replace(
  '      if (alerts && meta) {\n        gpuPoints.applyAlertIcons(alerts, meta, timeline.currentTime.value)',
  `      if (alerts && meta) {
        console.log('[alerts] CesiumViewer step6: alerts=' + (alerts?.length||0) + ', meta=' + (meta?.length||0) + ', time=' + timeline.currentTime.value)
        gpuPoints.applyAlertIcons(alerts, meta, timeline.currentTime.value)`
)
fs.writeFileSync('src/components/CesiumViewer.vue', s3, 'utf-8')
console.log('3 done')

console.log('ALL DONE!')
