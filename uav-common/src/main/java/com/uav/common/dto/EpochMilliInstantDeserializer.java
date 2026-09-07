package com.uav.common.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;

/**
 * epoch 毫秒 → Instant 反序列化器。
 * uav-realtime (Go) 的遥测 JSON 中 timestamp 为 epoch 毫秒数值。
 */
public class EpochMilliInstantDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return Instant.ofEpochMilli(p.getValueAsLong());
    }
}
