Page({
  data: { stats: { online: 0, alarms: 0, plans: 0, violations: 0 } },
  onShow() {
    this.setData({
      stats: {
        online: Math.floor(Math.random()*30)+20,
        alarms: Math.floor(Math.random()*5),
        plans: Math.floor(Math.random()*10)+3,
        violations: Math.floor(Math.random()*3)
      }
    })
  }
})
