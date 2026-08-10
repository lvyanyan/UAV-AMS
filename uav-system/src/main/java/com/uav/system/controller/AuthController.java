package com.uav.system.controller;

import com.uav.common.base.R;
import com.uav.system.dto.LoginRequest;
import com.uav.system.dto.LoginResponse;
import com.uav.system.entity.SysUser;
import com.uav.system.security.JwtTokenProvider;
import com.uav.system.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService userService;
    private final JwtTokenProvider jwt;
    private final PasswordEncoder passwordEncoder;

    public AuthController(SysUserService us, JwtTokenProvider jwt, PasswordEncoder pe) {
        this.userService = us;
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

        // 军民协调员二次验证 (开发阶段: 固定 123456)
        if (req.isMilitaryLogin() || "MILITARY".equals(user.getRoleCode())) {
            if (user.getMfaEnabled() != null && user.getMfaEnabled()) {
                if (req.getMfaCode() == null || !req.getMfaCode().equals("123456")) {
                    return R.fail(403, "二次验证码错误");
                }
            }
            String token = jwt.generateToken(user.getId(), user.getUsername(),
                    user.getRoleCode(), true);
            LoginResponse resp = new LoginResponse();
            resp.setToken(token);
            resp.setUsername(user.getUsername());
            resp.setRoleCode(user.getRoleCode());
            resp.setRealName(user.getRealName());
            resp.setMilitaryLogin(true);
            resp.setExpiresIn(900000);
            return R.ok(resp);
        }

        String token = jwt.generateToken(user.getId(), user.getUsername(),
                user.getRoleCode(), false);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setRoleCode(user.getRoleCode());
        resp.setRealName(user.getRealName());
        resp.setMilitaryLogin(false);
        resp.setExpiresIn(7200000);
        return R.ok(resp);
    }

    @PostMapping("/register")
    public R<String> register(@RequestBody SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(true);
        userService.save(user);
        return R.ok("注册成功");
    }

    @GetMapping("/me")
    public R<String> me(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        return R.ok(jwt.getUsername(token));
    }
}
