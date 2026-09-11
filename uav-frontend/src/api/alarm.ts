import request from './index'
import type { R } from './index'

export interface AlarmEvent {
  id?: number
  droneSn: string
  alarmType: string
  alarmLevel: string      // GENERAL / SERIOUS / CRITICAL
  message: string
  latitude?: number
  longitude?: number
  altitude?: number
  sourceModule?: string
  handled?: boolean
  createTime?: string
}

export const alarmApi = {
  list: (): Promise<R<AlarmEvent[]>> => request.get('/alarm/list'),
  listByDrone: (droneSn: string): Promise<R<AlarmEvent[]>> => request.get(`/alarm/drone/${droneSn}`),
  handle: (id: number): Promise<R<AlarmEvent>> => request.put(`/alarm/${id}/handle`),
  stats: (): Promise<R<{ total: number; byType: Record<string,number>; byLevel: Record<string,number> }>> =>
    request.get('/alarm/stats'),
}
