Page({
  data: { filter: 'ALL', plans: [] },
  setFilter(e: any) { this.setData({ filter: e.currentTarget.dataset.f }); this.loadPlans() },
  onShow() { this.loadPlans() },
  loadPlans() {
    this.setData({
      plans: [
        { id: 1, planNo: 'FP-20260528-001', planStatus: 'PENDING' },
        { id: 2, planNo: 'FP-20260528-002', planStatus: 'APPROVED' },
      ]
    })
  }
})
