package com.uav.airspace.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.uav.airspace.entity.Airspace;
import com.uav.airspace.mapper.AirspaceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AirspaceService extends ServiceImpl<AirspaceMapper, Airspace> {

    public List<Airspace> findByPoint(double lng, double lat) {
        return getBaseMapper().findByPoint(lng, lat);
    }
}
