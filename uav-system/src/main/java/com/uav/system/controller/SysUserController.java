package com.uav.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uav.common.base.R;
import com.uav.system.annotation.AuditLog;
import com.uav.system.dto.SysUserRequest;
import com.uav.system.entity.SysRole;
import com.uav.system.entity.SysUser;
import com.uav.system.service.SysRoleService;
import com.uav.system.service.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理写接口 + 分页查询（RBAC）
 * 旧的只读 /api/user/list 已由此接口的分页版升级替代；/api/auth 登录流程不在此处
 */
@RestController
@RequestMapping("/api/user")
public class SysUserController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public SysUserController(SysUserService userService, SysRoleService roleService,
                             PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    /** 分页 + 关键字（用户名/姓名模糊）+ 角色过滤；密码不下发 */
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String roleCode) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasRole = roleCode != null && !roleCode.isBlank();
        Page<SysUser> result = userService.lambdaQuery()
                .and(hasKeyword, w -> w.like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword))
                .eq(hasRole, SysUser::getRoleCode, roleCode)
                .orderByAsc(SysUser::getId)
                .page(new Page<>(page, size));

        // 角色编码 → 名称映射（一次查询）
        Map<String, String> roleNames = new HashMap<>();
        for (SysRole r : roleService.list()) {
            roleNames.put(r.getRoleCode(), r.getRoleName());
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (SysUser u : result.getRecords()) {
            records.add(toVo(u, roleNames));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", result.getTotal());
        return R.ok(out);
    }

    /** 创建用户：BCrypt 加密 + 角色存在性校验 */
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    @AuditLog(action = "创建用户", target = "用户管理")
    public R<String> create(@RequestBody SysUserRequest req) {
        if (isBlank(req.getUsername()) || isBlank(req.getPassword()) || isBlank(req.getRoleCode())) {
            return R.fail("用户名/密码/角色不能为空");
        }
        if (userService.lambdaQuery().eq(SysUser::getUsername, req.getUsername()).count() > 0) {
            return R.fail("用户名已存在");
        }
        if (roleService.getById(req.getRoleCode()) == null) {
            return R.fail("角色不存在: " + req.getRoleCode());
        }
        SysUser u = new SysUser();
        u.setUsername(req.getUsername().trim());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRealName(req.getRealName());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setRoleCode(req.getRoleCode());
        u.setEnabled(req.getEnabled() == null || req.getEnabled());
        u.setOrgId(1L);
        userService.save(u);
        return R.ok("ok");
    }

    /** 更新用户基础信息/角色（不改用户名与密码） */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @AuditLog(action = "更新用户", target = "用户管理")
    public R<String> update(@PathVariable long id, @RequestBody SysUserRequest req) {
        SysUser u = userService.getById(id);
        if (u == null) return R.fail("用户不存在");
        if (!isBlank(req.getRoleCode()) && roleService.getById(req.getRoleCode()) == null) {
            return R.fail("角色不存在: " + req.getRoleCode());
        }
        if (req.getRealName() != null) u.setRealName(req.getRealName());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getPhone() != null) u.setPhone(req.getPhone());
        if (req.getRoleCode() != null) u.setRoleCode(req.getRoleCode());
        if (req.getEnabled() != null) u.setEnabled(req.getEnabled());
        userService.updateById(u);
        return R.ok("ok");
    }

    /** 删除用户：内置 admin 与当前登录账户禁删 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @AuditLog(action = "删除用户", target = "用户管理")
    public R<String> delete(@PathVariable long id) {
        SysUser u = userService.getById(id);
        if (u == null) return R.fail("用户不存在");
        if ("admin".equalsIgnoreCase(u.getUsername())) {
            return R.fail("内置管理员账户禁止删除");
        }
        if (u.getUsername().equals(currentUsername())) {
            return R.fail("不能删除当前登录账户");
        }
        userService.removeById(id);
        return R.ok("ok");
    }

    /** 启用/停用 */
    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('system:user:enable')")
    @AuditLog(action = "启停用户", target = "用户管理")
    public R<String> setEnabled(@PathVariable long id, @RequestParam boolean enabled) {
        SysUser u = userService.getById(id);
        if (u == null) return R.fail("用户不存在");
        if (!enabled && u.getUsername().equals(currentUsername())) {
            return R.fail("不能停用当前登录账户");
        }
        u.setEnabled(enabled);
        userService.updateById(u);
        return R.ok("ok");
    }

    /** 重置密码（BCrypt 加密） */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-pwd')")
    @AuditLog(action = "重置密码", target = "用户管理")
    public R<String> resetPassword(@PathVariable long id, @RequestBody Map<String, String> body) {
        String pwd = body == null ? null : body.get("password");
        if (isBlank(pwd) || pwd.length() < 6) {
            return R.fail("新密码不能为空且长度至少 6 位");
        }
        SysUser u = userService.getById(id);
        if (u == null) return R.fail("用户不存在");
        u.setPassword(passwordEncoder.encode(pwd));
        userService.updateById(u);
        return R.ok("ok");
    }

    /** 单独分配角色 */
    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('system:user:update')")
    @AuditLog(action = "分配角色", target = "用户管理")
    public R<String> assignRole(@PathVariable long id, @RequestBody Map<String, String> body) {
        String roleCode = body == null ? null : body.get("roleCode");
        if (isBlank(roleCode)) return R.fail("roleCode 不能为空");
        if (roleService.getById(roleCode) == null) return R.fail("角色不存在: " + roleCode);
        SysUser u = userService.getById(id);
        if (u == null) return R.fail("用户不存在");
        u.setRoleCode(roleCode);
        userService.updateById(u);
        return R.ok("ok");
    }

    // ===== 内部工具 =====

    private Map<String, Object> toVo(SysUser u, Map<String, String> roleNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("roleCode", u.getRoleCode());
        m.put("roleName", roleNames.getOrDefault(u.getRoleCode(), u.getRoleCode()));
        m.put("enabled", u.getEnabled());
        m.put("mfaEnabled", u.getMfaEnabled());
        m.put("createTime", u.getCreateTime() == null ? null : TS.format(u.getCreateTime()));
        return m;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
