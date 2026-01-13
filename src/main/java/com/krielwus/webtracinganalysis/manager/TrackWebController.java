package com.krielwus.webtracinganalysis.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.TracingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
    @Value("${server.port:17001}")
    private int serverPort;

    public TrackWebController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    /**
     * 事件上报（POST）：支持大批量 JSON（XHR/sendBeacon）。
     */
    @PostMapping({ "/trackweb", "/api/trackweb" })
    public Map<String, Object> trackweb(@RequestBody(required = false) String body) {
        Map<String, Object> payload = parseBody(body);
        tracingService.ingestAsync(payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("meaage", "上报成功！");
        return resp;
    }

    /**
     * 事件上报（GET）：兼容图片打点，参数 v 为 JSON。
     */
    @GetMapping({ "/trackweb", "/api/trackweb" })
    public Map<String, Object> trackwebGet(@RequestParam(value = "v", required = false) String v) {
        Map<String, Object> payload = parseBody(v);
        tracingService.ingestAsync(payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", "上报成功");
        return resp;
    }

    /**
     * 查询事件列表，支持按事件类型过滤。
     */
    @GetMapping({ "/getAllTracingList", "/api/getAllTracingList" })
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
    @GetMapping({ "/getBaseInfo", "/api/getBaseInfo" })
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
    @PostMapping({ "/cleanTracingList", "/api/cleanTracingList" })
    public Map<String, Object> cleanTracingList() {
        tracingService.cleanAll();
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("meaage", "清除成功！");
        return resp;
    }

    @PostMapping({ "/stressRun", "/api/stressRun" })
    public Map<String, Object> stressRun(@RequestBody Map<String, Object> body) {
        int total = toInt(body.get("total"), 10000);
        int concurrency = toInt(body.get("concurrency"), 100);
        String method = String.valueOf(body.getOrDefault("method", "POST"));
        int eventsPer = toInt(body.get("eventsPerPayload"), 1);
        String appCode = String.valueOf(body.getOrDefault("appCode", "test-app"));
        String appName = String.valueOf(body.getOrDefault("appName", "Test App"));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger err = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Long> lat = Collections.synchronizedList(new ArrayList<>());
        long startAll = System.nanoTime();
        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                long t0 = System.nanoTime();
                boolean success = sendOnce(method, eventsPer, appCode, appName);
                long t1 = System.nanoTime();
                lat.add((t1 - t0) / 1_000_000);
                if (success) ok.incrementAndGet(); else err.incrementAndGet();
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(600, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        long endAll = System.nanoTime();
        long durMs = (endAll - startAll) / 1_000_000;
        double qps = durMs > 0 ? (ok.get() * 1000.0 / durMs) : 0.0;
        java.util.List<Long> snapshot = new java.util.ArrayList<>(lat);
        Collections.sort(snapshot);
        long p50 = percentile(snapshot, 50);
        long p90 = percentile(snapshot, 90);
        long p99 = percentile(snapshot, 99);
        Map<String, Object> resp = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("concurrency", concurrency);
        data.put("ok", ok.get());
        data.put("error", err.get());
        data.put("durationMs", durMs);
        data.put("qps", qps);
        data.put("p50_ms", p50);
        data.put("p90_ms", p90);
        data.put("p99_ms", p99);
        resp.put("code", 200);
        resp.put("data", data);
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

    private boolean sendOnce(String method, int eventsPer, String appCode, String appName) {
        try {
            String url = "http://127.0.0.1:" + serverPort + "/trackweb";
            Map<String, Object> payload = buildPayload(eventsPer, appCode, appName);
            if ("GET".equalsIgnoreCase(method)) {
                String json = objectMapper.writeValueAsString(payload);
                String q = URLEncoder.encode(json, "UTF-8");
                URL u = new URL(url + "?v=" + q);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                int code = c.getResponseCode();
                return code == 200;
            } else {
                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                byte[] bytes = objectMapper.writeValueAsBytes(payload);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
                int code = c.getResponseCode();
                return code == 200;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> buildPayload(int eventsPer, String appCode, String appName) {
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> base = new HashMap<>();
        base.put("appCode", appCode);
        base.put("appName", appName);
        base.put("sessionId", java.util.UUID.randomUUID().toString());
        m.put("baseInfo", base);
        java.util.List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < eventsPer; i++) {
            Map<String, Object> e = new HashMap<>();
            e.put("eventType", "PV");
            e.put("sessionId", base.get("sessionId"));
            e.put("appCode", appCode);
            e.put("appName", appName);
            events.add(e);
        }
        m.put("eventInfo", events);
        return m;
    }

    private int toInt(Object o, int d) {
        if (o == null) return d;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return d; }
    }

    private long percentile(java.util.List<Long> list, int p) {
        if (list == null || list.isEmpty()) return 0;
        int idx = Math.min(list.size() - 1, Math.max(0, (int) Math.round(p / 100.0 * (list.size() - 1))));
        return list.get(idx);
    }
}
