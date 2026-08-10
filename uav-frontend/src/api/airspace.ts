import request from './index'
import type { R } from './index'

export interface Airspace {
  id?: number
  name: string
  type: string          // 管制/适飞/禁飞/临时禁飞
  level?: number
  polygon: string        // GeoJSON polygon
  lowerAltitude: number
  upperAltitude: number
  effectiveTime?: string
  expireTime?: string
  status?: string
  createTime?: string
}

export const airspaceApi = {
  create: (data: Airspace): Promise<R<Airspace>> => request.post('/api/airspace', data),
  getById: (id: number): Promise<R<Airspace>> => request.get(`/api/airspace/${id}`),
  list: (): Promise<R<Airspace[]>> => request.get('/api/airspace/list'),
  update: (id: number, data: Airspace): Promise<R<Airspace>> => request.put(`/api/airspace/${id}`, data),
  delete: (id: number): Promise<R<any>> => request.delete(`/api/airspace/${id}`),
  getGeoJson: (): Promise<R<any>> => request.get('/api/airspace/geojson'),
}
