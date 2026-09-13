package com.uav.registry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.registry.entity.UavOwner;
import com.uav.registry.entity.UavRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UOM 对接（民航局无人驾驶航空器一体化综合监管服务平台）：
 * 实名登记数据 Webhook 出站上报 + external_integration_log 对接留痕 + 失败自动重试。
 *
 * 链路：登记审批通过（或手动触发/批量补报）→ 组装 UOM 报文 → POST {base-url}/registrations
 * → 成功记 SUCCESS 并置 uom_status=REPORTED；失败记 FAILED（retry_count 递增），
 * 定时任务对 FAILED 且未超最大重试次数的记录重发。
 *
 * base-url 默认指向本服务内置 mock 端点（/api/registry/mock-uom）跑通演示链路；
 * 生产环境替换为 UOM 真实网关地址 + 正式 app-id/app-secret 即可，报文字段向后兼容增量调整。
 */
@Service
public class UomReportService {

    private static final Logger log = LoggerFactory.getLogger(UomReportService.class);
    private static final String SYSTEM_CODE = "UOM";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final UavOwnerService ownerService;
    private final RestTemplate restTemplate;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${uav.uom.enabled:true}")
    private boolean enabled;
    @Value("${uav.uom.base-url:http://localhost:8086/api/registry/mock-uom}")
    private String baseUrl;
    @Value("${uav.uom.app-id:uav-ams-demo}")
    private String appId;
    @Value("${uav.uom.app-secret:dev-secret}")
    private String appSecret;
    @Value("${uav.uom.max-retry:5}")
    private int maxRetry;

    public UomReportService(JdbcTemplate jdbc, UavOwnerService ownerService) {
        this.jdbc = jdbc;
        this.ownerService = ownerService;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(f);
    }

    public boolean isEnabled() { return enabled; }

    // ===== 对外入口 =====

    /** 所有人登记上报 UOM（审批通过后自动调用；异常不外抛，避免阻断审批主流程） */
    public void reportOwnerAsync(UavOwner owner) {
        if (!enabled || owner == null) return;
        try {
            dispatch("OWNER_REGISTER", ownerPayload(owner), owner.getId(), "owner");
        } catch (Exception e) {
            log.warn("所有人 UOM 上报失败（id={}）: {}", owner.getId(), e.getMessage());
        }
    }

    /** 无人机登记上报 UOM（审批通过后自动调用；异常不外抛） */
    public void reportDroneAsync(UavRegistration reg) {
        if (!enabled || reg == null) return;
        try {
            dispatch("DRONE_REGISTER", dronePayload(reg), reg.getId(), "drone");
        } catch (Exception e) {
            log.warn("无人机 UOM 上报失败（id={}）: {}", reg.getId(), e.getMessage());
        }
    }

    /** 批量补报：所有已通过但未上报/上报失败的登记。返回摘要。 */
    public synchronized Map<String, Object> reportAllPending() {
        Map<String, Object> summary = new LinkedHashMap<>();
        int ok = 0;
        int fail = 0;
        int skipped = 0;
        if (!enabled) {
            summary.put("enabled", false);
            summary.put("note", "UOM 上报未启用");
            return summary;
        }
        List<Long> ownerIds = jdbc.queryForList(
            "select id from uav_owner where register_status = 'APPROVED' and (uom_status is null or uom_status = 'FAILED')", Long.class);
        for (Long id : ownerIds) {
            UavOwner owner = ownerService.getById(id);
            if (owner == null) { skipped++; continue; }
            ok += dispatch("OWNER_REGISTER", ownerPayload(owner), id, "owner") ? 1 : 0;
        }
        for (Map<String, Object> r : jdbc.queryForList(
            "select id, registration_id, drone_sn, owner_id, drone_model, drone_type, weight_g, manufacturer, register_status, create_time "
          + "from uav_registration where register_status = 'APPROVED' and (uom_status is null or uom_status = 'FAILED')")) {
            ok += dispatch("DRONE_REGISTER", dronePayloadFromRow(r), ((Number) r.get("id")).longValue(), "drone") ? 1 : 0;
        }
        fail = countFailed();
        summary.put("ownerPending", ownerIds.size());
        summary.put("reported", ok);
        summary.put("stillFailed", fail);
        summary.put("skipped", skipped);
        summary.put("reportedAt", LocalDateTime.now().toString());
        log.info("UOM 批量补报完成：{}", summary);
        return summary;
    }

    /** 失败重试：取 FAILED 且 retry_count 未超限的日志重发（定时 + 手动共用） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryFailedScheduled() {
        if (!enabled) return;
        try {
            retryFailed();
        } catch (Exception e) {
            log.warn("UOM 失败重试任务异常: {}", e.getMessage());
        }
    }

    public synchronized Map<String, Object> retryFailed() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id, event_type, payload, retry_count from external_integration_log "
          + "where system_code = ? and status = 'FAILED' and retry_count < ? order by id limit 20", SYSTEM_CODE, maxRetry);
        int ok = 0;
        for (Map<String, Object> row : rows) {
            long logId = ((Number) row.get("id")).longValue();
            String eventType = String.valueOf(row.get("event_type"));
            String payload = String.valueOf(row.get("payload"));
            String kind = "OWNER_REGISTER".equals(eventType) ? "owner" : "drone";
            if (dispatchPayload(eventType, payload, logId, kind)) ok++;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("candidates", rows.size());
        summary.put("retried", ok);
        return summary;
    }

    /** 对接日志（最近 limit 条，倒序） */
    public List<Map<String, Object>> logs(int limit) {
        return jdbc.query("select id, system_code, event_type, payload, status, retry_count, error_msg, create_time "
            + "from external_integration_log where system_code = ? order by id desc limit " + Math.min(Math.max(limit, 1), 200),
            (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("eventType", rs.getString("event_type"));
                m.put("payload", rs.getString("payload"));
                m.put("status", rs.getString("status"));
                m.put("retryCount", rs.getInt("retry_count"));
                m.put("errorMsg", rs.getString("error_msg"));
                m.put("createTime", rs.getTimestamp("create_time"));
                return m;
            }, SYSTEM_CODE);
    }

    // ===== 内部实现 =====

    private Map<String, Object> ownerPayload(UavOwner o) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("refId", o.getId());
        p.put("kind", "owner");
        p.put("ownerName", o.getOwnerName());
        p.put("idType", o.getIdType());
        p.put("idNumber", o.getIdNumber());
        p.put("phone", o.getPhone());
        p.put("address", o.getAddress());
        p.put("registerStatus", o.getRegisterStatus());
        p.put("reportedAt", LocalDateTime.now().format(TS));
        return p;
    }

    private Map<String, Object> dronePayload(UavRegistration reg) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("refId", reg.getId());
        p.put("kind", "drone");
        p.put("registrationId", reg.getRegistrationId());
        p.put("droneSn", reg.getDroneSn());
        p.put("droneModel", reg.getDroneModel());
        p.put("droneType", reg.getDroneType());
        p.put("weightG", reg.getWeightG());
        p.put("manufacturer", reg.getManufacturer());
        p.put("registerStatus", reg.getRegisterStatus());
        UavOwner owner = reg.getOwnerId() == null ? null : ownerService.getById(reg.getOwnerId());
        if (owner != null) {
            p.put("ownerName", owner.getOwnerName());
            p.put("ownerIdNumber", owner.getIdNumber());
            p.put("ownerPhone", owner.getPhone());
        }
        p.put("reportedAt", LocalDateTime.now().format(TS));
        return p;
    }

    private Map<String, Object> dronePayloadFromRow(Map<String, Object> r) {
        UavRegistration reg = new UavRegistration();
        reg.setId(((Number) r.get("id")).longValue());
        reg.setRegistrationId((String) r.get("registration_id"));
        reg.setDroneSn((String) r.get("drone_sn"));
        Object ownerId = r.get("owner_id");
        reg.setOwnerId(ownerId == null ? null : ((Number) ownerId).longValue());
        reg.setDroneModel((String) r.get("drone_model"));
        reg.setDroneType((String) r.get("drone_type"));
        Object weight = r.get("weight_g");
        reg.setWeightG(weight == null ? null : ((Number) weight).doubleValue());
        reg.setManufacturer((String) r.get("manufacturer"));
        reg.setRegisterStatus((String) r.get("register_status"));
        return dronePayload(reg);
    }

    /** 组报文 → POST → 记日志 + 更新记录状态。返回是否成功。 */
    private boolean dispatch(String eventType, Map<String, Object> payload, Long refId, String kind) {
        String payloadJson;
        try {
            payloadJson = om.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = String.valueOf(payload);
        }
        // 同一 refId 已成功上报过则直接置状态，不重复发（幂等）
        try {
            Integer existed = jdbc.queryForObject(
                "select count(*) from external_integration_log where system_code = ? and event_type = ? and status = 'SUCCESS' "
              + "and payload::jsonb @> ?::jsonb",
                Integer.class, SYSTEM_CODE, eventType, "{\"refId\":" + refId + "}");
            if (existed != null && existed > 0) {
                markRefReported(kind, refId);
                return true;
            }
        } catch (Exception ignore) { }
        return dispatchPayload(eventType, payloadJson, null, kind);
    }

    private boolean dispatchPayload(String eventType, String payloadJson, Long existingLogId, String kind) {
        boolean success = false;
        String error = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-UOM-APP-ID", appId);
            headers.set("X-UOM-APP-SECRET", appSecret);
            ResponseEntity<String> resp = restTemplate.postForEntity(baseUrl + "/registrations",
                new HttpEntity<>(payloadJson, headers), String.class);
            JsonNode root = om.readTree(resp.getBody());
            int code = root.path("code").asInt(-1);
            success = resp.getStatusCode().is2xxSuccessful() && code == 0;
            if (!success) error = "UOM 返回 code=" + code;
        } catch (Exception e) {
            error = e.getMessage();
        }

        if (existingLogId != null) {
            // 重试路径：更新原日志行
            if (success) {
                jdbc.update("update external_integration_log set status = 'SUCCESS', error_msg = null where id = ?", existingLogId);
            } else {
                jdbc.update("update external_integration_log set status = 'FAILED', retry_count = retry_count + 1, "
                    + "error_msg = ? where id = ?", error, existingLogId);
            }
        } else {
            jdbc.update("insert into external_integration_log (system_code, event_type, payload, status, retry_count, error_msg) "
                + "values (?,?,?,?,0,?)", SYSTEM_CODE, eventType, payloadJson, success ? "SUCCESS" : "FAILED", error);
        }

        // 同步登记记录的 UOM 状态
        try {
            JsonNode p = om.readTree(payloadJson);
            Long refId = p.path("refId").asLong(0);
            if (refId > 0) markRefReported(kind, refId);
            if (!success && refId > 0) {
                jdbc.update(kind.equals("owner")
                    ? "update uav_owner set uom_status = 'FAILED' where id = ?"
                    : "update uav_registration set uom_status = 'FAILED' where id = ?", refId);
            }
        } catch (Exception ignore) { }
        return success;
    }

    private void markRefReported(String kind, Long refId) {
        jdbc.update(kind.equals("owner")
            ? "update uav_owner set uom_status = 'REPORTED', uom_report_time = now() where id = ?"
            : "update uav_registration set uom_status = 'REPORTED', uom_report_time = now() where id = ?", refId);
    }

    private int countFailed() {
        Integer n = jdbc.queryForObject(
            "select count(*) from uav_registration where register_status = 'APPROVED' and uom_status = 'FAILED'", Integer.class);
        return n == null ? 0 : n;
    }
}
