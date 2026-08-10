package com.uav.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.system.entity.SysUser;
import com.uav.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

@Service
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {
}
