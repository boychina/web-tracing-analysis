package com.krielwus.webtracinganalysis.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krielwus.webtracinganalysis.service.TracingService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 埋点上报与查询接口。
 * 兼容 XHR/sendBeacon 的 POST 上报与图片打点的 GET 上报，
 * 并提供事件列表与最新基线信息的查询，以及数据清理能力。
 */
@RestController
public class TrackWebController {
    private final TracingService tracingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrackWebController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    /**
     * 事件上报（POST）：支持大批量 JSON（XHR/sendBeacon）。
     */
    @PostMapping("/trackweb")
    public Map<String, Object> trackweb(@RequestBody(required = false) String body) {
        Map<String, Object> payload = parseBody(body);
        tracingService.ingest(payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("meaage", "上报成功！");
        return resp;
    }

    /**
     * 事件上报（GET）：兼容图片打点，参数 v 为 JSON。
     */
    @GetMapping("/trackweb")
    public Map<String, Object> trackwebGet(@RequestParam(value = "v", required = false) String v) {
        Map<String, Object> payload = parseBody(v);
        tracingService.ingest(payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", "上报成功");
        return resp;
    }

    /**
     * 查询事件列表，支持按事件类型过滤。
     */
    @GetMapping("/getAllTracingList")
    public Map<String, Object> getAllTracingList(@RequestParam(value = "eventType", required = false) String eventType) {
        List<Map<String, Object>> data = tracingService.getAllTracingList(eventType);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", data);
        return resp;
    }

    /**
     * 查询最新一次上报的基线信息。
     */
    @GetMapping("/getBaseInfo")
    public Map<String, Object> getBaseInfo() {
        Map<String, Object> baseInfo = tracingService.getBaseInfo();
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", baseInfo);
        return resp;
    }

    /**
     * 清除所有事件与基线数据（开发调试用）。
     */
    @PostMapping("/cleanTracingList")
    public Map<String, Object> cleanTracingList() {
        tracingService.cleanAll();
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("meaage", "清除成功！");
        return resp;
    }

    /**
     * 安全解析 JSON 字符串为 Map。
     */
    private Map<String, Object> parseBody(String body) {
        try {
            if (body == null || body.trim().isEmpty()) return new HashMap<>();
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
