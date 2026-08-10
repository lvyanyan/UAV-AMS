package com.uav.airspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uav.airspace.entity.Airspace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AirspaceMapper extends BaseMapper<Airspace> {

    @Select("SELECT * FROM uav_airspace WHERE is_active = true " +
            "AND ST_Intersects(geom, ST_SetSRID(ST_MakePoint(#{lng}, #{lat}), 4326))")
    List<Airspace> findByPoint(double lng, double lat);
}
