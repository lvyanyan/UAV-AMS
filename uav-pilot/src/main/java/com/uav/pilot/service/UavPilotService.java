package com.uav.pilot.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.pilot.entity.UavPilot;
import com.uav.pilot.mapper.UavPilotMapper;
import org.springframework.stereotype.Service;

@Service
public class UavPilotService extends ServiceImpl<UavPilotMapper, UavPilot> {
}
