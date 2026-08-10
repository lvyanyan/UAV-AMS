Page({
  data: { user: { name: '张飞行员', licenseNo: 'UAV-2024-00001' } },
  uploadMedical() { wx.showToast({ title: '功能开发中' }) },
  logout() { wx.removeStorageSync('token'); wx.reLaunch({ url: '/pages/login/login' }) }
})
