package com.uav.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uav.common.base.R;
import com.uav.system.annotation.AuditLog;
import com.uav.system.entity.SysRole;
import com.uav.system.entity.SysUser;
import com.uav.system.service.SysRoleService;
import com.uav.system.service.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理：分页查询 / CRUD / 角色权限码全量分配
 * 写接口按 RBAC 按钮权限码做方法级鉴权（system:role:create/update/delete/assign）
 */
@RestController
@RequestMapping("/api/role")
public class SysRoleController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysRoleService roleService;
    private final SysUserService userService;

    public SysRoleController(SysRoleService roleService, SysUserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    /** 分页 + 关键字（角色编码/名称/描述模糊） */
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String keyword) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Page<SysRole> result = roleService.lambdaQuery()
                .and(hasKeyword, w -> w.like(SysRole::getRoleCode, keyword)
                        .or().like(SysRole::getRoleName, keyword)
                        .or().like(SysRole::getDescription, keyword))
                .orderByAsc(SysRole::getRoleCode)
                .page(new Page<>(page, size));

        List<Map<String, Object>> records = new ArrayList<>();
        for (SysRole r : result.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleCode", r.getRoleCode());
            m.put("roleName", r.getRoleName());
            m.put("description", r.getDescription());
            m.put("enabled", r.getEnabled());
            m.put("createTime", r.getCreatedAt() == null ? null : TS.format(r.getCreatedAt()));
            records.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", result.getTotal());
        return R.ok(out);
    }

    /** 创建角色 */
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    @AuditLog(action = "创建角色", target = "角色管理")
    public R<String> create(@RequestBody SysRole role) {
        if (role.getRoleCode() == null || role.getRoleCode().isBlank()
                || role.getRoleName() == null || role.getRoleName().isBlank()) {
            return R.fail("角色编码/名称不能为空");
        }
        String code = role.getRoleCode().trim().toUpperCase();
        if (roleService.getById(code) != null) {
            return R.fail("角色编码已存在: " + code);
        }
        role.setRoleCode(code);
        if (role.getEnabled() == null) role.setEnabled(true);
        roleService.save(role);
        return R.ok("ok");
    }

    /** 更新角色（角色编码不可变） */
    @PutMapping("/{roleCode}")
    @PreAuthorize("hasAuthority('system:role:update')")
    @AuditLog(action = "更新角色", target = "角色管理")
    public R<String> update(@PathVariable String roleCode, @RequestBody SysRole body) {
        SysRole role = roleService.getById(roleCode);
        if (role == null) return R.fail("角色不存在: " + roleCode);
        if (body.getRoleName() != null) role.setRoleName(body.getRoleName());
        if (body.getDescription() != null) role.setDescription(body.getDescription());
        if (body.getEnabled() != null) role.setEnabled(body.getEnabled());
        roleService.updateById(role);
        return R.ok("ok");
    }

    /** 删除角色：内置 ADMIN 禁删；仍有用户挂在该角色下禁删 */
    @DeleteMapping("/{roleCode}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @AuditLog(action = "删除角色", target = "角色管理")
    public R<String> delete(@PathVariable String roleCode) {
        if ("ADMIN".equalsIgnoreCase(roleCode)) {
            return R.fail("内置角色 ADMIN 禁止删除");
        }
        if (roleService.getById(roleCode) == null) {
            return R.fail("角色不存在: " + roleCode);
        }
        long users = userService.lambdaQuery().eq(SysUser::getRoleCode, roleCode).count();
        if (users > 0) {
            return R.fail("仍有 " + users + " 个用户使用该角色，请先调整用户角色");
        }
        roleService.removeById(roleCode);
        roleService.assignPermCodes(roleCode, List.of());
        return R.ok("ok");
    }

    /** 查询角色绑定的权限码列表 */
    @GetMapping("/{roleCode}/perms")
    public R<List<String>> getPerms(@PathVariable String roleCode) {
        if (roleService.getById(roleCode) == null) {
            return R.fail("角色不存在: " + roleCode);
        }
        return R.ok(roleService.getPermCodes(roleCode));
    }

    /** 全量设置角色权限码列表 */
    @PutMapping("/{roleCode}/perms")
    @PreAuthorize("hasAuthority('system:role:assign')")
    @AuditLog(action = "分配角色权限", target = "角色管理")
    public R<String> assignPerms(@PathVariable String roleCode, @RequestBody Map<String, Object> body) {
        if (roleService.getById(roleCode) == null) {
            return R.fail("角色不存在: " + roleCode);
        }
        Object permsObj = body == null ? null : body.get("perms");
        if (!(permsObj instanceof List<?> rawList)) {
            return R.fail("请求体应为 {\"perms\": [\"...\"]}");
        }
        List<String> perms = new ArrayList<>();
        for (Object o : rawList) {
            if (o != null && !String.valueOf(o).isBlank()) perms.add(String.valueOf(o));
        }
        roleService.assignPermCodes(roleCode, perms);
        return R.ok("ok");
    }
}
