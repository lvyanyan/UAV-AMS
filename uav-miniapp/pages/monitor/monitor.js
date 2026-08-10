Page({
  data: { count: 0, markers: [] as any[] },
  onShow() { this.refresh() },
  refresh() {
    const markers = []
    for (let i = 0; i < 5; i++) {
      markers.push({
        id: i, latitude: 39.9 + Math.random() * 0.05, longitude: 116.4 + Math.random() * 0.05,
        iconPath: '/static/drone.png', width: 24, height: 24,
        callout: { content: 'UAV-'+i, fontSize: 11, bgColor: '#fff', padding: 4 }
      })
    }
    this.setData({ markers, count: markers.length })
  }
})
