App({
  globalData: {
    baseUrl: 'http://localhost:8080',
    token: '',
    userInfo: null
  },
  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) this.globalData.token = token
  }
})
