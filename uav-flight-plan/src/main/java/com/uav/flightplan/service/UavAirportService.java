package com.uav.flightplan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.flightplan.entity.UavAirport;
import com.uav.flightplan.mapper.UavAirportMapper;
import org.springframework.stereotype.Service;

@Service
public class UavAirportService extends ServiceImpl<UavAirportMapper, UavAirport> implements IService<UavAirport> {
}
