package com.uav.system.controller;

import com.uav.common.base.R;
import com.uav.system.annotation.AuditLog;
import com.uav.system.entity.SysPermission;
import com.uav.system.service.SysPermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限管理：权限树查询 + 权限项维护（MENU/BUTTON）
 * 树节点 = 菜单（含子菜单），子节点 = 菜单下的按钮/操作权限
 */
@RestController
@RequestMapping("/api/permission")
public class SysPermissionController {

    private final SysPermissionService permissionService;

    public SysPermissionController(SysPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 全量权限树（菜单 → 按钮两级或更深） */
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        List<SysPermission> all = permissionService.lambdaQuery()
                .orderByAsc(SysPermission::getSortOrder).list();
        return R.ok(buildTree(all, 0L));
    }

    /** 新增权限项 */
    @PostMapping
    @PreAuthorize("hasAuthority('system:perm:manage')")
    @AuditLog(action = "创建权限项", target = "权限管理")
    public R<String> create(@RequestBody SysPermission perm) {
        if (perm.getPermCode() == null || perm.getPermCode().isBlank()
                || perm.getPermName() == null || perm.getPermName().isBlank()) {
            return R.fail("权限码/名称不能为空");
        }
        if (permissionService.lambdaQuery().eq(SysPermission::getPermCode, perm.getPermCode()).count() > 0) {
            return R.fail("权限码已存在: " + perm.getPermCode());
        }
        if (!"MENU".equals(perm.getPermType()) && !"BUTTON".equals(perm.getPermType())) {
            perm.setPermType("BUTTON");
        }
        if (perm.getParentId() == null) perm.setParentId(0L);
        if (perm.getSortOrder() == null) perm.setSortOrder(0);
        permissionService.save(perm);
        return R.ok("ok");
    }

    /** 更新权限项（权限码不可变） */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:perm:manage')")
    @AuditLog(action = "更新权限项", target = "权限管理")
    public R<String> update(@PathVariable long id, @RequestBody SysPermission body) {
        SysPermission perm = permissionService.getById(id);
        if (perm == null) return R.fail("权限项不存在");
        if (body.getPermName() != null) perm.setPermName(body.getPermName());
        if (body.getPermType() != null && ("MENU".equals(body.getPermType()) || "BUTTON".equals(body.getPermType()))) {
            perm.setPermType(body.getPermType());
        }
        if (body.getParentId() != null) perm.setParentId(body.getParentId());
        if (body.getPath() != null) perm.setPath(body.getPath());
        if (body.getIcon() != null) perm.setIcon(body.getIcon());
        if (body.getSortOrder() != null) perm.setSortOrder(body.getSortOrder());
        permissionService.updateById(perm);
        return R.ok("ok");
    }

    /** 删除权限项：存在子节点时禁删 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:perm:manage')")
    @AuditLog(action = "删除权限项", target = "权限管理")
    public R<String> delete(@PathVariable long id) {
        if (permissionService.lambdaQuery().eq(SysPermission::getParentId, id).count() > 0) {
            return R.fail("存在子权限，请先删除子节点");
        }
        permissionService.removeById(id);
        return R.ok("ok");
    }

    // ===== 内部工具 =====

    /** 递归组装权限树（permCode 为树节点 key） */
    private List<Map<String, Object>> buildTree(List<SysPermission> all, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<SysPermission> children = all.stream()
                .filter(p -> parentId.equals(p.getParentId() == null ? 0L : p.getParentId()))
                .sorted(Comparator.comparing(p -> p.getSortOrder() == null ? 0 : p.getSortOrder()))
                .toList();
        for (SysPermission p : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", p.getId());
            node.put("permCode", p.getPermCode());
            node.put("permName", p.getPermName());
            node.put("permType", p.getPermType());
            node.put("parentId", p.getParentId());
            node.put("path", p.getPath());
            node.put("icon", p.getIcon());
            node.put("sortOrder", p.getSortOrder());
            List<Map<String, Object>> sub = buildTree(all, p.getId());
            node.put("children", sub);
            nodes.add(node);
        }
        return nodes;
    }
}
