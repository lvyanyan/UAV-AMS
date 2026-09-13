package com.uav.system.dto;

import java.util.List;

/**
 * 登录响应：token/username/roleCode 等为既有字段（保持兼容），
 * roles/permissions 为 RBAC 新增字段（前端据此做菜单与按钮级权限控制）
 */
public class LoginResponse {
    private String token;
    private String username;
    private String roleCode;
    private String realName;
    private boolean militaryLogin;
    private long expiresIn;
    /** 角色编码列表（当前为单角色，预留多角色扩展） */
    private List<String> roles;
    /** 该角色的全部权限码（域:资源:操作） */
    private List<String> permissions;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public boolean isMilitaryLogin() { return militaryLogin; }
    public void setMilitaryLogin(boolean militaryLogin) { this.militaryLogin = militaryLogin; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
