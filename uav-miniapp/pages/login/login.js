Page({
  data: { username: '', password: '' },
  onUsername(e: any) { this.setData({ username: e.detail.value }) },
  onPassword(e: any) { this.setData({ password: e.detail.value }) },
  doLogin() {
    wx.request({
      url: getApp().globalData.baseUrl + '/api/auth/login',
      method: 'POST',
      data: { username: this.data.username, password: this.data.password },
      success: (res: any) => {
        if (res.data?.code === 200) {
          const token = res.data.data.token
          wx.setStorageSync('token', token)
          getApp().globalData.token = token
          wx.switchTab({ url: '/pages/index/index' })
        } else {
          wx.showToast({ title: '登录失败', icon: 'none' })
        }
      }
    })
  }
})
