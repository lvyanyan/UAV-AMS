import request from './index'
import type { R } from './index'

export interface FlightPlan {
  id?: number
  planNo?: string
  pilotId?: number
  pilotName?: string
  droneId?: number
  droneSn?: string
  flightArea?: string
  startTime?: string
  endTime?: string
  maxAltitude?: number
  flightPurpose?: string
  planStatus?: string
  militaryApproved?: boolean
  militaryApprovalId?: number
  createTime?: string
}

export interface FlightPlanApproval {
  id?: number
  planId: number
  approverId: number
  approvalLevel: number
  result: string
  comment?: string
  createTime?: string
}

export const flightPlanApi = {
  create: (data: FlightPlan): Promise<R<FlightPlan>> => request.post('/flight-plan', data),
  getById: (id: number): Promise<R<FlightPlan>> => request.get(`/flight-plan/${id}`),
  list: (): Promise<R<FlightPlan[]>> => request.get('/flight-plan/list'),
  listByStatus: (status: string): Promise<R<FlightPlan[]>> => request.get(`/flight-plan/list/status/${status}`),
  update: (id: number, data: FlightPlan): Promise<R<FlightPlan>> => request.put(`/flight-plan/${id}`, data),
  submit: (id: number): Promise<R<FlightPlan>> => request.put(`/flight-plan/${id}/submit`),
  approve: (id: number, approverId: number, comment?: string): Promise<R<FlightPlan>> =>
    request.put(`/flight-plan/${id}/approve`, null, { params: { approverId, comment } }),
  reject: (id: number, approverId: number, comment: string): Promise<R<FlightPlan>> =>
    request.put(`/flight-plan/${id}/reject`, null, { params: { approverId, comment } }),
  militaryApprove: (id: number, approverId: number): Promise<R<FlightPlan>> =>
    request.put(`/flight-plan/${id}/military-one-click`, null, { params: { militaryApproverId: approverId } }),
  militaryCancel: (id: number, approverId: number, reason: string): Promise<R<FlightPlan>> =>
    request.put(`/flight-plan/${id}/military-cancel`, null, { params: { militaryApproverId: approverId, reason } }),
  getApprovals: (id: number): Promise<R<FlightPlanApproval[]>> => request.get(`/flight-plan/${id}/approvals`),
}
