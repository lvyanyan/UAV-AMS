import request from './index'
import type { R } from './index'

export interface UavRoute {
  id?: number
  routeName?: string
  routeCode?: string
  /** 航点 JSON：[[lon,lat],...] */
  waypoints?: string
  corridorWidthM?: number
  /** ONE_WAY / TWO_WAY */
  direction?: string
  isActive?: boolean
  description?: string
  createTime?: string
}

export const routeApi = {
  list: (active?: boolean): Promise<R<UavRoute[]>> =>
    request.get('/route/list', { params: active === undefined ? {} : { active } }),
  create: (data: UavRoute): Promise<R<UavRoute>> => request.post('/route', data),
  update: (id: number, data: UavRoute): Promise<R<UavRoute>> => request.put(`/route/${id}`, data),
  remove: (id: number): Promise<R<string>> => request.delete(`/route/${id}`),
  /** 航路合规预检：返回违规描述列表（空数组=通过） */
  check: (waypoints: number[][]): Promise<R<string[]>> => request.post('/route/check', { waypoints }),
}
