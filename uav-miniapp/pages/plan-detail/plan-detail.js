Page({
  data: { plan: {} as any },
  onLoad(options: any) {
    this.setData({ plan: { planNo: 'FP-20260528-00'+options.id, planStatus: '审批中' } })
  }
})
