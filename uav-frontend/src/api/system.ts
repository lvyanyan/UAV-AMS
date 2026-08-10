import request from './index'
import type { R } from './index'

export interface SysUser {
  id?: number
  username: string
  realName?: string
  email?: string
  phone?: string
  roleId?: number
  roleName?: string
  status?: string
  mfaEnabled?: boolean
  createTime?: string
}

export interface SysRole {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  createTime?: string
}

export const systemApi = {
  // 用户管理
  listUsers: (): Promise<R<SysUser[]>> => request.get('/api/user/list'),
  createUser: (data: SysUser): Promise<R<SysUser>> => request.post('/api/user', data),
  updateUser: (id: number, data: SysUser): Promise<R<SysUser>> => request.put(`/api/user/${id}`, data),
  deleteUser: (id: number): Promise<R<any>> => request.delete(`/api/user/${id}`),
  enableUser: (id: number): Promise<R<SysUser>> => request.put(`/api/user/${id}/enable`),
  disableUser: (id: number): Promise<R<SysUser>> => request.put(`/api/user/${id}/disable`),

  // 角色管理
  listRoles: (): Promise<R<SysRole[]>> => request.get('/api/role/list'),
  createRole: (data: SysRole): Promise<R<SysRole>> => request.post('/api/role', data),

  // 审计日志
  listAuditLogs: (): Promise<R<any[]>> => request.get('/api/audit/list'),
}
