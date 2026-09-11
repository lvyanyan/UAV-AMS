import request from './index'
import type { R } from './index'

export interface UavAirport {
  id?: number
  airportName?: string
  airportCode?: string
  /** TAKEOFF / LANDING / ALL */
  airportType?: string
  lon?: number
  lat?: number
  elevationM?: number
  capacity?: number
  isActive?: boolean
  remark?: string
  createTime?: string
}

export const airportApi = {
  list: (active?: boolean): Promise<R<UavAirport[]>> =>
    request.get('/airport/list', { params: active === undefined ? {} : { active } }),
  create: (data: UavAirport): Promise<R<UavAirport>> => request.post('/airport', data),
  update: (id: number, data: UavAirport): Promise<R<UavAirport>> => request.put(`/airport/${id}`, data),
  remove: (id: number): Promise<R<string>> => request.delete(`/airport/${id}`),
  /** 起降点合规预检：返回违规描述列表（空数组=通过） */
  check: (lon: number, lat: number): Promise<R<string[]>> => request.post('/airport/check', { lon, lat }),
}
