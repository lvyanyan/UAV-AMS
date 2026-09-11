package com.uav.flightplan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.flightplan.entity.UavRoute;
import com.uav.flightplan.mapper.UavRouteMapper;
import org.springframework.stereotype.Service;

@Service
public class UavRouteService extends ServiceImpl<UavRouteMapper, UavRoute> implements IService<UavRoute> {
}
