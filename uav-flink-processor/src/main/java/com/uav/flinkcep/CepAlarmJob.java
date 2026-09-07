package com.uav.flinkcep;

import com.uav.flinkcep.model.AlarmEvent;
import com.uav.flinkcep.model.TelemetryEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternFlatSelectFunction;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Flink CEP 遥测流序列告警作业
 * <p>
 * 输入 Topic: uav.telemetry（按 droneSn 分区，事件时间语义，乱序容忍 5s）<br>
 * 输出 Topic: uav.alarm.event（与六维告警引擎同一出口，Go 侧消费后推 WebSocket）<br>
 * <p>
 * 三条序列模式（与阈值型规则引擎互补，捕获"跨时间窗口的行为模式"）：
 * <ol>
 *   <li><b>ABNORMAL_HOVER 异常悬停</b>：60s 内 groundSpeed &lt; 1m/s 的事件 ≥ 12 条（疑似故障悬停或黑飞）</li>
 *   <li><b>SPEED_PLUNGE 速度骤降</b>：10s 内地速从 &gt;3m/s 跌破该机前值的 30%（疑似失控 / 强干扰）</li>
 *   <li><b>BATTERY_PLUNGE 电量骤降</b>：60s 内电量较窗口起点下降 ≥ 15 个百分点（疑似电池异常）</li>
 * </ol>
 * <p>
 * 运行：{@code java -jar uav-flink-processor.jar --bootstrap localhost:9092}
 * （本地 MiniCluster 直接跑；集群模式提交到 Flink JobManager，见 README）
 */
public class CepAlarmJob {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String bootstrap = org.apache.flink.api.java.utils.ParameterTool.fromArgs(args)
                .get("bootstrap", "localhost:9092");

        // 本地运行时使用 WebUI 8081；集群提交时该配置被 JobManager 忽略
        Configuration conf = new Configuration();
        conf.setInteger("rest.port", 8481);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(conf);
        env.setParallelism(4);
        env.enableCheckpointing(10_000);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setTopics("uav.telemetry")
                .setGroupId("flink-cep")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<TelemetryEvent> events = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "uav.telemetry-source")
                .map(s -> TelemetryEvent.parse(s, JSON)).name("parse-json")
                .filter(java.util.Objects::nonNull).name("filter-invalid")
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<TelemetryEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((event, ts) -> event.getTsEpochMs()))
                .name("watermarks");

        DataStream<TelemetryEvent> keyed = events.keyBy(TelemetryEvent::getDroneSn);

        DataStream<AlarmEvent> alerts =
                CEP.pattern(keyed, abnormalHover()).select(new HoverSelect())
                        .union(CEP.pattern(keyed, speedPlunge()).select(new PlungeSelect()))
                        .union(CEP.pattern(keyed, batteryPlunge()).flatSelect(new BatterySelect()));

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrap)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("uav.alarm.event")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        alerts.map(AlarmEvent::toJson).name("to-json").sinkTo(sink).name("uav.alarm.event");

        env.execute("uav-flink-cep-alarm");
    }

    /** 模式 1：异常悬停 —— 60s 窗口内 ≥12 条事件地速 < 1m/s */
    static Pattern<TelemetryEvent, ?> abnormalHover() {
        return Pattern.<TelemetryEvent>begin("hover")
                .where(new SimpleCondition<TelemetryEvent>() {
                    @Override
                    public boolean filter(TelemetryEvent e) {
                        return e.getGroundSpeed() != null && e.getGroundSpeed() < 1.0;
                    }
                })
                .timesOrMore(12)
                .within(Time.seconds(60));
    }

    /** 模式 2：速度骤降 —— 10s 内地速自 >3m/s 跌破窗口起点速度的 30% */
    static Pattern<TelemetryEvent, ?> speedPlunge() {
        return Pattern.<TelemetryEvent>begin("fast")
                .where(new SimpleCondition<TelemetryEvent>() {
                    @Override
                    public boolean filter(TelemetryEvent e) {
                        return e.getGroundSpeed() != null && e.getGroundSpeed() > 3.0;
                    }
                })
                .next("slow")
                .where(new IterativeCondition<TelemetryEvent>() {
                    @Override
                    public boolean filter(TelemetryEvent e, Context<TelemetryEvent> ctx) throws Exception {
                        if (e.getGroundSpeed() == null) return false;
                        for (TelemetryEvent fast : ctx.getEventsForPattern("fast")) {
                            if (e.getGroundSpeed() < 0.3 * fast.getGroundSpeed()) return true;
                        }
                        return false;
                    }
                })
                .within(Time.seconds(10));
    }

    /** 模式 3：电量骤降 —— 60s 内电量较窗口起点下降 ≥15 个百分点 */
    static Pattern<TelemetryEvent, ?> batteryPlunge() {
        return Pattern.<TelemetryEvent>begin("bStart")
                .where(new SimpleCondition<TelemetryEvent>() {
                    @Override
                    public boolean filter(TelemetryEvent e) {
                        return e.getBatteryPercent() != null && e.getBatteryPercent() > 5;
                    }
                })
                .next("bDrop")
                .where(new IterativeCondition<TelemetryEvent>() {
                    @Override
                    public boolean filter(TelemetryEvent e, Context<TelemetryEvent> ctx) throws Exception {
                        if (e.getBatteryPercent() == null) return false;
                        for (TelemetryEvent start : ctx.getEventsForPattern("bStart")) {
                            if (e.getBatteryPercent() <= start.getBatteryPercent() - 15) return true;
                        }
                        return false;
                    }
                })
                .within(Time.seconds(60));
    }

    // ---------- 模式命中 → 告警事件 ----------

    static class HoverSelect implements PatternSelectFunction<TelemetryEvent, AlarmEvent> {
        @Override
        public AlarmEvent select(Map<String, List<TelemetryEvent>> match) {
            List<TelemetryEvent> seq = match.get("hover");
            TelemetryEvent last = seq.get(seq.size() - 1);
            return AlarmEvent.of("ABNORMAL_HOVER", last, "异常悬停",
                    String.format("60s 内出现 %d 条地速 <1m/s 事件，疑似故障悬停或黑飞", seq.size()),
                    1.0, last.getGroundSpeed());
        }
    }

    static class PlungeSelect implements PatternSelectFunction<TelemetryEvent, AlarmEvent> {
        @Override
        public AlarmEvent select(Map<String, List<TelemetryEvent>> match) {
            TelemetryEvent slow = match.get("slow").get(0);
            TelemetryEvent fast = match.get("fast").get(0);
            return AlarmEvent.of("SPEED_PLUNGE", slow, "速度骤降",
                    String.format("地速 %.1fm/s → %.1fm/s（10s 内跌幅超 70%%）",
                            fast.getGroundSpeed(), slow.getGroundSpeed()),
                    fast.getGroundSpeed() * 0.3, slow.getGroundSpeed());
        }
    }

    static class BatterySelect implements PatternFlatSelectFunction<TelemetryEvent, AlarmEvent> {
        @Override
        public void flatSelect(Map<String, List<TelemetryEvent>> match,
                               org.apache.flink.util.Collector<AlarmEvent> out) {
            List<TelemetryEvent> drops = match.get("bDrop");
            TelemetryEvent drop = drops.get(drops.size() - 1);
            TelemetryEvent start = match.get("bStart").get(0);
            out.collect(AlarmEvent.of("BATTERY_PLUNGE", drop, "电量骤降",
                    String.format("电量 %d%% → %d%%（60s 内下降 ≥15 个百分点）",
                            start.getBatteryPercent(), drop.getBatteryPercent()),
                    (double) start.getBatteryPercent() - 15, (double) drop.getBatteryPercent()));
        }
    }
}
