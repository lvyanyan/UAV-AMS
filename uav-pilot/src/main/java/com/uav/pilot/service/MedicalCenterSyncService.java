package com.uav.pilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 体检中心对接（定时拉取模式）：
 * 定期从体检中心 API 拉取体检结果，按身份证号（缺失时回退姓名）匹配驾驶员，
 * 幂等落库到 uav_pilot_medical（source = CENTER，同飞手同体检日期不重复入库）。
 *
 * base-url 默认指向本服务内置 mock 端点（/api/pilot/mock-center）跑通演示链路；
 * 生产环境替换为真实体检中心地址即可，接口契约：GET {base-url}/exams →
 * {code:0, data:[{idNumber, pilotName, examDate, examOrg, examResult, expireDate, reportUrl}]}。
 */
@Service
public class MedicalCenterSyncService {

    private static final Logger log = LoggerFactory.getLogger(MedicalCenterSyncService.class);

    private final JdbcTemplate jdbc;
    private final RestTemplate restTemplate;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${uav.medical-center.enabled:true}")
    private boolean enabled;
    @Value("${uav.medical-center.base-url:http://localhost:8087/api/pilot/mock-center}")
    private String baseUrl;
    @Value("${uav.medical-center.api-key:dev-key}")
    private String apiKey;

    /** 最近一次同步摘要（供状态查询接口） */
    private volatile Map<String, Object> lastSync = Map.of();

    public MedicalCenterSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(f);
    }

    public boolean isEnabled() { return enabled; }
    public Map<String, Object> getLastSync() { return lastSync; }

    @Scheduled(cron = "${uav.medical-center.cron:0 */5 * * * *}")
    public void scheduledSync() {
        if (!enabled) return;
        try {
            syncOnce();
        } catch (Exception e) {
            log.warn("体检中心定时同步失败: {}", e.getMessage());
        }
    }

    /** 拉取一次体检结果并落库，返回摘要（pulled/inserted/skipped/failed 明细见 detail） */
    public synchronized Map<String, Object> syncOnce() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", enabled);
        summary.put("baseUrl", baseUrl);
        summary.put("syncedAt", LocalDateTime.now().toString());
        if (!enabled) {
            summary.put("pulled", 0);
            summary.put("inserted", 0);
            summary.put("skipped", 0);
            summary.put("note", "体检中心同步未启用");
            lastSync = summary;
            return summary;
        }

        JsonNode items = fetchExams();
        int pulled = items.isArray() ? items.size() : 0;
        int inserted = 0;
        int skipped = 0;
        int unmatched = 0;

        if (items.isArray()) {
            for (JsonNode item : items) {
                try {
                    Long pilotId = matchPilot(item);
                    if (pilotId == null) { unmatched++; continue; }
                    LocalDateTime examDate = parseDate(item.path("examDate").asText(null));
                    if (examDate == null) { skipped++; continue; }
                    Integer dup = jdbc.queryForObject(
                        "select count(*) from uav_pilot_medical where pilot_id = ? and exam_date = ? and source = 'CENTER'",
                        Integer.class, pilotId, examDate);
                    if (dup != null && dup > 0) { skipped++; continue; }
                    jdbc.update("insert into uav_pilot_medical (pilot_id, exam_date, exam_org, exam_result, exam_report_url, expire_date, source) "
                            + "values (?,?,?,?,?,?, 'CENTER')",
                        pilotId, examDate,
                        item.path("examOrg").asText(null),
                        item.path("examResult").asText("PASS"),
                        item.path("reportUrl").asText(null),
                        parseDate(item.path("expireDate").asText(null)));
                    inserted++;
                } catch (Exception e) {
                    log.warn("体检记录落库失败（{}）: {}", item.path("pilotName").asText(""), e.getMessage());
                    skipped++;
                }
            }
        }

        summary.put("pulled", pulled);
        summary.put("inserted", inserted);
        summary.put("skipped", skipped);
        summary.put("unmatched", unmatched);
        lastSync = summary;
        log.info("体检中心同步完成：拉取 {} 条，新增 {} 条，跳过 {} 条，未匹配飞手 {} 条", pulled, inserted, skipped, unmatched);
        return summary;
    }

    private JsonNode fetchExams() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-KEY", apiKey);
            ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/exams", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = om.readTree(resp.getBody());
            if (root.has("data")) return root.path("data");
            return root;
        } catch (Exception e) {
            log.warn("拉取体检中心数据失败（{}）: {}", baseUrl, e.getMessage());
            return om.createObjectNode();
        }
    }

    /** 身份证号精确匹配，无身份证号时回退姓名匹配（演示数据的兜底路径） */
    private Long matchPilot(JsonNode item) {
        String idNumber = item.path("idNumber").asText("");
        if (!idNumber.isBlank()) {
            return jdbc.query("select id from uav_pilot where id_number = ? limit 1",
                rs -> rs.next() ? rs.getLong(1) : null, idNumber);
        }
        String name = item.path("pilotName").asText("");
        if (!name.isBlank()) {
            return jdbc.query("select id from uav_pilot where pilot_name = ? order by id limit 1",
                rs -> rs.next() ? rs.getLong(1) : null, name);
        }
        return null;
    }

    /** 兼容 yyyy-MM-dd 与 ISO 两种格式 */
    private LocalDateTime parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            if (text.length() == 10) return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            log.warn("日期解析失败: {}", text);
            return null;
        }
    }
}
