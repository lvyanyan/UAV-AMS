package com.uav.system.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uav.system.entity.SysUser;
import com.uav.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器
 * 开发环境：自动创建默认管理员和军民协调员账户
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserService userService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initUser("admin", "admin123", "系统管理员", "ADMIN", false);
        initUser("military01", "military123", "军事协调员甲", "MILITARY", true);
        initUser("regulator01", "reg123", "监管员甲", "REGULATOR", false);
        initUser("operator01", "oper123", "操作员甲", "OPERATOR", false);
        initUser("pilot01", "pilot123", "驾驶员甲", "PILOT", false);

        log.info("========================================");
        log.info("  默认账户已初始化:");
        log.info("  admin / admin123       (系统管理员)");
        log.info("  military01 / military123 (军民协调员, MFA=123456)");
        log.info("  regulator01 / reg123   (监管员)");
        log.info("  operator01 / oper123   (操作员)");
        log.info("  pilot01 / pilot123     (驾驶员)");
        log.info("========================================");
    }

    private void initUser(String username, String rawPwd, String realName,
                          String roleCode, boolean mfaEnabled) {
        if (userService.lambdaQuery()
                .eq(SysUser::getUsername, username).count() == 0) {
            SysUser u = new SysUser();
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode(rawPwd));
            u.setRealName(realName);
            u.setRoleCode(roleCode);
            u.setEnabled(true);
            u.setOrgId(1L);
            u.setMfaEnabled(mfaEnabled);
            if (mfaEnabled) {
                u.setMfaType("TOTP");
                u.setMfaSecret("dev-fixed-123456");
            }
            userService.save(u);
            log.info("  创建用户: {}", username);
        }
    }
}
