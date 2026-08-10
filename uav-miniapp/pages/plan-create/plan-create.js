Page({
  data: { area: '', date: '', altitude: '' },
  onArea(e: any) { this.setData({ area: e.detail.value }) },
  onDate(e: any) { this.setData({ date: e.detail.value }) },
  onAltitude(e: any) { this.setData({ altitude: e.detail.value }) },
  submit() { wx.showToast({ title: '计划已提交' }); wx.navigateBack() }
})
