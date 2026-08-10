import api from './index'

export interface LoginParams {
  username: string
  password: string
  mfaCode?: string
  militaryLogin?: boolean
}

export interface LoginResult {
  token: string
  username: string
  roleCode: string
  realName: string
  militaryLogin: boolean
  expiresIn: number
}

export function login(params: LoginParams) {
  return api.post<any, LoginResult>('/auth/login', params)
}

export function getCurrentUser() {
  return api.get('/auth/me')
}

// --- 空域 API ---
export function getAirspaces() {
  return api.get('/airspace')
}

export function getAirspaceGeoJson() {
  return api.get('/airspace/geojson')
}

// --- 告警 API ---
export function getAlarms(params?: any) {
  return api.get('/alarm', { params })
}

// --- 飞行计划 API ---
export function getFlightPlans(params?: any) {
  return api.get('/flight-plan', { params })
}
