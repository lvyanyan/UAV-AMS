package com.uav.system.dto;

public class LoginResponse {
    private String token;
    private String username;
    private String roleCode;
    private String realName;
    private boolean militaryLogin;
    private long expiresIn;

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
}
