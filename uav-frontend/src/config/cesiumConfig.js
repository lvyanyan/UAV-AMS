export const CESIUM_CONFIG = {
  imageryProvider: {
    url: 'https://webst01.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    credit: '高德卫星影像',
    maximumLevel: 18
  },
  labelOverlay: {
    url: 'https://webst01.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
    credit: '高德标注',
    maximumLevel: 18,
    alpha: 1
  },
  defaultView: {
    longitude: 116.397,
    latitude: 39.908,
    height: 15000
  }
}
