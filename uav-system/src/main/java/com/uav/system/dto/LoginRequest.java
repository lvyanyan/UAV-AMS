package com.uav.system.dto;

public class LoginRequest {
    private String username;
    private String password;
    private String mfaCode;
    private boolean militaryLogin;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getMfaCode() { return mfaCode; }
    public void setMfaCode(String mfaCode) { this.mfaCode = mfaCode; }
    public boolean isMilitaryLogin() { return militaryLogin; }
    public void setMilitaryLogin(boolean militaryLogin) { this.militaryLogin = militaryLogin; }
}
