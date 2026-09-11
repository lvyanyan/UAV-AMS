package com.uav.alarm.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 全局 JSON 日期格式化：接口输出统一 "yyyy-MM-dd HH:mm:ss"，
 * 前端不再依赖各处自行清洗 ISO 串
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer uavDateFormatCustomizer() {
        JsonSerializer<Timestamp> ts = new JsonSerializer<>() {
            @Override
            public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider sp) throws IOException {
                gen.writeString(TS.format(value.toLocalDateTime()));
            }
            @Override
            public Class<Timestamp> handledType() { return Timestamp.class; }
        };
        JsonSerializer<Date> date = new JsonSerializer<>() {
            @Override
            public void serialize(Date value, JsonGenerator gen, SerializerProvider sp) throws IOException {
                gen.writeString(TS.format(new Timestamp(value.getTime()).toLocalDateTime()));
            }
            @Override
            public Class<Date> handledType() { return Date.class; }
        };
        return builder -> builder.serializers(ts, date);
    }
}
