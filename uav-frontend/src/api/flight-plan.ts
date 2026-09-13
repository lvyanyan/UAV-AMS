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
  actualStart?: string
  actualEnd?: string
  cmdSentAt?: string
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

/** 放行检查单项（pass=false 时 reason 为失败原因） */
export interface ReleaseCheckItem {
  name: string
  pass: boolean
  reason?: string
}

/** 放行检查清单（成败都带 checks 数组；失败时 HTTP 400，body 在 axios error.response.data 里） */
export interface ReleaseCheckResult {
  planId?: number
  planCode?: string
  passed: boolean
  checks: ReleaseCheckItem[]
}

export const flightPlanApi = {
  create: (data: FlightPlan): Promise<R<FlightPlan>> => request.post('/flight-plan', data),
  getById: (id: number): Promise<R<FlightPlan>> => request.get(`/flight-plan/${id}`),
  list: (): Promise<R<FlightPlan[]>> => request.get(`/flight-plan/list`),
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
  /** 放行（五项检查；未通过时 HTTP 400，error.response.data.data.checks 为检查清单） */
  release: (id: number): Promise<R<ReleaseCheckResult>> => request.put(`/flight-plan/${id}/release`),
  /** 下发起飞指令（RELEASED → 等 TAKEOFF_ACK 遥测回流置 IN_FLIGHT） */
  takeoff: (id: number): Promise<R<FlightPlan>> => request.put(`/flight-plan/${id}/takeoff`),
  /** 中止（RELEASED/IN_FLIGHT → CANCELLED，在飞无人机紧急降落） */
  abort: (id: number): Promise<R<FlightPlan>> => request.put(`/flight-plan/${id}/abort`),
  /** 在飞返航（状态不变，降落后自动收口 COMPLETED） */
  rtl: (id: number): Promise<R<FlightPlan>> => request.put(`/flight-plan/${id}/rtl`),
}
