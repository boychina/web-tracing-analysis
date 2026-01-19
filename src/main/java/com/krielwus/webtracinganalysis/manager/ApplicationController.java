package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.repository.UserAccountRepository;
import com.krielwus.webtracinganalysis.entity.ApplicationInfo;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.ApplicationService;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.*;

@RestController
@RequestMapping("/api/application")
public class ApplicationController {
    private final ApplicationService service;
    private final UserAccountRepository userRepo;

    public ApplicationController(ApplicationService service, UserAccountRepository userRepo) {
        this.service = service;
        this.userRepo = userRepo;
    }

    @GetMapping("/monitor/weeklyError")
    public ResultInfo weeklyError(@RequestParam("appCode") String appCode,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate end;
            java.time.LocalDate start;
            if (startDate != null && endDate != null) {
                start = java.time.LocalDate.parse(startDate, fmt);
                end = java.time.LocalDate.parse(endDate, fmt);
                if (start.isAfter(end)) {
                    return new ResultInfo(400, "date range invalid");
                }
            } else {
                int d = (days == null || days < 1) ? 7 : days;
                end = java.time.LocalDate.now();
                start = end.minusDays(d - 1);
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailyErrorForApp(start, end,
                    appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/list")
    public ResultInfo list(HttpSession session) {
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session
                .getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));

        if (u == null || u.getId() == null) {
            return new ResultInfo(401, "unauthorized");
        }

        String userId = String.valueOf(u.getId());
        String username = u.getUsername();
        List<ApplicationInfo> list = service.listByUser(userId, username, role);
        return new ResultInfo(1000, "success", list);
    }

    @GetMapping("/monitor/dailyBase")
    public ResultInfo monitorDailyBase(@RequestParam("appCode") String appCode,
            @RequestParam(value = "date", required = false) String date) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            java.time.LocalDate today;
            if (date != null && !date.trim().isEmpty()) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
                today = java.time.LocalDate.parse(date.trim(), fmt);
            } else {
                today = java.time.LocalDate.now();
            }
            java.util.Map<String, Object> item = service.aggregateDailyBaseByApp(appCode.trim(), today);
            java.util.List<java.util.Map<String, Object>> data = java.util.Collections.singletonList(item);
            return new ResultInfo(1000, "success", data);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/allBase")
    public ResultInfo monitorAllBase(@RequestParam("appCode") String appCode) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            java.util.Map<String, Object> item = service.aggregateAllBaseByApp(appCode.trim());
            java.util.List<java.util.Map<String, Object>> data = java.util.Collections.singletonList(item);
            return new ResultInfo(1000, "success", data);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyPV")
    public ResultInfo monitorDailyPV(@RequestBody JSONObject body) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        if (start == null || end == null) {
            return new ResultInfo(400, "startDate/endDate required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) {
                return new ResultInfo(400, "date range invalid");
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailyPVForApp(s, e, appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyUV")
    public ResultInfo monitorDailyUV(@RequestBody JSONObject body) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        if (start == null || end == null) {
            return new ResultInfo(400, "startDate/endDate required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) {
                return new ResultInfo(400, "date range invalid");
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailyUVForApp(s, e, appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyDevice")
    public ResultInfo monitorDailyDevice(@RequestBody JSONObject body) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        if (start == null || end == null) {
            return new ResultInfo(400, "startDate/endDate required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) {
                return new ResultInfo(400, "date range invalid");
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailyDeviceForApp(s, e,
                    appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailySession")
    public ResultInfo monitorDailySession(@RequestBody JSONObject body) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        if (start == null || end == null) {
            return new ResultInfo(400, "startDate/endDate required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) {
                return new ResultInfo(400, "date range invalid");
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailySessionForApp(s, e,
                    appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyClick")
    public ResultInfo monitorDailyClick(@RequestBody JSONObject body) {
        if (body == null) { return new ResultInfo(400, "body required"); }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) { return new ResultInfo(400, "appCode required"); }
        if (start == null || end == null) { return new ResultInfo(400, "startDate/endDate required"); }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) { return new ResultInfo(400, "date range invalid"); }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailyClickForApp(s, e,
                    appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid"); }
        catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyError")
    public ResultInfo monitorDailyError(@RequestBody JSONObject body) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        if (start == null || end == null) {
            return new ResultInfo(400, "startDate/endDate required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) {
                return new ResultInfo(400, "date range invalid");
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregateDailyErrorForApp(s, e,
                    appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/errors/recent")
    public ResultInfo recentErrors(@RequestParam("appCode") String appCode,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "errorCode", required = false) String errorCode,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "requestUri", required = false) String requestUri,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            if (pageNo != null || pageSize != null) {
                int p = pageNo == null ? 1 : pageNo;
                int s = pageSize == null ? 20 : pageSize;
                java.util.Map<String, Object> data = service.pageRecentErrorsByAppWithFilters(appCode.trim(), p, s,
                        errorCode, severity, requestUri);
                return new ResultInfo(1000, "success", data);
            }
            int l = (limit == null || limit < 1) ? 20 : limit;
            java.util.List<java.util.Map<String, Object>> list = service.listRecentErrorsByApp(appCode.trim(), l);
            return new ResultInfo(1000, "success", list);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/errors/detail")
    public ResultInfo errorDetail(@RequestParam("appCode") String appCode,
            @RequestParam("id") Long id) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        if (id == null || id < 1) {
            return new ResultInfo(400, "id required");
        }
        try {
            String payload = service.getErrorPayloadByApp(appCode.trim(), id);
            if (payload == null) {
                return new ResultInfo(404, "not found");
            }
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("PAYLOAD", payload);
            return new ResultInfo(1000, "success", data);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/weeklyPagePV")
    public ResultInfo weeklyPagePV(@RequestParam("appCode") String appCode,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate end;
            java.time.LocalDate start;
            if (startDate != null && endDate != null) {
                start = java.time.LocalDate.parse(startDate, fmt);
                end = java.time.LocalDate.parse(endDate, fmt);
                if (start.isAfter(end)) {
                    return new ResultInfo(400, "date range invalid");
                }
            } else {
                int d = (days == null || days < 1) ? 7 : days;
                end = java.time.LocalDate.now();
                start = end.minusDays(d - 1);
            }
            java.util.List<java.util.Map<String, Object>> list = service.aggregatePagePVForApp(start, end,
                    appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/pageRoute/visits")
    public ResultInfo pageRouteVisits(@RequestParam("appCode") String appCode,
            @RequestParam("routePath") String routePath,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        if (appCode == null || appCode.trim().isEmpty())
            return new ResultInfo(400, "appCode required");
        if (routePath == null || routePath.trim().isEmpty())
            return new ResultInfo(400, "routePath required");
        if (startDate == null || endDate == null)
            return new ResultInfo(400, "startDate/endDate required");
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(startDate, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(endDate, fmt);
            if (s.isAfter(e))
                return new ResultInfo(400, "date range invalid");
            int p = pageNo == null ? 1 : pageNo;
            int sz = pageSize == null ? 20 : pageSize;
            java.util.Map<String, Object> data = service.pageRouteVisits(appCode.trim(), routePath.trim(), s, e, p, sz);
            return new ResultInfo(1000, "success", data);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/sessionPaths")
    public ResultInfo sessionPaths(@RequestBody JSONObject body) {
        if (body == null)
            return new ResultInfo(400, "body required");
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        Integer limit = body.getInteger("limitSessions");
        Boolean collapse = body.getBoolean("collapseConsecutiveDuplicates");
        Long minStayMs = body.getLong("minStayMs");
        Integer maxDepth = body.getInteger("maxDepth");
        java.util.List<String> ignoreRoutePatterns = body.getJSONArray("ignoreRoutePatterns") == null
                ? null
                : body.getJSONArray("ignoreRoutePatterns").toJavaList(String.class);
        if (appCode == null || appCode.trim().isEmpty())
            return new ResultInfo(400, "appCode required");
        if (start == null || end == null)
            return new ResultInfo(400, "startDate/endDate required");
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e))
                return new ResultInfo(400, "date range invalid");
            int l = limit == null ? 50 : limit;
            java.util.List<java.util.Map<String, Object>> list = service.listSessionPaths(appCode.trim(), s, e, l,
                    collapse, minStayMs, ignoreRoutePatterns, maxDepth);
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/sessionPaths/aggregate")
    public ResultInfo sessionPathAggregate(@RequestBody JSONObject body) {
        if (body == null)
            return new ResultInfo(400, "body required");
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        Integer limit = body.getInteger("limitSessions");
        Integer topN = body.getInteger("topN");
        String startRoutePath = body.getString("startRoutePath");
        String groupBy = body.getString("groupBy");
        String groupParamName = body.getString("groupParamName");
        Integer maxGroups = body.getInteger("maxGroups");
        Boolean collapse = body.getBoolean("collapseConsecutiveDuplicates");
        Long minStayMs = body.getLong("minStayMs");
        Integer maxDepth = body.getInteger("maxDepth");
        java.util.List<String> ignoreRoutePatterns = body.getJSONArray("ignoreRoutePatterns") == null
                ? null
                : body.getJSONArray("ignoreRoutePatterns").toJavaList(String.class);
        if (appCode == null || appCode.trim().isEmpty())
            return new ResultInfo(400, "appCode required");
        if (start == null || end == null)
            return new ResultInfo(400, "startDate/endDate required");
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e))
                return new ResultInfo(400, "date range invalid");
            int l = limit == null ? 200 : limit;
            int n = topN == null ? 20 : topN;
            java.util.Map<String, Object> data = service.aggregateSessionPathPatterns(appCode.trim(), s, e, l, n,
                    collapse, minStayMs,
                    ignoreRoutePatterns, maxDepth, startRoutePath, groupBy, groupParamName, maxGroups);
            return new ResultInfo(1000, "success", data);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/sessionPaths/sankey")
    public ResultInfo sessionPathSankey(@RequestBody JSONObject body) {
        if (body == null)
            return new ResultInfo(400, "body required");
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        Integer limit = body.getInteger("limitSessions");
        Boolean collapse = body.getBoolean("collapseConsecutiveDuplicates");
        Long minStayMs = body.getLong("minStayMs");
        Integer maxDepth = body.getInteger("maxDepth");
        String startRoutePath = body.getString("startRoutePath");
        java.util.List<String> ignoreRoutePatterns = body.getJSONArray("ignoreRoutePatterns") == null
                ? null
                : body.getJSONArray("ignoreRoutePatterns").toJavaList(String.class);
        if (appCode == null || appCode.trim().isEmpty())
            return new ResultInfo(400, "appCode required");
        if (start == null || end == null)
            return new ResultInfo(400, "startDate/endDate required");
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e))
                return new ResultInfo(400, "date range invalid");
            int l = limit == null ? 1000 : limit;
            java.util.Map<String, Object> data = service.aggregateSessionSankey(appCode.trim(), s, e, l, collapse,
                    minStayMs, ignoreRoutePatterns, maxDepth, startRoutePath);
            return new ResultInfo(1000, "success", data);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/sessionPaths/detail")
    public ResultInfo sessionPathDetail(@RequestParam("appCode") String appCode,
            @RequestParam("sessionId") String sessionId,
                    @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "collapseConsecutiveDuplicates", required = false) Boolean collapse,
            @RequestParam(value = "minStayMs", required = false) Long minStayMs,
            @RequestParam(value = "maxDepth", required = false) Integer maxDepth,
            @RequestParam(value = "ignoreRoutePatterns", required = false) String ignoreRoutePatterns) {
        if (appCode == null || appCode.trim().isEmpty())
            return new ResultInfo(400, "appCode required");
        if (sessionId == null || sessionId.trim().isEmpty())
            return new ResultInfo(400, "sessionId required");
        if (startDate == null || endDate == null)
            return new ResultInfo(400, "startDate/endDate required");
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(startDate, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(endDate, fmt);
            if (s.isAfter(e))
                return new ResultInfo(400, "date range invalid");
            java.util.List<String> ignoreList = null;
            if (ignoreRoutePatterns != null && !ignoreRoutePatterns.trim().isEmpty()) {
                ignoreList = java.util.Arrays.stream(ignoreRoutePatterns.split(","))
                        .map(String::trim).filter(v -> !v.isEmpty()).collect(java.util.stream.Collectors.toList());
            }
            java.util.List<java.util.Map<String, Object>> list = service.getSessionPathDetail(appCode.trim(),
                    sessionId.trim(), s, e, collapse, minStayMs, ignoreList, maxDepth);
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid");
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/create")
    public ResultInfo create(@RequestBody JSONObject body, HttpSession session) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        String name = body.getString("app_name");
        String prefix = body.getString("app_code_prefix");
        String desc = body.getString("app_desc");
        java.util.List<String> managers = body.getJSONArray("app_managers") == null ? java.util.Collections.emptyList()
                : body.getJSONArray("app_managers").toJavaList(String.class);
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session
                .getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        if (role == null || (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role))) {
            return new ResultInfo(403, "forbidden");
        }
        String creator = (u == null || u.getId() == null) ? null : String.valueOf(u.getId());
        managers = filterValidUserIds(managers);
        try {
            ApplicationInfo saved = service.create(name, prefix, desc, managers, creator);
            return new ResultInfo(1000, "success", saved);
        } catch (IllegalArgumentException e) {
            return new ResultInfo(400, e.getMessage());
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/update")
    public ResultInfo update(@RequestBody JSONObject body, HttpSession session) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        Long id = body.getLong("id");
        if (id == null) {
            return new ResultInfo(400, "id required");
        }
        String name = body.getString("app_name");
        String prefix = body.getString("app_code_prefix");
        String desc = body.getString("app_desc");
        java.util.List<String> managers = body.getJSONArray("app_managers") == null ? null
                : body.getJSONArray("app_managers").toJavaList(String.class);
        if (managers != null)
            managers = filterValidUserIds(managers);
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session
                .getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        if (u == null || u.getId() == null) {
            return new ResultInfo(401, "unauthorized");
        }
        String operator = "SUPER_ADMIN".equals(role) ? "admin" : String.valueOf(u.getId());
        try {
            ApplicationInfo saved = service.update(id, name, prefix, desc, managers, operator);
            return new ResultInfo(1000, "success", saved);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null) {
                if ("forbidden".equalsIgnoreCase(msg))
                    return new ResultInfo(403, "forbidden");
                if ("not found".equalsIgnoreCase(msg))
                    return new ResultInfo(404, "not found");
            }
            return new ResultInfo(400, msg == null ? "bad request" : msg);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/delete")
    public ResultInfo delete(@RequestBody JSONObject body, HttpSession session) {
        if (body == null) {
            return new ResultInfo(400, "body required");
        }
        Long id = body.getLong("id");
        if (id == null) {
            return new ResultInfo(400, "id required");
        }
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session
                .getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        if (u == null || u.getId() == null) {
            return new ResultInfo(401, "unauthorized");
        }
        String operator = "SUPER_ADMIN".equals(role) ? "admin" : String.valueOf(u.getId());
        try {
            service.delete(id, operator);
            return new ResultInfo(1000, "success");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && "forbidden".equalsIgnoreCase(msg))
                return new ResultInfo(403, "forbidden");
            return new ResultInfo(400, msg == null ? "bad request" : msg);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/users")
    public ResultInfo users(HttpSession session) {
        // 获取当前用户信息
        String currentRole = String.valueOf(session.getAttribute("role"));

        // 直接查询非 SUPER_ADMIN 角色用户，提高性能
        java.util.List<com.krielwus.webtracinganalysis.entity.UserAccount> list = userRepo.findByRoleNot("SUPER_ADMIN");
        java.util.List<java.util.Map<String, Object>> users = new java.util.ArrayList<>();
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        // 构建返回数据
        for (com.krielwus.webtracinganalysis.entity.UserAccount u : list) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            users.add(m);
        }

        result.put("users", users);
        result.put("currentUserRole", currentRole);

        return new ResultInfo(1000, "success", result);
    }

    private java.util.List<String> filterValidUserIds(java.util.List<String> managers) {
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (String m : managers) {
            if (m == null)
                continue;
            try {
                Long id = Long.valueOf(m);
                com.krielwus.webtracinganalysis.entity.UserAccount ua = userRepo.findById(id).orElse(null);
                if (ua != null)
                    set.add(String.valueOf(id));
            } catch (Exception ignore) {
            }
        }
        return new java.util.ArrayList<>(set);
    }
}
