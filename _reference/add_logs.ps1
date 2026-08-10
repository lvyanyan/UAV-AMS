param()
$path1 = 'src/composables/useDroneSimulation.js'
$content1 = Get-Content $path1 -Encoding UTF8

# Line 182: function getCurrentAlerts() { → add log
$content1[181] = "  function getCurrentAlerts() {
    console.log('[alerts] getCurrentAlerts() ->', _currentAlerts.length, '条, 包索引=', _loadedPacketIndex)
    return _currentAlerts
  }"

Set-Content $path1 -Value $content1 -Encoding UTF8

$path2 = 'src/composables/useGpuPoints.js'
$content2 = Get-Content $path2 -Encoding UTF8

# Line 144: function applyAlertIcons → add log at the beginning
$content2[143] = "  function applyAlertIcons(alerts, droneMeta, currentTime) {
    console.log('[alerts] applyAlertIcons(alerts=' + (alerts?.length || 0) + ', meta=' + (droneMeta?.length || 0) + ', time=' + currentTime + ')')
    if (!collection || !alerts || !droneMeta) return"

# Line 228: function _applyIconForDrone → add log
$content2[228] = "  function _applyIconForDrone(billboard, droneIndex) {
    const alertType = _alertOverrides.get(droneIndex)
    console.log('[alerts] _applyIconForDrone(idx=' + droneIndex + ', alertType=' + (alertType || 'null') + ', hasImage=' + (alertType && _alertImages[alertType] ? 'yes' : 'no') + ')')"

Set-Content $path2 -Value $content2 -Encoding UTF8

Write-Host 'Logs added successfully!'
