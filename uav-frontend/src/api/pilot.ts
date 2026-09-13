import request from './index'
import type { R } from './index'

export interface UavPilot {
  id?: number
  pilotName: string
  idNumber: string
  phone: string
  licenseNo?: string
  licenseLevel?: string
  licenseExpire?: string
  status?: string
  createTime?: string
}

export interface UavPilotMedical {
  id?: number
  pilotId: number
  examDate: string
  /** 体检机构 */
  examOrg?: string
  /** 体检结论：PASS 合格 / FAIL 不合格（与后端 exam_result 对齐） */
  examResult?: string
  /** 体检报告链接 */
  examReportUrl?: string
  expireDate?: string
  /** 记录来源：MANUAL 手动录入 / CENTER 体检中心同步 */
  source?: string
}

export const pilotApi = {
  create: (data: UavPilot): Promise<R<UavPilot>> => request.post('/pilot', data),
  getById: (id: number): Promise<R<UavPilot>> => request.get(`/pilot/${id}`),
  list: (): Promise<R<UavPilot[]>> => request.get('/pilot/list'),
  update: (id: number, data: UavPilot): Promise<R<UavPilot>> => request.put(`/pilot/${id}`, data),
  suspend: (id: number): Promise<R<UavPilot>> => request.put(`/pilot/${id}/suspend`),
  reactivate: (id: number): Promise<R<UavPilot>> => request.put(`/pilot/${id}/reactivate`),

  // 体检记录
  uploadMedical: (pilotId: number, data: UavPilotMedical): Promise<R<UavPilotMedical>> => request.post(`/pilot/${pilotId}/medical`, data),
  listMedical: (pilotId: number): Promise<R<UavPilotMedical[]>> => request.get(`/pilot/${pilotId}/medical/list`),
  medicalValid: (pilotId: number): Promise<R<Record<string, unknown>>> => request.get(`/pilot/${pilotId}/medical/valid`),

  // 体检中心对接
  syncMedicalCenter: (): Promise<R<Record<string, unknown>>> => request.post('/pilot/medical-center/sync'),
}
