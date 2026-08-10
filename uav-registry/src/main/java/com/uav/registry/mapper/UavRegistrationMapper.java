package com.uav.registry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uav.registry.entity.UavRegistration;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UavRegistrationMapper extends BaseMapper<UavRegistration> {
}
