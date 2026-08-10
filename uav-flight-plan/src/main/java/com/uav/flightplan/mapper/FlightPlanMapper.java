package com.uav.flightplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uav.flightplan.entity.FlightPlan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlightPlanMapper extends BaseMapper<FlightPlan> {
}
