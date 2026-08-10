package com.uav.registry.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.registry.entity.UavRegistration;
import com.uav.registry.mapper.UavRegistrationMapper;
import org.springframework.stereotype.Service;

@Service
public class UavRegistrationService extends ServiceImpl<UavRegistrationMapper, UavRegistration> {
}
