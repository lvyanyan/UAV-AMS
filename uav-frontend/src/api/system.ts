import request from './index'
import type { R } from './index'

export interface SysUser {
  id?: number
  username: string
  realName?: string
  email?: string
  phone?: string
  roleCode?: string
  roleName?: string
  enabled?: boolean
  status?: string
  mfaEnabled?: boolean
  createTime?: string
  password?: string
}

export interface SysRole {
  roleCode: string
  roleName: string
  description?: string
  enabled?: boolean
  createTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
}

/** 权限树节点：perm_type 为 MENU（菜单，含子节点）/ BUTTON（按钮/操作权限） */
export interface PermNode {
  id: number
  permCode: string
  permName: string
  permType: 'MENU' | 'BUTTON'
  parentId: number
  path?: string
  icon?: string
  sortOrder?: number
  children?: PermNode[]
}

export interface UserQuery {
  page?: number
  size?: number
  keyword?: string
  roleCode?: string
}

export interface RoleQuery {
  page?: number
  size?: number
  keyword?: string
}

export const systemApi = {
  // ===== 用户管理 =====
  listUsers: (params?: UserQuery): Promise<R<PageResult<SysUser>>> => request.get('/user/list', { params }),
  createUser: (data: Partial<SysUser>): Promise<R<any>> => request.post('/user', data),
  updateUser: (id: number, data: Partial<SysUser>): Promise<R<any>> => request.put(`/user/${id}`, data),
  deleteUser: (id: number): Promise<R<any>> => request.delete(`/user/${id}`),
  setUserEnabled: (id: number, enabled: boolean): Promise<R<any>> => request.put(`/user/${id}/enabled`, null, { params: { enabled } }),
  resetPassword: (id: number, password: string): Promise<R<any>> => request.put(`/user/${id}/reset-password`, { password }),
  assignRole: (id: number, roleCode: string): Promise<R<any>> => request.put(`/user/${id}/role`, { roleCode }),

  // ===== 角色管理 =====
  listRoles: (params?: RoleQuery): Promise<R<PageResult<SysRole>>> => request.get('/role/list', { params }),
  createRole: (data: Partial<SysRole>): Promise<R<any>> => request.post('/role', data),
  updateRole: (roleCode: string, data: Partial<SysRole>): Promise<R<any>> => request.put(`/role/${roleCode}`, data),
  deleteRole: (roleCode: string): Promise<R<any>> => request.delete(`/role/${roleCode}`),
  getRolePerms: (roleCode: string): Promise<R<string[]>> => request.get(`/role/${roleCode}/perms`),
  assignRolePerms: (roleCode: string, perms: string[]): Promise<R<any>> => request.put(`/role/${roleCode}/perms`, { perms }),

  // ===== 权限 =====
  permissionTree: (): Promise<R<PermNode[]>> => request.get('/permission/tree'),

  // 审计日志
  listAuditLogs: (): Promise<R<any[]>> => request.get('/audit/list'),

  // 违规台账（由危急/严重告警派生，只读）
  listViolations: (): Promise<R<any[]>> => request.get('/violation/list'),
}
