import request from './index'
import type { R } from './index'

export interface UavPilot {
  id?: number
  name: string
  idCardNo: string
  phone: string
  licenseNo?: string
  licenseType?: string
  licenseExpire?: string
  status?: string
  createTime?: string
}

export interface UavPilotMedical {
  id?: number
  pilotId: number
  examDate: string
  examOrg?: string
  result?: string
  expireDate?: string
  attachmentUrl?: string
}

export const pilotApi = {
  create: (data: UavPilot): Promise<R<UavPilot>> => request.post('/api/pilot', data),
  getById: (id: number): Promise<R<UavPilot>> => request.get(`/api/pilot/${id}`),
  list: (): Promise<R<UavPilot[]>> => request.get('/api/pilot/list'),
  update: (id: number, data: UavPilot): Promise<R<UavPilot>> => request.put(`/api/pilot/${id}`, data),
  suspend: (id: number): Promise<R<UavPilot>> => request.put(`/api/pilot/${id}/suspend`),
  reactivate: (id: number): Promise<R<UavPilot>> => request.put(`/api/pilot/${id}/reactivate`),

  // 体检记录
  uploadMedical: (pilotId: number, data: UavPilotMedical): Promise<R<UavPilotMedical>> => request.post(`/api/pilot/${pilotId}/medical`, data),
  listMedical: (pilotId: number): Promise<R<UavPilotMedical[]>> => request.get(`/api/pilot/${pilotId}/medical/list`),
}
