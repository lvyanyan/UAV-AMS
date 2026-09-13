package com.uav.system.controller;

import com.uav.common.base.R;
import com.uav.system.dto.LoginRequest;
import com.uav.system.dto.LoginResponse;
import com.uav.system.entity.SysUser;
import com.uav.system.security.JwtTokenProvider;
import com.uav.system.service.SysRoleService;
import com.uav.system.service.SysUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证接口：登录签发携带权限码（perms claim）的 JWT，登录响应返回角色与权限码列表
 * 说明：自注册端点已按安全规范移除，账户统一由管理员在「用户管理」中创建
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final JwtTokenProvider jwt;
    private final PasswordEncoder passwordEncoder;

    /** 演示用固定 MFA 验证码（application.yml uav.security.mfa-code 可覆盖，生产请接入真实 TOTP） */
    @Value("${uav.security.mfa-code:123456}")
    private String demoMfaCode;

    public AuthController(SysUserService us, SysRoleService roleService, JwtTokenProvider jwt, PasswordEncoder pe) {
        this.userService = us;
        this.roleService = roleService;
        this.jwt = jwt;
        this.passwordEncoder = pe;
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest req) {
        SysUser user = userService.lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername()).one();
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return R.fail(401, "用户名或密码错误");
        }
        if (!user.getEnabled()) {
            return R.fail(403, "账户已禁用");
        }

        // 军民协调员二次验证：验证码来自配置（默认 123456，仅供演示）
        if (req.isMilitaryLogin() || "MILITARY".equals(user.getRoleCode())) {
            if (user.getMfaEnabled() != null && user.getMfaEnabled()) {
                if (req.getMfaCode() == null || !req.getMfaCode().equals(demoMfaCode)) {
                    return R.fail(403, "二次验证码错误");
                }
            }
            return R.ok(buildResponse(user, true, 900000));
        }

        return R.ok(buildResponse(user, false, 7200000));
    }

    @GetMapping("/me")
    public R<String> me(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        return R.ok(jwt.getUsername(token));
    }

    // ===== 内部工具 =====

    /** 组装登录响应：token 携带权限码 claim，响应体返回 roles + permissions（向后兼容新增字段） */
    private LoginResponse buildResponse(SysUser user, boolean military, long expiresIn) {
        List<String> perms = roleService.getPermCodes(user.getRoleCode());
        String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRoleCode(), military, perms);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setRoleCode(user.getRoleCode());
        resp.setRealName(user.getRealName());
        resp.setMilitaryLogin(military);
        resp.setExpiresIn(expiresIn);
        resp.setRoles(List.of(user.getRoleCode()));
        resp.setPermissions(perms);
        return resp;
    }
}
