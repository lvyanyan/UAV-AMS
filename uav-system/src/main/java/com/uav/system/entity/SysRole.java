package com.uav.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 角色实体：role_code 为主键，与 sys_user.role_code 单角色字段直接对应
 * 表结构与 deploy/init-db.sql、RbacInitializer 幂等种子保持一致
 */
@TableName("sys_role")
public class SysRole {
    /** 角色编码（主键，业务标识，如 ADMIN/REGULATOR） */
    @TableId(value = "role_code", type = IdType.INPUT)
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
