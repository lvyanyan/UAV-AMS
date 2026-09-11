import request from './index'
import type { R } from './index'

export interface Airspace {
  id?: number
  airspaceName: string
  airspaceCode?: string
  airspaceType: string   // CONTROL 管制 / OPERATION 作业 / CORRIDOR 走廊 / DEMO 示范 / NO_FLY 禁飞
  geoJson?: string       // GeoJSON polygon
  altFloorM?: number
  altCeilingM?: number
  startTime?: string
  endTime?: string
  isActive?: boolean
  description?: string
  createTime?: string
}

export const airspaceApi = {
  create: (data: Airspace): Promise<R<Airspace>> => request.post('/airspace', data),
  getById: (id: number): Promise<R<Airspace>> => request.get(`/airspace/${id}`),
  list: (): Promise<R<Airspace[]>> => request.get('/airspace/list'),
  update: (id: number, data: Airspace): Promise<R<Airspace>> => request.put(`/airspace/${id}`, data),
  delete: (id: number): Promise<R<any>> => request.delete(`/airspace/${id}`),
  getGeoJson: (): Promise<R<any>> => request.get('/airspace/geojson'),
}
