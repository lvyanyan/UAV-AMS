package com.uav.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper（sys_role_permission 联合主键 role_code + perm_code，无独立实体）
 */
@Mapper
public interface RolePermissionMapper {

    @Insert("INSERT INTO sys_role_permission (role_code, perm_code) VALUES (#{roleCode}, #{permCode}) "
            + "ON CONFLICT (role_code, perm_code) DO NOTHING")
    int insert(@Param("roleCode") String roleCode, @Param("permCode") String permCode);

    @Delete("DELETE FROM sys_role_permission WHERE role_code = #{roleCode}")
    int deleteByRoleCode(@Param("roleCode") String roleCode);

    @Select("SELECT perm_code FROM sys_role_permission WHERE role_code = #{roleCode} ORDER BY perm_code")
    List<String> selectPermCodesByRoleCode(@Param("roleCode") String roleCode);

    @Select("SELECT COUNT(*) FROM sys_role_permission WHERE role_code = #{roleCode}")
    int countByRoleCode(@Param("roleCode") String roleCode);

    @Select("<script>"
            + "SELECT COUNT(*) FROM sys_role_permission WHERE perm_code IN "
            + "<foreach collection='permCodes' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + "</script>")
    int countByPermCodes(@Param("permCodes") List<String> permCodes);
}
