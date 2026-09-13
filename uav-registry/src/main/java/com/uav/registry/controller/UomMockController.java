package com.uav.registry.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 内置 mock UOM 接收端（演示链路）：模拟 UOM 平台的登记数据接收接口，
 * 供 UomReportService 上报，跑通「登记审批 → UOM 上报 → 日志留痕」全链路。
 * 生产环境将 uav.uom.base-url 指向 UOM 真实网关后，本端点即闲置。
 */
@RestController
@RequestMapping("/api/registry/mock-uom")
public class UomMockController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @PostMapping("/registrations")
    public Map<String, Object> receive(@RequestBody(required = false) Map<String, Object> payload) {
        // 模拟 UOM 平台自身响应契约（HTTP 2xx + code=0 视为受理成功），不走项目内 R 包装
        Map<String, Object> data = new LinkedHashMap<>();
        String receipt = "UOM-" + LocalDateTime.now().format(TS) + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        data.put("receiptNo", receipt);
        data.put("receivedAt", LocalDateTime.now().toString());
        data.put("echoRefId", payload == null ? null : payload.get("refId"));
        data.put("platform", "UOM-MOCK");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "接收成功");
        resp.put("data", data);
        return resp;
    }
}
