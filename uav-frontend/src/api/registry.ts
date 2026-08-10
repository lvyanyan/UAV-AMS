import request from './index'
import type { R } from './index'

export interface UavOwner {
  id?: number
  ownerName: string
  idCardNo: string
  phone: string
  email?: string
  address?: string
  organization?: string
  registerStatus?: string
  createTime?: string
}

export interface UavRegistration {
  id?: number
  ownerId: number
  droneSn: string
  droneModel: string
  droneWeight?: number
  droneType?: string
  registerNo?: string
  registerStatus?: string
  photoUrl?: string
  createTime?: string
}

export const registryApi = {
  // 所有人登记
  registerOwner: (data: UavOwner): Promise<R<UavOwner>> => request.post('/api/registry/owner', data),
  getOwner: (id: number): Promise<R<UavOwner>> => request.get(`/api/registry/owner/${id}`),
  listOwners: (): Promise<R<UavOwner[]>> => request.get('/api/registry/owner/list'),
  approveOwner: (id: number): Promise<R<UavOwner>> => request.put(`/api/registry/owner/${id}/approve`),
  rejectOwner: (id: number): Promise<R<UavOwner>> => request.put(`/api/registry/owner/${id}/reject`),

  // 无人机登记
  registerDrone: (data: UavRegistration): Promise<R<UavRegistration>> => request.post('/api/registry/drone', data),
  getDrone: (id: number): Promise<R<UavRegistration>> => request.get(`/api/registry/drone/${id}`),
  listDrones: (): Promise<R<UavRegistration[]>> => request.get('/api/registry/drone/list'),
  listDronesByOwner: (ownerId: number): Promise<R<UavRegistration[]>> => request.get(`/api/registry/drone/by-owner/${ownerId}`),
  approveDrone: (id: number): Promise<R<UavRegistration>> => request.put(`/api/registry/drone/${id}/approve`),
  rejectDrone: (id: number): Promise<R<UavRegistration>> => request.put(`/api/registry/drone/${id}/reject`),
}
