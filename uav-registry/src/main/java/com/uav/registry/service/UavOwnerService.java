package com.uav.registry.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.registry.entity.UavOwner;
import com.uav.registry.mapper.UavOwnerMapper;
import org.springframework.stereotype.Service;

@Service
public class UavOwnerService extends ServiceImpl<UavOwnerMapper, UavOwner> {
}
