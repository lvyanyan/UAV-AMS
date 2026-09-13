package com.uav.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.system.entity.SysRole;
import com.uav.system.mapper.RolePermissionMapper;
import com.uav.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色服务：角色 CRUD + 角色权限码查询/全量分配
 */
@Service
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    private final RolePermissionMapper rolePermissionMapper;

    public SysRoleService(RolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /** 查询角色绑定的全部权限码 */
    public List<String> getPermCodes(String roleCode) {
        return rolePermissionMapper.selectPermCodesByRoleCode(roleCode);
    }

    /** 全量覆盖角色权限码列表 */
    public void assignPermCodes(String roleCode, List<String> permCodes) {
        rolePermissionMapper.deleteByRoleCode(roleCode);
        if (permCodes != null) {
            for (String code : permCodes) {
                if (code != null && !code.isBlank()) {
                    rolePermissionMapper.insert(roleCode, code.trim());
                }
            }
        }
    }
}
