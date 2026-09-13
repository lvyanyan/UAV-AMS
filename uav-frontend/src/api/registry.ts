import request from './index'
import type { R } from './index'

export interface UavOwner {
  id?: number
  ownerName: string
  idNumber: string
  phone: string
  email?: string
  address?: string
  registerStatus?: string
  /** UOM 上报状态：REPORTED / FAILED / 未上报为空 */
  uomStatus?: string
  uomReportTime?: string
  createTime?: string
}

export interface UavRegistration {
  id?: number
  ownerId: number
  droneSn: string
  droneModel: string
  weightG?: number
  droneType?: string
  registrationId?: string
  registerStatus?: string
  photoUrl?: string
  /** UOM 上报状态：REPORTED / FAILED / 未上报为空 */
  uomStatus?: string
  uomReportTime?: string
  createTime?: string
}

export interface UomIntegrationLog {
  id: number
  eventType: string
  payload?: string
  status?: string
  retryCount?: number
  errorMsg?: string
  createTime?: string
}

export const registryApi = {
  // 所有人登记
  registerOwner: (data: UavOwner): Promise<R<UavOwner>> => request.post('/registry/owner', data),
  getOwner: (id: number): Promise<R<UavOwner>> => request.get(`/registry/owner/${id}`),
  listOwners: (): Promise<R<UavOwner[]>> => request.get(`/registry/owner/list`),
  approveOwner: (id: number): Promise<R<UavOwner>> => request.put(`/registry/owner/${id}/approve`),
  rejectOwner: (id: number): Promise<R<UavOwner>> => request.put(`/registry/owner/${id}/reject`),

  // 无人机登记
  registerDrone: (data: UavRegistration): Promise<R<UavRegistration>> => request.post('/registry/drone', data),
  getDrone: (id: number): Promise<R<UavRegistration>> => request.get(`/registry/drone/${id}`),
  listDrones: (): Promise<R<UavRegistration[]>> => request.get(`/registry/drone/list`),
  listDronesByOwner: (ownerId: number): Promise<R<UavRegistration[]>> => request.get(`/registry/drone/by-owner/${ownerId}`),
  approveDrone: (id: number): Promise<R<UavRegistration>> => request.put(`/registry/drone/${id}/approve`),
  rejectDrone: (id: number): Promise<R<UavRegistration>> => request.put(`/registry/drone/${id}/reject`),

  // UOM 对接（实名登记上报民航局 UOM 平台）
  uomReportOwner: (id: number): Promise<R<UavOwner>> => request.put(`/registry/owner/${id}/uom-report`),
  uomReportDrone: (id: number): Promise<R<UavRegistration>> => request.put(`/registry/drone/${id}/uom-report`),
  uomReportAll: (): Promise<R<Record<string, unknown>>> => request.post('/registry/uom/report-all'),
  uomRetry: (): Promise<R<Record<string, unknown>>> => request.post('/registry/uom/retry'),
  uomLogs: (limit = 50): Promise<R<UomIntegrationLog[]>> => request.get(`/registry/uom/logs`, { params: { limit } }),
}
